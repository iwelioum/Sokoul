use crate::{
    api::error::ApiError,
    db,
    events::{self, SearchRequestedPayload},
    models::{ApiSearchPayload, SearchResult},
    AppState,
};
use axum::{extract::{Path, State}, http::StatusCode, response::IntoResponse, Json};
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

pub async fn trigger_search_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<ApiSearchPayload>,
) -> Result<impl IntoResponse, ApiError> {
    let query = payload.query.trim().to_string();
    if query.is_empty() {
        return Err(ApiError::InvalidInput("La requête de recherche ne peut pas être vide.".into()));
    }
    if query.len() > 200 {
        return Err(ApiError::InvalidInput("La requête de recherche ne peut pas dépasser 200 caractères.".into()));
    }
    tracing::info!("Requête de recherche reçue pour: '{}'", query);

    let event_payload = SearchRequestedPayload {
        query,
    };

    let payload_bytes = serde_json::to_vec(&event_payload)
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Failed to serialize search event: {}", e)))?;

    state.jetstream_context.publish(events::SEARCH_REQUESTED_SUBJECT, payload_bytes.into()).await
        .map_err(|e| ApiError::MessageBus(format!("Failed to publish search event: {}", e)))?;

    Ok((StatusCode::ACCEPTED, Json(json!({"message": "La tâche de recherche a été acceptée."}))))
}

pub async fn get_search_results_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
) -> Result<Json<Vec<SearchResult>>, ApiError> {
    tracing::info!("Récupération des résultats de recherche pour le média: {}", media_id);

    let results = db::search_results::get_results_by_media_id(&state.db_pool, media_id).await?;

    Ok(Json(results))
}
