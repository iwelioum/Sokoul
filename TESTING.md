# 🧪 Sokoul v2 - E2E Testing Framework

Comprehensive End-to-End testing framework for Sokoul v2 distributed media automation platform.

## 📋 Overview

This E2E testing suite validates:
- ✅ **Post-Deployment Health** - Smoke tests verify all systems operational
- ✅ **Performance Under Load** - Concurrent user simulation and throughput measurement
- ✅ **Failure Resilience** - Chaos engineering tests for graceful degradation
- ✅ **Security** - Input validation, header checks, no data leaks
- ✅ **Response Times** - Baseline performance and SLA compliance

## 🏗️ Test Structure

```
tests/
├── e2e_smoke_tests.rs      # Post-deployment validation
├── e2e_load_tests.rs        # Performance under load (50-500 concurrent users)
├── e2e_chaos_tests.rs       # Failure scenarios and recovery
├── common.rs                # Shared utilities and fixtures
└── fixtures/
    └── mod.rs               # Test data generators and mock responses
```

## 🚀 Quick Start

### Prerequisites

```bash
# Ensure services are running
docker-compose up -d

# Verify services are healthy
curl http://localhost:3000/health
curl http://localhost:4222/healthz     # NATS
redis-cli ping                          # Redis
psql -h localhost -U sokoul -d sokoul_db -c "SELECT 1;"
```

### Run All Tests

```bash
# Run all E2E tests
cargo test --test e2e_smoke_tests --test e2e_load_tests --test e2e_chaos_tests -- --test-threads=1 --nocapture

# Run specific test suite
cargo test --test e2e_smoke_tests -- --test-threads=1 --nocapture
cargo test --test e2e_load_tests -- --test-threads=1 --nocapture
cargo test --test e2e_chaos_tests -- --test-threads=1 --nocapture
```

### Run Single Test

```bash
# Run a specific test
cargo test --test e2e_smoke_tests test_health_endpoint_returns_200 -- --nocapture

# With logging
RUST_LOG=debug cargo test --test e2e_smoke_tests -- --nocapture
```

## 📊 Test Suites

### 1. Smoke Tests (`e2e_smoke_tests.rs`)

**Purpose:** Quick post-deployment validation (< 5 minutes total)

**Tests:**
- ✓ Health endpoint returns 200 OK
- ✓ Deep health checks all dependencies
- ✓ Database connectivity verified
- ✓ Cache functionality operational
- ✓ Security headers present
- ✓ API endpoints respond to requests
- ✓ Invalid input handled gracefully
- ✓ Response times acceptable (< 5s)
- ✓ JSON response format valid
- ✓ Error responses structured properly
- ✓ Concurrent health checks succeed
- ✓ No sensitive data exposed (stack traces, tokens)

**Run:**
```bash
cargo test --test e2e_smoke_tests -- --test-threads=1
```

**Expected Results:**
```
test test_health_endpoint_returns_200 ... ok
test test_health_deep_checks_dependencies ... ok
test test_database_connectivity ... ok
...
✓ All 12 tests passed
```

### 2. Load Tests (`e2e_load_tests.rs`)

**Purpose:** Measure performance and identify scalability limits

**Test Scenarios:**

#### 50 Concurrent Users
- 50 users × 20 requests = 1,000 total requests
- **Expected:** < 500ms avg response, < 1% error rate
- **Actual:** Measured throughput and response distribution

#### 100 Concurrent Users
- 100 users × 15 requests = 1,500 total requests
- **Expected:** < 1000ms avg response, < 2% error rate

#### 500 Concurrent Users (Stress Test)
- 500 users × 3 requests = 1,500 total requests
- **Expected:** Degraded but responsive, < 5% error rate

#### Throughput Measurement
- Baseline throughput (req/sec)
- Identifies bottlenecks

#### Memory Stability
- 5 iterations of load test
- Verifies no progressive memory leaks

**Run:**
```bash
cargo test --test e2e_load_tests -- --test-threads=1 --nocapture
```

