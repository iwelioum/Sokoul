use lazy_static::lazy_static;
use prometheus::{
    Counter, CounterVec, HistogramOpts, HistogramVec, IntGauge, IntGaugeVec, Opts, Registry,
};
use std::sync::Arc;

lazy_static! {
    pub static ref REGISTRY: Arc<Registry> = Arc::new(Registry::new());

    // API Metrics
    pub static ref API_REQUESTS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_api_requests_total", "Total HTTP requests by endpoint"),
        &["endpoint", "method", "status"]
    ).expect("Failed to create API_REQUESTS_TOTAL metric");

    pub static ref API_REQUEST_DURATION_SECONDS: HistogramVec = HistogramVec::new(
        HistogramOpts::new("sokoul_api_request_duration_seconds", "HTTP request duration in seconds")
            .buckets(vec![0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]),
        &["endpoint", "method", "status"]
    ).expect("Failed to create API_REQUEST_DURATION_SECONDS metric");

    // Database Metrics
    pub static ref DB_CONNECTIONS_ACTIVE: IntGauge = IntGauge::new(
        "sokoul_db_connections_active",
        "Number of active database connections"
    ).expect("Failed to create DB_CONNECTIONS_ACTIVE metric");

    pub static ref DB_QUERY_DURATION_SECONDS: HistogramVec = HistogramVec::new(
        HistogramOpts::new("sokoul_db_query_duration_seconds", "Database query duration in seconds")
            .buckets(vec![0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0]),
        &["query_type"]
    ).expect("Failed to create DB_QUERY_DURATION_SECONDS metric");

    // Cache Metrics
    pub static ref CACHE_HITS_TOTAL: Counter = Counter::new(
        "sokoul_cache_hits_total",
        "Total number of cache hits"
    ).expect("Failed to create CACHE_HITS_TOTAL metric");

    pub static ref CACHE_MISSES_TOTAL: Counter = Counter::new(
        "sokoul_cache_misses_total",
        "Total number of cache misses"
    ).expect("Failed to create CACHE_MISSES_TOTAL metric");

    pub static ref CACHE_SIZE_BYTES: IntGauge = IntGauge::new(
        "sokoul_cache_size_bytes",
        "Current cache size in bytes"
    ).expect("Failed to create CACHE_SIZE_BYTES metric");

    // Worker Metrics
    pub static ref WORKER_JOBS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_worker_jobs_total", "Total worker jobs processed by status"),
        &["job_type", "status"]
    ).expect("Failed to create WORKER_JOBS_TOTAL metric");

    pub static ref WORKER_JOBS_DURATION_SECONDS: HistogramVec = HistogramVec::new(
        HistogramOpts::new("sokoul_worker_jobs_duration_seconds", "Worker job duration in seconds")
            .buckets(vec![0.1, 0.5, 1.0, 5.0, 10.0, 30.0, 60.0]),
        &["job_type", "status"]
    ).expect("Failed to create WORKER_JOBS_DURATION_SECONDS metric");

    pub static ref WORKER_QUEUE_SIZE: IntGaugeVec = IntGaugeVec::new(
        Opts::new("sokoul_worker_queue_size", "Size of worker job queue"),
        &["job_type"]
    ).expect("Failed to create WORKER_QUEUE_SIZE metric");

    // Search Metrics
    pub static ref SEARCH_REQUESTS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_search_requests_total", "Total search requests by provider"),
        &["provider", "media_type"]
    ).expect("Failed to create SEARCH_REQUESTS_TOTAL metric");

    pub static ref SEARCH_LATENCY_SECONDS: HistogramVec = HistogramVec::new(
        HistogramOpts::new("sokoul_search_latency_seconds", "Search provider response time in seconds")
            .buckets(vec![0.1, 0.5, 1.0, 2.0, 5.0, 10.0]),
        &["provider"]
    ).expect("Failed to create SEARCH_LATENCY_SECONDS metric");

    pub static ref SEARCH_ERRORS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_search_errors_total", "Total search errors by provider"),
        &["provider", "error_type"]
    ).expect("Failed to create SEARCH_ERRORS_TOTAL metric");

    // Download Metrics
    pub static ref DOWNLOAD_STARTED_TOTAL: Counter = Counter::new(
        "sokoul_download_started_total",
        "Total downloads started"
    ).expect("Failed to create DOWNLOAD_STARTED_TOTAL metric");

    pub static ref DOWNLOAD_COMPLETED_TOTAL: Counter = Counter::new(
        "sokoul_download_completed_total",
        "Total downloads completed successfully"
    ).expect("Failed to create DOWNLOAD_COMPLETED_TOTAL metric");

    pub static ref DOWNLOAD_FAILED_TOTAL: Counter = Counter::new(
        "sokoul_download_failed_total",
        "Total downloads failed"
    ).expect("Failed to create DOWNLOAD_FAILED_TOTAL metric");

    pub static ref DOWNLOAD_BYTES_TOTAL: Counter = Counter::new(
        "sokoul_download_bytes_total",
        "Total bytes downloaded"
    ).expect("Failed to create DOWNLOAD_BYTES_TOTAL metric");

    // Streaming Metrics
    pub static ref STREAM_SESSIONS_ACTIVE: IntGauge = IntGauge::new(
        "sokoul_stream_sessions_active",
        "Number of active streaming sessions"
    ).expect("Failed to create STREAM_SESSIONS_ACTIVE metric");

    pub static ref STREAM_SESSIONS_TOTAL: Counter = Counter::new(
        "sokoul_stream_sessions_total",
        "Total streaming sessions initiated"
    ).expect("Failed to create STREAM_SESSIONS_TOTAL metric");

    // Media Metrics
    pub static ref MEDIA_TOTAL: IntGauge = IntGauge::new(
        "sokoul_media_total",
        "Total number of media items in database"
    ).expect("Failed to create MEDIA_TOTAL metric");

    pub static ref MEDIA_STORAGE_BYTES: IntGauge = IntGauge::new(
        "sokoul_media_storage_bytes",
        "Total storage used by media files in bytes"
    ).expect("Failed to create MEDIA_STORAGE_BYTES metric");

    // Authentication Metrics
    pub static ref AUTH_ATTEMPTS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_auth_attempts_total", "Total authentication attempts by status"),
        &["status"]
    ).expect("Failed to create AUTH_ATTEMPTS_TOTAL metric");

    pub static ref AUTH_ERRORS_TOTAL: Counter = Counter::new(
        "sokoul_auth_errors_total",
        "Total authentication errors"
    ).expect("Failed to create AUTH_ERRORS_TOTAL metric");

    // External API Metrics
    pub static ref EXTERNAL_API_REQUESTS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_external_api_requests_total", "Total requests to external APIs by provider"),
        &["provider", "status"]
    ).expect("Failed to create EXTERNAL_API_REQUESTS_TOTAL metric");

    pub static ref EXTERNAL_API_LATENCY_SECONDS: HistogramVec = HistogramVec::new(
        HistogramOpts::new("sokoul_external_api_latency_seconds", "External API response time in seconds")
            .buckets(vec![0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]),
        &["provider"]
    ).expect("Failed to create EXTERNAL_API_LATENCY_SECONDS metric");

    // Error Metrics
    pub static ref ERRORS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_errors_total", "Total errors by type"),
        &["error_type", "component"]
    ).expect("Failed to create ERRORS_TOTAL metric");

    // Business Logic Metrics
    pub static ref RECOMMENDATIONS_GENERATED_TOTAL: Counter = Counter::new(
        "sokoul_recommendations_generated_total",
        "Total recommendations generated"
    ).expect("Failed to create RECOMMENDATIONS_GENERATED_TOTAL metric");

    pub static ref NOTIFICATIONS_SENT_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_notifications_sent_total", "Total notifications sent by channel"),
        &["channel", "status"]
    ).expect("Failed to create NOTIFICATIONS_SENT_TOTAL metric");

    // Scheduler Metrics
    pub static ref SCHEDULER_JOBS_TOTAL: CounterVec = CounterVec::new(
        Opts::new("sokoul_scheduler_jobs_total", "Total scheduled jobs executed by status"),
        &["job_name", "status"]
    ).expect("Failed to create SCHEDULER_JOBS_TOTAL metric");

    pub static ref SCHEDULER_JOBS_DURATION_SECONDS: HistogramVec = HistogramVec::new(
        HistogramOpts::new("sokoul_scheduler_jobs_duration_seconds", "Scheduled job duration in seconds")
            .buckets(vec![1.0, 5.0, 10.0, 30.0, 60.0, 300.0]),
        &["job_name"]
    ).expect("Failed to create SCHEDULER_JOBS_DURATION_SECONDS metric");
}

