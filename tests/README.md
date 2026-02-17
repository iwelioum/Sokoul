# E2E Testing Framework for Sokoul v2

## Quick Reference

```bash
# Run all E2E tests
cargo test --test e2e_smoke_tests --test e2e_load_tests --test e2e_chaos_tests -- --test-threads=1 --nocapture

# Run specific test suite
cargo test --test e2e_smoke_tests -- --test-threads=1
cargo test --test e2e_load_tests -- --test-threads=1 --nocapture
cargo test --test e2e_chaos_tests -- --test-threads=1 --nocapture

# Run single test with detailed output
RUST_LOG=debug cargo test --test e2e_smoke_tests test_health_endpoint_returns_200 -- --nocapture
```

## Test Files

### e2e_smoke_tests.rs (12 tests, ~2 min)
Post-deployment validation of basic functionality:
- Health endpoint check
- Deep dependency health
- Database connectivity
- Cache operations
- Security headers
- Request timeouts
- Response formats
- Concurrent requests
- No data leaks

**Run:** `cargo test --test e2e_smoke_tests -- --test-threads=1`

### e2e_load_tests.rs (5 tests, ~10 min)
Performance testing under concurrent load:
- 50 concurrent users (1,000 requests)
- 100 concurrent users (1,500 requests)
- 500 concurrent users (stress test)
- Throughput measurement
- Memory stability (no leaks)

**Run:** `cargo test --test e2e_load_tests -- --test-threads=1 --nocapture`

### e2e_chaos_tests.rs (10 tests, ~5 min)
Failure resilience and graceful degradation:
- Request timeouts
- Connection failures
- Partial failures
- Circuit breaker activation
- Retry with backoff
- Recovery after failure
- Resource exhaustion
- Error response format
- Timeout under load

**Run:** `cargo test --test e2e_chaos_tests -- --test-threads=1 --nocapture`

### fixtures/mod.rs
Test data generators and mock responses:
- `TestUser` - Random user creation
- `TestMedia` - Movie/series test data
- `MockSearchResponse` - TMDB API mock
- `MockHealthResponse` - Health check mock
- `MockDownloadJob` - Job tracking mock
- Helper functions for validation

## Prerequisites

### Services Running
```bash
docker-compose up -d
```

### Verify Service Health
```bash
# API
curl http://localhost:3000/health

# NATS JetStream
curl -s http://localhost:4222/healthz

# Redis
redis-cli PING

# PostgreSQL
psql -h localhost -U sokoul -d sokoul_db -c "SELECT 1;"
```

## Expected Results

### Smoke Tests (should all pass)
```
✓ test_health_endpoint_returns_200
✓ test_health_deep_checks_dependencies
✓ test_database_connectivity
✓ test_cache_connectivity
✓ test_security_headers_present
✓ test_api_responds_to_requests
✓ test_invalid_requests_handled_gracefully
✓ test_response_times_acceptable
✓ test_json_response_format
✓ test_error_responses_have_valid_format
✓ test_concurrent_health_checks
✓ test_no_stack_traces_exposed

test result: ok. 12 passed
```

### Load Tests (sample output)
```
📊 Starting load test: 50 concurrent users, 20 requests each

╔════════════════════════════════════════════════════════╗
║           Load Test Results - 50 Users                 ║
╠════════════════════════════════════════════════════════╣
║ Total Requests:        1000
║ Successful:             990 (99.0%)
║ Failed:                  10 (1.0%)
║ Avg Response Time:      245.50 ms
║ Total Time:             125000 ms
╚════════════════════════════════════════════════════════╝

test result: ok. 5 passed
```

### Chaos Tests (sample output)
```
⏱️  Testing timeout handling with 2-second limit
Request completed in 2.150s
✓ Timeout occurred (expected)

🔌 Testing connection refused handling
✓ Connection refused handled gracefully

⚠️  Testing graceful degradation under partial failure
Success rate: 85.0%
✓ System degraded gracefully

...more tests...

test result: ok. 10 passed
```