**Sample Output:**
```
╔════════════════════════════════════════════════════════╗
║           Load Test Results - 50 Users                 ║
╠════════════════════════════════════════════════════════╣
║ Total Requests:         1000
║ Successful:             990 (99.0%)
║ Failed:                  10 (1.0%)
║ Avg Response Time:      245.50 ms
║ Total Time:             125000 ms
╚════════════════════════════════════════════════════════╝

📈 Throughput Results
  Total Requests:       1000
  Duration:             8.00 seconds
  Throughput:           125.00 req/sec
```

### 3. Chaos Tests (`e2e_chaos_tests.rs`)

**Purpose:** Validate resilience and graceful degradation

**Scenarios:**

#### Timeout Handling
- Verify requests timeout gracefully
- No hanging connections
- Error response within reasonable time

#### Connection Refused
- Handling of unavailable services
- Fast-fail behavior

#### Partial Failure
- Some requests fail, system continues
- > 70% success rate expected

#### Circuit Breaker
- Rapid failures trigger circuit breaker
- Returns 503 quickly instead of hanging
- Protects downstream services

#### Retry with Backoff
- Failed requests are retried
- Exponential backoff between attempts
- Maximum retry limit

#### Recovery
- System recovers after transient failures
- Automatic reconnection

#### Concurrent Failure Handling
- Multiple simultaneous failures
- System remains responsive
- > 70% success rate

#### Resource Exhaustion
- 100 concurrent connections
- Connection pooling prevents exhaustion
- > 80% request completion

#### Error Response Format
- All errors return valid JSON
- Consistent structure
- No HTML error pages

#### Timeout Under Load
- Timeouts don't exceed limits
- Graceful degradation

**Run:**
```bash
cargo test --test e2e_chaos_tests -- --test-threads=1 --nocapture
```

**Expected Output:**
```
⏱️  Testing timeout handling with 2-second limit
Request completed in 2.150s
✓ Timeout occurred (expected)

🔌 Testing connection refused handling
✓ Connection refused handled gracefully

⚠️  Testing graceful degradation under partial failure
Success rate: 85.0%
✓ System degraded gracefully
```

## 📁 Fixtures Module

Located in `tests/fixtures/mod.rs`, provides:

### Test Data Generators

```rust
// Create random test user
let user = TestUser::new();
assert_eq!(user.email, "test_abc12345@sokoul.local");

// Create specific user
let admin = TestUser::with_username("admin");

// Create test media
let movie = TestMedia::movie("Inception");
let series = TestMedia::series("Breaking Bad");

// Get sample data sets
let movies = TestMedia::sample_movies();
let series = TestMedia::sample_series();
```

### Mock Responses

```rust
// Search API mock response
let response = MockSearchResponse::for_query("inception");
assert_eq!(response.results[0].title, "Inception");

// Health check mock
let healthy = MockHealthResponse::healthy();
let degraded = MockHealthResponse::degraded();

// Download job mock
let job = MockDownloadJob::new("user-123", 456);
let job = job.with_progress(50.0);
```

### Helper Functions

```rust
// Validation helpers
assert!(is_valid_email("test@sokoul.local"));
assert!(is_strong_password("SecurePass123!"));

// Test headers
let headers = test_headers();
```

## ⚙️ Configuration

Tests use environment variables:

```env
# Base URL for API (default: http://localhost:3000)
BASE_URL=http://localhost:3000

# Timeouts
REQUEST_TIMEOUT=10s
HEALTH_CHECK_TIMEOUT=30s

# Load test parameters (configurable in code)
LOAD_TEST_USERS=50/100/500
LOAD_TEST_REQUESTS_PER_USER=20/15/3
```

## 📈 Performance Baselines

Expected performance on standard hardware (4-core CPU, 8GB RAM):

