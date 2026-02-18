<script lang="ts">
	import { getTvChannels, searchTvPrograms } from '$lib/api/client';
	import type { TvChannel, TvProgram } from '$lib/api/client';
	import { onMount } from 'svelte';

	let channels: TvChannel[] = $state([]);
	let filtered: TvChannel[] = $state([]);
	let loading = $state(true);
	let error = $state('');

	let searchQuery = $state('');
	let selectedCategory = $state('');
	let selectedCountry = $state('');
	let showFreeOnly = $state(false);

	let categories: string[] = $state([]);
	let countries: string[] = $state([]);

	let activeChannel: TvChannel | null = $state(null);

	onMount(async () => {
		try {
			channels = await getTvChannels();
			// Extract unique categories and countries
			categories = [...new Set(channels.map(c => c.category).filter(Boolean) as string[])].sort();
			countries = [...new Set(channels.map(c => c.country).filter(Boolean) as string[])].sort();
			applyFilters();
		} catch (e) {
			error = 'Impossible de charger les chaînes TV.';
		} finally {
			loading = false;
		}
	});

	function applyFilters() {
		filtered = channels.filter(c => {
			if (searchQuery && !c.name.toLowerCase().includes(searchQuery.toLowerCase())) return false;
			if (selectedCategory && c.category !== selectedCategory) return false;
			if (selectedCountry && c.country !== selectedCountry) return false;
			if (showFreeOnly && !c.is_free) return false;
			return true;
		});
	}

	$effect(() => {
		searchQuery; selectedCategory; selectedCountry; showFreeOnly;
		applyFilters();
	});

	function playChannel(channel: TvChannel) {
		if (!channel.stream_url) return;
		activeChannel = channel;
	}

	function closePlayer() {
		activeChannel = null;
	}

	const FLAG_MAP: Record<string, string> = {
		FR: '🇫🇷', US: '🇺🇸', GB: '🇬🇧', DE: '🇩🇪', ES: '🇪🇸',
		IT: '🇮🇹', MA: '🇲🇦', DZ: '🇩🇿', TN: '🇹🇳', BE: '🇧🇪',
		CA: '🇨🇦', CH: '🇨🇭', PT: '🇵🇹', NL: '🇳🇱', AR: '🇦🇷',
	};

	function flag(country: string | undefined) {
		if (!country) return '';
		return FLAG_MAP[country.toUpperCase()] ?? country;
	}
</script>

<svelte:head>
	<title>TV en direct — SOKOUL</title>
</svelte:head>

