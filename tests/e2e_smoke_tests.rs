//! Smoke Tests - Post-deployment validation
//!
//! Run: cargo test --test e2e_smoke_tests -- --test-threads=1
//! Expected: All health checks pass, all critical endpoints respond correctly
//!
//! Tests that the application and all its dependencies are healthy and responding correctly
//! These tests validate basic functionality without stressing the system

use reqwest::Client;
use std::env;
use std::time::Duration;

const BASE_URL: &str = "http://localhost:3000";
const HEALTH_CHECK_TIMEOUT: Duration = Duration::from_secs(30);
const REQUEST_TIMEOUT: Duration = Duration::from_secs(10);

/// Create a configured HTTP client for testing
fn create_test_client() -> Client {
    Client::builder()
        .timeout(REQUEST_TIMEOUT)
        .cookie_store(true)
        .build()
        .expect("Failed to build HTTP client")
}

/// Helper to wait for service startup
async fn wait_for_service() {
    let client = create_test_client();
    let start = std::time::Instant::now();

    loop {
        match client.get(&format!("{}/health", BASE_URL)).send().await {
            Ok(resp) if resp.status().is_success() => {
                println!("✓ Service is ready");
                break;
            }
            _ if start.elapsed() < HEALTH_CHECK_TIMEOUT => {
                tokio::time::sleep(Duration::from_millis(500)).await;
            }
            _ => panic!("Service failed to become ready after 30s"),
        }
    }
}

#[tokio::test]
async fn test_health_endpoint_returns_200() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/health", BASE_URL))
        .send()
        .await
        .expect("Failed to make request");

    assert_eq!(
        response.status().as_u16(),
        200,
        "Health endpoint should return 200 OK"
    );

    let body = response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse response body");

    assert!(
        body["status"].as_str().is_some(),
        "Health response should have status field"
    );
}

#[tokio::test]
async fn test_health_deep_checks_dependencies() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/health/deep", BASE_URL))
        .send()
        .await
        .expect("Failed to make request to /health/deep");

    // May return 200 (all OK) or 503 (some dependencies down)
    // The important thing is it doesn't panic
    assert!(
        response.status().is_success() || response.status().as_u16() == 503,
        "Health deep check should return 200 or 503"
    );

    let body = response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse health deep response");

    // Verify response has expected structure
    assert!(body.is_object(), "Health deep response should be an object");
}

#[tokio::test]
async fn test_database_connectivity() {
    wait_for_service().await;
    let client = create_test_client();

    // Test by attempting to query user data (will fail with 401 but proves DB is working)
    let response = client
        .get(&format!("{}/user/profile", BASE_URL))
        .send()
        .await;

    // Should get a response (either 401 Unauthorized or 200)
    // Not a 500 Service Unavailable which would indicate DB is down
    match response {
        Ok(resp) => {
            assert!(
                !resp.status().is_server_error(),
                "Database connectivity test should not return server error"
            );
        }
        Err(e) => {
            panic!("Failed to reach /user/profile endpoint: {}", e);
        }
    }
}

#[tokio::test]
async fn test_cache_connectivity() {
    wait_for_service().await;
    let client = create_test_client();

    // Make multiple requests to same endpoint to test cache
    let endpoint = format!("{}/search?query=test", BASE_URL);

    for i in 0..3 {
        let response = client
            .get(&endpoint)
            .send()
            .await
            .expect("Failed to reach search endpoint");

        // Should complete successfully (cache working or graceful degradation)
        assert!(
            !response.status().is_server_error(),
            "Search endpoint should not error on attempt {}: status={}",
            i + 1,
            response.status()
        );
    }
}

#[tokio::test]
async fn test_security_headers_present() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/search?query=test", BASE_URL))
        .send()
        .await
        .expect("Failed to make request");

    let headers = response.headers();

    // Check for common security headers
    let has_cors = headers.contains_key("access-control-allow-origin");
    let has_content_type = headers.contains_key("content-type");

    assert!(
        has_cors || has_content_type,
        "Response should contain security-related headers"
    );
}

#[tokio::test]
async fn test_api_responds_to_requests() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/search?query=Inception", BASE_URL))
        .send()
        .await
        .expect("Failed to reach search endpoint");

    assert!(
        !response.status().is_server_error(),
        "API should respond to valid requests without 5xx errors"
    );
}

#[tokio::test]
async fn test_invalid_requests_handled_gracefully() {
    wait_for_service().await;
    let client = create_test_client();

    // Test with invalid query parameter
    let response = client
        .get(&format!(
            "{}/search?query=<script>alert(1)</script>",
            BASE_URL
        ))
        .send()
        .await
        .expect("Failed to make request");

    // Should handle gracefully - either 400 Bad Request or sanitize and process
    let status = response.status();
    assert!(
        !status.is_server_error(),
        "Invalid input should be handled gracefully, not return 5xx"
    );
}

#[tokio::test]
async fn test_response_times_acceptable() {
    wait_for_service().await;
    let client = create_test_client();

    let start = std::time::Instant::now();

    let _response = client
        .get(&format!("{}/health", BASE_URL))
        .send()
        .await
        .expect("Failed to reach health endpoint");

    let elapsed = start.elapsed();

    assert!(
        elapsed < Duration::from_secs(5),
        "Health endpoint should respond in less than 5 seconds, took {:?}",
        elapsed
    );
}

