<script lang="ts">
	import type { StreamSource } from '$lib/api/client';

	let {
		sources = [] as StreamSource[],
		title = '',
		onClose
	}: {
		sources: StreamSource[];
		title: string;
		onClose: () => void;
	} = $props();

	let activeIndex = $state(0);
	let iframeKey = $state(0); // Force iframe reload on source switch

	const activeSource = $derived(sources[activeIndex] ?? null);

	function switchSource(index: number) {
		activeIndex = index;
		iframeKey++;
	}

	function handleOverlayKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') onClose();
	}

	function handleModalKeydown(e: KeyboardEvent) {
		e.stopPropagation();
	}

	const qualityColor: Record<string, string> = {
		'4K': '#00cec9',
		'Multi': '#6c5ce7',
		'HD': '#00b894',
		'SD': '#636e72',
	};
</script>

<!-- svelte-ignore a11y_interactive_supports_focus -->
<div
	class="player-overlay"
	role="dialog"
	aria-modal="true"
	aria-label="Lecteur vidéo - {title}"
	onclick={onClose}
	onkeydown={handleOverlayKeydown}
>
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<div
		class="player-modal"
		role="document"
		onclick={(e) => e.stopPropagation()}
		onkeydown={handleModalKeydown}
	>
		<!-- Header -->
		<div class="player-header">
			<div class="player-title">
				<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
					<path d="M8 5v14l11-7z"/>
				</svg>
				<span>{title}</span>
			</div>
			<button class="close-btn" onclick={onClose} aria-label="Fermer">
				<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
					<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
				</svg>
			</button>
		</div>

		<!-- Source tabs -->
		<div class="source-tabs">
			{#each sources as source, i (source.name)}
				<button
					class="source-tab {i === activeIndex ? 'active' : ''}"
					onclick={() => switchSource(i)}
				>
					<span class="source-name">{source.name}</span>
					<span
						class="quality-badge"
						style="background: {qualityColor[source.quality] ?? '#636e72'}"
					>
						{source.quality}
					</span>
				</button>
			{/each}
		</div>

		<!-- Player frame -->
		<div class="frame-container">
			{#if activeSource}
				{#key iframeKey}
					<iframe
						src={activeSource.url}
						title="{title} — {activeSource.name}"
						allowfullscreen
						allow="fullscreen; autoplay; encrypted-media"
						referrerpolicy="no-referrer"
						sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
					></iframe>
				{/key}
			{:else}
				<div class="no-source">Aucune source disponible.</div>
			{/if}
		</div>

		<!-- Hint -->
		<p class="player-hint">
			Si le lecteur ne fonctionne pas, essayez un autre serveur ci-dessus.
		</p>
	</div>
</div>

<style>
	.player-overlay {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0.92);
		backdrop-filter: blur(8px);
		z-index: 1000;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 16px;
	}

	.player-modal {
		background: var(--bg-secondary);
		border: 1px solid var(--border);
		border-radius: 16px;
		width: 100%;
		max-width: 1100px;
		max-height: 95vh;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		box-shadow: 0 24px 60px rgba(0,0,0,0.6);
	}

	.player-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 16px 20px 12px;
		border-bottom: 1px solid var(--border);
	}

	.player-title {
		display: flex;
		align-items: center;
		gap: 8px;
		color: var(--text-primary);
		font-weight: 600;
		font-size: 16px;
	}

	.close-btn {
		background: none;
		border: none;
		color: var(--text-secondary);
		cursor: pointer;
		padding: 4px;
		border-radius: 6px;
		display: flex;
		transition: color var(--transition-fast), background var(--transition-fast);
	}

	.close-btn:hover {
		color: var(--text-primary);
		background: var(--bg-hover);
	}

	.source-tabs {
		display: flex;
		gap: 6px;
		padding: 12px 16px;
		overflow-x: auto;
		border-bottom: 1px solid var(--border);
		scrollbar-width: none;
	}

	.source-tab {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 6px 14px;
		border-radius: 20px;
		border: 1px solid var(--border);
		background: var(--bg-card);
		color: var(--text-secondary);
		cursor: pointer;
		font-size: 13px;
		font-weight: 500;
		transition: all var(--transition-fast);
		white-space: nowrap;
	}

	.source-tab:hover {
		border-color: var(--accent);
		color: var(--text-primary);
	}

	.source-tab.active {
		background: var(--accent);
		border-color: var(--accent);
		color: #fff;
	}

	.quality-badge {
		font-size: 10px;
		padding: 2px 6px;
		border-radius: 4px;
		color: #fff;
		font-weight: 700;
	}

	.source-tab.active .quality-badge {
		background: rgba(255,255,255,0.25) !important;
	}

	.frame-container {
		flex: 1;
		position: relative;
		aspect-ratio: 16 / 9;
		background: #000;
		min-height: 0;
	}

	.frame-container iframe {
		width: 100%;
		height: 100%;
		border: none;
		display: block;
	}

	.no-source {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
		font-size: 14px;
	}

	.player-hint {
		padding: 10px 20px;
		font-size: 12px;
		color: var(--text-muted);
		text-align: center;
		border-top: 1px solid var(--border);
	}
</style>
