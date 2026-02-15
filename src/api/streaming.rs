use crate::{api::error::ApiError, db, AppState};
use axum::{
    extract::{Path, Query, State},
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

#[derive(Serialize, Clone)]
pub struct StreamSource {
    pub name: String,
    pub url: String,
    pub quality: String,
}

#[derive(Serialize)]
pub struct StreamLinks {
    pub title: String,
    pub tmdb_id: i32,
    pub media_type: String,
    pub sources: Vec<StreamSource>,
}

#[derive(Debug, Deserialize)]
pub struct StreamQuery {
    pub season: Option<i32>,
    pub episode: Option<i32>,
}

/// Build embed sources from TMDB ID, media type, and optional season/episode
fn build_sources(
    tmdb_id: i32,
    media_type: &str,
    season: Option<i32>,
    episode: Option<i32>,
) -> Vec<StreamSource> {
    let s = season.unwrap_or(1);
    let e = episode.unwrap_or(1);
    let mut sources = Vec::new();

    // VidSrc.cc
    sources.push(StreamSource {
        name: "VidSrc".to_string(),
        url: match media_type {
            "tv" => format!("https://vidsrc.cc/v2/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidsrc.cc/v2/embed/movie/{}", tmdb_id),
        },
        quality: "Multi".to_string(),
    });

    // Embed.su
    sources.push(StreamSource {
        name: "Embed.su".to_string(),
        url: match media_type {
            "tv" => format!("https://embed.su/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://embed.su/embed/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
    });

    // SuperEmbed / MultiEmbed
    sources.push(StreamSource {
        name: "SuperEmbed".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://multiembed.mov/?video_id={}&tmdb=1&s={}&e={}",
                tmdb_id, s, e
            ),
            _ => format!("https://multiembed.mov/?video_id={}&tmdb=1", tmdb_id),
        },
        quality: "Multi".to_string(),
    });

    // 2embed
    sources.push(StreamSource {
        name: "2Embed".to_string(),
        url: match media_type {
            "tv" => format!("https://www.2embed.cc/embedtv/{}&&s={}&e={}", tmdb_id, s, e),
            _ => format!("https://www.2embed.cc/embed/{}", tmdb_id),
        },
        quality: "HD".to_string(),
    });

    // VidSrc.xyz
    sources.push(StreamSource {
        name: "VidSrc.xyz".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://vidsrc.xyz/embed/tv?tmdb={}&season={}&episode={}",
                tmdb_id, s, e
            ),
            _ => format!("https://vidsrc.xyz/embed/movie?tmdb={}", tmdb_id),
        },
        quality: "Multi".to_string(),
    });

    sources
}

/// GET /media/:id/stream - Get streaming embed links for a media (DB-based)
pub async fn get_stream_links_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
    Query(params): Query<StreamQuery>,
) -> Result<Json<StreamLinks>, ApiError> {
    let media = db::media::get_media_by_id(&state.db_pool, media_id).await?;

    let tmdb_id = media.tmdb_id.ok_or_else(|| {
        ApiError::InvalidInput("Ce media n'a pas de TMDB ID, streaming impossible.".into())
    })?;

    let sources = build_sources(tmdb_id, &media.media_type, params.season, params.episode);

    Ok(Json(StreamLinks {
        title: media.title,
        tmdb_id,
        media_type: media.media_type,
        sources,
    }))
}

/// GET /streaming/direct/:media_type/:tmdb_id - Get streaming links directly (no DB)
pub async fn direct_stream_handler(
    Path((media_type, tmdb_id)): Path<(String, i32)>,
    Query(params): Query<StreamQuery>,
) -> Result<Json<StreamLinks>, ApiError> {
    let sources = build_sources(tmdb_id, &media_type, params.season, params.episode);

    Ok(Json(StreamLinks {
        title: format!("TMDB #{}", tmdb_id),
        tmdb_id,
        media_type,
        sources,
    }))
}
