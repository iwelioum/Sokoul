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

/// Redis key prefix for watch progress heartbeats
const REDIS_WH_PREFIX: &str = "wh:progress";

/// POST /watch-history - Buffer watch progress in Redis (write-behind pattern)
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

    let completed = payload.completed;

    // Write to Redis (fast path — avoids DB write on every heartbeat)
    let redis_key = format!("{}:{}:{}", REDIS_WH_PREFIX, user_id, media_id);
    let value = serde_json::json!({
        "user_id": user_id.to_string(),
        "media_id": media_id.to_string(),
        "progress_seconds": progress_seconds,
        "completed": completed,
    })
    .to_string();

    if let Ok(mut conn) = state.redis_client.get_multiplexed_async_connection().await {
        // SET with 30min TTL — if user stops watching, entry expires
        let _: Result<(), _> =
            redis::AsyncCommands::set_ex(&mut conn, &redis_key, &value, 1800).await;
        // Track this key in a set for the flush worker to iterate
        let _: Result<(), _> =
            redis::AsyncCommands::sadd(&mut conn, "wh:dirty_keys", &redis_key).await;
    } else {
        // Redis unavailable — fall back to direct DB write
        tracing::warn!("Redis unavailable for watch progress, falling back to DB");
        db::watch_history::update_progress(
            &state.db_pool,
            user_id,
            media_id,
            progress_seconds,
            completed,
        )
        .await
        .map_err(ApiError::Database)?;
    }

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

/// Flush all buffered watch progress from Redis to PostgreSQL.
/// Called periodically by the sentinel worker.
pub async fn flush_watch_progress(
    redis_client: &redis::Client,
    db_pool: &sqlx::PgPool,
) -> anyhow::Result<usize> {
    let mut conn = redis_client.get_multiplexed_async_connection().await?;

    // Pop all dirty keys atomically
    let keys: Vec<String> = redis::AsyncCommands::smembers(&mut conn, "wh:dirty_keys").await?;
    if keys.is_empty() {
        return Ok(0);
    }

    let mut flushed = 0usize;

    for key in &keys {
        let value: Option<String> = redis::AsyncCommands::get(&mut conn, key).await?;
        let Some(json_str) = value else { continue };

        #[derive(Deserialize)]
        struct Entry {
            user_id: String,
            media_id: String,
            progress_seconds: i32,
            completed: bool,
        }

        let entry: Entry = match serde_json::from_str(&json_str) {
            Ok(e) => e,
            Err(_) => continue,
        };

        let user_id = match Uuid::parse_str(&entry.user_id) {
            Ok(id) => id,
            Err(_) => continue,
        };
        let media_id = match Uuid::parse_str(&entry.media_id) {
            Ok(id) => id,
            Err(_) => continue,
        };

        if let Err(e) = db::watch_history::update_progress(
            db_pool,
            user_id,
            media_id,
            entry.progress_seconds,
            entry.completed,
        )
        .await
        {
            tracing::error!("Failed to flush watch progress {}: {}", key, e);
            continue;
        }

        // Remove from dirty set and delete the key
        let _: Result<(), _> = redis::AsyncCommands::srem(&mut conn, "wh:dirty_keys", key).await;
        let _: Result<(), _> = redis::AsyncCommands::del(&mut conn, key).await;
        flushed += 1;
    }

    if flushed > 0 {
        tracing::info!(
            "Flushed {} watch progress entries from Redis to DB",
            flushed
        );
    }

    Ok(flushed)
}
