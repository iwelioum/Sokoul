//! Video hoster resolvers — extract direct m3u8/mp4 URLs from embed pages.
//!
//! Each resolver takes a hoster embed URL and returns the direct stream URL.
//! These are used by the French aggregator extractor to resolve file hoster links.

use reqwest::Client;
use std::collections::HashMap;

/// Result from resolving a hoster embed URL
#[derive(Debug, Clone)]
pub struct ResolvedStream {
    pub url: String,
    pub quality: String,
    pub stream_type: super::StreamType,
    pub headers: HashMap<String, String>,
}

/// Detect which hoster a URL belongs to and resolve it
pub async fn resolve_hoster(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let host = url::Url::parse(embed_url).ok()?.host_str()?.to_lowercase();

    match host.as_str() {
        h if h.contains("uqload") => resolve_uqload(client, embed_url).await,
        h if h.contains("supervideo") => resolve_supervideo(client, embed_url).await,
        h if h.contains("voe") => resolve_voe(client, embed_url).await,
        h if h.contains("doodstream") || h.contains("dood") => {
            resolve_doodstream(client, embed_url).await
        }
        h if h.contains("vidmoly") => resolve_vidmoly(client, embed_url).await,
        h if h.contains("netu") || h.contains("hqq") => resolve_netu(client, embed_url).await,
        h if h.contains("vidoza") => resolve_vidoza(client, embed_url).await,
        h if h.contains("lulustream") || h.contains("lulu") => {
            resolve_generic_packed(client, embed_url, "LuluStream").await
        }
        _ => {
            // Try generic packed JS extraction as fallback
            resolve_generic_packed(client, embed_url, "Unknown").await
        }
    }
}