/// Initialize all metrics by registering them with the registry
pub fn init() {
    let registry = &*REGISTRY;

    // Register all metrics
    registry.register(Box::new(API_REQUESTS_TOTAL.clone())).ok();
    registry
        .register(Box::new(API_REQUEST_DURATION_SECONDS.clone()))
        .ok();
    registry
        .register(Box::new(DB_CONNECTIONS_ACTIVE.clone()))
        .ok();
    registry
        .register(Box::new(DB_QUERY_DURATION_SECONDS.clone()))
        .ok();
    registry.register(Box::new(CACHE_HITS_TOTAL.clone())).ok();
    registry.register(Box::new(CACHE_MISSES_TOTAL.clone())).ok();
    registry.register(Box::new(CACHE_SIZE_BYTES.clone())).ok();
    registry.register(Box::new(WORKER_JOBS_TOTAL.clone())).ok();
    registry
        .register(Box::new(WORKER_JOBS_DURATION_SECONDS.clone()))
        .ok();
    registry.register(Box::new(WORKER_QUEUE_SIZE.clone())).ok();
    registry
        .register(Box::new(SEARCH_REQUESTS_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(SEARCH_LATENCY_SECONDS.clone()))
        .ok();
    registry
        .register(Box::new(SEARCH_ERRORS_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(DOWNLOAD_STARTED_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(DOWNLOAD_COMPLETED_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(DOWNLOAD_FAILED_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(DOWNLOAD_BYTES_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(STREAM_SESSIONS_ACTIVE.clone()))
        .ok();
    registry
        .register(Box::new(STREAM_SESSIONS_TOTAL.clone()))
        .ok();
    registry.register(Box::new(MEDIA_TOTAL.clone())).ok();
    registry
        .register(Box::new(MEDIA_STORAGE_BYTES.clone()))
        .ok();
    registry
        .register(Box::new(AUTH_ATTEMPTS_TOTAL.clone()))
        .ok();
    registry.register(Box::new(AUTH_ERRORS_TOTAL.clone())).ok();
    registry
        .register(Box::new(EXTERNAL_API_REQUESTS_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(EXTERNAL_API_LATENCY_SECONDS.clone()))
        .ok();
    registry.register(Box::new(ERRORS_TOTAL.clone())).ok();
    registry
        .register(Box::new(RECOMMENDATIONS_GENERATED_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(NOTIFICATIONS_SENT_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(SCHEDULER_JOBS_TOTAL.clone()))
        .ok();
    registry
        .register(Box::new(SCHEDULER_JOBS_DURATION_SECONDS.clone()))
        .ok();

    tracing::info!("✅ Metrics initialized and registered with Prometheus");
}

/// Export metrics as Prometheus text format
pub fn gather_metrics() -> Result<String, Box<dyn std::error::Error>> {
    use prometheus::{Encoder, TextEncoder};

    let encoder = TextEncoder::new();
    let metric_families = REGISTRY.gather();
    let mut buffer = vec![];
    encoder.encode(&metric_families, &mut buffer)?;
    Ok(String::from_utf8(buffer)?)
}
