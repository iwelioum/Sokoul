<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { page } from '$app/stores';
	import {
		createMedia,
		directSearch,
		streamSearch,
		startDownload,
		isLoggedIn,
		formatBytes,
		tmdbImageUrl,
		tmdbMovieDetails,
		tmdbTvDetails
	} from '$lib/api/client';
	import type { SearchResult, TmdbMovieDetail, TmdbTvDetail } from '$lib/api/client';
	import { downloads, initDownloadsStore } from '$lib/stores/downloadsStore';

	// ── URL & State ──
	const query = $derived($page.url.searchParams.get('query') || '');
	const tmdbId = $derived(Number($page.url.searchParams.get('tmdbId')) || 0);
	const mediaType = $derived($page.url.searchParams.get('mediaType') === 'tv' ? 'tv' : 'movie');

	let results: SearchResult[] = $state([]);
	let loading = $state(false);
	let searchingProviders = $state<string[]>([]);
	let completedProviders = $state<string[]>([]);
	let error = $state('');
	let mediaId = $state<string | null>(null);
	let startingDownloadId = $state<number | null>(null);
	let downloadedIds = $state<Set<number>>(new Set());
	let sidebarOpen = $state(false);
	let sidebarPulse = $state(false);
	let selectedFilter = $state<string>('all');
	let abortStream: (() => void) | null = null;

	let mediaDetails: TmdbMovieDetail | TmdbTvDetail | null = $state(null);

	// ── Init ──
	onMount(() => {
		if (isLoggedIn()) {
			initDownloadsStore();
			if (query && tmdbId) {
				runSearch(false);
				loadDetails();
			}
		} else {
			error = "Vous devez être connecté pour télécharger.";
		}
		return () => { abortStream?.(); };
	});

	// ── Functions ──
	async function runSearch(force = false) {
		loading = true;
		error = '';
		results = [];
		searchingProviders = [];
		completedProviders = [];
		abortStream?.();
		try {
			const media = await createMedia({ title: query, media_type: mediaType, tmdb_id: tmdbId });
			mediaId = media.id;

			// Try cache first (fast path) unless forcing refresh
			if (!force) {
				try {
					const cached = await directSearch(query, mediaId, false);
					if (cached.results && cached.results.length > 0) {
						results = cached.results;
						loading = false;
						return;
					}
				} catch { /* cache miss, continue to SSE */ }
			}

			// Use SSE streaming for live results per provider
			abortStream = streamSearch(query, mediaId, {
				onStart: (data) => {
					searchingProviders = Array.from({ length: data.providers }, (_, i) => `Provider ${i + 1}`);
				},
				onResults: (data) => {
					completedProviders = [...completedProviders, data.provider];
					results = [...results, ...data.results];
				},
				onProviderError: (data) => {
					completedProviders = [...completedProviders, data.provider];
					console.warn(`Provider ${data.provider} error: ${data.error}`);
				},
				onDone: () => {
					loading = false;
					searchingProviders = [];
					if (results.length === 0) error = "Aucun résultat trouvé.";
				},
				onError: () => {
					loading = false;
					error = "Erreur lors de la recherche.";
				},
			});
		} catch (e: any) {
			error = "Erreur lors de la recherche.";
			loading = false;
		}
	}

	async function loadDetails() {
		try {
			if (mediaType === 'movie') {
				mediaDetails = await tmdbMovieDetails(tmdbId);
			} else {
				mediaDetails = await tmdbTvDetails(tmdbId);
			}
		} catch {
			console.error("Could not load media details for header.");
		}
	}

	async function download(result: SearchResult) {
		if (!mediaId || startingDownloadId) return;
		startingDownloadId = result.id;
		try {
			await startDownload({ media_id: mediaId, search_result_id: result.id });
			downloadedIds = new Set([...downloadedIds, result.id]);
			initDownloadsStore();
			// Open sidebar + pulse
			sidebarOpen = true;
			sidebarPulse = true;
			await tick();
			setTimeout(() => { sidebarPulse = false; }, 1200);
		} catch (e: any) {
			alert("Erreur: " + e.message);
		} finally {
			startingDownloadId = null;
		}
	}

	function parseTags(title: string): { quality?: string, lang?: string, format?: string } {
		const t = title.toUpperCase();
		const tags: { quality?: string, lang?: string, format?: string } = {};

		if (t.includes('2160P') || t.includes('4K')) tags.quality = '4K';
		else if (t.includes('1080P')) tags.quality = '1080p';
		else if (t.includes('720P')) tags.quality = '720p';
		else if (t.includes('DVDRIP')) tags.quality = 'DVDRip';

		if (t.includes('TRUEFRENCH')) tags.lang = 'VFF';
		else if (t.includes('FRENCH')) tags.lang = 'VF';
		else if (t.includes('VOSTFR') || t.includes('SUBFRENCH')) tags.lang = 'VOSTFR';
		else if (t.includes('MULTI')) tags.lang = 'MULTI';

		if (t.includes('BLURAY')) tags.format = 'Blu-Ray';
		else if (t.includes('WEB-DL') || t.includes('WEBRIP')) tags.format = 'WEB';
		else if (t.includes('XVID')) tags.format = 'XviD';

		return tags;
	}

	function seedScore(s: number): string {
		if (s >= 50) return 'excellent';
		if (s >= 15) return 'good';
		if (s >= 3) return 'fair';
		return 'poor';
	}

	const activeDownloads = $derived($downloads.filter(t => t.status === 'running' || t.status === 'pending'));
	const totalActive = $derived(activeDownloads.length);

	const filteredResults = $derived(
		selectedFilter === 'all'
			? results
			: results.filter(r => {
				const tags = parseTags(r.title);
				if (selectedFilter === '4K') return tags.quality === '4K';
				if (selectedFilter === '1080p') return tags.quality === '1080p';
				if (selectedFilter === '720p') return tags.quality === '720p';
				if (selectedFilter === 'VF') return tags.lang === 'VF' || tags.lang === 'VFF';
				if (selectedFilter === 'MULTI') return tags.lang === 'MULTI';
				if (selectedFilter === 'VOSTFR') return tags.lang === 'VOSTFR';
				return true;
			})
	);

	const availableFilters = $derived(() => {
		const filters = new Set<string>();
		for (const r of results) {
			const tags = parseTags(r.title);
			if (tags.quality) filters.add(tags.quality);
			if (tags.lang) filters.add(tags.lang === 'VFF' ? 'VF' : tags.lang);
		}
		return ['all', ...Array.from(filters)];
	});
