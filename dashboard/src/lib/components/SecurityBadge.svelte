<script lang="ts">
	export let riskLevel: 'safe' | 'warning' | 'critical' = 'safe';
	export let size: 'sm' | 'md' | 'lg' = 'md';
	export let showText: boolean = true;

	const sizeClasses = {
		sm: {
			badge: 'px-2 py-1 text-xs',
			icon: 'w-3 h-3'
		},
		md: {
			badge: 'px-3 py-1.5 text-sm',
			icon: 'w-4 h-4'
		},
		lg: {
			badge: 'px-4 py-2 text-base',
			icon: 'w-5 h-5'
		}
	};

	const riskConfig = {
		safe: {
			icon: '✅',
			label: 'Safe',
			bgColor: 'bg-emerald-900',
			borderColor: 'border-emerald-700',
			textColor: 'text-emerald-300',
			hoverBg: 'hover:bg-emerald-800',
			description: 'Source verified and safe'
		},
		warning: {
			icon: '⚠️',
			label: 'Warning',
			bgColor: 'bg-amber-900',
			borderColor: 'border-amber-700',
			textColor: 'text-amber-300',
			hoverBg: 'hover:bg-amber-800',
			description: 'Unverified or low-risk source'
		},
		critical: {
			icon: '🚫',
			label: 'Critical',
			bgColor: 'bg-red-900',
			borderColor: 'border-red-700',
			textColor: 'text-red-300',
			hoverBg: 'hover:bg-red-800',
			description: 'High-risk or malicious source'
		}
	};

	const config = riskConfig[riskLevel];
	const classes = sizeClasses[size];
</script>

<div
	class="group relative inline-flex items-center gap-2"
	role="status"
	aria-label={`Security status: ${config.label}`}
>
	<div
		class={`
			flex items-center gap-1.5 rounded-full border
			${classes.badge}
			${config.bgColor}
			${config.borderColor}
			${config.textColor}
			${config.hoverBg}
			transition-all duration-300 ease-in-out
			cursor-help
		`}
	>
		<span class={classes.icon}>{config.icon}</span>
		{#if showText}
			<span class="font-semibold whitespace-nowrap">{config.label}</span>
		{/if}
	</div>

	<!-- Tooltip -->
	<div
		class={`
			absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-2
			bg-slate-900 border border-slate-700 rounded-lg text-sm text-slate-200
			opacity-0 invisible group-hover:opacity-100 group-hover:visible
			transition-all duration-300 ease-in-out whitespace-nowrap z-50
			pointer-events-none after:absolute after:content-[''] after:top-full
			after:left-1/2 after:-translate-x-1/2 after:border-4
			after:border-transparent after:border-t-slate-900
		`}
	>
		{config.description}
	</div>
</div>

<style>
	:global(.dark) {
		/* Ensure dark mode colors are applied */
	}
</style>
