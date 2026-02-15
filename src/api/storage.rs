use crate::{api::error::ApiError, config::CONFIG, AppState};
use axum::{extract::State, Json};
use serde::Serialize;
use std::sync::Arc;

#[derive(Debug, Serialize)]
pub struct StorageInfo {
    pub download_dir: String,
    pub total_bytes: u64,
    pub used_bytes: u64,
    pub free_bytes: u64,
    pub usage_percent: f64,
    pub files_count: u64,
    pub total_media_size_bytes: u64,
}

pub async fn get_storage_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<StorageInfo>, ApiError> {
    let download_dir = CONFIG.download_dir.clone();

    let disks = sysinfo::Disks::new_with_refreshed_list();

    let download_path = std::path::Path::new(&download_dir);
    let (total, free) = disks
        .iter()
        .filter(|d| download_path.starts_with(d.mount_point()))
        .max_by_key(|d| d.mount_point().as_os_str().len())
        .map(|d| (d.total_space(), d.available_space()))
        .unwrap_or((0, 0));

    let used = total.saturating_sub(free);
    let usage_percent = if total > 0 {
        (used as f64 / total as f64) * 100.0
    } else {
        0.0
    };

    let dir_clone = download_dir.clone();
    let (files_count, total_media_size) = count_dir_stats(&dir_clone).await;

    let db_total: i64 = sqlx::query_scalar("SELECT COALESCE(SUM(file_size), 0) FROM media_files")
        .fetch_one(&state.db_pool)
        .await
        .unwrap_or(0);

    Ok(Json(StorageInfo {
        download_dir,
        total_bytes: total,
        used_bytes: used,
        free_bytes: free,
        usage_percent,
        files_count,
        total_media_size_bytes: total_media_size.max(db_total as u64),
    }))
}

async fn count_dir_stats(dir: &str) -> (u64, u64) {
    let path = std::path::PathBuf::from(dir);
    if !path.exists() {
        return (0, 0);
    }

    tokio::task::spawn_blocking(move || {
        let mut count = 0u64;
        let mut size = 0u64;
        if let Ok(entries) = std::fs::read_dir(&path) {
            for entry in entries.flatten() {
                if let Ok(meta) = entry.metadata() {
                    if meta.is_file() {
                        count += 1;
                        size += meta.len();
                    }
                }
            }
        }
        (count, size)
    })
    .await
    .unwrap_or((0, 0))
}
