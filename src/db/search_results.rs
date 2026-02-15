use crate::models::SearchResult;
use crate::providers::TorrentResult;
use sqlx::{PgPool, QueryBuilder};
use uuid::Uuid;

pub async fn create_batch(
    pool: &PgPool,
    media_id: Uuid,
    results: &[TorrentResult],
) -> Result<u64, sqlx::Error> {
    if results.is_empty() {
        return Ok(0);
    }

    let mut query_builder: QueryBuilder<sqlx::Postgres> = QueryBuilder::new(
        "INSERT INTO search_results (media_id, provider, title, guid, url, magnet_link, info_hash, protocol, size_bytes, seeders, leechers) ",
    );

    query_builder.push_values(results.iter(), |mut b, result| {
        // Use download_url first (Prowlarr proxy link), fallback to info_url
        let url = result.download_url.as_ref().or(result.info_url.as_ref());
        b.push_bind(media_id)
            .push_bind(&result.provider_name)
            .push_bind(&result.title)
            .push_bind(&result.guid)
            .push_bind(url)
            .push_bind(&result.magnet_url)
            .push_bind(&result.info_hash)
            .push_bind(result.protocol.as_deref().unwrap_or("torrent"))
            .push_bind(result.size_bytes)
            .push_bind(result.seeders.unwrap_or(0))
            .push_bind(result.leechers.unwrap_or(0));
    });

    query_builder.push(" ON CONFLICT (media_id, guid) DO NOTHING");

    let query = query_builder.build();
    let result = query.execute(pool).await?;

    Ok(result.rows_affected())
}

pub async fn get_results_by_media_id(
    pool: &PgPool,
    media_id: Uuid,
) -> Result<Vec<SearchResult>, sqlx::Error> {
    let results = sqlx::query_as::<_, SearchResult>(
        "SELECT * FROM search_results WHERE media_id = $1 AND (expires_at IS NULL OR expires_at > NOW()) ORDER BY seeders DESC, score DESC NULLS LAST",
    )
    .bind(media_id)
    .fetch_all(pool)
    .await?;

    Ok(results)
}

pub async fn update_score(
    pool: &PgPool,
    id: i32,
    score: i32,
    ai_validated: bool,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "UPDATE search_results SET score = $1, ai_validated = $2 WHERE id = $3",
    )
    .bind(score)
    .bind(ai_validated)
    .bind(id)
    .execute(pool)
    .await?;

    Ok(())
}

pub async fn delete_expired(pool: &PgPool) -> Result<u64, sqlx::Error> {
    let result = sqlx::query("DELETE FROM search_results WHERE expires_at < NOW()")
        .execute(pool)
        .await?;

    Ok(result.rows_affected())
}
