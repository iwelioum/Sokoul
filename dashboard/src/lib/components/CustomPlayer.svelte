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

	// ── Playback state ────────────────────────────────────────────────────
	let playing    = $state(false);
	let currentTime = $state(0);
	let duration   = $state(0);
	let buffered   = $state(0);
	let volume     = $state(1);
	let muted      = $state(false);
	let rate       = $state(1);
	let fullscreen = $state(false);
	let waiting    = $state(true);
	let streamIdx  = $state(0);

	// ── Controls visibility ───────────────────────────────────────────────
	let visible  = $state(true);
	let hideTimer: ReturnType<typeof setTimeout>;

	function showControls() {
		visible = true;
		clearTimeout(hideTimer);
		hideTimer = setTimeout(() => { if (playing) visible = false; }, 2800);
	}

	// ── Quality / audio / subtitles ───────────────────────────────────────
	let qualities: { label: string; level: number }[] = $state([]);
	let qualityLevel = $state(-1);
	let audioTracks: { label: string; id: number }[] = $state([]);
	let audioIdx   = $state(0);
	let subIdx     = $state(-1);

	// ── Menus ─────────────────────────────────────────────────────────────
	let menu: 'quality' | 'audio' | 'sub' | 'speed' | 'source' | null = $state(null);
	const speeds = [0.5, 0.75, 1, 1.25, 1.5, 2];

	// ── Derived ───────────────────────────────────────────────────────────
	let progress  = $derived(duration > 0 ? (currentTime / duration) * 100 : 0);
	let buffPct   = $derived(duration > 0 ? (buffered  / duration) * 100 : 0);
	let activeStream = $derived(streams[streamIdx]);

	function pad(n: number) { return String(Math.floor(n)).padStart(2, '0'); }
	function fmt(s: number) {
		if (!isFinite(s) || s < 0) return '0:00';
		const h = Math.floor(s / 3600);
		const m = Math.floor((s % 3600) / 60);
		const ss = Math.floor(s % 60);
		return h > 0 ? `${h}:${pad(m)}:${pad(ss)}` : `${m}:${pad(ss)}`;
	}

	// ── HLS init ──────────────────────────────────────────────────────────
	function destroyHls() {
		if (hls) { hls.destroy(); hls = null; }
	}

	function loadStream(idx: number) {
		if (!video || idx < 0 || idx >= streams.length) return;
		const s = streams[idx];
		streamIdx = idx;
		waiting   = true;
		menu      = null;
		qualities = [];
		audioTracks = [];
		qualityLevel = -1;
		subIdx = -1;
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
				// Auto highest quality
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
				// Auto French
				const fr = d.audioTracks.findIndex((t: any) =>
					t.lang?.startsWith('fr') || t.lang === 'fre'
				);
				if (fr >= 0) { hls!.audioTrack = fr; audioIdx = fr; }
			});

			hls.on(Hls.Events.ERROR, (_e: any, d: any) => {
				if (d.fatal && idx + 1 < streams.length) loadStream(idx + 1);
			});
		} else if (s.stream_type === 'Mp4' || video.canPlayType('video/mp4')) {
			video.src = url;
			video.play().catch(() => {});
		} else {
			// Safari native HLS
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
		muted = volume === 0;
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
		menu = null;
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

	// ── Video events ──────────────────────────────────────────────────────
	function onTimeUpdate() {
		currentTime = video?.currentTime ?? 0;
		duration    = video?.duration ?? 0;
		if (video?.buffered.length) buffered = video.buffered.end(video.buffered.length - 1);
	}

	// ── Mount ─────────────────────────────────────────────────────────────
	onMount(() => {
		loadStream(0);

		function onKey(e: KeyboardEvent) {
			switch (e.key) {
				case ' ': case 'k': e.preventDefault(); togglePlay(); break;
				case 'f': toggleFullscreen(); break;
				case 'Escape':
					if (document.fullscreenElement) document.exitFullscreen();
					else onBack?.();
					break;
				case 'ArrowRight': seek(10); break;
				case 'ArrowLeft':  seek(-10); break;
				case 'ArrowUp':    e.preventDefault(); setVolume(volume + 0.1); break;
				case 'ArrowDown':  e.preventDefault(); setVolume(volume - 0.1); break;
				case 'm': toggleMute(); break;
			}
			showControls();
		}

		window.addEventListener('keydown', onKey);
		document.addEventListener('fullscreenchange', () => {
			fullscreen = !!document.fullscreenElement;
		});

		// Save progress
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

	let qualityLabel = $derived.by(() => {
		if (qualityLevel === -1) return 'Auto';
		return qualities.find(q => q.level === qualityLevel)?.label ?? 'Auto';
	});
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="wrap"
	bind:this={container}
	onmousemove={showControls}
	onmouseleave={() => { if (playing) visible = false; }}
	onclick={(e) => {
		const t = e.target as HTMLElement;
		if (t.closest('.bar') || t.closest('.popup')) return;
		if (menu) { menu = null; return; }
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
		crossorigin="anonymous"
		playsinline
	>
		{#each subtitles as sub, i}
			<track
				kind="subtitles"
				src={sub.url}
				srclang={sub.language}
				label={sub.label}
				default={i === subIdx}
			/>
		{/each}
	</video>

	<!-- Spinner -->
	{#if waiting}
		<div class="spinner"><div class="ring"></div></div>
	{/if}

	<!-- Top bar -->
	<div class="top bar" class:show={visible || !playing}>
		<button class="icon-btn" aria-label="Retour" onclick={() => onBack?.()}>
			<svg viewBox="0 0 24 24" width="22" height="22" fill="white">
				<path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
			</svg>
		</button>
		{#if title}
			<div class="title">
				<span>{title}</span>
				{#if mediaType === 'tv' && season && episode}
					<span class="ep">S{season} · E{episode}</span>
				{/if}
			</div>
		{/if}
	</div>

	<!-- Center big play indicator (brief flash) -->
	{#if !playing && !waiting}
		<div class="center-icon">
			<svg viewBox="0 0 24 24" width="64" height="64" fill="white" opacity="0.9">
				<path d="M8 5v14l11-7z"/>
			</svg>
		</div>
	{/if}

	<!-- Bottom controls -->
	<div class="bottom bar" class:show={visible || !playing}>
		<!-- Progress -->
		<!-- svelte-ignore a11y_click_events_have_key_events -->
		<div class="progress" bind:this={progressEl} onclick={handleProgressClick}>
			<div class="buf"  style="width:{buffPct}%"></div>
			<div class="play" style="width:{progress}%"></div>
			<div class="thumb" style="left:{progress}%"></div>
		</div>

		<!-- Row -->
		<div class="row">
			<!-- Left -->
			<div class="left">
				<!-- Play/Pause -->
				<button class="icon-btn" aria-label={playing ? 'Pause' : 'Lecture'} onclick={togglePlay}>
					{#if playing}
						<svg viewBox="0 0 24 24" width="26" height="26" fill="white"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
					{:else}
						<svg viewBox="0 0 24 24" width="26" height="26" fill="white"><path d="M8 5v14l11-7z"/></svg>
					{/if}
				</button>

				<!-- Skip back -->
				<button class="icon-btn" aria-label="Reculer 10 secondes" onclick={() => seek(-10)}>
					<svg viewBox="0 0 24 24" width="22" height="22" fill="white">
						<path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/>
					</svg>
				</button>

				<!-- Skip forward -->
				<button class="icon-btn" aria-label="Avancer 10 secondes" onclick={() => seek(10)}>
					<svg viewBox="0 0 24 24" width="22" height="22" fill="white">
						<path d="M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8z"/>
					</svg>
				</button>

				<!-- Volume -->
				<button class="icon-btn" aria-label={muted ? 'Activer le son' : 'Couper le son'} onclick={toggleMute}>
					{#if muted || volume === 0}
						<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z"/></svg>
					{:else}
						<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>
					{/if}
				</button>
				<input
					class="vol"
					type="range" min="0" max="1" step="0.05"
					value={muted ? 0 : volume}
					oninput={(e) => setVolume(Number((e.target as HTMLInputElement).value))}
				/>

				<!-- Time -->
				<span class="time">{fmt(currentTime)} / {fmt(duration)}</span>
			</div>

			<!-- Right -->
			<div class="right">
				<!-- Source selector -->
				{#if streams.length > 1}
					<div class="menu-wrap">
						<button class="text-btn" onclick={() => menu = menu === 'source' ? null : 'source'}>
							Source {streamIdx + 1}/{streams.length}
						</button>
						{#if menu === 'source'}
							<div class="popup">
								{#each streams as s, i}
									<button class="item" class:on={i === streamIdx} onclick={() => { loadStream(i); menu = null; }}>
										{s.provider} · {s.quality}
									</button>
								{/each}
							</div>
						{/if}
					</div>
				{/if}

				<!-- Quality -->
				{#if qualities.length > 1}
					<div class="menu-wrap">
						<button class="text-btn" onclick={() => menu = menu === 'quality' ? null : 'quality'}>
							{qualityLabel}
						</button>
						{#if menu === 'quality'}
							<div class="popup">
								<button class="item" class:on={qualityLevel === -1} onclick={() => setQuality(-1)}>Auto</button>
								{#each qualities as q}
									<button class="item" class:on={qualityLevel === q.level} onclick={() => setQuality(q.level)}>{q.label}</button>
								{/each}
							</div>
						{/if}
					</div>
				{/if}

				<!-- Audio -->
				{#if audioTracks.length > 1}
					<div class="menu-wrap">
						<button class="text-btn" aria-label="Piste audio" onclick={() => menu = menu === 'audio' ? null : 'audio'}>
							<svg viewBox="0 0 24 24" width="18" height="18" fill="white"><path d="M12 3v9.28c-.47-.17-.97-.28-1.5-.28C8.01 12 6 14.01 6 16.5S8.01 21 10.5 21c2.31 0 4.2-1.75 4.45-4H15V6h4V3h-7z"/></svg>
						</button>
						{#if menu === 'audio'}
							<div class="popup">
								{#each audioTracks as t}
									<button class="item" class:on={audioIdx === t.id} onclick={() => setAudio(t.id)}>{t.label}</button>
								{/each}
							</div>
						{/if}
					</div>
				{/if}

				<!-- Subtitles -->
				{#if subtitles.length > 0}
					<div class="menu-wrap">
						<button class="text-btn" class:active={subIdx >= 0} onclick={() => menu = menu === 'sub' ? null : 'sub'}>
							CC
						</button>
						{#if menu === 'sub'}
							<div class="popup">
								<button class="item" class:on={subIdx === -1} onclick={() => setSub(-1)}>Désactivé</button>
								{#each subtitles as s, i}
									<button class="item" class:on={subIdx === i} onclick={() => setSub(i)}>{s.label}</button>
								{/each}
							</div>
						{/if}
					</div>
				{/if}

				<!-- Speed -->
				<div class="menu-wrap">
					<button class="text-btn" onclick={() => menu = menu === 'speed' ? null : 'speed'}>{rate}×</button>
					{#if menu === 'speed'}
						<div class="popup">
							{#each speeds as s}
								<button class="item" class:on={rate === s} onclick={() => setSpeed(s)}>{s}×</button>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Fullscreen -->
				<button class="icon-btn" aria-label={fullscreen ? 'Quitter le plein écran' : 'Plein écran'} onclick={toggleFullscreen}>
					{#if fullscreen}
						<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>
					{:else}
						<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
					{/if}
				</button>
			</div>
		</div>
	</div>
</div>

<style>
	/* ── Layout ── */
	.wrap {
		position: fixed;
		inset: 0;
		background: #000;
		cursor: none;
		user-select: none;
	}
	.wrap:hover { cursor: default; }

	video {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}

	/* ── Spinner ── */
	.spinner {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		pointer-events: none;
	}
	.ring {
		width: 52px;
		height: 52px;
		border-radius: 50%;
		border: 3px solid rgba(255,255,255,0.15);
		border-top-color: #fff;
		animation: spin 0.8s linear infinite;
	}
	@keyframes spin { to { transform: rotate(360deg); } }

	/* ── Center icon ── */
	.center-icon {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		pointer-events: none;
		animation: fadeOut 0.6s forwards 0.3s;
	}
	@keyframes fadeOut { to { opacity: 0; } }

	/* ── Bars ── */
	.bar {
		position: absolute;
		left: 0; right: 0;
		z-index: 10;
		opacity: 0;
		transition: opacity 0.25s;
		pointer-events: none;
	}
	.bar.show {
		opacity: 1;
		pointer-events: auto;
	}

	.top {
		top: 0;
		padding: 20px 24px 40px;
		background: linear-gradient(to bottom, rgba(0,0,0,0.75) 0%, transparent 100%);
		display: flex;
		align-items: center;
		gap: 14px;
	}

	.bottom {
		bottom: 0;
		padding: 40px 20px 14px;
		background: linear-gradient(to top, rgba(0,0,0,0.85) 0%, transparent 100%);
	}

	/* ── Title ── */
	.title {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.title span {
		color: #fff;
		font-size: 17px;
		font-weight: 600;
		letter-spacing: 0.01em;
	}
	.ep {
		font-size: 13px !important;
		font-weight: 400 !important;
		color: rgba(255,255,255,0.6) !important;
	}

	/* ── Progress ── */
	.progress {
		position: relative;
		height: 4px;
		background: rgba(255,255,255,0.2);
		border-radius: 2px;
		margin-bottom: 12px;
		cursor: pointer;
		transition: height 0.1s;
	}
	.progress:hover { height: 6px; }
	.buf, .play {
		position: absolute;
		top: 0; left: 0;
		height: 100%;
		border-radius: 2px;
	}
	.buf  { background: rgba(255,255,255,0.25); }
	.play { background: #e50914; }
	.thumb {
		position: absolute;
		top: 50%;
		width: 14px; height: 14px;
		border-radius: 50%;
		background: #fff;
		transform: translate(-50%, -50%);
		opacity: 0;
		transition: opacity 0.1s;
		pointer-events: none;
	}
	.progress:hover .thumb { opacity: 1; }

	/* ── Controls row ── */
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.left, .right {
		display: flex;
		align-items: center;
		gap: 2px;
	}

	/* ── Buttons ── */
	.icon-btn {
		background: none;
		border: none;
		cursor: pointer;
		padding: 7px;
		border-radius: 4px;
		display: flex;
		align-items: center;
		justify-content: center;
		transition: background 0.15s;
	}
	.icon-btn:hover { background: rgba(255,255,255,0.12); }

	.text-btn {
		background: none;
		border: none;
		cursor: pointer;
		color: rgba(255,255,255,0.85);
		font-size: 13px;
		font-weight: 500;
		padding: 6px 10px;
		border-radius: 4px;
		white-space: nowrap;
		transition: background 0.15s, color 0.15s;
	}
	.text-btn:hover { background: rgba(255,255,255,0.12); color: #fff; }
	.text-btn.active { color: #e50914; }

	/* ── Volume ── */
	.vol {
		width: 72px;
		height: 4px;
		accent-color: #fff;
		cursor: pointer;
	}

	/* ── Time ── */
	.time {
		color: rgba(255,255,255,0.75);
		font-size: 13px;
		font-variant-numeric: tabular-nums;
		margin-left: 6px;
		white-space: nowrap;
	}

	/* ── Popup menus ── */
	.menu-wrap { position: relative; }

	.popup {
		position: absolute;
		bottom: calc(100% + 8px);
		right: 0;
		background: rgba(16,16,16,0.96);
		backdrop-filter: blur(12px);
		border: 1px solid rgba(255,255,255,0.08);
		border-radius: 8px;
		padding: 6px;
		min-width: 140px;
		max-height: 280px;
		overflow-y: auto;
		z-index: 30;
	}

	.item {
		display: block;
		width: 100%;
		padding: 9px 14px;
		background: none;
		border: none;
		color: rgba(255,255,255,0.75);
		font-size: 13px;
		text-align: left;
		cursor: pointer;
		border-radius: 5px;
		transition: background 0.12s, color 0.12s;
		white-space: nowrap;
	}
	.item:hover { background: rgba(255,255,255,0.08); color: #fff; }
	.item.on { color: #fff; font-weight: 600; }
	.item.on::before {
		content: '✓ ';
		color: #e50914;
	}

	/* ── Responsive ── */
	@media (max-width: 600px) {
		.vol, .time { display: none; }
		.text-btn { font-size: 11px; padding: 5px 7px; }
	}
</style>
