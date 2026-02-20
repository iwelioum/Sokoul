use axum::{
    extract::{Request, State},
    http::{header, StatusCode},
    middleware::Next,
    response::{IntoResponse, Response},
    routing::post,
    Json, Router,
};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::sync::Arc;
use uuid::Uuid;

use crate::config::CONFIG;
use crate::db;
use crate::AppState;

/// Cookie name for the access token
const ACCESS_TOKEN_COOKIE: &str = "access_token";

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Claims {
    pub sub: String, // user_id
    pub username: String,
    pub role: String,
    pub exp: usize,
    pub iat: usize,
}

#[derive(Debug, Deserialize)]
pub struct RegisterRequest {
    pub username: String,
    pub email: String,
    pub password: String,
}

#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub email: String,
    pub password: String,
}

#[derive(Debug, Serialize)]
pub struct AuthResponse {
    pub token: String,
    pub user: db::users::UserPublic,
}

pub fn auth_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/auth/register", post(register_handler))
        .route("/auth/login", post(login_handler))
        .route("/auth/logout", post(logout_handler))
        .route("/auth/me", axum::routing::get(me_handler))
}

/// Build a Set-Cookie header value for the access token (HttpOnly, Secure, SameSite)
fn build_auth_cookie(token: &str, max_age_secs: i64) -> String {
    let secure_flag = if CONFIG.node_env == "production" {
        "; Secure"
    } else {
        ""
    };
    format!(
        "{}={}; HttpOnly; Path=/; Max-Age={}; SameSite=Lax{}",
        ACCESS_TOKEN_COOKIE, token, max_age_secs, secure_flag
    )
}

async fn register_handler(
    State(state): State<Arc<AppState>>,
    Json(body): Json<RegisterRequest>,
) -> Result<Response, Response> {
    if body.username.len() < 3 || body.username.len() > 32 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(json!({"error": "Username must be between 3 and 32 characters."})),
        )
            .into_response());
    }
    if !body.email.contains('@') || body.email.len() < 5 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(json!({"error": "Invalid email."})),
        )
            .into_response());
    }
    if body.password.len() < 8 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json(json!({"error": "Password must be at least 8 characters."})),
        )
            .into_response());
    }

    if let Ok(Some(_)) = db::users::find_by_email(&state.db_pool, &body.email).await {
        return Err((
            StatusCode::CONFLICT,
            Json(json!({"error": "An account with this email already exists."})),
        )
            .into_response());
    }
    if let Ok(Some(_)) = db::users::find_by_username(&state.db_pool, &body.username).await {
        return Err((
            StatusCode::CONFLICT,
            Json(json!({"error": "This username is already taken."})),
        )
            .into_response());
    }

    let password_hash = bcrypt::hash(&body.password, 12).map_err(|_| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({"error": "Password hashing error."})),
        )
            .into_response()
    })?;

    let user = db::users::create_user(&state.db_pool, &body.username, &body.email, &password_hash)
        .await
        .map_err(|e| {
            tracing::error!("Failed to create user: {}", e);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(json!({"error": "Failed to create account."})),
            )
                .into_response()
        })?;

    let token = create_jwt(&user.id.to_string(), &user.username, &user.role)?;
    let cookie = build_auth_cookie(&token, 7 * 86400);

    Ok((
        StatusCode::OK,
        [(header::SET_COOKIE, cookie)],
        Json(AuthResponse {
            token: token.clone(),
            user: user.into(),
        }),
    )
        .into_response())
}

async fn login_handler(
    State(state): State<Arc<AppState>>,
    Json(body): Json<LoginRequest>,
) -> Result<Response, Response> {
    let user = db::users::find_by_email(&state.db_pool, &body.email)
        .await
        .map_err(|_| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(json!({"error": "Server error."})),
            )
                .into_response()
        })?
        .ok_or_else(|| {
            (
                StatusCode::UNAUTHORIZED,
                Json(json!({"error": "Invalid email or password."})),
            )
                .into_response()
        })?;

    let valid = bcrypt::verify(&body.password, &user.password_hash).unwrap_or(false);
    if !valid {
        return Err((
            StatusCode::UNAUTHORIZED,
            Json(json!({"error": "Invalid email or password."})),
        )
            .into_response());
    }

    let token = create_jwt(&user.id.to_string(), &user.username, &user.role)?;
    let cookie = build_auth_cookie(&token, 7 * 86400);

    Ok((
        StatusCode::OK,
        [(header::SET_COOKIE, cookie)],
        Json(AuthResponse {
            token: token.clone(),
            user: user.into(),
        }),
    )
        .into_response())
}

