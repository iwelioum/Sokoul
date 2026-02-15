pub mod jackett;
pub mod prowlarr;
#[allow(dead_code)]
pub mod realdebrid;
pub mod streaming;

use async_trait::async_trait;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TorrentResult {
    pub title: String,
    pub guid: String,
    pub size_bytes: i64,
    pub indexer: String,
    pub info_url: Option<String>,
    pub download_url: Option<String>,
    pub magnet_url: Option<String>,
    pub info_hash: Option<String>,
    pub seeders: Option<i32>,
    pub leechers: Option<i32>,
    pub protocol: Option<String>,
    pub provider_name: String,
}

#[async_trait]
pub trait SearchProvider: Send + Sync {
    /// Nom unique du provider (ex: "Prowlarr", "StreamingScraper")
    fn name(&self) -> &str;

    /// Recherche textuelle libre
    async fn search(&self, query: &str) -> anyhow::Result<Vec<TorrentResult>>;

    /// Recherche structurée par ID TMDB
    #[allow(dead_code)]
    async fn search_by_tmdb_id(
        &self,
        tmdb_id: i32,
        media_type: &str,
    ) -> anyhow::Result<Vec<TorrentResult>>;
}

pub struct ProviderRegistry {
    providers: Vec<Box<dyn SearchProvider>>,
}

impl ProviderRegistry {
    pub fn new() -> Self {
        Self { providers: vec![] }
    }

    pub fn register(&mut self, provider: Box<dyn SearchProvider>) {
        tracing::info!("Provider '{}' enregistré.", provider.name());
        self.providers.push(provider);
    }

    pub async fn search_all(
        &self,
        title: &str,
        _media_type: &str,
        _tmdb_id: Option<i32>,
    ) -> Vec<TorrentResult> {
        use futures::future::join_all;

        // Always use text search - public indexers don't support TMDB ID search
        let futures = self.providers.iter().map(|provider| {
            let title = title.to_string();
            async move {
                tracing::info!(
                    "Provider '{}': recherche textuelle pour '{}'",
                    provider.name(),
                    title
                );
                let result = provider.search(&title).await;
                (provider.name(), result)
            }
        });

        let results: Vec<TorrentResult> = join_all(futures)
            .await
            .into_iter()
            .flat_map(|(name, result)| match result {
                Ok(res) => {
                    tracing::info!("Provider '{}': {} source(s) trouvee(s)", name, res.len());
                    res
                }
                Err(e) => {
                    tracing::error!("Erreur du provider '{}': {}", name, e);
                    vec![]
                }
            })
            .collect();

        results
    }

    pub fn list_enabled_names(&self) -> Vec<String> {
        self.providers
            .iter()
            .map(|p| p.name().to_string())
            .collect()
    }
}
