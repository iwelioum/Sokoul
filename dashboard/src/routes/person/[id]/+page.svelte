<script lang="ts">
	import { page } from '$app/stores';
	import { tmdbPerson, tmdbPersonCredits, tmdbImageUrl } from '$lib/api/client';
	import type { TmdbPersonDetail, TmdbPersonCredit } from '$lib/api/client';
	import Skeleton from '$lib/components/Skeleton.svelte';

	const personId = $derived(Number($page.params.id));

	let person: TmdbPersonDetail | null = $state(null);
	let credits: TmdbPersonCredit[] = $state([]);
	let loading = $state(true);
	let error = $state('');
	let bioExpanded = $state(false);

	const BIO_LIMIT = 600;
	const shortBio = $derived.by((): string => {
		const bio = person?.biography;
		if (!bio) return '';
		return bio.length > BIO_LIMIT ? bio.substring(0, BIO_LIMIT) + '…' : bio;
	});

	// Sort credits by date desc, deduplicate by id+media_type
	const sortedCredits = $derived.by(() => {
		const seen = new Set<string>();
		return credits
			.filter(c => {
				const key = `${c.id}-${c.media_type}`;
				if (seen.has(key)) return false;
				seen.add(key);
				return true;
			})
			.sort((a, b) => {
				const da = a.release_date ?? a.first_air_date ?? '';
				const db2 = b.release_date ?? b.first_air_date ?? '';
				return db2.localeCompare(da);
			});
	});

	$effect(() => {
		if (personId) loadAll(personId);
	});

	async function loadAll(id: number) {
		loading = true;
		error = '';
		try {
			const [p, c] = await Promise.allSettled([
				tmdbPerson(id),
				tmdbPersonCredits(id),
			]);
			if (p.status === 'fulfilled') person = p.value;
			else { error = 'Impossible de charger cet artiste.'; loading = false; return; }
			if (c.status === 'fulfilled') credits = c.value;
		} catch {
			error = 'Erreur lors du chargement.';
		}
		loading = false;
	}
</script>

<svelte:head>
	<title>{person ? `${person.name} — SOKOUL` : 'Chargement...'}</title>
</svelte:head>

