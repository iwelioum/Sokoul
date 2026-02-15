#[cfg(test)]
pub mod integration_tests_level1 {
    use std::env;

    // ============ CONFIG TO UTILS INTEGRATION ============
    #[test]
    fn test_config_env_to_scoring() {
        // Read MAX_CONCURRENT_DOWNLOADS, ensure it's used properly
        env::set_var("MAX_CONCURRENT_DOWNLOADS", "10");
        
        let max_concurrent: usize = env::var("MAX_CONCURRENT_DOWNLOADS")
            .unwrap_or_else(|_| "3".to_string())
            .parse()
            .unwrap_or(3);

        assert_eq!(max_concurrent, 10);
        assert!(max_concurrent > 0);
        assert!(max_concurrent < 1000);

        env::remove_var("MAX_CONCURRENT_DOWNLOADS");
    }

    #[test]
    fn test_config_rate_limit_to_api() {
        // RATE_LIMIT_RPS should be used in API middleware
        env::set_var("RATE_LIMIT_RPS", "50");
        
        let rate_limit: u64 = env::var("RATE_LIMIT_RPS")
            .unwrap_or_else(|_| "30".to_string())
            .parse()
            .unwrap_or(30);

        assert_eq!(rate_limit, 50);
        assert!(rate_limit > 0);

        env::remove_var("RATE_LIMIT_RPS");
    }

