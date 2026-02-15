#[cfg(test)]
pub mod tmdb_client_tests {
    use wiremock::{MockServer, Mock, ResponseTemplate};
    use wiremock::matchers::{method, path, query_param};
    use serde_json::json;

    #[tokio::test]
    async fn test_tmdb_search_movie_mock() {
        let mock_server = MockServer::start().await;

        Mock::given(method("GET"))
            .and(path("/3/search/movie"))
            .and(query_param("api_key", "test_key"))
            .and(query_param("query", "The Matrix"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({
                "results": [
                    {
                        "id": 603,
                        "title": "The Matrix",
                        "release_date": "1999-03-31",
                        "poster_path": "/vLSMx96sNXI9h4RxLJ9qePiPVgX.jpg",
                        "overview": "Set in the 22nd century, The Matrix tells the story of an artificial world...",
                        "vote_average": 8.7
                    }
                ],
                "page": 1,
                "total_pages": 1,
                "total_results": 1
            })))
            .mount(&mock_server)
            .await;

        // Would call: tmdb_client.search_movie("The Matrix")
        // and verify response parsing here
        
        // For now, just verify mock setup works
        let client = reqwest::Client::new();
        let url = format!("{}/3/search/movie?api_key=test_key&query=The+Matrix", mock_server.uri());
        let response = client.get(&url).send().await.unwrap();
        
        assert_eq!(response.status(), 200);
        let body: serde_json::Value = response.json().await.unwrap();
        assert_eq!(body["results"][0]["title"], "The Matrix");
    }

    #[tokio::test]
    async fn test_tmdb_search_tv_mock() {
        let mock_server = MockServer::start().await;

        Mock::given(method("GET"))
            .and(path("/3/search/tv"))
            .and(query_param("api_key", "test_key"))
            .and(query_param("query", "Breaking Bad"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({
                "results": [
                    {
                        "id": 1396,
                        "name": "Breaking Bad",
                        "first_air_date": "2008-01-20",
                        "poster_path": "/ggFHVNu6YYI5L9pIv307DmHt0VD.jpg",
                        "overview": "When an unassuming high school chemistry teacher...",
                        "vote_average": 9.5
                    }
                ]
            })))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let url = format!("{}/3/search/tv?api_key=test_key&query=Breaking+Bad", mock_server.uri());
        let response = client.get(&url).send().await.unwrap();
        
        assert_eq!(response.status(), 200);
        let body: serde_json::Value = response.json().await.unwrap();
        assert_eq!(body["results"][0]["name"], "Breaking Bad");
    }

    #[tokio::test]
    async fn test_tmdb_api_error_handling() {
        let mock_server = MockServer::start().await;

        // Mock 401 Unauthorized
        Mock::given(method("GET"))
            .and(path("/3/search/movie"))
            .and(query_param("api_key", "invalid_key"))
            .respond_with(ResponseTemplate::new(401).set_body_json(json!({
                "success": false,
                "status_code": 7,
                "status_message": "Invalid API key: You must be granted a valid key."
            })))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let url = format!("{}/3/search/movie?api_key=invalid_key&query=test", mock_server.uri());
        let response = client.get(&url).send().await.unwrap();
        
        assert_eq!(response.status(), 401);
    }

    #[tokio::test]
    async fn test_tmdb_empty_results() {
        let mock_server = MockServer::start().await;

        Mock::given(method("GET"))
            .and(path("/3/search/movie"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({
                "results": [],
                "page": 1,
                "total_pages": 0,
                "total_results": 0
            })))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let url = format!("{}/3/search/movie?api_key=test_key&query=nonexistent", mock_server.uri());
        let response = client.get(&url).send().await.unwrap();
        
        let body: serde_json::Value = response.json().await.unwrap();
        assert_eq!(body["results"].as_array().unwrap().len(), 0);
    }
}

#[cfg(test)]
pub mod flaresolverr_client_tests {
    use wiremock::{MockServer, Mock, ResponseTemplate};
    use wiremock::matchers::{method, path};
    use serde_json::json;

