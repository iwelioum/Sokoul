use async_trait::async_trait;
use reqwest::Client;
use serde::Deserialize;

use crate::clients::flaresolverr::FlareSolverrClient;

use super::{SearchProvider, TorrentResult};

#[derive(Debug, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct ProwlarrSearchResult {
    pub title: String,
    pub size: i64,
    pub seeders: Option<i32>,
    pub leechers: Option<i32>,
    pub guid: String,
    pub info_hash: Option<String>,
    pub protocol: Option<String>,
    pub magnet_url: Option<String>,
    pub download_url: Option<String>,
    pub info_url: Option<String>,
    pub indexer: Option<String>,
}

pub struct ProwlarrProvider {
    client: Client,
    api_key: String,
    base_url: String,
    flaresolverr_client: Option<FlareSolverrClient>,
}

impl ProwlarrProvider {
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
}

#[async_trait]
impl SearchProvider for ProwlarrProvider {
    fn name(&self) -> &str {
        "Prowlarr"
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<TorrentResult>> {
        let url = format!("{}/api/v1/search", self.base_url);

        tracing::info!("Prowlarr: searching '{}' on {}", query, url);

        let resp_result = self
            .client
            .get(&url)
            .query(&[
                ("apikey", self.api_key.as_str()),
                ("query", query),
                ("type", "search"),
            ])
            .send()
            .await;

        let resp = match resp_result {
            Ok(r) => r.error_for_status(),
            Err(e) => {
                tracing::warn!("Prowlarr direct request failed: {}. Attempting with FlareSolverr if configured.", e);
                if let Some(flaresolverr) = &self.flaresolverr_client {
                    tracing::info!(
                        "Attempting Prowlarr search with FlareSolverr for query: {}",
                        query
                    );
                    let flaresolverr_url = self
                        .client
                        .get(&url)
                        .query(&[
                            ("apikey", self.api_key.as_str()),
                            ("query", query),
                            ("type", "search"),
                        ])
                        .build()?
                        .url()
                        .to_string();

                    let body = flaresolverr.get(&flaresolverr_url).await?;
                    let response: Vec<ProwlarrSearchResult> =
                        serde_json::from_str(&body).map_err(|e| {
                            tracing::error!(
                                "Prowlarr: deserialization error for '{}': {} - response start: {}",
                                query,
                                e,
                                &body[..body.len().min(500)]
                            );
                            e
                        })?;

                    return Ok(response
                        .into_iter()
                        .map(|r| TorrentResult {
                            title: r.title,
                            guid: r.guid,
                            size_bytes: r.size,
                            indexer: r.indexer.unwrap_or_else(|| "unknown".to_string()),
                            info_url: r.info_url,
                            download_url: r.download_url,
                            magnet_url: r.magnet_url,
                            info_hash: r.info_hash,
                            seeders: r.seeders,
                            leechers: r.leechers,
                            protocol: r.protocol,
                            provider_name: "Prowlarr".to_string(),
                        })
                        .collect());
                } else {
                    return Err(e.into());
                }
            }
        }?;

        let body = resp.text().await?;
        tracing::info!("Prowlarr: response {} bytes for '{}'", body.len(), query);

        let response: Vec<ProwlarrSearchResult> = serde_json::from_str(&body).map_err(|e| {
            tracing::error!(
                "Prowlarr: deserialization error for '{}': {} - response start: {}",
                query,
                e,
                &body[..body.len().min(500)]
            );
            e
        })?;

        let results = response
            .into_iter()
            .map(|r| TorrentResult {
                title: r.title,
                guid: r.guid,
                size_bytes: r.size,
                indexer: r.indexer.unwrap_or_else(|| "unknown".to_string()),
                info_url: r.info_url,
                download_url: r.download_url,
                magnet_url: r.magnet_url,
                info_hash: r.info_hash,
                seeders: r.seeders,
                leechers: r.leechers,
                protocol: r.protocol,
                provider_name: "Prowlarr".to_string(),
            })
            .collect();

        Ok(results)
    }

    async fn search_by_tmdb_id(
        &self,
        tmdb_id: i32,
        media_type: &str,
    ) -> anyhow::Result<Vec<TorrentResult>> {
        let url = format!("{}/api/v1/search", self.base_url);
        let categories = match media_type {
            "movie" => "2000",
            "tv" => "5000",
            _ => "",
        };

        let resp_result = self
            .client
            .get(&url)
            .query(&[
                ("apikey", self.api_key.as_str()),
                ("query", &format!("{{TmdbId:{}}}", tmdb_id)),
                ("categories", categories),
                ("type", "search"),
            ])
            .send()
            .await;

        let response = match resp_result {
            Ok(r) => {
                r.error_for_status()?
                    .json::<Vec<ProwlarrSearchResult>>()
                    .await?
            }
            Err(e) => {
                tracing::warn!("Prowlarr direct request failed for TMDB ID {}: {}. Attempting with FlareSolverr if configured.", tmdb_id, e);
                if let Some(flaresolverr) = &self.flaresolverr_client {
                    tracing::info!(
                        "Attempting Prowlarr search by TMDB ID {} with FlareSolverr.",
                        tmdb_id
                    );
                    let flaresolverr_url = self
                        .client
                        .get(&url)
                        .query(&[
                            ("apikey", self.api_key.as_str()),
                            ("query", &format!("{{TmdbId:{}}}", tmdb_id)),
                            ("categories", categories),
                            ("type", "search"),
                        ])
                        .build()?
                        .url()
                        .to_string();

                    let body = flaresolverr.get(&flaresolverr_url).await?;
                    serde_json::from_str(&body)
                        .map_err(|e| {
                            tracing::error!("Prowlarr: deserialization error for TMDB ID {}: {} - response start: {}", tmdb_id, e, &body[..body.len().min(500)]);
                            e
                        })?
                } else {
                    return Err(e.into());
                }
            }
        };

        let results = response
            .into_iter()
            .map(|r| TorrentResult {
                title: r.title,
                guid: r.guid,
                size_bytes: r.size,
                indexer: r.indexer.unwrap_or_else(|| "unknown".to_string()),
                info_url: r.info_url,
                download_url: r.download_url,
                magnet_url: r.magnet_url,
                info_hash: r.info_hash,
                seeders: r.seeders,
                leechers: r.leechers,
                protocol: r.protocol,
                provider_name: "Prowlarr".to_string(),
            })
            .collect();

        Ok(results)
    }
}
