use crate::extractors::SubtitleTrack;
use reqwest::Client;
use serde::Deserialize;

/// Client for fetching subtitles from subdl.com API (no auth required).
#[derive(Clone)]
pub struct SubtitleClient {
    client: Client,
}

#[derive(Debug, Deserialize)]
struct SubdlResponse {
    #[serde(default)]
    subtitles: Vec<SubdlSubtitle>,
}

#[derive(Debug, Deserialize)]
struct SubdlSubtitle {
    #[serde(default)]
    url: String,
    #[serde(default)]
    lang: String,
    #[serde(default, rename = "releaseName")]
    release_name: Option<String>,
    #[serde(default)]
    author: Option<String>,
}

impl SubtitleClient {
    pub fn new() -> Self {
        Self {
            client: Client::builder()
                .timeout(std::time::Duration::from_secs(10))
                .build()
                .unwrap_or_default(),
        }
    }

    /// Search for subtitles by TMDB ID.
    pub async fn search(
        &self,
        tmdb_id: i32,
        media_type: &str,
        season: Option<i32>,
        episode: Option<i32>,
        languages: &[&str],
    ) -> anyhow::Result<Vec<SubtitleTrack>> {
        let mut all_tracks = Vec::new();

        for lang in languages {
            let lang_name = match *lang {
                "fr" => "french",
                "en" => "english",
                "es" => "spanish",
                "de" => "german",
                "it" => "italian",
                "pt" => "portuguese",
                "ar" => "arabic",
                _ => lang,
            };

            let mut url = format!(
                "https://api.subdl.com/auto?tmdb_id={}&type={}&languages={}",
                tmdb_id,
                if media_type == "tv" { "tv" } else { "movie" },
                lang_name
            );

            if let Some(s) = season {
                url.push_str(&format!("&season_number={}", s));
            }
            if let Some(e) = episode {
                url.push_str(&format!("&episode_number={}", e));
            }

            tracing::debug!("SubDL: searching subtitles at {}", url);

            let resp = match self
                .client
                .get(&url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                )
                .send()
                .await
            {
                Ok(r) => r,
                Err(e) => {
                    tracing::warn!("SubDL request failed for {}: {}", lang, e);
                    continue;
                }
            };

            let body = match resp.text().await {
                Ok(t) => t,
                Err(e) => {
                    tracing::warn!("SubDL read failed for {}: {}", lang, e);
                    continue;
                }
            };

            let parsed: SubdlResponse = match serde_json::from_str(&body) {
                Ok(p) => p,
                Err(e) => {
                    tracing::warn!(
                        "SubDL parse failed for {}: {} — body start: {}",
                        lang,
                        e,
                        &body[..body.len().min(200)]
                    );
                    continue;
                }
            };

            let is_first_lang = all_tracks.is_empty() && *lang == languages[0];

            for (i, sub) in parsed.subtitles.into_iter().enumerate() {
                if sub.url.is_empty() {
                    continue;
                }

                let label = format!(
                    "{} {}",
                    lang_name_display(lang),
                    sub.release_name
                        .as_deref()
                        .or(sub.author.as_deref())
                        .unwrap_or("")
                )
                .trim()
                .to_string();

                // Proxy the subtitle URL through our VTT endpoint
                let proxy_url = format!(
                    "/api/streaming/subtitles/vtt?url={}",
                    urlencoding::encode(&format!("https://dl.subdl.com{}", sub.url))
                );

                all_tracks.push(SubtitleTrack {
                    language: lang.to_string(),
                    label,
                    url: proxy_url,
                    is_default: is_first_lang && i == 0,
                });

                // Limit to 3 subtitles per language
                if i >= 2 {
                    break;
                }
            }
        }

        Ok(all_tracks)
    }

    /// Fetch a subtitle file and convert to WebVTT if needed.
    pub async fn fetch_as_vtt(&self, url: &str) -> anyhow::Result<String> {
        let resp = self
            .client
            .get(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            )
            .send()
            .await?;

        let bytes = resp.bytes().await?;

        // Try to decompress if it's a zip
        if bytes.len() > 2 && bytes[0] == 0x50 && bytes[1] == 0x4B {
            return extract_srt_from_zip(&bytes);
        }

        let text = String::from_utf8_lossy(&bytes).to_string();

        // If already VTT, return as-is
        if text.starts_with("WEBVTT") {
            return Ok(text);
        }

        // Convert SRT to VTT
        Ok(srt_to_vtt(&text))
    }
}

fn lang_name_display(code: &str) -> &str {
    match code {
        "fr" => "Français",
        "en" => "English",
        "es" => "Español",
        "de" => "Deutsch",
        "it" => "Italiano",
        "pt" => "Português",
        "ar" => "العربية",
        _ => code,
    }
}

fn srt_to_vtt(srt: &str) -> String {
    let mut vtt = String::from("WEBVTT\n\n");
    for line in srt.lines() {
        // Replace SRT timestamp commas with VTT dots
        if line.contains(" --> ") && line.contains(',') {
            vtt.push_str(&line.replace(',', "."));
        } else {
            vtt.push_str(line);
        }
        vtt.push('\n');
    }
    vtt
}

fn extract_srt_from_zip(data: &[u8]) -> anyhow::Result<String> {
    use std::io::{Cursor, Read};

    let reader = Cursor::new(data);
    let mut archive =
        zip::ZipArchive::new(reader).map_err(|e| anyhow::anyhow!("Invalid zip: {}", e))?;

    for i in 0..archive.len() {
        let mut file = archive
            .by_index(i)
            .map_err(|e| anyhow::anyhow!("Zip entry error: {}", e))?;

        let name = file.name().to_lowercase();
        if name.ends_with(".srt") || name.ends_with(".vtt") {
            let mut content = String::new();
            file.read_to_string(&mut content)?;

            if name.ends_with(".vtt") || content.starts_with("WEBVTT") {
                return Ok(content);
            }
            return Ok(srt_to_vtt(&content));
        }
    }

    Err(anyhow::anyhow!("No subtitle file found in zip"))
}
