// "Qui regarde ?": the account's synced profiles. A protected profile asks for its code (checked against the synced
// digest, the code itself never leaves the device that set it).

import { t } from '../i18n.js';
import * as store from '../store.js';
import { h, avatar, setTitle } from '../ui.js';

export function render({ root, query, go }) {
  setTitle(t('whoIsWatching'));
  root.classList.add('screen-center', 'screen-profiles');
  const next = query.get('next');
  const target = next && next.startsWith('#/') && !next.startsWith('#/profiles') ? next : '#/home';

  const open = (p) => {
    store.chooseProfile(p.id);
    go(target, true);
  };

  const list = store.profiles();
  const grid = h('ul.profile-grid', list.map((p) => h('li',
    h('button.profile', { type: 'button', onClick: () => (p.pinHash ? askCode(p) : open(p)) },
      avatar(p, 'avatar-large'),
      h('span.profile-name', p.name),
      p.kids && p.name.trim().toLowerCase() !== t('kids').toLowerCase() ? h('span.profile-sub', t('kids')) : null,
    ))));

  const page = h('div.profiles', h('h1.page-title', t('whoIsWatching')), grid);
  root.append(page);

  function askCode(p) {
    const input = h('input.field.field-code', {
      type: 'password', inputmode: 'numeric', autocomplete: 'off', maxlength: 12, enterkeyhint: 'go',
      'aria-label': t('profileCode', { name: p.name }),
    });
    const error = h('p.form-error', { role: 'alert' });
    const form = h('form.form-card', { novalidate: true },
      avatar(p, 'avatar-large'),
      h('h1.page-title', t('profileCode', { name: p.name })),
      h('p.lead', t('profileCodeHint')),
      input,
      error,
      h('div.form-actions',
        h('button.btn.btn-primary', { type: 'submit' }, h('span', t('open'))),
        h('button.btn.btn-secondary', { type: 'button', onClick: () => form.replaceWith(page) }, h('span', t('cancel'))),
      ),
    );
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      if (store.pinMatches(p, input.value.trim())) open(p);
      else {
        error.textContent = t('wrongCode');
        input.select();
      }
    });
    page.replaceWith(form);
    input.focus();
  }
}
