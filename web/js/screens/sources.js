import { api } from '../api.js';
import { t, shortDate } from '../i18n.js';
import { h, icon, problem, skeletonLines, setTitle } from '../ui.js';

const CONTENT = ['iptv', 'realdebrid', 'subtitles', 'mainapi'];
const SUBTITLES = ['opensubtitles', 'subdl'];
const GLYPHS = { iptv: 'screen', realdebrid: 'film', subtitles: 'layers', mainapi: 'server', tmdb: 'film' };

function state(credential, guide) {
  if (guide.id === 'mainapi') return t('managedServer');
  if (!guide.editable) return t('noKeyRequired');
  if (!credential) return t('notSet');
  if (credential.verdict === 'accepted') return t('verdictAccepted', { date: shortDate(credential.checkedAt) });
  if (credential.verdict === 'refused') return t('verdictRefused');
  return t('verdictUnreachable', { date: shortDate(credential.checkedAt) });
}

function sourceCard(guide, credential) {
  const name = guide.id === 'iptv' ? t('channelsTv') : guide.name;
  return h('a.source-card', { href: '#/sources/' + guide.id },
    icon(GLYPHS[guide.id] || 'settings'),
    h('div.source-summary',
      h('div.source-title', h('strong', name),
        h('span.source-state', credential?.hint && credential.verdict === 'accepted' ? credential.hint : state(credential, guide))),
      h('p', guide.adds)));
}

function field(label, input) {
  return h('label.source-field', h('span.field-label', label), input);
}

function editForm(guide, credential, signal, onChange) {
  const status = h('p.form-error.source-feedback', { role: 'status' });
  const submit = h('button.btn.btn-primary', { type: 'submit' }, h('span', t('checkAndSave')));
  const remove = h('button.btn.btn-secondary', { type: 'button', hidden: !credential }, h('span', t('removeKey')));
  const fields = h('div.source-fields');
  const input = (attrs) => h('input.field', { autocomplete: 'off', spellcheck: 'false', autocapitalize: 'none', required: true, ...attrs });
  const key = input({ type: 'password', maxlength: 256 });
  const address = input({ type: 'url', maxlength: 2048 });
  const username = input({ type: 'text', maxlength: 256 });
  const password = input({ type: 'password', maxlength: 256 });
  const playlist = input({ type: 'password', inputmode: 'url', maxlength: 2048 });
  const mode = h('select.field',
    h('option', { value: 'xtream' }, t('xtreamLogin')),
    h('option', { value: 'playlist' }, t('playlistUrl')));
  const isIptv = guide.id === 'iptv';

  const setFields = () => {
    if (!isIptv) fields.replaceChildren(field(t('apiKey'), key));
    else if (mode.value === 'playlist') fields.replaceChildren(field(t('playlistUrl'), playlist));
    else fields.replaceChildren(field(t('serverAddress'), address), field(t('username'), username), field(t('password'), password));
  };
  mode.addEventListener('change', setFields);
  setFields();
  const form = h('form.source-form', isIptv ? field(t('iptvFormat'), mode) : null, fields,
    h('div.form-actions', submit, remove), status);

  async function update(action, message) {
    submit.disabled = true;
    remove.disabled = true;
    mode.disabled = true;
    for (const control of [key, address, username, password, playlist]) control.disabled = true;
    status.textContent = t('checking');
    try {
      const result = await action();
      if (signal.aborted) return;
      form.reset();
      for (const control of [key, address, username, password, playlist]) control.value = '';
      setFields();
      remove.hidden = !result;
      onChange(result);
      status.textContent = t(message);
    } catch (error) {
      if (signal.aborted) return;
      const messageKey = {
        refused: 'credentialRefused', bad_format: 'credentialFormat', unreachable: 'credentialUnavailable',
        too_many_requests: 'credentialRateLimit',
      }[error.code];
      status.textContent = t(messageKey || (error.offline ? 'noAnswer' : 'loadFailed'));
    } finally {
      submit.disabled = false;
      remove.disabled = false;
      mode.disabled = false;
      for (const control of [key, address, username, password, playlist]) control.disabled = false;
    }
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    let value = key.value.trim();
    if (isIptv) {
      value = mode.value === 'playlist' ? playlist.value.trim() :
        JSON.stringify({ server: address.value.trim(), username: username.value.trim(), password: password.value });
    }
    update(() => api.saveCredential(guide.id, value, { signal }), 'credentialSaved');
  });
  remove.addEventListener('click', () => {
    if (window.confirm(t('confirmRemoveKey', { service: guide.name }))) {
      update(() => api.removeCredential(guide.id, { signal }), 'credentialRemoved');
    }
  });
  return form;
}

function details(guide, credential, signal) {
  const status = h('p.lead');
  const showState = (value) => status.textContent = [state(value, guide), value?.hint].filter(Boolean).join(' · ');
  showState(credential);
  return [
    h('h1.page-title', guide.name), h('p.lead', guide.adds), status,
    guide.editable ? editForm(guide, credential, signal, showState) : null,
    h('div.source-guide',
      h('h2.section-title', t('getCredential')),
      h('ol', guide.steps.map((step) => h('li', step))),
      guide.link ? h('a.btn.btn-secondary', { href: guide.link, target: '_blank', rel: 'noopener noreferrer' }, guide.linkLabel || guide.name) : null,
      h('p.lead', guide.cost), h('p.lead', guide.privacy)),
  ];
}

export async function render({ root, params, signal }) {
  const service = params.service;
  setTitle(t('sources'));
  const page = h('div.page' + (service ? '.page-narrow' : ''));
  root.append(page);

  async function load() {
    page.replaceChildren(h('h1.page-title', t('sources')), skeletonLines(6));
    try {
      const [guides, credentials] = await Promise.all([api.guides({ signal }), api.credentials({ signal })]);
      if (signal.aborted) return;
      const byService = new Map(credentials.map((credential) => [credential.service, credential]));
      const byId = new Map(guides.map((guide) => [guide.id, guide]));
      if (service === 'subtitles') {
        setTitle(t('subtitles'));
        page.replaceChildren(h('h1.page-title', t('subtitles')), h('div.sources-grid',
          SUBTITLES.map((id) => byId.get(id)).filter(Boolean).map((guide) => sourceCard(guide, byService.get(guide.id)))));
        return;
      }
      if (service) {
        const guide = guides.find((guide) => guide.id === service);
        if (guide) {
          setTitle(guide.name);
          page.replaceChildren(...details(guide, byService.get(service), signal));
        } else page.replaceChildren(h('h1.page-title', t('sources')), h('a.btn.btn-secondary', { href: '#/sources' }, t('back')));
        return;
      }
      const group = (label, entries) => h('section.sources-group', h('h2.section-title', t(label)),
        h('div.sources-grid', entries.map((guide) => sourceCard(guide, byService.get(guide.id)))));
      byId.set('subtitles', { id: 'subtitles', name: t('subtitles'), adds: t('subtitleSources'), editable: true });
      const subtitleCredential = SUBTITLES.map((id) => byService.get(id)).find(Boolean);
      if (subtitleCredential) byService.set('subtitles', { ...subtitleCredential, hint: null });
      page.replaceChildren(h('h1.page-title', t('sources')), h('p.lead', t('sourcesIntro')),
        group('contentSources', CONTENT.map((id) => byId.get(id)).filter(Boolean)),
        group('expertKeys', guides.filter((guide) => !CONTENT.includes(guide.id) && !SUBTITLES.includes(guide.id))));
    } catch (error) {
      if (!signal.aborted) page.replaceChildren(h('h1.page-title', t('sources')), problem(error, load));
    }
  }

  await load();
}
