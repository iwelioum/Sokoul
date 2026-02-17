#![allow(dead_code)]
use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GotCharacter {
    pub id: Option<String>,
    #[serde(alias = "fullName")]
    pub full_name: Option<String>,
    pub title: Option<String>,
    pub family: Option<String>,
    pub image: Option<String>,
    #[serde(alias = "imageUrl")]
    pub image_url: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GotQuote {
    pub sentence: Option<String>,
    pub character: Option<GotCharacter>,
}

#[derive(Debug, Clone)]
pub struct GameOfThronesClient {
    client: Client,
    base_url: String,
}

impl GameOfThronesClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
            base_url: "https://anapioficeandfire.com/api".to_string(),
        }
    }

    pub async fn get_characters(
        &self,
        page: u32,
        page_size: u32,
    ) -> Result<Vec<GotCharacter>, String> {
        let url = format!(
            "{}/characters?page={}&pageSize={}",
            self.base_url, page, page_size
        );

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<GotCharacter>>().await {
                        Ok(characters) => Ok(characters),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_character(&self, id: &str) -> Result<Option<GotCharacter>, String> {
        let url = format!("{}/characters/{}", self.base_url, id);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<GotCharacter>().await {
                        Ok(character) => Ok(Some(character)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn get_houses(&self) -> Result<Vec<serde_json::Value>, String> {
        let url = format!("{}/houses", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<serde_json::Value>>().await {
                        Ok(houses) => Ok(houses),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn get_books(&self) -> Result<Vec<serde_json::Value>, String> {
        let url = format!("{}/books", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<serde_json::Value>>().await {
                        Ok(books) => Ok(books),
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
