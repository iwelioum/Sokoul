<script lang="ts">
	import {
		listLibrary, removeFromLibrary,
		listWatchlist, removeFromWatchlist, addToLibrary,
		getContinueWatching, tmdbImageUrl
	} from '$lib/api/client';
	import type { Favorite, WatchlistEntry, WatchHistoryEntry } from '$lib/api/client';
	import Skeleton from '$lib/components/Skeleton.svelte';

	type Tab = 'library' | 'watchlist' | 'history';
	let activeTab = $state<Tab>('library');

	// Library state
	let libraryItems: Favorite[] = $state([]);
	let libraryTotal = $state(0);
	let libraryPage = $state(1);
	let libraryLoading = $state(false);

	// Watchlist state
	let watchlistItems: WatchlistEntry[] = $state([]);
	let watchlistTotal = $state(0);
	let watchlistLoading = $state(false);

	// History state
	let historyItems: WatchHistoryEntry[] = $state([]);
	let historyLoading = $state(false);

	const PER_PAGE = 30;

	$effect(() => {
		if (activeTab === 'library' && libraryItems.length === 0) loadLibrary();
		if (activeTab === 'watchlist' && watchlistItems.length === 0) loadWatchlist();
		if (activeTab === 'history' && historyItems.length === 0) loadHistory();
	});

	async function loadLibrary() {
		libraryLoading = true;
		try {
			const result = await listLibrary(libraryPage, PER_PAGE);
			libraryItems = result.items;
			libraryTotal = result.total;
		} catch (e) { console.error(e); }
		libraryLoading = false;
	}

	async function loadWatchlist() {
		watchlistLoading = true;
		try {
			const result = await listWatchlist(1, PER_PAGE);
			watchlistItems = result.items;
			watchlistTotal = result.total;
		} catch (e) { console.error(e); }
		watchlistLoading = false;
	}

	async function loadHistory() {
		historyLoading = true;
		try {
			historyItems = await getContinueWatching(50);
		} catch (e) { console.error(e); }
		historyLoading = false;
	}

	async function removeLib(tmdbId: number, mediaType: string) {
		await removeFromLibrary(tmdbId, mediaType);
		libraryItems = libraryItems.filter(i => !(i.tmdb_id === tmdbId && i.media_type === mediaType));
		libraryTotal = Math.max(0, libraryTotal - 1);
	}

	async function removeWatch(tmdbId: number, mediaType: string) {
		await removeFromWatchlist(tmdbId, mediaType);
		watchlistItems = watchlistItems.filter(i => !(i.tmdb_id === tmdbId && i.media_type_wl === mediaType));
		watchlistTotal = Math.max(0, watchlistTotal - 1);
	}

	async function moveToLibrary(item: WatchlistEntry) {
		if (!item.tmdb_id || !item.media_type_wl || !item.title) return;
		await addToLibrary({
			tmdb_id: item.tmdb_id,
			media_type: item.media_type_wl,
			title: item.title,
			poster_url: item.poster_url
		});
		await removeFromWatchlist(item.tmdb_id, item.media_type_wl);
		watchlistItems = watchlistItems.filter(i => i.id !== item.id);
	}

	function progressPercent(entry: WatchHistoryEntry): number {
		if (!entry.progress) return 0;
		const p = typeof entry.progress === 'string' ? parseFloat(entry.progress) : entry.progress;
		return Math.min(100, Math.max(0, p * 100));
	}
</script>

<svelte:head>
	<title>Bibliothèque — SOKOUL</title>
</svelte:head>

