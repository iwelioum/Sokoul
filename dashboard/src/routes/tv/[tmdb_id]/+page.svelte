<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import {
		tmdbTvDetails, tmdbSeasonDetails, tmdbCredits, tmdbVideos, tmdbSimilar,
		tmdbImageUrl, addToLibrary, removeFromLibrary,
		addToWatchlist, removeFromWatchlist, getLibraryStatus, createMedia, triggerSearch, startDownload, getSearchResults,
		searchOmdb, getFanartMovie
	} from '$lib/api/client';
	import type { TmdbTvDetail, TmdbSeasonDetail, TmdbCastMember, TmdbVideo, TmdbSearchItem, LibraryStatus, Media, SearchResult, OmdbResponse, FanartMovieImages } from '$lib/api/client';
	import MediaRow from '$lib/components/MediaRow.svelte';
	import Skeleton from '$lib/components/Skeleton.svelte';

	const tmdbId = $derived(Number($page.params.tmdb_id));

	let show: TmdbTvDetail | null = $state(null);
	let cast: TmdbCastMember[] = $state([]);
	let videos: TmdbVideo[] = $state([]);
	let similar: TmdbSearchItem[] = $state([]);
	let libraryStatus: LibraryStatus | null = $state(null);
	let loading = $state(true);
	let error = $state('');

	// Enrichment data
	let omdbData: OmdbResponse | null = $state(null);
	let fanartData: FanartMovieImages | null = $state(null);

	// Season/episode selector
	let selectedSeason = $state(1);
	let seasonDetail: TmdbSeasonDetail | null = $state(null);
	let loadingSeason = $state(false);

	// Optimistic UI
	let libAdding = $state(false);

	// Download related state
	let showDownloadResults = $state(false);
	let searchResults: SearchResult[] = $state([]);
	let searchingDownloads = $state(false);
	let downloadError = $state('');

	const trailer = $derived(
		videos.find(v => v.video_type === 'Trailer' && v.site === 'YouTube') ??
		videos.find(v => v.site === 'YouTube') ?? null
	);

	$effect(() => {
		if (tmdbId) loadAll(tmdbId);
	});

	$effect(() => {
		if (show && selectedSeason) loadSeason(tmdbId, selectedSeason);
	});

	async function loadAll(id: number) {
		loading = true;
		error = '';
		try {
			const [s, c, v, sim, ls] = await Promise.allSettled([
				tmdbTvDetails(id),
				tmdbCredits('tv', id),
				tmdbVideos('tv', id),
				tmdbSimilar('tv', id),
				getLibraryStatus(id, 'tv').catch(() => null),
			]);
			if (s.status === 'fulfilled') {
				show = s.value;
				selectedSeason = show?.seasons?.find(s => s.season_number > 0)?.season_number ?? 1;
			} else {
				error = 'Impossible de charger cette série.';
				loading = false;
				return;
			}
			if (c.status === 'fulfilled') cast = c.value.cast.slice(0, 20);
			if (v.status === 'fulfilled') videos = v.value;
			if (sim.status === 'fulfilled') similar = sim.value;
			if (ls.status === 'fulfilled' && ls.value) libraryStatus = ls.value as LibraryStatus;
		} catch (e: any) {
			error = `Erreur lors du chargement: ${e.message}`;
		}
		loading = false;

		// Load enrichment data in background (non-blocking)
		if (show) {
			getFanartMovie(id).then(f => fanartData = f).catch(() => {});
			searchOmdb(show.name, show.first_air_date ? parseInt(show.first_air_date.substring(0, 4)) : undefined)
				.then(o => omdbData = o).catch(() => {});
		}
	}

	async function loadSeason(showId: number, seasonNumber: number) {
		loadingSeason = true;
		try {
			seasonDetail = await tmdbSeasonDetails(showId, seasonNumber);
		} catch {
			seasonDetail = null;
		}
		loadingSeason = false;
	}

	function handlePlay(season?: number, episode?: number) {
		if (!show) return;
		const s = season ?? selectedSeason;
		const e = episode ?? 1;
		goto(`/watch/tv/${show.id}?season=${s}&episode=${e}`);
	}

	async function handleDownload() {
		if (!show) return;
		goto(`/downloads?query=${encodeURIComponent(show.name)}&tmdbId=${show.id}&mediaType=tv`);
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
		if (!show || libAdding) return;
		const wasIn = libraryStatus?.in_library ?? false;
		if (libraryStatus) libraryStatus = { ...libraryStatus, in_library: !wasIn };
		else libraryStatus = { in_library: true, in_watchlist: false, watch_progress: null, completed: false };
		libAdding = true;
		try {
			if (wasIn) {
				await removeFromLibrary(show.id, 'tv');
			} else {
				await addToLibrary({
					tmdb_id: show.id, media_type: 'tv', title: show.name,
					poster_url: tmdbImageUrl(show.poster_path),
					backdrop_url: tmdbImageUrl(show.backdrop_path, 'w1280'),
					vote_average: show.vote_average,
					release_date: show.first_air_date,
					overview: show.overview
				});
			}
		} catch {
			if (libraryStatus) libraryStatus = { ...libraryStatus, in_library: wasIn };
		}
		libAdding = false;
	}

	async function toggleWatchlist() {
		if (!show) return;
		const wasIn = libraryStatus?.in_watchlist ?? false;
		if (libraryStatus) libraryStatus = { ...libraryStatus, in_watchlist: !wasIn };
		else libraryStatus = { in_library: false, in_watchlist: true, watch_progress: null, completed: false };
		try {
			if (wasIn) {
				await removeFromWatchlist(show.id, 'tv');
			} else {
				await addToWatchlist({
					tmdb_id: show.id, media_type: 'tv', title: show.name,
					poster_url: tmdbImageUrl(show.poster_path)
				});
			}
		} catch {
			if (libraryStatus) libraryStatus = { ...libraryStatus, in_watchlist: wasIn };
		}
	}
