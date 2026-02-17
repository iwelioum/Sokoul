<script lang="ts">
	import { onMount } from 'svelte';

	interface ListItem {
		id: string;
		domain: string;
		reason?: string;
		severity?: string;
		source?: string;
		created_at: string;
	}

	let items: ListItem[] = [];
	let loading = true;
	let error = '';
	let newDomain = '';
	let newReason = '';
	let newSeverity = 'high';
	let adding = false;
	let currentPage = 1;

	const severityOptions = ['low', 'medium', 'high', 'critical'];

	onMount(async () => {
		await fetchBlacklist();
	});

	async function fetchBlacklist() {
		loading = true;
		try {
			const response = await fetch(`/api/security/blacklist?page=${currentPage}&limit=10`, {
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				const data = await response.json();
				items = data.items || [];
			} else {
				error = 'Failed to fetch blacklist';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error loading blacklist';
		} finally {
			loading = false;
		}
	}

	async function handleAddDomain(e: Event) {
		e.preventDefault();
		if (!newDomain.trim()) {
			error = 'Please enter a domain';
			return;
		}

		adding = true;
		error = '';

		try {
			const response = await fetch('/api/security/blacklist', {
				method: 'POST',
				headers: {
					Authorization: `Bearer ${localStorage.getItem('sokoul_token')}`,
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({
					domain: newDomain.trim(),
					reason: newReason || undefined,
					severity: newSeverity
				})
			});

			if (response.ok) {
				newDomain = '';
				newReason = '';
				newSeverity = 'high';
				await fetchBlacklist();
			} else if (response.status === 409) {
				error = 'Domain already in blacklist';
			} else {
				error = 'Failed to add domain';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error adding domain';
		} finally {
			adding = false;
		}
	}

	async function handleRemove(domain: string) {
		if (!confirm(`Remove ${domain} from blacklist?`)) return;

		try {
			const response = await fetch(`/api/security/blacklist/${encodeURIComponent(domain)}`, {
				method: 'DELETE',
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				await fetchBlacklist();
			} else {
				error = 'Failed to remove domain';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error removing domain';
		}
	}

	const getSeverityColor = (severity: string) => {
		switch (severity) {
			case 'low':
				return '#facc15';
			case 'medium':
				return '#f97316';
			case 'high':
				return '#ef4444';
			case 'critical':
				return '#7c2d12';
			default:
				return '#999';
		}
	};

	const getSeverityIcon = (severity: string) => {
		switch (severity) {
			case 'low':
				return '⚠️';
			case 'medium':
				return '⚠️⚠️';
			case 'high':
				return '🚫';
			case 'critical':
				return '💀';
			default:
				return '❓';
		}
	};
</script>

<div class="blacklist-container">
	<header>
		<h1>⛔ Blacklist Management</h1>
		<p>Blocked domains that are flagged for malware, phishing, or abuse</p>
	</header>

	<form on:submit={handleAddDomain} class="add-form">
		<div class="form-group">
			<input
				type="text"
				placeholder="Domain to block (e.g., malicious-site.com)"
				bind:value={newDomain}
				class="form-input"
				required
			/>
			<input
				type="text"
				placeholder="Reason (optional)"
				bind:value={newReason}
				class="form-input"
			/>
			<select bind:value={newSeverity} class="form-input">
				{#each severityOptions as option}
					<option value={option}>{option.charAt(0).toUpperCase() + option.slice(1)}</option>
				{/each}
			</select>
			<button type="submit" disabled={adding} class="add-btn">
				{adding ? '➕ Adding...' : '➕ Block Domain'}
			</button>
		</div>
	</form>

	{#if error}
		<div class="error">{error}</div>
	{/if}

	{#if loading}
		<div class="loading">Loading blacklist...</div>
	{:else if items.length === 0}
		<div class="empty">No blacklisted domains</div>
	{:else}
		<div class="items-container">
			{#each items as item}
				<div class="item-card" style="--severity-color: {getSeverityColor(item.severity || 'high')}">
					<div class="item-header">
						<div class="item-domain">{item.domain}</div>
						<span class="severity-badge">
							{getSeverityIcon(item.severity || 'high')} {(item.severity || 'high').toUpperCase()}
						</span>
					</div>
					{#if item.source}
						<p class="item-source">Source: {item.source}</p>
					{/if}
					{#if item.reason}
						<p class="item-reason">{item.reason}</p>
					{/if}
					<div class="item-footer">
						<span class="item-time">
							Blocked: {new Date(item.created_at).toLocaleDateString()}
						</span>
						<button
							class="remove-btn"
							on:click={() => handleRemove(item.domain)}
							title="Remove from blacklist"
						>
							🗑️
						</button>
					</div>
				</div>
			{/each}
		</div>

		<div class="pagination">
			<button
				disabled={currentPage === 1}
				on:click={() => {
					currentPage--;
					fetchBlacklist();
				}}
			>
				← Previous
			</button>
			<span>Page {currentPage}</span>
			<button
				disabled={items.length < 10}
				on:click={() => {
					currentPage++;
					fetchBlacklist();
				}}
			>
				Next →
			</button>
		</div>
	{/if}
</div>

<style>
	.blacklist-container {
		max-width: 900px;
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

	.add-form {
		background: rgba(0, 0, 0, 0.2);
		border: 1px solid rgba(100, 200, 255, 0.2);
		border-radius: 12px;
		padding: 20px;
		margin-bottom: 30px;
	}

	.form-group {
		display: flex;
		gap: 10px;
		flex-wrap: wrap;
	}

	.form-input {
		flex: 1;
		min-width: 150px;
		padding: 10px 15px;
		background: rgba(0, 0, 0, 0.3);
		border: 1px solid rgba(100, 200, 255, 0.3);
		border-radius: 6px;
		color: #fff;
		font-size: 14px;
	}

	.form-input::placeholder {
		color: #666;
	}

	.form-input:focus {
		outline: none;
		border-color: #64c8ff;
		box-shadow: 0 0 10px rgba(100, 200, 255, 0.2);
	}

	select.form-input {
		cursor: pointer;
	}

	select.form-input option {
		background: #1a1a3e;
		color: #fff;
	}

	.add-btn {
		padding: 10px 20px;
		background: rgba(200, 50, 50, 0.2);
		border: 1px solid rgba(200, 50, 50, 0.5);
		color: #ff7070;
		border-radius: 6px;
		cursor: pointer;
		font-weight: 600;
		transition: all 0.3s ease;
		white-space: nowrap;
	}

	.add-btn:hover:not(:disabled) {
		background: rgba(200, 50, 50, 0.3);
		border-color: #ff7070;
	}

	.add-btn:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}

	.error {
		padding: 15px 20px;
		background: rgba(200, 50, 50, 0.1);
		border: 1px solid rgba(200, 50, 50, 0.3);
		color: #ff7070;
		border-radius: 8px;
		margin-bottom: 20px;
	}

	.loading,
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

	.empty {
		background: rgba(0, 0, 0, 0.2);
		color: #999;
	}

	.items-container {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
		gap: 15px;
		margin-bottom: 30px;
	}

	.item-card {
		background: rgba(0, 0, 0, 0.2);
		border: 2px solid var(--severity-color);
		border-radius: 12px;
		padding: 20px;
		transition: all 0.3s ease;
	}

	.item-card:hover {
		box-shadow: 0 0 15px rgba(239, 68, 68, 0.2);
	}

	.item-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 10px;
		gap: 10px;
	}

	.item-domain {
		font-size: 16px;
		font-weight: 600;
		color: #ef4444;
		word-break: break-all;
		font-family: monospace;
		flex: 1;
	}

	.severity-badge {
		padding: 4px 12px;
		background: var(--severity-color);
		color: #0f0f23;
		border-radius: 12px;
		font-size: 12px;
		font-weight: 600;
		white-space: nowrap;
	}

	.item-source {
		margin: 8px 0 0 0;
		color: #999;
		font-size: 12px;
		font-style: italic;
	}

	.item-reason {
		margin: 8px 0;
		color: #ccc;
		font-size: 13px;
		line-height: 1.4;
	}

	.item-footer {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding-top: 15px;
		border-top: 1px solid rgba(100, 200, 255, 0.1);
	}

	.item-time {
		font-size: 12px;
		color: #666;
	}

	.remove-btn {
		background: none;
		border: none;
		color: #999;
		cursor: pointer;
		font-size: 16px;
		transition: all 0.3s ease;
		padding: 5px 10px;
	}

	.remove-btn:hover {
		color: #ff7070;
		transform: scale(1.2);
	}

	.pagination {
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 15px;
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
</style>
