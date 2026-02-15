<script lang="ts">
	import { listTasks, formatDate } from '$lib/api/client';
	import type { Task } from '$lib/api/client';

	let tasks: Task[] = $state([]);
	let loading = $state(true);
	let filter = $state('all');
	let expandedErrors = $state<Set<string>>(new Set());

	let counts = $derived({
		all: tasks.length,
		pending: tasks.filter((t) => t.status === 'pending').length,
		running: tasks.filter((t) => t.status === 'running').length,
		completed: tasks.filter((t) => t.status === 'completed').length,
		failed: tasks.filter((t) => t.status === 'failed').length
	});

	let filtered = $derived(
		filter === 'all' ? tasks : tasks.filter((t) => t.status === filter)
	);

	$effect(() => {
		loadTasks();
		const interval = setInterval(loadTasks, 5000);
		return () => clearInterval(interval);
	});

	async function loadTasks() {
		try {
			tasks = await listTasks();
		} catch (e) {
			console.error('Failed to load tasks:', e);
		}
		loading = false;
	}

	function toggleError(id: string) {
		const next = new Set(expandedErrors);
		if (next.has(id)) {
			next.delete(id);
		} else {
			next.add(id);
		}
		expandedErrors = next;
	}

	function relativeTime(dateStr: string | null): string {
		if (!dateStr) return '-';
		const now = Date.now();
		const then = new Date(dateStr).getTime();
		const diffSec = Math.floor((now - then) / 1000);

		if (diffSec < 0) return 'dans le futur';
		if (diffSec < 60) return `il y a ${diffSec}s`;
		const diffMin = Math.floor(diffSec / 60);
		if (diffMin < 60) return `il y a ${diffMin} min`;
		const diffH = Math.floor(diffMin / 60);
		if (diffH < 24) return `il y a ${diffH}h`;
		const diffD = Math.floor(diffH / 24);
		return `il y a ${diffD}j`;
	}

	function statusLabel(status: string): string {
		switch (status) {
			case 'running': return 'En cours';
			case 'completed': return 'Termine';
			case 'failed': return 'Echoue';
			case 'pending': return 'En attente';
			case 'cancelled': return 'Annule';
			default: return status;
		}
	}

	type FilterKey = 'all' | 'running' | 'pending' | 'completed' | 'failed';

	const filterTabs: { key: FilterKey; label: string }[] = [
		{ key: 'all', label: 'Tout' },
		{ key: 'running', label: 'En cours' },
		{ key: 'pending', label: 'En attente' },
		{ key: 'completed', label: 'Termine' },
		{ key: 'failed', label: 'Echec' }
	];
</script>

