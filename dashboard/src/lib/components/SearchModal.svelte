<script lang="ts">
	import { goto } from '$app/navigation';
	import { tmdbSearch, tmdbImageUrl, getItemTitle, getItemYear } from '$lib/api/client';
	import type { TmdbSearchItem } from '$lib/api/client';

	let {
		open = $bindable(false),
		onClose
	}: {
		open: boolean;
		onClose: () => void;
	} = $props();

	let query = $state('');
	let results: TmdbSearchItem[] = $state([]);
	let loading = $state(false);
	let selectedIndex = $state(0);
	let inputEl: HTMLInputElement | null = $state(null);
	let debounceTimer: ReturnType<typeof setTimeout>;

	// Focus input when modal opens
	$effect(() => {
		if (open) {
			selectedIndex = 0;
			query = '';
			results = [];
			setTimeout(() => inputEl?.focus(), 50);
		}
	});

	function handleInput() {
		clearTimeout(debounceTimer);
		selectedIndex = 0;
		if (!query.trim()) {
			results = [];
			loading = false;
			return;
		}
		loading = true;
		debounceTimer = setTimeout(async () => {
			try {
				results = await tmdbSearch(query);
			} catch {
				results = [];
			}
			loading = false;
		}, 300);
	}

	function selectResult(item: TmdbSearchItem) {
		const type = item.media_type ?? 'movie';
		goto(`/${type}/${item.id}`);
		close();
	}

	function close() {
		query = '';
		results = [];
		loading = false;
		open = false;
		onClose();
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			close();
		} else if (e.key === 'ArrowDown') {
			e.preventDefault();
			selectedIndex = Math.min(selectedIndex + 1, results.length - 1);
		} else if (e.key === 'ArrowUp') {
			e.preventDefault();
			selectedIndex = Math.max(selectedIndex - 1, 0);
		} else if (e.key === 'Enter' && results[selectedIndex]) {
			selectResult(results[selectedIndex]);
		}
	}

	function handleOverlayClick(e: MouseEvent) {
		if (e.target === e.currentTarget) close();
	}
</script>

