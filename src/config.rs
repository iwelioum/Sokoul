use once_cell::sync::Lazy;
use std::env;

pub struct Config {
    #[allow(dead_code)]
    pub port: u16,
    #[allow(dead_code)]
    pub node_env: String,
    #[allow(dead_code)]
    pub base_url: String,
    pub database_url: String,
    pub redis_url: String,
    pub nats_url: String,
    pub server_address: String,
    pub tmdb_api_key: String,
    #[allow(dead_code)]
    pub tmdb_language: String,
    #[allow(dead_code)]
    pub tmdb_image_base_url: String,
    #[allow(dead_code)]
    pub thetvdb_api_key: String,
    #[allow(dead_code)]
    pub thetvdb_pin: String,
    #[allow(dead_code)]
    pub omdb_api_key: String,
    #[allow(dead_code)]
    pub fanart_api_key: String,
    #[allow(dead_code)]
    pub fanart_client_key: String,
    // Trakt
    #[allow(dead_code)]
    pub trakt_client_id: String,
    // TasteDive
    #[allow(dead_code)]
    pub tastedive_api_key: String,
    // Watchmode
    #[allow(dead_code)]
    pub watchmode_api_key: String,
    // Simkl
    #[allow(dead_code)]
    pub simkl_api_key: String,
    // uNoGS (Netflix)
    #[allow(dead_code)]
    pub unogs_api_key: String,
    // IMDbOT & Stream (no auth)
    #[allow(dead_code)]
    pub imdbbot_base_url: String,
    #[allow(dead_code)]
    pub stream_base_url: String,
    pub prowlarr_url: String,
    pub prowlarr_api_key: String,
    // FlareSolverr
    pub flaresolverr_url: String,
    // Jackett (alternative to Prowlarr)
    pub jackett_url: String,
    pub jackett_api_key: String,
    // Real-Debrid (used by Hunter worker when integrated)
    #[allow(dead_code)]
    pub realdebrid_api_token: String,
    // Telegram
    pub telegram_enabled: bool,
    // Oracle (llama.cpp)
    pub oracle_url: String,
    pub oracle_enabled: bool,
    // Downloads
    pub download_dir: String,
    pub max_concurrent_downloads: usize,
    // Streaming (Playwright)
    pub streaming_enabled: bool,
    pub streaming_headless: bool,
    // Security
    pub api_key: String,
    pub jwt_secret: String,
    pub cors_origins: Vec<String>,
    // Security - Malware Detection
    #[allow(dead_code)]
    pub virustotal_api_key: String,
    #[allow(dead_code)]
    pub security_check_enabled: bool,
    #[allow(dead_code)]
    pub auto_block_critical: bool,
    // Rate limiting
    pub rate_limit_rps: u64,
    // Consumet API (self-hosted stream resolver — docker service)
    pub consumet_url: String,
}