{#if error}
	<div class="error-state">
		<p>{error}</p>
		<a href="/" class="btn-back">← Retour à l'accueil</a>
	</div>
{:else if loading}
	<div class="person-skeleton">
		<div class="person-header-skeleton">
			<Skeleton height="200px" width="140px" borderRadius="50%" />
			<div style="flex:1; display:flex; flex-direction:column; gap:12px;">
				<Skeleton height="32px" width="40%" />
				<Skeleton height="16px" width="60%" />
				<Skeleton height="16px" width="50%" />
			</div>
		</div>
		<div style="margin-top:32px; display:flex; flex-direction:column; gap:10px;">
			<Skeleton height="14px" width="100%" />
			<Skeleton height="14px" width="90%" />
			<Skeleton height="14px" width="95%" />
		</div>
	</div>
{:else if person}
	<div class="person-page">

		<!-- Header -->
		<div class="person-header">
			<div class="person-photo-wrap">
				{#if person.profile_path}
					<img class="person-photo" src={tmdbImageUrl(person.profile_path, 'w342')} alt={person.name} />
				{:else}
					<div class="person-no-photo">
						<svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48">
							<path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
						</svg>
					</div>
				{/if}
			</div>

			<div class="person-info">
				<h1 class="person-name">{person.name}</h1>

				{#if person.known_for_department}
					<p class="person-department">{person.known_for_department}</p>
				{/if}

				<div class="person-meta">
					{#if person.birthday}
						<div class="meta-item">
							<span class="meta-label">Naissance</span>
							<span>{person.birthday}</span>
							{#if person.place_of_birth}
								<span class="meta-sep">·</span>
								<span>{person.place_of_birth}</span>
							{/if}
						</div>
					{/if}
					{#if person.deathday}
						<div class="meta-item">
							<span class="meta-label">Décès</span>
							<span>{person.deathday}</span>
						</div>
					{/if}
				</div>
			</div>
		</div>

		<!-- Biography -->
		{#if person.biography}
			<section class="detail-section">
				<h2 class="section-title">Biographie</h2>
				<p class="bio-text">
					{bioExpanded ? person.biography : shortBio}
				</p>
				{#if person.biography.length > BIO_LIMIT}
					<button class="bio-toggle" onclick={() => bioExpanded = !bioExpanded}>
						{bioExpanded ? 'Voir moins' : 'Voir plus'}
					</button>
				{/if}
			</section>
		{/if}

		<!-- Filmography -->
		{#if sortedCredits.length > 0}
			<section class="detail-section">
				<h2 class="section-title">Filmographie <span class="credits-count">({sortedCredits.length})</span></h2>
				<div class="credits-grid">
					{#each sortedCredits as credit (credit.id + '-' + credit.media_type)}
						<a
							href="/{credit.media_type}/{credit.id}"
							class="credit-card"
						>
							<div class="credit-poster">
								{#if credit.poster_path}
									<img src={tmdbImageUrl(credit.poster_path, 'w185')} alt={credit.title ?? credit.name} loading="lazy" />
								{:else}
									<div class="credit-no-poster">
										<svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
											<path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/>
										</svg>
									</div>
								{/if}
								<span class="credit-type-badge">{credit.media_type === 'tv' ? 'Série' : 'Film'}</span>
							</div>
							<div class="credit-info">
								<p class="credit-title">{credit.title ?? credit.name ?? '—'}</p>
								{#if credit.character}
									<p class="credit-character">{credit.character}</p>
								{/if}
								{#if credit.release_date || credit.first_air_date}
									<p class="credit-year">
										{(credit.release_date ?? credit.first_air_date ?? '').substring(0, 4)}
									</p>
								{/if}
							</div>
						</a>
					{/each}
				</div>
			</section>
		{/if}

	</div>
{/if}

<style>
	.error-state {
		padding: 60px 24px;
		text-align: center;
		color: var(--text-secondary);
	}

	.btn-back {
		display: inline-block;
		margin-top: 16px;
		color: var(--accent);
		text-decoration: none;
	}

	/* ── Skeleton ── */
	.person-skeleton { padding: 32px; }
	.person-header-skeleton {
		display: flex;
		align-items: center;
		gap: 28px;
	}

	/* ── Header ── */
	.person-page { padding: 32px 0; }

	.person-header {
		display: flex;
		align-items: flex-start;
		gap: 32px;
		margin-bottom: 40px;
	}

	.person-photo-wrap { flex-shrink: 0; }

	.person-photo {
		width: 160px;
		height: 240px;
		border-radius: var(--radius);
		object-fit: cover;
		box-shadow: 0 8px 32px rgba(0,0,0,0.4);
	}

	.person-no-photo {
		width: 160px;
		height: 240px;
		border-radius: var(--radius);
		background: var(--bg-card);
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
	}

	.person-info { flex: 1; min-width: 0; padding-top: 8px; }

	.person-name {
		font-size: clamp(24px, 3vw, 38px);
		font-weight: 800;
		color: var(--text-primary);
		margin-bottom: 6px;
	}

	.person-department {
		font-size: 14px;
		color: var(--accent);
		font-weight: 600;
		margin-bottom: 16px;
	}

	.person-meta { display: flex; flex-direction: column; gap: 6px; }

	.meta-item {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 13px;
		color: var(--text-secondary);
	}

	.meta-label {
		color: var(--text-muted);
		font-weight: 600;
		min-width: 70px;
	}

	.meta-sep { color: var(--text-muted); }

	/* ── Bio ── */
	.detail-section { margin-bottom: 40px; }

	.section-title {
		font-size: 18px;
		font-weight: 700;
		color: var(--text-primary);
		margin-bottom: 14px;
	}

	.credits-count {
		font-size: 14px;
		font-weight: 400;
		color: var(--text-muted);
	}

	.bio-text {
		color: var(--text-secondary);
		font-size: 15px;
		line-height: 1.8;
		max-width: 820px;
		white-space: pre-line;
	}

	.bio-toggle {
		margin-top: 10px;
		background: none;
		border: none;
		color: var(--accent);
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
		padding: 0;
	}

	.bio-toggle:hover { text-decoration: underline; }

	/* ── Filmography ── */
	.credits-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
		gap: 16px;
	}

	.credit-card {
		display: flex;
		flex-direction: column;
		text-decoration: none;
		color: inherit;
		transition: transform var(--transition-fast);
	}

	.credit-card:hover { transform: translateY(-4px); }

	.credit-poster {
		position: relative;
		aspect-ratio: 2 / 3;
		border-radius: var(--radius-sm);
		overflow: hidden;
		background: var(--bg-card);
		margin-bottom: 8px;
	}

	.credit-poster img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.credit-no-poster {
		width: 100%;
		height: 100%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-muted);
	}

	.credit-type-badge {
		position: absolute;
		top: 6px;
		left: 6px;
		background: rgba(0,0,0,0.7);
		color: #fff;
		font-size: 10px;
		padding: 2px 6px;
		border-radius: 4px;
	}

	.credit-info { padding: 0 2px; }

	.credit-title {
		font-size: 12px;
		font-weight: 600;
		color: var(--text-primary);
		overflow: hidden;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		margin-bottom: 3px;
	}

	.credit-character {
		font-size: 11px;
		color: var(--text-secondary);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.credit-year {
		font-size: 11px;
		color: var(--text-muted);
		margin-top: 2px;
	}
</style>
