use crate::{
    api::error::ApiError,
    cache::{get_from_cache, set_to_cache_with_ttl},
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

pub fn enrichment_routes() -> Router<Arc<AppState>> {
    Router::new()
        // Fanart.tv
        .route("/fanart/movie/:tmdb_id", get(fanart_movie_handler))
        .route("/fanart/tv/:tvdb_id", get(fanart_tv_handler))
        // OMDb
        .route("/omdb/imdb/:imdb_id", get(omdb_by_imdb_handler))
        .route("/omdb/search", get(omdb_search_handler))
        // TheTVDB
        .route("/thetvdb/series/:tvdb_id", get(thetvdb_series_handler))
        .route(
            "/thetvdb/series/:tvdb_id/episodes",
            get(thetvdb_episodes_handler),
        )
        .route(
            "/thetvdb/series/:tvdb_id/artworks",
            get(thetvdb_artworks_handler),
        )
        .route("/thetvdb/search", get(thetvdb_search_handler))
        // TVMaze
        .route("/tvmaze/search", get(tvmaze_search_handler))
        .route("/tvmaze/show/:show_id", get(tvmaze_show_handler))
        .route(
            "/tvmaze/show/:show_id/episodes",
            get(tvmaze_episodes_handler),
        )
        .route("/tvmaze/show/:show_id/cast", get(tvmaze_cast_handler))
        .route(
            "/tvmaze/lookup/tvdb/:tvdb_id",
            get(tvmaze_lookup_tvdb_handler),
        )
        // Jikan (Anime)
        .route("/jikan/anime/search", get(jikan_search_handler))
        .route("/jikan/anime/:mal_id", get(jikan_anime_handler))
        .route("/jikan/anime/top", get(jikan_top_handler))
        .route("/jikan/anime/season", get(jikan_season_handler))
        .route(
            "/jikan/anime/:mal_id/characters",
            get(jikan_characters_handler),
        )
        .route(
            "/jikan/anime/:mal_id/recommendations",
            get(jikan_recommendations_handler),
        )
        // Trakt
        .route("/trakt/movies/trending", get(trakt_trending_movies_handler))
        .route("/trakt/movies/popular", get(trakt_popular_movies_handler))
        .route("/trakt/shows/trending", get(trakt_trending_shows_handler))
        .route("/trakt/shows/popular", get(trakt_popular_shows_handler))
        .route(
            "/trakt/movies/:id/related",
            get(trakt_related_movies_handler),
        )
        .route("/trakt/shows/:id/related", get(trakt_related_shows_handler))
        // TasteDive
        .route("/tastedive/similar", get(tastedive_similar_handler))
        // Watchmode
        .route("/watchmode/search", get(watchmode_search_handler))
        .route(
            "/watchmode/title/:id/sources",
            get(watchmode_sources_handler),
        )
        .route(
            "/watchmode/sources/tmdb/:tmdb_id/:media_type",
            get(watchmode_tmdb_sources_handler),
        )
        // IMDbOT
        .route("/imdbbot/search", get(imdbbot_search_handler))
        .route("/imdbbot/:imdb_id", get(imdbbot_details_handler))
        .route("/imdbbot/:imdb_id/ratings", get(imdbbot_ratings_handler))
        // Simkl
        .route("/simkl/search", get(simkl_search_handler))
        .route("/simkl/:id/:item_type", get(simkl_details_handler))
        .route("/simkl/trending/:item_type", get(simkl_trending_handler))
        .route("/simkl/:id/sources", get(simkl_sources_handler))
        // uNoGS (Netflix)
        .route("/unogs/search", get(unogs_search_handler))
        .route("/unogs/imdb/:imdb_id", get(unogs_imdb_handler))
        .route("/unogs/regions", get(unogs_regions_handler))
        .route(
            "/unogs/region/:region/search",
            get(unogs_region_search_handler),
        )
        // Stream
        .route("/stream/search", get(stream_search_handler))
        .route("/stream/:id", get(stream_details_handler))
        .route("/stream/category/:category", get(stream_category_handler))
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
            error!("Enrichment API error for key {}: {}", key, e);
            Err(ApiError::InternalServerError)
        }
    }
}

// ─── Fanart.tv ───

pub async fn fanart_movie_handler(
    State(state): State<Arc<AppState>>,
    Path(tmdb_id): Path<i32>,
) -> Result<Json<crate::clients::fanart::FanartMovieImages>, ApiError> {
    let client = state
        .fanart_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Fanart.tv not configured"))?;
    let key = format!("fanart:movie:{}", tmdb_id);
    handle_cache(&state, key, 86400, async {
        client.movie_images(tmdb_id).await
    })
    .await
}

