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
use tracing::warn;
use uuid::Uuid;

pub fn tv_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/channels", get(list_channels_handler))
        .route("/channels/:id", get(get_channel_handler))
        .route("/channels/:id/programs", get(get_channel_programs_handler))
        .route("/channels/:code/stream", get(get_channel_stream_handler))
        .route("/programs/now", get(get_programs_now_handler))
        .route("/programs/search", get(search_programs_handler))
}

#[derive(Deserialize)]
pub struct PaginationParams {
    #[serde(default = "default_page")]
    page: i64,
    #[serde(default = "default_limit")]
    limit: i64,
}

#[derive(Deserialize)]
#[allow(dead_code)]
pub struct DateParams {
    date: Option<String>,
}

#[derive(Deserialize)]
pub struct SearchParams {
    q: String,
    #[serde(default = "default_search_limit")]
    limit: i64,
}

fn default_page() -> i64 {
    1
}

fn default_limit() -> i64 {
    50
}

fn default_search_limit() -> i64 {
    20
}

async fn sync_channels_from_provider(state: &Arc<AppState>) -> Result<(), ApiError> {
    let provider = crate::clients::tv_channels::TvChannelsClient::new();
    let channels = match provider.get_all_channels().await {
        Ok(list) if !list.is_empty() => list,
        Ok(_) => return Ok(()),
        Err(e) => {
            warn!("TV provider fetch failed: {}", e);
            return Ok(());
        }
    };

    for channel in channels {
        if let Err(e) = db::tv::upsert_channel(
            &state.db_pool,
            &channel.name,
            &channel.code,
            channel.country.as_deref(),
            channel.logo_url.as_deref(),
            channel.category.as_deref(),
            channel.is_free,
            channel.stream_url.as_deref(),
        )
        .await
        {
            warn!("Failed to upsert TV channel {}: {}", channel.code, e);
        }
    }

    Ok(())
}

pub async fn list_channels_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<db::tv::TvChannel>>, ApiError> {
    let key = "tv:channels:list";
    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::tv::TvChannel>>(&state.redis_client, key).await
    {
        if !cached.is_empty() {
            return Ok(Json(cached));
        }
    }

    let mut channels = db::tv::get_channels(&state.db_pool)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    if channels.is_empty() {
        sync_channels_from_provider(&state).await?;
        channels = db::tv::get_channels(&state.db_pool)
            .await
            .map_err(|_| ApiError::InternalServerError)?;
    }

    if !channels.is_empty() {
        let _ = set_to_cache_with_ttl(&state.redis_client, key, &channels, 86400).await;
    }

    Ok(Json(channels))
}

pub async fn get_channel_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<Json<db::tv::TvChannel>, ApiError> {
    let key = format!("tv:channel:{}", id);
    if let Ok(Some(cached)) = get_from_cache::<db::tv::TvChannel>(&state.redis_client, &key).await {
        return Ok(Json(cached));
    }

    let channel = db::tv::get_channel(&state.db_pool, id)
        .await
        .map_err(|_| ApiError::InternalServerError)?
        .ok_or_else(|| ApiError::NotFound("Channel not found".to_string()))?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &channel, 86400).await;

    Ok(Json(channel))
}

pub async fn get_channel_programs_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    Query(params): Query<PaginationParams>,
) -> Result<Json<Vec<db::tv::TvProgram>>, ApiError> {
    let offset = (params.page - 1) * params.limit;
    let key = format!("tv:programs:{}:{}:{}", id, params.page, params.limit);

    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::tv::TvProgram>>(&state.redis_client, &key).await
    {
        return Ok(Json(cached));
    }

    let programs = db::tv::get_programs_for_channel(&state.db_pool, id, params.limit, offset)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &programs, 1800).await;

    Ok(Json(programs))
}

pub async fn get_channel_stream_handler(
    State(state): State<Arc<AppState>>,
    Path(code): Path<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let mut channel = db::tv::get_channel_by_code(&state.db_pool, &code)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    if channel.is_none() {
        sync_channels_from_provider(&state).await?;
        channel = db::tv::get_channel_by_code(&state.db_pool, &code)
            .await
            .map_err(|_| ApiError::InternalServerError)?;
    }

    let channel = channel.ok_or_else(|| ApiError::NotFound("Channel not found".to_string()))?;

    let response = serde_json::json!({
        "id": channel.id,
        "name": channel.name,
        "code": channel.code,
        "stream_url": channel.stream_url,
        "logo_url": channel.logo_url,
    });

    Ok(Json(response))
}

pub async fn get_programs_now_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<db::tv::TvProgram>>, ApiError> {
    let key = "tv:programs:now";
    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::tv::TvProgram>>(&state.redis_client, key).await
    {
        return Ok(Json(cached));
    }

    let programs = db::tv::get_current_programs(&state.db_pool)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let _ = set_to_cache_with_ttl(&state.redis_client, key, &programs, 300).await;

    Ok(Json(programs))
}

pub async fn search_programs_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SearchParams>,
) -> Result<Json<Vec<db::tv::TvProgram>>, ApiError> {
    let key = format!("tv:search:{}:{}", params.q, params.limit);
    if let Ok(Some(cached)) =
        get_from_cache::<Vec<db::tv::TvProgram>>(&state.redis_client, &key).await
    {
        return Ok(Json(cached));
    }

    let programs = db::tv::search_programs(&state.db_pool, &params.q, params.limit)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let _ = set_to_cache_with_ttl(&state.redis_client, &key, &programs, 3600).await;

    Ok(Json(programs))
}
