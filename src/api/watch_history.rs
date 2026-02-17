use crate::{
    api::auth::extract_user_id,
    api::error::ApiError,
    api::media_ref::{resolve_media_id, MediaReferenceInput},
    db,
    models::UpdateWatchProgressPayload,
    AppState,
};
use axum::{
    extract::{Query, State},
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
pub struct ContinueQuery {
    pub limit: Option<i64>,
}

#[derive(Debug, Serialize)]
pub struct ContinueWatchingItem {
    pub id: Uuid,
    pub tmdb_id: Option<i32>,
    pub media_type_wh: Option<String>,
    pub title: Option<String>,
    pub poster_url: Option<String>,
    pub progress: Option<f64>,
    pub completed: Option<bool>,
    pub watched_at: Option<DateTime<Utc>>,
}

fn normalize_progress_to_percent(progress: f64) -> i32 {
    if progress <= 1.0 {
        (progress.max(0.0) * 100.0).round() as i32
    } else if progress <= 100.0 {
        progress.round() as i32
    } else {
        100
    }
}

/// POST /watch-history - Update watch progress
pub async fn update_watch_progress_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Json(payload): Json<UpdateWatchProgressPayload>,
) -> Result<StatusCode, ApiError> {
    let user_id = get_user_id(&headers);
    let media_id = resolve_media_id(
        &state.db_pool,
        MediaReferenceInput {
            media_id: payload.media_id,
            tmdb_id: payload.tmdb_id,
            media_type: payload.media_type,
            title: payload.title,
            year: None,
            release_date: None,
            overview: None,
            poster_url: payload.poster_url,
            backdrop_url: None,
            genres: None,
            vote_average: None,
        },
    )
    .await?;

    let progress_seconds = payload
        .progress_seconds
        .unwrap_or_else(|| {
            payload
                .progress
                .map(normalize_progress_to_percent)
                .unwrap_or(0)
        })
        .max(0);

    db::watch_history::update_progress(
        &state.db_pool,
        user_id,
        media_id,
        progress_seconds,
        payload.completed,
    )
    .await
    .map_err(|e| ApiError::Database(e))?;
    Ok(StatusCode::CREATED)
}

/// GET /watch-history/continue - Get "continue watching" items
pub async fn continue_watching_handler(
    State(state): State<Arc<AppState>>,
    headers: HeaderMap,
    Query(params): Query<ContinueQuery>,
) -> Result<Json<Vec<ContinueWatchingItem>>, ApiError> {
    let user_id = get_user_id(&headers);
    let limit = params.limit.unwrap_or(20).min(50);

    let rows = sqlx::query(
        r#"
        SELECT
            wh.id,
            wh.progress_seconds,
            wh.completed,
            wh.watched_at,
            m.tmdb_id,
            m.media_type AS media_type_wh,
            m.title,
            m.poster_url
        FROM watch_history wh
        JOIN media m ON m.id = wh.media_id
        WHERE wh.user_id = $1 AND wh.completed = FALSE
        ORDER BY wh.watched_at DESC
        LIMIT $2
        "#,
    )
    .bind(user_id)
    .bind(limit)
    .fetch_all(&state.db_pool)
    .await
    .map_err(ApiError::Database)?;

    let items: Vec<ContinueWatchingItem> = rows
        .into_iter()
        .map(|row| {
            let progress_seconds: i32 = row.get("progress_seconds");
            ContinueWatchingItem {
                id: row.get("id"),
                tmdb_id: row.get("tmdb_id"),
                media_type_wh: row.get("media_type_wh"),
                title: row.get("title"),
                poster_url: row.get("poster_url"),
                progress: Some((progress_seconds as f64 / 100.0).clamp(0.0, 1.0)),
                completed: Some(row.get("completed")),
                watched_at: row.get("watched_at"),
            }
        })
        .collect();

    Ok(Json(items))
}
