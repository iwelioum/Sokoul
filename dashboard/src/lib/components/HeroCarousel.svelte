<script lang="ts">
	import type { TmdbSearchItem } from '$lib/api/client';
	import { tmdbImageUrl } from '$lib/api/client';
	import { getDominantColor, getTextColor, getOverlayOpacity, getGradientMaskOpacity } from '$lib/utils/colorAnalysis';

	interface Props {
		items: TmdbSearchItem[];
		onPlay: (item: TmdbSearchItem) => void;
		onDetails: (item: TmdbSearchItem) => void;
		loading?: boolean;
	}

	let { items = [], onPlay, onDetails, loading = false }: Props = $props();

	// State
	let currentIndex = $state(0);
	let isAutoPlaying = $state(true);
	let autoPlayTimer: ReturnType<typeof setTimeout> | null = null;
	let textColorMode = $state<'light' | 'dark'>('light');
	let overlayOpacity = $state(0.3);
	let clearLogos = $state<Map<number, string>>(new Map());

	// Derived
	const currentSlide = $derived(items[currentIndex] ?? null);
	const heroBackdrop = $derived(
		currentSlide?.backdrop_path ? tmdbImageUrl(currentSlide.backdrop_path, 'original') : null
	);
	const heroClearLogo = $derived(currentSlide?.id ? clearLogos.get(currentSlide.id) : null);
	const heroTitle = $derived(currentSlide?.title || currentSlide?.name || 'Titre indisponible');
	const heroOverview = $derived(currentSlide?.overview || '');
	const heroRating = $derived(currentSlide?.vote_average ?? null);
	const heroYear = $derived(
		currentSlide?.release_date?.split('-')[0] || currentSlide?.first_air_date?.split('-')[0] || ''
	);
	const heroType = $derived(currentSlide?.media_type === 'tv' ? 'SÉRIE' : 'FILM');

	// Auto-play logic
	$effect(() => {
		if (!isAutoPlaying || items.length === 0) {
			if (autoPlayTimer) clearTimeout(autoPlayTimer);
			return;
		}

		if (autoPlayTimer) clearTimeout(autoPlayTimer);

		autoPlayTimer = setTimeout(() => {
			goToSlide((currentIndex + 1) % items.length);
		}, 8000);

		return () => {
			if (autoPlayTimer) clearTimeout(autoPlayTimer);
		};
	});

	// Analyze image colors when slide changes
	$effect(async () => {
		if (!heroBackdrop) return;

		const color = await getDominantColor(heroBackdrop);
		if (color) {
			textColorMode = getTextColor(color.luminance);
			overlayOpacity = getOverlayOpacity(color.luminance);
		}
	});

	// Functions
	function goToSlide(index: number) {
		currentIndex = Math.max(0, Math.min(index, items.length - 1));
		isAutoPlaying = true;
	}

	function nextSlide() {
		goToSlide((currentIndex + 1) % items.length);
	}

	function prevSlide() {
		goToSlide((currentIndex - 1 + items.length) % items.length);
	}

	function handleMouseEnter() {
		isAutoPlaying = false;
		if (autoPlayTimer) clearTimeout(autoPlayTimer);
	}

	function handleMouseLeave() {
		isAutoPlaying = true;
	}

	function handlePlayClick() {
		if (currentSlide) {
			isAutoPlaying = false;
			onPlay(currentSlide);
		}
	}

	function handleDetailsClick() {
		if (currentSlide) {
			isAutoPlaying = false;
			onDetails(currentSlide);
		}
	}

	// ── Touch / drag swipe ──
	let touchStartX = 0;
	let touchStartY = 0;
	let isDragging = false;

	function handlePointerDown(e: PointerEvent) {
		touchStartX = e.clientX;
		touchStartY = e.clientY;
		isDragging = true;
	}

	function handlePointerUp(e: PointerEvent) {
		if (!isDragging) return;
		isDragging = false;
		const dx = e.clientX - touchStartX;
		const dy = Math.abs(e.clientY - touchStartY);
		// Only swipe if horizontal movement > 50px and greater than vertical
		if (Math.abs(dx) > 50 && Math.abs(dx) > dy) {
			if (dx < 0) nextSlide();
			else prevSlide();
		}
	}
</script>

<div
	class="hero-carousel"
	class:text-dark={textColorMode === 'dark'}
	class:text-light={textColorMode === 'light'}
	style={heroBackdrop ? `--backdrop-image: url('${heroBackdrop}'); --overlay-opacity: ${overlayOpacity}` : ''}
	onmouseenter={handleMouseEnter}
	onmouseleave={handleMouseLeave}
	onpointerdown={handlePointerDown}
	onpointerup={handlePointerUp}
	role="region"
	aria-label="Hero carousel"
