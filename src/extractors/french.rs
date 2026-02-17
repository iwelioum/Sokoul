//! French streaming aggregator extractor.
//!
//! Scrapes French streaming aggregator sites to find video hoster embed links
//! for a given TMDB ID. Then optionally resolves each hoster to get direct
//! m3u8/mp4 stream URLs.

use async_trait::async_trait;
use reqwest::Client;

use super::hosters;
use super::{ExtractedStream, ExtractionResult, StreamExtractor};

/// A French streaming aggregator extractor.
///
/// It searches a French aggregator site for content matching a TMDB ID,
/// extracts embed links from video hosters (Uqload, Supervideo, etc.),
/// and tries to resolve them to direct stream URLs.
pub struct FrenchAggregatorExtractor {
    /// Display name for this aggregator
    pub name: String,
    /// Base URL of the aggregator site
    pub base_url: String,
    /// Category tag for sources found (e.g., "Multi", "FStream", "Viper")
    pub category: String,
}

impl FrenchAggregatorExtractor {
    pub fn new(name: &str, base_url: &str, category: &str) -> Self {
        Self {
            name: name.to_string(),
            base_url: base_url.to_string(),
            category: category.to_string(),
        }
    }

    /// Build the URL to search/lookup content on this aggregator
    fn build_lookup_url(
        &self,
        tmdb_id: i32,
        media_type: &str,
        season: Option<i32>,
        episode: Option<i32>,
    ) -> Vec<String> {
        let s = season.unwrap_or(1);
        let e = episode.unwrap_or(1);
        let mut urls = Vec::new();

        // Try TMDB-ID based URL patterns (many French sites use these)
        match media_type {
            "tv" => {
                urls.push(format!(
                    "{}/serie/{}/saison-{}/episode-{}",
                    self.base_url, tmdb_id, s, e
                ));
                urls.push(format!("{}/serie/{}", self.base_url, tmdb_id));
                urls.push(format!("{}/tv/{}/{}/{}", self.base_url, tmdb_id, s, e));
            }
            _ => {
                urls.push(format!("{}/film/{}", self.base_url, tmdb_id));
                urls.push(format!("{}/movie/{}", self.base_url, tmdb_id));
            }
        }

        urls
    }

    /// Extract iframe embed URLs from an aggregator page HTML
    fn extract_embeds(&self, html: &str) -> Vec<EmbedInfo> {
        let mut embeds = Vec::new();

        // Extract all iframe src attributes
        let mut search_from = 0;
        while let Some(iframe_start) = html[search_from..].find("<iframe") {
            let abs_start = search_from + iframe_start;
            let section = &html[abs_start..html.len().min(abs_start + 2000)];

            if let Some(src) = extract_attr(section, "src") {
                let url = if src.starts_with("//") {
                    format!("https:{}", src)
                } else if src.starts_with('/') {
                    format!("{}{}", self.base_url, src)
                } else {
                    src.clone()
                };

                if url.starts_with("http") && !url.contains("about:") {
                    let language = detect_language_context(html, abs_start);
                    let hoster_name = detect_hoster_name(&url);

                    embeds.push(EmbedInfo {
                        url,
                        hoster: hoster_name,
                        language,
                    });
                }
            }

            search_from = abs_start + 1;
        }

        // Also look for data-src or data-url attributes (lazy-loaded embeds)
        let lazy_patterns = ["data-src=\"", "data-url=\"", "data-embed=\""];
        for pattern in &lazy_patterns {
            let mut search_from = 0;
            while let Some(idx) = html[search_from..].find(pattern) {
                let abs_start = search_from + idx + pattern.len();
                if let Some(end) = html[abs_start..].find('"') {
                    let url_raw = &html[abs_start..abs_start + end];
                    let url = if url_raw.starts_with("//") {
                        format!("https:{}", url_raw)
                    } else {
                        url_raw.to_string()
                    };

                    if url.starts_with("http") {
                        let hoster_name = detect_hoster_name(&url);
                        let language = detect_language_context(html, abs_start);

                        if !embeds.iter().any(|e| e.url == url) {
                            embeds.push(EmbedInfo {
                                url,
                                hoster: hoster_name,
                                language,
                            });
                        }
                    }
                }
                search_from = abs_start + 1;
            }
        }

        embeds
    }
}

#[derive(Debug, Clone)]
struct EmbedInfo {
    url: String,
    hoster: String,
    language: String, // "VF", "VOSTFR", or "Multi"
}

#[async_trait]
impl StreamExtractor for FrenchAggregatorExtractor {
    fn provider_name(&self) -> &str {
        &self.name
    }

    fn needs_browser(&self) -> bool {
        false // We use HTTP scraping
    }

    fn french_priority(&self) -> u8 {
        10 // Higher than all international providers
    }

