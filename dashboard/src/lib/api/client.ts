const API_BASE = '/api';

// API Key from environment (set at build time or runtime)
let apiKey = '';

export function setApiKey(key: string) {
	apiKey = key;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
	const headers: Record<string, string> = { 'Content-Type': 'application/json' };
	if (apiKey) {
		headers['X-API-Key'] = apiKey;
	}
	const res = await fetch(`${API_BASE}${path}`, {
		headers,
		...options
	});
	if (!res.ok) {
		const text = await res.text();
		throw new Error(`API ${res.status}: ${text}`);
	}
	if (res.status === 204) {
		return undefined as T;
	}
	return res.json();
}

// ══════════════════════════════════════════════════
// TYPES — Existing
// ══════════════════════════════════════════════════

export interface Media {
	id: string;
	media_type: string;
	title: string;
	original_title: string | null;
	year: number | null;
	tmdb_id: number | null;
	imdb_id: string | null;
	overview: string | null;
	poster_url: string | null;
	backdrop_url: string | null;
	genres: string[] | null;
	rating: number | null;
	runtime_minutes: number | null;
	status: string | null;
	parent_id: string | null;
	season_number: number | null;
	episode_number: number | null;
	created_at: string;
	updated_at: string;
}

export interface SearchResult {
	id: number;
	media_id: string;
	provider: string;
	title: string;
	guid: string;
	url: string | null;
	magnet_link: string | null;
	info_hash: string | null;
	protocol: string;
	quality: string | null;
	size_bytes: number;
	seeders: number;
	leechers: number;
	score: number | null;
	ai_validated: boolean | null;
	created_at: string;
	expires_at: string | null;
}

export interface Task {
	id: string;
	task_type: string;
	status: string;
	payload: Record<string, unknown> | null;
	result: Record<string, unknown> | null;
	progress: number | null;
	error: string | null;
	created_at: string | null;
	started_at: string | null;
	completed_at: string | null;
}

export interface HealthStatus {
	status: string;
	database: string;
	redis: string;
	nats: string;
}

export interface PaginatedResponse<T> {
	data: T;
	page: number;
	per_page: number;
	total: number;
	total_pages: number;
}

export interface StorageInfo {
	download_dir: string;
	total_bytes: number;
	used_bytes: number;
	free_bytes: number;
	usage_percent: number;
	files_count: number;
	total_media_size_bytes: number;
}

export interface Recommendation {
	tmdb_id: number;
	title: string;
	overview: string | null;
	poster_url: string | null;
	media_type: string;
	vote_average: number | null;
	release_date: string | null;
}

export interface WsEvent {
	type: string;
	[key: string]: unknown;
}

export interface StreamSource {
	name: string;
	url: string;
	quality: string;
}

export interface StreamLinks {
	title: string;
	tmdb_id: number;
	media_type: string;
	sources: StreamSource[];
}

export interface MediaFile {
	id: string;
	media_id: string;
	file_path: string;
	file_size: number | null;
	codec_video: string | null;
	codec_audio: string | null;
	resolution: string | null;
	quality_score: number | null;
	source: string | null;
	downloaded_at: string | null;
}

export interface FileInfo {
	id: string;
	media_id: string;
	file_path: string;
	file_size: number | null;
	codec_video: string | null;
	codec_audio: string | null;
	resolution: string | null;
	exists: boolean;
	filename: string;
	stream_url: string;
	content_type: string;
}

// ══════════════════════════════════════════════════
// TYPES — TMDB
// ══════════════════════════════════════════════════

export interface TmdbSearchItem {
	id: number;
	media_type: string;
	title?: string;
	name?: string;
	poster_path: string | null;
	backdrop_path: string | null;
	overview: string | null;
	vote_average: number | null;
	release_date?: string;
	first_air_date?: string;
	genre_ids?: number[];
}

export interface TmdbGenre {
	id: number;
	name: string;
}

export interface TmdbMovieDetail {
	id: number;
	title: string;
	original_title: string | null;
	overview: string | null;
	poster_path: string | null;
	backdrop_path: string | null;
	genres: TmdbGenre[];
	runtime: number | null;
	vote_average: number | null;
	vote_count: number | null;
	release_date: string | null;
	imdb_id: string | null;
	tagline: string | null;
	status: string | null;
	budget: number | null;
	revenue: number | null;
}

export interface TmdbTvDetail {
	id: number;
	name: string;
	original_name: string | null;
	overview: string | null;
	poster_path: string | null;
	backdrop_path: string | null;
	genres: TmdbGenre[];
	episode_run_time: number[] | null;
	vote_average: number | null;
	vote_count: number | null;
	first_air_date: string | null;
	last_air_date: string | null;
	number_of_seasons: number | null;
	number_of_episodes: number | null;
	status: string | null;
	tagline: string | null;
	seasons: TmdbSeason[] | null;
}

