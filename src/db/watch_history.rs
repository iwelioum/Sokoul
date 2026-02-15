use sqlx::{PgPool, Row};
use uuid::Uuid;
use serde::{Serialize, Deserialize};
use chrono::{DateTime, Utc};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchHistoryItem {
    pub id: Uuid,
    pub media_id: Uuid,
    pub progress_seconds: i32,
    pub completed: bool,
    pub last_watched_at: Option<DateTime<Utc>>,
}

pub async fn update_progress(
    pool: &PgPool,
    media_id: Uuid,
    progress_seconds: i32,
    completed: bool,
) -> Result<WatchHistoryItem, sqlx::Error> {
    let row = sqlx::query(
        r#"
        INSERT INTO watch_history (media_id, progress_seconds, completed, last_watched_at)
        VALUES ($1, $2, $3, NOW())
        ON CONFLICT (media_id) DO UPDATE SET
            progress_seconds = EXCLUDED.progress_seconds,
            completed = EXCLUDED.completed,
            last_watched_at = NOW()
        RETURNING id, media_id, progress_seconds, completed, last_watched_at
        "#,
    )
    .bind(media_id)
    .bind(progress_seconds)
    .bind(completed)
    .fetch_one(pool)
    .await?;

    Ok(WatchHistoryItem {
        id: row.get("id"),
        media_id: row.get("media_id"),
        progress_seconds: row.get("progress_seconds"),
        completed: row.get("completed"),
        last_watched_at: row.get("last_watched_at"),
    })
}

pub async fn get_continue_watching(
    pool: &PgPool,
    limit: i64,
) -> Result<Vec<WatchHistoryItem>, sqlx::Error> {
    let rows = sqlx::query(
        r#"
        SELECT id, media_id, progress_seconds, completed, last_watched_at
        FROM watch_history
        WHERE completed = FALSE
        ORDER BY last_watched_at DESC
        LIMIT $1
        "#,
    )
    .bind(limit)
    .fetch_all(pool)
    .await?;

    Ok(rows.into_iter().map(|row| WatchHistoryItem {
        id: row.get("id"),
        media_id: row.get("media_id"),
        progress_seconds: row.get("progress_seconds"),
        completed: row.get("completed"),
        last_watched_at: row.get("last_watched_at"),
    }).collect())
}

pub async fn get_watch_progress(
    pool: &PgPool,
    media_id: Uuid,
) -> Result<Option<WatchHistoryItem>, sqlx::Error> {
    let row = sqlx::query(
        r#"
        SELECT id, media_id, progress_seconds, completed, last_watched_at
        FROM watch_history
        WHERE media_id = $1
        "#,
    )
    .bind(media_id)
    .fetch_optional(pool)
    .await?;

    Ok(row.map(|r| WatchHistoryItem {
        id: r.get("id"),
        media_id: r.get("media_id"),
        progress_seconds: r.get("progress_seconds"),
        completed: r.get("completed"),
        last_watched_at: r.get("last_watched_at"),
    }))
}
