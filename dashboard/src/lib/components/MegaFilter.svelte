<script lang="ts">
	import { goto } from '$app/navigation';

	// ── Props ──
	interface Props {
		open: boolean;
		mediaType: 'movie' | 'tv' | 'all';
		selectedGenres: number[];
		selectedSort: string;
		yearMin: number;
		yearMax: number;
		selectedProvider: string | null;
		onApply: (filters: FilterState) => void;
		onClose: () => void;
	}

	export interface FilterState {
		mediaType: 'movie' | 'tv' | 'all';
		genres: number[];
		sort: string;
		yearMin: number;
		yearMax: number;
		provider: string | null;
		providerName: string | null;
	}

	let {
		open = $bindable(false),
		mediaType = $bindable('movie'),
		selectedGenres = $bindable([]),
		selectedSort = $bindable('popularity.desc'),
		yearMin = $bindable(1980),
		yearMax = $bindable(new Date().getFullYear()),
		selectedProvider = $bindable(null),
		onApply,
		onClose,
	}: Props = $props();

	// ── Données complètes ──
	const movieGenres = [
		{ id: 28, name: 'Action', icon: '💥' },
		{ id: 12, name: 'Aventure', icon: '🗺️' },
		{ id: 16, name: 'Animation', icon: '🎨' },
		{ id: 35, name: 'Comédie', icon: '😂' },
		{ id: 80, name: 'Crime', icon: '🔪' },
		{ id: 99, name: 'Documentaire', icon: '📹' },
		{ id: 18, name: 'Drame', icon: '🎭' },
		{ id: 10751, name: 'Famille', icon: '👨‍👩‍👧‍👦' },
		{ id: 14, name: 'Fantaisie', icon: '🧙' },
		{ id: 36, name: 'Histoire', icon: '📜' },
		{ id: 27, name: 'Horreur', icon: '👻' },
		{ id: 10402, name: 'Musique', icon: '🎵' },
		{ id: 9648, name: 'Mystère', icon: '🔍' },
		{ id: 10749, name: 'Romance', icon: '❤️' },
		{ id: 878, name: 'Science-Fiction', icon: '🚀' },
		{ id: 10770, name: 'Téléfilm', icon: '📺' },
		{ id: 53, name: 'Thriller', icon: '😱' },
		{ id: 10752, name: 'Guerre', icon: '⚔️' },
		{ id: 37, name: 'Western', icon: '🤠' },
	];

	const tvGenres = [
		{ id: 10759, name: 'Action & Aventure', icon: '💥' },
		{ id: 16, name: 'Animation', icon: '🎨' },
		{ id: 35, name: 'Comédie', icon: '😂' },
		{ id: 80, name: 'Crime', icon: '🔪' },
		{ id: 99, name: 'Documentaire', icon: '📹' },
		{ id: 18, name: 'Drame', icon: '🎭' },
		{ id: 10751, name: 'Famille', icon: '👨‍👩‍👧‍👦' },
		{ id: 10762, name: 'Enfants', icon: '🧒' },
		{ id: 9648, name: 'Mystère', icon: '🔍' },
		{ id: 10763, name: 'Actualités', icon: '📰' },
		{ id: 10764, name: 'Réalité', icon: '🎬' },
		{ id: 10765, name: 'Sci-Fi & Fantasy', icon: '🚀' },
		{ id: 10766, name: 'Soap', icon: '📺' },
		{ id: 10767, name: 'Talk Show', icon: '🎤' },
		{ id: 10768, name: 'Guerre & Politique', icon: '⚔️' },
		{ id: 37, name: 'Western', icon: '🤠' },
	];

	const sortOptions = [
		{ id: 'popularity.desc', name: 'Les plus populaires', icon: '🔥' },
		{ id: 'popularity.asc', name: 'Les moins populaires', icon: '📉' },
		{ id: 'vote_average.desc', name: 'Mieux notés', icon: '⭐' },
		{ id: 'vote_average.asc', name: 'Moins bien notés', icon: '📊' },
		{ id: 'release_date.desc', name: 'Plus récents', icon: '🆕' },
		{ id: 'release_date.asc', name: 'Plus anciens', icon: '📅' },
		{ id: 'revenue.desc', name: 'Plus gros revenus', icon: '💰' },
		{ id: 'original_title.asc', name: 'Titre (A→Z)', icon: '🔤' },
	];

	const tvSortOptions = [
		{ id: 'popularity.desc', name: 'Les plus populaires', icon: '🔥' },
		{ id: 'popularity.asc', name: 'Les moins populaires', icon: '📉' },
		{ id: 'vote_average.desc', name: 'Mieux notées', icon: '⭐' },
		{ id: 'vote_average.asc', name: 'Moins bien notées', icon: '📊' },
		{ id: 'first_air_date.desc', name: 'Plus récentes', icon: '🆕' },
		{ id: 'first_air_date.asc', name: 'Plus anciennes', icon: '📅' },
	];

	const providers = [
		{ id: '8', name: 'Netflix', color: '#E50914' },
		{ id: '337', name: 'Disney+', color: '#1A73E8' },
		{ id: '119', name: 'Amazon Prime', color: '#00A8E1' },
		{ id: '384', name: 'HBO Max', color: '#B535F6' },
		{ id: '531', name: 'Paramount+', color: '#0064FF' },
		{ id: '350', name: 'Apple TV+', color: '#555555' },
		{ id: '283', name: 'Crunchyroll', color: '#F47521' },
		{ id: '236', name: 'Canal+', color: '#1A1A1A' },
		{ id: '56', name: 'OCS', color: '#FF6600' },
	];

	const currentYear = new Date().getFullYear();

	// ── Derived ──
	let genres = $derived(mediaType === 'tv' ? tvGenres : movieGenres);
	let sorts = $derived(mediaType === 'tv' ? tvSortOptions : sortOptions);
	let activeFilterCount = $derived(
		selectedGenres.length +
		(selectedProvider ? 1 : 0) +
		(yearMin !== 1980 || yearMax !== currentYear ? 1 : 0) +
		(selectedSort !== 'popularity.desc' ? 1 : 0)
	);

	// ── Actions ──
	function toggleGenre(id: number) {
		if (selectedGenres.includes(id)) {
			selectedGenres = selectedGenres.filter(g => g !== id);
		} else {
			selectedGenres = [...selectedGenres, id];
		}
	}

	function toggleProvider(id: string) {
		selectedProvider = selectedProvider === id ? null : id;
	}

	function resetAll() {
		selectedGenres = [];
		selectedSort = 'popularity.desc';
		yearMin = 1980;
		yearMax = currentYear;
		selectedProvider = null;
	}

	function apply() {
		const provName = selectedProvider
			? providers.find(p => p.id === selectedProvider)?.name ?? null
			: null;

		onApply({
			mediaType,
			genres: selectedGenres,
			sort: selectedSort,
			yearMin,
			yearMax,
			provider: selectedProvider,
			providerName: provName,
		});

		open = false;
	}

	function handleOverlayClick() {
		open = false;
		onClose();
	}

	function switchMediaType(type: 'movie' | 'tv' | 'all') {
		mediaType = type;
		selectedGenres = [];
		if (type === 'tv' && selectedSort.includes('release_date')) {
			selectedSort = selectedSort.replace('release_date', 'first_air_date');
		} else if (type === 'movie' && selectedSort.includes('first_air_date')) {
			selectedSort = selectedSort.replace('first_air_date', 'release_date');
		}
	}
