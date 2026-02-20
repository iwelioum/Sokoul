#![allow(dead_code)]
use chrono::{DateTime, Utc};
use sqlx::{FromRow, PgPool};
use uuid::Uuid;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, FromRow)]
pub struct TvChannel {
    pub id: Uuid,
    pub name: String,
    pub code: String,
    pub country: Option<String>,
    pub logo_url: Option<String>,
    pub category: Option<String>,
    pub is_free: bool,
    pub is_active: bool,
    pub stream_url: Option<String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, FromRow)]
pub struct TvProgram {
    pub id: Uuid,
    pub channel_id: Uuid,
    pub title: String,
    pub description: Option<String>,
    pub start_time: DateTime<Utc>,
    pub end_time: DateTime<Utc>,
    pub genre: Option<String>,
    pub image_url: Option<String>,
    pub rating: Option<f32>,
    pub external_id: Option<String>,
    pub created_at: DateTime<Utc>,
}

// Channels CRUD
pub async fn get_channel(pool: &PgPool, id: Uuid) -> Result<Option<TvChannel>, sqlx::Error> {
    sqlx::query_as::<_, TvChannel>(
        "SELECT id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at FROM tv_channels WHERE id = $1"
    )
    .bind(id)
    .fetch_optional(pool)
    .await
}

pub async fn get_channels(pool: &PgPool) -> Result<Vec<TvChannel>, sqlx::Error> {
    sqlx::query_as::<_, TvChannel>(
        "SELECT id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at FROM tv_channels WHERE is_active = true ORDER BY name"
    )
    .fetch_all(pool)
    .await
}

pub async fn get_channels_by_country(
    pool: &PgPool,
    country: &str,
) -> Result<Vec<TvChannel>, sqlx::Error> {
    sqlx::query_as::<_, TvChannel>(
        "SELECT id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at FROM tv_channels WHERE country = $1 AND is_active = true ORDER BY name"
    )
    .bind(country)
    .fetch_all(pool)
    .await
}

pub async fn get_channel_by_code(
    pool: &PgPool,
    code: &str,
) -> Result<Option<TvChannel>, sqlx::Error> {
    sqlx::query_as::<_, TvChannel>(
        "SELECT id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at FROM tv_channels WHERE code = $1"
    )
    .bind(code)
    .fetch_optional(pool)
    .await
}

#[allow(clippy::too_many_arguments)]
pub async fn create_channel(
    pool: &PgPool,
    name: &str,
    code: &str,
    country: Option<&str>,
    logo_url: Option<&str>,
    category: Option<&str>,
    is_free: bool,
    stream_url: Option<&str>,
) -> Result<TvChannel, sqlx::Error> {
    let id = Uuid::new_v4();
    let now = Utc::now();

    sqlx::query_as::<_, TvChannel>(
        "INSERT INTO tv_channels (id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, true, $8, $9)
         RETURNING id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at"
    )
    .bind(id)
    .bind(name)
    .bind(code)
    .bind(country)
    .bind(logo_url)
    .bind(category)
    .bind(is_free)
    .bind(stream_url)
    .bind(now)
    .fetch_one(pool)
    .await
}

#[allow(clippy::too_many_arguments)]
pub async fn upsert_channel(
    pool: &PgPool,
    name: &str,
    code: &str,
    country: Option<&str>,
    logo_url: Option<&str>,
    category: Option<&str>,
    is_free: bool,
    stream_url: Option<&str>,
) -> Result<TvChannel, sqlx::Error> {
    sqlx::query_as::<_, TvChannel>(
        "INSERT INTO tv_channels (id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at)
         VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6, true, $7, NOW())
         ON CONFLICT (code) DO UPDATE
         SET name = EXCLUDED.name,
             country = EXCLUDED.country,
             logo_url = EXCLUDED.logo_url,
             category = EXCLUDED.category,
             is_free = EXCLUDED.is_free,
             is_active = true,
             stream_url = EXCLUDED.stream_url
         RETURNING id, name, code, country, logo_url, category, is_free, is_active, stream_url, created_at"
    )
    .bind(name)
    .bind(code)
    .bind(country)
    .bind(logo_url)
    .bind(category)
    .bind(is_free)
    .bind(stream_url)
    .fetch_one(pool)
    .await
}

