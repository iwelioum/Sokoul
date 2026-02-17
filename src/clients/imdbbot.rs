use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImdbMovie {
    pub id: Option<String>,
    pub title: Option<String>,
    pub year: Option<u32>,
    pub description: Option<String>,
    pub image: Option<String>,
    pub imdb_id: Option<String>,
    pub rating: Option<f32>,
    pub vote_count: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImdbSearchResult {
    #[serde(rename = "@type")]
    pub result_type: Option<String>,
    #[serde(rename = "id")]
    pub id: Option<String>,
    #[serde(rename = "image")]
    pub image: Option<String>,
    #[serde(rename = "description")]
    pub description: Option<String>,
    #[serde(rename = "title")]
    pub title: Option<String>,
}

#[derive(Debug, Clone)]
pub struct ImdbBotClient {
    client: Client,
    base_url: String,
}

impl ImdbBotClient {
    pub fn new(base_url: String) -> Self {
        Self {
            client: Client::new(),
            base_url,
        }
    }

    pub async fn search_movie(&self, query: &str) -> Result<Vec<ImdbSearchResult>, String> {
        let url = format!(
            "{}/en/API/AdvancedSearch?title={}&sort=rank",
            self.base_url, query
        );

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<serde_json::Value>().await {
                        Ok(json) => {
                            let results = json
                                .get("results")
                                .and_then(|r| r.as_array())
                                .unwrap_or(&vec![])
                                .iter()
                                .filter_map(|item| {
                                    serde_json::from_value::<ImdbSearchResult>(item.clone()).ok()
                                })
                                .collect();
                            Ok(results)
                        }
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_movie_details(&self, imdb_id: &str) -> Result<Option<ImdbMovie>, String> {
        let url = format!("{}/en/API/Title/{}", self.base_url, imdb_id);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<ImdbMovie>().await {
                        Ok(movie) => Ok(Some(movie)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn get_ratings(&self, imdb_id: &str) -> Result<Option<serde_json::Value>, String> {
        let url = format!("{}/en/API/Title/{}", self.base_url, imdb_id);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<serde_json::Value>().await {
                        Ok(json) => Ok(Some(json)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }
}
