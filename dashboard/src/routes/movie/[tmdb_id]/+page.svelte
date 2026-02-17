<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import {
		tmdbMovieDetails, tmdbCredits, tmdbVideos, tmdbSimilar,
		tmdbImageUrl, addToLibrary, removeFromLibrary,
		addToWatchlist, removeFromWatchlist, getLibraryStatus, createMedia, triggerSearch, startDownload, getSearchResults,
		getOmdbByImdbId, getFanartMovie
	} from '$lib/api/client';
	import type { TmdbMovieDetail, TmdbCastMember, TmdbVideo, TmdbSearchItem, LibraryStatus, Media, SearchResult, OmdbResponse, FanartMovieImages } from '$lib/api/client';
	import MediaRow from '$lib/components/MediaRow.svelte';
	import Skeleton from '$lib/components/Skeleton.svelte';

	const tmdbId = $derived(Number($page.params.tmdb_id));

	let movie: TmdbMovieDetail | null = $state(null);
	let cast: TmdbCastMember[] = $state([]);
	let videos: TmdbVideo[] = $state([]);
	let similar: TmdbSearchItem[] = $state([]);
	let libraryStatus: LibraryStatus | null = $state(null);
	let loading = $state(true);
	let error = $state('');

	// Enrichment data
	let omdbData: OmdbResponse | null = $state(null);
	let fanartData: FanartMovieImages | null = $state(null);
	// Optimistic UI
	let libAdding = $state(false);

	// Download related state
	let showDownloadResults = $state(false);
	let searchResults: SearchResult[] = $state([]);
	let searchingDownloads = $state(false);
	let downloadError = $state('');

	// Trailer
	const trailer = $derived(videos.find(v => v.video_type === 'Trailer' && v.site === 'YouTube') ?? videos.find(v => v.site === 'YouTube') ?? null);

	$effect(() => {
		if (tmdbId) loadAll(tmdbId);
	});

	async function loadAll(id: number) {
		loading = true;
		error = '';
		try {
			const [m, c, v, s, ls] = await Promise.allSettled([
				tmdbMovieDetails(id),
				tmdbCredits('movie', id),
				tmdbVideos('movie', id),
				tmdbSimilar('movie', id),
				getLibraryStatus(id, 'movie').catch(() => null),
			]);
			if (m.status === 'fulfilled') movie = m.value;
			else { error = 'Impossible de charger ce film.'; loading = false; return; }
			if (c.status === 'fulfilled') cast = c.value.cast.slice(0, 20);
			if (v.status === 'fulfilled') videos = v.value;
			if (s.status === 'fulfilled') similar = s.value;
			if (ls.status === 'fulfilled' && ls.value) libraryStatus = ls.value as LibraryStatus;
		} catch (e: any) {
			error = `Erreur lors du chargement: ${e.message}`;
		}
		loading = false;

		// Load enrichment data in background (non-blocking)
		if (movie) {
			getFanartMovie(id).then(f => fanartData = f).catch(() => {});
			if (movie.imdb_id) {
				getOmdbByImdbId(movie.imdb_id).then(o => omdbData = o).catch(() => {});
			}
		}
	}

	function handlePlay() {
		if (!movie) return;
		goto(`/watch/movie/${movie.id}`);
	}

	async function handleDownload() {
		if (!movie) return;
		goto(`/downloads?query=${encodeURIComponent(movie.title)}&tmdbId=${movie.id}&mediaType=movie`);
	}

	async function startTorrentDownload(searchResultId: number, mediaId: string) {
		try {
			await startDownload({ media_id: mediaId, search_result_id: searchResultId });
			alert("Téléchargement démarré!");
		} catch (e: any) {
			alert(`Échec du démarrage du téléchargement: ${e.message}`);
		}
	}

	async function toggleLibrary() {
		if (!movie || libAdding) return;
		const wasIn = libraryStatus?.in_library ?? false;
		if (libraryStatus) libraryStatus = { ...libraryStatus, in_library: !wasIn };
		else libraryStatus = { in_library: true, in_watchlist: false, watch_progress: null, completed: false };
		libAdding = true;
		try {
			if (wasIn) {
				await removeFromLibrary(movie.id, 'movie');
			} else {
				await addToLibrary({
					tmdb_id: movie.id, media_type: 'movie', title: movie.title,
					poster_url: tmdbImageUrl(movie.poster_path),
					backdrop_url: tmdbImageUrl(movie.backdrop_path, 'w1280'),
					vote_average: movie.vote_average,
					release_date: movie.release_date,
					overview: movie.overview
				});
			}
		} catch {
			if (libraryStatus) libraryStatus = { ...libraryStatus, in_library: wasIn };
		}
		libAdding = false;
	}

	async function toggleWatchlist() {
		if (!movie) return;
		const wasIn = libraryStatus?.in_watchlist ?? false;
		if (libraryStatus) libraryStatus = { ...libraryStatus, in_watchlist: !wasIn };
		else libraryStatus = { in_library: false, in_watchlist: true, watch_progress: null, completed: false };
		try {
			if (wasIn) {
				await removeFromWatchlist(movie.id, 'movie');
			} else {
				await addToWatchlist({
					tmdb_id: movie.id, media_type: 'movie', title: movie.title,
					poster_url: tmdbImageUrl(movie.poster_path)
				});
			}
		} catch {
			if (libraryStatus) libraryStatus = { ...libraryStatus, in_watchlist: wasIn };
		}
	}

	function formatRuntime(min: number | null): string {
		if (!min) return '';
		return `${Math.floor(min / 60)}h ${min % 60}min`;
	}
