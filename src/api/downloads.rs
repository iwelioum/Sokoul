use crate::{
    api::error::ApiError,
    db,
    events::{self, DownloadRequestedPayload},
    security, AppState,
};
use axum::{
    extract::{Path, State},
    http::StatusCode,
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct StartDownloadPayload {
    pub media_id: Uuid,
    pub search_result_id: i32,
}

/// POST /downloads - Start a download from a search result
pub async fn start_download_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<StartDownloadPayload>,
) -> Result<(StatusCode, Json<serde_json::Value>), ApiError> {
    let results =
        db::search_results::get_results_by_media_id(&state.db_pool, payload.media_id).await?;

    let result = results
        .iter()
        .find(|r| r.id == payload.search_result_id)
        .ok_or_else(|| ApiError::NotFound("Search result not found".to_string()))?;

    let magnet_or_url = result
        .magnet_link
        .clone()
        .or_else(|| result.url.clone())
        .ok_or_else(|| {
            ApiError::InvalidInput("No magnet/URL available for this result".to_string())
        })?;

    // Security Check: Validate URL safety before allowing download
    let security_check = security::check_url_safety(&state, &magnet_or_url).await;

    if security_check.risk_level == "critical" {
        // Block critical risk and log event
        let _ = db::security::insert_audit_log(
            &state.db_pool,
            None,
            "download_blocked",
            Some("url"),
            Some(&payload.search_result_id.to_string()),
            Some(&magnet_or_url),
            None,
            None,
            "critical",
            "blocked",
            Some(serde_json::json!({
                "reason": security_check.reason,
                "virustotal_count": security_check.virustotal_malicious_count,
                "urlhaus_threat": security_check.urlhaus_threat,
            })),
        )
        .await;

        let admin_email =
            std::env::var("ADMIN_EMAIL").unwrap_or_else(|_| "admin@sokoul.local".to_string());
        let _ = state
            .email_service
            .send_critical_alert(
                &admin_email,
                &magnet_or_url,
                &security_check.risk_level,
                &security_check.reason,
            )
            .await;

        return Err(ApiError::Forbidden(format!(
            "Download blocked - Security risk detected: {}",
            security_check.reason
        )));
    }

    // Log warning-level risks but allow
    if security_check.risk_level == "warning" {
        let _ = db::security::insert_audit_log(
            &state.db_pool,
            None,
            "download_warning",
            Some("url"),
            Some(&payload.search_result_id.to_string()),
            Some(&magnet_or_url),
            None,
            None,
            "warning",
            "allowed",
            Some(serde_json::json!({
                "reason": security_check.reason,
            })),
        )
        .await;
    }

    let download_event = DownloadRequestedPayload {
        media_id: payload.media_id,
        search_result_id: payload.search_result_id,
        magnet_or_url,
        title: result.title.clone(),
    };

    let event_data = serde_json::to_vec(&download_event)
        .map_err(|e| ApiError::MessageBus(format!("Serialization error: {}", e)))?;

    state
        .jetstream_context
        .publish(events::DOWNLOAD_REQUESTED_SUBJECT, event_data.into())
        .await
        .map_err(|e| ApiError::MessageBus(e.to_string()))?;

    Ok((
        StatusCode::ACCEPTED,
        Json(serde_json::json!({
            "message": "Download started.",
            "media_id": payload.media_id,
            "search_result_id": payload.search_result_id,
            "security_warning": if security_check.risk_level == "warning" {
                Some(security_check.reason)
            } else {
                None
            },
        })),
    ))
}

/// GET /downloads - List current/completed downloads (via tasks)
pub async fn list_downloads_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<crate::models::Task>>, ApiError> {
    let all_tasks = sqlx::query_as::<_, crate::models::Task>(
        "SELECT * FROM tasks WHERE task_type = 'download' ORDER BY created_at DESC LIMIT 50",
    )
    .fetch_all(&state.db_pool)
    .await?;

    Ok(Json(all_tasks))
}

/// GET /media/:id/files - List downloaded files for a media
pub async fn list_media_files_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<Json<Vec<crate::models::MediaFile>>, ApiError> {
    let files = db::media_files::get_files_by_media_id(&state.db_pool, id).await?;
    Ok(Json(files))
}
