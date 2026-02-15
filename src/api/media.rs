use crate::{
    api::error::ApiError,
    cache,
    db,
    models::{CreateMediaPayload, Media, UpdateMediaPayload},
    AppState,
};
use axum::{extract::{Path, Query, State}, http::StatusCode, response::IntoResponse, Json};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

#[derive(Debug, Deserialize)]
pub struct Pagination {
    #[serde(default = "default_page")]
    page: u32,
    #[serde(default = "default_per_page")]
    per_page: u32,
    #[serde(default)]
    media_type: Option<String>,
}

fn default_page() -> u32 { 1 }
fn default_per_page() -> u32 { 30 }

#[derive(Debug, Serialize)]
pub struct PaginatedResponse<T: Serialize> {
    pub data: T,
    pub page: u32,
    pub per_page: u32,
    pub total: i64,
    pub total_pages: u32,
}

const VALID_MEDIA_TYPES: &[&str] = &["movie", "tv", "episode"];
const MAX_PER_PAGE: u32 = 100;
const MAX_TITLE_LEN: usize = 500;

fn validate_create_payload(payload: &CreateMediaPayload) -> Result<(), ApiError> {
    let title = payload.title.trim();
    if title.is_empty() {
        return Err(ApiError::InvalidInput("Le titre ne peut pas etre vide.".into()));
    }
    if title.len() > MAX_TITLE_LEN {
        return Err(ApiError::InvalidInput(format!(
            "Le titre ne peut pas depasser {} caracteres.", MAX_TITLE_LEN
        )));
    }
    if !VALID_MEDIA_TYPES.contains(&payload.media_type.as_str()) {
        return Err(ApiError::InvalidInput(format!(
            "Type de media invalide '{}'. Valeurs acceptees: {}",
            payload.media_type,
            VALID_MEDIA_TYPES.join(", ")
        )));
    }
    if let Some(year) = payload.year {
        if year < 1888 || year > 2100 {
            return Err(ApiError::InvalidInput("Annee invalide.".into()));
        }
    }
    if let Some(rating) = payload.rating {
        if !(0.0..=10.0).contains(&rating) {
            return Err(ApiError::InvalidInput("La note doit etre entre 0 et 10.".into()));
        }
    }
    Ok(())
}

pub async fn create_media_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<CreateMediaPayload>,
) -> Result<(StatusCode, Json<Media>), ApiError> {
    validate_create_payload(&payload)?;
    tracing::info!("Creation de media: {:?}", payload);

    let created_media = db::media::create_media(&state.db_pool, &payload).await?;

    tracing::info!("Media cree avec succes: {}", created_media.id);

    Ok((StatusCode::CREATED, Json(created_media)))
}

pub async fn get_media_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<Json<Media>, ApiError> {
    let cache_key = format!("media:{}", id);

    match cache::get_from_cache::<Media>(&state.redis_client, &cache_key).await {
        Ok(Some(media)) => {
            return Ok(Json(media));
        }
        Ok(None) => {}
        Err(e) => {
            tracing::warn!("Erreur du cache Redis (lecture): {}", e);
        }
    }

    let media = db::media::get_media_by_id(&state.db_pool, id).await?;

    if let Err(e) = cache::set_to_cache(&state.redis_client, &cache_key, &media).await {
        tracing::warn!("Erreur du cache Redis (ecriture): {}", e);
    }

    Ok(Json(media))
}

pub async fn list_media_handler(
    State(state): State<Arc<AppState>>,
    Query(pagination): Query<Pagination>,
) -> Result<Json<PaginatedResponse<Vec<Media>>>, ApiError> {
    let per_page = pagination.per_page.min(MAX_PER_PAGE).max(1);
    let page = pagination.page.max(1);

    let limit = i64::from(per_page);
    let offset = i64::from(page.saturating_sub(1) * per_page);

    let (media_list, total) = if let Some(ref mt) = pagination.media_type {
        let list = db::media::list_media_by_type(&state.db_pool, mt, limit, offset).await?;
        let count = db::media::count_media_by_type(&state.db_pool, mt).await?;
        (list, count)
    } else {
        let list = db::media::list_media(&state.db_pool, limit, offset).await?;
        let count = db::media::count_media(&state.db_pool).await?;
        (list, count)
    };

    let total_pages = ((total as f64) / (per_page as f64)).ceil() as u32;

    Ok(Json(PaginatedResponse {
        data: media_list,
        page,
        per_page,
        total,
        total_pages,
    }))
}

pub async fn get_episodes_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<Json<Vec<Media>>, ApiError> {
    let episodes = db::media::get_episodes(&state.db_pool, id).await?;
    Ok(Json(episodes))
}

pub async fn update_media_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
    Json(payload): Json<UpdateMediaPayload>,
) -> Result<Json<Media>, ApiError> {
    let updated_media = db::media::update_media_by_id(&state.db_pool, id, &payload).await?;

    let cache_key = format!("media:{}", id);
    if let Err(e) = cache::delete_from_cache(&state.redis_client, &cache_key).await {
        tracing::warn!("Echec de l'invalidation du cache pour {}: {}", id, e);
    }

    Ok(Json(updated_media))
}

pub async fn delete_media_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<impl IntoResponse, ApiError> {
    let rows_affected = db::media::delete_media_by_id(&state.db_pool, id).await?;

    if rows_affected == 0 {
        return Err(ApiError::NotFound(format!(
            "Le media avec l'ID {} n'a pas ete trouve pour la suppression.",
            id
        )));
    }

    let cache_key = format!("media:{}", id);
    if let Err(e) = cache::delete_from_cache(&state.redis_client, &cache_key).await {
        tracing::warn!("Echec de l'invalidation du cache pour {}: {}", id, e);
    }

    Ok(StatusCode::NO_CONTENT)
}