<div class="tasks-page">
	<h1 class="page-title">
		<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="24" height="24">
			<path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/>
			<rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
		</svg>
		Taches
	</h1>

	<!-- Filter Tabs -->
	<div class="filter-tabs">
		{#each filterTabs as tab}
			<button
				class="filter-tab"
				class:filter-active={filter === tab.key}
				onclick={() => filter = tab.key}
			>
				{tab.label}
				<span
					class="filter-count"
					class:count-pulse={counts[tab.key] > 0 && tab.key !== 'all' && tab.key !== 'completed'}
				>
					{counts[tab.key]}
				</span>
			</button>
		{/each}
	</div>

	{#if loading}
		<div class="loading-container">
			<span class="spinner"></span>
			<span>Chargement...</span>
		</div>
	{:else if filtered.length === 0}
		<div class="empty-state">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
				<path d="M16 4h2a2 2 0 012 2v14a2 2 0 01-2 2H6a2 2 0 01-2-2V6a2 2 0 012-2h2"/>
				<rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
			</svg>
			<p>Aucune tache{filter !== 'all' ? ` avec le statut "${filterTabs.find(t => t.key === filter)?.label}"` : ''}.</p>
		</div>
	{:else}
		<div class="task-timeline">
			{#each filtered as task, i}
				<div class="task-row" style="animation-delay: {i * 0.04}s">
					<!-- Timeline Dot & Line -->
					<div class="timeline-track">
						<div class="timeline-dot" class:dot-running={task.status === 'running'} class:dot-completed={task.status === 'completed'} class:dot-failed={task.status === 'failed'} class:dot-pending={task.status === 'pending'}>
							{#if task.status === 'running'}
								<span class="dot-pulse-ring"></span>
							{/if}
						</div>
						{#if i < filtered.length - 1}
							<div class="timeline-line"></div>
						{/if}
					</div>

					<!-- Task Card -->
					<div class="task-card">
						<div class="task-header">
							<span class="task-status-badge" class:ts-running={task.status === 'running'} class:ts-completed={task.status === 'completed'} class:ts-failed={task.status === 'failed'} class:ts-pending={task.status === 'pending'}>
								{statusLabel(task.status)}
							</span>
							<span class="task-type">{task.task_type}</span>
							<span class="task-id">{task.id.slice(0, 8)}</span>
							<span class="task-time" title={formatDate(task.created_at)}>
								{relativeTime(task.created_at)}
							</span>
						</div>

						{#if task.payload?.title}
							<div class="task-payload">
								<span class="payload-title">{task.payload.title}</span>
							</div>
						{/if}

						{#if task.status === 'running' && task.progress != null}
							<div class="progress-container">
								<div class="progress-bar">
									<div class="progress-fill progress-animated" style="width: {task.progress}%"></div>
								</div>
								<span class="progress-label">{task.progress.toFixed(0)}%</span>
							</div>
						{/if}

						{#if task.error}
							<div class="error-box" class:error-expanded={expandedErrors.has(task.id)}>
								<button class="error-header" onclick={() => toggleError(task.id)}>
									<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
										<circle cx="12" cy="12" r="10"/>
										<line x1="12" y1="8" x2="12" y2="12"/>
										<line x1="12" y1="16" x2="12.01" y2="16"/>
									</svg>
									<span class="error-label">Erreur</span>
									<svg class="error-chevron" class:error-chevron-open={expandedErrors.has(task.id)} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
										<polyline points="9 18 15 12 9 6"/>
									</svg>
								</button>
								{#if expandedErrors.has(task.id)}
									<div class="error-content">
										{task.error}
									</div>
								{/if}
							</div>
						{/if}

						<div class="task-dates">
							{#if task.started_at}
								<span class="task-date-item">
									<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
										<circle cx="12" cy="12" r="10"/>
										<polyline points="12 6 12 12 16 14"/>
									</svg>
									Demarre: {relativeTime(task.started_at)}
								</span>
							{/if}
							{#if task.completed_at}
								<span class="task-date-item">
									<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
										<path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
										<polyline points="22 4 12 14.01 9 11.01"/>
									</svg>
									Termine: {relativeTime(task.completed_at)}
								</span>
							{/if}
						</div>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>

<style>
	.tasks-page {
		max-width: 900px;
		margin: 0 auto;
		padding: 40px 20px;
	}

	.page-title {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 24px;
		font-weight: 800;
		color: var(--text-primary, #eee);
		margin: 0 0 24px 0;
	}

	/* Filter Tabs */
	.filter-tabs {
		display: flex;
		gap: 6px;
		margin-bottom: 28px;
		flex-wrap: wrap;
	}

	.filter-tab {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 8px 18px;
		border-radius: 12px;
		background: var(--bg-card, #1a1a2e);
		border: 1px solid var(--border, #2a2a4a);
		color: var(--text-secondary, #aaa);
		font-size: 13px;
		font-weight: 500;
		cursor: pointer;
		transition: all 0.2s;
	}

	.filter-tab:hover {
		border-color: var(--accent, #6c5ce7);
		color: var(--text-primary, #eee);
	}

	.filter-tab.filter-active {
		background: var(--accent, #6c5ce7);
		border-color: var(--accent, #6c5ce7);
		color: white;
	}

	.filter-count {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 22px;
		height: 22px;
		padding: 0 6px;
		border-radius: 11px;
		background: rgba(255, 255, 255, 0.1);
		font-size: 11px;
		font-weight: 700;
	}

	.filter-active .filter-count {
		background: rgba(255, 255, 255, 0.25);
	}

	.count-pulse {
		animation: countPulse 2s ease-in-out infinite;
	}

	/* Loading / Empty */
	.loading-container {
		display: flex;
		align-items: center;
		gap: 12px;
		justify-content: center;
		padding: 60px 0;
		color: var(--text-muted, #888);
	}

	.empty-state {
		text-align: center;
		padding: 60px 20px;
		color: var(--text-muted, #888);
	}

	.empty-state svg {
		margin-bottom: 12px;
		opacity: 0.4;
	}

	.empty-state p {
		font-size: 14px;
	}

	/* Task Timeline */
	.task-timeline {
		display: flex;
		flex-direction: column;
	}

	.task-row {
		display: flex;
		gap: 16px;
		animation: fadeSlideIn 0.3s ease-out both;
	}

	/* Timeline Track */
	.timeline-track {
		display: flex;
		flex-direction: column;
		align-items: center;
		width: 20px;
		flex-shrink: 0;
		padding-top: 20px;
	}

	.timeline-dot {
		width: 14px;
		height: 14px;
		border-radius: 50%;
		flex-shrink: 0;
		position: relative;
		z-index: 1;
	}

	.dot-running {
		background: var(--warning, #fdcb6e);
	}

	.dot-completed {
		background: var(--success, #00b894);
	}

	.dot-failed {
		background: var(--danger, #ff5252);
	}

	.dot-pending {
		background: var(--accent, #6c5ce7);
	}

	.dot-pulse-ring {
		position: absolute;
		inset: -4px;
		border-radius: 50%;
		border: 2px solid var(--warning, #fdcb6e);
		animation: dotPulse 1.5s ease-out infinite;
	}

	.timeline-line {
		width: 2px;
		flex: 1;
		background: var(--border, #2a2a4a);
		min-height: 16px;
	}

	/* Task Card */
	.task-card {
		flex: 1;
		background: var(--bg-card, #1a1a2e);
		border: 1px solid var(--border, #2a2a4a);
		border-radius: 14px;
		padding: 16px 20px;
		margin-bottom: 12px;
		min-width: 0;
		transition: border-color 0.2s;
	}

	.task-card:hover {
		border-color: rgba(108, 92, 231, 0.3);
	}

	.task-header {
		display: flex;
		align-items: center;
		gap: 10px;
		flex-wrap: wrap;
	}

	.task-status-badge {
		padding: 3px 10px;
		border-radius: 6px;
		font-size: 11px;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.ts-running {
		background: rgba(253, 203, 110, 0.15);
		color: var(--warning, #fdcb6e);
	}

	.ts-completed {
		background: rgba(0, 184, 148, 0.15);
		color: var(--success, #00b894);
	}

	.ts-failed {
		background: rgba(255, 82, 82, 0.15);
		color: var(--danger, #ff5252);
	}

	.ts-pending {
		background: rgba(108, 92, 231, 0.15);
		color: var(--accent, #6c5ce7);
	}

	.task-type {
		font-weight: 700;
		font-size: 13px;
		text-transform: uppercase;
		color: var(--text-secondary, #aaa);
		letter-spacing: 0.5px;
	}

	.task-id {
		font-family: monospace;
		font-size: 11px;
		color: var(--text-muted, #666);
		background: var(--bg-hover, #252545);
		padding: 2px 8px;
		border-radius: 4px;
	}

	.task-time {
		margin-left: auto;
		font-size: 12px;
		color: var(--text-muted, #888);
		white-space: nowrap;
	}

	.task-payload {
		margin-top: 8px;
	}

	.payload-title {
		font-size: 14px;
		color: var(--text-primary, #eee);
		font-weight: 500;
	}

	/* Progress */
	.progress-container {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-top: 10px;
	}

	.progress-bar {
		flex: 1;
		height: 5px;
		background: var(--bg-hover, #252545);
		border-radius: 3px;
		overflow: hidden;
	}

	.progress-fill {
		height: 100%;
		border-radius: 3px;
		transition: width 0.5s ease;
	}

	.progress-animated {
		background: linear-gradient(
			90deg,
			var(--accent, #6c5ce7),
			#a29bfe,
			var(--accent, #6c5ce7)
		);
		background-size: 200% 100%;
		animation: shimmer 2s linear infinite;
	}

	.progress-label {
		font-size: 12px;
		font-weight: 700;
		color: var(--accent, #6c5ce7);
		min-width: 36px;
		text-align: right;
	}

	/* Error Box */
	.error-box {
		margin-top: 10px;
		border: 1px solid rgba(255, 82, 82, 0.2);
		border-radius: 10px;
		overflow: hidden;
		background: rgba(255, 82, 82, 0.05);
	}

	.error-header {
		display: flex;
		align-items: center;
		gap: 8px;
		width: 100%;
		padding: 10px 14px;
		background: none;
		border: none;
		color: var(--danger, #ff5252);
		cursor: pointer;
		font-size: 13px;
		font-weight: 600;
	}

	.error-header:hover {
		background: rgba(255, 82, 82, 0.05);
	}

	.error-label {
		flex: 1;
		text-align: left;
	}

	.error-chevron {
		transition: transform 0.2s;
		flex-shrink: 0;
	}

	.error-chevron-open {
		transform: rotate(90deg);
	}

	.error-content {
		padding: 0 14px 12px 14px;
		font-size: 12px;
		color: var(--danger, #ff5252);
		line-height: 1.6;
		font-family: monospace;
		white-space: pre-wrap;
		word-break: break-all;
		animation: fadeIn 0.2s ease-out;
	}

	/* Task Dates */
	.task-dates {
		margin-top: 10px;
		display: flex;
		gap: 16px;
		flex-wrap: wrap;
	}

	.task-date-item {
		display: inline-flex;
		align-items: center;
		gap: 5px;
		font-size: 12px;
		color: var(--text-muted, #888);
	}

	/* Spinner */
	.spinner {
		width: 20px;
		height: 20px;
		border: 2px solid rgba(255, 255, 255, 0.3);
		border-top-color: white;
		border-radius: 50%;
		animation: spin 0.6s linear infinite;
		display: inline-block;
	}

	/* Animations */
	@keyframes spin {
		to { transform: rotate(360deg); }
	}

	@keyframes fadeSlideIn {
		from { opacity: 0; transform: translateY(12px); }
		to { opacity: 1; transform: translateY(0); }
	}

	@keyframes fadeIn {
		from { opacity: 0; }
		to { opacity: 1; }
	}

	@keyframes shimmer {
		0% { background-position: -200% 0; }
		100% { background-position: 200% 0; }
	}

	@keyframes countPulse {
		0%, 100% { transform: scale(1); }
		50% { transform: scale(1.15); }
	}

	@keyframes dotPulse {
		0% { transform: scale(1); opacity: 1; }
		100% { transform: scale(2); opacity: 0; }
	}

	@media (max-width: 640px) {
		.filter-tabs {
			gap: 4px;
		}

		.filter-tab {
			padding: 6px 12px;
			font-size: 12px;
		}

		.timeline-track {
			display: none;
		}

		.task-header {
			gap: 6px;
		}

		.task-time {
			width: 100%;
			margin-left: 0;
			margin-top: 4px;
		}
	}
</style>
