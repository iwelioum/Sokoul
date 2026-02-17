use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::error;

/// Client Fanart.tv — logos HD, thumbs, backdrops artistiques
#[derive(Clone)]
pub struct FanartClient {
    client: Client,
    api_key: String,
    client_key: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct FanartImage {
    pub id: Option<String>,
    pub url: Option<String>,
    pub lang: Option<String>,
    pub likes: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone, Default)]
pub struct FanartMovieImages {
    #[serde(default)]
    pub hdmovielogo: Vec<FanartImage>,
    #[serde(default)]
    pub hdmovieclearart: Vec<FanartImage>,
    #[serde(default)]
    pub movieposter: Vec<FanartImage>,
    #[serde(default)]
    pub moviebackground: Vec<FanartImage>,
    #[serde(default)]
    pub moviethumb: Vec<FanartImage>,
    #[serde(default)]
    pub moviebanner: Vec<FanartImage>,
    #[serde(default)]
    pub moviedisc: Vec<FanartImage>,
    #[serde(default)]
    pub movieart: Vec<FanartImage>,
}

#[derive(Debug, Serialize, Deserialize, Clone, Default)]
pub struct FanartTvImages {
    #[serde(default)]
    pub hdtvlogo: Vec<FanartImage>,
    #[serde(default)]
    pub hdclearart: Vec<FanartImage>,
    #[serde(default)]
    pub tvposter: Vec<FanartImage>,
    #[serde(default)]
    pub tvbanner: Vec<FanartImage>,
    #[serde(default)]
    pub tvthumb: Vec<FanartImage>,
    #[serde(default)]
    pub showbackground: Vec<FanartImage>,
    #[serde(default)]
    pub seasonposter: Vec<FanartImage>,
    #[serde(default)]
    pub seasonbanner: Vec<FanartImage>,
    #[serde(default)]
    pub characterart: Vec<FanartImage>,
}

impl FanartClient {
    pub fn new(api_key: String, client_key: Option<String>) -> Self {
        Self {
            client: Client::new(),
            api_key,
            client_key,
        }
    }

    /// Fetch Fanart.tv images for a movie (by TMDB ID)
    pub async fn movie_images(&self, tmdb_id: i32) -> Result<FanartMovieImages, reqwest::Error> {
        let mut url = format!(
            "https://webservice.fanart.tv/v3/movies/{}?api_key={}",
            tmdb_id, self.api_key
        );
        if let Some(ref ck) = self.client_key {
            url.push_str(&format!("&client_key={}", ck));
        }

        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(FanartMovieImages::default());
        }
        let images = resp.json::<FanartMovieImages>().await.unwrap_or_else(|e| {
            error!("Fanart.tv movie parse error: {}", e);
            FanartMovieImages::default()
        });
        Ok(images)
    }

    /// Fetch Fanart.tv images for a TV show (by TVDB ID)
    pub async fn tv_images(&self, tvdb_id: i32) -> Result<FanartTvImages, reqwest::Error> {
        let mut url = format!(
            "https://webservice.fanart.tv/v3/tv/{}?api_key={}",
            tvdb_id, self.api_key
        );
        if let Some(ref ck) = self.client_key {
            url.push_str(&format!("&client_key={}", ck));
        }

        let resp = self.client.get(&url).send().await?;
        if resp.status() == 404 {
            return Ok(FanartTvImages::default());
        }
        let images = resp.json::<FanartTvImages>().await.unwrap_or_else(|e| {
            error!("Fanart.tv TV parse error: {}", e);
            FanartTvImages::default()
        });
        Ok(images)
    }
}