{#if open}
	<!-- svelte-ignore a11y_interactive_supports_focus -->
	<div
		class="search-overlay"
		role="dialog"
		aria-modal="true"
		aria-label="Recherche globale"
		onclick={handleOverlayClick}
		onkeydown={handleKeydown}
	>
		<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
		<div
			class="search-modal"
			role="document"
			onclick={(e) => e.stopPropagation()}
			onkeydown={handleKeydown}
		>
			<!-- Search input -->
			<div class="search-input-wrap">
				<svg class="search-icon" viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
					<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
				</svg>
				<input
					bind:this={inputEl}
					bind:value={query}
					oninput={handleInput}
					type="text"
					placeholder="Rechercher un film ou une série..."
					class="search-input"
					autocomplete="off"
				/>
				{#if query}
					<button class="clear-btn" onclick={() => { query = ''; results = []; inputEl?.focus(); }} aria-label="Effacer">
						<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
							<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
						</svg>
					</button>
				{/if}
				<kbd class="esc-hint">Échap</kbd>
			</div>

			<!-- Results -->
			<div class="results-list" role="listbox">
				{#if loading}
					<div class="search-status">
						<span class="spinner"></span> Recherche...
					</div>
				{:else if query && results.length === 0}
					<div class="search-status">Aucun résultat pour « {query} »</div>
				{:else if !query}
					<div class="search-hint">
						<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
							<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
						</svg>
						<span>Tapez pour rechercher films et séries</span>
					</div>
				{:else}
					{#each results as item, i (item.id)}
						{@const isSelected = i === selectedIndex}
						{@const poster = tmdbImageUrl(item.poster_path, 'w92')}
						{@const itemTitle = getItemTitle(item)}
						{@const year = getItemYear(item)}
						<!-- svelte-ignore a11y_interactive_supports_focus -->
						<!-- svelte-ignore a11y_click_events_have_key_events -->
						<div
							class="result-item {isSelected ? 'selected' : ''}"
							role="option"
							aria-selected={isSelected}
							onclick={() => selectResult(item)}
							onmouseenter={() => { selectedIndex = i; }}
						>
							<div class="result-poster">
								{#if poster}
									<img src={poster} alt={itemTitle} loading="lazy" />
								{:else}
									<div class="no-poster-sm">
										<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
											<path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z"/>
										</svg>
									</div>
								{/if}
							</div>

							<div class="result-info">
								<span class="result-title">{itemTitle}</span>
								<div class="result-meta">
									{#if year}<span>{year}</span>{/if}
									<span class="result-type">{item.media_type === 'tv' ? 'Série' : 'Film'}</span>
									{#if item.vote_average && item.vote_average > 0}
										<span class="result-rating">★ {item.vote_average.toFixed(1)}</span>
									{/if}
								</div>
							</div>

							<svg class="result-arrow" viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
								<path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z"/>
							</svg>
						</div>
					{/each}
				{/if}
			</div>

			<!-- Footer -->
			<div class="search-footer">
				<span><kbd>↑</kbd><kbd>↓</kbd> naviguer</span>
				<span><kbd>↵</kbd> sélectionner</span>
				<span><kbd>Échap</kbd> fermer</span>
			</div>
		</div>
	</div>
{/if}

<style>
	.search-overlay {
		position: fixed;
		inset: 0;
		background: rgba(0,0,0,0.7);
		backdrop-filter: blur(6px);
		z-index: 2000;
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 80px 16px 16px;
	}

	.search-modal {
		background: var(--bg-secondary);
		border: 1px solid var(--border);
		border-radius: 16px;
		width: 100%;
		max-width: 620px;
		overflow: hidden;
		box-shadow: 0 24px 60px rgba(0,0,0,0.6);
		animation: slideDown 0.15s ease;
	}

	@keyframes slideDown {
		from { opacity: 0; transform: translateY(-12px); }
		to { opacity: 1; transform: translateY(0); }
	}

	.search-input-wrap {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 14px 16px;
		border-bottom: 1px solid var(--border);
	}

	.search-icon { color: var(--text-muted); flex-shrink: 0; }

	.search-input {
		flex: 1;
		background: none;
		border: none;
		outline: none;
		color: var(--text-primary);
		font-size: 16px;
		caret-color: var(--accent);
	}

	.search-input::placeholder { color: var(--text-muted); }

	.clear-btn {
		background: none;
		border: none;
		color: var(--text-muted);
		cursor: pointer;
		padding: 2px;
		display: flex;
		transition: color var(--transition-fast);
	}

	.clear-btn:hover { color: var(--text-primary); }

	.esc-hint {
		font-size: 11px;
		color: var(--text-muted);
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 4px;
		padding: 2px 6px;
		flex-shrink: 0;
	}

	.results-list {
		max-height: 400px;
		overflow-y: auto;
	}

	.search-status {
		padding: 24px 16px;
		color: var(--text-muted);
		font-size: 14px;
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.search-hint {
		padding: 24px 16px;
		color: var(--text-muted);
		font-size: 14px;
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.result-item {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 10px 16px;
		cursor: pointer;
		transition: background var(--transition-fast);
	}

	.result-item:hover, .result-item.selected {
		background: var(--bg-hover);
	}

	.result-poster {
		width: 40px;
		height: 60px;
		border-radius: 6px;
		overflow: hidden;
		flex-shrink: 0;
		background: var(--bg-card);
	}

	.result-poster img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.no-poster-sm {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
	}

	.result-info {
		flex: 1;
		min-width: 0;
	}

	.result-title {
		font-size: 14px;
		font-weight: 500;
		color: var(--text-primary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		display: block;
	}

	.result-meta {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-top: 3px;
		font-size: 12px;
		color: var(--text-secondary);
	}

	.result-type {
		background: rgba(108,92,231,0.2);
		color: var(--accent);
		padding: 1px 6px;
		border-radius: 4px;
		font-weight: 500;
	}

	.result-rating { color: var(--warning); }

	.result-arrow {
		color: var(--text-muted);
		flex-shrink: 0;
		opacity: 0;
		transition: opacity var(--transition-fast);
	}

	.result-item:hover .result-arrow,
	.result-item.selected .result-arrow { opacity: 1; }

	.search-footer {
		display: flex;
		gap: 16px;
		padding: 10px 16px;
		border-top: 1px solid var(--border);
		font-size: 11px;
		color: var(--text-muted);
	}

	.search-footer span {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	kbd {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: 3px;
		padding: 1px 5px;
		font-size: 10px;
		color: var(--text-secondary);
	}

	.spinner {
		width: 14px;
		height: 14px;
		border: 2px solid var(--border);
		border-top-color: var(--accent);
		border-radius: 50%;
		animation: spin 0.7s linear infinite;
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}
</style>
