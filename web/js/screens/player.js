// Player: the native <video> (HLS natively on Safari, hls.js elsewhere), the versions the server found, subtitles,
// and the resume point saved through sync every 15 s, on pause and on the way out.

import { api } from '../api.js';
import { attachMedia } from '../media.js';
import { t } from '../i18n.js';
import * as store from '../store.js';
import { preferences } from '../preferences.js';
import { playHref } from './home.js';
import { h, icon, roundButton, secondaryButton, problem, setTitle } from '../ui.js';

const SAVE_EVERY_MS = 15_000;
const RESUME_FROM_MS = 30_000;
const HIDE_CONTROLS_MS = 3_000;

export async function render({ root, params, query, signal, back, go }) {
  const kind = params.kind;
  const id = params.id;
  const season = params.season != null ? Number(params.season) : null;
  const episode = params.episode != null ? Number(params.episode) : null;
  const fromStart = query.get('from') === 'start';
  const key = store.resumeKey(kind, id, season, episode);
  const episodeLabel = season != null ? t('episodeShort', { season, episode }) : null;

  // Leaving the screen aborts [signal]; cleanup runs then, even while the versions are still loading.
  signal.addEventListener('abort', () => cleanup(), { once: true });
  root.classList.add('screen-player');
  document.body.classList.add('is-playing');

  const video = h('video.player-video', { playsinline: true, 'webkit-playsinline': true, controls: true, preload: 'metadata' });
  video.hidden = true; // shown once a version is attached, so an error is not drawn over an empty player
  const titleText = h('div.player-title', '');
  const subText = h('div.player-sub', episodeLabel || '');
  const status = h('div.player-status', { role: 'status', 'aria-live': 'polite' }, h('p.message-text', t('loadingVersions')));
  const panel = h('div.panel.glass.player-panel', { hidden: true, role: 'dialog', 'aria-label': t('versions') });
  const versionsButton = secondaryButton(t('versions'), () => togglePanel(), 'layers');
  versionsButton.hidden = true;
  const fullscreen = roundButton('fullscreen', t('fullscreen'), () => {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else if (stage.requestFullscreen) stage.requestFullscreen().catch(() => {});
    else video.webkitEnterFullscreen?.();
  }, 'glass');
  const nextButton = secondaryButton(t('nextEpisode'), () => nextHref && go(nextHref), 'next');
  nextButton.hidden = true;
  const top = h('div.player-top',
    roundButton('close', t('close'), () => back(kind && id ? `#/title/${kind}/${id}` : '#/home'), 'glass'),
    h('div.player-heading', titleText, subText),
    h('div.player-actions', nextButton, fullscreen, versionsButton));
  const stage = h('div.player', video, status, top, panel);
  root.append(stage);

  let meta = { title: '', image: null };
  let versions = [];
  let currentVersion = null;
  let subtitles = [];
  let hls = null;
  let saveTimer = 0;
  let hideTimer = 0;
  let pendingSeekMs = null;
  let closed = false;
  let nextHref = null;
  let audioChoice = null;
  let fallback = null;
  let onlineTrack = null;
  let online = null;
  let offset = 0;
  let cues = [];
  let subtitleRequest = null;
  let subtitleSearch = null;
  let subtitleLoading = false;
  let subtitleError = null;
  const prefs = preferences();

  // --- Resume points -----------------------------------------------------------------------------------------------
  const save = (leaving = false) => {
    if (!video.duration || !Number.isFinite(video.duration) || video.currentTime < 1) return;
    store.saveResume({
      kind, tmdbId: id, season, episode,
      positionMs: video.currentTime * 1000,
      durationMs: video.duration * 1000,
      title: meta.title,
      subtitle: episodeLabel,
      image: meta.image,
    }, leaving);
  };
  const onPause = () => save();
  const onEnded = () => {
    save();
    if (nextHref && prefs.autoplayNext) go(nextHref, true);
    else showControls();
  };
  const onPageHide = () => save(true);
  const onVisibility = () => { if (document.visibilityState === 'hidden') save(true); };
  video.addEventListener('pause', onPause);
  video.addEventListener('ended', onEnded);
  window.addEventListener('pagehide', onPageHide);
  document.addEventListener('visibilitychange', onVisibility);
  video.addEventListener('play', () => {
    clearInterval(saveTimer);
    saveTimer = setInterval(() => { if (!video.paused) save(); }, SAVE_EVERY_MS);
    scheduleHide();
  });
  video.addEventListener('pause', () => { clearInterval(saveTimer); showControls(); });

  // --- Controls overlay ---------------------------------------------------------------------------------------------
  function showControls() {
    stage.classList.remove('controls-hidden');
    scheduleHide();
  }
  function scheduleHide() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      if (!video.paused && panel.hidden) stage.classList.add('controls-hidden');
    }, HIDE_CONTROLS_MS);
  }
  stage.addEventListener('pointermove', showControls, { passive: true });
  stage.addEventListener('pointerdown', showControls, { passive: true });
  const onKey = (e) => {
    if (e.key === 'Escape') {
      if (!panel.hidden) togglePanel(false);
      else if (!document.fullscreenElement) back(`#/title/${kind}/${id}`);
      return;
    }
    if (!panel.hidden || video.hidden || e.ctrlKey || e.metaKey || e.altKey ||
        e.target.closest?.('button,a,input,select,textarea,[contenteditable]')) return;
    if (!['ArrowLeft', 'ArrowRight', ' '].includes(e.key)) return;
    e.preventDefault();
    if (e.key === ' ') {
      if (video.paused) video.play().catch(() => {});
      else video.pause();
    } else if (Number.isFinite(video.duration)) {
      const delta = (prefs.seek || 10) * (e.key === 'ArrowRight' ? 1 : -1);
      video.currentTime = Math.min(video.duration, Math.max(0, video.currentTime + delta));
    }
    showControls();
  };
  document.addEventListener('keydown', onKey);

  // --- Versions and subtitles panel ---------------------------------------------------------------------------------
  function togglePanel(open = panel.hidden) {
    panel.hidden = !open;
    versionsButton.setAttribute('aria-expanded', String(open));
    if (open) {
      drawPanel();
      panel.querySelector('[aria-checked="true"]')?.focus({ preventScroll: true });
    }
    showControls();
  }

  function option(label, detail, checked, onClick) {
    return h('button.option', { type: 'button', role: 'menuitemradio', 'aria-checked': String(checked), onClick },
      h('span.option-text', h('span.option-label', label), detail ? h('span.option-detail', detail) : null),
      checked ? icon('check', 'option-check') : null);
  }

  function drawPanel() {
    const versionList = h('div.option-list', { role: 'menu', 'aria-label': t('versions') },
      versions.map((v) => option(
        v.label || [v.quality, v.language].filter(Boolean).join(' · '),
        [v.quality, v.language, v.origin].filter(Boolean).filter((x) => !String(v.label || '').includes(x)).join(' · '),
        currentVersion && v.id === currentVersion.id,
        () => { togglePanel(false); if (!currentVersion || v.id !== currentVersion.id) startVersion(v, video.currentTime * 1000); },
      )));
    const tracks = [...video.textTracks].filter((tr) => tr !== onlineTrack && (tr.kind === 'subtitles' || tr.kind === 'captions'));
    const showing = tracks.find((tr) => tr.mode === 'showing');
    const subtitleList = tracks.length || onlineTrack ? h('div.option-list', { role: 'menu', 'aria-label': t('subtitles') },
      option(t('subtitlesOff'), null, !showing && onlineTrack?.mode !== 'showing', () => {
        disableSubtitles();
        drawPanel();
      }),
      tracks.map((tr) => option(tr.label || tr.language, null, tr === showing, () => {
        disableSubtitles();
        tr.mode = 'showing';
        drawPanel();
      })),
      online ? option(online.release || online.language, online.service, onlineTrack.mode === 'showing', () => {
        disableSubtitles();
        onlineTrack.mode = 'showing';
        drawPanel();
      }) : null) : null;
    const audio = availableAudio();
    const audioList = audio.length > 1 ? h('div.option-list', { role: 'menu', 'aria-label': t('audioTracks') },
      audio.map(({ label, language, active, choose }) => option(label, language, active, () => {
        choose();
        audioChoice = language || label;
        drawPanel();
      }))) : null;
    panel.replaceChildren(
      h('h2.panel-title', t('versions')), versionList,
      audioList ? h('h2.panel-title.panel-title-next', t('audioTracks')) : null, audioList,
      h('h2.panel-title.panel-title-next', t('subtitles')), subtitleList,
      h('div.player-subtitle-search', secondaryButton(t('onlineSubtitles'), findSubtitles),
        subtitleLoading ? h('p.list-detail', { role: 'status' }, t('checking')) : null,
        subtitleError ? problem(subtitleError) : null,
        subtitleSearch?.length === 0 ? h('p.list-detail', t('noOnlineSubtitles')) : null,
        subtitleSearch?.length ? h('div.option-list', { role: 'menu', 'aria-label': t('onlineSubtitles') },
          subtitleSearch.map((result) => option(result.release || result.language, [result.language, result.service].join(' · '),
            Boolean(online?.id === result.id && onlineTrack.mode === 'showing'), () => chooseSubtitles(result)))) : null,
        online ? h('div.form-actions',
          secondaryButton('−0.5 s', () => shiftSubtitles(-500)),
          h('span.list-detail', t('subtitleOffset', { n: (offset / 1000).toFixed(1) })),
          secondaryButton('+0.5 s', () => shiftSubtitles(500))) : null));
  }

  function disableSubtitles() {
    for (const track of video.textTracks) track.mode = 'disabled';
  }

  async function findSubtitles() {
    if (subtitleLoading) return;
    subtitleError = null;
    subtitleLoading = true;
    drawPanel();
    try {
      const languages = prefs.subtitles === 'en' ? 'en,fr' : 'fr,en';
      subtitleSearch = await api.subtitleSearch({ kind, id, season, episode, languages }, { signal });
    } catch (error) {
      if (!signal.aborted) subtitleError = error;
    } finally {
      subtitleLoading = false;
      if (!signal.aborted) drawPanel();
    }
  }

  async function chooseSubtitles(result) {
    const request = {};
    subtitleRequest = request;
    subtitleError = null;
    try {
      const found = await api.subtitleCues(result.id, { signal });
      if (signal.aborted || subtitleRequest !== request) return;
      onlineTrack ||= video.addTextTrack('subtitles', t('onlineSubtitles'));
      online = result;
      cues = found;
      offset = 0;
      shiftSubtitles(0);
    } catch (error) {
      if (!signal.aborted && subtitleRequest === request) {
        subtitleError = error;
        drawPanel();
      }
    }
  }

  function shiftSubtitles(delta) {
    const Cue = window.VTTCue || window.TextTrackCue;
    if (!onlineTrack || !Cue) return;
    offset = Math.max(-60_000, Math.min(60_000, offset + delta));
    disableSubtitles();
    onlineTrack.mode = 'hidden';
    for (const cue of Array.from(onlineTrack.cues || [])) onlineTrack.removeCue(cue);
    for (const cue of cues) {
      const start = Math.max(0, (cue.start + offset) / 1000);
      const end = (cue.end + offset) / 1000;
      if (end > start) onlineTrack.addCue(new Cue(start, end, String(cue.text)));
    }
    onlineTrack.mode = 'showing';
    if (!panel.hidden) drawPanel();
  }
  function availableAudio() {
    if (hls?.audioTracks?.length) return hls.audioTracks.map((track, index) => ({
      label: track.name || track.lang || String(index + 1), language: track.lang, active: hls.audioTrack === index,
      choose: () => { hls.audioTrack = index; },
    }));
    const tracks = Array.from(video.audioTracks || []);
    return tracks.map((track, index) => ({
      label: track.label || track.language || String(index + 1), language: track.language, active: track.enabled,
      choose: () => tracks.forEach((item) => { item.enabled = item === track; }),
    }));
  }

  function preferredAudio() {
    const language = audioChoice || (prefs.audio === 'french' ? 'fr' : '');
    if (!language) return;
    availableAudio().find((audio) => audio.label === language || audio.language?.toLowerCase().startsWith(language.toLowerCase()))?.choose();
  }
  // --- Playback ----------------------------------------------------------------------------------------------------
  function detach() {
    if (hls) { hls.destroy(); hls = null; }
    [...video.querySelectorAll('track')].forEach((tr) => tr.remove());
    video.removeAttribute('src');
    video.load();
  }

  function fail(error) {
    status.hidden = false;
    status.replaceChildren(error ? problem(error) : h('div.message', h('p.message-text', t('playFailed'))));
    if (versions.length > 1) versionsButton.hidden = false;
    showControls();
  }

  async function startVersion(version, atMs, automatic = false) {
    currentVersion = version;
    fallback = (error) => {
      if (closed || currentVersion !== version) return;
      const nextIndex = versions.indexOf(version) + 1;
      if (automatic && nextIndex < Math.min(4, versions.length)) startVersion(versions[nextIndex], atMs, true);
      else fail(error);
    };
    status.hidden = false;
    status.replaceChildren(h('p.message-text', version.label || ''));
    let started;
    try {
      started = await api.start(version.id, { signal });
    } catch (e) {
      if (closed || currentVersion !== version) return;
      return fallback(e);
    }
    if (closed || currentVersion !== version) return;
    detach();
    pendingSeekMs = atMs;
    subtitles = started.subtitles || [];
    subtitles.forEach((s, i) => video.append(h('track', {
      kind: 'subtitles', src: s.url, label: s.label || s.language || `${i + 1}`, srclang: s.language || undefined,
      default: Boolean(prefs.subtitles && s.language?.toLowerCase().startsWith(prefs.subtitles)),
    })));
    video.hidden = false;
    const stillCurrent = () => !closed && currentVersion === version;
    try {
      const attached = await attachMedia(video, started, stillCurrent, () => (fallback || fail)());
      if (!stillCurrent()) {
        attached?.destroy();
        return;
      }
      hls = attached;
      if (hls) hls.on(window.Hls.Events.AUDIO_TRACKS_UPDATED, preferredAudio);
    } catch {
      if (stillCurrent()) return (fallback || fail)();
    }
    versionsButton.hidden = false;
  }

  video.addEventListener('loadedmetadata', () => {
    fallback = null;
    status.hidden = true;
    preferredAudio();
    if (pendingSeekMs && pendingSeekMs > 0 && Number.isFinite(video.duration)) {
      video.currentTime = Math.min(pendingSeekMs / 1000, Math.max(0, video.duration - 5));
    }
    pendingSeekMs = null;
    video.play().catch(() => showControls());
  });
  video.addEventListener('error', () => { if (video.getAttribute('src') || hls) (fallback || fail)(); });

  // Title and picture for the resume point; versions in parallel.
  const metadata = api.title(kind, id, { signal });
  metadata.then(async (d) => {
    meta = { title: d.title || '', image: d.backdrop || d.poster || null };
    titleText.textContent = meta.title;
    setTitle(meta.title);
    if (kind !== 'tv' || season == null || episode == null) return;
    const seasonData = await api.season(id, season, { signal });
    const next = seasonData.episodes.find((entry) => entry.number > episode);
    if (next) nextHref = playHref(kind, id, season, next.number);
    else {
      const after = d.seasons?.find((entry) => entry.number > season);
      if (after) {
        const detail = await api.season(id, after.number, { signal });
        if (detail.episodes.length) nextHref = playHref(kind, id, after.number, detail.episodes[0].number);
      }
    }
    if (!signal.aborted) nextButton.hidden = !nextHref;
  }).catch(() => {});

  try {
    if (store.currentProfile()?.kids && !store.canPlayCertification((await metadata).certification)) {
      status.replaceChildren(h('p.message-text', t('kidsPlaybackBlocked')));
      return cleanup;
    }
    const body = await api.versions({ kind, id, season, episode }, { signal });
    versions = body.versions || [];
    if (prefs.audio === 'original') {
      const order = ['VO', 'MULTI', 'VOSTFR', 'VF'];
      const weight = (version) => order.includes(version.language) ? order.indexOf(version.language) : order.length;
      versions.sort((a, b) => weight(a) - weight(b));
    }
  } catch (e) {
    if (e.name === 'AbortError') return cleanup;
    fail(e);
    return cleanup;
  }
  if (!versions.length) {
    status.replaceChildren(h('div.message', h('p.message-text', t('noVersions'))));
    return cleanup;
  }
  const resume = store.resumeOf(key);
  const startAt = !fromStart && resume && !resume.finished && resume.positionMs > RESUME_FROM_MS ? resume.positionMs : 0;
  startVersion(versions[0], startAt, true);

  function cleanup() {
    if (closed) return;
    save(true);
    closed = true;
    clearInterval(saveTimer);
    clearTimeout(hideTimer);
    video.removeEventListener('pause', onPause);
    video.removeEventListener('ended', onEnded);
    window.removeEventListener('pagehide', onPageHide);
    document.removeEventListener('visibilitychange', onVisibility);
    document.removeEventListener('keydown', onKey);
    detach();
    document.body.classList.remove('is-playing');
  }
  return cleanup;
}
