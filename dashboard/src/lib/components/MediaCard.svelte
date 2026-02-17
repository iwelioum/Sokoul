<script lang="ts">
	import { tmdbImageUrl } from '$lib/api/client';
	import type { TmdbSearchItem, WatchHistoryEntry } from '$lib/api/client';

	let {
		item,
		history = null as WatchHistoryEntry | null,
		size = 'md' as 'sm' | 'md' | 'lg',
		inLibrary = false as boolean,
		playOnClick = false as boolean
	}: {
		item: TmdbSearchItem;
		history?: WatchHistoryEntry | null;
		size?: 'sm' | 'md' | 'lg';
		inLibrary?: boolean;
		playOnClick?: boolean;
	} = $props();

	const title = $derived(item.title ?? item.name ?? '');
	const year = $derived((item.release_date ?? item.first_air_date ?? '').substring(0, 4));
	const backdrop = $derived(tmdbImageUrl(item.backdrop_path, 'w780'));
	const poster = $derived(tmdbImageUrl(item.poster_path, 'w342'));
	const cardImage = $derived(backdrop || poster);
	const mediaType = $derived(
		item.media_type === 'tv' || item.media_type === 'movie' || item.media_type === 'person'
			? item.media_type
			: (item.first_air_date && !item.release_date ? 'tv' : 'movie')
	);
	const isPlayableMedia = $derived(mediaType === 'tv' || mediaType === 'movie');
	const detailHref = $derived(`/${mediaType}/${item.id}`);
	const watchHref = $derived(
		mediaType === 'tv'
			? `/watch/tv/${item.id}?season=1&episode=1`
			: mediaType === 'movie'
				? `/watch/movie/${item.id}`
				: detailHref
	);
	const cardHref = $derived(playOnClick && isPlayableMedia ? watchHref : detailHref);
	const mediaLabel = $derived(mediaType === 'tv' ? 'Série' : mediaType === 'movie' ? 'Film' : 'Personne');
	const rating = $derived(item.vote_average);
	const progressPercent = $derived(history ? Math.min(Math.round((history.progress ?? 0) * 100), 100) : 0);
</script>

<a
	href={cardHref}
	class="media-card size-{size}"
	aria-label={title}
>
	<div class="poster-wrap">
		{#if cardImage}
			<img src={cardImage} alt={title} loading="lazy" />
		{:else}
			<div class="no-poster">
				<svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
					<path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 8.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5zm6 7H6v-.75c0-2 4-3.1 6-3.1s6 1.1 6 3.1V18.5z"/>
				</svg>
			</div>
		{/if}

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
		display: block;
		cursor: pointer;
		flex-shrink: 0;
		text-decoration: none;
		color: inherit;
		transition: transform 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		outline: none;
	}

	.media-card:hover { transform: scale(1.05); z-index: 2; }
	.media-card:focus-visible { outline: 2px solid rgba(249, 249, 249, 0.8); border-radius: 4px; }

	.size-sm { width: 100%; }
	.size-md { width: 100%; }
	.size-lg { width: 100%; }

	.poster-wrap {
		position: relative;
		aspect-ratio: 16 / 9;
		border-radius: 4px;
		overflow: hidden;
		background: var(--bg-surface);
		box-shadow: rgb(0 0 0 / 69%) 0px 26px 30px -10px, rgb(0 0 0 / 73%) 0px 16px 10px -10px;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		border: 3px solid transparent;
	}

	.media-card:hover .poster-wrap {
		box-shadow: 0 40px 58px -16px rgba(0,0,0,0.72);
		border-color: #F9F9F9;
	}

	.poster-wrap img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
		transition: transform 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.media-card:hover .poster-wrap img { transform: scale(1.05); }

	.no-poster {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
		background: #252833;
	}

	.lib-badge {
		position: absolute;
		bottom: 10px;
		right: 8px;
		background: rgba(0, 114, 210, 0.9);
		color: #fff;
		font-size: 11px;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
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
		background: rgba(0,0,0,0.45);
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
		opacity: 0;
		pointer-events: none;
		transition: opacity 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.media-card:hover .overlay { opacity: 1; }

	.info {
		margin-top: 8px;
		padding: 0 2px;
	}

	.card-title {
		font-size: 13px;
		font-weight: 600;
		color: #f9f9f9;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		line-height: 1.3;
	}

	.card-year {
		font-size: 11px;
		color: rgba(249, 249, 249, 0.5);
	}
</style>
