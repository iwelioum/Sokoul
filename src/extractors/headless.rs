use async_trait::async_trait;
use futures::StreamExt;
use reqwest::Client;
use std::collections::HashMap;

use super::{ExtractedStream, ExtractionResult, StreamExtractor, StreamType};

/// Generic headless browser extractor.
/// Loads an embed page in Playwright, intercepts all network responses,
/// and captures any m3u8/mp4 URLs that the page's JS player requests.
pub struct HeadlessExtractor {
    pub name: String,
    pub embed_url_movie: String,
    pub embed_url_tv: String,
    pub priority: u8,
}

impl HeadlessExtractor {
    pub fn new(name: &str, movie_pattern: &str, tv_pattern: &str, priority: u8) -> Self {
        Self {
            name: name.to_string(),
            embed_url_movie: movie_pattern.to_string(),
            embed_url_tv: tv_pattern.to_string(),
            priority,
        }
    }

    fn build_url(&self, tmdb_id: i32, media_type: &str, season: i32, episode: i32) -> String {
        match media_type {
            "tv" => self
                .embed_url_tv
                .replace("{id}", &tmdb_id.to_string())
                .replace("{s}", &season.to_string())
                .replace("{e}", &episode.to_string()),
            _ => self.embed_url_movie.replace("{id}", &tmdb_id.to_string()),
        }
    }
}

#[async_trait]
impl StreamExtractor for HeadlessExtractor {
    fn provider_name(&self) -> &str {
        &self.name
    }

    fn needs_browser(&self) -> bool {
        true
    }

    fn french_priority(&self) -> u8 {
        self.priority
    }

    async fn extract(
        &self,
        tmdb_id: i32,
        media_type: &str,
        season: Option<i32>,
        episode: Option<i32>,
        _http_client: &Client,
        browser: Option<&playwright::api::Browser>,
    ) -> ExtractionResult {
        let browser = match browser {
            Some(b) => b,
            None => {
                return ExtractionResult {
                    provider: self.name.clone(),
                    streams: vec![],
                    error: Some("No browser available".to_string()),
                };
            }
        };

        let s = season.unwrap_or(1);
        let e = episode.unwrap_or(1);
        let url = self.build_url(tmdb_id, media_type, s, e);
        let provider_name = self.name.clone();

        tracing::info!("[{}] Headless extraction: {}", provider_name, url);

        // Create a new context with a realistic user agent
        let context = match browser
            .context_builder()
            .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .ignore_https_errors(true)
            .build()
            .await
        {
            Ok(ctx) => ctx,
            Err(e) => {
                return ExtractionResult {
                    provider: provider_name,
                    streams: vec![],
                    error: Some(format!("Failed to create context: {}", e)),
                };
            }
        };

        let page = match context.new_page().await {
            Ok(p) => p,
            Err(e) => {
                let _ = context.close().await;
                return ExtractionResult {
                    provider: provider_name,
                    streams: vec![],
                    error: Some(format!("Failed to create page: {}", e)),
                };
            }
        };

        // Subscribe to page events BEFORE navigation
        let mut event_stream = match page.subscribe_event() {
            Ok(s) => s,
            Err(e) => {
                let _ = context.close().await;
                return ExtractionResult {
                    provider: provider_name,
                    streams: vec![],
                    error: Some(format!("Failed to subscribe events: {}", e)),
                };
            }
        };

        // Navigate to the embed URL
        let goto_result = page.goto_builder(&url).goto().await;
        if let Err(e) = goto_result {
            tracing::warn!("[{}] Navigation failed: {}", provider_name, e);
            // Don't return yet - some pages still fire network events even after nav error
        }

        // Collect m3u8/mp4 URLs from network responses for up to 12 seconds
        let mut found_urls: Vec<(String, String)> = Vec::new(); // (url, referer)
        let collect_timeout = tokio::time::Duration::from_secs(12);

        let _ = tokio::time::timeout(collect_timeout, async {
            while let Some(Ok(evt)) = event_stream.next().await {
                if let playwright::api::page::Event::Response(response) = evt {
                    if let Ok(resp_url) = response.url() {
                        let is_m3u8 = resp_url.contains(".m3u8")
                            || resp_url.contains("master.m3u8")
                            || resp_url.contains("index.m3u8");
                        let is_mp4 = resp_url.contains(".mp4")
                            && !resp_url.contains(".mp4.seg")
                            && !resp_url.contains("favicon");
                        let is_mpd = resp_url.contains(".mpd");

                        if is_m3u8 || is_mp4 || is_mpd {
                            // Get the referer from the request
                            let referer = response
                                .request()
                                .headers()
                                .ok()
                                .and_then(|h| h.get("referer").cloned())
                                .unwrap_or_default();

                            tracing::info!(
                                "[{}] Captured stream URL: {}",
                                provider_name,
                                &resp_url[..resp_url.len().min(100)]
                            );

                            if !found_urls.iter().any(|(u, _)| u == &resp_url) {
                                found_urls.push((resp_url, referer));
                            }

                            // If we found an m3u8 master playlist, we can stop early
                            if is_m3u8 && found_urls.len() >= 1 {
                                break;
                            }
                        }
                    }
                }
            }
        })
        .await;

        // Close context
        let _ = context.close().await;

        // Build extracted streams from captured URLs
        let streams: Vec<ExtractedStream> = found_urls
            .into_iter()
            .map(|(stream_url, referer)| {
                let stream_type = if stream_url.contains(".m3u8") {
                    StreamType::Hls
                } else if stream_url.contains(".mpd") {
                    StreamType::Hls // treat DASH as HLS for simplicity
                } else {
                    StreamType::Mp4
                };

                let mut headers = HashMap::new();
                if !referer.is_empty() {
                    headers.insert("Referer".to_string(), referer.clone());
                    if let Ok(parsed) = url::Url::parse(&referer) {
                        headers.insert(
                            "Origin".to_string(),
                            format!("{}://{}", parsed.scheme(), parsed.host_str().unwrap_or("")),
                        );
                    }
                }

                ExtractedStream {
                    provider: provider_name.clone(),
                    url: stream_url,
                    quality: "auto".to_string(),
                    audio_lang: None,
                    headers: if headers.is_empty() {
                        None
                    } else {
                        Some(headers)
                    },
                    stream_type,
                    category: None,
                    language: None,
                }
            })
            .collect();

        tracing::info!(
            "[{}] Headless extraction complete: {} stream(s)",
            self.name,
            streams.len()
        );

        ExtractionResult {
            provider: self.name.clone(),
            streams,
            error: None,
        }
    }
}
