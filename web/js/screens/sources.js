// Sources et clés: what the account's vault holds, read-only (masks and verdicts; keys never come back).
// Keys are entered from the PC or TV app for now.

import { api } from '../api.js';
import { t, shortDate } from '../i18n.js';
import { h, problem, skeletonLines, serviceName, SERVICE_ORDER, setTitle } from '../ui.js';

/** What each service adds, in plain words. */
const ADDS = {
  fr: {
    tmdb: 'Le catalogue, les affiches et les fiches.',
    realdebrid: 'Les liens rapides vers les films et les épisodes.',
    iptv: 'Vos chaînes et la VOD de votre abonnement.',
    omdb: 'Des informations en plus sur les films.',
    fanart: 'Des logos et des images de meilleure qualité.',
    tvdb: 'Les épisodes des séries peu connues.',
    watchmode: 'Où un titre se regarde.',
    tastedive: 'Des suggestions proches de ce que vous aimez.',
    motn: 'Les plateformes qui proposent un titre.',
    opensubtitles: 'Des sous-titres.',
    subdl: 'D’autres sous-titres.',
  },
  en: {
    tmdb: 'The catalogue, posters and title pages.',
    realdebrid: 'Fast links to films and episodes.',
    iptv: 'Your subscription’s channels and VOD.',
    omdb: 'More information about films.',
    fanart: 'Logos and better pictures.',
    tvdb: 'Episodes of lesser-known series.',
    watchmode: 'Where a title can be watched.',
    tastedive: 'Suggestions close to what you like.',
    motn: 'Which services offer a title.',
    opensubtitles: 'Subtitles.',
    subdl: 'More subtitles.',
  },
};

function state(cred) {
  if (!cred) return t('notSet');
  if (cred.verdict === 'accepted') return t('verdictAccepted', { date: shortDate(cred.checkedAt) });
  if (cred.verdict === 'refused') return t('verdictRefused');
  return t('verdictUnreachable', { date: shortDate(cred.checkedAt) });
}

export async function render({ root, signal }) {
  setTitle(t('sources'));
  const list = h('div.list-slot', skeletonLines(6, 'list-skeleton'));
  root.append(h('div.page.page-narrow',
    h('h1.page-title', t('sources')),
    h('p.lead', t('sourcesIntro')),
    list));

  const load = async () => {
    let creds;
    try {
      creds = await api.credentials();
    } catch (e) {
      if (e.name === 'AbortError') return;
      list.replaceChildren(problem(e, load));
      return;
    }
    if (signal.aborted) return;
    const byService = new Map((Array.isArray(creds) ? creds : []).map((c) => [c.service, c]));
    const ids = [...SERVICE_ORDER, ...[...byService.keys()].filter((s) => !SERVICE_ORDER.includes(s))];
    const words = ADDS[document.documentElement.lang === 'en' ? 'en' : 'fr'];
    const rows = ids.map((id) => {
      const c = byService.get(id);
      return h('li.list-row', { class: c ? (c.verdict === 'accepted' ? 'is-set' : 'is-problem') : 'is-unset' },
        h('div.list-main',
          h('div.list-label', serviceName(id)),
          words[id] ? h('div.list-detail', words[id]) : null),
        h('div.list-value',
          c?.hint ? h('div.list-hint', c.hint) : null,
          h('div.list-state', state(c))));
    });
    list.replaceChildren(h('ul.list', rows));
  };
  await load();
}