<div class="library-page">
	<div class="page-header">
		<h1 class="page-title">Bibliothèque</h1>

		<div class="tabs">
			<button class="tab {activeTab === 'library' ? 'active' : ''}" onclick={() => activeTab = 'library'}>
				<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
				Ma bibliothèque
				{#if libraryTotal > 0}<span class="badge">{libraryTotal}</span>{/if}
			</button>
			<button class="tab {activeTab === 'watchlist' ? 'active' : ''}" onclick={() => activeTab = 'watchlist'}>
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
				Liste d'envies
				{#if watchlistTotal > 0}<span class="badge">{watchlistTotal}</span>{/if}
			</button>
			<button class="tab {activeTab === 'history' ? 'active' : ''}" onclick={() => activeTab = 'history'}>
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
				Historique
			</button>
		</div>
	</div>

	<!-- ── Library tab ── -->
	{#if activeTab === 'library'}
		{#if libraryLoading}
			<div class="media-grid">
				{#each Array(12) as _, i (i)}
					<Skeleton height="240px" borderRadius="10px" />
				{/each}
			</div>
		{:else if libraryItems.length === 0}
			<div class="empty-state">
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="56" height="56">
					<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
				</svg>
				<p>Votre bibliothèque est vide</p>
				<p class="empty-hint">Explorez le catalogue et ajoutez vos films et séries préférés.</p>
				<a href="/" class="btn-explore">Découvrir le catalogue</a>
			</div>
		{:else}
			<div class="media-grid">
				{#each libraryItems as item (item.id)}
					<div class="media-card">
						<a href="/{item.media_type}/{item.tmdb_id}" class="card-link">
							<div class="card-poster">
								{#if item.poster_url}
									<img src={item.poster_url} alt={item.title} loading="lazy" />
								{:else}
									<div class="card-no-poster">
										<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/></svg>
									</div>
								{/if}
								<span class="card-type">{item.media_type === 'tv' ? 'Série' : 'Film'}</span>
								{#if item.vote_average && item.vote_average > 0}
									<span class="card-rating">★ {item.vote_average.toFixed(1)}</span>
								{/if}
							</div>
							<div class="card-info">
								<p class="card-title">{item.title}</p>
								{#if item.release_date}
									<p class="card-year">{item.release_date.substring(0, 4)}</p>
								{/if}
							</div>
						</a>
						<button
							class="card-remove"
							onclick={() => removeLib(item.tmdb_id, item.media_type)}
							title="Retirer de la bibliothèque"
						>✕</button>
					</div>
				{/each}
			</div>
		{/if}
	{/if}

	<!-- ── Watchlist tab ── -->
	{#if activeTab === 'watchlist'}
		{#if watchlistLoading}
			<div class="media-grid">
				{#each Array(12) as _, i (i)}
					<Skeleton height="240px" borderRadius="10px" />
				{/each}
			</div>
		{:else if watchlistItems.length === 0}
			<div class="empty-state">
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="56" height="56">
					<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
				</svg>
				<p>Votre liste d'envies est vide</p>
				<p class="empty-hint">Ajoutez des titres à regarder plus tard depuis les pages de détail.</p>
				<a href="/" class="btn-explore">Découvrir le catalogue</a>
			</div>
		{:else}
			<div class="media-grid">
				{#each watchlistItems as item (item.id)}
					<div class="media-card">
						<a href="/{item.media_type_wl}/{item.tmdb_id}" class="card-link">
							<div class="card-poster">
								{#if item.poster_url}
									<img src={item.poster_url} alt={item.title} loading="lazy" />
								{:else}
									<div class="card-no-poster">
										<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/></svg>
									</div>
								{/if}
								<span class="card-type">{item.media_type_wl === 'tv' ? 'Série' : 'Film'}</span>
							</div>
							<div class="card-info">
								<p class="card-title">{item.title}</p>
							</div>
						</a>
						<div class="card-actions">
							<button class="btn-move" onclick={() => moveToLibrary(item)} title="Ajouter à ma bibliothèque">
								<svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
								Bibliothèque
							</button>
							<button class="card-remove" onclick={() => removeWatch(item.tmdb_id ?? 0, item.media_type_wl ?? '')} title="Retirer">✕</button>
						</div>
					</div>
				{/each}
			</div>
		{/if}
	{/if}

	<!-- ── History tab ── -->
	{#if activeTab === 'history'}
		{#if historyLoading}
			<div class="history-list">
				{#each Array(8) as _, i (i)}
					<Skeleton height="80px" borderRadius="10px" />
				{/each}
			</div>
		{:else if historyItems.length === 0}
			<div class="empty-state">
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="56" height="56">
					<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
				</svg>
				<p>Aucun historique de visionnage</p>
				<p class="empty-hint">Votre historique apparaîtra ici après avoir regardé du contenu.</p>
				<a href="/" class="btn-explore">Découvrir le catalogue</a>
			</div>
		{:else}
			<div class="history-list">
				{#each historyItems as entry (entry.id)}
					{@const pct = progressPercent(entry)}
					<a href="/{entry.media_type_wh}/{entry.tmdb_id}" class="history-item">
						<div class="history-poster">
							{#if entry.poster_url}
								<img src={entry.poster_url} alt={entry.title ?? ''} loading="lazy" />
							{:else}
								<div class="history-no-poster">
									<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M8 5v14l11-7z"/></svg>
								</div>
							{/if}
						</div>
						<div class="history-info">
							<p class="history-title">{entry.title ?? '—'}</p>
							<p class="history-type">{entry.media_type_wh === 'tv' ? 'Série' : 'Film'}</p>
							<div class="history-progress-bar">
								<div class="history-progress-fill" style="width:{pct}%"></div>
							</div>
							<p class="history-pct">{pct.toFixed(0)}% regardé</p>
						</div>
						<div class="history-play">
							<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M8 5v14l11-7z"/></svg>
						</div>
					</a>
				{/each}
			</div>
		{/if}
	{/if}
</div>

<style>
	.library-page { padding-bottom: 60px; }

	.page-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: 16px;
		margin-bottom: 28px;
	}

	.page-title {
		font-size: 28px;
		font-weight: 800;
		color: var(--text-primary);
	}

	/* ── Tabs ── */
	.tabs {
		display: flex;
		gap: 4px;
		background: var(--bg-secondary);
		border-radius: var(--radius-sm);
		padding: 4px;
	}

	.tab {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 8px 16px;
		border-radius: 6px;
		border: none;
		background: transparent;
		color: var(--text-secondary);
		font-size: 13px;
		font-weight: 500;
		cursor: pointer;
		transition: all var(--transition-fast);
	}

	.tab:hover { color: var(--text-primary); background: rgba(255,255,255,0.05); }
	.tab.active { background: var(--accent); color: #fff; }

	.badge {
		background: rgba(255,255,255,0.25);
		color: #fff;
		font-size: 11px;
		font-weight: 700;
		padding: 1px 6px;
		border-radius: 10px;
		min-width: 18px;
		text-align: center;
	}

	/* ── Grid ── */
	.media-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: 16px;
	}

	.media-card {
		position: relative;
		background: var(--bg-card);
		border-radius: var(--radius-sm);
		overflow: hidden;
		border: 1px solid var(--border);
		transition: all var(--transition-fast);
	}

	.media-card:hover { border-color: var(--accent); transform: translateY(-2px); }

	.card-link { display: block; text-decoration: none; color: inherit; }

	.card-poster {
		position: relative;
		aspect-ratio: 2 / 3;
		overflow: hidden;
		background: var(--bg-secondary);
	}

	.card-poster img { width: 100%; height: 100%; object-fit: cover; }

	.card-no-poster {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
	}

	.card-type {
		position: absolute;
		top: 6px;
		left: 6px;
		background: rgba(0,0,0,0.75);
		color: #fff;
		font-size: 10px;
		padding: 2px 6px;
		border-radius: 4px;
	}

	.card-rating {
		position: absolute;
		bottom: 6px;
		right: 6px;
		background: rgba(0,0,0,0.75);
		color: var(--warning);
		font-size: 11px;
		font-weight: 700;
		padding: 2px 6px;
		border-radius: 4px;
	}

	.card-info { padding: 8px 10px; }

	.card-title {
		font-size: 12px;
		font-weight: 600;
		color: var(--text-primary);
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		margin-bottom: 2px;
	}

	.card-year { font-size: 11px; color: var(--text-muted); }

	.card-remove {
		position: absolute;
		top: 6px;
		right: 6px;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		background: rgba(0,0,0,0.7);
		border: none;
		color: #fff;
		font-size: 12px;
		cursor: pointer;
		display: flex;
		align-items: center;
		justify-content: center;
		opacity: 0;
		transition: opacity var(--transition-fast);
	}

	.media-card:hover .card-remove { opacity: 1; }
	.card-remove:hover { background: var(--danger); }

	/* Watchlist card-actions */
	.card-actions {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 6px 10px;
		border-top: 1px solid var(--border);
	}

	.btn-move {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 4px;
		padding: 5px 8px;
		background: rgba(108,92,231,0.15);
		border: 1px solid rgba(108,92,231,0.3);
		color: var(--accent);
		border-radius: 5px;
		font-size: 11px;
		font-weight: 600;
		cursor: pointer;
		transition: all var(--transition-fast);
	}

	.btn-move:hover { background: var(--accent); color: #fff; }

	/* .card-remove in watchlist — always visible in actions row */
	.card-actions .card-remove {
		position: static;
		opacity: 1;
		background: rgba(255,255,255,0.08);
		color: var(--text-secondary);
	}

	.card-actions .card-remove:hover { background: var(--danger); color: #fff; }

	/* ── History ── */
	.history-list { display: flex; flex-direction: column; gap: 10px; }

	.history-item {
		display: flex;
		align-items: center;
		gap: 16px;
		background: var(--bg-card);
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		padding: 12px;
		text-decoration: none;
		color: inherit;
		transition: all var(--transition-fast);
	}

	.history-item:hover { border-color: var(--accent); background: var(--bg-secondary); }

	.history-poster {
		width: 56px;
		height: 80px;
		border-radius: 6px;
		overflow: hidden;
		background: var(--bg-secondary);
		flex-shrink: 0;
	}

	.history-poster img { width: 100%; height: 100%; object-fit: cover; }

	.history-no-poster {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
	}

	.history-info { flex: 1; min-width: 0; }

	.history-title {
		font-size: 14px;
		font-weight: 600;
		color: var(--text-primary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		margin-bottom: 3px;
	}

	.history-type {
		font-size: 12px;
		color: var(--text-muted);
		margin-bottom: 8px;
	}

	.history-progress-bar {
		height: 4px;
		background: rgba(255,255,255,0.1);
		border-radius: 2px;
		margin-bottom: 4px;
		overflow: hidden;
	}

	.history-progress-fill {
		height: 100%;
		background: var(--accent);
		border-radius: 2px;
		transition: width 0.3s ease;
	}

	.history-pct { font-size: 11px; color: var(--text-muted); }

	.history-play {
		color: var(--text-muted);
		flex-shrink: 0;
		transition: color var(--transition-fast);
	}

	.history-item:hover .history-play { color: var(--accent); }

	/* ── Empty state ── */
	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 80px 24px;
		text-align: center;
		color: var(--text-secondary);
		gap: 12px;
	}

	.empty-state svg { color: var(--text-muted); opacity: 0.5; }
	.empty-state p { font-size: 16px; font-weight: 600; }
	.empty-hint { font-size: 14px; color: var(--text-muted); max-width: 400px; }

	.btn-explore {
		margin-top: 8px;
		display: inline-block;
		padding: 10px 24px;
		background: var(--accent);
		color: #fff;
		border-radius: var(--radius-sm);
		text-decoration: none;
		font-size: 14px;
		font-weight: 600;
		transition: background var(--transition-fast);
	}

	.btn-explore:hover { background: var(--accent-hover); }
</style>
