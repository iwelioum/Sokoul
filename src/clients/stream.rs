use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamItem {
    pub id: Option<String>,
    pub title: Option<String>,
    pub description: Option<String>,
    pub image: Option<String>,
    pub url: Option<String>,
    pub category: Option<String>,
    pub country: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StreamProgram {
    pub id: Option<String>,
    pub title: Option<String>,
    pub description: Option<String>,
    pub image: Option<String>,
    pub start_time: Option<String>,
    pub end_time: Option<String>,
    pub channel: Option<String>,
}

#[derive(Debug, Clone)]
pub struct StreamClient {
    client: Client,
    base_url: String,
}

impl StreamClient {
    pub fn new(base_url: String) -> Self {
        Self {
            client: Client::new(),
            base_url,
        }
    }

    pub async fn search(&self, query: &str) -> Result<Vec<StreamItem>, String> {
        // Stream API uses a simple search interface
        let url = format!("{}/search?q={}", self.base_url, query);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<StreamItem>>().await {
                        Ok(items) => Ok(items),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_stream(&self, id: &str) -> Result<Option<StreamItem>, String> {
        let url = format!("{}/{}", self.base_url, id);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<StreamItem>().await {
                        Ok(item) => Ok(Some(item)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn get_by_category(&self, category: &str) -> Result<Vec<StreamItem>, String> {
        let url = format!("{}/category/{}", self.base_url, category);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<StreamItem>>().await {
                        Ok(items) => Ok(items),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_programs(&self, channel: &str) -> Result<Vec<StreamProgram>, String> {
        let url = format!("{}/channel/{}/programs", self.base_url, channel);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<StreamProgram>>().await {
                        Ok(programs) => Ok(programs),
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
