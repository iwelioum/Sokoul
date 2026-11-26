// Sign-in: name + passphrase. Also the landing of "#/link?code=…": the TV's code is confirmed on the server's /link page.

import { api, session, ApiError } from '../api.js';
import { t } from '../i18n.js';
import * as store from '../store.js';
import { h, setTitle } from '../ui.js';

function deviceName() {
  const ua = navigator.userAgent;
  const what = /iPhone/.test(ua) ? 'iPhone' : /iPad/.test(ua) ? 'iPad' : /Macintosh/.test(ua) ? 'Mac' : /Android/.test(ua) ? 'Android' : /Windows/.test(ua) ? 'Windows' : '';
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : t('deviceName');
  const standalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone;
  return [standalone ? 'Sokoul' : browser, what].filter(Boolean).join(' · ').slice(0, 60);
}

export function render({ root, query, path, signal, go }) {
  const joining = path === 'join';
  const setup = path === 'setup';
  const heading = t(joining ? 'joinInvite' : setup ? 'createOwner' : 'signinTitle');
  setTitle(heading);
  root.classList.add('screen-center', 'screen-signin');

  const code = query.get('code');
  if (path === 'link' && code) {
    root.append(h('div.form-card',
      h('img.signin-logo', { src: 'icons/icon-192.png', alt: '', width: 72, height: 72 }),
      h('h1.page-title', t('linkTitle')),
      h('p.lead', t('linkIntro', { code })),
      h('a.btn.btn-primary.btn-block', { href: `/link?code=${encodeURIComponent(code)}` }, h('span', t('linkButton'))),
    ));
    return;
  }

  const next = query.get('next');
  const invitation = h('input.field', { id: 'signin-invite', type: 'text', autocomplete: 'off',
    value: code || '', maxlength: 20, required: true, autocapitalize: 'characters' });
  const nameInput = h('input.field', {
    id: 'signin-name', name: 'username', type: 'text', autocomplete: 'username', autocapitalize: 'none',
    autocorrect: 'off', spellcheck: 'false', enterkeyhint: 'next', required: true, maxlength: 64,
  });
  const passInput = h('input.field', {
    id: 'signin-pass', name: 'password', type: 'password', autocomplete: joining || setup ? 'new-password' : 'current-password', enterkeyhint: 'go',
    required: true, maxlength: 1024,
  });
  const error = h('p.form-error', { role: 'alert', 'aria-live': 'polite' });
  const submitLabel = t(joining || setup ? 'save' : 'signinButton');
  const submit = h('button.btn.btn-primary.btn-block', { type: 'submit' }, h('span', submitLabel));
  const alternate = h('div.signin-alternate', joining || setup ? h('a.btn-text', { href: '#/signin' }, t('signinTitle'))
    : h('a.btn-text', { href: '#/join' }, t('joinInvite')));

  const form = h('form.form-card', { novalidate: true },
    h('img.signin-logo', { src: 'icons/icon-192.png', alt: '', width: 72, height: 72 }),
    h('h1.page-title', heading),
    h('p.lead', t(joining || setup ? 'newAccountIntro' : 'signinIntro')),
    joining ? h('label.field-label', { for: 'signin-invite' }, t('inviteCode')) : null,
    joining ? invitation : null,
    h('label.field-label', { for: 'signin-name' }, t('name')),
    nameInput,
    h('label.field-label', { for: 'signin-pass' }, t('passphrase')),
    passInput,
    error,
    submit, alternate,
  );

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = nameInput.value.trim();
    const passphrase = passInput.value;
    if (!name || !passphrase || joining && !invitation.value.trim()) {
      error.textContent = t('missingFields');
      return;
    }
    if ((joining || setup) && passphrase.length < 12) {
      error.textContent = t('newAccountIntro');
      return;
    }
    error.textContent = '';
    submit.disabled = true;
    submit.firstChild.textContent = t('signingIn');
    try {
      const res = joining ? await api.join(invitation.value.trim(), name, passphrase, deviceName())
        : setup ? await api.setupOwner(name, passphrase, deviceName()) : await api.login(name, passphrase, deviceName());
      if (signal.aborted) return;
      session.set(res.token);
      store.reset();
      go(next && next.startsWith('#/') && !next.startsWith('#/signin') ? next : '#/profiles', true);
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      if (signal.aborted) return;
      error.textContent = status === 429 ? t('tooMany') : setup ? t('setupFailed') : joining ? t('joinFailed')
        : status === 401 ? t('wrongCredentials') : t('noAnswer');
      submit.disabled = false;
      submit.firstChild.textContent = submitLabel;
      // Keep what was typed (product principle 3); select the passphrase so it can be retyped at once.
      if (status === 401) passInput.select();
    }
  });

  root.append(form);
  if (!joining && !setup) api.setupStatus({ signal }).then((status) => {
    if (status?.needsOwner && !signal.aborted) alternate.prepend(h('a.btn-text', { href: '#/setup' }, t('createOwner')));
  }).catch(() => {});
  if (window.matchMedia('(pointer: fine)').matches) nameInput.focus();
}
