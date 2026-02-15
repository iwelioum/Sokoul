mod api;
mod cache;
mod clients;
mod config;
mod db;
mod events;
mod models;
mod providers;
mod telegram;
mod scheduler;
mod utils;
mod workers;
#[cfg(test)]
mod tests;
#[cfg(test)]
mod config_tests;
#[cfg(test)]
mod client_tests;
#[cfg(test)]
mod security_robustness_tests;
#[cfg(test)]
mod performance_concurrency_tests;
#[cfg(test)]
mod integration_tests_level1;
#[cfg(test)]
mod nats_integration_tests;
#[cfg(test)]
mod workers_idempotence_tests;
#[cfg(test)]
mod message_contract_tests;
#[cfg(test)]
mod auth_flow_tests;
#[cfg(test)]
mod input_sanitization_tests;
#[cfg(test)]
mod rate_limiting_tests;
#[cfg(test)]
mod secrets_audit_tests;
#[cfg(test)]
mod load_testing_tests;
#[cfg(test)]
mod chaos_engineering_tests;
#[cfg(test)]
mod prometheus_metrics_tests;
#[cfg(test)]
mod distributed_tracing_tests;
#[cfg(test)]
mod health_checks_tests;
#[cfg(test)]
mod precommit_hooks_tests;
#[cfg(test)]
mod github_actions_tests;
#[cfg(test)]
mod release_automation_tests;

use axum::{
    middleware,
    routing::{get, post, delete},
    Router,
};
use axum_prometheus::PrometheusMetricLayer;
use playwright::Playwright;
use config::CONFIG;
use sqlx::postgres::PgPoolOptions;
use std::{net::SocketAddr, sync::Arc, time::Duration};
use tokio::net::TcpListener;
use tokio::sync::broadcast;
use tokio::signal;
use tower::limit::ConcurrencyLimitLayer;
use tower_http::cors::{AllowOrigin, CorsLayer};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};

#[derive(Clone)]
pub struct AppState {
    pub db_pool: sqlx::PgPool,
    pub redis_client: redis::Client,
    pub jetstream_context: async_nats::jetstream::Context,
    pub event_tx: broadcast::Sender<String>,
    pub browser: Option<Arc<playwright::api::Browser>>,
    pub tmdb_client: clients::tmdb::TmdbClient,
    pub flaresolverr_client: Option<clients::flaresolverr::FlareSolverrClient>,
}

