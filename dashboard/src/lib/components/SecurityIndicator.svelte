<script lang="ts">
	export let riskLevel: 'safe' | 'warning' | 'critical' = 'safe';

	const riskConfig = {
		safe: {
			icon: '✅',
			bgColor: 'bg-emerald-600',
			borderColor: 'border-emerald-400',
			shadowColor: 'shadow-emerald-500/0',
			pulse: false
		},
		warning: {
			icon: '⚠️',
			bgColor: 'bg-amber-600',
			borderColor: 'border-amber-400',
			shadowColor: 'shadow-amber-500/0',
			pulse: false
		},
		critical: {
			icon: '🚫',
			bgColor: 'bg-red-600',
			borderColor: 'border-red-400',
			shadowColor: 'shadow-red-500/50',
			pulse: true
		}
	};

	const config = riskConfig[riskLevel];
</script>

<div
	class={`
		absolute -top-2 -right-2 flex items-center justify-center
		w-8 h-8 rounded-full border-2
		${config.bgColor}
		${config.borderColor}
		shadow-lg ${config.shadowColor}
		animate-fade-in
		${config.pulse ? 'animate-pulse' : ''}
		z-30
	`}
	role="img"
	aria-label={`Security risk: ${riskLevel}`}
	title={`Risk Level: ${riskLevel}`}
>
	<span class="text-base">{config.icon}</span>
</div>

<style>
	@keyframes fadeIn {
		from {
			opacity: 0;
			transform: scale(0.8);
		}
		to {
			opacity: 1;
			transform: scale(1);
		}
	}

	:global(.animate-fade-in) {
		animation: fadeIn 0.3s ease-in-out;
	}

	:global(.animate-pulse) {
		animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
	}

	@keyframes pulse {
		0%,
		100% {
			opacity: 1;
		}
		50% {
			opacity: 0.7;
		}
	}
</style>
