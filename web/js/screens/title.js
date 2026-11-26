// A title's page as the apps draw it (title/TitleScreen.kt, HeroDetails.kt): the picture the height of the screen with
// the hero's text and buttons at its foot, then the episodes, the trailers, the cast, the titles like it and the details.

import { api } from '../api.js';
import { lang, t, runtime } from '../i18n.js';
import * as store from '../store.js';
import { h, img, problem, roundButton, icon, setTitle } from '../ui.js';
import { cardRow, skeletonRow, posterTile } from '../cards.js';
import { heroInfo, playCard, labeledRound, cinemaLabels, formatDate } from '../hero.js';
import { playHref } from './home.js';

const RESUME_FROM_MS = 30_000;

function showPanel(root, title, contents, className = '') {
  const dialog = h('dialog.title-panel.glass', { class: className, 'aria-label': title });
  dialog.append(h('div.title-panel-head', h('h2.panel-title', title), roundButton('close', t('close'), () => dialog.close())),
    contents);
  dialog.addEventListener('close', () => dialog.remove(), { once: true });
  dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });
  root.append(dialog);
  dialog.showModal();
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

/** "15 septembre 2021": a release date in full. */
function longDate(iso) {
  if (!iso) return null;
  const date = new Date(`${iso.slice(0, 10)}T12:00:00`);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat(lang === 'en' ? 'en' : 'fr-FR', { day: 'numeric', month: 'long', year: 'numeric' }).format(date);
}

function languageName(code) {
  try {
    const name = new Intl.DisplayNames([lang], { type: 'language' }).of(code);
    return name ? name.charAt(0).toUpperCase() + name.slice(1) : code;
  } catch {
    return code;
  }
}

/** The Details block: the synopsis, then what it is and who made it, in two columns (TitleDetails.DetailsBlock). */
function details(kind, data) {
  const series = kind === 'tv';
  const writers = (data.crew || []).filter((c) => c.character === 'Writer' || c.character === 'Screenplay').map((c) => c.name);
  const facts = [
    series ? (data.seasonCount ? [t('factSeasons'), String(data.seasonCount)] : null) : (data.runtime ? [t('factLength'), runtime(data.runtime)] : null),
    data.releaseDate ? [t(series ? 'factFirstAired' : 'factReleased'), longDate(data.releaseDate)] : null,
    data.genres?.length ? [t('factGenres'), data.genres.join(', ')] : null,
    data.certification ? [t('factRating'), data.certification] : null,
    data.originalLanguage ? [t('factLanguage'), languageName(data.originalLanguage)] : null,
    data.brands?.length ? [t('factStudios'), data.brands.slice(0, 3).map((b) => b.name).join(', ')] : null,
  ].filter(Boolean);
  const people = [
    data.director ? [t(series ? 'factCreated' : 'factDirected'), data.director] : null,
    writers.length ? [t('factWritten'), [...new Set(writers)].join(', ')] : null,
    data.cast?.length ? [t('factStarring'), data.cast.slice(0, 5).map((p) => p.name).join(', ')] : null,
  ].filter(Boolean);
  const column = (rows) => h('dl.details-column', rows.map(([label, value]) => h('div', h('dt', label), h('dd', value))));
  return h('section.card-row.details-row',
    h('h2.row-heading', t('details')),
    h('div.details-block',
      h('div.details-synopsis', h('p.details-title', data.title),
        data.tagline ? h('p.details-tagline', data.tagline) : null,
        data.overview ? h('p.details-overview', data.overview) : null),
      column(facts), column(people)));
}

function personTile(person) {
  const initials = (person.name || '?').split(/\s+/).slice(0, 2).map((word) => word[0]).join('').toUpperCase();
  const photo = img(person.photo, '');
  return h(person.id ? 'a.tile.tile-person' : 'div.tile.tile-person', person.id ? { href: `#/person/${person.id}` } : null,
    h('div.card-art.art-person', h('span.person-initials', { 'aria-hidden': 'true' }, initials), photo),
    h('span.person-name', person.name), person.character ? h('span.person-role', person.character) : null);
}

