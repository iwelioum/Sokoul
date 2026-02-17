use crate::{api::error::ApiError, db, AppState};
use axum::{
    extract::{Path, Query, State},
    routing::{delete, get},
    Json, Router,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;
use uuid::Uuid;

pub fn security_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route("/audit-logs", get(get_audit_logs_handler))
        .route(
            "/audit-logs/:risk_level",
            get(get_audit_logs_by_risk_handler),
        )
        .route("/reputation/:domain", get(get_reputation_handler))
        .route(
            "/whitelist",
            get(list_whitelist_handler).post(add_whitelist_handler),
        )
        .route("/whitelist/:domain", delete(remove_whitelist_handler))
        .route(
            "/blacklist",
            get(list_blacklist_handler).post(add_blacklist_handler),
        )
        .route("/blacklist/:domain", delete(remove_blacklist_handler))
        .route("/status", get(security_status_handler))
}

#[derive(Serialize)]
pub struct AuditLogResponse {
    pub id: Uuid,
    pub user_id: Option<Uuid>,
    pub action: String,
    pub url: Option<String>,
    pub risk_level: String,
    pub status: String,
    pub created_at: String,
}

#[derive(Deserialize)]
pub struct PaginationParams {
    #[serde(default = "default_page")]
    page: i64,
    #[serde(default = "default_limit")]
    limit: i64,
}

#[derive(Serialize)]
pub struct ReputationResponse {
    pub url: String,
    pub domain: Option<String>,
    pub risk_level: String,
    pub malicious_count: i32,
    pub last_checked: String,
}

#[derive(Deserialize)]
pub struct WhitelistRequest {
    pub domain: String,
    pub reason: Option<String>,
}

#[derive(Deserialize)]
pub struct BlacklistRequest {
    pub domain: String,
    pub risk_level: String,
    pub threat_type: Option<String>,
    pub reason: Option<String>,
}

fn default_page() -> i64 {
    1
}

fn default_limit() -> i64 {
    20
}

/// Get audit logs (admin only)
pub async fn get_audit_logs_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<PaginationParams>,
) -> Result<Json<Vec<AuditLogResponse>>, ApiError> {
    let offset = (params.page - 1) * params.limit;

    let logs = db::security::get_audit_logs(&state.db_pool, params.limit, offset)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let response = logs
        .iter()
        .map(|log| AuditLogResponse {
            id: log.id,
            user_id: log.user_id,
            action: log.action.clone(),
            url: log.url.clone(),
            risk_level: log.risk_level.clone(),
            status: log.status.clone(),
            created_at: log.created_at.to_rfc3339(),
        })
        .collect();

    Ok(Json(response))
}

/// Get audit logs filtered by risk level (admin only)
pub async fn get_audit_logs_by_risk_handler(
    State(state): State<Arc<AppState>>,
    Path(risk_level): Path<String>,
    Query(params): Query<PaginationParams>,
) -> Result<Json<Vec<AuditLogResponse>>, ApiError> {
    let offset = (params.page - 1) * params.limit;

    let logs =
        db::security::get_audit_logs_by_risk(&state.db_pool, &risk_level, params.limit, offset)
            .await
            .map_err(|_| ApiError::InternalServerError)?;

    let response = logs
        .iter()
        .map(|log| AuditLogResponse {
            id: log.id,
            user_id: log.user_id,
            action: log.action.clone(),
            url: log.url.clone(),
            risk_level: log.risk_level.clone(),
            status: log.status.clone(),
            created_at: log.created_at.to_rfc3339(),
        })
        .collect();

    Ok(Json(response))
}

/// Get URL reputation (admin only)
pub async fn get_reputation_handler(
    State(state): State<Arc<AppState>>,
    Path(domain): Path<String>,
) -> Result<Json<ReputationResponse>, ApiError> {
    let reputation = db::security::get_reputation_by_domain(&state.db_pool, &domain)
        .await
        .map_err(|_| ApiError::InternalServerError)?
        .ok_or_else(|| ApiError::NotFound(format!("No reputation data for domain: {}", domain)))?;

    Ok(Json(ReputationResponse {
        url: reputation.url,
        domain: reputation.domain,
        risk_level: reputation.risk_level,
        malicious_count: reputation.malicious_count,
        last_checked: reputation.last_checked.to_rfc3339(),
    }))
}

/// List whitelisted domains (admin only)
pub async fn list_whitelist_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<String>>, ApiError> {
    let whitelist = db::security::get_all_whitelisted_domains(&state.db_pool)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let domains = whitelist.iter().map(|w| w.domain.clone()).collect();

    Ok(Json(domains))
}

/// Add domain to whitelist (admin only)
pub async fn add_whitelist_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<WhitelistRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let whitelist = db::security::insert_whitelist(
        &state.db_pool,
        &payload.domain,
        None,
        payload.reason.as_deref(),
    )
    .await
    .map_err(|_| ApiError::InternalServerError)?;

    Ok(Json(serde_json::json!({
        "message": format!("Domain {} whitelisted", whitelist.domain),
        "domain": whitelist.domain,
    })))
}

/// Remove domain from whitelist (admin only)
pub async fn remove_whitelist_handler(
    State(state): State<Arc<AppState>>,
    Path(domain): Path<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    db::security::remove_whitelist(&state.db_pool, &domain)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    Ok(Json(serde_json::json!({
        "message": format!("Domain {} removed from whitelist", domain),
    })))
}

/// List blacklisted domains (admin only)
pub async fn list_blacklist_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<serde_json::Value>>, ApiError> {
    let blacklist = db::security::get_all_blacklisted_domains(&state.db_pool)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    let domains = blacklist
        .iter()
        .map(|b| {
            serde_json::json!({
                "domain": b.domain,
                "risk_level": b.risk_level,
                "threat_type": b.threat_type,
            })
        })
        .collect();

    Ok(Json(domains))
}

/// Add domain to blacklist (admin only)
pub async fn add_blacklist_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<BlacklistRequest>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let blacklist = db::security::insert_blacklist(
        &state.db_pool,
        &payload.domain,
        &payload.risk_level,
        payload.threat_type.as_deref(),
        None,
        payload.reason.as_deref(),
    )
    .await
    .map_err(|_| ApiError::InternalServerError)?;

    Ok(Json(serde_json::json!({
        "message": format!("Domain {} blacklisted", blacklist.domain),
        "domain": blacklist.domain,
        "risk_level": blacklist.risk_level,
    })))
}

/// Remove domain from blacklist (admin only)
pub async fn remove_blacklist_handler(
    State(state): State<Arc<AppState>>,
    Path(domain): Path<String>,
) -> Result<Json<serde_json::Value>, ApiError> {
    db::security::remove_blacklist(&state.db_pool, &domain)
        .await
        .map_err(|_| ApiError::InternalServerError)?;

    Ok(Json(serde_json::json!({
        "message": format!("Domain {} removed from blacklist", domain),
    })))
}

/// Security status (public, no auth required)
pub async fn security_status_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let virustotal_available = state.virustotal_client.is_some();

    Ok(Json(serde_json::json!({
        "status": "operational",
        "virustotal_enabled": virustotal_available,
        "urlhaus_enabled": true,
        "whitelist_active": true,
        "blacklist_active": true,
    })))
}