## Pass Criteria

### Smoke Tests
- ✓ All 12 tests pass
- ✓ Health endpoint responds < 1s
- ✓ No 5xx errors on valid requests
- ✓ Response format is valid JSON

### Load Tests
- ✓ 50 users: < 1% error rate
- ✓ 100 users: < 2% error rate
- ✓ 500 users: < 5% error rate (degraded acceptable)
- ✓ Memory stable (< 30% variance)

### Chaos Tests
- ✓ All 10 scenarios complete without panic
- ✓ No unhandled exceptions
- ✓ Circuit breaker works
- ✓ Recovery automatic

## Performance Baselines

| Test | Expected | Typical |
|------|----------|---------|
| Health latency | < 100ms | ~50ms |
| Search latency | < 500ms | ~200ms |
| 50 user error rate | < 1% | 0.8% |
| 100 user error rate | < 2% | 1.5% |
| Memory variance | < 30% | < 15% |

## Debugging

### Enable logging
```bash
RUST_LOG=debug cargo test --test e2e_smoke_tests -- --nocapture
RUST_LOG=trace cargo test --test e2e_chaos_tests test_circuit_breaker_protection -- --nocapture
```

### Check service status
```bash
docker-compose ps
docker-compose logs sokoul-api
docker-compose logs sokoul-db
```

### Run single test with output
```bash
cargo test --test e2e_load_tests test_load_50_concurrent_users -- --test-threads=1 --nocapture
```

## CI/CD Integration

### GitHub Actions
Tests run on every push/PR. See `.github/workflows/` for configuration.

### Local Pre-commit
```bash
#!/bin/bash
cargo test --test e2e_smoke_tests -- --test-threads=1
if [ $? -ne 0 ]; then
  echo "Smoke tests failed - commit blocked"
  exit 1
fi
```

## Common Issues

### "Service failed to become ready after 30s"
- Service not running on port 3000
- Database not initialized
- Check: `docker-compose ps` and `docker-compose logs`

### "Connection refused"
- NATS/Redis not accessible
- Check: `docker-compose exec sokoul-api curl http://localhost:4222/healthz`

### High error rate in load tests
- Insufficient resources
- Database slow queries
- Check: `docker stats` and `docker-compose logs sokoul-db`

### Memory test fails
- Memory leak detected in code
- Run with profiling: `valgrind` or similar tools

## Extending Tests

### Add smoke test
```rust
#[tokio::test]
async fn test_my_feature() {
    wait_for_service().await;
    let client = create_test_client();
    
    let response = client
        .get(&format!("{}/my-endpoint", BASE_URL))
        .send()
        .await
        .expect("Request failed");
    
    assert_eq!(response.status().as_u16(), 200);
}
```

### Add load test
```rust
#[tokio::test]
async fn test_load_my_endpoint() {
    wait_for_service().await;
    let metrics = Arc::new(LoadTestMetrics::default());
    
    // Spawn workers and collect metrics
    // Use metrics.record_request() to track results
}
```

### Add fixture
```rust
pub fn my_test_data() -> SomeType {
    // Create and return test data
}

#[test]
fn test_my_fixture() {
    let data = my_test_data();
    assert!(!data.is_empty());
}
```

## Full Test Run Example

```bash
# Start services
docker-compose up -d

# Wait for startup
sleep 10

# Run all tests
RUST_LOG=info cargo test \
  --test e2e_smoke_tests \
  --test e2e_load_tests \
  --test e2e_chaos_tests \
  -- --test-threads=1 --nocapture

# Cleanup
docker-compose logs > test_run.log
docker-compose down
```

## Documentation

For detailed information, see:
- `TESTING.md` - Full testing strategy and architecture
- `CAHIER_DES_CHARGES.md` - System architecture and requirements
- Test code comments - Inline documentation in each test

## Support

Questions? Check:
1. Test code comments
2. Service health: `GET /health/deep`
3. Service logs: `docker-compose logs`
4. GitHub issues with `testing` label
