#![allow(dead_code)]
use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StrangerThingsQuote {
    pub id: Option<u32>,
    pub quote: Option<String>,
    pub character: Option<String>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
}

#[derive(Debug, Clone)]
pub struct StrangerThingsClient {
    client: Client,
    base_url: String,
}

impl StrangerThingsClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
            base_url: "https://api.strangerthingsapi.com".to_string(),
        }
    }

    pub async fn get_quotes(&self) -> Result<Vec<StrangerThingsQuote>, String> {
        let url = format!("{}/quotes", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<StrangerThingsQuote>>().await {
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

    pub async fn random_quote(&self) -> Result<Option<StrangerThingsQuote>, String> {
        let url = format!("{}/quote/random", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<StrangerThingsQuote>().await {
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

    pub async fn get_quote_by_character(
        &self,
        character: &str,
    ) -> Result<Vec<StrangerThingsQuote>, String> {
        let url = format!("{}/quotes?character={}", self.base_url, character);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<StrangerThingsQuote>>().await {
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
