<script lang="ts">
	import '../app.css';
	import { page } from '$app/stores';
	import { getHealth, connectWebSocket, listDownloads, getMe, isLoggedIn, clearAuth } from '$lib/api/client';
	import type { WsEvent, Task, UserPublic } from '$lib/api/client';
	import SearchOverlay from '$lib/components/SearchOverlay.svelte';

	let { children } = $props();

	/* ── Nav items ── */
	const navItems = [
		{ href: '/', label: 'Home' },
		{ href: '/films', label: 'Films' },
		{ href: '/series', label: 'Series' },
		{ href: '/tv', label: 'TV' },
		{ href: '/collections', label: 'Collections' }
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
	let currentUser    = $state<UserPublic | null>(null);
	let showUserMenu   = $state(false);

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
			if (!isLoggedIn()) {
				downloads = [];
				return;
			}
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
		
		// Only connect WebSocket if logged in
		let ws: WebSocket | null = null;
		if (isLoggedIn()) {
			ws = connectWebSocket(handleWsEvent);
			getMe().then(u => currentUser = u).catch(() => { clearAuth(); currentUser = null; });
		}

		/* Scroll → glassmorphism + bottom bar hide/show */
		function handleScroll() {
			const y = window.scrollY || document.documentElement.scrollTop || 0;
			scrolled = y > 10;
			bottomBarVisible = y < lastScrollY || y < 60;
			lastScrollY = y;
		}
		window.addEventListener('scroll', handleScroll, { passive: true });
		handleScroll();

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
			if (ws && ws.readyState === WebSocket.OPEN) ws.close();
			window.removeEventListener('scroll', handleScroll);
			window.removeEventListener('keydown', handleGlobalKey);
		};
	});

	function isActive(href: string) {
		const path = $page.url.pathname;
		if (href === '/') return path === '/';
		return path.startsWith(href);
	}

	function handleLogout() {
		clearAuth();
		currentUser = null;
		showUserMenu = false;
	}
</script>

