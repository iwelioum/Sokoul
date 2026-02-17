<script lang="ts">
	import { onMount } from 'svelte';

	interface Stat {
		label: string;
		value: string | number;
		icon: string;
		color: string;
	}

	interface RecentLog {
		id: string;
		user_id: string;
		action: string;
		url: string;
		risk_level: 'safe' | 'warning' | 'critical';
		created_at: string;
	}

	let stats: Stat[] = [];
	let recentLogs: RecentLog[] = [];
	let loading = true;
	let error = '';

	onMount(async () => {
		try {
			// Fetch stats
			const response = await fetch('/api/security/status', {
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				const data = await response.json();
				stats = [
					{
						label: 'Safe Downloads',
						value: data.safe_count || 0,
						icon: '✅',
						color: '#4ade80'
					},
					{
						label: 'Warnings',
						value: data.warning_count || 0,
						icon: '⚠️',
						color: '#facc15'
					},
					{
						label: 'Blocked',
						value: data.critical_count || 0,
						icon: '🚫',
						color: '#ef4444'
					},
					{
						label: 'Whitelisted',
						value: data.whitelist_count || 0,
						icon: '💚',
						color: '#64c8ff'
					}
				];
			}

			// Fetch recent logs
			const logsResponse = await fetch('/api/security/audit-logs?page=1&limit=5', {
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (logsResponse.ok) {
				const logsData = await logsResponse.json();
				recentLogs = logsData.logs || [];
			}

			loading = false;
		} catch (err) {
			error = err instanceof Error ? err.message : 'Failed to load stats';
			loading = false;
		}
	});

	const getRiskBadge = (risk: string) => {
		switch (risk) {
			case 'safe':
				return '✅';
			case 'warning':
				return '⚠️';
			case 'critical':
				return '🚫';
			default:
				return '❓';
		}
	};
</script>

<div class="dashboard">
	<header>
		<h1>🛡️ Security Dashboard</h1>
		<p>Real-time monitoring of downloads and streams</p>
	</header>

	{#if loading}
		<div class="loading">Loading statistics...</div>
	{:else if error}
		<div class="error">{error}</div>
	{:else}
		<div class="stats-grid">
			{#each stats as stat}
				<div class="stat-card" style="--color: {stat.color}">
					<div class="stat-icon">{stat.icon}</div>
					<div class="stat-content">
						<div class="stat-value">{stat.value}</div>
						<div class="stat-label">{stat.label}</div>
					</div>
				</div>
			{/each}
		</div>

		<div class="recent-section">
			<h2>📋 Recent Activity</h2>
			{#if recentLogs.length === 0}
				<p class="empty">No recent activity</p>
			{:else}
				<table class="logs-table">
					<thead>
						<tr>
							<th>Time</th>
							<th>Action</th>
							<th>URL</th>
							<th>Risk Level</th>
						</tr>
					</thead>
					<tbody>
						{#each recentLogs as log}
							<tr>
								<td>
									{new Date(log.created_at).toLocaleTimeString()}
								</td>
								<td>{log.action}</td>
								<td class="url-cell">
									{log.url ? log.url.substring(0, 40) + '...' : 'N/A'}
								</td>
								<td class="risk-cell">
									<span class="risk-badge risk-{log.risk_level}">
										{getRiskBadge(log.risk_level)} {log.risk_level}
									</span>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</div>
	{/if}
</div>

<style>
	.dashboard {
		max-width: 1200px;
	}

	header {
		margin-bottom: 40px;
		border-bottom: 2px solid rgba(100, 200, 255, 0.2);
		padding-bottom: 20px;
	}

	header h1 {
		margin: 0 0 10px 0;
		font-size: 28px;
		color: #64c8ff;
	}

	header p {
		margin: 0;
		color: #999;
	}

	.loading,
	.error {
		padding: 30px;
		border-radius: 8px;
		text-align: center;
		font-size: 16px;
	}

	.loading {
		background: rgba(100, 200, 255, 0.1);
		color: #64c8ff;
	}

	.error {
		background: rgba(200, 50, 50, 0.1);
		color: #ff7070;
	}

	.stats-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 20px;
		margin-bottom: 40px;
	}

	.stat-card {
		background: rgba(0, 0, 0, 0.2);
		border: 1px solid rgba(100, 200, 255, 0.2);
		border-radius: 12px;
		padding: 20px;
		display: flex;
		gap: 15px;
		transition: all 0.3s ease;
	}

	.stat-card:hover {
		border-color: var(--color);
		box-shadow: 0 0 20px rgba(100, 200, 255, 0.2);
	}

	.stat-icon {
		font-size: 32px;
		min-width: 50px;
		text-align: center;
	}

	.stat-content {
		flex: 1;
	}

	.stat-value {
		font-size: 24px;
		font-weight: bold;
		color: var(--color);
	}

	.stat-label {
		font-size: 12px;
		color: #999;
		margin-top: 5px;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.recent-section {
		background: rgba(0, 0, 0, 0.2);
		border: 1px solid rgba(100, 200, 255, 0.2);
		border-radius: 12px;
		padding: 20px;
	}

	.recent-section h2 {
		margin: 0 0 20px 0;
		color: #64c8ff;
		font-size: 18px;
	}

	.empty {
		text-align: center;
		color: #999;
		padding: 40px 20px;
		margin: 0;
	}

	.logs-table {
		width: 100%;
		border-collapse: collapse;
	}

	.logs-table thead {
		background: rgba(100, 200, 255, 0.1);
	}

	.logs-table th {
		padding: 12px;
		text-align: left;
		color: #64c8ff;
		font-weight: 600;
		border-bottom: 2px solid rgba(100, 200, 255, 0.2);
	}

	.logs-table td {
		padding: 12px;
		border-bottom: 1px solid rgba(100, 200, 255, 0.1);
		color: #ddd;
	}

	.url-cell {
		max-width: 300px;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		color: #888;
	}

	.risk-badge {
		padding: 4px 12px;
		border-radius: 20px;
		font-size: 12px;
		font-weight: 600;
	}

	.risk-safe {
		background: rgba(74, 222, 128, 0.2);
		color: #4ade80;
	}

	.risk-warning {
		background: rgba(250, 204, 21, 0.2);
		color: #facc15;
	}

	.risk-critical {
		background: rgba(239, 68, 68, 0.2);
		color: #ef4444;
	}
</style>
