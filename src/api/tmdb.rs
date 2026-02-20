use crate::{
    api::error::ApiError,
    cache::{get_from_cache, set_to_cache_with_ttl},
    clients::tmdb::DiscoverParams,
    AppState,
};
use axum::{
    extract::{Path, Query, State},
    routing::get,
    Json, Router,
};
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use std::sync::Arc;
use tracing::error;

/// Common query param to select TMDB language per request.
#[derive(Debug, Deserialize, Default)]
pub(crate) struct LangParam {
    lang: Option<String>,
}

impl LangParam {
    fn as_str(&self) -> &str {
        self.lang.as_deref().unwrap_or("fr-FR")
    }
}

pub fn tmdb_routes() -> Router<Arc<AppState>> {
    Router::new()
        .route(
            "/tmdb/trending/:media_type/:time_window",
            get(trending_handler),
        )
        .route("/tmdb/discover/:media_type", get(discover_handler))
        .route("/tmdb/movie/:id", get(movie_details_handler))
        .route("/tmdb/tv/:id", get(tv_details_handler))
        .route(
            "/tmdb/tv/:tv_id/season/:season_number",
            get(season_details_handler),
        )
        .route("/tmdb/:media_type/:id/credits", get(credits_handler))
        .route("/tmdb/:media_type/:id/videos", get(videos_handler))
        .route(
            "/tmdb/:media_type/:id/watch/providers",
            get(watch_providers_handler),
        )
        .route("/tmdb/person/:id", get(person_details_handler))
        .route(
            "/tmdb/person/:id/credits",
            get(person_combined_credits_handler),
        )
        .route("/tmdb/:media_type/:id/similar", get(similar_handler))
        .route("/tmdb/search", get(search_handler))
        .route("/tmdb/collection/:id", get(collection_handler))
        .route(
            "/tmdb/movie/:id/certification",
            get(movie_certification_handler),
        )
        .route("/tmdb/tv/:id/certification", get(tv_certification_handler))
}

async fn handle_cache<T: Serialize + DeserializeOwned>(
    state: &Arc<AppState>,
    key: String,
    ttl: usize,
    fetcher: impl std::future::Future<Output = Result<T, reqwest::Error>>,
) -> Result<Json<T>, ApiError> {
    if let Ok(Some(cached)) = get_from_cache::<T>(&state.redis_client, &key).await {
        return Ok(Json(cached));
    }

    match fetcher.await {
        Ok(data) => {
            if let Err(e) =
                set_to_cache_with_ttl(&state.redis_client, &key, &data, ttl as u64).await
            {
                error!("Failed to cache data for key {}: {}", key, e);
            }
            Ok(Json(data))
        }
        Err(e) => {
            error!("TMDB API error for key {}: {}", key, e);
            Err(ApiError::InternalServerError)
        }
    }
}

pub async fn trending_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, time_window)): Path<(String, String)>,
    Query(lq): Query<LangParam>,
) -> Result<Json<Vec<crate::clients::tmdb::TmdbSearchResult>>, ApiError> {
    let lang = lq.as_str();
    let key = format!("tmdb:trending:{}:{}:{}", media_type, time_window, lang);
    let client = state.tmdb_client.with_language(lang);
    handle_cache(&state, key, 3600, async {
        client.trending(&media_type, &time_window).await
    })
    .await
}

pub async fn discover_handler(
    State(state): State<Arc<AppState>>,
    Path(media_type): Path<String>,
    Query(params): Query<DiscoverParams>,
    Query(lq): Query<LangParam>,
) -> Result<Json<crate::clients::tmdb::TmdbPaginatedResponse>, ApiError> {
    let lang = lq.as_str();
    let key = format!(
        "tmdb:discover:{}:{}:{}:{}:{}:{}:{}:{}",
        media_type,
        params.with_watch_providers.as_deref().unwrap_or(""),
        params.watch_region.as_deref().unwrap_or(""),
        params.with_genres.as_deref().unwrap_or(""),
        params.sort_by.as_deref().unwrap_or(""),
        params.page.unwrap_or(1),
        params.vote_average_gte.unwrap_or(0.0) as u8,
        lang
    );
    let client = state.tmdb_client.with_language(lang);
    handle_cache(&state, key, 3600, async {
        client.discover(&media_type, &params).await
    })
    .await
}

