// Player: the native <video> (HLS natively on Safari, hls.js elsewhere), the versions the server found, subtitles,
// and the resume point saved through sync every 15 s, on pause and on the way out.

import { api } from '../api.js';
import { t } from '../i18n.js';
import * as store from '../store.js';
import { h, icon, roundButton, secondaryButton, problem, setTitle } from '../ui.js';

const SAVE_EVERY_MS = 15_000;
const RESUME_FROM_MS = 30_000;
const HIDE_CONTROLS_MS = 3_000;

let hlsLoading = null;
/** Loads vendor/hls.min.js once (a script from our own origin, so the CSP allows it). */
function loadHls() {
  if (window.Hls) return Promise.resolve(window.Hls);
  if (!hlsLoading) {
    hlsLoading = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'vendor/hls.min.js';
      s.async = true;
      s.onload = () => (window.Hls ? resolve(window.Hls) : reject(new Error('hls.js missing')));
      s.onerror = () => { hlsLoading = null; reject(new Error('hls.js failed')); };
      document.head.append(s);
    });
  }
  return hlsLoading;
}

const nativeHls = (video) => Boolean(video.canPlayType('application/vnd.apple.mpegurl'));

export async function render({ root, params, query, signal, back }) {
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
  const top = h('div.player-top',
    roundButton('close', t('close'), () => back(kind && id ? `#/title/${kind}/${id}` : '#/home'), 'glass'),
    h('div.player-heading', titleText, subText),
    versionsButton);
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
  const onEnded = () => save();
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
    if (e.key !== 'Escape') return;
    if (!panel.hidden) togglePanel(false);
    else back(`#/title/${kind}/${id}`);
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
    const tracks = [...video.textTracks].filter((tr) => tr.kind === 'subtitles' || tr.kind === 'captions');
    const showing = tracks.find((tr) => tr.mode === 'showing');
    const subtitleList = tracks.length ? h('div.option-list', { role: 'menu', 'aria-label': t('subtitles') },
      option(t('subtitlesOff'), null, !showing, () => { tracks.forEach((tr) => { tr.mode = 'disabled'; }); drawPanel(); }),
      tracks.map((tr) => option(tr.label || tr.language, null, tr === showing, () => {
        tracks.forEach((x) => { x.mode = x === tr ? 'showing' : 'disabled'; });
        drawPanel();
      }))) : null;
    panel.replaceChildren(
      h('h2.panel-title', t('versions')), versionList,
      subtitleList ? h('h2.panel-title.panel-title-next', t('subtitles')) : null, subtitleList);
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

  async function startVersion(version, atMs) {
    currentVersion = version;
    status.hidden = false;
    status.replaceChildren(h('p.message-text', version.label || ''));
    let started;
    try {
      started = await api.start(version.id);
    } catch (e) {
      if (closed) return;
      return fail(e);
    }
    if (closed || currentVersion !== version) return;
    detach();
    pendingSeekMs = atMs;
    subtitles = started.subtitles || [];
    subtitles.forEach((s, i) => video.append(h('track', {
      kind: 'subtitles', src: s.url, label: s.label || s.language || `${i + 1}`, srclang: s.language || undefined,
    })));
    video.hidden = false;
    const isHls = started.remux || /\.m3u8(\?|$)/i.test(started.url);
    try {
      if (isHls && !nativeHls(video)) {
        const Hls = await loadHls();
        if (closed) return;
        if (!Hls.isSupported()) return fail();
        // No worker: hls.js would start one from a blob: URL, which the CSP refuses.
        hls = new Hls({ enableWorker: false });
        hls.on(Hls.Events.ERROR, (_, data) => { if (data && data.fatal) fail(); });
        hls.loadSource(started.url);
        hls.attachMedia(video);
      } else {
        video.src = started.url;
      }
    } catch {
      return fail();
    }
    if (versions.length > 1) versionsButton.hidden = false;
  }

  video.addEventListener('loadedmetadata', () => {
    status.hidden = true;
    if (pendingSeekMs && pendingSeekMs > 0 && Number.isFinite(video.duration)) {
      video.currentTime = Math.min(pendingSeekMs / 1000, Math.max(0, video.duration - 5));
    }
    pendingSeekMs = null;
    video.play().catch(() => showControls());
  });
  video.addEventListener('error', () => { if (video.getAttribute('src') || hls) fail(); });

  // Title and picture for the resume point; versions in parallel.
  api.title(kind, id, { signal }).then((d) => {
    meta = { title: d.title || '', image: d.backdrop || d.poster || null };
    titleText.textContent = meta.title;
    setTitle(meta.title);
  }).catch(() => {});

  try {
    const body = await api.versions({ kind, id, season, episode }, { signal });
    versions = body.versions || [];
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
  startVersion(versions[0], startAt);

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
