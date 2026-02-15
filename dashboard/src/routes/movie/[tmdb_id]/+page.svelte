<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import {
		tmdbMovieDetails, tmdbCredits, tmdbVideos, tmdbSimilar,
		tmdbImageUrl, addToLibrary, removeFromLibrary,
		addToWatchlist, removeFromWatchlist, getLibraryStatus, createMedia, triggerSearch, startDownload, getSearchResults
	} from '$lib/api/client';
	import type { TmdbMovieDetail, TmdbCastMember, TmdbVideo, TmdbSearchItem, LibraryStatus, Media, SearchResult } from '$lib/api/client';
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
	}

	function handlePlay() {
		if (!movie) return;
		goto(`/watch/movie/${movie.id}`);
	}

	async function handleDownload() {
		if (!movie) return;
		goto(`/downloads?query=${encodeURIComponent(movie.title)}&tmdbId=${movie.id}`);
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
		if (libraryStatus?.in_watchlist) {
			await removeFromWatchlist(movie.id, 'movie');
			if (libraryStatus) libraryStatus = { ...libraryStatus, in_watchlist: false };
		} else {
			await addToWatchlist({
				tmdb_id: movie.id, media_type: 'movie', title: movie.title,
				poster_url: tmdbImageUrl(movie.poster_path)
			});
			if (libraryStatus) libraryStatus = { ...libraryStatus, in_watchlist: true };
			else libraryStatus = { in_library: false, in_watchlist: true, watch_progress: null, completed: false };
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
	<!-- Breadcrumbs -->
	<nav class="breadcrumbs" aria-label="Fil d'Ariane">
		<a href="/">Accueil</a>
		<span class="bc-sep">›</span>
		<a href="/films">Films</a>
		<span class="bc-sep">›</span>
		<span class="bc-current">{movie.title}</span>
	</nav>

	<!-- Backdrop Hero -->
	<div class="detail-hero">
		{#if movie.backdrop_path}
			<div class="hero-backdrop" style="background-image:url({tmdbImageUrl(movie.backdrop_path, 'original')})"></div>
		{/if}
		<div class="hero-gradient"></div>

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
				</div>

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
	/* ── Breadcrumbs ── */
	.breadcrumbs {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 13px;
		color: var(--text-muted);
		padding: 0 0 16px;
	}

	.breadcrumbs a {
		color: var(--text-secondary);
		text-decoration: none;
		transition: color var(--transition-fast);
	}

	.breadcrumbs a:hover { color: var(--text-primary); }

	.bc-sep { color: var(--text-muted); }

	.bc-current {
		color: var(--text-primary);
		font-weight: 500;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 200px;
	}

	.error-state {
		padding: 60px 24px;
		text-align: center;
		color: var(--text-secondary);
	}

	.btn-back {
		display: inline-block;
		margin-top: 16px;
		color: var(--accent);
		text-decoration: none;
	}

	.detail-skeleton { animation: none; }

	/* ── Hero ── */
	.detail-hero {
		position: relative;
		min-height: 480px;
		display: flex;
		align-items: flex-end;
		margin: -24px -24px 0;
		overflow: hidden;
	}

	.hero-backdrop {
		position: absolute;
		inset: 0;
		background-size: cover;
		background-position: center 20%;
	}

	.hero-gradient {
		position: absolute;
		inset: 0;
		background: linear-gradient(to bottom, rgba(10,10,15,0.3) 0%, rgba(10,10,15,0.8) 60%, var(--bg-primary) 100%);
	}

	.hero-content {
		position: relative;
		z-index: 1;
		display: flex;
		align-items: flex-end;
		gap: 28px;
		padding: 32px;
		width: 100%;
	}

	.hero-poster {
		width: 160px;
		border-radius: var(--radius);
		box-shadow: 0 8px 32px rgba(0,0,0,0.5);
		flex-shrink: 0;
	}

	.hero-info { flex: 1; min-width: 0; }

	.tagline {
		color: var(--text-secondary);
		font-style: italic;
		font-size: 14px;
		margin-bottom: 8px;
	}

	.detail-title {
		font-size: clamp(24px, 3.5vw, 42px);
		font-weight: 800;
		color: #fff;
		line-height: 1.1;
		text-shadow: 0 2px 8px rgba(0,0,0,0.4);
		margin-bottom: 10px;
	}

	.detail-meta {
		display: flex;
		align-items: center;
		gap: 12px;
		color: var(--text-secondary);
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
		background: rgba(108,92,231,0.2);
		border: 1px solid rgba(108,92,231,0.4);
		color: var(--accent);
		font-size: 12px;
		padding: 3px 10px;
		border-radius: 20px;
	}

	.action-buttons {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
	}

	.btn-primary {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 22px;
		background: var(--accent);
		color: #fff;
		border: none;
		border-radius: var(--radius-sm);
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
		transition: all var(--transition-fast);
	}

	.btn-primary:hover { background: var(--accent-hover); transform: scale(1.02); }
	.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

	.btn-outline {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 10px 18px;
		background: rgba(255,255,255,0.08);
		color: var(--text-primary);
		border: 1px solid rgba(255,255,255,0.2);
		border-radius: var(--radius-sm);
		font-size: 13px;
		font-weight: 500;
		cursor: pointer;
		transition: all var(--transition-fast);
	}

	.btn-outline:hover { background: rgba(255,255,255,0.15); border-color: rgba(255,255,255,0.4); }
	.btn-outline.active { background: rgba(108,92,231,0.2); border-color: var(--accent); color: var(--accent); }

	/* ── Body ── */
	.detail-body { padding: 32px 0; }

	.detail-section { margin-bottom: 40px; }

	.section-title {
		font-size: 18px;
		font-weight: 700;
		color: var(--text-primary);
		margin-bottom: 14px;
	}

	.overview {
		color: var(--text-secondary);
		font-size: 15px;
		line-height: 1.7;
		max-width: 800px;
	}

	/* ── Trailer ── */
	.trailer-wrap {
		position: relative;
		aspect-ratio: 16 / 9;
		max-width: 800px;
		border-radius: var(--radius);
		overflow: hidden;
		background: #000;
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
		border-radius: var(--radius-sm);
		overflow: hidden;
		background: var(--bg-card);
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
		background: var(--bg-secondary);
	}

	.cast-name {
		font-size: 12px;
		font-weight: 600;
		color: var(--text-primary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.cast-character {
		font-size: 11px;
		color: var(--text-muted);
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
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		padding: 15px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.torrent-title {
		font-size: 15px;
		font-weight: 600;
		color: var(--text-primary);
	}

	.torrent-meta {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
		font-size: 12px;
		color: var(--text-secondary);
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
</style>