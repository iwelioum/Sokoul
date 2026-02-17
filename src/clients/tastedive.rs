use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// TasteDive client — Similar recommendations (movies, TV shows, music, books)
#[derive(Clone)]
pub struct TasteDiveClient {
    client: Client,
    api_key: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TasteDiveResponse {
    #[serde(rename = "similar")]
    pub similar: TasteDiveSimilar,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TasteDiveSimilar {
    pub info: Vec<TasteDiveItem>,
    pub results: Vec<TasteDiveItem>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TasteDiveItem {
    #[serde(rename = "Name")]
    pub name: Option<String>,
    #[serde(rename = "Type")]
    pub item_type: Option<String>,
    #[serde(rename = "wTeaser")]
    pub teaser: Option<String>,
    #[serde(rename = "wUrl")]
    pub wikipedia_url: Option<String>,
    #[serde(rename = "yUrl")]
    pub youtube_url: Option<String>,
    #[serde(rename = "yID")]
    pub youtube_id: Option<String>,
}

const BASE_URL: &str = "https://tastedive.com/api";

impl TasteDiveClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            api_key,
        }
    }

    /// Get similar recommendations
    /// `query`: movie/series/artist name (e.g., "Inception", "Breaking Bad")
    /// `media_type`: optionnel — "movie", "show", "music", "book", "author", "game"
    /// `limit`: number of results (max 20)
    pub async fn similar(
        &self,
        query: &str,
        media_type: Option<&str>,
        limit: u32,
    ) -> Result<TasteDiveResponse, reqwest::Error> {
        let mut url = format!(
            "{}/similar?q={}&k={}&limit={}&info=1",
            BASE_URL,
            query,
            self.api_key,
            limit.min(20)
        );
        if let Some(mt) = media_type {
            url.push_str(&format!("&type={}", mt));
        }

        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(TasteDiveResponse {
                similar: TasteDiveSimilar {
                    info: vec![],
                    results: vec![],
                },
            });
        }
        resp.json().await.or_else(|e| {
            error!("TasteDive parse error: {}", e);
            Ok(TasteDiveResponse {
                similar: TasteDiveSimilar {
                    info: vec![],
                    results: vec![],
                },
            })
        })
    }
}
