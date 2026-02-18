<script lang="ts">
	import {
		tmdbTrending, tmdbDiscover, getContinueWatching, listWatchlist, isLoggedIn, tmdbImageUrl
	} from '$lib/api/client';
	import type { TmdbSearchItem, WatchHistoryEntry, WatchlistEntry } from '$lib/api/client';

	import MediaRow from '$lib/components/MediaRow.svelte';
	import HeroCarousel from '$lib/components/HeroCarousel.svelte';
	import BrandTiles from '$lib/components/BrandTiles.svelte';
	import { goto } from '$app/navigation';

	// ── State ──
	let trendingAll: TmdbSearchItem[] = $state([]);
	let trendingMovies: TmdbSearchItem[] = $state([]);
	let trendingSeries: TmdbSearchItem[] = $state([]);
	let netflixMovies: TmdbSearchItem[] = $state([]);
	let disneyMovies: TmdbSearchItem[] = $state([]);
	let amazonMovies: TmdbSearchItem[] = $state([]);
	let continueWatching: WatchHistoryEntry[] = $state([]);
	let watchlistItems: WatchlistEntry[] = $state([]);

	let loadingTrending = $state(true);
	let loadingPlatforms = $state(true);

	// ── Derived ──
	const top10 = $derived(trendingAll.slice(0, 10));

	function getPlayableMediaType(item: TmdbSearchItem): 'movie' | 'tv' | null {
		if (item.media_type === 'movie' || item.media_type === 'tv') return item.media_type;
		if (item.first_air_date && !item.release_date) return 'tv';
		if (item.release_date || item.title) return 'movie';
		return null;
	}

	function getWatchPath(item: TmdbSearchItem): string | null {
		const mediaType = getPlayableMediaType(item);
		if (mediaType === 'tv') return `/watch/tv/${item.id}?season=1&episode=1`;
		if (mediaType === 'movie') return `/watch/movie/${item.id}`;
		return null;
	}

	function getDetailPath(item: TmdbSearchItem): string {
		const mediaType = item.media_type || 'movie';
		return `/${mediaType}/${item.id}`;
	}

	// ── Load data ──
	$effect(() => {
		loadTrending();
		loadPlatforms();
		loadHistory();
		loadWatchlist();
	});

	async function loadTrending() {
		loadingTrending = true;
		const [all, movies, series] = await Promise.allSettled([
			tmdbTrending('all', 'day'),
			tmdbTrending('movie', 'week'),
			tmdbTrending('tv', 'week'),
		]);
		if (all.status === 'fulfilled') trendingAll = all.value.filter((item) => getPlayableMediaType(item) !== null);
		if (movies.status === 'fulfilled') trendingMovies = movies.value.filter((item) => getPlayableMediaType(item) !== null);
		if (series.status === 'fulfilled') trendingSeries = series.value.filter((item) => getPlayableMediaType(item) !== null);
		loadingTrending = false;
	}

	async function loadPlatforms() {
		loadingPlatforms = true;
		const [netflix, disney, amazon] = await Promise.allSettled([
			tmdbDiscover('movie', { with_watch_providers: '8', watch_region: 'FR', sort_by: 'popularity.desc' }),
			tmdbDiscover('movie', { with_watch_providers: '337', watch_region: 'FR', sort_by: 'popularity.desc' }),
			tmdbDiscover('movie', { with_watch_providers: '119', watch_region: 'FR', sort_by: 'popularity.desc' }),
		]);
		if (netflix.status === 'fulfilled') netflixMovies = netflix.value.results.filter((item) => getPlayableMediaType(item) !== null);
		if (disney.status === 'fulfilled') disneyMovies = disney.value.results.filter((item) => getPlayableMediaType(item) !== null);
		if (amazon.status === 'fulfilled') amazonMovies = amazon.value.results.filter((item) => getPlayableMediaType(item) !== null);
		loadingPlatforms = false;
	}

	async function loadHistory() {
		try {
			continueWatching = await getContinueWatching(20);
		} catch { /* ignore */ }
	}

	async function loadWatchlist() {
		if (!isLoggedIn()) return;
		try {
			const res = await listWatchlist(1, 20);
			watchlistItems = res.items;
		} catch { /* ignore */ }
	}

	async function handleHeroPlay(item: TmdbSearchItem) {
		const watchPath = getWatchPath(item);
		goto(watchPath ?? getDetailPath(item));
	}

	function handleHeroDetail(item: TmdbSearchItem) {
		goto(getDetailPath(item));
	}

	function getHistoryPath(entry: WatchHistoryEntry): string {
		if (entry.media_type_wh === 'tv' && entry.tmdb_id) {
			return `/watch/tv/${entry.tmdb_id}?season=1&episode=1`;
		}
		if (entry.media_type_wh === 'movie' && entry.tmdb_id) {
			return `/watch/movie/${entry.tmdb_id}`;
		}
		return `/${entry.media_type_wh ?? 'movie'}/${entry.tmdb_id ?? ''}`;
	}

	function progressPercent(entry: WatchHistoryEntry): number {
		return Math.min(Math.round((entry.progress ?? 0) * 100), 100);
	}
