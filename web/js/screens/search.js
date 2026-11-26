import { api } from '../api.js';
import { t } from '../i18n.js';
import { h, icon, posterCard, personCard, problem, empty, setTitle, skeletonLines, secondaryButton } from '../ui.js';

const DEBOUNCE_MS = 300;
const TABS = [['all', 'allResults'], ['movie', 'movies'], ['tv', 'seriesNav'], ['person', 'people'],
  ['anime', 'anime'], ['manga', 'manga'], ['live', 'channelsTv']];

export function render({ root, query, signal }) {
  setTitle(t('search'));
  let tab = TABS.some(([key]) => key === query.get('tab')) ? query.get('tab') : 'all';
  const input = h('input.field.search-input', {
    type: 'search', inputmode: 'search', enterkeyhint: 'search', autocomplete: 'off', autocorrect: 'off',
    autocapitalize: 'none', spellcheck: 'false', placeholder: t('searchPlaceholder'), 'aria-label': t('search'), maxlength: 100,
  });
  const results = h('div.search-results', { 'aria-live': 'polite' });
  const filters = h('div.segmented', { 'aria-label': t('search') });
  for (const [id, label] of TABS) {
    filters.append(h('button.segment', { type: 'button', 'data-search-tab': id,
      onClick: () => { clearTimeout(timer); tab = id; run(true); } }, t(label)));
  }
  const place = h('div.page', h('h1.page-title', t('search')),
    h('form.search-box', { role: 'search', onSubmit: (event) => {
      event.preventDefault();
      clearTimeout(timer);
      run(true);
      input.blur();
    } }, icon('search', 'search-icon'), input),
    h('div.segmented-wrap', filters), results);
  root.append(place);

  let timer;
  let controller;
  let currentPage = 1;
  const all = { titles: [], people: [], channels: [] };

  async function search(text, currentTab, page, options) {
    if (currentTab === 'anime' || currentTab === 'manga') {
      const response = await api.animationSearch({ q: text, kind: currentTab, page }, options);
      return { titles: response.items, more: response.more };
    }
    if (currentTab === 'live') {
      const response = await api.channels({ q: text, page }, options);
      return { channels: response.items, more: response.more };
    }
    const response = await api.search(text, { ...options, page, type: currentTab === 'all' ? undefined : currentTab });
    return { titles: response.items, people: response.people, more: response.more };
  }

  function draw(more, text) {
    const parts = [];
    if (all.titles.length) parts.push(h('div.grid', all.titles.map((item) => posterCard(item, true))));
    if (all.people.length) parts.push(h('section.search-people',
      h('h2.section-title', t('people')), h('div.grid.people-grid', all.people.map(personCard))));
    if (all.channels.length) parts.push(h('ul.list', all.channels.map((item) => {
      const params = new URLSearchParams({ channel: item.id, q: item.name });
      return h('li', h('a.list-row.list-link', { href: '#/tv?' + params },
        h('div.list-main', h('div.list-label', item.name), h('div.list-detail', item.group)), icon('chevron')));
    })));
    if (!parts.length) parts.push(empty(t('searchNone', { q: text })));
    if (more) parts.push(h('div.page-actions', secondaryButton(t('loadMore'), () => { currentPage++; run(false); })));
    results.replaceChildren(...parts);
  }

  async function run(reset) {
    controller?.abort();
    if (signal.aborted) return;
    controller = new AbortController();
    const request = controller;
    const text = input.value.trim();
    const currentTab = tab;
    for (const button of filters.children) button.setAttribute('aria-pressed', String(button.dataset.searchTab === tab));
    const params = new URLSearchParams();
    if (text) params.set('q', text);
    if (tab !== 'all') params.set('tab', tab);
    history.replaceState(history.state, '', '#/search' + (params.size ? '?' + params : ''));
    if (reset) {
      currentPage = 1;
      for (const key in all) all[key] = [];
      results.replaceChildren(skeletonLines(3));
    } else results.querySelector('.page-actions')?.remove();

    try {
      if (!text) {
        const plan = await api.section('home', { signal: request.signal });
        if (request.signal.aborted) return;
        const links = plan.filter((row) => row.links?.length).slice(0, 4).flatMap((row) => row.links.slice(0, 4));
        results.replaceChildren(h('h2.section-title', t('explore')), h('div.search-explore',
          links.map((link) => h('a.editorial-tile', { href: '#/' + link.route + '?label=' + encodeURIComponent(link.title) }, link.title))));
        return;
      }
      const response = await search(text, currentTab, currentPage, { signal: request.signal });
      if (request.signal.aborted) return;
      for (const key in all) {
        for (const item of response[key] || []) {
          if (!all[key].some((old) => old.id === item.id && old.kind === item.kind)) all[key].push(item);
        }
      }
      draw(response.more, text);
    } catch (error) {
      if (request.signal.aborted) return;
      if (reset) results.replaceChildren(problem(error, () => run(true)));
      else results.append(problem(error, () => run(false)));
    }
  }

  input.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => run(true), DEBOUNCE_MS);
  });
  signal.addEventListener('abort', () => { clearTimeout(timer); controller?.abort(); }, { once: true });
  input.value = query.get('q') || '';
  run(true);
  if (!input.value && window.matchMedia('(pointer: fine)').matches) input.focus();
}
