// Entry point: the hash router, the navigation bar and the guards (session, then profile).

import { session, api } from './api.js';
import { lang, t } from './i18n.js';
import * as store from './store.js';
import { h, icon, avatar } from './ui.js';

import * as signin from './screens/signin.js';
import * as profiles from './screens/profiles.js';
import * as home from './screens/home.js';
import * as title from './screens/title.js';
import * as search from './screens/search.js';
import * as player from './screens/player.js';
import * as sources from './screens/sources.js';
import * as account from './screens/account.js';
import * as library from './screens/library.js';
import * as live from './screens/live.js';
import * as discover from './screens/discover.js';
import * as work from './screens/work.js';
import * as settings from './screens/settings.js';
import { preferences } from './preferences.js';

document.documentElement.lang = lang;

/** [pattern, screen module, options]. `nav`: the bar shows; `back`: it starts with a back button. */
const ROUTES = [
  [/^signin$/, signin, { open: true }],
  [/^join$/, signin, { open: true }],
  [/^setup$/, signin, { open: true }],
  [/^link$/, signin, { open: true }],
  [/^profiles$/, profiles, { noProfile: true }],
  [/^(home|movies|series|anime)$/, home, { nav: 'section', keys: ['section'] }],
  [/^(myList|manga)$/, library, { nav: 'section', keys: ['section'] }],
  [/^row\/(home|movies|series|anime)\/([\w-]+)$/, library, { nav: 'section', back: true, keys: ['section', 'row'] }],
  [/^shelf\/(genre|mood|studio|platform|anime)\/([\w-]+)$/, library, { nav: 'title', back: true, keys: ['family', 'id'] }],
  [/^discover\/(releases)$/, library, { nav: 'title', back: true, keys: ['section'] }],
  [/^(history|anime-calendar)$/, discover, { nav: 'title', back: true, keys: ['section'] }],
  [/^discover\/(pick)$/, discover, { nav: 'title', back: true, keys: ['section'] }],
  [/^person\/(\d+)$/, discover, { nav: 'title', back: true, keys: ['id'] }],
  [/^work\/(\d+)$/, work, { nav: 'anime', back: true, keys: ['id'] }],
  [/^read\/(\d+)\/([a-f0-9-]+)$/i, { render: work.read }, { nav: 'anime', back: true, keys: ['id', 'chapter'] }],
  [/^(collections)(?:\/([a-z0-9-]+))?$/, library, { nav: 'section', keys: ['section', 'collection'] }],
  [/^(tv|sports)$/, live, { nav: 'section', keys: ['section'] }],
  [/^(tv)\/vod\/(movies|series)(?:\/(\d+))?$/, live, { nav: 'section', back: true, keys: ['section', 'iptvKind', 'iptvId'] }],
  [/^(tv)\/replay\/([\w-]+)$/, live, { nav: 'section', back: true, keys: ['section', 'archiveChannel'] }],
  [/^title\/(movie|tv)\/([\w-]+)$/, title, { nav: 'title', back: true, keys: ['kind', 'id'] }],
  [/^search$/, search, { nav: 'search' }],
  [/^play\/(movie|tv)\/([\w-]+)(?:\/(\d+)\/(\d+))?$/, player, { keys: ['kind', 'id', 'season', 'episode'] }],
  [/^sources(?:\/([a-z]+))?$/, sources, { nav: 'settings', back: true, keys: ['service'] }],
  [/^settings(?:\/(home|playback|language|menu))?$/, settings, { nav: 'settings', keys: ['pane'] }],
  [/^account$/, account, { nav: 'settings', back: true }],
];

// The menu's pages and their icons, in the apps' order (App.kt).
const SECTIONS = [
  ['home', 'home'], ['myList', 'myList'], ['movies', 'movies'], ['series', 'series'],
  ['anime', 'anime'], ['collections', 'collections'], ['tv', 'tv'], ['sports', 'trophy'],
];

const app = document.getElementById('app');
const nav = document.getElementById('nav');
const scrollMemory = new Map();
let current = null; // { hash, cleanup, controller }
let generation = 0;

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [path, query = ''] = raw.split('?');
  return { path: path || 'home', query: new URLSearchParams(query), hash: location.hash || '#/home' };
}