</script>

<svelte:head>
	<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
	<title>Télécharger "{query}"</title>
</svelte:head>

<div class="dl-page">

	<!-- ═══ CUSTOM TOP BAR ═══ -->
	<nav class="dl-topbar">
		<button class="dl-topbar__back" onclick={() => history.back()} aria-label="Retour">
			<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
			<span>Retour</span>
		</button>

		<button class="dl-topbar__downloads" class:dl-topbar__downloads--active={sidebarOpen} onclick={() => sidebarOpen = !sidebarOpen} aria-label="Téléchargements">
			<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
			<span>Téléchargements</span>
			{#if totalActive > 0}
				<span class="dl-topbar__badge">{totalActive}</span>
			{/if}
		</button>
	</nav>

	<!-- ═══ HERO HEADER ═══ -->
	<header class="dl-hero" style:--backdrop-url="url({tmdbImageUrl(mediaDetails?.backdrop_path, 'original')})">
		<div class="dl-hero__gradient"></div>
		<div class="dl-hero__inner">
			{#if mediaDetails}
				<div class="dl-hero__media">
					<img
						class="dl-hero__poster"
						src={tmdbImageUrl(mediaDetails.poster_path)}
						alt="Affiche"
						loading="eager"
					/>
					<div class="dl-hero__info">
						<span class="dl-hero__type-badge">
							{mediaType === 'tv' ? 'Série' : 'Film'}
						</span>
						<h1 class="dl-hero__title">{'title' in mediaDetails ? mediaDetails.title : mediaDetails.name}</h1>
						<div class="dl-hero__meta">
							{#if 'release_date' in mediaDetails && mediaDetails.release_date}
								<span class="dl-hero__meta-chip">{mediaDetails.release_date.substring(0, 4)}</span>
							{/if}
							{#if 'first_air_date' in mediaDetails && mediaDetails.first_air_date}
								<span class="dl-hero__meta-chip">{mediaDetails.first_air_date.substring(0, 4)}</span>
							{/if}
							{#if mediaDetails.genres && mediaDetails.genres.length > 0}
								{#each mediaDetails.genres.slice(0, 2) as genre}
									<span class="dl-hero__meta-chip">{genre.name}</span>
								{/each}
							{/if}
							{#if 'runtime' in mediaDetails && mediaDetails.runtime}
								<span class="dl-hero__meta-chip"><i class="fa-regular fa-clock"></i> {mediaDetails.runtime} min</span>
							{/if}
							{#if mediaDetails.vote_average}
								<span class="dl-hero__meta-chip dl-hero__meta-chip--gold">
									<i class="fa-solid fa-star"></i> {mediaDetails.vote_average.toFixed(1)}
								</span>
							{/if}
						</div>
						{#if mediaDetails.overview}
							<p class="dl-hero__overview">{mediaDetails.overview.substring(0, 200)}{mediaDetails.overview.length > 200 ? '…' : ''}</p>
						{/if}
						<div class="dl-hero__result-count">
							{#if loading && results.length > 0}
								<span class="dl-hero__searching"><i class="fa-solid fa-spinner fa-spin"></i> Recherche en cours… <strong>{results.length}</strong> sources trouvées</span>
							{:else if loading}
								<span class="dl-hero__searching"><i class="fa-solid fa-spinner fa-spin"></i> Recherche en cours…</span>
							{:else if results.length > 0}
								<span><strong>{results.length}</strong> sources disponibles</span>
							{/if}
						</div>
					</div>
				</div>
			{:else if loading}
				<div class="dl-hero__media">
					<div class="dl-hero__poster skeleton" style="width:140px;height:210px;border-radius:12px;"></div>
					<div class="dl-hero__info">
						<div class="skeleton" style="width:200px;height:32px;border-radius:6px;margin-bottom:12px;"></div>
						<div class="skeleton" style="width:300px;height:20px;border-radius:4px;"></div>
					</div>
				</div>
			{/if}
		</div>
	</header>

	<!-- ═══ MAIN CONTENT ═══ -->
	<div class="dl-body">
		<section class="dl-sources">
			<!-- Filter chips -->
			{#if results.length > 0}
				<div class="dl-filters">
					{#each availableFilters() as f}
						<button
							class="dl-filter-chip"
							class:active={selectedFilter === f}
							onclick={() => selectedFilter = f}
						>
							{f === 'all' ? 'Tous' : f}
						</button>
					{/each}
				</div>
			{/if}

			<div class="dl-sources__list">
				{#if loading}
					{#each Array(6) as _, i}
						<div class="dl-card skeleton" style="animation-delay: {i * 0.08}s">
							<div style="height: 72px;"></div>
						</div>
					{/each}
				{:else if error}
					<div class="dl-empty">
						<div class="dl-empty__icon">
							<i class="fa-solid fa-triangle-exclamation"></i>
						</div>
						<p class="dl-empty__text">{error}</p>
						<button class="dl-empty__retry" onclick={() => runSearch(true)}>
							<i class="fa-solid fa-rotate-right"></i> Réessayer
						</button>
					</div>
				{:else if filteredResults.length === 0 && results.length > 0}
					<div class="dl-empty">
						<div class="dl-empty__icon">
							<i class="fa-solid fa-filter-circle-xmark"></i>
						</div>
						<p class="dl-empty__text">Aucun résultat pour ce filtre</p>
						<button class="dl-empty__retry" onclick={() => selectedFilter = 'all'}>
							Voir tous les résultats
						</button>
					</div>
				{:else}
					{#each filteredResults as result, idx (result.id)}
						{@const tags = parseTags(result.title)}
						{@const isDownloading = startingDownloadId === result.id}
						{@const isDownloaded = downloadedIds.has(result.id)}
						{@const health = seedScore(result.seeders)}
						<div
							class="dl-card"
							class:dl-card--downloaded={isDownloaded}
							style="animation-delay: {idx * 0.04}s"
						>
							<div class="dl-card__health dl-card__health--{health}" title="Santé: {health}"></div>
							<div class="dl-card__body">
								<div class="dl-card__top">
									<h3 class="dl-card__title" title={result.title}>{result.title}</h3>
									<div class="dl-card__tags">
										{#if tags.quality}
											<span class="dl-tag dl-tag--quality"
												class:dl-tag--4k={tags.quality === '4K'}
											>{tags.quality}</span>
										{/if}
										{#if tags.format}
											<span class="dl-tag dl-tag--format">{tags.format}</span>
										{/if}
										{#if tags.lang}
											<span class="dl-tag dl-tag--lang">{tags.lang}</span>
										{/if}
									</div>
								</div>
								<div class="dl-card__bottom">
									<div class="dl-card__stats">
										<span class="dl-stat dl-stat--size">
											<i class="fa-solid fa-hard-drive"></i>
											{formatBytes(result.size_bytes)}
										</span>
										<span class="dl-stat dl-stat--seed">
											<i class="fa-solid fa-arrow-up"></i>
											{result.seeders}
										</span>
										<span class="dl-stat dl-stat--leech">
											<i class="fa-solid fa-arrow-down"></i>
											{result.leechers}
										</span>
									</div>
									<button
										class="dl-card__btn"
										class:dl-card__btn--loading={isDownloading}
										class:dl-card__btn--done={isDownloaded}
										disabled={isDownloading || isDownloaded}
										onclick={() => download(result)}
										title={isDownloaded ? 'Téléchargement lancé' : 'Télécharger'}
									>
										{#if isDownloading}
											<i class="fa-solid fa-spinner fa-spin"></i>
										{:else if isDownloaded}
											<i class="fa-solid fa-check"></i>
										{:else}
											<i class="fa-solid fa-download"></i>
											<span class="dl-card__btn-label">Télécharger</span>
										{/if}
									</button>
								</div>
							</div>
						</div>
					{/each}
				{/if}
			</div>
		</section>
	</div>
</div>

<!-- ═══ SIDEBAR OVERLAY + PANEL ═══ -->
{#if sidebarOpen}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="dl-overlay" onclick={() => sidebarOpen = false} onkeydown={() => {}}></div>
{/if}
<aside class="dl-sidebar" class:dl-sidebar--open={sidebarOpen} class:dl-sidebar--pulse={sidebarPulse}>
	<div class="dl-sidebar__header">
		<div class="dl-sidebar__header-left">
			<div class="dl-sidebar__header-icon">
				<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
			</div>
			<h3 class="dl-sidebar__title">Téléchargements</h3>
			{#if totalActive > 0}
				<span class="dl-sidebar__count">{totalActive}</span>
			{/if}
		</div>
		<button class="dl-sidebar__close" onclick={() => sidebarOpen = false} aria-label="Fermer">
			<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6L6 18"/><path d="M6 6l12 12"/></svg>
		</button>
	</div>

	<div class="dl-sidebar__list">
		{#if activeDownloads.length === 0}
			<div class="dl-sidebar__empty">
				<div class="dl-sidebar__empty-icon">
					<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3">
						<path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
					</svg>
				</div>
				<p>Aucun téléchargement en cours</p>
				<a href="/history" class="dl-sidebar__history-link">
					<i class="fa-solid fa-clock-rotate-left"></i> Voir l'historique
				</a>
			</div>
		{:else}
			<div class="dl-sidebar__section-label">En cours</div>
			{#each activeDownloads as task (task.id)}
				{@const payload = task.payload as any}
				{@const pct = Number(task.progress) || 0}
				<div class="dl-sidebar__item dl-sidebar__item--active">
					<div class="dl-sidebar__item-icon">
						{#if task.status === 'running'}
							<i class="fa-solid fa-circle-notch fa-spin"></i>
						{:else}
							<i class="fa-solid fa-clock"></i>
						{/if}
					</div>
					<div class="dl-sidebar__item-body">
						<span class="dl-sidebar__item-title" title={payload?.title}>{payload?.title || 'Téléchargement…'}</span>
						<div class="dl-sidebar__progress-row">
							<div class="dl-sidebar__progress-track">
								<div
									class="dl-sidebar__progress-fill"
									class:dl-sidebar__progress-fill--indeterminate={pct === 0 && task.status === 'running'}
									style="width: {pct}%"
								></div>
							</div>
							<span class="dl-sidebar__pct">{pct.toFixed(0)}%</span>
						</div>
					</div>
				</div>
			{/each}
			<a href="/history" class="dl-sidebar__history-link">
				<i class="fa-solid fa-clock-rotate-left"></i> Voir l'historique
			</a>
		{/if}
	</div>
</aside>

<style>
/* ═══════════════════════════════════════
   DOWNLOAD PAGE — Premium Streaming UI
   ═══════════════════════════════════════ */

.dl-page {
	display: flex;
	flex-direction: column;
	min-height: 100vh;
	background: var(--bg-primary, #1A1D29);
}

/* ═══ CUSTOM TOP BAR ═══ */
.dl-topbar {
	position: fixed;
	top: 0;
	left: 0;
	right: 0;
	z-index: 100;
	height: 60px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 0 24px;
	background: rgba(26, 29, 41, 0.85);
	backdrop-filter: blur(16px);
	-webkit-backdrop-filter: blur(16px);
	border-bottom: 1px solid rgba(255,255,255,0.06);
}
.dl-topbar__back {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 8px 16px;
	border-radius: 8px;
	background: rgba(255,255,255,0.05);
	border: 1px solid rgba(255,255,255,0.08);
	color: #F9F9F9;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
.dl-topbar__back:hover {
	background: rgba(255,255,255,0.1);
	border-color: rgba(255,255,255,0.15);
	transform: translateX(-2px);
}

.dl-topbar__downloads {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 8px 18px;
	border-radius: 8px;
	background: rgba(0,114,210,0.12);
	border: 1px solid rgba(0,114,210,0.25);
	color: #60a5fa;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	position: relative;
}
.dl-topbar__downloads:hover {
	background: rgba(0,114,210,0.2);
	border-color: rgba(0,114,210,0.4);
	color: #93bbfc;
}
.dl-topbar__downloads--active {
	background: rgba(0,114,210,0.25);
	border-color: rgba(0,114,210,0.5);
	color: #93bbfc;
}
.dl-topbar__badge {
	min-width: 20px;
	height: 20px;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 10px;
	background: #0072D2;
	color: #fff;
	font-size: 11px;
	font-weight: 700;
	padding: 0 5px;
	animation: pulse 2s ease-in-out infinite;
}

/* ── HERO ── */
.dl-hero {
	position: relative;
	min-height: 300px;
	margin-top: 60px;
	display: flex;
	align-items: flex-end;
	background-image:
		linear-gradient(to top, var(--bg-primary, #1A1D29) 0%, rgba(26,29,41,0.85) 40%, rgba(26,29,41,0.3) 100%),
		var(--backdrop-url, none);
	background-size: cover;
	background-position: center 25%;
	overflow: hidden;
}
.dl-hero__gradient {
	position: absolute;
	inset: 0;
	background: linear-gradient(to top, var(--bg-primary, #1A1D29) 2%, transparent 60%);
	pointer-events: none;
}
.dl-hero__inner {
	position: relative;
	z-index: 2;
	width: 100%;
	max-width: 1440px;
	margin: 0 auto;
	padding: 0 40px 32px;
}
.dl-hero__media {
	display: flex;
	align-items: flex-end;
	gap: 28px;
}
.dl-hero__poster {
	width: 140px;
	border-radius: 12px;
	box-shadow: 0 16px 40px rgba(0,0,0,0.6);
	flex-shrink: 0;
}
.dl-hero__info {
	flex: 1;
	min-width: 0;
}
.dl-hero__type-badge {
	display: inline-block;
	padding: 3px 10px;
	background: rgba(0,114,210,0.25);
	color: #60a5fa;
	border-radius: 20px;
	font-size: 11px;
	font-weight: 700;
	text-transform: uppercase;
	letter-spacing: 1px;
	margin-bottom: 8px;
}
.dl-hero__title {
	font-size: clamp(24px, 4vw, 42px);
	font-weight: 800;
	color: #F9F9F9;
	margin: 0 0 10px;
	line-height: 1.15;
	text-shadow: 0 2px 12px rgba(0,0,0,0.4);
}
.dl-hero__meta {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	margin-bottom: 12px;
}
.dl-hero__meta-chip {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	padding: 4px 12px;
	background: rgba(255,255,255,0.08);
	border-radius: 20px;
	font-size: 12px;
	font-weight: 500;
	color: #CACACA;
}
.dl-hero__meta-chip--gold {
	background: rgba(250,204,21,0.15);
	color: #facc15;
}
.dl-hero__overview {
	font-size: 13px;
	line-height: 1.7;
	color: rgba(249,249,249,0.6);
	max-width: 520px;
	margin: 0 0 12px;
}
.dl-hero__result-count {
	font-size: 13px;
	color: rgba(249,249,249,0.5);
}
.dl-hero__result-count strong { color: #60a5fa; font-weight: 700; }
.dl-hero__searching { color: #60a5fa; }

/* ── BODY LAYOUT ── */
.dl-body {
	flex: 1;
	max-width: 1440px;
	width: 100%;
	margin: 0 auto;
	padding: 0 20px 40px;
}

/* ── FILTERS ── */
.dl-filters {
	display: flex;
	gap: 8px;
	flex-wrap: wrap;
	padding: 0 0 16px;
}
.dl-filter-chip {
	padding: 6px 16px;
	border-radius: 20px;
	background: rgba(255,255,255,0.05);
	border: 1px solid rgba(255,255,255,0.08);
	color: #CACACA;
	font-size: 12px;
	font-weight: 600;
	cursor: pointer;
	transition: all 200ms ease;
	text-transform: uppercase;
	letter-spacing: 0.5px;
}
.dl-filter-chip:hover {
	background: rgba(255,255,255,0.1);
	color: #F9F9F9;
}
.dl-filter-chip.active {
	background: rgba(0,114,210,0.2);
	border-color: rgba(0,114,210,0.5);
	color: #60a5fa;
}

/* ── SOURCES LIST ── */
.dl-sources {
	min-width: 0;
}
.dl-sources__list {
	display: flex;
	flex-direction: column;
	gap: 8px;
}

/* ── TORRENT CARD ── */
.dl-card {
	position: relative;
	display: flex;
	background: linear-gradient(135deg, rgba(37,40,51,0.7) 0%, rgba(30,33,44,0.9) 100%);
	border: 1px solid rgba(255,255,255,0.06);
	border-radius: 12px;
	overflow: hidden;
	transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	animation: cardIn 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94) both;
}
@keyframes cardIn {
	from { opacity: 0; transform: translateY(12px); }
	to   { opacity: 1; transform: translateY(0); }
}
.dl-card:hover {
	background: linear-gradient(135deg, rgba(45,50,65,0.9) 0%, rgba(37,40,51,1) 100%);
	border-color: rgba(0,114,210,0.35);
	transform: translateY(-2px);
	box-shadow: 0 8px 32px rgba(0,0,0,0.3), 0 0 0 1px rgba(0,114,210,0.15);
}
.dl-card--downloaded {
	opacity: 0.55;
	border-color: rgba(16,185,129,0.2);
}
.dl-card--downloaded:hover { opacity: 0.7; }

.dl-card__health {
	width: 3px;
	flex-shrink: 0;
	border-radius: 3px 0 0 3px;
}
.dl-card__health--excellent { background: linear-gradient(to bottom, #10b981, #059669); }
.dl-card__health--good { background: linear-gradient(to bottom, #3b82f6, #2563eb); }
.dl-card__health--fair { background: linear-gradient(to bottom, #f59e0b, #d97706); }
.dl-card__health--poor { background: linear-gradient(to bottom, #ef4444, #b91c1c); }

.dl-card__body {
	flex: 1;
	min-width: 0;
	padding: 14px 18px;
	display: flex;
	flex-direction: column;
	gap: 10px;
}
.dl-card__top {
	display: flex;
	flex-direction: column;
	gap: 6px;
}
.dl-card__title {
	margin: 0;
	font-size: 14px;
	font-weight: 600;
	color: #F9F9F9;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	line-height: 1.4;
}
.dl-card__tags {
	display: flex;
	gap: 6px;
	flex-wrap: wrap;
}

.dl-tag {
	padding: 2px 8px;
	border-radius: 4px;
	font-size: 10px;
	font-weight: 700;
	text-transform: uppercase;
	letter-spacing: 0.5px;
}
.dl-tag--quality {
	background: rgba(59,130,246,0.2);
	color: #60a5fa;
	border: 1px solid rgba(59,130,246,0.3);
}
.dl-tag--4k {
	background: linear-gradient(135deg, rgba(139,92,246,0.3), rgba(236,72,153,0.2));
	color: #c084fc;
	border-color: rgba(139,92,246,0.4);
}
.dl-tag--format {
	background: rgba(255,255,255,0.06);
	color: #94a3b8;
	border: 1px solid rgba(255,255,255,0.08);
}
.dl-tag--lang {
	background: rgba(16,185,129,0.15);
	color: #34d399;
	border: 1px solid rgba(16,185,129,0.25);
}

.dl-card__bottom {
	display: flex;
	align-items: center;
	justify-content: space-between;
}
.dl-card__stats {
	display: flex;
	gap: 16px;
}
.dl-stat {
	display: flex;
	align-items: center;
	gap: 5px;
	font-size: 12px;
	font-weight: 500;
}
.dl-stat i { font-size: 10px; }
.dl-stat--size { color: #94a3b8; }
.dl-stat--seed { color: #4ade80; }
.dl-stat--leech { color: #f87171; }

.dl-card__btn {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 8px 20px;
	border-radius: 8px;
	background: rgba(0,114,210,0.15);
	border: 1px solid rgba(0,114,210,0.3);
	color: #60a5fa;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	white-space: nowrap;
}
.dl-card__btn:hover:not(:disabled) {
	background: var(--accent, #0072D2);
	border-color: var(--accent, #0072D2);
	color: #fff;
	transform: scale(1.04);
	box-shadow: 0 4px 20px rgba(0,114,210,0.35);
}
.dl-card__btn:active:not(:disabled) { transform: scale(0.97); }
.dl-card__btn--loading {
	background: rgba(0,114,210,0.1);
	border-color: rgba(0,114,210,0.2);
	color: #60a5fa;
	pointer-events: none;
}
.dl-card__btn--done {
	background: rgba(16,185,129,0.15);
	border-color: rgba(16,185,129,0.3);
	color: #34d399;
	pointer-events: none;
}
.dl-card__btn-label { display: inline; }

/* ── EMPTY STATE ── */
.dl-empty {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 60px 20px;
	text-align: center;
}
.dl-empty__icon {
	font-size: 40px;
	color: rgba(249,249,249,0.15);
	margin-bottom: 16px;
}
.dl-empty__text {
	font-size: 15px;
	color: #94a3b8;
	margin-bottom: 20px;
}
.dl-empty__retry {
	padding: 10px 24px;
	border-radius: 8px;
	background: rgba(0,114,210,0.15);
	border: 1px solid rgba(0,114,210,0.3);
	color: #60a5fa;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	transition: all 200ms ease;
}
.dl-empty__retry:hover {
	background: var(--accent, #0072D2);
	color: #fff;
}

/* ═══ OVERLAY ═══ */
.dl-overlay {
	position: fixed;
	inset: 0;
	z-index: 200;
	background: rgba(0,0,0,0.5);
	animation: fadeIn 0.25s ease both;
}

/* ═══ SIDEBAR PANEL (slide-in from right) ═══ */
.dl-sidebar {
	position: fixed;
	top: 0;
	right: 0;
	bottom: 0;
	z-index: 210;
	width: 380px;
	max-width: 90vw;
	background: rgba(26, 29, 41, 0.97);
	backdrop-filter: blur(20px);
	-webkit-backdrop-filter: blur(20px);
	border-left: 1px solid rgba(255,255,255,0.08);
	display: flex;
	flex-direction: column;
	transform: translateX(100%);
	transition: transform 0.35s cubic-bezier(0.25, 0.46, 0.45, 0.94),
	            box-shadow 0.35s ease;
	box-shadow: none;
}
.dl-sidebar--open {
	transform: translateX(0);
	box-shadow: -8px 0 40px rgba(0,0,0,0.4);
}
.dl-sidebar--pulse {
	animation: sidebarGlow 1.2s ease both;
}
@keyframes sidebarGlow {
	0%   { border-left-color: rgba(255,255,255,0.08); }
	30%  { border-left-color: rgba(0,114,210,0.7); box-shadow: -8px 0 60px rgba(0,114,210,0.2); }
	100% { border-left-color: rgba(255,255,255,0.08); }
}

.dl-sidebar__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 18px 20px;
	border-bottom: 1px solid rgba(255,255,255,0.06);
}
.dl-sidebar__header-left {
	display: flex;
	align-items: center;
	gap: 10px;
}
.dl-sidebar__header-icon {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 32px;
	height: 32px;
	border-radius: 8px;
	background: rgba(0,114,210,0.15);
	color: #60a5fa;
}
.dl-sidebar__title {
	font-size: 14px;
	font-weight: 700;
	color: #F9F9F9;
	margin: 0;
	text-transform: uppercase;
	letter-spacing: 0.5px;
}
.dl-sidebar__count {
	min-width: 22px;
	height: 22px;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 11px;
	background: var(--accent, #0072D2);
	color: #fff;
	font-size: 11px;
	font-weight: 700;
	padding: 0 6px;
	animation: pulse 2s ease-in-out infinite;
}
.dl-sidebar__close {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 34px;
	height: 34px;
	border-radius: 8px;
	background: rgba(255,255,255,0.05);
	border: 1px solid rgba(255,255,255,0.08);
	color: #94a3b8;
	cursor: pointer;
	padding: 0;
	transition: all 200ms ease;
}
.dl-sidebar__close:hover {
	background: rgba(255,255,255,0.1);
	color: #F9F9F9;
}

.dl-sidebar__list {
	flex: 1;
	overflow-y: auto;
	padding: 12px;
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.dl-sidebar__section-label {
	font-size: 10px;
	font-weight: 700;
	text-transform: uppercase;
	letter-spacing: 1px;
	color: #64748b;
	padding: 8px 8px 4px;
}

.dl-sidebar__item {
	display: flex;
	align-items: flex-start;
	gap: 10px;
	padding: 12px;
	border-radius: 10px;
	background: rgba(255,255,255,0.03);
	border: 1px solid transparent;
	animation: itemSlideIn 0.4s cubic-bezier(0.25, 0.46, 0.45, 0.94) both;
}
@keyframes itemSlideIn {
	from { opacity: 0; transform: translateX(20px); }
	to   { opacity: 1; transform: translateX(0); }
}
.dl-sidebar__item--active {
	border-color: rgba(0,114,210,0.15);
	background: rgba(0,114,210,0.06);
}
.dl-sidebar__item--done { opacity: 0.6; }

.dl-sidebar__item-icon {
	width: 28px;
	height: 28px;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 50%;
	background: rgba(0,114,210,0.15);
	color: #60a5fa;
	font-size: 12px;
	flex-shrink: 0;
	margin-top: 2px;
}
.dl-sidebar__item-icon--done {
	background: rgba(16,185,129,0.15);
	color: #34d399;
}

.dl-sidebar__item-body {
	flex: 1;
	min-width: 0;
}
.dl-sidebar__item-title {
	display: block;
	font-size: 12px;
	font-weight: 600;
	color: #e2e8f0;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	margin-bottom: 6px;
}

.dl-sidebar__progress-row {
	display: flex;
	align-items: center;
	gap: 8px;
}
.dl-sidebar__progress-track {
	flex: 1;
	height: 4px;
	background: rgba(255,255,255,0.06);
	border-radius: 2px;
	overflow: hidden;
}
.dl-sidebar__progress-fill {
	height: 100%;
	background: linear-gradient(90deg, #0072D2, #3b82f6);
	border-radius: 2px;
	transition: width 0.5s ease;
}
.dl-sidebar__progress-fill--indeterminate {
	width: 40% !important;
	animation: indeterminate 1.5s ease-in-out infinite;
}
@keyframes indeterminate {
	0%   { transform: translateX(-100%); }
	100% { transform: translateX(350%); }
}
.dl-sidebar__pct {
	font-size: 11px;
	font-weight: 700;
	color: #60a5fa;
	min-width: 32px;
	text-align: right;
}

.dl-sidebar__empty {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	padding: 40px 20px;
	text-align: center;
}
.dl-sidebar__empty-icon { margin-bottom: 12px; color: rgba(255,255,255,0.15); }
.dl-sidebar__empty p {
	font-size: 12px;
	color: #64748b;
	line-height: 1.6;
	margin: 0;
}
.dl-sidebar__history-link {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	margin-top: 16px;
	padding: 8px 16px;
	font-size: 12px;
	font-weight: 600;
	color: #60a5fa;
	text-decoration: none;
	background: rgba(0,114,210,0.1);
	border-radius: 8px;
	transition: all 200ms ease;
}
.dl-sidebar__history-link:hover {
	background: rgba(0,114,210,0.2);
	color: #93bbfc;
}

/* ═══ RESPONSIVE ═══ */
@media (max-width: 768px) {
	.dl-topbar { padding: 0 16px; }
	.dl-topbar__back span,
	.dl-topbar__downloads span { display: none; }
	.dl-body { padding: 0 16px 100px; }
	.dl-hero__inner { padding: 0 20px 24px; }
	.dl-hero__poster { width: 100px; }
	.dl-hero__title { font-size: 22px; }
	.dl-hero__overview { display: none; }
	.dl-card__btn-label { display: none; }
	.dl-sidebar { width: 100vw; max-width: 100vw; }
}
</style>
