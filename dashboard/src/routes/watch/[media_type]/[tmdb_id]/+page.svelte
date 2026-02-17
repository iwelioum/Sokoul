<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { extractStreams, getDirectStreamLinks, getSubtitles } from '$lib/api/client';
	import type { ExtractedStream, SubtitleTrack, StreamSource, FrenchSourceGroup } from '$lib/api/client';
	import { tmdbMovieDetails, tmdbTvDetails } from '$lib/api/client';
	import CustomPlayer from '$lib/components/CustomPlayer.svelte';
	import { onMount } from 'svelte';

	let { params } = $props();

	let mediaType = $derived(params.media_type);
	let tmdbId = $derived(params.tmdb_id);
	let season = $derived($page.url.searchParams.get('season'));
	let episode = $derived($page.url.searchParams.get('episode'));

	let streams: ExtractedStream[] = $state([]);
	let subtitles: SubtitleTrack[] = $state([]);
	let iframeFallbacks: StreamSource[] = $state([]);
	let frenchGroups: FrenchSourceGroup[] = $state([]);
	let mediaTitle = $state('');
	let loading = $state(true);

	async function loadPlayer() {
		loading = true;

		// Fetch media details for title (in parallel with streams)
		const detailsPromise = mediaType === 'movie'
			? tmdbMovieDetails(Number(tmdbId)).then(d => { mediaTitle = d.title; })
			: tmdbTvDetails(Number(tmdbId)).then(d => { mediaTitle = d.name; });

		try {
			// Try extracting direct streams first
			const [extraction] = await Promise.all([
				extractStreams(
					mediaType,
					Number(tmdbId),
					season ? Number(season) : undefined,
					episode ? Number(episode) : undefined
				),
				detailsPromise.catch(() => {}),
			]);

			streams = extraction.streams;
			iframeFallbacks = extraction.iframe_fallbacks;
			frenchGroups = extraction.french_groups ?? [];
			subtitles = extraction.subtitles;
		} catch (e) {
			console.warn('Stream extraction failed, falling back to iframes:', e);
			// Fall back to iframe-only mode
			try {
				const result = await getDirectStreamLinks(
					mediaType,
					Number(tmdbId),
					season ? Number(season) : undefined,
					episode ? Number(episode) : undefined
				);
				iframeFallbacks = result.sources;
			} catch (err) {
				console.error('All stream sources failed:', err);
			}

			// Still try to load subtitles separately
			try {
				subtitles = await getSubtitles(
					mediaType,
					Number(tmdbId),
					season ? Number(season) : undefined,
					episode ? Number(episode) : undefined
				);
			} catch {
				// Subtitles not critical
			}

			await detailsPromise.catch(() => {});
		}

		loading = false;
	}

	function handleBack() {
		goto(`/${mediaType}/${tmdbId}`);
	}

	onMount(() => {
		loadPlayer();
	});
</script>

<svelte:head>
	<title>{mediaTitle || 'Lecture'} — Sokoul</title>
</svelte:head>

{#if loading}
	<div class="loading-page">
		<div class="spinner"></div>
		<p>Chargement du lecteur...</p>
	</div>
{:else}
	<CustomPlayer
		{streams}
		{subtitles}
		{iframeFallbacks}
		{frenchGroups}
		title={mediaTitle}
		tmdbId={Number(tmdbId)}
		{mediaType}
		season={season ? Number(season) : undefined}
		episode={episode ? Number(episode) : undefined}
		onBack={handleBack}
	/>
{/if}

<style>
	.loading-page {
		height: 100vh;
		background: #000;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
		color: rgba(255,255,255,0.7);
	}

	.spinner {
		width: 48px;
		height: 48px;
		border: 4px solid rgba(255,255,255,0.2);
		border-top-color: #c8a44e;
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}
</style>