/// POST /auth/logout — Clear the auth cookie
async fn logout_handler() -> impl IntoResponse {
    let cookie = format!(
        "{}=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax",
        ACCESS_TOKEN_COOKIE
    );
    (
        StatusCode::OK,
        [(header::SET_COOKIE, cookie)],
        Json(json!({"message": "Logged out."})),
    )
}

async fn me_handler(
    State(state): State<Arc<AppState>>,
    headers: axum::http::HeaderMap,
) -> Result<Json<db::users::UserPublic>, Response> {
    let user_id = extract_user_id(&headers).ok_or_else(|| {
        (
            StatusCode::UNAUTHORIZED,
            Json(json!({"error": "JWT token required."})),
        )
            .into_response()
    })?;

    let user = db::users::find_by_id(&state.db_pool, user_id)
        .await
        .map_err(|_| {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(json!({"error": "Server error."})),
            )
                .into_response()
        })?
        .ok_or_else(|| {
            (
                StatusCode::NOT_FOUND,
                Json(json!({"error": "User not found."})),
            )
                .into_response()
        })?;

    Ok(Json(user.into()))
}

#[allow(clippy::result_large_err)]
fn create_jwt(user_id: &str, username: &str, role: &str) -> Result<String, Response> {
    let now = chrono::Utc::now().timestamp() as usize;
    let claims = Claims {
        sub: user_id.to_string(),
        username: username.to_string(),
        role: role.to_string(),
        iat: now,
        exp: now + 86400 * 7, // 7 days
    };

    encode(
        &Header::default(),
        &claims,
        &EncodingKey::from_secret(CONFIG.jwt_secret.as_bytes()),
    )
    .map_err(|_| {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(json!({"error": "Failed to create token."})),
        )
            .into_response()
    })
}

pub fn decode_jwt(token: &str) -> Option<Claims> {
    decode::<Claims>(
        token,
        &DecodingKey::from_secret(CONFIG.jwt_secret.as_bytes()),
        &Validation::default(),
    )
    .ok()
    .map(|data| data.claims)
}

/// Extract JWT from Cookie header, falling back to Authorization Bearer header
fn extract_token_from_headers(headers: &axum::http::HeaderMap) -> Option<String> {
    // 1. Try HttpOnly cookie first
    if let Some(cookie_header) = headers.get("Cookie").and_then(|v| v.to_str().ok()) {
        for part in cookie_header.split(';') {
            let part = part.trim();
            if let Some(token) = part.strip_prefix(&format!("{}=", ACCESS_TOKEN_COOKIE)) {
                if !token.is_empty() {
                    return Some(token.to_string());
                }
            }
        }
    }

    // 2. Fallback to Authorization header (backward compatible)
    headers
        .get("Authorization")
        .or_else(|| headers.get("X-API-Key"))
        .and_then(|v| v.to_str().ok())
        .map(|v| v.trim_start_matches("Bearer ").to_string())
}

/// Middleware: accepts JWT from Cookie OR Bearer token OR API key
pub async fn api_key_middleware(request: Request, next: Next) -> Response {
    if CONFIG.api_key.is_empty() && CONFIG.jwt_secret == "sokoul_default_secret_change_me" {
        return next.run(request).await;
    }

    let token = match extract_token_from_headers(request.headers()) {
        Some(t) => t,
        None => {
            return (
                StatusCode::UNAUTHORIZED,
                Json(json!({"error": "Authentication required. Use a JWT token or API key."})),
            )
                .into_response();
        }
    };

    // Try API key first (backward compatible)
    if !CONFIG.api_key.is_empty() && token == CONFIG.api_key {
        return next.run(request).await;
    }

    // Try JWT
    if decode_jwt(&token).is_some() {
        return next.run(request).await;
    }

    (
        StatusCode::FORBIDDEN,
        Json(json!({"error": "Invalid token or API key."})),
    )
        .into_response()
}

/// Extract user_id from JWT in request headers (Cookie or Bearer).
pub fn extract_user_id(headers: &axum::http::HeaderMap) -> Option<Uuid> {
    let token = extract_token_from_headers(headers)?;
    let raw = token.trim_start_matches("Bearer ");
    let claims = decode_jwt(raw)?;
    Uuid::parse_str(&claims.sub).ok()
}
