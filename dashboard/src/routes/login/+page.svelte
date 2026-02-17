<script lang="ts">
	import { goto } from '$app/navigation';
	import { login, register } from '$lib/api/client';

	let mode = $state<'login' | 'register'>('login');
	let email = $state('');
	let username = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);

	async function handleSubmit() {
		error = '';
		loading = true;
		try {
			if (mode === 'login') {
				await login(email, password);
			} else {
				await register(username, email, password);
			}
			goto('/');
		} catch (e: any) {
			const msg = e.message || 'Erreur inconnue';
			try {
				const parsed = JSON.parse(msg.replace(/^API \d+: /, ''));
				error = parsed.error || msg;
			} catch {
				error = msg;
			}
		} finally {
			loading = false;
		}
	}
</script>

<svelte:head>
	<title>{mode === 'login' ? 'Connexion' : 'Inscription'} — Sokoul</title>
</svelte:head>

<div class="auth-page">
	<div class="auth-card">
		<img src="/logo.png" alt="Sokoul" class="auth-logo" />
		<h1>{mode === 'login' ? 'Connexion' : 'Créer un compte'}</h1>

		{#if error}
			<div class="error-msg">{error}</div>
		{/if}

		<form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }}>
			{#if mode === 'register'}
				<label>
					<span>Nom d'utilisateur</span>
					<input type="text" bind:value={username} placeholder="sokoul_user" required minlength="3" maxlength="32" />
				</label>
			{/if}

			<label>
				<span>Email</span>
				<input type="email" bind:value={email} placeholder="email@exemple.com" required />
			</label>

			<label>
				<span>Mot de passe</span>
				<input type="password" bind:value={password} placeholder="••••••••" required minlength="8" />
			</label>

			<button type="submit" class="btn-primary" disabled={loading}>
				{#if loading}
					Chargement...
				{:else}
					{mode === 'login' ? 'Se connecter' : "S'inscrire"}
				{/if}
			</button>
		</form>

		<p class="switch-mode">
			{#if mode === 'login'}
				Pas encore de compte ?
				<button type="button" class="link-btn" onclick={() => { mode = 'register'; error = ''; }}>Créer un compte</button>
			{:else}
				Déjà un compte ?
				<button type="button" class="link-btn" onclick={() => { mode = 'login'; error = ''; }}>Se connecter</button>
			{/if}
		</p>
	</div>
</div>

<style>
	.auth-page {
		min-height: 100vh;
		display: flex;
		align-items: center;
		justify-content: center;
		background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
		padding: 1rem;
	}

	.auth-card {
		background: rgba(37, 40, 51, 0.8);
		backdrop-filter: blur(16px);
		border: 1px solid rgba(148, 163, 184, 0.15);
		border-radius: 1rem;
		padding: 2.5rem;
		width: 100%;
		max-width: 420px;
		text-align: center;
	}

	.auth-logo {
		width: 64px;
		height: 64px;
		margin-bottom: 1rem;
		border-radius: 12px;
	}

	h1 {
		color: #f1f5f9;
		font-size: 1.5rem;
		margin-bottom: 1.5rem;
	}

	.error-msg {
		background: rgba(239, 68, 68, 0.15);
		border: 1px solid rgba(239, 68, 68, 0.3);
		color: #fca5a5;
		padding: 0.75rem;
		border-radius: 0.5rem;
		margin-bottom: 1rem;
		font-size: 0.875rem;
	}

	form {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}

	label {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		text-align: left;
	}

	label span {
		color: #94a3b8;
		font-size: 0.85rem;
		font-weight: 500;
	}

	input {
		background: rgba(26, 29, 41, 0.6);
		border: 1px solid rgba(148, 163, 184, 0.2);
		border-radius: 0.5rem;
		padding: 0.75rem 1rem;
		color: #f1f5f9;
		font-size: 1rem;
		outline: none;
		transition: border-color 0.2s;
	}

	input:focus {
		border-color: #3b82f6;
	}

	input::placeholder {
		color: #475569;
	}

	.btn-primary {
		background: linear-gradient(135deg, #3b82f6, #2563eb);
		color: white;
		border: none;
		border-radius: 0.5rem;
		padding: 0.85rem;
		font-size: 1rem;
		font-weight: 600;
		cursor: pointer;
		transition: opacity 0.2s;
		margin-top: 0.5rem;
	}

	.btn-primary:hover:not(:disabled) {
		opacity: 0.9;
	}

	.btn-primary:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.switch-mode {
		color: #94a3b8;
		font-size: 0.875rem;
		margin-top: 1.5rem;
	}

	.link-btn {
		background: none;
		border: none;
		color: #3b82f6;
		cursor: pointer;
		font-size: 0.875rem;
		text-decoration: underline;
		padding: 0;
	}

	.link-btn:hover {
		color: #60a5fa;
	}
</style>
