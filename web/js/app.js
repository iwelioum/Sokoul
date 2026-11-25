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

document.documentElement.lang = lang;

/** [pattern, screen module, options]. `nav`: the bar shows; `back`: it starts with a back button. */
const ROUTES = [
  [/^signin$/, signin, { open: true }],
  [/^link$/, signin, { open: true }],
  [/^profiles$/, profiles, { noProfile: true }],
  [/^home$/, home, { nav: 'home' }],
  [/^title\/(movie|tv)\/([\w-]+)$/, title, { nav: 'title', back: true, keys: ['kind', 'id'] }],
  [/^search$/, search, { nav: 'search' }],
  [/^play\/(movie|tv)\/([\w-]+)(?:\/(\d+)\/(\d+))?$/, player, { keys: ['kind', 'id', 'season', 'episode'] }],
  [/^sources$/, sources, { nav: 'sources', back: true }],
  [/^account$/, account, { nav: 'account' }],
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

function renderNav(active, withBack) {
  if (!active) {
    nav.hidden = true;
    nav.replaceChildren();
    document.body.classList.remove('has-nav');
    return;
  }
  const profile = store.currentProfile();
  const tab = (id, label, href) => h('a.nav-tab', { href, 'aria-current': active === id ? 'page' : null }, label);
  const bar = h('div.nav-row',
    withBack ? h('button.nav-back.glass', { type: 'button', 'aria-label': t('back'), title: t('back'), onClick: () => back() }, icon('back')) : null,
    h('nav.nav-pill.glass', { 'aria-label': 'Sokoul' },
      profile ? h('a.nav-avatar', { href: '#/profiles', 'aria-label': t('switchProfile'), title: t('switchProfile') }, avatar(profile, 'avatar-nav')) : null,
      tab('home', t('home'), '#/home'),
      tab('search', t('search'), '#/search'),
      h('span.nav-wide', tab('sources', t('sources'), '#/sources')),
      tab('account', t('account'), '#/account'),
    ),
  );
  nav.replaceChildren(bar);
  nav.hidden = false;
  document.body.classList.add('has-nav');
}

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
  }

  const params = {};
  (opts.keys || []).forEach((k, i) => { params[k] = m[i + 1] ?? null; });
  const controller = new AbortController();
  const root = h('div.screen', { 'data-screen': path.split('/')[0] });
  document.body.dataset.screen = path.split('/')[0];
  renderNav(opts.nav || null, Boolean(opts.back));
  app.replaceChildren(root);
  const restoreTo = scrollMemory.get(hash);
  window.scrollTo(0, 0);
  current = { hash, controller, cleanup: null };
  try {
    current.cleanup = await mod.render({ root, params, query, signal: controller.signal, path, go, back, signOut }) || null;
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
  route();
}

// Expose for screens that need to sign out cleanly.
export async function signOut() {
  try { await api.logout(); } catch { /* the token is dropped either way */ }
  session.clear();
  store.reset();
  go('#/signin', true);
}