/// Fetch an embed page with appropriate headers
async fn fetch_embed(client: &Client, url: &str, referer: &str) -> anyhow::Result<String> {
    let resp = client
        .get(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
        .header("Referer", referer)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.5")
        .send()
        .await?;
    Ok(resp.text().await?)
}

/// Extract the origin from a URL (scheme + host)
fn url_origin(url: &str) -> String {
    if let Ok(parsed) = url::Url::parse(url) {
        format!("{}://{}", parsed.scheme(), parsed.host_str().unwrap_or(""))
    } else {
        url.to_string()
    }
}

/// Build standard headers for a resolved stream
fn stream_headers(embed_url: &str) -> HashMap<String, String> {
    let mut headers = HashMap::new();
    let origin = url_origin(embed_url);
    headers.insert("Referer".to_string(), format!("{}/", origin));
    headers.insert("Origin".to_string(), origin);
    headers
}

// ── Uqload ───────────────────────────────────────────────────────────

/// Resolve Uqload embed — looks for `sources: [{src:"url"}]` in page HTML
async fn resolve_uqload(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // Pattern: sources: ["url.mp4"] or sources: [{src: "url"}]
    let url = extract_sources_url(&html).or_else(|| extract_file_url(&html))?;

    let stream_type = if url.contains(".m3u8") {
        super::StreamType::Hls
    } else {
        super::StreamType::Mp4
    };

    Some(ResolvedStream {
        url,
        quality: "HD".to_string(),
        stream_type,
        headers: stream_headers(embed_url),
    })
}

// ── SuperVideo ───────────────────────────────────────────────────────

/// Resolve SuperVideo embed — packed JS with file URL
async fn resolve_supervideo(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // Try packed JS first
    if let Some(unpacked) = super::moviesapi::unpack_p_a_c_k_e_d(&html) {
        if let Some(url) = extract_file_url(&unpacked) {
            let stream_type = if url.contains(".m3u8") {
                super::StreamType::Hls
            } else {
                super::StreamType::Mp4
            };
            return Some(ResolvedStream {
                url,
                quality: "HD".to_string(),
                stream_type,
                headers: stream_headers(embed_url),
            });
        }
    }

    // Fallback: direct pattern search
    let url = extract_file_url(&html).or_else(|| extract_sources_url(&html))?;

    let stream_type = if url.contains(".m3u8") {
        super::StreamType::Hls
    } else {
        super::StreamType::Mp4
    };

    Some(ResolvedStream {
        url,
        quality: "HD".to_string(),
        stream_type,
        headers: stream_headers(embed_url),
    })
}

// ── Voe / VoeSx ──────────────────────────────────────────────────────

/// Resolve Voe embed — m3u8 URL often in page source or base64 encoded
async fn resolve_voe(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // Pattern 1: direct m3u8 URL in source
    if let Some(url) = extract_m3u8_from_text(&html) {
        return Some(ResolvedStream {
            url,
            quality: "HD".to_string(),
            stream_type: super::StreamType::Hls,
            headers: stream_headers(embed_url),
        });
    }

    // Pattern 2: base64 encoded URL — "atob('...')" or window.location pattern
    if let Some(url) = extract_base64_url(&html) {
        let stream_type = if url.contains(".m3u8") {
            super::StreamType::Hls
        } else {
            super::StreamType::Mp4
        };
        return Some(ResolvedStream {
            url,
            quality: "HD".to_string(),
            stream_type,
            headers: stream_headers(embed_url),
        });
    }

    // Pattern 3: packed JS
    if let Some(unpacked) = super::moviesapi::unpack_p_a_c_k_e_d(&html) {
        if let Some(url) = extract_m3u8_from_text(&unpacked).or_else(|| extract_file_url(&unpacked))
        {
            let stream_type = if url.contains(".m3u8") {
                super::StreamType::Hls
            } else {
                super::StreamType::Mp4
            };
            return Some(ResolvedStream {
                url,
                quality: "HD".to_string(),
                stream_type,
                headers: stream_headers(embed_url),
            });
        }
    }

    // Pattern 4: JSON "sources" array
    extract_sources_url(&html).map(|url| {
        let stream_type = if url.contains(".m3u8") {
            super::StreamType::Hls
        } else {
            super::StreamType::Mp4
        };
        ResolvedStream {
            url,
            quality: "HD".to_string(),
            stream_type,
            headers: stream_headers(embed_url),
        }
    })
}

// ── DoodStream ───────────────────────────────────────────────────────

/// Resolve DoodStream embed — uses /pass_md5/ token system
async fn resolve_doodstream(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // DoodStream uses a /pass_md5/ endpoint to get a token
    // Pattern: $.get('/pass_md5/...', function(data){...})
    let pass_url = extract_pass_md5_url(&html)?;

    let full_url = if pass_url.starts_with('/') {
        format!("{}{}", url_origin(embed_url), pass_url)
    } else {
        pass_url
    };

    let token_resp = client
        .get(&full_url)
        .header("Referer", embed_url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        )
        .send()
        .await
        .ok()?;

    let direct_url = token_resp.text().await.ok()?;

    if direct_url.starts_with("http") {
        // DoodStream appends a random string and expiry token
        let final_url = format!(
            "{}{}?token={}&expiry={}",
            direct_url,
            generate_random_string(10),
            extract_token_from_html(&html).unwrap_or_default(),
            chrono::Utc::now().timestamp_millis()
        );

        return Some(ResolvedStream {
            url: final_url,
            quality: "HD".to_string(),
            stream_type: super::StreamType::Mp4,
            headers: stream_headers(embed_url),
        });
    }

    None
}

// ── VidMoly ──────────────────────────────────────────────────────────

/// Resolve VidMoly embed — packed JS with m3u8 URL
async fn resolve_vidmoly(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // VidMoly typically uses packed JS
    if let Some(unpacked) = super::moviesapi::unpack_p_a_c_k_e_d(&html) {
        if let Some(url) = extract_file_url(&unpacked).or_else(|| extract_m3u8_from_text(&unpacked))
        {
            let stream_type = if url.contains(".m3u8") {
                super::StreamType::Hls
            } else {
                super::StreamType::Mp4
            };
            return Some(ResolvedStream {
                url,
                quality: "HD".to_string(),
                stream_type,
                headers: stream_headers(embed_url),
            });
        }
    }

    // Fallback: direct search
    extract_file_url(&html)
        .or_else(|| extract_m3u8_from_text(&html))
        .map(|url| {
            let stream_type = if url.contains(".m3u8") {
                super::StreamType::Hls
            } else {
                super::StreamType::Mp4
            };
            ResolvedStream {
                url,
                quality: "HD".to_string(),
                stream_type,
                headers: stream_headers(embed_url),
            }
        })
}

// ── Netu / HQQ ───────────────────────────────────────────────────────

/// Resolve Netu embed — encrypted/obfuscated player
async fn resolve_netu(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // Netu uses various obfuscation, try common patterns
    if let Some(url) = extract_m3u8_from_text(&html)
        .or_else(|| extract_file_url(&html))
        .or_else(|| extract_sources_url(&html))
    {
        let stream_type = if url.contains(".m3u8") {
            super::StreamType::Hls
        } else {
            super::StreamType::Mp4
        };
        return Some(ResolvedStream {
            url,
            quality: "HD".to_string(),
            stream_type,
            headers: stream_headers(embed_url),
        });
    }

    // Try packed JS
    if let Some(unpacked) = super::moviesapi::unpack_p_a_c_k_e_d(&html) {
        if let Some(url) = extract_m3u8_from_text(&unpacked).or_else(|| extract_file_url(&unpacked))
        {
            let stream_type = if url.contains(".m3u8") {
                super::StreamType::Hls
            } else {
                super::StreamType::Mp4
            };
            return Some(ResolvedStream {
                url,
                quality: "HD".to_string(),
                stream_type,
                headers: stream_headers(embed_url),
            });
        }
    }

    None
}

// ── Vidoza ────────────────────────────────────────────────────────────

/// Resolve Vidoza embed — simple sourceslist pattern
async fn resolve_vidoza(client: &Client, embed_url: &str) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // Vidoza uses sourceslist: [{...}]
    let url = extract_sources_url(&html)
        .or_else(|| extract_file_url(&html))
        .or_else(|| extract_m3u8_from_text(&html))?;

    let stream_type = if url.contains(".m3u8") {
        super::StreamType::Hls
    } else {
        super::StreamType::Mp4
    };

    Some(ResolvedStream {
        url,
        quality: "HD".to_string(),
        stream_type,
        headers: stream_headers(embed_url),
    })
}

