use crate::models::{CreateMediaPayload, Media, UpdateMediaPayload};
use sqlx::PgPool;
use uuid::Uuid;

pub async fn create_media(
    pool: &PgPool,
    payload: &CreateMediaPayload,
) -> Result<Media, sqlx::Error> {
    let id = Uuid::new_v4();

    let media = sqlx::query_as::<_, Media>(
        r#"
        INSERT INTO media (id, title, media_type, tmdb_id, year, overview, poster_url, genres)
        VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
        ON CONFLICT (tmdb_id, media_type) DO UPDATE
            SET title = EXCLUDED.title,
                overview = COALESCE(EXCLUDED.overview, media.overview),
                poster_url = COALESCE(EXCLUDED.poster_url, media.poster_url),
                genres = COALESCE(EXCLUDED.genres, media.genres),
                updated_at = NOW()
        RETURNING *
        "#,
    )
    .bind(id)
    .bind(&payload.title)
    .bind(&payload.media_type)
    .bind(payload.tmdb_id)
    .bind(payload.year)
    .bind(&payload.overview)
    .bind(&payload.poster_url)
    .bind(&payload.genres)
    .fetch_one(pool)
    .await?;

    Ok(media)
}

pub async fn get_media_by_id(pool: &PgPool, id: Uuid) -> Result<Media, sqlx::Error> {
    let media = sqlx::query_as::<_, Media>("SELECT * FROM media WHERE id = $1")
        .bind(id)
        .fetch_one(pool)
        .await?;

    Ok(media)
}

pub async fn list_media(pool: &PgPool, limit: i64, offset: i64) -> Result<Vec<Media>, sqlx::Error> {
    let media_list = sqlx::query_as::<_, Media>(
        "SELECT * FROM media ORDER BY updated_at DESC LIMIT $1 OFFSET $2",
    )
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await?;

    Ok(media_list)
}

pub async fn list_media_by_type(
    pool: &PgPool,
    media_type: &str,
    limit: i64,
    offset: i64,
) -> Result<Vec<Media>, sqlx::Error> {
    let media_list = sqlx::query_as::<_, Media>(
        "SELECT * FROM media WHERE media_type = $1 ORDER BY updated_at DESC LIMIT $2 OFFSET $3",
    )
    .bind(media_type)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await?;

    Ok(media_list)
}

pub async fn get_episodes(pool: &PgPool, series_id: Uuid) -> Result<Vec<Media>, sqlx::Error> {
    let episodes = sqlx::query_as::<_, Media>(
        "SELECT * FROM media WHERE parent_id = $1 ORDER BY season_number ASC, episode_number ASC",
    )
    .bind(series_id)
    .fetch_all(pool)
    .await?;

    Ok(episodes)
}

pub async fn count_media(pool: &PgPool) -> Result<i64, sqlx::Error> {
    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM media")
        .fetch_one(pool)
        .await?;
    Ok(count.0)
}

pub async fn count_media_by_type(pool: &PgPool, media_type: &str) -> Result<i64, sqlx::Error> {
    let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM media WHERE media_type = $1")
        .bind(media_type)
        .fetch_one(pool)
        .await?;
    Ok(count.0)
}

pub async fn search_media(
    pool: &PgPool,
    query: &str,
    limit: i64,
) -> Result<Vec<Media>, sqlx::Error> {
    let media_list = sqlx::query_as::<_, Media>(
        r#"
        SELECT * FROM media
        WHERE title ILIKE $1 OR original_title ILIKE $1
        ORDER BY similarity(title, $2) DESC
        LIMIT $3
        "#,
    )
    .bind(format!("%{}%", query))
    .bind(query)
    .bind(limit)
    .fetch_all(pool)
    .await?;

    Ok(media_list)
}

pub async fn update_media_by_id(
    pool: &PgPool,
    id: Uuid,
    payload: &UpdateMediaPayload,
) -> Result<Media, sqlx::Error> {
    let current = get_media_by_id(pool, id).await?;

    let title = payload.title.as_deref().unwrap_or(&current.title);
    let year = payload.year.or(current.year);
    let overview = payload.overview.as_deref().or(current.overview.as_deref());
    let poster_url = payload
        .poster_url
        .as_deref()
        .or(current.poster_url.as_deref());
    let backdrop_url = payload
        .backdrop_url
        .as_deref()
        .or(current.backdrop_url.as_deref());
    let status = payload.status.as_deref().or(current.status.as_deref());
    let genres = payload.genres.as_ref().or(current.genres.as_ref());

    let media = sqlx::query_as::<_, Media>(
        r#"
        UPDATE media
        SET title = $1, year = $2, overview = $3, poster_url = $4,
            backdrop_url = $5, status = $6, genres = $7, updated_at = NOW()
        WHERE id = $8
        RETURNING *
        "#,
    )
    .bind(title)
    .bind(year)
    .bind(overview)
    .bind(poster_url)
    .bind(backdrop_url)
    .bind(status)
    .bind(genres)
    .bind(id)
    .fetch_one(pool)
    .await?;

    Ok(media)
}

pub async fn delete_media_by_id(pool: &PgPool, id: Uuid) -> Result<u64, sqlx::Error> {
    let result = sqlx::query("DELETE FROM media WHERE id = $1")
        .bind(id)
        .execute(pool)
        .await?;

    Ok(result.rows_affected())
}