>
	<!-- Slides -->
	{#each items as item, idx (`${item.media_type}-${item.id}`)}
		<div class="hero-carousel-slide" class:active={idx === currentIndex}>
			{#if item.backdrop_path}
				<img
					src={tmdbImageUrl(item.backdrop_path, 'original')}
					alt={item.title || item.name}
					class="hero-carousel-image"
				/>
			{/if}
		</div>
	{/each}

	<!-- Gradient masks -->
	<div class="hero-carousel-mask"></div>

	<!-- Content -->
	{#if currentSlide && !loading}
		<div class="hero-carousel-content">
			<div class="hero-carousel-meta">
				<span class="meta-type">{heroType}</span>
				{#if heroRating}
					<span class="meta-rating">★ {heroRating.toFixed(1)}</span>
				{/if}
				{#if heroYear}
					<span class="meta-year">{heroYear}</span>
				{/if}
			</div>

			<h1 class="hero-carousel-title">{heroTitle}</h1>

			{#if heroOverview}
				<p class="hero-carousel-overview">{heroOverview}</p>
			{/if}

			<div class="hero-carousel-actions">
				<button class="btn-primary" onclick={handlePlayClick}>
					<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
						<path d="M8 5v14l11-7z" />
					</svg>
					Regarder
				</button>
				<button class="btn-secondary" onclick={handleDetailsClick}>
					<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
						<path
							d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"
						/>
					</svg>
					Plus d'infos
				</button>
			</div>
		</div>
	{/if}

	<!-- Navigation buttons -->
	{#if items.length > 1}
		<button
			class="hero-carousel-nav prev"
			onclick={prevSlide}
			aria-label="Slide précédent"
			disabled={loading}
		>
			<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
				<path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" />
			</svg>
		</button>
		<button
			class="hero-carousel-nav next"
			onclick={nextSlide}
			aria-label="Slide suivant"
			disabled={loading}
		>
			<svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24">
				<path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" />
			</svg>
		</button>
	{/if}

	<!-- Progress indicators -->
	{#if items.length > 1}
		<div class="hero-carousel-indicators">
			{#each items as item, idx (`${item.media_type}-${item.id}`)}
				<div
					class="hero-carousel-indicator"
					class:active={idx === currentIndex}
					role="button"
					tabindex="0"
					onclick={() => goToSlide(idx)}
					onkeydown={(e) => {
						if (e.key === 'Enter' || e.key === ' ') {
							e.preventDefault();
							goToSlide(idx);
						}
					}}
					aria-label={`Aller au slide ${idx + 1}`}
				>
					{#if idx === currentIndex}
						<div class="hero-carousel-indicator-fill"></div>
					{/if}
				</div>
			{/each}
		</div>
	{/if}
</div>

<style>
	.hero-carousel {
		position: relative;
		width: 100vw;
		height: 85vh;
		min-height: 520px;
		overflow: hidden;
		border-radius: 0;
		background: #1A1D29;
	}

	.hero-carousel-slide {
		position: absolute;
		width: 100%;
		height: 100%;
		opacity: 0;
		transition: opacity var(--transition-slow);
	}

	.hero-carousel-slide.active {
		opacity: 1;
		z-index: 1;
	}

	.hero-carousel-image {
		width: 100%;
		height: 100%;
		object-fit: cover;
		object-position: center top;
		display: block;
		transition: transform var(--transition-slow);
	}

	.hero-carousel-slide.active .hero-carousel-image {
		animation: kenBurns 8s ease-in-out forwards;
	}

	@keyframes kenBurns {
		from { transform: scale(1); }
		to { transform: scale(1.05); }
	}

	.hero-carousel-mask {
		position: absolute;
		inset: 0;
		background:
			linear-gradient(to top, #1A1D29 8%, transparent 60%),
			linear-gradient(to right, rgba(26, 29, 41, 0.7) 0%, transparent 30%);
		pointer-events: none;
		z-index: 2;
	}

	.hero-carousel-content {
		position: absolute;
		bottom: 20%;
		left: 4%;
		right: 4%;
		max-width: 600px;
		z-index: 3;
		color: white;
		text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
		transition: color var(--transition-smooth);
	}

	/* Dark image: light text */
	.hero-carousel.text-light .hero-carousel-content {
		color: white;
		text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
	}

	/* Bright image: dark text */
	.hero-carousel.text-dark .hero-carousel-content {
		color: var(--text-primary);
		text-shadow: 0 1px 4px rgba(255, 255, 255, 0.3);
	}

	.hero-carousel.text-dark .btn-primary {
		background: var(--accent);
		color: white;
	}

	.hero-carousel.text-dark .btn-secondary {
		border-color: var(--text-primary);
		color: var(--text-primary);
	}

	.hero-carousel-meta {
		display: flex;
		gap: 16px;
		font-size: 16px;
		margin-bottom: 16px;
		opacity: 0.95;
	}

	.meta-type {
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.meta-rating,
	.meta-year {
		display: flex;
		align-items: center;
	}

	.hero-carousel-title {
		font-family: 'Inter', sans-serif;
		font-size: 56px;
		font-weight: 800;
		line-height: 1.1;
		margin-bottom: 12px;
	}

	.hero-carousel-logo {
		max-width: 400px;
		max-height: 150px;
		object-fit: contain;
		margin-bottom: 16px;
		filter: drop-shadow(0 2px 8px rgba(0, 0, 0, 0.4));
	}

	.hero-carousel-overview {
		font-size: 16px;
		line-height: 1.6;
		max-width: 500px;
		margin-bottom: 20px;
		opacity: 0.9;
	}

	.hero-carousel-actions {
		display: flex;
		gap: 12px;
		align-items: center;
	}

	.hero-carousel-actions :global(button) {
		padding: 12px 28px;
		font-size: 15px;
		border-radius: 4px;
		display: flex;
		gap: 8px;
		align-items: center;
		text-transform: uppercase;
		letter-spacing: 1.5px;
	}

	.hero-carousel {
		touch-action: pan-y;
		user-select: none;
	}

	.hero-carousel-nav {
		position: absolute;
		top: 50%;
		transform: translateY(-50%);
		background: rgba(0, 0, 0, 0.3);
		color: white;
		border: none;
		padding: 16px 20px;
		border-radius: 8px;
		cursor: pointer;
		opacity: 0.6;
		transition: opacity var(--transition-smooth), background var(--transition-smooth);
		z-index: 3;
		font-size: 20px;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.hero-carousel:hover .hero-carousel-nav {
		opacity: 0.9;
	}

	.hero-carousel-nav:hover:not(:disabled) {
		background: rgba(0, 0, 0, 0.4);
	}

	.hero-carousel-nav:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}

	.hero-carousel-nav.prev {
		left: 40px;
	}

	.hero-carousel-nav.next {
		right: 40px;
	}

	.hero-carousel-indicators {
		position: absolute;
		bottom: 20px;
		right: 60px;
		display: flex;
		gap: 8px;
		z-index: 4;
	}

	.hero-carousel-indicator {
		width: 60px;
		height: 4px;
		background: rgba(255, 255, 255, 0.3);
		border-radius: 2px;
		cursor: pointer;
		transition: background var(--transition-smooth);
		overflow: hidden;
	}

	.hero-carousel-indicator:hover {
		background: rgba(255, 255, 255, 0.5);
	}

	.hero-carousel-indicator.active {
		background: rgba(255, 255, 255, 0.8);
	}

	.hero-carousel-indicator-fill {
		height: 100%;
		background: white;
		width: 0;
		border-radius: 2px;
		animation: fillProgress 8s linear forwards;
	}

	@keyframes fillProgress {
		from {
			width: 0%;
		}
		to {
			width: 100%;
		}
	}

	/* Responsive */
	@media (max-width: 1024px) {
		.hero-carousel {
			height: 70vh;
			min-height: 420px;
		}

		.hero-carousel-content {
			bottom: 14%;
			left: 4%;
			right: 4%;
		}

		.hero-carousel-title {
			font-size: 40px;
		}

		.hero-carousel-overview {
			max-width: 400px;
			font-size: 14px;
		}

		.hero-carousel-indicators {
			right: 4%;
		}

		.hero-carousel-nav.prev {
			left: 20px;
		}

		.hero-carousel-nav.next {
			right: 20px;
		}
	}

	@media (max-width: 768px) {
		.hero-carousel {
			height: 55vh;
			min-height: 320px;
		}

		.hero-carousel-content {
			bottom: 12%;
			left: 4%;
			right: 4%;
		}

		.hero-carousel-title {
			font-size: 28px;
		}

		.hero-carousel-overview {
			display: none;
		}

		.hero-carousel-indicators {
			right: 4%;
			bottom: 12px;
		}

		.hero-carousel-actions :global(button) {
			padding: 10px 20px;
			font-size: 14px;
		}

		.hero-carousel-nav {
			padding: 12px 16px;
			font-size: 16px;
		}
	}
</style>