</script>

<svelte:head>
	<title>{show ? `${show.name} — SOKOUL` : 'Chargement...'}</title>
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
{:else if show}
	<!-- Backdrop Hero -->
	<div class="detail-hero">
		{#if show.backdrop_path}
			<div class="hero-backdrop" style="background-image:url({tmdbImageUrl(show.backdrop_path, 'original')})"></div>
		{/if}
		<div class="hero-gradient"></div>

		<a href="/series" class="back-btn" aria-label="Retour aux séries">
			<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
			Séries
		</a>

		<div class="hero-content">
			{#if show.poster_path}
				<img class="hero-poster" src={tmdbImageUrl(show.poster_path, 'w500')} alt={show.name} />
			{/if}

			<div class="hero-info">
				{#if show.tagline}
					<p class="tagline">{show.tagline}</p>
				{/if}
				<h1 class="detail-title">{show.name}</h1>

				<div class="detail-meta">
					{#if show.first_air_date}
						<span>{show.first_air_date.substring(0, 4)}</span>
					{/if}
					{#if show.number_of_seasons}
						<span>{show.number_of_seasons} saison{show.number_of_seasons > 1 ? 's' : ''}</span>
					{/if}
					{#if show.number_of_episodes}
						<span>{show.number_of_episodes} épisodes</span>
					{/if}
					{#if show.vote_average}
						<span class="rating">★ {show.vote_average.toFixed(1)}</span>
					{/if}
					{#if omdbData?.imdbRating}
						<span class="rating imdb">IMDb {omdbData.imdbRating}</span>
					{/if}
					{#if omdbData?.Ratings}
						{#each omdbData.Ratings.filter(r => r.Source === 'Rotten Tomatoes') as rt}
							<span class="rating rt">🍅 {rt.Value}</span>
						{/each}
					{/if}
					{#if show.status}
						<span class="status-pill">{show.status}</span>
					{/if}
				</div>

				{#if omdbData?.Awards && omdbData.Awards !== 'N/A'}
					<p class="awards">🏆 {omdbData.Awards}</p>
				{/if}

				<div class="genres">
					{#each show.genres as genre (genre.id)}
						<span class="genre-pill">{genre.name}</span>
					{/each}
				</div>

				<div class="action-buttons">
					<button class="btn-primary" onclick={() => handlePlay(selectedSeason, 1)}>
						<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M8 5v14l11-7z"/></svg>
						Regarder S{selectedSeason}E1
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
						title={libraryStatus?.in_watchlist ? "Retirer de la liste d'envies" : "Ajouter à la liste d'envies"}
					>
						<svg viewBox="0 0 24 24" fill={libraryStatus?.in_watchlist ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" width="18" height="18">
							<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
						</svg>
						{libraryStatus?.in_watchlist ? 'Dans ma liste' : "Liste d'envies"}
					</button>
				</div>
			</div>
		</div>
	</div>

	<!-- Torrent Search Results -->
	{#if showDownloadResults && show}
		<section class="detail-section">
			<h2 class="section-title">Résultats de téléchargement</h2>
			{#if searchingDownloads}
				<p>Recherche de torrents pour "{show.name}" en cours...</p>
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
							<button class="btn-primary btn-sm" onclick={() => startTorrentDownload(result.id, show!.id.toString())}>
								Télécharger ce torrent
							</button>
						</div>
					{/each}
				</div>
			{:else}
				<p>Aucun résultat de torrent trouvé pour "{show.name}".</p>
			{/if}
		</section>
	{/if}

	<!-- Body -->
	<div class="detail-body">

		<!-- Synopsis -->
		{#if show.overview}
			<section class="detail-section">
				<h2 class="section-title">Synopsis</h2>
				<p class="overview">{show.overview}</p>
			</section>
		{/if}

		<!-- Infos enrichies (OMDb) -->
		{#if omdbData && omdbData.Title}
			<section class="detail-section">
				<h2 class="section-title">Informations complémentaires</h2>
				<div class="enrichment-grid">
					{#if omdbData.Director && omdbData.Director !== 'N/A'}
						<div class="info-item"><span class="info-label">Créateur</span><span class="info-value">{omdbData.Director}</span></div>
					{/if}
					{#if omdbData.Writer && omdbData.Writer !== 'N/A'}
						<div class="info-item"><span class="info-label">Scénariste</span><span class="info-value">{omdbData.Writer}</span></div>
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

		<!-- Season/Episode selector -->
		{#if show.seasons && show.seasons.length > 0}
			<section class="detail-section">
				<div class="season-header">
					<h2 class="section-title">Épisodes</h2>
					<select class="season-select" bind:value={selectedSeason}>
						{#each show.seasons.filter(s => s.season_number > 0) as season (season.id)}
							<option value={season.season_number}>
								Saison {season.season_number}
								{#if season.episode_count}({season.episode_count} épisodes){/if}
							</option>
						{/each}
					</select>
				</div>

				{#if loadingSeason}
					<div class="episodes-grid">
						{#each Array(6) as _, i (i)}
							<Skeleton height="120px" borderRadius="10px" />
						{/each}
					</div>
				{:else if seasonDetail?.episodes}
					<div class="episodes-grid">
						{#each seasonDetail.episodes as ep (ep.id)}
							<button class="episode-card" onclick={() => handlePlay(selectedSeason, ep.episode_number)}>
								<div class="ep-thumb">
									{#if ep.still_path}
										<img src={tmdbImageUrl(ep.still_path, 'w300')} alt={ep.name} loading="lazy" />
									{:else}
										<div class="ep-no-thumb">
											<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M8 5v14l11-7z"/></svg>
										</div>
									{/if}
									<div class="ep-play-overlay">
										<svg viewBox="0 0 24 24" fill="white" width="32" height="32"><path d="M8 5v14l11-7z"/></svg>
									</div>
								</div>
								<div class="ep-info">
									<p class="ep-number">S{selectedSeason}E{ep.episode_number}</p>
									<p class="ep-name">{ep.name}</p>
									{#if ep.vote_average && ep.vote_average > 0}
										<p class="ep-rating">★ {ep.vote_average.toFixed(1)}</p>
									{/if}
								</div>
							</button>
						{/each}
					</div>
				{/if}
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
			<MediaRow title="Séries similaires" items={similar} />
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
		background: rgba(0, 0, 0, 0.4);
		backdrop-filter: blur(8px);
		color: rgba(255, 255, 255, 0.9);
		border-radius: 50px;
		font-size: 13px;
		font-weight: 500;
		text-decoration: none;
		transition: all var(--transition-fast);
	}

	.back-btn:hover {
		background: rgba(0, 0, 0, 0.6);
		color: #fff;
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
		flex-wrap: wrap;
		gap: 12px;
		color: var(--text-secondary);
		font-size: 14px;
		margin-bottom: 12px;
	}

	.rating { color: var(--warning); font-weight: 700; }

	.status-pill {
		background: rgba(0,200,83,0.15);
		border: 1px solid rgba(0,200,83,0.3);
		color: #00c853;
		font-size: 11px;
		padding: 2px 8px;
		border-radius: 20px;
	}

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

	/* ── Season selector ── */
	.season-header {
		display: flex;
		align-items: center;
		gap: 16px;
		margin-bottom: 16px;
	}

	.season-header .section-title { margin-bottom: 0; }

	.season-select {
		background: var(--bg-secondary);
		border: 1px solid var(--border);
		color: var(--text-primary);
		padding: 6px 12px;
		border-radius: var(--radius-sm);
		font-size: 13px;
		cursor: pointer;
	}

	/* ── Episodes ── */
	.episodes-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
		gap: 12px;
	}

	.episode-card {
		display: flex;
		flex-direction: column;
		background: var(--bg-card);
		border-radius: var(--radius-sm);
		overflow: hidden;
		cursor: pointer;
		border: 1px solid var(--border);
		transition: all var(--transition-fast);
		text-align: left;
	}

	.episode-card:hover { border-color: var(--accent); transform: translateY(-2px); }

	.ep-thumb {
		position: relative;
		aspect-ratio: 16 / 9;
		overflow: hidden;
		background: var(--bg-secondary);
	}

	.ep-thumb img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform var(--transition-fast);
	}

	.episode-card:hover .ep-thumb img { transform: scale(1.05); }

	.ep-no-thumb {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
	}

	.ep-play-overlay {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		background: rgba(0,0,0,0.4);
		opacity: 0;
		transition: opacity var(--transition-fast);
	}

	.episode-card:hover .ep-play-overlay { opacity: 1; }

	.ep-info { padding: 10px 12px; }

	.ep-number {
		font-size: 11px;
		color: var(--accent);
		font-weight: 600;
		margin-bottom: 3px;
	}

	.ep-name {
		font-size: 13px;
		font-weight: 600;
		color: var(--text-primary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		margin-bottom: 3px;
	}

	.ep-rating {
		font-size: 12px;
		color: var(--warning);
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

	.cast-photo img { width: 100%; height: 100%; object-fit: cover; }

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

	/* ── Enrichment ── */
	.rating.imdb { color: #f5c518; font-weight: 700; }
	.rating.rt { color: #fa320a; font-weight: 600; }
	.awards { color: #f5c518; font-size: 13px; margin-top: 6px; font-style: italic; }
	.enrichment-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
		gap: 12px;
	}
	.info-item { display: flex; flex-direction: column; gap: 2px; }
	.info-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); }
	.info-value { font-size: 14px; color: var(--text-primary); }
	.fanart-gallery { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
	.fanart-logo { max-height: 80px; width: auto; object-fit: contain; }
	.fanart-bg { width: 280px; height: 160px; object-fit: cover; border-radius: 8px; }
</style>
