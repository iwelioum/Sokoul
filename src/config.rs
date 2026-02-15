use once_cell::sync::Lazy;
use std::env;

pub struct Config {
    pub database_url: String,
    pub redis_url: String,
    pub nats_url: String,
    pub server_address: String,
    pub tmdb_api_key: String,
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
    pub cors_origins: Vec<String>,
    // Rate limiting
    pub rate_limit_rps: u64,
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            database_url: env::var("DATABASE_URL").expect("DATABASE_URL must be set"),
            redis_url: env::var("REDIS_URL")
                .unwrap_or_else(|_| "redis://127.0.0.1:6379".to_string()),
            nats_url: env::var("NATS_URL").unwrap_or_else(|_| "nats://127.0.0.1:4222".to_string()),
            server_address: env::var("SERVER_ADDRESS")
                .unwrap_or_else(|_| "127.0.0.1:3000".to_string()),
            tmdb_api_key: env::var("TMDB_API_KEY").expect("TMDB_API_KEY must be set"),
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
            cors_origins: env::var("CORS_ORIGINS")
                .unwrap_or_else(|_| "http://localhost:5173,http://localhost:3000".to_string())
                .split(',')
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect(),
            rate_limit_rps: env::var("RATE_LIMIT_RPS")
                .unwrap_or_else(|_| "30".to_string())
                .parse()
                .unwrap_or(30),
        }
    }
}

pub static CONFIG: Lazy<Config> = Lazy::new(Config::from_env);

pub fn init() {
    let _ = &*CONFIG;
}
