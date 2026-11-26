import { t } from '../i18n.js';
import * as store from '../store.js';
import { h, icon, avatar, setTitle, secondaryButton } from '../ui.js';

const MAX_PROFILES = 6;
const COLORS = 8;

export function render({ root, query, signal, go }) {
  setTitle(t('whoIsWatching'));
  root.classList.add('screen-center', 'screen-profiles');
  const next = query.get('next');
  const target = next && next.startsWith('#/') && !next.startsWith('#/profiles') ? next : '#/home';
  let editing = query.get('edit') === '1';

  function open(profile) {
    store.chooseProfile(profile.id);
    go(target, true);
  }

  function draw(focusId) {
    const profiles = store.profiles();
    const grid = h('ul.profile-grid', profiles.map((profile) => h('li',
      h('button.profile', { type: 'button', 'data-id': profile.id, onClick: () => {
        const action = () => editing ? form(profile) : open(profile);
        if (profile.pinHash) askCode(profile, action);
        else action();
      } },
      avatar(profile, 'avatar-large'),
      h('span.profile-name', profile.name),
      profile.kids ? h('span.profile-sub', t('kids')) : null,
      editing ? h('span.profile-edit-icon', icon('edit')) : null))));
    if (profiles.length < MAX_PROFILES) {
      grid.append(h('li', h('button.profile.profile-add', { type: 'button', onClick: () => form(null) },
        h('span.avatar.avatar-large.profile-plus', icon('plus')), h('span.profile-name', t('addProfile')))));
    }
    root.replaceChildren(h('div.profiles', h('h1.page-title', t(editing ? 'editProfiles' : 'whoIsWatching')), grid,
      secondaryButton(t(editing ? 'done' : 'editProfiles'), () => { editing = !editing; draw(); })));
    if (focusId) [...grid.querySelectorAll('button')].find((button) => button.dataset.id === focusId)?.focus();
  }

  function askCode(profile, accept) {
    const input = h('input.field.field-code', {
      type: 'password', inputmode: 'numeric', autocomplete: 'off', maxlength: 4, enterkeyhint: 'go',
      'aria-label': t('profileCode', { name: profile.name }),
    });
    const feedback = h('p.form-error', { role: 'alert' });
    const form = h('form.form-card', { onSubmit: (event) => {
      event.preventDefault();
      if (store.pinMatches(profile, input.value.trim())) accept();
      else {
        feedback.textContent = t('wrongCode');
        input.select();
      }
    } }, avatar(profile, 'avatar-large'), h('h1.page-title', t('profileCode', { name: profile.name })),
    h('p.lead', t('profileCodeHint')), input, feedback,
    h('div.form-actions', h('button.btn.btn-primary', { type: 'submit' }, t('open')),
      secondaryButton(t('cancel'), () => draw(profile.id))));
    root.replaceChildren(form);
    input.focus();
  }

  function form(profile) {
    const name = h('input.field', { type: 'text', value: profile?.name || '', required: true, maxlength: 20, autocomplete: 'off' });
    const kids = h('input', { type: 'checkbox', checked: profile?.kids });
    const pin = h('input.field', { type: 'password', inputmode: 'numeric', maxlength: 4, pattern: '[0-9]{4}',
      autocomplete: 'off', placeholder: profile?.pinHash ? t('keepCode') : '' });
    const removeCode = h('input', { type: 'checkbox' });
    let color = profile?.avatar ?? store.profiles().length % COLORS;
    const palette = h('div.profile-colours', { role: 'group', 'aria-label': t('avatarColour') });
    for (let i = 0; i < COLORS; i++) {
      palette.append(h('button.profile-colour', {
        type: 'button', 'aria-pressed': String(i === color), 'aria-label': t('colourNumber', { n: i + 1 }),
        onClick: () => {
          color = i;
          [...palette.children].forEach((button, index) => button.setAttribute('aria-pressed', String(color === index)));
        },
      }, avatar({ avatar: i, name: name.value || 'S' }, 'avatar-medium')));
    }
    const status = h('p.form-error', { role: 'status' });
    const save = h('button.btn.btn-primary', { type: 'submit' }, t('save'));
    const remove = profile && store.profiles().length > 1 ? secondaryButton(t('removeProfile'), async () => {
      if (!window.confirm(t('confirmProfileRemoval', { name: profile.name }))) return;
      await mutate(() => store.removeProfile(profile));
    }) : null;
    async function mutate(action) {
      if (save.disabled) return;
      save.disabled = true;
      if (remove) remove.disabled = true;
      status.textContent = '';
      try {
        const id = await action();
        if (!signal.aborted) draw(id);
      } catch {
        if (!signal.aborted) status.textContent = t('loadFailed');
      } finally {
        save.disabled = false;
        if (remove) remove.disabled = false;
      }
    }
    const view = h('form.form-card.profile-form', { onSubmit: (event) => {
      event.preventDefault();
      if (!name.value.trim()) {
        status.textContent = t('profileNameRequired');
        return;
      }
      mutate(() => store.saveProfile(profile, {
        name: name.value.trim(), avatar: color, kids: kids.checked,
        pin: removeCode.checked ? null : pin.value || undefined,
      }));
    } }, h('h1.page-title', t(profile ? 'editProfile' : 'addProfile')),
    h('label.source-field', t('name'), name), palette,
    h('label.check-field', kids, t('kidsProfile')),
    h('label.source-field', t('optionalCode'), pin),
    profile?.pinHash ? h('label.check-field', removeCode, t('removeCode')) : null,
    status, h('div.form-actions', save, secondaryButton(t('cancel'), () => draw(profile?.id)), remove));
    root.replaceChildren(view);
    name.focus();
  }

  draw();
}
