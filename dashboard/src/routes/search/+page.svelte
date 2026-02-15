<script lang="ts">
	import { tmdbDiscover, tmdbSearch } from '$lib/api/client';
	import type { TmdbSearchItem } from '$lib/api/client';
	import MediaCard from '$lib/components/MediaCard.svelte';
	import { onMount } from 'svelte';

	let query = $state('');
	let results: TmdbSearchItem[] = $state([]);
	let loading = $state(false);
	let debounceTimer: ReturnType<typeof setTimeout> | null = $state(null);

	const filters = {
		genres: {
			movie: [
				{ id: 28, name: 'Action' },
				{ id: 18, name: 'Drame' },
				{ id: 35, name: 'Comédie' },
			],
			tv: [
				{ id: 10759, name: 'Action & Adventure' },
				{ id: 18, name: 'Drame' },
				{ id: 35, name: 'Comédie' },
			]
		},
		sorts: [
			{ id: 'popularity.desc', name: 'Popularité' },
			{ id: 'release_date.desc', name: 'Date de sortie' },
			{ id: 'vote_average.desc', name: 'Note' },
		],
	};

	let selectedGenre: number | null = $state(null);
	let selectedSort = $state('popularity.desc');
	let selectedYear: number | null = $state(new Date().getFullYear());
	let selectedType = $state('all');

	async function performSearch() {
		loading = true;
		try {
			if (query.trim()) {
				// Text search is active
				const searchResults = await tmdbSearch(query.trim());
				// Client-side filter for movie/tv
				if (selectedType !== 'all') {
					results = searchResults.filter(r => r.media_type === selectedType);
				} else {
					results = searchResults;
				}
			} else {
				// No text search, use discover
				const res = await tmdbDiscover(selectedType === 'all' ? 'movie' : selectedType, {
					with_genres: selectedGenre ? String(selectedGenre) : undefined,
					sort_by: selectedSort,
					year: selectedYear ?? undefined,
				});
				results = res.results;
			}
		} catch (error) {
			console.error("Failed to search:", error);
			results = [];
		} finally {
			loading = false;
		}
	}
	
	$effect(() => {
		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(performSearch, 400);
	});

</script>

<div class="search-page">
	<div class="search-pill">
		<input bind:value={query} placeholder="Rechercher un film ou une série..." />
	</div>

	<div class="filters">
		<select bind:value={selectedType}>
			<option value="all">Tout</option>
			<option value="movie">Films</option>
			<option value="tv">Séries</option>
		</select>
		<select bind:value={selectedGenre} disabled={selectedType === 'all'}>
			<option value={null}>Genre</option>
			{#if selectedType === 'movie'}
				{#each filters.genres.movie as genre}
					<option value={genre.id}>{genre.name}</option>
				{/each}
			{/if}
			{#if selectedType === 'tv'}
				{#each filters.genres.tv as genre}
					<option value={genre.id}>{genre.name}</option>
				{/each}
			{/if}
		</select>
		<input type="number" bind:value={selectedYear} placeholder="Année" />
		<select bind:value={selectedSort}>
			{#each filters.sorts as sort}
				<option value={sort.id}>{sort.name}</option>
			{/each}
		</select>
	</div>

	<div class="results-grid">
		{#if loading}
			{#each Array(20) as _}
				<div class="skeleton-card"></div>
			{/each}
		{:else if results.length > 0}
			{#each results as item}
				<MediaCard item={item} />
			{/each}
		{:else}
			<p>Commencez à taper pour rechercher.</p>
		{/if}
	</div>
</div>

<style>
	.search-page {
		padding: 2rem;
	}
	.search-pill {
		margin-bottom: 1rem;
	}
	.filters {
		display: flex;
		gap: 1rem;
		margin-bottom: 2rem;
	}
	.results-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: 1rem;
	}
	.skeleton-card {
		aspect-ratio: 2 / 3;
		background-color: var(--bg-card);
		border-radius: var(--radius);
	}
</style>
