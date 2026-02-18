<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { tmdbMovieDetails, tmdbTvDetails, resolveConsumetStreams } from '$lib/api/client';
	import type { ExtractedStream, SubtitleTrack } from '$lib/api/client';
	import Player from '$lib/components/CustomPlayer.svelte';

	let { params } = $props();

	const mediaType = $derived(params.media_type);
	const tmdbId   = $derived(Number(params.tmdb_id));
	const season   = $derived($page.url.searchParams.get('season') ? Number($page.url.searchParams.get('season')) : undefined);
	const episode  = $derived($page.url.searchParams.get('episode') ? Number($page.url.searchParams.get('episode')) : undefined);

	let title   = $state('');
	let streams: ExtractedStream[] = $state([]);
	let subtitles: SubtitleTrack[] = $state([]);
	let error   = $state('');
	let loading = $state(true);

	onMount(async () => {
		const titlePromise = mediaType === 'movie'
			? tmdbMovieDetails(tmdbId).then(d => { title = d.title; }).catch(() => {})
			: tmdbTvDetails(tmdbId).then(d => { title = d.name; }).catch(() => {});

		try {
			const res = await resolveConsumetStreams(mediaType, tmdbId, season, episode);
			streams   = res.streams;
			subtitles = res.subtitles;
		} catch (e: any) {
			error = e?.message ?? 'Impossible de charger les flux vidéo.';
		} finally {
			loading = false;
		}

		await titlePromise;
	});
</script>

<svelte:head>
	<title>{title || 'Lecture'} — Sokoul</title>
</svelte:head>

{#if loading}
	<div class="loading">
		<div class="ring"></div>
	</div>
{:else if error || streams.length === 0}
	<div class="error">
		<p>{error || 'Aucun flux disponible pour ce contenu.'}</p>
		<button onclick={() => goto(`/${mediaType}/${tmdbId}`)}>← Retour</button>
	</div>
{:else}
	<Player
		{streams}
		{subtitles}
		{title}
		{mediaType}
		{tmdbId}
		{season}
		{episode}
		onBack={() => goto(`/${mediaType}/${tmdbId}`)}
	/>
{/if}

<style>
	.loading {
		height: 100vh;
		background: #000;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.ring {
		width: 52px;
		height: 52px;
		border-radius: 50%;
		border: 3px solid rgba(255,255,255,0.15);
		border-top-color: #e50914;
		animation: spin 0.8s linear infinite;
	}

	@keyframes spin { to { transform: rotate(360deg); } }

	.error {
		height: 100vh;
		background: #000;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 20px;
		color: rgba(255,255,255,0.7);
		font-size: 15px;
	}

	.error button {
		background: transparent;
		border: 1px solid rgba(255,255,255,0.3);
		color: #fff;
		padding: 10px 24px;
		border-radius: 4px;
		cursor: pointer;
		font-size: 14px;
		transition: border-color 0.2s;
	}

	.error button:hover { border-color: #fff; }
</style>