impl Config {
    pub fn from_env() -> Self {
        let port = env::var("PORT")
            .ok()
            .and_then(|p| p.parse::<u16>().ok())
            .unwrap_or(3000);

        Self {
            port,
            node_env: env::var("NODE_ENV").unwrap_or_else(|_| "development".to_string()),
            base_url: env::var("BASE_URL").unwrap_or_else(|_| format!("http://localhost:{port}")),
            database_url: env::var("DATABASE_URL").expect("DATABASE_URL must be set"),
            redis_url: env::var("REDIS_URL")
                .unwrap_or_else(|_| "redis://127.0.0.1:6379".to_string()),
            nats_url: env::var("NATS_URL").unwrap_or_else(|_| "nats://127.0.0.1:4222".to_string()),
            server_address: env::var("SERVER_ADDRESS")
                .unwrap_or_else(|_| format!("127.0.0.1:{port}")),
            tmdb_api_key: env::var("TMDB_API_KEY").expect("TMDB_API_KEY must be set"),
            tmdb_language: env::var("TMDB_LANGUAGE").unwrap_or_else(|_| "fr-FR".to_string()),
            tmdb_image_base_url: env::var("TMDB_IMAGE_BASE_URL")
                .unwrap_or_else(|_| "https://image.tmdb.org/t/p/original".to_string()),
            thetvdb_api_key: env::var("THETVDB_API_KEY").unwrap_or_default(),
            thetvdb_pin: env::var("THETVDB_PIN").unwrap_or_default(),
            omdb_api_key: env::var("OMDB_API_KEY").unwrap_or_default(),
            fanart_api_key: env::var("FANART_API_KEY").unwrap_or_default(),
            fanart_client_key: env::var("FANART_CLIENT_KEY").unwrap_or_default(),
            trakt_client_id: env::var("TRAKT_CLIENT_ID").unwrap_or_default(),
            tastedive_api_key: env::var("TASTEDIVE_API_KEY").unwrap_or_default(),
            watchmode_api_key: env::var("WATCHMODE_API_KEY").unwrap_or_default(),
            simkl_api_key: env::var("SIMKL_API_KEY").unwrap_or_default(),
            unogs_api_key: env::var("UNOGS_API_KEY").unwrap_or_default(),
            imdbbot_base_url: env::var("IMDBBOT_BASE_URL")
                .unwrap_or_else(|_| "https://imdb-api.com".to_string()),
            stream_base_url: env::var("STREAM_BASE_URL")
                .unwrap_or_else(|_| "https://www.stream.cz/api".to_string()),
            prowlarr_url: env::var("PROWLARR_URL").unwrap_or_default(),
            prowlarr_api_key: env::var("PROWLARR_API_KEY").unwrap_or_default(),
            flaresolverr_url: env::var("FLARESOLVERR_URL").unwrap_or_default(),
            jackett_url: env::var("JACKETT_URL").unwrap_or_default(),
            jackett_api_key: env::var("JACKETT_API_KEY").unwrap_or_default(),
            realdebrid_api_token: env::var("REALDEBRID_API_TOKEN").unwrap_or_default(),
            telegram_enabled: env::var("TELEGRAM_ENABLED").unwrap_or_else(|_| "false".to_string())
                == "true",
            oracle_url: env::var("ORACLE_URL")
                .unwrap_or_else(|_| "http://127.0.0.1:8080".to_string()),
            oracle_enabled: env::var("ORACLE_ENABLED").unwrap_or_else(|_| "false".to_string())
                == "true",
            download_dir: env::var("DOWNLOAD_DIR").unwrap_or_else(|_| "./downloads".to_string()),
            max_concurrent_downloads: env::var("MAX_CONCURRENT_DOWNLOADS")
                .unwrap_or_else(|_| "3".to_string())
                .parse()
                .unwrap_or(3),
            streaming_enabled: env::var("STREAMING_ENABLED")
                .unwrap_or_else(|_| "false".to_string())
                == "true",
            streaming_headless: env::var("STREAMING_HEADLESS")
                .unwrap_or_else(|_| "true".to_string())
                == "true",
            api_key: env::var("SOKOUL_API_KEY").unwrap_or_default(),
            jwt_secret: env::var("JWT_SECRET")
                .unwrap_or_else(|_| "sokoul_default_secret_change_me".to_string()),
            cors_origins: env::var("CORS_ORIGINS")
                .unwrap_or_else(|_| "http://localhost:5173,http://localhost:3000".to_string())
                .split(',')
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect(),
            virustotal_api_key: env::var("VIRUSTOTAL_API_KEY").unwrap_or_default(),
            security_check_enabled: env::var("SECURITY_CHECK_ENABLED")
                .unwrap_or_else(|_| "true".to_string())
                == "true",
            auto_block_critical: env::var("AUTO_BLOCK_CRITICAL")
                .unwrap_or_else(|_| "true".to_string())
                == "true",
            rate_limit_rps: env::var("RATE_LIMIT_RPS")
                .unwrap_or_else(|_| "30".to_string())
                .parse()
                .unwrap_or(30),
            consumet_url: env::var("CONSUMET_URL").unwrap_or_default(),
        }
    }
}

pub static CONFIG: Lazy<Config> = Lazy::new(Config::from_env);

pub fn init() {
    let _ = &*CONFIG;
}
