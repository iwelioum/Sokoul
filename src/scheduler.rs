use crate::{db, AppState};
use std::sync::Arc;
use tokio::time::{self, Duration};

pub async fn run_scheduler(state: Arc<AppState>) {
    tracing::info!("Le planificateur de tâches (Scheduler) démarre...");
    // Exécute toutes les heures
    let mut interval = time::interval(Duration::from_secs(3600));

    loop {
        interval.tick().await;
        tracing::info!("Scheduler: Exécution des tâches planifiées...");

        // Tâche 1: Nettoyer les anciens résultats de recherche
        match db::search_results::delete_expired(&state.db_pool).await {
            Ok(deleted_count) => {
                if deleted_count > 0 {
                    tracing::info!(
                        "Scheduler: {} anciens résultats de recherche ont été nettoyés.",
                        deleted_count
                    );
                }
            }
            Err(e) => {
                tracing::error!(
                    "Scheduler: Erreur lors du nettoyage des résultats de recherche expirés: {}",
                    e
                );
            }
        }
    }
}
