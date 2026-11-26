// A browsing page (Home, Films, Séries, Animés) as the apps draw it (home/HomeScreen.kt): a hero the height of the
// screen, then every row of the page's plan from the server, in its order. Each row has one heading.

import { api } from '../api.js';
import { t, runtime } from '../i18n.js';
import * as store from '../store.js';
import { orderRows } from '../preferences.js';
import { h, img, problem, empty, titleHref, setTitle, icon } from '../ui.js';
import {
  cardRow, skeletonRow, posterTile, landscapeTile, seeAllTile, continueTile, leftLabel,
} from '../cards.js';
import { heroInfo, playCard, labeledRound, dots, cinemaLabels } from '../hero.js';

const carouselPositions = new Map();
/** Titles a row shows before "See All" (HomeScreen.ROW_CAP), and a Top 10's ten. */
const ROW_CAP = 10;
const SLIDES = 8;
const SLIDE_MS = 12_000;

export function playHref(kind, id, season, episode) {
  return season != null && episode != null ? '#/play/' + [kind, id, season, episode].join('/') : '#/play/' + [kind, id].join('/');
}

const tmdbImage = (path, size) => (path?.startsWith('/') ? `https://image.tmdb.org/t/p/${size}${path}` : path);

/** A "Continue watching" card (LandscapeCard 320 × 230). */
export function continueCard(point) {
  return continueTile({
    href: playHref(point.kind, point.tmdbId, point.season, point.episode),
    image: tmdbImage(point.image, 'w780'),
    title: point.title || '',
    meta: leftLabel(point),
    progress: point.durationMs > 0 ? Math.min(1, point.positionMs / point.durationMs) : null,
  });
}

/* ---------------------------------------------------------------- Hero */

