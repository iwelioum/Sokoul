use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SimklSearchResult {
    pub id: Option<i64>,
    pub title: Option<String>,
    pub year: Option<u32>,
    pub poster: Option<String>,
    pub fanart: Option<String>,
    #[serde(rename = "type")]
    pub item_type: Option<String>,
    pub rating: Option<f32>,
    pub imdb: Option<String>,
    pub tmdb: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SimklShowDetails {
    pub id: Option<i64>,
    pub title: Option<String>,
    pub year: Option<u32>,
    pub description: Option<String>,
    pub poster: Option<String>,
    pub fanart: Option<String>,
    pub seasons: Option<i32>,
    pub episodes: Option<i32>,
    pub rating: Option<f32>,
    pub genres: Option<Vec<String>>,
    pub country: Option<String>,
    pub runtime: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SimklStreamSource {
    pub source: Option<String>,
    pub url: Option<String>,
    pub region: Option<String>,
    pub is_free: Option<bool>,
}

#[derive(Debug, Clone)]
pub struct SimklClient {
    client: Client,
    base_url: String,
    api_key: String,
}

impl SimklClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            base_url: "https://api.simkl.com".to_string(),
            api_key,
        }
    }

    pub async fn search(
        &self,
        query: &str,
        item_type: &str,
    ) -> Result<Vec<SimklSearchResult>, String> {
        let url = format!(
            "{}/search/{}?q={}&client_id={}",
            self.base_url, item_type, query, self.api_key
        );

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<SimklSearchResult>>().await {
                        Ok(results) => Ok(results),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_details(
        &self,
        id: i64,
        item_type: &str,
    ) -> Result<Option<SimklShowDetails>, String> {
        let endpoint = match item_type {
            "tv" => "shows",
            "movie" => "movies",
            "anime" => "anime",
            _ => "shows",
        };

        let url = format!(
            "{}/{}/{}?client_id={}",
            self.base_url, endpoint, id, self.api_key
        );

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<SimklShowDetails>().await {
                        Ok(details) => Ok(Some(details)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn trending(&self, item_type: &str) -> Result<Vec<SimklSearchResult>, String> {
        let endpoint = match item_type {
            "tv" => "shows",
            "movie" => "movies",
            "anime" => "anime",
            _ => "shows",
        };

        let url = format!(
            "{}/{}/trending?client_id={}",
            self.base_url, endpoint, self.api_key
        );

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<SimklSearchResult>>().await {
                        Ok(results) => Ok(results),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_sources(&self, id: i64) -> Result<Vec<SimklStreamSource>, String> {
        let url = format!(
            "{}/movies/{}/watching?client_id={}",
            self.base_url, id, self.api_key
        );

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<SimklStreamSource>>().await {
                        Ok(sources) => Ok(sources),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }
}
