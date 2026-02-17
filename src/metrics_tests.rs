#[cfg(test)]
mod metrics_tests {
    use crate::metrics;

    #[test]
    fn test_metrics_initialization() {
        // Initialize metrics (may be called multiple times, should not panic)
        metrics::init();

        // Try to gather metrics - should not panic
        let result = metrics::gather_metrics();
        assert!(result.is_ok(), "Failed to gather metrics");

        let metrics_output = result.unwrap();
        assert!(!metrics_output.is_empty(), "Metrics output is empty");
        // Check for at least one metric to exist (may not all be present if gather is empty)
        assert!(
            metrics_output.contains("sokoul_"),
            "Missing Sokoul metrics in output"
        );
    }

    #[test]
    fn test_api_request_counter() {
        metrics::init();

        // Increment counter
        metrics::API_REQUESTS_TOTAL
            .with_label_values(&["/test", "GET", "200"])
            .inc();

        // Verify we can gather metrics
        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();
        assert!(
            output.contains("sokoul_api_requests_total"),
            "Counter not recorded"
        );
    }

    #[test]
    fn test_histogram_observation() {
        metrics::init();

        // Record a histogram observation
        metrics::API_REQUEST_DURATION_SECONDS
            .with_label_values(&["/health", "GET", "200"])
            .observe(0.05);

        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();
        assert!(
            output.contains("sokoul_api_request_duration_seconds"),
            "Histogram not recorded"
        );
    }

    #[test]
    fn test_worker_job_counter() {
        metrics::init();

        metrics::WORKER_JOBS_TOTAL
            .with_label_values(&["search", "success"])
            .inc();

        metrics::WORKER_JOBS_TOTAL
            .with_label_values(&["search", "failure"])
            .inc();

        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();
        assert!(
            output.contains("sokoul_worker_jobs_total"),
            "Worker jobs metric not recorded"
        );
    }

    #[test]
    fn test_gauge_set() {
        metrics::init();

        metrics::DB_CONNECTIONS_ACTIVE.set(5);

        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();
        assert!(
            output.contains("sokoul_db_connections_active"),
            "DB connections gauge not recorded"
        );
    }

    #[test]
    fn test_prometheus_text_format() {
        metrics::init();

        // Record some metrics
        metrics::API_REQUESTS_TOTAL
            .with_label_values(&["/health", "GET", "200"])
            .inc();

        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();

        // Verify Prometheus text format
        assert!(output.contains("# HELP"), "Missing HELP metadata");
        assert!(output.contains("# TYPE"), "Missing TYPE metadata");

        // Should contain the metric name
        assert!(
            output.contains("sokoul_api_requests_total{"),
            "Metric not in proper Prometheus format"
        );
    }

    #[test]
    fn test_search_metrics() {
        metrics::init();

        metrics::SEARCH_REQUESTS_TOTAL
            .with_label_values(&["tmdb", "movie"])
            .inc();

        metrics::SEARCH_LATENCY_SECONDS
            .with_label_values(&["tmdb"])
            .observe(1.5);

        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();
        assert!(
            output.contains("sokoul_search_requests_total"),
            "Search requests not recorded"
        );
        assert!(
            output.contains("sokoul_search_latency_seconds"),
            "Search latency not recorded"
        );
    }

    #[test]
    fn test_download_metrics() {
        metrics::init();

        metrics::DOWNLOAD_STARTED_TOTAL.inc();
        metrics::DOWNLOAD_COMPLETED_TOTAL.inc();
        metrics::DOWNLOAD_BYTES_TOTAL.inc_by(1_000_000.0);

        let result = metrics::gather_metrics();
        assert!(result.is_ok());

        let output = result.unwrap();
        assert!(
            output.contains("sokoul_download_started_total"),
            "Download started metric not recorded"
        );
        assert!(
            output.contains("sokoul_download_completed_total"),
            "Download completed metric not recorded"
        );
        assert!(
            output.contains("sokoul_download_bytes_total"),
            "Download bytes metric not recorded"
        );
    }
}