function trailerTile(root, video, original) {
  const language = /\bVF\b|français|french/i.test(video.name) && original !== 'fr' ? 'VF' : 'VO';
  return h('button.tile.tile-trailer', { type: 'button', onClick: () => previewTrailer(root, video), 'aria-label': video.name },
    h('div.card-art.art-trailer', img(`https://i.ytimg.com/vi/${video.key}/hqdefault.jpg`, ''), h('span.card-bottom-fade'),
      h('span.trailer-text', h('span.trailer-caption', language), h('span.trailer-name', video.name || t('trailer')))));
}

function episodeTile(id, number, ep) {
  const seen = store.resumeOf(store.resumeKey('tv', id, number, ep.number));
  const part = seen ? (seen.finished ? 1 : seen.durationMs > 0 ? seen.positionMs / seen.durationMs : 0) : 0;
  return h('a.tile.tile-episode', { href: playHref('tv', id, number, ep.number), 'aria-label': t('episodeTitle', { n: ep.number, name: ep.name || '' }) },
    h('div.card-art.art-episode', img(ep.still, ''), h('span.episode-fade'),
      seen?.finished ? icon('check', 'episode-seen') : null,
      part > 0 ? h('span.episode-bar', h('span.episode-fill', { vars: { '--p': String(Math.min(1, part)) } })) : null,
      h('span.episode-play', icon('play'), ep.runtime ? h('span', runtime(ep.runtime)) : null)),
    h('div.episode-text',
      h('span.episode-number', t('episodeUpper', { n: ep.number })),
      h('span.episode-name', ep.name || ''),
      ep.overview ? h('span.episode-overview', ep.overview) : null,
      ep.airDate ? h('span.episode-date', formatDate(ep.airDate)) : null));
}

