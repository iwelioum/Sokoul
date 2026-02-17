use axum::{extract::Request, middleware::Next, response::Response};
use std::time::Instant;
use tokio::time::{timeout, Duration};

/// Middleware to add global request timeouts
/// Timeout: 30s by default (configurable)
#[allow(dead_code)]
pub async fn timeout_middleware(
    req: Request,
    next: Next,
) -> Result<Response, axum::http::StatusCode> {
    let timeout_duration = Duration::from_secs(30);

    match timeout(timeout_duration, next.run(req)).await {
        Ok(response) => Ok(response),
        Err(_) => {
            tracing::error!("Request timeout after {:?}", timeout_duration);
            Err(axum::http::StatusCode::REQUEST_TIMEOUT)
        }
    }
}

/// Middleware to log requests with timing
#[allow(dead_code)]
pub async fn request_logging_middleware(req: Request, next: Next) -> Response {
    let method = req.method().clone();
    let path = req.uri().path().to_string();
    let start = Instant::now();

    let response = next.run(req).await;

    let elapsed = start.elapsed();
    let status = response.status();

    if status.is_client_error() || status.is_server_error() {
        tracing::warn!(
            "{} {} → {} ({:.2}ms)",
            method,
            path,
            status,
            elapsed.as_secs_f64() * 1000.0
        );
    } else {
        tracing::debug!(
            "{} {} → {} ({:.2}ms)",
            method,
            path,
            status,
            elapsed.as_secs_f64() * 1000.0
        );
    }

    response
}

/// Audit logging for sensitive operations
/// Log CREATE, UPDATE, DELETE with user context
#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct AuditLog {
    pub user_id: Option<String>, // From API key (if known)
    pub operation: AuditOperation,
    pub resource_type: String,
    pub resource_id: String,
    pub status: AuditStatus,
    pub timestamp: chrono::DateTime<chrono::Utc>,
    pub details: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[allow(dead_code)]
pub enum AuditOperation {
    Create,
    Read,
    Update,
    Delete,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[allow(dead_code)]
pub enum AuditStatus {
    Success,
    Failure,
}

#[allow(dead_code)]
impl AuditLog {
    pub fn new(
        operation: AuditOperation,
        resource_type: impl Into<String>,
        resource_id: impl Into<String>,
    ) -> Self {
        Self {
            user_id: None,
            operation,
            resource_type: resource_type.into(),
            resource_id: resource_id.into(),
            status: AuditStatus::Success,
            timestamp: chrono::Utc::now(),
            details: None,
        }
    }

    pub fn with_user(mut self, user_id: impl Into<String>) -> Self {
        self.user_id = Some(user_id.into());
        self
    }

    pub fn with_failure(mut self, reason: impl Into<String>) -> Self {
        self.status = AuditStatus::Failure;
        self.details = Some(reason.into());
        self
    }

    pub fn log(&self) {
        let status_str = match self.status {
            AuditStatus::Success => "✓",
            AuditStatus::Failure => "✗",
        };

        let user_str = self.user_id.as_deref().unwrap_or("anonymous");
        let op_str = match self.operation {
            AuditOperation::Create => "CREATE",
            AuditOperation::Read => "READ",
            AuditOperation::Update => "UPDATE",
            AuditOperation::Delete => "DELETE",
        };

        if let Some(details) = &self.details {
            tracing::info!(
                "[AUDIT] {} {} {} {}/{} - {}",
                status_str,
                user_str,
                op_str,
                self.resource_type,
                self.resource_id,
                details
            );
        } else {
            tracing::info!(
                "[AUDIT] {} {} {} {}/{}",
                status_str,
                user_str,
                op_str,
                self.resource_type,
                self.resource_id
            );
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_audit_log_creation() {
        let log = AuditLog::new(AuditOperation::Create, "media", "uuid-123");
        assert_eq!(log.resource_type, "media");
        assert_eq!(log.status, AuditStatus::Success);
    }

    #[test]
    fn test_audit_log_with_failure() {
        let log = AuditLog::new(AuditOperation::Delete, "media", "uuid-123")
            .with_user("user-456")
            .with_failure("Permission denied");

        assert_eq!(log.status, AuditStatus::Failure);
        assert_eq!(log.user_id, Some("user-456".to_string()));
    }
}
