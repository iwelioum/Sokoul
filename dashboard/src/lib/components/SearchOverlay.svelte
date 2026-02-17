<script lang="ts">
	import { tmdbSearch } from '$lib/api/client';
	import type { TmdbSearchItem } from '$lib/api/client';
	import { goto } from '$app/navigation';

	let {
		open = $bindable(false),
		onClose = () => {}
	}: {
		open?: boolean;
		onClose?: () => void;
	} = $props();

	let query = $state('');
	let results: TmdbSearchItem[] = $state([]);
	let loading = $state(false);
	let inputRef: HTMLInputElement;

	async function handleSearch() {
		if (!query.trim()) {
			results = [];
			return;
		}

		loading = true;
		try {
			const res = await tmdbSearch(query);
			results = res.results.slice(0, 8);
		} catch (error) {
			console.error('Search failed:', error);
			results = [];
		} finally {
			loading = false;
		}
	}

	function handleItemClick(item: TmdbSearchItem) {
		const type = item.media_type === 'tv' ? 'tv' : 'movie';
		goto(`/${type}/${item.id}`);
		close();
	}

	function close() {
		open = false;
		query = '';
		results = [];
		onClose();
	}

	$effect(() => {
		if (open && inputRef) {
			setTimeout(() => inputRef?.focus(), 100);
		}
	});

	$effect(() => {
		if (query) {
			const timeout = setTimeout(handleSearch, 300);
			return () => clearTimeout(timeout);
		} else {
			results = [];
		}
	});
</script>