export interface TmdbSeason {
	id: number;
	season_number: number;
	name: string | null;
	episode_count: number | null;
	poster_path: string | null;
	air_date: string | null;
	overview: string | null;
}

export interface TmdbSeasonDetail {
	id: number;
	season_number: number;
	name: string | null;
	episodes: TmdbEpisode[];
}

export interface TmdbEpisode {
	id: number;
	episode_number: number;
	name: string | null;
	overview: string | null;
	still_path: string | null;
	air_date: string | null;
	vote_average: number | null;
	runtime: number | null;
}

export interface TmdbCastMember {
	id: number;
	name: string;
	character: string | null;
	profile_path: string | null;
	order: number | null;
}

export interface TmdbCrewMember {
	id: number;
	name: string;
	job: string | null;
	department: string | null;
	profile_path: string | null;
}

export interface TmdbCreditsResponse {
	cast: TmdbCastMember[];
	crew: TmdbCrewMember[];
}

export interface TmdbVideo {
	key: string;
	site: string;
	video_type: string;
	name: string | null;
	official: boolean | null;
}

export interface TmdbWatchProvider {
	provider_id: number;
	provider_name: string;
	logo_path: string | null;
}

export interface TmdbWatchProviderCountry {
	link: string | null;
	flatrate: TmdbWatchProvider[] | null;
	rent: TmdbWatchProvider[] | null;
	buy: TmdbWatchProvider[] | null;
}

export interface TmdbPersonDetail {
	id: number;
	name: string;
	biography: string | null;
	profile_path: string | null;
	birthday: string | null;
	deathday: string | null;
	place_of_birth: string | null;
	known_for_department: string | null;
}

export interface TmdbPersonCredit {
	id: number;
	title?: string;
	name?: string;
	media_type: string | null;
	poster_path: string | null;
	vote_average: number | null;
	character: string | null;
	release_date?: string;
	first_air_date?: string;
}

export interface TmdbPaginatedResponse {
	page: number;
	results: TmdbSearchItem[];
	total_pages: number;
	total_results: number;
}

// ══════════════════════════════════════════════════
// TYPES — Library / Watchlist / History
// ══════════════════════════════════════════════════

export interface Favorite {
	id: string;
	tmdb_id: number;
	media_type: string;
	title: string;
	poster_url: string | null;
	backdrop_url: string | null;
	vote_average: number | null;
	release_date: string | null;
	overview: string | null;
	added_at: string | null;
}

export interface WatchlistEntry {
	id: string;
	tmdb_id: number | null;
	media_type_wl: string | null;
	title: string | null;
	poster_url: string | null;
	added_at: string | null;
}

export interface WatchHistoryEntry {
	id: string;
	tmdb_id: number | null;
	media_type_wh: string | null;
	title: string | null;
	poster_url: string | null;
	progress: number | null;
	completed: boolean | null;
	watched_at: string | null;
}

export interface LibraryStatus {
	in_library: boolean;
	in_watchlist: boolean;
	watch_progress: number | null;
	completed: boolean;
}

export interface PaginatedFavorites {
	items: Favorite[];
	total: number;
	page: number;
	per_page: number;
	total_pages: number;
}

export interface PaginatedWatchlist {
	items: WatchlistEntry[];
	total: number;
	page: number;
	per_page: number;
	total_pages: number;
}

// ══════════════════════════════════════════════════
// HELPERS
// ══════════════════════════════════════════════════

const TMDB_IMAGE_BASE = 'https://image.tmdb.org/t/p';

export function tmdbImageUrl(path: string | null | undefined, size = 'w500'): string | null {
	if (!path) return null;
	return `${TMDB_IMAGE_BASE}/${size}${path}`;
}

export function getItemTitle(item: TmdbSearchItem | TmdbMovieDetail | TmdbTvDetail): string {
	if ('title' in item && item.title) return item.title;
	if ('name' in item && item.name) return item.name;
	return '';
}

export function getItemYear(item: TmdbSearchItem | TmdbMovieDetail | TmdbTvDetail): string {
	const date = ('release_date' in item ? item.release_date : null) ||
		('first_air_date' in item ? item.first_air_date : null);
	return date ? date.substring(0, 4) : '';
}

export function formatBytes(bytes: number): string {
	if (bytes === 0) return '0 B';
	const k = 1024;
	const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
	const i = Math.floor(Math.log(bytes) / Math.log(k));
	return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
}

export function formatDate(dateStr: string | null): string {
	if (!dateStr) return '-';
	return new Date(dateStr).toLocaleString('fr-FR', {
		day: '2-digit',
		month: '2-digit',
		year: 'numeric',
		hour: '2-digit',
		minute: '2-digit'
	});
}

