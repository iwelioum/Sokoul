use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// Trakt client — Tracking, trending, movie/TV show recommendations
#[derive(Clone)]
pub struct TraktClient {
    client: Client,
    client_id: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktMovie {
    pub title: Option<String>,
    pub year: Option<i32>,
    pub ids: Option<TraktIds>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktShow {
    pub title: Option<String>,
    pub year: Option<i32>,
    pub ids: Option<TraktIds>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktIds {
    pub trakt: Option<i64>,
    pub slug: Option<String>,
    pub imdb: Option<String>,
    pub tmdb: Option<i64>,
    pub tvdb: Option<i64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktTrendingMovie {
    pub watchers: Option<i64>,
    pub movie: TraktMovie,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktTrendingShow {
    pub watchers: Option<i64>,
    pub show: TraktShow,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[allow(dead_code)]
pub struct TraktPopularMovie(pub TraktMovie);

#[derive(Debug, Serialize, Deserialize, Clone)]
#[allow(dead_code)]
pub struct TraktPopularShow(pub TraktShow);

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktMovieDetails {
    pub title: Option<String>,
    pub year: Option<i32>,
    pub ids: Option<TraktIds>,
    pub tagline: Option<String>,
    pub overview: Option<String>,
    pub released: Option<String>,
    pub runtime: Option<i32>,
    pub certification: Option<String>,
    pub trailer: Option<String>,
    pub genres: Option<Vec<String>>,
    pub rating: Option<f64>,
    pub votes: Option<i64>,
    pub language: Option<String>,
    pub country: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TraktShowDetails {
    pub title: Option<String>,
    pub year: Option<i32>,
    pub ids: Option<TraktIds>,
    pub overview: Option<String>,
    pub first_aired: Option<String>,
    pub runtime: Option<i32>,
    pub certification: Option<String>,
    pub network: Option<String>,
    pub trailer: Option<String>,
    pub genres: Option<Vec<String>>,
    pub rating: Option<f64>,
    pub votes: Option<i64>,
    pub language: Option<String>,
    pub aired_episodes: Option<i32>,
    pub status: Option<String>,
}

const BASE_URL: &str = "https://api.trakt.tv";

impl TraktClient {
    pub fn new(client_id: String) -> Self {
        Self {
            client: Client::new(),
            client_id,
        }
    }

    fn headers(&self) -> Vec<(&str, String)> {
        vec![
            ("Content-Type", "application/json".to_string()),
            ("trakt-api-version", "2".to_string()),
            ("trakt-api-key", self.client_id.clone()),
        ]
    }

    fn build_request(&self, url: &str) -> reqwest::RequestBuilder {
        let mut req = self.client.get(url);
        for (k, v) in self.headers() {
            req = req.header(k, v);
        }
        req
    }

    /// Films tendance
    pub async fn trending_movies(
        &self,
        limit: u32,
    ) -> Result<Vec<TraktTrendingMovie>, reqwest::Error> {
        let url = format!("{}/movies/trending?limit={}", BASE_URL, limit);
        let resp = self.build_request(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Trakt trending movies parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Films populaires
    pub async fn popular_movies(&self, limit: u32) -> Result<Vec<TraktMovie>, reqwest::Error> {
        let url = format!("{}/movies/popular?limit={}", BASE_URL, limit);
        let resp = self.build_request(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Trakt popular movies parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Trending TV shows
    pub async fn trending_shows(
        &self,
        limit: u32,
    ) -> Result<Vec<TraktTrendingShow>, reqwest::Error> {
        let url = format!("{}/shows/trending?limit={}", BASE_URL, limit);
        let resp = self.build_request(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Trakt trending shows parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Popular TV shows
    pub async fn popular_shows(&self, limit: u32) -> Result<Vec<TraktShow>, reqwest::Error> {
        let url = format!("{}/shows/popular?limit={}", BASE_URL, limit);
        let resp = self.build_request(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Trakt popular shows parse error: {}", e);
            Ok(vec![])
        })
    }

    /// Movie details by Trakt slug or TMDB ID
    pub async fn movie_details(
        &self,
        id: &str,
    ) -> Result<Option<TraktMovieDetails>, reqwest::Error> {
        let url = format!("{}/movies/{}?extended=full", BASE_URL, id);
        let resp = self.build_request(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        Ok(resp.json().await.ok())
    }

    /// Series details by Trakt slug or TMDB ID
    pub async fn show_details(&self, id: &str) -> Result<Option<TraktShowDetails>, reqwest::Error> {
        let url = format!("{}/shows/{}?extended=full", BASE_URL, id);
        let resp = self.build_request(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        Ok(resp.json().await.ok())
    }

    /// Similar movies/series (recommendations)
    pub async fn movie_related(
        &self,
        id: &str,
        limit: u32,
    ) -> Result<Vec<TraktMovie>, reqwest::Error> {
        let url = format!("{}/movies/{}/related?limit={}", BASE_URL, id, limit);
        let resp = self.build_request(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Trakt related movies parse error: {}", e);
            Ok(vec![])
        })
    }

    pub async fn show_related(
        &self,
        id: &str,
        limit: u32,
    ) -> Result<Vec<TraktShow>, reqwest::Error> {
        let url = format!("{}/shows/{}/related?limit={}", BASE_URL, id, limit);
        let resp = self.build_request(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        resp.json().await.or_else(|e| {
            error!("Trakt related shows parse error: {}", e);
            Ok(vec![])
        })
    }
}
