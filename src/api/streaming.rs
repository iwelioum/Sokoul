use crate::api::error::ApiError;
use axum::{
    extract::{Path, Query},
    Json,
};
use serde::{Deserialize, Serialize};

/// Gold theme matching the project's midnight-blue/gold design
const VIDFAST_THEME: &str = "D4AF37";

/// Allowed VidFast origins for PostMessage security validation (frontend use only)
#[allow(dead_code)]
pub const VIDFAST_ORIGINS: &[&str] = &[
    "https://vidfast.pro",
    "https://vidfast.in",
    "https://vidfast.io",
    "https://vidfast.me",
    "https://vidfast.net",
    "https://vidfast.pm",
    "https://vidfast.xyz",
];

#[derive(Debug, Deserialize)]
pub struct EmbedQuery {
    pub season: Option<u32>,
    pub episode: Option<u32>,
}

#[derive(Serialize)]
pub struct EmbedResponse {
    pub embed_url: String,
    pub media_type: String,
    pub tmdb_id: i32,
    pub season: Option<u32>,
    pub episode: Option<u32>,
}

/// GET /streaming/embed/:media_type/:tmdb_id
///
/// Returns the VidFast embed URL for a given TMDB ID.
/// The frontend can also build this URL directly — this endpoint
/// centralises the theme/config so a single backend change propagates everywhere.
///
/// VidFast docs: https://vidfast.pro
///   Movie  → https://vidfast.pro/movie/{tmdb_id}
///   TV     → https://vidfast.pro/tv/{tmdb_id}/{season}/{episode}
pub async fn vidfast_embed_handler(
    Path((media_type, tmdb_id)): Path<(String, i32)>,
    Query(params): Query<EmbedQuery>,
) -> Result<Json<EmbedResponse>, ApiError> {
    let s = params.season.unwrap_or(1);
    let e = params.episode.unwrap_or(1);

    let embed_url = match media_type.as_str() {
        "tv" => format!(
            "https://vidfast.pro/tv/{}/{}/{}?autoPlay=true&sub=fr&theme={}&nextButton=true&autoNext=true&title=true&poster=true",
            tmdb_id, s, e, VIDFAST_THEME
        ),
        _ => format!(
            "https://vidfast.pro/movie/{}?autoPlay=true&sub=fr&theme={}&title=true&poster=true",
            tmdb_id, VIDFAST_THEME
        ),
    };

    Ok(Json(EmbedResponse {
        embed_url,
        media_type,
        tmdb_id,
        season: if s > 0 { Some(s) } else { None },
        episode: if e > 0 { Some(e) } else { None },
    }))
}
