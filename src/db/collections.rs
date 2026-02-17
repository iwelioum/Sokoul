#![allow(dead_code)]
use sqlx::{FromRow, PgPool};
use uuid::Uuid;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, FromRow)]
pub struct Collection {
    pub id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub category: String,
    pub api_source: String,
    pub cover_image_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize, FromRow)]
pub struct CollectionItem {
    pub id: Uuid,
    pub collection_id: Uuid,
    pub external_id: Option<String>,
    pub name: String,
    pub description: Option<String>,
    pub image_url: Option<String>,
    pub item_type: Option<String>,
    pub data_json: Option<serde_json::Value>,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

pub async fn get_collection(pool: &PgPool, id: Uuid) -> Result<Option<Collection>, sqlx::Error> {
    sqlx::query_as::<_, Collection>(
        "SELECT id, name, description, category, api_source, cover_image_url, backdrop_url, created_at FROM collections WHERE id = $1"
    )
    .bind(id)
    .fetch_optional(pool)
    .await
}

pub async fn get_collections(pool: &PgPool) -> Result<Vec<Collection>, sqlx::Error> {
    sqlx::query_as::<_, Collection>(
        "SELECT id, name, description, category, api_source, cover_image_url, backdrop_url, created_at FROM collections ORDER BY name"
    )
    .fetch_all(pool)
    .await
}

pub async fn get_collection_by_category(
    pool: &PgPool,
    category: &str,
) -> Result<Option<Collection>, sqlx::Error> {
    sqlx::query_as::<_, Collection>(
        "SELECT id, name, description, category, api_source, cover_image_url, backdrop_url, created_at FROM collections WHERE category = $1 LIMIT 1"
    )
    .bind(category)
    .fetch_optional(pool)
    .await
}

pub async fn create_collection(
    pool: &PgPool,
    name: &str,
    category: &str,
    api_source: &str,
    description: Option<&str>,
    cover_image_url: Option<&str>,
    backdrop_url: Option<&str>,
) -> Result<Collection, sqlx::Error> {
    let id = Uuid::new_v4();
    let now = chrono::Utc::now();

    sqlx::query_as::<_, Collection>(
        "INSERT INTO collections (id, name, category, api_source, description, cover_image_url, backdrop_url, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
         RETURNING id, name, description, category, api_source, cover_image_url, backdrop_url, created_at"
    )
    .bind(id)
    .bind(name)
    .bind(category)
    .bind(api_source)
    .bind(description)
    .bind(cover_image_url)
    .bind(backdrop_url)
    .bind(now)
    .fetch_one(pool)
    .await
}

pub async fn get_collection_items(
    pool: &PgPool,
    collection_id: Uuid,
    limit: i64,
    offset: i64,
) -> Result<Vec<CollectionItem>, sqlx::Error> {
    sqlx::query_as::<_, CollectionItem>(
        "SELECT id, collection_id, external_id, name, description, image_url, item_type, data_json, created_at
         FROM collection_items WHERE collection_id = $1
         ORDER BY created_at DESC LIMIT $2 OFFSET $3"
    )
    .bind(collection_id)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await
}

pub async fn get_collection_items_by_type(
    pool: &PgPool,
    collection_id: Uuid,
    item_type: &str,
    limit: i64,
    offset: i64,
) -> Result<Vec<CollectionItem>, sqlx::Error> {
    sqlx::query_as::<_, CollectionItem>(
        "SELECT id, collection_id, external_id, name, description, image_url, item_type, data_json, created_at
         FROM collection_items WHERE collection_id = $1 AND item_type = $2
         ORDER BY created_at DESC LIMIT $3 OFFSET $4"
    )
    .bind(collection_id)
    .bind(item_type)
    .bind(limit)
    .bind(offset)
    .fetch_all(pool)
    .await
}

pub async fn create_collection_item(
    pool: &PgPool,
    collection_id: Uuid,
    name: &str,
    item_type: &str,
    external_id: Option<&str>,
    description: Option<&str>,
    image_url: Option<&str>,
    data: Option<serde_json::Value>,
) -> Result<CollectionItem, sqlx::Error> {
    let id = Uuid::new_v4();
    let now = chrono::Utc::now();

    sqlx::query_as::<_, CollectionItem>(
        "INSERT INTO collection_items (id, collection_id, name, item_type, external_id, description, image_url, data_json, created_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
         RETURNING id, collection_id, external_id, name, description, image_url, item_type, data_json, created_at"
    )
    .bind(id)
    .bind(collection_id)
    .bind(name)
    .bind(item_type)
    .bind(external_id)
    .bind(description)
    .bind(image_url)
    .bind(data)
    .bind(now)
    .fetch_one(pool)
    .await
}

pub async fn clear_collection_items(
    pool: &PgPool,
    collection_id: Uuid,
) -> Result<u64, sqlx::Error> {
    sqlx::query("DELETE FROM collection_items WHERE collection_id = $1")
        .bind(collection_id)
        .execute(pool)
        .await
        .map(|result| result.rows_affected())
}

pub async fn count_collection_items(
    pool: &PgPool,
    collection_id: Uuid,
) -> Result<i64, sqlx::Error> {
    sqlx::query_scalar::<_, i64>("SELECT COUNT(*) FROM collection_items WHERE collection_id = $1")
        .bind(collection_id)
        .fetch_one(pool)
        .await
}
