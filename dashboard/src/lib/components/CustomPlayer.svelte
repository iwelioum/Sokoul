<script lang="ts">
	import Hls from 'hls.js';
	import type { ExtractedStream, SubtitleTrack, StreamSource, FrenchSourceGroup } from '$lib/api/client';
	import { getProxyUrl } from '$lib/api/client';
	import { onMount } from 'svelte';

	let {
		streams = [],
		subtitles = [],
		iframeFallbacks = [],
		frenchGroups = [],
		title = '',
		tmdbId = 0,
		mediaType = 'movie',
		season,
		episode,
		initialProgress = 0,
		onBack
	}: {
		streams: ExtractedStream[];
		subtitles: SubtitleTrack[];
		iframeFallbacks: StreamSource[];
		frenchGroups: FrenchSourceGroup[];
		title: string;
		tmdbId: number;
		mediaType: string;
		season?: number;
		episode?: number;
		initialProgress?: number;
		onBack?: () => void;
	} = $props();

	// Refs
	let videoElement: HTMLVideoElement | undefined = $state();
	let playerWrapper: HTMLDivElement | undefined = $state();
	let hlsInstance: Hls | null = $state(null);
	let progressBar: HTMLDivElement | undefined = $state();

	// Playback state
	let isPlaying = $state(false);
	let currentTime = $state(0);
	let duration = $state(0);
	let volume = $state(1);
	let isMuted = $state(false);
	let playbackRate = $state(1);
	let isFullscreen = $state(false);
	let buffered = $state(0);
	let isLoading = $state(true);
	let hasError = $state(false);

	// Controls visibility
	let showControls = $state(true);
	let controlsTimer: ReturnType<typeof setTimeout>;
	let showTitle = $state(true);

	// Source/quality/audio/subtitle selection
	let activeStreamIndex = $state(0);
	let usingIframeFallback = $state(false);
	let iframeFallbackIndex = $state(0);
	let availableQualities: { label: string; height: number; level: number }[] = $state([]);
	let currentQualityLevel = $state(-1); // -1 = auto
	let availableAudioTracks: { label: string; id: number; lang: string }[] = $state([]);
	let currentAudioTrack = $state(0);
	let activeSubtitleIndex = $state(-1); // -1 = off
	let audioTracksChecked = $state(false);
	let hasFrAudioTrack = $state(false);
	let autoSubtitleDone = $state(false);

	function maybeAutoEnableSubtitles() {
		if (autoSubtitleDone) return;
		const st = streams[activeStreamIndex]?.stream_type;
		if (st === 'Hls' && !audioTracksChecked) return;
		if (subtitles.length === 0) {
			autoSubtitleDone = true;
			return;
		}
		if (hasFrAudioTrack) {
			autoSubtitleDone = true;
			return;
		}
		const frSubIdx = subtitles.findIndex(s => s.language === 'fr');
		if (frSubIdx >= 0) setSubtitle(frSubIdx);
		autoSubtitleDone = true;
	}

	// Menus
	let showQualityMenu = $state(false);
	let showAudioMenu = $state(false);
	let showSubtitleMenu = $state(false);
	let showSpeedMenu = $state(false);
	let showSourceMenu = $state(false);

	const speedOptions = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];

	// Derived
	let activeStream: ExtractedStream | undefined = $derived(streams[activeStreamIndex]);
	let playedPercent = $derived(duration > 0 ? (currentTime / duration) * 100 : 0);
	let bufferedPercent = $derived(duration > 0 ? (buffered / duration) * 100 : 0);
	let currentQualityLabel = $derived.by(() => {
		if (currentQualityLevel === -1) return 'Auto';
		const q = availableQualities.find(q => q.level === currentQualityLevel);
		return q ? q.label : 'Auto';
	});

	function formatTime(seconds: number): string {
		if (!isFinite(seconds) || seconds < 0) return '0:00';
		const h = Math.floor(seconds / 3600);
		const m = Math.floor((seconds % 3600) / 60);
		const s = Math.floor(seconds % 60);
		if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
		return `${m}:${s.toString().padStart(2, '0')}`;
	}

	function destroyHls() {
		if (hlsInstance) {
			hlsInstance.destroy();
			hlsInstance = null;
		}
	}

	function initHls(stream: ExtractedStream) {
		destroyHls();
		if (!videoElement) return;

		isLoading = true;
		hasError = false;
		availableQualities = [];
		availableAudioTracks = [];
		audioTracksChecked = false;
		hasFrAudioTrack = false;

		// Build the stream URL — proxy if headers are required
		let streamUrl = stream.url;
		if (stream.headers && Object.keys(stream.headers).length > 0) {
			streamUrl = getProxyUrl(stream.url, stream.headers['Referer'] || stream.headers['referer']);
		}

		if (stream.stream_type === 'Hls' && Hls.isSupported()) {
			const hls = new Hls({
				startLevel: -1,
				maxBufferLength: 30,
				maxMaxBufferLength: 120,
				enableWorker: true,
			});
			hlsInstance = hls;

			hls.loadSource(streamUrl);
			hls.attachMedia(videoElement);

			hls.on(Hls.Events.MANIFEST_PARSED, (_event: any, data: any) => {
				// Populate quality levels
				availableQualities = data.levels.map((level: any, i: number) => ({
					label: level.height ? `${level.height}p` : `Qualité ${i + 1}`,
					height: level.height || 0,
					level: i,
				}));

				// Auto-select highest quality
				if (availableQualities.length > 0) {
					const highest = availableQualities.reduce((a, b) => a.height > b.height ? a : b);
					hls.currentLevel = highest.level;
					currentQualityLevel = highest.level;
				}

				isLoading = false;
				videoElement?.play().catch(() => {});
			});

			hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, (_event: any, data: any) => {
				audioTracksChecked = true;
				availableAudioTracks = data.audioTracks.map((track: any, i: number) => ({
					label: track.name || track.lang || `Piste ${i + 1}`,
					id: i,
					lang: track.lang || '',
				}));

				// Auto-select French audio
				const frIdx = data.audioTracks.findIndex(
					(t: any) => t.lang && (t.lang.startsWith('fr') || t.lang === 'fre')
				);
				hasFrAudioTrack = frIdx >= 0;
				if (frIdx >= 0) {
					hls.audioTrack = frIdx;
					currentAudioTrack = frIdx;
				}

				maybeAutoEnableSubtitles();
			});

			hls.on(Hls.Events.ERROR, (_event: any, data: any) => {
				if (data.fatal) {
					console.error('HLS fatal error:', data);
					hasError = true;
					isLoading = false;
					// Try next stream, or fallback to iframe
					if (activeStreamIndex < streams.length - 1) {
						switchToStream(activeStreamIndex + 1);
					} else if (iframeFallbacks.length > 0) {
						console.warn('All direct streams failed, switching to iframe fallback');
						switchToIframeFallback(0);
					}
				}
			});

			hls.on(Hls.Events.FRAG_BUFFERED, () => {
				isLoading = false;
			});
		} else if (stream.stream_type === 'Mp4') {
			videoElement.src = streamUrl;
			videoElement.onloadeddata = () => {
				isLoading = false;
				videoElement?.play().catch(() => {});
			};
			videoElement.onerror = () => {
				hasError = true;
				isLoading = false;
			};
		} else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
			// Safari native HLS
			videoElement.src = streamUrl;
			videoElement.onloadeddata = () => {
				isLoading = false;
				videoElement?.play().catch(() => {});
			};
		}

		// Resume from saved progress
		if (initialProgress > 0 && videoElement) {
			videoElement.currentTime = initialProgress;
		}
	}

	function switchToStream(index: number) {
		if (index < 0 || index >= streams.length) return;
		activeStreamIndex = index;
		usingIframeFallback = false;
		closeAllMenus();
		if (streams[index]) {
			initHls(streams[index]);
		}
	}

	function switchToIframeFallback(index: number = 0) {
		destroyHls();
		usingIframeFallback = true;
		iframeFallbackIndex = index;
		isLoading = false;
		hasError = false;
		closeAllMenus();
	}

	// Player controls
	function togglePlay() {
		if (!videoElement) return;
		if (videoElement.paused) {
			videoElement.play().catch(() => {});
		} else {
			videoElement.pause();
		}
	}

	function skip(seconds: number) {
		if (!videoElement) return;
		videoElement.currentTime = Math.max(0, Math.min(duration, videoElement.currentTime + seconds));
	}

	function setQuality(level: number) {
		if (!hlsInstance) return;
		hlsInstance.currentLevel = level;
		currentQualityLevel = level;
		showQualityMenu = false;
	}

	function setAudioTrack(id: number) {
		if (!hlsInstance) return;
		hlsInstance.audioTrack = id;
		currentAudioTrack = id;
		showAudioMenu = false;
	}

	function setSubtitle(index: number) {
		activeSubtitleIndex = index;
		showSubtitleMenu = false;
		if (!videoElement) return;
		// Update text tracks
		for (let i = 0; i < videoElement.textTracks.length; i++) {
			videoElement.textTracks[i].mode = i === index ? 'showing' : 'hidden';
		}
	}

	function setSpeed(rate: number) {
		playbackRate = rate;
		if (videoElement) videoElement.playbackRate = rate;
		showSpeedMenu = false;
	}

	function toggleMute() {
		isMuted = !isMuted;
		if (videoElement) videoElement.muted = isMuted;
	}

	function setVolume(val: number) {
		volume = val;
		if (videoElement) {
			videoElement.volume = val;
			isMuted = val === 0;
			videoElement.muted = val === 0;
		}
	}

	function toggleFullscreen() {
		if (!playerWrapper) return;
		if (document.fullscreenElement) {
			document.exitFullscreen();
		} else {
			playerWrapper.requestFullscreen();
		}
	}

	function handleProgressClick(e: MouseEvent) {
		if (!progressBar || !videoElement || !duration) return;
		const rect = progressBar.getBoundingClientRect();
		const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
		videoElement.currentTime = ratio * duration;
	}

	function closeAllMenus() {
		showQualityMenu = false;
		showAudioMenu = false;
		showSubtitleMenu = false;
		showSpeedMenu = false;
		showSourceMenu = false;
	}

	function resetControlsTimer() {
		showControls = true;
		clearTimeout(controlsTimer);
		controlsTimer = setTimeout(() => {
			if (isPlaying) showControls = false;
		}, 3000);
	}

	function handleBack() {
		if (onBack) {
			onBack();
		} else {
			history.back();
		}
	}

	// Video event handlers
	function onTimeUpdate() {
		if (!videoElement) return;
		currentTime = videoElement.currentTime;
		duration = videoElement.duration || 0;

		// Update buffered
		if (videoElement.buffered.length > 0) {
			buffered = videoElement.buffered.end(videoElement.buffered.length - 1);
		}
	}

	function onPlay() { isPlaying = true; }
	function onPause() { isPlaying = false; showControls = true; }
	function onWaiting() { isLoading = true; }
	function onCanPlay() { isLoading = false; }

	// Progress saving (every 15s)
	let lastSavedTime = 0;
	function maybeSaveProgress() {
		if (Math.abs(currentTime - lastSavedTime) < 15) return;
		lastSavedTime = currentTime;
		// Fire and forget — save progress to watch history
		fetch('/api/watch-history', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('sokoul_token') || ''}` },
			body: JSON.stringify({
				tmdb_id: tmdbId,
				media_type: mediaType,
				title,
				progress_seconds: Math.floor(currentTime),
				total_seconds: Math.floor(duration),
				completed: duration > 0 && currentTime / duration > 0.9,
			})
		}).catch(() => {});
	}

	onMount(() => {
		// Initialize with first stream if available
		if (streams.length > 0) {
			initHls(streams[0]);
		} else if (iframeFallbacks.length > 0) {
			switchToIframeFallback(0);
		}

		// Auto-enable FR subtitles if no FR audio track is available
		maybeAutoEnableSubtitles();

		// Hide title after 3s
		const titleTimer = setTimeout(() => { showTitle = false; }, 3000);

		// Keyboard shortcuts
		function handleKeydown(e: KeyboardEvent) {
			if (usingIframeFallback) return;
			switch (e.key) {
				case ' ':
				case 'k':
					e.preventDefault();
					togglePlay();
					break;
				case 'f':
					toggleFullscreen();
					break;
				case 'Escape':
					if (document.fullscreenElement) {
						document.exitFullscreen();
					} else {
						handleBack();
					}
					break;
				case 'ArrowRight':
					skip(10);
					break;
				case 'ArrowLeft':
					skip(-10);
					break;
				case 'ArrowUp':
					e.preventDefault();
					setVolume(Math.min(1, volume + 0.1));
					break;
				case 'ArrowDown':
					e.preventDefault();
					setVolume(Math.max(0, volume - 0.1));
					break;
				case 'm':
					toggleMute();
					break;
			}
			resetControlsTimer();
		}

		function handleFullscreenChange() {
			isFullscreen = !!document.fullscreenElement;
		}

		window.addEventListener('keydown', handleKeydown);
		document.addEventListener('fullscreenchange', handleFullscreenChange);

		// Save progress periodically
		const progressInterval = setInterval(maybeSaveProgress, 15000);

		return () => {
			destroyHls();
			clearTimeout(titleTimer);
			clearTimeout(controlsTimer);
			clearInterval(progressInterval);
			window.removeEventListener('keydown', handleKeydown);
			document.removeEventListener('fullscreenchange', handleFullscreenChange);
		};
	});
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
	class="player-wrapper"
	class:fullscreen={isFullscreen}
	bind:this={playerWrapper}
	onmousemove={resetControlsTimer}
	onclick={(e) => {
		if ((e.target as HTMLElement).closest('.controls-bar, .menu-popup, .top-bar, .source-bar')) return;
		if (!usingIframeFallback) togglePlay();
	}}
>
	{#if !usingIframeFallback}
		<!-- Native video player -->
		<video
			bind:this={videoElement}
			ontimeupdate={onTimeUpdate}
			onplay={onPlay}
			onpause={onPause}
			onwaiting={onWaiting}
			oncanplay={onCanPlay}
			crossorigin="anonymous"
			playsinline
		>
			{#each subtitles as sub, i}
				<track
					kind="subtitles"
					src={sub.url}
					srclang={sub.language}
					label={sub.label}
					default={i === activeSubtitleIndex}
				/>
			{/each}
		</video>

		<!-- Loading spinner -->
		{#if isLoading}
			<div class="loading-overlay">
				<div class="spinner"></div>
			</div>
		{/if}

		<!-- Error state -->
		{#if hasError && streams.length === 0}
			<div class="error-overlay">
				<p>Impossible de charger le flux vidéo</p>
				{#if iframeFallbacks.length > 0}
					<button onclick={() => switchToIframeFallback(0)}>Utiliser un lecteur externe</button>
				{/if}
			</div>
		{/if}
	{:else}
		<!-- Iframe fallback -->
		<iframe
			src={iframeFallbacks[iframeFallbackIndex]?.url || ''}
			allow="fullscreen; autoplay; encrypted-media"
			allowfullscreen
			title="Video Player"
		></iframe>
	{/if}

	<!-- Top bar: title + back -->
	<div class="top-bar" class:visible={showControls || !isPlaying || usingIframeFallback}>
		<button class="back-btn" onclick={handleBack}>
			<svg viewBox="0 0 24 24" width="24" height="24" fill="white"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
		</button>
		{#if title}
			<div class="title-text">
				<h2>{title}</h2>
				{#if mediaType === 'tv' && season && episode}
					<span>S{season} E{episode}</span>
				{/if}
			</div>
		{/if}
	</div>

	<!-- Bottom controls -->
	{#if !usingIframeFallback}
		<div class="controls-bar" class:visible={showControls || !isPlaying}>
			<!-- Progress bar -->
			<!-- svelte-ignore a11y_click_events_have_key_events -->
			<div class="progress-container" bind:this={progressBar} onclick={handleProgressClick}>
				<div class="progress-buffered" style="width: {bufferedPercent}%"></div>
				<div class="progress-played" style="width: {playedPercent}%"></div>
				<div class="progress-thumb" style="left: {playedPercent}%"></div>
			</div>

			<div class="controls-row">
				<div class="controls-left">
					<button class="ctrl-btn" onclick={togglePlay} title={isPlaying ? 'Pause (K)' : 'Lecture (K)'}>
						{#if isPlaying}
							<svg viewBox="0 0 24 24" width="28" height="28" fill="white"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
						{:else}
							<svg viewBox="0 0 24 24" width="28" height="28" fill="white"><path d="M8 5v14l11-7z"/></svg>
						{/if}
					</button>

					<button class="ctrl-btn" onclick={() => skip(-10)} title="Reculer 10s">
						<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"/></svg>
					</button>

					<button class="ctrl-btn" onclick={() => skip(10)} title="Avancer 10s">
						<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8z"/></svg>
					</button>

					<!-- Volume -->
					<button class="ctrl-btn" onclick={toggleMute} title="Muet (M)">
						{#if isMuted || volume === 0}
							<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>
						{:else if volume < 0.5}
							<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z"/></svg>
						{:else}
							<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/></svg>
						{/if}
					</button>
					<input
						type="range" min="0" max="1" step="0.05"
						value={isMuted ? 0 : volume}
						oninput={(e) => setVolume(Number((e.target as HTMLInputElement).value))}
						class="volume-slider"
					/>

					<span class="time-display">
						{formatTime(currentTime)} / {formatTime(duration)}
					</span>
				</div>

				<div class="controls-right">
					<!-- Source selector -->
					{#if streams.length > 0 || iframeFallbacks.length > 0}
						<div class="menu-wrapper">
							<button class="ctrl-btn text-btn source-indicator" onclick={() => { closeAllMenus(); showSourceMenu = !showSourceMenu; }}>
								<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14zM9.41 15.95L12 13.36l2.59 2.59L16 14.54l-4-4-4 4z"/></svg>
								{activeStream?.provider || iframeFallbacks[iframeFallbackIndex]?.name || 'Source'}
							</button>
							{#if showSourceMenu}
								<div class="menu-popup source-popup">
									{#if streams.length > 0}
										<div class="menu-section-label">Lecteur natif</div>
										{#each streams as stream, i}
											<button
												class="menu-item"
												class:active={!usingIframeFallback && activeStreamIndex === i}
												onclick={() => switchToStream(i)}
											>
												<span class="stream-badge native">▶</span>
												{stream.provider} ({stream.quality})
											</button>
										{/each}
									{/if}
									{#if frenchGroups.length > 0}
										{#each frenchGroups as group}
											<div class="menu-divider"></div>
											<div class="menu-section-label">{group.category}</div>
											{#each group.sources as source}
												<button
													class="menu-item"
													class:active={usingIframeFallback && iframeFallbacks[iframeFallbackIndex]?.url === source.url}
													onclick={() => {
														const idx = iframeFallbacks.findIndex(f => f.url === source.url);
														if (idx >= 0) switchToIframeFallback(idx);
													}}
												>
													<span class="stream-badge french">FR</span>
													{source.name}
													{#if source.language}
														<span class="lang-badge" class:vf={source.language === 'VF'} class:vostfr={source.language === 'VOSTFR'}>
															{source.language}
														</span>
													{/if}
												</button>
											{/each}
										{/each}
									{/if}
									{#if iframeFallbacks.length > 0}
										{#if streams.length > 0 || frenchGroups.length > 0}<div class="menu-divider"></div>{/if}
										<div class="menu-section-label">International</div>
										{#each iframeFallbacks.filter(f => !f.category || f.category === 'International') as fallback}
											{@const idx = iframeFallbacks.indexOf(fallback)}
											<button
												class="menu-item"
												class:active={usingIframeFallback && iframeFallbackIndex === idx}
												onclick={() => switchToIframeFallback(idx)}
											>
												<span class="stream-badge external">⧉</span>
												{fallback.name}
											</button>
										{/each}
									{/if}
								</div>
							{/if}
						</div>
					{/if}

					<!-- Quality -->
					{#if availableQualities.length > 1}
						<div class="menu-wrapper">
							<button class="ctrl-btn text-btn" onclick={() => { closeAllMenus(); showQualityMenu = !showQualityMenu; }}>
								{currentQualityLabel}
							</button>
							{#if showQualityMenu}
								<div class="menu-popup">
									<button class="menu-item" class:active={currentQualityLevel === -1} onclick={() => setQuality(-1)}>
										Auto
									</button>
									{#each availableQualities as q}
										<button class="menu-item" class:active={currentQualityLevel === q.level} onclick={() => setQuality(q.level)}>
											{q.label}
										</button>
									{/each}
								</div>
							{/if}
						</div>
					{/if}

					<!-- Audio tracks -->
					{#if availableAudioTracks.length > 1}
						<div class="menu-wrapper">
							<button class="ctrl-btn text-btn" onclick={() => { closeAllMenus(); showAudioMenu = !showAudioMenu; }}>
								<svg viewBox="0 0 24 24" width="20" height="20" fill="white"><path d="M12 3v9.28c-.47-.17-.97-.28-1.5-.28C8.01 12 6 14.01 6 16.5S8.01 21 10.5 21c2.31 0 4.2-1.75 4.45-4H15V6h4V3h-7z"/></svg>
							</button>
							{#if showAudioMenu}
								<div class="menu-popup">
									{#each availableAudioTracks as track}
										<button class="menu-item" class:active={currentAudioTrack === track.id} onclick={() => setAudioTrack(track.id)}>
											{track.label}
										</button>
									{/each}
								</div>
							{/if}
						</div>
					{/if}

					<!-- Subtitles -->
					{#if subtitles.length > 0}
						<div class="menu-wrapper">
							<button class="ctrl-btn text-btn" onclick={() => { closeAllMenus(); showSubtitleMenu = !showSubtitleMenu; }}
								class:active-indicator={activeSubtitleIndex >= 0}
							>
								CC
							</button>
							{#if showSubtitleMenu}
								<div class="menu-popup">
									<button class="menu-item" class:active={activeSubtitleIndex === -1} onclick={() => setSubtitle(-1)}>
										Désactivé
									</button>
									{#each subtitles as sub, i}
										<button class="menu-item" class:active={activeSubtitleIndex === i} onclick={() => setSubtitle(i)}>
											{sub.label}
										</button>
									{/each}
								</div>
							{/if}
						</div>
					{/if}

					<!-- Speed -->
					<div class="menu-wrapper">
						<button class="ctrl-btn text-btn" onclick={() => { closeAllMenus(); showSpeedMenu = !showSpeedMenu; }}>
							{playbackRate}x
						</button>
						{#if showSpeedMenu}
							<div class="menu-popup">
								{#each speedOptions as speed}
									<button class="menu-item" class:active={playbackRate === speed} onclick={() => setSpeed(speed)}>
										{speed}x
									</button>
								{/each}
							</div>
						{/if}
					</div>

					<!-- Fullscreen -->
					<button class="ctrl-btn" onclick={toggleFullscreen} title="Plein écran (F)">
						{#if isFullscreen}
							<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>
						{:else}
							<svg viewBox="0 0 24 24" width="22" height="22" fill="white"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
						{/if}
					</button>
				</div>
			</div>
		</div>
	{:else}
		<!-- Iframe source bar — always visible -->
		<div class="iframe-source-bar visible">
			<button class="back-btn-iframe" onclick={handleBack} title="Retour">
				<svg viewBox="0 0 24 24" width="20" height="20" fill="white"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>
			</button>
			{#if streams.length > 0}
				<button class="iframe-switch-btn gold" onclick={() => switchToStream(0)}>
					▶ Lecteur natif
				</button>
			{/if}
			<div class="source-buttons">
				{#if frenchGroups.length > 0}
					{#each frenchGroups as group}
						<span class="group-label">{group.category}</span>
						{#each group.sources as source}
							{@const idx = iframeFallbacks.findIndex(f => f.url === source.url)}
							<button
								class="source-btn french"
								class:active={iframeFallbackIndex === idx}
								onclick={() => { if (idx >= 0) iframeFallbackIndex = idx; }}
							>
								{source.name}
								{#if source.language}
									<span class="lang-tag" class:vf={source.language === 'VF'} class:vostfr={source.language === 'VOSTFR'}>{source.language}</span>
								{/if}
							</button>
						{/each}
					{/each}
					<span class="group-label">Int.</span>
				{/if}
				{#each iframeFallbacks.filter(f => !f.category || f.category === 'International') as fb}
					{@const i = iframeFallbacks.indexOf(fb)}
					<button
						class="source-btn"
						class:active={iframeFallbackIndex === i}
						onclick={() => { iframeFallbackIndex = i; }}
					>
						{fb.name}
					</button>
				{/each}
			</div>
		</div>
	{/if}
</div>

<style>
	.player-wrapper {
		position: relative;
		width: 100%;
		height: 100vh;
		background: #000;
		overflow: hidden;
		cursor: none;
	}

	.player-wrapper:hover {
		cursor: default;
	}

	video {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}

	iframe {
		width: 100%;
		height: 100%;
		border: none;
	}

	/* Loading & error overlays */
	.loading-overlay, .error-overlay {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
		z-index: 5;
		pointer-events: none;
	}

	.error-overlay {
		pointer-events: auto;
		background: rgba(0,0,0,0.8);
		color: #fff;
	}

	.error-overlay button {
		background: #c8a44e;
		color: #000;
		border: none;
		padding: 10px 20px;
		border-radius: 6px;
		cursor: pointer;
		font-weight: 600;
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

	/* Top bar */
	.top-bar {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		padding: 16px 20px;
		display: flex;
		align-items: center;
		gap: 12px;
		background: linear-gradient(to bottom, rgba(0,0,0,0.7), transparent);
		z-index: 10;
		opacity: 0;
		transition: opacity 0.3s;
		pointer-events: none;
	}

	.top-bar.visible {
		opacity: 1;
		pointer-events: auto;
	}

	.back-btn {
		background: none;
		border: none;
		cursor: pointer;
		padding: 4px;
		border-radius: 50%;
		transition: background 0.2s;
	}

	.back-btn:hover {
		background: rgba(255,255,255,0.15);
	}

	.title-text h2 {
		color: #fff;
		font-size: 18px;
		margin: 0;
		font-weight: 600;
	}

	.title-text span {
		color: rgba(255,255,255,0.7);
		font-size: 14px;
	}

	/* Controls bar */
	.controls-bar {
		position: absolute;
		bottom: 0;
		left: 0;
		right: 0;
		padding: 0 16px 12px;
		background: linear-gradient(to top, rgba(0,0,0,0.85), transparent);
		z-index: 10;
		opacity: 0;
		transition: opacity 0.3s;
		pointer-events: none;
	}

	.controls-bar.visible {
		opacity: 1;
		pointer-events: auto;
	}

	/* Progress bar */
	.progress-container {
		position: relative;
		height: 4px;
		background: rgba(255,255,255,0.2);
		border-radius: 2px;
		cursor: pointer;
		margin-bottom: 10px;
		transition: height 0.1s;
	}

	.progress-container:hover {
		height: 8px;
	}

	.progress-buffered {
		position: absolute;
		top: 0;
		left: 0;
		height: 100%;
		background: rgba(255,255,255,0.3);
		border-radius: 2px;
	}

	.progress-played {
		position: absolute;
		top: 0;
		left: 0;
		height: 100%;
		background: #c8a44e;
		border-radius: 2px;
	}

	.progress-thumb {
		position: absolute;
		top: 50%;
		width: 14px;
		height: 14px;
		background: #c8a44e;
		border-radius: 50%;
		transform: translate(-50%, -50%);
		opacity: 0;
		transition: opacity 0.1s;
	}

	.progress-container:hover .progress-thumb {
		opacity: 1;
	}

	/* Controls row */
	.controls-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.controls-left, .controls-right {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	.ctrl-btn {
		background: none;
		border: none;
		cursor: pointer;
		padding: 6px;
		border-radius: 4px;
		display: flex;
		align-items: center;
		justify-content: center;
		transition: background 0.15s;
	}

	.ctrl-btn:hover {
		background: rgba(255,255,255,0.15);
	}

	.text-btn {
		color: #fff;
		font-size: 13px;
		font-weight: 500;
		padding: 4px 8px;
		white-space: nowrap;
	}

	.active-indicator {
		color: #c8a44e;
	}

	.volume-slider {
		width: 70px;
		height: 4px;
		accent-color: #c8a44e;
		cursor: pointer;
	}

	.time-display {
		color: rgba(255,255,255,0.8);
		font-size: 13px;
		font-variant-numeric: tabular-nums;
		margin-left: 8px;
		white-space: nowrap;
	}

	/* Menu popups */
	.menu-wrapper {
		position: relative;
	}

	.menu-popup {
		position: absolute;
		bottom: 100%;
		right: 0;
		background: rgba(20, 20, 30, 0.95);
		backdrop-filter: blur(10px);
		border-radius: 8px;
		padding: 6px;
		min-width: 150px;
		max-height: 300px;
		overflow-y: auto;
		border: 1px solid rgba(255,255,255,0.1);
		z-index: 20;
	}

	.menu-item {
		display: block;
		width: 100%;
		padding: 8px 12px;
		background: none;
		border: none;
		color: rgba(255,255,255,0.8);
		font-size: 13px;
		text-align: left;
		cursor: pointer;
		border-radius: 4px;
		transition: background 0.15s;
	}

	.menu-item:hover {
		background: rgba(255,255,255,0.1);
	}

	.menu-item.active {
		color: #c8a44e;
		font-weight: 600;
	}

	.menu-divider {
		height: 1px;
		background: rgba(255,255,255,0.1);
		margin: 4px 0;
	}

	.menu-section-label {
		padding: 4px 12px 2px;
		font-size: 10px;
		text-transform: uppercase;
		letter-spacing: 0.5px;
		color: rgba(255,255,255,0.4);
		font-weight: 600;
	}

	.source-popup {
		min-width: 200px;
	}

	.source-indicator {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	.stream-badge {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 18px;
		height: 18px;
		border-radius: 4px;
		font-size: 10px;
		margin-right: 4px;
		flex-shrink: 0;
	}

	.stream-badge.native {
		background: rgba(200, 164, 78, 0.25);
		color: #c8a44e;
	}

	.stream-badge.external {
		background: rgba(255, 255, 255, 0.1);
		color: rgba(255, 255, 255, 0.5);
	}

	.stream-badge.french {
		background: rgba(59, 130, 246, 0.25);
		color: #60a5fa;
	}

	.lang-badge, .lang-tag {
		font-size: 9px;
		padding: 1px 5px;
		border-radius: 3px;
		margin-left: 4px;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.3px;
	}

	.lang-badge.vf, .lang-tag.vf {
		background: rgba(59, 130, 246, 0.3);
		color: #93bbfc;
	}

	.lang-badge.vostfr, .lang-tag.vostfr {
		background: rgba(168, 85, 247, 0.3);
		color: #c4b5fd;
	}

	.quality-tag {
		font-size: 9px;
		color: rgba(255,255,255,0.4);
		margin-left: auto;
		padding-left: 8px;
	}

	.group-label {
		font-size: 10px;
		color: rgba(255,255,255,0.4);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.5px;
		padding: 2px 6px;
		white-space: nowrap;
	}

	.source-btn.french {
		border-color: rgba(59, 130, 246, 0.3);
	}

	.source-btn.french.active {
		background: rgba(59, 130, 246, 0.25);
		color: #60a5fa;
		border-color: #60a5fa;
	}

	/* Iframe source bar */
	.iframe-source-bar {
		position: absolute;
		bottom: 0;
		left: 0;
		right: 0;
		padding: 10px 16px;
		background: linear-gradient(to top, rgba(0,0,0,0.95), rgba(0,0,0,0.7));
		display: flex;
		align-items: center;
		gap: 10px;
		z-index: 10;
		opacity: 0;
		transition: opacity 0.3s;
	}

	.iframe-source-bar.visible {
		opacity: 1;
	}

	.back-btn-iframe {
		background: none;
		border: none;
		cursor: pointer;
		padding: 6px;
		border-radius: 50%;
		transition: background 0.2s;
	}

	.back-btn-iframe:hover {
		background: rgba(255,255,255,0.15);
	}

	.iframe-switch-btn.gold {
		background: #c8a44e;
		color: #000;
		border: none;
		padding: 6px 14px;
		border-radius: 6px;
		cursor: pointer;
		font-weight: 600;
		font-size: 13px;
		white-space: nowrap;
		transition: background 0.2s;
	}

	.iframe-switch-btn.gold:hover {
		background: #dbb85e;
	}

	.source-buttons {
		display: flex;
		align-items: center;
		gap: 6px;
		flex-wrap: wrap;
		flex: 1;
		overflow-x: auto;
	}

	.source-btn {
		background: rgba(255,255,255,0.08);
		color: rgba(255,255,255,0.75);
		border: 1px solid rgba(255,255,255,0.15);
		padding: 5px 12px;
		border-radius: 6px;
		cursor: pointer;
		font-size: 12px;
		white-space: nowrap;
		transition: all 0.2s;
	}

	.source-btn:hover {
		background: rgba(255,255,255,0.15);
		color: #fff;
	}

	.source-btn.active {
		background: rgba(200, 164, 78, 0.25);
		color: #c8a44e;
		border-color: #c8a44e;
		font-weight: 600;
	}

	/* Responsive */
	@media (max-width: 640px) {
		.controls-left .volume-slider {
			display: none;
		}

		.time-display {
			font-size: 11px;
		}

		.text-btn {
			font-size: 11px;
			padding: 4px 5px;
		}
	}
</style>
