use std::sync::Arc;
use std::sync::atomic::{AtomicU64, AtomicU32, Ordering};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;

/// Worker status and metrics
#[allow(dead_code)]
pub struct WorkerMetrics {
    pub name: String,
    pub status: WorkerStatus,
    pub last_heartbeat: DateTime<Utc>,
    pub processed_messages: u64,
    pub errors_last_5m: u32,
    pub lag_messages: u32,     // NATS consumer lag
    pub uptime_seconds: u64,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
#[allow(dead_code)]
pub enum WorkerStatus {
    #[serde(rename = "healthy")]
    Healthy,
    #[serde(rename = "idle")]
    Idle,
    #[serde(rename = "degraded")]
    Degraded,
    #[serde(rename = "down")]
    Down,
}

/// Track metrics for a single worker
#[allow(dead_code)]
pub struct WorkerMetricsCollector {
    pub name: String,
    processed_messages: Arc<AtomicU64>,
    errors_5m: Arc<AtomicU32>,
    last_heartbeat: Arc<RwLock<DateTime<Utc>>>,
    started_at: DateTime<Utc>,
}

#[allow(dead_code)]
impl WorkerMetricsCollector {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            processed_messages: Arc::new(AtomicU64::new(0)),
            errors_5m: Arc::new(AtomicU32::new(0)),
            last_heartbeat: Arc::new(RwLock::new(Utc::now())),
            started_at: Utc::now(),
        }
    }

    pub async fn heartbeat(&self) {
        *self.last_heartbeat.write().await = Utc::now();
    }

    pub fn record_message_processed(&self) {
        self.processed_messages.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_error(&self) {
        self.errors_5m.fetch_add(1, Ordering::Relaxed);
    }

    pub async fn get_metrics(&self, lag: u32) -> WorkerMetrics {
        let last_hb = *self.last_heartbeat.read().await;
        let now = Utc::now();
        let time_since_hb = (now - last_hb).num_seconds() as u64;
        
        // If no heartbeat in 2 minutes, mark as down
        let status = if time_since_hb > 120 {
            WorkerStatus::Down
        } else if self.errors_5m.load(Ordering::Relaxed) > 10 {
            WorkerStatus::Degraded
        } else if time_since_hb > 60 {
            WorkerStatus::Idle
        } else {
            WorkerStatus::Healthy
        };

        WorkerMetrics {
            name: self.name.clone(),
            status,
            last_heartbeat: last_hb,
            processed_messages: self.processed_messages.load(Ordering::Relaxed),
            errors_last_5m: self.errors_5m.load(Ordering::Relaxed),
            lag_messages: lag,
            uptime_seconds: (now - self.started_at).num_seconds() as u64,
        }
    }

    /// Reset error counter (call periodically, e.g. every 5 minutes)
    pub fn reset_error_counter(&self) {
        self.errors_5m.store(0, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_worker_metrics_creation() {
        let collector = WorkerMetricsCollector::new("scout");
        assert_eq!(collector.name, "scout");
        
        let metrics = collector.get_metrics(0).await;
        assert_eq!(metrics.status, WorkerStatus::Healthy);
        assert_eq!(metrics.processed_messages, 0);
    }

    #[tokio::test]
    async fn test_worker_metrics_error_tracking() {
        let collector = WorkerMetricsCollector::new("hunter");
        
        for _ in 0..15 {
            collector.record_error();
        }
        
        let metrics = collector.get_metrics(0).await;
        assert_eq!(metrics.status, WorkerStatus::Degraded);
        assert_eq!(metrics.errors_last_5m, 15);
    }
}
