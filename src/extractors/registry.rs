use super::{ExtractedStream, ExtractionResult, StreamExtractor};
use std::cmp::Reverse;
use tokio::time::{timeout, Duration};

pub struct ExtractorRegistry {
    extractors: Vec<Box<dyn StreamExtractor>>,
}

impl ExtractorRegistry {
    pub fn new() -> Self {
        Self {
            extractors: Vec::new(),
        }
    }

    pub fn register(&mut self, extractor: Box<dyn StreamExtractor>) {
        self.extractors.push(extractor);
    }

    /// Run all extractors with a per-provider timeout.
    /// Extractors are executed in descending `french_priority()` order.
    pub async fn extract_all(
        &self,
        tmdb_id: i32,
        media_type: &str,
        season: Option<i32>,
        episode: Option<i32>,
        http_client: &reqwest::Client,
        browser: Option<&playwright::api::Browser>,
    ) -> Vec<ExtractionResult> {
        let mut handles = Vec::new();

        // Try FR-friendly providers first (higher priority first)
        let mut extractors: Vec<&Box<dyn StreamExtractor>> = self.extractors.iter().collect();
        extractors.sort_by_key(|e| Reverse(e.french_priority()));

        for extractor in extractors {
            let provider_name = extractor.provider_name().to_string();
            let needs_browser = extractor.needs_browser();

            // Skip browser-based extractors if no browser available
            if needs_browser && browser.is_none() {
                tracing::debug!("Skipping {} (needs browser, none available)", provider_name);
                continue;
            }

            // We can't move the extractor into the task easily due to lifetimes,
            // so we'll run them sequentially with timeout per provider.
            // Browser-based extractors get more time
            let timeout_secs = if needs_browser { 20 } else { 8 };
            let result = timeout(
                Duration::from_secs(timeout_secs),
                extractor.extract(tmdb_id, media_type, season, episode, http_client, browser),
            )
            .await;

            match result {
                Ok(extraction) => {
                    if extraction.error.is_some() {
                        tracing::warn!("Extractor {} error: {:?}", provider_name, extraction.error);
                    }
                    if !extraction.streams.is_empty() {
                        tracing::info!(
                            "Extractor {} found {} stream(s)",
                            provider_name,
                            extraction.streams.len()
                        );
                    }
                    handles.push(extraction);
                }
                Err(_) => {
                    tracing::warn!(
                        "Extractor {} timed out after {}s",
                        provider_name,
                        timeout_secs
                    );
                    handles.push(ExtractionResult {
                        provider: provider_name,
                        streams: vec![],
                        error: Some("Timeout".to_string()),
                    });
                }
            }
        }

        handles
    }

    /// Sort streams: FR audio first, then FR-friendly providers, then best quality.
    pub fn sort_streams(streams: &mut Vec<ExtractedStream>) {
        streams.sort_by(|a, b| {
            let a_fr = a.audio_lang.as_deref() == Some("fr");
            let b_fr = b.audio_lang.as_deref() == Some("fr");
            let a_p = provider_priority(&a.provider);
            let b_p = provider_priority(&b.provider);

            b_fr.cmp(&a_fr).then_with(|| b_p.cmp(&a_p)).then_with(|| {
                let a_q = quality_rank(&a.quality);
                let b_q = quality_rank(&b.quality);
                b_q.cmp(&a_q)
            })
        });
    }

    pub fn list_names(&self) -> Vec<&str> {
        self.extractors.iter().map(|e| e.provider_name()).collect()
    }
}

fn provider_priority(provider: &str) -> u8 {
    match provider {
        "AutoEmbed" => 5,
        "VidSrc" => 4,
        "MoviesAPI" => 3,
        "SuperEmbed" => 3,
        "VidSrc.pro" => 3,
        "Embed.su" => 2,
        "Smashy" => 2,
        _ => 0,
    }
}

fn quality_rank(quality: &str) -> u32 {
    match quality {
        "4K" | "2160p" => 4,
        "1080p" | "FHD" => 3,
        "720p" | "HD" => 2,
        "480p" | "SD" => 1,
        "360p" => 0,
        "auto" => 3, // Assume auto = high
        _ => 1,
    }
}
