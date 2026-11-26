// The apps' rows and cards (ui/Cards.kt, ui/SmartPoster.kt, home/HomeScreen.kt): a heading that lights up while the row
// is in use, cards with no text under them, the title's logo over a textless poster, the Top 10 numerals, "See All".

import { api } from './api.js';
import { t, runtime } from './i18n.js';
import { h, img, icon, roundButton, titleHref } from './ui.js';

/* ---------------------------------------------------------------- Artwork, asked once per title, a few at a time */

const artworkCache = new Map();
const waiting = [];
let running = 0;
const PARALLEL = 6;

function pump() {
  while (running < PARALLEL && waiting.length) {
    const job = waiting.shift();
    running++;
    job().finally(() => { running--; pump(); });
  }
}

/** The textless poster, the backdrop, the logo and the first genre of a film or series; null when none is known. */
export function artwork(kind, id) {
  if (kind !== 'movie' && kind !== 'tv') return Promise.resolve(null);
  const key = `${kind}:${id}`;
  if (!artworkCache.has(key)) {
    artworkCache.set(key, new Promise((resolve) => {
      waiting.push(() => api.artwork(kind, id).then(resolve, () => {
        artworkCache.delete(key);
        resolve(null);
      }));
      pump();
    }));
  }
  return artworkCache.get(key);
}

// Cards ask for their artwork when they come near the screen, not all at once.
const nearScreen = typeof IntersectionObserver === 'function' ? new IntersectionObserver((entries) => {
  for (const entry of entries) {
    if (!entry.isIntersecting) continue;
    nearScreen.unobserve(entry.target);
    entry.target.__load?.();
  }
}, { rootMargin: '300px 600px' }) : null;

function whenNear(el, load) {
  if (!nearScreen) { load(); return; }
  el.__load = load;
  nearScreen.observe(el);
}

/** A picture that fades in once loaded and leaves the grey plaque when it fails. */
function picture(src, cls = '') {
  return img(src, '', cls) || null;
}

/** The title in bold over a picture that has no logo (SmartPoster.PosterTitle). */
function cardName(text) {
  return h('span.card-name', h('span.card-name-text', { lang: document.documentElement.lang }, text));
}

/** A logo over the art; the title in text if it fails. */
function logoOver(src, title, cls) {
  const box = h(`span.${cls}`);
  const logo = img(src, title, '', false);
  if (logo) {
    logo.addEventListener('error', () => box.replaceWith(cardName(title)), { once: true });
    box.append(logo);
  }
  return box;
}

export const kindLabel = (kind) => t(kind === 'tv' ? 'typeSeries' : 'movie');

/* ---------------------------------------------------------------- Cards */

/**
 * PosterCard: 2:3, corner 20, no text under it. The textless poster with the logo on it when TMDB has both, otherwise the
 * poster as it is. A caption ("Film · Genre") shows while it is pointed at; a Top 10 card always shows its genre and
 * carries its rank in large numerals.
 */
export function posterTile(item, { rank = null, href = titleHref(item), size = '' } = {}) {
  const art = h('div.card-art.art-poster', picture(item.poster));
  const caption = h('span.card-caption');
  const card = h(`a.tile.tile-poster${size ? '.tile-' + size : ''}${rank ? '.tile-ranked' : ''}`,
    { href, 'aria-label': item.year ? `${item.title}, ${item.year}` : item.title }, art, caption,
    rank ? h('span.tile-rank', { 'aria-hidden': 'true' }, String(rank)) : null);
  if (!item.poster) art.append(cardName(item.title));
  whenNear(card, () => artwork(item.kind, item.id).then((a) => {
    if (!a) return;
    caption.textContent = rank ? (a.genre || '') : [kindLabel(item.kind), a.genre].filter(Boolean).join(' · ');
    if (a.cleanPoster && a.logo) {
      const clean = picture(a.cleanPoster);
      if (!clean) return;
      clean.addEventListener('load', () => {
        clean.classList.remove('is-preloading');
        art.replaceChildren(clean, h('span.card-fade'), logoOver(a.logo, item.title, 'card-logo'));
        card.classList.add('has-logo');
      }, { once: true });
      // Loaded off-screen first: the original poster stays until the clean one is there.
      clean.classList.add('is-preloading');
      art.append(clean);
    }
  }));
  return card;
}

/**
 * SmartLandscape: 16:9 (or square), the backdrop under a light veil, the title's logo in the middle, or the title in bold
 * when it has none.
 */
