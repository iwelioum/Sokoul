<script lang="ts">
	import { goto } from '$app/navigation';
	import { tmdbImageUrl } from '$lib/api/client';
	import type { TmdbSearchItem, WatchHistoryEntry } from '$lib/api/client';

	let {
		item,
		history = null as WatchHistoryEntry | null,
		size = 'md' as 'sm' | 'md' | 'lg',
		inLibrary = false as boolean
	}: {
		item: TmdbSearchItem;
		history?: WatchHistoryEntry | null;
		size?: 'sm' | 'md' | 'lg';
		inLibrary?: boolean;
	} = $props();

	const title = $derived(item.title ?? item.name ?? '');
	const year = $derived((item.release_date ?? item.first_air_date ?? '').substring(0, 4));
	const poster = $derived(tmdbImageUrl(item.poster_path, 'w342'));
	const mediaType = $derived(item.media_type ?? 'movie');
	const rating = $derived(item.vote_average);
	const progressPercent = $derived(history ? Math.min(Math.round((history.progress ?? 0) * 100), 100) : 0);

	function handleClick() {
		goto(`/${mediaType}/${item.id}`);
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			handleClick();
		}
	}
</script>

<a
	href={`/${mediaType}/${item.id}`}
	class="media-card size-{size}"
	role="button"
	aria-label={title}
>
	<div class="poster-wrap">
		{#if poster}
			<img src={poster} alt={title} loading="lazy" />
		{:else}
			<div class="no-poster">
				<svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
					<path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 8.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5zm6 7H6v-.75c0-2 4-3.1 6-3.1s6 1.1 6 3.1V18.5z"/>
				</svg>
			</div>
		{/if}

		{#if rating}
			<div class="rating-badge">
				<svg viewBox="0 0 24 24" fill="currentColor" width="10" height="10">
					<path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
				</svg>
				{rating.toFixed(1)}
			</div>
		{/if}

		<div class="type-badge">{mediaType === 'tv' ? 'Série' : 'Film'}</div>

		{#if inLibrary}
			<div class="lib-badge" title="Dans ma bibliothèque">♥</div>
		{/if}

		{#if progressPercent > 0}
			<div class="progress-bar-wrap">
				<div class="progress-bar" style="width:{progressPercent}%"></div>
			</div>
		{/if}

		<div class="overlay">
			<svg viewBox="0 0 24 24" fill="currentColor" width="36" height="36">
				<path d="M8 5v14l11-7z"/>
			</svg>
		</div>
	</div>

	<div class="info">
		<p class="card-title">{title}</p>
		{#if year}
			<span class="card-year">{year}</span>
		{/if}
	</div>
</a>

<style>
	.media-card {
		cursor: pointer;
		flex-shrink: 0;
		transition: transform var(--transition-smooth), opacity var(--transition-fast);
		outline: none;
	}

	.media-card:hover { transform: scale(1.05); z-index: 2; }
	.media-card:focus-visible { outline: 2px solid var(--accent); border-radius: var(--radius); }

	.size-sm { width: 120px; }
	.size-md { width: 160px; }
	.size-lg { width: 200px; }

	.poster-wrap {
		position: relative;
		aspect-ratio: 2 / 3;
		border-radius: var(--radius);
		overflow: hidden;
		background: var(--bg-surface);
		box-shadow: var(--shadow-dark);
		transition: all var(--transition-smooth);
	}

	.media-card:hover .poster-wrap {
		box-shadow: var(--shadow-dark-md);
		transform: scale(1.05);
		border: 1px solid rgba(255, 255, 255, 0.2);
	}

	.poster-wrap img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
		transition: transform var(--transition-smooth);
	}

	.media-card:hover .poster-wrap img { transform: scale(1.08); }

	.no-poster {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
		background: var(--bg-secondary);
	}

	.rating-badge {
		position: absolute;
		top: 6px;
		left: 6px;
		background: rgba(0,0,0,0.75);
		backdrop-filter: blur(4px);
		color: var(--warning);
		font-size: 11px;
		font-weight: 600;
		padding: 3px 6px;
		border-radius: 6px;
		display: flex;
		align-items: center;
		gap: 3px;
	}

	.type-badge {
		position: absolute;
		top: 6px;
		right: 6px;
		background: rgba(108,92,231,0.85);
		color: #fff;
		font-size: 10px;
		font-weight: 600;
		padding: 2px 6px;
		border-radius: 4px;
	}

	.lib-badge {
		position: absolute;
		bottom: 30px;
		right: 6px;
		background: rgba(192, 74, 53, 0.85);
		color: #fff;
		font-size: 11px;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		backdrop-filter: blur(4px);
	}

	.progress-bar-wrap {
		position: absolute;
		bottom: 0;
		left: 0;
		right: 0;
		height: 3px;
		background: rgba(255,255,255,0.2);
	}

	.progress-bar {
		height: 100%;
		background: var(--accent);
		transition: width 0.3s;
	}

	.overlay {
		position: absolute;
		inset: 0;
		background: rgba(0,0,0,0.5);
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		opacity: 0;
		transition: opacity var(--transition-smooth);
	}

	.media-card:hover .overlay { opacity: 1; }

	.info {
		margin-top: 8px;
		padding: 0 2px;
	}

	.card-title {
		font-size: 13px;
		font-weight: 500;
		color: var(--text-primary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		line-height: 1.3;
	}

	.card-year {
		font-size: 11px;
		color: var(--text-muted);
	}
</style>
