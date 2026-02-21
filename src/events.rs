use serde::{Deserialize, Serialize};
use uuid::Uuid;

// ── NATS Subjects ──
pub const SEARCH_REQUESTED_SUBJECT: &str = "sokoul.search.requested";
pub const DOWNLOAD_REQUESTED_SUBJECT: &str = "sokoul.download.requested";
pub const SEARCH_RESULTS_FOUND_SUBJECT: &str = "sokoul.search.results_found";

// ── NATS Payloads ──
#[derive(Debug, Serialize, Deserialize)]
pub struct SearchRequestedPayload {
    pub query: String,
    pub media_id: Option<Uuid>,
    pub tmdb_id: Option<i32>,
    pub season: Option<i32>,
    pub episode: Option<i32>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct DownloadRequestedPayload {
    pub media_id: Uuid,
    pub search_result_id: i32,
    pub magnet_or_url: String,
    pub title: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SearchResultsFoundPayload {
    pub media_id: Uuid,
}

// ── Structured WebSocket Events ──
#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum WsEvent {
    SearchStarted {
        query: String,
    },
    SearchCompleted {
        media_id: String,
        title: String,
        results_count: usize,
    },
    DownloadStarted {
        media_id: String,
        title: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        task_id: Option<String>,
    },
    DownloadProgress {
        media_id: String,
        title: String,
        progress: f64,
        #[serde(skip_serializing_if = "Option::is_none")]
        task_id: Option<String>,
    },
    DownloadCompleted {
        media_id: String,
        title: String,
        file_path: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        task_id: Option<String>,
    },
    DownloadFailed {
        media_id: String,
        title: String,
        error: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        task_id: Option<String>,
    },
    OracleValidated {
        media_id: String,
        validated_count: u32,
    },
    SystemAlert {
        level: String,
        message: String,
    },
    StorageUpdate {
        total_gb: f64,
        used_gb: f64,
        free_gb: f64,
        usage_percent: f64,
    },
}

impl WsEvent {
    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap_or_default()
    }
}
