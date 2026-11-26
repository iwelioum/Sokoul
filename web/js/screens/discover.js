import { api } from '../api.js';
import { t, shortDate } from '../i18n.js';
import * as store from '../store.js';
import { continueCard } from './home.js';
import { h, img, empty, problem, posterCard, secondaryButton, setTitle, skeletonLines } from '../ui.js';

export async function render({ root, params, signal }) {
  const section = params.section;
  const title = t(section === 'pick' ? 'pickForMe' : section === 'anime-calendar' ? 'animeCalendar' : section || 'person');
  const heading = h('h1.page-title', title);
  const region = h('div.explore-region', skeletonLines(3));
  root.append(h('div.page', heading, region));
  setTitle(title);

  async function load() {
    try {
      if (section === 'history') await history();
      else if (section === 'pick') await pick();
      else if (section === 'anime-calendar') await calendar();
      else await person();
    } catch (error) {
      if (!signal.aborted) region.replaceChildren(problem(error, load));
    }
  }

  async function history() {
    await store.pull();
    if (signal.aborted) return;
    const records = store.viewingHistory();
    if (!records.length) {
      region.replaceChildren(empty(t('historyEmpty')));
      return;
    }
    const status = h('div', { role: 'status' });
    let busy = false;
    async function forget(entries) {
      if (busy) return;
      busy = true;
      try {
        await store.forgetHistory(entries);
        if (!signal.aborted) await history();
      } catch (error) {
        if (!signal.aborted) status.replaceChildren(problem(error, () => forget(entries)));
      } finally {
        busy = false;
      }
    }
    region.replaceChildren(
      h('div.page-actions', secondaryButton(t('clearHistory'), () => {
        if (window.confirm(t('confirmClearHistory'))) forget(records);
      })),
      status,
      h('div.history-grid', records.map((record) => h('article.history-entry',
        continueCard(record),
        h('div.history-meta', h('time.card-sub', { datetime: new Date(record.updatedAt).toISOString() }, shortDate(record.updatedAt)),
          h('button.btn-text', { type: 'button', onClick: () => forget([record]) }, t('forgetTitle')))))));
  }

  async function pick() {
    const response = await api.sectionRow('home', 'top-movie', { profile: store.currentProfile()?.id }, { signal });
    if (signal.aborted) return;
    const items = response.items.slice().sort(() => Math.random() - .5);
    if (!items.length) {
      region.replaceChildren(empty(t('homeEmpty')));
      return;
    }
    const card = h('div.pick-card');
    let index = 0;
    const show = () => card.replaceChildren(posterCard(items[index++ % items.length], true));
    show();
    region.replaceChildren(card, h('div.page-actions', secondaryButton(t('anotherTitle'), show)));
  }

  async function calendar() {
    const items = await api.airingCalendar({ signal });
    if (signal.aborted) return;
    const clock = new Intl.DateTimeFormat(document.documentElement.lang, { weekday: 'long', month: 'short', day: 'numeric' });
    const time = new Intl.DateTimeFormat(document.documentElement.lang, { hour: 'numeric', minute: '2-digit' });
    const grouped = new Map();
    for (const item of items) {
      const label = clock.format(item.at);
      if (!grouped.has(label)) grouped.set(label, []);
      grouped.get(label).push(item);
    }
    region.replaceChildren(...(grouped.size ? [...grouped].map(([label, entries]) => h('section.calendar-day',
      h('h2.section-title', label), h('ul.list', entries.map((entry) => h('li',
        h('a.list-row.list-link', { href: '#/work/' + entry.item.id },
          h('time.list-detail', { datetime: new Date(entry.at).toISOString() }, time.format(entry.at)),
          h('div.list-main', h('div.list-label', entry.item.title), h('div.list-detail', t('episodeNumber', { n: entry.episode }))))))))) :
      [empty(t('homeEmpty'))]));
  }

  async function person() {
    const details = await api.person(params.id, { signal });
    if (signal.aborted) return;
    heading.textContent = details.name;
    setTitle(details.name);
    region.replaceChildren(
      h('div.person-profile', details.photo ? img(details.photo, '', 'person-profile-photo') : null,
        h('div', details.knownFor ? h('p.lead', details.knownFor) : null,
          details.biography ? h('p.synopsis', details.biography) : null)),
      details.titles?.length ? h('section', h('h2.section-title', t('filmography')),
        h('div.grid', details.titles.map((item) => posterCard(item, true)))) : null);
  }

  await load();
}
