use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// Watchmode client — Streaming availability (Netflix, Disney+, Amazon, etc.)
#[derive(Clone)]
pub struct WatchmodeClient {
    client: Client,
    api_key: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchmodeSearchResult {
    pub title_results: Option<Vec<WatchmodeTitle>>,
    pub people_results: Option<Vec<serde_json::Value>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchmodeTitle {
    pub id: Option<i64>,
    pub name: Option<String>,
    pub year: Option<i32>,
    #[serde(rename = "type")]
    pub title_type: Option<String>,
    pub tmdb_id: Option<i64>,
    pub tmdb_type: Option<String>,
    pub imdb_id: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchmodeSource {
    pub source_id: Option<i64>,
    pub name: Option<String>,
    #[serde(rename = "type")]
    pub source_type: Option<String>,
    pub region: Option<String>,
    pub web_url: Option<String>,
    pub format: Option<String>,
    pub price: Option<f64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchmodeTitleDetails {
    pub id: Option<i64>,
    pub title: Option<String>,
    pub year: Option<i32>,
    pub plot_overview: Option<String>,
    #[serde(rename = "type")]
    pub title_type: Option<String>,
    pub tmdb_id: Option<i64>,
    pub imdb_id: Option<String>,
    pub runtime_minutes: Option<i32>,
    pub genre_names: Option<Vec<String>>,
    pub user_rating: Option<f64>,
    pub critic_score: Option<i32>,
    pub us_rating: Option<String>,
    pub poster: Option<String>,
    pub backdrop: Option<String>,
    pub similar_titles: Option<Vec<i64>>,
    pub sources: Option<Vec<WatchmodeSource>>,
}

const BASE_URL: &str = "https://api.watchmode.com/v1";

impl WatchmodeClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            api_key,
        }
    }

    /// Search for a title
    pub async fn search(&self, query: &str) -> Result<WatchmodeSearchResult, reqwest::Error> {
        let url = format!(
            "{}/search/?apiKey={}&search_field=name&search_value={}",
            BASE_URL, self.api_key, query
        );
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(WatchmodeSearchResult {
                title_results: Some(vec![]),
                people_results: Some(vec![]),
            });
        }
        resp.json().await.or_else(|e| {
            error!("Watchmode search parse error: {}", e);
            Ok(WatchmodeSearchResult {
                title_results: Some(vec![]),
                people_results: Some(vec![]),
            })
        })
    }

    /// Streaming sources for a title (by Watchmode ID)
    pub async fn title_sources(
        &self,
        title_id: i64,
    ) -> Result<Vec<WatchmodeSource>, reqwest::Error> {
        let url = format!(
            "{}/title/{}/sources/?apiKey={}",
            BASE_URL, title_id, self.api_key
        );
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Watchmode sources parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Title details (by Watchmode ID) with sources
    pub async fn title_details(
        &self,
        title_id: i64,
    ) -> Result<Option<WatchmodeTitleDetails>, reqwest::Error> {
        let url = format!(
            "{}/title/{}/details/?apiKey={}&append_to_response=sources",
            BASE_URL, title_id, self.api_key
        );
        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        Ok(resp.json().await.ok())
    }

    /// Lookup by TMDB ID → Watchmode sources
    pub async fn sources_by_tmdb(
        &self,
        tmdb_id: i64,
        media_type: &str,
    ) -> Result<Vec<WatchmodeSource>, reqwest::Error> {
        let source_type = if media_type == "movie" { "movie" } else { "tv" };
        let url = format!(
            "{}/title/{}-{}/sources/?apiKey={}",
            BASE_URL, tmdb_id, source_type, self.api_key
        );
        // This endpoint format may vary — fall back gracefully
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Watchmode TMDB sources parse error: {}", e);
            Ok(vec![])
        })
    }
}
