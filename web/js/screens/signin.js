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

export function render({ root, query, path, go }) {
  setTitle(t('signinTitle'));
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
  const nameInput = h('input.field', {
    id: 'signin-name', name: 'username', type: 'text', autocomplete: 'username', autocapitalize: 'none',
    autocorrect: 'off', spellcheck: 'false', enterkeyhint: 'next', required: true, maxlength: 64,
  });
  const passInput = h('input.field', {
    id: 'signin-pass', name: 'password', type: 'password', autocomplete: 'current-password', enterkeyhint: 'go',
    required: true, maxlength: 1024,
  });
  const error = h('p.form-error', { role: 'alert', 'aria-live': 'polite' });
  const submit = h('button.btn.btn-primary.btn-block', { type: 'submit' }, h('span', t('signinButton')));

  const form = h('form.form-card', { novalidate: true },
    h('img.signin-logo', { src: 'icons/icon-192.png', alt: '', width: 72, height: 72 }),
    h('h1.page-title', t('signinTitle')),
    h('p.lead', t('signinIntro')),
    h('label.field-label', { for: 'signin-name' }, t('name')),
    nameInput,
    h('label.field-label', { for: 'signin-pass' }, t('passphrase')),
    passInput,
    error,
    submit,
  );

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = nameInput.value.trim();
    const passphrase = passInput.value;
    if (!name || !passphrase) {
      error.textContent = t('missingFields');
      return;
    }
    error.textContent = '';
    submit.disabled = true;
    submit.firstChild.textContent = t('signingIn');
    try {
      const res = await api.login(name, passphrase, deviceName());
      session.set(res.token);
      store.reset();
      go(next && next.startsWith('#/') && !next.startsWith('#/signin') ? next : '#/profiles', true);
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      error.textContent = status === 401 ? t('wrongCredentials') : status === 429 ? t('tooMany') : t('noAnswer');
      submit.disabled = false;
      submit.firstChild.textContent = t('signinButton');
      // Keep what was typed (product principle 3); select the passphrase so it can be retyped at once.
      if (status === 401) passInput.select();
    }
  });

  root.append(form);
  if (window.matchMedia('(pointer: fine)').matches) nameInput.focus();
}
