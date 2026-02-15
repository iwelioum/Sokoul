<script lang="ts">
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';
	import { getMedia } from '$lib/api/client';
	import { onMount } from 'svelte';

	onMount(async () => {
		const id = $page.params.id;
		try {
			const media = await getMedia(id);
			const tmdbId = media.tmdb_id;
			const type = media.media_type === 'tv' ? 'tv' : 'movie';
			if (tmdbId) {
				goto(`/${type}/${tmdbId}`, { replaceState: true });
			} else {
				goto('/', { replaceState: true });
			}
		} catch {
			goto('/', { replaceState: true });
		}
	});
</script>

<div style="padding:60px 24px; text-align:center; color:var(--text-secondary);">
	Redirection en cours…
</div>
