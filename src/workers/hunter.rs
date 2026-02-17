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
use reqwest::header::LOCATION;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::Semaphore;
use url::Url;

pub async fn hunter_worker(state: Arc<AppState>) -> anyhow::Result<()> {
    tracing::info!("Hunter worker starting...");

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
                tracing::error!("Invalid payload for Hunter: {}", e);
                message
                    .ack()
                    .await
                    .map_err(|e| anyhow::anyhow!("Ack failed: {}", e))?;
                continue;
            }
        };

        tracing::info!(
            "Hunter: download requested for '{}' (media_id: {})",
            payload.title,
            payload.media_id
        );

        let media = match db::media::get_media_by_id(&state.db_pool, payload.media_id).await {
            Ok(m) => Some(m),
            Err(e) => {
                tracing::warn!("Hunter: failed to load media for fuzzy validation: {}", e);
                None
            }
        };

        if let Some(ref media) = media {
            let similarity = fuzzy::title_similarity(&payload.title, &media.title);
            tracing::info!(
                "Hunter: title similarity '{}' vs '{}' = {:.2}%",
                payload.title,
                media.title,
                similarity * 100.0
            );

            if !fuzzy::is_title_match(&payload.title, &media.title, 0.50) {
                tracing::warn!(
                    "Hunter: title '{}' does not match '{}' (score: {:.2}%), skipping.",
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
                tracing::error!("Failed to create task for download: {}", e);
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

            // Retry with exponential backoff
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
                        "Download completed for '{}': {}",
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
                    tracing::error!("Download failed for '{}': {}", payload.title, e);

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
    let mut current = magnet_or_url.to_string();

    // Prowlarr/Jackett download URLs may redirect (301/302) to a magnet link.
    // rqbit doesn't handle HTTP redirects here, so resolve them ourselves.
    for _ in 0..5 {
        let parsed = Url::parse(&current)
            .map_err(|e| anyhow::anyhow!("Invalid URL/magnet: {}: {}", e, current))?;

        match parsed.scheme() {
            "http" | "https" => {
                let client = reqwest::Client::builder()
                    .redirect(reqwest::redirect::Policy::none())
                    .build()?;

                let resp = client.get(parsed.as_str()).send().await?;

                if resp.status().is_redirection() {
                    let loc = resp
                        .headers()
                        .get(LOCATION)
                        .and_then(|v| v.to_str().ok())
                        .ok_or_else(|| anyhow::anyhow!("Redirect without Location: {}", current))?;

                    // Location can be a magnet:? or a relative/absolute URL.
                    current = parsed
                        .join(loc)
                        .map(|u| u.to_string())
                        .unwrap_or_else(|_| loc.to_string());
                    continue;
                }

                if !resp.status().is_success() {
                    return Err(anyhow::anyhow!(
                        "GET {} returned {}",
                        current,
                        resp.status()
                    ));
                }

                break;
            }
            "magnet" => break,
            _ => break,
        }
    }

    let _ = Url::parse(&current)
        .map_err(|e| anyhow::anyhow!("Invalid URL/magnet: {}: {}", e, current))?;

    let handle = session
        .add_torrent(AddTorrent::from_url(&current), None)
        .await?
        .into_handle()
        .ok_or_else(|| anyhow::anyhow!("Torrent already managed or failed to add: {}", current))?;

    handle.wait_until_completed().await?;

    Ok(current)
}
