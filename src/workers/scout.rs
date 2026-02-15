use crate::{
    clients::tmdb::TmdbClient,
    config::CONFIG,
    db,
    events::{self, SearchRequestedPayload, SearchResultsFoundPayload, WsEvent},
    models::CreateMediaPayload,
    providers::{
        jackett::JackettProvider, prowlarr::ProwlarrProvider, streaming::StreamingProvider,
        ProviderRegistry,
    },
    utils::scoring,
    AppState,
};
use futures::stream::StreamExt;
use std::sync::Arc;

pub async fn scout_worker(state: Arc<AppState>) -> anyhow::Result<()> {
    tracing::info!("Le worker Scout demarre...");

    let tmdb_client = TmdbClient::new(CONFIG.tmdb_api_key.clone());

    let mut registry = ProviderRegistry::new();

    if !CONFIG.prowlarr_url.is_empty() && !CONFIG.prowlarr_api_key.is_empty() {
        registry.register(Box::new(ProwlarrProvider::new(
            CONFIG.prowlarr_api_key.clone(),
            CONFIG.prowlarr_url.clone(),
            state.flaresolverr_client.clone(),
        )));
    }

    if !CONFIG.jackett_url.is_empty() && !CONFIG.jackett_api_key.is_empty() {
        registry.register(Box::new(JackettProvider::new(
            CONFIG.jackett_api_key.clone(),
            CONFIG.jackett_url.clone(),
            state.flaresolverr_client.clone(),
        )));
    }

    if CONFIG.streaming_enabled {
        if let Some(browser) = &state.browser {
            registry.register(Box::new(StreamingProvider::new(browser.clone()).await));
        }
    }

    let registry = Arc::new(registry);

    tracing::info!(
        "Scout: provider(s) actif(s): {:?}",
        registry.list_enabled_names()
    );

    let jetstream = state.jetstream_context.clone();

    let stream = state
        .jetstream_context
        .get_or_create_stream(async_nats::jetstream::stream::Config {
            name: "sokoul_jobs".to_string(),
            subjects: vec![events::SEARCH_REQUESTED_SUBJECT.to_string()],
            ..Default::default()
        })
        .await?;

    let consumer = stream
        .create_consumer(async_nats::jetstream::consumer::pull::Config {
            durable_name: Some("scout_worker".to_string()),
            ..Default::default()
        })
        .await?;

    let mut messages = consumer.messages().await?;
    while let Some(Ok(message)) = messages.next().await {
        tracing::info!("Scout a recu un nouveau message.");
        let payload: SearchRequestedPayload = serde_json::from_slice(&message.payload)?;

        tracing::info!("Debut de la recherche TMDB pour: '{}'", payload.query);

        // Emit search started event
        let _ = state.event_tx.send(
            WsEvent::SearchStarted {
                query: payload.query.clone(),
            }
            .to_json(),
        );

        let tmdb_results = match tmdb_client.search_multi(&payload.query).await {
            Ok(results) => results,
            Err(e) => {
                tracing::error!(
                    "Echec recherche TMDB pour '{}': {}. Le message sera re-traite.",
                    payload.query,
                    e
                );
                continue;
            }
        };

        tracing::info!(
            "TMDB a trouve {} resultat(s) pour '{}'. Traitement en parallele.",
            tmdb_results.len(),
            payload.query
        );

        let event_tx_clone = state.event_tx.clone();

        futures::stream::iter(tmdb_results)
            .for_each_concurrent(5, |result| {
                let registry_clone = registry.clone();
                let db_pool_clone = state.db_pool.clone();
                let jetstream_clone = jetstream.clone();
                let event_tx = event_tx_clone.clone();

                async move {
                    let media_payload = CreateMediaPayload {
                        media_type: result.media_type.clone(),
                        title: result.get_title(),
                        year: result.get_year(),
                        tmdb_id: Some(result.id),
                        overview: result.overview.clone(),
                        poster_url: result.poster_path.as_ref().map(|p| format!("https://image.tmdb.org/t/p/w500{}", p)),
                        genres: None,
                        rating: None,
                    };

                    match db::media::create_media(&db_pool_clone, &media_payload).await {
                        Ok(media) => {
                            tracing::info!("Media '{}' (ID: {}) ajoute/mis a jour dans la base.", media.title, media.id);

                            let sources = registry_clone
                                .search_all(&media.title, &media.media_type, media.tmdb_id)
                                .await;

                            if sources.is_empty() {
                                tracing::info!("Aucune source trouvee pour '{}'", media.title);
                                return;
                            }

                            // Auto-score each source before saving
                            let scored_sources: Vec<_> = sources.iter().map(|s| {
                                let score = scoring::compute_score(s);
                                tracing::debug!("Score auto pour '{}': {}", s.title, score);
                                (s, score)
                            }).collect();

                            tracing::info!("{} source(s) trouvee(s) pour '{}' (tous providers)", sources.len(), media.title);

                            match db::search_results::create_batch(&db_pool_clone, media.id, &sources).await {
                                Ok(inserted_count) => {
                                    if inserted_count > 0 {
                                        tracing::info!("{} nouvelle(s) source(s) sauvegardee(s) pour '{}'.", inserted_count, media.title);

                                        // Apply auto-scores to inserted results
                                        if let Ok(saved_results) = db::search_results::get_results_by_media_id(&db_pool_clone, media.id).await {
                                            for result in &saved_results {
                                                if result.score.is_none() {
                                                    if let Some((_, auto_score)) = scored_sources.iter().find(|(s, _)| s.guid == result.guid) {
                                                        let _ = db::search_results::update_score(
                                                            &db_pool_clone,
                                                            result.id,
                                                            *auto_score,
                                                            false,
                                                        ).await;
                                                    }
                                                }
                                            }
                                        }

                                        // Emit search completed event
                                        let _ = event_tx.send(WsEvent::SearchCompleted {
                                            media_id: media.id.to_string(),
                                            title: media.title.clone(),
                                            results_count: sources.len(),
                                        }.to_json());

                                        let event = SearchResultsFoundPayload { media_id: media.id };
                                        if let Ok(payload) = serde_json::to_vec(&event) {
                                            if let Err(e) = jetstream_clone.publish(events::SEARCH_RESULTS_FOUND_SUBJECT, payload.into()).await {
                                                tracing::error!("Erreur publication event search_results_found: {}", e);
                                            }
                                        }
                                    }
                                }
                                Err(e) => {
                                    tracing::error!("Echec sauvegarde sources pour '{}': {}", media.title, e);
                                }
                            }
                        }
                        Err(e) => {
                            tracing::error!("Echec enregistrement media TMDB ID {}: {}", result.id, e);
                        }
                    }
                }
            })
            .await;

        message
            .ack()
            .await
            .map_err(|e| anyhow::anyhow!("Failed to ack message: {}", e))?;
    }

    Ok(())
}
