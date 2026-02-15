<script lang="ts">
	import {
		tmdbTrending, tmdbDiscover, getContinueWatching,
		tmdbImageUrl, getItemTitle, getItemYear,
		getDirectStreamLinks
	} from '$lib/api/client';
	import type { TmdbSearchItem, WatchHistoryEntry, StreamLinks } from '$lib/api/client';
	import MediaRow from '$lib/components/MediaRow.svelte';
	import VideoPlayer from '$lib/components/VideoPlayer.svelte';
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

	// Hero
	const heroItem = $derived(trendingAll[0] ?? null);
	const heroBackdrop = $derived(heroItem ? tmdbImageUrl(heroItem.backdrop_path, 'original') : null);
	const heroTitle = $derived(heroItem ? getItemTitle(heroItem) : '');
	const heroYear = $derived(heroItem ? getItemYear(heroItem) : '');
	const heroOverview = $derived(heroItem?.overview ?? '');
	const heroRating = $derived(heroItem?.vote_average ?? null);

	// Player
	let showPlayer = $state(false);
	let playerLinks: StreamLinks | null = $state(null);
	let playerTitle = $state('');
	let loadingPlayer = $state(false);

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
		if (all.status === 'fulfilled') trendingAll = all.value;
		if (movies.status === 'fulfilled') trendingMovies = movies.value;
		if (series.status === 'fulfilled') trendingSeries = series.value;
		loadingTrending = false;
	}

	async function loadPlatforms() {
		loadingPlatforms = true;
		const [netflix, disney, amazon] = await Promise.allSettled([
			tmdbDiscover('movie', { with_watch_providers: '8', watch_region: 'FR', sort_by: 'popularity.desc' }),
			tmdbDiscover('movie', { with_watch_providers: '337', watch_region: 'FR', sort_by: 'popularity.desc' }),
			tmdbDiscover('movie', { with_watch_providers: '119', watch_region: 'FR', sort_by: 'popularity.desc' }),
		]);
		if (netflix.status === 'fulfilled') netflixMovies = netflix.value.results;
		if (disney.status === 'fulfilled') disneyMovies = disney.value.results;
		if (amazon.status === 'fulfilled') amazonMovies = amazon.value.results;
		loadingPlatforms = false;
	}

	async function loadHistory() {
		try {
			continueWatching = await getContinueWatching(20);
		} catch { /* ignore */ }
	}

	async function handleHeroPlay() {
		if (!heroItem) return;
		loadingPlayer = true;
		try {
			const type = heroItem.media_type || 'movie';
			playerLinks = await getDirectStreamLinks(type, heroItem.id);
			playerTitle = heroTitle;
			showPlayer = true;
		} catch (e) {
			console.error(e);
		}
		loadingPlayer = false;
	}

	function handleHeroDetail() {
		if (!heroItem) return;
		goto(`/${heroItem.media_type || 'movie'}/${heroItem.id}`);
	}

	function progressPercent(entry: WatchHistoryEntry): number {
		return Math.min(Math.round((entry.progress ?? 0) * 100), 100);
	}
</script>

<svelte:head>
	<title>SOKOUL — Accueil</title>
</svelte:head>

<!-- Hero Banner -->
{#if heroItem}
	<div class="hero" style={heroBackdrop ? `--backdrop:url(${heroBackdrop})` : ''}>
		<div class="hero-bg" class:has-backdrop={!!heroBackdrop}></div>
		<div class="hero-content">
			<div class="hero-meta">
				<span class="hero-type">{heroItem.media_type === 'tv' ? 'SÉRIE' : 'FILM'}</span>
				{#if heroRating}
					<span class="hero-rating">★ {heroRating.toFixed(1)}</span>
				{/if}
				{#if heroYear}<span class="hero-year">{heroYear}</span>{/if}
			</div>
			<h1 class="hero-title">{heroTitle}</h1>
			{#if heroOverview}
				<p class="hero-overview">{heroOverview}</p>
			{/if}
			<div class="hero-actions">
				<button class="btn-play" onclick={handleHeroPlay} disabled={loadingPlayer}>
					{#if loadingPlayer}
						<span class="spinner-sm"></span>
					{:else}
						<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M8 5v14l11-7z"/></svg>
					{/if}
					Regarder
				</button>
				<button class="btn-info" onclick={handleHeroDetail}>
					<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>
					Plus d'infos
				</button>
			</div>
		</div>
	</div>
{:else if loadingTrending}
	<div class="hero-skeleton"></div>
{/if}

<!-- Catalog -->
<div class="catalog">

	{#if continueWatching.length > 0}
		<section class="continue-section">
			<h2 class="section-title">Reprendre la lecture</h2>
			<div class="continue-row">
				{#each continueWatching as entry (entry.id)}
					{#if entry.tmdb_id && entry.media_type_wh}
						{@const pct = progressPercent(entry)}
						<a href="/{entry.media_type_wh}/{entry.tmdb_id}" class="continue-card" aria-label={entry.title ?? 'Reprendre'}>
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
		</section>
	{/if}

	<MediaRow title="Tendances du jour" items={trendingAll} loading={loadingTrending} />
	<MediaRow title="Films populaires" items={trendingMovies} loading={loadingTrending} />
	<MediaRow title="Séries populaires" items={trendingSeries} loading={loadingTrending} />
	<MediaRow title="Nouveautés Netflix" items={netflixMovies} loading={loadingPlatforms} />
	<MediaRow title="Nouveautés Disney+" items={disneyMovies} loading={loadingPlatforms} />
	<MediaRow title="Nouveautés Amazon Prime" items={amazonMovies} loading={loadingPlatforms} />

</div>

{#if showPlayer && playerLinks}
	<VideoPlayer
		sources={playerLinks.sources}
		title={playerTitle}
		onClose={() => { showPlayer = false; playerLinks = null; }}
	/>
{/if}

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

	.catalog { padding: 32px 0 0; }

	.section-title {
		font-size: 18px;
		font-weight: 700;
		color: var(--text-primary);
		margin-bottom: 12px;
		padding: 0 4px;
	}

	.continue-section { margin-bottom: 2rem; }

	.continue-row {
		display: flex;
		gap: 12px;
		overflow-x: auto;
		padding: 4px 4px 12px;
		scrollbar-width: thin;
	}

	.continue-card {
		flex-shrink: 0;
		width: 160px;
		text-decoration: none;
		color: inherit;
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

	@keyframes spin { to { transform: rotate(360deg); } }
	@keyframes shimmer {
		0% { background-position: 200% 0; }
		100% { background-position: -200% 0; }
	}
</style>
