use async_trait::async_trait;
use reqwest::Client;
use serde::Deserialize;
use std::collections::HashMap;

use super::{ExtractedStream, ExtractionResult, StreamExtractor, StreamType};

/// VidSrc.cc extractor — fetches the embed page and extracts m3u8 source URLs.
/// VidSrc uses an internal API that returns source server URLs.
pub struct VidSrcExtractor;

#[derive(Debug, Deserialize)]
struct VidSrcApiResponse {
    #[serde(default)]
    sources: Vec<VidSrcSource>,
}

#[derive(Debug, Deserialize)]
struct VidSrcSource {
    #[serde(default)]
    file: String,
    #[serde(default)]
    label: Option<String>,
    #[serde(default, rename = "type")]
    source_type: Option<String>,
}

#[async_trait]
impl StreamExtractor for VidSrcExtractor {
    fn provider_name(&self) -> &str {
        "VidSrc"
    }

    fn french_priority(&self) -> u8 {
        3
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
        let s = season.unwrap_or(1);
        let e = episode.unwrap_or(1);

        let embed_url = match media_type {
            "tv" => format!("https://vidsrc.cc/v2/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://vidsrc.cc/v2/embed/movie/{}", tmdb_id),
        };

        tracing::debug!("VidSrc: fetching embed page {}", embed_url);

        // Fetch the embed page to find the internal API endpoint
        let page_html = match http_client
            .get(&embed_url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            )
            .header("Referer", "https://vidsrc.cc/")
            .send()
            .await
        {
            Ok(resp) => match resp.text().await {
                Ok(text) => text,
                Err(e) => {
                    return ExtractionResult {
                        provider: "VidSrc".to_string(),
                        streams: vec![],
                        error: Some(format!("Failed to read response: {}", e)),
                    };
                }
            },
            Err(e) => {
                return ExtractionResult {
                    provider: "VidSrc".to_string(),
                    streams: vec![],
                    error: Some(format!("Failed to fetch embed page: {}", e)),
                };
            }
        };

        // Try to extract m3u8 URLs from the page
        let mut streams = Vec::new();
        let mut headers = HashMap::new();
        headers.insert("Referer".to_string(), "https://vidsrc.cc/".to_string());
        headers.insert("Origin".to_string(), "https://vidsrc.cc".to_string());

        // Pattern 1: Look for direct m3u8 URLs in the page source
        for line in page_html.lines() {
            if let Some(url) = extract_m3u8_url(line) {
                streams.push(ExtractedStream {
                    provider: "VidSrc".to_string(),
                    url,
                    quality: "auto".to_string(),
                    audio_lang: None,
                    headers: Some(headers.clone()),
                    stream_type: StreamType::Hls,
                    category: None,
                    language: None,
                });
            }
        }

        // Pattern 2: Look for source API endpoint (data-src, src= patterns)
        let api_urls = extract_api_urls(&page_html);
        for api_url in api_urls {
            match fetch_source_from_api(http_client, &api_url, &embed_url).await {
                Ok(mut extracted) => streams.append(&mut extracted),
                Err(e) => {
                    tracing::debug!("VidSrc API {} failed: {}", api_url, e);
                }
            }
        }

        // Pattern 3: Look for iframe src that may contain the actual player
        let iframe_srcs = extract_iframe_srcs(&page_html);
        for iframe_src in iframe_srcs {
            if iframe_src.contains(".m3u8") {
                streams.push(ExtractedStream {
                    provider: "VidSrc".to_string(),
                    url: iframe_src,
                    quality: "auto".to_string(),
                    audio_lang: None,
                    headers: Some(headers.clone()),
                    stream_type: StreamType::Hls,
                    category: None,
                    language: None,
                });
            } else if !iframe_src.starts_with("about:") {
                // Try fetching the iframe page for m3u8
                match extract_from_sub_page(http_client, &iframe_src, &embed_url).await {
                    Ok(mut extracted) => streams.append(&mut extracted),
                    Err(e) => {
                        tracing::debug!("VidSrc sub-page {} failed: {}", iframe_src, e);
                    }
                }
            }
        }

        ExtractionResult {
            provider: "VidSrc".to_string(),
            streams,
            error: None,
        }
    }
}

fn extract_m3u8_url(text: &str) -> Option<String> {
    // Look for patterns like "https://...m3u8" or 'https://...m3u8'
    let patterns = [".m3u8", "master.m3u8", "index.m3u8"];
    for pattern in patterns {
        if let Some(idx) = text.find(pattern) {
            // Walk backwards to find the start of the URL
            let before = &text[..idx + pattern.len()];
            for delim in ['"', '\'', '`'] {
                if let Some(start) = before.rfind(delim) {
                    let url = &before[start + 1..];
                    if url.starts_with("http") {
                        return Some(url.to_string());
                    }
                }
            }
        }
    }
    None
}

