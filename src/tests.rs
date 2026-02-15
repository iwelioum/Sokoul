#[cfg(test)]
pub mod fixtures {
    use uuid::Uuid;

    /// Builder pour configurations de test
    #[derive(Clone, Debug)]
    pub struct TestConfig {
        pub tmdb_api_key: String,
        pub prowlarr_url: String,
        pub prowlarr_api_key: String,
        pub jackett_url: String,
        pub jackett_api_key: String,
        pub flaresolverr_url: String,
    }

    impl Default for TestConfig {
        fn default() -> Self {
            Self {
                tmdb_api_key: "test_tmdb_key_12345".to_string(),
                prowlarr_url: "http://localhost:9696".to_string(),
                prowlarr_api_key: "test_prowlarr_key".to_string(),
                jackett_url: "http://localhost:9117".to_string(),
                jackett_api_key: "test_jackett_key".to_string(),
                flaresolverr_url: "http://localhost:8191".to_string(),
            }
        }
    }

    impl TestConfig {
        pub fn new() -> Self {
            Self::default()
        }

        pub fn with_tmdb_key(mut self, key: impl Into<String>) -> Self {
            self.tmdb_api_key = key.into();
            self
        }

        pub fn with_prowlarr(mut self, url: impl Into<String>, api_key: impl Into<String>) -> Self {
            self.prowlarr_url = url.into();
            self.prowlarr_api_key = api_key.into();
            self
        }
    }

    /// Builder pour TorrentResult de test
    #[derive(Clone, Debug)]
    pub struct TestTorrentBuilder {
        title: String,
        guid: String,
        size_bytes: i64,
        seeders: Option<i32>,
        leechers: Option<i32>,
        provider_name: String,
    }

    impl Default for TestTorrentBuilder {
        fn default() -> Self {
            Self {
                title: "Test Torrent".to_string(),
                guid: Uuid::new_v4().to_string(),
                size_bytes: 1024 * 1024 * 1024, // 1GB
                seeders: Some(100),
                leechers: Some(50),
                provider_name: "TestProvider".to_string(),
            }
        }
    }

    impl TestTorrentBuilder {
        pub fn new() -> Self {
            Self::default()
        }

        pub fn with_title(mut self, title: impl Into<String>) -> Self {
            self.title = title.into();
            self
        }

        pub fn with_seeders(mut self, count: i32) -> Self {
            self.seeders = Some(count);
            self
        }

        pub fn with_leechers(mut self, count: i32) -> Self {
            self.leechers = Some(count);
            self
        }

        pub fn with_size(mut self, bytes: i64) -> Self {
            self.size_bytes = bytes;
            self
        }

        pub fn with_provider(mut self, provider: impl Into<String>) -> Self {
            self.provider_name = provider.into();
            self
        }
    }

    #[cfg(test)]
    mod tests {
        use super::*;

        #[test]
        fn test_config_builder() {
            let config = TestConfig::new()
                .with_tmdb_key("custom_key")
                .with_prowlarr("http://custom:9696", "custom_api_key");

            assert_eq!(config.tmdb_api_key, "custom_key");
            assert_eq!(config.prowlarr_url, "http://custom:9696");
            assert_eq!(config.prowlarr_api_key, "custom_api_key");
        }
    }
}
