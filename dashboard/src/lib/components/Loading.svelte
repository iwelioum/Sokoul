<script lang="ts">
	export let loading: boolean = false;
	export let size: 'sm' | 'md' | 'lg' = 'md';
	export let message: string = 'Loading...';
	export let overlay: boolean = false;
</script>

<div class="loading-container" class:overlay class:size-{size}>
	<div class="spinner"></div>
	{#if message}
		<p class="loading-message">{message}</p>
	{/if}
</div>

<style>
	.loading-container {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
	}

	.loading-container.overlay {
		position: fixed;
		inset: 0;
		background: rgba(26, 29, 41, 0.7);
		backdrop-filter: blur(4px);
		z-index: 9998;
	}

	.size-sm .spinner {
		width: 24px;
		height: 24px;
		border-width: 2px;
	}

	.size-md .spinner {
		width: 40px;
		height: 40px;
		border-width: 3px;
	}

	.size-lg .spinner {
		width: 56px;
		height: 56px;
		border-width: 4px;
	}

	.spinner {
		border: 3px solid rgba(0, 114, 210, 0.2);
		border-top-color: var(--accent, #d97706);
		border-radius: 50%;
		animation: spin 1s linear infinite;
	}

	.loading-message {
		color: var(--text-secondary, #94a3b8);
		font-size: 14px;
		margin: 0;
		font-weight: 500;
	}

	@keyframes spin {
		from {
			transform: rotate(0deg);
		}
		to {
			transform: rotate(360deg);
		}
	}
</style>
