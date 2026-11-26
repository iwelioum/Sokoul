import { api } from '../api.js';
import { t } from '../i18n.js';
import { preferences, setPreference } from '../preferences.js';
import { currentProfile } from '../store.js';
import { h, icon, problem, secondaryButton, setTitle, skeletonLines } from '../ui.js';

const SECTIONS = ['home', 'movies', 'series', 'anime'];
const MENU = ['home', 'myList', 'movies', 'series', 'anime', 'collections', 'tv', 'sports'];
const titleOf = (id) => t(id === 'series' ? 'seriesNav' : id);

/*
 * The apps' Settings (library/SettingsScreen.kt): "Réglages" over the categories, grouped "Pour ce profil" / "Pour cet
 * appareil", each with its icon; the chosen one fills white. Its page opens in a large panel on the right, under a
 * breadcrumb, with its rows: the name on the left, the value or the switch on the right. On a phone, the list, then the
 * page alone with the breadcrumb to come back.
 */
const PANES = { playback: 'playbackSettings', home: 'homeRows', menu: 'menuSettings', language: 'interfaceLanguage' };

export async function render({ root, params, signal }) {
  const pane = PANES[params.pane] ? params.pane : null;
  setTitle(t('settings'));
  const entry = (label, href, glyph, id = null) => h('li', h('a.settings-entry', { href, 'aria-current': id && id === pane ? 'page' : null },
    icon(glyph, 'settings-entry-icon'), h('span', label)));
  const categories = [
    [t('settingsProfile'), [
      [t('editProfiles'), '#/profiles?edit=1', 'person'], [t('account'), '#/account', 'server'], [t('history'), '#/history', 'history'],
    ]],
    [t('settingsDevice'), [
      [t('sources'), '#/sources', 'movies'], [t('playbackSettings'), '#/settings/playback', 'play', 'playback'],
      [t('homeRows'), '#/settings/home', 'collections', 'home'], [t('menuSettings'), '#/settings/menu', 'menu', 'menu'],
      [t('interfaceLanguage'), '#/settings/language', 'language', 'language'], [t('linkTv'), '/link', 'tv'],
    ]],
  ];
  const sections = currentProfile()?.kids ? [[t('settingsProfile'), [
    [t('editProfiles'), '#/profiles?edit=1', 'person'], [t('history'), '#/history', 'history'],
  ]]] : categories;
  const sidebar = h('nav.settings-categories', { 'aria-label': t('settings') },
    sections.map(([group, rows]) => h('section', h('h2.settings-group', group),
      h('ul.settings-entries', rows.map(([label, href, glyph, id]) => entry(label, href, glyph, id))))));
  const content = h('div.settings-content');
  const panel = h('section.settings-panel', { 'aria-labelledby': 'settings-pane-title' },
    h('p.settings-crumb', h('a', { href: '#/settings' }, t('settings')), pane ? ` › ${t(PANES[pane])}` : ''),
    h('h2.settings-pane-title', { id: 'settings-pane-title' }, pane ? t(PANES[pane]) : t('settings')),
    content);
  root.append(h('div.page.settings-page', { 'data-pane': pane || '' },
    h('div.settings-side', h('h1.settings-title', t('settings')), sidebar), panel));
  if (!pane || currentProfile()?.kids) {
    content.append(h('p.settings-hint', t('settingsHint')));
    return;
  }

  function select(label, name, values, refresh = false) {
    const value = preferences()[name];
    return h('label.setting-row', h('span.setting-label', t(label)),
      h('span.setting-value', h('select.setting-select', { onChange: (event) => {
        const choice = event.target.value;
        setPreference(name, name === 'seek' ? Number(choice) : choice);
        if (refresh) location.reload();
      } }, values.map(([id, text]) => h('option', { value: id, selected: String(value) === String(id) }, text))), icon('chevron', 'setting-updown')));
  }

  if (pane === 'language') {
    content.replaceChildren(
      select('interfaceLanguage', 'language', [['', t('systemLanguage')], ['fr', 'Français'], ['en', 'English']], true));
  } else if (pane === 'playback') {
    const toggle = h('input.switch', { type: 'checkbox', role: 'switch', checked: preferences().autoplayNext,
      onChange: (event) => setPreference('autoplayNext', event.target.checked) });
    content.replaceChildren(
      h('label.setting-row', h('span.setting-label', t('autoNext')), toggle),
      select('preferredAudio', 'audio', [['french', t('audioFrench')], ['original', t('audioOriginal')]]),
      select('subtitlePref', 'subtitles', [['', t('subtitlesOff')], ['fr', 'Français'], ['en', 'English']]),
      select('seekStep', 'seek', [5, 10, 15, 30].map((n) => [n, t('seconds', { n })])));
  } else if (pane === 'menu') {
    const renderMenu = () => content.replaceChildren(
      orderList(MENU.filter((id) => id !== 'home').map((id) => ({ id, title: titleOf(id) })),
        preferences().menu, (value) => setPreference('menu', value), renderMenu));
    renderMenu();
  } else if (pane === 'home') {
    const tabs = h('select.setting-select', { 'aria-label': t('homeRows'), onChange: loadRows },
      SECTIONS.map((id) => h('option', { value: id }, titleOf(id))));
    const list = h('div.settings-rows');
    content.replaceChildren(h('label.setting-row', h('span.setting-label', t('page')), h('span.setting-value', tabs, icon('chevron', 'setting-updown'))), list);
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
    h('ul.reorder-list', order.map((id, index) => h('li.setting-row.reorder-row',
      h('label.check-field', h('input.switch', { type: 'checkbox', role: 'switch', checked: !hidden.has(id), onChange: (event) => {
        if (event.target.checked) hidden.delete(id); else hidden.add(id);
        save({ order, hidden: [...hidden] });
      } }), h('span', byId.get(id))),
      h('div.row-order',
        h('button.btn-text', { type: 'button', disabled: index === 0, onClick: () => swap(index, index - 1), 'aria-label': t('up') + ' · ' + byId.get(id) }, '↑'),
        h('button.btn-text', { type: 'button', disabled: index === order.length - 1, onClick: () => swap(index, index + 1), 'aria-label': t('down') + ' · ' + byId.get(id) }, '↓'))))),
    h('div.page-actions', secondaryButton(t('resetSettings'), () => { save({}); draw(); })));
}