// ══════════════════════════════════════════════════
// API — Health
// ══════════════════════════════════════════════════

export function getHealth(): Promise<HealthStatus> {
	return request('/health');
}

// ══════════════════════════════════════════════════
// API — Media (DB)
// ══════════════════════════════════════════════════

export function listMedia(page = 1, perPage = 30, mediaType?: string): Promise<PaginatedResponse<Media[]>> {
	let url = `/media?page=${page}&per_page=${perPage}`;
	if (mediaType) url += `&media_type=${mediaType}`;
	return request(url);
}

export function getMedia(id: string): Promise<Media> {
	return request(`/media/${id}`);
}

export function getEpisodes(seriesId: string): Promise<Media[]> {
	return request(`/media/${seriesId}/episodes`);
}

export function createMedia(data: {
	title: string;
	media_type: string;
	tmdb_id?: number;
	year?: number;
	overview?: string;
	poster_url?: string;
	backdrop_url?: string;
	genres?: string[];
	rating?: number;
}): Promise<Media> {
	return request('/media', { method: 'POST', body: JSON.stringify(data) });
}

export function updateMedia(
	id: string,
	data: Partial<Pick<Media, 'title' | 'year' | 'overview' | 'poster_url' | 'backdrop_url' | 'genres' | 'rating' | 'status'>>
): Promise<Media> {
	return request(`/media/${id}`, { method: 'PUT', body: JSON.stringify(data) });
}

export function deleteMedia(id: string): Promise<void> {
	return request(`/media/${id}`, { method: 'DELETE' });
}

// ══════════════════════════════════════════════════
// API — TMDB (proxy with cache)
// ══════════════════════════════════════════════════

export function tmdbTrending(mediaType: string, timeWindow: string): Promise<TmdbSearchItem[]> {
	return request(`/tmdb/trending/${mediaType}/${timeWindow}`);
}

export function tmdbDiscover(
	mediaType: string,
	params: {
		with_watch_providers?: string;
		watch_region?: string;
		with_genres?: string;
		sort_by?: string;
		page?: number;
		year?: number;
	} = {}
): Promise<TmdbPaginatedResponse> {
	const qs = new URLSearchParams();
	for (const [k, v] of Object.entries(params)) {
		if (v !== undefined) qs.set(k, String(v));
	}
	return request(`/tmdb/discover/${mediaType}?${qs.toString()}`);
}

export function tmdbMovieDetails(id: number): Promise<TmdbMovieDetail> {
	return request(`/tmdb/movie/${id}`);
}

export function tmdbTvDetails(id: number): Promise<TmdbTvDetail> {
	return request(`/tmdb/tv/${id}`);
}

export function tmdbSeasonDetails(tvId: number, seasonNumber: number): Promise<TmdbSeasonDetail> {
	return request(`/tmdb/tv/${tvId}/season/${seasonNumber}`);
}

export function tmdbCredits(mediaType: string, id: number): Promise<TmdbCreditsResponse> {
	return request(`/tmdb/${mediaType}/${id}/credits`);
}

export function tmdbVideos(mediaType: string, id: number): Promise<TmdbVideo[]> {
	return request(`/tmdb/${mediaType}/${id}/videos`);
}

export function tmdbWatchProviders(mediaType: string, id: number): Promise<TmdbWatchProviderCountry | null> {
	return request(`/tmdb/${mediaType}/${id}/watch/providers`);
}

export function tmdbPerson(id: number): Promise<TmdbPersonDetail> {
	return request(`/tmdb/person/${id}`);
}

export function tmdbPersonCredits(id: number): Promise<TmdbPersonCredit[]> {
	return request(`/tmdb/person/${id}/credits`);
}

export function tmdbSimilar(mediaType: string, id: number): Promise<TmdbSearchItem[]> {
	return request(`/tmdb/${mediaType}/${id}/similar`);
}

export function tmdbSearch(query: string): Promise<TmdbSearchItem[]> {
	return request(`/tmdb/search?query=${encodeURIComponent(query)}`);
}

// ══════════════════════════════════════════════════
// API — Streaming
// ══════════════════════════════════════════════════

export function getStreamLinks(mediaId: string, season?: number, episode?: number): Promise<StreamLinks> {
	let url = `/media/${mediaId}/stream`;
	if (season !== undefined) url += `?season=${season}&episode=${episode ?? 1}`;
	return request(url);
}

export function getDirectStreamLinks(
	mediaType: string,
	tmdbId: number,
	season?: number,
	episode?: number
): Promise<StreamLinks> {
	let url = `/streaming/direct/${mediaType}/${tmdbId}`;
	if (season !== undefined) url += `?season=${season}&episode=${episode ?? 1}`;
	return request(url);
}