pub async fn fanart_tv_handler(
    State(state): State<Arc<AppState>>,
    Path(tvdb_id): Path<i32>,
) -> Result<Json<crate::clients::fanart::FanartTvImages>, ApiError> {
    let client = state
        .fanart_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Fanart.tv not configured"))?;
    let key = format!("fanart:tv:{}", tvdb_id);
    handle_cache(&state, key, 86400, async {
        client.tv_images(tvdb_id).await
    })
    .await
}

// ─── OMDb ───

pub async fn omdb_by_imdb_handler(
    State(state): State<Arc<AppState>>,
    Path(imdb_id): Path<String>,
) -> Result<Json<crate::clients::omdb::OmdbResponse>, ApiError> {
    let client = state
        .omdb_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("OMDb not configured"))?;
    let key = format!("omdb:imdb:{}", imdb_id);
    handle_cache(&state, key, 86400, async {
        client.get_by_imdb_id(&imdb_id).await
    })
    .await
}

#[derive(Debug, Deserialize)]
pub struct OmdbSearchQuery {
    title: String,
    year: Option<i32>,
}

pub async fn omdb_search_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<OmdbSearchQuery>,
) -> Result<Json<crate::clients::omdb::OmdbResponse>, ApiError> {
    let client = state
        .omdb_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("OMDb not configured"))?;
    let key = format!("omdb:search:{}:{}", query.title, query.year.unwrap_or(0));
    handle_cache(&state, key, 86400, async {
        client.search_by_title(&query.title, query.year).await
    })
    .await
}

// ─── TheTVDB ───

pub async fn thetvdb_series_handler(
    State(state): State<Arc<AppState>>,
    Path(tvdb_id): Path<i64>,
) -> Result<Json<Option<crate::clients::thetvdb::ThetvdbSeries>>, ApiError> {
    let client = state
        .thetvdb_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("TheTVDB not configured"))?;
    let key = format!("thetvdb:series:{}", tvdb_id);
    handle_cache(&state, key, 86400, async {
        client.series_details(tvdb_id).await
    })
    .await
}

#[derive(Debug, Deserialize)]
pub struct EpisodesQuery {
    season: Option<i32>,
}

pub async fn thetvdb_episodes_handler(
    State(state): State<Arc<AppState>>,
    Path(tvdb_id): Path<i64>,
    Query(query): Query<EpisodesQuery>,
) -> Result<Json<Vec<crate::clients::thetvdb::ThetvdbEpisode>>, ApiError> {
    let client = state
        .thetvdb_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("TheTVDB not configured"))?;
    let key = format!(
        "thetvdb:episodes:{}:{}",
        tvdb_id,
        query.season.unwrap_or(-1)
    );
    handle_cache(&state, key, 86400, async {
        client.series_episodes(tvdb_id, query.season).await
    })
    .await
}

pub async fn thetvdb_artworks_handler(
    State(state): State<Arc<AppState>>,
    Path(tvdb_id): Path<i64>,
) -> Result<Json<Vec<crate::clients::thetvdb::ThetvdbArtwork>>, ApiError> {
    let client = state
        .thetvdb_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("TheTVDB not configured"))?;
    let key = format!("thetvdb:artworks:{}", tvdb_id);
    handle_cache(&state, key, 86400, async {
        client.series_artworks(tvdb_id).await
    })
    .await
}

#[derive(Debug, Deserialize)]
pub struct ThetvdbSearchQuery {
    query: String,
}

pub async fn thetvdb_search_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<ThetvdbSearchQuery>,
) -> Result<Json<Vec<crate::clients::thetvdb::ThetvdbSeries>>, ApiError> {
    let client = state
        .thetvdb_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("TheTVDB not configured"))?;
    let key = format!("thetvdb:search:{}", query.query);
    handle_cache(&state, key, 3600, async {
        client.search(&query.query).await
    })
    .await
}

// ─── TVMaze ───

#[derive(Debug, Deserialize)]
pub struct TvMazeSearchQuery {
    query: String,
}

async fn tvmaze_search_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<TvMazeSearchQuery>,
) -> Result<Json<Vec<crate::clients::tvmaze::TvMazeSearchResult>>, ApiError> {
    let key = format!("tvmaze:search:{}", query.query);
    handle_cache(&state, key, 3600, async {
        state.tvmaze_client.search(&query.query).await
    })
    .await
}

