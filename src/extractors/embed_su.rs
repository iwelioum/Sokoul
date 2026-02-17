use async_trait::async_trait;
use reqwest::Client;
use std::collections::HashMap;

use super::{ExtractedStream, ExtractionResult, StreamExtractor, StreamType};

/// Embed.su extractor — often provides good HD sources.
pub struct EmbedSuExtractor;

#[async_trait]
impl StreamExtractor for EmbedSuExtractor {
    fn provider_name(&self) -> &str {
        "Embed.su"
    }

    fn french_priority(&self) -> u8 {
        2
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
            "tv" => format!("https://embed.su/embed/tv/{}/{}/{}", tmdb_id, s, e),
            _ => format!("https://embed.su/embed/movie/{}", tmdb_id),
        };

        tracing::debug!("Embed.su: fetching {}", embed_url);

        let page_html = match http_client
            .get(&embed_url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            )
            .header("Referer", "https://embed.su/")
            .send()
            .await
        {
            Ok(resp) => match resp.text().await {
                Ok(text) => text,
                Err(e) => {
                    return ExtractionResult {
                        provider: "Embed.su".to_string(),
                        streams: vec![],
                        error: Some(format!("Read error: {}", e)),
                    };
                }
            },
            Err(e) => {
                return ExtractionResult {
                    provider: "Embed.su".to_string(),
                    streams: vec![],
                    error: Some(format!("Fetch error: {}", e)),
                };
            }
        };

        let mut streams = Vec::new();
        let mut headers = HashMap::new();
        headers.insert("Referer".to_string(), "https://embed.su/".to_string());
        headers.insert("Origin".to_string(), "https://embed.su".to_string());

        // Extract m3u8 and mp4 URLs from the page
        extract_video_urls(&page_html, &headers, &mut streams, "Embed.su");

        // Follow iframes
        let iframe_srcs = find_iframe_srcs(&page_html);
        for iframe_src in iframe_srcs {
            let full_url = if iframe_src.starts_with("//") {
                format!("https:{}", iframe_src)
            } else if iframe_src.starts_with("/") {
                format!("https://embed.su{}", iframe_src)
            } else {
                iframe_src.clone()
            };

            if let Ok(resp) = http_client
                .get(&full_url)
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
                    if let Ok(parsed) = url::Url::parse(&full_url) {
                        sub_headers.insert(
                            "Referer".to_string(),
                            format!("{}://{}/", parsed.scheme(), parsed.host_str().unwrap_or("")),
                        );
                    }
                    extract_video_urls(&sub_html, &sub_headers, &mut streams, "Embed.su");
                }
            }
        }

        ExtractionResult {
            provider: "Embed.su".to_string(),
            streams,
            error: None,
        }
    }
}

fn extract_video_urls(
    html: &str,
    headers: &HashMap<String, String>,
    streams: &mut Vec<ExtractedStream>,
    provider: &str,
) {
    let extensions = [(".m3u8", StreamType::Hls), (".mp4", StreamType::Mp4)];

    for (ext, stream_type) in &extensions {
        let mut search_from = 0;
        while let Some(idx) = html[search_from..].find(ext) {
            let abs_idx = search_from + idx + ext.len();
            // Handle potential query strings after extension
            let end = html[abs_idx..]
                .find(|c: char| c == '"' || c == '\'' || c == '`' || c == ' ' || c == '\\')
                .map(|i| abs_idx + i)
                .unwrap_or(abs_idx);
            let before = &html[..end];

            for delim in ['"', '\'', '`'] {
                if let Some(start) = before.rfind(delim) {
                    let url = &before[start + 1..];
                    if url.starts_with("http") && !streams.iter().any(|s| s.url == url) {
                        streams.push(ExtractedStream {
                            provider: provider.to_string(),
                            url: url.to_string(),
                            quality: "auto".to_string(),
                            audio_lang: None,
                            headers: Some(headers.clone()),
                            stream_type: stream_type.clone(),
                            category: None,
                            language: None,
                        });
                        break;
                    }
                }
            }
            search_from = abs_idx;
        }
    }

    // Parse JSON source arrays in the HTML
    if let Some(idx) = html.find("\"sources\"") {
        let after = &html[idx..];
        if let Some(arr_start) = after.find('[') {
            if let Some(arr_end) = after[arr_start..].find(']') {
                let json_str = &after[arr_start..arr_start + arr_end + 1];
                if let Ok(sources) = serde_json::from_str::<Vec<serde_json::Value>>(json_str) {
                    for source in sources {
                        if let Some(file) = source
                            .get("file")
                            .or_else(|| source.get("src"))
                            .and_then(|f| f.as_str())
                        {
                            if !file.is_empty() && !streams.iter().any(|s| s.url == file) {
                                let st = if file.contains(".m3u8") {
                                    StreamType::Hls
                                } else {
                                    StreamType::Mp4
                                };
                                let label = source
                                    .get("label")
                                    .or_else(|| source.get("quality"))
                                    .and_then(|l| l.as_str())
                                    .unwrap_or("auto");
                                streams.push(ExtractedStream {
                                    provider: provider.to_string(),
                                    url: file.to_string(),
                                    quality: label.to_string(),
                                    audio_lang: None,
                                    headers: Some(headers.clone()),
                                    stream_type: st,
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

fn find_iframe_srcs(html: &str) -> Vec<String> {
    let mut srcs = Vec::new();
    let mut search_from = 0;
    while let Some(start) = html[search_from..].find("<iframe") {
        let abs_start = search_from + start;
        let section = &html[abs_start..html.len().min(abs_start + 500)];
        if let Some(src_start) = section.find("src=\"") {
            let after = &section[src_start + 5..];
            if let Some(src_end) = after.find('"') {
                let src = &after[..src_end];
                if !src.is_empty() && !src.starts_with("about:") {
                    srcs.push(src.to_string());
                }
            }
        }
        search_from = abs_start + 1;
    }
    srcs
}