fn extract_api_urls(html: &str) -> Vec<String> {
    let mut urls = Vec::new();
    // Look for patterns like fetch("/api/...", data-src="...", or src="/api/..."
    let patterns = ["data-src=\"", "data-url=\"", "fetch(\"", "src=\"/api"];
    for pattern in patterns {
        let mut search_from = 0;
        while let Some(start) = html[search_from..].find(pattern) {
            let abs_start = search_from + start + pattern.len();
            if let Some(end) = html[abs_start..].find('"') {
                let url = &html[abs_start..abs_start + end];
                if url.starts_with("http") || url.starts_with("/") {
                    let full_url = if url.starts_with("/") {
                        format!("https://vidsrc.cc{}", url)
                    } else {
                        url.to_string()
                    };
                    urls.push(full_url);
                }
            }
            search_from = abs_start;
        }
    }
    urls
}

fn extract_iframe_srcs(html: &str) -> Vec<String> {
    let mut srcs = Vec::new();
    let pattern = "iframe";
    let mut search_from = 0;
    while let Some(start) = html[search_from..].find(pattern) {
        let abs_start = search_from + start;
        let section = &html[abs_start..html.len().min(abs_start + 500)];
        if let Some(src_start) = section.find("src=\"") {
            let after_src = &section[src_start + 5..];
            if let Some(src_end) = after_src.find('"') {
                let src = &after_src[..src_end];
                if !src.is_empty() {
                    let full = if src.starts_with("//") {
                        format!("https:{}", src)
                    } else if src.starts_with("/") {
                        format!("https://vidsrc.cc{}", src)
                    } else {
                        src.to_string()
                    };
                    srcs.push(full);
                }
            }
        }
        search_from = abs_start + 1;
    }
    srcs
}

async fn fetch_source_from_api(
    client: &Client,
    api_url: &str,
    referer: &str,
) -> anyhow::Result<Vec<ExtractedStream>> {
    let resp = client
        .get(api_url)
        .header("Referer", referer)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        )
        .send()
        .await?;

    let body = resp.text().await?;
    let mut streams = Vec::new();
    let mut headers = HashMap::new();
    headers.insert("Referer".to_string(), "https://vidsrc.cc/".to_string());

    // Try parsing as JSON with sources
    if let Ok(api_resp) = serde_json::from_str::<VidSrcApiResponse>(&body) {
        for source in api_resp.sources {
            if !source.file.is_empty() {
                let stream_type = if source.file.contains(".m3u8") {
                    StreamType::Hls
                } else {
                    StreamType::Mp4
                };
                streams.push(ExtractedStream {
                    provider: "VidSrc".to_string(),
                    url: source.file,
                    quality: source.label.unwrap_or_else(|| "auto".to_string()),
                    audio_lang: None,
                    headers: Some(headers.clone()),
                    stream_type,
                    category: None,
                    language: None,
                });
            }
        }
    }

    // Also scan the body text for m3u8 URLs
    for line in body.lines() {
        if let Some(url) = extract_m3u8_url(line) {
            if !streams.iter().any(|s| s.url == url) {
                streams.push(ExtractedStream {
                    provider: "VidSrc".to_string(),
                    url,
                    quality: "auto".to_string(),
                    audio_lang: None,
                    headers: Some(headers.clone()),
                    stream_type: StreamType::Hls,
                    category: None,
                    language: None,
                });
            }
        }
    }

    Ok(streams)
}

async fn extract_from_sub_page(
    client: &Client,
    url: &str,
    referer: &str,
) -> anyhow::Result<Vec<ExtractedStream>> {
    let resp = client
        .get(url)
        .header("Referer", referer)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        )
        .send()
        .await?;

    let body = resp.text().await?;
    let mut streams = Vec::new();
    let mut headers = HashMap::new();

    // Use the sub-page domain as referer
    if let Ok(parsed) = url::Url::parse(url) {
        headers.insert(
            "Referer".to_string(),
            format!("{}://{}/", parsed.scheme(), parsed.host_str().unwrap_or("")),
        );
    }

    for line in body.lines() {
        if let Some(m3u8_url) = extract_m3u8_url(line) {
            streams.push(ExtractedStream {
                provider: "VidSrc".to_string(),
                url: m3u8_url,
                quality: "auto".to_string(),
                audio_lang: None,
                headers: Some(headers.clone()),
                stream_type: StreamType::Hls,
                category: None,
                language: None,
            });
        }
    }

    // Also look for API URLs in this page
    let api_urls = extract_api_urls(&body);
    for api_url in api_urls {
        if let Ok(mut extracted) = fetch_source_from_api(client, &api_url, url).await {
            streams.append(&mut extracted);
        }
    }

    Ok(streams)
}
