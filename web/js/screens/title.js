// Title page: backdrop, logo or title, one metadata line, Play / Resume, synopsis; seasons and episodes for a series;
// the cast row.

import { api } from '../api.js';
import { t, runtime } from '../i18n.js';
import * as store from '../store.js';
import {
  h, img, rail, thumbCard, skeletonRail, skeletonLines, problem, empty, primaryButton, roundButton, metaLine,
  setTitle,
} from '../ui.js';
import { playHref } from './home.js';

const RESUME_FROM_MS = 30_000;

function castCard(person) {
  const initials = (person.name || '?').split(/\s+/).map((w) => w.charAt(0)).slice(0, 2).join('').toUpperCase();
  return h('div.card.card-person',
    h('div.art.art-person', h('span.art-initials', { 'aria-hidden': 'true' }, initials), img(person.photo, '')),
    h('div.card-text', h('div.card-title', person.name), person.character ? h('div.card-sub', person.character) : null));
}

function skeleton() {
  return [
    h('section.hero.hero-detail.hero-loading', { 'aria-hidden': 'true' },
      h('div.hero-art.skeleton'),
      h('div.hero-body', skeletonLines(3, 'hero-lines'))),
    h('div.rails', skeletonRail('thumb', 5)),
  ];
}

export async function render({ root, params, signal, go }) {
  const { kind, id } = params;
  root.replaceChildren(...skeleton());

  let data;
  try {
    data = await api.title(kind, id, { signal });
  } catch (e) {
    if (e.name === 'AbortError') return;
    root.replaceChildren(h('div.page-top'), h('div.rails', problem(e, () => render({ root, params, signal, go }))));
    return;
  }
  setTitle(data.title);

  const isTv = kind === 'tv';
  const latest = store.latestResumeFor(kind, id);
  const resumable = latest && !latest.finished && latest.positionMs > RESUME_FROM_MS;

  // What Play starts: the film, or for a series the episode in progress, else the first episode.
  const firstSeason = isTv ? (data.seasons || []).find((s) => s.number > 0) || (data.seasons || [])[0] : null;
  let target;
  if (isTv) {
    if (latest) target = { season: latest.season, episode: latest.episode };
    else if (firstSeason) target = { season: firstSeason.number, episode: 1 };
  } else {
    target = {};
  }

  const actions = h('div.actions');
  if (target) {
    const href = playHref(kind, id, target.season, target.episode);
    let label;
    if (isTv) label = t(resumable ? 'resumeEpisode' : 'playEpisode', { season: target.season, episode: target.episode });
    else label = t(resumable ? 'resume' : 'play');
    actions.append(primaryButton(label, () => go(href)));
    if (resumable) actions.append(roundButton('restart', t('fromStart'), () => go(`${href}?from=start`)));
  }

  const heading = data.logo
    ? h('h1.title-logo-wrap', img(data.logo, data.title, 'title-logo', true))
    : h('h1.hero-title', data.title);
  if (data.logo) {
    // When the logo fails, fall back to the title in text.
    const logoImg = heading.querySelector('img');
    logoImg?.addEventListener('error', () => heading.replaceWith(h('h1.hero-title', data.title)), { once: true });
  }

  const progress = resumable && latest.durationMs > 0
    ? h('div.title-progress', { 'aria-hidden': 'true' }, h('div.progress', h('div.progress-fill', { vars: { '--p': String(latest.positionMs / latest.durationMs) } })))
    : null;

  const top = h('section.hero.hero-detail',
    h('div.hero-art', img(data.backdrop, '', 'hero-img', true)),
    h('div.hero-scrim'),
    h('div.hero-body',
      heading,
      metaLine([data.year, isTv ? t('series') : runtime(data.runtime), ...(data.genres || []).slice(0, 2)], 'meta hero-meta'),
      actions,
      progress,
      data.overview ? h('p.synopsis', data.overview) : null,
    ),
  );

  const rails = h('div.rails');
  root.replaceChildren(top, rails);

  if (isTv && (data.seasons || []).length) {
    const seasonsWithEpisodes = data.seasons.filter((s) => s.episodeCount > 0 || s.number > 0);
    const episodesSlot = h('div.episodes-slot');
    let active = target?.season ?? seasonsWithEpisodes[0]?.number;
    const pills = h('div.segmented', { role: 'tablist', 'aria-label': t('seasons') });
    const loadSeason = async (number) => {
      active = number;
      pills.querySelectorAll('button').forEach((b) => b.setAttribute('aria-selected', String(Number(b.dataset.season) === number)));
      episodesSlot.replaceChildren(skeletonRail('thumb', 5, false));
      try {
        const body = await api.season(id, number, { signal });
        if (active !== number) return;
        const eps = body.episodes || [];
        if (!eps.length) {
          episodesSlot.replaceChildren(empty(t('noEpisodes')));
          return;
        }
        episodesSlot.replaceChildren(rail(null, eps.map((ep) => {
          const r = store.resumeOf(store.resumeKey('tv', id, number, ep.number));
          return thumbCard({
            href: playHref('tv', id, number, ep.number),
            image: ep.still,
            title: t('episodeTitle', { n: ep.number, name: ep.name || '' }),
            subtitle: [runtime(ep.runtime), ep.overview].filter(Boolean).join(' · '),
            progress: r && r.durationMs > 0 && !r.finished ? r.positionMs / r.durationMs : null,
          });
        }), 'thumb episodes'));
      } catch (e) {
        if (e.name === 'AbortError') return;
        episodesSlot.replaceChildren(problem(e, () => loadSeason(number)));
      }
    };
    seasonsWithEpisodes.forEach((s) => pills.append(h('button.segment', {
      type: 'button', role: 'tab', 'data-season': s.number, 'aria-selected': String(s.number === active),
      onClick: () => loadSeason(s.number),
    }, s.name || t('season', { n: s.number }))));
    rails.append(h('section.rail.rail-seasons',
      h('h2.rail-title', t('episodes')),
      h('div.segmented-wrap', pills),
      episodesSlot));
    if (active != null) loadSeason(active);
  }

  if ((data.cast || []).length) rails.append(rail(t('cast'), data.cast.slice(0, 20).map(castCard), 'person'));
}
