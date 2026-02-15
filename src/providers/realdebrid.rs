use async_trait::async_trait;
use reqwest::Client;
use serde::{Deserialize, Serialize};

use super::{SearchProvider, TorrentResult};

#[derive(Debug, Deserialize)]
pub struct RdTorrentInfo {
    pub id: String,
    pub filename: String,
    pub bytes: i64,
    pub status: String,
    pub links: Vec<String>,
}

#[derive(Debug, Deserialize)]
pub struct RdUnrestrictResponse {
    pub id: String,
    pub filename: String,
    pub filesize: i64,
    pub download: String,
}

#[derive(Debug, Serialize)]
struct AddMagnetRequest {
    magnet: String,
}

pub struct RealDebridProvider {
    client: Client,
    api_token: String,
}

impl RealDebridProvider {
    pub fn new(api_token: String) -> Self {
        Self {
            client: Client::new(),
            api_token,
        }
    }

    /// Add a magnet link to Real-Debrid and return unrestricted download links
    pub async fn add_magnet(&self, magnet: &str) -> anyhow::Result<RdTorrentInfo> {
        let resp = self
            .client
            .post("https://api.real-debrid.com/rest/1.0/torrents/addMagnet")
            .header("Authorization", format!("Bearer {}", self.api_token))
            .form(&[("magnet", magnet)])
            .send()
            .await?
            .error_for_status()?;

        #[derive(Deserialize)]
        struct AddResponse {
            id: String,
        }
        let add_resp: AddResponse = resp.json().await?;

        // Select all files
        self.client
            .post(format!(
                "https://api.real-debrid.com/rest/1.0/torrents/selectFiles/{}",
                add_resp.id
            ))
            .header("Authorization", format!("Bearer {}", self.api_token))
            .form(&[("files", "all")])
            .send()
            .await?
            .error_for_status()?;

        // Get torrent info
        let info: RdTorrentInfo = self
            .client
            .get(format!(
                "https://api.real-debrid.com/rest/1.0/torrents/info/{}",
                add_resp.id
            ))
            .header("Authorization", format!("Bearer {}", self.api_token))
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;

        Ok(info)
    }

    /// Unrestrict a hoster link to get a direct download URL
    pub async fn unrestrict_link(&self, link: &str) -> anyhow::Result<RdUnrestrictResponse> {
        let resp: RdUnrestrictResponse = self
            .client
            .post("https://api.real-debrid.com/rest/1.0/unrestrict/link")
            .header("Authorization", format!("Bearer {}", self.api_token))
            .form(&[("link", link)])
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;

        Ok(resp)
    }

    /// Check if RD has this torrent already cached (instant availability)
    pub async fn check_instant(&self, info_hash: &str) -> anyhow::Result<bool> {
        let resp = self
            .client
            .get(format!(
                "https://api.real-debrid.com/rest/1.0/torrents/instantAvailability/{}",
                info_hash
            ))
            .header("Authorization", format!("Bearer {}", self.api_token))
            .send()
            .await?
            .error_for_status()?
            .text()
            .await?;

        // If the hash key exists and has content, it's cached
        Ok(resp.contains(info_hash) && resp.len() > 10)
    }
}

#[async_trait]
impl SearchProvider for RealDebridProvider {
    fn name(&self) -> &str {
        "RealDebrid"
    }

    async fn search(&self, _query: &str) -> anyhow::Result<Vec<TorrentResult>> {
        // Real-Debrid is not a search provider, it's a download accelerator
        Ok(vec![])
    }

    async fn search_by_tmdb_id(
        &self,
        _tmdb_id: i32,
        _media_type: &str,
    ) -> anyhow::Result<Vec<TorrentResult>> {
        Ok(vec![])
    }
}
