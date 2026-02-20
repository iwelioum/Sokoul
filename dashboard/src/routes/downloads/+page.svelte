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

	// ── URL params ──────────────────────────────────────
	let query      = $state('');
	let tmdbId     = $state<number | null>(null);
	let mediaType  = $state<'movie' | 'tv'>('movie');
	let mediaId    = $state<string | null>(null);

	// ── Raw results ─────────────────────────────────────
	let allResults: SearchResult[] = $state([]);
	let searching       = $state(false);
	let searchError     = $state('');
	let selectedTorrent: SearchResult | null = $state(null);
	let startingDownload = $state(false);

	// ── Downloads ────────────────────────────────────────
	let downloads: Task[] = $state([]);
	let loadingDownloads  = $state(true);
	let downloadsTimer: ReturnType<typeof setInterval> | null = null;

	let initialized = false;

	// ── Filters state ────────────────────────────────────
	type QualityKey = 'all' | '4k' | '1080p' | '720p' | 'sd';
	type SortKey    = 'seeders' | 'score' | 'size_asc' | 'size_desc' | 'ratio';
	type ProtoKey   = 'all' | 'torrent' | 'magnet';

	let filterQuality  = $state<QualityKey>('all');
	let filterProtocol = $state<ProtoKey>('all');
	let filterProvider = $state('all');
	let filterMinSeed  = $state(0);
	let sortBy         = $state<SortKey>('seeders');
	let searchTerm     = $state('');

	// ── Derived: available providers from results ─────────
	const availableProviders = $derived.by<string[]>(() => {
		const set = new Set(allResults.map(r => r.provider));
		return ['all', ...Array.from(set)];
	});

	// ── Quality detection helper ──────────────────────────
	function detectQuality(r: SearchResult): QualityKey {
		const t = (r.quality ?? r.title).toLowerCase();
		if (t.includes('2160') || t.includes('4k') || t.includes('uhd')) return '4k';
		if (t.includes('1080'))  return '1080p';
		if (t.includes('720'))   return '720p';
		return 'sd';
	}

	// ── Smart score ──────────────────────────────────────
	function smartScore(r: SearchResult): number {
		const qualityBonus: Record<QualityKey, number> = { '4k': 40, '1080p': 30, '720p': 15, 'sd': 0, 'all': 0 };
		const seedBonus  = Math.min(r.seeders * 0.5, 30);
		const ratio      = r.leechers > 0 ? r.seeders / r.leechers : r.seeders;
		const ratioBonus = Math.min(ratio * 3, 20);
		const aiBonus    = r.ai_validated ? 10 : 0;
		const scoreBonus = r.score ? r.score * 0.1 : 0;
		return qualityBonus[detectQuality(r)] + seedBonus + ratioBonus + aiBonus + scoreBonus;
	}

	// ── Filtered & sorted results ─────────────────────────
	const filteredResults = $derived.by<SearchResult[]>(() => {
		let out = allResults.filter(r => {
			// quality
			if (filterQuality !== 'all' && detectQuality(r) !== filterQuality) return false;
			// protocol
			if (filterProtocol === 'torrent' && !r.url) return false;
			if (filterProtocol === 'magnet'  && !r.magnet_link) return false;
			// provider
			if (filterProvider !== 'all' && r.provider !== filterProvider) return false;
			// min seeders
			if (r.seeders < filterMinSeed) return false;
			// search within results
			if (searchTerm && !r.title.toLowerCase().includes(searchTerm.toLowerCase())) return false;
			return true;
		});

		// sort
		out.sort((a, b) => {
			switch (sortBy) {
				case 'seeders':   return b.seeders - a.seeders;
				case 'score':     return smartScore(b) - smartScore(a);
				case 'size_desc': return b.size_bytes - a.size_bytes;
				case 'size_asc':  return a.size_bytes - b.size_bytes;
				case 'ratio': {
					const ra = a.leechers > 0 ? a.seeders / a.leechers : a.seeders;
					const rb = b.leechers > 0 ? b.seeders / b.leechers : b.seeders;
					return rb - ra;
				}
				default: return 0;
			}
		});
		return out;
	});

	// ── Quality badge colour ──────────────────────────────
	const qualityColor: Record<QualityKey, string> = {
		'4k':    '#a855f7',
		'1080p': '#3b82f6',
		'720p':  '#22c55e',
		'sd':    '#6b7280',
		'all':   '#6b7280'
	};

	// ── Init from URL ─────────────────────────────────────
	$effect(() => {
		const params = $page.url.searchParams;
		query     = params.get('query') || '';
		tmdbId    = params.get('tmdbId') ? Number(params.get('tmdbId')) : null;
		mediaType = params.get('mediaType') === 'tv' ? 'tv' : 'movie';

		if (query && tmdbId && !initialized) {
			initialized = true;
			initializeDownload();
		}
	});

	async function initializeDownload() {
		if (!isLoggedIn()) { searchError = 'Connexion requise.'; return; }
		searching = true; searchError = ''; allResults = [];
		try {
			const media = await createMedia({ title: query, media_type: mediaType, tmdb_id: tmdbId! });
			mediaId = media.id;
			const resp = await directSearch(query, mediaId);
			allResults = resp.results;
			if (allResults.length === 0) searchError = 'Aucun torrent trouvé.';
		} catch (e) {
			searchError = e instanceof Error ? e.message : 'Erreur de recherche';
		} finally { searching = false; }
	}

	async function retrySearch() {
		if (!query || !mediaId) return;
		searching = true; searchError = ''; allResults = [];
		try {
			const resp = await directSearch(query, mediaId);
			allResults = resp.results;
			if (allResults.length === 0) searchError = 'Aucun torrent trouvé.';
		} catch (e) {
			searchError = e instanceof Error ? e.message : 'Erreur de recherche';
		} finally { searching = false; }
	}

	async function loadDownloads() {
		if (!isLoggedIn()) { downloads = []; loadingDownloads = false; return; }
		try { downloads = await listDownloads(); } catch { downloads = []; }
		loadingDownloads = false;
	}

	async function confirmDownload() {
		if (!selectedTorrent || !mediaId || startingDownload) return;
		startingDownload = true;
		try {
			await startDownload({ media_id: mediaId, search_result_id: selectedTorrent.id });
			selectedTorrent = null;
			await loadDownloads();
		} catch (e) {
			alert(`Échec : ${e instanceof Error ? e.message : 'Erreur inconnue'}`);
		} finally { startingDownload = false; }
	}

	function resetFilters() {
		filterQuality = 'all'; filterProtocol = 'all';
		filterProvider = 'all'; filterMinSeed = 0;
		sortBy = 'seeders'; searchTerm = '';
	}

	const seedPresets = [0, 5, 20, 50, 100];

	onMount(() => {
		loadDownloads();
		downloadsTimer = setInterval(loadDownloads, 5000);
		return () => { if (downloadsTimer) clearInterval(downloadsTimer); };
	});
