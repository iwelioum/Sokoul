use crate::{
    api::error::ApiError,
    db,
    models::{CreateTaskPayload, Task},
    AppState,
};
use axum::{
    extract::{Path, State},
    http::StatusCode,
    Json,
};
use std::sync::Arc;
use uuid::Uuid;

pub async fn create_task_handler(
    State(state): State<Arc<AppState>>,
    Json(payload): Json<CreateTaskPayload>,
) -> Result<(StatusCode, Json<Task>), ApiError> {
    tracing::info!("Creation d'une tache de type: {}", payload.task_type);
    let task = db::tasks::create_task(&state.db_pool, &payload).await?;
    Ok((StatusCode::CREATED, Json(task)))
}

pub async fn get_task_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<Uuid>,
) -> Result<Json<Task>, ApiError> {
    let task = db::tasks::get_task_by_id(&state.db_pool, id).await?;
    Ok(Json(task))
}

pub async fn list_tasks_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<Task>>, ApiError> {
    let tasks = db::tasks::list_recent(&state.db_pool, 50).await?;
    Ok(Json(tasks))
}
