<script lang="ts">
	import {
		tmdbTrending, tmdbDiscover, getContinueWatching
	} from '$lib/api/client';
	import type { TmdbSearchItem, WatchHistoryEntry } from '$lib/api/client';
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

	let loadingTrending = $state(true);
	let loadingPlatforms = $state(true);

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

<!-- Hero Carousel (Full Width) -->
<HeroCarousel
	items={trendingAll.slice(0, 5)}
	onPlay={handleHeroPlay}
	onDetails={handleHeroDetail}
	loading={loadingTrending}
/>

<!-- Brand Tiles (Disney+ style) -->
<BrandTiles />

<!-- Catalog (Contained) -->
<div class="catalog">

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

	<MediaRow title="Tendances du jour" items={trendingAll} loading={loadingTrending} />
	<MediaRow title="Films populaires" items={trendingMovies} loading={loadingTrending} />
	<MediaRow title="Séries populaires" items={trendingSeries} loading={loadingTrending} />
	<MediaRow title="Nouveautés Netflix" items={netflixMovies} loading={loadingPlatforms} />
	<MediaRow title="Nouveautés Disney+" items={disneyMovies} loading={loadingPlatforms} />
	<MediaRow title="Nouveautés Amazon Prime" items={amazonMovies} loading={loadingPlatforms} />

</div>

<style>
	.hero {
		position: relative;
		min-height: 520px;
		display: flex;
		align-items: flex-end;
		margin: -24px -24px 0;
		overflow: hidden;
	}

	.hero-bg {
		position: absolute;
		inset: 0;
		background: linear-gradient(135deg, rgba(108,92,231,0.3) 0%, rgba(10,10,15,0.6) 50%, var(--bg-primary) 100%);
	}

	.hero-bg.has-backdrop {
		background-image: var(--backdrop);
		background-size: cover;
		background-position: center 30%;
	}

	.hero-bg.has-backdrop::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(to bottom, rgba(10,10,15,0.2) 0%, rgba(10,10,15,0.7) 60%, var(--bg-primary) 100%);
	}

	.hero-content {
		position: relative;
		padding: 40px 32px 48px;
		max-width: 680px;
		z-index: 1;
	}

	.hero-meta {
		display: flex;
		align-items: center;
		gap: 10px;
		margin-bottom: 12px;
	}

	.hero-type {
		background: var(--accent);
		color: #fff;
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 1px;
		padding: 3px 10px;
		border-radius: 4px;
	}

	.hero-rating { color: var(--warning); font-weight: 700; font-size: 15px; }
	.hero-year { color: var(--text-secondary); font-size: 14px; }

	.hero-title {
		font-size: clamp(28px, 4vw, 48px);
		font-weight: 800;
		color: #fff;
		line-height: 1.1;
		text-shadow: 0 2px 12px rgba(0,0,0,0.5);
		margin-bottom: 12px;
	}

	.hero-overview {
		color: rgba(255,255,255,0.8);
		font-size: 14px;
		line-height: 1.6;
		max-width: 560px;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
		margin-bottom: 24px;
	}

	.hero-actions {
		display: flex;
		gap: 12px;
		flex-wrap: wrap;
	}

	.btn-play {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 12px 28px;
		background: #fff;
		color: #000;
		border: none;
		border-radius: 8px;
		font-size: 15px;
		font-weight: 700;
		cursor: pointer;
		transition: all var(--transition-fast);
	}

	.btn-play:hover { background: rgba(255,255,255,0.85); transform: scale(1.03); }
	.btn-play:disabled { opacity: 0.6; cursor: not-allowed; }

	.btn-info {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 12px 24px;
		background: rgba(255,255,255,0.15);
		backdrop-filter: blur(8px);
		color: #fff;
		border: 1px solid rgba(255,255,255,0.3);
		border-radius: 8px;
		font-size: 15px;
		font-weight: 600;
		cursor: pointer;
		transition: all var(--transition-fast);
	}

	.btn-info:hover { background: rgba(255,255,255,0.25); }

	.hero-skeleton {
		min-height: 520px;
		margin: -24px -24px 0;
		background: linear-gradient(90deg, var(--bg-card) 25%, var(--bg-hover) 50%, var(--bg-card) 75%);
		background-size: 200% 100%;
		animation: shimmer 1.5s infinite;
	}

	.catalog { padding: 20px calc(3.5vw + 5px) 0; }

	.section-title {
		font-size: 18px;
		font-weight: 700;
		color: var(--text-primary);
		margin-bottom: 12px;
		padding: 0;
	}

	.continue-section { margin-bottom: 2rem; position: relative; }

	.continue-wrapper {
		position: relative;
	}

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

	.continue-row::-webkit-scrollbar {
		display: none;
	}

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

	.no-poster-sm {
		width: 100%;
		height: 100%;
		background: var(--bg-secondary);
	}

	/* Scroll buttons identiques aux autres carousels */
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

	.continue-section:hover .scroll-btn {
		opacity: 1;
	}

	.scroll-btn:hover {
		background: rgba(249, 249, 249, 0.2);
	}

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

	.spinner-sm {
		width: 16px;
		height: 16px;
		border: 2px solid rgba(0,0,0,0.3);
		border-top-color: #000;
		border-radius: 50%;
		animation: spin 0.7s linear infinite;
	}

	@media (max-width: 900px) {
		.scroll-btn {
			display: none;
		}
	}

	@keyframes spin { to { transform: rotate(360deg); } }
	@keyframes shimmer {
		0% { background-position: 200% 0; }
		100% { background-position: -200% 0; }
	}
</style>
