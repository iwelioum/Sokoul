<script lang="ts">
	import { onMount } from 'svelte';
	import { getMe, isLoggedIn, getPreferredLang, setPreferredLang } from '$lib/api/client';
	import type { UserPublic } from '$lib/api/client';

	const LANGS = [
		{ code: 'fr-FR', label: '🇫🇷 Français' },
		{ code: 'en-US', label: '🇺🇸 English' },
		{ code: 'es-ES', label: '🇪🇸 Español' },
		{ code: 'de-DE', label: '🇩🇪 Deutsch' },
		{ code: 'it-IT', label: '🇮🇹 Italiano' },
		{ code: 'pt-BR', label: '🇧🇷 Português' },
		{ code: 'ja-JP', label: '🇯🇵 日本語' },
		{ code: 'ko-KR', label: '🇰🇷 한국어' },
	];

	const QUALITY_OPTS = [
		{ value: 'auto', label: 'Auto (recommandé)' },
		{ value: '1080p', label: '1080p Full HD' },
		{ value: '720p', label: '720p HD' },
		{ value: '480p', label: '480p SD' },
	];

	let user = $state<UserPublic | null>(null);
	let loading = $state(true);

	let lang     = $state('fr-FR');
	let quality  = $state('auto');
	let autoplay = $state(true);
	let subtitles = $state(false);
	let subLang  = $state('fr');

	let saved    = $state(false);
	let saveErr  = $state('');

	onMount(() => {
		lang      = getPreferredLang();
		quality   = localStorage.getItem('sokoul_quality') ?? 'auto';
		autoplay  = localStorage.getItem('sokoul_autoplay') !== 'false';
		subtitles = localStorage.getItem('sokoul_subtitles') === 'true';
		subLang   = localStorage.getItem('sokoul_sub_lang') ?? 'fr';

		if (isLoggedIn()) {
			getMe().then(u => user = u).catch(() => user = null);
		}
		loading = false;
	});

	function savePreferences() {
		try {
			setPreferredLang(lang);
			localStorage.setItem('sokoul_quality', quality);
			localStorage.setItem('sokoul_autoplay', String(autoplay));
			localStorage.setItem('sokoul_subtitles', String(subtitles));
			localStorage.setItem('sokoul_sub_lang', subLang);
			saved = true;
			saveErr = '';
			setTimeout(() => saved = false, 3000);
		} catch {
			saveErr = 'Impossible de sauvegarder.';
		}
	}
</script>

<svelte:head>
	<title>Paramètres — Sokoul</title>
</svelte:head>