async fn tvmaze_show_handler(
    State(state): State<Arc<AppState>>,
    Path(show_id): Path<i64>,
) -> Result<Json<Option<crate::clients::tvmaze::TvMazeShow>>, ApiError> {
    let key = format!("tvmaze:show:{}", show_id);
    handle_cache(&state, key, 86400, async {
        state.tvmaze_client.show_details(show_id).await
    })
    .await
}

async fn tvmaze_episodes_handler(
    State(state): State<Arc<AppState>>,
    Path(show_id): Path<i64>,
) -> Result<Json<Vec<crate::clients::tvmaze::TvMazeEpisode>>, ApiError> {
    let key = format!("tvmaze:episodes:{}", show_id);
    handle_cache(&state, key, 86400, async {
        state.tvmaze_client.episodes(show_id).await
    })
    .await
}

async fn tvmaze_cast_handler(
    State(state): State<Arc<AppState>>,
    Path(show_id): Path<i64>,
) -> Result<Json<Vec<crate::clients::tvmaze::TvMazeCastMember>>, ApiError> {
    let key = format!("tvmaze:cast:{}", show_id);
    handle_cache(&state, key, 86400, async {
        state.tvmaze_client.cast(show_id).await
    })
    .await
}

async fn tvmaze_lookup_tvdb_handler(
    State(state): State<Arc<AppState>>,
    Path(tvdb_id): Path<i64>,
) -> Result<Json<Option<crate::clients::tvmaze::TvMazeShow>>, ApiError> {
    let key = format!("tvmaze:lookup:tvdb:{}", tvdb_id);
    handle_cache(&state, key, 86400, async {
        state.tvmaze_client.lookup_by_tvdb(tvdb_id).await
    })
    .await
}

// ─── Jikan (Anime) ───

#[derive(Debug, Deserialize)]
pub struct JikanSearchQuery {
    query: String,
    page: Option<u32>,
}

async fn jikan_search_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<JikanSearchQuery>,
) -> Result<
    Json<crate::clients::jikan::JikanResponse<Vec<crate::clients::jikan::JikanAnime>>>,
    ApiError,
> {
    let page = query.page.unwrap_or(1);
    let key = format!("jikan:search:{}:{}", query.query, page);
    handle_cache(&state, key, 3600, async {
        state.jikan_client.search_anime(&query.query, page).await
    })
    .await
}

async fn jikan_anime_handler(
    State(state): State<Arc<AppState>>,
    Path(mal_id): Path<i64>,
) -> Result<Json<Option<crate::clients::jikan::JikanAnime>>, ApiError> {
    let key = format!("jikan:anime:{}", mal_id);
    handle_cache(&state, key, 86400, async {
        state.jikan_client.anime_details(mal_id).await
    })
    .await
}

#[derive(Debug, Deserialize)]
pub struct JikanTopQuery {
    filter: Option<String>,
    page: Option<u32>,
    limit: Option<u32>,
}

async fn jikan_top_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<JikanTopQuery>,
) -> Result<
    Json<crate::clients::jikan::JikanResponse<Vec<crate::clients::jikan::JikanAnime>>>,
    ApiError,
> {
    let filter = query.filter.as_deref().unwrap_or("bypopularity");
    let page = query.page.unwrap_or(1);
    let limit = query.limit.unwrap_or(25).min(25);
    let key = format!("jikan:top:{}:{}:{}", filter, page, limit);
    handle_cache(&state, key, 3600, async {
        state.jikan_client.top_anime(filter, page, limit).await
    })
    .await
}

#[derive(Debug, Deserialize)]
pub struct JikanSeasonQuery {
    year: i32,
    season: String,
    page: Option<u32>,
}

async fn jikan_season_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<JikanSeasonQuery>,
) -> Result<
    Json<crate::clients::jikan::JikanResponse<Vec<crate::clients::jikan::JikanAnime>>>,
    ApiError,
> {
    let page = query.page.unwrap_or(1);
    let key = format!("jikan:season:{}:{}:{}", query.year, query.season, page);
    handle_cache(&state, key, 3600, async {
        state
            .jikan_client
            .season_anime(query.year, &query.season, page)
            .await
    })
    .await
}

async fn jikan_characters_handler(
    State(state): State<Arc<AppState>>,
    Path(mal_id): Path<i64>,
) -> Result<Json<Vec<crate::clients::jikan::JikanCharacter>>, ApiError> {
    let key = format!("jikan:characters:{}", mal_id);
    handle_cache(&state, key, 86400, async {
        state.jikan_client.anime_characters(mal_id).await
    })
    .await
}

