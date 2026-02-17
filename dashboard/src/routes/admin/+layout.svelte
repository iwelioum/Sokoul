<script lang="ts">
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';

	interface NavItem {
		label: string;
		path: string;
		icon: string;
	}

	const navItems: NavItem[] = [
		{ label: 'Dashboard', path: '/admin', icon: '📊' },
		{ label: 'Audit Logs', path: '/admin/audit', icon: '📋' },
		{ label: 'Reputation', path: '/admin/reputation', icon: '🔍' },
		{ label: 'Whitelist', path: '/admin/whitelist', icon: '✅' },
		{ label: 'Blacklist', path: '/admin/blacklist', icon: '⛔' }
	];

	let isAdmin = false;
	let loading = true;

	onMount(async () => {
		// Check if user is authenticated and is admin
		const token = localStorage.getItem('sokoul_token');
		if (!token) {
			await goto('/login');
			return;
		}

		// For now, assume authenticated user is admin
		// In production, verify via /api/user/profile or similar
		isAdmin = true;
		loading = false;
	});


</script>

<div class="admin-layout">
	<nav class="admin-nav">
		<div class="nav-header">
			<h1>🛡️ Sokoul Admin</h1>
		</div>
		<ul class="nav-list">
			{#each navItems as item}
				<li>
					<a
						href={item.path}
						class={window.location.pathname === item.path ? 'active' : ''}
						data-icon={item.icon}
					>
						{item.label}
					</a>
				</li>
			{/each}
		</ul>
		<div class="nav-footer">
			<button
				on:click={() => {
					localStorage.removeItem('sokoul_token');
					goto('/login');
				}}
			>
				Logout
			</button>
		</div>
	</nav>

	<div class="admin-content">
		<slot />
	</div>
</div>

<style>
	.admin-layout {
		display: flex;
		height: 100vh;
		background: linear-gradient(135deg, #0f0f23 0%, #1a1a3e 100%);
		color: #fff;
	}

	.admin-nav {
		width: 280px;
		background: rgba(0, 0, 0, 0.3);
		border-right: 1px solid rgba(100, 200, 255, 0.2);
		padding: 20px;
		display: flex;
		flex-direction: column;
		overflow-y: auto;
	}

	.nav-header {
		margin-bottom: 30px;
		border-bottom: 2px solid rgba(100, 200, 255, 0.3);
		padding-bottom: 15px;
	}

	.nav-header h1 {
		margin: 0;
		font-size: 20px;
		color: #64c8ff;
	}

	.nav-list {
		list-style: none;
		padding: 0;
		margin: 0;
		flex: 1;
	}

	.nav-list li {
		margin-bottom: 10px;
	}

	.nav-list a {
		display: block;
		padding: 12px 16px;
		border-radius: 8px;
		color: #ccc;
		text-decoration: none;
		transition: all 0.3s ease;
		border-left: 3px solid transparent;
	}

	.nav-list a::before {
		content: attr(data-icon);
		margin-right: 10px;
	}

	.nav-list a:hover {
		background: rgba(100, 200, 255, 0.1);
		color: #64c8ff;
		border-left-color: #64c8ff;
	}

	.nav-list a.active {
		background: rgba(100, 200, 255, 0.2);
		color: #64c8ff;
		border-left-color: #64c8ff;
		font-weight: 600;
	}

	.nav-footer {
		border-top: 1px solid rgba(100, 200, 255, 0.2);
		padding-top: 15px;
	}

	.nav-footer button {
		width: 100%;
		padding: 10px;
		background: rgba(200, 50, 50, 0.2);
		border: 1px solid rgba(200, 50, 50, 0.5);
		color: #ff7070;
		border-radius: 6px;
		cursor: pointer;
		transition: all 0.3s ease;
	}

	.nav-footer button:hover {
		background: rgba(200, 50, 50, 0.3);
		border-color: #ff7070;
	}

	.admin-content {
		flex: 1;
		padding: 30px;
		overflow-y: auto;
	}

	@media (max-width: 768px) {
		.admin-layout {
			flex-direction: column;
		}

		.admin-nav {
			width: 100%;
			height: auto;
			border-right: none;
			border-bottom: 1px solid rgba(100, 200, 255, 0.2);
			flex-direction: row;
			justify-content: space-between;
		}

		.nav-list {
			display: flex;
			gap: 10px;
		}

		.nav-list li {
			margin-bottom: 0;
		}
	}
</style>
