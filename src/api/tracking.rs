use axum::{http::StatusCode, response::IntoResponse};

pub async fn enable_tracking_handler() -> impl IntoResponse {
    (StatusCode::OK, "Tracking enabled (placeholder)")
}

pub async fn disable_tracking_handler() -> impl IntoResponse {
    (StatusCode::OK, "Tracking disabled (placeholder)")
}

pub async fn get_tracking_handler() -> impl IntoResponse {
    (StatusCode::OK, "Get tracking status (placeholder)")
}
