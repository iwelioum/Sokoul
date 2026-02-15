<script lang="ts">
	import MediaCard from './MediaCard.svelte';
	import Skeleton from './Skeleton.svelte';
	import type { TmdbSearchItem } from '$lib/api/client';

	let {
		title,
		items = [] as TmdbSearchItem[],
		loading = false,
		seeMoreHref = null as string | null
	} = $props();

	const SKELETON_COUNT = 8;
</script>

<section class="media-row">
	<div class="row-header">
		<h2 class="row-title">{title}</h2>
		{#if seeMoreHref && !loading && items.length > 0}
			<a href={seeMoreHref} class="see-more">Voir plus →</a>
		{/if}
	</div>

	<div class="row-scroll">
		{#if loading}
			{#each { length: SKELETON_COUNT } as _, i (i)}
				<div class="skeleton-card">
					<Skeleton variant="card" />
					<div style="margin-top:8px; display:flex; flex-direction:column; gap:4px;">
						<Skeleton height="12px" width="80%" />
						<Skeleton height="10px" width="50%" />
					</div>
				</div>
			{/each}
		{:else if items.length === 0}
			<p class="empty-msg">Aucun contenu disponible.</p>
		{:else}
			{#each items as item (item.id)}
				<MediaCard {item} />
			{/each}
		{/if}
	</div>
</section>

<style>
	.media-row {
		margin-bottom: 2rem;
	}

	.row-header {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin-bottom: 12px;
		padding: 0 4px;
	}

	.row-title {
		font-size: 18px;
		font-weight: 700;
		color: var(--text-primary);
		letter-spacing: -0.3px;
	}

	.see-more {
		font-size: 13px;
		color: var(--accent);
		text-decoration: none;
		transition: color var(--transition-fast);
	}

	.see-more:hover { color: var(--accent-hover); }

	.row-scroll {
		display: flex;
		gap: 12px;
		overflow-x: auto;
		padding: 4px 4px 12px;
		scroll-snap-type: x mandatory;
		scrollbar-width: thin;
		-webkit-overflow-scrolling: touch;
	}

	.row-scroll > :global(*) {
		scroll-snap-align: start;
	}

	.skeleton-card {
		flex-shrink: 0;
		width: 160px;
	}

	.empty-msg {
		color: var(--text-muted);
		font-size: 14px;
		padding: 16px 0;
	}
</style>