async fn jikan_recommendations_handler(
    State(state): State<Arc<AppState>>,
    Path(mal_id): Path<i64>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let key = format!("jikan:recommendations:{}", mal_id);
    handle_cache(&state, key, 86400, async {
        state.jikan_client.anime_recommendations(mal_id).await
    })
    .await
}

// ─── Trakt ───

#[derive(Debug, Deserialize)]
pub struct TraktLimitQuery {
    limit: Option<u32>,
}

async fn trakt_trending_movies_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<TraktLimitQuery>,
) -> Result<Json<Vec<crate::clients::trakt::TraktTrendingMovie>>, ApiError> {
    let client = state
        .trakt_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Trakt not configured"))?;
    let limit = query.limit.unwrap_or(20).min(50);
    let key = format!("trakt:trending:movies:{}", limit);
    handle_cache(&state, key, 1800, async {
        client.trending_movies(limit).await
    })
    .await
}

async fn trakt_popular_movies_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<TraktLimitQuery>,
) -> Result<Json<Vec<crate::clients::trakt::TraktMovie>>, ApiError> {
    let client = state
        .trakt_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Trakt not configured"))?;
    let limit = query.limit.unwrap_or(20).min(50);
    let key = format!("trakt:popular:movies:{}", limit);
    handle_cache(&state, key, 1800, async {
        client.popular_movies(limit).await
    })
    .await
}

async fn trakt_trending_shows_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<TraktLimitQuery>,
) -> Result<Json<Vec<crate::clients::trakt::TraktTrendingShow>>, ApiError> {
    let client = state
        .trakt_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Trakt not configured"))?;
    let limit = query.limit.unwrap_or(20).min(50);
    let key = format!("trakt:trending:shows:{}", limit);
    handle_cache(&state, key, 1800, async {
        client.trending_shows(limit).await
    })
    .await
}

async fn trakt_popular_shows_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<TraktLimitQuery>,
) -> Result<Json<Vec<crate::clients::trakt::TraktShow>>, ApiError> {
    let client = state
        .trakt_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Trakt not configured"))?;
    let limit = query.limit.unwrap_or(20).min(50);
    let key = format!("trakt:popular:shows:{}", limit);
    handle_cache(&state, key, 1800, async {
        client.popular_shows(limit).await
    })
    .await
}

async fn trakt_related_movies_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Query(query): Query<TraktLimitQuery>,
) -> Result<Json<Vec<crate::clients::trakt::TraktMovie>>, ApiError> {
    let client = state
        .trakt_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Trakt not configured"))?;
    let limit = query.limit.unwrap_or(10).min(20);
    let key = format!("trakt:related:movie:{}:{}", id, limit);
    handle_cache(&state, key, 86400, async {
        client.movie_related(&id, limit).await
    })
    .await
}

async fn trakt_related_shows_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
    Query(query): Query<TraktLimitQuery>,
) -> Result<Json<Vec<crate::clients::trakt::TraktShow>>, ApiError> {
    let client = state
        .trakt_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Trakt not configured"))?;
    let limit = query.limit.unwrap_or(10).min(20);
    let key = format!("trakt:related:show:{}:{}", id, limit);
    handle_cache(&state, key, 86400, async {
        client.show_related(&id, limit).await
    })
    .await
}

// ─── TasteDive ───

#[derive(Debug, Deserialize)]
pub struct TasteDiveQuery {
    query: String,
    #[serde(rename = "type")]
    media_type: Option<String>,
    limit: Option<u32>,
}

async fn tastedive_similar_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<TasteDiveQuery>,
) -> Result<Json<crate::clients::tastedive::TasteDiveResponse>, ApiError> {
    let client = state
        .tastedive_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("TasteDive not configured"))?;
    let limit = query.limit.unwrap_or(10).min(20);
    let key = format!(
        "tastedive:similar:{}:{}:{}",
        query.query,
        query.media_type.as_deref().unwrap_or("all"),
        limit
    );
    handle_cache(&state, key, 86400, async {
        client
            .similar(&query.query, query.media_type.as_deref(), limit)
            .await
    })
    .await
}

// ─── Watchmode ───

#[derive(Debug, Deserialize)]
pub struct WatchmodeSearchQuery {
    query: String,
}

