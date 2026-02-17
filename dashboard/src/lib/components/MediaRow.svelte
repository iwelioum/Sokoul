<script lang="ts">
	import MediaCard from './MediaCard.svelte';
	import Skeleton from './Skeleton.svelte';
	import type { TmdbSearchItem } from '$lib/api/client';

	let {
		title,
		items = [] as TmdbSearchItem[],
		loading = false,
		seeMoreHref = null as string | null,
		playOnClick = false
	} = $props();

	const SKELETON_COUNT = 8;

	let scrollContainer: HTMLDivElement;

	function scrollLeft() {
		scrollContainer.scrollBy({ left: -600, behavior: 'smooth' });
	}

	function scrollRight() {
		scrollContainer.scrollBy({ left: 600, behavior: 'smooth' });
	}
</script>

<section class="media-row">
	<div class="row-header">
		<h2 class="row-title">{title}</h2>
		{#if seeMoreHref && !loading && items.length > 0}
			<a href={seeMoreHref} class="see-more">Voir plus →</a>
		{/if}
	</div>

	<div class="row-wrapper">
		<!-- Scroll buttons -->
		{#if !loading && items.length > 4}
			<button class="scroll-btn scroll-left" onclick={scrollLeft} aria-label="Défiler à gauche">
				<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
			</button>
		{/if}

		<div class="row-scroll" bind:this={scrollContainer}>
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
				{#each items as item (`${item.media_type}-${item.id}`)}
					<MediaCard {item} {playOnClick} />
				{/each}
			{/if}
		</div>

		{#if !loading && items.length > 4}
			<button class="scroll-btn scroll-right" onclick={scrollRight} aria-label="Défiler à droite">
				<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z"/></svg>
			</button>
		{/if}
	</div>
</section>

<style>
	.media-row {
		margin-bottom: 2rem;
		width: 100%;
		position: relative;
	}

	.row-header {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		margin-bottom: 16px;
		padding: 0;
	}

	.row-title {
		font-size: 22px;
		font-weight: 700;
		color: #F9F9F9;
		letter-spacing: 0.5px;
		font-family: 'Inter', sans-serif;
	}

	.see-more {
		font-size: 13px;
		color: #CACACA;
		text-decoration: none;
		transition: color 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.see-more:hover { color: #F9F9F9; }

	/* Wrapper pour contenir scroll + boutons */
	.row-wrapper {
		position: relative;
	}

	.row-scroll {
		display: flex;
		gap: 20px;
		overflow-x: auto;
		scroll-snap-type: x mandatory;
		scrollbar-width: none;
		-ms-overflow-style: none;
		padding: 10px 0 16px;
		scroll-behavior: smooth;
	}

	.row-scroll::-webkit-scrollbar {
		display: none;
	}

	.row-scroll > :global(*) {
		flex-shrink: 0;
		width: 220px;
		scroll-snap-align: start;
	}

	.skeleton-card {
		flex-shrink: 0;
		width: 220px;
	}

	.empty-msg {
		color: #CACACA;
		font-size: 14px;
		padding: 48px 16px;
		text-align: center;
		background: rgba(37, 40, 51, 0.7);
		border-radius: 10px;
		border: 1px dashed rgba(249, 249, 249, 0.1);
		width: 100%;
	}

	/* Scroll buttons - identique à BrandTiles */
	.scroll-btn {
		position: absolute;
		top: 50%;
		transform: translateY(-50%);
		z-index: 10;
		width: 44px;
		height: 44px;
		border-radius: 50%;
		border: none;
		background: rgba(26, 29, 41, 0.85);
		color: #F9F9F9;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		opacity: 0;
		transition: opacity 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		backdrop-filter: blur(4px);
	}

	.media-row:hover .scroll-btn {
		opacity: 1;
	}

	.scroll-btn:hover {
		background: rgba(249, 249, 249, 0.2);
	}

	.scroll-left { left: -16px; }
	.scroll-right { right: -16px; }

	@media (max-width: 900px) {
		.scroll-btn {
			display: none;
		}

		.row-scroll > :global(*),
		.skeleton-card {
			width: 160px;
		}

		.row-scroll {
			gap: 14px;
		}
	}

	@media (max-width: 500px) {
		.row-scroll > :global(*),
		.skeleton-card {
			width: 130px;
		}

		.row-scroll {
			gap: 12px;
		}
	}
</style>
