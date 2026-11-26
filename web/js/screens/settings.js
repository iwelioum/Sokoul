import { api } from '../api.js';
import { t } from '../i18n.js';
import { preferences, setPreference } from '../preferences.js';
import { currentProfile } from '../store.js';
import { h, icon, problem, secondaryButton, setTitle, skeletonLines } from '../ui.js';

const SECTIONS = ['home', 'movies', 'series', 'anime'];
const MENU = ['home', 'myList', 'movies', 'series', 'anime', 'collections', 'tv', 'sports'];
const titleOf = (id) => t(id === 'series' ? 'seriesNav' : id);

function link(label, href, glyph = 'chevron') {
  return h('li', h('a.list-row.list-link', { href }, h('div.list-main', h('div.list-label', label)), icon(glyph)));
}

export async function render({ root, params, signal }) {
  const pane = params.pane;
  setTitle(t('settings'));
  const categories = [
    [t('settingsProfile'), [
      [t('editProfiles'), '#/profiles?edit=1'], [t('account'), '#/account'], [t('history'), '#/history'],
    ]],
    [t('settingsDevice'), [
      [t('sources'), '#/sources'], [t('playbackSettings'), '#/settings/playback'],
      [t('homeRows'), '#/settings/home'], [t('menuSettings'), '#/settings/menu'],
      [t('interfaceLanguage'), '#/settings/language'], [t('linkTv'), '/link'],
    ]],
  ];
  const sections = currentProfile()?.kids ? [[t('settingsProfile'), [
    [t('editProfiles'), '#/profiles?edit=1'], [t('history'), '#/history'],
  ]]] : categories;
  const sidebar = h('nav.settings-categories', { 'aria-label': t('settings') },
    sections.map(([group, rows]) => h('section', h('h2.section-title', group),
      h('ul.list', rows.map(([label, href]) => link(label, href))))));
  const content = h('div.settings-content');
  root.append(h('div.page.settings-layout', h('h1.page-title', t('settings')), sidebar, content));
  if (!pane || currentProfile()?.kids) return;

  function select(label, name, values, refresh = false) {
    const value = preferences()[name];
    return h('label.setting-field', h('span', t(label)),
      h('select.field', { onChange: (event) => {
        const choice = event.target.value;
        setPreference(name, name === 'seek' ? Number(choice) : choice);
        if (refresh) location.reload();
      } }, values.map(([id, text]) => h('option', { value: id, selected: String(value) === String(id) }, text))));
  }

  if (pane === 'language') {
    content.replaceChildren(h('h2.section-title', t('interfaceLanguage')),
      select('interfaceLanguage', 'language', [['', t('systemLanguage')], ['fr', 'Français'], ['en', 'English']], true));
  } else if (pane === 'playback') {
    const toggle = h('input', { type: 'checkbox', checked: preferences().autoplayNext,
      onChange: (event) => setPreference('autoplayNext', event.target.checked) });
    content.replaceChildren(h('h2.section-title', t('playbackSettings')),
      h('label.setting-field', t('autoNext'), toggle),
      select('preferredAudio', 'audio', [['french', t('audioFrench')], ['original', t('audioOriginal')]]),
      select('subtitlePref', 'subtitles', [['', t('subtitlesOff')], ['fr', 'Français'], ['en', 'English']]),
      select('seekStep', 'seek', [5, 10, 15, 30].map((n) => [n, t('seconds', { n })])));
  } else if (pane === 'menu') {
    const renderMenu = () => content.replaceChildren(h('h2.section-title', t('menuSettings')),
      orderList(MENU.filter((id) => id !== 'home').map((id) => ({ id, title: titleOf(id) })),
        preferences().menu, (value) => setPreference('menu', value), renderMenu));
    renderMenu();
  } else if (pane === 'home') {
    const tabs = h('select.field', { 'aria-label': t('homeRows'), onChange: loadRows },
      SECTIONS.map((id) => h('option', { value: id }, titleOf(id))));
    const list = h('div.settings-rows');
    content.replaceChildren(h('h2.section-title', t('homeRows')), tabs, list);
    await loadRows();
    async function loadRows() {
      const section = tabs.value;
      list.replaceChildren(skeletonLines(3));
      try {
        const rows = await api.section(section, { signal });
        if (signal.aborted || tabs.value !== section) return;
        const draw = () => list.replaceChildren(orderList(rows, preferences().rows?.[section], (value) => {
          setPreference('rows', { ...preferences().rows, [section]: value });
        }, draw));
        draw();
      } catch (error) {
        if (!signal.aborted) list.replaceChildren(problem(error, loadRows));
      }
    }
  }
}

function orderList(entries, config, save, draw) {
  const byId = new Map(entries.map((entry) => [entry.id, entry.title]));
  const order = [...new Set([...(config?.order || []), ...byId.keys()])].filter((id) => byId.has(id));
  const hidden = new Set(config?.hidden || []);
  function swap(index, other) {
    [order[index], order[other]] = [order[other], order[index]];
    commit();
  }
  function commit() {
    save({ order, hidden: [...hidden] });
    draw();
  }
  return h('div',
    h('ul.list.reorder-list', order.map((id, index) => h('li.list-row',
      h('label.check-field', h('input', { type: 'checkbox', checked: !hidden.has(id), onChange: (event) => {
        if (event.target.checked) hidden.delete(id); else hidden.add(id);
        save({ order, hidden: [...hidden] });
      } }), h('span', byId.get(id))),
      h('div.row-order',
        h('button.btn-text', { type: 'button', disabled: index === 0, onClick: () => swap(index, index - 1), 'aria-label': t('up') + ' · ' + byId.get(id) }, '↑'),
        h('button.btn-text', { type: 'button', disabled: index === order.length - 1, onClick: () => swap(index, index + 1), 'aria-label': t('down') + ' · ' + byId.get(id) }, '↓'))))),
    h('div.page-actions', secondaryButton(t('resetSettings'), () => { save({}); draw(); })));
}
