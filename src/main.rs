mod api;
#[cfg(test)]
mod auth_flow_tests;
mod cache;
#[cfg(test)]
mod chaos_engineering_tests;
#[cfg(test)]
mod client_tests;
mod clients;
mod config;
#[cfg(test)]
mod config_tests;
mod db;
#[cfg(test)]
mod distributed_tracing_tests;
mod events;
mod extractors;
#[cfg(test)]
mod github_actions_tests;
#[cfg(test)]
mod health_checks_tests;
#[cfg(test)]
mod input_sanitization_tests;
#[cfg(test)]
mod integration_tests_level1;
#[cfg(test)]
mod load_testing_tests;
#[cfg(test)]
mod message_contract_tests;
mod metrics;
#[cfg(test)]
mod metrics_tests;
mod middleware;
mod models;
mod notifications;
use notifications::EmailService;
#[cfg(test)]
mod nats_integration_tests;
#[cfg(test)]
mod performance_concurrency_tests;
#[cfg(test)]
mod precommit_hooks_tests;
#[cfg(test)]
mod prometheus_metrics_tests;
mod providers;
#[cfg(test)]
mod rate_limiting_tests;
#[cfg(test)]
mod release_automation_tests;
mod scheduler;
#[cfg(test)]
mod secrets_audit_tests;
mod security;
#[cfg(test)]
mod security_robustness_tests;
mod telegram;
#[cfg(test)]
mod tests;
mod utils;
mod workers;
#[cfg(test)]
mod workers_idempotence_tests;

use axum::{
    middleware as axum_middleware,
    routing::{delete, get, post},
    Router,
};
use axum_prometheus::PrometheusMetricLayer;
use config::CONFIG;
use playwright::Playwright;
use sqlx::postgres::PgPoolOptions;
use std::{net::SocketAddr, sync::Arc, time::Duration};
use tokio::net::TcpListener;
use tokio::signal;
use tokio::sync::broadcast;
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
    pub fanart_client: Option<clients::fanart::FanartClient>,
    pub omdb_client: Option<clients::omdb::OmdbClient>,
    pub thetvdb_client: Option<clients::thetvdb::ThetvdbClient>,
    pub tvmaze_client: clients::tvmaze::TvMazeClient,
    pub jikan_client: clients::jikan::JikanClient,
    pub trakt_client: Option<clients::trakt::TraktClient>,
    pub tastedive_client: Option<clients::tastedive::TasteDiveClient>,
    pub watchmode_client: Option<clients::watchmode::WatchmodeClient>,
    pub imdbbot_client: clients::imdbbot::ImdbBotClient,
    pub simkl_client: Option<clients::simkl::SimklClient>,
    pub unogs_client: Option<clients::unogs::UnogsClient>,
    pub stream_client: clients::stream::StreamClient,
    pub virustotal_client: Option<clients::virustotal::VirusTotalClient>,
    pub email_service: EmailService,
}

