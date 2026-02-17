use crate::{
    api::error::ApiError,
    cache::{get_from_cache, set_to_cache_with_ttl},
    db, AppState,
};
use axum::{
    extract::{Path, Query, State},
    routing::get,
    Json, Router,
};
use serde::Deserialize;
use std::sync::Arc;
use uuid::Uuid;

pub fn collections_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/", get(list_collections_handler))
        .route("/:id", get(get_collection_handler))
        .route("/:id/items", get(get_collection_items_handler))
        .route(
            "/:id/items/:item_type",
            get(get_collection_items_by_type_handler),
        )
}

#[derive(Deserialize)]
pub struct PaginationParams {
    #[serde(default = "default_page")]
    page: i64,
    #[serde(default = "default_limit")]
    limit: i64,
}

fn default_page() -> i64 {
    1
}

fn default_limit() -> i64 {
    20
}

pub async fn list_collections_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<db::collections::Collection>>, ApiError> {
    let key = "collections:list";
    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::collections::Collection>>(&state.redis_client, &key).await
    {
        return Ok(Json(cached));
    }

    let collections = db::collections::get_collections(&state.db_pool)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &collections, 3600).await;

    Ok(Json(collections))
}

pub async fn get_collection_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<Json<db::collections::Collection>, ApiError> {
    let key = format!("collections:detail:{}", id);
    if let Ok(Some(cached)) =
        get_from_cache::<db::collections::Collection>(&state.redis_client, &key).await
    {
        return Ok(Json(cached));
    }

    let collection = db::collections::get_collection(&state.db_pool, id)
        .await
        .map_err(|_| ApiError::InternalServerError)?
        .ok_or_else(|| ApiError::NotFound("Collection not found".to_string()))?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &collection, 86400).await;

    Ok(Json(collection))
}

pub async fn get_collection_items_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    Query(params): Query<PaginationParams>,
) -> Result<Json<Vec<db::collections::CollectionItem>>, ApiError> {
    let offset = (params.page - 1) * params.limit;
    let key = format!("collections:items:{}:{}:{}", id, params.page, params.limit);

    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::collections::CollectionItem>>(&state.redis_client, &key).await
    {
        return Ok(Json(cached));
    }

    let items = db::collections::get_collection_items(&state.db_pool, id, params.limit, offset)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &items, 1800).await;

    Ok(Json(items))
}

pub async fn get_collection_items_by_type_handler(
    State(state): State<Arc<AppState>>,
    Path((id, item_type)): Path<(Uuid, String)>,
    Query(params): Query<PaginationParams>,
) -> Result<Json<Vec<db::collections::CollectionItem>>, ApiError> {
    let offset = (params.page - 1) * params.limit;
    let key = format!(
        "collections:items:{}:{}:{}:{}",
        id, item_type, params.page, params.limit
    );

    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::collections::CollectionItem>>(&state.redis_client, &key).await
    {
        return Ok(Json(cached));
    }

    let items = db::collections::get_collection_items_by_type(
        &state.db_pool,
        id,
        &item_type,
        params.limit,
        offset,
    )
    .await
    .map_err(|_| ApiError::InternalServerError)?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &items, 1800).await;

    Ok(Json(items))
}
