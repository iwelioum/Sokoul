use crate::{db, AppState};
use std::sync::Arc;
use tokio::time::{self, Duration};

pub async fn run_scheduler(state: Arc<AppState>) {
    tracing::info!("Scheduler starting...");
    let mut interval = time::interval(Duration::from_secs(3600));

    loop {
        interval.tick().await;
        tracing::info!("Scheduler: running scheduled tasks...");

        match db::search_results::delete_expired(&state.db_pool).await {
            Ok(deleted_count) => {
                if deleted_count > 0 {
                    tracing::info!(
                        "Scheduler: cleaned up {} expired search results",
                        deleted_count
                    );
                }
            }
            Err(e) => {
                tracing::error!("Scheduler: failed to clean expired search results: {}", e);
            }
        }
    }
}
