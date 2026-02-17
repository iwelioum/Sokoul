use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use tracing::error;

/// TheTVDB client — enriched TV metadata (artwork, seasons, episodes)
#[derive(Clone)]
pub struct ThetvdbClient {
    client: Client,
    api_key: String,
    pin: String,
    token: Arc<tokio::sync::RwLock<Option<String>>>,
}

#[derive(Debug, Serialize, Deserialize)]
struct AuthRequest {
    apikey: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pin: String,
}

#[derive(Debug, Deserialize)]
struct AuthResponse {
    data: Option<AuthData>,
}

#[derive(Debug, Deserialize)]
struct AuthData {
    token: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ThetvdbSeries {
    pub id: Option<i64>,
    pub name: Option<String>,
    pub slug: Option<String>,
    pub image: Option<String>,
    pub year: Option<String>,
    pub overview: Option<String>,
    pub status: Option<ThetvdbStatus>,
    #[serde(rename = "firstAired")]
    pub first_aired: Option<String>,
    #[serde(rename = "originalNetwork")]
    pub original_network: Option<String>,
    pub aliases: Option<Vec<ThetvdbAlias>>,
    #[serde(rename = "averageRuntime")]
    pub average_runtime: Option<i32>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ThetvdbStatus {
    pub name: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ThetvdbAlias {
    pub language: Option<String>,
    pub name: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ThetvdbEpisode {
    pub id: Option<i64>,
    pub name: Option<String>,
    pub overview: Option<String>,
    pub image: Option<String>,
    #[serde(rename = "seasonNumber")]
    pub season_number: Option<i32>,
    #[serde(rename = "number")]
    pub episode_number: Option<i32>,
    #[serde(rename = "aired")]
    pub aired: Option<String>,
    pub runtime: Option<i32>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ThetvdbArtwork {
    pub id: Option<i64>,
    pub image: Option<String>,
    pub thumbnail: Option<String>,
    #[serde(rename = "type")]
    pub art_type: Option<i32>,
    pub language: Option<String>,
    pub score: Option<i32>,
}

#[derive(Debug, Deserialize)]
struct ApiResponse<T> {
    data: Option<T>,
}

#[derive(Debug, Deserialize)]
struct EpisodesWrapper {
    episodes: Option<Vec<ThetvdbEpisode>>,
}

#[derive(Debug, Deserialize)]
struct ArtworkWrapper {
    artworks: Option<Vec<ThetvdbArtwork>>,
}

impl ThetvdbClient {
    pub fn new(api_key: String, pin: String) -> Self {
        Self {
            client: Client::new(),
            api_key,
            pin,
            token: Arc::new(tokio::sync::RwLock::new(None)),
        }
    }

    async fn authenticate(&self) -> Result<String, reqwest::Error> {
        let body = AuthRequest {
            apikey: self.api_key.clone(),
            pin: self.pin.clone(),
        };
        let resp = self
            .client
            .post("https://api4.thetvdb.com/v4/login")
            .json(&body)
            .send()
            .await?;
        let auth: AuthResponse = resp.json().await?;
        let token = auth.data.and_then(|d| d.token).unwrap_or_default();
        Ok(token)
    }

    async fn get_token(&self) -> Result<String, reqwest::Error> {
        {
            let guard = self.token.read().await;
            if let Some(ref t) = *guard {
                if !t.is_empty() {
                    return Ok(t.clone());
                }
            }
        }
        let new_token = self.authenticate().await?;
        {
            let mut guard = self.token.write().await;
            *guard = Some(new_token.clone());
        }
        Ok(new_token)
    }

    /// Fetch series details by TVDB ID
    pub async fn series_details(
        &self,
        tvdb_id: i64,
    ) -> Result<Option<ThetvdbSeries>, reqwest::Error> {
        let token = self.get_token().await?;
        let url = format!("https://api4.thetvdb.com/v4/series/{}", tvdb_id);
        let resp = self.client.get(&url).bearer_auth(&token).send().await?;

        if resp.status() == 404 {
            return Ok(None);
        }

        let data: ApiResponse<ThetvdbSeries> = resp.json().await.unwrap_or_else(|e| {
            error!("TheTVDB series parse error: {}", e);
            ApiResponse { data: None }
        });
        Ok(data.data)
    }

    /// Fetch episodes for a series
    pub async fn series_episodes(
        &self,
        tvdb_id: i64,
        season: Option<i32>,
    ) -> Result<Vec<ThetvdbEpisode>, reqwest::Error> {
        let token = self.get_token().await?;
        let url = format!(
            "https://api4.thetvdb.com/v4/series/{}/episodes/default",
            tvdb_id
        );
        let mut req = self.client.get(&url).bearer_auth(&token);
        if let Some(s) = season {
            req = req.query(&[("season", s.to_string())]);
        }
        let resp = req.send().await?;

        if resp.status() == 404 {
            return Ok(vec![]);
        }

        let data: ApiResponse<EpisodesWrapper> = resp.json().await.unwrap_or_else(|e| {
            error!("TheTVDB episodes parse error: {}", e);
            ApiResponse { data: None }
        });
        Ok(data.data.and_then(|w| w.episodes).unwrap_or_default())
    }

    /// Fetch artworks for a series
    pub async fn series_artworks(
        &self,
        tvdb_id: i64,
    ) -> Result<Vec<ThetvdbArtwork>, reqwest::Error> {
        let token = self.get_token().await?;
        let url = format!("https://api4.thetvdb.com/v4/series/{}/artworks", tvdb_id);
        let resp = self.client.get(&url).bearer_auth(&token).send().await?;

        if resp.status() == 404 {
            return Ok(vec![]);
        }

        let data: ApiResponse<ArtworkWrapper> = resp.json().await.unwrap_or_else(|e| {
            error!("TheTVDB artworks parse error: {}", e);
            ApiResponse { data: None }
        });
        Ok(data.data.and_then(|w| w.artworks).unwrap_or_default())
    }

    /// Search for a series by name
    pub async fn search(&self, query: &str) -> Result<Vec<ThetvdbSeries>, reqwest::Error> {
        let token = self.get_token().await?;
        let url = "https://api4.thetvdb.com/v4/search";
        let resp = self
            .client
            .get(url)
            .bearer_auth(&token)
            .query(&[("query", query), ("type", "series")])
            .send()
            .await?;

        let data: ApiResponse<Vec<ThetvdbSeries>> = resp.json().await.unwrap_or_else(|e| {
            error!("TheTVDB search parse error: {}", e);
            ApiResponse { data: None }
        });
        Ok(data.data.unwrap_or_default())
    }
}
