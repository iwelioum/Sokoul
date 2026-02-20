use crate::{
    api::auth::extract_user_id,
    api::error::ApiError,
    api::media_ref::{find_media_id_by_tmdb, resolve_media_id, MediaReferenceInput},
    db,
    models::{AddFavoritePayload, LibraryStatus},
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

// Default user UUID for API key auth (backward compatible)
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
pub struct FavoriteListItem {
    pub id: Uuid,
    pub tmdb_id: i32,
    pub media_type: String,
    pub title: String,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub vote_average: Option<f64>,
    pub release_date: Option<String>,
    pub overview: Option<String>,
    pub added_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Serialize)]
pub struct PaginatedFavoritesResponse {
    pub items: Vec<FavoriteListItem>,
    pub total: i64,
    pub page: i64,
    pub per_page: i64,
    pub total_pages: i64,
}

/// POST /library - Add to favorites
pub async fn add_to_library_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Json(payload): Json<AddFavoritePayload>,
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

    db::favorites::add_favorite(&state.db_pool, user_id, media_id)
        .await
        .map_err(ApiError::Database)?;
    Ok(StatusCode::CREATED)
}

/// DELETE /library/:media_id - Remove from favorites
pub async fn remove_from_library_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Path(media_id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let user_id = get_user_id(&headers);
    db::favorites::remove_favorite(&state.db_pool, user_id, media_id)
        .await
        .map_err(ApiError::Database)?;
    Ok(StatusCode::NO_CONTENT)
}

/// DELETE /library/:tmdb_id/:media_type - Remove from favorites (TMDB compatibility)
pub async fn remove_from_library_by_tmdb_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Path((tmdb_id, media_type)): Path<(i32, String)>,
) -> Result<StatusCode, ApiError> {
    let user_id = get_user_id(&headers);
    if let Some(media_id) = find_media_id_by_tmdb(&state.db_pool, tmdb_id, &media_type).await? {
        db::favorites::remove_favorite(&state.db_pool, user_id, media_id)
            .await
            .map_err(ApiError::Database)?;
    }
    Ok(StatusCode::NO_CONTENT)
}

/// GET /library - List favorites (paginated)
pub async fn list_library_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Query(params): Query<PaginationQuery>,
) -> Result<Json<PaginatedFavoritesResponse>, ApiError> {
    let user_id = get_user_id(&headers);
    let page = params.page.unwrap_or(1).max(1);
    let per_page = params.per_page.unwrap_or(30).min(100);
    let offset = (page - 1) * per_page;

    let total = db::favorites::count_favorites(&state.db_pool, user_id)
        .await
        .map_err(ApiError::Database)?;
    let rows = sqlx::query(
        r#"
        SELECT
            f.id AS favorite_id,
            f.added_at,
            COALESCE(m.tmdb_id, 0) AS tmdb_id,
            m.media_type,
            m.title,
            m.poster_url,
            m.backdrop_url,
            m.rating::float8 AS vote_average,
            m.year,
            m.overview
        FROM favorites f
        JOIN media m ON m.id = f.media_id
        WHERE f.user_id = $1
        ORDER BY f.added_at DESC
        LIMIT $2 OFFSET $3
        "#,
    )
    .bind(user_id)
    .bind(per_page)
    .bind(offset)
    .fetch_all(&state.db_pool)
    .await
    .map_err(ApiError::Database)?;

    let items: Vec<FavoriteListItem> = rows
        .into_iter()
        .map(|row| {
            let year: Option<i32> = row.get("year");
            FavoriteListItem {
                id: row.get("favorite_id"),
                tmdb_id: row.get("tmdb_id"),
                media_type: row.get("media_type"),
                title: row.get("title"),
                poster_url: row.get("poster_url"),
                backdrop_url: row.get("backdrop_url"),
                vote_average: row.get("vote_average"),
                release_date: year.map(|y| format!("{:04}-01-01", y)),
                overview: row.get("overview"),
                added_at: row.get("added_at"),
            }
        })
        .collect();

    let total_pages = if total == 0 {
        0
    } else {
        (total as f64 / per_page as f64).ceil() as i64
    };

    Ok(Json(PaginatedFavoritesResponse {
        items,
        total,
        page,
        per_page,
        total_pages,
    }))
}

/// GET /library/status/:tmdb_id/:media_type
pub async fn library_status_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Path((tmdb_id, media_type)): Path<(i32, String)>,
) -> Result<Json<LibraryStatus>, ApiError> {
    let user_id = get_user_id(&headers);

    let Some(media_id) = find_media_id_by_tmdb(&state.db_pool, tmdb_id, &media_type).await? else {
        return Ok(Json(LibraryStatus {
            in_library: false,
            in_watchlist: false,
            watch_progress: None,
            completed: false,
        }));
    };

    let in_library: bool = sqlx::query_scalar(
        "SELECT EXISTS (SELECT 1 FROM favorites WHERE user_id = $1 AND media_id = $2)",
    )
    .bind(user_id)
    .bind(media_id)
    .fetch_one(&state.db_pool)
    .await
    .map_err(ApiError::Database)?;

    let in_watchlist: bool = sqlx::query_scalar(
        "SELECT EXISTS (SELECT 1 FROM watchlist WHERE user_id = $1 AND media_id = $2)",
    )
    .bind(user_id)
    .bind(media_id)
    .fetch_one(&state.db_pool)
    .await
    .map_err(ApiError::Database)?;

    let progress_row = sqlx::query(
        r#"
        SELECT progress_seconds, completed
        FROM watch_history
        WHERE user_id = $1 AND media_id = $2
        "#,
    )
    .bind(user_id)
    .bind(media_id)
    .fetch_optional(&state.db_pool)
    .await
    .map_err(ApiError::Database)?;

    let (watch_progress, completed) = if let Some(row) = progress_row {
        (
            Some(row.get::<i32, _>("progress_seconds")),
            row.get::<bool, _>("completed"),
        )
    } else {
        (None, false)
    };

    Ok(Json(LibraryStatus {
        in_library,
        in_watchlist,
        watch_progress,
        completed,
    }))
}
