use crate::{
    api::auth::extract_user_id,
    api::error::ApiError,
    api::media_ref::{find_media_id_by_tmdb, resolve_media_id, MediaReferenceInput},
    db,
    models::AddWatchlistPayload,
    AppState,
};
use axum::{
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    Json,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::Row;
use std::sync::Arc;
use uuid::Uuid;

fn get_user_id(headers: &HeaderMap) -> Uuid {
    extract_user_id(headers)
        .unwrap_or_else(|| Uuid::parse_str("00000000-0000-0000-0000-000000000001").unwrap())
}

#[derive(Debug, Deserialize)]
pub struct PaginationQuery {
    pub page: Option<i64>,
    pub per_page: Option<i64>,
}

#[derive(Debug, Serialize)]
pub struct WatchlistListItem {
    pub id: Uuid,
    pub tmdb_id: Option<i32>,
    pub media_type_wl: Option<String>,
    pub title: Option<String>,
    pub poster_url: Option<String>,
    pub added_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize)]
pub struct PaginatedWatchlistResponse {
    pub items: Vec<WatchlistListItem>,
    pub total: i64,
    pub page: i64,
    pub per_page: i64,
    pub total_pages: i64,
}

/// POST /watchlist - Add to watchlist
pub async fn add_to_watchlist_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Json(payload): Json<AddWatchlistPayload>,
) -> Result<StatusCode, ApiError> {
    let user_id = get_user_id(&headers);
    let media_id = resolve_media_id(
        &state.db_pool,
        MediaReferenceInput {
            media_id: payload.media_id,
            tmdb_id: payload.tmdb_id,
            media_type: payload.media_type,
            title: payload.title,
            year: payload.year,
            release_date: payload.release_date,
            overview: payload.overview,
            poster_url: payload.poster_url,
            backdrop_url: payload.backdrop_url,
            genres: None,
            vote_average: payload.vote_average,
        },
    )
    .await?;

    db::watchlist::add_to_watchlist(
        &state.db_pool,
        user_id,
        media_id,
        payload.auto_download,
        &payload.quality_min,
    )
    .await
    .map_err(ApiError::Database)?;
    Ok(StatusCode::CREATED)
}

/// DELETE /watchlist/:media_id - Remove from watchlist
pub async fn remove_from_watchlist_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Path(media_id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let user_id = get_user_id(&headers);
    db::watchlist::remove_from_watchlist(&state.db_pool, user_id, media_id)
        .await
        .map_err(ApiError::Database)?;
    Ok(StatusCode::NO_CONTENT)
}

/// DELETE /watchlist/:tmdb_id/:media_type - Remove from watchlist (TMDB compatibility)
pub async fn remove_from_watchlist_by_tmdb_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Path((tmdb_id, media_type)): Path<(i32, String)>,
) -> Result<StatusCode, ApiError> {
    let user_id = get_user_id(&headers);
    if let Some(media_id) = find_media_id_by_tmdb(&state.db_pool, tmdb_id, &media_type).await? {
        db::watchlist::remove_from_watchlist(&state.db_pool, user_id, media_id)
            .await
            .map_err(ApiError::Database)?;
    }
    Ok(StatusCode::NO_CONTENT)
}

/// GET /watchlist - List watchlist (paginated)
pub async fn list_watchlist_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Query(params): Query<PaginationQuery>,
) -> Result<Json<PaginatedWatchlistResponse>, ApiError> {
    let user_id = get_user_id(&headers);
    let page = params.page.unwrap_or(1).max(1);
    let per_page = params.per_page.unwrap_or(30).min(100);
    let offset = (page - 1) * per_page;

    let total = db::watchlist::count_watchlist(&state.db_pool, user_id)
        .await
        .map_err(ApiError::Database)?;
    let rows = sqlx::query(
        r#"
        SELECT
            w.id AS watchlist_id,
            w.added_at,
            m.tmdb_id,
            m.media_type AS media_type_wl,
            m.title,
            m.poster_url
        FROM watchlist w
        JOIN media m ON m.id = w.media_id
        WHERE w.user_id = $1
        ORDER BY w.added_at DESC
        LIMIT $2 OFFSET $3
        "#,
    )
    .bind(user_id)
    .bind(per_page)
    .bind(offset)
    .fetch_all(&state.db_pool)
    .await
    .map_err(ApiError::Database)?;

    let items: Vec<WatchlistListItem> = rows
        .into_iter()
        .map(|row| WatchlistListItem {
            id: row.get("watchlist_id"),
            tmdb_id: row.get("tmdb_id"),
            media_type_wl: row.get("media_type_wl"),
            title: row.get("title"),
            poster_url: row.get("poster_url"),
            added_at: row.get("added_at"),
        })
        .collect();

    let total_pages = if total == 0 {
        0
    } else {
        (total as f64 / per_page as f64).ceil() as i64
    };

    Ok(Json(PaginatedWatchlistResponse {
        items,
        total,
        page,
        per_page,
        total_pages,
    }))
}