async fn watchmode_search_handler(
    State(state): State<Arc<AppState>>,
    Query(query): Query<WatchmodeSearchQuery>,
) -> Result<Json<crate::clients::watchmode::WatchmodeSearchResult>, ApiError> {
    let client = state
        .watchmode_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Watchmode not configured"))?;
    let key = format!("watchmode:search:{}", query.query);
    handle_cache(&state, key, 3600, async {
        client.search(&query.query).await
    })
    .await
}

async fn watchmode_sources_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
) -> Result<Json<Vec<crate::clients::watchmode::WatchmodeSource>>, ApiError> {
    let client = state
        .watchmode_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Watchmode not configured"))?;
    let key = format!("watchmode:sources:{}", id);
    handle_cache(&state, key, 3600, async { client.title_sources(id).await }).await
}

async fn watchmode_tmdb_sources_handler(
    State(state): State<Arc<AppState>>,
    Path((tmdb_id, media_type)): Path<(i64, String)>,
) -> Result<Json<Vec<crate::clients::watchmode::WatchmodeSource>>, ApiError> {
    let client = state
        .watchmode_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Watchmode not configured"))?;
    let key = format!("watchmode:tmdb:{}:{}", tmdb_id, media_type);
    handle_cache(&state, key, 3600, async {
        client.sources_by_tmdb(tmdb_id, &media_type).await
    })
    .await
}

// ─── IMDbOT ───

#[derive(Deserialize)]
pub struct ImdbotSearchQuery {
    pub q: String,
}

pub async fn imdbbot_search_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<ImdbotSearchQuery>,
) -> Result<Json<Vec<crate::clients::imdbbot::ImdbSearchResult>>, ApiError> {
    let key = format!("imdbbot:search:{}", params.q);
    handle_cache(&state, key, 3600, async {
        let results = state
            .imdbbot_client
            .search_movie(&params.q)
            .await
            .unwrap_or_default();
        Ok(results)
    })
    .await
}

pub async fn imdbbot_details_handler(
    State(state): State<Arc<AppState>>,
    Path(imdb_id): Path<String>,
) -> Result<Json<Option<crate::clients::imdbbot::ImdbMovie>>, ApiError> {
    let key = format!("imdbbot:details:{}", imdb_id);
    handle_cache(&state, key, 86400, async {
        let details = state
            .imdbbot_client
            .get_movie_details(&imdb_id)
            .await
            .unwrap_or(None);
        Ok(details)
    })
    .await
}

pub async fn imdbbot_ratings_handler(
    State(state): State<Arc<AppState>>,
    Path(imdb_id): Path<String>,
) -> Result<Json<Option<serde_json::Value>>, ApiError> {
    let key = format!("imdbbot:ratings:{}", imdb_id);
    handle_cache(&state, key, 86400, async {
        let ratings = state
            .imdbbot_client
            .get_ratings(&imdb_id)
            .await
            .unwrap_or(None);
        Ok(ratings)
    })
    .await
}

// ─── Simkl ───

#[derive(Deserialize)]
pub struct SimklSearchQuery {
    pub q: String,
    #[serde(rename = "type")]
    pub item_type: Option<String>,
}

pub async fn simkl_search_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SimklSearchQuery>,
) -> Result<Json<Vec<crate::clients::simkl::SimklSearchResult>>, ApiError> {
    let client = state
        .simkl_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Simkl not configured"))?;
    let item_type = params.item_type.unwrap_or_else(|| "show".to_string());
    let key = format!("simkl:search:{}:{}", params.q, item_type);
    handle_cache(&state, key, 3600, async {
        let results = client
            .search(&params.q, &item_type)
            .await
            .unwrap_or_default();
        Ok(results)
    })
    .await
}

pub async fn simkl_details_handler(
    State(state): State<Arc<AppState>>,
    Path((id, item_type)): Path<(i64, String)>,
) -> Result<Json<Option<crate::clients::simkl::SimklShowDetails>>, ApiError> {
    let client = state
        .simkl_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Simkl not configured"))?;
    let key = format!("simkl:details:{}:{}", id, item_type);
    handle_cache(&state, key, 86400, async {
        let details = client.get_details(id, &item_type).await.unwrap_or(None);
        Ok(details)
    })
    .await
}

pub async fn simkl_trending_handler(
    State(state): State<Arc<AppState>>,
    Path(item_type): Path<String>,
) -> Result<Json<Vec<crate::clients::simkl::SimklSearchResult>>, ApiError> {
    let client = state
        .simkl_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Simkl not configured"))?;
    let key = format!("simkl:trending:{}", item_type);
    handle_cache(&state, key, 3600, async {
        let results = client.trending(&item_type).await.unwrap_or_default();
        Ok(results)
    })
    .await
}

