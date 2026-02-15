use crate::{
    config::CONFIG,
    db,
    events::{self, SearchResultsFoundPayload, WsEvent},
    AppState,
};
use futures::StreamExt;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::sync::Arc;

#[derive(Debug, Serialize, Deserialize)]
struct LlamaResponse {
    content: String,
}

#[derive(Debug, Deserialize)]
struct AiScoreEntry {
    index: usize,
    score: i32,
    valid: bool,
}

pub async fn oracle_worker(state: Arc<AppState>) -> anyhow::Result<()> {
    if !CONFIG.oracle_enabled {
        tracing::info!("Oracle (IA) desactive via la configuration.");
        return Ok(());
    }

    tracing::info!("Le worker Oracle demarre...");
    let client = reqwest::Client::new();

    let stream = state
        .jetstream_context
        .get_or_create_stream(async_nats::jetstream::stream::Config {
            name: "sokoul_analysis".to_string(),
            subjects: vec![events::SEARCH_RESULTS_FOUND_SUBJECT.to_string()],
            ..Default::default()
        })
        .await?;

    let consumer = stream
        .create_consumer(async_nats::jetstream::consumer::pull::Config {
            durable_name: Some("oracle_worker".to_string()),
            ..Default::default()
        })
        .await?;

    let mut messages = consumer.messages().await?;
    while let Some(Ok(message)) = messages.next().await {
        let payload: SearchResultsFoundPayload = match serde_json::from_slice(&message.payload) {
            Ok(p) => p,
            Err(e) => {
                tracing::error!("Payload invalide pour Oracle: {}", e);
                message.ack().await.map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
                continue;
            }
        };

        tracing::info!("Oracle: Analyse demandee pour media_id {}", payload.media_id);

        let media = match db::media::get_media_by_id(&state.db_pool, payload.media_id).await {
            Ok(m) => m,
            Err(e) => {
                tracing::error!("Oracle: impossible de charger le media {}: {}", payload.media_id, e);
                message.ack().await.map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
                continue;
            }
        };
        let results = db::search_results::get_results_by_media_id(&state.db_pool, payload.media_id).await?;

        if results.is_empty() {
            message.ack().await.map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
            continue;
        }

        let titles: Vec<String> = results.iter().map(|r| r.title.clone()).collect();
        let prompt = format!(
            "Tu es un expert en cinema. Analyse la liste de fichiers suivante pour le film/serie '{}' ({:?}). \
            Identifie les fichiers qui correspondent vraiment (pas de fake, bonne annee) et attribue un score de qualite (0-100) base sur la resolution et les mots cles (HDR, HEVC, etc). \
            Liste des fichiers: {:?}. \
            Reponds UNIQUEMENT avec un tableau JSON d'objets {{ \"index\": int, \"score\": int, \"valid\": bool }}.",
            media.title, media.year, titles
        );

        match client.post(format!("{}/completion", CONFIG.oracle_url))
            .json(&json!({
                "prompt": prompt,
                "n_predict": 512,
                "temperature": 0.1,
                "json_schema": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "index": {"type": "integer"},
                            "score": {"type": "integer"},
                            "valid": {"type": "boolean"}
                        }
                    }
                }
            }))
            .send()
            .await
        {
            Ok(resp) => {
                match resp.json::<LlamaResponse>().await {
                    Ok(llama_resp) => {
                        match serde_json::from_str::<Vec<AiScoreEntry>>(&llama_resp.content) {
                            Ok(entries) => {
                                let mut updated = 0u32;
                                for entry in &entries {
                                    if entry.index < results.len() {
                                        let result_id = results[entry.index].id;
                                        if let Err(e) = db::search_results::update_score(
                                            &state.db_pool,
                                            result_id,
                                            entry.score,
                                            entry.valid,
                                        ).await {
                                            tracing::error!("Erreur MAJ score result_id {}: {}", result_id, e);
                                        } else {
                                            updated += 1;
                                        }
                                    }
                                }
                                tracing::info!(
                                    "Oracle: {} resultats mis a jour pour media_id {}",
                                    updated, payload.media_id
                                );

                                // Emit oracle validated event
                                let _ = state.event_tx.send(WsEvent::OracleValidated {
                                    media_id: payload.media_id.to_string(),
                                    validated_count: updated,
                                }.to_json());
                            }
                            Err(e) => {
                                tracing::error!("Oracle: Impossible de parser la reponse IA: {} - contenu: {}", e, llama_resp.content);
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!("Oracle: Erreur de deserialisation de la reponse llama.cpp: {}", e);
                    }
                }
            }
            Err(e) => {
                tracing::error!("Erreur appel IA: {}", e);
            }
        }

        message.ack().await.map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
    }

    Ok(())
}
