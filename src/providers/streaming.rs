use crate::providers::{SearchProvider, TorrentResult};
use async_trait::async_trait;
use playwright::api::{Browser, BrowserContext, Page};
use std::sync::Arc;
use tokio::time::Duration;

/// Un provider de recherche qui scrape des sites de streaming en utilisant Playwright.
pub struct StreamingProvider {
    browser: Arc<Browser>,
}

impl StreamingProvider {
    pub async fn new(browser: Arc<Browser>) -> Self {
        Self { browser }
    }

    /// Scrape une page de streaming pour trouver des liens video.
    async fn scrape_site(
        &self,
        page: &Page,
        search_url: &str,
        search_query: &str,
    ) -> anyhow::Result<Vec<TorrentResult>> {
        tracing::info!("Scraping en cours sur '{}' pour '{}'...", search_url, search_query);

        // Naviguer vers l'URL
        page.goto_builder(search_url).goto().await?;
        tracing::debug!("Navigation vers {} réussie.", search_url);

        // Attendre le chargement de la page
        tokio::time::sleep(Duration::from_secs(5)).await;

        // Extraire les liens video via JavaScript
        let js_result = page.evaluate::<(), Vec<String>>(
            r#"() => {
                const links = [];
                document.querySelectorAll('video source, video, iframe').forEach(el => {
                    const src = el.src || el.getAttribute('src') || el.getAttribute('data-src');
                    if (src && (src.includes('.m3u8') || src.includes('.mp4') || src.includes('embed'))) {
                        links.push(src);
                    }
                });
                return links;
            }"#,
            (),
        ).await;

        let found_urls: Vec<String> = match js_result {
            Ok(urls) => urls,
            Err(e) => {
                tracing::warn!("Échec extraction JS sur {}: {}", search_url, e);
                vec![]
            }
        };

        let results = found_urls
            .into_iter()
            .map(|url| TorrentResult {
                title: format!("{} (Stream)", search_query),
                guid: url.clone(),
                size_bytes: 0,
                indexer: "StreamingScraper".to_string(),
                info_url: Some(search_url.to_string()),
                download_url: Some(url),
                magnet_url: None,
                info_hash: None,
                seeders: None,
                leechers: None,
                protocol: Some("http_stream".to_string()),
                provider_name: self.name().to_string(),
            })
            .collect::<Vec<_>>();

        tracing::info!("Scraping terminé, {} liens trouvés.", results.len());
        Ok(results)
    }
}

#[async_trait]
impl SearchProvider for StreamingProvider {
    fn name(&self) -> &str {
        "StreamingScraper"
    }

    async fn search(&self, query: &str) -> anyhow::Result<Vec<TorrentResult>> {
        let search_url = format!("https://vidsrc.to/embed/movie/{}", query);

        let context: BrowserContext = self.browser.context_builder().build().await?;
        let page: Page = context.new_page().await?;

        let result = self.scrape_site(&page, &search_url, query).await;
        context.close().await?;
        result
    }

    async fn search_by_tmdb_id(&self, tmdb_id: i32, media_type: &str) -> anyhow::Result<Vec<TorrentResult>> {
        let search_url = match media_type {
            "movie" => format!("https://vidsrc.to/embed/movie/{}", tmdb_id),
            "tv" => format!("https://vidsrc.to/embed/tv/{}", tmdb_id),
            _ => return Ok(vec![]),
        };

        let query_display = format!("tmdb:{} type:{}", tmdb_id, media_type);

        let context: BrowserContext = self.browser.context_builder().build().await?;
        let page: Page = context.new_page().await?;

        let result = self.scrape_site(&page, &search_url, &query_display).await;
        context.close().await?;
        result
    }
}
