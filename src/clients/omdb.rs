use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// OMDb client — IMDb/Rotten Tomatoes ratings, detailed synopsis, awards
#[derive(Clone)]
pub struct OmdbClient {
    client: Client,
    api_key: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct OmdbRating {
    #[serde(rename = "Source")]
    pub source: String,
    #[serde(rename = "Value")]
    pub value: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct OmdbResponse {
    #[serde(rename = "Title")]
    pub title: Option<String>,
    #[serde(rename = "Year")]
    pub year: Option<String>,
    #[serde(rename = "Rated")]
    pub rated: Option<String>,
    #[serde(rename = "Released")]
    pub released: Option<String>,
    #[serde(rename = "Runtime")]
    pub runtime: Option<String>,
    #[serde(rename = "Genre")]
    pub genre: Option<String>,
    #[serde(rename = "Director")]
    pub director: Option<String>,
    #[serde(rename = "Writer")]
    pub writer: Option<String>,
    #[serde(rename = "Actors")]
    pub actors: Option<String>,
    #[serde(rename = "Plot")]
    pub plot: Option<String>,
    #[serde(rename = "Language")]
    pub language: Option<String>,
    #[serde(rename = "Country")]
    pub country: Option<String>,
    #[serde(rename = "Awards")]
    pub awards: Option<String>,
    #[serde(rename = "Poster")]
    pub poster: Option<String>,
    #[serde(rename = "Ratings")]
    #[serde(default)]
    pub ratings: Vec<OmdbRating>,
    pub imdb_rating: Option<String>,
    #[serde(rename = "imdbRating")]
    pub imdb_rating_field: Option<String>,
    #[serde(rename = "imdbVotes")]
    pub imdb_votes: Option<String>,
    #[serde(rename = "imdbID")]
    pub imdb_id: Option<String>,
    #[serde(rename = "Type")]
    pub media_type: Option<String>,
    #[serde(rename = "BoxOffice")]
    pub box_office: Option<String>,
    #[serde(rename = "Production")]
    pub production: Option<String>,
    #[serde(rename = "Response")]
    pub response: Option<String>,
}

impl OmdbClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            api_key,
        }
    }

    /// Search by IMDb ID (most precise)
    pub async fn get_by_imdb_id(&self, imdb_id: &str) -> Result<OmdbResponse, reqwest::Error> {
        let url = format!(
            "https://www.omdbapi.com/?apikey={}&i={}&plot=full",
            self.api_key, imdb_id
        );
        let resp = self.client.get(&url).send().await?;
        let data = resp.json::<OmdbResponse>().await.unwrap_or_else(|e| {
            error!("OMDb parse error for {}: {}", imdb_id, e);
            OmdbResponse::default()
        });
        Ok(data)
    }

    /// Search by title
    pub async fn search_by_title(
        &self,
        title: &str,
        year: Option<i32>,
    ) -> Result<OmdbResponse, reqwest::Error> {
        let encoded_title: String = title
            .chars()
            .map(|c| {
                if c.is_ascii_alphanumeric() || c == '-' || c == '_' || c == '.' {
                    c.to_string()
                } else if c == ' ' {
                    "+".to_string()
                } else {
                    format!("%{:02X}", c as u32)
                }
            })
            .collect();
        let mut url = format!(
            "https://www.omdbapi.com/?apikey={}&t={}&plot=full",
            self.api_key, encoded_title
        );
        if let Some(y) = year {
            url.push_str(&format!("&y={}", y));
        }
        let resp = self.client.get(&url).send().await?;
        let data = resp.json::<OmdbResponse>().await.unwrap_or_else(|e| {
            error!("OMDb parse error for title '{}': {}", title, e);
            OmdbResponse::default()
        });
        Ok(data)
    }
}

impl Default for OmdbResponse {
    fn default() -> Self {
        Self {
            title: None,
            year: None,
            rated: None,
            released: None,
            runtime: None,
            genre: None,
            director: None,
            writer: None,
            actors: None,
            plot: None,
            language: None,
            country: None,
            awards: None,
            poster: None,
            ratings: vec![],
            imdb_rating: None,
            imdb_rating_field: None,
            imdb_votes: None,
            imdb_id: None,
            media_type: None,
            box_office: None,
            production: None,
            response: None,
        }
    }
}
