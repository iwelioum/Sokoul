<script lang="ts">
	import { page } from '$app/stores';
	import { getDirectStreamLinks, tmdbMovieDetails, tmdbTvDetails } from '$lib/api/client';
	import type { TmdbMovieDetail, TmdbTvDetail, StreamSource } from '$lib/api/client';
	import { onMount } from 'svelte';

	let { params } = $props();

	let mediaType = $derived(params.media_type);
	let tmdbId = $derived(params.tmdb_id);
	let season = $derived($page.url.searchParams.get('season'));
	let episode = $derived($page.url.searchParams.get('episode'));

	let links: string[] = $state([]);
	let mediaDetails: TmdbMovieDetail | TmdbTvDetail | null = $state(null);
	let activeIndex = $state(0);
	let iframeKey = $state(0);
	let showTitle = $state(true);
	let showSkipIntro = $state(false);
	
	let iframeElement: HTMLIFrameElement | undefined = $state();

	async function fetchStreamLinks() {
		try {
			const result = await getDirectStreamLinks(mediaType, Number(tmdbId), season ? Number(season) : undefined, episode ? Number(episode) : undefined);
			links = result.sources.map((s: StreamSource) => s.url);
		} catch (error) {
			console.error("Failed to fetch stream links:", error);
		}
	}

	async function fetchMediaDetails() {
		try {
			if (mediaType === 'movie') {
				mediaDetails = await tmdbMovieDetails(Number(tmdbId));
			} else if (mediaType === 'tv') {
				mediaDetails = await tmdbTvDetails(Number(tmdbId));
			}
		} catch (error) {
			console.error("Failed to fetch media details:", error);
		}
	}

	function switchToNext() {
		activeIndex = (activeIndex + 1) % links.length;
		iframeKey++;
	}

	function switchToPrevious() {
		activeIndex = (activeIndex - 1 + links.length) % links.length;
		iframeKey++;
	}

	function requestFullscreen() {
		if (iframeElement) {
			iframeElement.requestFullscreen();
		}
	}

	function goBack() {
		history.back();
	}
	
	onMount(() => {
		fetchStreamLinks();
		fetchMediaDetails();

		const titleTimeout = setTimeout(() => {
			showTitle = false;
		}, 3000);

		const skipIntroTimeout = setTimeout(() => {
			showSkipIntro = true;
		}, 30000);

		function handleKeydown(e: KeyboardEvent) {
			if (e.key === 'f') {
				requestFullscreen();
			} else if (e.key === 'Escape') {
				goBack();
			} else if (e.key === 'ArrowRight') {
				switchToNext();
			} else if (e.key === 'ArrowLeft') {
				switchToPrevious();
			}
		}

		window.addEventListener('keydown', handleKeydown);

		return () => {
			clearTimeout(titleTimeout);
			clearTimeout(skipIntroTimeout);
			window.removeEventListener('keydown', handleKeydown);
		};
	});

	function getTitle(media: TmdbMovieDetail | TmdbTvDetail | null) {
		if (!media) return '';
		if ('title' in media) {
			return media.title;
		}
		return media.name;
	}

</script>

<div class="player-page">
	{#if mediaDetails}
		<div class="title-overlay" class:hidden={!showTitle}>
			<h1>{getTitle(mediaDetails)}</h1>
			{#if mediaType === 'tv' && season && episode}
				<h2>S{season} E{episode}</h2>
			{/if}
		</div>
	{/if}

	<a href={'/' + mediaType + '/' + tmdbId} class="back-button">←</a>
	
	{#if links.length > 0}
		{#key iframeKey}
			<iframe 
				bind:this={iframeElement} 
				src={links[activeIndex]} 
				allow="fullscreen"
				title="Video Player"
			></iframe>
		{/key}
	{:else}
		<div class="loading-container">
			<p>Loading streams...</p>
		</div>
	{/if}

	{#if showSkipIntro}
		<button class="skip-intro-button" onclick={switchToNext}>
			Passer l'intro
		</button>
	{/if}

	<div class="controls">
		<div class="source-controls">
			<select bind:value={activeIndex}>
				{#each links as link, i}
					<option value={i}>Source {i + 1}</option>
				{/each}
			</select>
			<button onclick={switchToNext}>Lien mort</button>
		</div>
		<button onclick={requestFullscreen}>⛶ Fullscreen</button>
	</div>
</div>

<style>
	.player-page {
		background: #000;
		min-height: 100vh;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		position: relative;
		overflow: hidden;
	}

	iframe {
		width: 100%;
		height: 85vh;
		border: none;
	}

	.title-overlay {
		position: absolute;
		top: 20px;
		left: 60px;
		color: white;
		z-index: 10;
		transition: opacity 0.5s ease-in-out;
		opacity: 1;
	}

	.title-overlay.hidden {
		opacity: 0;
	}

	.back-button {
		position: absolute;
		top: 20px;
		left: 20px;
		font-size: 24px;
		color: white;
		text-decoration: none;
		z-index: 11;
	}
	
	.skip-intro-button {
		position: absolute;
		bottom: 80px;
		right: 20px;
		z-index: 10;
		background-color: rgba(0, 0, 0, 0.7);
		color: white;
		border: 1px solid white;
		padding: 10px 20px;
		cursor: pointer;
		opacity: 0;
		animation: fadeIn 1s forwards;
		animation-delay: 30s;
	}

	@keyframes fadeIn {
		to { opacity: 1; }
	}

	.controls {
		position: absolute;
		bottom: 20px;
		left: 20px;
		right: 20px;
		display: flex;
		justify-content: space-between;
		align-items: center;
		z-index: 10;
		color: white;
	}

	.source-controls {
		display: flex;
		gap: 10px;
	}

	.loading-container {
		height: 85vh;
		display: flex;
		justify-content: center;
		align-items: center;
	}
</style>
