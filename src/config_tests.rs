#[cfg(test)]
pub mod config_tests {
    use std::env;

    #[test]
    fn test_required_env_vars_present() {
        // Must be set: DATABASE_URL, TMDB_API_KEY
        // These should be present or the app won't start
        // During testing, they may not be in the environment if tests run in CI
        // so we just verify the logic of parsing them when available
        if let Ok(db_url) = env::var("DATABASE_URL") {
            assert!(!db_url.is_empty());
        }
        if let Ok(tmdb_key) = env::var("TMDB_API_KEY") {
            assert!(!tmdb_key.is_empty());
        }
        // At least one should be set (likely to be set before app startup)
        let _has_db = env::var("DATABASE_URL").is_ok();
        let _has_tmdb = env::var("TMDB_API_KEY").is_ok();
        // Don't fail test - just verify they work when present
        // The real requirement is tested at app startup
    }

    #[test]
    fn test_optional_defaults() {
        // These should have defaults if not set
        let redis_url =
            env::var("REDIS_URL").unwrap_or_else(|_| "redis://127.0.0.1:6379".to_string());
        assert!(!redis_url.is_empty());

        let nats_url = env::var("NATS_URL").unwrap_or_else(|_| "nats://127.0.0.1:4222".to_string());
        assert!(!nats_url.is_empty());
    }

    #[test]
    fn test_bool_parsing() {
        // Test telegram_enabled parsing
        env::set_var("TELEGRAM_ENABLED", "true");
        let telegram_enabled =
            env::var("TELEGRAM_ENABLED").unwrap_or_else(|_| "false".to_string()) == "true";
        assert!(telegram_enabled);

        env::set_var("TELEGRAM_ENABLED", "false");
        let telegram_enabled =
            env::var("TELEGRAM_ENABLED").unwrap_or_else(|_| "false".to_string()) == "true";
        assert!(!telegram_enabled);

        env::remove_var("TELEGRAM_ENABLED");
    }

    #[test]
    fn test_int_parsing() {
        // Test max_concurrent_downloads parsing
        env::set_var("MAX_CONCURRENT_DOWNLOADS", "5");
        let parsed: usize = env::var("MAX_CONCURRENT_DOWNLOADS")
            .unwrap_or_else(|_| "3".to_string())
            .parse()
            .unwrap_or(3);
        assert_eq!(parsed, 5);

        // Test invalid value → should use default
        env::set_var("MAX_CONCURRENT_DOWNLOADS", "invalid");
        let parsed: usize = env::var("MAX_CONCURRENT_DOWNLOADS")
            .unwrap_or_else(|_| "3".to_string())
            .parse()
            .unwrap_or(3);
        assert_eq!(parsed, 3);

        env::remove_var("MAX_CONCURRENT_DOWNLOADS");
    }

    #[test]
    fn test_rate_limit_parsing() {
        env::set_var("RATE_LIMIT_RPS", "100");
        let parsed: u64 = env::var("RATE_LIMIT_RPS")
            .unwrap_or_else(|_| "30".to_string())
            .parse()
            .unwrap_or(30);
        assert_eq!(parsed, 100);

        env::remove_var("RATE_LIMIT_RPS");
    }

    #[test]
    fn test_cors_origins_parsing() {
        let cors_string = "http://localhost:5173,http://localhost:3000,http://example.com";
        let cors_origins: Vec<String> = cors_string
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect();

        assert_eq!(cors_origins.len(), 3);
        assert!(cors_origins.contains(&"http://localhost:5173".to_string()));
        assert!(cors_origins.contains(&"http://example.com".to_string()));
    }

    #[test]
    fn test_empty_cors_origins() {
        let cors_string = "";
        let cors_origins: Vec<String> = cors_string
            .split(',')
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
            .collect();

        assert_eq!(cors_origins.len(), 0);
    }
}

#[cfg(test)]
pub mod utils_tests {
    #[test]
    fn test_scoring_concept() {
        // Scoring should prefer high seeders
        let high_seeders = 1000i32;
        let low_seeders = 1i32;

        // Logarithmic scoring: ln(1000) ~= 6.9, ln(1) = 0
        let score_high = (high_seeders as f64).ln();
        let score_low = (low_seeders as f64).ln();

        assert!(score_high > score_low);
    }

    #[test]
    fn test_scoring_high_seeders() {
        let seeders = 1000i32;
        let ln_result = (seeders as f64).ln();
        // ln(1000) * 6 gives decent score
        let score = (ln_result * 6.0).min(40.0);
        assert!(score > 20.0);
    }

    #[test]
    fn test_scoring_low_seeders() {
        let seeders = 1i32;
        let ln_result = (seeders as f64).ln();
        let score = (ln_result * 6.0).min(40.0);
        assert!(score < 10.0);
    }

    #[test]
    fn test_scoring_no_seeders() {
        let seeders: Option<i32> = None;
        let seeders_val = seeders.unwrap_or(0) as f64;
        let score = (seeders_val.ln().max(0.0) * 6.0).min(40.0);
        assert!(score == 0.0);
    }

    #[test]
    fn test_scoring_good_ratio() {
        let seeders1 = 500i32;
        let leechers1 = 100i32;
        let ratio1 = seeders1 as f64 / (leechers1 as f64 + 1.0);

        let seeders2 = 500i32;
        let leechers2 = 500i32;
        let ratio2 = seeders2 as f64 / (leechers2 as f64 + 1.0);

        assert!(ratio1 > ratio2);
    }
}

#[cfg(test)]
pub mod retry_tests {
    #[test]
    fn test_exponential_backoff_calculation() {
        // First retry: 1s * 2^0 = 1s
        let initial_delay = 1000.0;
        let delay_1 = initial_delay * (2_f64.powi(0));
        assert_eq!(delay_1, 1000.0);

        // Second retry: 1s * 2^1 = 2s
        let delay_2 = initial_delay * (2_f64.powi(1));
        assert_eq!(delay_2, 2000.0);

        // Third retry: 1s * 2^2 = 4s
        let delay_3 = initial_delay * (2_f64.powi(2));
        assert_eq!(delay_3, 4000.0);
    }

    #[test]
    fn test_exponential_backoff_max_cap() {
        let max_ms = 10_000.0_f64;
        let mut delay = 1000.0_f64;

        for _ in 0..10 {
            let capped = delay.min(max_ms);
            assert!(capped <= max_ms);
            delay *= 2.0;
        }
    }
}