</script>

<!-- Overlay -->
{#if open}
	<div class="filter-overlay" onclick={handleOverlayClick} role="presentation"></div>
{/if}

<!-- Panel -->
<aside class="filter-panel" class:open>
	<div class="panel-header">
		<h2>
			<svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22"><path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"/></svg>
			Filtres
		</h2>
		<div class="panel-header-actions">
			{#if activeFilterCount > 0}
				<button class="btn-reset" onclick={resetAll}>
					Réinitialiser ({activeFilterCount})
				</button>
			{/if}
			<button class="btn-close" onclick={() => { open = false; onClose(); }} aria-label="Fermer">
				<svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
			</button>
		</div>
	</div>

	<div class="panel-body">

		<!-- Type de contenu -->
		<section class="filter-section">
			<h3 class="section-label">Type de contenu</h3>
			<div class="type-toggle">
				<button
					class="type-btn" class:active={mediaType === 'movie'}
					onclick={() => switchMediaType('movie')}
				>
					<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/></svg>
					Films
				</button>
				<button
					class="type-btn" class:active={mediaType === 'tv'}
					onclick={() => switchMediaType('tv')}
				>
					<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z"/></svg>
					Séries
				</button>
			</div>
		</section>

		<!-- Plateforme -->
		<section class="filter-section">
			<h3 class="section-label">Plateforme de streaming</h3>
			<div class="provider-grid">
				{#each providers as prov (prov.id)}
					<button
						class="provider-chip"
						class:active={selectedProvider === prov.id}
						style="--prov-color: {prov.color}"
						onclick={() => toggleProvider(prov.id)}
					>
						<span class="provider-dot"></span>
						{prov.name}
					</button>
				{/each}
			</div>
		</section>

		<!-- Genres -->
		<section class="filter-section">
			<h3 class="section-label">Genres <span class="count-badge">{selectedGenres.length > 0 ? selectedGenres.length : ''}</span></h3>
			<div class="genre-grid">
				{#each genres as genre (genre.id)}
					<button
						class="genre-chip"
						class:active={selectedGenres.includes(genre.id)}
						onclick={() => toggleGenre(genre.id)}
					>
						<span class="genre-icon">{genre.icon}</span>
						{genre.name}
					</button>
				{/each}
			</div>
		</section>

		<!-- Année -->
		<section class="filter-section">
			<h3 class="section-label">Année de sortie</h3>
			<div class="year-range">
				<div class="year-inputs">
					<div class="year-field">
						<label for="year-min">De</label>
						<input id="year-min" type="number" min="1900" max={currentYear} bind:value={yearMin} />
					</div>
					<span class="year-separator">—</span>
					<div class="year-field">
						<label for="year-max">À</label>
						<input id="year-max" type="number" min="1900" max={currentYear} bind:value={yearMax} />
					</div>
				</div>
				<input
					type="range" min="1900" max={currentYear}
					bind:value={yearMin}
					class="range-slider"
				/>
				<div class="year-presets">
					<button class="preset-btn" onclick={() => { yearMin = currentYear; yearMax = currentYear; }}>Cette année</button>
					<button class="preset-btn" onclick={() => { yearMin = currentYear - 5; yearMax = currentYear; }}>5 dernières années</button>
					<button class="preset-btn" onclick={() => { yearMin = 2000; yearMax = 2010; }}>2000s</button>
					<button class="preset-btn" onclick={() => { yearMin = 1990; yearMax = 1999; }}>90s</button>
					<button class="preset-btn" onclick={() => { yearMin = 1980; yearMax = 1989; }}>80s</button>
					<button class="preset-btn" onclick={() => { yearMin = 1900; yearMax = currentYear; }}>Toutes</button>
				</div>
			</div>
		</section>

		<!-- Tri -->
		<section class="filter-section">
			<h3 class="section-label">Trier par</h3>
			<div class="sort-grid">
				{#each sorts as sort (sort.id)}
					<button
						class="sort-chip"
						class:active={selectedSort === sort.id}
						onclick={() => { selectedSort = sort.id; }}
					>
						<span class="sort-icon">{sort.icon}</span>
						{sort.name}
					</button>
				{/each}
			</div>
		</section>

	</div>

	<!-- Footer fixe -->
	<div class="panel-footer">
		<button class="btn-apply" onclick={apply}>
			<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
			Appliquer les filtres
			{#if activeFilterCount > 0}
				<span class="filter-count">{activeFilterCount}</span>
			{/if}
		</button>
	</div>
</aside>

<style>
	/* ── Overlay ── */
	.filter-overlay {
		position: fixed;
		inset: 0;
		background: rgba(0, 0, 0, 0.6);
		z-index: 998;
		backdrop-filter: blur(4px);
		animation: fadeIn 250ms ease;
	}

	/* ── Panel ── */
	.filter-panel {
		position: fixed;
		top: 0;
		right: 0;
		width: 420px;
		max-width: 90vw;
		height: 100vh;
		background: #1A1D29;
		border-left: 1px solid rgba(249, 249, 249, 0.08);
		z-index: 999;
		display: flex;
		flex-direction: column;
		transform: translateX(100%);
		transition: transform 300ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		box-shadow: -10px 0 40px rgba(0, 0, 0, 0.5);
	}

	.filter-panel.open {
		transform: translateX(0);
	}

	/* ── Header ── */
	.panel-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20px 24px;
		border-bottom: 1px solid rgba(249, 249, 249, 0.08);
		flex-shrink: 0;
	}

	.panel-header h2 {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 18px;
		font-weight: 700;
		color: #F9F9F9;
		margin: 0;
	}

	.panel-header-actions {
		display: flex;
		align-items: center;
		gap: 10px;
	}

	.btn-reset {
		padding: 6px 12px;
		background: rgba(0, 114, 210, 0.15);
		border: 1px solid rgba(0, 114, 210, 0.3);
		border-radius: 6px;
		color: #0072D2;
		font-size: 12px;
		font-weight: 600;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.btn-reset:hover {
		background: rgba(0, 114, 210, 0.25);
	}

	.btn-close {
		width: 36px;
		height: 36px;
		border-radius: 8px;
		background: rgba(249, 249, 249, 0.06);
		border: none;
		color: #CACACA;
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		padding: 0;
	}

	.btn-close:hover { background: rgba(249, 249, 249, 0.12); color: #F9F9F9; }

	/* ── Body scrollable ── */
	.panel-body {
		flex: 1;
		overflow-y: auto;
		padding: 8px 24px 24px;
		scrollbar-width: thin;
		scrollbar-color: rgba(249,249,249,0.15) transparent;
	}

	/* ── Section ── */
	.filter-section {
		padding: 18px 0;
		border-bottom: 1px solid rgba(249, 249, 249, 0.05);
	}

	.filter-section:last-child { border-bottom: none; }

	.section-label {
		font-size: 13px;
		font-weight: 600;
		color: #CACACA;
		text-transform: uppercase;
		letter-spacing: 1.2px;
		margin: 0 0 14px 0;
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.count-badge {
		font-size: 11px;
		background: #0072D2;
		color: #fff;
		border-radius: 10px;
		padding: 1px 7px;
		font-weight: 700;
	}

	/* ── Type toggle ── */
	.type-toggle {
		display: flex;
		gap: 10px;
	}

	.type-btn {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		padding: 12px;
		border-radius: 10px;
		background: rgba(249, 249, 249, 0.04);
		border: 2px solid rgba(249, 249, 249, 0.08);
		color: #CACACA;
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.type-btn:hover {
		background: rgba(249, 249, 249, 0.08);
		border-color: rgba(249, 249, 249, 0.15);
	}

	.type-btn.active {
		background: rgba(0, 114, 210, 0.15);
		border-color: #0072D2;
		color: #F9F9F9;
	}

	/* ── Provider grid ── */
	.provider-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}

	.provider-chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 8px 14px;
		border-radius: 20px;
		background: rgba(249, 249, 249, 0.04);
		border: 1px solid rgba(249, 249, 249, 0.1);
		color: #CACACA;
		font-size: 13px;
		font-weight: 500;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.provider-dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--prov-color);
	}

	.provider-chip:hover {
		background: rgba(249, 249, 249, 0.08);
		border-color: rgba(249, 249, 249, 0.2);
	}

	.provider-chip.active {
		background: rgba(249, 249, 249, 0.1);
		border-color: var(--prov-color);
		color: #F9F9F9;
		box-shadow: 0 0 12px color-mix(in srgb, var(--prov-color) 30%, transparent);
	}

	/* ── Genre grid ── */
	.genre-grid {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}

	.genre-chip {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 8px 14px;
		border-radius: 8px;
		background: rgba(249, 249, 249, 0.04);
		border: 1px solid rgba(249, 249, 249, 0.08);
		color: #CACACA;
		font-size: 13px;
		font-weight: 500;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.genre-icon { font-size: 15px; }

	.genre-chip:hover {
		background: rgba(249, 249, 249, 0.08);
		border-color: rgba(249, 249, 249, 0.15);
	}

	.genre-chip.active {
		background: rgba(0, 114, 210, 0.15);
		border-color: rgba(0, 114, 210, 0.5);
		color: #F9F9F9;
	}

	/* ── Year range ── */
	.year-range {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}

	.year-inputs {
		display: flex;
		align-items: center;
		gap: 12px;
	}

	.year-field {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}

	.year-field label {
		font-size: 11px;
		color: rgba(249,249,249,0.4);
		text-transform: uppercase;
		letter-spacing: 1px;
	}

	.year-field input[type="number"] {
		width: 100%;
		padding: 10px 12px;
		background: rgba(249, 249, 249, 0.06);
		border: 1px solid rgba(249, 249, 249, 0.1);
		border-radius: 8px;
		color: #F9F9F9;
		font-size: 15px;
		font-weight: 600;
		font-family: inherit;
		text-align: center;
	}

	.year-field input[type="number"]:focus {
		outline: none;
		border-color: #0072D2;
		box-shadow: 0 0 0 3px rgba(0, 114, 210, 0.15);
	}

	.year-separator {
		color: rgba(249,249,249,0.3);
		font-size: 18px;
		padding-top: 18px;
	}

	.range-slider {
		width: 100%;
		height: 4px;
		-webkit-appearance: none;
		appearance: none;
		background: rgba(249, 249, 249, 0.1);
		border-radius: 2px;
		outline: none;
	}

	.range-slider::-webkit-slider-thumb {
		-webkit-appearance: none;
		width: 18px;
		height: 18px;
		border-radius: 50%;
		background: #0072D2;
		cursor: pointer;
		border: 2px solid #F9F9F9;
		box-shadow: 0 2px 8px rgba(0,0,0,0.4);
	}

	.range-slider::-moz-range-thumb {
		width: 18px;
		height: 18px;
		border-radius: 50%;
		background: #0072D2;
		cursor: pointer;
		border: 2px solid #F9F9F9;
		box-shadow: 0 2px 8px rgba(0,0,0,0.4);
	}

	.year-presets {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.preset-btn {
		padding: 5px 12px;
		border-radius: 14px;
		background: rgba(249, 249, 249, 0.04);
		border: 1px solid rgba(249, 249, 249, 0.08);
		color: #CACACA;
		font-size: 12px;
		font-weight: 500;
		cursor: pointer;
		transition: all 200ms ease;
	}

	.preset-btn:hover {
		background: rgba(0, 114, 210, 0.15);
		border-color: rgba(0, 114, 210, 0.3);
		color: #F9F9F9;
	}

	/* ── Sort grid ── */
	.sort-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}

	.sort-chip {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 14px;
		border-radius: 8px;
		background: rgba(249, 249, 249, 0.04);
		border: 1px solid rgba(249, 249, 249, 0.08);
		color: #CACACA;
		font-size: 13px;
		font-weight: 500;
		cursor: pointer;
		text-align: left;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.sort-icon { font-size: 15px; }

	.sort-chip:hover {
		background: rgba(249, 249, 249, 0.08);
		border-color: rgba(249, 249, 249, 0.15);
	}

	.sort-chip.active {
		background: rgba(0, 114, 210, 0.15);
		border-color: rgba(0, 114, 210, 0.5);
		color: #F9F9F9;
	}

	/* ── Footer ── */
	.panel-footer {
		padding: 16px 24px;
		border-top: 1px solid rgba(249, 249, 249, 0.08);
		flex-shrink: 0;
	}

	.btn-apply {
		width: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 10px;
		padding: 14px;
		background: #0072D2;
		border: none;
		border-radius: 10px;
		color: #fff;
		font-size: 15px;
		font-weight: 700;
		letter-spacing: 0.5px;
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.btn-apply:hover {
		background: #0585F2;
		transform: scale(1.02);
		box-shadow: 0 8px 24px rgba(0, 114, 210, 0.35);
	}

	.filter-count {
		background: rgba(255, 255, 255, 0.25);
		border-radius: 10px;
		padding: 2px 8px;
		font-size: 12px;
		font-weight: 700;
	}

	@keyframes fadeIn {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	/* ── Mobile ── */
	@media (max-width: 500px) {
		.filter-panel {
			width: 100vw;
			max-width: 100vw;
		}

		.sort-grid {
			grid-template-columns: 1fr;
		}
	}
</style>
