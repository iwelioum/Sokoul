<script lang="ts">
	import { tmdbDiscover, listLibrary, isLoggedIn } from '$lib/api/client';
	import type { TmdbSearchItem, Favorite } from '$lib/api/client';
	import MediaCard from '$lib/components/MediaCard.svelte';
	import MediaRow from '$lib/components/MediaRow.svelte';
	import MegaFilter from '$lib/components/MegaFilter.svelte';
	import type { FilterState } from '$lib/components/MegaFilter.svelte';
	import Skeleton from '$lib/components/Skeleton.svelte';
	import { onMount } from 'svelte';

	const TV_GENRES = [
		{ id: 18, name: 'Drame' },
		{ id: 35, name: 'Comédie' },
		{ id: 80, name: 'Crime' },
		{ id: 10759, name: 'Action & Aventure' },
		{ id: 10765, name: 'SF & Fantastique' },
		{ id: 9648, name: 'Mystère' },
		{ id: 16, name: 'Animation' },
	];

	let items: TmdbSearchItem[] = $state([]);
	let genreItems: Record<number, TmdbSearchItem[]> = $state({});
	let libraryIds = $state(new Set<number>());
	let page = $state(1);
	let loading = $state(true);
	let loadingGenres = $state(true);
	let hasMore = $state(true);

	// Filter state
	let filterOpen = $state(false);
	let mediaType: 'movie' | 'tv' | 'all' = $state('tv');
	let selectedGenres: number[] = $state([]);
	let selectedSort = $state('popularity.desc');
	let yearMin = $state(1980);
	let yearMax = $state(new Date().getFullYear());
	let providerFilter: string | null = $state(null);
	let providerName: string | null = $state(null);
	let activeFilterCount = $state(0);

	async function loadGenreRows() {
		loadingGenres = true;
		const results = await Promise.allSettled(
			TV_GENRES.map(g =>
				tmdbDiscover('tv', { with_genres: String(g.id), sort_by: 'popularity.desc', page: 1 })
			)
		);
		const newGenreItems: Record<number, TmdbSearchItem[]> = {};
		results.forEach((result, i) => {
			if (result.status === 'fulfilled') {
				newGenreItems[TV_GENRES[i].id] = result.value.results.slice(0, 20);
			}
		});
		genreItems = newGenreItems;
		loadingGenres = false;
	}

	async function fetchItems() {
		if (!hasMore) return;
		loading = true;
		try {
			const mType = mediaType === 'all' ? 'tv' : mediaType;
			const newItems = await tmdbDiscover(mType, {
				with_genres: selectedGenres.length > 0 ? selectedGenres.join(',') : undefined,
				sort_by: selectedSort,
				year: yearMin === yearMax ? yearMin : undefined,
				page: page,
				with_watch_providers: providerFilter ?? undefined,
				watch_region: providerFilter ? 'FR' : undefined,
			});
			if (newItems.results.length === 0) {
				hasMore = false;
			} else {
				items = [...items, ...newItems.results];
				page++;
			}
		} catch (error) {
			console.error("Failed to discover tv shows:", error);
		} finally {
			loading = false;
		}
	}

	async function fetchLibrary() {
		if (!isLoggedIn()) { libraryIds = new Set(); return; }
		try {
			const libraryItems = await listLibrary();
			libraryIds = new Set(libraryItems.items.map((item: Favorite) => item.tmdb_id));
		} catch (error) {
			if (!(error instanceof Error && error.message.startsWith('API 401:'))) {
				console.error("Failed to fetch library:", error);
			}
		}
	}

	function handleApplyFilters(state: FilterState) {
		mediaType = state.mediaType;
		selectedGenres = state.genres;
		selectedSort = state.sort;
		yearMin = state.yearMin;
		yearMax = state.yearMax;
		providerFilter = state.provider;
		providerName = state.providerName;
		activeFilterCount = state.genres.length
			+ (state.provider ? 1 : 0)
			+ (state.yearMin !== 1980 || state.yearMax !== new Date().getFullYear() ? 1 : 0)
			+ (state.sort !== 'popularity.desc' ? 1 : 0);

		items = [];
		page = 1;
		hasMore = true;
		fetchItems();
	}

	function clearAllFilters() {
		selectedGenres = [];
		selectedSort = 'popularity.desc';
		yearMin = 1980;
		yearMax = new Date().getFullYear();
		providerFilter = null;
		providerName = null;
		activeFilterCount = 0;
		window.history.replaceState({}, '', '/series');
		items = [];
		page = 1;
		hasMore = true;
		loadGenreRows();
	}

	onMount(() => {
		const params = new URL(window.location.href).searchParams;
		providerFilter = params.get('provider');
		providerName = params.get('provider_name');

		const genreParam = params.get('genre');
		if (genreParam) {
			const genreId = parseInt(genreParam);
			if (!isNaN(genreId)) selectedGenres = [genreId];
		}

		if (providerFilter || selectedGenres.length > 0) {
			activeFilterCount = (providerFilter ? 1 : 0) + selectedGenres.length;
			fetchItems();
		} else {
			loadGenreRows();
		}
		fetchLibrary();
	});
</script>

<svelte:head>
	<title>{providerName ? `${providerName} — Séries` : 'Séries'} — SOKOUL</title>
</svelte:head>

<MegaFilter
	bind:open={filterOpen}
	bind:mediaType
	bind:selectedGenres
	bind:selectedSort
	bind:yearMin
	bind:yearMax
	bind:selectedProvider={providerFilter}
	onApply={handleApplyFilters}
	onClose={() => {}}