</script>

<svelte:head>
	<title>{movie ? `${movie.title} — SOKOUL` : 'Chargement...'}</title>
</svelte:head>

{#if error}
	<div class="error-state">
		<p>{error}</p>
		<a href="/" class="btn-back">← Retour à l'accueil</a>
	</div>
{:else if loading}
	<div class="detail-skeleton">
		<Skeleton height="400px" borderRadius="0" />
		<div style="padding:24px; display:flex; gap:24px;">
			<Skeleton height="280px" width="180px" borderRadius="12px" />
			<div style="flex:1; display:flex; flex-direction:column; gap:12px;">
				<Skeleton height="36px" width="60%" />
				<Skeleton height="20px" width="40%" />
				<Skeleton height="16px" width="80%" />
				<Skeleton height="16px" width="70%" />
			</div>
		</div>
	</div>
{:else if movie}
	<!-- Backdrop Hero -->
	<div class="detail-hero">
		{#if movie.backdrop_path}
			<div class="hero-backdrop" style="background-image:url({tmdbImageUrl(movie.backdrop_path, 'original')})"></div>
		{/if}
		<div class="hero-gradient"></div>

		<a href="/films" class="back-btn" aria-label="Retour aux films">
			<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
			Films
		</a>

		<div class="hero-content">
			{#if movie.poster_path}
				<img class="hero-poster" src={tmdbImageUrl(movie.poster_path, 'w500')} alt={movie.title} />
			{/if}

			<div class="hero-info">
				{#if movie.tagline}
					<p class="tagline">{movie.tagline}</p>
				{/if}
				<h1 class="detail-title">{movie.title}</h1>

				<div class="detail-meta">
					{#if movie.release_date}
						<span>{movie.release_date.substring(0, 4)}</span>
					{/if}
					{#if movie.runtime}
						<span>{formatRuntime(movie.runtime)}</span>
					{/if}
					{#if movie.vote_average}
						<span class="rating">★ {movie.vote_average.toFixed(1)}</span>
					{/if}
					{#if omdbData?.imdbRating}
						<span class="rating imdb">IMDb {omdbData.imdbRating}</span>
					{/if}
					{#if omdbData?.Ratings}
						{#each omdbData.Ratings.filter(r => r.Source === 'Rotten Tomatoes') as rt}
							<span class="rating rt">🍅 {rt.Value}</span>
						{/each}
					{/if}
				</div>

				{#if omdbData?.Awards && omdbData.Awards !== 'N/A'}
					<p class="awards">🏆 {omdbData.Awards}</p>
				{/if}

				<div class="genres">
					{#each movie.genres as genre (genre.id)}
						<span class="genre-pill">{genre.name}</span>
					{/each}
				</div>

				<div class="action-buttons">
					<button class="btn-primary" onclick={handlePlay}>
						<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M8 5v14l11-7z"/></svg>
						Regarder
					</button>

					<button class="btn-outline" onclick={handleDownload} title="Télécharger">
						<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
							<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
						</svg>
						Télécharger
					</button>

					<button
						class="btn-outline {libraryStatus?.in_library ? 'active' : ''} {libAdding ? 'animate-bounce' : ''}"
						onclick={toggleLibrary}
						title={libraryStatus?.in_library ? 'Retirer de la bibliothèque' : 'Ajouter à la bibliothèque'}
					>
						<svg viewBox="0 0 24 24" fill={libraryStatus?.in_library ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" width="18" height="18">
							<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
						</svg>
						{libraryStatus?.in_library ? 'Dans ma bibliothèque' : 'Ajouter'}
					</button>

					<button
						class="btn-outline {libraryStatus?.in_watchlist ? 'active' : ''}"
						onclick={toggleWatchlist}
						title={libraryStatus?.in_watchlist ? 'Retirer de la liste d\'envies' : 'Ajouter à la liste d\'envies'}
					>
						<svg viewBox="0 0 24 24" fill={libraryStatus?.in_watchlist ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" width="18" height="18">
							<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
						</svg>
						{libraryStatus?.in_watchlist ? 'Dans ma liste' : 'Liste d\'envies'}
					</button>
				</div>
			</div>
		</div>
	</div>

	<!-- Torrent Search Results -->
	{#if showDownloadResults && movie}
		<section class="detail-section">
			<h2 class="section-title">Résultats de téléchargement</h2>
			{#if searchingDownloads}
				<p>Recherche de torrents pour "{movie.title}" en cours...</p>
			{:else if downloadError}
				<p class="error-message">{downloadError}</p>
			{:else if searchResults.length > 0}
				<div class="torrent-results">
					{#each searchResults as result (result.id)}
						<div class="torrent-item">
							<p class="torrent-title">{result.title}</p>
							<div class="torrent-meta">
								<span>Qualité: {result.quality || 'N/A'}</span>
								<span>Taille: {result.size_bytes ? (result.size_bytes / 1_000_000_000).toFixed(2) + ' GB' : 'N/A'}</span>
								<span>Seeders: {result.seeders}</span>
							</div>
							<button class="btn-primary btn-sm" onclick={() => startTorrentDownload(result.id, movie!.id.toString())}>
								Télécharger ce torrent
							</button>
						</div>
					{/each}
				</div>
			{:else}
				<p>Aucun résultat de torrent trouvé pour "{movie.title}".</p>
			{/if}
		</section>
	{/if}

	<!-- Body -->
	<div class="detail-body">

		<!-- Synopsis -->
		{#if movie.overview}
			<section class="detail-section">
				<h2 class="section-title">Synopsis</h2>
				<p class="overview">{movie.overview}</p>
			</section>
		{/if}

		<!-- Infos enrichies (OMDb) -->
		{#if omdbData && omdbData.Title}
			<section class="detail-section">
				<h2 class="section-title">Informations complémentaires</h2>
				<div class="enrichment-grid">
					{#if omdbData.Director && omdbData.Director !== 'N/A'}
						<div class="info-item"><span class="info-label">Réalisateur</span><span class="info-value">{omdbData.Director}</span></div>
					{/if}
					{#if omdbData.Writer && omdbData.Writer !== 'N/A'}
						<div class="info-item"><span class="info-label">Scénariste</span><span class="info-value">{omdbData.Writer}</span></div>
					{/if}
					{#if omdbData.BoxOffice && omdbData.BoxOffice !== 'N/A'}
						<div class="info-item"><span class="info-label">Box Office</span><span class="info-value">{omdbData.BoxOffice}</span></div>
					{/if}
					{#if omdbData.Country && omdbData.Country !== 'N/A'}
						<div class="info-item"><span class="info-label">Pays</span><span class="info-value">{omdbData.Country}</span></div>
					{/if}
					{#if omdbData.Language && omdbData.Language !== 'N/A'}
						<div class="info-item"><span class="info-label">Langue</span><span class="info-value">{omdbData.Language}</span></div>
					{/if}
					{#if omdbData.Rated && omdbData.Rated !== 'N/A'}
						<div class="info-item"><span class="info-label">Classification</span><span class="info-value">{omdbData.Rated}</span></div>
					{/if}
					{#if omdbData.Production && omdbData.Production !== 'N/A'}
						<div class="info-item"><span class="info-label">Production</span><span class="info-value">{omdbData.Production}</span></div>
					{/if}
				</div>
			</section>
		{/if}

		<!-- Fanart.tv Images -->
		{#if fanartData && (fanartData.hdmovielogo?.length > 0 || fanartData.moviebackground?.length > 0)}
			<section class="detail-section">
				<h2 class="section-title">Galerie artistique</h2>
				<div class="fanart-gallery">
					{#if fanartData.hdmovielogo?.length > 0}
						<img class="fanart-logo" src={fanartData.hdmovielogo[0].url} alt="Logo HD" loading="lazy" />
					{/if}
					{#each fanartData.moviebackground?.slice(0, 4) ?? [] as bg}
						<img class="fanart-bg" src={bg.url} alt="Background" loading="lazy" />
					{/each}
				</div>
			</section>
		{/if}

		<!-- Trailer -->
		{#if trailer}
			<section class="detail-section">
				<h2 class="section-title">Bande-annonce</h2>
				<div class="trailer-wrap">
					<iframe
						src="https://www.youtube.com/embed/{trailer.key}"
						title={trailer.name ?? 'Bande-annonce'}
						allowfullscreen
						allow="fullscreen; autoplay; encrypted-media"
					></iframe>
				</div>
			</section>
		{/if}

		<!-- Cast -->
		{#if cast.length > 0}
			<section class="detail-section">
				<h2 class="section-title">Distribution</h2>
				<div class="cast-row">
					{#each cast as actor (actor.id)}
						<a href="/person/{actor.id}" class="cast-card">
							<div class="cast-photo">
								{#if actor.profile_path}
									<img src={tmdbImageUrl(actor.profile_path, 'w185')} alt={actor.name} loading="lazy" />
								{:else}
									<div class="cast-no-photo">
										<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
											<path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
										</svg>
									</div>
								{/if}
							</div>
							<p class="cast-name">{actor.name}</p>
							{#if actor.character}
								<p class="cast-character">{actor.character}</p>
							{/if}
						</a>
					{/each}
				</div>
			</section>
		{/if}

		<!-- Similar -->
		{#if similar.length > 0}
			<MediaRow title="Films similaires" items={similar} />
		{/if}

	</div>
{/if}

<style>
	/* ── Back button ── */
	.back-btn {
		position: absolute;
		top: 80px;
		left: 20px;
		z-index: 5;
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 8px 16px;
		background: rgba(0, 0, 0, 0.5);
		backdrop-filter: blur(8px);
		color: rgba(249, 249, 249, 0.9);
		border-radius: 4px;
		font-size: 13px;
		font-weight: 500;
		text-decoration: none;
		transition: all var(--transition-fast);
	}

	.back-btn:hover {
		background: rgba(0, 0, 0, 0.7);
		color: #f9f9f9;
	}

	.error-state {
		padding: 60px 24px;
		text-align: center;
		color: rgba(249, 249, 249, 0.6);
	}

	.btn-back {
		display: inline-block;
		margin-top: 16px;
		color: var(--accent);
		text-decoration: none;
	}

	.detail-skeleton { animation: none; }

	/* ── Hero — Disney+ fixed background style ── */
	.detail-hero {
		position: relative;
		min-height: 100vh;
		display: flex;
		align-items: flex-end;
		overflow: hidden;
	}

	.hero-backdrop {
		position: fixed;
		inset: 0;
		background-size: cover;
		background-position: center 20%;
		opacity: 0.8;
		z-index: -1;
	}

	.hero-gradient {
		position: absolute;
		inset: 0;
		background: linear-gradient(to bottom, rgba(26, 29, 41, 0.3) 0%, rgba(26, 29, 41, 0.85) 60%, #1A1D29 100%);
	}

	.hero-content {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: flex-end;
		gap: 28px;
		padding: 32px calc(3.5vw + 5px);
		width: 100%;
	}

	.hero-poster {
		width: 160px;
		border-radius: 10px;
		box-shadow: rgb(0 0 0 / 69%) 0px 26px 30px -10px, rgb(0 0 0 / 73%) 0px 16px 10px -10px;
		flex-shrink: 0;
	}

	.hero-info { flex: 1; min-width: 0; }

	.tagline {
		color: rgba(249, 249, 249, 0.5);
		font-style: italic;
		font-size: 14px;
		margin-bottom: 8px;
	}

	.detail-title {
		font-size: clamp(24px, 3.5vw, 42px);
		font-weight: 800;
		color: #f9f9f9;
		line-height: 1.1;
		text-shadow: 0 2px 8px rgba(0,0,0,0.4);
		margin-bottom: 10px;
	}

	.detail-meta {
		display: flex;
		align-items: center;
		gap: 12px;
		color: rgba(249, 249, 249, 0.6);
		font-size: 14px;
		margin-bottom: 12px;
	}

	.rating { color: var(--warning); font-weight: 700; }

	.genres {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin-bottom: 20px;
	}

	.genre-pill {
		background: rgba(249, 249, 249, 0.1);
		border: 1px solid rgba(249, 249, 249, 0.2);
		color: rgba(249, 249, 249, 0.8);
		font-size: 12px;
		padding: 3px 10px;
		border-radius: 4px;
	}

	.action-buttons {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
		align-items: center;
	}

	/* Play button: accent gold */
	.btn-primary {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 12px 28px;
		background: var(--accent);
		color: #fff;
		border: none;
		border-radius: 4px;
		font-size: 15px;
		font-weight: 700;
		cursor: pointer;
		text-transform: uppercase;
		letter-spacing: 1.5px;
		transition: all var(--transition-fast);
	}

	.btn-primary:hover { background: var(--accent-hover); transform: scale(1.02); }
	.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

	/* Outline button: transparent, border */
	.btn-outline {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 10px 18px;
		background: rgba(0, 0, 0, 0.3);
		color: var(--text-primary);
		border: 2px solid var(--border);
		border-radius: 4px;
		font-size: 13px;
		font-weight: 600;
		cursor: pointer;
		text-transform: uppercase;
		letter-spacing: 1px;
		transition: all var(--transition-fast);
	}

	.btn-outline:hover { background: var(--bg-hover); border-color: var(--accent); }
	.btn-outline.active { background: rgba(0, 114, 210, 0.2); border-color: var(--accent); color: var(--text-primary); }

	/* ── Body ── */
	.detail-body { padding: 32px calc(3.5vw + 5px); }

	.detail-section { margin-bottom: 40px; }

	.section-title {
		font-size: 18px;
		font-weight: 700;
		color: #f9f9f9;
		margin-bottom: 14px;
	}

	.overview {
		color: rgba(249, 249, 249, 0.7);
		font-size: 15px;
		line-height: 1.7;
		max-width: 800px;
	}

	/* ── Trailer ── */
	.trailer-wrap {
		position: relative;
		aspect-ratio: 16 / 9;
		max-width: 800px;
		border-radius: 10px;
		overflow: hidden;
		background: #000;
		box-shadow: rgb(0 0 0 / 69%) 0px 26px 30px -10px;
	}

	.trailer-wrap iframe {
		width: 100%;
		height: 100%;
		border: none;
	}

	/* ── Cast ── */
	.cast-row {
		display: flex;
		gap: 12px;
		overflow-x: auto;
		padding: 4px 4px 12px;
		scrollbar-width: thin;
	}

	.cast-card {
		flex-shrink: 0;
		width: 100px;
		text-decoration: none;
		color: inherit;
		transition: transform var(--transition-fast);
	}

	.cast-card:hover { transform: scale(1.05); }

	.cast-photo {
		width: 100%;
		aspect-ratio: 2 / 3;
		border-radius: 10px;
		overflow: hidden;
		background: #252833;
		margin-bottom: 6px;
	}

	.cast-photo img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.cast-no-photo {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
		background: #252833;
	}

	.cast-name {
		font-size: 12px;
		font-weight: 600;
		color: #f9f9f9;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.cast-character {
		font-size: 11px;
		color: rgba(249, 249, 249, 0.5);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	/* ── Torrent Search Results ── */
	.torrent-results {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: 15px;
	}

	.torrent-item {
		background: #252833;
		border: 1px solid var(--border);
		border-radius: 10px;
		padding: 15px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.torrent-title {
		font-size: 15px;
		font-weight: 600;
		color: #f9f9f9;
	}

	.torrent-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
		font-size: 12px;
		color: rgba(249, 249, 249, 0.6);
	}

	.torrent-meta span:not(:last-child)::after {
		content: '•';
		margin-left: 10px;
	}

	.btn-sm {
		padding: 6px 12px;
		font-size: 12px;
		align-self: flex-start;
		margin-top: 10px;
	}

	.error-message {
		color: var(--danger);
		font-weight: 500;
	}

	/* ── Enrichment ── */
	.rating.imdb {
		color: #f5c518;
		font-weight: 700;
	}
	.rating.rt {
		color: #fa320a;
		font-weight: 600;
	}
	.awards {
		color: #f5c518;
		font-size: 13px;
		margin-top: 6px;
		font-style: italic;
	}
	.enrichment-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
		gap: 12px;
	}
	.info-item {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.info-label {
		font-size: 11px;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		color: rgba(249, 249, 249, 0.4);
	}
	.info-value {
		font-size: 14px;
		color: #f9f9f9;
	}
	.fanart-gallery {
		display: flex;
		flex-wrap: wrap;
		gap: 12px;
		align-items: center;
	}
	.fanart-logo {
		max-height: 80px;
		width: auto;
		object-fit: contain;
	}
	.fanart-bg {
		width: 280px;
		height: 160px;
		object-fit: cover;
		border-radius: 10px;
	}
</style>