export function go(hash, replace = false) {
  if (replace) {
    history.replaceState(null, '', hash);
    route();
  } else if (location.hash === hash) {
    route();
  } else {
    location.hash = hash;
  }
}

/** Back in history when there is somewhere to go back to inside the app, otherwise to [fallback]. */
export function back(fallback = '#/home') {
  if (history.state && history.state.sokoulDepth > 0) history.back();
  else go(fallback, true);
}

session.onUnauthorized(() => {
  store.reset();
  if (!location.hash.startsWith('#/signin')) go('#/signin', true);
});

/*
 * The apps' bar (shell/NavShell.kt): the menu icon on the left and search on the right, two dark discs over the picture.
 * The sections live in the menu behind the left icon. Scrolled, a black veil comes behind them; going down the bar
 * leaves, going up brings it back. A page with a way back (a title) puts the back arrow in place of the menu.
 */
function renderNav(active, withBack, page = 'browse') {
  nav.querySelector('dialog[open]')?.close();
  if (!active) {
    nav.hidden = true;
    nav.replaceChildren();
    document.body.classList.remove('has-nav');
    return;
  }
  const profile = store.currentProfile();
  const menuPrefs = preferences().menu;
  const ordered = new Set(['home', ...(menuPrefs?.order || []), ...SECTIONS.map(([id]) => id)]);
  const glyphs = new Map(SECTIONS);
  const visible = [...ordered].filter((id) => glyphs.has(id) &&
    !menuPrefs?.hidden?.includes(id) && (!profile?.kids || id !== 'sports'));
  const entry = (id, glyph) => h('a.nav-entry', { href: `#/${id}`, 'aria-current': active === id ? 'page' : null },
    h('span.nav-entry-icon', icon(glyph)), h('span.nav-entry-label', t(id === 'series' ? 'seriesNav' : id)),
    active === id ? icon('check', 'nav-current') : null);
  const panel = h('dialog.nav-panel', { id: 'section-menu', 'aria-label': t('menu') },
    h('nav.nav-list.glass', { 'aria-label': t('menu') },
      profile ? h('a.nav-entry.nav-profile', { href: '#/profiles' }, avatar(profile, 'avatar-menu'),
        h('span.nav-entry-label', profile.name, h('small', t('switchProfile')))) : null,
      h('hr'), visible.map((id) => entry(id, glyphs.get(id))), h('hr'), entry('settings', 'settings')));
  const menu = h('button.nav-button', { type: 'button', 'aria-label': t('menu'), title: t('menu'),
    'aria-haspopup': 'dialog', 'aria-controls': panel.id, 'aria-expanded': 'false',
    onClick: () => {
      panel.showModal();
      menu.setAttribute('aria-expanded', 'true');
      nav.classList.add('is-menu-open');
      (panel.querySelector('[aria-current="page"]') || panel.querySelector('a'))?.focus({ preventScroll: true });
    } }, icon('menu', 'nav-menu-icon'));
  panel.addEventListener('click', (event) => {
    if (event.target === panel || event.target.closest('a')) panel.close();
  });
  panel.addEventListener('close', () => {
    menu.setAttribute('aria-expanded', 'false');
    nav.classList.remove('is-menu-open');
  });
  const backButton = h('button.nav-button', { type: 'button', 'aria-label': t('back'), title: t('back'), onClick: () => back() }, icon('back'));
  const left = withBack ? backButton : menu;
  const search = page === 'title' ? null : h('a.nav-button.nav-search', { href: '#/search', 'aria-label': t('search'), title: t('search'),
    'aria-current': active === 'search' ? 'page' : null }, icon('search', 'nav-search-icon'));
  nav.replaceChildren(h('div.nav-veil', { 'aria-hidden': 'true' }), h('div.nav-row', left, search), panel);
  nav.classList.remove('is-hidden', 'is-veiled');
  nav.hidden = false;
  document.body.classList.add('has-nav');
}

