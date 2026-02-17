use crate::{
    api::error::ApiError,
    db,
    models::{CreateMediaPayload, UpdateMediaPayload},
};
use sqlx::PgPool;
use uuid::Uuid;

#[derive(Debug, Clone, Default)]
pub struct MediaReferenceInput {
    pub media_id: Option<Uuid>,
    pub tmdb_id: Option<i32>,
    pub media_type: Option<String>,
    pub title: Option<String>,
    pub year: Option<i32>,
    pub release_date: Option<String>,
    pub overview: Option<String>,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub genres: Option<Vec<String>>,
    pub vote_average: Option<f64>,
}

pub fn normalize_media_type(raw: &str) -> Result<String, ApiError> {
    let normalized = raw.trim().to_lowercase();
    match normalized.as_str() {
        "movie" | "tv" | "episode" => Ok(normalized),
        _ => Err(ApiError::InvalidInput(format!(
            "Invalid media type '{}'. Accepted values: movie, tv, episode",
            raw
        ))),
    }
}

fn parse_year_from_release_date(release_date: Option<&str>) -> Option<i32> {
    let date = release_date?;
    let year = date.get(0..4)?;
    year.parse::<i32>().ok()
}

fn derive_year(input: &MediaReferenceInput) -> Option<i32> {
    input
        .year
        .or_else(|| parse_year_from_release_date(input.release_date.as_deref()))
}

pub async fn find_media_id_by_tmdb(
    pool: &PgPool,
    tmdb_id: i32,
    media_type: &str,
) -> Result<Option<Uuid>, ApiError> {
    let media_type = normalize_media_type(media_type)?;
    let existing = db::media::get_media_by_tmdb_and_type(pool, tmdb_id, &media_type)
        .await
        .map_err(ApiError::Database)?;
    Ok(existing.map(|m| m.id))
}

pub async fn resolve_media_id(pool: &PgPool, input: MediaReferenceInput) -> Result<Uuid, ApiError> {
    if let Some(media_id) = input.media_id {
        return Ok(media_id);
    }

    let tmdb_id = input.tmdb_id.ok_or_else(|| {
        ApiError::InvalidInput(
            "A media identifier is required: media_id or (tmdb_id + media_type).".into(),
        )
    })?;
    let media_type = normalize_media_type(input.media_type.as_deref().ok_or_else(|| {
        ApiError::InvalidInput(
            "A media type is required: media_id or (tmdb_id + media_type).".into(),
        )
    })?)?;

    let media_id = if let Some(existing) =
        db::media::get_media_by_tmdb_and_type(pool, tmdb_id, &media_type)
            .await
            .map_err(ApiError::Database)?
    {
        existing.id
    } else {
        let create_payload = CreateMediaPayload {
            title: input
                .title
                .clone()
                .unwrap_or_else(|| format!("TMDB #{}", tmdb_id)),
            media_type: media_type.clone(),
            tmdb_id: Some(tmdb_id),
            year: derive_year(&input),
            overview: input.overview.clone(),
            poster_url: input.poster_url.clone(),
            genres: input.genres.clone(),
            rating: input.vote_average,
        };

        db::media::create_media(pool, &create_payload)
            .await
            .map_err(ApiError::Database)?
            .id
    };

    if input.title.is_some()
        || derive_year(&input).is_some()
        || input.overview.is_some()
        || input.poster_url.is_some()
        || input.backdrop_url.is_some()
        || input.genres.is_some()
        || input.vote_average.is_some()
    {
        let update_payload = UpdateMediaPayload {
            title: input.title.clone(),
            year: derive_year(&input),
            overview: input.overview.clone(),
            poster_url: input.poster_url.clone(),
            backdrop_url: input.backdrop_url.clone(),
            genres: input.genres.clone(),
            rating: input.vote_average,
            status: None,
        };

        db::media::update_media_by_id(pool, media_id, &update_payload)
            .await
            .map_err(ApiError::Database)?;
    }

    Ok(media_id)
}