pub async fn simkl_sources_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<i64>,
) -> Result<Json<Vec<crate::clients::simkl::SimklStreamSource>>, ApiError> {
    let client = state
        .simkl_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("Simkl not configured"))?;
    let key = format!("simkl:sources:{}", id);
    handle_cache(&state, key, 3600, async {
        let sources = client.get_sources(id).await.unwrap_or_default();
        Ok(sources)
    })
    .await
}

// ─── uNoGS (Netflix) ───

#[derive(Deserialize)]
pub struct UnogsSearchQuery {
    pub q: String,
    #[serde(rename = "type")]
    pub content_type: Option<String>,
}

pub async fn unogs_search_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<UnogsSearchQuery>,
) -> Result<Json<Vec<crate::clients::unogs::UnogsResult>>, ApiError> {
    let client = state
        .unogs_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("uNoGS not configured"))?;
    let content_type = params.content_type.unwrap_or_else(|| "movie".to_string());
    let key = format!("unogs:search:{}:{}", params.q, content_type);
    handle_cache(&state, key, 7200, async {
        let results = client
            .search_netflix(&params.q, &content_type)
            .await
            .unwrap_or_default();
        Ok(results)
    })
    .await
}

pub async fn unogs_imdb_handler(
    State(state): State<Arc<AppState>>,
    Path(imdb_id): Path<String>,
) -> Result<Json<Option<crate::clients::unogs::UnogsResult>>, ApiError> {
    let client = state
        .unogs_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("uNoGS not configured"))?;
    let key = format!("unogs:imdb:{}", imdb_id);
    handle_cache(&state, key, 7200, async {
        let result = client.get_by_imdb_id(&imdb_id).await.unwrap_or(None);
        Ok(result)
    })
    .await
}

pub async fn unogs_regions_handler(
    State(state): State<Arc<AppState>>,
) -> Result<Json<Vec<crate::clients::unogs::UnogsRegion>>, ApiError> {
    let client = state
        .unogs_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("uNoGS not configured"))?;
    let key = "unogs:regions".to_string();
    handle_cache(&state, key, 86400, async {
        let regions = client.netflix_regions().await.unwrap_or_default();
        Ok(regions)
    })
    .await
}

#[derive(Deserialize)]
pub struct UnogsRegionSearchQuery {
    pub q: String,
}

pub async fn unogs_region_search_handler(
    State(state): State<Arc<AppState>>,
    Path(region): Path<String>,
    Query(params): Query<UnogsRegionSearchQuery>,
) -> Result<Json<Vec<crate::clients::unogs::UnogsResult>>, ApiError> {
    let client = state
        .unogs_client
        .as_ref()
        .ok_or(ApiError::ServiceUnavailable("uNoGS not configured"))?;
    let key = format!("unogs:region:{}:{}", region, params.q);
    handle_cache(&state, key, 7200, async {
        let results = client
            .search_by_region(&params.q, &region)
            .await
            .unwrap_or_default();
        Ok(results)
    })
    .await
}

// ─── Stream ───

#[derive(Deserialize)]
pub struct StreamSearchQuery {
    pub q: String,
}

pub async fn stream_search_handler(
    State(state): State<Arc<AppState>>,
    Query(params): Query<StreamSearchQuery>,
) -> Result<Json<Vec<crate::clients::stream::StreamItem>>, ApiError> {
    let key = format!("stream:search:{}", params.q);
    handle_cache(&state, key, 14400, async {
        let results = state
            .stream_client
            .search(&params.q)
            .await
            .unwrap_or_default();
        Ok(results)
    })
    .await
}

pub async fn stream_details_handler(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<Json<Option<crate::clients::stream::StreamItem>>, ApiError> {
    let key = format!("stream:details:{}", id);
    handle_cache(&state, key, 86400, async {
        let details = state.stream_client.get_stream(&id).await.unwrap_or(None);
        Ok(details)
    })
    .await
}

pub async fn stream_category_handler(
    State(state): State<Arc<AppState>>,
    Path(category): Path<String>,
) -> Result<Json<Vec<crate::clients::stream::StreamItem>>, ApiError> {
    let key = format!("stream:category:{}", category);
    handle_cache(&state, key, 14400, async {
        let items = state
            .stream_client
            .get_by_category(&category)
            .await
            .unwrap_or_default();
        Ok(items)
    })
    .await
}
