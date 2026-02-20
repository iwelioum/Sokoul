use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

const TMDB_API_BASE_URL: &str = "https://api.themoviedb.org/3";
const TMDB_IMAGE_BASE: &str = "https://image.tmdb.org/t/p";

// ── Search Result (existing, extended) ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbSearchResult {
    pub id: i32,
    #[serde(default)]
    pub media_type: String,
    pub title: Option<String>,
    pub name: Option<String>,
    pub release_date: Option<String>,
    pub first_air_date: Option<String>,
    pub overview: Option<String>,
    pub poster_path: Option<String>,
    pub backdrop_path: Option<String>,
    pub vote_average: Option<f64>,
    pub genre_ids: Option<Vec<i32>>,
}

impl TmdbSearchResult {
    pub fn get_title(&self) -> String {
        self.title
            .clone()
            .or_else(|| self.name.clone())
            .unwrap_or_default()
    }

    pub fn get_year(&self) -> Option<i32> {
        let date_str = self
            .release_date
            .as_ref()
            .or(self.first_air_date.as_ref())?;
        date_str.split('-').next()?.parse::<i32>().ok()
    }

    pub fn poster_url(&self) -> Option<String> {
        self.poster_path
            .as_ref()
            .map(|p| format!("{}/w500{}", TMDB_IMAGE_BASE, p))
    }

    pub fn backdrop_url(&self) -> Option<String> {
        self.backdrop_path
            .as_ref()
            .map(|p| format!("{}/w1280{}", TMDB_IMAGE_BASE, p))
    }
}

#[derive(Debug, Deserialize, Serialize)]
pub struct TmdbPaginatedResponse {
    pub page: i32,
    pub results: Vec<TmdbSearchResult>,
    pub total_pages: i32,
    pub total_results: i32,
}

// ── Movie Details ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbGenre {
    pub id: i32,
    pub name: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbMovieDetail {
    pub id: i32,
    pub title: String,
    pub original_title: Option<String>,
    pub overview: Option<String>,
    pub poster_path: Option<String>,
    pub backdrop_path: Option<String>,
    pub genres: Vec<TmdbGenre>,
    pub runtime: Option<i32>,
    pub vote_average: Option<f64>,
    pub vote_count: Option<i32>,
    pub release_date: Option<String>,
    pub imdb_id: Option<String>,
    pub tagline: Option<String>,
    pub status: Option<String>,
    pub budget: Option<i64>,
    pub revenue: Option<i64>,
    pub belongs_to_collection: Option<BelongsToCollection>,
}

// ── TV Details ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbTvDetail {
    pub id: i32,
    pub name: String,
    pub original_name: Option<String>,
    pub overview: Option<String>,
    pub poster_path: Option<String>,
    pub backdrop_path: Option<String>,
    pub genres: Vec<TmdbGenre>,
    pub episode_run_time: Option<Vec<i32>>,
    pub vote_average: Option<f64>,
    pub vote_count: Option<i32>,
    pub first_air_date: Option<String>,
    pub last_air_date: Option<String>,
    pub number_of_seasons: Option<i32>,
    pub number_of_episodes: Option<i32>,
    pub status: Option<String>,
    pub seasons: Option<Vec<TmdbSeason>>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbSeason {
    pub id: i32,
    pub season_number: i32,
    pub name: Option<String>,
    pub episode_count: Option<i32>,
    pub poster_path: Option<String>,
    pub air_date: Option<String>,
    pub overview: Option<String>,
}

// ── TV Season Detail ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbSeasonDetail {
    pub id: i32,
    pub season_number: i32,
    pub name: Option<String>,
    pub episodes: Vec<TmdbEpisode>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbEpisode {
    pub id: i32,
    pub episode_number: i32,
    pub name: Option<String>,
    pub overview: Option<String>,
    pub still_path: Option<String>,
    pub air_date: Option<String>,
    pub vote_average: Option<f64>,
    pub runtime: Option<i32>,
}

// ── Credits ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbCastMember {
    pub id: i32,
    pub name: String,
    pub character: Option<String>,
    pub profile_path: Option<String>,
    pub order: Option<i32>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbCrewMember {
    pub id: i32,
    pub name: String,
    pub job: Option<String>,
    pub department: Option<String>,
    pub profile_path: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbCreditsResponse {
    pub cast: Vec<TmdbCastMember>,
    pub crew: Vec<TmdbCrewMember>,
}

// ── Videos ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbVideo {
    pub key: String,
    pub site: String,
    #[serde(rename = "type")]
    pub video_type: String,
    pub name: Option<String>,
    pub official: Option<bool>,
}