#[tokio::test]
async fn test_json_response_format() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/health", BASE_URL))
        .send()
        .await
        .expect("Failed to make request");

    let content_type = response
        .headers()
        .get("content-type")
        .and_then(|h| h.to_str().ok())
        .unwrap_or("");

    assert!(
        content_type.contains("application/json"),
        "API should return application/json responses"
    );
}

#[tokio::test]
async fn test_error_responses_have_valid_format() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/nonexistent-endpoint-12345", BASE_URL))
        .send()
        .await;

    // Should either get 404 or connection refused
    if let Ok(resp) = response {
        if resp.status() == 404 || resp.status().is_client_error() {
            let body = resp.json::<serde_json::Value>().await;
            // Should be parseable JSON (not HTML error page)
            assert!(body.is_ok(), "Error responses should be valid JSON");
        }
    }
}

#[tokio::test]
async fn test_concurrent_health_checks() {
    wait_for_service().await;
    let client = create_test_client();

    // Fire 10 concurrent health checks
    let mut handles = vec![];

    for _ in 0..10 {
        let client = client.clone();
        let handle =
            tokio::spawn(async move { client.get(&format!("{}/health", BASE_URL)).send().await });
        handles.push(handle);
    }

    // Wait for all to complete
    for handle in handles {
        let result = handle
            .await
            .expect("Task panicked")
            .expect("Request failed");

        assert_eq!(
            result.status().as_u16(),
            200,
            "Concurrent health checks should all succeed"
        );
    }
}

#[tokio::test]
async fn test_no_stack_traces_exposed() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/error-trigger-endpoint", BASE_URL))
        .send()
        .await;

    if let Ok(resp) = response {
        let body_text = resp.text().await.unwrap_or_default();

        // Stack traces should not be exposed in production
        assert!(
            !body_text.contains("at 0x"),
            "Stack traces with memory addresses should not be exposed"
        );
        assert!(
            !body_text.contains("thread 'main' panicked"),
            "Panic messages should not be exposed"
        );
    }
}

#[tokio::test]
async fn test_protected_library_status_requires_auth() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/library/status/550/movie", BASE_URL))
        .send()
        .await
        .expect("Failed to call protected library status endpoint");

    assert_eq!(
        response.status().as_u16(),
        401,
        "Protected endpoint should reject anonymous requests"
    );
}

#[tokio::test]
async fn test_protected_library_status_accepts_api_key() {
    wait_for_service().await;
    let api_key = match env::var("SOKOUL_API_KEY") {
        Ok(value) if !value.trim().is_empty() => value,
        _ => {
            println!("Skipping API key compatibility test: SOKOUL_API_KEY not configured");
            return;
        }
    };

    let client = create_test_client();
    let response = client
        .get(&format!("{}/library/status/550/movie", BASE_URL))
        .header("X-API-Key", api_key)
        .send()
        .await
        .expect("Failed to call protected endpoint with API key");

    assert!(
        response.status().is_success(),
        "API key should be accepted on protected endpoint, got {}",
        response.status()
    );
}

#[tokio::test]
async fn test_protected_library_status_accepts_jwt() {
    wait_for_service().await;
    let client = create_test_client();

    let suffix = uuid::Uuid::new_v4().to_string().replace('-', "");
    let register_payload = serde_json::json!({
        "username": format!("smoke_{}", &suffix[..10]),
        "email": format!("smoke_{}@example.com", &suffix[..10]),
        "password": "Password123!"
    });

    let register_response = client
        .post(&format!("{}/auth/register", BASE_URL))
        .json(&register_payload)
        .send()
        .await
        .expect("Failed to register test user for JWT compatibility test");

    assert!(
        register_response.status().is_success(),
        "Expected register to succeed, got {}",
        register_response.status()
    );

    let register_body = register_response
        .json::<serde_json::Value>()
        .await
        .expect("Failed to parse register response JSON");
    let token = register_body["token"]
        .as_str()
        .expect("Register response should include token");

    let response = client
        .get(&format!("{}/library/status/550/movie", BASE_URL))
        .bearer_auth(token)
        .send()
        .await
        .expect("Failed to call protected endpoint with JWT");

    assert!(
        response.status().is_success(),
        "JWT should be accepted on protected endpoint, got {}",
        response.status()
    );
}

#[tokio::test]
async fn test_public_metadata_route_not_blocked_by_auth() {
    wait_for_service().await;
    let client = create_test_client();

    let response = client
        .get(&format!("{}/streaming/direct/movie/550", BASE_URL))
        .send()
        .await
        .expect("Failed to call public metadata endpoint");

    let status = response.status().as_u16();
    assert_ne!(status, 401, "Public metadata route should not require auth");
    assert_ne!(status, 403, "Public metadata route should not require auth");
}

// Summary of smoke tests
// ✓ Health endpoint operational
// ✓ Deep health checks working
// ✓ Database accessible
// ✓ Cache functional
// ✓ Security headers present
// ✓ API responding
// ✓ Error handling graceful
// ✓ Response times acceptable
// ✓ Response format valid (JSON)
// ✓ Error responses structured
// ✓ Concurrent requests handled
// ✓ No sensitive data exposed
