use crate::AppState;
use std::sync::Arc;

pub mod hunter;
pub mod metrics;
pub mod oracle;
pub mod scout;
pub mod sentinel;

/// Point d'entree pour lancer tous les workers en parallele.
pub async fn run_workers(state: Arc<AppState>) {
    tracing::info!("Lancement de l'ensemble des workers...");
    let workers = vec![
        tokio::spawn(scout::scout_worker(state.clone())),
        tokio::spawn(hunter::hunter_worker(state.clone())),
        tokio::spawn(oracle::oracle_worker(state.clone())),
        tokio::spawn(sentinel::sentinel_worker(state.clone())),
    ];

    for worker in workers {
        if let Err(e) = worker.await {
            tracing::error!("Un worker a panique: {:?}", e);
        }
    }
}
