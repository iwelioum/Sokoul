use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UnogsResult {
    pub id: Option<String>,
    pub title: Option<String>,
    pub image: Option<String>,
    pub year: Option<u32>,
    #[serde(rename = "type")]
    pub content_type: Option<String>,
    pub imdb_id: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UnogsSearchResponse {
    pub results: Option<Vec<UnogsResult>>,
    pub pagination: Option<serde_json::Value>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UnogsRegion {
    pub id: Option<String>,
    pub country: Option<String>,
}

#[derive(Debug, Clone)]
pub struct UnogsClient {
    client: Client,
    base_url: String,
    api_key: String,
}

impl UnogsClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            base_url: "https://unogsng.p.rapidapi.com".to_string(),
            api_key,
        }
    }

    pub async fn search_netflix(
        &self,
        query: &str,
        content_type: &str,
    ) -> Result<Vec<UnogsResult>, String> {
        let type_filter = match content_type {
            "tv" => "1",
            "movie" => "2",
            _ => "0",
        };

        let url = format!(
            "{}/search/titles?query={}&type={}",
            self.base_url, query, type_filter
        );

        match self
            .client
            .get(&url)
            .header("X-RapidAPI-Key", &self.api_key)
            .header("X-RapidAPI-Host", "unogsng.p.rapidapi.com")
            .send()
            .await
        {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<UnogsSearchResponse>().await {
                        Ok(data) => Ok(data.results.unwrap_or_default()),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_by_imdb_id(&self, imdb_id: &str) -> Result<Option<UnogsResult>, String> {
        let url = format!("{}/imdb/{}", self.base_url, imdb_id);

        match self
            .client
            .get(&url)
            .header("X-RapidAPI-Key", &self.api_key)
            .header("X-RapidAPI-Host", "unogsng.p.rapidapi.com")
            .send()
            .await
        {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<UnogsResult>().await {
                        Ok(result) => Ok(Some(result)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn netflix_regions(&self) -> Result<Vec<UnogsRegion>, String> {
        let url = format!("{}/regions", self.base_url);

        match self
            .client
            .get(&url)
            .header("X-RapidAPI-Key", &self.api_key)
            .header("X-RapidAPI-Host", "unogsng.p.rapidapi.com")
            .send()
            .await
        {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<UnogsRegion>>().await {
                        Ok(regions) => Ok(regions),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn search_by_region(
        &self,
        query: &str,
        region: &str,
    ) -> Result<Vec<UnogsResult>, String> {
        let url = format!(
            "{}/search/titles?query={}&countrylist={}",
            self.base_url, query, region
        );

        match self
            .client
            .get(&url)
            .header("X-RapidAPI-Key", &self.api_key)
            .header("X-RapidAPI-Host", "unogsng.p.rapidapi.com")
            .send()
            .await
        {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<UnogsSearchResponse>().await {
                        Ok(data) => Ok(data.results.unwrap_or_default()),
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