<div class="app-layout">

	<!-- ══════════════════════════════════════════
	     TOP NAVBAR
	     ══════════════════════════════════════════ -->
	<header class="navbar" class:scrolled={scrolled}>
		<div class="navbar-container">
			<!-- LEFT: Logo + Navigation -->
			<div class="navbar-left">
				<!-- Logo -->
				<a href="/" class="navbar-logo" aria-label="SOKOUL">
					<img src="/Sokoul_Logo.svg" alt="Sokoul" class="logo-img" />
				</a>

				<!-- Desktop nav -->
				<nav class="navbar-nav" aria-label="Navigation principale">
					{#each navItems as item}
						<a
							href={item.href}
							class="nav-link"
							class:active={isActive(item.href)}
						>
							<span>{item.label}</span>
						</a>
					{/each}
				</nav>
			</div>

			<!-- RIGHT: Search + Notifications + User -->
			<div class="navbar-right">
				<!-- Search icon only -->
				<button class="icon-btn" onclick={() => searchOpen = true} aria-label="Rechercher" title="Rechercher (Ctrl+K)">
					<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
						<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
					</svg>
				</button>

				<!-- Notifications (bell icon) -->
				<button class="icon-btn" onclick={toggleNotifPanel} aria-label="Notifications" title="Notifications">
					<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
						<path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-2 1H8v-6c0-2.48 1.51-4.5 4-4.5s4 2.02 4 4.5v6z"/>
					</svg>
					{#if notifCount > 0}
						<span class="notif-badge">{notifCount > 9 ? '9+' : notifCount}</span>
					{/if}
				</button>

				<!-- User menu -->
				{#if currentUser}
					<div class="dropdown-wrap">
						<button class="user-btn" onclick={() => showUserMenu = !showUserMenu} aria-label="Menu utilisateur" title={currentUser.username}>
							<span class="user-avatar">{currentUser.username.charAt(0).toUpperCase()}</span>
						</button>
						{#if showUserMenu}
							<!-- svelte-ignore a11y_no_static_element_interactions -->
							<div class="dropdown-overlay" onclick={() => showUserMenu = false} onkeydown={() => {}}></div>
							<div class="user-dropdown">
								<div class="user-dropdown-header">
									<span class="user-avatar-lg">{currentUser.username.charAt(0).toUpperCase()}</span>
									<div>
										<p class="user-dropdown-name">{currentUser.username}</p>
										<p class="user-dropdown-email">{currentUser.email}</p>
									</div>
								</div>
								<div class="user-dropdown-divider"></div>
								<a href="/settings" class="user-menu-item" onclick={() => showUserMenu = false}>
									<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
										<path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/>
									</svg>
									Gérer le profil
								</a>
								<a href="/downloads" class="user-menu-item" onclick={() => showUserMenu = false}>
									<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
										<path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/>
									</svg>
									Téléchargements
								</a>
								<a href="/library" class="user-menu-item" onclick={() => showUserMenu = false}>
									<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
										<path d="M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z"/>
									</svg>
									Watchlist
								</a>
								<div class="user-dropdown-divider"></div>
								<button class="user-menu-item logout-item" onclick={handleLogout}>
									<svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
										<path d="M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z"/>
									</svg>
									Déconnexion
								</button>
							</div>
						{/if}
					</div>
				{:else}
					<a href="/login" class="login-btn">Sign In</a>
				{/if}

				<!-- Hamburger (mobile only) -->
				<button class="hamburger icon-btn" onclick={() => mobileMenuOpen = !mobileMenuOpen} aria-label="Menu">
					<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
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
		<a href={currentUser ? "/library" : "/login"} class="bottom-item">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
				<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
			</svg>
			<span>{currentUser ? 'Profil' : 'Connexion'}</span>
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

<SearchOverlay bind:open={searchOpen} onClose={() => searchOpen = false} />

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
		position: fixed;
		top: 0;
		left: 0;
		right: 0;
		z-index: 1000;
		width: 100%;
		height: var(--nav-height, 70px);
		background: linear-gradient(to bottom, rgba(26, 29, 41, 0.9) 0%, transparent 100%);
		backdrop-filter: none;
		border-bottom: none;
		transition: background 0.4s ease;
	}

	.navbar.scrolled {
		background: #1A1D29;
		border-bottom: none;
	}

	.navbar-container {
		display: flex;
		align-items: center;
		justify-content: space-between;
		height: 100%;
		padding: 0 36px;
		gap: 20px;
	}

	/* ── Left side: Logo + Nav links ── */
	.navbar-left {
		display: flex;
		align-items: center;
		gap: 28px;
		flex: 0 1 auto;
	}

	.navbar-logo {
		text-decoration: none;
		flex-shrink: 0;
		display: flex;
		align-items: center;
	}

	.logo-img {
		height: 200px;
		width: auto;
		transition: filter 0.2s ease;
		max-height: 250px;
		object-fit: contain;
	}

	.navbar-logo:hover .logo-img {
		filter: brightness(1.1);
	}

	.navbar-nav {
		display: flex;
		align-items: center;
		gap: 8px;
	}

	.nav-link {
		position: relative;
		display: flex;
		align-items: center;
		padding: 8px 16px;
		font-size: 15px;
		font-weight: 500;
		color: rgba(249, 249, 249, 0.7);
		text-decoration: none;
		text-transform: uppercase;
		letter-spacing: 1.5px;
		white-space: nowrap;
		transition: color 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.nav-link::after {
		content: '';
		position: absolute;
		bottom: -2px;
		left: 0;
		right: 0;
		height: 2px;
		background: #F9F9F9;
		border-radius: 0 0 4px 4px;
		transform: scaleX(0);
		transform-origin: left center;
		transition: transform 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.nav-link:hover { color: #f9f9f9; }
	.nav-link:hover::after { transform: scaleX(1); }
	.nav-link.active { color: #f9f9f9; }
	.nav-link.active::after { transform: scaleX(1); }

	/* ── Right side: Search + Notifications + User ── */
	.navbar-right {
		display: flex;
		align-items: center;
		gap: 12px;
		flex-shrink: 0;
	}

	/* ── Icon buttons ── */
	.icon-btn {
		position: relative;
		display: flex;
		align-items: center;
		justify-content: center;
		min-width: 42px;
		min-height: 42px;
		border: none;
		background: rgba(255,255,255,0.04);
		border-radius: 50%;
		color: rgba(249, 249, 249, 0.7);
		cursor: pointer;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		padding: 0;
	}
	.icon-btn:hover { 
		background: rgba(255,255,255,0.1); 
		color: #f9f9f9; 
		transform: scale(1.05);
	}

	/* ── Notification badge ── */
	.notif-badge {
		position: absolute;
		top: 4px; 
		right: 4px;
		min-width: 16px; 
		height: 16px;
		border-radius: 8px;
		background: #E05252;
		color: #fff;
		font-size: 10px; 
		font-weight: 700;
		display: flex; 
		align-items: center; 
		justify-content: center;
		padding: 0 4px;
		animation: pulse 2s ease-in-out infinite;
	}

	/* ── User button & avatar ── */
	.user-btn {
		min-width: 38px;
		min-height: 38px;
		padding: 0;
		background: transparent;
		border: 2px solid rgba(249, 249, 249, 0.2);
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.user-btn:hover {
		border-color: #F9F9F9;
		transform: scale(1.05);
	}

	.user-avatar {
		width: 34px;
		height: 34px;
		border-radius: 50%;
		background: linear-gradient(135deg, #0072D2, #3b82f6);
		color: white;
		font-size: 14px;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		text-transform: uppercase;
	}

	.user-avatar-lg {
		width: 44px;
		height: 44px;
		font-size: 18px;
		border-radius: 50%;
		background: linear-gradient(135deg, #0072D2, #3b82f6);
		color: white;
		font-weight: 700;
		display: flex;
		align-items: center;
		justify-content: center;
		text-transform: uppercase;
		flex-shrink: 0;
	}

	/* ── Login button ── */
	.login-btn {
		padding: 8px 20px;
		font-size: 14px;
		font-weight: 600;
		color: white;
		background: rgba(0, 114, 210, 0.9);
		border: none;
		border-radius: 4px;
		cursor: pointer;
		text-decoration: none;
		transition: background 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		text-transform: uppercase;
		letter-spacing: 1px;
	}
	.login-btn:hover { 
		background: #0072D2; 
		transform: scale(1.05);
	}

	/* ── Dropdown system ── */
	.dropdown-wrap { 
		position: relative; 
	}

	.dropdown-overlay {
		position: fixed;
		inset: 0;
		z-index: 1001;
		background: transparent;
	}

	/* ── User dropdown (expanded menu) ── */
	.user-dropdown {
		position: absolute;
		top: calc(100% + 12px);
		right: 0;
		min-width: 260px;
		background: rgba(37, 40, 51, 0.98);
		backdrop-filter: blur(20px);
		border: 1px solid rgba(249, 249, 249, 0.1);
		border-radius: 10px;
		box-shadow: rgb(0 0 0 / 69%) 0px 26px 30px -10px, rgb(0 0 0 / 73%) 0px 16px 10px -10px;
		z-index: 1002;
		animation: slideDown 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
		transform-origin: top right;
		overflow: hidden;
	}

	@keyframes slideDown {
		from {
			opacity: 0;
			transform: translateY(-10px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.user-dropdown-header {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 16px;
		background: linear-gradient(180deg, #30343E 0%, #252833 100%);
	}

	.user-dropdown-name {
		font-size: 15px;
		font-weight: 700;
		color: #F9F9F9;
		margin: 0;
	}

	.user-dropdown-email {
		font-size: 12px;
		color: #CACACA;
		margin: 2px 0 0 0;
	}

	.user-dropdown-divider {
		height: 1px;
		background: rgba(249, 249, 249, 0.1);
		margin: 0;
	}

	.user-menu-item {
		display: flex;
		align-items: center;
		gap: 12px;
		width: 100%;
		text-align: left;
		padding: 12px 16px;
		font-size: 14px;
		font-weight: 500;
		color: rgba(249, 249, 249, 0.8);
		background: none;
		border: none;
		cursor: pointer;
		text-decoration: none;
		transition: all 250ms cubic-bezier(0.25, 0.46, 0.45, 0.94);
	}

	.user-menu-item svg {
		flex-shrink: 0;
		opacity: 0.7;
	}

	.user-menu-item:hover { 
		background: rgba(255, 255, 255, 0.08); 
		color: #F9F9F9;
	}

	.user-menu-item:hover svg {
		opacity: 1;
	}

	.logout-item {
		color: #E05252;
	}

	.logout-item:hover {
		background: rgba(224, 82, 82, 0.1);
	}

	/* ── Hamburger ── */
	.hamburger { 
		display: none; 
	}

	/* ── Mobile top dropdown ── */
	.mobile-menu {
		display: none;
		flex-direction: column;
		padding: 8px 16px 14px;
		border-top: 1px solid var(--border);
		background: rgba(26, 29, 41, 0.97);
	}
	.mobile-nav-link {
		padding: 12px 16px;
		font-size: 15px;
		font-weight: 500;
		color: rgba(249, 249, 249, 0.6);
		text-decoration: none;
		border-radius: 8px;
		transition: all 0.15s;
	}
	.mobile-nav-link:hover { background: rgba(255,255,255,0.05); color: #f9f9f9; }
	.mobile-nav-link.active { color: #F9F9F9; background: rgba(0, 114, 210, 0.15); }

	/* ═══ Content ═══ */
	.content {
		flex: 1;
		padding: 0;
		max-width: 100%;
		width: 100%;
		margin: 0;
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
		background: rgba(26, 29, 41, 0.95);
		backdrop-filter: blur(16px);
		-webkit-backdrop-filter: blur(16px);
		border-top: 1px solid rgba(249, 249, 249, 0.08);
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

	.bottom-item:hover, .bottom-item.active { color: #f9f9f9; box-shadow: none; }

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

	/* ═══ User menu ═══ */
	/* Styles moved above with navbar styles */

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
		.content { padding-bottom: 76px; }
		.notif-panel { width: 100vw; max-width: 100vw; }
		.toast-container { left: 12px; right: 12px; bottom: 72px; max-width: none; }
		.logo-text { font-size: 22px; letter-spacing: 2px; }
	}
</style>
