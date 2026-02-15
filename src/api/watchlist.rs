use crate::{api::error::ApiError, db, models::{AddWatchlistPayload, WatchlistEntry, PaginatedWatchlist}, AppState};
use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    Json,
};
use serde::Deserialize;
use std::sync::Arc;
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct PaginationQuery {
    pub page: Option<i64>,
    pub per_page: Option<i64>,
}

/// POST /watchlist - Add to watchlist
pub async fn add_to_watchlist_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<AddWatchlistPayload>,
) -> Result<StatusCode, ApiError> {
    db::watchlist::add_to_watchlist(
        &state.db_pool,
        payload.media_id,
        payload.auto_download,
        &payload.quality_min,
    )
    .await
    .map_err(|e| ApiError::Database(e))?;
    Ok(StatusCode::CREATED)
}

/// DELETE /watchlist/:media_id - Remove from watchlist
pub async fn remove_from_watchlist_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    db::watchlist::remove_from_watchlist(&state.db_pool, media_id)
        .await
        .map_err(|e| ApiError::Database(e))?;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /watchlist - List watchlist (paginated)
pub async fn list_watchlist_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PaginationQuery>,
) -> Result<Json<PaginatedWatchlist>, ApiError> {
    let page = params.page.unwrap_or(1).max(1);
    let per_page = params.per_page.unwrap_or(30).min(100);
    let offset = (page - 1) * per_page;

    let total = db::watchlist::count_watchlist(&state.db_pool)
        .await
        .map_err(|e| ApiError::Database(e))?;
    let items = db::watchlist::list_watchlist(&state.db_pool, per_page, offset)
        .await
        .map_err(|e| ApiError::Database(e))?;
    let total_pages = (total as f64 / per_page as f64).ceil() as i64;

    // Convert to API models
    let models_items: Vec<WatchlistEntry> = items
        .into_iter()
        .map(|item| WatchlistEntry {
            id: item.id,
            media_id: item.media_id,
            added_at: item.added_at.unwrap_or_else(chrono::Utc::now),
        })
        .collect();

    Ok(Json(PaginatedWatchlist {
        items: models_items,
        total,
        page,
        per_page,
        total_pages,
    }))
}