<div class="settings-page">
	<div class="settings-container">

		<header class="settings-header">
			<div class="header-icon">
				<svg viewBox="0 0 24 24" fill="currentColor" width="26" height="26">
					<path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
				</svg>
			</div>
			<div>
				<h1>Paramètres</h1>
				<p class="header-sub">Personnalise ton expérience Sokoul</p>
			</div>
		</header>

		<!-- Compte -->
		{#if !loading && user}
			<section class="settings-section">
				<h2 class="section-title">
					<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
					Compte
				</h2>
				<div class="profile-card">
					<div class="avatar">{user.username.charAt(0).toUpperCase()}</div>
					<div class="profile-info">
						<p class="profile-name">{user.username}</p>
						<p class="profile-email">{user.email}</p>
						<span class="role-badge">{user.role}</span>
					</div>
				</div>
			</section>
		{/if}

		<!-- Langue TMDB -->
		<section class="settings-section">
			<h2 class="section-title">
				<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95c-.32-1.25-.78-2.45-1.38-3.56 1.84.63 3.37 1.91 4.33 3.56zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2s.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56-1.84-.63-3.37-1.9-4.33-3.56zm2.95-8H5.08c.96-1.66 2.49-2.93 4.33-3.56C8.81 5.55 8.35 6.75 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2s.07-1.35.16-2h4.68c.09.65.16 1.32.16 2s-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95c-.96 1.65-2.49 2.93-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2s-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z"/></svg>
				Langue du contenu
			</h2>
			<p class="section-desc">Langue des titres, synopsis et métadonnées (TMDB).</p>
			<div class="lang-grid">
				{#each LANGS as l}
					<button
						class="lang-btn"
						class:active={lang === l.code}
						onclick={() => lang = l.code}
					>
						{l.label}
					</button>
				{/each}
			</div>
		</section>

		<!-- Lecture -->
		<section class="settings-section">
			<h2 class="section-title">
				<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M8 5v14l11-7z"/></svg>
				Lecture
			</h2>

			<div class="setting-row">
				<div>
					<p class="setting-label">Qualité vidéo par défaut</p>
					<p class="setting-sub">Qualité de lecture initiale</p>
				</div>
				<select class="setting-select" bind:value={quality}>
					{#each QUALITY_OPTS as q}
						<option value={q.value}>{q.label}</option>
					{/each}
				</select>
			</div>

			<div class="setting-row">
				<div>
					<p class="setting-label">Lecture automatique</p>
					<p class="setting-sub">Passe à l'épisode suivant automatiquement</p>
				</div>
				<button
					class="toggle"
					class:on={autoplay}
					onclick={() => autoplay = !autoplay}
					role="switch"
					aria-checked={autoplay}
				>
					<span class="toggle-knob"></span>
				</button>
			</div>

			<div class="setting-row">
				<div>
					<p class="setting-label">Sous-titres par défaut</p>
					<p class="setting-sub">Affiche les sous-titres si disponibles</p>
				</div>
				<button
					class="toggle"
					class:on={subtitles}
					onclick={() => subtitles = !subtitles}
					role="switch"
					aria-checked={subtitles}
				>
					<span class="toggle-knob"></span>
				</button>
			</div>

			{#if subtitles}
				<div class="setting-row indent">
					<p class="setting-label">Langue des sous-titres</p>
					<select class="setting-select" bind:value={subLang}>
						<option value="fr">Français</option>
						<option value="en">English</option>
						<option value="es">Español</option>
						<option value="de">Deutsch</option>
					</select>
				</div>
			{/if}
		</section>

		<!-- Sauvegarde -->
		<div class="save-row">
			{#if saveErr}
				<p class="save-error">{saveErr}</p>
			{/if}
			{#if saved}
				<span class="save-ok">
					<svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/></svg>
					Préférences sauvegardées !
				</span>
			{/if}
			<button class="save-btn" onclick={savePreferences}>
				<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z"/></svg>
				Sauvegarder
			</button>
		</div>

	</div>
</div>

<style>
	.settings-page {
		min-height: 100vh;
		background: var(--bg-primary);
		padding: calc(var(--nav-height, 70px) + 2rem) 1.5rem 4rem;
	}

	.settings-container {
		max-width: 720px;
		margin: 0 auto;
		display: flex;
		flex-direction: column;
		gap: 1.5rem;
	}

	.settings-header {
		display: flex; align-items: center; gap: 1rem;
	}

	.header-icon {
		width: 52px; height: 52px;
		background: linear-gradient(135deg, #0072D2, #3b82f6);
		border-radius: 14px;
		display: flex; align-items: center; justify-content: center;
		color: white; flex-shrink: 0;
	}

	h1 { font-size: 1.8rem; font-weight: 700; color: var(--text-primary); margin: 0; }
	.header-sub { font-size: 0.9rem; color: var(--text-muted); margin: 2px 0 0; }

	.settings-section {
		background: var(--bg-card);
		border: 1px solid rgba(255,255,255,0.07);
		border-radius: 16px; padding: 1.5rem;
		display: flex; flex-direction: column; gap: 1.2rem;
	}

	.section-title {
		display: flex; align-items: center; gap: 8px;
		font-size: 0.82rem; font-weight: 700;
		text-transform: uppercase; letter-spacing: 1px;
		color: var(--text-muted); margin: 0;
		padding-bottom: 1rem;
		border-bottom: 1px solid rgba(255,255,255,0.06);
	}

	.section-desc { font-size: 0.83rem; color: var(--text-muted); margin: -0.5rem 0 0; }

	/* Profile */
	.profile-card { display: flex; align-items: center; gap: 1rem; }

	.avatar {
		width: 52px; height: 52px; border-radius: 50%; flex-shrink: 0;
		background: linear-gradient(135deg, #0072D2, #3b82f6);
		color: white; font-size: 1.3rem; font-weight: 700;
		display: flex; align-items: center; justify-content: center;
	}

	.profile-info { display: flex; flex-direction: column; gap: 2px; }
	.profile-name { font-size: 1rem; font-weight: 700; color: var(--text-primary); margin: 0; }
	.profile-email { font-size: 0.82rem; color: var(--text-muted); margin: 0; }

	.role-badge {
		display: inline-block;
		padding: 2px 10px; border-radius: 999px;
		background: rgba(0,114,210,0.15); color: #93c5fd;
		border: 1px solid rgba(0,114,210,0.25);
		font-size: 0.7rem; font-weight: 700; text-transform: uppercase;
		align-self: flex-start;
	}

	/* Lang grid */
	.lang-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(155px, 1fr));
		gap: 8px;
	}

	.lang-btn {
		padding: 10px 14px; border-radius: 10px;
		background: rgba(255,255,255,0.04);
		border: 1px solid rgba(255,255,255,0.1);
		color: var(--text-secondary); font-size: 0.88rem;
		cursor: pointer; transition: all 0.15s; text-align: left;
	}

	.lang-btn:hover { border-color: #3b82f6; color: #93c5fd; }
	.lang-btn.active { background: rgba(59,130,246,0.15); border-color: #3b82f6; color: #93c5fd; font-weight: 700; }

	/* Setting rows */
	.setting-row {
		display: flex; align-items: center;
		justify-content: space-between; gap: 1rem;
	}

	.setting-row.indent { padding-left: 1rem; }
	.setting-label { font-size: 0.92rem; font-weight: 600; color: var(--text-primary); margin: 0; }
	.setting-sub   { font-size: 0.78rem; color: var(--text-muted); margin: 2px 0 0; }

	.setting-select {
		padding: 8px 12px;
		background: var(--bg-secondary);
		border: 1px solid rgba(255,255,255,0.1);
		border-radius: 8px; color: var(--text-primary);
		font-size: 0.85rem; cursor: pointer; min-width: 140px; outline: none;
	}

	.setting-select:focus { border-color: #3b82f6; }

	/* Toggle */
	.toggle {
		width: 46px; height: 26px; border-radius: 13px; flex-shrink: 0;
		background: rgba(255,255,255,0.1); border: none;
		cursor: pointer; position: relative; transition: background 0.2s;
	}

	.toggle.on { background: #3b82f6; }

	.toggle-knob {
		position: absolute; top: 3px; left: 3px;
		width: 20px; height: 20px; border-radius: 50%;
		background: white; transition: transform 0.2s;
		box-shadow: 0 1px 4px rgba(0,0,0,0.3);
	}

	.toggle.on .toggle-knob { transform: translateX(20px); }

	/* Save */
	.save-row {
		display: flex; align-items: center;
		justify-content: flex-end; gap: 1rem; flex-wrap: wrap;
	}

	.save-ok {
		display: flex; align-items: center; gap: 6px;
		color: #4ade80; font-size: 0.85rem; font-weight: 600;
	}

	.save-error { color: #ef4444; font-size: 0.85rem; }

	.save-btn {
		display: flex; align-items: center; gap: 8px;
		padding: 12px 28px; border-radius: 10px;
		background: linear-gradient(135deg, #0072D2, #3b82f6);
		color: white; border: none; font-size: 0.95rem; font-weight: 700;
		cursor: pointer; transition: all 0.2s;
	}

	.save-btn:hover { filter: brightness(1.1); transform: translateY(-1px); }

	@media (max-width: 600px) {
		.lang-grid { grid-template-columns: 1fr 1fr; }
		.setting-row { flex-direction: column; align-items: flex-start; gap: 0.75rem; }
		.save-row { justify-content: stretch; }
		.save-btn { width: 100%; justify-content: center; }
	}
</style>
