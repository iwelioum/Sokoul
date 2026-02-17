<script lang="ts">
	export let riskLevel: 'safe' | 'warning' | 'critical' = 'safe';
	export let flaggedSources: Array<{
		name: string;
		reason: string;
		severity?: 'low' | 'medium' | 'high';
	}> = [];
	export let onViewDetails: () => void = () => {};
	export let onBlock: () => void = () => {};
	export let onAllow: () => void = () => {};
	export let isDismissed: boolean = false;

	let localDismissed = isDismissed;

	const riskConfig = {
		safe: {
			icon: '✅',
			title: 'Source Verified',
			description: 'This source has been verified and is safe to use.',
			bgColor: 'bg-emerald-950',
			borderColor: 'border-emerald-800',
			textColor: 'text-emerald-200',
			labelColor: 'bg-emerald-900 text-emerald-300',
			bannerClass: 'banner-safe'
		},
		warning: {
			icon: '⚠️',
			title: 'Unverified Source',
			description: 'This source has not been verified. Proceed with caution.',
			bgColor: 'bg-amber-950',
			borderColor: 'border-amber-800',
			textColor: 'text-amber-200',
			labelColor: 'bg-amber-900 text-amber-300',
			bannerClass: 'banner-warning'
		},
		critical: {
			icon: '🚫',
			title: 'Dangerous Source Detected',
			description: 'This source has been flagged as potentially dangerous. Proceed at your own risk.',
			bgColor: 'bg-red-950',
			borderColor: 'border-red-800',
			textColor: 'text-red-200',
			labelColor: 'bg-red-900 text-red-300',
			bannerClass: 'banner-critical'
		}
	};

	const severityConfig = {
		low: { color: 'text-amber-400', icon: '⚠️' },
		medium: { color: 'text-orange-400', icon: '⚠️' },
		high: { color: 'text-red-400', icon: '🔴' }
	};

	const config = riskConfig[riskLevel];

	function handleDismiss() {
		localDismissed = true;
	}

	function handleUndo() {
		localDismissed = false;
	}
</script>

{#if !localDismissed && riskLevel !== 'safe'}
	<div
		class={`
			w-full rounded-lg border-l-4 p-4 mb-4
			${config.bgColor}
			${config.borderColor}
			${config.textColor}
			transition-all duration-300 ease-in-out
			shadow-lg
		`}
		role="alert"
	>
		<!-- Header -->
		<div class="flex items-start justify-between mb-3">
			<div class="flex items-start gap-3">
				<span class="text-2xl flex-shrink-0">{config.icon}</span>
				<div>
					<h3 class="text-lg font-bold">{config.title}</h3>
					<p class="text-sm opacity-90">{config.description}</p>
				</div>
			</div>
			<button
				on:click={handleDismiss}
				class="flex-shrink-0 text-xl hover:opacity-70 transition-opacity duration-200"
				aria-label="Dismiss warning"
				title="Dismiss this warning"
			>
				✕
			</button>
		</div>

		<!-- Flagged Sources List -->
		{#if flaggedSources.length > 0}
			<div class="mb-4 space-y-2">
				<p class="text-sm font-semibold opacity-75">Flagged Sources:</p>
				<div class="space-y-2 max-h-48 overflow-y-auto">
					{#each flaggedSources as source, idx (idx)}
						<div class="flex items-start gap-2 pl-8 py-1.5 bg-black/20 rounded-lg px-2.5">
							<span class="mt-0.5">
								{severityConfig[source.severity || 'low'].icon}
							</span>
							<div class="flex-1 min-w-0">
								<p class="font-semibold text-sm break-words">{source.name}</p>
								<p class="text-xs opacity-75 mt-0.5">{source.reason}</p>
							</div>
							{#if source.severity}
								<span class={`text-xs font-bold whitespace-nowrap ${severityConfig[source.severity].color}`}>
									{source.severity.toUpperCase()}
								</span>
							{/if}
						</div>
					{/each}
				</div>
			</div>
		{/if}

		<!-- Action Buttons -->
		<div class="flex flex-wrap items-center gap-2 pt-2 border-t border-white/10">
			<button
				on:click={onViewDetails}
				class={`
					px-3 py-1.5 rounded text-sm font-semibold
					transition-all duration-200
					hover:opacity-80 active:opacity-60
					${config.labelColor}
				`}
			>
				View Details
			</button>

			<button
				on:click={onBlock}
				class={`
					px-3 py-1.5 rounded text-sm font-semibold
					bg-red-700 text-red-100
					hover:bg-red-600 active:bg-red-800
					transition-all duration-200
				`}
			>
				Block Source
			</button>

			<button
				on:click={onAllow}
				class={`
					px-3 py-1.5 rounded text-sm font-semibold
					bg-slate-700 text-slate-100
					hover:bg-slate-600 active:bg-slate-800
					transition-all duration-200
				`}
			>
				Allow
			</button>

			<button
				on:click={handleDismiss}
				class={`
					ml-auto px-3 py-1.5 rounded text-sm font-semibold
					bg-slate-800 text-slate-300
					hover:bg-slate-700 active:bg-slate-900
					transition-all duration-200
				`}
			>
				Dismiss
			</button>
		</div>
	</div>
{:else if localDismissed && riskLevel !== 'safe'}
	<!-- Dismissed State - Show Undo -->
	<div
		class={`
			w-full rounded-lg p-2 mb-4
			bg-slate-900 border border-slate-700
			text-slate-300
			transition-all duration-300 ease-in-out
			flex items-center justify-between
		`}
	>
		<p class="text-sm">Security warning dismissed</p>
		<button
			on:click={handleUndo}
			class={`
				px-3 py-1 rounded text-sm font-semibold
				text-slate-200 hover:text-slate-100
				hover:bg-slate-700 active:bg-slate-800
				transition-all duration-200
			`}
		>
			Undo
		</button>
	</div>
{/if}

<style>
	.banner-safe {
		animation: slideInDown 0.3s ease-out;
	}

	.banner-warning {
		animation: slideInDown 0.3s ease-out;
	}

	.banner-critical {
		animation: slideInDown 0.3s ease-out;
	}

	@keyframes slideInDown {
		from {
			opacity: 0;
			transform: translateY(-1rem);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	:global(div::-webkit-scrollbar) {
		width: 4px;
	}

	:global(div::-webkit-scrollbar-track) {
		background: transparent;
	}

	:global(div::-webkit-scrollbar-thumb) {
		background: rgba(255, 255, 255, 0.1);
		border-radius: 2px;
	}

	:global(div::-webkit-scrollbar-thumb:hover) {
		background: rgba(255, 255, 255, 0.2);
	}
</style>