<!-- Inline player overlay -->
{#if activeChannel}
	<div class="player-overlay" role="dialog" aria-label="Lecteur TV">
		<div class="player-header">
			{#if activeChannel.logo_url}
				<img src={activeChannel.logo_url} alt={activeChannel.name} class="player-logo" />
			{/if}
			<span class="player-name">{activeChannel.name}</span>
			<button class="player-close" onclick={closePlayer} aria-label="Fermer">✕</button>
		</div>
		<video
			class="player-video"
			src={activeChannel.stream_url}
			controls
			autoplay
			crossorigin="anonymous"
		>
			Votre navigateur ne supporte pas la lecture vidéo.
		</video>
	</div>
	<div class="overlay-backdrop" role="button" tabindex="-1" onclick={closePlayer} onkeydown={() => {}}></div>
{/if}

<div class="page">
	<div class="page-header">
		<h1>TV en direct</h1>
		{#if !loading}
			<span class="count">{filtered.length} chaîne{filtered.length > 1 ? 's' : ''}</span>
		{/if}
	</div>

	<!-- Filters -->
	<div class="filters">
		<div class="search-wrap">
			<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" class="search-icon">
				<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
			</svg>
			<input
				type="text"
				placeholder="Rechercher une chaîne..."
				bind:value={searchQuery}
				class="search-input"
			/>
			{#if searchQuery}
				<button class="search-clear" onclick={() => searchQuery = ''}>✕</button>
			{/if}
		</div>

		<select bind:value={selectedCategory} class="filter-select">
			<option value="">Toutes les catégories</option>
			{#each categories as cat}
				<option value={cat}>{cat}</option>
			{/each}
		</select>

		<select bind:value={selectedCountry} class="filter-select">
			<option value="">Tous les pays</option>
			{#each countries as c}
				<option value={c}>{flag(c)} {c}</option>
			{/each}
		</select>

		<label class="toggle-free">
			<input type="checkbox" bind:checked={showFreeOnly} />
			Gratuites uniquement
		</label>
	</div>

	{#if loading}
		<div class="grid">
			{#each Array(24) as _}
				<div class="channel-skeleton"></div>
			{/each}
		</div>
	{:else if error}
		<div class="empty-state">
			<p>{error}</p>
		</div>
	{:else if filtered.length === 0}
		<div class="empty-state">
			<svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" style="opacity:0.3">
				<path d="M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z"/>
			</svg>
			<p>Aucune chaîne trouvée</p>
		</div>
	{:else}
		<div class="grid">
			{#each filtered as channel (channel.id)}
				<button
					class="channel-card"
					onclick={() => playChannel(channel)}
					disabled={!channel.stream_url}
					title={channel.stream_url ? `Regarder ${channel.name}` : 'Flux non disponible'}
				>
					<div class="channel-logo-wrap">
						{#if channel.logo_url}
							<img src={channel.logo_url} alt={channel.name} class="channel-logo" loading="lazy" />
						{:else}
							<div class="channel-logo-placeholder">
								<svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
									<path d="M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z"/>
								</svg>
							</div>
						{/if}
						{#if channel.stream_url}
							<div class="play-overlay">
								<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
									<path d="M8 5v14l11-7z"/>
								</svg>
							</div>
						{/if}
					</div>
					<div class="channel-info">
						<span class="channel-name">{channel.name}</span>
						<div class="channel-meta">
							{#if channel.country}
								<span class="channel-country">{flag(channel.country)}</span>
							{/if}
							{#if channel.category}
								<span class="channel-category">{channel.category}</span>
							{/if}
							{#if channel.is_free}
								<span class="badge-free">Gratuit</span>
							{/if}
						</div>
					</div>
				</button>
			{/each}
		</div>
	{/if}
</div>

<style>
	.page {
		padding: 90px calc(3.5vw + 5px) 40px;
	}

	.page-header {
		display: flex;
		align-items: baseline;
		gap: 14px;
		margin-bottom: 24px;
	}

	.page-header h1 {
		font-size: 28px;
		font-weight: 700;
		color: #F9F9F9;
		margin: 0;
	}

	.count {
		font-size: 14px;
		color: #6b7280;
	}

	/* Filters */
	.filters {
		display: flex;
		flex-wrap: wrap;
		gap: 10px;
		margin-bottom: 28px;
		align-items: center;
	}

	.search-wrap {
		position: relative;
		display: flex;
		align-items: center;
		flex: 1;
		min-width: 200px;
		max-width: 320px;
	}

	.search-icon {
		position: absolute;
		left: 12px;
		color: #6b7280;
		pointer-events: none;
	}

	.search-input {
		width: 100%;
		padding: 10px 36px 10px 36px;
		background: rgba(255,255,255,0.06);
		border: 1px solid rgba(255,255,255,0.1);
		border-radius: 10px;
		color: #F9F9F9;
		font-size: 14px;
		outline: none;
		transition: border-color 200ms;
	}

	.search-input:focus {
		border-color: rgba(0,114,210,0.6);
	}

	.search-input::placeholder { color: #6b7280; }

	.search-clear {
		position: absolute;
		right: 10px;
		background: none;
		border: none;
		color: #6b7280;
		cursor: pointer;
		font-size: 14px;
		padding: 2px 4px;
	}

	.filter-select {
		padding: 10px 14px;
		background: rgba(255,255,255,0.06);
		border: 1px solid rgba(255,255,255,0.1);
		border-radius: 10px;
		color: #F9F9F9;
		font-size: 13px;
		cursor: pointer;
		outline: none;
	}

	.filter-select option {
		background: #1a1a2e;
		color: #F9F9F9;
	}

	.toggle-free {
		display: flex;
		align-items: center;
		gap: 8px;
		color: #CACACA;
		font-size: 13px;
		cursor: pointer;
		user-select: none;
	}

	.toggle-free input { cursor: pointer; accent-color: #0072D2; }

	/* Grid */
	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: 16px;
	}

	/* Channel card */
	.channel-card {
		background: rgba(255,255,255,0.04);
		border: 1px solid rgba(255,255,255,0.08);
		border-radius: 12px;
		padding: 0;
		cursor: pointer;
		text-align: left;
		transition: all 200ms;
		overflow: hidden;
		display: flex;
		flex-direction: column;
	}

	.channel-card:hover:not(:disabled) {
		border-color: rgba(0,114,210,0.5);
		background: rgba(0,114,210,0.08);
		transform: translateY(-2px);
		box-shadow: 0 8px 24px rgba(0,0,0,0.3);
	}

	.channel-card:disabled {
		opacity: 0.45;
		cursor: not-allowed;
	}

	.channel-logo-wrap {
		position: relative;
		width: 100%;
		aspect-ratio: 16/9;
		background: rgba(0,0,0,0.4);
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.channel-logo {
		width: 100%;
		height: 100%;
		object-fit: contain;
		padding: 16px;
	}

	.channel-logo-placeholder {
		display: flex;
		align-items: center;
		justify-content: center;
		color: rgba(255,255,255,0.15);
	}

	.play-overlay {
		position: absolute;
		inset: 0;
		background: rgba(0,0,0,0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		opacity: 0;
		transition: opacity 200ms;
		color: #fff;
	}

	.channel-card:hover .play-overlay {
		opacity: 1;
	}

	.channel-info {
		padding: 10px 12px;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}

	.channel-name {
		font-size: 13px;
		font-weight: 600;
		color: #F9F9F9;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.channel-meta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 5px;
	}

	.channel-country {
		font-size: 15px;
	}

	.channel-category {
		font-size: 11px;
		color: #6b7280;
		background: rgba(255,255,255,0.05);
		border-radius: 4px;
		padding: 1px 6px;
	}

	.badge-free {
		font-size: 10px;
		font-weight: 700;
		color: #10b981;
		background: rgba(16,185,129,0.12);
		border: 1px solid rgba(16,185,129,0.25);
		border-radius: 4px;
		padding: 1px 6px;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	/* Skeleton */
	.channel-skeleton {
		border-radius: 12px;
		aspect-ratio: 3/2;
		background: linear-gradient(90deg, rgba(255,255,255,0.04) 25%, rgba(255,255,255,0.08) 50%, rgba(255,255,255,0.04) 75%);
		background-size: 200% 100%;
		animation: shimmer 1.5s infinite;
	}

	@keyframes shimmer {
		0% { background-position: 200% 0; }
		100% { background-position: -200% 0; }
	}

	/* Player overlay */
	.overlay-backdrop {
		position: fixed;
		inset: 0;
		background: rgba(0,0,0,0.8);
		z-index: 100;
	}

	.player-overlay {
		position: fixed;
		top: 50%;
		left: 50%;
		transform: translate(-50%, -50%);
		z-index: 101;
		width: min(900px, 95vw);
		background: #0d0d1a;
		border: 1px solid rgba(255,255,255,0.1);
		border-radius: 16px;
		overflow: hidden;
		box-shadow: 0 24px 80px rgba(0,0,0,0.8);
	}

	.player-header {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 16px;
		background: rgba(255,255,255,0.04);
		border-bottom: 1px solid rgba(255,255,255,0.08);
	}

	.player-logo {
		height: 32px;
		width: auto;
		object-fit: contain;
	}

	.player-name {
		font-size: 15px;
		font-weight: 600;
		color: #F9F9F9;
		flex: 1;
	}

	.player-close {
		background: none;
		border: none;
		color: #6b7280;
		font-size: 18px;
		cursor: pointer;
		padding: 4px 8px;
		border-radius: 6px;
		transition: color 150ms;
	}

	.player-close:hover { color: #F9F9F9; }

	.player-video {
		width: 100%;
		aspect-ratio: 16/9;
		background: #000;
		display: block;
	}

	/* Empty */
	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 16px;
		padding: 80px 20px;
		color: #6b7280;
	}

	.empty-state p { font-size: 16px; margin: 0; }

	@media (max-width: 600px) {
		.grid { grid-template-columns: repeat(2, 1fr); }
		.filters { flex-direction: column; align-items: stretch; }
		.search-wrap { max-width: 100%; }
	}
</style>
