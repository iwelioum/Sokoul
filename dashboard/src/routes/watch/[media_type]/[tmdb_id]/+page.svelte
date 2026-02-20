<script lang="ts">
	import { page } from '$app/state';
	import { goto } from '$app/navigation';

	let { params } = $props();

	const mediaType = $derived(params.media_type as string);
	const tmdbId    = $derived(Number(params.tmdb_id));
	const season    = $derived(Number(page.url.searchParams.get('season')  ?? '1'));
	const episode   = $derived(Number(page.url.searchParams.get('episode') ?? '1'));
	const titleHint = $derived(decodeURIComponent(page.url.searchParams.get('title') ?? ''));

	/* ── Sources ─────────────────────────────────────────────────────────── */
	const THEME = 'D4AF37';

	type SourceKey = 'vidfast' | 'autoembed' | 'vidsrc' | 'multiembed' | 'moviesapi' | 'openvids' | '2embed';

	const SOURCES: { key: SourceKey; label: string; url: string }[] = $derived.by(() => {
		const id = tmdbId;
		const s  = season;
		const e  = episode;
		const tv = mediaType === 'tv';

		return [
			{
				key: 'vidfast',
				label: 'VidFast',
				url: tv
					? `https://vidfast.pro/tv/${id}/${s}/${e}?autoPlay=true&sub=fr&theme=${THEME}&nextButton=true&autoNext=true&title=true&poster=true`
					: `https://vidfast.pro/movie/${id}?autoPlay=true&sub=fr&theme=${THEME}&title=true&poster=true`,
			},
			{
				key: 'autoembed',
				label: 'AutoEmbed',
				url: tv
					? `https://player.autoembed.cc/embed/tv/${id}/${s}/${e}`
					: `https://player.autoembed.cc/embed/movie/${id}`,
			},
			{
				key: 'vidsrc',
				label: 'VidSrc',
				url: tv
					? `https://vidsrc.to/embed/tv/${id}/${s}/${e}`
					: `https://vidsrc.to/embed/movie/${id}`,
			},
			{
				key: 'multiembed',
				label: 'MultiEmbed',
				url: tv
					? `https://multiembed.mov/?video_id=${id}&tmdb=1&s=${s}&e=${e}`
					: `https://multiembed.mov/?video_id=${id}&tmdb=1`,
			},
			{
				key: 'moviesapi',
				label: 'MoviesAPI',
				url: tv
					? `https://moviesapi.club/tv/${id}-${s}-${e}`
					: `https://moviesapi.club/movie/${id}`,
			},
			{
				key: 'openvids',
				label: 'OpenVids',
				url: tv
					? `https://openvids.io/tmdb/episode/${id}-${s}-${e}`
					: `https://openvids.io/tmdb/movie/${id}`,
			},
			{
				key: '2embed',
				label: '2Embed',
				url: tv
					? `https://2embed.cc/embedtv/${id}&s=${s}&e=${e}`
					: `https://2embed.cc/embed/${id}`,
			},
		] satisfies { key: SourceKey; label: string; url: string }[];
	});

	let source     = $state<SourceKey>('vidfast');
	let panelOpen  = $state(false);

	const embedUrl = $derived(SOURCES.find(s => s.key === source)?.url ?? '');

	/* ── PostMessage — save VidFast progress ────────────────────────────── */
	const VIDFAST_ORIGINS = [
		'https://vidfast.pro', 'https://vidfast.in', 'https://vidfast.io',
		'https://vidfast.me',  'https://vidfast.net','https://vidfast.pm',
		'https://vidfast.xyz'
	];

	function onMessage(event: MessageEvent) {
		if (!VIDFAST_ORIGINS.includes(event.origin) || !event.data) return;
		if (event.data.type === 'MEDIA_DATA') {
			try {
				const stored = JSON.parse(localStorage.getItem('vidfast_progress') ?? '{}');
				const key = mediaType === 'tv' ? `t${tmdbId}` : `m${tmdbId}`;
				stored[key] = event.data.data;
				localStorage.setItem('vidfast_progress', JSON.stringify(stored));
			} catch { /* ignore */ }
		}
	}

	$effect(() => {
		window.addEventListener('message', onMessage);
		return () => window.removeEventListener('message', onMessage);
	});

	/* ── Navigation ─────────────────────────────────────────────────────── */
	function back() {
		goto(mediaType === 'tv' ? `/tv/${tmdbId}` : `/movie/${tmdbId}`);
	}

	function prevEpisode() {
		if (episode > 1) {
			goto(`/watch/tv/${tmdbId}?season=${season}&episode=${episode - 1}&title=${encodeURIComponent(titleHint)}`);
		} else if (season > 1) {
			goto(`/watch/tv/${tmdbId}?season=${season - 1}&episode=1&title=${encodeURIComponent(titleHint)}`);
		}
	}

	function nextEpisode() {
		goto(`/watch/tv/${tmdbId}?season=${season}&episode=${episode + 1}&title=${encodeURIComponent(titleHint)}`);
	}

	function selectSource(key: SourceKey) {
		source = key;
		panelOpen = false;
	}
