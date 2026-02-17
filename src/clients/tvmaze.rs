use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// TVMaze client — Detailed TV data, episodes, casting (free, no auth)
#[derive(Clone)]
pub struct TvMazeClient {
    client: Client,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeShow {
    pub id: i64,
    pub url: Option<String>,
    pub name: Option<String>,
    #[serde(rename = "type")]
    pub show_type: Option<String>,
    pub language: Option<String>,
    pub genres: Option<Vec<String>>,
    pub status: Option<String>,
    pub runtime: Option<i32>,
    pub premiered: Option<String>,
    pub ended: Option<String>,
    pub rating: Option<TvMazeRating>,
    pub network: Option<TvMazeNetwork>,
    pub image: Option<TvMazeImage>,
    pub summary: Option<String>,
    pub updated: Option<i64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeRating {
    pub average: Option<f64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeNetwork {
    pub id: Option<i64>,
    pub name: Option<String>,
    pub country: Option<TvMazeCountry>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeCountry {
    pub name: Option<String>,
    pub code: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeImage {
    pub medium: Option<String>,
    pub original: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeSearchResult {
    pub score: Option<f64>,
    pub show: TvMazeShow,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeEpisode {
    pub id: i64,
    pub url: Option<String>,
    pub name: Option<String>,
    pub season: Option<i32>,
    pub number: Option<i32>,
    pub airdate: Option<String>,
    pub runtime: Option<i32>,
    pub rating: Option<TvMazeRating>,
    pub image: Option<TvMazeImage>,
    pub summary: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeCastMember {
    pub person: Option<TvMazePerson>,
    pub character: Option<TvMazeCharacter>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazePerson {
    pub id: i64,
    pub name: Option<String>,
    pub image: Option<TvMazeImage>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TvMazeCharacter {
    pub id: i64,
    pub name: Option<String>,
    pub image: Option<TvMazeImage>,
}

const BASE_URL: &str = "https://api.tvmaze.com";

impl TvMazeClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
        }
    }

    /// Search for TV series
    pub async fn search(&self, query: &str) -> Result<Vec<TvMazeSearchResult>, reqwest::Error> {
        let url = format!("{}/search/shows?q={}", BASE_URL, query);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("TVMaze search parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Series details by TVMaze ID
    pub async fn show_details(&self, show_id: i64) -> Result<Option<TvMazeShow>, reqwest::Error> {
        let url = format!("{}/shows/{}", BASE_URL, show_id);
        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        Ok(resp.json().await.ok())
    }

    /// List episodes for a series
    pub async fn episodes(&self, show_id: i64) -> Result<Vec<TvMazeEpisode>, reqwest::Error> {
        let url = format!("{}/shows/{}/episodes", BASE_URL, show_id);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("TVMaze episodes parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Cast of a series
    pub async fn cast(&self, show_id: i64) -> Result<Vec<TvMazeCastMember>, reqwest::Error> {
        let url = format!("{}/shows/{}/cast", BASE_URL, show_id);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("TVMaze cast parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Lookup by external ID (TMDB, TVDB, IMDb)
    pub async fn lookup_by_tvdb(&self, tvdb_id: i64) -> Result<Option<TvMazeShow>, reqwest::Error> {
        let url = format!("{}/lookup/shows?thetvdb={}", BASE_URL, tvdb_id);
        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        Ok(resp.json().await.ok())
    }

    pub async fn lookup_by_imdb(
        &self,
        imdb_id: &str,
    ) -> Result<Option<TvMazeShow>, reqwest::Error> {
        let url = format!("{}/lookup/shows?imdb={}", BASE_URL, imdb_id);
        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        Ok(resp.json().await.ok())
    }
}
