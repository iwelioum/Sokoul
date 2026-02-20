use axum::{
    body::Body,
    http::{Request, Response, StatusCode},
    response::IntoResponse,
    Json,
};
use serde_json::json;
use std::{
    collections::HashMap,
    future::Future,
    net::IpAddr,
    pin::Pin,
    sync::{Arc, Mutex},
    task::{Context, Poll},
    time::Instant,
};
use tower::{Layer, Service};

/// Simple sliding-window rate limiter per IP address.
#[derive(Clone)]
pub struct RateLimitLayer {
    max_requests: u32,
    window_secs: u64,
    state: Arc<Mutex<HashMap<IpAddr, Vec<Instant>>>>,
}

impl RateLimitLayer {
    pub fn new(max_requests: u32, window_secs: u64) -> Self {
        Self {
            max_requests,
            window_secs,
            state: Arc::new(Mutex::new(HashMap::new())),
        }
    }
}

impl<S> Layer<S> for RateLimitLayer {
    type Service = RateLimitService<S>;

    fn layer(&self, inner: S) -> Self::Service {
        RateLimitService {
            inner,
            max_requests: self.max_requests,
            window_secs: self.window_secs,
            state: self.state.clone(),
        }
    }
}

#[derive(Clone)]
pub struct RateLimitService<S> {
    inner: S,
    max_requests: u32,
    window_secs: u64,
    state: Arc<Mutex<HashMap<IpAddr, Vec<Instant>>>>,
}

impl<S> Service<Request<Body>> for RateLimitService<S>
where
    S: Service<Request<Body>, Response = Response<Body>> + Clone + Send + 'static,
    S::Future: Send + 'static,
{
    type Response = S::Response;
    type Error = S::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        self.inner.poll_ready(cx)
    }

    fn call(&mut self, req: Request<Body>) -> Self::Future {
        // Extract client IP from X-Forwarded-For, X-Real-IP, or connection
        let ip = extract_client_ip(&req);
        let now = Instant::now();
        let window = std::time::Duration::from_secs(self.window_secs);
        let max = self.max_requests;

        let allowed = if let Some(ip) = ip {
            let mut state = self.state.lock().unwrap();
            let entries = state.entry(ip).or_default();
            entries.retain(|t| now.duration_since(*t) < window);
            if entries.len() >= max as usize {
                false
            } else {
                entries.push(now);
                true
            }
        } else {
            true // can't determine IP, allow
        };

        if !allowed {
            return Box::pin(async move {
                let resp = (
                    StatusCode::TOO_MANY_REQUESTS,
                    Json(json!({"error": "Rate limit exceeded. Try again later."})),
                )
                    .into_response();
                Ok(resp)
            });
        }

        let future = self.inner.call(req);
        Box::pin(future)
    }
}

fn extract_client_ip(req: &Request<Body>) -> Option<IpAddr> {
    // X-Forwarded-For (first IP in the chain)
    if let Some(xff) = req
        .headers()
        .get("X-Forwarded-For")
        .and_then(|v| v.to_str().ok())
    {
        if let Some(first) = xff.split(',').next() {
            if let Ok(ip) = first.trim().parse::<IpAddr>() {
                return Some(ip);
            }
        }
    }

    // X-Real-IP
    if let Some(xri) = req.headers().get("X-Real-IP").and_then(|v| v.to_str().ok()) {
        if let Ok(ip) = xri.trim().parse::<IpAddr>() {
            return Some(ip);
        }
    }

    None
}
