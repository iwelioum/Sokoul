use crate::{
    api::error::ApiError,
    clients::subtitles::SubtitleClient,
    db,
    extractors::{self, registry::ExtractorRegistry, SubtitleTrack as ExtSubtitleTrack},
    security, AppState,
};
use axum::{
    extract::{Path, Query, State},
    http::{header, StatusCode},
    response::IntoResponse,
    Json,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

#[derive(Serialize, Deserialize, Clone)]
pub struct StreamSource {
    pub name: String,
    pub url: String,
    pub quality: String,
    pub security_status: Option<String>, // "safe", "warning"
    #[serde(skip_serializing_if = "Option::is_none")]
    pub category: Option<String>, // "Omega", "Multi", "FStream", "Viper", "International"
    #[serde(skip_serializing_if = "Option::is_none")]
    pub language: Option<String>, // "VF", "VOSTFR", "Multi"
}

#[derive(Serialize)]
pub struct StreamLinks {
    pub title: String,
    pub tmdb_id: i32,
    pub media_type: String,
    pub sources: Vec<StreamSource>,
    pub security_warning: Option<String>,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct FrenchSourceGroup {
    pub category: String,
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

    // ── International sources ───────────────────────────────────────────

    let int_cat = Some("International".to_string());

    // VidSrc.cc
    sources.push(StreamSource {
        name: "VidSrc".to_string(),
        url: match media_type {
            "tv" => format!("https://vidsrc.cc/v2/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidsrc.cc/v2/embed/movie/{}", tmdb_id),
        },
        quality: "Multi".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // Embed.su
    sources.push(StreamSource {
        name: "Embed.su".to_string(),
        url: match media_type {
            "tv" => format!("https://embed.su/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://embed.su/embed/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
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
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // 2embed
    sources.push(StreamSource {
        name: "2Embed".to_string(),
        url: match media_type {
            "tv" => format!("https://www.2embed.cc/embedtv/{}&&s={}&e={}", tmdb_id, s, e),
            _ => format!("https://www.2embed.cc/embed/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
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
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // VidSrc.pro
    sources.push(StreamSource {
        name: "VidSrc.pro".to_string(),
        url: match media_type {
            "tv" => format!("https://vidsrc.pro/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidsrc.pro/embed/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // VidSrc.icu
    sources.push(StreamSource {
        name: "VidSrc.icu".to_string(),
        url: match media_type {
            "tv" => format!("https://vidsrc.icu/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidsrc.icu/embed/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // MoviesAPI
    sources.push(StreamSource {
        name: "MoviesAPI".to_string(),
        url: match media_type {
            "tv" => format!("https://moviesapi.club/tv/{}-{}-{}", tmdb_id, s, e),
            _ => format!("https://moviesapi.club/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // Autoembed
    sources.push(StreamSource {
        name: "AutoEmbed".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://player.autoembed.cc/embed/tv/{}/{}/{}",
                tmdb_id, s, e
            ),
            _ => format!("https://player.autoembed.cc/embed/movie/{}", tmdb_id),
        },
        quality: "Multi".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // NontonGo
    sources.push(StreamSource {
        name: "NontonGo".to_string(),
        url: match media_type {
            "tv" => format!("https://www.NontonGo.win/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://www.NontonGo.win/embed/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // VidBinge
    sources.push(StreamSource {
        name: "VidBinge".to_string(),
        url: match media_type {
            "tv" => format!("https://vidbinge.dev/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidbinge.dev/embed/movie/{}", tmdb_id),
        },
        quality: "Multi".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // smashystream
    sources.push(StreamSource {
        name: "Smashy".to_string(),
        url: match media_type {
            "tv" => format!("https://player.smashy.stream/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://player.smashy.stream/movie/{}", tmdb_id),
        },
        quality: "Multi".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // VidLink.pro
    sources.push(StreamSource {
        name: "VidLink".to_string(),
        url: match media_type {
            "tv" => format!("https://vidlink.pro/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidlink.pro/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: int_cat.clone(),
        language: None,
    });

    // ── French sources (TMDB-ID based) ───────────────────────────────────

    // Omega — SuperVideo (no ads, French priority)
    sources.push(StreamSource {
        name: "SuperVideo".to_string(),
        url: match media_type {
            "tv" => format!("https://supervideo.cc/e/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://supervideo.cc/e/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Omega".to_string()),
        language: Some("VF".to_string()),
    });

    // Multi — Uqload
    sources.push(StreamSource {
        name: "Uqload".to_string(),
        url: match media_type {
            "tv" => format!("https://uqload.is/embed-tv/{}/{}/{}.html", tmdb_id, s, e),
            _ => format!("https://uqload.is/embed-movie/{}.html", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Multi".to_string()),
        language: Some("VF".to_string()),
    });

    // FStream — VF sources
    sources.push(StreamSource {
        name: "Vidzy VF".to_string(),
        url: match media_type {
            "tv" => format!("https://vidzy.to/embed/tv/{}/{}/{}?lang=vf", tmdb_id, s, e),
            _ => format!("https://vidzy.to/embed/movie/{}?lang=vf", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("FStream".to_string()),
        language: Some("VF".to_string()),
    });

    sources.push(StreamSource {
        name: "Voe VF".to_string(),
        url: match media_type {
            "tv" => format!("https://voe.sx/embed/tv/{}/{}/{}?lang=vf", tmdb_id, s, e),
            _ => format!("https://voe.sx/embed/movie/{}?lang=vf", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("FStream".to_string()),
        language: Some("VF".to_string()),
    });

    // FStream — VOSTFR sources
    sources.push(StreamSource {
        name: "Vidzy VOSTFR".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://vidzy.to/embed/tv/{}/{}/{}?lang=vostfr",
                tmdb_id, s, e
            ),
            _ => format!("https://vidzy.to/embed/movie/{}?lang=vostfr", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("FStream".to_string()),
        language: Some("VOSTFR".to_string()),
    });

    sources.push(StreamSource {
        name: "Voe VOSTFR".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://voe.sx/embed/tv/{}/{}/{}?lang=vostfr",
                tmdb_id, s, e
            ),
            _ => format!("https://voe.sx/embed/movie/{}?lang=vostfr", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("FStream".to_string()),
        language: Some("VOSTFR".to_string()),
    });

    // Viper — VF HD sources
    sources.push(StreamSource {
        name: "Voe VF HD".to_string(),
        url: match media_type {
            "tv" => format!("https://voe.sx/e/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://voe.sx/e/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VF".to_string()),
    });

    sources.push(StreamSource {
        name: "Netu VF HD".to_string(),
        url: match media_type {
            "tv" => format!("https://netu.ac/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://netu.ac/embed/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VF".to_string()),
    });

    sources.push(StreamSource {
        name: "DoodStream VF HD".to_string(),
        url: match media_type {
            "tv" => format!("https://doodstream.com/e/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://doodstream.com/e/movie/{}", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VF".to_string()),
    });

    sources.push(StreamSource {
        name: "Vidoza VF HD".to_string(),
        url: match media_type {
            "tv" => format!("https://vidoza.net/embed-tv/{}/{}/{}.html", tmdb_id, s, e),
            _ => format!("https://vidoza.net/embed-movie/{}.html", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VF".to_string()),
    });

    // Viper — VOSTFR HD sources
    sources.push(StreamSource {
        name: "Voe VOSTFR HD".to_string(),
        url: match media_type {
            "tv" => format!("https://voe.sx/e/tv/{}/{}/{}?lang=vostfr", tmdb_id, s, e),
            _ => format!("https://voe.sx/e/movie/{}?lang=vostfr", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VOSTFR".to_string()),
    });

    sources.push(StreamSource {
        name: "Netu VOSTFR HD".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://netu.ac/embed/tv/{}/{}/{}?lang=vostfr",
                tmdb_id, s, e
            ),
            _ => format!("https://netu.ac/embed/movie/{}?lang=vostfr", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VOSTFR".to_string()),
    });

    sources.push(StreamSource {
        name: "DoodStream VOSTFR HD".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://doodstream.com/e/tv/{}/{}/{}?lang=vostfr",
                tmdb_id, s, e
            ),
            _ => format!("https://doodstream.com/e/movie/{}?lang=vostfr", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VOSTFR".to_string()),
    });

    sources.push(StreamSource {
        name: "Vidoza VOSTFR HD".to_string(),
        url: match media_type {
            "tv" => format!(
                "https://vidoza.net/embed-tv/{}/{}/{}?lang=vostfr",
                tmdb_id, s, e
            ),
            _ => format!("https://vidoza.net/embed-movie/{}?lang=vostfr", tmdb_id),
        },
        quality: "HD".to_string(),
        security_status: None,
        category: Some("Viper".to_string()),
        language: Some("VOSTFR".to_string()),
    });

    sources
}

/// Validate stream sources for security issues
async fn validate_sources(
    state: &Arc<AppState>,
    sources: &mut Vec<StreamSource>,
) -> Option<String> {
    let mut warning_reason = None;

    for source in sources.iter_mut() {
        let check = security::check_url_safety(state, &source.url).await;

        source.security_status = Some(check.risk_level.clone());

        if check.risk_level == "critical" {
            warning_reason = Some(format!(
                "A streaming source was blocked for security risk: {}",
                check.reason
            ));
        } else if check.risk_level == "warning" && warning_reason.is_none() {
            warning_reason = Some(format!(
                "Warning: a source presents a potential risk: {}",
                check.reason
            ));
        }

        // Log the check
        let _ = db::security::insert_audit_log(
            &state.db_pool,
            None,
            "stream_check",
            Some("stream_url"),
            Some(&source.name),
            Some(&source.url),
            None,
            None,
            &check.risk_level,
            "logged",
            None,
        )
        .await;
    }

    warning_reason
}

/// GET /media/:id/stream - Get streaming embed links for a media (DB-based)
pub async fn get_stream_links_handler(
    State(state): State<Arc<AppState>>,
    Path(media_id): Path<Uuid>,
    Query(params): Query<StreamQuery>,
) -> Result<Json<StreamLinks>, ApiError> {
    let media = db::media::get_media_by_id(&state.db_pool, media_id).await?;

    let tmdb_id = media.tmdb_id.ok_or_else(|| {
        ApiError::InvalidInput("This media has no TMDB ID, streaming unavailable.".into())
    })?;

    let mut sources = build_sources(tmdb_id, &media.media_type, params.season, params.episode);

    // Validate security for all sources
    let security_warning = validate_sources(&state, &mut sources).await;

    // Filter out critical-risk sources
    sources.retain(|s| s.security_status.as_deref() != Some("critical"));

    if sources.is_empty() {
        return Err(ApiError::Forbidden(
            "All streaming sources were blocked for security reasons.".to_string(),
        ));
    }

    Ok(Json(StreamLinks {
        title: media.title,
        tmdb_id,
        media_type: media.media_type,
        sources,
        security_warning,
    }))
}

/// GET /streaming/direct/:media_type/:tmdb_id - Get streaming links directly (no DB)
pub async fn direct_stream_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, tmdb_id)): Path<(String, i32)>,
    Query(params): Query<StreamQuery>,
) -> Result<Json<StreamLinks>, ApiError> {
    let mut sources = build_sources(tmdb_id, &media_type, params.season, params.episode);

    // Validate security for all sources
    let security_warning = validate_sources(&state, &mut sources).await;

    // Filter out critical-risk sources
    sources.retain(|s| s.security_status.as_deref() != Some("critical"));

    if sources.is_empty() {
        return Err(ApiError::Forbidden(
            "All streaming sources were blocked for security reasons.".to_string(),
        ));
    }

    Ok(Json(StreamLinks {
        title: format!("TMDB #{}", tmdb_id),
        tmdb_id,
        media_type,
        sources,
        security_warning,
    }))
}

// ── New endpoints for custom player ──────────────────────────────────

#[derive(Serialize, Deserialize)]
pub struct ExtractionResponse {
    pub tmdb_id: i32,
    pub media_type: String,
    pub streams: Vec<extractors::ExtractedStream>,
    pub iframe_fallbacks: Vec<StreamSource>,
    pub french_groups: Vec<FrenchSourceGroup>,
    pub subtitles: Vec<ExtSubtitleTrack>,
}

/// Group sources by category into FrenchSourceGroups
fn build_french_groups(sources: &[StreamSource]) -> Vec<FrenchSourceGroup> {
    let categories = ["Omega", "Multi", "FStream", "Viper"];
    let mut groups = Vec::new();
    for cat in &categories {
        let cat_sources: Vec<StreamSource> = sources
            .iter()
            .filter(|s| s.category.as_deref() == Some(cat))
            .cloned()
            .collect();
        if !cat_sources.is_empty() {
            groups.push(FrenchSourceGroup {
                category: cat.to_string(),
                sources: cat_sources,
            });
        }
    }
    groups
}

/// GET /streaming/extract/:media_type/:tmdb_id — Extract direct m3u8/mp4 streams
pub async fn extract_streams_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, tmdb_id)): Path<(String, i32)>,
    Query(params): Query<StreamQuery>,
) -> Result<Json<ExtractionResponse>, ApiError> {
    let http_client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(20))
        .build()
        .unwrap_or_default();

    // Check Redis cache
    let cache_key = format!(
        "stream_extract:{}:{}:{}:{}",
        media_type,
        tmdb_id,
        params.season.unwrap_or(0),
        params.episode.unwrap_or(0)
    );

    if let Ok(mut conn) = state.redis_client.get_multiplexed_async_connection().await {
        if let Ok(cached) = redis::AsyncCommands::get::<_, String>(&mut conn, &cache_key).await {
            if let Ok(response) = serde_json::from_str::<ExtractionResponse>(&cached) {
                tracing::info!(
                    "Extract cache hit for {} {} ({}s)",
                    media_type,
                    tmdb_id,
                    response.streams.len()
                );
                return Ok(Json(response));
            }
        }
    }

    // Build extractor registry
    let mut registry = ExtractorRegistry::new();
    // HTTP-based extractors (fast, no browser needed)
    registry.register(Box::new(extractors::moviesapi::MoviesApiExtractor));
    // Headless browser extractors (intercept m3u8 from network traffic)
    registry.register(Box::new(extractors::headless::HeadlessExtractor::new(
        "VidSrc",
        "https://vidsrc.cc/v2/embed/movie/{id}",
        "https://vidsrc.cc/v2/embed/tv/{id}/{s}/{e}",
        4,
    )));
    registry.register(Box::new(extractors::headless::HeadlessExtractor::new(
        "AutoEmbed",
        "https://player.autoembed.cc/embed/movie/{id}",
        "https://player.autoembed.cc/embed/tv/{id}/{s}/{e}",
        5,
    )));
    registry.register(Box::new(extractors::headless::HeadlessExtractor::new(
        "Embed.su",
        "https://embed.su/embed/movie/{id}",
        "https://embed.su/embed/tv/{id}/{s}/{e}",
        2,
    )));
    registry.register(Box::new(extractors::headless::HeadlessExtractor::new(
        "SuperEmbed",
        "https://multiembed.mov/?video_id={id}&tmdb=1",
        "https://multiembed.mov/?video_id={id}&tmdb=1&s={s}&e={e}",
        3,
    )));
    registry.register(Box::new(extractors::headless::HeadlessExtractor::new(
        "VidSrc.pro",
        "https://vidsrc.pro/embed/movie/{id}",
        "https://vidsrc.pro/embed/tv/{id}/{s}/{e}",
        3,
    )));
    registry.register(Box::new(extractors::headless::HeadlessExtractor::new(
        "Smashy",
        "https://player.smashy.stream/movie/{id}",
        "https://player.smashy.stream/tv/{id}/{s}/{e}",
        2,
    )));

    // French aggregator extractors (scrape French streaming sites)
    registry.register(Box::new(
        extractors::french::FrenchAggregatorExtractor::new(
            "FrenchStream",
            "https://french-stream.re",
            "FStream",
        ),
    ));
    registry.register(Box::new(
        extractors::french::FrenchAggregatorExtractor::new(
            "Coflix",
            "https://coflix.plus",
            "Multi",
        ),
    ));
    registry.register(Box::new(
        extractors::french::FrenchAggregatorExtractor::new("Wiflix", "https://wiflix.day", "Multi"),
    ));

    let browser_ref = state.browser.as_ref().map(|b| b.as_ref());

    // Run extractors
    let results = registry
        .extract_all(
            tmdb_id,
            &media_type,
            params.season,
            params.episode,
            &http_client,
            browser_ref,
        )
        .await;

    // Flatten and sort streams
    let mut streams: Vec<extractors::ExtractedStream> =
        results.into_iter().flat_map(|r| r.streams).collect();
    ExtractorRegistry::sort_streams(&mut streams);

    tracing::info!(
        "Extracted {} direct stream(s) for {} {}",
        streams.len(),
        media_type,
        tmdb_id
    );

    // Build iframe fallbacks from existing providers
    let iframe_fallbacks = build_sources(tmdb_id, &media_type, params.season, params.episode);

    // Build French source groups from categorized sources
    let french_groups = build_french_groups(&iframe_fallbacks);

    // Load subtitles in parallel (best effort)
    let subtitle_client = SubtitleClient::new();
    let subtitles = subtitle_client
        .search(
            tmdb_id,
            &media_type,
            params.season,
            params.episode,
            &["fr", "en"],
        )
        .await
        .unwrap_or_default();

    let response = ExtractionResponse {
        tmdb_id,
        media_type,
        streams,
        iframe_fallbacks,
        french_groups,
        subtitles,
    };

    // Cache for 30 minutes
    if let Ok(mut conn) = state.redis_client.get_multiplexed_async_connection().await {
        if let Ok(json) = serde_json::to_string(&response) {
            let _: Result<(), _> =
                redis::AsyncCommands::set_ex(&mut conn, &cache_key, json, 1800).await;
        }
    }

    Ok(Json(response))
}

#[derive(Debug, Deserialize)]
pub struct ProxyQuery {
    pub url: String,
    pub referer: Option<String>,
}

/// GET /streaming/proxy — CORS proxy for m3u8/ts segments
pub async fn stream_proxy_handler(
    Query(params): Query<ProxyQuery>,
) -> Result<impl IntoResponse, ApiError> {
    let client = reqwest::Client::new();

    let mut request = client.get(&params.url).header(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    );

    if let Some(referer) = &params.referer {
        request = request.header("Referer", referer);
        // Also set Origin from referer
        if let Ok(parsed) = url::Url::parse(referer) {
            request = request.header(
                "Origin",
                format!("{}://{}", parsed.scheme(), parsed.host_str().unwrap_or("")),
            );
        }
    }

    let resp = request
        .send()
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Proxy fetch failed: {}", e)))?;

    let content_type = resp
        .headers()
        .get("content-type")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("application/octet-stream")
        .to_string();

    let body = resp
        .bytes()
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Proxy read failed: {}", e)))?;

    // If it's an m3u8 playlist, rewrite relative URLs to go through our proxy
    if content_type.contains("mpegurl")
        || content_type.contains("m3u8")
        || params.url.contains(".m3u8")
    {
        let text = String::from_utf8_lossy(&body);
        let base_url = {
            let mut u = params.url.clone();
            if let Some(pos) = u.rfind('/') {
                u.truncate(pos + 1);
            }
            u
        };
        let referer_param = params.referer.as_deref().unwrap_or("");

        let rewritten: String = text
            .lines()
            .map(|line| {
                let trimmed = line.trim();
                if trimmed.starts_with('#') || trimmed.is_empty() {
                    line.to_string()
                } else if trimmed.starts_with("http") {
                    // Absolute URL — proxy it
                    format!(
                        "/api/streaming/proxy?url={}&referer={}",
                        urlencoding::encode(trimmed),
                        urlencoding::encode(referer_param)
                    )
                } else {
                    // Relative URL — resolve and proxy
                    let absolute = format!("{}{}", base_url, trimmed);
                    format!(
                        "/api/streaming/proxy?url={}&referer={}",
                        urlencoding::encode(&absolute),
                        urlencoding::encode(referer_param)
                    )
                }
            })
            .collect::<Vec<_>>()
            .join("\n");

        return Ok((
            StatusCode::OK,
            [
                (
                    header::CONTENT_TYPE,
                    "application/vnd.apple.mpegurl".to_string(),
                ),
                (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*".to_string()),
            ],
            rewritten,
        )
            .into_response());
    }

    // For .ts segments or other binary data, pass through
    Ok((
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, content_type),
            (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*".to_string()),
        ],
        body,
    )
        .into_response())
}

#[derive(Debug, Deserialize)]
pub struct SubtitleQuery {
    pub season: Option<i32>,
    pub episode: Option<i32>,
    pub lang: Option<String>, // comma-separated: "fr,en"
}

/// GET /streaming/subtitles/:media_type/:tmdb_id — Get subtitles from subdl.com
pub async fn get_subtitles_handler(
    Path((media_type, tmdb_id)): Path<(String, i32)>,
    Query(params): Query<SubtitleQuery>,
) -> Result<Json<Vec<ExtSubtitleTrack>>, ApiError> {
    let languages: Vec<&str> = params
        .lang
        .as_deref()
        .unwrap_or("fr,en")
        .split(',')
        .map(|s| s.trim())
        .collect();

    let client = SubtitleClient::new();
    let subtitles = client
        .search(
            tmdb_id,
            &media_type,
            params.season,
            params.episode,
            &languages,
        )
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Subtitle search failed: {}", e)))?;

    Ok(Json(subtitles))
}

/// GET /streaming/subtitles/vtt — Fetch and serve a subtitle file as VTT
#[derive(Debug, Deserialize)]
pub struct VttQuery {
    pub url: String,
}

pub async fn serve_subtitle_vtt_handler(
    Query(params): Query<VttQuery>,
) -> Result<impl IntoResponse, ApiError> {
    let client = SubtitleClient::new();
    let vtt = client
        .fetch_as_vtt(&params.url)
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Subtitle fetch failed: {}", e)))?;

    Ok((
        StatusCode::OK,
        [
            (header::CONTENT_TYPE, "text/vtt; charset=utf-8".to_string()),
            (header::ACCESS_CONTROL_ALLOW_ORIGIN, "*".to_string()),
        ],
        vtt,
    ))
}
