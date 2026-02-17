pub mod autoembed;
pub mod embed_su;
pub mod french;
pub mod headless;
pub mod hosters;
pub mod moviesapi;
pub mod registry;
pub mod vidsrc;

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum StreamType {
    Hls,
    Mp4,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedStream {
    pub provider: String,
    pub url: String,
    pub quality: String,
    pub audio_lang: Option<String>,
    pub headers: Option<HashMap<String, String>>,
    pub stream_type: StreamType,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub category: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub language: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SubtitleTrack {
    pub language: String,
    pub label: String,
    pub url: String,
    pub is_default: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractionResult {
    pub provider: String,
    pub streams: Vec<ExtractedStream>,
    pub error: Option<String>,
}

#[async_trait]
pub trait StreamExtractor: Send + Sync {
    fn provider_name(&self) -> &str;

    /// Whether this extractor needs a headless browser
    fn needs_browser(&self) -> bool {
        false
    }

    /// Priority for French content (higher = tried first)
    fn french_priority(&self) -> u8 {
        0
    }

    async fn extract(
        &self,
        tmdb_id: i32,
        media_type: &str,
        season: Option<i32>,
        episode: Option<i32>,
        http_client: &reqwest::Client,
        browser: Option<&playwright::api::Browser>,
    ) -> ExtractionResult;
}
