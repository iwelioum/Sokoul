use axum::{body::Body, extract::MatchedPath, http::Request, middleware::Next, response::Response};
use std::time::Instant;

use crate::metrics::{API_REQUESTS_TOTAL, API_REQUEST_DURATION_SECONDS};

/// Middleware that instruments all API requests with Prometheus metrics
pub async fn track_metrics(matched_path: MatchedPath, req: Request<Body>, next: Next) -> Response {
    let path = matched_path.as_str();
    let method = req.method().to_string();
    let start = Instant::now();

    let res = next.run(req).await;

    let duration = start.elapsed().as_secs_f64();
    let status = res.status().as_u16().to_string();

    // Record metrics
    API_REQUESTS_TOTAL
        .with_label_values(&[path, &method, &status])
        .inc();

    API_REQUEST_DURATION_SECONDS
        .with_label_values(&[path, &method, &status])
        .observe(duration);

    res
}
