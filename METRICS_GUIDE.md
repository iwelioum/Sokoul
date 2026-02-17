#!/bin/bash

# Prometheus Metrics Quick Reference Guide for Sokoul API

## 🚀 Quick Start

### 1. Access Metrics Endpoint

```bash
# Get all metrics in Prometheus text format
curl http://localhost:8080/metrics

# Filter specific metrics
curl http://localhost:8080/metrics | grep sokoul_api_requests_total

# Count total metrics
curl http://localhost:8080/metrics | grep -c "^sokoul_"
```

### 2. Example Prometheus Configuration

Add to your `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'sokoul'
    metrics_path: '/metrics'
    scrape_interval: 10s
    static_configs:
      - targets: ['localhost:8080']
```

### 3. Verify Metrics Collection

```bash
# Check if metrics endpoint is accessible
curl -v http://localhost:8080/metrics 2>&1 | head -20

# Verify metrics are being populated
curl http://localhost:8080/metrics | grep "sokoul_api_requests_total{" | head -5
```

## 📊 Available Metrics

### API Metrics
```
sokoul_api_requests_total{endpoint="...",method="GET|POST|...",status="200|404|..."}
sokoul_api_request_duration_seconds{endpoint="...",method="...",status="..."}
```

### Worker Metrics
```
sokoul_worker_jobs_total{job_type="...",status="success|failure"}
sokoul_worker_jobs_duration_seconds{job_type="...",status="..."}
sokoul_worker_queue_size{job_type="..."}
```

### Cache Metrics
```
sokoul_cache_hits_total
sokoul_cache_misses_total
sokoul_cache_size_bytes
```

### Database Metrics
```
sokoul_db_connections_active
sokoul_db_query_duration_seconds{query_type="..."}
```

### Search Metrics
```
sokoul_search_requests_total{provider="tmdb|tvmaze|...",media_type="movie|tv"}
sokoul_search_latency_seconds{provider="..."}
sokoul_search_errors_total{provider="...",error_type="..."}
```

### Download Metrics
```
sokoul_download_started_total
sokoul_download_completed_total
sokoul_download_failed_total
sokoul_download_bytes_total
```

### Streaming Metrics
```
sokoul_stream_sessions_active
sokoul_stream_sessions_total
```

## 🎯 Integration Examples

### In Rust Code - Recording Metrics

```rust
use crate::metrics::*;

// Count a request
SEARCH_REQUESTS_TOTAL
    .with_label_values(&["tmdb", "movie"])
    .inc();

// Record request time
let start = Instant::now();
// ... do work ...
let duration = start.elapsed().as_secs_f64();
SEARCH_LATENCY_SECONDS
    .with_label_values(&["tmdb"])
    .observe(duration);

// Update gauge
DB_CONNECTIONS_ACTIVE.set(5);

// Count with status
WORKER_JOBS_TOTAL
    .with_label_values(&["search_worker", "success"])
    .inc();
```

### PromQL Queries

```promql
# Total API requests per endpoint
sum by (endpoint) (sokoul_api_requests_total)

# Request rate (requests per minute)
rate(sokoul_api_requests_total[1m])

# Average response time by endpoint
avg by (endpoint) (sokoul_api_request_duration_seconds)

# P95 response time
histogram_quantile(0.95, sokoul_api_request_duration_seconds)

# Cache hit ratio
sokoul_cache_hits_total / (sokoul_cache_hits_total + sokoul_cache_misses_total)

# Worker job success rate
sokoul_worker_jobs_total{status="success"} / sokoul_worker_jobs_total

# Active streaming sessions
sokoul_stream_sessions_active

# Database query performance
rate(sokoul_db_query_duration_seconds_sum[5m]) / rate(sokoul_db_query_duration_seconds_count[5m])
```

### Grafana Dashboard Examples

**Request Rate Panel:**
```
rate(sokoul_api_requests_total[5m])
```

**Response Time Panel:**
```
histogram_quantile(0.95, rate(sokoul_api_request_duration_seconds_bucket[5m]))
```

