#![allow(dead_code)]
use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BreakingBadCharacter {
    pub char_id: Option<u32>,
    pub name: Option<String>,
    pub birthday: Option<String>,
    pub occupation: Option<Vec<String>>,
    pub img: Option<String>,
    pub status: Option<String>,
    pub nickname: Option<String>,
    pub appearance: Option<Vec<u32>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BreakingBadQuote {
    pub quote_id: Option<u32>,
    pub quote: Option<String>,
    pub author: Option<String>,
    pub series: Option<String>,
}

#[derive(Debug, Clone)]
pub struct BreakingBadClient {
    client: Client,
    base_url: String,
}

impl BreakingBadClient {
    pub fn new() -> Self {
        Self {
            client: Client::new(),
            base_url: "https://www.breakingbadapi.com/api".to_string(),
        }
    }

    pub async fn get_characters(&self) -> Result<Vec<BreakingBadCharacter>, String> {
        let url = format!("{}/characters", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<BreakingBadCharacter>>().await {
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

    pub async fn get_character(&self, id: u32) -> Result<Option<BreakingBadCharacter>, String> {
        let url = format!("{}/characters/{}", self.base_url, id);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<BreakingBadCharacter>>().await {
                        Ok(mut chars) => Ok(chars.pop()),
                        Err(_) => Ok(None),
                    }
                } else {
                    Ok(None)
                }
            }
            Err(_) => Ok(None),
        }
    }

    pub async fn get_quotes(&self) -> Result<Vec<BreakingBadQuote>, String> {
        let url = format!("{}/quotes", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<BreakingBadQuote>>().await {
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

    pub async fn random_quote(&self) -> Result<Option<BreakingBadQuote>, String> {
        let url = format!("{}/quote/random", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<BreakingBadQuote>>().await {
                        Ok(mut quotes) => Ok(quotes.pop()),
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
