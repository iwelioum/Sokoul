use crate::{api::error::ApiError, db, AppState};
use axum::{
    body::Body,
    extract::{Path, State},
    http::{header, StatusCode},
    response::Response,
    Json,
};
use std::sync::Arc;
use tokio_util::io::ReaderStream;
use uuid::Uuid;

/// GET /files/:file_id/stream - Stream a media file
pub async fn stream_file_handler(
    State(state): State<Arc<AppState>>,
    Path(file_id): Path<Uuid>,
) -> Result<Response, ApiError> {
    let file = db::media_files::get_file_by_id(&state.db_pool, file_id).await?;

    let path = std::path::Path::new(&file.file_path);
    if !path.exists() {
        return Err(ApiError::NotFound("Fichier introuvable sur le disque".into()));
    }

    let tokio_file = tokio::fs::File::open(path)
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Impossible d'ouvrir le fichier: {}", e)))?;

    let metadata = tokio_file
        .metadata()
        .await
        .map_err(|e| ApiError::Internal(anyhow::anyhow!("Metadata error: {}", e)))?;

    let file_size = metadata.len();
    let stream = ReaderStream::new(tokio_file);
    let body = Body::from_stream(stream);

    let filename = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("video.mp4");

    let content_type = guess_content_type(filename);

    Ok(Response::builder()
        .status(StatusCode::OK)
        .header(header::CONTENT_TYPE, content_type)
        .header(header::CONTENT_LENGTH, file_size)
        .header(
            header::CONTENT_DISPOSITION,
            format!("inline; filename=\"{}\"", filename),
        )
        .header(header::ACCEPT_RANGES, "bytes")
        .body(body)
        .unwrap())
}

/// GET /files/:file_id/info - Get file info for the player
pub async fn file_info_handler(
    State(state): State<Arc<AppState>>,
    Path(file_id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let file = db::media_files::get_file_by_id(&state.db_pool, file_id).await?;

    let path = std::path::Path::new(&file.file_path);
    let exists = path.exists();
    let filename = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("unknown");

    Ok(Json(serde_json::json!({
        "id": file.id,
        "media_id": file.media_id,
        "file_path": file.file_path,
        "file_size": file.file_size,
        "codec_video": file.codec_video,
        "codec_audio": file.codec_audio,
        "resolution": file.resolution,
        "exists": exists,
        "filename": filename,
        "stream_url": format!("/api/files/{}/stream", file.id),
        "content_type": guess_content_type(filename),
    })))
}

fn guess_content_type(filename: &str) -> &'static str {
    let ext = filename
        .rsplit('.')
        .next()
        .unwrap_or("")
        .to_lowercase();
    match ext.as_str() {
        "mp4" | "m4v" => "video/mp4",
        "mkv" => "video/x-matroska",
        "avi" => "video/x-msvideo",
        "webm" => "video/webm",
        "mov" => "video/quicktime",
        "ts" => "video/mp2t",
        "flv" => "video/x-flv",
        "wmv" => "video/x-ms-wmv",
        "mp3" => "audio/mpeg",
        "flac" => "audio/flac",
        "aac" => "audio/aac",
        "srt" => "text/plain",
        "vtt" => "text/vtt",
        "ass" | "ssa" => "text/plain",
        _ => "application/octet-stream",
    }
}
