import { api } from '../api.js';
import { t } from '../i18n.js';
import { currentProfile, myList, pull } from '../store.js';
import { empty, h, img, posterCard, problem, secondaryButton, setTitle, skeletonLines } from '../ui.js';

export async function render({ root, params, query, signal }) {
  const { section, collection, row, family, id } = params;
  const label = query.get('label')?.slice(0, 100) || t(section === 'series' ? 'seriesNav' : section === 'releases' ? 'releaseCalendar' : section || 'explore');
  const title = h('h1.page-title', label);
  const content = h('div.library-content');
  const grid = h('div.grid');
  const footer = h('div.page-actions');
  const listed = new Set();
  let page = 1;
  let loading = false;
  setTitle(label);
  // A row opened without its name in the address (a shared link, a reload of an old one) takes it from the plan.
  if (row && !query.get('label')) {
    api.section(section, { signal }).then((plan) => {
      const named = plan.find((entry) => entry.id === row)?.title;
      if (named && !signal.aborted) {
        title.textContent = named;
        setTitle(named);
      }
    }).catch(() => {});
  }
  root.append(h('div.page', title, content, footer));
  content.append(grid, skeletonLines(3));

  const collectionCard = (item) => h('a.card.card-poster.collection-card', { href: `#/collections/${item.id}` },
    h('div.art.art-poster', img(item.poster, ''), item.poster ? null : h('span.collection-name', item.title)),
    h('div.card-text', h('div.card-title', item.title)));

  async function load() {
    if (loading || signal.aborted) return;
    loading = true;
    footer.replaceChildren();
    try {
      let result;
      if (section === 'myList') {
        await pull();
        result = { items: myList() };
      } else if (collection) {
        result = await api.collection(collection, { signal });
      } else if (section === 'collections') {
        result = await api.collections(page, { signal });
      } else if (row) {
        result = await api.sectionRow(section, row, { page, profile: currentProfile()?.id }, { signal });
      } else if (family) {
        result = await api.shelf(family, id, page, { signal });
      } else if (section === 'manga') {
        result = await api.animationSearch({ kind: 'manga', page }, { signal });
      } else if (section === 'releases') {
        result = await api.upcoming(page, { signal });
      } else {
        result = await api.browse(section, page, { signal });
      }
      if (signal.aborted) return;
      if (result.title) {
        title.textContent = result.title;
        setTitle(result.title);
      }
      for (const item of result.items || []) {
        const key = `${item.kind || 'collection'}:${item.id}`;
        if (listed.has(key)) continue;
        listed.add(key);
        const card = section === 'collections' && !collection ? collectionCard(item) : posterCard(item, true);
        if (section === 'releases' && item.date) {
          card.querySelector('.card-text')?.append(h('time.card-sub', { datetime: item.date }, item.date));
        }
        grid.append(card);
      }
      content.replaceChildren(listed.size ? grid : empty(t(section === 'myList' ? 'listEmpty' : 'homeEmpty')));
      if (result.more) {
        page++;
        footer.append(secondaryButton(t('loadMore'), load));
      }
    } catch (error) {
      if (!signal.aborted) {
        const message = problem(error, load);
        if (listed.size) footer.replaceChildren(message);
        else content.replaceChildren(message);
      }
    } finally {
      loading = false;
    }
  }

  await load();
}
