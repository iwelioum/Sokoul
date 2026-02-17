import adapter from '@sveltejs/adapter-static';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: adapter({
			fallback: 'index.html'   // SPA fallback — all routes resolve to index.html
		})
	}
};

export default config;
