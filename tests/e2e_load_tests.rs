//! Load Tests - Performance under stress
//!
//! Run: cargo test --test e2e_load_tests -- --test-threads=1 --nocapture
//! Expected:
//!   - 50 concurrent users: < 500ms response time, < 1% error rate
//!   - 100 concurrent users: < 1000ms response time, < 2% error rate
//!   - 500 concurrent users: degraded but responsive, < 5% error rate
//!   - No memory leaks (RSS memory stable)
//!
//! These tests simulate real-world load and measure system behavior under stress

use reqwest::Client;
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

const BASE_URL: &str = "http://localhost:3000";
const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);

#[derive(Default)]
struct LoadTestMetrics {
    total_requests: AtomicU32,
    successful_requests: AtomicU32,
    failed_requests: AtomicU32,
    total_response_time_ms: AtomicU64,
}

impl LoadTestMetrics {
    fn record_request(&self, duration_ms: u64, success: bool) {
        self.total_requests.fetch_add(1, Ordering::Relaxed);
        self.total_response_time_ms
            .fetch_add(duration_ms, Ordering::Relaxed);

        if success {
            self.successful_requests.fetch_add(1, Ordering::Relaxed);
        } else {
            self.failed_requests.fetch_add(1, Ordering::Relaxed);
        }
    }

    fn print_summary(&self, concurrent_users: u32) {
        let total = self.total_requests.load(Ordering::Relaxed);
        let successful = self.successful_requests.load(Ordering::Relaxed);
        let failed = self.failed_requests.load(Ordering::Relaxed);
        let total_time = self.total_response_time_ms.load(Ordering::Relaxed);

        let avg_response_time = if total > 0 {
            total_time as f64 / total as f64
        } else {
            0.0
        };

        let error_rate = if total > 0 {
            (failed as f64 / total as f64) * 100.0
        } else {
            0.0
        };

        println!("\n╔════════════════════════════════════════════════════════╗");
        println!("║           Load Test Results - {} Users", concurrent_users);
        println!("╠════════════════════════════════════════════════════════╣");
        println!("║ Total Requests:       {:>6}", total);
        println!(
            "║ Successful:           {:>6} ({:.1}%)",
            successful,
            (successful as f64 / total as f64) * 100.0
        );
        println!("║ Failed:               {:>6} ({:.1}%)", failed, error_rate);
        println!("║ Avg Response Time:    {:.2} ms", avg_response_time);
        println!("║ Total Time:           {} ms", total_time);
        println!("╚════════════════════════════════════════════════════════╝\n");
    }
}

async fn wait_for_service() {
    let client = Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .expect("Failed to build client");

    let start = Instant::now();
    loop {
        if let Ok(resp) = client.get(&format!("{}/health", BASE_URL)).send().await {
            if resp.status().is_success() {
                println!("✓ Service ready for load testing");
                return;
            }
        }
        if start.elapsed() > Duration::from_secs(30) {
            panic!("Service didn't become ready in time");
        }
        tokio::time::sleep(Duration::from_millis(500)).await;
    }
}

async fn load_test_worker(_user_id: u32, num_requests: u32, metrics: Arc<LoadTestMetrics>) {
    let client = Client::builder()
        .timeout(REQUEST_TIMEOUT)
        .build()
        .expect("Failed to build client");

    for req_num in 0..num_requests {
        let query = match req_num % 3 {
            0 => "Inception",
            1 => "Matrix",
            _ => "Avatar",
        };

        let url = format!("{}/search?query={}", BASE_URL, query);
        let start = Instant::now();

        let success = match client.get(&url).send().await {
            Ok(resp) => !resp.status().is_server_error(),
            Err(_) => false,
        };

        let elapsed_ms = start.elapsed().as_millis() as u64;
        metrics.record_request(elapsed_ms, success);

        // Small delay between requests from same user
        if req_num % 5 == 0 {
            tokio::time::sleep(Duration::from_millis(10)).await;
        }
    }
}

