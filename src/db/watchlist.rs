use sqlx::{PgPool, Row};
use uuid::Uuid;
use serde::{Serialize, Deserialize};
use chrono::{DateTime, Utc};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchlistItem {
    pub id: Uuid,
    pub media_id: Uuid,
    pub added_at: Option<DateTime<Utc>>,
}

pub async fn add_to_watchlist(
    pool: &PgPool,
    media_id: Uuid,
    auto_download: bool,
    quality_min: &str,
) -> Result<WatchlistItem, sqlx::Error> {
    let row = sqlx::query(
        r#"
        INSERT INTO watchlist (media_id, auto_download, quality_min)
        VALUES ($1, $2, $3)
        ON CONFLICT (media_id) DO UPDATE SET
            auto_download = EXCLUDED.auto_download,
            quality_min = EXCLUDED.quality_min
        RETURNING id, media_id, added_at
        "#,
    )
    .bind(media_id)
    .bind(auto_download)
    .bind(quality_min)
    .fetch_one(pool)
    .await?;

    Ok(WatchlistItem {
        id: row.get("id"),
        media_id: row.get("media_id"),
        added_at: row.get("added_at"),
    })
}

pub async fn remove_from_watchlist(
    pool: &PgPool,
    media_id: Uuid,
) -> Result<u64, sqlx::Error> {
    sqlx::query("DELETE FROM watchlist WHERE media_id = $1")
        .bind(media_id)
        .execute(pool)
        .await
        .map(|r| r.rows_affected())
}

pub async fn list_watchlist(
    pool: &PgPool,
    limit: i64,
    offset: i64,
) -> Result<Vec<WatchlistItem>, sqlx::Error> {
    let rows = sqlx::query(
        r#"
        SELECT id, media_id, added_at
        FROM watchlist
        ORDER BY added_at DESC
        LIMIT $1 OFFSET $2
        "#,
    )
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await?;

    Ok(rows.into_iter().map(|row| WatchlistItem {
        id: row.get("id"),
        media_id: row.get("media_id"),
        added_at: row.get("added_at"),
    }).collect())
}

pub async fn is_in_watchlist(
    pool: &PgPool,
    media_id: Uuid,
) -> Result<bool, sqlx::Error> {
    let row = sqlx::query(
        "SELECT COUNT(*) as count FROM watchlist WHERE media_id = $1"
    )
    .bind(media_id)
    .fetch_one(pool)
    .await?;

    let count: i64 = row.get("count");
    Ok(count > 0)
}

pub async fn count_watchlist(
    pool: &PgPool,
) -> Result<i64, sqlx::Error> {
    let row = sqlx::query("SELECT COUNT(*) as count FROM watchlist")
        .fetch_one(pool)
        .await?;

    Ok(row.get("count"))
}
