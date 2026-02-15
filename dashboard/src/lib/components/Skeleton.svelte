<script lang="ts">
	let {
		width = '100%',
		height = '20px',
		borderRadius = '8px',
		variant = 'default' as 'default' | 'card' | 'circle' | 'text'
	} = $props();

	const style = $derived.by(() => {
		if (variant === 'card') return 'width:100%;aspect-ratio:2/3;border-radius:12px;';
		if (variant === 'circle') return `width:${width};height:${width};border-radius:50%;`;
		if (variant === 'text') return `width:${width};height:${height};border-radius:4px;`;
		return `width:${width};height:${height};border-radius:${borderRadius};`;
	});
</script>

<div class="skeleton" style={style}></div>

<style>
	.skeleton {
		background: linear-gradient(
			90deg,
			#2D3F56 25%,
			#3D5070 50%,
			#2D3F56 75%
		);
		background-size: 200% 100%;
		animation: shimmer 1.5s infinite;
		flex-shrink: 0;
		box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.5);
	}

	@keyframes shimmer {
		0% { background-position: 200% 0; }
		100% { background-position: -200% 0; }
	}
</style>