function hero(items, key, go, signal) {
  let active = Math.min(items.length - 1, carouselPositions.get(key) || 0);
  let generation = 0;
  let timer = 0;
  const art = h('div.hero-art');
  const body = h('div.hero-body');
  const dotsSlot = h('div.hero-dots-slot');
  const section = h('section.hero.hero-home', { 'aria-roledescription': 'carousel', 'aria-label': t('featured') },
    art, h('div.hero-scrim'), body, dotsSlot);

  const schedule = () => {
    clearTimeout(timer);
    if (items.length < 2 || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    timer = setTimeout(() => {
      // Only while it is on screen and nothing in it is being used, as the app advances only the hero in view.
      if (signal.aborted) return;
      if (document.hidden || window.scrollY > 40 || section.matches(':hover') || section.contains(document.activeElement)) schedule();
      else change(active + 1);
    }, SLIDE_MS);
  };
  signal.addEventListener('abort', () => clearTimeout(timer), { once: true });

  const buttons = (item, data) => {
    const latest = item.kind === 'movie' || item.kind === 'tv' ? store.latestResumeFor(item.kind, item.id) : null;
    const resumable = latest && !latest.finished && latest.durationMs > 0;
    const cinema = !resumable ? cinemaLabels(item.kind, data) : null;
    const left = resumable ? Math.max(1, Math.round((latest.durationMs - latest.positionMs) / 60000)) : null;
    const standard = item.kind === 'movie' || item.kind === 'tv';
    const play = playCard({
      label: cinema?.[0] || t(resumable ? 'resume' : 'play'),
      sub: cinema?.[1] || (left ? t('left', { time: runtime(left) }) : null),
      progress: resumable ? latest.positionMs / latest.durationMs : null,
      // As in the app, Play on the hero opens the title's page, where the version is chosen.
      onClick: () => go(titleHref(item)),
    });
    const saved = () => store.inMyList(item.kind, item.id);
    const list = labeledRound(saved() ? 'check' : 'plus', t(saved() ? 'inMyListShort' : 'myListShort'), async () => {
      list.disabled = true;
      try {
        await store.toggleMyList({ ...item, ...(data || {}), kind: item.kind, id: item.id, poster: item.poster, backdrop: item.backdrop });
        list.setLabel(t(saved() ? 'inMyListShort' : 'myListShort'), saved() ? 'check' : 'plus');
      } catch { /* the button stays as it was; the store keeps the change for the next push */ } finally {
        list.disabled = false;
      }
    });
    return h('div.hero-actions', play, standard ? list : null,
      labeledRound('info', t('about'), () => go(titleHref(item))),
      items.length > 1 ? labeledRound('next', t('next'), () => change(active + 1, true)) : null);
  };

  const draw = (item, data, current) => {
    if (current !== generation) return;
    const info = data
      ? heroInfo(item.kind, { ...data, overview: data.overview || item.overview })
      : heroInfo(item.kind, { title: item.title, year: item.year, overview: item.overview });
    body.replaceChildren(info, buttons(item, data));
  };

  const change = (index, focusNext = false) => {
    active = (items.length + index) % items.length;
    carouselPositions.set(key, active);
    const current = ++generation;
    const item = items[active];
    // The new picture fades in over the old one (Crossfade, 300 ms).
    const picture = img(item.backdrop || item.poster, '', 'hero-img', true);
    if (picture) {
      art.append(picture);
      picture.addEventListener('load', () => {
        [...art.children].filter((c) => c !== picture).forEach((c) => setTimeout(() => c.remove(), 320));
      }, { once: true });
    }
    body.replaceChildren();
    const standard = item.kind === 'movie' || item.kind === 'tv';
    if (standard) {
      api.title(item.kind, item.id, { signal }).then((data) => {
        if (data.backdrop && picture && current === generation) picture.src = data.backdrop;
        draw(item, data, current);
      }).catch(() => draw(item, null, current));
    } else {
      draw(item, null, current);
    }
    dotsSlot.replaceChildren(items.length > 1 ? dots(items.length, active, (i) => change(i), items.map((i) => i.title)) : '');
    if (focusNext) requestAnimationFrame(() => body.querySelector('.labeled-round:last-child')?.focus({ preventScroll: true }));
    schedule();
  };
  change(active);
  return section;
}

/* ---------------------------------------------------------------- Link rows (home/PageRows.kt) */

const MOODS = { chills: 0, light: 1, family: 2, puzzle: 3, emotions: 4, adrenaline: 5 };
const linkHref = (link) => '#/' + link.route + '?label=' + encodeURIComponent(link.title);

function moodTile(link) {
  const mood = Object.keys(MOODS).find((m) => link.route.endsWith('-' + m)) || 'chills';
  return h(`a.tile.tile-landscape.tile-mood.mood-${mood}`, { href: linkHref(link) },
    h('div.card-art.art-landscape.mood-art', h('span.mood-glow'), h('span.mood-veil'), h('span.mood-name', link.title)));
}

function platformTile(link, brand) {
  const art = h('div.card-art.art-platform');
  const name = () => art.replaceChildren(h('span.platform-name', link.title));
  if (brand?.color) {
    art.style.setProperty('--brand', brand.color);
    art.style.setProperty('--brand-accent', brand.accent || brand.color);
  }
  const logo = brand?.logo ? img(brand.logo, link.title, '', false) : null;
  if (logo) {
    logo.addEventListener('error', name, { once: true });
    art.append(logo);
  } else name();
  return h('a.tile.tile-platform', { href: linkHref(link), 'aria-label': link.title }, art);
}

function studioTile(link, brand) {
  const art = h('div.card-art.art-studio');
  const name = () => art.replaceChildren(h('span.studio-name', link.title));
  const logo = brand?.logo ? img(brand.logo, link.title, 'studio-logo', false) : null;
  if (logo) {
    logo.addEventListener('error', name, { once: true });
    art.append(logo);
  } else name();
  return h('a.tile.tile-studio', { href: linkHref(link), 'aria-label': link.title }, art);
}

function sagaTile(link) {
  const art = h('div.card-art.art-landscape.saga-art', h('span.card-fade'));
  const logo = link.image ? img(link.image, link.title, 'saga-logo', false) : null;
  if (logo) {
    logo.addEventListener('error', () => logo.replaceWith(h('span.saga-name', link.title)), { once: true });
    art.append(logo);
  } else art.append(h('span.saga-name', link.title));
  art.append(h('span.saga-label', t('collectionLabel')));
  return h('a.tile.tile-landscape.tile-saga', { href: '#/' + link.route, 'aria-label': link.title }, art);
}

const TOOL_ICONS = { 'discover/releases': 'calendar', 'discover/pick': 'casino', history: 'history', 'anime-calendar': 'calendar', manga: 'manga' };

function toolTile(link) {
  const glyph = TOOL_ICONS[link.route];
  return h('a.tile.tile-tool', { href: linkHref(link) },
    h('div.card-art.art-tool', glyph ? icon(glyph, 'tool-icon') : null, h('span.tool-name', link.title)));
}

function genreTile(link, index) {
  return h(`a.tile.tile-landscape.tile-genre.genre-${index % 8}`, { href: linkHref(link), 'vars': { '--lean': String(0.30 + 0.08 * (index % 4)) } },
    h('div.card-art.art-landscape.genre-art', h('span.genre-shape'), h('span.genre-edge'), h('span.genre-name', link.title)));
}

function linkRow(row, brands) {
  const family = row.links[0]?.route.split('/').slice(0, 2).join('/');
  let cards;
  let kind = 'tiles';
  if (family === 'shelf/mood') cards = row.links.map(moodTile);
  else if (family === 'shelf/platform') {
    kind = 'platforms';
    cards = row.links.map((l) => platformTile(l, brands?.platforms?.[l.route.split('-').pop()]));
  } else if (family === 'shelf/studio') {
    kind = 'studios';
    cards = row.links.map((l) => studioTile(l, brands?.studios?.[l.route.split('/').pop()]));
  } else if (family?.startsWith('collections')) cards = row.links.map(sagaTile);
  else if (family === 'shelf/genre') cards = row.links.map(genreTile);
  else {
    kind = 'tools';
    cards = row.links.map(toolTile);
  }
  return cardRow(row.title, cards, { kind, id: row.id });
}

/* ---------------------------------------------------------------- Title rows */

function titleRow(row, result, section) {
  const style = row.style || 'Poster';
  const items = result.items.slice(0, ROW_CAP);
  const cards = items.map((item, index) => {
    if (style === 'Landscape') return landscapeTile(item);
    if (style === 'Square') return landscapeTile(item, { square: true });
    if (style === 'Top10') return posterTile(item, { rank: index + 1 });
    return posterTile(item);
  });
  if (style !== 'Top10' && (result.more || result.items.length > ROW_CAP)) {
    cards.push(seeAllTile(style, '#/row/' + [section, row.id].map(encodeURIComponent).join('/') + '?label=' + encodeURIComponent(row.title), row.title));
  }
  const kind = { Landscape: 'landscape', Square: 'square' }[style] || 'poster';
  return cardRow(row.title, cards, { kind, id: row.id });
}

/* ---------------------------------------------------------------- Page */

export async function render({ root, signal, go, params }) {
  const section = params.section || 'home';
  const label = t(section === 'series' ? 'seriesNav' : section);
  const profile = store.currentProfile()?.id;
  const top = h('div.home-hero', h('section.hero.hero-home.hero-loading', { 'aria-hidden': 'true' },
    h('div.hero-art.skeleton'), h('div.hero-body', h('div.skeleton.skeleton-logo'), h('div.skeleton.skeleton-line'), h('div.skeleton.skeleton-line.short'))));
  const body = h('div.rows', skeletonRow('poster'), skeletonRow('poster'));
  setTitle(label);
  root.replaceChildren(top, body);
  const resolved = new Map();
  let observer;
  let noticeTimer;
  signal.addEventListener('abort', () => observer?.disconnect(), { once: true });
  signal.addEventListener('abort', () => clearTimeout(noticeTimer), { once: true });

  function rowData(row) {
    if (!resolved.has(row.id)) {
      const result = api.sectionRow(section, row.id, { profile }, { signal }).catch((error) => {
        resolved.delete(row.id);
        throw error;
      });
      resolved.set(row.id, result);
    }
    return resolved.get(row.id);
  }

  async function loadRow(row, slot) {
    try {
      const result = await rowData(row);
      if (signal.aborted) return;
      // A row with too few titles never shows (PageRow.MIN_ITEMS).
      if (result.items.length >= 3) slot.replaceChildren(titleRow(row, result, section));
      else slot.remove();
    } catch (error) {
      if (!signal.aborted) slot.replaceChildren(h('section.card-row', h('h2.row-heading', row.title), h('div.row-message', problem(error, () => loadRow(row, slot)))));
    }
  }

  async function load() {
    try {
      const all = await api.section(section, { signal });
      if (signal.aborted) return;
      const rows = orderRows(all, section);
      const featured = rows.find((row) => row.style === 'Top10') || rows.find((row) => row.id === 'anime-popular') ||
        rows.find((row) => !row.links?.length && row.id !== 'because');
      let heroItems = [];
      if (featured) {
        try {
          heroItems = (await rowData(featured)).items.filter((item) => item.backdrop || item.poster).slice(0, SLIDES);
        } catch { /* no hero: the page starts with its rows */ }
      }
      if (signal.aborted) return;
      top.replaceChildren(heroItems.length
        ? hero(heroItems, `${store.user()?.id}|${profile}|${section}`, go, signal)
        : h('div.page', h('h1.page-title', label)));

      const needsBrands = rows.some((row) => row.links?.some((l) => /^shelf\/(platform|studio)\//.test(l.route)));
      const brands = needsBrands
        ? api.brandTiles(section === 'series' ? 'tv' : 'movie', { signal }).catch(() => null) : Promise.resolve(null);

      body.replaceChildren();
      if (section === 'home') {
        const resume = store.continueWatching();
        if (resume.length) body.append(cardRow(t('continueWatching'), resume.map(continueCard), { kind: 'continue', id: 'continue' }));
        const follows = store.followedTitles();
        if (follows.length) body.append(cardRow(t('followingTitles'), follows.map((i) => posterTile(i)), { id: 'followed' }));
      }
      observer?.disconnect();
      observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          observer.unobserve(entry.target);
          const row = rows.find((item) => item.id === entry.target.dataset.shelf);
          if (row) loadRow(row, entry.target);
        });
      }, { rootMargin: '600px 0px' });
      for (const row of rows) {
        if (row.links?.length) {
          const slot = h('div.row-slot');
          body.append(slot);
          brands.then((b) => { if (!signal.aborted) slot.replaceWith(linkRow(row, b)); });
        } else {
          const kind = { Landscape: 'landscape', Square: 'square' }[row.style] || 'poster';
          const slot = h('div.row-slot', { 'data-shelf': row.id }, skeletonRow(kind, 7));
          body.append(slot);
          observer.observe(slot);
        }
      }
      if (!body.childElementCount) body.append(h('div.rows-message', empty(t('homeEmpty'))));
    } catch (error) {
      if (signal.aborted) return;
      top.replaceChildren(h('div.page-top'));
      body.replaceChildren(h('div.rows-message', problem(error, load)));
    }
  }

  function scheduleNotices() {
    const check = async () => {
      if (signal.aborted) return;
      try {
        const followed = store.followedTitles();
        if (!followed.length || !profile) return;
        const items = await api.followedNews(profile, { signal });
        for (const entry of items) {
          if (signal.aborted) return;
          const title = followed.find((item) => item.kind === entry.kind && item.id === entry.id);
          if (!title) continue;
          const notice = h('a.followed-notice.glass', { href: titleHref(title), role: 'status' },
            h('strong', title.title), h('span', entry.season != null ?
              t('newEpisodeNotice', { season: entry.season, episode: entry.episode }) : t('newReleaseNotice')));
          root.querySelector('.followed-notice')?.remove();
          root.append(notice);
          await store.markFollowedNews(entry);
          await new Promise((done) => setTimeout(done, 8_000));
          notice.remove();
        }
      } catch {
        // A provider failure leaves the news pending for the next check.
      } finally {
        if (!signal.aborted) noticeTimer = setTimeout(check, 6 * 60 * 60_000);
      }
    };
    if (section === 'home') noticeTimer = setTimeout(check, 20_000);
  }
  scheduleNotices();
  await load();
}