{#if open}
	<!-- Overlay -->
	<div class="search-overlay" onclick={close} role="presentation"></div>

	<!-- Panel -->
	<div class="search-panel">
		<div class="search-header">
			<div class="search-input-wrap">
				<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24" class="search-icon">
					<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
				</svg>
				<input
					bind:this={inputRef}
					type="text"
					placeholder="Rechercher des films, séries..."
					bind:value={query}
					class="search-input"
					autocomplete="off"
				/>
				{#if query}
					<button class="clear-btn" onclick={() => { query = ''; results = []; }}>
						<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
							<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
						</svg>
					</button>
				{/if}
			</div>
			<button class="close-btn" onclick={close}>
				<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
					<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
				</svg>
			</button>
		</div>

		<div class="search-body">
			{#if loading}
				<div class="search-loading">
					<div class="spinner"></div>
					<p>Recherche en cours...</p>
				</div>
			{:else if query && results.length === 0}
				<div class="search-empty">
					<svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" style="opacity:0.3">
						<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
					</svg>
					<p>Aucun résultat pour « {query} »</p>
				</div>
			{:else if results.length > 0}
				<div class="search-results">
					{#each results as item (item.id)}
						<button class="result-item" onclick={() => handleItemClick(item)}>
							<div class="result-poster">
								{#if item.poster_path}
									<img src="https://image.tmdb.org/t/p/w92{item.poster_path}" alt={item.title || item.name || ''} />
								{:else}
									<div class="no-poster">
										<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
											<path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/>
										</svg>
									</div>
								{/if}
							</div>
							<div class="result-info">
								<p class="result-title">{item.title || item.name || 'Sans titre'}</p>
								<div class="result-meta">
									<span class="result-type">{item.media_type === 'tv' ? 'Série' : 'Film'}</span>
									{#if item.release_date || item.first_air_date}
										<span class="result-year">{(item.release_date || item.first_air_date || '').slice(0, 4)}</span>
									{/if}
									{#if item.vote_average}
										<span class="result-rating">⭐ {item.vote_average.toFixed(1)}</span>
									{/if}
								</div>
							</div>
						</button>
					{/each}
				</div>
			{:else}
				<div class="search-placeholder">
					<svg viewBox="0 0 24 24" fill="currentColor" width="64" height="64" style="opacity:0.2">
						<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
					</svg>
					<p>Recherchez des films, séries, acteurs...</p>
					<p class="hint">Tapez pour commencer</p>
				</div>
			{/if}
		</div>
	</div>
{/if}

<style>
	.search-overlay {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0.85);
		z-index: 9998;
		backdrop-filter: blur(8px);
		animation: fadeIn 200ms ease;
	}

	.search-panel {
		position: fixed;
		top: 0;
		left: 50%;
		transform: translateX(-50%);
		width: 90%;
		max-width: 720px;
		height: 100vh;
		background: #1A1D29;
		z-index: 9999;
		display: flex;
		flex-direction: column;
		animation: slideDown 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.search-header {
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 24px;
		border-bottom: 1px solid rgba(249, 249, 249, 0.08);
		flex-shrink: 0;
	}

	.search-input-wrap {
		flex: 1;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 20px;
		background: rgba(249, 249, 249, 0.06);
		border: 2px solid rgba(249, 249, 249, 0.1);
		border-radius: 12px;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.search-input-wrap:focus-within {
		background: rgba(249, 249, 249, 0.08);
		border-color: #0072D2;
	}

	.search-icon {
		color: #CACACA;
		flex-shrink: 0;
	}

	.search-input {
		flex: 1;
		background: none;
		border: none;
		outline: none;
		color: #F9F9F9;
		font-size: 18px;
		font-weight: 500;
		font-family: inherit;
	}

	.search-input::placeholder {
		color: rgba(249, 249, 249, 0.4);
	}

	.clear-btn {
		background: rgba(249, 249, 249, 0.08);
		border: none;
		border-radius: 6px;
		width: 32px;
		height: 32px;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #CACACA;
		cursor: pointer;
		transition: all 200ms ease;
	}

	.clear-btn:hover {
		background: rgba(249, 249, 249, 0.15);
		color: #F9F9F9;
	}

	.close-btn {
		background: rgba(249, 249, 249, 0.06);
		border: none;
		border-radius: 10px;
		width: 44px;
		height: 44px;
		display: flex;
		align-items: center;
		justify-content: center;
		color: #CACACA;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		flex-shrink: 0;
	}

	.close-btn:hover {
		background: rgba(249, 249, 249, 0.12);
		color: #F9F9F9;
	}

	.search-body {
		flex: 1;
		overflow-y: auto;
		padding: 20px 24px;
	}

	.search-loading,
	.search-empty,
	.search-placeholder {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
		padding: 80px 20px;
		color: #CACACA;
	}

	.spinner {
		width: 40px;
		height: 40px;
		border: 3px solid rgba(249, 249, 249, 0.1);
		border-top-color: #0072D2;
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}

	.hint {
		font-size: 14px;
		opacity: 0.6;
	}

	.search-results {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}

	.result-item {
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 12px;
		background: rgba(249, 249, 249, 0.04);
		border: 1px solid rgba(249, 249, 249, 0.08);
		border-radius: 10px;
		cursor: pointer;
		text-align: left;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		width: 100%;
	}

	.result-item:hover {
		background: rgba(249, 249, 249, 0.08);
		border-color: rgba(249, 249, 249, 0.15);
		transform: translateX(4px);
	}

	.result-poster {
		width: 60px;
		height: 90px;
		border-radius: 6px;
		overflow: hidden;
		background: rgba(249, 249, 249, 0.06);
		flex-shrink: 0;
	}

	.result-poster img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.no-poster {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: rgba(249, 249, 249, 0.3);
	}

	.result-info {
		flex: 1;
		min-width: 0;
	}

	.result-title {
		font-size: 16px;
		font-weight: 600;
		color: #F9F9F9;
		margin: 0 0 6px 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.result-meta {
		display: flex;
		align-items: center;
		gap: 12px;
		font-size: 13px;
		color: #CACACA;
	}

	.result-type {
		background: rgba(0, 114, 210, 0.15);
		color: #0072D2;
		padding: 2px 8px;
		border-radius: 4px;
		font-size: 11px;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	@keyframes fadeIn {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	@keyframes slideDown {
		from {
			opacity: 0;
			transform: translateX(-50%) translateY(-20px);
		}
		to {
			opacity: 1;
			transform: translateX(-50%) translateY(0);
		}
	}

	@keyframes spin {
		to { transform: rotate(360deg); }
	}

	@media (max-width: 768px) {
		.search-panel {
			width: 100%;
			max-width: none;
		}

		.search-input {
			font-size: 16px;
		}
	}
</style>
