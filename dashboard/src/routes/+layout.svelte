<script lang="ts">
	import '../app.css';
	import { page } from '$app/stores';
	import { getHealth, connectWebSocket, listDownloads } from '$lib/api/client';
	import type { WsEvent, Task } from '$lib/api/client';
	import SearchModal from '$lib/components/SearchModal.svelte';

	let { children } = $props();

	/* ── Nav items ── */
	const navItems = [
		{ href: '/', label: 'Accueil' },
		{ href: '/films', label: 'Films' },
		{ href: '/series', label: 'Séries' },
		{ href: '/library', label: 'Bibliothèque' }, // Changed from 'Ma Liste' to 'Bibliothèque'
		{ href: '/downloads', label: 'Téléchargements' }, // Added Downloads
		{ href: '/tasks', label: 'Système' } // Added Système
	];

	/* ── State ── */
	let backendOnline  = $state(false);
	let notifCount     = $state(0);
	let showNotifPanel = $state(false);
	let searchOpen     = $state(false);
	let mobileMenuOpen = $state(false);
	let showDownloads  = $state(false);
	let downloads: Task[] = $state([]);
	let scrolled       = $state(false);
	let bottomBarVisible = $state(true);
	let lastScrollY    = $state(0);

	interface Toast { id: number; type: string; message: string; leaving: boolean; }
	let toasts: Toast[]  = $state([]);
	let toastIdCounter   = $state(0);

	/* ── Health ── */
	async function checkHealth() {
		try {
			const h = await getHealth();
			backendOnline = h.status === 'ok';
		} catch {
			backendOnline = false;
		}
	}

	/* ── Downloads dropdown ── */
	async function toggleDownloads() {
		showDownloads = !showDownloads;
		if (showDownloads) {
			try { downloads = await listDownloads(); } catch { downloads = []; }
		}
	}

	/* ── Toasts ── */
	function addToast(type: string, message: string) {
		const id = ++toastIdCounter;
		toasts = [...toasts, { id, type, message, leaving: false }];
		notifCount++;
		setTimeout(() => dismissToast(id), 5000);
	}

	function dismissToast(id: number) {
		toasts = toasts.map(t => t.id === id ? { ...t, leaving: true } : t);
		setTimeout(() => { toasts = toasts.filter(t => t.id !== id); }, 300);
	}

	function toggleNotifPanel() {
		showNotifPanel = !showNotifPanel;
		if (showNotifPanel) notifCount = 0;
	}

	function handleWsEvent(event: WsEvent) {
		switch (event.type) {
			case 'download_completed':
				addToast('success', `Téléchargement terminé : ${event.title || 'fichier'}`);
				break;
			case 'search_completed':
				addToast('info', `Recherche terminée : ${event.query || event.title || 'résultats prêts'}`);
				break;
			case 'system_alert':
				addToast('warning', `Alerte : ${event.message || 'événement système'}`);
				break;
		}
	}

	/* ── Lifecycle ── */
	$effect(() => {
		checkHealth();
		const healthInterval = setInterval(checkHealth, 15000);
		const ws = connectWebSocket(handleWsEvent);

		/* Scroll → glassmorphism + bottom bar hide/show */
		function handleScroll() {
			const y = window.scrollY;
			scrolled = y > 10;
			bottomBarVisible = y < lastScrollY || y < 60;
			lastScrollY = y;
		}
		window.addEventListener('scroll', handleScroll, { passive: true });

		/* Global shortcuts */
		function handleGlobalKey(e: KeyboardEvent) {
			if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
				e.preventDefault();
				searchOpen = true;
			}
			if (e.key === 'Escape') {
				mobileMenuOpen = false;
				showDownloads = false;
			}
		}
		window.addEventListener('keydown', handleGlobalKey);

		return () => {
			clearInterval(healthInterval);
			ws.close();
			window.removeEventListener('scroll', handleScroll);
			window.removeEventListener('keydown', handleGlobalKey);
		};
	});

	function isActive(href: string) {
		const path = $page.url.pathname;
		if (href === '/') return path === '/';
		return path.startsWith(href);
	}
</script>