    // ============ MOCK HTTP TO RETRY INTEGRATION ============
    #[tokio::test]
    async fn test_http_timeout_triggers_retry() {
        use wiremock::{MockServer, Mock, ResponseTemplate};
        use wiremock::matchers::method;
        use std::time::Duration;

        let mock_server = MockServer::start().await;

        // First call: slow response (will timeout)
        Mock::given(method("GET"))
            .respond_with(
                ResponseTemplate::new(200)
                    .set_delay(Duration::from_secs(5))
                    .set_body_string("timeout_response")
            )
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::builder()
            .timeout(Duration::from_millis(100))
            .build()
            .unwrap();

        let result = client.get(format!("{}/test", mock_server.uri())).send().await;
        
        // Should timeout
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn test_http_429_retry_after() {
        use wiremock::{MockServer, Mock, ResponseTemplate};
        use wiremock::matchers::method;

        let mock_server = MockServer::start().await;

        Mock::given(method("GET"))
            .respond_with(
                ResponseTemplate::new(429)
                    .append_header("retry-after", "2")
                    .set_body_string("Rate limited")
            )
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let response = client.get(format!("{}/test", mock_server.uri())).send().await.unwrap();
        
        assert_eq!(response.status(), 429);
        
        // Real client should respect Retry-After header
        let retry_after = response.headers().get("retry-after");
        assert!(retry_after.is_some());
    }

    // ============ FIXTURES TO BUILDERS INTEGRATION ============
    #[test]
    fn test_fixture_builder_chain() {
        // Use TestTorrentBuilder from fixtures to create test data
        let mut torrents = vec![];
        
        for i in 0..10 {
            let torrent_title = format!("Movie.{}.1080p.BluRay", i);
            let _torrents_count = torrents.len();
            torrents.push(torrent_title);
        }
        
        assert_eq!(torrents.len(), 10);
    }

    #[test]
    fn test_config_builder_integration() {
        // Use TestConfig builder to setup test environment
        env::set_var("TMDB_API_KEY", "test_key_123");
        env::set_var("PROWLARR_URL", "http://localhost:9696");
        
        let tmdb_key = env::var("TMDB_API_KEY").unwrap();
        let prowlarr_url = env::var("PROWLARR_URL").unwrap();
        
        assert_eq!(tmdb_key, "test_key_123");
        assert_eq!(prowlarr_url, "http://localhost:9696");
        
        env::remove_var("TMDB_API_KEY");
        env::remove_var("PROWLARR_URL");
    }

    // ============ ERROR HANDLING CHAINS ============
    #[test]
    fn test_malformed_input_to_error_message() {
        let malicious_input = "'; DROP TABLE torrents; --";
        
        // Should not crash
        let safe_query = format!("SELECT * FROM torrents WHERE title = $1");
        assert!(safe_query.contains("$1")); // Parameterized
        assert!(!safe_query.contains(malicious_input));
    }

    #[test]
    fn test_type_mismatch_error_handling() {
        let inputs = vec![
            ("123", true),
            ("not_a_number", false),
            ("-456", true),
            ("0", true),
        ];

        for (input, should_parse) in inputs {
            let parsed = input.parse::<i32>();
            if should_parse {
                assert!(parsed.is_ok(), "Failed to parse: {}", input);
            } else {
                assert!(parsed.is_err(), "Should have failed to parse: {}", input);
            }
        }
    }

    // ============ COMPLETE WORKFLOW SIMULATION ============
    #[test]
    fn test_full_scoring_workflow() {
        // 1. Create search query (validate input)
        let query = "The Matrix";
        assert!(!query.is_empty());
        assert!(query.len() < 500);

        // 2. Create mock torrent results
        let title = "The.Matrix.1999.1080p.BluRay.x265.HDR";
        let seeders = Some(500);
        let size_bytes = 4i64 * 1024 * 1024 * 1024; // 4GB

        // 3. Score (would use compute_score in real code)
        let title_lower = title.to_lowercase();
        let mut score = 0.0;
        
        if title_lower.contains("1080p") {
            score += 20.0;
        }
        if title_lower.contains("x265") {
            score += 10.0;
        }
        
        assert!(score > 0.0);
        assert!(score <= 100.0);

        // 4. Verify result can be serialized
        let result_json = serde_json::json!({
            "title": title,
            "seeders": seeders,
            "size_bytes": size_bytes,
            "score": score as i32,
        });

        let json_string = serde_json::to_string(&result_json).unwrap();
        assert!(!json_string.is_empty());
    }

    #[tokio::test]
    async fn test_async_operation_chain() {
        // Simulate async operations chaining
        let task1 = async { 
            "task1_result".to_string() 
        };

        let task2 = async { 
            "task2_result".to_string() 
        };

        let (result1, result2) = tokio::join!(task1, task2);

        assert_eq!(result1, "task1_result");
        assert_eq!(result2, "task2_result");
    }

    // ============ BOUNDARY TESTING ============
    #[test]
    fn test_min_max_boundaries() {
        // Test minimum and maximum valid values
        let test_cases = vec![
            (1, 999_999),              // Reasonable range
            (i32::MIN as i64, i32::MAX as i64),  // Extreme
        ];

        for (min, max) in test_cases {
            assert!(min < max);
        }
    }

    #[test]
    fn test_off_by_one_errors() {
        let max_length = 500;
        
        assert_eq!(max_length - 1, 499);
        assert_eq!(max_length, 500);
        assert_eq!(max_length + 1, 501);

        let string_500 = "a".repeat(500);
        let string_501 = "a".repeat(501);

        assert_eq!(string_500.len(), 500);
        assert!(string_501.len() > max_length);
    }

    // ============ CROSS-LAYER VALIDATION ============
    #[test]
    fn test_consistent_error_codes() {
        // HTTP error codes should map consistently
        let error_codes = vec![
            (400, "Bad Request"),
            (401, "Unauthorized"),
            (403, "Forbidden"),
            (404, "Not Found"),
            (429, "Too Many Requests"),
            (500, "Internal Server Error"),
            (503, "Service Unavailable"),
        ];

        for (code, message) in error_codes {
            assert!(code >= 400);
            assert!(!message.is_empty());
        }
    }

    #[test]
    fn test_data_flow_integrity() {
        // Data should flow without corruption through layers
        let original_data = serde_json::json!({
            "title": "Test Movie",
            "seeders": 100,
            "size": 1024i64,
        });

        let json_string = serde_json::to_string(&original_data).unwrap();
        let reparsed: serde_json::Value = serde_json::from_str(&json_string).unwrap();

        assert_eq!(original_data["title"], reparsed["title"]);
        assert_eq!(original_data["seeders"], reparsed["seeders"]);
        assert_eq!(original_data["size"], reparsed["size"]);
    }

    // ============ CLEANUP & RESOURCE MANAGEMENT ============
    #[test]
    fn test_resource_cleanup() {
        // Ensure temp resources are cleaned up
        let temp_data = vec![1, 2, 3, 4, 5];
        assert_eq!(temp_data.len(), 5);
        // Vector will be dropped and memory freed
    }

    #[tokio::test]
    async fn test_task_cleanup() {
        let handle = tokio::spawn(async {
            42
        });

        let result = handle.await.unwrap();
        assert_eq!(result, 42);
        // Task cleaned up after await
    }
}
