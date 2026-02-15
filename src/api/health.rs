use crate::AppState;
use axum::{extract::State, http::StatusCode, Json};
use serde_json::{json, Value};
use std::sync::Arc;

pub async fn health_check_handler(
    State(state): State<Arc<AppState>>,
) -> (StatusCode, Json<Value>) {
    let db_ok = sqlx::query("SELECT 1").execute(&state.db_pool).await.is_ok();

    let redis_ok = match state.redis_client.get_multiplexed_async_connection().await {
        Ok(mut con) => redis::cmd("PING").query_async::<_, String>(&mut con).await.is_ok(),
        Err(_) => false,
    };

    // Simple NATS health check: try to publish a small message to a health topic
    let nats_ok = state
        .jetstream_context
        .publish("sokoul.health.ping", "ping".into())
        .await
        .is_ok();

    let all_ok = db_ok && redis_ok && nats_ok;

    let status_code = if all_ok {
        StatusCode::OK
    } else {
        StatusCode::SERVICE_UNAVAILABLE
    };

    let body = json!({
        "status": if all_ok { "ok" } else { "error" },
        "database": if db_ok { "ok" } else { "error" },
        "redis": if redis_ok { "ok" } else { "error" },
        "nats": if nats_ok { "ok" } else { "error" },
    });

    (status_code, Json(body))
}
