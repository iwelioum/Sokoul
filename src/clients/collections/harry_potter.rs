#![allow(dead_code)]
use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HarryPotterCharacter {
    pub id: Option<String>,
    pub name: Option<String>,
    pub alternate_names: Option<Vec<String>>,
    pub species: Option<String>,
    pub gender: Option<String>,
    pub house: Option<String>,
    pub wizard: Option<bool>,
    pub image: Option<String>,
    pub patronus: Option<String>,
}

#[derive(Debug, Clone)]
pub struct HarryPotterClient {
    client: Client,
    base_url: String,
    api_key: String,
}

impl HarryPotterClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            base_url: "https://hp-api.herokuapp.com/api".to_string(),
            api_key,
        }
    }

    pub async fn get_characters(&self) -> Result<Vec<HarryPotterCharacter>, String> {
        let url = format!("{}/characters", self.base_url);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<HarryPotterCharacter>>().await {
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

    pub async fn get_character(&self, id: &str) -> Result<Option<HarryPotterCharacter>, String> {
        let url = format!("{}/character/{}", self.base_url, id);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<HarryPotterCharacter>().await {
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

    pub async fn get_characters_by_house(
        &self,
        house: &str,
    ) -> Result<Vec<HarryPotterCharacter>, String> {
        let url = format!("{}/characters?house={}", self.base_url, house);

        match self.client.get(&url).send().await {
            Ok(resp) => {
                if resp.status() == 200 {
                    match resp.json::<Vec<HarryPotterCharacter>>().await {
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

    pub async fn get_houses(&self) -> Result<Vec<String>, String> {
        let houses = vec![
            "Gryffindor".to_string(),
            "Slytherin".to_string(),
            "Hufflepuff".to_string(),
            "Ravenclaw".to_string(),
        ];
        Ok(houses)
    }
}
