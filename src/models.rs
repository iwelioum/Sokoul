use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use std::collections::HashMap;
use uuid::Uuid;

// ── Media ──

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct Media {
    pub id: Uuid,
    pub media_type: String,
    pub title: String,
    pub original_title: Option<String>,
    pub year: Option<i32>,
    pub tmdb_id: Option<i32>,
    pub imdb_id: Option<String>,
    pub overview: Option<String>,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub genres: Option<Vec<String>>,
    pub rating: Option<rust_decimal::Decimal>,
    pub runtime_minutes: Option<i32>,
    pub status: Option<String>,
    pub parent_id: Option<Uuid>,
    pub season_number: Option<i32>,
    pub episode_number: Option<i32>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct CreateMediaPayload {
    pub title: String,
    pub media_type: String,
    pub tmdb_id: Option<i32>,
    pub year: Option<i32>,
    pub overview: Option<String>,
    pub poster_url: Option<String>,
    pub genres: Option<Vec<String>>,
    pub rating: Option<f64>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct UpdateMediaPayload {
    pub title: Option<String>,
    pub year: Option<i32>,
    pub overview: Option<String>,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub genres: Option<Vec<String>>,
    pub rating: Option<f64>,
    pub status: Option<String>,
}

// ── Search ──

#[derive(Debug, Deserialize, Serialize)]
pub struct ApiSearchPayload {
    pub query: String,
}

// ── Search Results ──

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct SearchResult {
    pub id: i32,
    pub media_id: Uuid,
    pub provider: String,
    pub title: String,
    pub guid: String,
    pub url: Option<String>,
    pub magnet_link: Option<String>,
    pub info_hash: Option<String>,
    pub protocol: String,
    pub quality: Option<String>,
    pub size_bytes: i64,
    pub seeders: i32,
    pub leechers: i32,
    pub score: Option<i32>,
    pub ai_validated: Option<bool>,
    pub created_at: DateTime<Utc>,
    pub expires_at: Option<DateTime<Utc>>,
}

// ── Tasks ──

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct Task {
    pub id: Uuid,
    pub task_type: String,
    pub status: String,
    pub payload: Option<serde_json::Value>,
    pub result: Option<serde_json::Value>,
    pub progress: Option<rust_decimal::Decimal>,
    pub error: Option<String>,
    pub created_at: Option<DateTime<Utc>>,
    pub started_at: Option<DateTime<Utc>>,
    pub completed_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct CreateTaskPayload {
    pub task_type: String,
    pub payload: Option<serde_json::Value>,
}

// ── Favorites ──

#[allow(dead_code)]
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Favorite {
    pub id: Uuid,
    pub media_id: Uuid,
    pub added_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct AddFavoritePayload {
    pub media_id: Option<Uuid>,
    pub tmdb_id: Option<i32>,
    pub media_type: Option<String>,
    pub title: Option<String>,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub vote_average: Option<f64>,
    pub release_date: Option<String>,
    pub overview: Option<String>,
    pub year: Option<i32>,
}

// ── Watch History ──

#[allow(dead_code)]
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchHistoryEntry {
    pub id: Uuid,
    pub media_id: Uuid,
    pub progress_seconds: i32,
    pub completed: bool,
    pub last_watched_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct UpdateWatchProgressPayload {
    pub media_id: Option<Uuid>,
    pub tmdb_id: Option<i32>,
    pub media_type: Option<String>,
    pub title: Option<String>,
    pub poster_url: Option<String>,
    pub progress_seconds: Option<i32>,
    pub progress: Option<f64>,
    #[serde(default)]
    pub completed: bool,
}

// ── Watchlist ──

#[allow(dead_code)]
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WatchlistEntry {
    pub id: Uuid,
    pub media_id: Uuid,
    pub added_at: DateTime<Utc>,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct AddWatchlistPayload {
    pub media_id: Option<Uuid>,
    pub tmdb_id: Option<i32>,
    pub media_type: Option<String>,
    pub title: Option<String>,
    pub poster_url: Option<String>,
    pub backdrop_url: Option<String>,
    pub vote_average: Option<f64>,
    pub release_date: Option<String>,
    pub overview: Option<String>,
    pub year: Option<i32>,
    #[serde(default)]
    pub auto_download: bool,
    #[serde(default = "default_quality")]
    pub quality_min: String,
}

fn default_quality() -> String {
    "1080p".to_string()
}

// ── Library Status ──

#[derive(Debug, Serialize, Deserialize)]
pub struct LibraryStatus {
    pub in_library: bool,
    pub in_watchlist: bool,
    pub watch_progress: Option<i32>,
    pub completed: bool,
}

// ── Media Files ──

#[derive(Debug, Serialize, Deserialize, FromRow, Clone)]
pub struct MediaFile {
    pub id: Uuid,
    pub media_id: Uuid,
    pub file_path: String,
    pub file_size: Option<i64>,
    pub codec_video: Option<String>,
    pub codec_audio: Option<String>,
    pub resolution: Option<String>,
    pub quality_score: Option<i32>,
    pub hash_info: Option<String>,
    pub source: Option<String>,
    pub downloaded_at: Option<DateTime<Utc>>,
}

// ── Pagination ──
#[allow(dead_code)]
#[derive(Debug, Serialize, Deserialize)]
pub struct PaginatedFavorites {
    pub items: Vec<Favorite>,
    pub total: i64,
    pub page: i64,
    pub per_page: i64,
    pub total_pages: i64,
}

#[allow(dead_code)]
#[derive(Debug, Serialize, Deserialize)]
pub struct PaginatedWatchlist {
    pub items: Vec<WatchlistEntry>,
    pub total: i64,
    pub page: i64,
    pub per_page: i64,
    pub total_pages: i64,
}

// ── Streaming ──

#[derive(Debug, Serialize, Deserialize, Clone)]
#[allow(dead_code)]
pub struct StreamResponse {
    pub sources: Vec<StreamSource>,
    pub recommended: usize, // Index de la source recommandée
    pub subtitles: Vec<SubtitleTrack>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct StreamSource {
    pub url: String,
    pub quality: String,  // "1080p", "720p", "auto"
    pub provider: String, // "flixhq", "vidsrc", etc.
    pub has_vf: bool,
    pub is_alive: bool,
    pub audio_tracks: Vec<AudioTrack>,
    /// HTTP headers required to fetch this stream (e.g. Referer from Consumet).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub headers: Option<HashMap<String, String>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[allow(dead_code)]
pub struct SubtitleTrack {
    pub lang: String,
    pub label: String,
    pub url: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AudioTrack {
    pub id: u32,
    pub lang: String,
    pub label: String,
}
