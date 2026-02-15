use axum::{
    extract::Request,
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;

use crate::config::CONFIG;

pub async fn api_key_middleware(request: Request, next: Next) -> Response {
    if CONFIG.api_key.is_empty() {
        return next.run(request).await;
    }

    let auth_header = request
        .headers()
        .get("X-API-Key")
        .or_else(|| request.headers().get("Authorization"))
        .and_then(|v| v.to_str().ok());

    let provided_key = match auth_header {
        Some(key) => key.trim_start_matches("Bearer ").to_string(),
        None => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(json!({ "error": "Clé API manquante. Utilisez l'en-tête X-API-Key ou Authorization: Bearer <key>." })),
            )
                .into_response();
        }
    };

    if provided_key != CONFIG.api_key {
        return (
            StatusCode::FORBIDDEN,
            Json(json!({ "error": "Clé API invalide." })),
        )
            .into_response();
    }

    next.run(request).await
}
