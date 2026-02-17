#![allow(dead_code)]
use crate::{
    clients::{urlhaus::UrlhausClient, virustotal::VirusTotalClient},
    db, AppState,
};
use std::sync::Arc;
use url::Url;
use uuid::Uuid;

#[derive(Debug, Clone)]
pub struct SecurityCheckResult {
    pub url: String,
    pub domain: Option<String>,
    pub risk_level: String,
    pub reason: String,
    pub is_allowed: bool,
    pub virustotal_malicious_count: i32,
    pub urlhaus_threat: Option<String>,
}

impl SecurityCheckResult {
    pub fn safe(url: &str, domain: Option<String>) -> Self {
        Self {
            url: url.to_string(),
            domain,
            risk_level: "safe".to_string(),
            reason: "URL passed security checks".to_string(),
            is_allowed: true,
            virustotal_malicious_count: 0,
            urlhaus_threat: None,
        }
    }

    pub fn whitelisted(url: &str, domain: Option<String>, reason: &str) -> Self {
        Self {
            url: url.to_string(),
            domain,
            risk_level: "safe".to_string(),
            reason: format!("Whitelisted: {}", reason),
            is_allowed: true,
            virustotal_malicious_count: 0,
            urlhaus_threat: None,
        }
    }

    pub fn blocked(url: &str, domain: Option<String>, reason: &str, risk_level: &str) -> Self {
        Self {
            url: url.to_string(),
            domain,
            risk_level: risk_level.to_string(),
            reason: reason.to_string(),
            is_allowed: false,
            virustotal_malicious_count: 0,
            urlhaus_threat: None,
        }
    }
}

fn extract_domain(url: &str) -> Option<String> {
    match Url::parse(url) {
        Ok(parsed) => parsed.host_str().map(|h| h.to_string()),
        Err(_) => None,
    }
}

pub async fn check_url_safety(state: &Arc<AppState>, url: &str) -> SecurityCheckResult {
    let domain = extract_domain(url);

    if let Some(ref domain_str) = domain {
        if let Ok(Some(whitelist_entry)) =
            db::security::get_whitelisted_domain(&state.db_pool, domain_str).await
        {
            return SecurityCheckResult::whitelisted(
                url,
                domain.clone(),
                &whitelist_entry.reason.unwrap_or_default(),
            );
        }
    }

    if let Some(ref domain_str) = domain {
        if let Ok(Some(blacklist_entry)) =
            db::security::get_blacklisted_domain(&state.db_pool, domain_str).await
        {
            return SecurityCheckResult::blocked(
                url,
                domain.clone(),
                &format!(
                    "Domain blacklisted: {}",
                    blacklist_entry.threat_type.unwrap_or_default()
                ),
                &blacklist_entry.risk_level,
            );
        }
    }

    if let Ok(Some(cached)) = db::security::get_reputation_by_url(&state.db_pool, url).await {
        if chrono::Utc::now() < cached.expires_at {
            let risk_level = cached.risk_level.clone();
            return SecurityCheckResult {
                url: url.to_string(),
                domain: domain.clone(),
                risk_level: risk_level.clone(),
                reason: "Cached result".to_string(),
                is_allowed: risk_level == "safe",
                virustotal_malicious_count: cached.malicious_count,
                urlhaus_threat: cached
                    .urlhaus_result
                    .as_ref()
                    .and_then(|v| v["threat"].as_str().map(|s| s.to_string())),
            };
        }
    }

    let mut virustotal_malicious = 0;
    let mut urlhaus_threat = None;
    let mut api_risk_level = "safe".to_string();

    let urlhaus_client = UrlhausClient::new();
    match urlhaus_client.check_url(url).await {
        Ok(result) => {
            if result.blacklist_status {
                api_risk_level = "critical".to_string();
                urlhaus_threat = result.threat;
            }
        }
        Err(e) => {
            tracing::error!("URLhaus check failed: {}", e);
        }
    }

    if state.virustotal_client.is_some() {
        if let Some(vt_client) = &state.virustotal_client {
            match vt_client.scan_url(url).await {
                Ok(result) => {
                    virustotal_malicious = result.last_analysis_stats.malicious;
                    if virustotal_malicious > 0 {
                        let vt_risk =
                            VirusTotalClient::assess_risk_level(&result.last_analysis_stats);
                        if vt_risk == "critical" {
                            api_risk_level = "critical".to_string();
                        } else if vt_risk == "warning" && api_risk_level != "critical" {
                            api_risk_level = "warning".to_string();
                        }
                    }
                }
                Err(e) => {
                    tracing::error!("VirusTotal check failed: {}", e);
                }
            }
        }
    }

    let _ = db::security::insert_or_update_reputation(
        &state.db_pool,
        url,
        domain.as_deref(),
        &api_risk_level,
        None,
        None,
        virustotal_malicious,
    )
    .await;

    SecurityCheckResult {
        url: url.to_string(),
        domain,
        risk_level: api_risk_level.clone(),
        reason: if virustotal_malicious > 0 {
            format!(
                "VirusTotal flagged {} malware indicators",
                virustotal_malicious
            )
        } else if urlhaus_threat.is_some() {
            format!(
                "URLhaus flagged: {}",
                urlhaus_threat.clone().unwrap_or_default()
            )
        } else {
            "URL passed security checks".to_string()
        },
        is_allowed: api_risk_level == "safe",
        virustotal_malicious_count: virustotal_malicious,
        urlhaus_threat,
    }
}

pub async fn log_security_event(
    state: &Arc<AppState>,
    user_id: Option<Uuid>,
    action: &str,
    url: Option<&str>,
    ip_address: Option<&str>,
    risk_level: &str,
    status: &str,
    metadata: Option<serde_json::Value>,
) {
    let _ = db::security::insert_audit_log(
        &state.db_pool,
        user_id,
        action,
        url.map(|_| "url"),
        url,
        url.map(|u| u.split('/').next().unwrap_or(u)),
        ip_address,
        None,
        risk_level,
        status,
        metadata,
    )
    .await;
}
