<script lang="ts">
	import { onMount } from 'svelte';
	import FormInput from '$lib/components/FormInput.svelte';
	import Toast from '$lib/components/Toast.svelte';
	import Loading from '$lib/components/Loading.svelte';
	import { success, error as showError } from '$lib/stores/toastStore';

	interface UserProfile {
		id: string;
		username: string;
		email: string;
		first_name?: string;
		last_name?: string;
		created_at: string;
	}

	interface UserPreferences {
		theme: 'dark' | 'light';
		quality: 'auto' | '480p' | '720p' | '1080p';
		language: 'en' | 'fr' | 'es' | 'de';
		notifications: boolean;
		autoplay: boolean;
		subtitles: boolean;
		subtitle_language: 'en' | 'fr' | 'es' | 'de';
	}

	let profile: UserProfile | null = null;
	let preferences: UserPreferences = {
		theme: 'dark',
		quality: 'auto',
		language: 'en',
		notifications: true,
		autoplay: true,
		subtitles: false,
		subtitle_language: 'en'
	};

	let loading = true;
	let saving = false;
	let currentPassword = '';
	let newPassword = '';
	let confirmPassword = '';

	onMount(async () => {
		try {
			const response = await fetch('/api/user/profile', {
				headers: { Authorization: `Bearer ${localStorage.getItem('authToken')}` }
			});

			if (response.ok) {
				profile = await response.json();
			}
		} catch (err) {
			showError('Failed to load profile');
		} finally {
			loading = false;
		}
	});

	async function handleSavePreferences() {
		saving = true;
		try {
			const response = await fetch('/api/user/preferences', {
				method: 'PUT',
				headers: {
					Authorization: `Bearer ${localStorage.getItem('authToken')}`,
					'Content-Type': 'application/json'
				},
				body: JSON.stringify(preferences)
			});

			if (response.ok) {
				success('Preferences saved successfully');
			} else {
				showError('Failed to save preferences');
			}
		} catch (err) {
			showError(err instanceof Error ? err.message : 'Error saving preferences');
		} finally {
			saving = false;
		}
	}

	async function handleChangePassword() {
		if (newPassword !== confirmPassword) {
			showError('Passwords do not match');
			return;
		}

		if (newPassword.length < 8) {
			showError('Password must be at least 8 characters');
			return;
		}

		saving = true;
		try {
			const response = await fetch('/api/user/change-password', {
				method: 'POST',
				headers: {
					Authorization: `Bearer ${localStorage.getItem('authToken')}`,
					'Content-Type': 'application/json'
				},
				body: JSON.stringify({
					current_password: currentPassword,
					new_password: newPassword
				})
			});

			if (response.ok) {
				success('Password changed successfully');
				currentPassword = '';
				newPassword = '';
				confirmPassword = '';
			} else if (response.status === 401) {
				showError('Current password is incorrect');
			} else {
				showError('Failed to change password');
			}
		} catch (err) {
			showError(err instanceof Error ? err.message : 'Error changing password');
		} finally {
			saving = false;
		}
	}

	async function handleLogout() {
		localStorage.removeItem('authToken');
		window.location.href = '/login';
	}
</script>

<Toast />

