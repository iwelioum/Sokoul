import { api } from '../api.js';
import { t, runtime } from '../i18n.js';
import * as store from '../store.js';
import { orderRows } from '../preferences.js';
import {
  h, img, rail, posterCard, thumbCard, skeletonRail, skeletonLines, problem, empty, primaryButton, roundButton,
  metaLine, titleHref, setTitle, icon,
} from '../ui.js';

const carouselPositions = new Map();

export function playHref(kind, id, season, episode) {
  return season != null && episode != null ? '#/play/' + [kind, id, season, episode].join('/') : '#/play/' + [kind, id].join('/');
}

export function continueCard(point) {
  const leftMin = point.durationMs > 0 ? Math.max(1, Math.round((point.durationMs - point.positionMs) / 60000)) : 0;
  const parts = [];
  if (point.kind === 'tv' && point.season != null && point.episode != null) {
    parts.push(t('episodeShort', { season: point.season, episode: point.episode }));
  }
  if (point.finished) parts.push(t('watched'));
  else if (leftMin) parts.push(t('left', { time: runtime(leftMin) }));
  return thumbCard({
    href: playHref(point.kind, point.tmdbId, point.season, point.episode),
    image: point.image?.startsWith('/') ? 'https://image.tmdb.org/t/p/w780' + point.image : point.image,
    title: point.title || '',
    subtitle: parts.join(' · '),
    progress: point.durationMs > 0 ? Math.min(1, point.positionMs / point.durationMs) : null,
  });
}