function skeleton() {
  return [
    h('section.hero.hero-title-page.hero-loading', { 'aria-hidden': 'true' },
      h('div.hero-art.skeleton'),
      h('div.hero-body', h('div.skeleton.skeleton-logo'), h('div.skeleton.skeleton-line'), h('div.skeleton.skeleton-line.short'))),
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
    root.replaceChildren(h('div.page-top'), h('div.rows-message', problem(e, () => render({ root, params, signal, go }))));
    return;
  }
  setTitle(data.title);

  const isTv = kind === 'tv';
  const canPlay = store.canPlayCertification(data.certification);
  const latest = store.latestResumeFor(kind, id);
  const resumable = latest && !latest.finished && latest.positionMs > RESUME_FROM_MS && latest.durationMs > 0;

  // What Play starts: the film, or for a series the episode in progress, else the first episode.
  const firstSeason = isTv ? (data.seasons || []).find((s) => s.number > 0) || (data.seasons || [])[0] : null;
  let target;
  if (isTv) {
    if (latest) target = { season: latest.season, episode: latest.episode };
    else if (firstSeason) target = { season: firstSeason.number, episode: 1 };
  } else {
    target = {};
  }

  const status = h('p.form-error.title-status', { role: 'status' });
  const actions = h('div.hero-actions');
  const cinema = !resumable ? cinemaLabels(kind, data) : null;
  if (target && canPlay) {
    const href = playHref(kind, id, target.season, target.episode);
    const left = resumable ? Math.max(1, Math.round((latest.durationMs - latest.positionMs) / 60000)) : null;
    let label = t(resumable ? 'resume' : 'play');
    if (isTv && resumable && target.season != null) label = `${t('resume')} ${t('episodeShort', { season: target.season, episode: target.episode })}`;
    if (latest?.finished) label = t('watchAgain');
    const sub = cinema?.[1] || (left ? t('left', { time: runtime(left) })
      : latest?.finished ? t('alreadySeen')
        : isTv ? (target.season != null ? `S${target.season} E${target.episode}` : null)
          : (data.runtime ? runtime(data.runtime) : null));
    actions.append(playCard({ label: cinema?.[0] || label, sub, progress: resumable ? latest.positionMs / latest.durationMs : null, onClick: () => go(href) }));
    if (resumable && !cinema) actions.append(labeledRound('restart', t('startOver'), () => go(`${href}?from=start`)));
  }

  const guarded = (button, action, refresh) => async () => {
    button.disabled = true;
    status.textContent = '';
    try {
      await action();
      refresh();
    } catch {
      status.textContent = t('loadFailed');
    } finally {
      button.disabled = false;
    }
  };
  const saved = () => store.inMyList(kind, id);
  const list = labeledRound(saved() ? 'check' : 'plus', t(saved() ? 'inMyListShort' : 'myListShort'), () => {});
  list.addEventListener('click', guarded(list, () => store.toggleMyList(data),
    () => list.setLabel(t(saved() ? 'inMyListShort' : 'myListShort'), saved() ? 'check' : 'plus')));
  const following = () => store.follows(kind, id);
  const alert = labeledRound(following() ? 'bellOn' : 'bell', t(following() ? 'alertOn' : 'notifyMe'), () => {});
  alert.addEventListener('click', guarded(alert, () => store.toggleFollow(data),
    () => alert.setLabel(t(following() ? 'alertOn' : 'notifyMe'), following() ? 'bellOn' : 'bell')));
  actions.append(list, alert, labeledRound('doc', t('about'), () => {
    root.querySelector('.details-row')?.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'center' });
  }));
  if (data.trailers?.length) actions.append(labeledRound('film', t('trailer'), () => previewTrailer(root, data.trailers[0])));

  const top = h('section.hero.hero-title-page',
    h('div.hero-art', img(data.backdrop || data.poster, '', 'hero-img', true)),
    h('div.hero-scrim'),
    h('div.hero-body',
      heroInfo(kind, data, { remainingMin: resumable ? Math.max(1, Math.round((latest.durationMs - latest.positionMs) / 60000)) : null }),
      actions,
      !canPlay ? h('p.title-note', t('kidsPlaybackBlocked')) : null,
      status,
      data.collection ? h('a.pill-button', { href: `#/collections/${data.collection.id}` }, t('collectionOf', { name: data.collection.title })) : null));

  const rows = h('div.rows.title-rows');
  root.replaceChildren(top, rows);

  if (isTv && (data.seasons || []).length) {
    const seasons = data.seasons.filter((s) => s.number > 0 && (s.episodeCount ?? 1) > 0);
    const shown = seasons.length ? seasons : data.seasons;
    const slot = h('div.episodes-slot');
    let active = target?.season ?? shown[0]?.number;
    const pills = h('div.season-pills', { role: 'tablist', 'aria-label': t('seasons') });
    const loadSeason = async (number) => {
      active = number;
      pills.querySelectorAll('button').forEach((b) => b.setAttribute('aria-selected', String(Number(b.dataset.season) === number)));
      slot.replaceChildren(skeletonRow('episode', 4));
      try {
        const body = await api.season(id, number, { signal });
        if (active !== number) return;
        const eps = body.episodes || [];
        slot.replaceChildren(eps.length ? cardRow(null, eps.map((ep) => episodeTile(id, number, ep)), { kind: 'episodes' })
          : h('p.row-note', t('noEpisodes')));
      } catch (e) {
        if (e.name === 'AbortError' || active !== number) return;
        slot.replaceChildren(h('div.row-message', problem(e, () => loadSeason(number))));
      }
    };
    shown.forEach((s) => pills.append(h('button.season-pill', {
      type: 'button', role: 'tab', 'data-season': s.number, 'aria-selected': String(s.number === active),
      onClick: () => loadSeason(s.number),
    }, s.number > 0 ? t('season', { n: s.number }) : (s.name || t('season', { n: 0 })))));
    rows.append(h('section.card-row.episodes-row', h('div.season-scroll', pills), slot));
    if (active != null) loadSeason(active);
  }

  if (data.trailers?.length) rows.append(cardRow(t('trailers'), data.trailers.slice(0, 12).map((v) => trailerTile(root, v, data.originalLanguage)), { kind: 'trailers' }));
  if (data.cast?.length) rows.append(cardRow(t('castCrew'), data.cast.slice(0, 20).map(personTile), { kind: 'people' }));
  if (data.similar?.length) rows.append(cardRow(t('ifYouLiked'), data.similar.map((item) => posterTile(item)), { kind: 'poster' }));
  rows.append(details(kind, data));
}
