const API_BASE = '/api';

// Auth token storage
let authToken = '';

export function setAuthToken(token: string) {
	authToken = token;
	if (typeof localStorage !== 'undefined') {
		localStorage.setItem('sokoul_token', token);
	}
}

export function getAuthToken(): string {
	if (!authToken && typeof localStorage !== 'undefined') {
		authToken = localStorage.getItem('sokoul_token') || '';
	}
	return authToken;
}

export function clearAuth() {
	authToken = '';
	if (typeof localStorage !== 'undefined') {
		localStorage.removeItem('sokoul_token');
	}
}

const API_KEY = 'c29rb3VsLXRlc3Qta2V5LTEyMzQ1';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
	const headers: Record<string, string> = { 'Content-Type': 'application/json' };
	const token = getAuthToken();
	if (token) {
		headers['Authorization'] = `Bearer ${token}`;
	} else {
		headers['Authorization'] = `Bearer ${API_KEY}`;
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
// AUTH
// ══════════════════════════════════════════════════

export interface UserPublic {
	id: string;
	username: string;
	email: string;
	role: string;
	avatar_url: string | null;
	is_active: boolean;
	created_at: string;
}

export interface AuthResponse {
	token: string;
	user: UserPublic;
}

export async function register(username: string, email: string, password: string): Promise<AuthResponse> {
	const res = await request<AuthResponse>('/auth/register', {
		method: 'POST',
		body: JSON.stringify({ username, email, password })
	});
	setAuthToken(res.token);
	return res;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
	const res = await request<AuthResponse>('/auth/login', {
		method: 'POST',
		body: JSON.stringify({ email, password })
	});
	setAuthToken(res.token);
	return res;
}

export async function getMe(): Promise<UserPublic> {
	return request<UserPublic>('/auth/me');
}

export function isLoggedIn(): boolean {
	return !!getAuthToken();
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

export interface VidfastEmbed {
	embed_url: string;
	media_type: string;
	tmdb_id: number;
	season: number | null;
	episode: number | null;
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

export interface BelongsToCollection {
	id: number;
	name: string;
	poster_path: string | null;
	backdrop_path: string | null;
}

export interface TmdbCollection {
	id: number;
	name: string;
	overview: string | null;
	poster_path: string | null;
	backdrop_path: string | null;
	parts: TmdbSearchItem[];
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
	belongs_to_collection: BelongsToCollection | null;
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

// ── Language preference (stored in localStorage) ──────────────────────────

export function getPreferredLang(): string {
	if (typeof localStorage !== 'undefined') {
		return localStorage.getItem('sokoul_lang') || 'fr-FR';
	}
	return 'fr-FR';
}

export function setPreferredLang(lang: string) {
	if (typeof localStorage !== 'undefined') {
		localStorage.setItem('sokoul_lang', lang);
	}
}

function langParam(): string {
	return `lang=${encodeURIComponent(getPreferredLang())}`;
}

// ── TMDB API ───────────────────────────────────────────────────────────────

export function tmdbTrending(mediaType: string, timeWindow: string): Promise<TmdbSearchItem[]> {
	return request(`/tmdb/trending/${mediaType}/${timeWindow}?${langParam()}`);
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
		'primary_release_date.gte'?: string;
		'primary_release_date.lte'?: string;
		'first_air_date.gte'?: string;
		'first_air_date.lte'?: string;
		'vote_average.gte'?: number;
		'vote_average.lte'?: number;
		with_original_language?: string;
	} = {}
): Promise<TmdbPaginatedResponse> {
	const qs = new URLSearchParams();
	qs.set('lang', getPreferredLang());
	for (const [k, v] of Object.entries(params)) {
		if (v !== undefined) qs.set(k, String(v));
	}
	return request(`/tmdb/discover/${mediaType}?${qs.toString()}`);
}

export function tmdbMovieDetails(id: number): Promise<TmdbMovieDetail> {
	return request(`/tmdb/movie/${id}?${langParam()}`);
}

export function tmdbTvDetails(id: number): Promise<TmdbTvDetail> {
	return request(`/tmdb/tv/${id}?${langParam()}`);
}

export function tmdbSeasonDetails(tvId: number, seasonNumber: number): Promise<TmdbSeasonDetail> {
	return request(`/tmdb/tv/${tvId}/season/${seasonNumber}?${langParam()}`);
}

export function tmdbCredits(mediaType: string, id: number): Promise<TmdbCreditsResponse> {
	return request(`/tmdb/${mediaType}/${id}/credits`);
}

export function tmdbVideos(mediaType: string, id: number): Promise<TmdbVideo[]> {
	return request(`/tmdb/${mediaType}/${id}/videos?${langParam()}`);
}

export function tmdbWatchProviders(mediaType: string, id: number): Promise<TmdbWatchProviderCountry | null> {
	return request(`/tmdb/${mediaType}/${id}/watch/providers`);
}

export function tmdbPerson(id: number): Promise<TmdbPersonDetail> {
	return request(`/tmdb/person/${id}?${langParam()}`);
}

export function tmdbPersonCredits(id: number): Promise<TmdbPersonCredit[]> {
	return request(`/tmdb/person/${id}/credits?${langParam()}`);
}

export function tmdbSimilar(mediaType: string, id: number): Promise<TmdbSearchItem[]> {
	return request(`/tmdb/${mediaType}/${id}/similar?${langParam()}`);
}

export function tmdbSearch(query: string): Promise<TmdbSearchItem[]> {
	return request(`/tmdb/search?query=${encodeURIComponent(query)}&${langParam()}`);
}

export function tmdbCollection(id: number): Promise<TmdbCollection> {
	return request(`/tmdb/collection/${id}?${langParam()}`);
}

export function tmdbMovieCertification(id: number): Promise<string | null> {
	return request(`/tmdb/movie/${id}/certification`);
}

export function tmdbTvCertification(id: number): Promise<string | null> {
	return request(`/tmdb/tv/${id}/certification`);
}

// ══════════════════════════════════════════════════
// API — Streaming (VidFast)
// ══════════════════════════════════════════════════

export function getVidfastEmbed(
	mediaType: string,
	tmdbId: number,
	season?: number,
	episode?: number
): Promise<VidfastEmbed> {
	let url = `/streaming/embed/${mediaType}/${tmdbId}`;
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

export function directSearch(query: string, mediaId: string): Promise<{ results: SearchResult[]; count: number }> {
	return request('/search/direct', { method: 'POST', body: JSON.stringify({ query, media_id: mediaId }) });
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

export function connectWebSocket(onMessage: (data: WsEvent) => void): WebSocket | null {
	try {
		// Connect directly to backend (port 3000) bypassing Vite proxy
		const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
		const backendHost = window.location.hostname + ':3000';
		const token = getAuthToken();
		
		// Add token as query parameter if available
		const wsUrl = token 
			? `${protocol}//${backendHost}/ws?token=${encodeURIComponent(token)}`
			: `${protocol}//${backendHost}/ws`;
		
		const ws = new WebSocket(wsUrl);

		ws.onopen = () => {
			console.log('✅ WebSocket connecté au backend');
		};

		ws.onmessage = (event) => {
			try {
				const data = JSON.parse(event.data);
				// Ignore internal messages
				if (data.type === 'connected') {
					console.log('🔌 WebSocket ready');
					return;
				}
				onMessage(data);
			} catch {
				onMessage({ type: 'raw', data: event.data });
			}
		};

		ws.onerror = (error) => {
			console.warn('⚠️ WebSocket error (notifications désactivées):', error);
		};

		ws.onclose = (event) => {
			console.log(`WebSocket fermé (code: ${event.code}), reconnexion dans 10s...`);
			setTimeout(() => connectWebSocket(onMessage), 10000);
		};

		return ws;
	} catch (error) {
		console.warn('Impossible de connecter le WebSocket, notifications désactivées:', error);
		return null;
	}
}

// ══════════════════════════════════════════════════
// TYPES — Enrichment (Fanart.tv, OMDb, TheTVDB)
// ══════════════════════════════════════════════════

export interface FanartImage {
	id?: string;
	url?: string;
	lang?: string;
	likes?: string;
}

export interface FanartMovieImages {
	hdmovielogo: FanartImage[];
	hdmovieclearart: FanartImage[];
	movieposter: FanartImage[];
	moviebackground: FanartImage[];
	moviethumb: FanartImage[];
	moviebanner: FanartImage[];
	moviedisc: FanartImage[];
	movieart: FanartImage[];
}

export interface FanartTvImages {
	hdtvlogo: FanartImage[];
	hdclearart: FanartImage[];
	tvposter: FanartImage[];
	tvbanner: FanartImage[];
	tvthumb: FanartImage[];
	showbackground: FanartImage[];
	seasonposter: FanartImage[];
	seasonbanner: FanartImage[];
	characterart: FanartImage[];
}

export interface OmdbRating {
	Source: string;
	Value: string;
}

export interface OmdbResponse {
	Title?: string;
	Year?: string;
	Rated?: string;
	Released?: string;
	Runtime?: string;
	Genre?: string;
	Director?: string;
	Writer?: string;
	Actors?: string;
	Plot?: string;
	Language?: string;
	Country?: string;
	Awards?: string;
	Poster?: string;
	Ratings: OmdbRating[];
	imdbRating?: string;
	imdbVotes?: string;
	imdbID?: string;
	Type?: string;
	BoxOffice?: string;
	Production?: string;
}

export interface ThetvdbSeries {
	id?: number;
	name?: string;
	slug?: string;
	image?: string;
	year?: string;
	overview?: string;
	status?: { name?: string };
	firstAired?: string;
	originalNetwork?: string;
	averageRuntime?: number;
}

export interface ThetvdbEpisode {
	id?: number;
	name?: string;
	overview?: string;
	image?: string;
	seasonNumber?: number;
	number?: number;
	aired?: string;
	runtime?: number;
}

export interface ThetvdbArtwork {
	id?: number;
	image?: string;
	thumbnail?: string;
	type?: number;
	language?: string;
	score?: number;
}

// ══════════════════════════════════════════════════
// API — Fanart.tv
// ══════════════════════════════════════════════════

export function getFanartMovie(tmdbId: number): Promise<FanartMovieImages> {
	return request(`/fanart/movie/${tmdbId}`);
}

export function getFanartTv(tvdbId: number): Promise<FanartTvImages> {
	return request(`/fanart/tv/${tvdbId}`);
}

// ══════════════════════════════════════════════════
// API — OMDb
// ══════════════════════════════════════════════════

export function getOmdbByImdbId(imdbId: string): Promise<OmdbResponse> {
	return request(`/omdb/imdb/${imdbId}`);
}

export function searchOmdb(title: string, year?: number): Promise<OmdbResponse> {
	const params = new URLSearchParams({ title });
	if (year) params.set('year', year.toString());
	return request(`/omdb/search?${params}`);
}

// ══════════════════════════════════════════════════
// API — TheTVDB
// ══════════════════════════════════════════════════

export function getThetvdbSeries(tvdbId: number): Promise<ThetvdbSeries | null> {
	return request(`/thetvdb/series/${tvdbId}`);
}

export function getThetvdbEpisodes(tvdbId: number, season?: number): Promise<ThetvdbEpisode[]> {
	const params = season !== undefined ? `?season=${season}` : '';
	return request(`/thetvdb/series/${tvdbId}/episodes${params}`);
}

export function getThetvdbArtworks(tvdbId: number): Promise<ThetvdbArtwork[]> {
	return request(`/thetvdb/series/${tvdbId}/artworks`);
}

export function searchThetvdb(query: string): Promise<ThetvdbSeries[]> {
	return request(`/thetvdb/search?query=${encodeURIComponent(query)}`);
}

// ══════════════════════════════════════════════════
// TYPES — TVMaze
// ══════════════════════════════════════════════════

export interface TvMazeShow {
	id: number;
	url?: string;
	name?: string;
	type?: string;
	language?: string;
	genres?: string[];
	status?: string;
	runtime?: number;
	premiered?: string;
	ended?: string;
	rating?: { average?: number };
	network?: { id?: number; name?: string; country?: { name?: string; code?: string } };
	image?: { medium?: string; original?: string };
	summary?: string;
}

export interface TvMazeSearchResult {
	score?: number;
	show: TvMazeShow;
}

export interface TvMazeEpisode {
	id: number;
	name?: string;
	season?: number;
	number?: number;
	airdate?: string;
	runtime?: number;
	rating?: { average?: number };
	image?: { medium?: string; original?: string };
	summary?: string;
}

export interface TvMazeCastMember {
	person?: { id: number; name?: string; image?: { medium?: string; original?: string } };
	character?: { id: number; name?: string; image?: { medium?: string; original?: string } };
}

// ══════════════════════════════════════════════════
// API — TVMaze
// ══════════════════════════════════════════════════

export function searchTvMaze(query: string): Promise<TvMazeSearchResult[]> {
	return request(`/tvmaze/search?query=${encodeURIComponent(query)}`);
}

export function getTvMazeShow(showId: number): Promise<TvMazeShow | null> {
	return request(`/tvmaze/show/${showId}`);
}

export function getTvMazeEpisodes(showId: number): Promise<TvMazeEpisode[]> {
	return request(`/tvmaze/show/${showId}/episodes`);
}

export function getTvMazeCast(showId: number): Promise<TvMazeCastMember[]> {
	return request(`/tvmaze/show/${showId}/cast`);
}

export function lookupTvMazeByTvdb(tvdbId: number): Promise<TvMazeShow | null> {
	return request(`/tvmaze/lookup/tvdb/${tvdbId}`);
}

// ══════════════════════════════════════════════════
// TYPES — Jikan (Anime/MyAnimeList)
// ══════════════════════════════════════════════════

export interface JikanAnime {
	mal_id: number;
	url?: string;
	images?: { jpg?: { image_url?: string; large_image_url?: string }; webp?: { image_url?: string; large_image_url?: string } };
	trailer?: { youtube_id?: string; url?: string };
	title?: string;
	title_english?: string;
	title_japanese?: string;
	type?: string;
	episodes?: number;
	status?: string;
	airing?: boolean;
	duration?: string;
	rating?: string;
	score?: number;
	scored_by?: number;
	rank?: number;
	popularity?: number;
	members?: number;
	synopsis?: string;
	season?: string;
	year?: number;
	genres?: { mal_id: number; name?: string }[];
	studios?: { mal_id: number; name?: string }[];
}

export interface JikanResponse<T> {
	data: T;
	pagination?: { last_visible_page?: number; has_next_page?: boolean };
}

export interface JikanCharacter {
	character?: { mal_id: number; name?: string; images?: { jpg?: { image_url?: string } } };
	role?: string;
}

// ══════════════════════════════════════════════════
// API — Jikan (Anime)
// ══════════════════════════════════════════════════

export function searchJikanAnime(query: string, page?: number): Promise<JikanResponse<JikanAnime[]>> {
	const params = new URLSearchParams({ query });
	if (page) params.set('page', page.toString());
	return request(`/jikan/anime/search?${params}`);
}

export function getJikanAnime(malId: number): Promise<JikanAnime | null> {
	return request(`/jikan/anime/${malId}`);
}

export function getJikanTopAnime(filter?: string, page?: number, limit?: number): Promise<JikanResponse<JikanAnime[]>> {
	const params = new URLSearchParams();
	if (filter) params.set('filter', filter);
	if (page) params.set('page', page.toString());
	if (limit) params.set('limit', limit.toString());
	return request(`/jikan/anime/top?${params}`);
}

export function getJikanSeasonAnime(year: number, season: string, page?: number): Promise<JikanResponse<JikanAnime[]>> {
	const params = new URLSearchParams({ year: year.toString(), season });
	if (page) params.set('page', page.toString());
	return request(`/jikan/anime/season?${params}`);
}

export function getJikanAnimeCharacters(malId: number): Promise<JikanCharacter[]> {
	return request(`/jikan/anime/${malId}/characters`);
}

export function getJikanAnimeRecommendations(malId: number): Promise<any> {
	return request(`/jikan/anime/${malId}/recommendations`);
}

// ══════════════════════════════════════════════════
// TYPES — Trakt
// ══════════════════════════════════════════════════

export interface TraktIds {
	trakt?: number;
	slug?: string;
	imdb?: string;
	tmdb?: number;
	tvdb?: number;
}

export interface TraktMovie {
	title?: string;
	year?: number;
	ids?: TraktIds;
}

export interface TraktShow {
	title?: string;
	year?: number;
	ids?: TraktIds;
}

export interface TraktTrendingMovie {
	watchers?: number;
	movie: TraktMovie;
}

export interface TraktTrendingShow {
	watchers?: number;
	show: TraktShow;
}

// ══════════════════════════════════════════════════
// API — Trakt
// ══════════════════════════════════════════════════

export function getTraktTrendingMovies(limit?: number): Promise<TraktTrendingMovie[]> {
	const params = limit ? `?limit=${limit}` : '';
	return request(`/trakt/movies/trending${params}`);
}

export function getTraktPopularMovies(limit?: number): Promise<TraktMovie[]> {
	const params = limit ? `?limit=${limit}` : '';
	return request(`/trakt/movies/popular${params}`);
}

export function getTraktTrendingShows(limit?: number): Promise<TraktTrendingShow[]> {
	const params = limit ? `?limit=${limit}` : '';
	return request(`/trakt/shows/trending${params}`);
}

export function getTraktPopularShows(limit?: number): Promise<TraktShow[]> {
	const params = limit ? `?limit=${limit}` : '';
	return request(`/trakt/shows/popular${params}`);
}

export function getTraktRelatedMovies(id: string, limit?: number): Promise<TraktMovie[]> {
	const params = limit ? `?limit=${limit}` : '';
	return request(`/trakt/movies/${id}/related${params}`);
}

export function getTraktRelatedShows(id: string, limit?: number): Promise<TraktShow[]> {
	const params = limit ? `?limit=${limit}` : '';
	return request(`/trakt/shows/${id}/related${params}`);
}

// ══════════════════════════════════════════════════
// TYPES — TasteDive
// ══════════════════════════════════════════════════

export interface TasteDiveItem {
	Name?: string;
	Type?: string;
	wTeaser?: string;
	wUrl?: string;
	yUrl?: string;
	yID?: string;
}

export interface TasteDiveResponse {
	similar: { info: TasteDiveItem[]; results: TasteDiveItem[] };
}

// ══════════════════════════════════════════════════
// API — TasteDive
// ══════════════════════════════════════════════════

export function getTasteDiveSimilar(query: string, mediaType?: string, limit?: number): Promise<TasteDiveResponse> {
	const params = new URLSearchParams({ query });
	if (mediaType) params.set('type', mediaType);
	if (limit) params.set('limit', limit.toString());
	return request(`/tastedive/similar?${params}`);
}

// ══════════════════════════════════════════════════
// TYPES — Watchmode
// ══════════════════════════════════════════════════

export interface WatchmodeTitle {
	id?: number;
	name?: string;
	year?: number;
	type?: string;
	tmdb_id?: number;
	imdb_id?: string;
}

export interface WatchmodeSource {
	source_id?: number;
	name?: string;
	type?: string;
	region?: string;
	web_url?: string;
	format?: string;
	price?: number;
}

export interface WatchmodeSearchResult {
	title_results?: WatchmodeTitle[];
}

// ══════════════════════════════════════════════════
// API — Watchmode
// ══════════════════════════════════════════════════

export function searchWatchmode(query: string): Promise<WatchmodeSearchResult> {
	return request(`/watchmode/search?query=${encodeURIComponent(query)}`);
}

export function getWatchmodeSources(titleId: number): Promise<WatchmodeSource[]> {
	return request(`/watchmode/title/${titleId}/sources`);
}

export function getWatchmodeSourcesByTmdb(tmdbId: number, mediaType: string): Promise<WatchmodeSource[]> {
	return request(`/watchmode/sources/tmdb/${tmdbId}/${mediaType}`);
}

// ══════════════════════════════════════════════════
// IMDBBOT (Alternative IMDb)
// ══════════════════════════════════════════════════

export interface ImdbSearchResult {
	result_type?: string;
	id?: string;
	image?: string;
	description?: string;
	title?: string;
}

export interface ImdbMovie {
	id?: string;
	title?: string;
	year?: number;
	description?: string;
	image?: string;
	imdb_id?: string;
	rating?: number;
	vote_count?: number;
}

export function searchImdbBot(query: string): Promise<ImdbSearchResult[]> {
	return request(`/imdbbot/search?q=${encodeURIComponent(query)}`);
}

export function getImdbbotDetails(imdbId: string): Promise<ImdbMovie | null> {
	return request(`/imdbbot/${imdbId}`);
}

export function getImdbbotRatings(imdbId: string): Promise<Record<string, any> | null> {
	return request(`/imdbbot/${imdbId}/ratings`);
}

// ══════════════════════════════════════════════════
// SIMKL (Movie + TV + Anime + Streaming)
// ══════════════════════════════════════════════════

export interface SimklSearchResult {
	id?: number;
	title?: string;
	year?: number;
	poster?: string;
	fanart?: string;
	type?: string;
	rating?: number;
	imdb?: string;
	tmdb?: number;
}

export interface SimklShowDetails {
	id?: number;
	title?: string;
	year?: number;
	description?: string;
	poster?: string;
	fanart?: string;
	seasons?: number;
	episodes?: number;
	rating?: number;
	genres?: string[];
	country?: string;
	runtime?: number;
}

export interface SimklStreamSource {
	source?: string;
	url?: string;
	region?: string;
	is_free?: boolean;
}

export function searchSimkl(query: string, type: string = 'show'): Promise<SimklSearchResult[]> {
	return request(`/simkl/search?q=${encodeURIComponent(query)}&type=${type}`);
}

export function getSimklDetails(id: number, type: string = 'show'): Promise<SimklShowDetails | null> {
	return request(`/simkl/${id}/${type}`);
}

export function getSimklTrending(type: string = 'show'): Promise<SimklSearchResult[]> {
	return request(`/simkl/trending/${type}`);
}

export function getSimklSources(id: number): Promise<SimklStreamSource[]> {
	return request(`/simkl/${id}/sources`);
}

// ══════════════════════════════════════════════════
// UNOGS (Netflix Search by Region)
// ══════════════════════════════════════════════════

export interface UnogsResult {
	id?: string;
	title?: string;
	image?: string;
	year?: number;
	type?: string;
	imdb_id?: string;
}

export interface UnogsRegion {
	id?: string;
	country?: string;
}

export function searchUnogs(query: string, type: string = 'movie'): Promise<UnogsResult[]> {
	return request(`/unogs/search?q=${encodeURIComponent(query)}&type=${type}`);
}

export function getUnogsFromImdb(imdbId: string): Promise<UnogsResult | null> {
	return request(`/unogs/imdb/${imdbId}`);
}

export function getUnogsRegions(): Promise<UnogsRegion[]> {
	return request(`/unogs/regions`);
}

export function searchUnogsInRegion(region: string, query: string): Promise<UnogsResult[]> {
	return request(`/unogs/region/${region}/search?q=${encodeURIComponent(query)}`);
}

// ══════════════════════════════════════════════════
// STREAM (Czech Streams)
// ══════════════════════════════════════════════════

export interface StreamItem {
	id?: string;
	title?: string;
	description?: string;
	image?: string;
	url?: string;
	category?: string;
	country?: string;
}

export interface StreamProgram {
	id?: string;
	title?: string;
	description?: string;
	image?: string;
	start_time?: string;
	end_time?: string;
	channel?: string;
}

// ══════════════════════════════════════════════════
// COLLECTIONS (Game of Thrones, Breaking Bad, etc)
// ══════════════════════════════════════════════════

export interface Collection {
	id: string;
	name: string;
	description?: string;
	category: string;
	api_source: string;
	cover_image_url?: string;
	backdrop_url?: string;
	created_at: string;
}

export interface CollectionItem {
	id: string;
	collection_id: string;
	external_id?: string;
	name: string;
	description?: string;
	image_url?: string;
	item_type?: string;
	data_json?: Record<string, any>;
	created_at: string;
}

export function getCollections(): Promise<Collection[]> {
	return request('/collections');
}

export function getCollection(id: string): Promise<Collection> {
	return request(`/collections/${id}`);
}

export function getCollectionItems(id: string, page: number = 1, limit: number = 20): Promise<CollectionItem[]> {
	return request(`/collections/${id}/items?page=${page}&limit=${limit}`);
}

export function getCollectionItemsByType(
	id: string,
	itemType: string,
	page: number = 1,
	limit: number = 20
): Promise<CollectionItem[]> {
	return request(`/collections/${id}/items/${itemType}?page=${page}&limit=${limit}`);
}

// ══════════════════════════════════════════════════
// TV CHANNELS & EPG
// ══════════════════════════════════════════════════

export interface TvChannel {
	id: string;
	name: string;
	code: string;
	country?: string;
	logo_url?: string;
	category?: string;
	is_free: boolean;
	is_active: boolean;
	stream_url?: string;
	created_at: string;
}

export interface TvProgram {
	id: string;
	channel_id: string;
	title: string;
	description?: string;
	start_time: string;
	end_time: string;
	genre?: string;
	image_url?: string;
	rating?: number;
	external_id?: string;
	created_at: string;
}

export function getTvChannels(): Promise<TvChannel[]> {
	return request('/tv/channels');
}

export function getTvChannel(id: string): Promise<TvChannel> {
	return request(`/tv/channels/${id}`);
}

export function getTvChannelPrograms(
	id: string,
	page: number = 1,
	limit: number = 50
): Promise<TvProgram[]> {
	return request(`/tv/channels/${id}/programs?page=${page}&limit=${limit}`);
}

export function getTvChannelStream(code: string): Promise<{
	id: string;
	name: string;
	code: string;
	stream_url?: string;
	logo_url?: string;
}> {
	return request(`/tv/channels/${code}/stream`);
}

export function getTvProgramsNow(): Promise<TvProgram[]> {
	return request('/tv/programs/now');
}

export function searchTvPrograms(query: string, limit: number = 20): Promise<TvProgram[]> {
	return request(`/tv/programs/search?q=${encodeURIComponent(query)}&limit=${limit}`);
}

export function searchStream(query: string): Promise<StreamItem[]> {
	return request(`/stream/search?q=${encodeURIComponent(query)}`);
}

export function getStreamDetails(id: string): Promise<StreamItem | null> {
	return request(`/stream/${id}`);
}

export function getStreamByCategory(category: string): Promise<StreamItem[]> {
	return request(`/stream/category/${category}`);
}
