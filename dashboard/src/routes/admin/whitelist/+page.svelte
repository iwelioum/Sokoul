<script lang="ts">
	import { onMount } from 'svelte';

	interface ListItem {
		id: string;
		domain: string;
		reason?: string;
		category?: string;
		created_at: string;
	}

	let items: ListItem[] = [];
	let loading = true;
	let error = '';
	let newDomain = '';
	let newReason = '';
	let newCategory = '';
	let adding = false;
	let currentPage = 1;

	onMount(async () => {
		await fetchWhitelist();
	});

	async function fetchWhitelist() {
		loading = true;
		try {
			const response = await fetch(`/api/security/whitelist?page=${currentPage}&limit=10`, {
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				const data = await response.json();
				items = data.items || [];
			} else {
				error = 'Failed to fetch whitelist';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error loading whitelist';
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
			const response = await fetch('/api/security/whitelist', {
				method: 'POST',
				headers: {
					Authorization: `Bearer ${localStorage.getItem('sokoul_token')}`,
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({
					domain: newDomain.trim(),
					category: newCategory || undefined,
					reason: newReason || undefined
				})
			});

			if (response.ok) {
				newDomain = '';
				newReason = '';
				newCategory = '';
				await fetchWhitelist();
			} else if (response.status === 409) {
				error = 'Domain already in whitelist';
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
		if (!confirm(`Remove ${domain} from whitelist?`)) return;

		try {
			const response = await fetch(`/api/security/whitelist/${encodeURIComponent(domain)}`, {
				method: 'DELETE',
				headers: { Authorization: `Bearer ${localStorage.getItem('sokoul_token')}` }
			});

			if (response.ok) {
				await fetchWhitelist();
			} else {
				error = 'Failed to remove domain';
			}
		} catch (err) {
			error = err instanceof Error ? err.message : 'Error removing domain';
		}
	}
</script>

<div class="whitelist-container">
	<header>
		<h1>✅ Whitelist Management</h1>
		<p>Trusted domains that bypass security checks</p>
	</header>

	<form on:submit={handleAddDomain} class="add-form">
		<div class="form-group">
			<input
				type="text"
				placeholder="Domain (e.g., netflix.com)"
				bind:value={newDomain}
				class="form-input"
				required
			/>
			<input
				type="text"
				placeholder="Category (optional, e.g., official_stream)"
				bind:value={newCategory}
				class="form-input"
			/>
			<input
				type="text"
				placeholder="Reason (optional)"
				bind:value={newReason}
				class="form-input"
			/>
			<button type="submit" disabled={adding} class="add-btn">
				{adding ? '➕ Adding...' : '➕ Add Domain'}
			</button>
		</div>
	</form>

	{#if error}
		<div class="error">{error}</div>
	{/if}

	{#if loading}
		<div class="loading">Loading whitelist...</div>
	{:else if items.length === 0}
		<div class="empty">No whitelisted domains</div>
	{:else}
		<div class="items-container">
			{#each items as item}
				<div class="item-card">
					<div class="item-header">
						<div class="item-domain">{item.domain}</div>
						{#if item.category}
							<span class="item-category">{item.category}</span>
						{/if}
					</div>
					{#if item.reason}
						<p class="item-reason">{item.reason}</p>
					{/if}
					<div class="item-footer">
						<span class="item-time">
							Added: {new Date(item.created_at).toLocaleDateString()}
						</span>
						<button
							class="remove-btn"
							on:click={() => handleRemove(item.domain)}
							title="Remove from whitelist"
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
					fetchWhitelist();
				}}
			>
				← Previous
			</button>
			<span>Page {currentPage}</span>
			<button
				disabled={items.length < 10}
				on:click={() => {
					currentPage++;
					fetchWhitelist();
				}}
			>
				Next →
			</button>
		</div>
	{/if}
</div>

<style>
	.whitelist-container {
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

	.add-btn {
		padding: 10px 20px;
		background: rgba(74, 222, 128, 0.2);
		border: 1px solid rgba(74, 222, 128, 0.5);
		color: #4ade80;
		border-radius: 6px;
		cursor: pointer;
		font-weight: 600;
		transition: all 0.3s ease;
		white-space: nowrap;
	}

	.add-btn:hover:not(:disabled) {
		background: rgba(74, 222, 128, 0.3);
		border-color: #4ade80;
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
		border: 1px solid rgba(74, 222, 128, 0.3);
		border-radius: 12px;
		padding: 20px;
		transition: all 0.3s ease;
	}

	.item-card:hover {
		border-color: #4ade80;
		box-shadow: 0 0 15px rgba(74, 222, 128, 0.1);
	}

	.item-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 10px;
	}

	.item-domain {
		font-size: 16px;
		font-weight: 600;
		color: #4ade80;
		word-break: break-all;
		font-family: monospace;
	}

	.item-category {
		padding: 4px 12px;
		background: rgba(74, 222, 128, 0.2);
		color: #4ade80;
		border-radius: 12px;
		font-size: 12px;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.item-reason {
		margin: 10px 0;
		color: #999;
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
