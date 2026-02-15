use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::Mutex;

/// Circuit Breaker State Machine
#[derive(Debug, Clone, Copy, PartialEq)]
#[allow(dead_code)]
pub enum CircuitState {
    Closed,   // Normal operation
    Open,     // Failing, reject fast
    HalfOpen, // Testing if recovered
}

/// Circuit Breaker for resilient external calls
#[allow(dead_code)]
pub struct CircuitBreaker {
    state: Arc<Mutex<CircuitState>>,
    failure_count: Arc<AtomicU32>,
    success_count: Arc<AtomicU32>,
    last_failure_time: Arc<Mutex<Option<Instant>>>,
    failure_threshold: u32, // Failures before opening
    success_threshold: u32, // Successes in half-open to close
    timeout: Duration,      // How long to stay open
}

#[allow(dead_code)]
impl CircuitBreaker {
    pub fn new(failure_threshold: u32, success_threshold: u32, timeout: Duration) -> Self {
        Self {
            state: Arc::new(Mutex::new(CircuitState::Closed)),
            failure_count: Arc::new(AtomicU32::new(0)),
            success_count: Arc::new(AtomicU32::new(0)),
            last_failure_time: Arc::new(Mutex::new(None)),
            failure_threshold,
            success_threshold,
            timeout,
        }
    }

    pub async fn is_open(&self) -> bool {
        let state = *self.state.lock().await;

        if state == CircuitState::Open {
            // Check if timeout has elapsed to transition to HalfOpen
            if let Some(last_fail) = *self.last_failure_time.lock().await {
                if last_fail.elapsed() > self.timeout {
                    // Transition to HalfOpen
                    *self.state.lock().await = CircuitState::HalfOpen;
                    self.success_count.store(0, Ordering::Relaxed);
                    return false;
                }
            }
            true
        } else {
            false
        }
    }

    pub async fn record_success(&self) {
        let mut state = self.state.lock().await;

        match *state {
            CircuitState::Closed => {
                // Reset failure count on success
                self.failure_count.store(0, Ordering::Relaxed);
            }
            CircuitState::HalfOpen => {
                self.success_count.fetch_add(1, Ordering::Relaxed);
                if self.success_count.load(Ordering::Relaxed) >= self.success_threshold {
                    *state = CircuitState::Closed;
                    self.failure_count.store(0, Ordering::Relaxed);
                    self.success_count.store(0, Ordering::Relaxed);
                    tracing::info!("Circuit breaker closed - service recovered");
                }
            }
            _ => {}
        }
    }

    pub async fn record_failure(&self) {
        let mut state = self.state.lock().await;

        match *state {
            CircuitState::Closed => {
                self.failure_count.fetch_add(1, Ordering::Relaxed);
                if self.failure_count.load(Ordering::Relaxed) >= self.failure_threshold {
                    *state = CircuitState::Open;
                    *self.last_failure_time.lock().await = Some(Instant::now());
                    tracing::warn!("Circuit breaker opened - too many failures");
                }
            }
            CircuitState::HalfOpen => {
                *state = CircuitState::Open;
                *self.last_failure_time.lock().await = Some(Instant::now());
                tracing::warn!("Circuit breaker reopened - still failing");
            }
            _ => {}
        }
    }
}

/// Retry configuration with exponential backoff
#[allow(dead_code)]
pub struct RetryPolicy {
    pub max_retries: u32,
    pub initial_delay: Duration,
    pub max_delay: Duration,
    pub multiplier: f64,
}

#[allow(dead_code)]
impl RetryPolicy {
    pub fn default_exponential() -> Self {
        Self {
            max_retries: 3,
            initial_delay: Duration::from_millis(100),
            max_delay: Duration::from_secs(10),
            multiplier: 2.0,
        }
    }

    pub fn calculate_delay(&self, attempt: u32) -> Duration {
        let backoff = self.initial_delay.as_millis() as f64 * self.multiplier.powi(attempt as i32);
        let millis = backoff.min(self.max_delay.as_millis() as f64);
        Duration::from_millis(millis as u64)
    }
}

/// Retry helper for transient failures
#[allow(dead_code)]
pub async fn with_retry<F, T, E>(
    mut operation: impl FnMut() -> F,
    policy: &RetryPolicy,
) -> Result<T, E>
where
    F: std::future::Future<Output = Result<T, E>>,
    E: std::fmt::Display,
{
    let mut attempt = 0;

    loop {
        match operation().await {
            Ok(result) => return Ok(result),
            Err(err) => {
                attempt += 1;
                if attempt > policy.max_retries {
                    tracing::error!(
                        "Operation failed after {} retries: {}",
                        policy.max_retries,
                        err
                    );
                    return Err(err);
                }

                let delay = policy.calculate_delay(attempt - 1);
                tracing::warn!(
                    "Operation failed (attempt {}), retrying in {:?}: {}",
                    attempt,
                    delay,
                    err
                );
                tokio::time::sleep(delay).await;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_circuit_breaker_opens_after_failures() {
        let cb = CircuitBreaker::new(3, 1, Duration::from_secs(1));

        assert!(!cb.is_open().await);

        cb.record_failure().await;
        cb.record_failure().await;
        cb.record_failure().await;

        assert!(cb.is_open().await);
    }

    #[tokio::test]
    async fn test_circuit_breaker_closes_after_recovery() {
        let cb = CircuitBreaker::new(2, 2, Duration::from_millis(100));

        // Trip the breaker
        cb.record_failure().await;
        cb.record_failure().await;
        assert!(cb.is_open().await);

        // Wait for timeout
        tokio::time::sleep(Duration::from_millis(150)).await;

        // Should be half-open now
        assert!(!cb.is_open().await);

        // Recover
        cb.record_success().await;
        cb.record_success().await;

        assert!(!cb.is_open().await);
    }

    #[test]
    fn test_retry_delay_exponential() {
        let policy = RetryPolicy::default_exponential();

        let d1 = policy.calculate_delay(0);
        let d2 = policy.calculate_delay(1);
        let d3 = policy.calculate_delay(2);

        assert!(d2 > d1);
        assert!(d3 > d2);
    }
}
