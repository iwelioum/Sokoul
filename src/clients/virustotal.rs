use serde::{Deserialize, Serialize};
use std::error::Error;

#[derive(Debug, Clone)]
pub struct VirusTotalClient {
    api_key: String,
    base_url: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct UrlScanResult {
    pub url: String,
    pub last_analysis_stats: AnalysisStats,
    pub last_analysis_date: Option<i64>,
    pub reputation: Option<i64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AnalysisStats {
    pub malicious: i32,
    pub suspicious: i32,
    pub undetected: i32,
    pub harmless: i32,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct DomainReputationResult {
    pub domain: String,
    pub reputation: i64,
    pub last_dns_records: Option<Vec<String>>,
    pub categories: Option<std::collections::HashMap<String, String>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct FileScanResult {
    pub file_hash: String,
    pub last_analysis_stats: AnalysisStats,
    pub meaningful_name: Option<String>,
}

impl VirusTotalClient {
    pub fn new(api_key: String) -> Self {
        Self {
            api_key,
            base_url: "https://www.virustotal.com/api/v3".to_string(),
        }
    }

    /// Scan a URL for malware/phishing
    /// Returns: (malicious_count, suspicious_count, total_vendors)
    pub async fn scan_url(&self, url: &str) -> Result<UrlScanResult, Box<dyn Error>> {
        let url_id = urlx_encode(url);

        let client = reqwest::Client::new();
        let response = client
            .get(&format!("{}/urls/{}", self.base_url, url_id))
            .header("x-apikey", &self.api_key)
            .timeout(std::time::Duration::from_secs(10))
            .send()
            .await?;

        if response.status() != 200 {
            return Err(format!("VirusTotal API error: {}", response.status()).into());
        }

        let data: serde_json::Value = response.json().await?;
        let attributes = &data["data"]["attributes"];

        Ok(UrlScanResult {
            url: url.to_string(),
            last_analysis_stats: serde_json::from_value(attributes["last_analysis_stats"].clone())?,
            last_analysis_date: attributes["last_analysis_date"].as_i64(),
            reputation: attributes["reputation"].as_i64(),
        })
    }

    /// Scan a file (by hash: MD5, SHA-1, SHA-256)
    pub async fn scan_file(&self, file_hash: &str) -> Result<FileScanResult, Box<dyn Error>> {
        let client = reqwest::Client::new();
        let response = client
            .get(&format!("{}/files/{}", self.base_url, file_hash))
            .header("x-apikey", &self.api_key)
            .timeout(std::time::Duration::from_secs(10))
            .send()
            .await?;

        if response.status() != 200 {
            return Err(format!("VirusTotal API error: {}", response.status()).into());
        }

        let data: serde_json::Value = response.json().await?;
        let attributes = &data["data"]["attributes"];

        Ok(FileScanResult {
            file_hash: file_hash.to_string(),
            last_analysis_stats: serde_json::from_value(attributes["last_analysis_stats"].clone())?,
            meaningful_name: attributes["meaningful_name"]
                .as_str()
                .map(|s| s.to_string()),
        })
    }

    /// Get domain reputation
    pub async fn get_domain_reputation(
        &self,
        domain: &str,
    ) -> Result<DomainReputationResult, Box<dyn Error>> {
        let client = reqwest::Client::new();
        let response = client
            .get(&format!("{}/domains/{}", self.base_url, domain))
            .header("x-apikey", &self.api_key)
            .timeout(std::time::Duration::from_secs(10))
            .send()
            .await?;

        if response.status() != 200 {
            return Err(format!("VirusTotal API error: {}", response.status()).into());
        }

        let data: serde_json::Value = response.json().await?;
        let attributes = &data["data"]["attributes"];

        Ok(DomainReputationResult {
            domain: domain.to_string(),
            reputation: attributes["reputation"].as_i64().unwrap_or(0),
            last_dns_records: attributes["last_dns_records"].as_array().map(|arr| {
                arr.iter()
                    .filter_map(|r| r["value"].as_str().map(|s| s.to_string()))
                    .collect()
            }),
            categories: attributes["categories"].as_object().map(|obj| {
                obj.iter()
                    .map(|(k, v)| (k.clone(), v.as_str().unwrap_or("").to_string()))
                    .collect()
            }),
        })
    }

    /// Determine risk level from analysis stats
    pub fn assess_risk_level(stats: &AnalysisStats) -> String {
        match stats.malicious {
            0 => {
                if stats.suspicious > 0 {
                    "warning".to_string()
                } else {
                    "safe".to_string()
                }
            }
            1..=3 => "warning".to_string(),
            _ => "critical".to_string(),
        }
    }
}

/// URL-safe base64 encoding (VirusTotal format)
fn urlx_encode(url: &str) -> String {
    use std::fmt::Write;
    url.chars().fold(String::new(), |mut output, c| {
        match c {
            'A'..='Z' | 'a'..='z' | '0'..='9' | '-' | '_' => output.push(c),
            _ => {
                let _ = write!(output, "{:02x}", c as u8);
            }
        }
        output
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_assess_risk_level_safe() {
        let stats = AnalysisStats {
            malicious: 0,
            suspicious: 0,
            undetected: 45,
            harmless: 50,
        };
        assert_eq!(VirusTotalClient::assess_risk_level(&stats), "safe");
    }

    #[test]
    fn test_assess_risk_level_warning() {
        let stats = AnalysisStats {
            malicious: 0,
            suspicious: 2,
            undetected: 45,
            harmless: 48,
        };
        assert_eq!(VirusTotalClient::assess_risk_level(&stats), "warning");
    }

    #[test]
    fn test_assess_risk_level_critical() {
        let stats = AnalysisStats {
            malicious: 5,
            suspicious: 2,
            undetected: 40,
            harmless: 48,
        };
        assert_eq!(VirusTotalClient::assess_risk_level(&stats), "critical");
    }
}
