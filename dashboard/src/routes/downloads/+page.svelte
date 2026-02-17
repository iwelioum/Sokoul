<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import {
		createMedia,
		directSearch,
		startDownload,
		listDownloads,
		isLoggedIn,
		formatBytes
	} from '$lib/api/client';
	import type { SearchResult, Task } from '$lib/api/client';

	// URL parameters
	let query = $state('');
	let tmdbId = $state<number | null>(null);
	let mediaType = $state<'movie' | 'tv'>('movie');
	let mediaId = $state<string | null>(null);

	// Search results state
	let searchResults: SearchResult[] = $state([]);
	let searching = $state(false);
	let searchError = $state('');
	let selectedTorrent: SearchResult | null = $state(null);
	
	// Downloads state
	let downloads: Task[] = $state([]);
	let loadingDownloads = $state(true);
	let startingDownload = $state(false);

	let downloadsTimer: ReturnType<typeof setInterval> | null = null;
	let initialized = false;

	// Initialize from URL parameters
	$effect(() => {
		const params = $page.url.searchParams;
		query = params.get('query') || '';
		tmdbId = params.get('tmdbId') ? Number(params.get('tmdbId')) : null;
		mediaType = params.get('mediaType') === 'tv' ? 'tv' : 'movie';
		
		if (query && tmdbId && !initialized) {
			initialized = true;
			initializeDownload();
		}
	});

	async function initializeDownload() {
		if (!isLoggedIn()) {
			searchError = 'Connexion requise pour télécharger.';
			return;
		}

		searching = true;
		searchError = '';
		searchResults = [];

		try {
			// Step 1: Create or get media in database
			const media = await createMedia({
				title: query,
				media_type: mediaType,
				tmdb_id: tmdbId!
			});
			mediaId = media.id;
			
			// Step 2: Search Prowlarr/Jackett directly (synchronous)
			const response = await directSearch(query, mediaId);
			searchResults = response.results;
			
			if (searchResults.length === 0) {
				searchError = 'Aucun torrent trouvé pour ce titre. Essaie un autre terme de recherche.';
			}
		} catch (e) {
			searchError = e instanceof Error ? e.message : 'Erreur lors de la recherche';
		} finally {
			searching = false;
		}
	}

	async function retrySearch() {
		if (!query || !mediaId) return;
		searching = true;
		searchError = '';
		searchResults = [];
		
		try {
			const response = await directSearch(query, mediaId);
			searchResults = response.results;
			if (searchResults.length === 0) {
				searchError = 'Aucun torrent trouvé. Essaie un autre terme.';
			}
		} catch (e) {
			searchError = e instanceof Error ? e.message : 'Erreur lors de la recherche';
		} finally {
			searching = false;
		}
	}

	async function loadDownloads() {
		if (!isLoggedIn()) {
			downloads = [];
			loadingDownloads = false;
			return;
		}

		try {
			downloads = await listDownloads();
		} catch (e) {
			downloads = [];
		}
		loadingDownloads = false;
	}

	function selectTorrent(result: SearchResult) {
		selectedTorrent = result;
	}

	function cancelSelection() {
		selectedTorrent = null;
	}

	async function confirmDownload() {
		if (!selectedTorrent || !mediaId || startingDownload) return;
		
		startingDownload = true;
		try {
			await startDownload({
				media_id: mediaId,
				search_result_id: selectedTorrent.id
			});
			
			alert('✅ Téléchargement démarré avec succès !');
			selectedTorrent = null;
			await loadDownloads();
			
		} catch (e) {
			const msg = e instanceof Error ? e.message : 'Erreur inconnue';
			alert(`❌ Échec du téléchargement : ${msg}`);
		} finally {
			startingDownload = false;
		}
	}

	onMount(() => {
		loadDownloads();
		downloadsTimer = setInterval(loadDownloads, 5000);
		
		return () => {
			if (downloadsTimer) clearInterval(downloadsTimer);
		};
	});
</script>

<svelte:head>
	<title>Téléchargements — {query || 'Sokoul'}</title>
</svelte:head>

