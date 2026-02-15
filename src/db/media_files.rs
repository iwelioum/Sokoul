use crate::models::MediaFile;
use sqlx::PgPool;
use uuid::Uuid;

pub async fn create_media_file(
    pool: &PgPool,
    media_id: Uuid,
    file_path: &str,
    source: &str,
) -> Result<MediaFile, sqlx::Error> {
    let file = sqlx::query_as::<_, MediaFile>(
        r#"
        INSERT INTO media_files (media_id, file_path, source)
        VALUES ($1, $2, $3)
        ON CONFLICT (file_path) DO UPDATE SET media_id = EXCLUDED.media_id
        RETURNING *
        "#,
    )
    .bind(media_id)
    .bind(file_path)
    .bind(source)
    .fetch_one(pool)
    .await?;

    Ok(file)
}

pub async fn get_files_by_media_id(
    pool: &PgPool,
    media_id: Uuid,
) -> Result<Vec<MediaFile>, sqlx::Error> {
    let files = sqlx::query_as::<_, MediaFile>(
        "SELECT * FROM media_files WHERE media_id = $1 ORDER BY downloaded_at DESC",
    )
    .bind(media_id)
    .fetch_all(pool)
    .await?;

    Ok(files)
}

pub async fn get_file_by_id(pool: &PgPool, file_id: Uuid) -> Result<MediaFile, sqlx::Error> {
    let file = sqlx::query_as::<_, MediaFile>("SELECT * FROM media_files WHERE id = $1")
        .bind(file_id)
        .fetch_one(pool)
        .await?;

    Ok(file)
}
