use crate::models::{CreateTaskPayload, Task};
use sqlx::PgPool;
use uuid::Uuid;

pub async fn create_task(pool: &PgPool, payload: &CreateTaskPayload) -> Result<Task, sqlx::Error> {
    let task = sqlx::query_as::<_, Task>(
        r#"
        INSERT INTO tasks (task_type, payload)
        VALUES ($1, $2)
        RETURNING *
        "#,
    )
    .bind(&payload.task_type)
    .bind(&payload.payload)
    .fetch_one(pool)
    .await?;

    Ok(task)
}

pub async fn get_task_by_id(pool: &PgPool, id: Uuid) -> Result<Task, sqlx::Error> {
    let task = sqlx::query_as::<_, Task>("SELECT * FROM tasks WHERE id = $1")
        .bind(id)
        .fetch_one(pool)
        .await?;

    Ok(task)
}

pub async fn update_task_status(
    pool: &PgPool,
    id: Uuid,
    status: &str,
    error: Option<&str>,
) -> Result<Task, sqlx::Error> {
    let task = sqlx::query_as::<_, Task>(
        r#"
        UPDATE tasks
        SET status = $1,
            error = $2,
            started_at = CASE WHEN $1 = 'running' THEN NOW() ELSE started_at END,
            completed_at = CASE WHEN $1 IN ('completed', 'failed', 'cancelled') THEN NOW() ELSE completed_at END
        WHERE id = $3
        RETURNING *
        "#,
    )
    .bind(status)
    .bind(error)
    .bind(id)
    .fetch_one(pool)
    .await?;

    Ok(task)
}

pub async fn update_task_progress(
    pool: &PgPool,
    id: Uuid,
    progress: rust_decimal::Decimal,
) -> Result<Task, sqlx::Error> {
    let task = sqlx::query_as::<_, Task>(
        r#"
        UPDATE tasks
        SET progress = $1, updated_at = NOW()
        WHERE id = $2
        RETURNING *
        "#,
    )
    .bind(progress)
    .bind(id)
    .fetch_one(pool)
    .await?;

    Ok(task)
}

pub async fn list_recent(pool: &PgPool, limit: i64) -> Result<Vec<Task>, sqlx::Error> {
    let tasks = sqlx::query_as::<_, Task>("SELECT * FROM tasks ORDER BY created_at DESC LIMIT $1")
        .bind(limit)
        .fetch_all(pool)
        .await?;

    Ok(tasks)
}

pub async fn complete_task(
    pool: &PgPool,
    id: Uuid,
    result: Option<serde_json::Value>,
) -> Result<Task, sqlx::Error> {
    let task = sqlx::query_as::<_, Task>(
        r#"
        UPDATE tasks
        SET status = 'completed', result = $1, progress = 100, completed_at = NOW()
        WHERE id = $2
        RETURNING *
        "#,
    )
    .bind(result)
    .bind(id)
    .fetch_one(pool)
    .await?;

    Ok(task)
}