<div class="settings-page">
	<header class="page-header">
		<h1>⚙️ Settings</h1>
		<p>Manage your account and preferences</p>
	</header>

	{#if loading}
		<Loading message="Loading settings..." overlay={false} />
	{:else}
		<div class="settings-grid">
			<!-- Profile Section -->
			<section class="settings-section">
				<h2>👤 Account Information</h2>

				{#if profile}
					<div class="info-grid">
						<div class="info-item">
							<label>Username</label>
							<p class="info-value">{profile.username}</p>
						</div>
						<div class="info-item">
							<label>Email</label>
							<p class="info-value">{profile.email}</p>
						</div>
						<div class="info-item">
							<label>Member Since</label>
							<p class="info-value">
								{new Date(profile.created_at).toLocaleDateString()}
							</p>
						</div>
					</div>
				{/if}
			</section>

			<!-- Display Preferences -->
			<section class="settings-section">
				<h2>🎨 Display Preferences</h2>

				<div class="settings-group">
					<label for="theme-select">Theme</label>
					<select id="theme-select" bind:value={preferences.theme} class="select-input">
						<option value="dark">Dark</option>
						<option value="light">Light</option>
					</select>
					<p class="hint">Choose your preferred color theme</p>
				</div>

				<div class="settings-group">
					<label for="language-select">Language</label>
					<select id="language-select" bind:value={preferences.language} class="select-input">
						<option value="en">English</option>
						<option value="fr">Français</option>
						<option value="es">Español</option>
						<option value="de">Deutsch</option>
					</select>
					<p class="hint">Select your preferred language</p>
				</div>
			</section>

			<!-- Playback Settings -->
			<section class="settings-section">
				<h2>📹 Playback Settings</h2>

				<div class="settings-group">
					<label for="quality-select">Video Quality</label>
					<select id="quality-select" bind:value={preferences.quality} class="select-input">
						<option value="auto">Auto (recommended)</option>
						<option value="480p">480p</option>
						<option value="720p">720p</option>
						<option value="1080p">1080p</option>
					</select>
					<p class="hint">Default quality for new streams</p>
				</div>

				<div class="checkbox-group">
					<input
						type="checkbox"
						id="autoplay"
						bind:checked={preferences.autoplay}
						class="checkbox-input"
					/>
					<label for="autoplay">Autoplay next episode</label>
				</div>

				<div class="checkbox-group">
					<input
						type="checkbox"
						id="subtitles"
						bind:checked={preferences.subtitles}
						class="checkbox-input"
					/>
					<label for="subtitles">Show subtitles by default</label>
				</div>

				{#if preferences.subtitles}
					<div class="settings-group indent">
						<label for="sub-lang-select">Subtitle Language</label>
						<select
							id="sub-lang-select"
							bind:value={preferences.subtitle_language}
							class="select-input"
						>
							<option value="en">English</option>
							<option value="fr">Français</option>
							<option value="es">Español</option>
							<option value="de">Deutsch</option>
						</select>
					</div>
				{/if}
			</section>

			<!-- Notifications -->
			<section class="settings-section">
				<h2>🔔 Notifications</h2>

				<div class="checkbox-group">
					<input
						type="checkbox"
						id="notifications"
						bind:checked={preferences.notifications}
						class="checkbox-input"
					/>
					<label for="notifications">Enable notifications</label>
					<p class="hint">Get notified about new episodes and downloads</p>
				</div>
			</section>

			<!-- Security -->
			<section class="settings-section password-section">
				<h2>🔒 Change Password</h2>

				<div class="password-form">
					<FormInput
						id="current-password"
						type="password"
						label="Current Password"
						bind:value={currentPassword}
						icon="🔐"
						required
					/>

					<FormInput
						id="new-password"
						type="password"
						label="New Password"
						bind:value={newPassword}
						icon="🔐"
						hint="At least 8 characters"
						required
					/>

					<FormInput
						id="confirm-password"
						type="password"
						label="Confirm Password"
						bind:value={confirmPassword}
						icon="🔐"
						required
					/>

					<button
						class="btn btn-primary"
						on:click={handleChangePassword}
						disabled={saving || !currentPassword || !newPassword || !confirmPassword}
					>
						{saving ? '⏳ Updating...' : '✓ Update Password'}
					</button>
				</div>
			</section>

			<!-- Save Settings -->
			<section class="settings-section save-section">
				<button class="btn btn-primary" on:click={handleSavePreferences} disabled={saving}>
					{saving ? '⏳ Saving...' : '💾 Save Preferences'}
				</button>
			</section>

			<!-- Logout -->
			<section class="settings-section logout-section">
				<button class="btn btn-danger" on:click={handleLogout}>
					🚪 Logout
				</button>
			</section>
		</div>
	{/if}
</div>

<style>
	.settings-page {
		max-width: 900px;
		margin: 0 auto;
		padding: 32px 16px;
	}

	.page-header {
		margin-bottom: 32px;
		border-bottom: 2px solid rgba(0, 114, 210, 0.2);
		padding-bottom: 16px;
	}

	.page-header h1 {
		margin: 0 0 8px 0;
		font-size: clamp(24px, 4vw, 36px);
		color: var(--text-primary, #f8fafc);
	}

	.page-header p {
		margin: 0;
		color: var(--text-secondary, #94a3b8);
	}

	.settings-grid {
		display: flex;
		flex-direction: column;
		gap: 24px;
	}

	.settings-section {
		background: var(--bg-surface, #1e293b);
		border: 1px solid var(--border-color, #334155);
		border-radius: 12px;
		padding: 24px;
	}

	.settings-section h2 {
		margin: 0 0 20px 0;
		font-size: 18px;
		color: var(--text-primary, #f8fafc);
		border-bottom: 1px solid var(--border-color, #334155);
		padding-bottom: 12px;
	}

	.info-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 16px;
	}

	.info-item {
		padding: 12px;
		background: rgba(0, 114, 210, 0.05);
		border-radius: 8px;
	}

	.info-item label {
		font-size: 12px;
		color: var(--text-secondary, #94a3b8);
		text-transform: uppercase;
		letter-spacing: 0.5px;
		display: block;
		margin-bottom: 8px;
	}

	.info-value {
		margin: 0;
		color: var(--text-primary, #f8fafc);
		font-weight: 500;
		word-break: break-all;
	}

	.settings-group {
		margin-bottom: 20px;
	}

	.settings-group.indent {
		margin-left: 24px;
		margin-top: 16px;
		padding-left: 16px;
		border-left: 2px solid rgba(0, 114, 210, 0.2);
	}

	.settings-group label {
		display: block;
		margin-bottom: 8px;
		font-size: 14px;
		font-weight: 600;
		color: var(--text-primary, #f8fafc);
	}

	.select-input {
		width: 100%;
		padding: 10px 12px;
		background: var(--bg-primary, #0f172a);
		border: 1px solid var(--border-color, #334155);
		border-radius: 8px;
		color: var(--text-primary, #f8fafc);
		font-size: 14px;
		cursor: pointer;
	}

	.select-input:focus {
		outline: none;
		border-color: var(--accent, #d97706);
		box-shadow: 0 0 8px rgba(0, 114, 210, 0.2);
	}

	.select-input option {
		background: var(--bg-surface, #1e293b);
		color: var(--text-primary, #f8fafc);
	}

	.checkbox-group {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-bottom: 16px;
	}

	.checkbox-input {
		width: 20px;
		height: 20px;
		cursor: pointer;
	}

	.checkbox-group label {
		margin: 0;
		font-size: 14px;
		color: var(--text-primary, #f8fafc);
		cursor: pointer;
	}

	.hint {
		margin: 8px 0 0 0;
		font-size: 12px;
		color: var(--text-secondary, #94a3b8);
	}

	.password-form {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}

	.btn {
		padding: 12px 24px;
		border: none;
		border-radius: 8px;
		cursor: pointer;
		font-weight: 600;
		font-size: 14px;
		transition: all 0.2s ease;
		text-transform: uppercase;
		letter-spacing: 0.5px;
	}

	.btn-primary {
		background: linear-gradient(135deg, var(--accent, #d97706), #f59e0b);
		color: #0f172a;
	}

	.btn-primary:hover:not(:disabled) {
		transform: translateY(-2px);
		box-shadow: 0 8px 16px rgba(0, 114, 210, 0.3);
	}

	.btn-primary:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}

	.btn-danger {
		background: rgba(239, 68, 68, 0.1);
		border: 1px solid rgba(239, 68, 68, 0.5);
		color: var(--danger, #ef4444);
	}

	.btn-danger:hover:not(:disabled) {
		background: rgba(239, 68, 68, 0.2);
		border-color: var(--danger, #ef4444);
	}

	.save-section,
	.logout-section {
		display: flex;
	}

	.save-section button,
	.logout-section button {
		width: 100%;
	}

	@media (max-width: 768px) {
		.settings-page {
			padding: 16px;
		}

		.settings-section {
			padding: 16px;
		}

		.info-grid {
			grid-template-columns: 1fr;
		}
	}
</style>
