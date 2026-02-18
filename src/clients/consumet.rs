use crate::models::StreamSource;
use serde::Deserialize;
use std::collections::HashMap;

/// HTTP client for the self-hosted Consumet API (https://github.com/consumet/api.consumet.org).
///
/// Provider strategy:
///   - Movies : provider=Goku  (FlixHQ has SSL issues for movie watch calls)
///   - TV     : default provider (FlixHQ) — Goku crashes on TV info endpoint
///
/// Watch: `GET /meta/tmdb/watch/:episodeId?id=:mediaId[&provider=…]`
///   → returns `{ sources: [ { url, quality, isM3U8 } ], subtitles: [ { url, lang } ] }`
pub struct ConsumetClient {
    pub base_url: String,
    client: reqwest::Client,
}

// ── Response types ─────────────────────────────────────────────────────────

/// An episode inside a season (TV shows).
#[derive(Debug, Deserialize, Clone)]
pub struct ConsometEpisode {
    /// Consumet internal episode ID (used as episodeId in the watch call).
    /// Optional because some providers omit it for unavailable episodes.
    pub id: Option<String>,
    /// Episode number within the season.
    #[serde(alias = "episode")]
    pub number: Option<u32>,
    pub season: Option<u32>,
    pub title: Option<String>,
}

/// A season containing its episodes.
#[derive(Debug, Deserialize)]
pub struct ConsometSeason {
    pub season: u32,
    #[serde(default)]
    pub episodes: Vec<ConsometEpisode>,
}

/// Response from `/meta/tmdb/info/:id?type=movie|tv`.
#[derive(Debug, Deserialize)]
pub struct ConsometMediaInfo {
    /// Consumet media ID (e.g. "movie/inception-19764" or "tv/breaking-bad-39506").
    /// Optional: some providers return a different top-level structure.
    pub id: Option<String>,
    /// For movies: the episode ID to pass to the watch endpoint.
    #[serde(rename = "episodeId")]
    pub episode_id: Option<String>,
    /// For TV shows: list of seasons with their episodes.
    #[serde(default)]
    pub seasons: Vec<ConsometSeason>,
}

/// A single stream source from the watch endpoint.
#[derive(Debug, Deserialize, Clone)]
pub struct ConsometSource {
    pub url: String,
    pub quality: Option<String>,
    /// True = HLS (.m3u8) playlist, false = direct MP4.
    #[serde(rename = "isM3U8", default)]
    pub is_m3u8: bool,
}

/// A subtitle track from the watch endpoint.
#[derive(Debug, Deserialize, Clone)]
pub struct ConsometSubtitle {
    pub url: String,
    pub lang: String,
}

/// Response from `/meta/tmdb/watch/:episodeId?id=:mediaId`.
#[derive(Debug, Deserialize)]
pub struct ConsometWatchResponse {
    #[serde(default)]
    pub sources: Vec<ConsometSource>,
    pub subtitles: Option<Vec<ConsometSubtitle>>,
    /// Required HTTP headers to send when fetching the stream (e.g. Referer).
    pub headers: Option<HashMap<String, String>>,
}

// ── Client implementation ──────────────────────────────────────────────────

