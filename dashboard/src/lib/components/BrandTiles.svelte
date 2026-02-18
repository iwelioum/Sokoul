<script lang="ts">
	const brands = [
		{ name: 'Netflix', logo: '/brands/netflix.svg', provider: '8' },
		{ name: 'Disney+', logo: '/brands/disney.svg', provider: '337' },
		{ name: 'Amazon Prime', logo: '/brands/prime.svg', provider: '119' },
		{ name: 'HBO', logo: '/brands/hbo.svg', provider: '384' },
		{ name: 'Apple TV+', logo: '/brands/appletv.svg', provider: '350' },
		{ name: 'Paramount+', logo: '/brands/paramount.svg', provider: '531' },
		{ name: 'Pixar', logo: '/brands/pixar.svg', provider: '337' },
		{ name: 'National Geographic', logo: '/brands/natgeo.svg', provider: '337' },
		{ name: 'Warner Bros.', logo: '/brands/warner.svg', provider: '8' },
	];

	let scrollContainer: HTMLDivElement;

	function scrollLeft() {
		scrollContainer.scrollBy({ left: -320, behavior: 'smooth' });
	}

	function scrollRight() {
		scrollContainer.scrollBy({ left: 320, behavior: 'smooth' });
	}
</script>

<section class="brand-tiles-section">
	<button class="scroll-btn scroll-left" onclick={scrollLeft} aria-label="Défiler à gauche">
		<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>
	</button>

	<div class="brand-tiles-scroll" bind:this={scrollContainer}>
		{#each brands as brand (brand.name)}
			<a
				href="/films?provider={brand.provider}&provider_name={encodeURIComponent(brand.name)}"
				class="brand-card"
				title={brand.name}
			>
				<img src={brand.logo} alt={brand.name} class="brand-logo" />
			</a>
		{/each}
	</div>

	<button class="scroll-btn scroll-right" onclick={scrollRight} aria-label="Défiler à droite">
		<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z"/></svg>
	</button>
</section>

<style>
	.brand-tiles-section {
		position: relative;
		padding: 20px calc(3.5vw + 5px) 0;
	}

	.brand-tiles-scroll {
		display: flex;
		gap: 25px;
		overflow-x: auto;
		scroll-snap-type: x mandatory;
		scrollbar-width: none;
		-ms-overflow-style: none;
		padding: 10px 0 16px;
	}

	.brand-tiles-scroll::-webkit-scrollbar {
		display: none;
	}

	.brand-card {
		flex-shrink: 0;
		width: 220px;
		aspect-ratio: 16 / 9;
		background: linear-gradient(180deg, #30343E 0%, #252833 100%);
		border: 3px solid rgba(249, 249, 249, 0.1);
		border-radius: 10px;
		box-shadow: rgb(0 0 0 / 69%) 0px 26px 30px -10px, rgb(0 0 0 / 73%) 0px 16px 10px -10px;
		display: flex;
		justify-content: center;
		align-items: center;
		cursor: pointer;
		overflow: hidden;
		text-decoration: none;
		scroll-snap-align: start;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.brand-card:hover {
		transform: scale(1.05);
		border-color: rgba(249, 249, 249, 1);
		box-shadow: rgb(0 0 0 / 80%) 0px 40px 58px -16px;
	}

	.brand-logo {
		width: 70%;
		max-height: 60%;
		object-fit: contain;
		opacity: 0.85;
		transition: opacity 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.brand-card:hover .brand-logo {
		opacity: 1;
	}

	/* Scroll buttons */
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

	.brand-tiles-section:hover .scroll-btn {
		opacity: 1;
	}

	.scroll-btn:hover {
		background: rgba(249, 249, 249, 0.2);
	}

	.scroll-left { left: calc(3.5vw - 16px); }
	.scroll-right { right: calc(3.5vw - 16px); }

	@media (max-width: 768px) {
		.brand-card {
			width: 170px;
		}

		.brand-tiles-scroll {
			gap: 16px;
		}

		.scroll-btn {
			display: none;
		}
	}

	@media (max-width: 500px) {
		.brand-card {
			width: 140px;
			border-width: 2px;
		}

		.brand-tiles-scroll {
			gap: 12px;
		}
	}
</style>
