<script lang="ts">
	import { onMount } from 'svelte';

	interface ReputationResult {
		url: string;
		domain: string;
		risk_level: 'safe' | 'warning' | 'critical';
		malicious_count: number;
		last_checked: string;
		virustotal_score?: number;
		urlhaus_result?: string;
		reason?: string;
	}

	let searchQuery = '';
	let result: ReputationResult | null = null;
	let loading = false;
	let error = '';
	let searchHistory: string[] = [];

	onMount(() => {
		searchHistory = JSON.parse(localStorage.getItem('reputationSearchHistory') || '[]');
	});

	async function handleSearch(e: Event) {
		e.preventDefault();
		if (!searchQuery.trim()) {
			error = 'Please enter a domain or URL';
			return;
		}

		loading = true;
		error = '';
		result = null;

		try {
			const response = await fetch(`/api/security/reputation/${encodeURIComponent(searchQuery)}`, {
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				result = await response.json();
				if (!searchHistory.includes(searchQuery)) {
					searchHistory = [searchQuery, ...searchHistory.slice(0, 9)];
					localStorage.setItem('reputationSearchHistory', JSON.stringify(searchHistory));
				}
			} else if (response.status === 404) {
				result = {
					url: searchQuery,
					domain: searchQuery,
					risk_level: 'safe',
					malicious_count: 0,
					last_checked: new Date().toISOString(),
					reason: 'No data found, assuming safe'
				};
			} else {
				error = 'Failed to fetch reputation';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error fetching reputation';
		} finally {
			loading = false;
		}
	}

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
</script>

<div class="reputation-container">
	<header>
		<h1>🔍 Reputation Lookup</h1>
		<p>Check domain or URL reputation status</p>
	</header>

	<form on:submit={handleSearch} class="search-form">
		<div class="search-box">
			<input
				type="text"
				placeholder="Enter domain or URL (e.g., example.com or https://example.com)"
				bind:value={searchQuery}
				class="search-input"
			/>
			<button type="submit" disabled={loading} class="search-btn">
				{loading ? '🔄 Searching...' : '🔍 Search'}
			</button>
		</div>

		{#if searchHistory.length > 0}
			<div class="history">
				<p>Recent searches:</p>
				<div class="history-tags">
					{#each searchHistory as item}
						<button
							type="button"
							class="history-tag"
							on:click={() => {
								searchQuery = item;
							}}
						>
							{item}
						</button>
					{/each}
				</div>
			</div>
		{/if}
	</form>

	{#if error}
		<div class="error">{error}</div>
	{/if}

	{#if result}
		<div class="result-card" style="--risk-color: {getRiskColor(result.risk_level)}">
			<div class="result-header">
				<div class="risk-indicator">
					<span class="risk-icon">{getRiskIcon(result.risk_level)}</span>
					<div class="risk-info">
						<span class="risk-level">{result.risk_level.toUpperCase()}</span>
						<span class="domain-name">{result.domain}</span>
					</div>
				</div>
				<div class="checked-time">
					Last checked: {new Date(result.last_checked).toLocaleString()}
				</div>
			</div>

			<div class="result-grid">
				<div class="result-item">
					<span class="result-label">Malicious Vendors</span>
					<span class="result-value">{result.malicious_count}</span>
				</div>

				{#if result.virustotal_score !== undefined}
					<div class="result-item">
						<span class="result-label">VirusTotal Score</span>
						<span class="result-value">{result.virustotal_score}/100</span>
					</div>
				{/if}

				{#if result.urlhaus_result}
					<div class="result-item">
						<span class="result-label">URLhaus Status</span>
						<span class="result-value">{result.urlhaus_result}</span>
					</div>
				{/if}
			</div>

			{#if result.reason}
				<div class="result-reason">
					<strong>Reason:</strong> {result.reason}
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.reputation-container {
		max-width: 800px;
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

	.search-form {
		margin-bottom: 30px;
	}

	.search-box {
		display: flex;
		gap: 10px;
	}

	.search-input {
		flex: 1;
		padding: 12px 16px;
		background: rgba(0, 0, 0, 0.3);
		border: 1px solid rgba(100, 200, 255, 0.3);
		border-radius: 8px;
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

	.search-btn {
		padding: 12px 24px;
		background: rgba(100, 200, 255, 0.2);
		border: 1px solid rgba(100, 200, 255, 0.5);
		color: #64c8ff;
		border-radius: 8px;
		cursor: pointer;
		font-weight: 600;
		transition: all 0.3s ease;
		white-space: nowrap;
	}

	.search-btn:hover:not(:disabled) {
		background: rgba(100, 200, 255, 0.3);
		border-color: #64c8ff;
	}

	.search-btn:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}

	.history {
		margin-top: 15px;
		padding-top: 15px;
		border-top: 1px solid rgba(100, 200, 255, 0.2);
	}

	.history p {
		margin: 0 0 10px 0;
		color: #999;
		font-size: 13px;
	}

	.history-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}

	.history-tag {
		padding: 6px 12px;
		background: rgba(100, 200, 255, 0.1);
		border: 1px solid rgba(100, 200, 255, 0.3);
		color: #64c8ff;
		border-radius: 20px;
		cursor: pointer;
		font-size: 12px;
		transition: all 0.3s ease;
	}

	.history-tag:hover {
		background: rgba(100, 200, 255, 0.2);
		border-color: #64c8ff;
	}

	.error {
		padding: 15px 20px;
		background: rgba(200, 50, 50, 0.1);
		border: 1px solid rgba(200, 50, 50, 0.3);
		color: #ff7070;
		border-radius: 8px;
		margin-bottom: 20px;
	}

	.result-card {
		background: rgba(0, 0, 0, 0.2);
		border: 2px solid var(--risk-color);
		border-radius: 12px;
		padding: 30px;
	}

	.result-header {
		margin-bottom: 30px;
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 20px;
	}

	.risk-indicator {
		display: flex;
		gap: 15px;
		align-items: center;
	}

	.risk-icon {
		font-size: 48px;
		min-width: 60px;
		text-align: center;
	}

	.risk-info {
		display: flex;
		flex-direction: column;
	}

	.risk-level {
		font-size: 18px;
		font-weight: bold;
		color: var(--risk-color);
		text-transform: uppercase;
	}

	.domain-name {
		font-size: 14px;
		color: #999;
		margin-top: 5px;
		font-family: monospace;
	}

	.checked-time {
		font-size: 12px;
		color: #999;
		text-align: right;
	}

	.result-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
		gap: 15px;
		margin-bottom: 20px;
	}

	.result-item {
		background: rgba(100, 200, 255, 0.05);
		border: 1px solid rgba(100, 200, 255, 0.2);
		padding: 15px;
		border-radius: 8px;
		display: flex;
		flex-direction: column;
	}

	.result-label {
		font-size: 12px;
		color: #999;
		margin-bottom: 8px;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.result-value {
		font-size: 20px;
		font-weight: bold;
		color: #64c8ff;
	}

	.result-reason {
		background: rgba(100, 200, 255, 0.1);
		padding: 15px;
		border-radius: 8px;
		color: #ddd;
		font-size: 14px;
		line-height: 1.5;
	}

	.result-reason strong {
		color: #64c8ff;
	}
</style>
