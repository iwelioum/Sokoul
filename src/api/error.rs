use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum ApiError {
    #[error("Erreur interne du serveur: {0}")]
    Internal(#[from] anyhow::Error),

    #[error("Erreur de base de données")]
    Database(#[source] sqlx::Error),

    #[error("Entrée invalide: {0}")]
    InvalidInput(String),

    #[error("Ressource non trouvée: {0}")]
    NotFound(String),

    #[error("Erreur du bus de messages: {0}")]
    MessageBus(String),

    #[error("Erreur interne du serveur")]
    InternalServerError,
}

impl From<sqlx::Error> for ApiError {
    fn from(err: sqlx::Error) -> Self {
        match err {
            sqlx::Error::RowNotFound => {
                ApiError::NotFound("La ressource demandée n'a pas été trouvée.".to_string())
            }
            _ => ApiError::Database(err),
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, error_message) = match self {
            ApiError::Database(e) => {
                tracing::error!("Erreur de base de données: {:?}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "Une erreur de base de données est survenue.".to_string(),
                )
            }
            ApiError::Internal(e) => {
                tracing::error!("Erreur interne: {:?}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "Une erreur interne est survenue.".to_string(),
                )
            }
            ApiError::InvalidInput(msg) => (StatusCode::BAD_REQUEST, msg),
            ApiError::NotFound(msg) => (StatusCode::NOT_FOUND, msg),
            ApiError::MessageBus(e) => {
                tracing::error!("Erreur du bus de messages: {:?}", e);
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "Une erreur interne est survenue lors de la communication avec les workers."
                        .to_string(),
                )
            }
            ApiError::InternalServerError => {
                tracing::error!("Erreur interne du serveur sans détail spécifique.");
                (
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "Une erreur interne inattendue est survenue.".to_string(),
                )
            }
        };

        let body = Json(json!({ "error": error_message }));
        (status, body).into_response()
    }
}