// The bar's veil and its leaving: at the top only the icons show; scrolled, the veil comes; down hides, up shows.
let lastScroll = 0;
window.addEventListener('scroll', () => {
  const y = Math.max(0, window.scrollY);
  const veiled = y > 40;
  nav.classList.toggle('is-veiled', veiled);
  if (Math.abs(y - lastScroll) > 6) {
    nav.classList.toggle('is-hidden', veiled && y > lastScroll && !nav.classList.contains('is-menu-open'));
    lastScroll = y;
  }
}, { passive: true });

async function route() {
  const { path, query, hash } = parseHash();
  const match = ROUTES.map(([re, mod, opts]) => ({ m: path.match(re), mod, opts })).find((r) => r.m);
  if (!match) return go('#/home', true);
  const { m, mod, opts } = match;

  if (!opts.open && !session.signedIn) {
    return go(`#/signin?next=${encodeURIComponent(hash)}`, true);
  }

  const gen = ++generation;
  if (current) {
    scrollMemory.set(current.hash, window.scrollY);
    current.controller.abort();
    try { current.cleanup?.(); } catch { /* a screen's cleanup must not stop navigation */ }
    current = null;
  }

  if (!opts.open) {
    try {
      await store.load();
    } catch (e) {
      if (gen !== generation) return;
      if (e && e.status === 401) return; // onUnauthorized already routed to sign-in
      renderNav(null);
      app.replaceChildren(h('div.screen.screen-center', h('div.message',
        h('p.message-text', e && e.offline ? t('noAnswer') : t('loadFailed')),
        h('button.btn.btn-secondary', { type: 'button', onClick: () => route() }, h('span', t('retry'))))));
      return;
    }
    if (gen !== generation) return;
    if (!opts.noProfile && !store.currentProfile()) {
      return go(`#/profiles?next=${encodeURIComponent(hash)}`, true);
    }
    if (store.currentProfile()?.kids && ['sports', 'sources', 'account'].includes(path.split('/')[0])) {
      return go('#/home', true);
    }
  }

  const params = {};
  (opts.keys || []).forEach((k, i) => { params[k] = m[i + 1] ?? null; });
  const controller = new AbortController();
  const root = h('div.screen', { 'data-screen': path.split('/')[0] });
  document.body.dataset.screen = path.split('/')[0];
  renderNav(opts.nav === 'section' ? params.section : opts.nav, Boolean(opts.back || params.collection), opts.nav);
  lastScroll = 0;
  app.replaceChildren(root);
  const restoreTo = scrollMemory.get(hash);
  window.scrollTo(0, 0);
  const screen = { hash, controller, cleanup: null };
  current = screen;
  try {
    const cleanup = await mod.render({ root, params, query, signal: controller.signal, path, go, back, signOut });
    if (current === screen) screen.cleanup = cleanup || null;
    else cleanup?.();
  } catch (e) {
    if (e && e.name === 'AbortError') return;
    console.error(e);
  }
  if (gen === generation && restoreTo) window.scrollTo(0, restoreTo);
}

// Depth in history.state lets the back button know whether history.back() stays inside the app.
window.addEventListener('hashchange', () => {
  if (!history.state || history.state.sokoulDepth === undefined) {
    const depth = (window.__sokoulDepth = (window.__sokoulDepth || 0) + 1);
    history.replaceState({ sokoulDepth: depth }, '');
  } else {
    window.__sokoulDepth = history.state.sokoulDepth;
  }
  route();
});

history.replaceState({ sokoulDepth: 0 }, '');
window.__sokoulDepth = 0;

// The server's /link page confirms a TV's code; a QR that opened the web client instead is sent there.
const linkCode = new URLSearchParams(location.search).get('code');
if (location.pathname.replace(/\/$/, '').endsWith('/link') && linkCode) {
  location.replace(`/link?code=${encodeURIComponent(linkCode)}`);
} else {
  const path = location.pathname.replace(/^\/|\/$/g, '');
  if (path) {
    const hash = location.hash || (ROUTES.some(([pattern]) => pattern.test(path)) ? `#/${path}${location.search}` : '#/home');
    history.replaceState(history.state, '', `/${hash}`);
  }
  route();
}

// Expose for screens that need to sign out cleanly.
export async function signOut() {
  try { await api.logout(); } catch { /* the token is dropped either way */ }
  session.clear();
  store.reset();
  go('#/signin', true);
}
