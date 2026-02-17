<script lang="ts">
	import { onMount } from 'svelte';

	interface AuditLog {
		id: string;
		user_id: string;
		action: string;
		url: string;
		risk_level: 'safe' | 'warning' | 'critical';
		status: string;
		created_at: string;
	}

	let logs: AuditLog[] = [];
	let loading = true;
	let error = '';
	let currentPage = 1;
	let selectedRisk: string = '';
	let searchAction = '';

	onMount(async () => {
		await fetchLogs();
	});

	async function fetchLogs() {
		loading = true;
		try {
			let url = `/api/security/audit-logs?page=${currentPage}&limit=20`;
			if (selectedRisk) {
				url = `/api/security/audit-logs/${selectedRisk}?page=${currentPage}&limit=20`;
			}

			const response = await fetch(url, {
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				const data = await response.json();
				logs = data.logs || [];
			} else {
				error = 'Failed to fetch logs';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error loading logs';
		} finally {
			loading = false;
		}
	}

	const handleRiskFilter = (risk: string) => {
		selectedRisk = selectedRisk === risk ? '' : risk;
		currentPage = 1;
		fetchLogs();
	};

	const getRiskColor = (risk: string) => {
		switch (risk) {
			case 'safe':
				return '#4ade80';
			case 'warning':
				return '#facc15';
			case 'critical':
				return '#ef4444';
			default:
				return '#999';
		}
	};

	const getRiskIcon = (risk: string) => {
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

	const filteredLogs = logs.filter((log) => {
		if (searchAction && !log.action.toLowerCase().includes(searchAction.toLowerCase())) {
			return false;
		}
		return true;
	});
</script>

<div class="audit-container">
	<header>
		<h1>📋 Audit Logs</h1>
		<p>Track all download and streaming activities</p>
	</header>

	<div class="filters">
		<input
			type="text"
			placeholder="Search by action..."
			bind:value={searchAction}
			class="search-input"
		/>

		<div class="risk-filters">
			<button
				class={`risk-btn ${selectedRisk === 'safe' ? 'active' : ''}`}
				on:click={() => handleRiskFilter('safe')}
			>
				✅ Safe
			</button>
			<button
				class={`risk-btn ${selectedRisk === 'warning' ? 'active' : ''}`}
				on:click={() => handleRiskFilter('warning')}
			>
				⚠️ Warning
			</button>
			<button
				class={`risk-btn ${selectedRisk === 'critical' ? 'active' : ''}`}
				on:click={() => handleRiskFilter('critical')}
			>
				🚫 Critical
			</button>
		</div>
	</div>

	{#if loading}
		<div class="loading">Loading logs...</div>
	{:else if error}
		<div class="error">{error}</div>
	{:else if filteredLogs.length === 0}
		<div class="empty">No logs found</div>
	{:else}
		<div class="table-wrapper">
			<table class="logs-table">
				<thead>
					<tr>
						<th>Time</th>
						<th>User</th>
						<th>Action</th>
						<th>URL</th>
						<th>Risk</th>
						<th>Status</th>
					</tr>
				</thead>
				<tbody>
					{#each filteredLogs as log}
						<tr>
							<td>
								{new Date(log.created_at).toLocaleString()}
							</td>
							<td class="user-cell">
								{log.user_id ? log.user_id.substring(0, 8) : 'anonymous'}
							</td>
							<td class="action-cell">{log.action}</td>
							<td class="url-cell" title={log.url}>
								{log.url ? log.url.substring(0, 50) + '...' : 'N/A'}
							</td>
							<td>
								<span
									class="risk-badge"
									style="--risk-color: {getRiskColor(log.risk_level)}"
								>
									{getRiskIcon(log.risk_level)} {log.risk_level}
								</span>
							</td>
							<td>
								<span class={`status-badge status-${log.status}`}>
									{log.status}
								</span>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>

		<div class="pagination">
			<button
				disabled={currentPage === 1}
				on:click={() => {
					currentPage--;
					fetchLogs();
				}}
			>
				← Previous
			</button>
			<span>Page {currentPage}</span>
			<button
				disabled={filteredLogs.length < 20}
				on:click={() => {
					currentPage++;
					fetchLogs();
				}}
			>
				Next →
			</button>
		</div>
	{/if}
</div>

<style>
	.audit-container {
		max-width: 1400px;
	}

	header {
		margin-bottom: 30px;
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

	.filters {
		display: flex;
		gap: 15px;
		margin-bottom: 30px;
		flex-wrap: wrap;
	}

	.search-input {
		flex: 1;
		min-width: 200px;
		padding: 10px 15px;
		background: rgba(0, 0, 0, 0.3);
		border: 1px solid rgba(100, 200, 255, 0.3);
		border-radius: 6px;
		color: #fff;
		font-size: 14px;
	}

	.search-input::placeholder {
		color: #666;
	}

	.search-input:focus {
		outline: none;
		border-color: #64c8ff;
		box-shadow: 0 0 10px rgba(100, 200, 255, 0.2);
	}

	.risk-filters {
		display: flex;
		gap: 10px;
	}

	.risk-btn {
		padding: 8px 16px;
		background: rgba(0, 0, 0, 0.2);
		border: 1px solid rgba(100, 200, 255, 0.3);
		color: #999;
		border-radius: 6px;
		cursor: pointer;
		transition: all 0.3s ease;
		font-size: 14px;
	}

	.risk-btn:hover {
		border-color: #64c8ff;
		color: #64c8ff;
	}

	.risk-btn.active {
		background: rgba(100, 200, 255, 0.2);
		border-color: #64c8ff;
		color: #64c8ff;
		font-weight: 600;
	}

	.loading,
	.error,
	.empty {
		padding: 40px;
		text-align: center;
		border-radius: 8px;
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

	.empty {
		background: rgba(0, 0, 0, 0.2);
		color: #999;
	}

	.table-wrapper {
		background: rgba(0, 0, 0, 0.2);
		border: 1px solid rgba(100, 200, 255, 0.2);
		border-radius: 12px;
		overflow-x: auto;
	}

	.logs-table {
		width: 100%;
		border-collapse: collapse;
	}

	.logs-table thead {
		background: rgba(100, 200, 255, 0.1);
	}

	.logs-table th {
		padding: 15px;
		text-align: left;
		color: #64c8ff;
		font-weight: 600;
		border-bottom: 2px solid rgba(100, 200, 255, 0.2);
		white-space: nowrap;
	}

	.logs-table td {
		padding: 12px 15px;
		border-bottom: 1px solid rgba(100, 200, 255, 0.1);
		color: #ddd;
	}

	.user-cell,
	.action-cell {
		font-family: monospace;
		font-size: 13px;
		color: #999;
	}

	.url-cell {
		max-width: 300px;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		color: #888;
		font-size: 13px;
	}

	.risk-badge {
		padding: 4px 12px;
		border-radius: 20px;
		font-size: 12px;
		font-weight: 600;
		background: rgba(var(--risk-color), 0.2);
		color: var(--risk-color);
	}

	.status-badge {
		padding: 4px 12px;
		border-radius: 20px;
		font-size: 12px;
		font-weight: 600;
	}

	.status-allowed {
		background: rgba(74, 222, 128, 0.2);
		color: #4ade80;
	}

	.status-flagged {
		background: rgba(250, 204, 21, 0.2);
		color: #facc15;
	}

	.status-blocked {
		background: rgba(239, 68, 68, 0.2);
		color: #ef4444;
	}

	.pagination {
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 15px;
		margin-top: 30px;
	}

	.pagination button {
		padding: 10px 20px;
		background: rgba(100, 200, 255, 0.1);
		border: 1px solid rgba(100, 200, 255, 0.3);
		color: #64c8ff;
		border-radius: 6px;
		cursor: pointer;
		transition: all 0.3s ease;
	}

	.pagination button:hover:not(:disabled) {
		background: rgba(100, 200, 255, 0.2);
		border-color: #64c8ff;
	}

	.pagination button:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.pagination span {
		color: #999;
		font-weight: 600;
	}
</style>