impl ConsumetClient {
    pub fn new(base_url: String) -> Self {
        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(30))
            .user_agent("Sokoul/3.0")
            .build()
            .unwrap_or_default();
        Self { base_url, client }
    }

    /// Fetch media metadata. `provider` is appended to the URL when set.
    async fn get_media_info(
        &self,
        tmdb_id: i32,
        media_type: &str,
        provider: Option<&str>,
    ) -> anyhow::Result<ConsometMediaInfo> {
        let mut url = format!(
            "{}/meta/tmdb/info/{}?type={}",
            self.base_url, tmdb_id, media_type
        );
        if let Some(p) = provider {
            url.push_str("&provider=");
            url.push_str(p);
        }

        let resp = self
            .client
            .get(&url)
            .send()
            .await
            .map_err(|e| anyhow::anyhow!("Consumet info request error: {}", e))?;

        if !resp.status().is_success() {
            anyhow::bail!(
                "Consumet info returned HTTP {} for {}",
                resp.status(),
                url
            );
        }

        resp.json::<ConsometMediaInfo>()
            .await
            .map_err(|e| anyhow::anyhow!("Consumet info parse error: {}", e))
    }

    /// Fetch direct stream sources for a given episode ID + media ID.
    async fn get_sources(
        &self,
        episode_id: &str,
        media_id: &str,
        provider: Option<&str>,
    ) -> anyhow::Result<ConsometWatchResponse> {
        let mut url = format!(
            "{}/meta/tmdb/watch/{}?id={}",
            self.base_url,
            urlencoding::encode(episode_id),
            urlencoding::encode(media_id)
        );
        if let Some(p) = provider {
            url.push_str("&provider=");
            url.push_str(p);
        }

        let resp = self
            .client
            .get(&url)
            .send()
            .await
            .map_err(|e| anyhow::anyhow!("Consumet watch request error: {}", e))?;

        if !resp.status().is_success() {
            anyhow::bail!(
                "Consumet watch returned HTTP {} for {}",
                resp.status(),
                url
            );
        }

        let watch = resp
            .json::<ConsometWatchResponse>()
            .await
            .map_err(|e| anyhow::anyhow!("Consumet watch parse error: {}", e))?;

        if watch.sources.is_empty() {
            anyhow::bail!(
                "Consumet watch returned empty sources for episodeId={} mediaId={}",
                episode_id,
                media_id
            );
        }

        Ok(watch)
    }

    /// Resolve a movie by TMDB ID → returns raw Consumet stream sources.
    /// Uses the Goku provider (more reliable than FlixHQ for movies).
    pub async fn resolve_movie(&self, tmdb_id: i32) -> anyhow::Result<Vec<StreamSource>> {
        let info = self.get_media_info(tmdb_id, "movie", Some("Goku")).await?;

        let episode_id = info.episode_id.as_deref().ok_or_else(|| {
            anyhow::anyhow!("Consumet returned no episodeId for movie tmdb_id={}", tmdb_id)
        })?;
        let fallback_id = format!("tmdb:{}", tmdb_id);
        let media_id = info.id.as_deref().unwrap_or(&fallback_id);

        let watch = self.get_sources(episode_id, media_id, Some("Goku")).await?;
        Ok(self.map_sources(watch))
    }

    /// Resolve a TV episode by TMDB ID, season and episode number.
    /// Uses the default provider (FlixHQ) — Goku does not support TV shows.
    pub async fn resolve_episode(
        &self,
        tmdb_id: i32,
        season: u32,
        ep: u32,
    ) -> anyhow::Result<Vec<StreamSource>> {
        // No provider → consumet defaults to FlixHQ for TV
        let info = self.get_media_info(tmdb_id, "tv", None).await?;

        let season_obj = info
            .seasons
            .iter()
            .find(|s| s.season == season)
            .ok_or_else(|| {
                anyhow::anyhow!("Season {} not found for tmdb_id={}", season, tmdb_id)
            })?;

        let episode_obj = season_obj
            .episodes
            .iter()
            .find(|e| e.number == Some(ep))
            .ok_or_else(|| {
                anyhow::anyhow!(
                    "Episode S{}E{} not found for tmdb_id={}",
                    season,
                    ep,
                    tmdb_id
                )
            })?;

        let episode_id = episode_obj.id.as_deref().ok_or_else(|| {
            anyhow::anyhow!(
                "Episode S{}E{} has no id for tmdb_id={}",
                season,
                ep,
                tmdb_id
            )
        })?;
        let fallback_id = format!("tmdb:{}", tmdb_id);
        let media_id = info.id.as_deref().unwrap_or(&fallback_id);

        let watch = self.get_sources(episode_id, media_id, None).await?;
        Ok(self.map_sources(watch))
    }

    /// HEAD request with short timeout to verify a stream URL is alive.
    #[allow(dead_code)]
    pub async fn check_alive(&self, url: &str) -> bool {
        let short_client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(3))
            .build()
            .unwrap_or_default();
        short_client
            .head(url)
            .send()
            .await
            .map(|r| r.status().is_success() || r.status().as_u16() == 405)
            .unwrap_or(false)
    }

    fn map_sources(&self, watch: ConsometWatchResponse) -> Vec<StreamSource> {
        let headers = watch.headers.clone();
        watch
            .sources
            .into_iter()
            .filter(|s| s.is_m3u8 || s.url.contains(".m3u8"))
            .map(|s| StreamSource {
                url: s.url,
                quality: s.quality.unwrap_or_else(|| "auto".to_string()),
                provider: "consumet".to_string(),
                has_vf: false,
                is_alive: true,
                audio_tracks: vec![],
                headers: headers.clone(),
            })
            .collect()
    }
}