**Cache Hit Rate:**
```
(sokoul_cache_hits_total) / (sokoul_cache_hits_total + sokoul_cache_misses_total) * 100
```

**Error Rate:**
```
rate(sokoul_errors_total[5m])
```

## 🔧 Running Tests

### Test Metrics Module

```bash
# Run only metrics tests
cargo test metrics_tests --bin sokoul

# Run with output
cargo test metrics_tests --bin sokoul -- --nocapture

# Run all tests including metrics validation
cargo test --bin sokoul -- --test-threads=1
```

### Test Output

```
test metrics_tests::metrics_tests::test_metrics_initialization ... ok
test metrics_tests::metrics_tests::test_api_request_counter ... ok
test metrics_tests::metrics_tests::test_histogram_observation ... ok
test metrics_tests::metrics_tests::test_worker_job_counter ... ok
test metrics_tests::metrics_tests::test_gauge_set ... ok
test metrics_tests::metrics_tests::test_prometheus_text_format ... ok
test metrics_tests::metrics_tests::test_search_metrics ... ok
test metrics_tests::metrics_tests::test_download_metrics ... ok

test result: ok. 8 passed; 0 failed
```

## 📈 Monitoring Alerts

### Alert Rules Example (prometheus.yml)

```yaml
groups:
  - name: sokoul_alerts
    rules:
      - alert: HighErrorRate
        expr: |
          (rate(sokoul_errors_total[5m]) / rate(sokoul_api_requests_total[5m])) > 0.05
        for: 5m
        annotations:
          summary: "High error rate detected"

      - alert: SlowRequests
        expr: |
          histogram_quantile(0.95, rate(sokoul_api_request_duration_seconds_bucket[5m])) > 1
        for: 5m
        annotations:
          summary: "Request latency is high"

      - alert: LowCacheHitRate
        expr: |
          (sokoul_cache_hits_total) / (sokoul_cache_hits_total + sokoul_cache_misses_total) < 0.7
        for: 10m
        annotations:
          summary: "Cache hit rate is below 70%"
```

## 🐛 Debugging

### Check Metric Labels

```bash
# Get unique values for a label
curl http://localhost:8080/metrics | grep "sokoul_api_requests_total" | cut -d'{' -f2 | cut -d'}' -f1 | sort -u

# Count metrics by type
curl http://localhost:8080/metrics | grep "^sokoul_" | grep -o "^sokoul_[^_]*_[^{]*" | sort | uniq -c
```

### Validate Prometheus Format

```bash
# Check if metrics are valid Prometheus format
curl http://localhost:8080/metrics | promtool check metrics
```

### Monitor Metric Growth

```bash
# Watch metrics in real-time (requires watch command)
watch -n 5 "curl -s http://localhost:8080/metrics | grep sokoul_api_requests_total | wc -l"
```

## 📝 Notes

- All metrics are registered on application startup
- Metrics are thread-safe using lazy_static
- API endpoint is public (no authentication required)
- Metrics are exported in Prometheus text format
- Response times are in seconds (buckets: 0.01, 0.05, 0.1, 0.5, 1, 2, 5)
- Counters are monotonically increasing
- Gauges can increase or decrease
- Histograms provide distribution with configurable buckets

## 🚫 Troubleshooting

**No metrics showing:**
- Ensure application has been running long enough for requests to be recorded
- Check that requests are hitting the application
- Verify `/metrics` endpoint is accessible

**High cardinality metrics:**
- Check that label values are bounded (endpoints, status codes, etc.)
- Avoid using unbounded values as labels (e.g., user IDs, request IDs)

**Memory usage increasing:**
- Metrics are stored in memory
- Check label cardinality - too many unique label combinations can increase memory
- Review metric definitions for appropriate label usage

## 📞 Support

For issues or questions:
1. Check the metrics endpoint: `curl http://localhost:8080/metrics`
2. Review Prometheus logs for scraping errors
3. Verify label values are bounded
4. Check application logs for initialization messages

Build Status: ✅ All 38 tests passing
Binary Size: 41.4 MB (release build)
