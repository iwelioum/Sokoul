use crate::{
    api::error::ApiError,
    config::CONFIG,
    db,
    events::{self, SearchRequestedPayload},
    models::{ApiSearchPayload, SearchResult},
    providers::{jackett::JackettProvider, prowlarr::ProwlarrProvider, ProviderRegistry},
    AppState,
};
use axum::{
    extract::{Path, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

pub async fn trigger_search_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<ApiSearchPayload>,
) -> Result<impl IntoResponse, ApiError> {
    let query = payload.query.trim().to_string();
    if query.is_empty() {
        return Err(ApiError::InvalidInput(
            "Search query cannot be empty.".into(),
        ));
    }
    if query.len() > 200 {
        return Err(ApiError::InvalidInput(
            "Search query cannot exceed 200 characters.".into(),
        ));
    }
    tracing::info!("Search request received for: '{}'", query);

    let event_payload = SearchRequestedPayload { query };

    let payload_bytes = serde_json::to_vec(&event_payload).map_err(|e| {
        ApiError::Internal(anyhow::anyhow!("Failed to serialize search event: {}", e))
    })?;

    state
        .jetstream_context
        .publish(events::SEARCH_REQUESTED_SUBJECT, payload_bytes.into())
        .await
        .map_err(|e| ApiError::MessageBus(format!("Failed to publish search event: {}", e)))?;

    Ok((
        StatusCode::ACCEPTED,
        Json(json!({"message": "Search task accepted."})),
    ))
}

pub async fn get_search_results_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
) -> Result<Json<Vec<SearchResult>>, ApiError> {
    tracing::info!("Fetching search results for media: {}", media_id);

    let results = db::search_results::get_results_by_media_id(&state.db_pool, media_id).await?;

    Ok(Json(results))
}

#[derive(Debug, Deserialize)]
pub struct DirectSearchPayload {
    pub query: String,
    pub media_id: Uuid,
}

/// POST /search/direct - Search Prowlarr/Jackett directly and return results immediately
pub async fn direct_search_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<DirectSearchPayload>,
) -> Result<impl IntoResponse, ApiError> {
    let query = payload.query.trim().to_string();
    if query.is_empty() {
        return Err(ApiError::InvalidInput(
            "Search query cannot be empty.".into(),
        ));
    }

    tracing::info!(
        "Direct search for '{}' (media_id: {})",
        query,
        payload.media_id
    );

    // Build provider registry on-the-fly
    let mut registry = ProviderRegistry::new();

    if !CONFIG.prowlarr_url.is_empty() && !CONFIG.prowlarr_api_key.is_empty() {
        registry.register(Box::new(ProwlarrProvider::new(
            CONFIG.prowlarr_api_key.clone(),
            CONFIG.prowlarr_url.clone(),
            state.flaresolverr_client.clone(),
        )));
    }

    if !CONFIG.jackett_url.is_empty() && !CONFIG.jackett_api_key.is_empty() {
        registry.register(Box::new(JackettProvider::new(
            CONFIG.jackett_api_key.clone(),
            CONFIG.jackett_url.clone(),
            state.flaresolverr_client.clone(),
        )));
    }

    // Search all providers directly
    let mut sources = registry.search_all(&query, "movie", None).await;

    // Keep response fast: sort by seeders desc and cap results
    sources.sort_by_key(|s| std::cmp::Reverse(s.seeders.unwrap_or(0)));
    sources.truncate(200);

    if sources.is_empty() {
        tracing::info!("No sources found for '{}'", query);
        return Ok((StatusCode::OK, Json(json!({"results": [], "count": 0}))));
    }

    tracing::info!("{} source(s) found for '{}'", sources.len(), query);

    // Save results to DB
    if let Err(e) =
        db::search_results::create_batch(&state.db_pool, payload.media_id, &sources).await
    {
        tracing::error!("Failed to save sources: {}", e);
    }

    // Return fresh results from DB
    let results =
        db::search_results::get_results_by_media_id(&state.db_pool, payload.media_id).await?;

    Ok((
        StatusCode::OK,
        Json(json!({"results": results, "count": results.len()})),
    ))
}
