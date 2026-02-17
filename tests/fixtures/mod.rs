//! Test Fixtures - Shared test data and helpers
//!
//! Provides:
//! - Mock API responses
//! - Test user creation
//! - Sample media data
//! - Helper functions

use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TestUser {
    pub id: String,
    pub username: String,
    pub email: String,
    pub password: String,
}

impl TestUser {
    /// Create a new test user with random data
    pub fn new() -> Self {
        let uuid = Uuid::new_v4().to_string();
        TestUser {
            id: uuid.clone(),
            username: format!("test_user_{}", &uuid[..8]),
            email: format!("test_{}@sokoul.local", &uuid[..8]),
            password: "SecurePassword123!".to_string(),
        }
    }

    /// Create a test user with specific username
    pub fn with_username(username: &str) -> Self {
        let uuid = Uuid::new_v4().to_string();
        TestUser {
            id: uuid,
            username: username.to_string(),
            email: format!("{}@sokoul.local", username),
            password: "SecurePassword123!".to_string(),
        }
    }
}

impl Default for TestUser {
    fn default() -> Self {
        Self::new()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TestMedia {
    pub id: u32,
    pub title: String,
    pub tmdb_id: u32,
    pub media_type: MediaType,
    pub description: String,
    pub genres: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MediaType {
    Movie,
    Series,
}

impl TestMedia {
    /// Create a test movie
    pub fn movie(title: &str) -> Self {
        TestMedia {
            id: rand::random(),
            title: title.to_string(),
            tmdb_id: rand::random(),
            media_type: MediaType::Movie,
            description: format!("A test movie: {}", title),
            genres: vec!["Action".to_string(), "Drama".to_string()],
        }
    }

    /// Create a test series
    pub fn series(title: &str) -> Self {
        TestMedia {
            id: rand::random(),
            title: title.to_string(),
            tmdb_id: rand::random(),
            media_type: MediaType::Series,
            description: format!("A test series: {}", title),
            genres: vec!["Drama".to_string(), "Thriller".to_string()],
        }
    }

    /// Get sample movies for testing
    pub fn sample_movies() -> Vec<Self> {
        vec![
            TestMedia::movie("Inception"),
            TestMedia::movie("The Matrix"),
            TestMedia::movie("Interstellar"),
        ]
    }

    /// Get sample series for testing
    pub fn sample_series() -> Vec<Self> {
        vec![
            TestMedia::series("Breaking Bad"),
            TestMedia::series("Game of Thrones"),
            TestMedia::series("The Crown"),
        ]
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MockSearchResponse {
    pub results: Vec<MockSearchResult>,
    pub total_results: u32,
    pub total_pages: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MockSearchResult {
    pub id: u32,
    pub title: String,
    pub description: String,
    pub release_date: String,
}

impl MockSearchResponse {
    /// Create a mock response for a search query
    pub fn for_query(query: &str) -> Self {
        let results = match query.to_lowercase().as_str() {
            "inception" => vec![MockSearchResult {
                id: 27205,
                title: "Inception".to_string(),
                description: "A thief who steals corporate secrets through the use of dream-sharing technology".to_string(),
                release_date: "2010-07-16".to_string(),
            }],
            "matrix" => vec![MockSearchResult {
                id: 603,
                title: "The Matrix".to_string(),
                description: "A computer hacker learns from mysterious rebels about the true nature of his reality".to_string(),
                release_date: "1999-03-31".to_string(),
            }],
            _ => vec![
                MockSearchResult {
                    id: 1,
                    title: format!("Result for {}", query),
                    description: "Generic test result".to_string(),
                    release_date: "2024-01-01".to_string(),
                }
            ],
        };

        let total_results = results.len() as u32;
        MockSearchResponse {
            results,
            total_results,
            total_pages: 1,
        }
    }

    /// Empty search response
    pub fn empty() -> Self {
        MockSearchResponse {
            results: vec![],
            total_results: 0,
            total_pages: 0,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MockDownloadJob {
    pub job_id: String,
    pub user_id: String,
    pub media_id: u32,
    pub status: DownloadStatus,
    pub progress: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum DownloadStatus {
    Pending,
    Running,
    Completed,
    Failed,
}

impl MockDownloadJob {
    /// Create a new mock download job
    pub fn new(user_id: &str, media_id: u32) -> Self {
        MockDownloadJob {
            job_id: Uuid::new_v4().to_string(),
            user_id: user_id.to_string(),
            media_id,
            status: DownloadStatus::Pending,
            progress: 0.0,
        }
    }

    /// Simulate job progress
    pub fn with_progress(mut self, progress: f64) -> Self {
        self.progress = progress.min(100.0).max(0.0);
        self.status = if progress >= 100.0 {
            DownloadStatus::Completed
        } else if progress > 0.0 {
            DownloadStatus::Running
        } else {
            DownloadStatus::Pending
        };
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MockHealthResponse {
    pub status: String,
    pub timestamp: String,
    pub version: String,
    pub dependencies: HealthDependencies,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HealthDependencies {
    pub database: String,
    pub redis: String,
    pub nats: String,
}

impl MockHealthResponse {
    /// Create a healthy response (all systems operational)
    pub fn healthy() -> Self {
        MockHealthResponse {
            status: "healthy".to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            version: "2.0.0".to_string(),
            dependencies: HealthDependencies {
                database: "up".to_string(),
                redis: "up".to_string(),
                nats: "up".to_string(),
            },
        }
    }

    /// Create a degraded response (some systems down)
    pub fn degraded() -> Self {
        MockHealthResponse {
            status: "degraded".to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            version: "2.0.0".to_string(),
            dependencies: HealthDependencies {
                database: "up".to_string(),
                redis: "down".to_string(),
                nats: "up".to_string(),
            },
        }
    }

    /// Create an unhealthy response (critical systems down)
    pub fn unhealthy() -> Self {
        MockHealthResponse {
            status: "unhealthy".to_string(),
            timestamp: chrono::Utc::now().to_rfc3339(),
            version: "2.0.0".to_string(),
            dependencies: HealthDependencies {
                database: "down".to_string(),
                redis: "down".to_string(),
                nats: "down".to_string(),
            },
        }
    }
}

/// Helper function to create standard test headers
pub fn test_headers() -> std::collections::HashMap<String, String> {
    let mut headers = std::collections::HashMap::new();
    headers.insert("Content-Type".to_string(), "application/json".to_string());
    headers.insert("User-Agent".to_string(), "Sokoul-Tests/2.0".to_string());
    headers
}

/// Helper function to create a valid JWT token for testing
/// Note: This is a simplified mock - real tokens should be generated by the auth system
pub fn mock_jwt_token(user_id: &str) -> String {
    format!("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidXNlcl9pZCI6IiIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTUxNjIzOTAyMn0.{}", user_id)
}

/// Helper to validate email format
pub fn is_valid_email(email: &str) -> bool {
    email.contains('@') && email.contains('.')
}

/// Helper to validate strong password
pub fn is_strong_password(password: &str) -> bool {
    password.len() >= 8
        && password.chars().any(|c| c.is_uppercase())
        && password.chars().any(|c| c.is_lowercase())
        && password.chars().any(|c| c.is_numeric())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_user_creation() {
        let user = TestUser::new();
        assert!(!user.id.is_empty());
        assert!(!user.username.is_empty());
        assert!(is_valid_email(&user.email));
    }

    #[test]
    fn test_media_samples() {
        let movies = TestMedia::sample_movies();
        assert_eq!(movies.len(), 3);

        let series = TestMedia::sample_series();
        assert_eq!(series.len(), 3);
    }

    #[test]
    fn test_search_response() {
        let response = MockSearchResponse::for_query("inception");
        assert!(!response.results.is_empty());

        let empty = MockSearchResponse::empty();
        assert_eq!(empty.results.len(), 0);
    }

    #[test]
    fn test_health_responses() {
        let healthy = MockHealthResponse::healthy();
        assert_eq!(healthy.status, "healthy");

        let degraded = MockHealthResponse::degraded();
        assert_eq!(degraded.status, "degraded");
    }

    #[test]
    fn test_email_validation() {
        assert!(is_valid_email("test@sokoul.local"));
        assert!(!is_valid_email("invalid-email"));
    }

    #[test]
    fn test_password_validation() {
        assert!(is_strong_password("SecurePassword123!"));
        assert!(!is_strong_password("weak"));
    }
}