</script>

<svelte:head>
	<title>{titleHint || 'Lecture'} — Sokoul</title>
</svelte:head>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<div class="watch-root">

	<!-- ── Video fills everything ── -->
	<div class="player-wrap">
		{#key embedUrl}
			<iframe
				src={embedUrl}
				title={titleHint || 'Lecteur vidéo'}
				allowfullscreen
				allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
				referrerpolicy="origin"
			></iframe>
		{/key}
	</div>

	<!-- ── Top overlay bar (transparent) ── -->
	<div class="top-bar">
		<!-- Left: back -->
		<button class="btn-back" onclick={back}>
			<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
				<path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
			</svg>
			Retour
		</button>

		<!-- Center: episode nav (TV only) -->
		{#if mediaType === 'tv'}
			<div class="ep-nav">
				<button
					class="btn-ep"
					disabled={season <= 1 && episode <= 1}
					onclick={prevEpisode}
				>←</button>
				<span class="ep-label">S{season} · E{episode}</span>
				<button class="btn-ep" onclick={nextEpisode}>→</button>
			</div>
		{/if}

		<!-- Right: sources toggle -->
		<button
			class="btn-sources"
			class:open={panelOpen}
			onclick={() => panelOpen = !panelOpen}
		>
			<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
				<path d="M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z"/>
			</svg>
			Serveurs
			<span class="chevron" class:rotated={panelOpen}>▸</span>
		</button>
	</div>

	<!-- ── Right panel (transparent backdrop) ── -->
	{#if panelOpen}
		<!-- Click outside to close -->
		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div class="panel-backdrop" onclick={() => panelOpen = false}></div>

		<div class="servers-panel">
			<p class="panel-hint">Si le lecteur ne fonctionne pas,<br>essayez un autre serveur</p>
			<div class="panel-list">
				{#each SOURCES as src, i}
					<button
						class="panel-server"
						class:active={source === src.key}
						onclick={() => selectSource(src.key)}
					>
						<span class="snum">{i + 1}</span>
						<span class="sname">{src.label}</span>
						{#if source === src.key}
							<span class="sactive-dot"></span>
						{/if}
					</button>
				{/each}
			</div>
		</div>
	{/if}

</div>

<style>
	/* ── Root ────────────────────────────────────────────────────────────── */
	.watch-root {
		position: fixed;
		inset: 0;
		background: #000;
		overflow: hidden;
	}

	/* ── Player ──────────────────────────────────────────────────────────── */
	.player-wrap {
		position: absolute;
		inset: 0;
	}

	.player-wrap iframe {
		width: 100%;
		height: 100%;
		border: none;
		display: block;
	}

	/* ── Top bar (transparent) ───────────────────────────────────────────── */
	.top-bar {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		z-index: 20;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 14px 16px;
		background: linear-gradient(to bottom, rgba(0,0,0,0.65) 0%, transparent 100%);
		pointer-events: none; /* let clicks pass through to video by default */
	}

	/* Re-enable pointer events only on buttons */
	.top-bar > * {
		pointer-events: auto;
	}

	.btn-back {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 7px 13px;
		border-radius: 8px;
		border: 1px solid rgba(255, 255, 255, 0.2);
		background: rgba(0, 0, 0, 0.35);
		backdrop-filter: blur(6px);
		color: rgba(255, 255, 255, 0.9);
		font-size: 12px;
		font-weight: 600;
		cursor: pointer;
		white-space: nowrap;
	}
	.btn-back:hover {
		background: rgba(0, 0, 0, 0.55);
		border-color: rgba(255, 255, 255, 0.35);
		color: #fff;
	}

	/* ── Episode nav ─────────────────────────────────────────────────────── */
	.ep-nav {
		display: flex;
		align-items: center;
		gap: 8px;
		margin: 0 auto;
	}

	.ep-label {
		font-size: 12px;
		font-weight: 700;
		color: rgba(255, 255, 255, 0.75);
		white-space: nowrap;
	}

	.btn-ep {
		padding: 6px 10px;
		border-radius: 6px;
		border: 1px solid rgba(255, 255, 255, 0.18);
		background: rgba(0, 0, 0, 0.35);
		backdrop-filter: blur(6px);
		color: rgba(255, 255, 255, 0.75);
		font-size: 13px;
		font-weight: 700;
		cursor: pointer;
	}
	.btn-ep:hover:not(:disabled) {
		background: rgba(0, 0, 0, 0.55);
		color: #fff;
	}
	.btn-ep:disabled {
		opacity: 0.2;
		cursor: default;
	}

	/* ── Sources toggle button ───────────────────────────────────────────── */
	.btn-sources {
		display: inline-flex;
		align-items: center;
		gap: 7px;
		padding: 7px 14px;
		border-radius: 8px;
		border: 1px solid rgba(255, 255, 255, 0.2);
		background: rgba(0, 0, 0, 0.35);
		backdrop-filter: blur(6px);
		color: rgba(255, 255, 255, 0.9);
		font-size: 12px;
		font-weight: 600;
		cursor: pointer;
		white-space: nowrap;
		margin-left: auto;
	}
	.btn-sources:hover,
	.btn-sources.open {
		background: rgba(212, 175, 55, 0.15);
		border-color: #D4AF37;
		color: #D4AF37;
	}

	.chevron {
		font-size: 10px;
		transition: transform 0.2s;
		display: inline-block;
	}
	.chevron.rotated {
		transform: rotate(90deg);
	}

	/* ── Backdrop (transparent click-away) ───────────────────────────────── */
	.panel-backdrop {
		position: absolute;
		inset: 0;
		z-index: 29;
		background: transparent;
	}

	/* ── Right servers panel ─────────────────────────────────────────────── */
	.servers-panel {
		position: absolute;
		top: 0;
		right: 0;
		bottom: 0;
		width: 220px;
		z-index: 30;
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 68px 14px 20px;
		background: transparent;
		pointer-events: auto;
	}

	.panel-hint {
		font-size: 10px;
		color: rgba(255, 255, 255, 0.35);
		line-height: 1.5;
		text-align: center;
		margin: 0 0 6px;
	}

	.panel-list {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.panel-server {
		display: flex;
		align-items: center;
		gap: 10px;
		width: 100%;
		padding: 10px 12px;
		border-radius: 8px;
		border: 1px solid rgba(255, 255, 255, 0.08);
		background: rgba(0, 0, 0, 0.45);
		backdrop-filter: blur(10px);
		color: rgba(255, 255, 255, 0.65);
		font-size: 13px;
		font-weight: 600;
		cursor: pointer;
		text-align: left;
		transition: background 0.15s, color 0.15s, border-color 0.15s;
	}

	.panel-server:hover:not(.active) {
		background: rgba(0, 0, 0, 0.65);
		color: rgba(255, 255, 255, 0.9);
		border-color: rgba(255, 255, 255, 0.18);
	}

	.panel-server.active {
		background: rgba(212, 175, 55, 0.12);
		border-color: rgba(212, 175, 55, 0.5);
		color: #D4AF37;
	}

	.snum {
		font-size: 10px;
		font-weight: 800;
		color: rgba(255, 255, 255, 0.25);
		width: 16px;
		text-align: center;
		flex-shrink: 0;
	}
	.panel-server.active .snum {
		color: rgba(212, 175, 55, 0.6);
	}

	.sname {
		flex: 1;
	}

	.sactive-dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: #D4AF37;
		flex-shrink: 0;
	}

	/* ── Responsive ─────────────────────────────────────────────────────── */
	@media (max-width: 600px) {
		.servers-panel { width: 180px; padding-top: 60px; }
		.top-bar { padding: 10px 12px; }
	}
</style>