/>

<div class="page-container">
	<div class="page-header">
		<div class="header-left">
			{#if providerName}
				<h1>{providerName} — Séries</h1>
			{:else}
				<h1>Séries</h1>
			{/if}
		</div>
		<div class="header-right">
			{#if activeFilterCount > 0}
				<button class="btn-clear" onclick={clearAllFilters}>
					<svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
					Effacer
				</button>
			{/if}
			<button class="btn-filter" onclick={() => { filterOpen = true; }}>
				<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"/></svg>
				FILTRER
				{#if activeFilterCount > 0}
					<span class="badge">{activeFilterCount}</span>
				{/if}
			</button>
		</div>
	</div>

	{#if activeFilterCount > 0}
		<div class="active-filters">
			{#if providerName}
				<span class="filter-tag">
					📺 {providerName}
					<button onclick={() => { providerFilter = null; providerName = null; activeFilterCount--; items = []; page = 1; hasMore = true; fetchItems(); }}>×</button>
				</span>
			{/if}
			{#if selectedGenres.length > 0}
				<span class="filter-tag">
					🎭 {selectedGenres.length} genre{selectedGenres.length > 1 ? 's' : ''}
				</span>
			{/if}
			{#if yearMin !== 1980 || yearMax !== new Date().getFullYear()}
				<span class="filter-tag">📅 {yearMin}–{yearMax}</span>
			{/if}
			{#if selectedSort !== 'popularity.desc'}
				<span class="filter-tag">📊 Tri personnalisé</span>
			{/if}
		</div>
	{/if}

	{#if activeFilterCount === 0 && !providerFilter}
		<!-- Genre rows view -->
		{#each TV_GENRES as genre (genre.id)}
			<MediaRow
				title={genre.name}
				items={genreItems[genre.id] ?? []}
				loading={loadingGenres}
				seeMoreHref={`/series?genre=${genre.id}&genre_name=${encodeURIComponent(genre.name)}`}
			/>
		{/each}
	{:else}
		<!-- Filtered flat grid view -->
		<div class="grid">
			{#each items as item (item.id)}
				<MediaCard item={item} inLibrary={libraryIds.has(item.id)} />
			{/each}
		</div>

		{#if loading}
			<div class="grid">
				{#each Array(20) as _}
					<Skeleton />
				{/each}
			</div>
		{/if}

		{#if hasMore && !loading}
			<button class="load-more" onclick={fetchItems}>Charger plus</button>
		{/if}

		{#if !loading && items.length === 0 && !hasMore}
			<div class="empty-state">
				<svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" style="opacity:0.3"><path d="M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z"/></svg>
				<p>Aucun résultat trouvé</p>
				<button class="btn-clear" onclick={clearAllFilters}>Réinitialiser les filtres</button>
			</div>
		{/if}
	{/if}
</div>

<style>
	.page-container {
		padding: 90px calc(3.5vw + 5px) 20px;
	}

	.page-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		flex-wrap: wrap;
		margin-bottom: 20px;
	}

	.header-left h1 {
		font-size: 28px;
		font-weight: 700;
		color: #F9F9F9;
		margin: 0;
	}

	.header-right {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.btn-filter {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 20px;
		background: linear-gradient(135deg, #0072D2 0%, #0585F2 100%);
		border: none;
		border-radius: 10px;
		color: #fff;
		font-size: 14px;
		font-weight: 700;
		letter-spacing: 1.5px;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		text-transform: uppercase;
	}

	.btn-filter:hover {
		transform: scale(1.05);
		box-shadow: 0 8px 24px rgba(0, 114, 210, 0.35);
	}

	.badge {
		background: rgba(255, 255, 255, 0.25);
		border-radius: 10px;
		padding: 1px 7px;
		font-size: 11px;
		font-weight: 700;
	}

	.btn-clear {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 8px 14px;
		background: rgba(249, 249, 249, 0.08);
		border: 1px solid rgba(249, 249, 249, 0.15);
		border-radius: 8px;
		color: #CACACA;
		font-size: 13px;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.btn-clear:hover {
		background: rgba(249, 249, 249, 0.15);
		color: #F9F9F9;
	}

	.active-filters {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
		margin-bottom: 20px;
	}

	.filter-tag {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 5px 12px;
		background: rgba(0, 114, 210, 0.1);
		border: 1px solid rgba(0, 114, 210, 0.25);
		border-radius: 16px;
		color: #CACACA;
		font-size: 12px;
		font-weight: 500;
	}

	.filter-tag button {
		background: none;
		border: none;
		color: rgba(249,249,249,0.5);
		font-size: 16px;
		cursor: pointer;
		padding: 0 0 0 4px;
		line-height: 1;
	}

	.filter-tag button:hover { color: #F9F9F9; }

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
		gap: 20px;
	}

	.load-more {
		display: block;
		margin: 30px auto;
		padding: 12px 32px;
		background: rgba(249, 249, 249, 0.08);
		border: 1px solid rgba(249, 249, 249, 0.15);
		border-radius: 10px;
		color: #F9F9F9;
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.load-more:hover {
		background: rgba(249, 249, 249, 0.15);
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 16px;
		padding: 60px 20px;
		color: #CACACA;
	}

	.empty-state p {
		font-size: 16px;
	}

	@media (max-width: 600px) {
		.page-header { flex-direction: column; align-items: flex-start; }
		.grid { grid-template-columns: repeat(2, 1fr); }
	}
</style>