<div class="app-layout">

	<!-- ══════════════════════════════════════════
	     TOP NAVBAR
	     ══════════════════════════════════════════ -->
	<header class="navbar" class:scrolled>
		<div class="navbar-inner">

			<!-- Logo -->
			<a href="/" class="navbar-logo" aria-label="SOKOUL — Accueil">
				<img src="/Sokoul_Logo.svg" alt="Sokoul Logo" class="logo-img" />
			</a>

			<!-- Desktop nav -->
			<nav class="navbar-nav" aria-label="Navigation principale">
				{#each navItems as item}
					<a
						href={item.href}
						class="nav-link"
						class:active={isActive(item.href)}
					>{item.label}</a>
				{/each}
			</nav>

			<!-- Right controls -->
			<div class="navbar-right">
				<!-- Ctrl+K search -->
				<button class="search-trigger" onclick={() => searchOpen = true} aria-label="Rechercher (Ctrl+K)">
					<svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor">
						<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
					</svg>
					<span class="search-label">Rechercher…</span>
					<kbd>Ctrl+K</kbd>
				</button>

				<!-- Downloads dropdown -->
				<div class="dropdown-wrap">
					<button class="icon-btn" onclick={toggleDownloads} aria-label="Téléchargements" title="Téléchargements">
						<svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor">
							<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
						</svg>
					</button>

					{#if showDownloads}
						<!-- svelte-ignore a11y_no_static_element_interactions -->
						<div class="dropdown-overlay" onclick={() => showDownloads = false} onkeydown={() => {}}></div>
						<div class="download-dropdown">
							<p class="dropdown-title">Téléchargements</p>
							{#if downloads.length === 0}
								<p class="dropdown-empty">Aucun téléchargement en cours</p>
							{:else}
								{#each downloads.slice(0, 8) as dl (dl.id)}
									<div class="download-item">
										<span class="dl-title">{String(dl.payload?.title ?? dl.task_type ?? dl.id)}</span>
										<span class="dl-status badge badge-{dl.status === 'completed' ? 'success' : dl.status === 'failed' ? 'danger' : 'warning'}">{dl.status}</span>
									</div>
								{/each}
							{/if}
							<a href="/downloads" class="dropdown-link" onclick={() => showDownloads = false}>Voir tous →</a>
						</div>
					{/if}
				</div>

				<!-- Notifications -->
				<button class="icon-btn" onclick={toggleNotifPanel} aria-label="Notifications">
					<svg width="17" height="17" viewBox="0 0 16 16" fill="currentColor">
						<path d="M8 1.5A3.5 3.5 0 0 0 4.5 5v2.947c0 .249-.09.489-.254.667L2.692 10.4a.75.75 0 0 0 .558 1.25h9.5a.75.75 0 0 0 .558-1.25l-1.554-1.786A1.04 1.04 0 0 1 11.5 7.947V5A3.5 3.5 0 0 0 8 1.5zM6.5 13a1.5 1.5 0 0 0 3 0h-3z"/>
					</svg>
					{#if notifCount > 0}
						<span class="notif-dot">{notifCount > 9 ? '9+' : notifCount}</span>
					{/if}
				</button>

				<!-- Backend status -->
				<div class="status-indicator" title={backendOnline ? 'Backend connecté' : 'Backend hors ligne'}>
					<span class="status-dot" class:offline={!backendOnline}></span>
				</div>

				<!-- Hamburger (mobile) -->
				<button class="hamburger icon-btn" onclick={() => mobileMenuOpen = !mobileMenuOpen} aria-label="Menu">
					<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
						{#if mobileMenuOpen}
							<path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
						{:else}
							<path d="M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z"/>
						{/if}
					</svg>
				</button>
			</div>
		</div>

		<!-- Mobile top dropdown -->
		{#if mobileMenuOpen}
			<nav class="mobile-menu" aria-label="Menu mobile">
				{#each navItems as item}
					<a
						href={item.href}
						class="mobile-nav-link"
						class:active={isActive(item.href)}
						onclick={() => mobileMenuOpen = false}
					>{item.label}</a>
				{/each}
			</nav>
		{/if}
	</header>

	<!-- ══════════════════════════════════════════
	     CONTENT
	     ══════════════════════════════════════════ -->
	<main class="content">
		{@render children()}
	</main>

	<!-- ══════════════════════════════════════════
	     MOBILE BOTTOM BAR
	     ══════════════════════════════════════════ -->
	<nav class="bottom-bar" class:hidden={!bottomBarVisible} aria-label="Navigation mobile">
		<a href="/" class="bottom-item" class:active={$page.url.pathname === '/'}>
			<svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
				<path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/>
			</svg>
			<span>Accueil</span>
		</a>
		<button class="bottom-item" onclick={() => searchOpen = true}>
			<svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
				<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
			</svg>
			<span>Recherche</span>
		</button>
		<a href="/library" class="bottom-item" class:active={$page.url.pathname.startsWith('/library')}>
			<svg viewBox="0 0 24 24" fill={$page.url.pathname.startsWith('/library') ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" width="22" height="22">
				<path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
			</svg>
			<span>Ma Liste</span>
		</a>
		<a href="/library" class="bottom-item">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
				<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
			</svg>
			<span>Profil</span>
		</a>
	</nav>
</div>

<!-- ── Notification panel ── -->
{#if showNotifPanel}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="notif-overlay" onclick={toggleNotifPanel} onkeydown={() => {}}></div>
	<aside class="notif-panel">
		<div class="notif-header">
			<h3>Notifications</h3>
			<button class="icon-btn" onclick={toggleNotifPanel} aria-label="Fermer">
				<svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
					<path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06z"/>
				</svg>
			</button>
		</div>
		{#if toasts.length === 0}
			<p class="notif-empty">Aucune notification récente</p>
		{:else}
			<div class="notif-list">
				{#each toasts as toast (toast.id)}
					<div class="notif-item notif-{toast.type}" class:leaving={toast.leaving}>
						<p>{toast.message}</p>
					</div>
				{/each}
			</div>
		{/if}
	</aside>
{/if}

<SearchModal bind:open={searchOpen} onClose={() => searchOpen = false} />

<!-- Toast container -->
<div class="toast-container">
	{#each toasts as toast (toast.id)}
		<div class="toast toast-{toast.type}" class:leaving={toast.leaving}>
			<span class="toast-message">{toast.message}</span>
			<button class="icon-btn toast-close" onclick={() => dismissToast(toast.id)} aria-label="Fermer">
				<svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
					<path d="M3.72 3.72a.75.75 0 0 1 1.06 0L8 6.94l3.22-3.22a.75.75 0 1 1 1.06 1.06L9.06 8l3.22 3.22a.75.75 0 1 1-1.06 1.06L8 9.06l-3.22 3.22a.75.75 0 0 1-1.06-1.06L6.94 8 3.72 4.78a.75.75 0 0 1 0-1.06z"/>
				</svg>
			</button>
		</div>
	{/each}
</div>

<style>
	/* ═══ Layout ═══ */
	.app-layout {
		display: flex;
		flex-direction: column;
		min-height: 100vh;
	}

	/* ═══ Navbar ═══ */
	.navbar {
		position: sticky;
		top: 0;
		z-index: 50;
		background: transparent;
		border-bottom: 1px solid transparent;
		transition: background 0.3s ease, border-color 0.3s ease, backdrop-filter 0.3s ease;
	}

	.navbar.scrolled {
		background: rgba(26, 23, 22, 0.88);
		backdrop-filter: blur(16px);
		-webkit-backdrop-filter: blur(16px);
		border-bottom-color: rgba(58, 51, 48, 0.6);
	}

	.navbar-inner {
		display: flex;
		align-items: center;
		gap: 40px;
		padding: 0 48px;
		height: auto;
		min-height: 80px;
		max-width: 1600px;
		margin: 0 auto;
		width: 100%;
	}

	/* ── Logo ── */
	.navbar-logo {
		text-decoration: none;
		flex-shrink: 0;
		display: flex;
		align-items: center;
		padding: 5px 0; /* Add some padding to vertically center it better if needed */
	}

	.logo-img {
		width: 120px;
		height: auto;
		transition: filter 0.2s ease;
		max-height: none;
	}

	.navbar-logo:hover .logo-img {
		filter: drop-shadow(0 1px 5px rgba(192, 74, 53, 0.6)) drop-shadow(0 0 15px rgba(192, 74, 53, 0.3));
	}

	/* ── Desktop nav ── */
	.navbar-nav {
		display: flex;
		align-items: center;
		gap: 2px;
		flex: 1;
	}

	.nav-link {
		position: relative;
		padding: 8px 18px;
		border-radius: 6px;
		font-size: 14px;
		font-weight: 500;
		color: var(--text-secondary);
		text-decoration: none;
		white-space: nowrap;
		transition: color 0.15s ease;
	}

	.nav-link::after {
		content: '';
		position: absolute;
		bottom: -2px;
		left: 50%;
		transform: translateX(-50%) scaleX(0);
		width: 60%;
		height: 2px;
		background: var(--brand-gold);
		border-radius: 1px;
		transition: transform 0.2s ease;
	}

	.nav-link:hover { color: var(--text-primary); }
	.nav-link.active { color: var(--text-primary); }
	.nav-link.active::after { transform: translateX(-50%) scaleX(1); }

	/* ── Right zone ── */
	.navbar-right {
		display: flex;
		align-items: center;
		gap: 6px;
		flex-shrink: 0;
	}

	/* ── Search trigger ── */
	.search-trigger {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 18px;
		background: rgba(255,255,255,0.05);
		border: 1px solid rgba(255,255,255,0.09);
		border-radius: 8px;
		color: var(--text-secondary);
		cursor: pointer;
		font-size: 13px;
		transition: all 0.15s ease;
	}

	.search-trigger:hover {
		border-color: rgba(192,74,53,0.45);
		color: var(--text-primary);
		background: rgba(192,74,53,0.07);
		box-shadow: none;
	}

	.search-label { min-width: 110px; text-align: left; }

	.search-trigger kbd {
		font-size: 11px;
		background: rgba(255,255,255,0.07);
		border: 1px solid rgba(255,255,255,0.1);
		border-radius: 4px;
		padding: 1px 5px;
		color: var(--text-muted);
		font-family: inherit;
	}

	/* ── Icon buttons ── */
	.icon-btn {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
		width: 42px;
		height: 42px;
		border: none;
		background: rgba(255,255,255,0.04);
		border-radius: 8px;
		color: var(--text-secondary);
		cursor: pointer;
		transition: all 0.15s ease;
		padding: 0;
	}
	.icon-btn:hover { background: rgba(255,255,255,0.09); color: var(--text-primary); box-shadow: none; }

	/* ── Status ── */
	.status-indicator { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; }
	.status-dot {
		width: 8px; height: 8px; border-radius: 50%;
		background: var(--success);
		box-shadow: 0 0 6px rgba(76,175,125,0.5);
		transition: all 0.3s;
	}
	.status-dot.offline { background: var(--danger); box-shadow: 0 0 6px rgba(224,82,82,0.5); }

	/* ── Notification badge ── */
	.notif-dot {
		position: absolute;
		top: 3px; right: 3px;
		min-width: 16px; height: 16px;
		border-radius: 8px;
		background: var(--danger);
		color: #fff;
		font-size: 10px; font-weight: 700;
		display: flex; align-items: center; justify-content: center;
		padding: 0 3px;
		animation: pulse 2s ease-in-out infinite;
	}

	/* ── Download dropdown ── */
	.dropdown-wrap { position: relative; }

	.dropdown-overlay {
		position: fixed;
		inset: 0;
		z-index: 40;
	}

	.download-dropdown {
		position: absolute;
		top: calc(100% + 10px);
		right: 0;
		width: 300px;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: var(--radius);
		box-shadow: 0 16px 40px rgba(0,0,0,0.4);
		z-index: 60;
		animation: scaleIn 0.15s ease both;
		transform-origin: top right;
		overflow: hidden;
	}

	.dropdown-title {
		font-size: 12px;
		font-weight: 700;
		text-transform: uppercase;
		letter-spacing: 0.5px;
		color: var(--text-muted);
		padding: 12px 16px 8px;
	}

	.dropdown-empty {
		font-size: 13px;
		color: var(--text-muted);
		padding: 8px 16px 12px;
		text-align: center;
	}

	.download-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		padding: 8px 16px;
		border-top: 1px solid var(--border);
	}

	.dl-title {
		font-size: 13px;
		color: var(--text-primary);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		flex: 1;
	}

	.dl-status {
		font-size: 10px;
		flex-shrink: 0;
	}

	.dropdown-link {
		display: block;
		text-align: center;
		padding: 10px;
		font-size: 13px;
		font-weight: 600;
		color: var(--accent);
		border-top: 1px solid var(--border);
		text-decoration: none;
		transition: background 0.15s;
	}
	.dropdown-link:hover { background: var(--bg-hover); color: var(--accent); }

	/* ── Hamburger ── */
	.hamburger { display: none; }

	/* ── Mobile top dropdown ── */
	.mobile-menu {
		display: none;
		flex-direction: column;
		padding: 8px 16px 14px;
		border-top: 1px solid rgba(255,255,255,0.05);
		background: rgba(26,23,22,0.97);
	}
	.mobile-nav-link {
		padding: 12px 16px;
		font-size: 15px;
		font-weight: 500;
		color: var(--text-secondary);
		text-decoration: none;
		border-radius: 8px;
		transition: all 0.15s;
	}
	.mobile-nav-link:hover { background: rgba(255,255,255,0.05); color: var(--text-primary); }
	.mobile-nav-link.active { color: var(--accent); background: rgba(192, 74, 53, 0.08); }

	/* ═══ Content ═══ */
	.content {
		flex: 1;
		padding: 32px;
		max-width: 1400px;
		width: 100%;
		margin: 0 auto;
		animation: fadeIn 0.3s ease both;
	}

	/* ═══ Mobile Bottom Bar ═══ */
	.bottom-bar {
		display: none;
		position: fixed;
		bottom: 0;
		left: 0;
		right: 0;
		height: 56px;
		background: rgba(26,23,22,0.95);
		backdrop-filter: blur(16px);
		-webkit-backdrop-filter: blur(16px);
		border-top: 1px solid var(--border);
		z-index: 50;
		flex-direction: row;
		align-items: stretch;
		transition: transform 0.3s ease;
	}

	.bottom-bar.hidden { transform: translateY(100%); }

	.bottom-item {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 3px;
		color: var(--text-muted);
		text-decoration: none;
		font-size: 10px;
		font-weight: 500;
		background: none;
		border: none;
		padding: 6px 4px;
		cursor: pointer;
		transition: color 0.15s;
	}

	.bottom-item:hover, .bottom-item.active { color: var(--accent); box-shadow: none; }

	/* ═══ Notif panel ═══ */
	.notif-overlay {
		position: fixed; inset: 0;
		background: rgba(0,0,0,0.4);
		z-index: 90;
		animation: fadeIn 0.2s ease both;
	}

	.notif-panel {
		position: fixed;
		top: 0; right: 0; bottom: 0;
		width: 360px; max-width: 90vw;
		background: var(--bg-secondary);
		border-left: 1px solid var(--border);
		z-index: 100;
		display: flex; flex-direction: column;
		animation: slideInRight 0.3s cubic-bezier(0.4,0,0.2,1) both;
	}

	.notif-header {
		display: flex; align-items: center; justify-content: space-between;
		padding: 20px 24px;
		border-bottom: 1px solid var(--border);
	}
	.notif-header h3 { font-size: 16px; font-weight: 600; }

	.notif-empty {
		padding: 40px 24px;
		text-align: center;
		color: var(--text-muted);
		font-size: 14px;
	}

	.notif-list {
		flex: 1; overflow-y: auto;
		padding: 12px;
		display: flex; flex-direction: column; gap: 8px;
	}

	.notif-item {
		padding: 12px 16px;
		border-radius: var(--radius-sm);
		font-size: 13px;
		border-left: 3px solid var(--border);
		background: var(--bg-card);
		animation: slideUp 0.2s ease both;
	}

	.notif-item.leaving { animation: slideOutRight 0.3s ease both; }
	.notif-success { border-left-color: var(--success); }
	.notif-info    { border-left-color: var(--info); }
	.notif-warning { border-left-color: var(--warning); }

	/* ═══ Toasts ═══ */
	.toast-container {
		position: fixed;
		bottom: 24px; right: 24px;
		z-index: 200;
		display: flex; flex-direction: column-reverse;
		gap: 8px; max-width: 380px;
		pointer-events: none;
	}

	.toast {
		display: flex; align-items: center; gap: 12px;
		padding: 12px 16px;
		border-radius: var(--radius-sm);
		background: var(--bg-card);
		border: 1px solid var(--border);
		box-shadow: 0 8px 24px rgba(0,0,0,0.4);
		animation: slideInRight 0.3s cubic-bezier(0.4,0,0.2,1) both;
		pointer-events: auto;
		font-size: 13px;
	}

	.toast.leaving { animation: slideOutRight 0.3s ease both; }
	.toast-success { border-left: 3px solid var(--success); }
	.toast-info    { border-left: 3px solid var(--info); }
	.toast-warning { border-left: 3px solid var(--warning); }
	.toast-message { flex: 1; color: var(--text-primary); }

	.toast-close {
		background: transparent; border: none;
		color: var(--text-muted);
		width: auto; height: auto; padding: 4px;
	}

	/* ═══ Responsive ═══ */
	@media (max-width: 900px) {
		.navbar-nav   { display: none; }
		.hamburger    { display: flex; }
		.mobile-menu  { display: flex; }
		.search-label { display: none; }
		.search-trigger kbd { display: none; }
		.search-trigger { padding: 7px 10px; }
		.bottom-bar { display: flex; }
		.content { padding-bottom: 72px; }
	}

	@media (max-width: 600px) {
		.navbar-inner { padding: 0 16px; gap: 10px; }
		.content { padding: 16px; padding-bottom: 76px; }
		.notif-panel { width: 100vw; max-width: 100vw; }
		.toast-container { left: 12px; right: 12px; bottom: 72px; max-width: none; }
		.logo-text { font-size: 22px; letter-spacing: 2px; }
	}
</style>
