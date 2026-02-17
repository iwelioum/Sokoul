use async_trait::async_trait;
use reqwest::Client;
use std::collections::HashMap;

use super::{ExtractedStream, ExtractionResult, StreamExtractor, StreamType};

/// MoviesAPI extractor — moviesapi.club wraps vidora.stream which uses JWPlayer
/// with a packed eval JS containing the m3u8 URL.
pub struct MoviesApiExtractor;

#[async_trait]
impl StreamExtractor for MoviesApiExtractor {
    fn provider_name(&self) -> &str {
        "MoviesAPI"
    }

    fn french_priority(&self) -> u8 {
        1
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

        let page_url = match media_type {
            "tv" => format!("https://moviesapi.club/tv/{}-{}-{}", tmdb_id, s, e),
            _ => format!("https://moviesapi.club/movie/{}", tmdb_id),
        };

        tracing::debug!("MoviesAPI: fetching {}", page_url);

        // Step 1: Get MoviesAPI page → find iframe src (e.g. vidora.stream/embed/...)
        let page_html = match fetch_page(http_client, &page_url, "https://moviesapi.club/").await {
            Ok(html) => html,
            Err(e) => {
                return ExtractionResult {
                    provider: "MoviesAPI".to_string(),
                    streams: vec![],
                    error: Some(format!("Page fetch: {}", e)),
                };
            }
        };

        let iframe_src = match extract_iframe_src(&page_html) {
            Some(src) => {
                if src.starts_with("//") {
                    format!("https:{}", src)
                } else {
                    src
                }
            }
            None => {
                return ExtractionResult {
                    provider: "MoviesAPI".to_string(),
                    streams: vec![],
                    error: Some("No iframe found".to_string()),
                };
            }
        };

        tracing::debug!("MoviesAPI: following iframe {}", iframe_src);

        // Step 2: Fetch the embed page (e.g. vidora.stream/embed/...)
        let embed_html = match fetch_page(http_client, &iframe_src, &page_url).await {
            Ok(html) => html,
            Err(e) => {
                return ExtractionResult {
                    provider: "MoviesAPI".to_string(),
                    streams: vec![],
                    error: Some(format!("Embed fetch: {}", e)),
                };
            }
        };

        let mut streams = Vec::new();

        // Step 3: Unpack eval(function(p,a,c,k,e,d)...) to get JWPlayer setup
        if let Some(unpacked) = unpack_p_a_c_k_e_d(&embed_html) {
            // Extract m3u8 URL from the unpacked JWPlayer setup
            let referer = iframe_referer(&iframe_src);
            let mut headers = HashMap::new();
            headers.insert("Referer".to_string(), referer.clone());
            headers.insert("Origin".to_string(), referer_origin(&iframe_src));

            // Find file:"..." patterns (m3u8)
            let m3u8_urls = extract_file_urls(&unpacked);
            for url in m3u8_urls {
                let stream_type = if url.contains(".m3u8") {
                    StreamType::Hls
                } else {
                    StreamType::Mp4
                };
                streams.push(ExtractedStream {
                    provider: "MoviesAPI".to_string(),
                    url,
                    quality: "auto".to_string(),
                    audio_lang: None,
                    headers: Some(headers.clone()),
                    stream_type,
                    category: None,
                    language: None,
                });
            }
        }

        // Also try direct m3u8 extraction from embed HTML
        if streams.is_empty() {
            let referer = iframe_referer(&iframe_src);
            let mut headers = HashMap::new();
            headers.insert("Referer".to_string(), referer);

            for url in extract_file_urls(&embed_html) {
                let stream_type = if url.contains(".m3u8") {
                    StreamType::Hls
                } else {
                    StreamType::Mp4
                };
                if !streams.iter().any(|s| s.url == url) {
                    streams.push(ExtractedStream {
                        provider: "MoviesAPI".to_string(),
                        url,
                        quality: "auto".to_string(),
                        audio_lang: None,
                        headers: Some(headers.clone()),
                        stream_type,
                        category: None,
                        language: None,
                    });
                }
            }
        }

        if !streams.is_empty() {
            tracing::info!("MoviesAPI: extracted {} stream(s)", streams.len());
        }

        ExtractionResult {
            provider: "MoviesAPI".to_string(),
            streams,
            error: None,
        }
    }
}

async fn fetch_page(client: &Client, url: &str, referer: &str) -> anyhow::Result<String> {
    let resp = client
        .get(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        )
        .header("Referer", referer)
        .send()
        .await?;
    Ok(resp.text().await?)
}

fn extract_iframe_src(html: &str) -> Option<String> {
    // Find <iframe ... src="URL" ...>
    let mut search_from = 0;
    while let Some(start) = html[search_from..].find("<iframe") {
        let abs_start = search_from + start;
        let section = &html[abs_start..html.len().min(abs_start + 1000)];
        if let Some(src_start) = section.find("src=\"") {
            let after = &section[src_start + 5..];
            if let Some(src_end) = after.find('"') {
                let src = &after[..src_end];
                if !src.is_empty() && !src.starts_with("about:") {
                    return Some(src.to_string());
                }
            }
        }
        search_from = abs_start + 1;
    }
    None
}

fn iframe_referer(url: &str) -> String {
    if let Ok(parsed) = url::Url::parse(url) {
        format!("{}://{}/", parsed.scheme(), parsed.host_str().unwrap_or(""))
    } else {
        url.to_string()
    }
}

