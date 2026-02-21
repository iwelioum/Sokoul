<script lang="ts">
	import { onMount } from 'svelte';
	import { listDownloadHistory, isLoggedIn, formatBytes } from '$lib/api/client';
	import type { Task } from '$lib/api/client';

	let tasks: Task[] = $state([]);
	let total = $state(0);
	let currentPage = $state(1);
	let perPage = $state(20);
	let statusFilter = $state<string>('');
	let loading = $state(false);
	let error = $state('');

	const totalPages = $derived(Math.ceil(total / perPage) || 1);

	onMount(() => {
		if (isLoggedIn()) {
			loadHistory();
		} else {
			error = "Vous devez être connecté.";
		}
	});

	async function loadHistory() {
		loading = true;
		error = '';
		try {
			const resp = await listDownloadHistory({
				page: currentPage,
				per_page: perPage,
				status: statusFilter || undefined,
			});
			tasks = resp.tasks;
			total = resp.total;
		} catch {
			error = "Erreur lors du chargement de l'historique.";
		} finally {
			loading = false;
		}
	}

	function changePage(p: number) {
		currentPage = p;
		loadHistory();
	}

	function changeFilter(s: string) {
		statusFilter = s;
		currentPage = 1;
		loadHistory();
	}

	function statusLabel(s: string): string {
		switch (s) {
			case 'completed': return 'Terminé';
			case 'failed': return 'Échoué';
			case 'cancelled': return 'Annulé';
			case 'running': return 'En cours';
			case 'pending': return 'En attente';
			default: return s;
		}
	}

	function statusClass(s: string): string {
		switch (s) {
			case 'completed': return 'status--completed';
			case 'failed': return 'status--failed';
			case 'cancelled': return 'status--cancelled';
			case 'running': return 'status--running';
			case 'pending': return 'status--pending';
			default: return '';
		}
	}

	function formatDate(d: string | null): string {
		if (!d) return '—';
		return new Date(d).toLocaleDateString('fr-FR', {
			day: '2-digit', month: '2-digit', year: 'numeric',
			hour: '2-digit', minute: '2-digit',
		});
	}
</script>

<svelte:head>
	<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
	<title>Historique — Sokoul</title>
</svelte:head>