async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("failed to install signal handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }

    tracing::info!("Signal de fermeture recu, demarrage de l'arret progressif.");
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    dotenvy::dotenv().ok();
    tracing_subscriber::registry()
        .with(tracing_subscriber::EnvFilter::new(
            std::env::var("RUST_LOG").unwrap_or_else(|_| "sokoul=debug,tower_http=debug".into()),
        ))
        .with(tracing_subscriber::fmt::layer())
        .init();

    tracing::info!("Demarrage de SOKOUL v3...");

    config::init();

    // Connexion Base de donnees avec retry
    let db_pool = {
        let mut retries = 10;
        let mut last_error = None;
        let mut pool = None;
        
        while retries > 0 && pool.is_none() {
            match PgPoolOptions::new()
                .acquire_timeout(Duration::from_secs(10))
                .max_connections(50)
                .connect(&CONFIG.database_url)
                .await
            {
                Ok(p) => {
                    tracing::info!("✅ Connexion a la base de donnees reussie");
                    pool = Some(p);
                }
                Err(e) => {
                    last_error = Some(e);
                    retries -= 1;
                    if retries > 0 {
                        tracing::warn!("❌ Connexion DB echouee, nouvelle tentative ({} restantes)...", retries);
                        tokio::time::sleep(Duration::from_secs(2)).await;
                    }
                }
            }
        }
        
        pool.unwrap_or_else(|| {
            panic!("Impossible de se connecter a la base de donnees apres 3 tentatives: {:?}", last_error)
        })
    };

    // Run migrations (toggle via RUN_MIGRATIONS env var; default: disabled to avoid migration mismatches)
    let run_migrations = std::env::var("RUN_MIGRATIONS").unwrap_or_else(|_| "false".to_string()) == "true";
    if run_migrations {
        tracing::info!("Execution des migrations SQL...");
        match sqlx::migrate!("./migrations")
            .run(&db_pool)
            .await
        {
            Ok(_) => {
                tracing::info!("✅ Migrations SQL terminees avec succes");
            }
            Err(e) => {
                tracing::warn!("⚠️ Migrations SQL echouees (non-bloquant): {}", e);
                tracing::warn!("Le serveur demarrera quand meme, mais les tables peuvent ne pas exister");
            }
        }
    } else {
        tracing::info!("RUN_MIGRATIONS not set to 'true' => skipping SQL migrations to avoid known mismatches.");
    }

    // Connexion Redis
    let redis_client = redis::Client::open(CONFIG.redis_url.as_str())
        .expect("URL Redis invalide");

    // Connexion NATS
    let nats_client = async_nats::connect(&CONFIG.nats_url).await
        .expect("Impossible de se connecter a NATS");
    let jetstream_context = async_nats::jetstream::new(nats_client);

    // Canal de broadcast pour WebSocket
    let (event_tx, _rx) = broadcast::channel(100);

    // Client TMDB
    let tmdb_client = clients::tmdb::TmdbClient::new(CONFIG.tmdb_api_key.clone());

    // Client FlareSolverr
    let flaresolverr_client = if !CONFIG.flaresolverr_url.is_empty() {
        tracing::info!("Initialisation du client FlareSolverr a l'URL: {}", CONFIG.flaresolverr_url);
        Some(clients::flaresolverr::FlareSolverrClient::new(CONFIG.flaresolverr_url.clone()))
    } else {
        tracing::warn!("FlareSolverr non configure. Les requetes de scraping protegees par Cloudflare peuvent echouer.");
        None
    };

    // Initialisation de Playwright (si active)
    let browser = if CONFIG.streaming_enabled {
        tracing::info!("Initialisation de Playwright pour le scraping...");
        match Playwright::initialize().await {
            Ok(playwright) => {
                let chromium = playwright.chromium();
                match chromium
                    .launcher()
                    .headless(CONFIG.streaming_headless)
                    .launch()
                    .await
                {
                    Ok(browser) => Some(Arc::new(browser)),
                    Err(e) => {
                        tracing::error!("Echec lancement Chromium: {}. Streaming desactive.", e);
                        None
                    }
                }
            }
            Err(e) => {
                tracing::error!("Echec initialisation Playwright: {}. Streaming desactive.", e);
                None
            }
        }
    } else {
        None
    };

    let state = Arc::new(AppState {
        db_pool,
        redis_client,
        jetstream_context,
        event_tx,
        browser,
        tmdb_client,
        flaresolverr_client,
    });

    // Demarrage des Workers
    let worker_state = state.clone();
    tokio::spawn(async move {
        workers::run_workers(worker_state).await;
    });

    // Demarrage du Bot Telegram
    if CONFIG.telegram_enabled {
        let bot_state = state.clone();
        tokio::spawn(async move {
            telegram::bot::run_bot(bot_state).await;
        });
    }

    // Demarrage du Scheduler
    let scheduler_state = state.clone();
    tokio::spawn(async move {
        scheduler::run_scheduler(scheduler_state).await;
    });

    // Metriques Prometheus
    let prometheus_layer = PrometheusMetricLayer::new();

    // CORS configuration
    let cors = if CONFIG.cors_origins.is_empty() || CONFIG.cors_origins.iter().any(|o| o == "*") {
        CorsLayer::permissive()
    } else {
        let origins: Vec<_> = CONFIG
            .cors_origins
            .iter()
            .filter_map(|o| o.parse().ok())
            .collect();
        CorsLayer::new()
            .allow_origin(AllowOrigin::list(origins))
            .allow_methods(tower_http::cors::Any)
            .allow_headers(tower_http::cors::Any)
    };

    // Protected API routes (require API key)
    let protected_routes = Router::new()
        // Media CRUD
        .route("/media", post(api::media::create_media_handler).get(api::media::list_media_handler))
        .route("/media/:id", get(api::media::get_media_handler).put(api::media::update_media_handler).delete(api::media::delete_media_handler))
        .route("/media/:id/files", get(api::downloads::list_media_files_handler))
        .route("/media/:id/results", get(api::search::get_search_results_handler))
        .route("/media/:id/episodes", get(api::media::get_episodes_handler))
        .route("/media/:id/recommendations", get(api::recommendations::get_recommendations_handler))
        .route("/media/:id/stream", get(api::streaming::get_stream_links_handler))
        // Search & Downloads
        .route("/search", post(api::search::trigger_search_handler))
        .route("/search/:media_id", get(api::search::get_search_results_handler))
        .route("/downloads", post(api::downloads::start_download_handler).get(api::downloads::list_downloads_handler))
        // Tasks
        .route("/tasks", post(api::tasks::create_task_handler).get(api::tasks::list_tasks_handler))
        .route("/tasks/:id", get(api::tasks::get_task_handler))
        // Storage
        .route("/storage", get(api::storage::get_storage_handler))
        // File streaming
        .route("/files/:file_id/stream", get(api::files::stream_file_handler))
        .route("/files/:file_id/info", get(api::files::file_info_handler))
        .merge(api::tmdb::tmdb_routes())
        // Direct streaming (no DB needed)
        .route("/streaming/direct/:media_type/:tmdb_id", get(api::streaming::direct_stream_handler))
        // Library (favorites)
        .route("/library", post(api::library::add_to_library_handler).get(api::library::list_library_handler))
        .route("/library/:media_id", delete(api::library::remove_from_library_handler))
        .route("/library/status/:media_id", get(api::library::library_status_handler))
        // Watchlist
        .route("/watchlist", post(api::watchlist::add_to_watchlist_handler).get(api::watchlist::list_watchlist_handler))
        .route("/watchlist/:media_id", delete(api::watchlist::remove_from_watchlist_handler))
        // Watch History
        .route("/watch-history", post(api::watch_history::update_watch_progress_handler))
        .route("/watch-history/continue", get(api::watch_history::continue_watching_handler))
        .layer(middleware::from_fn(api::auth::api_key_middleware));

    // Public routes (no auth needed)
    let public_routes = Router::new()
        .route("/health", get(api::health::health_check_handler))
        .route("/ws", get(api::ws::ws_handler));

    let app = Router::new()
        .merge(public_routes)
        .merge(protected_routes)
        .layer(cors)
        .layer(ConcurrencyLimitLayer::new(CONFIG.rate_limit_rps as usize))
        .layer(prometheus_layer)
        .with_state(state);

    // Demarrage du serveur HTTP (axum 0.7 API)
    let addr: SocketAddr = CONFIG.server_address.parse()?;
    tracing::info!("Serveur API ecoute sur {}", addr);

    if !CONFIG.api_key.is_empty() {
        tracing::info!("Authentification API Key activee.");
    } else {
        tracing::warn!("ATTENTION: Pas de SOKOUL_API_KEY configuree. L'API est ouverte.");
    }

    let listener = TcpListener::bind(addr).await?;
    axum::serve(listener, app.into_make_service())
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    Ok(())
}
