#![allow(dead_code)]
use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

const IPTV_ORG_CHANNELS_URL: &str =
    "https://raw.githubusercontent.com/iptv-org/database/master/data/channels.json";
const IPTV_ORG_STREAMS_URL: &str =
    "https://raw.githubusercontent.com/iptv-org/database/master/data/streams.json";
const DEFAULT_MAX_CHANNELS: usize = 250;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TvChannel {
    pub id: Option<String>,
    pub name: String,
    pub code: String,
    pub country: Option<String>,
    pub logo_url: Option<String>,
    pub category: Option<String>,
    pub is_free: bool,
    pub stream_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TvProgram {
    pub id: Option<String>,
    pub title: String,
    pub description: Option<String>,
    pub start_time: String,
    pub end_time: String,
    pub genre: Option<String>,
    pub image_url: Option<String>,
    pub rating: Option<f32>,
    pub channel_id: Option<String>,
}

#[derive(Debug, Clone)]
pub struct TvChannelsClient {
    client: Client,
}

#[derive(Debug, Deserialize)]
struct IptvOrgChannel {
    id: String,
    name: String,
    country: Option<String>,
    logo: Option<String>,
    categories: Option<Vec<String>>,
}

#[derive(Debug, Deserialize)]
struct IptvOrgStream {
    channel: Option<String>,
    url: Option<String>,
    status: Option<String>,
}

impl TvChannelsClient {
    pub fn new() -> Self {
        let client = Client::builder()
            .user_agent("Sokoul/1.0 (+https://github.com/iptv-org/database)")
            .build()
            .unwrap_or_else(|_| Client::new());

        Self { client }
    }

    pub async fn fetch_iptv_org_channels(
        &self,
        max_channels: usize,
    ) -> Result<Vec<TvChannel>, String> {
        let channels_request = self.client.get(IPTV_ORG_CHANNELS_URL).send();
        let streams_request = self.client.get(IPTV_ORG_STREAMS_URL).send();
        let (channels_response, streams_response) = tokio::join!(channels_request, streams_request);

        let channels: Vec<IptvOrgChannel> = channels_response
            .map_err(|e| format!("IPTV channels request failed: {e}"))?
            .error_for_status()
            .map_err(|e| format!("IPTV channels HTTP error: {e}"))?
            .json()
            .await
            .map_err(|e| format!("IPTV channels parse error: {e}"))?;

        let streams: Vec<IptvOrgStream> = streams_response
            .map_err(|e| format!("IPTV streams request failed: {e}"))?
            .error_for_status()
            .map_err(|e| format!("IPTV streams HTTP error: {e}"))?
            .json()
            .await
            .map_err(|e| format!("IPTV streams parse error: {e}"))?;

        let mut stream_by_channel: HashMap<String, String> = HashMap::new();
        for stream in streams {
            if !matches!(
                stream.status.as_deref(),
                None | Some("online") | Some("stable")
            ) {
                continue;
            }

            let Some(channel_id) = stream.channel else {
                continue;
            };
            let Some(url) = stream.url else {
                continue;
            };

            stream_by_channel.entry(channel_id).or_insert(url);
        }

        let mut used_codes: HashSet<String> = HashSet::new();
        let mut mapped = Vec::with_capacity(max_channels);
        for channel in channels {
            if mapped.len() >= max_channels {
                break;
            }

            let code = Self::sanitize_code(&channel.id);
            if code.is_empty() || !used_codes.insert(code.clone()) {
                continue;
            }

            let category = channel.categories.as_ref().and_then(|v| v.first()).cloned();

            mapped.push(TvChannel {
                id: Some(channel.id.clone()),
                name: channel.name,
                code,
                country: channel.country.map(|c| c.to_uppercase()),
                logo_url: channel.logo.filter(|l| !l.trim().is_empty()),
                category,
                is_free: true,
                stream_url: stream_by_channel.get(&channel.id).cloned(),
            });
        }

        mapped.sort_by(|a, b| {
            a.country
                .as_deref()
                .unwrap_or("")
                .cmp(b.country.as_deref().unwrap_or(""))
                .then(a.name.to_lowercase().cmp(&b.name.to_lowercase()))
        });

        Ok(mapped)
    }

    fn sanitize_code(value: &str) -> String {
        let mut out = String::with_capacity(value.len());
        for c in value.chars() {
            if c.is_ascii_alphanumeric() {
                out.push(c.to_ascii_lowercase());
            } else if c == '-' || c == '_' || c == '.' {
                out.push('-');
            }
        }

        while out.contains("--") {
            out = out.replace("--", "-");
        }

        out.trim_matches('-').to_string()
    }

    /// Fallback channels if remote source is unavailable
    pub fn fallback_channels() -> Vec<TvChannel> {
        let channels = vec![
            TvChannel {
                id: Some("ct1".to_string()),
                name: "ČT 1".to_string(),
                code: "ct1".to_string(),
                country: Some("CZ".to_string()),
                logo_url: Some("https://www.ceskatelevize.cz/static-2020/img/logo.png".to_string()),
                category: Some("entertainment".to_string()),
                is_free: true,
                stream_url: Some("https://stream.cz/ct1".to_string()),
            },
            TvChannel {
                id: Some("ct2".to_string()),
                name: "ČT 2".to_string(),
                code: "ct2".to_string(),
                country: Some("CZ".to_string()),
                logo_url: Some(
                    "https://www.ceskatelevize.cz/static-2020/img/logo2.png".to_string(),
                ),
                category: Some("movies".to_string()),
                is_free: true,
                stream_url: Some("https://stream.cz/ct2".to_string()),
            },
            TvChannel {
                id: Some("prima".to_string()),
                name: "Prima".to_string(),
                code: "prima".to_string(),
                country: Some("CZ".to_string()),
                logo_url: Some("https://img.prima.cz/images/logo.png".to_string()),
                category: Some("entertainment".to_string()),
                is_free: true,
                stream_url: Some("https://stream.cz/prima".to_string()),
            },
            TvChannel {
                id: Some("nova".to_string()),
                name: "Nova".to_string(),
                code: "nova".to_string(),
                country: Some("CZ".to_string()),
                logo_url: Some("https://nova.cz/images/logo.png".to_string()),
                category: Some("entertainment".to_string()),
                is_free: true,
                stream_url: Some("https://stream.cz/nova".to_string()),
            },
            TvChannel {
                id: Some("france2".to_string()),
                name: "France 2".to_string(),
                code: "france2".to_string(),
                country: Some("FR".to_string()),
                logo_url: Some("https://www.france.tv/images/f2.png".to_string()),
                category: Some("entertainment".to_string()),
                is_free: true,
                stream_url: Some("https://www.france.tv/france-2/".to_string()),
            },
            TvChannel {
                id: Some("france3".to_string()),
                name: "France 3".to_string(),
                code: "france3".to_string(),
                country: Some("FR".to_string()),
                logo_url: Some("https://www.france.tv/images/f3.png".to_string()),
                category: Some("entertainment".to_string()),
                is_free: true,
                stream_url: Some("https://www.france.tv/france-3/".to_string()),
            },
            TvChannel {
                id: Some("tf1".to_string()),
                name: "TF1".to_string(),
                code: "tf1".to_string(),
                country: Some("FR".to_string()),
                logo_url: Some("https://www.tf1.fr/images/logo.png".to_string()),
                category: Some("entertainment".to_string()),
                is_free: true,
                stream_url: Some("https://www.tf1.fr".to_string()),
            },
        ];
        channels
    }

    /// Get all available channels (IPTV-org + fallback)
    pub async fn get_all_channels(&self) -> Result<Vec<TvChannel>, String> {
        match self.fetch_iptv_org_channels(DEFAULT_MAX_CHANNELS).await {
            Ok(channels) if !channels.is_empty() => Ok(channels),
            _ => Ok(Self::fallback_channels()),
        }
    }

    /// Get channels by country
    pub async fn get_channels_by_country(&self, country: &str) -> Result<Vec<TvChannel>, String> {
        let needle = country.to_uppercase();
        let all = self.get_all_channels().await?;
        Ok(all
            .into_iter()
            .filter(|ch| ch.country.as_deref() == Some(needle.as_str()))
            .collect())
    }

    /// Dummy EPG data - in production this would fetch real EPG
    pub async fn get_programs_for_channel(
        &self,
        _channel_code: &str,
        _date: &str,
    ) -> Result<Vec<TvProgram>, String> {
        // Return empty - to be synced from real EPG source
        Ok(vec![])
    }

    /// Search programs by title
    pub async fn search_programs(&self, _query: &str) -> Result<Vec<TvProgram>, String> {
        // Stub - would search across all programs in DB
        Ok(vec![])
    }
}