<div class="history-page">
	<header class="history-header">
		<div class="history-header__inner">
			<button class="history-back" onclick={() => history.back()}>
				<i class="fa-solid fa-arrow-left"></i> Retour
			</button>
			<div>
				<h1 class="history-title"><i class="fa-solid fa-clock-rotate-left"></i> Historique des téléchargements</h1>
				<p class="history-subtitle">{total} téléchargement{total !== 1 ? 's' : ''} au total</p>
			</div>
		</div>
	</header>

	<div class="history-body">
		<!-- Filters -->
		<div class="history-filters">
			{#each ['', 'completed', 'failed', 'cancelled', 'running', 'pending'] as f}
				<button
					class="history-filter"
					class:active={statusFilter === f}
					onclick={() => changeFilter(f)}
				>
					{f === '' ? 'Tous' : statusLabel(f)}
				</button>
			{/each}
		</div>

		{#if loading}
			<div class="history-loading">
				<i class="fa-solid fa-spinner fa-spin"></i> Chargement…
			</div>
		{:else if error}
			<div class="history-error">
				<i class="fa-solid fa-triangle-exclamation"></i> {error}
			</div>
		{:else if tasks.length === 0}
			<div class="history-empty">
				<i class="fa-solid fa-inbox"></i>
				<p>Aucun téléchargement trouvé</p>
			</div>
		{:else}
			<div class="history-table-wrap">
				<table class="history-table">
					<thead>
						<tr>
							<th>Titre</th>
							<th>Statut</th>
							<th>Progression</th>
							<th>Date</th>
						</tr>
					</thead>
					<tbody>
						{#each tasks as task (task.id)}
							{@const payload = task.payload as any}
							{@const pct = Number(task.progress) || 0}
							<tr>
								<td class="history-cell-title" title={payload?.title}>
									{payload?.title || 'Téléchargement'}
								</td>
								<td>
									<span class="history-status {statusClass(task.status)}">
										{statusLabel(task.status)}
									</span>
								</td>
								<td>
									<div class="history-progress-track">
										<div class="history-progress-fill" style="width: {pct}%"></div>
									</div>
									<span class="history-pct">{pct.toFixed(0)}%</span>
								</td>
								<td class="history-cell-date">
									{formatDate(task.completed_at || task.created_at)}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>

			<!-- Pagination -->
			{#if totalPages > 1}
				<div class="history-pagination">
					<button
						class="history-page-btn"
						disabled={currentPage <= 1}
						onclick={() => changePage(currentPage - 1)}
					>
						<i class="fa-solid fa-chevron-left"></i>
					</button>
					<span class="history-page-info">Page {currentPage} / {totalPages}</span>
					<button
						class="history-page-btn"
						disabled={currentPage >= totalPages}
						onclick={() => changePage(currentPage + 1)}
					>
						<i class="fa-solid fa-chevron-right"></i>
					</button>
				</div>
			{/if}
		{/if}
	</div>
</div>

<style>
.history-page {
	min-height: 100vh;
	background: var(--bg-primary, #1A1D29);
	color: #F9F9F9;
}

.history-header {
	padding: 100px 40px 32px;
	background: linear-gradient(180deg, rgba(0,114,210,0.08) 0%, transparent 100%);
}
.history-header__inner {
	max-width: 1200px;
	margin: 0 auto;
	display: flex;
	align-items: flex-start;
	gap: 20px;
}
.history-back {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 8px 16px;
	border-radius: 8px;
	background: rgba(255,255,255,0.05);
	border: 1px solid rgba(255,255,255,0.08);
	color: #F9F9F9;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	transition: all 200ms ease;
	flex-shrink: 0;
}
.history-back:hover { background: rgba(255,255,255,0.1); }

.history-title {
	font-size: 28px;
	font-weight: 800;
	margin: 0 0 4px;
	display: flex;
	align-items: center;
	gap: 12px;
}
.history-subtitle {
	font-size: 14px;
	color: #64748b;
	margin: 0;
}

.history-body {
	max-width: 1200px;
	margin: 0 auto;
	padding: 0 40px 60px;
}

.history-filters {
	display: flex;
	gap: 8px;
	flex-wrap: wrap;
	margin-bottom: 24px;
}
.history-filter {
	padding: 6px 16px;
	border-radius: 20px;
	background: rgba(255,255,255,0.05);
	border: 1px solid rgba(255,255,255,0.08);
	color: #CACACA;
	font-size: 12px;
	font-weight: 600;
	cursor: pointer;
	transition: all 200ms ease;
	text-transform: uppercase;
	letter-spacing: 0.5px;
}
.history-filter:hover { background: rgba(255,255,255,0.1); color: #F9F9F9; }
.history-filter.active {
	background: rgba(0,114,210,0.2);
	border-color: rgba(0,114,210,0.5);
	color: #60a5fa;
}

.history-loading, .history-error, .history-empty {
	text-align: center;
	padding: 60px 20px;
	color: #64748b;
	font-size: 15px;
}
.history-empty i { font-size: 48px; display: block; margin-bottom: 16px; opacity: 0.3; }
.history-error { color: #f87171; }

.history-table-wrap {
	overflow-x: auto;
	border-radius: 12px;
	border: 1px solid rgba(255,255,255,0.06);
	background: rgba(37,40,51,0.5);
}
.history-table {
	width: 100%;
	border-collapse: collapse;
	font-size: 13px;
}
.history-table thead {
	background: rgba(255,255,255,0.03);
}
.history-table th {
	text-align: left;
	padding: 12px 16px;
	font-weight: 600;
	color: #94a3b8;
	font-size: 11px;
	text-transform: uppercase;
	letter-spacing: 1px;
	border-bottom: 1px solid rgba(255,255,255,0.06);
}
.history-table td {
	padding: 14px 16px;
	border-bottom: 1px solid rgba(255,255,255,0.04);
	vertical-align: middle;
}
.history-table tbody tr {
	transition: background 150ms ease;
}
.history-table tbody tr:hover {
	background: rgba(255,255,255,0.03);
}

.history-cell-title {
	max-width: 400px;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	font-weight: 500;
}
.history-cell-date {
	color: #64748b;
	white-space: nowrap;
}

.history-status {
	display: inline-block;
	padding: 3px 10px;
	border-radius: 12px;
	font-size: 11px;
	font-weight: 700;
	text-transform: uppercase;
	letter-spacing: 0.5px;
}
.status--completed { background: rgba(16,185,129,0.15); color: #10b981; }
.status--failed { background: rgba(239,68,68,0.15); color: #ef4444; }
.status--cancelled { background: rgba(251,191,36,0.15); color: #fbbf24; }
.status--running { background: rgba(96,165,250,0.15); color: #60a5fa; }
.status--pending { background: rgba(148,163,184,0.15); color: #94a3b8; }

.history-progress-track {
	width: 80px;
	height: 4px;
	background: rgba(255,255,255,0.06);
	border-radius: 2px;
	display: inline-block;
	vertical-align: middle;
	margin-right: 8px;
}
.history-progress-fill {
	height: 100%;
	background: #60a5fa;
	border-radius: 2px;
	transition: width 300ms ease;
}
.history-pct {
	font-size: 11px;
	color: #64748b;
}

.history-pagination {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 16px;
	margin-top: 24px;
}
.history-page-btn {
	width: 36px;
	height: 36px;
	display: flex;
	align-items: center;
	justify-content: center;
	border-radius: 8px;
	background: rgba(255,255,255,0.05);
	border: 1px solid rgba(255,255,255,0.08);
	color: #F9F9F9;
	cursor: pointer;
	transition: all 200ms ease;
}
.history-page-btn:hover:not(:disabled) { background: rgba(255,255,255,0.1); }
.history-page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.history-page-info {
	font-size: 13px;
	color: #94a3b8;
}

@media (max-width: 768px) {
	.history-header { padding: 80px 16px 24px; }
	.history-body { padding: 0 16px 40px; }
	.history-title { font-size: 22px; }
	.history-cell-title { max-width: 200px; }
}
</style>
