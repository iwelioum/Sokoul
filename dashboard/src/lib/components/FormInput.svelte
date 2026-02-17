<script lang="ts">
	import { createEventDispatcher } from 'svelte';

	export let id: string = '';
	export let type: 'text' | 'email' | 'password' | 'number' | 'search' = 'text';
	export let label: string = '';
	export let placeholder: string = '';
	export let value: string | number = '';
	export let error: string = '';
	export let disabled: boolean = false;
	export let required: boolean = false;
	export let icon: string = '';
	export let hint: string = '';

	const dispatch = createEventDispatcher();

	const handleInput = (e: Event) => {
		const target = e.target as HTMLInputElement;
		value = type === 'number' ? parseFloat(target.value) : target.value;
		dispatch('change', value);
	};

	const handleFocus = () => dispatch('focus');
	const handleBlur = () => dispatch('blur');
</script>

<div class="form-group">
	{#if label}
		<label for={id}>
			{label}
			{#if required}
				<span class="required">*</span>
			{/if}
		</label>
	{/if}

	<div class="input-wrapper" class:has-icon={!!icon} class:has-error={!!error}>
		{#if icon}
			<span class="input-icon">{icon}</span>
		{/if}
		<input
			{id}
			{type}
			{placeholder}
			{value}
			{disabled}
			on:input={handleInput}
			on:focus={handleFocus}
			on:blur={handleBlur}
			class:error={!!error}
		/>
	</div>

	{#if error}
		<p class="error-text">❌ {error}</p>
	{:else if hint}
		<p class="hint-text">ℹ️ {hint}</p>
	{/if}
</div>

<style>
	.form-group {
		display: flex;
		flex-direction: column;
		gap: 8px;
		margin-bottom: 16px;
	}

	label {
		font-size: 14px;
		font-weight: 600;
		color: var(--text-primary, #f8fafc);
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.required {
		color: var(--danger, #ef4444);
	}

	.input-wrapper {
		position: relative;
		display: flex;
		align-items: center;
	}

	.input-wrapper.has-icon {
		position: relative;
	}

	.input-icon {
		position: absolute;
		left: 12px;
		font-size: 18px;
		pointer-events: none;
		color: var(--text-secondary, #94a3b8);
	}

	input {
		width: 100%;
		padding: 12px 16px;
		padding-left: 40px;
		background: var(--bg-surface, #1e293b);
		border: 2px solid var(--border-color, #334155);
		border-radius: 8px;
		color: var(--text-primary, #f8fafc);
		font-size: 14px;
		transition: all 0.3s ease;
		font-family: inherit;
	}

	input:not(.has-icon) {
		padding-left: 16px;
	}

	input:focus {
		outline: none;
		border-color: var(--accent, #d97706);
		box-shadow: 0 0 12px rgba(0, 114, 210, 0.2);
		background: var(--bg-surface-light, #475569);
	}

	input:disabled {
		opacity: 0.6;
		cursor: not-allowed;
		background: var(--bg-disabled, #0f172a);
	}

	input.error {
		border-color: var(--danger, #ef4444);
	}

	input.error:focus {
		box-shadow: 0 0 12px rgba(239, 68, 68, 0.2);
	}

	input::placeholder {
		color: var(--text-tertiary, #64748b);
	}

	.error-text {
		font-size: 12px;
		color: var(--danger, #ef4444);
		margin: 0;
	}

	.hint-text {
		font-size: 12px;
		color: var(--text-secondary, #94a3b8);
		margin: 0;
	}
</style>
