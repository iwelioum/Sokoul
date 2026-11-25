// Account: who is signed in, the devices of the account (GET /api/sessions), and sign out.

import { api } from '../api.js';
import { t, ago } from '../i18n.js';
import * as store from '../store.js';
import { h, icon, avatar, problem, skeletonLines, setTitle } from '../ui.js';

function linkRow(label, href, glyph = 'chevron') {
  return h('li', h('a.list-row.list-link', { href },
    h('div.list-main', h('div.list-label', label)),
    icon(glyph, 'list-chevron')));
}

export async function render({ root, signal, signOut }) {
  setTitle(t('account'));
  const me = store.user();
  const profile = store.currentProfile();
  const devices = h('div.list-slot', skeletonLines(3, 'list-skeleton'));

  root.append(h('div.page.page-narrow',
    h('h1.page-title', t('account')),
    h('div.account-head',
      profile ? avatar(profile, 'avatar-medium') : null,
      h('div',
        h('div.account-name', me?.name || ''),
        h('div.account-role', me?.role === 'owner' ? t('owner') : t('member')))),
    h('ul.list',
      linkRow(t('switchProfile'), '#/profiles'),
      linkRow(t('sources'), '#/sources'),
      linkRow(t('linkTv'), '/link')),
    h('h2.section-title', t('devices')),
    devices,
    h('div.page-actions',
      h('button.btn.btn-secondary', { type: 'button', onClick: () => signOut() }, h('span', t('signOut'))))));

  const load = async () => {
    let body;
    try {
      body = await api.sessions();
    } catch (e) {
      if (e.name === 'AbortError') return;
      devices.replaceChildren(problem(e, load));
      return;
    }
    if (signal.aborted) return;
    const sessions = body?.sessions || [];
    devices.replaceChildren(h('ul.list', sessions.map((s) => {
      const revoke = s.current ? null : h('button.btn-text', {
        type: 'button',
        onClick: async (e) => {
          e.currentTarget.disabled = true;
          try { await api.revokeSession(s.id); } catch { /* the list reloads either way */ }
          load();
        },
      }, t('revoke'));
      return h('li.list-row',
        h('div.list-main',
          h('div.list-label', s.deviceName || t('deviceName')),
          h('div.list-detail', s.current ? t('thisDevice') : t('lastUsed', { when: ago(s.lastUsedAt) }))),
        revoke);
    })));
  };
  await load();
}
