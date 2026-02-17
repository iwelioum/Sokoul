use crate::AppState;
use std::sync::Arc;

pub mod hunter;
pub mod metrics;
pub mod oracle;
pub mod scout;
pub mod sentinel;

/// Entry point to launch all workers in parallel.
pub async fn run_workers(state: Arc<AppState>) {
    tracing::info!("Starting all workers...");
    let workers = vec![
        tokio::spawn(scout::scout_worker(state.clone())),
        tokio::spawn(hunter::hunter_worker(state.clone())),
        tokio::spawn(oracle::oracle_worker(state.clone())),
        tokio::spawn(sentinel::sentinel_worker(state.clone())),
    ];

    for worker in workers {
        if let Err(e) = worker.await {
            tracing::error!("A worker panicked: {:?}", e);
        }
    }
}