// ── Generic packed JS resolver ───────────────────────────────────────

/// Try to resolve any hoster using packed JS or common patterns
async fn resolve_generic_packed(
    client: &Client,
    embed_url: &str,
    _provider: &str,
) -> Option<ResolvedStream> {
    let html = fetch_embed(client, embed_url, &url_origin(embed_url))
        .await
        .ok()?;

    // Try all extraction methods
    let url = extract_m3u8_from_text(&html)
        .or_else(|| extract_file_url(&html))
        .or_else(|| extract_sources_url(&html))
        .or_else(|| {
            super::moviesapi::unpack_p_a_c_k_e_d(&html).and_then(|unpacked| {
                extract_m3u8_from_text(&unpacked)
                    .or_else(|| extract_file_url(&unpacked))
                    .or_else(|| extract_sources_url(&unpacked))
            })
        })?;

    let stream_type = if url.contains(".m3u8") {
        super::StreamType::Hls
    } else {
        super::StreamType::Mp4
    };

    Some(ResolvedStream {
        url,
        quality: "HD".to_string(),
        stream_type,
        headers: stream_headers(embed_url),
    })
}

// ── Shared extraction helpers ────────────────────────────────────────

/// Extract m3u8 URLs from text (any https URL ending in .m3u8)
fn extract_m3u8_from_text(text: &str) -> Option<String> {
    // Match URLs like https://.../.m3u8 or https://.../.m3u8?...
    let patterns = [".m3u8\"", ".m3u8'", ".m3u8`", ".m3u8?"];
    for pattern_end in &patterns {
        let search = format!(".m3u8{}", &pattern_end[5..]);
        let _ = search; // just using pattern_end

        let mut search_from = 0;
        while let Some(idx) = text[search_from..].find(pattern_end) {
            let abs_end = search_from + idx + 5; // include .m3u8
                                                 // Walk backwards to find URL start
            let before = &text[..abs_end];
            if let Some(start) = before.rfind("https://").or_else(|| before.rfind("http://")) {
                let url = &text[start..abs_end];
                if !url.contains(' ') && !url.contains('\n') {
                    return Some(url.to_string());
                }
            }
            search_from = search_from + idx + 1;
        }
    }
    None
}

/// Extract "file":"url" patterns (used by JWPlayer and similar)
fn extract_file_url(text: &str) -> Option<String> {
    let patterns = [
        "\"file\":\"",
        "\"file\": \"",
        "file:\"",
        "'file':'",
        "\"file\":\"",
    ];
    for pattern in &patterns {
        if let Some(idx) = text.find(pattern) {
            let after = &text[idx + pattern.len()..];
            let delim = if pattern.contains('\'') { '\'' } else { '"' };
            if let Some(end) = after.find(delim) {
                let url = &after[..end];
                if url.starts_with("http") && (url.contains(".m3u8") || url.contains(".mp4")) {
                    return Some(url.to_string());
                }
            }
        }
    }
    None
}

