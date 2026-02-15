use crate::{api::error::ApiError, config::CONFIG, AppState};
use axum::{
    extract::{Path, State},
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Debug, Serialize, Deserialize)]
pub struct Recommendation {
    pub tmdb_id: i32,
    pub title: String,
    pub overview: Option<String>,
    pub poster_url: Option<String>,
    pub media_type: String,
    pub vote_average: Option<f64>,
    pub release_date: Option<String>,
}

#[derive(Debug, Deserialize)]
struct TmdbRecommendationResult {
    id: i32,
    title: Option<String>,
    name: Option<String>,
    overview: Option<String>,
    poster_path: Option<String>,
    vote_average: Option<f64>,
    release_date: Option<String>,
    first_air_date: Option<String>,
}

#[derive(Debug, Deserialize)]
struct TmdbRecommendationResponse {
    results: Vec<TmdbRecommendationResult>,
}

pub async fn get_recommendations_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<uuid::Uuid>,
) -> Result<Json<Vec<Recommendation>>, ApiError> {
    let media = crate::db::media::get_media_by_id(&state.db_pool, media_id).await?;

    let tmdb_id = media.tmdb_id.ok_or_else(|| {
        ApiError::InvalidInput("Ce média n'a pas de TMDB ID pour les recommandations.".into())
    })?;

    let client = reqwest::Client::new();
    let endpoint = match media.media_type.as_str() {
        "movie" => format!(
            "https://api.themoviedb.org/3/movie/{}/recommendations",
            tmdb_id
        ),
        "tv" => format!(
            "https://api.themoviedb.org/3/tv/{}/recommendations",
            tmdb_id
        ),
        _ => {
            return Err(ApiError::InvalidInput(
                "Les recommandations ne sont disponibles que pour les films et séries.".into(),
            ))
        }
    };

    let response = client
        .get(&endpoint)
        .query(&[
            ("api_key", CONFIG.tmdb_api_key.as_str()),
            ("language", "fr-FR"),
            ("page", "1"),
        ])
        .send()
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Erreur TMDB: {}", e)))?
        .json::<TmdbRecommendationResponse>()
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Erreur parsing TMDB: {}", e)))?;

    let recommendations: Vec<Recommendation> = response
        .results
        .into_iter()
        .take(20)
        .map(|r| Recommendation {
            tmdb_id: r.id,
            title: r.title.or(r.name).unwrap_or_default(),
            overview: r.overview,
            poster_url: r
                .poster_path
                .map(|p| format!("https://image.tmdb.org/t/p/w500{}", p)),
            media_type: media.media_type.clone(),
            vote_average: r.vote_average,
            release_date: r.release_date.or(r.first_air_date),
        })
        .collect();

    Ok(Json(recommendations))
}
