use std::future::Future;
use tokio::time::{sleep, Duration};

pub struct RetryConfig {
    pub max_attempts: u32,
    pub initial_delay_ms: u64,
    pub backoff_multiplier: f64,
    pub max_delay_ms: u64,
}

impl Default for RetryConfig {
    fn default() -> Self {
        Self {
            max_attempts: 3,
            initial_delay_ms: 2000,
            backoff_multiplier: 2.0,
            max_delay_ms: 30_000,
        }
    }
}

pub async fn retry_with_backoff<F, Fut, T, E>(
    config: &RetryConfig,
    operation_name: &str,
    mut f: F,
) -> Result<T, E>
where
    F: FnMut() -> Fut,
    Fut: Future<Output = Result<T, E>>,
    E: std::fmt::Display,
{
    let mut delay = config.initial_delay_ms as f64;

    for attempt in 1..=config.max_attempts {
        match f().await {
            Ok(result) => {
                if attempt > 1 {
                    tracing::info!(
                        "{}: réussi à la tentative {}/{}",
                        operation_name,
                        attempt,
                        config.max_attempts
                    );
                }
                return Ok(result);
            }
            Err(e) => {
                if attempt >= config.max_attempts {
                    tracing::error!(
                        "{}: échec après {}/{} tentatives — {}",
                        operation_name,
                        attempt,
                        config.max_attempts,
                        e
                    );
                    return Err(e);
                }

                let actual_delay = (delay as u64).min(config.max_delay_ms);
                tracing::warn!(
                    "{}: tentative {}/{} échouée ({}), retry dans {}ms…",
                    operation_name,
                    attempt,
                    config.max_attempts,
                    e,
                    actual_delay
                );
                sleep(Duration::from_millis(actual_delay)).await;
                delay *= config.backoff_multiplier;
            }
        }
    }

    unreachable!()
}
