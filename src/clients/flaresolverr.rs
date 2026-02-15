use anyhow::{anyhow, Result};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use tracing::{debug, error, info, instrument};

const FLARESOLVERR_DEFAULT_TIMEOUT: u64 = 60; // seconds

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct FlareSolverrRequest {
    cmd: String,
    url: String,
    max_timeout: u64,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FlareSolverrResponse {
    solution: Option<FlareSolverrSolution>,
    message: Option<String>,
    status: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct FlareSolverrSolution {
    _url: String,
    status: u16,
    _headers: Option<serde_json::Value>,
    response: Option<String>,
    // Add other fields as needed, e.g., "cookies"
}

#[derive(Clone)]
pub struct FlareSolverrClient {
    client: Client,
    base_url: String,
}

impl FlareSolverrClient {
    pub fn new(base_url: String) -> Self {
        Self {
            client: Client::new(),
            base_url,
        }
    }

    #[instrument(skip(self, url), fields(flaresolverr_url = %self.base_url, target_url = %url))]
    pub async fn get(&self, url: &str) -> Result<String> {
        if self.base_url.is_empty() {
            return Err(anyhow!("FlareSolverr URL is not configured."));
        }

        info!("Sending request to FlareSolverr for URL: {}", url);

        let flaresolverr_request = FlareSolverrRequest {
            cmd: "request.get".to_string(),
            url: url.to_string(),
            max_timeout: FLARESOLVERR_DEFAULT_TIMEOUT * 1000, // ms
        };

        let request_url = format!("{}/v1", self.base_url);

        let resp = self
            .client
            .post(&request_url)
            .json(&flaresolverr_request)
            .send()
            .await?
            .error_for_status()?;

        let flaresolverr_response: FlareSolverrResponse = resp.json().await?;

        debug!(
            "FlareSolverr response status: {}",
            flaresolverr_response.status
        );

        if flaresolverr_response.status != "ok" {
            error!(
                "FlareSolverr returned an error: {:?}",
                flaresolverr_response.message
            );
            return Err(anyhow!(
                "FlareSolverr error: {}",
                flaresolverr_response
                    .message
                    .unwrap_or_else(|| "Unknown error".to_string())
            ));
        }

        match flaresolverr_response.solution {
            Some(solution) => {
                debug!("FlareSolverr solution status: {}", solution.status);
                if solution.status >= 400 {
                    return Err(anyhow!(
                        "FlareSolverr solution returned an error status: {}",
                        solution.status
                    ));
                }
                solution
                    .response
                    .ok_or_else(|| anyhow!("No response from FlareSolverr solution"))
            }
            None => Err(anyhow!("No solution found in FlareSolverr response")),
        }
    }
}
