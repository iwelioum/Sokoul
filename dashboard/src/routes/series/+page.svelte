<script lang="ts">
	import { tmdbDiscover, listLibrary } from '$lib/api/client';
	import type { TmdbSearchItem, Favorite } from '$lib/api/client';
	import MediaCard from '$lib/components/MediaCard.svelte';
	import Skeleton from '$lib/components/Skeleton.svelte';
	import { onMount } from 'svelte';

	let items: TmdbSearchItem[] = $state([]);
	let libraryIds = $state(new Set<number>());
	let page = $state(1);
	let loading = $state(true);
	let hasMore = $state(true);

	const filters = {
		genres: [
			{ id: 10759, name: 'Action & Adventure' },
			{ id: 18, name: 'Drame' },
			{ id: 35, name: 'Comédie' },
			{ id: 10765, name: 'Sci-Fi & Fantasy' },
			{ id: 80, name: 'Crime' },
		],
		sorts: [
			{ id: 'popularity.desc', name: 'Popularité' },
			{ id: 'first_air_date.desc', name: 'Date de diffusion' },
			{ id: 'vote_average.desc', name: 'Note' },
		],
	};

	let selectedGenre: number | null = $state(null);
	let selectedSort = $state('popularity.desc');
	let selectedYear = $state(new Date().getFullYear());

	async function fetchItems() {
		if (!hasMore) return;
		loading = true;
		try {
			const newItems = await tmdbDiscover('tv', {
				with_genres: selectedGenre ? String(selectedGenre) : undefined,
				sort_by: selectedSort,
				year: selectedYear,
				page: page,
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
		try {
			const libraryItems = await listLibrary();
			libraryIds = new Set(libraryItems.items.map((item: Favorite) => item.tmdb_id));
		} catch (error) {
			console.error("Failed to fetch library:", error);
		}
	}

	function applyFilters() {
		items = [];
		page = 1;
		hasMore = true;
		fetchItems();
	}

	onMount(() => {
		fetchItems();
		fetchLibrary();
	});
</script>

<div class="page-container">
	<h1>Séries</h1>

	<div class="filters">
		<select bind:value={selectedGenre} onchange={applyFilters}>
			<option value={null}>Tous les genres</option>
			{#each filters.genres as genre}
				<option value={genre.id}>{genre.name}</option>
			{/each}
		</select>
		<select bind:value={selectedSort} onchange={applyFilters}>
			{#each filters.sorts as sort}
				<option value={sort.id}>{sort.name}</option>
			{/each}
		</select>
		<input type="number" bind:value={selectedYear} onchange={applyFilters} />
	</div>

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
</div>

<style>
	.page-container {
		padding: 20px;
	}

	.filters {
		display: flex;
		gap: 20px;
		margin-bottom: 20px;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: 20px;
	}

	.load-more {
		display: block;
		margin: 20px auto;
	}
</style>
