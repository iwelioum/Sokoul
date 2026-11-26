// Title page: backdrop, logo or title, one metadata line, Play / Resume, synopsis; seasons and episodes for a series;
// the cast row.

import { api } from '../api.js';
import { lang, t, runtime } from '../i18n.js';
import * as store from '../store.js';
import {
  h, img, rail, thumbCard, skeletonRail, skeletonLines, problem, empty, primaryButton, roundButton, metaLine,
  icon, personCard, posterCard, setTitle,
} from '../ui.js';
import { playHref } from './home.js';

const RESUME_FROM_MS = 30_000;

function showPanel(root, title, contents, className = '') {
  const dialog = h('dialog.title-panel.glass', { class: className, 'aria-label': title });
  dialog.append(h('div.title-panel-head', h('h2.section-title', title), roundButton('close', t('close'), () => dialog.close())),
    contents);
  dialog.addEventListener('close', () => dialog.remove(), { once: true });
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  root.append(dialog);
  dialog.showModal();
}

function about(root, data) {
  const language = data.originalLanguage ? new Intl.DisplayNames([lang], { type: 'language', fallback: 'code' }).of(data.originalLanguage) : null;
  const fields = [
    [t('originalTitle'), data.originalTitle], [t('originalLanguage'), language],
    [t('releaseDate'), data.releaseDate], [t('rating'), data.rating ? (data.rating.toFixed(1) + ' / 10') : null],
    [t('classification'), data.certification],
  ].filter(([, value]) => value);
  showPanel(root, t('aboutTitle'), h('div.title-info', h('h3', data.title),
    data.overview ? h('p.synopsis', data.overview) : null,
    h('dl', fields.map(([label, value]) => h('div', h('dt', label), h('dd', value))))));
}

function previewTrailer(root, trailer) {
  if (!/^[A-Za-z0-9_-]{11}$/.test(trailer.key)) return;
  showPanel(root, trailer.name,
    h('div.title-trailer', h('iframe', {
      src: `https://www.youtube-nocookie.com/embed/${trailer.key}?autoplay=1`,
      title: trailer.name, allow: 'autoplay; encrypted-media; picture-in-picture', allowfullscreen: true,
      referrerpolicy: 'strict-origin-when-cross-origin',
    }), h('a.btn.btn-secondary', { href: `https://www.youtube.com/watch?v=${trailer.key}`, target: '_blank', rel: 'noopener noreferrer' }, t('openYoutube'))),
    'title-panel-wide');
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
  const canPlay = store.canPlayCertification(data.certification);
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
  if (target && canPlay) {
    const href = playHref(kind, id, target.season, target.episode);
    let label;
    if (isTv) label = t(resumable ? 'resumeEpisode' : 'playEpisode', { season: target.season, episode: target.episode });
    else label = t(resumable ? 'resume' : 'play');
    actions.append(primaryButton(label, () => go(href)));
    if (resumable) actions.append(roundButton('restart', t('fromStart'), () => go(`${href}?from=start`)));
  }
  const listStatus = h('p.form-error', { role: 'status' });
  const listButton = roundButton('bookmark', t('addToList'), async () => {
    listButton.disabled = true;
    listStatus.textContent = '';
    try {
      await store.toggleMyList(data);
      updateListButton();
    } catch {
      listStatus.textContent = t('loadFailed');
    } finally {
      listButton.disabled = false;
    }
  }, 'list-toggle');
  function updateListButton() {
    const saved = store.inMyList(kind, id);
    const label = t(saved ? 'removeFromList' : 'addToList');
    listButton.setAttribute('aria-label', label);
    listButton.setAttribute('aria-pressed', String(saved));
    listButton.title = label;
  }
  updateListButton();
  actions.append(listButton);
  const follow = roundButton('bell', t('followTitle'), async () => {
    follow.disabled = true;
    listStatus.textContent = '';
    try {
      await store.toggleFollow(data);
      updateFollow();
    } catch {
      listStatus.textContent = t('loadFailed');
    } finally {
      follow.disabled = false;
    }
  }, 'list-toggle');
  function updateFollow() {
    const followed = store.follows(kind, id);
    follow.setAttribute('aria-pressed', String(followed));
    const label = t(followed ? 'unfollowTitle' : 'followTitle');
    follow.setAttribute('aria-label', label);
    follow.title = label;
  }
  updateFollow();
  actions.append(follow, roundButton('info', t('aboutTitle'), () => about(root, data)));
  if (data.trailers?.length) actions.append(roundButton('film', t('trailers'), () => previewTrailer(root, data.trailers[0])));

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
      metaLine([data.year, isTv ? t('series') : runtime(data.runtime), data.certification, ...(data.genres || []).slice(0, 2)], 'meta hero-meta'),
      actions,
      !canPlay ? h('p.lead', t('kidsPlaybackBlocked')) : null,
      listStatus,
      progress,
      data.overview ? h('p.synopsis', data.overview) : null,
      data.collection ? h('a.btn.btn-secondary', { href: `#/collections/${data.collection.id}` }, data.collection.title) : null,
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
        if (e.name === 'AbortError' || active !== number) return;
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

  if (data.trailers?.length) rails.append(rail(t('trailers'), data.trailers.map((video) =>
    h('button.card.trailer-card', { type: 'button', onClick: () => previewTrailer(root, video) },
      h('div.art.art-thumb', img(data.backdrop, ''), h('span.trailer-play', icon('play'))), h('span.card-title', video.name))), 'thumb'));
  if (data.similar?.length) rails.append(rail(t('relatedTitles'), data.similar.map((item) => posterCard(item, true))));
  if (data.cast?.length) rails.append(rail(t('cast'), data.cast.slice(0, 20).map(personCard), 'person'));
  if (data.crew?.length) rails.append(rail(t('crew'), data.crew.map(personCard), 'person'));
  if (data.watch?.length) {
    rails.append(rail(t('howToWatch'), data.watch.map((offer) => {
      const text = h('span', offer.name, h('small', t('watch' + offer.type)));
      const logo = img(offer.logo, '', 'offer-logo');
      return data.watchLink ? h('a.card.offer-card', { href: data.watchLink, target: '_blank', rel: 'noopener noreferrer' }, logo, text)
        : h('div.card.offer-card', logo, text);
    }), 'editorial-tiles'));
  }
}
