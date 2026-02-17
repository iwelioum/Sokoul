use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// Client Jikan (MyAnimeList unofficial) — Anime/Manga database (gratuit, sans auth)
#[derive(Clone)]
pub struct JikanClient {
    client: Client,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanResponse<T> {
    pub data: T,
    pub pagination: Option<JikanPagination>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanPagination {
    pub last_visible_page: Option<i32>,
    pub has_next_page: Option<bool>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanAnime {
    pub mal_id: i64,
    pub url: Option<String>,
    pub images: Option<JikanImages>,
    pub trailer: Option<JikanTrailer>,
    pub title: Option<String>,
    pub title_english: Option<String>,
    pub title_japanese: Option<String>,
    #[serde(rename = "type")]
    pub anime_type: Option<String>,
    pub episodes: Option<i32>,
    pub status: Option<String>,
    pub airing: Option<bool>,
    pub duration: Option<String>,
    pub rating: Option<String>,
    pub score: Option<f64>,
    pub scored_by: Option<i64>,
    pub rank: Option<i32>,
    pub popularity: Option<i32>,
    pub members: Option<i64>,
    pub synopsis: Option<String>,
    pub season: Option<String>,
    pub year: Option<i32>,
    pub genres: Option<Vec<JikanGenre>>,
    pub studios: Option<Vec<JikanStudio>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanImages {
    pub jpg: Option<JikanImageFormat>,
    pub webp: Option<JikanImageFormat>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanImageFormat {
    pub image_url: Option<String>,
    pub small_image_url: Option<String>,
    pub large_image_url: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanTrailer {
    pub youtube_id: Option<String>,
    pub url: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanGenre {
    pub mal_id: i64,
    pub name: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanStudio {
    pub mal_id: i64,
    pub name: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanCharacter {
    pub character: Option<JikanCharacterInfo>,
    pub role: Option<String>,
    pub voice_actors: Option<Vec<JikanVoiceActor>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanCharacterInfo {
    pub mal_id: i64,
    pub name: Option<String>,
    pub images: Option<JikanImages>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanVoiceActor {
    pub person: Option<JikanPerson>,
    pub language: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JikanPerson {
    pub mal_id: i64,
    pub name: Option<String>,
    pub images: Option<JikanImages>,
}

const BASE_URL: &str = "https://api.jikan.moe/v4";

impl JikanClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
        }
    }

    /// Search for anime
    pub async fn search_anime(
        &self,
        query: &str,
        page: u32,
    ) -> Result<JikanResponse<Vec<JikanAnime>>, reqwest::Error> {
        let url = format!("{}/anime?q={}&page={}&sfw=true", BASE_URL, query, page);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(JikanResponse {
                data: vec![],
                pagination: None,
            });
        }
        resp.json().await.or_else(|e| {
            error!("Jikan search parse error: {}", e);
            Ok(JikanResponse {
                data: vec![],
                pagination: None,
            })
        })
    }

    /// Anime details by MAL ID
    pub async fn anime_details(&self, mal_id: i64) -> Result<Option<JikanAnime>, reqwest::Error> {
        let url = format!("{}/anime/{}/full", BASE_URL, mal_id);
        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(None);
        }
        let result: Result<JikanResponse<JikanAnime>, _> = resp.json().await;
        Ok(result.ok().map(|r| r.data))
    }

    /// Top anime (by score, popularity, etc.)
    pub async fn top_anime(
        &self,
        filter: &str,
        page: u32,
        limit: u32,
    ) -> Result<JikanResponse<Vec<JikanAnime>>, reqwest::Error> {
        let url = format!(
            "{}/top/anime?filter={}&page={}&limit={}",
            BASE_URL, filter, page, limit
        );
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(JikanResponse {
                data: vec![],
                pagination: None,
            });
        }
        resp.json().await.or_else(|e| {
            error!("Jikan top anime parse error: {}", e);
            Ok(JikanResponse {
                data: vec![],
                pagination: None,
            })
        })
    }

    /// Anime by season
    pub async fn season_anime(
        &self,
        year: i32,
        season: &str,
        page: u32,
    ) -> Result<JikanResponse<Vec<JikanAnime>>, reqwest::Error> {
        let url = format!("{}/seasons/{}/{}?page={}", BASE_URL, year, season, page);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(JikanResponse {
                data: vec![],
                pagination: None,
            });
        }
        resp.json().await.or_else(|e| {
            error!("Jikan season parse error: {}", e);
            Ok(JikanResponse {
                data: vec![],
                pagination: None,
            })
        })
    }

    /// Personnages d'un anime
    pub async fn anime_characters(
        &self,
        mal_id: i64,
    ) -> Result<Vec<JikanCharacter>, reqwest::Error> {
        let url = format!("{}/anime/{}/characters", BASE_URL, mal_id);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(vec![]);
        }
        let result: Result<JikanResponse<Vec<JikanCharacter>>, _> = resp.json().await;
        Ok(result.map(|r| r.data).unwrap_or_default())
    }

    /// Recommendations based on an anime
    pub async fn anime_recommendations(
        &self,
        mal_id: i64,
    ) -> Result<serde_json::Value, reqwest::Error> {
        let url = format!("{}/anime/{}/recommendations", BASE_URL, mal_id);
        let resp = self.client.get(&url).send().await?;
        if !resp.status().is_success() {
            return Ok(serde_json::json!({"data": []}));
        }
        resp.json().await.or_else(|e| {
            error!("Jikan recommendations parse error: {}", e);
            Ok(serde_json::json!({"data": []}))
        })
    }
}