<div class="downloads-page">
	<div class="container">
		<h1>⬇️ Téléchargement de "{query}"</h1>
		<p class="subtitle">{mediaType === 'tv' ? '📺 Série TV' : '🎬 Film'} · TMDB ID: {tmdbId}</p>

		{#if searchError}
			<div class="error-box">
				<p>{searchError}</p>
				<button class="btn btn-secondary" onclick={retrySearch} style="margin-top: 1rem;">
					🔄 Réessayer la recherche
				</button>
			</div>
		{/if}

		<!-- Torrent Selection -->
		<section class="torrents-section">
			<h2>📦 Torrents disponibles {searchResults.length > 0 ? `(${searchResults.length})` : ''}</h2>
			
			{#if searching}
				<div class="loading-box">
					<div class="spinner"></div>
					<p>⏳ Recherche de torrents en cours...</p>
					<p class="info-text">Interrogation de Prowlarr et Jackett en temps réel...</p>
				</div>
			{:else if searchResults.length === 0 && !searchError}
				<div class="info-box">
					<p>🔍 Aucun torrent trouvé pour le moment.</p>
					<p class="info-text">Clique sur le bouton ci-dessous pour lancer une recherche.</p>
					{#if mediaId}
						<button class="btn btn-primary" onclick={retrySearch} style="margin-top: 1rem;">
							🔍 Lancer la recherche
						</button>
					{/if}
				</div>
			{:else}
				<div class="results-grid">
					{#each searchResults as result (result.id)}
						<button 
							class="torrent-card" 
							class:selected={selectedTorrent?.id === result.id}
							onclick={() => selectTorrent(result)}
						>
							<div class="torrent-header">
								<h3 class="torrent-title">{result.title}</h3>
								{#if selectedTorrent?.id === result.id}
									<span class="selected-badge">✓ Sélectionné</span>
								{/if}
							</div>
							
							<div class="torrent-meta">
								<span class="meta-item">🏷️ {result.provider}</span>
								<span class="meta-item">📊 {result.quality || result.protocol.toUpperCase()}</span>
								<span class="meta-item">💾 {formatBytes(result.size_bytes)}</span>
							</div>
							
							<div class="torrent-stats">
								<span class="stat-item stat-seeders">↑ {result.seeders} seeders</span>
								<span class="stat-item stat-leechers">↓ {result.leechers} leechers</span>
								{#if result.score}
									<span class="stat-item stat-score">⭐ {result.score}/100</span>
								{/if}
							</div>
						</button>
					{/each}
				</div>
			{/if}
		</section>

		<!-- Confirmation Panel (appears when torrent selected) -->
		{#if selectedTorrent}
			<section class="confirmation-panel">
				<h3>📋 Confirmation du téléchargement</h3>
				
				<div class="selected-details">
					<p class="detail-title"><strong>Fichier sélectionné :</strong></p>
					<p class="detail-value">{selectedTorrent.title}</p>
					
					<div class="details-grid">
						<div class="detail-item">
							<span class="label">Provider :</span>
							<span class="value">{selectedTorrent.provider}</span>
						</div>
						<div class="detail-item">
							<span class="label">Qualité :</span>
							<span class="value">{selectedTorrent.quality || 'N/A'}</span>
						</div>
						<div class="detail-item">
							<span class="label">Taille :</span>
							<span class="value">{formatBytes(selectedTorrent.size_bytes)}</span>
						</div>
						<div class="detail-item">
							<span class="label">Seeders :</span>
							<span class="value">{selectedTorrent.seeders}</span>
						</div>
						<div class="detail-item">
							<span class="label">Protocol :</span>
							<span class="value">{selectedTorrent.protocol.toUpperCase()}</span>
						</div>
						{#if selectedTorrent.score}
							<div class="detail-item">
								<span class="label">Score :</span>
								<span class="value">{selectedTorrent.score}/100</span>
							</div>
						{/if}
					</div>
				</div>

				<div class="action-buttons">
					<button class="btn btn-secondary" onclick={cancelSelection}>
						Annuler
					</button>
					<button 
						class="btn btn-primary" 
						onclick={confirmDownload}
						disabled={startingDownload}
					>
						{startingDownload ? '⏳ Démarrage...' : '✅ Confirmer le téléchargement'}
					</button>
				</div>
			</section>
		{/if}

		<!-- Active Downloads -->
		<section class="downloads-section">
			<h2>📥 Téléchargements actifs ({downloads.length})</h2>
			
			{#if loadingDownloads}
				<p class="info-text">Chargement...</p>
			{:else if downloads.length === 0}
				<p class="info-text">Aucun téléchargement en cours.</p>
			{:else}
				<div class="downloads-list">
					{#each downloads as dl (dl.id)}
						{@const payload = dl.payload as Record<string, unknown> | null}
						{@const title = typeof payload?.title === 'string' ? payload.title : `Task ${dl.id.slice(0, 8)}`}
						<div class="download-item">
							<div class="download-info">
								<p class="download-title">{title}</p>
								<p class="download-meta">
									<span class="badge badge-{dl.status === 'completed' ? 'success' : dl.status === 'failed' ? 'danger' : 'warning'}">
										{dl.status}
									</span>
									{#if dl.progress !== null && dl.progress !== undefined}
										<span>Progression: {dl.progress}%</span>
									{/if}
								</p>
							</div>
							{#if dl.progress !== null && dl.progress !== undefined && dl.status === 'running'}
								<div class="progress-bar">
									<div class="progress-fill" style="width: {dl.progress}%"></div>
								</div>
							{/if}
						</div>
					{/each}
				</div>
			{/if}
		</section>
	</div>
</div>

<style>
	.downloads-page {
		padding: calc(var(--nav-height) + 2rem) 0 4rem;
		min-height: 100vh;
		background: var(--bg-primary);
	}

	.container {
		max-width: 1400px;
		margin: 0 auto;
		padding: 0 2rem;
	}

	h1 {
		font-size: 2.5rem;
		margin-bottom: 0.5rem;
		color: var(--text-primary);
	}

	.subtitle {
		font-size: 1.1rem;
		color: var(--text-muted);
		margin-bottom: 2rem;
	}

	h2 {
		font-size: 1.8rem;
		margin-bottom: 1.5rem;
		color: var(--text-primary);
	}

	h3 {
		font-size: 1.3rem;
		margin-bottom: 1rem;
		color: var(--text-primary);
	}

	/* Alert Boxes */
	.error-box, .info-box, .loading-box {
		padding: 1.5rem;
		border-radius: var(--radius);
		margin-bottom: 2rem;
	}

	.error-box {
		background: rgba(239, 68, 68, 0.1);
		border: 1px solid rgba(239, 68, 68, 0.3);
		color: #fca5a5;
	}

	.info-box {
		background: rgba(59, 130, 246, 0.1);
		border: 1px solid rgba(59, 130, 246, 0.3);
		color: #93c5fd;
		text-align: center;
	}

	.loading-box {
		background: var(--bg-card);
		border: 1px solid var(--border);
		text-align: center;
	}

	.info-text {
		font-size: 0.9rem;
		color: var(--text-muted);
		margin-top: 0.5rem;
	}

	.spinner {
		width: 40px;
		height: 40px;
		border: 4px solid var(--border);
		border-top-color: var(--accent);
		border-radius: 50%;
		animation: spin 1s linear infinite;
		margin: 0 auto 1rem;
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}

	/* Torrents Grid */
	.torrents-section {
		margin-bottom: 3rem;
	}

	.results-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
		gap: 1rem;
	}

	.torrent-card {
		background: var(--bg-card);
		border: 2px solid var(--border);
		border-radius: var(--radius);
		padding: 1.5rem;
		cursor: pointer;
		transition: all 0.2s;
		text-align: left;
	}

	.torrent-card:hover {
		border-color: var(--accent);
		transform: translateY(-2px);
		box-shadow: 0 8px 20px rgba(0,0,0,0.3);
	}

	.torrent-card.selected {
		border-color: var(--accent);
		background: rgba(192, 74, 53, 0.1);
	}

	.torrent-header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 1rem;
		gap: 1rem;
	}

	.torrent-title {
		font-size: 0.95rem;
		font-weight: 600;
		color: var(--text-primary);
		line-height: 1.4;
		flex: 1;
	}

	.selected-badge {
		background: var(--accent);
		color: white;
		padding: 0.25rem 0.75rem;
		border-radius: 999px;
		font-size: 0.75rem;
		font-weight: 600;
		white-space: nowrap;
	}

	.torrent-meta, .torrent-stats {
		display: flex;
		flex-wrap: wrap;
		gap: 0.75rem;
		font-size: 0.85rem;
	}

	.torrent-meta {
		margin-bottom: 0.75rem;
		color: var(--text-secondary);
	}

	.meta-item {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
	}

	.stat-item {
		padding: 0.25rem 0.75rem;
		background: var(--bg-secondary);
		border-radius: 4px;
		font-weight: 600;
	}

	.stat-seeders { color: #4caf7d; }
	.stat-leechers { color: #fb923c; }
	.stat-score { color: #fbbf24; }

	/* Confirmation Panel */
	.confirmation-panel {
		background: var(--bg-card);
		border: 2px solid var(--accent);
		border-radius: var(--radius);
		padding: 2rem;
		margin-bottom: 3rem;
		animation: slideIn 0.3s ease;
	}

	@keyframes slideIn {
		from {
			opacity: 0;
			transform: translateY(-10px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.selected-details {
		margin-bottom: 2rem;
	}

	.detail-title {
		font-size: 0.9rem;
		color: var(--text-muted);
		margin-bottom: 0.5rem;
	}

	.detail-value {
		font-size: 1.1rem;
		font-weight: 600;
		color: var(--text-primary);
		margin-bottom: 1.5rem;
	}

	.details-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 1rem;
	}

	.detail-item {
		display: flex;
		justify-content: space-between;
		padding: 0.75rem;
		background: var(--bg-secondary);
		border-radius: 6px;
	}

	.detail-item .label {
		color: var(--text-muted);
		font-size: 0.9rem;
	}

	.detail-item .value {
		color: var(--text-primary);
		font-weight: 600;
	}

	.action-buttons {
		display: flex;
		gap: 1rem;
		justify-content: flex-end;
	}

	.btn {
		padding: 1rem 2rem;
		border: none;
		border-radius: var(--radius);
		font-weight: 600;
		font-size: 1rem;
		cursor: pointer;
		transition: all 0.2s;
	}

	.btn-primary {
		background: linear-gradient(135deg, #3b82f6, #2563eb);
		color: white;
	}

	.btn-primary:hover:not(:disabled) {
		opacity: 0.9;
		transform: translateY(-2px);
	}

	.btn-primary:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.btn-secondary {
		background: var(--bg-secondary);
		color: var(--text-primary);
		border: 1px solid var(--border);
	}

	.btn-secondary:hover {
		background: var(--bg-hover);
	}

	/* Downloads Section */
	.downloads-section {
		margin-top: 3rem;
	}

	.downloads-list {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	.download-item {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: var(--radius);
		padding: 1.5rem;
	}

	.download-info {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 0.5rem;
	}

	.download-title {
		font-weight: 600;
		color: var(--text-primary);
		flex: 1;
	}

	.download-meta {
		display: flex;
		gap: 1rem;
		font-size: 0.9rem;
		color: var(--text-muted);
	}

	.badge {
		padding: 0.25rem 0.75rem;
		border-radius: 999px;
		font-size: 0.75rem;
		font-weight: 600;
	}

	.badge-success { background: rgba(76, 175, 125, 0.2); color: #4caf7d; }
	.badge-warning { background: rgba(251, 146, 60, 0.2); color: #fb923c; }
	.badge-danger { background: rgba(239, 68, 68, 0.2); color: #ef4444; }

	.progress-bar {
		height: 6px;
		background: var(--bg-secondary);
		border-radius: 999px;
		overflow: hidden;
		margin-top: 1rem;
	}

	.progress-fill {
		height: 100%;
		background: linear-gradient(90deg, var(--accent), #fb923c);
		transition: width 0.3s ease;
	}

	@media (max-width: 768px) {
		.results-grid {
			grid-template-columns: 1fr;
		}

		.action-buttons {
			flex-direction: column;
		}

		.btn {
			width: 100%;
		}
	}
</style>
