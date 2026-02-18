/**
 * sources.ts — Embed iframe sources (frontend-only, no backend call needed)
 *
 * Ce fichier génère les URLs embed directement côté client.
 * Si le lecteur natif (HLS/MP4) ne parvient pas à charger un flux,
 * ces sources sont toujours disponibles comme fallback immédiat.
 */

export interface EmbedSource {
	name: string;
	url: string;
	quality: string;
	category: 'International' | 'French-VF' | 'French-VOSTFR';
	language?: string;
}

/**
 * Génère toutes les sources embed pour un média donné.
 * Aucun appel réseau — résultat instantané.
 */
export function buildEmbedSources(
	mediaType: string,
	tmdbId: number,
	season?: number,
	episode?: number
): EmbedSource[] {
	const s = season ?? 1;
	const e = episode ?? 1;
	const isTV = mediaType === 'tv';
	const sources: EmbedSource[] = [];

	// ── International ─────────────────────────────────────────────────────

	sources.push({
		name: 'VidSrc',
		url: isTV
			? `https://vidsrc.cc/v2/embed/tv/${tmdbId}/${s}/${e}`
			: `https://vidsrc.cc/v2/embed/movie/${tmdbId}`,
		quality: 'Multi',
		category: 'International',
	});

	sources.push({
		name: 'Embed.su',
		url: isTV
			? `https://embed.su/embed/tv/${tmdbId}/${s}/${e}`
			: `https://embed.su/embed/movie/${tmdbId}`,
		quality: 'HD',
		category: 'International',
	});

	sources.push({
		name: 'SuperEmbed',
		url: isTV
			? `https://multiembed.mov/?video_id=${tmdbId}&tmdb=1&s=${s}&e=${e}`
			: `https://multiembed.mov/?video_id=${tmdbId}&tmdb=1`,
		quality: 'Multi',
		category: 'International',
	});

	sources.push({
		name: '2Embed',
		url: isTV
			? `https://www.2embed.cc/embedtv/${tmdbId}&&s=${s}&e=${e}`
			: `https://www.2embed.cc/embed/${tmdbId}`,
		quality: 'HD',
		category: 'International',
	});

	sources.push({
		name: 'VidSrc.xyz',
		url: isTV
			? `https://vidsrc.xyz/embed/tv?tmdb=${tmdbId}&season=${s}&episode=${e}`
			: `https://vidsrc.xyz/embed/movie?tmdb=${tmdbId}`,
		quality: 'Multi',
		category: 'International',
	});

	sources.push({
		name: 'AutoEmbed',
		url: isTV
			? `https://player.autoembed.cc/embed/tv/${tmdbId}/${s}/${e}`
			: `https://player.autoembed.cc/embed/movie/${tmdbId}`,
		quality: 'Multi',
		category: 'International',
	});

	sources.push({
		name: 'VidLink',
		url: isTV
			? `https://vidlink.pro/tv/${tmdbId}/${s}/${e}`
			: `https://vidlink.pro/movie/${tmdbId}`,
		quality: 'HD',
		category: 'International',
	});

	sources.push({
		name: 'MoviesAPI',
		url: isTV
			? `https://moviesapi.club/tv/${tmdbId}-${s}-${e}`
			: `https://moviesapi.club/movie/${tmdbId}`,
		quality: 'HD',
		category: 'International',
	});

	sources.push({
		name: 'VidBinge',
		url: isTV
			? `https://vidbinge.dev/embed/tv/${tmdbId}/${s}/${e}`
			: `https://vidbinge.dev/embed/movie/${tmdbId}`,
		quality: 'Multi',
		category: 'International',
	});

	sources.push({
		name: 'Smashy',
		url: isTV
			? `https://player.smashy.stream/tv/${tmdbId}/${s}/${e}`
			: `https://player.smashy.stream/movie/${tmdbId}`,
		quality: 'Multi',
		category: 'International',
	});

	// ── French VF ─────────────────────────────────────────────────────────

	sources.push({
		name: 'VidSrc.pro',
		url: isTV
			? `https://vidsrc.pro/embed/tv/${tmdbId}/${s}/${e}`
			: `https://vidsrc.pro/embed/movie/${tmdbId}`,
		quality: 'HD',
		category: 'International',
	});

	sources.push({
		name: 'Vidzy VF',
		url: isTV
			? `https://vidzy.to/embed/tv/${tmdbId}/${s}/${e}?lang=vf`
			: `https://vidzy.to/embed/movie/${tmdbId}?lang=vf`,
		quality: 'HD',
		category: 'French-VF',
		language: 'VF',
	});

	sources.push({
		name: 'Voe VF',
		url: isTV
			? `https://voe.sx/embed/tv/${tmdbId}/${s}/${e}?lang=vf`
			: `https://voe.sx/embed/movie/${tmdbId}?lang=vf`,
		quality: 'HD',
		category: 'French-VF',
		language: 'VF',
	});

	sources.push({
		name: 'Netu VF',
		url: isTV
			? `https://netu.ac/embed/tv/${tmdbId}/${s}/${e}`
			: `https://netu.ac/embed/movie/${tmdbId}`,
		quality: 'HD',
		category: 'French-VF',
		language: 'VF',
	});

	sources.push({
		name: 'DoodStream VF',
		url: isTV
			? `https://doodstream.com/e/tv/${tmdbId}/${s}/${e}`
			: `https://doodstream.com/e/movie/${tmdbId}`,
		quality: 'HD',
		category: 'French-VF',
		language: 'VF',
	});

	// ── French VOSTFR ──────────────────────────────────────────────────────

	sources.push({
		name: 'Vidzy VOSTFR',
		url: isTV
			? `https://vidzy.to/embed/tv/${tmdbId}/${s}/${e}?lang=vostfr`
			: `https://vidzy.to/embed/movie/${tmdbId}?lang=vostfr`,
		quality: 'HD',
		category: 'French-VOSTFR',
		language: 'VOSTFR',
	});

	sources.push({
		name: 'Voe VOSTFR',
		url: isTV
			? `https://voe.sx/embed/tv/${tmdbId}/${s}/${e}?lang=vostfr`
			: `https://voe.sx/embed/movie/${tmdbId}?lang=vostfr`,
		quality: 'HD',
		category: 'French-VOSTFR',
		language: 'VOSTFR',
	});

	sources.push({
		name: 'Netu VOSTFR',
		url: isTV
			? `https://netu.ac/embed/tv/${tmdbId}/${s}/${e}?lang=vostfr`
			: `https://netu.ac/embed/movie/${tmdbId}?lang=vostfr`,
		quality: 'HD',
		category: 'French-VOSTFR',
		language: 'VOSTFR',
	});

	sources.push({
		name: 'DoodStream VOSTFR',
		url: isTV
			? `https://doodstream.com/e/tv/${tmdbId}/${s}/${e}?lang=vostfr`
			: `https://doodstream.com/e/movie/${tmdbId}?lang=vostfr`,
		quality: 'HD',
		category: 'French-VOSTFR',
		language: 'VOSTFR',
	});

	return sources;
}

/**
 * Regroupe les sources par catégorie pour l'affichage dans le lecteur.
 */
export function groupSources(sources: EmbedSource[]) {
	return {
		international: sources.filter(s => s.category === 'International'),
		vf: sources.filter(s => s.category === 'French-VF'),
		vostfr: sources.filter(s => s.category === 'French-VOSTFR'),
	};
}