#[derive(Debug, Deserialize)]
struct TmdbVideosResponse {
    results: Vec<TmdbVideo>,
}

// ── Watch Providers ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbWatchProvider {
    pub provider_id: i32,
    pub provider_name: String,
    pub logo_path: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbWatchProviderCountry {
    pub link: Option<String>,
    pub flatrate: Option<Vec<TmdbWatchProvider>>,
    pub rent: Option<Vec<TmdbWatchProvider>>,
    pub buy: Option<Vec<TmdbWatchProvider>>,
}

#[derive(Debug, Deserialize)]
struct TmdbWatchProviderResponse {
    results: HashMap<String, TmdbWatchProviderCountry>,
}

// ── Person ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbPersonDetail {
    pub id: i32,
    pub name: String,
    pub biography: Option<String>,
    pub profile_path: Option<String>,
    pub birthday: Option<String>,
    pub deathday: Option<String>,
    pub place_of_birth: Option<String>,
    pub known_for_department: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbPersonCredit {
    pub id: i32,
    pub title: Option<String>,
    pub name: Option<String>,
    pub media_type: Option<String>,
    pub poster_path: Option<String>,
    pub vote_average: Option<f64>,
    pub character: Option<String>,
    pub release_date: Option<String>,
    pub first_air_date: Option<String>,
}

#[derive(Debug, Deserialize)]
struct TmdbPersonCreditsResponse {
    cast: Vec<TmdbPersonCredit>,
}

// ── Collection ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct BelongsToCollection {
    pub id: i32,
    pub name: String,
    pub poster_path: Option<String>,
    pub backdrop_path: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbCollection {
    pub id: i32,
    pub name: String,
    pub overview: Option<String>,
    pub poster_path: Option<String>,
    pub backdrop_path: Option<String>,
    pub parts: Vec<TmdbSearchResult>,
}