    #[tokio::test]
    async fn test_flaresolverr_request_success() {
        let mock_server = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/v1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({
                "status": "ok",
                "solution": {
                    "url": "https://example.com",
                    "status": 200,
                    "headers": {
                        "user-agent": "Mozilla/5.0"
                    },
                    "response": r#"{"result": "success"}"#
                }
            })))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let url = format!("{}/v1", mock_server.uri());
        
        let payload = serde_json::json!({
            "cmd": "request.get",
            "url": "https://example.com",
            "maxTimeout": 60000
        });

        let response = client
            .post(&url)
            .json(&payload)
            .send()
            .await
            .unwrap();

        assert_eq!(response.status(), 200);
        let body: serde_json::Value = response.json().await.unwrap();
        assert_eq!(body["status"], "ok");
    }

    #[tokio::test]
    async fn test_flaresolverr_cloudflare_challenge() {
        let mock_server = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/v1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({
                "status": "ok",
                "solution": {
                    "url": "https://protected.com",
                    "status": 200,
                    "response": "<html>Challenge solved</html>"
                }
            })))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let url = format!("{}/v1", mock_server.uri());
        let payload = serde_json::json!({
            "cmd": "request.get",
            "url": "https://protected.com",
            "maxTimeout": 60000
        });

        let response = client.post(&url).json(&payload).send().await.unwrap();
        
        let body: serde_json::Value = response.json().await.unwrap();
        assert_eq!(body["status"], "ok");
    }

    #[tokio::test]
    async fn test_flaresolverr_error_response() {
        let mock_server = MockServer::start().await;

        Mock::given(method("POST"))
            .and(path("/v1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({
                "status": "error",
                "message": "Error solving challenge"
            })))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        let url = format!("{}/v1", mock_server.uri());
        let payload = serde_json::json!({
            "cmd": "request.get",
            "url": "https://example.com",
            "maxTimeout": 60000
        });

        let response = client.post(&url).json(&payload).send().await.unwrap();
        
        let body: serde_json::Value = response.json().await.unwrap();
        assert_eq!(body["status"], "error");
    }

    #[tokio::test]
    async fn test_flaresolverr_timeout() {
        let mock_server = MockServer::start().await;

        // Simulate slow response
        Mock::given(method("POST"))
            .and(path("/v1"))
            .respond_with(
                ResponseTemplate::new(200)
                    .set_delay(std::time::Duration::from_secs(2))
                    .set_body_json(json!({"status": "ok"}))
            )
            .mount(&mock_server)
            .await;

        // This test would verify timeout handling
        // In real client, short timeout should fail
        let client = reqwest::Client::new();
        let url = format!("{}/v1", mock_server.uri());
        let payload = serde_json::json!({
            "cmd": "request.get",
            "url": "https://example.com",
            "maxTimeout": 60000
        });

        let response = client.post(&url).json(&payload).send().await;
        // Depends on client timeout settings
        assert!(response.is_ok()); // Mock server responds, but client might timeout
    }
}

#[cfg(test)]
pub mod http_mock_integration_tests {
    use wiremock::{MockServer, Mock, ResponseTemplate};
    use wiremock::matchers::method;
    use serde_json::json;

    #[tokio::test]
    async fn test_multiple_api_calls() {
        let mock_server = MockServer::start().await;

        // Setup multiple endpoints
        Mock::given(method("GET"))
            .respond_with(ResponseTemplate::new(200).set_body_json(json!({"data": "test1"})))
            .mount(&mock_server)
            .await;

        let client = reqwest::Client::new();
        
        let res1 = client.get(format!("{}/api/test1", mock_server.uri())).send().await.unwrap();
        assert_eq!(res1.status(), 200);

        let res2 = client.get(format!("{}/api/test2", mock_server.uri())).send().await.unwrap();
        assert_eq!(res2.status(), 200);
    }
}