// ══════════════════════════════════════════════════
// API — Library (Favorites)
// ══════════════════════════════════════════════════

export function addToLibrary(data: {
	tmdb_id: number;
	media_type: string;
	title: string;
	poster_url?: string | null;
	backdrop_url?: string | null;
	vote_average?: number | null;
	release_date?: string | null;
	overview?: string | null;
}): Promise<Favorite> {
	return request('/library', { method: 'POST', body: JSON.stringify(data) });
}

export function removeFromLibrary(tmdbId: number, mediaType: string): Promise<void> {
	return request(`/library/${tmdbId}/${mediaType}`, { method: 'DELETE' });
}

export function listLibrary(page = 1, perPage = 30): Promise<PaginatedFavorites> {
	return request(`/library?page=${page}&per_page=${perPage}`);
}

export function getLibraryStatus(tmdbId: number, mediaType: string): Promise<LibraryStatus> {
	return request(`/library/status/${tmdbId}/${mediaType}`);
}

// ══════════════════════════════════════════════════
// API — Watchlist
// ══════════════════════════════════════════════════

export function addToWatchlist(data: {
	tmdb_id: number;
	media_type: string;
	title: string;
	poster_url?: string | null;
}): Promise<WatchlistEntry> {
	return request('/watchlist', { method: 'POST', body: JSON.stringify(data) });
}

export function removeFromWatchlist(tmdbId: number, mediaType: string): Promise<void> {
	return request(`/watchlist/${tmdbId}/${mediaType}`, { method: 'DELETE' });
}

export function listWatchlist(page = 1, perPage = 30): Promise<PaginatedWatchlist> {
	return request(`/watchlist?page=${page}&per_page=${perPage}`);
}

// ══════════════════════════════════════════════════
// API — Watch History
// ══════════════════════════════════════════════════

export function updateWatchProgress(data: {
	tmdb_id: number;
	media_type: string;
	title: string;
	poster_url?: string | null;
	progress: number;
	completed?: boolean;
}): Promise<WatchHistoryEntry> {
	return request('/watch-history', { method: 'POST', body: JSON.stringify(data) });
}

export function getContinueWatching(limit = 20): Promise<WatchHistoryEntry[]> {
	return request(`/watch-history/continue?limit=${limit}`);
}

// ══════════════════════════════════════════════════
// API — Search (Torrent)
// ══════════════════════════════════════════════════

export function triggerSearch(query: string): Promise<{ message: string }> {
	return request('/search', { method: 'POST', body: JSON.stringify({ query }) });
}

export function getSearchResults(mediaId: string): Promise<SearchResult[]> {
	return request(`/media/${mediaId}/results`);
}

// ══════════════════════════════════════════════════
// API — Downloads
// ══════════════════════════════════════════════════

export function startDownload(data: {
	media_id: string;
	search_result_id: number;
}): Promise<{ message: string }> {
	return request('/downloads', { method: 'POST', body: JSON.stringify(data) });
}

export function listDownloads(): Promise<Task[]> {
	return request('/downloads');
}

// ══════════════════════════════════════════════════
// API — Tasks
// ══════════════════════════════════════════════════

export function listTasks(): Promise<Task[]> {
	return request('/tasks');
}

export function getTask(id: string): Promise<Task> {
	return request(`/tasks/${id}`);
}

// ══════════════════════════════════════════════════
// API — Media Files
// ══════════════════════════════════════════════════

export function getMediaFiles(mediaId: string): Promise<MediaFile[]> {
	return request(`/media/${mediaId}/files`);
}

export function getFileInfo(fileId: string): Promise<FileInfo> {
	return request(`/files/${fileId}/info`);
}

export function getFileStreamUrl(fileId: string): string {
	return `${API_BASE}/files/${fileId}/stream`;
}

// ══════════════════════════════════════════════════
// API — Storage
// ══════════════════════════════════════════════════

export function getStorage(): Promise<StorageInfo> {
	return request('/storage');
}

// ══════════════════════════════════════════════════
// API — Recommendations (legacy, DB-based)
// ══════════════════════════════════════════════════

export function getRecommendations(mediaId: string): Promise<Recommendation[]> {
	return request(`/media/${mediaId}/recommendations`);
}

// ══════════════════════════════════════════════════
// WebSocket
// ══════════════════════════════════════════════════

export function connectWebSocket(onMessage: (data: WsEvent) => void): WebSocket {
	const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
	const ws = new WebSocket(`${protocol}//${window.location.host}/ws`);

	ws.onmessage = (event) => {
		try {
			const data = JSON.parse(event.data);
			onMessage(data);
		} catch {
			onMessage({ type: 'raw', data: event.data });
		}
	};

	ws.onclose = () => {
		setTimeout(() => connectWebSocket(onMessage), 3000);
	};

	return ws;
}