function carousel(items, key, go, signal) {
  let active = Math.min(items.length - 1, carouselPositions.get(key) || 0);
  let generation = 0;
  const artwork = h('div.hero-art');
  const content = h('div.hero-body');
  const dots = h('div.hero-dots', { 'aria-label': t('featured') });
  const section = h('section.hero.hero-carousel', artwork, h('div.hero-scrim'), content, dots);
  const highlight = (item, info) => {
    const heading = h('h1.hero-title', item.title);
    if (info?.logo) {
      const logo = img(info.logo, item.title, 'title-logo', true);
      const logoWrap = h('h1.title-logo-wrap', logo);
      logo.addEventListener('error', () => logoWrap.replaceWith(heading), { once: true });
      return logoWrap;
    }
    return heading;
  };
  const change = (index, focus = false) => {
    active = (items.length + index) % items.length;
    carouselPositions.set(key, active);
    const current = ++generation;
    const item = items[active];
    const standard = item.kind === 'movie' || item.kind === 'tv';
    const play = item.kind === 'movie' ? playHref('movie', item.id) : titleHref(item);
    const draw = (info) => {
      content.replaceChildren(highlight(item, info),
        metaLine([item.year, t(({ movie: 'movie', tv: 'series', anime: 'anime', manga: 'manga' })[item.kind]),
          ...(info?.genres || []).slice(0, 2)], 'meta hero-meta'),
        info?.overview ? h('p.synopsis.hero-synopsis', info.overview) : null,
        h('div.actions', standard ? primaryButton(t('play'), () => go(play)) : null,
          roundButton('info', t('moreInfo'), () => go(titleHref(item)))));
    };
    artwork.replaceChildren(img(item.backdrop || item.poster, '', 'hero-img', true) || h('div.hero-art-placeholder'));
    draw();
    if (standard) api.title(item.kind, item.id, { signal }).then((info) => {
      if (!signal.aborted && current === generation && !content.contains(document.activeElement)) draw(info);
    }).catch(() => {});
    dots.replaceChildren(...items.map((entry, i) => h('button.hero-dot', {
      type: 'button', 'aria-label': entry.title, 'aria-pressed': String(i === active),
      onClick: () => change(i, true),
    })));
    if (focus) dots.children[active]?.focus();
  };
  if (items.length > 1) {
    section.append(h('div.hero-controls', roundButton('prev', t('previous'), () => change(active - 1)),
      roundButton('next', t('next'), () => change(active + 1))));
    section.addEventListener('keydown', (event) => {
      if (!dots.contains(document.activeElement) || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      event.preventDefault();
      change(active + (event.key === 'ArrowRight' ? 1 : -1), true);
    });
  }
  change(active);
  return section;
}

function header(row, href) {
  // One title per row, a word or a short phrase: no kicker above it, no line under it.
  return h('div.editorial-heading',
    h('div.editorial-heading-line', h('h2.rail-title', row.title),
      href ? h('a.editorial-more', { href, 'aria-label': t('seeAllOf', { title: row.title }) }, t('loadMore'), icon('chevron')) : null));
}

function linkShelf(row) {
  return h('section.editorial-shelf', header(row), rail(null, row.links.map((link) =>
    h('a.card.editorial-tile', { href: '#/' + link.route + '?label=' + encodeURIComponent(link.title) },
      link.image ? img(link.image, '', 'editorial-logo') : null, h('span', link.title))), 'editorial-tiles'));
}

function mediaShelf(row, items, section, more) {
  const layout = row.style.toLowerCase();
  const cards = items.map((item, index) => {
    if (layout === 'landscape') return thumbCard({ href: titleHref(item), image: item.backdrop || item.poster, title: item.title });
    if (layout === 'top10') return h('div.editorial-ranked', h('span.editorial-rank', { 'aria-hidden': 'true' }, index + 1), posterCard(item, true));
    return posterCard(item, true);
  });
  const href = more ? '#/row/' + [section, row.id].map(encodeURIComponent).join('/') + '?label=' + encodeURIComponent(row.title) : null;
  return h('section.editorial-shelf', header(row, href), rail(null, cards, 'editorial-' + layout));
}

export async function render({ root, signal, go, params }) {
  const section = params.section || 'home';
  const label = t(section === 'series' ? 'seriesNav' : section);
  const profile = store.currentProfile()?.id;
  const hero = h('div.editorial-hero', h('section.hero.hero-loading', { 'aria-hidden': 'true' },
    h('div.hero-art.skeleton'), h('div.hero-body', skeletonLines(2))));
  const body = h('div.rails.editorial-rails', skeletonRail('poster'), skeletonRail('poster'));
  setTitle(label);
  root.replaceChildren(hero, body);
  const resolved = new Map();
  let observer;
  let noticeTimer;
  signal.addEventListener('abort', () => observer?.disconnect(), { once: true });
  signal.addEventListener('abort', () => clearTimeout(noticeTimer), { once: true });

  async function load() {
    try {
      const all = await api.section(section, { signal });
      if (signal.aborted) return;
      const rows = orderRows(all, section);
      const featured = rows.find((row) => row.style === 'Top10') || rows.find((row) => row.id === 'anime-popular') ||
        rows.find((row) => !row.links?.length && row.id !== 'because');
      if (featured) {
        const selection = await rowData(featured);
        if (signal.aborted) return;
        const items = selection.items.filter((item) => item.backdrop || item.poster).slice(0, 5);
        hero.replaceChildren(items.length ? carousel(items, `${store.user()?.id}|${profile}|${section}`, go, signal)
          : h('div.page', h('h1.page-title', label)));
      } else hero.replaceChildren(h('div.page', h('h1.page-title', label)));

      body.replaceChildren();
      if (section === 'home') {
        const resume = store.continueWatching();
        if (resume.length) body.append(rail(t('continueWatching'), resume.map(continueCard), 'thumb'));
        const follows = store.followedTitles();
        if (follows.length) body.append(rail(t('followingTitles'), follows.map((i) => posterCard(i, true))));
      }
      observer?.disconnect();
      observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          observer.unobserve(entry.target);
          const row = rows.find((item) => item.id === entry.target.dataset.shelf);
          if (row) loadShelf(row, entry.target);
        });
      }, { rootMargin: '500px 0px' });
      for (const row of rows) {
        if (row.links?.length) body.append(linkShelf(row));
        else {
          const slot = h('div.editorial-slot', { 'data-shelf': row.id }, skeletonRail('poster', 4));
          body.append(slot);
          observer.observe(slot);
        }
      }
      if (!body.childElementCount) body.append(empty(t('homeEmpty')));
    } catch (error) {
      if (signal.aborted) return;
      hero.replaceChildren(h('div.page-top'));
      body.replaceChildren(problem(error, load));
    }
  }

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
  async function loadShelf(row, slot) {
    try {
      const result = await rowData(row);
      if (signal.aborted) return;
      if (result.items.length) slot.replaceChildren(mediaShelf(row, result.items, section, result.more));
      else slot.remove();
    } catch (error) {
      if (!signal.aborted) slot.replaceChildren(header(row), problem(error, () => loadShelf(row, slot)));
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