</script>

<svelte:head>
	<title>SOKOUL — Accueil</title>
</svelte:head>

<!-- 1. Hero Carousel (Full Width) -->
<HeroCarousel
	items={trendingAll.slice(0, 5)}
	onPlay={handleHeroPlay}
	onDetails={handleHeroDetail}
	loading={loadingTrending}
/>

<!-- Catalog (Contained) -->
<div class="catalog">

	<!-- 3. Reprendre la lecture -->
	{#if continueWatching.length > 0}
		<section class="continue-section">
			<h2 class="section-title">Reprendre la lecture</h2>
			<div class="continue-wrapper">
				{#if continueWatching.length > 4}
					<button class="scroll-btn scroll-left" onclick={() => { document.querySelector('.continue-row')?.scrollBy({ left: -320, behavior: 'smooth' }); }} aria-label="Défiler à gauche">
						<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
					</button>
				{/if}
				<div class="continue-row">
					{#each continueWatching as entry (entry.id)}
						{#if entry.tmdb_id && entry.media_type_wh}
							{@const pct = progressPercent(entry)}
							<a
								href={getHistoryPath(entry)}
								class="continue-card"
								aria-label={entry.title ?? 'Reprendre'}
							>
								<div class="continue-poster">
									{#if entry.poster_url}
										<img src={entry.poster_url} alt={entry.title ?? ''} loading="lazy" />
									{:else}
										<div class="no-poster-sm"></div>
									{/if}
									<div class="continue-overlay">
										<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28"><path d="M8 5v14l11-7z"/></svg>
									</div>
									<div class="continue-progress">
										<div class="continue-bar" style="width:{pct}%"></div>
									</div>
								</div>
								<p class="continue-title">{entry.title ?? '—'}</p>
								<p class="continue-pct">{pct}%</p>
							</a>
						{/if}
					{/each}
				</div>
				{#if continueWatching.length > 4}
					<button class="scroll-btn scroll-right" onclick={() => { document.querySelector('.continue-row')?.scrollBy({ left: 320, behavior: 'smooth' }); }} aria-label="Défiler à droite">
						<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z"/></svg>
					</button>
				{/if}
			</div>
		</section>
	{/if}

	<!-- 4. Ma Liste -->
	{#if watchlistItems.length > 0}
		<section class="watchlist-section">
			<h2 class="section-title">
				Ma liste
				<a href="/library" class="see-more-link">Voir tout</a>
			</h2>
			<div class="continue-row">
				{#each watchlistItems as item (item.id)}
					{#if item.tmdb_id && item.media_type_wl}
						<a
							href="/{item.media_type_wl}/{item.tmdb_id}"
							class="continue-card"
							aria-label={item.title ?? 'Voir'}
						>
							<div class="continue-poster">
								{#if item.poster_url}
									<img src={item.poster_url} alt={item.title ?? ''} loading="lazy" />
								{:else}
									<div class="no-poster-sm"></div>
								{/if}
								<div class="continue-overlay">
									<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z"/></svg>
								</div>
							</div>
							<p class="continue-title">{item.title ?? '—'}</p>
						</a>
					{/if}
				{/each}
			</div>
		</section>
	{/if}

	<!-- 5. Top 10 en France -->
	{#if !loadingTrending && top10.length >= 5}
		<section class="top10-section">
			<h2 class="section-title">Top 10 en France aujourd'hui</h2>
			<div class="top10-row">
				{#each top10 as item, i (item.id)}
					<a href={getDetailPath(item)} class="top10-card" aria-label={item.title ?? item.name ?? ''}>
						<span class="top10-number">{i + 1}</span>
						<div class="top10-poster">
							{#if item.poster_path}
								<img
									src={tmdbImageUrl(item.poster_path, 'w300')}
									alt={item.title ?? item.name ?? ''}
									loading="lazy"
								/>
							{:else}
								<div class="no-poster-sm"></div>
							{/if}
							<div class="top10-play-overlay">
								<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M8 5v14l11-7z"/></svg>
							</div>
						</div>
					</a>
				{/each}
			</div>
		</section>
	{/if}

	<!-- 6. Tendances du jour -->
	<MediaRow title="Tendances du jour" items={trendingAll} loading={loadingTrending} />

	<!-- 7. Films populaires -->
	<MediaRow title="Films populaires" items={trendingMovies} loading={loadingTrending} />

	<!-- 8. Séries populaires -->
	<MediaRow title="Séries populaires" items={trendingSeries} loading={loadingTrending} />

	<!-- 9 & 10. Plateformes -->
	<MediaRow title="Populaires sur Netflix"       items={netflixMovies} loading={loadingPlatforms} seeMoreHref="/films?provider=8&provider_name=Netflix" />
	<MediaRow title="Populaires sur Disney+"       items={disneyMovies}  loading={loadingPlatforms} seeMoreHref="/films?provider=337&provider_name=Disney%2B" />
	<MediaRow title="Populaires sur Amazon Prime"  items={amazonMovies}  loading={loadingPlatforms} seeMoreHref="/films?provider=119&provider_name=Amazon%20Prime" />

</div>

<!-- 11. Brand Tiles -->
<BrandTiles />

<style>
	/* ── Catalog ── */
	.catalog { padding: 0 calc(3.5vw + 5px) 20px; }

	.section-title {
		font-size: 18px;
		font-weight: 700;
		color: var(--text-primary);
		margin-bottom: 12px;
		padding: 0;
		display: flex;
		align-items: center;
		gap: 16px;
	}

	.see-more-link {
		font-size: 13px;
		font-weight: 500;
		color: var(--text-muted);
		text-decoration: none;
		transition: color 200ms;
	}

	.see-more-link:hover { color: var(--text-primary); }

	/* ── Reprendre la lecture ── */
	.continue-section { margin-bottom: 2rem; position: relative; }
	.watchlist-section { margin-bottom: 2rem; }

	.continue-wrapper { position: relative; }

	.continue-row {
		display: flex;
		gap: 12px;
		overflow-x: auto;
		scroll-snap-type: x mandatory;
		scrollbar-width: none;
		-ms-overflow-style: none;
		padding: 10px 0 16px;
		scroll-behavior: smooth;
	}

	.continue-row::-webkit-scrollbar { display: none; }

	.continue-card {
		flex-shrink: 0;
		width: 160px;
		text-decoration: none;
		color: inherit;
		scroll-snap-align: start;
	}

	.continue-poster {
		position: relative;
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
		overflow: hidden;
		background: var(--bg-card);
	}

	.continue-poster img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform var(--transition-smooth);
	}

	.continue-card:hover .continue-poster img { transform: scale(1.05); }

	.continue-overlay {
		position: absolute;
		inset: 0;
		background: rgba(0,0,0,0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		opacity: 0;
		pointer-events: none;
		transition: opacity var(--transition-smooth);
	}

	.continue-card:hover .continue-overlay { opacity: 1; }

	.continue-progress {
		position: absolute;
		bottom: 0;
		left: 0;
		right: 0;
		height: 3px;
		background: rgba(255,255,255,0.2);
	}

	.continue-bar { height: 100%; background: var(--accent); }

	.no-poster-sm { width: 100%; height: 100%; background: var(--bg-secondary); }

	.scroll-btn {
		position: absolute;
		top: 50%;
		transform: translateY(-50%);
		z-index: 10;
		width: 44px;
		height: 44px;
		border-radius: 50%;
		border: none;
		background: rgba(26, 29, 41, 0.85);
		color: #F9F9F9;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		opacity: 0;
		transition: opacity 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		backdrop-filter: blur(4px);
	}

	.continue-section:hover .scroll-btn { opacity: 1; }
	.scroll-btn:hover { background: rgba(249, 249, 249, 0.2); }
	.scroll-left { left: -16px; }
	.scroll-right { right: -16px; }

	.continue-title {
		font-size: 12px;
		color: var(--text-primary);
		margin-top: 8px;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		padding: 0 2px;
	}

	.continue-pct { font-size: 11px; color: var(--text-muted); padding: 0 2px; }

	/* ── Top 10 ── */
	.top10-section { margin-bottom: 2rem; }

	.top10-row {
		display: flex;
		gap: 0;
		overflow-x: auto;
		scrollbar-width: none;
		padding: 10px 0 16px;
	}

	.top10-row::-webkit-scrollbar { display: none; }

	.top10-card {
		position: relative;
		flex-shrink: 0;
		display: flex;
		align-items: flex-end;
		text-decoration: none;
		/* number overlaps left, poster offset right */
		padding-left: 36px;
	}

	.top10-number {
		position: absolute;
		left: 0;
		bottom: -6px;
		font-size: 96px;
		font-weight: 900;
		line-height: 1;
		color: transparent;
		-webkit-text-stroke: 3px rgba(249, 249, 249, 0.55);
		text-stroke: 3px rgba(249, 249, 249, 0.55);
		letter-spacing: -4px;
		user-select: none;
		z-index: 1;
	}

	.top10-poster {
		position: relative;
		width: 130px;
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
		overflow: hidden;
		background: var(--bg-card);
		z-index: 2;
		transition: transform 200ms;
	}

	.top10-card:hover .top10-poster { transform: scale(1.04); }

	.top10-poster img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.top10-play-overlay {
		position: absolute;
		inset: 0;
		background: rgba(0,0,0,0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		opacity: 0;
		transition: opacity 200ms;
	}

	.top10-card:hover .top10-play-overlay { opacity: 1; }

	@media (max-width: 900px) {
		.scroll-btn { display: none; }
		.top10-number { font-size: 72px; }
		.top10-poster { width: 100px; }
		.top10-card { padding-left: 28px; }
	}
</style>
