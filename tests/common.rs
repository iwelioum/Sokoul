//! E2E Test Suite Common Utilities
//!
//! Provides shared functionality for all E2E tests

pub mod fixtures;

/// Re-export commonly used test fixtures
pub use fixtures::{
    is_strong_password, is_valid_email, test_headers, MockDownloadJob, MockHealthResponse,
    MockSearchResponse, TestMedia, TestUser,
};

#[cfg(test)]
mod common_tests {
    use super::*;

    #[test]
    fn test_fixtures_available() {
        let user = TestUser::new();
        assert!(!user.id.is_empty());

        let media = TestMedia::movie("Test");
        assert!(!media.title.is_empty());
    }
}