pub async fn movie_details_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
    Query(lq): Query<LangParam>,
) -> Result<Json<crate::clients::tmdb::TmdbMovieDetail>, ApiError> {
    let lang = lq.as_str();
    let key = format!("tmdb:movie:{}:{}", id, lang);
    let client = state.tmdb_client.with_language(lang);
    handle_cache(&state, key, 86400, async { client.movie_details(id).await }).await
}

pub async fn tv_details_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
    Query(lq): Query<LangParam>,
) -> Result<Json<crate::clients::tmdb::TmdbTvDetail>, ApiError> {
    let lang = lq.as_str();
    let key = format!("tmdb:tv:{}:{}", id, lang);
    let client = state.tmdb_client.with_language(lang);
    handle_cache(&state, key, 86400, async { client.tv_details(id).await }).await
}

pub async fn season_details_handler(
    State(state): State<Arc<AppState>>,
    Path((tv_id, season_number)): Path<(i32, i32)>,
) -> Result<Json<crate::clients::tmdb::TmdbSeasonDetail>, ApiError> {
    let key = format!("tmdb:tv:{}:season:{}", tv_id, season_number);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.season_details(tv_id, season_number).await
    })
    .await
}

pub async fn credits_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, id)): Path<(String, i32)>,
) -> Result<Json<crate::clients::tmdb::TmdbCreditsResponse>, ApiError> {
    let key = format!("tmdb:credits:{}:{}", media_type, id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.credits(&media_type, id).await
    })
    .await
}

pub async fn videos_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, id)): Path<(String, i32)>,
) -> Result<Json<Vec<crate::clients::tmdb::TmdbVideo>>, ApiError> {
    let key = format!("tmdb:videos:{}:{}", media_type, id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.videos(&media_type, id).await
    })
    .await
}

pub async fn watch_providers_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, id)): Path<(String, i32)>,
) -> Result<Json<Option<crate::clients::tmdb::TmdbWatchProviderCountry>>, ApiError> {
    let key = format!("tmdb:watch_providers:{}:{}", media_type, id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.watch_providers(&media_type, id).await
    })
    .await
}

pub async fn person_details_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
) -> Result<Json<crate::clients::tmdb::TmdbPersonDetail>, ApiError> {
    let key = format!("tmdb:person:{}", id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.person_details(id).await
    })
    .await
}

pub async fn person_combined_credits_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
) -> Result<Json<Vec<crate::clients::tmdb::TmdbPersonCredit>>, ApiError> {
    let key = format!("tmdb:person_credits:{}", id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.person_combined_credits(id).await
    })
    .await
}

pub async fn similar_handler(
    State(state): State<Arc<AppState>>,
    Path((media_type, id)): Path<(String, i32)>,
) -> Result<Json<Vec<crate::clients::tmdb::TmdbSearchResult>>, ApiError> {
    let key = format!("tmdb:similar:{}:{}", media_type, id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.similar(&media_type, id).await
    })
    .await
}

#[derive(Debug, Deserialize, Serialize)]
pub struct SearchQuery {
    query: String,
}

pub async fn search_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<SearchQuery>,
    Query(lq): Query<LangParam>,
) -> Result<Json<Vec<crate::clients::tmdb::TmdbSearchResult>>, ApiError> {
    let lang = lq.as_str();
    let key = format!("tmdb:search:{}:{}", query.query, lang);
    let client = state.tmdb_client.with_language(lang);
    handle_cache(&state, key, 1800, async {
        client.search_multi(&query.query).await
    })
    .await
}

pub async fn collection_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
    Query(lq): Query<LangParam>,
) -> Result<Json<crate::clients::tmdb::TmdbCollection>, ApiError> {
    let lang = lq.as_str();
    let key = format!("tmdb:collection:{}:{}", id, lang);
    let client = state.tmdb_client.with_language(lang);
    handle_cache(&state, key, 86400, async { client.collection(id).await }).await
}

pub async fn movie_certification_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
) -> Result<Json<Option<String>>, ApiError> {
    let key = format!("tmdb:cert:movie:{}", id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.movie_certification(id).await
    })
    .await
}

pub async fn tv_certification_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i32>,
) -> Result<Json<Option<String>>, ApiError> {
    let key = format!("tmdb:cert:tv:{}", id);
    handle_cache(&state, key, 86400, async {
        state.tmdb_client.tv_certification(id).await
    })
    .await
}
