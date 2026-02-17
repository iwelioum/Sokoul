use async_trait::async_trait;
use reqwest::Client;
use serde::Deserialize;

use crate::clients::flaresolverr::FlareSolverrClient;

use super::{SearchProvider, TorrentResult};

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct JackettResult {
    pub title: Option<String>,
    pub size: Option<i64>,
    pub seeders: Option<i32>,
    pub peers: Option<i32>,
    pub guid: Option<String>,
    pub magnet_uri: Option<String>,
    pub link: Option<String>,
    pub comments: Option<String>,
    pub tracker: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "PascalCase")]
struct JackettResponse {
    pub results: Vec<JackettResult>,
}

pub struct JackettProvider {
    client: Client,
    api_key: String,
    base_url: String,
    flaresolverr_client: Option<FlareSolverrClient>,
}

impl JackettProvider {
    pub fn new(
        api_key: String,
        base_url: String,
        flaresolverr_client: Option<FlareSolverrClient>,
    ) -> Self {
        Self {
            client: Client::new(),
            api_key,
            base_url,
            flaresolverr_client,
        }
    }

    async fn do_search(&self, query: &str, categories: &str) -> anyhow::Result<Vec<TorrentResult>> {
        let url = format!("{}/api/v2.0/indexers/all/results", self.base_url);

        let mut params = vec![("apikey", self.api_key.as_str()), ("Query", query)];
        if !categories.is_empty() {
            params.push(("Category[]", categories));
        }

        let resp_result = self.client.get(&url).query(&params).send().await;

        let response = match resp_result {
            Ok(r) => r.error_for_status()?.json::<JackettResponse>().await?,
            Err(e) => {
                tracing::warn!("Jackett direct request failed: {}. Attempting with FlareSolverr if configured.", e);
                if let Some(flaresolverr) = &self.flaresolverr_client {
                    tracing::info!(
                        "Attempting Jackett search with FlareSolverr for query: {}",
                        query
                    );
                    let flaresolverr_url = self
                        .client
                        .get(&url)
                        .query(&params)
                        .build()?
                        .url()
                        .to_string();

                    let body = flaresolverr.get(&flaresolverr_url).await?;
                    serde_json::from_str(&body).map_err(|e| {
                        tracing::error!(
                            "Jackett: deserialization error for '{}': {} - response start: {}",
                            query,
                            e,
                            &body[..body.len().min(500)]
                        );
                        e
                    })?
                } else {
                    return Err(e.into());
                }
            }
        };

        let results = response
            .results
            .into_iter()
            .filter_map(|r| {
                let title = r.title?;
                Some(TorrentResult {
                    guid: r.guid.unwrap_or_else(|| title.clone()),
                    title,
                    size_bytes: r.size.unwrap_or(0),
                    indexer: r.tracker.unwrap_or_else(|| "Jackett".to_string()),
                    info_url: r.comments,
                    download_url: r.link,
                    magnet_url: r.magnet_uri,
                    info_hash: None,
                    seeders: r.seeders,
                    leechers: r.peers.map(|p| p.saturating_sub(r.seeders.unwrap_or(0))),
                    protocol: Some("torrent".to_string()),
                    provider_name: "Jackett".to_string(),
                })
            })
            .collect();

        Ok(results)
    }
}

#[async_trait]
impl SearchProvider for JackettProvider {
    fn name(&self) -> &str {
        "Jackett"
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<TorrentResult>> {
        self.do_search(query, "").await
    }

    async fn search_by_tmdb_id(
        &self,
        _tmdb_id: i32,
        media_type: &str,
    ) -> anyhow::Result<Vec<TorrentResult>> {
        // Jackett doesn't natively support TMDB IDs, fall back to empty
        // The Scout worker will use the title-based search instead
        let _ = media_type;
        Ok(vec![])
    }
}
