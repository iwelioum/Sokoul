use async_trait::async_trait;
use reqwest::Client;
use std::collections::HashMap;

use super::{ExtractedStream, ExtractionResult, StreamExtractor, StreamType};

/// AutoEmbed extractor — player.autoembed.cc
/// Has good coverage for French content and often exposes direct m3u8 URLs.
pub struct AutoEmbedExtractor;

#[async_trait]
impl StreamExtractor for AutoEmbedExtractor {
    fn provider_name(&self) -> &str {
        "AutoEmbed"
    }

    fn french_priority(&self) -> u8 {
        5 // Good for French content
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
            "tv" => format!(
                "https://player.autoembed.cc/embed/tv/{}/{}/{}",
                tmdb_id, s, e
            ),
            _ => format!("https://player.autoembed.cc/embed/movie/{}", tmdb_id),
        };

        tracing::debug!("AutoEmbed: fetching {}", embed_url);

        let page_html = match http_client
            .get(&embed_url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            )
            .header("Referer", "https://player.autoembed.cc/")
            .send()
            .await
        {
            Ok(resp) => match resp.text().await {
                Ok(text) => text,
                Err(e) => {
                    return ExtractionResult {
                        provider: "AutoEmbed".to_string(),
                        streams: vec![],
                        error: Some(format!("Read error: {}", e)),
                    };
                }
            },
            Err(e) => {
                return ExtractionResult {
                    provider: "AutoEmbed".to_string(),
                    streams: vec![],
                    error: Some(format!("Fetch error: {}", e)),
                };
            }
        };

        let mut streams = Vec::new();
        let mut headers = HashMap::new();
        headers.insert(
            "Referer".to_string(),
            "https://player.autoembed.cc/".to_string(),
        );

        // AutoEmbed often has m3u8 URLs directly in the page or in JSON config
        extract_streams_from_html(&page_html, &headers, &mut streams);

        // Look for sub-iframes and follow them
        let iframe_srcs = extract_iframe_srcs(&page_html, "https://player.autoembed.cc");
        for iframe_src in iframe_srcs {
            if let Ok(resp) = http_client
                .get(&iframe_src)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                )
                .header("Referer", &embed_url)
                .send()
                .await
            {
                if let Ok(sub_html) = resp.text().await {
                    let mut sub_headers = HashMap::new();
                    if let Ok(parsed) = url::Url::parse(&iframe_src) {
                        sub_headers.insert(
                            "Referer".to_string(),
                            format!("{}://{}/", parsed.scheme(), parsed.host_str().unwrap_or("")),
                        );
                    }
                    extract_streams_from_html(&sub_html, &sub_headers, &mut streams);
                }
            }
        }

        ExtractionResult {
            provider: "AutoEmbed".to_string(),
            streams,
            error: None,
        }
    }
}

fn extract_streams_from_html(
    html: &str,
    headers: &HashMap<String, String>,
    streams: &mut Vec<ExtractedStream>,
) {
    // Look for m3u8 URLs
    let m3u8_pattern = ".m3u8";
    let mut search_from = 0;
    while let Some(idx) = html[search_from..].find(m3u8_pattern) {
        let abs_idx = search_from + idx + m3u8_pattern.len();
        let before = &html[..abs_idx];
        // Find URL start
        for delim in ['"', '\'', '`'] {
            if let Some(start) = before.rfind(delim) {
                let url = &before[start + 1..];
                if url.starts_with("http") && !streams.iter().any(|s| s.url == url) {
                    streams.push(ExtractedStream {
                        provider: "AutoEmbed".to_string(),
                        url: url.to_string(),
                        quality: "auto".to_string(),
                        audio_lang: None,
                        headers: Some(headers.clone()),
                        stream_type: StreamType::Hls,
                        category: None,
                        language: None,
                    });
                    break;
                }
            }
        }
        search_from = abs_idx;
    }

    // Also look for mp4 direct URLs
    let mp4_pattern = ".mp4";
    search_from = 0;
    while let Some(idx) = html[search_from..].find(mp4_pattern) {
        let abs_idx = search_from + idx + mp4_pattern.len();
        let before = &html[..abs_idx];
        for delim in ['"', '\'', '`'] {
            if let Some(start) = before.rfind(delim) {
                let url = &before[start + 1..];
                if url.starts_with("http")
                    && !url.contains("javascript")
                    && !streams.iter().any(|s| s.url == url)
                {
                    streams.push(ExtractedStream {
                        provider: "AutoEmbed".to_string(),
                        url: url.to_string(),
                        quality: "auto".to_string(),
                        audio_lang: None,
                        headers: Some(headers.clone()),
                        stream_type: StreamType::Mp4,
                        category: None,
                        language: None,
                    });
                    break;
                }
            }
        }
        search_from = abs_idx;
    }

    // Look for JSON-embedded sources: "file":"url" or "sources":[{...}]
    if let Some(sources_start) = html.find("\"sources\"") {
        let after = &html[sources_start..];
        if let Some(arr_start) = after.find('[') {
            if let Some(arr_end) = after[arr_start..].find(']') {
                let json_str = &after[arr_start..arr_start + arr_end + 1];
                if let Ok(sources) = serde_json::from_str::<Vec<serde_json::Value>>(json_str) {
                    for source in sources {
                        if let Some(file) = source.get("file").and_then(|f| f.as_str()) {
                            if !file.is_empty() && !streams.iter().any(|s| s.url == file) {
                                let stream_type = if file.contains(".m3u8") {
                                    StreamType::Hls
                                } else {
                                    StreamType::Mp4
                                };
                                let label = source
                                    .get("label")
                                    .and_then(|l| l.as_str())
                                    .unwrap_or("auto");
                                streams.push(ExtractedStream {
                                    provider: "AutoEmbed".to_string(),
                                    url: file.to_string(),
                                    quality: label.to_string(),
                                    audio_lang: None,
                                    headers: Some(headers.clone()),
                                    stream_type,
                                    category: None,
                                    language: None,
                                });
                            }
                        }
                    }
                }
            }
        }
    }
}

fn extract_iframe_srcs(html: &str, base_url: &str) -> Vec<String> {
    let mut srcs = Vec::new();
    let mut search_from = 0;
    while let Some(start) = html[search_from..].find("iframe") {
        let abs_start = search_from + start;
        let section = &html[abs_start..html.len().min(abs_start + 500)];
        if let Some(src_start) = section.find("src=\"") {
            let after = &section[src_start + 5..];
            if let Some(src_end) = after.find('"') {
                let src = &after[..src_end];
                if !src.is_empty() && !src.starts_with("about:") {
                    let full = if src.starts_with("//") {
                        format!("https:{}", src)
                    } else if src.starts_with("/") {
                        format!("{}{}", base_url, src)
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
