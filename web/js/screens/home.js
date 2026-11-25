// Home: a full-bleed hero from the first row, then "Continuer à regarder" (from synced resume points) and the
// catalogue rows of GET /api/catalog/home.

import { api } from '../api.js';
import { t, runtime } from '../i18n.js';
import * as store from '../store.js';
import {
  h, img, rail, posterCard, thumbCard, skeletonRail, skeletonLines, problem, empty, primaryButton, roundButton,
  metaLine, titleHref, setTitle,
} from '../ui.js';

/** profile id → { at, rows }: going back to Home shows the last rows at once (and keeps the scroll position). */
const cache = new Map();
const FRESH_MS = 5 * 60 * 1000;

export function playHref(kind, id, season, episode) {
  return season != null && episode != null ? `#/play/${kind}/${id}/${season}/${episode}` : `#/play/${kind}/${id}`;
}

function continueCard(r) {
  const leftMin = r.durationMs > 0 ? Math.max(1, Math.round((r.durationMs - r.positionMs) / 60000)) : 0;
  const parts = [];
  if (r.kind === 'tv' && r.season != null && r.episode != null) parts.push(t('episodeShort', { season: r.season, episode: r.episode }));
  if (leftMin) parts.push(t('left', { time: runtime(leftMin) }));
  return thumbCard({
    href: playHref(r.kind, r.tmdbId, r.season, r.episode),
    image: r.image,
    title: r.title || '',
    subtitle: parts.join(' · '),
    progress: r.durationMs > 0 ? r.positionMs / r.durationMs : null,
  });
}

function hero(item, go) {
  const playTarget = item.kind === 'movie' ? playHref('movie', item.id) : titleHref(item);
  return h('section.hero',
    h('div.hero-art', img(item.backdrop, '', 'hero-img', true)),
    h('div.hero-scrim'),
    h('div.hero-body',
      h('h1.hero-title', item.title),
      metaLine([item.year, item.kind === 'tv' ? t('series') : t('movie')], 'meta hero-meta'),
      h('div.actions',
        primaryButton(t('play'), () => go(playTarget)),
        roundButton('info', t('moreInfo'), () => go(titleHref(item))),
      ),
    ),
  );
}

function heroSkeleton() {
  return h('section.hero.hero-loading', { 'aria-hidden': 'true' },
    h('div.hero-art.skeleton'),
    h('div.hero-body', skeletonLines(2, 'hero-lines')));
}

export async function render({ root, signal, go }) {
  setTitle(t('home'));
  const profile = store.currentProfile();
  const cached = cache.get(profile.id);

  const draw = (rows) => {
    const catalogRows = (rows || []).filter((r) => r && r.id !== 'continue' && Array.isArray(r.items) && r.items.length);
    const first = catalogRows.flatMap((r) => r.items).find((i) => i.backdrop) || catalogRows[0]?.items[0];
    const resume = store.continueWatching();
    const parts = [];
    if (first) parts.push(hero(first, go));
    else parts.push(h('div.page-top'));
    const rails = h('div.rails');
    if (resume.length) rails.append(rail(t('continueWatching'), resume.map(continueCard), 'thumb'));
    for (const row of catalogRows) rails.append(rail(row.title, row.items.map((i) => posterCard(i))));
    if (!resume.length && !catalogRows.length) rails.append(empty(t('homeEmpty')));
    parts.push(rails);
    root.replaceChildren(...parts);
  };

  if (cached && Date.now() - cached.at < FRESH_MS) {
    draw(cached.rows);
    return;
  }

  root.replaceChildren(heroSkeleton(), h('div.rails', skeletonRail('thumb', 6), skeletonRail('poster'), skeletonRail('poster')));
  const load = async () => {
    try {
      const body = await api.home(profile.id, { signal });
      cache.set(profile.id, { at: Date.now(), rows: body.rows || [] });
      draw(body.rows || []);
    } catch (e) {
      if (e.name === 'AbortError') return;
      root.replaceChildren(h('div.page-top'), h('div.rails', problem(e, load)));
    }
  };
  await load();
}