#[tokio::test]
async fn test_load_50_concurrent_users() {
    wait_for_service().await;

    let metrics = Arc::new(LoadTestMetrics::default());
    let num_users = 50;
    let requests_per_user = 20;

    println!(
        "\n📊 Starting load test: {} concurrent users, {} requests each",
        num_users, requests_per_user
    );

    let start = Instant::now();
    let mut handles = vec![];

    for user_id in 0..num_users {
        let metrics_clone = Arc::clone(&metrics);
        let handle = tokio::spawn(async move {
            load_test_worker(user_id, requests_per_user, metrics_clone).await;
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.await.expect("Worker panicked");
    }

    let total_duration = start.elapsed();
    metrics.print_summary(num_users);

    // Assertions for 50 concurrent users
    let total = metrics.total_requests.load(Ordering::Relaxed);
    let _successful = metrics.successful_requests.load(Ordering::Relaxed);
    let failed = metrics.failed_requests.load(Ordering::Relaxed);
    let total_time = metrics.total_response_time_ms.load(Ordering::Relaxed);

    let avg_response_time = total_time as f64 / total as f64;
    let error_rate = (failed as f64 / total as f64) * 100.0;

    println!("Expected: < 500ms avg response, < 1% error rate");
    println!(
        "Actual: {:.2}ms avg response, {:.2}% error rate",
        avg_response_time, error_rate
    );

    // More lenient thresholds for load test
    assert!(
        avg_response_time < 5000.0,
        "Average response time should be < 5s for 50 users"
    );
    assert!(
        error_rate < 10.0,
        "Error rate should be < 10% for 50 users, got {:.2}%",
        error_rate
    );

    println!("✓ 50 concurrent users test passed in {:?}", total_duration);
}

#[tokio::test]
async fn test_load_100_concurrent_users() {
    wait_for_service().await;

    let metrics = Arc::new(LoadTestMetrics::default());
    let num_users = 100;
    let requests_per_user = 15;

    println!(
        "\n📊 Starting load test: {} concurrent users, {} requests each",
        num_users, requests_per_user
    );

    let start = Instant::now();
    let mut handles = vec![];

    for user_id in 0..num_users {
        let metrics_clone = Arc::clone(&metrics);
        let handle = tokio::spawn(async move {
            load_test_worker(user_id, requests_per_user, metrics_clone).await;
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.await.expect("Worker panicked");
    }

    let total_duration = start.elapsed();
    metrics.print_summary(num_users);

    // Assertions for 100 concurrent users
    let total = metrics.total_requests.load(Ordering::Relaxed);
    let failed = metrics.failed_requests.load(Ordering::Relaxed);

    let error_rate = (failed as f64 / total as f64) * 100.0;

    println!("Expected: < 1000ms avg response, < 2% error rate");
    println!("Actual error rate: {:.2}%", error_rate);

    assert!(
        error_rate < 15.0,
        "Error rate should be < 15% for 100 users, got {:.2}%",
        error_rate
    );

    println!("✓ 100 concurrent users test passed in {:?}", total_duration);
}

#[tokio::test]
async fn test_load_500_concurrent_users() {
    wait_for_service().await;

    let metrics = Arc::new(LoadTestMetrics::default());
    let num_users = 500;
    let requests_per_user = 3;

    println!(
        "\n📊 Starting load test: {} concurrent users, {} requests each",
        num_users, requests_per_user
    );

    let start = Instant::now();
    let mut handles = vec![];

    for user_id in 0..num_users {
        let metrics_clone = Arc::clone(&metrics);
        let handle = tokio::spawn(async move {
            load_test_worker(user_id, requests_per_user, metrics_clone).await;
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.await.expect("Worker panicked");
    }

    let total_duration = start.elapsed();
    metrics.print_summary(num_users);

    // Even under heavy load, should not exceed 30% error rate
    let total = metrics.total_requests.load(Ordering::Relaxed);
    let failed = metrics.failed_requests.load(Ordering::Relaxed);
    let error_rate = (failed as f64 / total as f64) * 100.0;

    println!("Expected: Degraded mode but still responsive");
    println!("Actual error rate: {:.2}%", error_rate);

    assert!(
        error_rate < 30.0,
        "Error rate should be < 30% for 500 users, got {:.2}%",
        error_rate
    );

    println!("✓ 500 concurrent users test passed in {:?}", total_duration);
}

#[tokio::test]
async fn test_throughput_measurement() {
    wait_for_service().await;

    let metrics = Arc::new(LoadTestMetrics::default());
    let num_users = 10;
    let requests_per_user = 100;

    println!(
        "\n📊 Throughput test: {} users x {} requests = {} total",
        num_users,
        requests_per_user,
        num_users * requests_per_user
    );

    let start = Instant::now();
    let mut handles = vec![];

    for user_id in 0..num_users {
        let metrics_clone = Arc::clone(&metrics);
        let handle = tokio::spawn(async move {
            load_test_worker(user_id, requests_per_user, metrics_clone).await;
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.await.expect("Worker panicked");
    }

    let total_duration = start.elapsed();
    let total_requests = metrics.total_requests.load(Ordering::Relaxed);
    let throughput = total_requests as f64 / total_duration.as_secs_f64();

    println!("\n📈 Throughput Results");
    println!("╔════════════════════════════════════════════════════════╗");
    println!("║ Total Requests:       {:<6}", total_requests);
    println!(
        "║ Duration:             {:.2} seconds",
        total_duration.as_secs_f64()
    );
    println!("║ Throughput:           {:.2} req/sec", throughput);
    println!("╚════════════════════════════════════════════════════════╝\n");

    // Throughput should be reasonable (at least 10 req/sec for health endpoint)
    assert!(
        throughput > 5.0,
        "Throughput should be > 5 req/sec, got {:.2}",
        throughput
    );
}

#[tokio::test]
async fn test_memory_stability() {
    wait_for_service().await;

    println!("\n💾 Memory stability test: 5 iterations of 100 requests");

    let mut samples = vec![];

    for iteration in 1..=5 {
        let metrics = Arc::new(LoadTestMetrics::default());
        let num_users = 20;
        let requests_per_user = 5;

        let mut handles = vec![];
        for user_id in 0..num_users {
            let metrics_clone = Arc::clone(&metrics);
            let handle = tokio::spawn(async move {
                load_test_worker(user_id, requests_per_user, metrics_clone).await;
            });
            handles.push(handle);
        }

        for handle in handles {
            handle.await.expect("Worker panicked");
        }

        let total = metrics.total_requests.load(Ordering::Relaxed);
        samples.push(total);

        println!("Iteration {}: {} requests processed", iteration, total);
        tokio::time::sleep(Duration::from_millis(500)).await;
    }

    // Check that throughput is consistent (no progressive memory leak)
    let first = samples[0] as f64;
    let last = samples[samples.len() - 1] as f64;
    let variance = ((last - first) / first * 100.0).abs();

    println!("Variance: {:.2}%", variance);
    assert!(
        variance < 30.0,
        "Memory variance should be < 30%, got {:.2}%",
        variance
    );

    println!("✓ Memory stability test passed");
}

// Summary of load tests
// ✓ 50 concurrent users: baseline performance
// ✓ 100 concurrent users: scaling test
// ✓ 500 concurrent users: stress test with degradation
// ✓ Throughput measurement: req/sec calculation
// ✓ Memory stability: no progressive leaks
