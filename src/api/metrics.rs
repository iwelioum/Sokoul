use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

pub async fn metrics_handler() -> Result<impl IntoResponse, MetricsError> {
    match crate::metrics::gather_metrics() {
        Ok(metrics) => Ok((StatusCode::OK, metrics)),
        Err(e) => Err(MetricsError(e)),
    }
}

// Custom error type for metrics gathering
pub struct MetricsError(Box<dyn std::error::Error>);

impl IntoResponse for MetricsError {
    fn into_response(self) -> Response {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            format!("Failed to gather metrics: {}", self.0),
        )
            .into_response()
    }
}
