#![allow(dead_code)]
use chrono::{DateTime, Utc};
use sqlx::FromRow;
use uuid::Uuid;

#[derive(Debug, Clone, FromRow)]
pub struct AuditLog {
    pub id: Uuid,
    pub user_id: Option<Uuid>,
    pub action: String,
    pub resource_type: Option<String>,
    pub resource_id: Option<String>,
    pub url: Option<String>,
    pub ip_address: Option<String>,
    pub user_agent: Option<String>,
    pub risk_level: String,
    pub status: String,
    pub metadata: Option<serde_json::Value>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, FromRow)]
pub struct UrlReputation {
    pub id: Uuid,
    pub url: String,
    pub domain: Option<String>,
    pub risk_level: String,
    pub virustotal_result: Option<serde_json::Value>,
    pub urlhaus_result: Option<serde_json::Value>,
    pub malicious_count: i32,
    pub last_checked: DateTime<Utc>,
    pub expires_at: DateTime<Utc>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, FromRow)]
pub struct DomainWhitelist {
    pub id: Uuid,
    pub domain: String,
    pub added_by: Option<Uuid>,
    pub reason: Option<String>,
    pub is_active: bool,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, FromRow)]
pub struct DomainBlacklist {
    pub id: Uuid,
    pub domain: String,
    pub risk_level: String,
    pub threat_type: Option<String>,
    pub added_by: Option<Uuid>,
    pub reason: Option<String>,
    pub is_active: bool,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

// ═══════════════════════════════════════════════════════════
// AUDIT LOGS — Logging security actions
// ═══════════════════════════════════════════════════════════

pub async fn insert_audit_log(
    db: &sqlx::PgPool,
    user_id: Option<Uuid>,
    action: &str,
    resource_type: Option<&str>,
    resource_id: Option<&str>,
    url: Option<&str>,
    ip_address: Option<&str>,
    user_agent: Option<&str>,
    risk_level: &str,
    status: &str,
    metadata: Option<serde_json::Value>,
) -> Result<AuditLog, sqlx::Error> {
    sqlx::query_as::<_, AuditLog>(
        r#"
        INSERT INTO audit_logs (
            user_id, action, resource_type, resource_id, url,
            ip_address, user_agent, risk_level, status, metadata
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
        RETURNING id, user_id, action, resource_type, resource_id, url,
                  ip_address, user_agent, risk_level, status, metadata, created_at
        "#,
    )
    .bind(user_id)
    .bind(action)
    .bind(resource_type)
    .bind(resource_id)
    .bind(url)
    .bind(ip_address)
    .bind(user_agent)
    .bind(risk_level)
    .bind(status)
    .bind(metadata)
    .fetch_one(db)
    .await
}

pub async fn get_audit_logs(
    db: &sqlx::PgPool,
    limit: i64,
    offset: i64,
) -> Result<Vec<AuditLog>, sqlx::Error> {
    sqlx::query_as::<_, AuditLog>(
        "SELECT id, user_id, action, resource_type, resource_id, url, ip_address, user_agent, risk_level, status, metadata, created_at FROM audit_logs ORDER BY created_at DESC LIMIT $1 OFFSET $2"
    )
    .bind(limit)
    .bind(offset)
    .fetch_all(db)
    .await
}

pub async fn get_audit_logs_by_user(
    db: &sqlx::PgPool,
    user_id: Uuid,
    limit: i64,
    offset: i64,
) -> Result<Vec<AuditLog>, sqlx::Error> {
    sqlx::query_as::<_, AuditLog>(
        "SELECT id, user_id, action, resource_type, resource_id, url, ip_address, user_agent, risk_level, status, metadata, created_at FROM audit_logs WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3"
    )
    .bind(user_id)
    .bind(limit)
    .bind(offset)
    .fetch_all(db)
    .await
}

pub async fn get_audit_logs_by_risk(
    db: &sqlx::PgPool,
    risk_level: &str,
    limit: i64,
    offset: i64,
) -> Result<Vec<AuditLog>, sqlx::Error> {
    sqlx::query_as::<_, AuditLog>(
        "SELECT id, user_id, action, resource_type, resource_id, url, ip_address, user_agent, risk_level, status, metadata, created_at FROM audit_logs WHERE risk_level = $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3"
    )
    .bind(risk_level)
    .bind(limit)
    .bind(offset)
    .fetch_all(db)
    .await
}

// ═══════════════════════════════════════════════════════════
// URL REPUTATION — Caching URL safety checks
// ═══════════════════════════════════════════════════════════

pub async fn insert_or_update_reputation(
    db: &sqlx::PgPool,
    url: &str,
    domain: Option<&str>,
    risk_level: &str,
    virustotal_result: Option<serde_json::Value>,
    urlhaus_result: Option<serde_json::Value>,
    malicious_count: i32,
) -> Result<UrlReputation, sqlx::Error> {
    let expires_at = Utc::now() + chrono::Duration::days(1);

    sqlx::query_as::<_, UrlReputation>(
        r#"
        INSERT INTO url_reputation (
            url, domain, risk_level, virustotal_result, urlhaus_result,
            malicious_count, last_checked, expires_at
        ) VALUES ($1, $2, $3, $4, $5, $6, NOW(), $7)
        ON CONFLICT (url) DO UPDATE SET
            domain = $2,
            risk_level = $3,
            virustotal_result = $4,
            urlhaus_result = $5,
            malicious_count = $6,
            last_checked = NOW(),
            expires_at = $7
        RETURNING id, url, domain, risk_level, virustotal_result, urlhaus_result,
                  malicious_count, last_checked, expires_at, created_at
        "#,
    )
    .bind(url)
    .bind(domain)
    .bind(risk_level)
    .bind(virustotal_result)
    .bind(urlhaus_result)
    .bind(malicious_count)
    .bind(expires_at)
    .fetch_one(db)
    .await
}

pub async fn get_reputation_by_url(
    db: &sqlx::PgPool,
    url: &str,
) -> Result<Option<UrlReputation>, sqlx::Error> {
    sqlx::query_as::<_, UrlReputation>(
        "SELECT id, url, domain, risk_level, virustotal_result, urlhaus_result, malicious_count, last_checked, expires_at, created_at FROM url_reputation WHERE url = $1"
    )
    .bind(url)
    .fetch_optional(db)
    .await
}

pub async fn get_reputation_by_domain(
    db: &sqlx::PgPool,
    domain: &str,
) -> Result<Option<UrlReputation>, sqlx::Error> {
    sqlx::query_as::<_, UrlReputation>(
        "SELECT id, url, domain, risk_level, virustotal_result, urlhaus_result, malicious_count, last_checked, expires_at, created_at FROM url_reputation WHERE domain = $1 LIMIT 1"
    )
    .bind(domain)
    .fetch_optional(db)
    .await
}

// ═══════════════════════════════════════════════════════════
// WHITELIST — Trusted domains
// ═══════════════════════════════════════════════════════════

pub async fn insert_whitelist(
    db: &sqlx::PgPool,
    domain: &str,
    added_by: Option<Uuid>,
    reason: Option<&str>,
) -> Result<DomainWhitelist, sqlx::Error> {
    sqlx::query_as::<_, DomainWhitelist>(
        r#"
        INSERT INTO domain_whitelist (domain, added_by, reason)
        VALUES ($1, $2, $3)
        RETURNING id, domain, added_by, reason, is_active, created_at, updated_at
        "#,
    )
    .bind(domain)
    .bind(added_by)
    .bind(reason)
    .fetch_one(db)
    .await
}

pub async fn get_whitelisted_domain(
    db: &sqlx::PgPool,
    domain: &str,
) -> Result<Option<DomainWhitelist>, sqlx::Error> {
    sqlx::query_as::<_, DomainWhitelist>(
        "SELECT id, domain, added_by, reason, is_active, created_at, updated_at FROM domain_whitelist WHERE domain = $1 AND is_active = true"
    )
    .bind(domain)
    .fetch_optional(db)
    .await
}

pub async fn get_all_whitelisted_domains(
    db: &sqlx::PgPool,
) -> Result<Vec<DomainWhitelist>, sqlx::Error> {
    sqlx::query_as::<_, DomainWhitelist>(
        "SELECT id, domain, added_by, reason, is_active, created_at, updated_at FROM domain_whitelist WHERE is_active = true"
    )
    .fetch_all(db)
    .await
}

pub async fn remove_whitelist(db: &sqlx::PgPool, domain: &str) -> Result<(), sqlx::Error> {
    sqlx::query("DELETE FROM domain_whitelist WHERE domain = $1")
        .bind(domain)
        .execute(db)
        .await?;
    Ok(())
}

// ═══════════════════════════════════════════════════════════
// BLACKLIST — Blocked domains
// ═══════════════════════════════════════════════════════════

pub async fn insert_blacklist(
    db: &sqlx::PgPool,
    domain: &str,
    risk_level: &str,
    threat_type: Option<&str>,
    added_by: Option<Uuid>,
    reason: Option<&str>,
) -> Result<DomainBlacklist, sqlx::Error> {
    sqlx::query_as::<_, DomainBlacklist>(
        r#"
        INSERT INTO domain_blacklist (domain, risk_level, threat_type, added_by, reason)
        VALUES ($1, $2, $3, $4, $5)
        RETURNING id, domain, risk_level, threat_type, added_by, reason, is_active, created_at, updated_at
        "#,
    )
    .bind(domain)
    .bind(risk_level)
    .bind(threat_type)
    .bind(added_by)
    .bind(reason)
    .fetch_one(db)
    .await
}

pub async fn get_blacklisted_domain(
    db: &sqlx::PgPool,
    domain: &str,
) -> Result<Option<DomainBlacklist>, sqlx::Error> {
    sqlx::query_as::<_, DomainBlacklist>(
        "SELECT id, domain, risk_level, threat_type, added_by, reason, is_active, created_at, updated_at FROM domain_blacklist WHERE domain = $1 AND is_active = true"
    )
    .bind(domain)
    .fetch_optional(db)
    .await
}

pub async fn get_all_blacklisted_domains(
    db: &sqlx::PgPool,
) -> Result<Vec<DomainBlacklist>, sqlx::Error> {
    sqlx::query_as::<_, DomainBlacklist>(
        "SELECT id, domain, risk_level, threat_type, added_by, reason, is_active, created_at, updated_at FROM domain_blacklist WHERE is_active = true"
    )
    .fetch_all(db)
    .await
}

pub async fn remove_blacklist(db: &sqlx::PgPool, domain: &str) -> Result<(), sqlx::Error> {
    sqlx::query("DELETE FROM domain_blacklist WHERE domain = $1")
        .bind(domain)
        .execute(db)
        .await?;
    Ok(())
}
