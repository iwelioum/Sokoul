use crate::{
    api::error::ApiError,
    db,
    models::{UpdateWatchProgressPayload, WatchHistoryEntry},
    AppState,
};
use axum::{
    extract::{Query, State},
    http::StatusCode,
    Json,
};
use serde::Deserialize;
use std::sync::Arc;

#[derive(Debug, Deserialize)]
pub struct ContinueQuery {
    pub limit: Option<i64>,
}

/// POST /watch-history - Update watch progress
pub async fn update_watch_progress_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<UpdateWatchProgressPayload>,
) -> Result<StatusCode, ApiError> {
    db::watch_history::update_progress(
        &state.db_pool,
        payload.media_id,
        payload.progress_seconds,
        payload.completed,
    )
    .await
    .map_err(|e| ApiError::Database(e))?;
    Ok(StatusCode::CREATED)
}

/// GET /watch-history/continue - Get "continue watching" items
pub async fn continue_watching_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<ContinueQuery>,
) -> Result<Json<Vec<WatchHistoryEntry>>, ApiError> {
    let limit = params.limit.unwrap_or(20).min(50);
    let items = db::watch_history::get_continue_watching(&state.db_pool, limit)
        .await
        .map_err(|e| ApiError::Database(e))?;

    let models_items: Vec<WatchHistoryEntry> = items
        .into_iter()
        .map(|item| WatchHistoryEntry {
            id: item.id,
            media_id: item.media_id,
            progress_seconds: item.progress_seconds,
            completed: item.completed,
            last_watched_at: item.last_watched_at.unwrap_or_else(chrono::Utc::now),
        })
        .collect();

    Ok(Json(models_items))
}
