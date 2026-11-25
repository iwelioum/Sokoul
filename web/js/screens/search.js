// Search: one field, results as a poster grid; debounced, the query kept in the address so Back returns to it.

import { api } from '../api.js';
import { t } from '../i18n.js';
import { h, icon, posterCard, problem, empty, setTitle } from '../ui.js';

const DEBOUNCE_MS = 300;

export function render({ root, query, signal }) {
  setTitle(t('search'));
  const input = h('input.field.search-input', {
    type: 'search', inputmode: 'search', enterkeyhint: 'search', autocomplete: 'off', autocorrect: 'off',
    autocapitalize: 'none', spellcheck: 'false', placeholder: t('searchPlaceholder'), 'aria-label': t('search'),
    maxlength: 200,
  });
  const results = h('div.search-results', { 'aria-live': 'polite' });
  root.append(h('div.page',
    h('h1.page-title', t('search')),
    h('form.search-box', { role: 'search', onSubmit: (e) => { e.preventDefault(); input.blur(); } },
      icon('search', 'search-icon'), input),
    results));

  let timer = 0;
  let controller = null;

  const skeleton = () => h('div.grid', { 'aria-hidden': 'true' },
    Array.from({ length: 12 }, () => h('div.card.card-poster', h('div.art.art-poster.skeleton'))));

  const run = async (text) => {
    controller?.abort();
    const q = text.trim();
    history.replaceState(history.state, '', q ? `#/search?q=${encodeURIComponent(q)}` : '#/search');
    if (!q) {
      results.replaceChildren(empty(t('searchHint')));
      return;
    }
    controller = new AbortController();
    signal.addEventListener('abort', () => controller.abort(), { once: true });
    results.replaceChildren(skeleton());
    try {
      const body = await api.search(q, { signal: controller.signal });
      const items = body.items || [];
      results.replaceChildren(items.length
        ? h('div.grid', items.map((i) => posterCard(i, true)))
        : empty(t('searchNone', { q })));
    } catch (e) {
      if (e.name === 'AbortError') return;
      results.replaceChildren(problem(e, () => run(input.value)));
    }
  };

  input.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => run(input.value), DEBOUNCE_MS);
  });

  const initial = query.get('q') || '';
  input.value = initial;
  run(initial);
  if (!initial && window.matchMedia('(pointer: fine)').matches) input.focus();

  return () => {
    clearTimeout(timer);
    controller?.abort();
  };
}
