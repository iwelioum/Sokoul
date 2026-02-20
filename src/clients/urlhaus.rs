#![allow(dead_code)]
use serde::{Deserialize, Serialize};
use std::error::Error;

#[derive(Debug, Clone)]
pub struct UrlhausClient {
    base_url: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct UrlhausCheckResult {
    pub url: String,
    pub threat: Option<String>, // "malware", "phishing", etc
    pub malware_family: Option<String>,
    pub date_added: Option<String>,
    pub blacklist_status: bool,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct UrlhausSearchResult {
    pub results: Vec<UrlhausCheckResult>,
    pub query_status: String, // "ok", "not_found"
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct PayloadSearchResult {
    pub results: Vec<PayloadInfo>,
    pub query_status: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct PayloadInfo {
    pub file_hash: String,
    pub file_type: Option<String>,
    pub file_size: Option<i64>,
    pub threat: Option<String>,
    pub urls: Option<Vec<String>>,
}

impl UrlhausClient {
    pub fn new() -> Self {
        Self {
            base_url: "https://urlhaus-api.abuse.ch/v1".to_string(),
        }
    }

    /// Check if URL is in URLhaus database
    /// No authentication required
    pub async fn check_url(&self, url: &str) -> Result<UrlhausCheckResult, Box<dyn Error>> {
        let client = reqwest::Client::new();
        let response = client
            .post(format!("{}/url/", self.base_url))
            .form(&[("url", url)])
            .timeout(std::time::Duration::from_secs(10))
            .send()
            .await?;

        if response.status() != 200 {
            return Err(format!("URLhaus API error: {}", response.status()).into());
        }

        let data: serde_json::Value = response.json().await?;

        // URLhaus returns {"query_status": "ok", "result": {...}} or "not_found"
        match data["query_status"].as_str() {
            Some("ok") => {
                let result = &data["result"];
                Ok(UrlhausCheckResult {
                    url: url.to_string(),
                    threat: result["threat"].as_str().map(|s| s.to_string()),
                    malware_family: result["malware_family"].as_str().map(|s| s.to_string()),
                    date_added: result["date_added"].as_str().map(|s| s.to_string()),
                    blacklist_status: result["threat"].as_str().is_some(),
                })
            }
            _ => {
                // Not found in URLhaus — safe
                Ok(UrlhausCheckResult {
                    url: url.to_string(),
                    threat: None,
                    malware_family: None,
                    date_added: None,
                    blacklist_status: false,
                })
            }
        }
    }

    /// Search for URLs by domain
    pub async fn search_urls(&self, domain: &str) -> Result<UrlhausSearchResult, Box<dyn Error>> {
        let client = reqwest::Client::new();
        let response = client
            .post(format!("{}/urls/", self.base_url))
            .form(&[("search", domain)])
            .timeout(std::time::Duration::from_secs(10))
            .send()
            .await?;

        if response.status() != 200 {
            return Err(format!("URLhaus API error: {}", response.status()).into());
        }

        let data: serde_json::Value = response.json().await?;

        let results = match data["results"].as_array() {
            Some(arr) => arr
                .iter()
                .map(|item| UrlhausCheckResult {
                    url: item["url"].as_str().unwrap_or("").to_string(),
                    threat: item["threat"].as_str().map(|s| s.to_string()),
                    malware_family: item["malware_family"].as_str().map(|s| s.to_string()),
                    date_added: item["date_added"].as_str().map(|s| s.to_string()),
                    blacklist_status: item["threat"].as_str().is_some(),
                })
                .collect(),
            None => vec![],
        };

        Ok(UrlhausSearchResult {
            results,
            query_status: data["query_status"]
                .as_str()
                .unwrap_or("unknown")
                .to_string(),
        })
    }

    /// Search for malware payloads by hash
    pub async fn search_payload(
        &self,
        file_hash: &str,
    ) -> Result<PayloadSearchResult, Box<dyn Error>> {
        let client = reqwest::Client::new();
        let response = client
            .post(format!("{}/payload/", self.base_url))
            .form(&[("sha256_hash", file_hash)])
            .timeout(std::time::Duration::from_secs(10))
            .send()
            .await?;

        if response.status() != 200 {
            return Err(format!("URLhaus API error: {}", response.status()).into());
        }

        let data: serde_json::Value = response.json().await?;

        let results = match data["results"].as_array() {
            Some(arr) => arr
                .iter()
                .map(|item| PayloadInfo {
                    file_hash: item["file_hash"].as_str().unwrap_or("").to_string(),
                    file_type: item["file_type"].as_str().map(|s| s.to_string()),
                    file_size: item["file_size"].as_i64(),
                    threat: item["threat"].as_str().map(|s| s.to_string()),
                    urls: item["urls"].as_array().map(|arr| {
                        arr.iter()
                            .filter_map(|u| u.as_str().map(|s| s.to_string()))
                            .collect()
                    }),
                })
                .collect(),
            None => vec![],
        };

        Ok(PayloadSearchResult {
            results,
            query_status: data["query_status"]
                .as_str()
                .unwrap_or("unknown")
                .to_string(),
        })
    }

    /// Determine risk level from URLhaus result
    pub fn assess_risk_level(result: &UrlhausCheckResult) -> String {
        match &result.threat {
            Some(threat) => match threat.as_str() {
                "malware" => "critical".to_string(),
                "phishing" => "critical".to_string(),
                "scam" => "warning".to_string(),
                _ => "warning".to_string(),
            },
            None => "safe".to_string(),
        }
    }
}

impl Default for UrlhausClient {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_assess_risk_level_malware() {
        let result = UrlhausCheckResult {
            url: "http://example.com".to_string(),
            threat: Some("malware".to_string()),
            malware_family: Some("trojan".to_string()),
            date_added: None,
            blacklist_status: true,
        };
        assert_eq!(UrlhausClient::assess_risk_level(&result), "critical");
    }

    #[test]
    fn test_assess_risk_level_phishing() {
        let result = UrlhausCheckResult {
            url: "http://example.com".to_string(),
            threat: Some("phishing".to_string()),
            malware_family: None,
            date_added: None,
            blacklist_status: true,
        };
        assert_eq!(UrlhausClient::assess_risk_level(&result), "critical");
    }

    #[test]
    fn test_assess_risk_level_safe() {
        let result = UrlhausCheckResult {
            url: "http://example.com".to_string(),
            threat: None,
            malware_family: None,
            date_added: None,
            blacklist_status: false,
        };
        assert_eq!(UrlhausClient::assess_risk_level(&result), "safe");
    }
}
