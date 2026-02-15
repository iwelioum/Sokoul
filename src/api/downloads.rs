use crate::{
    api::error::ApiError,
    db,
    events::{self, DownloadRequestedPayload},
    AppState,
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
        .ok_or_else(|| ApiError::NotFound("Résultat de recherche non trouvé".to_string()))?;

    let magnet_or_url = result
        .magnet_link
        .clone()
        .or_else(|| result.url.clone())
        .ok_or_else(|| ApiError::InvalidInput("Pas de magnet/URL pour ce résultat".to_string()))?;

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
            "message": "Téléchargement lancé.",
            "media_id": payload.media_id,
            "search_result_id": payload.search_result_id,
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
