use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::{PgPool, Row};
use uuid::Uuid;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Favorite {
    pub id: Uuid,
    pub user_id: Uuid,
    pub media_id: Uuid,
    pub added_at: Option<DateTime<Utc>>,
}

pub async fn add_favorite(
    pool: &PgPool,
    user_id: Uuid,
    media_id: Uuid,
) -> Result<Favorite, sqlx::Error> {
    let row = sqlx::query(
        r#"
        INSERT INTO favorites (user_id, media_id)
        VALUES ($1, $2)
        ON CONFLICT (user_id, media_id) DO UPDATE SET
            added_at = NOW()
        RETURNING id, user_id, media_id, added_at
        "#,
    )
    .bind(user_id)
    .bind(media_id)
    .fetch_one(pool)
    .await?;

    Ok(Favorite {
        id: row.get("id"),
        user_id: row.get("user_id"),
        media_id: row.get("media_id"),
        added_at: row.get("added_at"),
    })
}

pub async fn remove_favorite(
    pool: &PgPool,
    user_id: Uuid,
    media_id: Uuid,
) -> Result<u64, sqlx::Error> {
    sqlx::query(
        r#"
        DELETE FROM favorites
        WHERE user_id = $1 AND media_id = $2
        "#,
    )
    .bind(user_id)
    .bind(media_id)
    .execute(pool)
    .await
    .map(|r| r.rows_affected())
}

#[allow(dead_code)]
pub async fn list_favorites(
    pool: &PgPool,
    user_id: Uuid,
    limit: i64,
    offset: i64,
) -> Result<Vec<Favorite>, sqlx::Error> {
    let rows = sqlx::query(
        r#"
        SELECT id, user_id, media_id, added_at
        FROM favorites
        WHERE user_id = $1
        ORDER BY added_at DESC
        LIMIT $2 OFFSET $3
        "#,
    )
    .bind(user_id)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await?;

    Ok(rows
        .into_iter()
        .map(|row| Favorite {
            id: row.get("id"),
            user_id: row.get("user_id"),
            media_id: row.get("media_id"),
            added_at: row.get("added_at"),
        })
        .collect())
}

#[allow(dead_code)]
pub async fn is_favorite(
    pool: &PgPool,
    user_id: Uuid,
    media_id: Uuid,
) -> Result<bool, sqlx::Error> {
    let row = sqlx::query(
        r#"
        SELECT COUNT(*) as count
        FROM favorites
        WHERE user_id = $1 AND media_id = $2
        "#,
    )
    .bind(user_id)
    .bind(media_id)
    .fetch_one(pool)
    .await?;

    let count: i64 = row.get("count");
    Ok(count > 0)
}

pub async fn count_favorites(pool: &PgPool, user_id: Uuid) -> Result<i64, sqlx::Error> {
    let row = sqlx::query("SELECT COUNT(*) as count FROM favorites WHERE user_id = $1")
        .bind(user_id)
        .fetch_one(pool)
        .await?;

    Ok(row.get("count"))
}