// ── Certification ──

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbReleaseDateEntry {
    pub certification: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbReleaseDateCountry {
    pub iso_3166_1: String,
    pub release_dates: Vec<TmdbReleaseDateEntry>,
}

#[derive(Debug, Deserialize)]
struct TmdbReleaseDatesResponse {
    pub results: Vec<TmdbReleaseDateCountry>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct TmdbContentRatingEntry {
    pub iso_3166_1: String,
    pub rating: String,
}

#[derive(Debug, Deserialize)]
struct TmdbContentRatingsResponse {
    pub results: Vec<TmdbContentRatingEntry>,
}

// ── Discover Params ──

#[derive(Debug, Deserialize, Default)]
pub struct DiscoverParams {
    pub with_watch_providers: Option<String>,
    pub with_genres: Option<String>,
    pub primary_release_year: Option<i32>,
    pub first_air_date_year: Option<i32>,
    pub sort_by: Option<String>,
    pub page: Option<i32>,
    pub watch_region: Option<String>,
    #[serde(rename = "primary_release_date.gte")]
    pub primary_release_date_gte: Option<String>,
    #[serde(rename = "primary_release_date.lte")]
    pub primary_release_date_lte: Option<String>,
    #[serde(rename = "first_air_date.gte")]
    pub first_air_date_gte: Option<String>,
    #[serde(rename = "first_air_date.lte")]
    pub first_air_date_lte: Option<String>,
    #[serde(rename = "vote_average.gte")]
    pub vote_average_gte: Option<f64>,
    #[serde(rename = "vote_average.lte")]
    pub vote_average_lte: Option<f64>,
    pub with_original_language: Option<String>,
}

// ── Client ──

#[derive(Clone)]
pub struct TmdbClient {
    client: Client,
    api_key: String,
    language: String,
}

impl TmdbClient {
    pub fn new(api_key: String) -> Self {
        Self {
            client: Client::new(),
            api_key,
            language: "fr-FR".to_string(),
        }
    }

    /// Return a clone of this client with a different language.
    pub fn with_language(&self, lang: &str) -> Self {
        Self {
            client: self.client.clone(),
            api_key: self.api_key.clone(),
            language: lang.to_string(),
        }
    }

    fn base_params(&self) -> Vec<(&str, String)> {
        vec![
            ("api_key", self.api_key.clone()),
            ("language", self.language.clone()),
        ]
    }

    // ── Search ──

    pub async fn search_multi(&self, query: &str) -> Result<Vec<TmdbSearchResult>, reqwest::Error> {
        let url = format!("{}/search/multi", TMDB_API_BASE_URL);
        let mut params = self.base_params();
        params.push(("query", query.to_string()));
        params.push(("include_adult", "false".to_string()));

        let response = self
            .client
            .get(&url)
            .query(&params)
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbPaginatedResponse>()
            .await?;

        Ok(response
            .results
            .into_iter()
            .filter(|r| r.media_type == "movie" || r.media_type == "tv")
            .collect())
    }

    // ── Trending ──

    pub async fn trending(
        &self,
        media_type: &str,
        time_window: &str,
    ) -> Result<Vec<TmdbSearchResult>, reqwest::Error> {
        let url = format!(
            "{}/trending/{}/{}",
            TMDB_API_BASE_URL, media_type, time_window
        );
        let response = self
            .client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbPaginatedResponse>()
            .await?;
        Ok(response.results)
    }

    // ── Discover ──

    pub async fn discover(
        &self,
        media_type: &str,
        params: &DiscoverParams,
    ) -> Result<TmdbPaginatedResponse, reqwest::Error> {
        let url = format!("{}/discover/{}", TMDB_API_BASE_URL, media_type);
        let mut query = self.base_params();
        query.push(("include_adult", "false".to_string()));

        if let Some(ref wp) = params.with_watch_providers {
            query.push(("with_watch_providers", wp.clone()));
            // When filtering by watch providers, must specify monetization type
            query.push(("with_watch_monetization_types", "flatrate".to_string()));
        }
        if let Some(ref wr) = params.watch_region {
            query.push(("watch_region", wr.clone()));
        }
        if let Some(ref g) = params.with_genres {
            query.push(("with_genres", g.clone()));
        }
        if let Some(y) = params.primary_release_year {
            query.push(("primary_release_year", y.to_string()));
        }
        if let Some(y) = params.first_air_date_year {
            query.push(("first_air_date_year", y.to_string()));
        }
        if let Some(ref sb) = params.sort_by {
            query.push(("sort_by", sb.clone()));
        } else {
            query.push(("sort_by", "popularity.desc".to_string()));
        }
        if let Some(ref v) = params.primary_release_date_gte {
            query.push(("primary_release_date.gte", v.clone()));
        }
        if let Some(ref v) = params.primary_release_date_lte {
            query.push(("primary_release_date.lte", v.clone()));
        }
        if let Some(ref v) = params.first_air_date_gte {
            query.push(("first_air_date.gte", v.clone()));
        }
        if let Some(ref v) = params.first_air_date_lte {
            query.push(("first_air_date.lte", v.clone()));
        }
        if let Some(v) = params.vote_average_gte {
            query.push(("vote_average.gte", v.to_string()));
        }
        if let Some(v) = params.vote_average_lte {
            query.push(("vote_average.lte", v.to_string()));
        }
        if let Some(ref lang) = params.with_original_language {
            query.push(("with_original_language", lang.clone()));
        }
        query.push(("page", params.page.unwrap_or(1).to_string()));

        let mut response = self
            .client
            .get(&url)
            .query(&query)
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbPaginatedResponse>()
            .await?;

        // Ensure all results have media_type set (TMDB discover endpoint doesn't include it)
        for result in response.results.iter_mut() {
            if result.media_type.is_empty() {
                result.media_type = media_type.to_string();
            }
        }

        Ok(response)
    }

    // ── Movie Details ──

    pub async fn movie_details(&self, id: i32) -> Result<TmdbMovieDetail, reqwest::Error> {
        let url = format!("{}/movie/{}", TMDB_API_BASE_URL, id);
        self.client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbMovieDetail>()
            .await
    }

    // ── TV Details ──

    pub async fn tv_details(&self, id: i32) -> Result<TmdbTvDetail, reqwest::Error> {
        let url = format!("{}/tv/{}", TMDB_API_BASE_URL, id);
        self.client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbTvDetail>()
            .await
    }

    // ── TV Season Detail ──

    pub async fn season_details(
        &self,
        tv_id: i32,
        season_number: i32,
    ) -> Result<TmdbSeasonDetail, reqwest::Error> {
        let url = format!(
            "{}/tv/{}/season/{}",
            TMDB_API_BASE_URL, tv_id, season_number
        );
        self.client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbSeasonDetail>()
            .await
    }

    // ── Credits ──

    pub async fn credits(
        &self,
        media_type: &str,
        id: i32,
    ) -> Result<TmdbCreditsResponse, reqwest::Error> {
        let url = format!("{}/{}/{}/credits", TMDB_API_BASE_URL, media_type, id);
        self.client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbCreditsResponse>()
            .await
    }

    // ── Videos ──

    pub async fn videos(
        &self,
        media_type: &str,
        id: i32,
    ) -> Result<Vec<TmdbVideo>, reqwest::Error> {
        let url = format!("{}/{}/{}/videos", TMDB_API_BASE_URL, media_type, id);
        let resp = self
            .client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbVideosResponse>()
            .await?;
        Ok(resp.results)
    }

    // ── Watch Providers ──

    pub async fn watch_providers(
        &self,
        media_type: &str,
        id: i32,
    ) -> Result<Option<TmdbWatchProviderCountry>, reqwest::Error> {
        let url = format!(
            "{}/{}/{}/watch/providers",
            TMDB_API_BASE_URL, media_type, id
        );
        let resp = self
            .client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbWatchProviderResponse>()
            .await?;
        Ok(resp.results.get("FR").cloned())
    }

    // ── Person ──

    pub async fn person_details(&self, id: i32) -> Result<TmdbPersonDetail, reqwest::Error> {
        let url = format!("{}/person/{}", TMDB_API_BASE_URL, id);
        self.client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbPersonDetail>()
            .await
    }

    pub async fn person_combined_credits(
        &self,
        id: i32,
    ) -> Result<Vec<TmdbPersonCredit>, reqwest::Error> {
        let url = format!("{}/person/{}/combined_credits", TMDB_API_BASE_URL, id);
        let resp = self
            .client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbPersonCreditsResponse>()
            .await?;
        Ok(resp.cast)
    }

    // ── Similar ──

    pub async fn similar(
        &self,
        media_type: &str,
        id: i32,
    ) -> Result<Vec<TmdbSearchResult>, reqwest::Error> {
        let url = format!("{}/{}/{}/similar", TMDB_API_BASE_URL, media_type, id);
        let resp = self
            .client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbPaginatedResponse>()
            .await?;
        Ok(resp.results)
    }

    // ── Collection (TMDB franchise/saga) ──

    pub async fn collection(&self, id: i32) -> Result<TmdbCollection, reqwest::Error> {
        let url = format!("{}/collection/{}", TMDB_API_BASE_URL, id);
        self.client
            .get(&url)
            .query(&self.base_params())
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbCollection>()
            .await
    }

    // ── Certification ──

    /// Fetch the content certification for a movie (prefers FR, falls back to US).
    pub async fn movie_certification(&self, id: i32) -> Result<Option<String>, reqwest::Error> {
        let url = format!("{}/movie/{}/release_dates", TMDB_API_BASE_URL, id);
        let resp = self
            .client
            .get(&url)
            .query(&[("api_key", self.api_key.clone())])
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbReleaseDatesResponse>()
            .await?;

        // Priority: FR → US → any non-empty
        let pick = |code: &str| -> Option<String> {
            resp.results
                .iter()
                .find(|c| c.iso_3166_1 == code)
                .and_then(|c| {
                    c.release_dates
                        .iter()
                        .find(|r| !r.certification.is_empty())
                        .map(|r| r.certification.clone())
                })
        };

        Ok(pick("FR").or_else(|| pick("US")).or_else(|| {
            resp.results.iter().find_map(|c| {
                c.release_dates
                    .iter()
                    .find(|r| !r.certification.is_empty())
                    .map(|r| r.certification.clone())
            })
        }))
    }

    /// Fetch the content rating for a TV show (prefers FR, falls back to US).
    pub async fn tv_certification(&self, id: i32) -> Result<Option<String>, reqwest::Error> {
        let url = format!("{}/tv/{}/content_ratings", TMDB_API_BASE_URL, id);
        let resp = self
            .client
            .get(&url)
            .query(&[("api_key", self.api_key.clone())])
            .send()
            .await?
            .error_for_status()?
            .json::<TmdbContentRatingsResponse>()
            .await?;

        let pick = |code: &str| -> Option<String> {
            resp.results
                .iter()
                .find(|c| c.iso_3166_1 == code && !c.rating.is_empty())
                .map(|c| c.rating.clone())
        };

        Ok(pick("FR").or_else(|| pick("US")).or_else(|| {
            resp.results
                .iter()
                .find(|c| !c.rating.is_empty())
                .map(|c| c.rating.clone())
        }))
    }
}
