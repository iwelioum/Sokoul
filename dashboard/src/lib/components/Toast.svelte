<script lang="ts">
	import { toasts, removeToast } from '$lib/stores/toastStore';

	const getIcon = (type: string) => {
		switch (type) {
			case 'success':
				return '✅';
			case 'error':
				return '❌';
			case 'warning':
				return '⚠️';
			case 'info':
				return 'ℹ️';
			default:
				return '📌';
		}
	};

	const getColor = (type: string) => {
		switch (type) {
			case 'success':
				return '#10b981';
			case 'error':
				return '#ef4444';
			case 'warning':
				return '#f59e0b';
			case 'info':
				return '#3b82f6';
			default:
				return '#64748b';
		}
	};
</script>

<div class="toasts-container">
	{#each $toasts as toast (toast.id)}
		<div class="toast" style="--toast-color: {getColor(toast.type)}" on:animationend={() => removeToast(toast.id)}>
			<span class="toast-icon">{getIcon(toast.type)}</span>
			<span class="toast-message">{toast.message}</span>
			<button
				class="toast-close"
				on:click={() => removeToast(toast.id)}
				title="Dismiss"
				aria-label="Close notification"
			>
				✕
			</button>
		</div>
	{/each}
</div>

<style>
	.toasts-container {
		position: fixed;
		top: 20px;
		right: 20px;
		z-index: 9999;
		display: flex;
		flex-direction: column;
		gap: 12px;
		pointer-events: none;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 16px;
		background: var(--bg-surface, #1e293b);
		border-left: 4px solid var(--toast-color);
		border-radius: 8px;
		box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
		color: var(--text-primary, #f8fafc);
		font-size: 14px;
		font-weight: 500;
		animation: slideIn 0.3s ease, slideOut 0.3s ease 2.7s forwards;
		pointer-events: all;
	}

	.toast-icon {
		font-size: 18px;
		min-width: 24px;
		text-align: center;
	}

	.toast-message {
		flex: 1;
		line-height: 1.4;
	}

	.toast-close {
		background: none;
		border: none;
		color: var(--text-secondary, #94a3b8);
		cursor: pointer;
		font-size: 16px;
		padding: 4px;
		display: flex;
		align-items: center;
		justify-content: center;
		transition: color 0.2s ease;
	}

	.toast-close:hover {
		color: var(--text-primary, #f8fafc);
	}

	@keyframes slideIn {
		from {
			transform: translateX(400px);
			opacity: 0;
		}
		to {
			transform: translateX(0);
			opacity: 1;
		}
	}

	@keyframes slideOut {
		from {
			transform: translateX(0);
			opacity: 1;
		}
		to {
			transform: translateX(400px);
			opacity: 0;
		}
	}

	@media (max-width: 768px) {
		.toasts-container {
			top: 10px;
			right: 10px;
			left: 10px;
		}

		.toast {
			margin: 0;
		}
	}
</style>