/// Extract URL from sources array: sources: [{src:"url"}] or sources: ["url"]
fn extract_sources_url(text: &str) -> Option<String> {
    // Pattern 1: sources: [{src: "url", ...}]
    let patterns = [
        "\"src\":\"",
        "\"src\": \"",
        "'src':'",
        "\"file\":\"",
        "\"file\": \"",
    ];

    // First try to find a sources array context
    let sources_markers = ["sources:", "\"sources\":", "sourceslist:"];
    for marker in &sources_markers {
        if let Some(marker_idx) = text.find(marker) {
            let section = &text[marker_idx..text.len().min(marker_idx + 2000)];
            for pattern in &patterns {
                if let Some(idx) = section.find(pattern) {
                    let after = &section[idx + pattern.len()..];
                    let delim = if pattern.contains('\'') { '\'' } else { '"' };
                    if let Some(end) = after.find(delim) {
                        let url = &after[..end];
                        if url.starts_with("http") {
                            return Some(url.to_string());
                        }
                    }
                }
            }

            // Pattern 2: sources: ["url"] (simple array of strings)
            if let Some(arr_start) = section.find('[') {
                let arr_section = &section[arr_start..];
                if let Some(quote_start) = arr_section.find('"') {
                    let after = &arr_section[quote_start + 1..];
                    if let Some(quote_end) = after.find('"') {
                        let url = &after[..quote_end];
                        if url.starts_with("http")
                            && (url.contains(".mp4") || url.contains(".m3u8"))
                        {
                            return Some(url.to_string());
                        }
                    }
                }
            }
        }
    }

    None
}

/// Extract base64-encoded URL (common in Voe)
fn extract_base64_url(text: &str) -> Option<String> {
    // Pattern: atob('base64string') or window.atob("base64string")
    let patterns = ["atob('", "atob(\""];
    for pattern in &patterns {
        if let Some(idx) = text.find(pattern) {
            let after = &text[idx + pattern.len()..];
            let delim = if pattern.contains('\'') { '\'' } else { '"' };
            if let Some(end) = after.find(delim) {
                let b64 = &after[..end];
                if let Ok(decoded_bytes) = base64_decode(b64) {
                    if let Ok(decoded) = String::from_utf8(decoded_bytes) {
                        if decoded.starts_with("http") {
                            return Some(decoded);
                        }
                    }
                }
            }
        }
    }
    None
}

/// Simple base64 decoder (standard alphabet)
fn base64_decode(input: &str) -> Result<Vec<u8>, ()> {
    const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    let input = input.trim_end_matches('=');
    let mut output = Vec::with_capacity(input.len() * 3 / 4);

    let mut buf: u32 = 0;
    let mut bits: u32 = 0;

    for &byte in input.as_bytes() {
        let val = TABLE.iter().position(|&c| c == byte).ok_or(())?;
        buf = (buf << 6) | val as u32;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            output.push((buf >> bits) as u8);
            buf &= (1 << bits) - 1;
        }
    }

    Ok(output)
}

/// Extract /pass_md5/ URL from DoodStream page
fn extract_pass_md5_url(html: &str) -> Option<String> {
    // Pattern: $.get('/pass_md5/...', or fetch('/pass_md5/...')
    let patterns = ["/pass_md5/"];
    for pattern in &patterns {
        if let Some(idx) = html.find(pattern) {
            // Find the end of the URL (quote or space)
            let after = &html[idx..];
            let end = after
                .find('\'')
                .or_else(|| after.find('"'))
                .or_else(|| after.find(','))
                .unwrap_or(after.len().min(200));
            let url = &after[..end];
            if !url.is_empty() {
                return Some(url.to_string());
            }
        }
    }
    None
}

/// Extract token from DoodStream HTML
fn extract_token_from_html(html: &str) -> Option<String> {
    // Pattern: token=something or makePlay.token = "..."
    if let Some(idx) = html.find("token") {
        let after = &html[idx..];
        if let Some(eq) = after.find('=') {
            let val_start = &after[eq + 1..];
            let trimmed = val_start.trim().trim_matches(|c| c == '"' || c == '\'');
            let end = trimmed
                .find(|c: char| !c.is_alphanumeric() && c != '-' && c != '_')
                .unwrap_or(trimmed.len().min(64));
            let token = &trimmed[..end];
            if !token.is_empty() {
                return Some(token.to_string());
            }
        }
    }
    None
}

/// Generate a random alphanumeric string
fn generate_random_string(len: usize) -> String {
    use std::time::SystemTime;
    let seed = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();

    let chars = b"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    let mut result = String::with_capacity(len);
    let mut state = seed;
    for _ in 0..len {
        state = state.wrapping_mul(6364136223846793005).wrapping_add(1);
        let idx = ((state >> 33) as usize) % chars.len();
        result.push(chars[idx] as char);
    }
    result
}
