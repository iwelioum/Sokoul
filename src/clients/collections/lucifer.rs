#![allow(dead_code)]
use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LuciferQuote {
    pub id: Option<u32>,
    pub quote: Option<String>,
    pub character: Option<String>,
    pub episode: Option<String>,
}

#[derive(Debug, Clone)]
pub struct LuciferClient {
    client: Client,
    base_url: String,
}

impl LuciferClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
            base_url: "https://api.luciferquotes.xyz".to_string(),
        }
    }

    pub async fn get_quotes(&self) -> Result<Vec<LuciferQuote>, String> {
        let url = format!("{}/quotes", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<LuciferQuote>>().await {
                        Ok(quotes) => Ok(quotes),
                        Err(_) => Ok(vec![]),
                    }
                } else {
                    Ok(vec![])
                }
            }
            Err(_) => Ok(vec![]),
        }
    }

    pub async fn random_quote(&self) -> Result<Option<LuciferQuote>, String> {
        let url = format!("{}/quote/random", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<LuciferQuote>().await {
                        Ok(quote) => Ok(Some(quote)),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn get_quotes_by_character(
        &self,
        character: &str,
    ) -> Result<Vec<LuciferQuote>, String> {
        let url = format!("{}/character/{}", self.base_url, character);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<LuciferQuote>>().await {
                        Ok(quotes) => Ok(quotes),
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