    async fn extract(
        &self,
        tmdb_id: i32,
        media_type: &str,
        season: Option<i32>,
        episode: Option<i32>,
        http_client: &Client,
        _browser: Option<&playwright::api::Browser>,
    ) -> ExtractionResult {
        let lookup_urls = self.build_lookup_url(tmdb_id, media_type, season, episode);

        let mut page_html = None;

        // Try each lookup URL until one works
        for url in &lookup_urls {
            tracing::debug!("[{}] Trying lookup: {}", self.name, url);

            match fetch_aggregator_page(http_client, url, &self.base_url).await {
                Ok(html) => {
                    // Check if page has actual content (not a 404 page)
                    if html.contains("<iframe")
                        || html.contains("data-src")
                        || html.contains("data-url")
                        || html.contains("player")
                    {
                        page_html = Some(html);
                        break;
                    }
                }
                Err(e) => {
                    tracing::debug!("[{}] Lookup failed for {}: {}", self.name, url, e);
                }
            }
        }

        let html = match page_html {
            Some(h) => h,
            None => {
                return ExtractionResult {
                    provider: self.name.clone(),
                    streams: vec![],
                    error: Some(format!("No content found on {}", self.name)),
                };
            }
        };

        // Extract embed URLs from the page
        let embeds = self.extract_embeds(&html);

        if embeds.is_empty() {
            return ExtractionResult {
                provider: self.name.clone(),
                streams: vec![],
                error: Some("No embeds found on page".to_string()),
            };
        }

        tracing::info!(
            "[{}] Found {} embed(s) for TMDB {} ({})",
            self.name,
            embeds.len(),
            tmdb_id,
            media_type
        );

        // Try to resolve each embed to a direct stream
        let mut streams = Vec::new();

        for embed in &embeds {
            match hosters::resolve_hoster(http_client, &embed.url).await {
                Some(resolved) => {
                    streams.push(ExtractedStream {
                        provider: format!("{} ({})", self.name, embed.hoster),
                        url: resolved.url,
                        quality: resolved.quality,
                        audio_lang: if embed.language == "VF" {
                            Some("fr".to_string())
                        } else {
                            None
                        },
                        headers: if resolved.headers.is_empty() {
                            None
                        } else {
                            Some(resolved.headers)
                        },
                        stream_type: resolved.stream_type,
                        category: Some(self.category.clone()),
                        language: Some(embed.language.clone()),
                    });
                }
                None => {
                    tracing::debug!(
                        "[{}] Could not resolve hoster {} at {}",
                        self.name,
                        embed.hoster,
                        embed.url
                    );
                }
            }
        }

        if !streams.is_empty() {
            tracing::info!(
                "[{}] Resolved {}/{} stream(s)",
                self.name,
                streams.len(),
                embeds.len()
            );
        }

        ExtractionResult {
            provider: self.name.clone(),
            streams,
            error: None,
        }
    }
}

// ── Helper functions ─────────────────────────────────────────────────

/// Fetch an aggregator page with FR-appropriate headers
async fn fetch_aggregator_page(
    client: &Client,
    url: &str,
    base_url: &str,
) -> anyhow::Result<String> {
    let resp = client
        .get(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
        .header("Referer", format!("{}/", base_url))
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.5")
        .send()
        .await?;

    if !resp.status().is_success() {
        return Err(anyhow::anyhow!("HTTP {}", resp.status()));
    }

    Ok(resp.text().await?)
}

/// Extract an HTML attribute value from a tag string
fn extract_attr(tag_html: &str, attr_name: &str) -> Option<String> {
    let patterns = [format!("{}=\"", attr_name), format!("{}='", attr_name)];

    for pattern in &patterns {
        if let Some(idx) = tag_html.find(pattern.as_str()) {
            let after = &tag_html[idx + pattern.len()..];
            let delim = if pattern.ends_with('"') { '"' } else { '\'' };
            if let Some(end) = after.find(delim) {
                return Some(after[..end].to_string());
            }
        }
    }
    None
}

/// Detect the language context (VF/VOSTFR) near an embed position
fn detect_language_context(html: &str, position: usize) -> String {
    // Look at the surrounding ~500 chars before the embed for language indicators
    let context_start = position.saturating_sub(500);
    let context = &html[context_start..position.min(html.len())];
    let context_lower = context.to_lowercase();

    if context_lower.contains("vostfr")
        || context_lower.contains("vo sous-titr")
        || context_lower.contains("sous-titré")
    {
        "VOSTFR".to_string()
    } else if context_lower.contains("vf ")
        || context_lower.contains("vf\"")
        || context_lower.contains("vf'")
        || context_lower.contains("french")
        || context_lower.contains("français")
        || context_lower.contains("version française")
    {
        "VF".to_string()
    } else {
        "Multi".to_string()
    }
}

/// Detect the hoster name from a URL
fn detect_hoster_name(url: &str) -> String {
    let host = url::Url::parse(url)
        .ok()
        .and_then(|u| u.host_str().map(|h| h.to_lowercase()))
        .unwrap_or_default();

    if host.contains("uqload") {
        "Uqload".to_string()
    } else if host.contains("supervideo") {
        "SuperVideo".to_string()
    } else if host.contains("voe") {
        "VoeSx".to_string()
    } else if host.contains("doodstream") || host.contains("dood") {
        "DoodStream".to_string()
    } else if host.contains("vidmoly") {
        "VidMoly".to_string()
    } else if host.contains("netu") || host.contains("hqq") {
        "Netu".to_string()
    } else if host.contains("vidoza") {
        "Vidoza".to_string()
    } else if host.contains("lulustream") || host.contains("lulu") {
        "LuluStream".to_string()
    } else if host.contains("darkivideo") || host.contains("darki") {
        "DarkiVideo".to_string()
    } else if host.contains("wish") {
        "WishVideo".to_string()
    } else if host.contains("vidzy") {
        "Vidzy".to_string()
    } else if host.contains("veed") {
        "Veed".to_string()
    } else {
        // Use the domain as name
        host.split('.').next().unwrap_or("Unknown").to_string()
    }
}