async fn ensure_critical_schema(pool: &sqlx::PgPool) -> anyhow::Result<()> {
    sqlx::query(r#"CREATE EXTENSION IF NOT EXISTS "pgcrypto""#)
        .execute(pool)
        .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS users (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            username TEXT NOT NULL UNIQUE,
            email TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT 'user',
            avatar_url TEXT,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        "#,
    )
    .execute(pool)
    .await?;

    // System user for API key fallback (no JWT).
    sqlx::query(
        r#"
        INSERT INTO users (id, username, email, password_hash, role, is_active)
        VALUES (
            '00000000-0000-0000-0000-000000000001',
            'system-api',
            'system-api@sokoul.local',
            'api-key-fallback-user',
            'user',
            TRUE
        )
        ON CONFLICT DO NOTHING
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS media (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            media_type TEXT NOT NULL,
            title TEXT NOT NULL,
            original_title TEXT,
            year INTEGER,
            tmdb_id INTEGER,
            imdb_id TEXT,
            overview TEXT,
            poster_url TEXT,
            backdrop_url TEXT,
            genres TEXT[],
            rating DECIMAL(3,1),
            runtime_minutes INTEGER,
            status TEXT DEFAULT 'unknown',
            parent_id UUID REFERENCES media(id) ON DELETE CASCADE,
            season_number INTEGER,
            episode_number INTEGER,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            UNIQUE (tmdb_id, media_type)
        )
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS watch_history (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            watched_at TIMESTAMPTZ DEFAULT NOW(),
            progress_seconds INTEGER DEFAULT 0,
            total_seconds INTEGER DEFAULT 0,
            completed BOOLEAN DEFAULT FALSE,
            UNIQUE (media_id, user_id)
        )
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS favorites (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
            added_at TIMESTAMPTZ DEFAULT NOW(),
            UNIQUE (user_id, media_id)
        )
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS watchlist (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
            auto_download BOOLEAN DEFAULT FALSE,
            quality_min TEXT DEFAULT '1080p',
            added_at TIMESTAMPTZ DEFAULT NOW(),
            UNIQUE (user_id, media_id)
        )
        "#,
    )
    .execute(pool)
    .await?;

    // TV channels table
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS tv_channels (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name TEXT NOT NULL,
            code TEXT NOT NULL UNIQUE,
            country TEXT,
            logo_url TEXT,
            category TEXT,
            is_free BOOLEAN DEFAULT TRUE,
            is_active BOOLEAN DEFAULT TRUE,
            stream_url TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        "#,
    )
    .execute(pool)
    .await?;

    // TV programs table (EPG)
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS tv_programs (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            channel_id UUID NOT NULL REFERENCES tv_channels(id) ON DELETE CASCADE,
            title TEXT NOT NULL,
            description TEXT,
            start_time TIMESTAMPTZ NOT NULL,
            end_time TIMESTAMPTZ NOT NULL,
            genre TEXT,
            image_url TEXT,
            rating DECIMAL(3,1),
            external_id TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        "#,
    )
    .execute(pool)
    .await?;

    // Collections table

    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS collections (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            name TEXT NOT NULL,
            description TEXT,
            is_public BOOLEAN DEFAULT FALSE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        "#,
    )
    .execute(pool)
    .await?;

    // Collection items
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS collection_items (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            collection_id UUID NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
            media_id UUID NOT NULL REFERENCES media(id) ON DELETE CASCADE,
            added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            UNIQUE (collection_id, media_id)
        )
        "#,
    )
    .execute(pool)
    .await?;

    sqlx::query("CREATE INDEX IF NOT EXISTS idx_watch_history_user ON watch_history(user_id)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_watch_history_media ON watch_history(media_id)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_watchlist_user ON watchlist(user_id)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_tv_channels_country ON tv_channels(country)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_tv_channels_code ON tv_channels(code)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_tv_programs_channel ON tv_programs(channel_id)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_tv_programs_start_time ON tv_programs(start_time)")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_collection_items_collection ON collection_items(collection_id)")
        .execute(pool)
        .await?;

    // Fail-fast verification: ensure expected columns exist.
    sqlx::query(
        "SELECT media_id, user_id, progress_seconds, completed, watched_at FROM watch_history LIMIT 1",
    )
    .fetch_optional(pool)
    .await?;
    sqlx::query("SELECT media_id, user_id, added_at FROM favorites LIMIT 1")
        .fetch_optional(pool)
        .await?;
    sqlx::query("SELECT media_id, user_id, added_at FROM watchlist LIMIT 1")
        .fetch_optional(pool)
        .await?;

    tracing::info!("✅ Critical schema verified (library/watchlist/watch_history)");
    Ok(())
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

    tracing::info!("Shutdown signal received, starting graceful shutdown");
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

    tracing::info!("Starting SOKOUL v3...");

    config::init();

    metrics::init();
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
                    tracing::info!("✅ Database connection established");
                    pool = Some(p);
                }
                Err(e) => {
                    last_error = Some(e);
                    retries -= 1;
                    if retries > 0 {
                        tracing::warn!(
                            "❌ DB connection failed, retrying ({} remaining)...",
                            retries
                        );
                        tokio::time::sleep(Duration::from_secs(2)).await;
                    }
                }
            }
        }

        pool.unwrap_or_else(|| {
            panic!(
                "Failed to connect to database after 10 attempts: {:?}",
                last_error
            )
        })
    };

    // Run migrations (toggle via RUN_MIGRATIONS env var; default: disabled to avoid migration mismatches)
    let run_migrations =
        std::env::var("RUN_MIGRATIONS").unwrap_or_else(|_| "false".to_string()) == "true";
    if run_migrations {
        tracing::info!("Running SQL migrations...");
        match sqlx::migrate!("./migrations").run(&db_pool).await {
            Ok(_) => {
                tracing::info!("✅ SQL migrations completed successfully");
            }
            Err(e) => {
                tracing::warn!("⚠️ SQL migrations failed (non-blocking): {}", e);
                tracing::warn!("Server will start anyway, but tables may not exist");
            }
        }
    } else {
        tracing::info!("RUN_MIGRATIONS not set to 'true' => skipping SQL migrations to avoid known mismatches.");
    }

    ensure_critical_schema(&db_pool).await?;

    let redis_client = redis::Client::open(CONFIG.redis_url.as_str()).expect("Invalid Redis URL");

    let nats_client = async_nats::connect(&CONFIG.nats_url)
        .await
        .expect("Failed to connect to NATS");
    let jetstream_context = async_nats::jetstream::new(nats_client);

    // Broadcast channel for WebSocket events
    let (event_tx, _rx) = broadcast::channel(100);

    // TMDB client
    let tmdb_client = clients::tmdb::TmdbClient::new(CONFIG.tmdb_api_key.clone());

    // FlareSolverr client (optional)
    let flaresolverr_client = if !CONFIG.flaresolverr_url.is_empty() {
        tracing::info!(
            "Initializing FlareSolverr client at: {}",
            CONFIG.flaresolverr_url
        );
        Some(clients::flaresolverr::FlareSolverrClient::new(
            CONFIG.flaresolverr_url.clone(),
        ))
    } else {
        tracing::warn!(
            "FlareSolverr not configured. Cloudflare-protected scraping requests may fail."
        );
        None
    };

    // Fanart.tv client (optional)
    let fanart_client = if !CONFIG.fanart_api_key.is_empty() {
        tracing::info!("✅ Fanart.tv client initialized");
        let ck = if CONFIG.fanart_client_key.is_empty() {
            None
        } else {
            Some(CONFIG.fanart_client_key.clone())
        };
        Some(clients::fanart::FanartClient::new(
            CONFIG.fanart_api_key.clone(),
            ck,
        ))
    } else {
        tracing::warn!("Fanart.tv not configured (FANART_API_KEY missing).");
        None
    };

    // OMDb client (optional)
    let omdb_client = if !CONFIG.omdb_api_key.is_empty() {
        tracing::info!("✅ OMDb client initialized");
        Some(clients::omdb::OmdbClient::new(CONFIG.omdb_api_key.clone()))
    } else {
        tracing::warn!("OMDb not configured (OMDB_API_KEY missing).");
        None
    };

    // TheTVDB client (optional)
    let thetvdb_client = if !CONFIG.thetvdb_api_key.is_empty() {
        tracing::info!("✅ TheTVDB client initialized");
        Some(clients::thetvdb::ThetvdbClient::new(
            CONFIG.thetvdb_api_key.clone(),
            CONFIG.thetvdb_pin.clone(),
        ))
    } else {
        tracing::warn!("TheTVDB not configured (THETVDB_API_KEY missing).");
        None
    };

    // TVMaze client (free, no auth)
    let tvmaze_client = clients::tvmaze::TvMazeClient::new();
    tracing::info!("✅ TVMaze client initialized");

    // Jikan/MyAnimeList client (free, no auth)
    let jikan_client = clients::jikan::JikanClient::new();
    tracing::info!("✅ Jikan/MAL client initialized");

    // Trakt client (optional)
    let trakt_client = if !CONFIG.trakt_client_id.is_empty() {
        tracing::info!("✅ Trakt client initialized");
        Some(clients::trakt::TraktClient::new(
            CONFIG.trakt_client_id.clone(),
        ))
    } else {
        tracing::warn!("Trakt not configured (TRAKT_CLIENT_ID missing).");
        None
    };

    // TasteDive client (optional)
    let tastedive_client = if !CONFIG.tastedive_api_key.is_empty() {
        tracing::info!("✅ TasteDive client initialized");
        Some(clients::tastedive::TasteDiveClient::new(
            CONFIG.tastedive_api_key.clone(),
        ))
    } else {
        tracing::warn!("TasteDive not configured (TASTEDIVE_API_KEY missing).");
        None
    };

    // Watchmode client (optional)
    let watchmode_client = if !CONFIG.watchmode_api_key.is_empty() {
        tracing::info!("✅ Watchmode client initialized");
        Some(clients::watchmode::WatchmodeClient::new(
            CONFIG.watchmode_api_key.clone(),
        ))
    } else {
        tracing::warn!("Watchmode not configured (WATCHMODE_API_KEY missing).");
        None
    };

    // IMDbOT client (free, no auth)
    let imdbbot_client = clients::imdbbot::ImdbBotClient::new(CONFIG.imdbbot_base_url.clone());
    tracing::info!("✅ IMDbOT client initialized");

    // Simkl client (optional)
    let simkl_client = if !CONFIG.simkl_api_key.is_empty() {
        tracing::info!("✅ Simkl client initialized");
        Some(clients::simkl::SimklClient::new(
            CONFIG.simkl_api_key.clone(),
        ))
    } else {
        tracing::warn!("Simkl not configured (SIMKL_API_KEY missing).");
        None
    };

    // uNoGS client (optional, Netflix)
    let unogs_client = if !CONFIG.unogs_api_key.is_empty() {
        tracing::info!("✅ uNoGS client initialized");
        Some(clients::unogs::UnogsClient::new(
            CONFIG.unogs_api_key.clone(),
        ))
    } else {
        tracing::warn!("uNoGS not configured (UNOGS_API_KEY missing).");
        None
    };

    // Stream client (free, no auth)
    let stream_client = clients::stream::StreamClient::new(CONFIG.stream_base_url.clone());
    tracing::info!("✅ Stream client initialized");

    // Playwright browser (optional, for scraping)
    let browser = if CONFIG.streaming_enabled {
        tracing::info!("Initializing Playwright for scraping...");
        match Playwright::initialize().await {
            Ok(playwright) => {
                if let Err(e) = playwright.install_chromium() {
                    tracing::warn!(
                        "Playwright install_chromium: {} (may already be installed)",
                        e
                    );
                }
                let chromium = playwright.chromium();
                match chromium
                    .launcher()
                    .headless(CONFIG.streaming_headless)
                    .launch()
                    .await
                {
                    Ok(browser) => Some(Arc::new(browser)),
                    Err(e) => {
                        tracing::error!("Failed to launch Chromium: {}. Streaming disabled.", e);
                        None
                    }
                }
            }
            Err(e) => {
                tracing::error!(
                    "Failed to initialize Playwright: {}. Streaming disabled.",
                    e
                );
                None
            }
        }
    } else {
        None
    };

    // VirusTotal client (optional, for malware detection)
    let virustotal_client = if !CONFIG.virustotal_api_key.is_empty() {
        tracing::info!("✅ VirusTotal client initialized");
        Some(clients::virustotal::VirusTotalClient::new(
            CONFIG.virustotal_api_key.clone(),
        ))
    } else {
        tracing::warn!("VirusTotal not configured (VIRUSTOTAL_API_KEY missing). URL security checks will be limited.");
        None
    };

    let email_service = EmailService::from_env();
    if email_service.enabled {
        tracing::info!("✅ Email service initialized");
    } else {
        tracing::warn!("Email service disabled (SMTP_ENABLED missing or false)");
    }

    let state = Arc::new(AppState {
        db_pool,
        redis_client,
        jetstream_context,
        event_tx,
        browser,
        tmdb_client,
        flaresolverr_client,
        fanart_client,
        omdb_client,
        thetvdb_client,
        tvmaze_client,
        jikan_client,
        trakt_client,
        tastedive_client,
        watchmode_client,
        imdbbot_client,
        simkl_client,
        unogs_client,
        stream_client,
        virustotal_client,
        email_service,
    });

    // Start workers
    let worker_state = state.clone();
    tokio::spawn(async move {
        workers::run_workers(worker_state).await;
    });

    // Start Telegram bot (optional)
    if CONFIG.telegram_enabled {
        let bot_state = state.clone();
        tokio::spawn(async move {
            telegram::bot::run_bot(bot_state).await;
        });
    }

    // Start scheduler
    let scheduler_state = state.clone();
    tokio::spawn(async move {
        scheduler::run_scheduler(scheduler_state).await;
    });

    // Prometheus metrics layer
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
        .route(
            "/media",
            post(api::media::create_media_handler).get(api::media::list_media_handler),
        )
        .route(
            "/media/:id",
            get(api::media::get_media_handler)
                .put(api::media::update_media_handler)
                .delete(api::media::delete_media_handler),
        )
        .route(
            "/media/:id/files",
            get(api::downloads::list_media_files_handler),
        )
        .route(
            "/media/:id/results",
            get(api::search::get_search_results_handler),
        )
        .route("/media/:id/episodes", get(api::media::get_episodes_handler))
        .route(
            "/media/:id/recommendations",
            get(api::recommendations::get_recommendations_handler),
        )
        .route(
            "/media/:id/stream",
            get(api::streaming::get_stream_links_handler),
        )
        // Search & Downloads
        .route("/search", post(api::search::trigger_search_handler))
        .route("/search/direct", post(api::search::direct_search_handler))
        .route(
            "/search/:media_id",
            get(api::search::get_search_results_handler),
        )
        .route(
            "/downloads",
            post(api::downloads::start_download_handler)
                .get(api::downloads::list_downloads_handler),
        )
        // Tasks
        .route(
            "/tasks",
            post(api::tasks::create_task_handler).get(api::tasks::list_tasks_handler),
        )
        .route("/tasks/:id", get(api::tasks::get_task_handler))
        // Storage
        .route("/storage", get(api::storage::get_storage_handler))
        // File streaming
        .route(
            "/files/:file_id/stream",
            get(api::files::stream_file_handler),
        )
        .route("/files/:file_id/info", get(api::files::file_info_handler))
        // Library (favorites)
        .route(
            "/library",
            post(api::library::add_to_library_handler).get(api::library::list_library_handler),
        )
        .route(
            "/library/:media_id",
            delete(api::library::remove_from_library_handler),
        )
        .route(
            "/library/:tmdb_id/:media_type",
            delete(api::library::remove_from_library_by_tmdb_handler),
        )
        .route(
            "/library/status/:tmdb_id/:media_type",
            get(api::library::library_status_handler),
        )
        // Watchlist
        .route(
            "/watchlist",
            post(api::watchlist::add_to_watchlist_handler)
                .get(api::watchlist::list_watchlist_handler),
        )
        .route(
            "/watchlist/:media_id",
            delete(api::watchlist::remove_from_watchlist_handler),
        )
        .route(
            "/watchlist/:tmdb_id/:media_type",
            delete(api::watchlist::remove_from_watchlist_by_tmdb_handler),
        )
        // Watch History
        .route(
            "/watch-history",
            post(api::watch_history::update_watch_progress_handler),
        )
        .route(
            "/watch-history/continue",
            get(api::watch_history::continue_watching_handler),
        )
        .layer(axum_middleware::from_fn(api::auth::api_key_middleware));

    // Public metadata routes (no auth required)
    let public_metadata_routes = Router::new()
        .merge(api::tmdb::tmdb_routes())
        .merge(api::enrichment::enrichment_routes())
        .route(
            "/streaming/direct/:media_type/:tmdb_id",
            get(api::streaming::direct_stream_handler),
        )
        .route(
            "/streaming/extract/:media_type/:tmdb_id",
            get(api::streaming::extract_streams_handler),
        )
        .route(
            "/streaming/proxy",
            get(api::streaming::stream_proxy_handler),
        )
        .route(
            "/streaming/subtitles/:media_type/:tmdb_id",
            get(api::streaming::get_subtitles_handler),
        )
        .route(
            "/streaming/subtitles/vtt",
            get(api::streaming::serve_subtitle_vtt_handler),
        );

    // Collections routes (nested under /api/collections)
    let collections_routes = Router::new()
        .nest("/", api::collections::collections_routes())
        .layer(axum_middleware::from_fn(api::auth::api_key_middleware));

    // TV routes (nested under /api/tv)
    let tv_routes = Router::new()
        .nest("/", api::tv::tv_routes())
        .layer(axum_middleware::from_fn(api::auth::api_key_middleware));

    // Security routes (nested under /api/security)
    let security_routes = Router::new()
        .nest("/", api::security::security_routes())
        .layer(axum_middleware::from_fn(api::auth::api_key_middleware));

    // Public routes (no auth needed)
    let public_routes = Router::new()
        .route("/health", get(api::health::health_check_handler))
        .route("/metrics", get(api::metrics::metrics_handler))
        .route("/ws", get(api::ws::ws_handler))
        .merge(api::auth::auth_routes());

    let app = Router::new()
        .merge(public_routes)
        .merge(public_metadata_routes)
        .nest("/api/collections", collections_routes)
        .nest("/api/tv", tv_routes)
        .nest("/api/security", security_routes)
        .merge(protected_routes)
        .layer(axum_middleware::from_fn(middleware::track_metrics))
        .layer(cors)
        .layer(ConcurrencyLimitLayer::new(CONFIG.rate_limit_rps as usize))
        .layer(prometheus_layer)
        .with_state(state);

    // Start HTTP server
    let addr: SocketAddr = CONFIG.server_address.parse()?;
    tracing::info!("API server listening on {}", addr);

    if !CONFIG.api_key.is_empty() {
        tracing::info!("API key authentication enabled.");
    } else {
        tracing::warn!("WARNING: No SOKOUL_API_KEY configured. API is open.");
    }

    let listener = TcpListener::bind(addr).await?;
    axum::serve(listener, app.into_make_service())
        .with_graceful_shutdown(shutdown_signal())
        .await?;

    Ok(())
}
