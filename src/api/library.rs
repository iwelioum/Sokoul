use crate::{api::error::ApiError, db, models::{AddFavoritePayload, LibraryStatus, PaginatedFavorites}, AppState};
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

/// POST /library - Add to favorites
pub async fn add_to_library_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<AddFavoritePayload>,
) -> Result<StatusCode, ApiError> {
    db::favorites::add_favorite(&state.db_pool, payload.media_id)
        .await
        .map_err(|e| ApiError::Database(e))?;
    Ok(StatusCode::CREATED)
}

/// DELETE /library/:media_id - Remove from favorites
pub async fn remove_from_library_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    db::favorites::remove_favorite(&state.db_pool, media_id)
        .await
        .map_err(|e| ApiError::Database(e))?;
    Ok(StatusCode::NO_CONTENT)
}

/// GET /library - List favorites (paginated)
pub async fn list_library_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PaginationQuery>,
) -> Result<Json<PaginatedFavorites>, ApiError> {
    let page = params.page.unwrap_or(1).max(1);
    let per_page = params.per_page.unwrap_or(30).min(100);
    let offset = (page - 1) * per_page;

    let total = db::favorites::count_favorites(&state.db_pool)
        .await
        .map_err(|e| ApiError::Database(e))?;
    let items = db::favorites::list_favorites(&state.db_pool, per_page, offset)
        .await
        .map_err(|e| ApiError::Database(e))?;
    let total_pages = (total as f64 / per_page as f64).ceil() as i64;

    // Convert db favorites to API models
    let models_items: Vec<crate::models::Favorite> = items
        .into_iter()
        .map(|fav| crate::models::Favorite {
            id: fav.id,
            media_id: fav.media_id,
            added_at: fav.added_at.unwrap_or_else(chrono::Utc::now),
        })
        .collect();

    Ok(Json(PaginatedFavorites {
        items: models_items,
        total,
        page,
        per_page,
        total_pages,
    }))
}

/// GET /library/status/:media_id - Get library status for a media
pub async fn library_status_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
) -> Result<Json<LibraryStatus>, ApiError> {
    let in_library = db::favorites::is_favorite(&state.db_pool, media_id)
        .await
        .map_err(|e| ApiError::Database(e))?;
    let in_watchlist = db::watchlist::is_in_watchlist(&state.db_pool, media_id)
        .await
        .map_err(|e| ApiError::Database(e))?;
    let watch = db::watch_history::get_watch_progress(&state.db_pool, media_id)
        .await
        .map_err(|e| ApiError::Database(e))?;

    let (watch_progress, completed) = match watch {
        Some(entry) => (Some(entry.progress_seconds), entry.completed),
        None => (None, false),
    };

    Ok(Json(LibraryStatus {
        in_library,
        in_watchlist,
        watch_progress,
        completed,
    }))
}
