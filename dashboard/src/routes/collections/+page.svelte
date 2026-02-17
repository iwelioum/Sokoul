<script lang="ts">
	import { onMount } from 'svelte';

	interface Collection {
		id: number;
		name: string;
		description: string;
		image: string;
	}

	// Example collections - later this can be fetched from TMDB or backend
	let collections: Collection[] = $state([
		{
			id: 1,
			name: "Harry Potter",
			description: "La saga complète Harry Potter",
			image: "/collections/harry-potter.jpg"
		},
		{
			id: 2,
			name: "Avatar",
			description: "L'univers Avatar",
			image: "/collections/avatar.jpg"
		},
		{
			id: 3,
			name: "Marvel Cinematic Universe",
			description: "Toutes les phases de l'univers Marvel",
			image: "/collections/mcu.jpg"
		},
		{
			id: 4,
			name: "Star Wars",
			description: "La saga Star Wars complète",
			image: "/collections/star-wars.jpg"
		},
		{
			id: 5,
			name: "Lord of the Rings",
			description: "Le Seigneur des Anneaux et le Hobbit",
			image: "/collections/lotr.jpg"
		},
		{
			id: 6,
			name: "DC Universe",
			description: "Les héros de DC Comics",
			image: "/collections/dc.jpg"
		}
	]);
</script>

<svelte:head>
	<title>Collections — SOKOUL</title>
</svelte:head>

<div class="collections-page">
	<div class="page-header">
		<h1>Collections</h1>
		<p class="subtitle">Explorez les sagas et univers cinématographiques</p>
	</div>

	<div class="collections-grid">
		{#each collections as collection (collection.id)}
			<a href="/collection/{collection.id}" class="collection-card">
				<div class="collection-image">
					<div class="collection-placeholder">
						<svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48">
							<path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/>
						</svg>
					</div>
				</div>
				<div class="collection-info">
					<h3 class="collection-name">{collection.name}</h3>
					<p class="collection-desc">{collection.description}</p>
				</div>
			</a>
		{/each}
	</div>
</div>

<style>
	.collections-page {
		padding: calc(var(--nav-height, 70px) + 40px) 36px 80px;
		min-height: 100vh;
	}

	.page-header {
		margin-bottom: 48px;
	}

	.page-header h1 {
		font-size: 48px;
		font-weight: 700;
		color: #F9F9F9;
		margin: 0 0 12px 0;
		letter-spacing: 0.5px;
	}

	.subtitle {
		font-size: 18px;
		color: #CACACA;
		margin: 0;
	}

	.collections-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
		gap: 28px;
	}

	.collection-card {
		display: block;
		background: linear-gradient(180deg, #30343E 0%, #252833 100%);
		border: 3px solid rgba(249, 249, 249, 0.1);
		border-radius: 10px;
		overflow: hidden;
		text-decoration: none;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		box-shadow: rgb(0 0 0 / 69%) 0px 26px 30px -10px, rgb(0 0 0 / 73%) 0px 16px 10px -10px;
	}

	.collection-card:hover {
		transform: scale(1.05);
		border-color: #F9F9F9;
		box-shadow: rgb(0 0 0 / 80%) 0px 40px 58px -16px;
	}

	.collection-image {
		position: relative;
		width: 100%;
		aspect-ratio: 16/9;
		background: rgba(26, 29, 41, 0.6);
		overflow: hidden;
	}

	.collection-placeholder {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: rgba(249, 249, 249, 0.3);
	}

	.collection-info {
		padding: 20px 24px;
	}

	.collection-name {
		font-size: 20px;
		font-weight: 600;
		color: #F9F9F9;
		margin: 0 0 8px 0;
	}

	.collection-desc {
		font-size: 14px;
		color: #CACACA;
		margin: 0;
		line-height: 1.5;
	}

	@media (max-width: 900px) {
		.collections-page {
			padding: calc(var(--nav-height, 70px) + 24px) 20px 80px;
		}

		.page-header h1 {
			font-size: 36px;
		}

		.collections-grid {
			grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
			gap: 20px;
		}
	}

	@media (max-width: 600px) {
		.page-header h1 {
			font-size: 28px;
		}

		.collections-grid {
			grid-template-columns: 1fr;
		}
	}
</style>