// Programs CRUD
pub async fn get_program(pool: &PgPool, id: Uuid) -> Result<Option<TvProgram>, sqlx::Error> {
    sqlx::query_as::<_, TvProgram>(
        "SELECT id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at FROM tv_programs WHERE id = $1"
    )
    .bind(id)
    .fetch_optional(pool)
    .await
}

pub async fn get_programs_for_channel(
    pool: &PgPool,
    channel_id: Uuid,
    limit: i64,
    offset: i64,
) -> Result<Vec<TvProgram>, sqlx::Error> {
    sqlx::query_as::<_, TvProgram>(
        "SELECT id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at
         FROM tv_programs WHERE channel_id = $1
         ORDER BY start_time DESC LIMIT $2 OFFSET $3"
    )
    .bind(channel_id)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await
}

pub async fn get_programs_for_channel_on_date(
    pool: &PgPool,
    channel_id: Uuid,
    date: &str,
) -> Result<Vec<TvProgram>, sqlx::Error> {
    sqlx::query_as::<_, TvProgram>(
        "SELECT id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at
         FROM tv_programs WHERE channel_id = $1 AND DATE(start_time) = $2::DATE
         ORDER BY start_time ASC"
    )
    .bind(channel_id)
    .bind(date)
    .fetch_all(pool)
    .await
}

pub async fn get_current_programs(pool: &PgPool) -> Result<Vec<TvProgram>, sqlx::Error> {
    sqlx::query_as::<_, TvProgram>(
        "SELECT id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at
         FROM tv_programs WHERE start_time <= NOW() AND end_time > NOW()
         ORDER BY channel_id"
    )
    .fetch_all(pool)
    .await
}

pub async fn search_programs(
    pool: &PgPool,
    query: &str,
    limit: i64,
) -> Result<Vec<TvProgram>, sqlx::Error> {
    let search_term = format!("%{}%", query);
    sqlx::query_as::<_, TvProgram>(
        "SELECT id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at
         FROM tv_programs WHERE title ILIKE $1 OR description ILIKE $1
         ORDER BY start_time DESC LIMIT $2"
    )
    .bind(search_term)
    .bind(limit)
    .fetch_all(pool)
    .await
}

#[allow(clippy::too_many_arguments)]
pub async fn create_program(
    pool: &PgPool,
    channel_id: Uuid,
    title: &str,
    start_time: DateTime<Utc>,
    end_time: DateTime<Utc>,
    description: Option<&str>,
    genre: Option<&str>,
    image_url: Option<&str>,
    rating: Option<f32>,
    external_id: Option<&str>,
) -> Result<TvProgram, sqlx::Error> {
    let id = Uuid::new_v4();
    let now = Utc::now();

    sqlx::query_as::<_, TvProgram>(
        "INSERT INTO tv_programs (id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
         RETURNING id, channel_id, title, description, start_time, end_time, genre, image_url, rating, external_id, created_at"
    )
    .bind(id)
    .bind(channel_id)
    .bind(title)
    .bind(description)
    .bind(start_time)
    .bind(end_time)
    .bind(genre)
    .bind(image_url)
    .bind(rating)
    .bind(external_id)
    .bind(now)
    .fetch_one(pool)
    .await
}

pub async fn clear_programs_for_channel(
    pool: &PgPool,
    channel_id: Uuid,
) -> Result<u64, sqlx::Error> {
    sqlx::query("DELETE FROM tv_programs WHERE channel_id = $1")
        .bind(channel_id)
        .execute(pool)
        .await
        .map(|result| result.rows_affected())
}

pub async fn count_programs_for_channel(
    pool: &PgPool,
    channel_id: Uuid,
) -> Result<i64, sqlx::Error> {
    sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM tv_programs WHERE channel_id = $1")
        .bind(channel_id)
        .fetch_one(pool)
        .await
}