</script>

<svelte:head>
	<title>{query ? `Télécharger "${query}"` : 'Téléchargements'} — Sokoul</title>
</svelte:head>

<div class="dl-page">

	<!-- ── Header ──────────────────────────────────────── -->
	<header class="dl-header">
		<div class="dl-header-inner">
			<div class="dl-breadcrumb">
				<a href="/" class="crumb">Accueil</a>
				<span class="crumb-sep">/</span>
				{#if mediaType === 'movie'}
					<a href="/films" class="crumb">Films</a>
				{:else}
					<a href="/series" class="crumb">Séries</a>
				{/if}
				<span class="crumb-sep">/</span>
				<span class="crumb-current">Télécharger</span>
			</div>

			<div class="dl-title-row">
				<div class="dl-title-icon">
					<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
						<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
					</svg>
				</div>
				<div>
					<h1 class="dl-title">{query || 'Téléchargements'}</h1>
					<p class="dl-subtitle">
						<span class="type-badge">{mediaType === 'tv' ? '📺 Série' : '🎬 Film'}</span>
						{#if tmdbId}<span class="tmdb-tag">TMDB {tmdbId}</span>{/if}
					</p>
				</div>

				{#if !searching && allResults.length > 0}
					<div class="result-summary">
						<span class="result-count">{filteredResults.length}</span>
						<span class="result-label">/ {allResults.length} torrents</span>
					</div>
				{/if}
			</div>
		</div>
	</header>

	<!-- ── Main layout ──────────────────────────────────── -->
	<div class="dl-body">

		<!-- ── Sidebar Filters ───────────────────────────── -->
		<aside class="dl-sidebar">
			<div class="sidebar-inner">

				<!-- Search within results -->
				{#if allResults.length > 0}
					<div class="filter-group">
						<label class="filter-label">Filtrer les titres</label>
						<div class="search-input-wrap">
							<svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14" class="search-ico">
								<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
							</svg>
							<input
								class="filter-input"
								type="text"
								placeholder="ex: BluRay, FRENCH…"
								bind:value={searchTerm}
							/>
						</div>
					</div>

					<!-- Qualité -->
					<div class="filter-group">
						<label class="filter-label">Qualité</label>
						<div class="pill-group">
							{#each (['all', '4k', '1080p', '720p', 'sd'] as QualityKey[]) as q}
								<button
									class="pill"
									class:active={filterQuality === q}
									onclick={() => filterQuality = q}
								>
									{q === 'all' ? 'Toutes' : q === '4k' ? '4K' : q.toUpperCase()}
								</button>
							{/each}
						</div>
					</div>

					<!-- Provider -->
					{#if availableProviders.length > 2}
						<div class="filter-group">
							<label class="filter-label">Source</label>
							<div class="pill-group">
								{#each availableProviders as p}
									<button
										class="pill"
										class:active={filterProvider === p}
										onclick={() => filterProvider = p}
									>
										{p === 'all' ? 'Toutes' : p}
									</button>
								{/each}
							</div>
						</div>
					{/if}

					<!-- Protocol -->
					<div class="filter-group">
						<label class="filter-label">Protocole</label>
						<div class="pill-group">
							{#each (['all', 'torrent', 'magnet'] as ProtoKey[]) as p}
								<button
									class="pill"
									class:active={filterProtocol === p}
									onclick={() => filterProtocol = p}
								>
									{p === 'all' ? 'Tous' : p === 'torrent' ? '🗂 Torrent' : '🧲 Magnet'}
								</button>
							{/each}
						</div>
					</div>

					<!-- Min seeders -->
					<div class="filter-group">
						<label class="filter-label">Seeders minimum</label>
						<div class="pill-group">
							{#each seedPresets as s}
								<button
									class="pill"
									class:active={filterMinSeed === s}
									onclick={() => filterMinSeed = s}
								>
									{s === 0 ? 'Tous' : `${s}+`}
								</button>
							{/each}
						</div>
					</div>

					<!-- Sort -->
					<div class="filter-group">
						<label class="filter-label">Trier par</label>
						<select class="filter-select" bind:value={sortBy}>
							<option value="seeders">Seeders (↓)</option>
							<option value="score">Score intelligent (↓)</option>
							<option value="ratio">Ratio S/L (↓)</option>
							<option value="size_desc">Taille (↓)</option>
							<option value="size_asc">Taille (↑)</option>
						</select>
					</div>

					<!-- Reset -->
					<button class="reset-btn" onclick={resetFilters}>
						<svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
							<path d="M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
						</svg>
						Réinitialiser les filtres
					</button>
				{/if}

				<!-- Downloads section in sidebar -->
				<div class="filter-group sidebar-downloads">
					<div class="sidebar-dl-header">
						<label class="filter-label">Téléchargements actifs</label>
						{#if downloads.length > 0}
							<span class="dl-count-badge">{downloads.length}</span>
						{/if}
					</div>

					{#if loadingDownloads}
						<p class="sidebar-dl-empty">Chargement…</p>
					{:else if downloads.length === 0}
						<p class="sidebar-dl-empty">Aucun téléchargement.</p>
					{:else}
						<div class="sidebar-dl-list">
							{#each downloads as dl (dl.id)}
								{@const payload = dl.payload as Record<string, unknown> | null}
								{@const title = typeof payload?.title === 'string' ? payload.title : `Tâche ${dl.id.slice(0, 6)}`}
								<div class="sidebar-dl-item">
									<div class="sidebar-dl-top">
										<span class="sidebar-dl-title">{title}</span>
										<span class="status-dot status-{dl.status}"></span>
									</div>
									{#if dl.progress !== null && dl.progress !== undefined && dl.status === 'running'}
										<div class="mini-progress">
											<div class="mini-progress-fill" style="width:{dl.progress}%"></div>
										</div>
									{/if}
									<div class="sidebar-dl-meta">
										<span class="status-label status-label-{dl.status}">{dl.status}</span>
										{#if dl.progress !== null && dl.progress !== undefined}
											<span class="sidebar-dl-pct">{dl.progress}%</span>
										{/if}
									</div>
								</div>
							{/each}
						</div>
					{/if}
				</div>

			</div>
		</aside>

		<!-- ── Results main area ──────────────────────────── -->
		<main class="dl-main">

			<!-- Error -->
			{#if searchError}
				<div class="state-box state-error">
					<svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32" class="state-icon">
						<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
					</svg>
					<p class="state-msg">{searchError}</p>
					<button class="action-btn" onclick={retrySearch}>Réessayer</button>
				</div>
			{/if}

			<!-- Loading -->
			{#if searching}
				<div class="state-box state-loading">
					<div class="spinner-ring"></div>
					<p class="state-msg">Interrogation de Prowlarr &amp; Jackett…</p>
					<p class="state-sub">Ça peut prendre quelques secondes</p>
				</div>
			{:else if allResults.length === 0 && !searchError}
				<div class="state-box state-empty">
					<svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" class="state-icon state-icon-lg">
						<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
					</svg>
					<p class="state-msg">Aucun torrent trouvé</p>
					{#if mediaId}
						<button class="action-btn" onclick={retrySearch}>Lancer la recherche</button>
					{/if}
				</div>
			{:else if filteredResults.length === 0 && allResults.length > 0}
				<div class="state-box state-empty">
					<p class="state-msg">Aucun résultat pour ces filtres</p>
					<button class="action-btn secondary" onclick={resetFilters}>Réinitialiser les filtres</button>
				</div>
			{:else}
				<!-- Results table -->
				<div class="results-table-wrap">
					<table class="results-table">
						<thead>
							<tr>
								<th class="col-quality">Qualité</th>
								<th class="col-title">Titre</th>
								<th class="col-provider">Source</th>
								<th class="col-size">Taille</th>
								<th class="col-seeds">Seeds</th>
								<th class="col-leech">Leech</th>
								<th class="col-score">Score</th>
								<th class="col-action"></th>
							</tr>
						</thead>
						<tbody>
							{#each filteredResults as result (result.id)}
								{@const q = detectQuality(result)}
								{@const ss = Math.round(smartScore(result))}
								<tr
									class="result-row"
									class:selected={selectedTorrent?.id === result.id}
									onclick={() => selectedTorrent = selectedTorrent?.id === result.id ? null : result}
								>
									<td class="col-quality">
										<span class="quality-badge" style="--qc:{qualityColor[q]}">
											{q === 'all' ? '—' : q === '4k' ? '4K' : q.toUpperCase()}
										</span>
									</td>
									<td class="col-title">
										<span class="result-title">{result.title}</span>
										<div class="result-tags">
											{#if result.magnet_link}
												<span class="proto-tag">🧲</span>
											{/if}
											{#if result.url}
												<span class="proto-tag">🗂</span>
											{/if}
											{#if result.ai_validated}
												<span class="ai-tag">IA ✓</span>
											{/if}
										</div>
									</td>
									<td class="col-provider">
										<span class="provider-tag">{result.provider}</span>
									</td>
									<td class="col-size">{formatBytes(result.size_bytes)}</td>
									<td class="col-seeds">
										<span class="seed-val" class:seed-high={result.seeders >= 20} class:seed-mid={result.seeders >= 5 && result.seeders < 20} class:seed-low={result.seeders < 5}>
											↑{result.seeders}
										</span>
									</td>
									<td class="col-leech">
										<span class="leech-val">↓{result.leechers}</span>
									</td>
									<td class="col-score">
										<div class="score-bar-wrap">
											<div class="score-bar" style="width:{Math.min(ss, 100)}%"></div>
											<span class="score-num">{ss}</span>
										</div>
									</td>
									<td class="col-action">
										<button
											class="dl-btn"
											class:dl-btn-active={selectedTorrent?.id === result.id}
											onclick={(e) => { e.stopPropagation(); selectedTorrent = result; }}
											title="Sélectionner pour télécharger"
										>
											<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
												<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
											</svg>
										</button>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				</div>
			{/if}

		</main>
	</div>

	<!-- ── Confirmation drawer (bottom slide-up) ─────────── -->
	{#if selectedTorrent}
		{@const q = detectQuality(selectedTorrent)}
		<!-- svelte-ignore a11y_click_events_have_key_events -->
		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div class="confirm-overlay" onclick={() => selectedTorrent = null}></div>
		<div class="confirm-drawer">
			<div class="confirm-inner">
				<div class="confirm-left">
					<div class="confirm-quality-badge" style="--qc:{qualityColor[q]}">
						{q === '4k' ? '4K' : q.toUpperCase()}
					</div>
					<div class="confirm-info">
						<p class="confirm-title">{selectedTorrent.title}</p>
						<div class="confirm-meta">
							<span class="confirm-tag">{selectedTorrent.provider}</span>
							<span class="confirm-tag">{formatBytes(selectedTorrent.size_bytes)}</span>
							<span class="confirm-tag seed-tag">↑ {selectedTorrent.seeders} seeders</span>
							{#if selectedTorrent.magnet_link}<span class="confirm-tag">🧲 Magnet</span>{/if}
							{#if selectedTorrent.url}<span class="confirm-tag">🗂 Torrent</span>{/if}
						</div>
					</div>
				</div>
				<div class="confirm-actions">
					<button class="cancel-btn" onclick={() => selectedTorrent = null}>Annuler</button>
					<button
						class="start-btn"
						onclick={confirmDownload}
						disabled={startingDownload}
					>
						{#if startingDownload}
							<span class="btn-spinner"></span> Démarrage…
						{:else}
							<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
								<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
							</svg>
							Télécharger
						{/if}
					</button>
				</div>
			</div>
		</div>
	{/if}

</div>

<style>
	/* ── Base ───────────────────────────────────────────── */
	.dl-page {
		min-height: 100vh;
		background: var(--bg-primary);
		display: flex;
		flex-direction: column;
	}

	/* ── Header ─────────────────────────────────────────── */
	.dl-header {
		background: linear-gradient(180deg, rgba(0,0,0,0.6) 0%, transparent 100%),
		            var(--bg-secondary);
		border-bottom: 1px solid rgba(255,255,255,0.06);
		padding: calc(var(--nav-height, 70px) + 1.5rem) 2rem 1.5rem;
	}
	.dl-header-inner { max-width: 1600px; margin: 0 auto; }

	.dl-breadcrumb {
		display: flex; align-items: center; gap: 6px;
		font-size: 12px; color: var(--text-muted);
		margin-bottom: 1.2rem;
	}
	.crumb { color: var(--text-muted); text-decoration: none; }
	.crumb:hover { color: var(--text-primary); }
	.crumb-sep { opacity: 0.4; }
	.crumb-current { color: var(--text-secondary); }

	.dl-title-row {
		display: flex; align-items: center; gap: 1.2rem; flex-wrap: wrap;
	}
	.dl-title-icon {
		width: 52px; height: 52px;
		background: linear-gradient(135deg, #3b82f6, #1d4ed8);
		border-radius: 14px;
		display: flex; align-items: center; justify-content: center;
		color: white; flex-shrink: 0;
	}
	.dl-title {
		font-size: 1.8rem; font-weight: 700;
		color: var(--text-primary); margin: 0;
	}
	.dl-subtitle {
		display: flex; align-items: center; gap: 8px;
		margin: 4px 0 0; font-size: 0.85rem;
	}
	.type-badge {
		background: rgba(59,130,246,0.15);
		color: #93c5fd;
		border: 1px solid rgba(59,130,246,0.25);
		padding: 2px 10px; border-radius: 999px; font-size: 0.78rem;
	}
	.tmdb-tag {
		color: var(--text-muted); font-size: 0.78rem;
	}

	.result-summary {
		margin-left: auto;
		text-align: right;
	}
	.result-count {
		font-size: 2.5rem; font-weight: 800;
		color: var(--text-primary); line-height: 1;
	}
	.result-label {
		display: block; font-size: 0.8rem; color: var(--text-muted);
	}

	/* ── Body layout ────────────────────────────────────── */
	.dl-body {
		display: flex; flex: 1;
		max-width: 1600px; width: 100%;
		margin: 0 auto; padding: 0;
	}

	/* ── Sidebar ─────────────────────────────────────────── */
	.dl-sidebar {
		width: 260px; flex-shrink: 0;
		border-right: 1px solid rgba(255,255,255,0.06);
	}
	.sidebar-inner {
		position: sticky; top: var(--nav-height, 70px);
		max-height: calc(100vh - var(--nav-height, 70px));
		overflow-y: auto; padding: 1.5rem 1.2rem;
		display: flex; flex-direction: column; gap: 1.5rem;
		scrollbar-width: thin;
	}

	.filter-group { display: flex; flex-direction: column; gap: 8px; }
	.filter-label {
		font-size: 0.72rem; font-weight: 700;
		text-transform: uppercase; letter-spacing: 1px;
		color: var(--text-muted);
	}

	.search-input-wrap { position: relative; }
	.search-ico {
		position: absolute; left: 10px; top: 50%;
		transform: translateY(-50%); color: var(--text-muted); pointer-events: none;
	}
	.filter-input {
		width: 100%; padding: 8px 10px 8px 32px;
		background: var(--bg-card); border: 1px solid rgba(255,255,255,0.1);
		border-radius: 8px; color: var(--text-primary); font-size: 0.82rem;
		outline: none; transition: border-color 0.2s;
	}
	.filter-input:focus { border-color: #3b82f6; }

	.pill-group { display: flex; flex-wrap: wrap; gap: 5px; }
	.pill {
		padding: 4px 10px;
		background: var(--bg-card); border: 1px solid rgba(255,255,255,0.1);
		border-radius: 999px; font-size: 0.75rem; color: var(--text-secondary);
		cursor: pointer; transition: all 0.15s;
	}
	.pill:hover { border-color: #3b82f6; color: #93c5fd; }
	.pill.active {
		background: rgba(59,130,246,0.2);
		border-color: #3b82f6; color: #93c5fd; font-weight: 600;
	}

	.filter-select {
		width: 100%; padding: 8px 10px;
		background: var(--bg-card); border: 1px solid rgba(255,255,255,0.1);
		border-radius: 8px; color: var(--text-primary); font-size: 0.82rem;
		outline: none; cursor: pointer;
	}
	.filter-select:focus { border-color: #3b82f6; }

	.reset-btn {
		display: flex; align-items: center; justify-content: center; gap: 6px;
		padding: 8px; background: rgba(255,255,255,0.04);
		border: 1px solid rgba(255,255,255,0.1); border-radius: 8px;
		color: var(--text-muted); font-size: 0.78rem; cursor: pointer;
		transition: all 0.15s;
	}
	.reset-btn:hover { background: rgba(255,255,255,0.08); color: var(--text-secondary); }

	/* Sidebar downloads */
	.sidebar-downloads { border-top: 1px solid rgba(255,255,255,0.06); padding-top: 1.2rem; }
	.sidebar-dl-header { display: flex; align-items: center; justify-content: space-between; }
	.dl-count-badge {
		width: 20px; height: 20px; border-radius: 50%;
		background: #3b82f6; color: white; font-size: 0.72rem; font-weight: 700;
		display: flex; align-items: center; justify-content: center;
	}
	.sidebar-dl-empty { font-size: 0.8rem; color: var(--text-muted); }
	.sidebar-dl-list { display: flex; flex-direction: column; gap: 8px; }
	.sidebar-dl-item {
		background: var(--bg-card); border: 1px solid rgba(255,255,255,0.07);
		border-radius: 10px; padding: 10px 12px;
		display: flex; flex-direction: column; gap: 5px;
	}
	.sidebar-dl-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 6px; }
	.sidebar-dl-title {
		font-size: 0.78rem; color: var(--text-secondary);
		display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2;
		-webkit-box-orient: vertical; overflow: hidden;
	}
	.status-dot {
		width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; margin-top: 2px;
	}
	.status-dot.status-running   { background: #22c55e; animation: pulse 1.5s ease-in-out infinite; }
	.status-dot.status-completed { background: #4caf7d; }
	.status-dot.status-failed    { background: #ef4444; }
	.status-dot.status-pending   { background: #fb923c; }

	.mini-progress {
		height: 3px; background: rgba(255,255,255,0.1); border-radius: 999px; overflow: hidden;
	}
	.mini-progress-fill {
		height: 100%; background: #3b82f6; border-radius: 999px; transition: width 0.3s;
	}
	.sidebar-dl-meta { display: flex; align-items: center; gap: 8px; }
	.status-label { font-size: 0.7rem; font-weight: 600; text-transform: uppercase; }
	.status-label-running   { color: #22c55e; }
	.status-label-completed { color: #4caf7d; }
	.status-label-failed    { color: #ef4444; }
	.status-label-pending   { color: #fb923c; }
	.sidebar-dl-pct { font-size: 0.7rem; color: var(--text-muted); margin-left: auto; }

	/* ── Main area ──────────────────────────────────────── */
	.dl-main { flex: 1; overflow: hidden; padding: 1.5rem 1.5rem 8rem; min-width: 0; }

	/* State boxes */
	.state-box {
		display: flex; flex-direction: column; align-items: center;
		justify-content: center; gap: 1rem;
		padding: 4rem 2rem; border-radius: 16px;
		text-align: center;
	}
	.state-error  { background: rgba(239,68,68,0.08); border: 1px solid rgba(239,68,68,0.2); }
	.state-loading{ background: var(--bg-card); border: 1px solid rgba(255,255,255,0.08); }
	.state-empty  { background: var(--bg-card); border: 1px solid rgba(255,255,255,0.08); }
	.state-icon { color: var(--text-muted); }
	.state-icon-lg { opacity: 0.3; }
	.state-msg { font-size: 1.1rem; font-weight: 600; color: var(--text-primary); margin: 0; }
	.state-sub { font-size: 0.85rem; color: var(--text-muted); margin: 0; }

	.action-btn {
		padding: 10px 24px; border-radius: 8px; font-size: 0.9rem; font-weight: 600;
		background: #3b82f6; color: white; border: none; cursor: pointer; transition: all 0.2s;
	}
	.action-btn:hover { background: #2563eb; transform: translateY(-1px); }
	.action-btn.secondary {
		background: var(--bg-secondary); color: var(--text-primary);
		border: 1px solid rgba(255,255,255,0.1);
	}
	.action-btn.secondary:hover { background: var(--bg-hover); }

	/* Spinner */
	.spinner-ring {
		width: 48px; height: 48px;
		border: 3px solid rgba(255,255,255,0.1);
		border-top-color: #3b82f6;
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}
	@keyframes spin { to { transform: rotate(360deg); } }
	@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

	/* ── Results table ──────────────────────────────────── */
	.results-table-wrap {
		border-radius: 14px; overflow: hidden;
		border: 1px solid rgba(255,255,255,0.07);
	}
	.results-table {
		width: 100%; border-collapse: collapse;
		font-size: 0.85rem;
	}
	.results-table thead tr {
		background: rgba(255,255,255,0.04);
		border-bottom: 1px solid rgba(255,255,255,0.07);
	}
	.results-table thead th {
		padding: 12px 14px;
		text-align: left; font-size: 0.72rem; font-weight: 700;
		text-transform: uppercase; letter-spacing: 0.8px;
		color: var(--text-muted); white-space: nowrap;
	}
	.result-row {
		border-bottom: 1px solid rgba(255,255,255,0.04);
		cursor: pointer; transition: background 0.15s;
		background: var(--bg-card);
	}
	.result-row:last-child { border-bottom: none; }
	.result-row:hover { background: rgba(59,130,246,0.06); }
	.result-row.selected { background: rgba(59,130,246,0.12); }
	.result-row td { padding: 12px 14px; vertical-align: middle; }

	/* Columns */
	.col-quality  { width: 70px; }
	.col-title    { min-width: 200px; }
	.col-provider { width: 90px; }
	.col-size     { width: 80px; white-space: nowrap; color: var(--text-muted); }
	.col-seeds    { width: 70px; }
	.col-leech    { width: 70px; }
	.col-score    { width: 100px; }
	.col-action   { width: 50px; }

	.quality-badge {
		display: inline-block;
		padding: 3px 8px; border-radius: 6px; font-size: 0.7rem; font-weight: 700;
		background: color-mix(in srgb, var(--qc) 20%, transparent);
		color: var(--qc);
		border: 1px solid color-mix(in srgb, var(--qc) 40%, transparent);
		white-space: nowrap;
	}

	.result-title {
		display: block; color: var(--text-primary);
		white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
		max-width: 340px;
	}
	.result-tags {
		display: flex; gap: 4px; margin-top: 3px;
	}
	.proto-tag {
		font-size: 0.7rem; opacity: 0.6;
	}
	.ai-tag {
		font-size: 0.65rem; padding: 1px 6px; border-radius: 999px;
		background: rgba(168,85,247,0.2); color: #c084fc;
		border: 1px solid rgba(168,85,247,0.3); font-weight: 600;
	}

	.provider-tag {
		display: inline-block; padding: 2px 8px; border-radius: 6px; font-size: 0.72rem;
		background: rgba(255,255,255,0.06); color: var(--text-muted);
		border: 1px solid rgba(255,255,255,0.1);
	}

	.seed-val { font-weight: 700; font-size: 0.82rem; }
	.seed-high { color: #22c55e; }
	.seed-mid  { color: #fb923c; }
	.seed-low  { color: #ef4444; }
	.leech-val { color: var(--text-muted); font-size: 0.82rem; }

	/* Score bar */
	.score-bar-wrap {
		display: flex; align-items: center; gap: 6px;
	}
	.score-bar {
		height: 4px; background: linear-gradient(90deg, #3b82f6, #a855f7);
		border-radius: 999px; flex: 1; max-width: 60px; transition: width 0.3s;
	}
	.score-num { font-size: 0.75rem; color: var(--text-muted); white-space: nowrap; }

	/* Download button in row */
	.dl-btn {
		width: 32px; height: 32px; border-radius: 8px;
		background: rgba(59,130,246,0.1); border: 1px solid rgba(59,130,246,0.2);
		color: #93c5fd; cursor: pointer; transition: all 0.15s;
		display: flex; align-items: center; justify-content: center;
	}
	.dl-btn:hover { background: rgba(59,130,246,0.25); transform: scale(1.08); }
	.dl-btn.dl-btn-active {
		background: #3b82f6; color: white; border-color: #3b82f6;
	}

	/* ── Confirmation drawer ────────────────────────────── */
	.confirm-overlay {
		position: fixed; inset: 0; z-index: 49;
		background: rgba(0,0,0,0.4);
		backdrop-filter: blur(2px);
		animation: fadeIn 0.2s ease;
	}
	.confirm-drawer {
		position: fixed; bottom: 0; left: 0; right: 0; z-index: 50;
		background: rgba(26,29,41,0.97);
		backdrop-filter: blur(20px);
		border-top: 1px solid rgba(59,130,246,0.4);
		box-shadow: 0 -20px 60px rgba(0,0,0,0.6);
		animation: slideUp 0.25s cubic-bezier(0.4,0,0.2,1) both;
	}
	@keyframes slideUp {
		from { transform: translateY(100%); opacity: 0; }
		to   { transform: translateY(0);    opacity: 1; }
	}
	@keyframes fadeIn {
		from { opacity: 0; } to { opacity: 1; }
	}

	.confirm-inner {
		max-width: 1600px; margin: 0 auto;
		display: flex; align-items: center; gap: 1.5rem;
		padding: 1.2rem 2rem; flex-wrap: wrap;
	}
	.confirm-left { display: flex; align-items: center; gap: 1rem; flex: 1; min-width: 0; }
	.confirm-quality-badge {
		width: 56px; height: 56px; border-radius: 12px; flex-shrink: 0;
		background: color-mix(in srgb, var(--qc) 20%, transparent);
		color: var(--qc);
		border: 2px solid color-mix(in srgb, var(--qc) 50%, transparent);
		display: flex; align-items: center; justify-content: center;
		font-size: 0.8rem; font-weight: 800;
	}
	.confirm-info { min-width: 0; }
	.confirm-title {
		font-size: 0.95rem; font-weight: 600; color: var(--text-primary);
		white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
		margin-bottom: 6px;
	}
	.confirm-meta { display: flex; flex-wrap: wrap; gap: 6px; }
	.confirm-tag {
		padding: 2px 10px; border-radius: 999px; font-size: 0.75rem;
		background: rgba(255,255,255,0.08); color: var(--text-muted);
		border: 1px solid rgba(255,255,255,0.12);
	}
	.seed-tag { color: #4ade80; border-color: rgba(74,222,128,0.3); background: rgba(74,222,128,0.08); }

	.confirm-actions { display: flex; gap: 10px; flex-shrink: 0; }
	.cancel-btn {
		padding: 10px 20px; border-radius: 8px; font-size: 0.9rem; font-weight: 600;
		background: rgba(255,255,255,0.08); color: var(--text-secondary);
		border: 1px solid rgba(255,255,255,0.12); cursor: pointer; transition: all 0.15s;
	}
	.cancel-btn:hover { background: rgba(255,255,255,0.12); }
	.start-btn {
		display: flex; align-items: center; gap: 8px;
		padding: 10px 24px; border-radius: 8px; font-size: 0.9rem; font-weight: 700;
		background: linear-gradient(135deg, #3b82f6, #1d4ed8);
		color: white; border: none; cursor: pointer; transition: all 0.2s;
	}
	.start-btn:hover:not(:disabled) { filter: brightness(1.1); transform: translateY(-1px); }
	.start-btn:disabled { opacity: 0.5; cursor: not-allowed; }
	.btn-spinner {
		width: 14px; height: 14px; border-radius: 50%;
		border: 2px solid rgba(255,255,255,0.3); border-top-color: white;
		animation: spin 0.7s linear infinite; display: inline-block;
	}

	/* ── Responsive ─────────────────────────────────────── */
	@media (max-width: 900px) {
		.dl-body { flex-direction: column; }
		.dl-sidebar {
			width: 100%; border-right: none;
			border-bottom: 1px solid rgba(255,255,255,0.06);
		}
		.sidebar-inner {
			position: static; max-height: none;
			flex-direction: row; flex-wrap: wrap;
			padding: 1rem; gap: 1rem;
		}
		.filter-group { min-width: 160px; flex: 1; }
		.sidebar-downloads { width: 100%; flex: unset; border-top: none; padding-top: 0; }
		.result-title { max-width: 180px; }
		.col-leech, .col-score { display: none; }
	}

	@media (max-width: 600px) {
		.dl-header { padding: calc(var(--nav-height, 70px) + 1rem) 1rem 1rem; }
		.dl-main { padding: 1rem 1rem 8rem; }
		.results-table { font-size: 0.78rem; }
		.result-title { max-width: 120px; }
		.col-provider { display: none; }
		.confirm-inner { padding: 1rem; }
	}
</style>
