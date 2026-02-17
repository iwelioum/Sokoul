use crate::{config::CONFIG, events::WsEvent, AppState};
use std::{sync::Arc, time::Duration};
use sysinfo::{CpuRefreshKind, Disks, RefreshKind, System};
use tokio::time;

pub async fn sentinel_worker(state: Arc<AppState>) -> anyhow::Result<()> {
    tracing::info!("Sentinel worker starting...");

    let mut sys = System::new_with_specifics(
        RefreshKind::new()
            .with_cpu(CpuRefreshKind::everything())
            .with_memory(sysinfo::MemoryRefreshKind::everything()),
    );

    let mut interval = time::interval(Duration::from_secs(60));

    loop {
        interval.tick().await;

        // 1. Health Check DB
        let db_ok = sqlx::query("SELECT 1")
            .execute(&state.db_pool)
            .await
            .is_ok();
        if !db_ok {
            tracing::error!("Sentinel: ALERT - DATABASE DOWN");
            let _ = state.event_tx.send(
                WsEvent::SystemAlert {
                    level: "critical".to_string(),
                    message: "Database unreachable".to_string(),
                }
                .to_json(),
            );
        }

        // 2. Health Check Redis
        let redis_ok = match state.redis_client.get_multiplexed_async_connection().await {
            Ok(mut con) => redis::cmd("PING")
                .query_async::<_, String>(&mut con)
                .await
                .is_ok(),
            Err(e) => {
                tracing::error!("Sentinel: ALERT - REDIS DOWN: {}", e);
                let _ = state.event_tx.send(
                    WsEvent::SystemAlert {
                        level: "critical".to_string(),
                        message: "Redis unreachable".to_string(),
                    }
                    .to_json(),
                );
                false
            }
        };

        // 3. System Metrics
        sys.refresh_all();
        let cpu_usage = sys.global_cpu_info().cpu_usage();
        let total_mem = sys.total_memory();
        let used_mem = sys.used_memory();
        let mem_percent = if total_mem > 0 {
            (used_mem as f64 / total_mem as f64) * 100.0
        } else {
            0.0
        };

        tracing::debug!(
            "Sentinel Metrics: CPU {:.1}% | RAM {:.1}% ({}/{} MB) | DB:{} Redis:{}",
            cpu_usage,
            mem_percent,
            used_mem / 1024 / 1024,
            total_mem / 1024 / 1024,
            if db_ok { "OK" } else { "DOWN" },
            if redis_ok { "OK" } else { "DOWN" }
        );

        // 4. Storage monitoring
        let disks = Disks::new_with_refreshed_list();
        let download_path = std::path::Path::new(&CONFIG.download_dir);
        if let Some(disk) = disks
            .iter()
            .filter(|d| download_path.starts_with(d.mount_point()))
            .max_by_key(|d| d.mount_point().as_os_str().len())
        {
            let total = disk.total_space();
            let free = disk.available_space();
            let used = total.saturating_sub(free);
            let disk_percent = if total > 0 {
                (used as f64 / total as f64) * 100.0
            } else {
                0.0
            };

            let _ = state.event_tx.send(
                WsEvent::StorageUpdate {
                    total_gb: total as f64 / 1_073_741_824.0,
                    used_gb: used as f64 / 1_073_741_824.0,
                    free_gb: free as f64 / 1_073_741_824.0,
                    usage_percent: disk_percent,
                }
                .to_json(),
            );

            if disk_percent > 90.0 {
                tracing::warn!("Sentinel: ALERT - CRITICAL STORAGE ({:.1}%)", disk_percent);
                let _ = state.event_tx.send(
                    WsEvent::SystemAlert {
                        level: "warning".to_string(),
                        message: format!("Critical storage: {:.1}% used", disk_percent),
                    }
                    .to_json(),
                );
            }
        }

        // Alert if RAM critical (> 90%)
        if mem_percent > 90.0 {
            tracing::warn!("Sentinel: ALERT - CRITICAL MEMORY ({:.1}%)", mem_percent);
            let _ = state.event_tx.send(
                WsEvent::SystemAlert {
                    level: "warning".to_string(),
                    message: format!("Critical memory: {:.1}% used", mem_percent),
                }
                .to_json(),
            );
        }
    }
}
