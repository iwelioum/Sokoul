use crate::{
    config::CONFIG,
    db,
    events::{self, DownloadRequestedPayload, WsEvent},
    utils::{
        fuzzy,
        retry::{self, RetryConfig},
    },
    AppState,
};
use futures::StreamExt;
use librqbit::{AddTorrent, Session};
use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::Semaphore;
use url::Url;

pub async fn hunter_worker(state: Arc<AppState>) -> anyhow::Result<()> {
    tracing::info!("Le worker Hunter demarre...");

    let download_dir = PathBuf::from(&CONFIG.download_dir);
    tokio::fs::create_dir_all(&download_dir).await?;

    let session: Arc<Session> = Session::new(download_dir).await?;

    let semaphore = Arc::new(Semaphore::new(CONFIG.max_concurrent_downloads));

    let stream = state
        .jetstream_context
        .get_or_create_stream(async_nats::jetstream::stream::Config {
            name: "sokoul_downloads".to_string(),
            subjects: vec![events::DOWNLOAD_REQUESTED_SUBJECT.to_string()],
            ..Default::default()
        })
        .await?;

    let consumer = stream
        .create_consumer(async_nats::jetstream::consumer::pull::Config {
            durable_name: Some("hunter_worker".to_string()),
            ..Default::default()
        })
        .await?;

    let mut messages = consumer.messages().await?;
    while let Some(Ok(message)) = messages.next().await {
        let payload: DownloadRequestedPayload = match serde_json::from_slice(&message.payload) {
            Ok(p) => p,
            Err(e) => {
                tracing::error!("Payload invalide pour Hunter: {}", e);
                message
                    .ack()
                    .await
                    .map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
                continue;
            }
        };

        tracing::info!(
            "Hunter: telechargement demande pour '{}' (media_id: {})",
            payload.title,
            payload.media_id
        );

        // Fuzzy validation du titre
        let media = match db::media::get_media_by_id(&state.db_pool, payload.media_id).await {
            Ok(m) => Some(m),
            Err(e) => {
                tracing::warn!(
                    "Hunter: impossible de charger le media pour validation fuzzy: {}",
                    e
                );
                None
            }
        };

        if let Some(ref media) = media {
            let similarity = fuzzy::title_similarity(&payload.title, &media.title);
            tracing::info!(
                "Hunter: similarite titre '{}' vs '{}' = {:.2}%",
                payload.title,
                media.title,
                similarity * 100.0
            );

            if !fuzzy::is_title_match(&payload.title, &media.title, 0.50) {
                tracing::warn!(
                    "Hunter: titre '{}' ne correspond pas a '{}' (score: {:.2}%), skip.",
                    payload.title,
                    media.title,
                    similarity * 100.0
                );
                message
                    .ack()
                    .await
                    .map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
                continue;
            }
        }

        let session_clone = session.clone();
        let db_pool = state.db_pool.clone();
        let event_tx = state.event_tx.clone();
        let permit = semaphore.clone().acquire_owned().await?;
        let media_id = payload.media_id;

        let task = db::tasks::create_task(
            &db_pool,
            &crate::models::CreateTaskPayload {
                task_type: "download".to_string(),
                payload: Some(serde_json::json!({
                    "media_id": media_id.to_string(),
                    "title": payload.title,
                    "search_result_id": payload.search_result_id,
                })),
            },
        )
        .await;

        let task_id = match task {
            Ok(t) => Some(t.id),
            Err(e) => {
                tracing::error!("Echec creation tache pour download: {}", e);
                None
            }
        };

        tokio::spawn(async move {
            let _permit = permit;

            if let Some(tid) = task_id {
                let _ = db::tasks::update_task_status(&db_pool, tid, "running", None).await;
            }

            let _ = event_tx.send(
                WsEvent::DownloadStarted {
                    media_id: media_id.to_string(),
                    title: payload.title.clone(),
                }
                .to_json(),
            );

            // Retry avec backoff exponentiel
            let retry_config = RetryConfig {
                max_attempts: 3,
                initial_delay_ms: 5000,
                backoff_multiplier: 2.0,
                max_delay_ms: 30_000,
            };

            let magnet = payload.magnet_or_url.clone();
            let result = retry::retry_with_backoff(
                &retry_config,
                &format!("download '{}'", payload.title),
                || {
                    let session_ref = session_clone.clone();
                    let magnet_ref = magnet.clone();
                    async move { download_torrent(&session_ref, &magnet_ref).await }
                },
            )
            .await;

            match result {
                Ok(output_name) => {
                    tracing::info!(
                        "Telechargement termine pour '{}': {}",
                        payload.title,
                        output_name
                    );

                    let _ = db::media_files::create_media_file(
                        &db_pool,
                        media_id,
                        &output_name,
                        "torrent",
                    )
                    .await;

                    if let Some(tid) = task_id {
                        let _ = db::tasks::complete_task(
                            &db_pool,
                            tid,
                            Some(serde_json::json!({ "file_path": output_name })),
                        )
                        .await;
                    }

                    let _ = event_tx.send(
                        WsEvent::DownloadCompleted {
                            media_id: media_id.to_string(),
                            title: payload.title.clone(),
                            file_path: output_name,
                        }
                        .to_json(),
                    );
                }
                Err(e) => {
                    tracing::error!("Echec telechargement '{}': {}", payload.title, e);

                    if let Some(tid) = task_id {
                        let _ = db::tasks::update_task_status(
                            &db_pool,
                            tid,
                            "failed",
                            Some(&e.to_string()),
                        )
                        .await;
                    }

                    let _ = event_tx.send(
                        WsEvent::DownloadFailed {
                            media_id: media_id.to_string(),
                            title: payload.title.clone(),
                            error: e.to_string(),
                        }
                        .to_json(),
                    );
                }
            }
        });

        message
            .ack()
            .await
            .map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
    }

    Ok(())
}

async fn download_torrent(session: &Arc<Session>, magnet_or_url: &str) -> anyhow::Result<String> {
    let _ = Url::parse(magnet_or_url)
        .map_err(|e| anyhow::anyhow!("URL/magnet invalide: {}: {}", e, magnet_or_url))?;

    let handle = session
        .add_torrent(AddTorrent::from_url(magnet_or_url), None)
        .await?
        .into_handle()
        .ok_or_else(|| anyhow::anyhow!("Torrent deja gere ou echec d'ajout: {}", magnet_or_url))?;

    handle.wait_until_completed().await?;

    Ok(magnet_or_url.to_string())
}