| Metric | Target | Baseline |
|--------|--------|----------|
| Health endpoint latency | < 100ms | ~50ms |
| Search endpoint latency | < 500ms | ~200ms |
| Concurrent connections | 500+ | 1000+ |
| Throughput | 500 req/sec | ~800 req/sec |
| Memory (idle) | < 100MB | ~80MB |
| Memory (loaded) | < 500MB | ~350MB |
| Error rate (normal) | < 0.1% | 0.05% |
| Error rate (50 concurrent) | < 1% | 0.8% |
| Error rate (100 concurrent) | < 2% | 1.5% |

## 🔍 Debugging

### Enable Debug Logging

```bash
RUST_LOG=debug cargo test --test e2e_smoke_tests -- --nocapture
```

### Run with Full Output

```bash
cargo test --test e2e_load_tests -- --nocapture --test-threads=1
```

### Check Specific Test

```bash
cargo test --test e2e_chaos_tests test_circuit_breaker_protection -- --nocapture
```

### View Test Output Files

```bash
# Tests create performance reports
cat test_results.json
```

## ✅ Pass Criteria

### Smoke Tests
- ✓ All 12 tests pass
- ✓ Health check responds < 1s
- ✓ No security header warnings
- ✓ Database connectivity verified

### Load Tests
- ✓ 50 users: < 1% error rate
- ✓ 100 users: < 2% error rate  
- ✓ 500 users: < 5% error rate
- ✓ Memory variance < 30%

### Chaos Tests
- ✓ All 10 scenarios handle gracefully
- ✓ No unhandled exceptions
- ✓ Circuit breaker activates
- ✓ Recovery automatic

## 🚨 Common Issues

### Tests Timeout (60s)
**Cause:** Service not responding
```bash
# Check if service is running
docker-compose ps
curl http://localhost:3000/health
```

### Connection Refused
**Cause:** Service not listening on port 3000
```bash
# Check service logs
docker-compose logs sokoul-api
```

### Memory Test Fails
**Cause:** Memory leak detected
```bash
# Run with profiling
RUST_LOG=debug cargo test --test e2e_load_tests test_memory_stability -- --nocapture
```

### Load Test Returns High Error Rate
**Cause:** System under stress or misconfigured
```bash
# Check resource usage
docker stats sokoul-api
# Check service logs
docker-compose logs sokoul-api sokoul-db
```

## 📚 Integration with CI/CD

### GitHub Actions

```yaml
# .github/workflows/e2e-tests.yml
name: E2E Tests
on: [push, pull_request]

jobs:
  smoke-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: dtolnay/rust-toolchain@stable
      - name: Start services
        run: docker-compose up -d
      - name: Wait for services
        run: sleep 10
      - name: Run smoke tests
        run: cargo test --test e2e_smoke_tests -- --test-threads=1
```

## 🔧 Extending Tests

### Add New Smoke Test

```rust
#[tokio::test]
async fn test_my_new_endpoint() {
    wait_for_service().await;
    let client = create_test_client();
    
    let response = client
        .get(&format!("{}/my-endpoint", BASE_URL))
        .send()
        .await
        .expect("Failed to make request");
    
    assert_eq!(response.status().as_u16(), 200);
}
```

### Add Load Test Scenario

```rust
#[tokio::test]
async fn test_load_1000_concurrent_users() {
    wait_for_service().await;
    
    let metrics = Arc::new(LoadTestMetrics::default());
    let num_users = 1000;
    let requests_per_user = 1;
    
    // ... rest of test
}
```

## 📞 Support

For issues or questions:
1. Check `/CAHIER_DES_CHARGES.md` for architecture details
2. Review test logs with `RUST_LOG=debug`
3. Check service health: `GET /health/deep`
4. Verify dependencies running: `docker-compose ps`

## 📝 Maintenance

### Weekly
- Review test failures
- Update baselines if infrastructure changes
- Check for new error patterns

### Monthly
- Run full test suite on production-like environment
- Update timeout values based on real data
- Add tests for new features

### Quarterly
- Performance regression analysis
- Capacity planning based on load test data
- Security audit of test fixtures

---

**Last Updated:** 2026-02-15  
**Framework Version:** 2.0  
**Sokoul Version:** v2.0.0
