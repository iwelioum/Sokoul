// SPA mode — disable SSR, all rendering happens client-side.
// This ensures SvelteKit's client-side router handles all navigation natively.
export const ssr = false;

import { browser } from '$app/environment';
import { goto } from '$app/navigation';
import { getAuthToken } from '$lib/api/client';

// Protected routes - authentication required
export const load = async ({ url }) => {
	if (browser) {
		const isLoginPage = url.pathname === '/login';
		const token = getAuthToken();

		// Redirect to login if not authenticated (except for login page itself)
		if (!token && !isLoginPage) {
			goto('/login');
			return;
		}

		// Redirect to home if already authenticated and trying to access login
		if (token && isLoginPage) {
			goto('/');
			return;
		}
	}
};
