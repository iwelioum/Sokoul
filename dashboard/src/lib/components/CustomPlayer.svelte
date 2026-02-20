<script lang="ts">
	import { onMount } from 'svelte';
	import Hls from 'hls.js';
	import { getProxyUrl } from '$lib/api/client';
	import type { ExtractedStream, SubtitleTrack } from '$lib/api/client';

	let {
		streams,
		subtitles = [],
		title = '',
		mediaType = 'movie',
		tmdbId = 0,
		season,
		episode,
		onBack,
	}: {
		streams: ExtractedStream[];
		subtitles?: SubtitleTrack[];
		title?: string;
		mediaType?: string;
		tmdbId?: number;
		season?: number;
		episode?: number;
		onBack?: () => void;
	} = $props();

	// ── DOM refs ──────────────────────────────────────────────────────────
	let container: HTMLDivElement;
	let video: HTMLVideoElement;
	let hls: Hls | null = null;
	let progressEl: HTMLDivElement;

	// ── Playback state ─────────────────────────────────────────────────────
	let playing     = $state(false);
	let currentTime = $state(0);
	let duration    = $state(0);
	let buffered    = $state(0);
	let volume      = $state(1);
	let muted       = $state(false);
	let rate        = $state(1);
	let fullscreen  = $state(false);
	let waiting     = $state(true);
	let streamIdx   = $state(0);

	// ── Controls visibility ───────────────────────────────────────────────
	let visible   = $state(true);
	let hideTimer: ReturnType<typeof setTimeout>;

	function showControls() {
		visible = true;
		clearTimeout(hideTimer);
		hideTimer = setTimeout(() => { if (playing && !sidebarOpen) visible = false; }, 3000);
	}

	// ── Quality / audio / subtitles ───────────────────────────────────────
	let qualities: { label: string; level: number }[] = $state([]);
	let qualityLevel = $state(-1);
	let audioTracks: { label: string; id: number }[] = $state([]);
	let audioIdx  = $state(0);
	let subIdx    = $state(-1);

	// ── Menus ─────────────────────────────────────────────────────────────
	let menu: 'quality' | 'audio' | 'sub' | 'speed' | 'source' | null = $state(null);
	let sidebarOpen = $state(false);
	const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2];

	// ── Derived ───────────────────────────────────────────────────────────
	let progress     = $derived(duration > 0 ? (currentTime / duration) * 100 : 0);
	let buffPct      = $derived(duration > 0 ? (buffered  / duration) * 100 : 0);
	let activeStream = $derived(streams[streamIdx]);

	// Source classification for sidebar
	function parseQualityPx(q: string): number {
		if (/4k/i.test(q)) return 2160;
		const m = q.match(/(\d+)/);
		return m ? parseInt(m[1]) : 0;
	}
	function classifyLang(s: ExtractedStream): 'vf' | 'vostfr' | 'vo' {
		const lang = (s.audio_lang ?? s.language ?? '').toLowerCase();
		if (s.category === 'local' || lang.startsWith('fr') || lang.includes('french')) return 'vf';
		if (lang.includes('vostfr') || lang.includes('vost-fr') || lang.includes('vost fr')) return 'vostfr';
		if (lang && subtitles.some(sub => sub.language.startsWith('fr'))) return 'vostfr';
		return 'vo';
	}
	let vfStreams     = $derived(streams.filter(s => classifyLang(s) === 'vf').sort((a,b) => parseQualityPx(b.quality) - parseQualityPx(a.quality)));
	let vostfrStreams = $derived(streams.filter(s => classifyLang(s) === 'vostfr').sort((a,b) => parseQualityPx(b.quality) - parseQualityPx(a.quality)));
	let voStreams     = $derived(streams.filter(s => classifyLang(s) === 'vo').sort((a,b) => parseQualityPx(b.quality) - parseQualityPx(a.quality)));
	let sortedSidebarStreams = $derived([...vfStreams, ...vostfrStreams, ...voStreams]);

	let qualityLabel = $derived.by((): string => {
		if (qualityLevel === -1) return 'Auto';
		return qualities.find(q => q.level === qualityLevel)?.label ?? 'Auto';
	});

	function pad(n: number) { return String(Math.floor(n)).padStart(2, '0'); }
	function fmt(s: number) {
		if (!isFinite(s) || s < 0) return '0:00';
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		const ss = Math.floor(s % 60);
		return h > 0 ? `${h}:${pad(m)}:${pad(ss)}` : `${m}:${pad(ss)}`;
	}

	// ── HLS ───────────────────────────────────────────────────────────────
	function destroyHls() {
		if (hls) { hls.destroy(); hls = null; }
	}

	function loadStream(idx: number) {
		if (!video || idx < 0 || idx >= streams.length) return;
		const s = streams[idx];
		streamIdx    = idx;
		waiting      = true;
		menu         = null;
		qualities    = [];
		audioTracks  = [];
		qualityLevel = -1;
		subIdx       = -1;
		destroyHls();

		const url = s.headers && Object.keys(s.headers).length > 0
			? getProxyUrl(s.url, s.headers['Referer'] ?? s.headers['referer'])
			: s.url;

		if (s.stream_type === 'Hls' && Hls.isSupported()) {
			hls = new Hls({ startLevel: -1, enableWorker: true });
			hls.loadSource(url);
			hls.attachMedia(video);

			hls.on(Hls.Events.MANIFEST_PARSED, (_e: any, d: any) => {
				qualities = d.levels.map((l: any, i: number) => ({
					label: l.height ? `${l.height}p` : `Source ${i + 1}`,
					level: i,
				}));
				if (qualities.length) {
					const top = qualities.reduce((a, b) => {
						const ah = d.levels[a.level].height ?? 0;
						const bh = d.levels[b.level].height ?? 0;
						return ah >= bh ? a : b;
					});
					hls!.currentLevel = top.level;
					qualityLevel = top.level;
				}
				video.play().catch(() => {});
			});

			hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, (_e: any, d: any) => {
				audioTracks = d.audioTracks.map((t: any, i: number) => ({
					label: t.name || t.lang || `Piste ${i + 1}`,
					id: i,
				}));
				const fr = d.audioTracks.findIndex((t: any) =>
					t.lang?.startsWith('fr') || t.lang === 'fre'
				);
				if (fr >= 0) { hls!.audioTrack = fr; audioIdx = fr; }
			});

			hls.on(Hls.Events.ERROR, (_e: any, d: any) => {
				if (d.fatal && idx + 1 < streams.length) loadStream(idx + 1);
			});
		} else {
			video.src = url;
			video.play().catch(() => {});
		}
	}

	// ── Controls ──────────────────────────────────────────────────────────
	function togglePlay() {
		if (!video) return;
		video.paused ? video.play().catch(() => {}) : video.pause();
	}

	function seek(delta: number) {
		if (!video) return;
		video.currentTime = Math.max(0, Math.min(duration, video.currentTime + delta));
	}

	function seekTo(pct: number) {
		if (!video || !duration) return;
		video.currentTime = pct * duration;
	}

	function setVolume(v: number) {
		volume = Math.max(0, Math.min(1, v));
		muted  = volume === 0;
		if (video) { video.volume = volume; video.muted = muted; }
	}

	function toggleMute() {
		muted = !muted;
		if (video) video.muted = muted;
	}

	function setQuality(level: number) {
		if (!hls) return;
		hls.currentLevel = level;
		qualityLevel = level;
		menu = null;
	}

	function setAudio(id: number) {
		if (!hls) return;
		hls.audioTrack = id;
		audioIdx = id;
		menu = null;
	}

	function setSub(idx: number) {
		subIdx = idx;
		menu   = null;
		if (!video) return;
		for (let i = 0; i < video.textTracks.length; i++) {
			video.textTracks[i].mode = i === idx ? 'showing' : 'hidden';
		}
	}

	function setSpeed(s: number) {
		rate = s;
		if (video) video.playbackRate = s;
		menu = null;
	}

	function toggleFullscreen() {
		if (!container) return;
		document.fullscreenElement ? document.exitFullscreen() : container.requestFullscreen();
	}

	function handleProgressClick(e: MouseEvent) {
		if (!progressEl || !duration) return;
		const r = progressEl.getBoundingClientRect();
		seekTo(Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)));
	}

	function onTimeUpdate() {
		currentTime = video?.currentTime ?? 0;
		duration    = video?.duration    ?? 0;
		if (video?.buffered.length) buffered = video.buffered.end(video.buffered.length - 1);
	}

	// ── Mount ─────────────────────────────────────────────────────────────
	onMount(() => {
		loadStream(0);

		function onKey(e: KeyboardEvent) {
			switch (e.key) {
				case ' ': case 'k': e.preventDefault(); togglePlay(); break;
				case 'f':           toggleFullscreen(); break;
				case 'Escape':
					if (sidebarOpen) sidebarOpen = false;
					else if (document.fullscreenElement) document.exitFullscreen();
					else onBack?.();
					break;
				case 'ArrowRight': seek(10);  break;
				case 'ArrowLeft':  seek(-10); break;
				case 'ArrowUp':   e.preventDefault(); setVolume(volume + 0.1); break;
				case 'ArrowDown': e.preventDefault(); setVolume(volume - 0.1); break;
				case 'm': toggleMute(); break;
			}
			showControls();
		}

		window.addEventListener('keydown', onKey);
		document.addEventListener('fullscreenchange', () => { fullscreen = !!document.fullscreenElement; });

		const ticker = setInterval(() => {
			if (!video || currentTime < 5) return;
			fetch('/api/watch-history', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('sokoul_token') ?? ''}` },
				body: JSON.stringify({
					tmdb_id: tmdbId, media_type: mediaType, title,
					progress_seconds: Math.floor(currentTime),
					total_seconds: Math.floor(duration),
					completed: duration > 0 && currentTime / duration > 0.9,
				}),
			}).catch(() => {});
		}, 15000);

		return () => {
			destroyHls();
			clearTimeout(hideTimer);
			clearInterval(ticker);
			window.removeEventListener('keydown', onKey);
		};
	});
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="player-wrap"
	bind:this={container}
	onmousemove={showControls}
	onmouseleave={() => { if (playing && !sidebarOpen) visible = false; }}
	onclick={(e) => {
		const t = e.target as HTMLElement;
		if (t.closest('.sources-overlay') || t.closest('.top-bar')) return;
		if (sidebarOpen) { sidebarOpen = false; return; }
		togglePlay();
	}}
>
	<!-- Video -->
	<video
		bind:this={video}
		ontimeupdate={onTimeUpdate}
		onplay={() => { playing = true; showControls(); }}
		onpause={() => { playing = false; visible = true; }}
		onwaiting={() => { waiting = true; }}
		oncanplay={() => { waiting = false; }}
		onerror={() => { if (streamIdx + 1 < streams.length) loadStream(streamIdx + 1); }}
		crossorigin="anonymous"
		playsinline
	>
		{#each subtitles as sub, i}
			<track kind="subtitles" src={sub.url} srclang={sub.language} label={sub.label} default={i === subIdx} />
		{/each}
	</video>

	<!-- Spinner -->
	{#if waiting}
		<div class="spinner-wrap">
			<div class="spinner-ring"></div>
		</div>
	{/if}

	<!-- ── TOP OVERLAY ── -->
	<div class="top-bar" class:show={visible || !playing || sidebarOpen}>
		<button class="btn-top" onclick={() => onBack?.()}>
			<svg viewBox="0 0 24 24" width="18" height="18" fill="white">
				<path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
			</svg>
			Retour
		</button>
		<button class="btn-top" onclick={() => sidebarOpen = !sidebarOpen}>
			<svg viewBox="0 0 24 24" width="18" height="18" fill="white">
				<path d="M4 6h16v2H4zm4 5h12v2H8zm5 5h7v2h-7z"/>
			</svg>
			Lecteurs
		</button>
	</div>

	<!-- ── CENTER PLAY/PAUSE FLASH ── -->
	{#if !playing && !waiting}
		<div class="center-flash">
			<div class="center-flash-ring">
				<svg viewBox="0 0 24 24" width="32" height="32" fill="white"><path d="M8 5v14l11-7z"/></svg>
			</div>
		</div>
	{/if}

	<!-- ── SOURCES OVERLAY ── -->
	{#if sidebarOpen}
		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div class="sources-overlay" onclick={() => sidebarOpen = false}>
			<!-- svelte-ignore a11y_no_static_element_interactions -->
			<div class="sources-panel" onclick={(e) => e.stopPropagation()}>
				<h3 class="sources-title">Lecteurs disponibles</h3>
				<div class="sources-list">
					{#each sortedSidebarStreams as s}
						<button
							class="source-item"
							class:active={s === activeStream}
							onclick={() => { const idx = streams.indexOf(s); if (idx >= 0) loadStream(idx); sidebarOpen = false; }}
						>
							<div class="source-info">
								<span class="source-name">{s.provider}</span>
								<span class="source-detail">{s.quality}{s.audio_lang ? ' · ' + s.audio_lang.toUpperCase() : ''}</span>
							</div>
							{#if s === activeStream}
								<div class="playing-icon">
									<span></span><span></span><span></span>
								</div>
							{/if}
						</button>
					{/each}
					{#if sortedSidebarStreams.length === 0}
						<div class="sources-empty">Aucun lecteur disponible</div>
					{/if}
				</div>
			</div>
		</div>
	{/if}
</div>

<style>
	/* ═══════════════════════════════════════════════════════════
	   LAYOUT
	   ═══════════════════════════════════════════════════════════ */
	.player-wrap {
		position: fixed;
		inset: 0;
		background: #000;
		user-select: none;
		cursor: none;
	}
	.player-wrap:hover { cursor: default; }

	video {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}

	/* ═══════════════════════════════════════════════════════════
	   SPINNER
	   ═══════════════════════════════════════════════════════════ */
	.spinner-wrap {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		pointer-events: none;
	}
	.spinner-ring {
		width: 48px;
		height: 48px;
		border-radius: 50%;
		border: 3px solid rgba(255,255,255,0.08);
		border-top-color: #0072D2;
		animation: spin 0.75s linear infinite;
	}
	@keyframes spin { to { transform: rotate(360deg); } }

	/* ═══════════════════════════════════════════════════════════
	   TOP OVERLAY — transparent, pointer-events: none en permanence
	   Seuls les boutons internes sont cliquables
	   ═══════════════════════════════════════════════════════════ */
	.top-bar {
		position: absolute;
		top: 0; left: 0; right: 0;
		z-index: 10;
		padding: 20px 24px;
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		background: linear-gradient(180deg, rgba(0,0,0,0.5) 0%, transparent 100%);
		pointer-events: none;
		opacity: 0;
		transition: opacity 0.28s;
	}
	.top-bar.show { opacity: 1; }

	.top-bar .btn-top {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 18px;
		border-radius: 10px;
		background: rgba(0,0,0,0.42);
		backdrop-filter: blur(10px);
		-webkit-backdrop-filter: blur(10px);
		border: 1px solid rgba(255,255,255,0.14);
		color: #fff;
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
		flex-shrink: 0;
		pointer-events: auto;
		transition: background 0.18s, border-color 0.18s;
		text-transform: none;
		letter-spacing: normal;
	}
	.top-bar .btn-top:hover { background: rgba(0,0,0,0.65); border-color: rgba(255,255,255,0.28); transform: none; }

	/* ═══════════════════════════════════════════════════════════
	   CENTER FLASH
	   ═══════════════════════════════════════════════════════════ */
	.center-flash {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		pointer-events: none;
		animation: flashOut 0.5s ease forwards 0.25s;
	}
	.center-flash-ring {
		width: 80px;
		height: 80px;
		border-radius: 50%;
		background: rgba(255,255,255,0.12);
		border: 1px solid rgba(255,255,255,0.2);
		backdrop-filter: blur(4px);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	@keyframes flashOut { to { opacity: 0; transform: scale(1.15); } }

	/* ═══════════════════════════════════════════════════════════
	   SOURCES OVERLAY
	   ═══════════════════════════════════════════════════════════ */
	.sources-overlay {
		position: absolute;
		inset: 0;
		z-index: 40;
		background: rgba(0,0,0,0.6);
		display: flex;
		align-items: center;
		justify-content: center;
		animation: fadeIn 0.2s ease;
	}
	@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

	.sources-panel {
		background: rgba(13,17,23,0.95);
		backdrop-filter: blur(20px);
		-webkit-backdrop-filter: blur(20px);
		border: 1px solid rgba(255,255,255,0.1);
		border-radius: 16px;
		padding: 24px;
		min-width: 320px;
		max-width: 420px;
		max-height: 70vh;
		display: flex;
		flex-direction: column;
		box-shadow: 0 20px 60px rgba(0,0,0,0.7);
		animation: panelIn 0.25s cubic-bezier(0.4, 0, 0.2, 1);
	}
	@keyframes panelIn {
		from { opacity: 0; transform: scale(0.95); }
		to   { opacity: 1; transform: scale(1); }
	}

	.sources-title {
		color: #fff;
		font-size: 18px;
		font-weight: 700;
		margin: 0 0 16px;
	}

	.sources-list {
		overflow-y: auto;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.sources-panel .source-item {
		width: 100%;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 12px 16px;
		border-radius: 10px;
		background: rgba(255,255,255,0.03);
		border: 1px solid transparent;
		color: rgba(255,255,255,0.7);
		cursor: pointer;
		transition: all 0.2s;
		text-align: left;
		text-transform: none;
		letter-spacing: normal;
		font-family: inherit;
		font-size: inherit;
	}
	.sources-panel .source-item:hover { background: rgba(255,255,255,0.08); color: #fff; transform: none; }
	.sources-panel .source-item.active { background: rgba(0,114,210,0.12); border-color: rgba(0,114,210,0.3); color: #3b9aef; }

	.source-info { display: flex; flex-direction: column; gap: 2px; }
	.source-name { font-size: 14px; font-weight: 600; }
	.source-detail { font-size: 11px; font-weight: 500; opacity: 0.5; }
	.sources-empty { padding: 40px; text-align: center; color: rgba(255,255,255,0.3); font-size: 13px; }

	/* Playing animation */
	.playing-icon { display: flex; align-items: flex-end; gap: 2px; height: 12px; }
	.playing-icon span { width: 3px; background: #3b9aef; border-radius: 1px; animation: playBounce 0.6s infinite alternate; }
	.playing-icon span:nth-child(2) { animation-delay: 0.2s; }
	.playing-icon span:nth-child(3) { animation-delay: 0.4s; }
	@keyframes playBounce { from { height: 4px; } to { height: 12px; } }

	@media (max-width: 600px) {
		.sources-panel { min-width: 0; width: calc(100% - 32px); margin: 0 16px; }
	}
</style>