fn referer_origin(url: &str) -> String {
    if let Ok(parsed) = url::Url::parse(url) {
        format!("{}://{}", parsed.scheme(), parsed.host_str().unwrap_or(""))
    } else {
        url.to_string()
    }
}

/// Unpack JS eval(function(p,a,c,k,e,d){...})
/// This is the Dean Edwards packer format.
pub(crate) fn unpack_p_a_c_k_e_d(html: &str) -> Option<String> {
    let marker = "eval(function(p,a,c,k,e,d)";
    let idx = html.find(marker)?;
    let after = &html[idx..];

    // Find the end — match the outermost closing paren of eval(...)
    // The packed code ends with .split('|'))); or similar
    let end_marker = ".split('|')";
    let split_idx = after.find(end_marker)?;

    // Find the closing parens after split
    let after_split = &after[split_idx + end_marker.len()..];
    let mut depth = 0;
    let mut close_idx = 0;
    for (i, ch) in after_split.chars().enumerate() {
        match ch {
            ')' => {
                if depth == 0 {
                    close_idx = i + 1;
                    break;
                }
                depth -= 1;
            }
            '(' => depth += 1,
            _ => {}
        }
    }

    let packed_full = &after[..split_idx + end_marker.len() + close_idx];

    // Extract the parameters: function(p,a,c,k,e,d){...}('packed_string',a,c,'dict'.split('|'))
    // We need: the encoded string (p), base (a), count (c), and dictionary
    let inner_start = packed_full.find("}(")?;
    let params_str = &packed_full[inner_start + 2..packed_full.len() - 1];

    // Parse parameters: 'encoded_string',base,count,'dict'.split('|')
    let (p_str, rest) = extract_quoted_string(params_str)?;
    let rest = rest.trim_start_matches(',');

    // Parse a (base) and c (count)
    let parts: Vec<&str> = rest.splitn(3, ',').collect();
    if parts.len() < 3 {
        return None;
    }
    let a: u32 = parts[0].trim().parse().ok()?;
    let c: u32 = parts[1].trim().parse().ok()?;

    // Parse dictionary
    let dict_str = parts[2].trim();
    let (dict_raw, _) = extract_quoted_string(dict_str)?;
    let dict: Vec<&str> = dict_raw.split('|').collect();

    // Decode: replace base-N encoded numbers with dictionary words
    let mut result = p_str.to_string();
    for i in (0..c).rev() {
        let encoded = encode_base_n(i, a);
        let replacement = if (i as usize) < dict.len() && !dict[i as usize].is_empty() {
            dict[i as usize]
        } else {
            &encoded
        };
        result = replace_word_boundary(&result, &encoded, replacement);
    }

    Some(result)
}

fn replace_word_boundary(text: &str, word: &str, replacement: &str) -> String {
    if word.is_empty() {
        return text.to_string();
    }
    let chars: Vec<char> = text.chars().collect();
    let word_chars: Vec<char> = word.chars().collect();
    let mut result = String::with_capacity(text.len());
    let mut i = 0;
    while i < chars.len() {
        if i + word_chars.len() <= chars.len()
            && &chars[i..i + word_chars.len()] == word_chars.as_slice()
        {
            let before_ok = i == 0 || !chars[i - 1].is_alphanumeric() && chars[i - 1] != '_';
            let after_ok = i + word_chars.len() >= chars.len()
                || !chars[i + word_chars.len()].is_alphanumeric()
                    && chars[i + word_chars.len()] != '_';
            if before_ok && after_ok {
                result.push_str(replacement);
                i += word_chars.len();
                continue;
            }
        }
        result.push(chars[i]);
        i += 1;
    }
    result
}

fn extract_quoted_string(s: &str) -> Option<(&str, &str)> {
    let s = s.trim();
    let bytes = s.as_bytes();
    if bytes.is_empty() {
        return None;
    }
    let quote = bytes[0];
    if quote != b'\'' && quote != b'"' {
        return None;
    }
    // Find closing quote, skipping escaped ones (backslash + quote)
    let mut i = 1;
    while i < bytes.len() {
        if bytes[i] == b'\\' && i + 1 < bytes.len() && bytes[i + 1] == quote {
            i += 2; // skip escaped quote
            continue;
        }
        if bytes[i] == quote {
            return Some((&s[1..i], &s[i + 1..]));
        }
        i += 1;
    }
    None
}

fn encode_base_n(mut num: u32, base: u32) -> String {
    if num == 0 {
        return "0".to_string();
    }
    let chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    let mut result = String::new();
    while num > 0 {
        let rem = (num % base) as usize;
        if rem < chars.len() {
            result.insert(0, chars.as_bytes()[rem] as char);
        }
        num /= base;
    }
    result
}

fn extract_file_urls(text: &str) -> Vec<String> {
    let mut urls = Vec::new();
    // Match "file":"URL" or file:"URL" patterns
    let patterns = ["\"file\":\"", "\"file\": \"", "file:\"", "'file':'"];
    for pattern in patterns {
        let mut search_from = 0;
        while let Some(idx) = text[search_from..].find(pattern) {
            let abs_start = search_from + idx + pattern.len();
            let after = &text[abs_start..];
            let delim = if pattern.contains('\'') { '\'' } else { '"' };
            if let Some(end) = after.find(delim) {
                let url = &after[..end];
                if url.starts_with("http")
                    && (url.contains(".m3u8") || url.contains(".mp4"))
                    && !urls.contains(&url.to_string())
                {
                    urls.push(url.to_string());
                }
            }
            search_from = abs_start;
        }
    }
    urls
}