export function landscapeTile(item, { square = false, href = titleHref(item), size = '' } = {}) {
  const art = h(`div.card-art.${square ? 'art-square' : 'art-landscape'}`, picture(square ? (item.backdrop || item.poster) : (item.backdrop || item.poster)),
    h('span.card-veil'));
  const card = h(`a.tile.${square ? 'tile-square' : 'tile-landscape'}${size ? '.tile-' + size : ''}`,
    { href, 'aria-label': item.title }, art);
  let named = false;
  const name = () => { if (!named) { named = true; art.append(cardName(item.title)); } };
  whenNear(card, () => artwork(item.kind, item.id).then((a) => {
    if (a?.logo) art.append(logoOver(a.logo, item.title, 'card-logo card-logo-wide'));
    else name();
  }));
  if (item.kind !== 'movie' && item.kind !== 'tv') name();
  return card;
}

/** The last card of a long row: the same size as its neighbours, it opens the whole list. */
export function seeAllTile(style, href, title) {
  const shape = { Landscape: 'landscape', Square: 'square' }[style] || 'poster';
  return h(`a.tile.tile-${shape}.tile-see-all`, { href, 'aria-label': t('seeAllOf', { title }) },
    h(`div.card-art.art-${shape}.see-all`, icon('chevron'), h('span', t('seeAll'))));
}

/** LandscapeCard of "Continue watching": the still, a fade, the title, then play, a short bar and what is left. */
export function continueTile({ href, image, title, meta, progress }) {
  const bar = progress != null && progress > 0
    ? h('span.continue-bar', h('span.continue-fill', { vars: { '--p': String(Math.min(1, Math.max(0.05, progress))) } })) : null;
  return h('a.tile.tile-continue', { href, 'aria-label': [title, meta].filter(Boolean).join(', ') },
    h('div.card-art.art-continue', picture(image), h('span.card-bottom-fade'),
      h('span.continue-text', title ? h('span.continue-title', title) : null,
        h('span.continue-line', icon('play', 'continue-play'), bar, meta ? h('span.continue-meta', meta) : null))));
}

/* ---------------------------------------------------------------- Rows */

/**
 * CardRow: one heading (a word or a short phrase, nothing above or under it) that turns white while the row is in use,
 * over a row that scrolls sideways and bleeds past the right margin. With a mouse, arrows show on hover.
 */
export function cardRow(title, cards, { kind = 'poster', id = null } = {}) {
  const track = h(`div.row-track.row-${kind}`, { role: 'list' }, cards.map((c) => {
    c.setAttribute('role', 'listitem');
    return c;
  }));
  const scroll = (dir) => track.scrollBy({ left: dir * track.clientWidth * 0.8, behavior: 'smooth' });
  const prev = roundButton('prev', t('previous'), () => scroll(-1), 'row-arrow row-prev');
  const next = roundButton('next', t('next'), () => scroll(1), 'row-arrow row-next');
  prev.tabIndex = -1;
  next.tabIndex = -1;
  const arrows = () => {
    prev.classList.toggle('is-hidden', track.scrollLeft < 8);
    next.classList.toggle('is-hidden', track.scrollLeft + track.clientWidth >= track.scrollWidth - 8);
  };
  track.addEventListener('scroll', arrows, { passive: true });
  requestAnimationFrame(arrows);
  return h('section.card-row', { 'data-row': id },
    title ? h('h2.row-heading', title) : null,
    h('div.row-body', track, prev, next));
}

/** The loading shape of a row: grey cards with a shimmer, no heading text. */
export function skeletonRow(kind = 'poster', count = 7) {
  const shape = { landscape: 'landscape', square: 'square', continue: 'continue', episode: 'episode' }[kind] || 'poster';
  return h('section.card-row.is-loading', { 'aria-hidden': 'true' },
    h('div.skeleton.row-heading-skeleton'),
    h('div.row-body', h(`div.row-track.row-${shape}`,
      Array.from({ length: count }, () => h(`div.tile.tile-${shape}`, h(`div.card-art.art-${shape}.skeleton`))))));
}

/** "2 h 45 min left" or "S1 · É3 · 20 min left": what a continue card says under its title. */
export function leftLabel(point) {
  const left = point.durationMs > 0 ? Math.max(1, Math.round((point.durationMs - point.positionMs) / 60000)) : 0;
  const parts = [];
  if (point.season != null && point.episode != null) parts.push(t('episodeShort', { season: point.season, episode: point.episode }));
  if (point.finished) parts.push(t('watched'));
  else if (left) parts.push(runtime(left));
  return parts.join(' · ');
}
