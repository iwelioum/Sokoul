import { api } from '../api.js';
import { t } from '../i18n.js';
import { attachMedia } from '../media.js';
import { currentProfile, user } from '../store.js';
import { empty, h, icon, img, problem, secondaryButton, setTitle, skeletonLines } from '../ui.js';

export function render({ root, params, query, signal }) {
  const sports = params.section === 'sports';
  const label = t(params.section);
  const player = h('section.live-player', { hidden: true });
  const name = h('h2.section-title');
  const video = h('video.live-video', { controls: true, playsinline: true, preload: 'none' });
  const playerStatus = h('div', { role: 'status' });
  const guide = h('div.live-guide');
  let hls = null;
  let selected = null;
  let streamId = null;
  let vodProgress = null;

  function saveProgress() {
    if (!vodProgress || !Number.isFinite(video.duration) || video.currentTime <= 2) return;
    try {
      if (video.currentTime >= video.duration - 15) localStorage.removeItem(vodProgress);
      else localStorage.setItem(vodProgress, String(video.currentTime));
    } catch { /* storage is optional */ }
  }

  function stop() {
    saveProgress();
    vodProgress = null;
    selected?.abort();
    selected = null;
    hls?.destroy();
    hls = null;
    if (streamId) api.stopLive(streamId).catch(() => {});
    streamId = null;
    video.removeAttribute('src');
    video.load();
    player.hidden = true;
  }
  player.append(h('div.live-player-heading', name, secondaryButton(t('close'), stop, 'close')),
    video, playerStatus, guide);

  async function open(title, start, guideId, progress) {
    stop();
    if (signal.aborted) return;
    selected = new AbortController();
    const request = selected;
    const current = () => selected === request && !request.signal.aborted;
    const opts = { signal: request.signal };
    name.textContent = title;
    vodProgress = progress ? `sokoul.vod.${user()?.id}.${currentProfile()?.id}.${progress}` : null;
    playerStatus.replaceChildren(skeletonLines(1));
    guide.replaceChildren();
    player.hidden = false;
    player.scrollIntoView({ block: 'nearest' });

    if (guideId) api.guide(guideId, opts).then((programs) => {
      if (!current() || !programs.length) return;
      const clock = new Intl.DateTimeFormat(document.documentElement.lang, { hour: 'numeric', minute: '2-digit' });
      guide.replaceChildren(h('ol.list', programs.map((program) => h('li.list-row',
        h('time.list-detail', { datetime: new Date(program.start).toISOString() }, clock.format(program.start)),
        h('div.list-main', h('div.list-label', program.title),
          program.description ? h('div.list-detail', program.description) : null)))));
    }).catch(() => {});

    try {
      const started = await start(opts);
      if (!current()) return;
      streamId = started.url.match(/^\/api\/stream\/([\w-]+)\//)?.[1] || null;
      const instance = await attachMedia(video, started, current,
        () => playerStatus.replaceChildren(empty(t('channelUnavailable'))));
      if (!current()) {
        instance?.destroy();
        return;
      }
      hls = instance;
      playerStatus.replaceChildren();
      video.play().catch(() => {});
    } catch (error) {
      if (current()) playerStatus.replaceChildren(problem(error, () => open(title, start, guideId, progress)));
    }
  }
  video.addEventListener('error', () => {
    if (selected && video.getAttribute('src')) playerStatus.replaceChildren(empty(t('channelUnavailable')));
  });
  video.addEventListener('pause', saveProgress);
  video.addEventListener('loadedmetadata', () => {
    if (!vodProgress || !Number.isFinite(video.duration)) return;
    let from = 0;
    try { from = Number(localStorage.getItem(vodProgress)); } catch { return; }
    if (from > 2 && from < video.duration - 10) video.currentTime = from;
  });

  const vod = params.iptvKind;
  const pageLabel = vod ? t(vod === 'movies' ? 'iptvMovies' : 'iptvSeries') :
    params.archiveChannel ? t('replay') : label;
  const page = h('div.page', h('h1.page-title', pageLabel),
    !sports && !params.archiveChannel ? h('div.segmented-wrap', h('nav.segmented.live-tabs', { 'aria-label': t('tv') },
      [['', 'channelsTv'], ['/vod/movies', 'iptvMovies'], ['/vod/series', 'iptvSeries']].map(([path, item]) =>
        h('a.segment', { href: '#/tv' + path, 'aria-current': (vod ? path.endsWith('/' + vod) : !path) ? 'page' : null }, t(item))))) : null,
    player);
  root.append(page);
  setTitle(pageLabel);
  if (vod) {
    page.append(vodSection(vod, params.iptvId, signal, open));
    signal.addEventListener('abort', stop, { once: true });
    return;
  }
  if (params.archiveChannel) {
    page.append(replaySection(params.archiveChannel, query?.get('label'), signal, open));
    signal.addEventListener('abort', stop, { once: true });
    return;
  }
  if (sports) page.append(sportsSection(signal, open), scoresSection(signal));
  page.append(channelSection(signal, sports, open, query));
  signal.addEventListener('abort', stop, { once: true });
}

function replaySection(id, title, signal, open) {
  const section = h('section.live-section', title ? h('h2.section-title', title) : null);
  const list = h('div', skeletonLines(3));
  section.append(list);
  async function load() {
    try {
      const programs = await api.replay(id, { signal });
      if (signal.aborted) return;
      const format = new Intl.DateTimeFormat(document.documentElement.lang, { dateStyle: 'short', timeStyle: 'short' });
      list.replaceChildren(programs.length ? h('ul.list', programs.map((program) => h('li',
        h('button.list-row.list-link.live-row', { type: 'button', onClick: () => {
          open(program.title, (opts) => api.startReplay(id, program.start, opts), null, id + ':' + program.start);
        } }, icon('play'), h('div.list-main', h('div.list-label', program.title),
          h('div.list-detail', format.format(program.start))))))) : empty(t('guideEmpty')));
    } catch (error) {
      if (!signal.aborted) list.replaceChildren(problem(error, load));
    }
  }
  load();
  return section;
}

function vodSection(kind, id, signal, open) {
  const block = h('section.live-section');
  const status = h('div', skeletonLines(3));
  if (id) {
    block.append(status);
    async function loadTitle() {
      try {
        const detail = await api.iptvTitle(kind, id, { signal });
        if (signal.aborted) return;
        const { title, episodes, overview } = detail;
        const playable = (episode) => open(episode?.title || title.title,
          (opts) => api.startIptvTitle(kind, id, episode?.id, opts), null, [kind, id, episode?.id].filter(Boolean).join('.'));
        const groups = new Map();
        for (const episode of episodes) {
          if (!groups.has(episode.season)) groups.set(episode.season, []);
          groups.get(episode.season).push(episode);
        }
        status.replaceChildren(h('h2.section-title', title.title),
          overview ? h('p.synopsis', overview) : null,
          kind === 'movies' ? h('div.page-actions', secondaryButton(t('play'), () => playable(), 'play')) : null,
          [...groups].map(([season, list]) => h('section.calendar-day', h('h3', t('season', { n: season })),
            h('ul.list', list.map((episode) => h('li',
              h('button.list-row.list-link.live-row', { type: 'button', onClick: () => playable(episode) },
                icon('play'), h('div.list-main', h('div.list-label', t('episodeTitle', { n: episode.number, name: episode.title })),
                  episode.overview ? h('div.list-detail', episode.overview) : null))))))),
          kind === 'series' && !groups.size ? empty(t('noEpisodes')) : null);
      } catch (error) {
        if (!signal.aborted) status.replaceChildren(problem(error, loadTitle));
      }
    }
    loadTitle();
    return block;
  }
  const search = h('input.field', { type: 'search', maxlength: 100, placeholder: t('search'), 'aria-label': t('search') });
  const groups = h('select.field', { 'aria-label': t('allChannels') }, h('option', { value: '' }, t('allChannels')));
  const grid = h('div.grid');
  const footer = h('div.page-actions');
  block.append(h('div.live-filters', search, groups), grid, status, footer);
  let page = 1;
  let timer;
  let loading;
  async function load(reset = false) {
    loading?.abort();
    if (signal.aborted) return;
    if (reset) {
      page = 1;
      grid.replaceChildren();
    }
    const request = new AbortController();
    loading = request;
    footer.replaceChildren();
    status.replaceChildren(skeletonLines(1));
    try {
      const selected = groups.value;
      const result = await api.iptvVod(kind, { q: search.value.trim(), group: selected, page }, { signal: request.signal });
      if (request.signal.aborted) return;
      groups.replaceChildren(h('option', { value: '' }, t('allChannels')),
        result.groups.map((group) => h('option', { value: group, selected: group === selected }, group)));
      grid.append(...result.items.map((item) => h('a.card.card-poster.collection-card', { href: '#/tv/vod/' + kind + '/' + item.id },
        h('div.art.art-poster', item.poster ? img(item.poster, '') : h('span.collection-name', item.title)),
        h('div.card-text', h('div.card-title', item.title), h('div.card-sub', [item.year, item.group].filter(Boolean).join(' · '))))));
      status.replaceChildren(...(grid.childElementCount ? [] : [empty(t('liveEmpty'))]));
      if (result.more) footer.append(secondaryButton(t('loadMore'), () => { page++; load(); }));
    } catch (error) {
      if (!request.signal.aborted) status.replaceChildren(problem(error, () => load()));
    }
  }
  search.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => load(true), 300);
  });
  groups.addEventListener('change', () => {
    clearTimeout(timer);
    load(true);
  });
  signal.addEventListener('abort', () => { clearTimeout(timer); loading?.abort(); }, { once: true });
  load();
  return block;
}

function scoresSection(signal) {
  const content = h('div', skeletonLines(3));
  const section = h('section.live-section', h('h2.section-title', t('scoresAndSchedule')), content);
  async function load() {
    try {
      const matches = await api.sportsScores({ signal });
      if (signal.aborted) return;
      const date = new Intl.DateTimeFormat(document.documentElement.lang, { dateStyle: 'short', timeStyle: 'short' });
      const rows = matches.filter((event) => event.state !== 'Off').map((event) => {
        const score = event.state === 'Live' || event.state === 'Finished' ?
          [event.homeScore, event.awayScore].every((value) => value != null) ? `${event.homeScore} – ${event.awayScore}` : null : null;
        const broadcast = event.broadcasters?.join(', ');
        return h('li.list-row.live-row',
          h('div.list-main', h('div.list-label', event.title),
            h('div.list-detail', [event.league, date.format(event.start)].join(' · ')),
            broadcast ? h('div.list-detail', event.usualBroadcasts ? t('usualBroadcasters', { names: broadcast }) : broadcast) : null),
          h('div.score-result', score, event.state === 'Live' ? h('div.list-detail', event.clock || t('onAir')) : null));
      });
      content.replaceChildren(rows.length ? h('ul.list', rows) : empty(t('noScheduledGames')));
    } catch (error) {
      if (!signal.aborted) content.replaceChildren(problem(error, load));
    }
  }
  load();
  return section;
}

function channelSection(signal, sports, open, query) {
  const search = h('input.field', { type: 'search', maxlength: 100, placeholder: t('searchChannels'), 'aria-label': t('searchChannels') });
  const groups = h('select.field', { 'aria-label': t('allChannels') }, h('option', { value: '' }, t('allChannels')));
  const filters = h('div.live-filters', { hidden: true }, search, groups);
  const rows = h('ul.list');
  const status = h('div', skeletonLines(4));
  const tabs = h('div.segmented.live-tabs', { hidden: true, 'aria-label': t('channelsTv') });
  const guideBlock = h('div.live-schedule', { hidden: true });
  const section = h('section.live-section', sports ? h('h2.section-title', t('sportChannel')) : null,
    h('div.segmented-wrap', tabs), filters, rows, guideBlock, status);
  const organization = h('details.live-organization', { hidden: true });
  if (!sports) filters.after(organization);
  let active;
  let guideRequest;
  let timer;
  let page = 1;
  let view = 'channels';
  let collectionId = '';
  let guideCursor = 0;
  const channels = [];
  const favKey = `sokoul.tv.${user()?.id}.${currentProfile()?.id}`;
  const readList = (key) => {
    try { return JSON.parse(localStorage.getItem(key)) || []; } catch { return []; }
  };
  const saveList = (key, entries) => {
    try { localStorage.setItem(key, JSON.stringify(entries)); } catch { /* storage is optional */ }
  };
  const recentKey = favKey + '.recent';
  const collectionsKey = favKey + '.collections';
  const favorites = new Set(readList(favKey));
  const recent = readList(recentKey);
  const collections = readList(collectionsKey);
  const collectionPicker = h('select.field', { 'aria-label': t('manageTvCollections') });
  const collectionName = h('input.field', { required: true, maxlength: 60, placeholder: t('name'), 'aria-label': t('name') });
  const collectionNotice = h('p.list-detail', { role: 'status' });
  const removeCollection = secondaryButton(t('deleteTvCollection'), () => {
    const collection = collections.find(({ id }) => id === collectionPicker.value);
    if (!collection || !window.confirm(t('confirmTvCollection', { name: collection.name }))) return;
    collections.splice(collections.indexOf(collection), 1);
    if (collectionId === collection.id) {
      collectionId = '';
      groups.value = '';
    }
    saveList(collectionsKey, collections);
    updateCollections();
    load(true);
  });
  const collectionForm = h('form.form', { onSubmit: (event) => {
    event.preventDefault();
    const name = collectionName.value.trim();
    if (!name) return;
    const existing = collections.find(({ id }) => id === collectionPicker.value);
    if (existing) existing.name = name;
    else {
      if (collections.length >= 24) {
        collectionNotice.textContent = t('tvCollectionsLimit');
        return;
      }
      collections.push({ id: String(Date.now()), name, channels: [] });
    }
    saveList(collectionsKey, collections);
    updateCollections(existing?.id || collections.at(-1).id);
    load(true);
  } }, h('div.live-filters', collectionPicker, collectionName),
  h('div.form-actions', h('button.btn-secondary', { type: 'submit' }, t('save')), removeCollection), collectionNotice);
  organization.append(h('summary.btn-text', t('manageTvCollections')), collectionForm);

  function updateCollections(selected = '') {
    collectionPicker.replaceChildren(h('option', { value: '' }, t('newTvCollection')),
      collections.map(({ id, name }) => h('option', { value: id }, name)));
    collectionPicker.value = selected;
    collectionPicker.dispatchEvent(new Event('change'));
  }
  collectionPicker.addEventListener('change', () => {
    const collection = collections.find(({ id }) => id === collectionPicker.value);
    collectionName.value = collection?.name || '';
    removeCollection.hidden = !collection;
    collectionNotice.textContent = '';
  });
  updateCollections();
  const openChannel = (channel) => open(channel.name, async (opts) => {
    const started = await api.startChannel(channel.id, opts);
    const old = recent.indexOf(channel.id);
    if (old >= 0) recent.splice(old, 1);
    recent.unshift(channel.id);
    recent.splice(30);
    saveList(recentKey, recent);
    return started;
  }, channel.id);
  const views = [['channels', 'channelsTv'], ['favorites', 'favorites']];
  if (!sports) views.push(['recent', 'recentChannels']);
  views.push(['guide', 'tvGuide']);
  for (const [id, label] of views) {
    tabs.append(h('button.segment', { type: 'button', 'data-live-view': id, onClick: () => {
      clearTimeout(timer);
      view = id;
      load(true);
    } }, t(label)));
  }
  let initial = query?.get('channel');
  search.value = query?.get('q')?.slice(0, 100) || '';

  async function load(reset = false) {
    active?.abort();
    guideRequest?.abort();
    if (signal.aborted) return;
    if (reset) {
      page = 1;
      rows.replaceChildren();
      guideBlock.replaceChildren();
      guideCursor = 0;
      channels.length = 0;
    }
    for (const tab of tabs.children) tab.setAttribute('aria-pressed', String(tab.dataset.liveView === view));
    guideBlock.hidden = view !== 'guide';
    rows.hidden = view === 'guide';
    const request = new AbortController();
    active = request;
    status.replaceChildren(skeletonLines(1));
    try {
      const personal = collections.find(({ id }) => id === collectionId);
      const ids = view === 'favorites' ? [...favorites].filter((id) => !personal || personal.channels.includes(id)) :
        view === 'recent' ? recent.filter((id) => !personal || personal.channels.includes(id)) : personal?.channels;
      const group = collectionId ? '' : groups.value;
      const result = await api.channels({ page, sports, group, q: search.value.trim(),
        ids: ids ? ids.slice(0, 100).join(',') || '-' : undefined }, { signal: request.signal });
      if (request.signal.aborted) return;
      const found = result.items.find((channel) => channel.id === initial);
      initial = null;
      if (found) openChannel(found);
      groups.replaceChildren(h('option', { value: '' }, t('allChannels')),
        sports || !collections.length ? null : h('optgroup', { label: t('tvCollections') },
          collections.map(({ id, name }) => h('option', { value: 'personal:' + id }, name))),
        result.groups.filter(Boolean).map((name) => h('option', { value: name }, name)));
      groups.value = personal ? 'personal:' + personal.id : result.groups.includes(group) ? group : '';
      filters.hidden = false;
      tabs.hidden = false;
      organization.hidden = false;
      if (view === 'recent') result.items.sort((a, b) => recent.indexOf(a.id) - recent.indexOf(b.id));
      channels.push(...result.items);
      if (view === 'guide') {
        await drawGuide();
        status.replaceChildren(...(result.more ? [secondaryButton(t('loadMore'), () => { page++; load(); })] : []));
        return;
      }
      rows.append(...result.items.map((channel) => {
        const star = h('button.btn-round.live-star', { type: 'button', 'aria-label': t('favorites'),
          'aria-pressed': String(favorites.has(channel.id)), onClick: () => {
            if (favorites.has(channel.id)) favorites.delete(channel.id);
            else favorites.add(channel.id);
            saveList(favKey, [...favorites]);
            star.setAttribute('aria-pressed', String(favorites.has(channel.id)));
            if (view === 'favorites') load(true);
          } }, icon('star'));
        return h('li.live-channel-item',
        h('button.list-row.list-link.live-row', { type: 'button', onClick: () => openChannel(channel) },
        icon('play'), h('div.list-main', h('div.list-label', channel.name),
          channel.group ? h('div.list-detail', channel.group) : null)),
        channel.archiveDays ? h('a.btn-text.live-replay', {
          href: '#/tv/replay/' + channel.id + '?label=' + encodeURIComponent(channel.name),
        }, t('replay')) : null, star, !sports && collections.length ? h('select.field.live-collection-channel', {
          'aria-label': t('channelCollections', { name: channel.name }),
          onChange: (event) => {
            const select = event.target;
            const option = select.selectedOptions[0];
            const collection = collections.find(({ id }) => id === select.value);
            select.value = '';
            if (!collection) return;
            const index = collection.channels.indexOf(channel.id);
            if (index >= 0) collection.channels.splice(index, 1);
            else {
              if (collection.channels.length >= 100) {
                select.options[0].textContent = t('tvCollectionFull');
                return;
              }
              collection.channels.push(channel.id);
            }
            select.options[0].textContent = t('tvCollections');
            saveList(collectionsKey, collections);
            if (personal) load(true);
            else option.textContent = t(index >= 0 ? 'addTvCollection' : 'removeTvCollection', { name: collection.name });
          },
        }, h('option', { value: '' }, t('tvCollections')),
        collections.map(({ id, name, channels }) =>
          h('option', { value: id }, t(channels.includes(channel.id) ? 'removeTvCollection' : 'addTvCollection', { name })))) : null);
      }));
      status.replaceChildren(...(result.more ? [secondaryButton(t('loadMore'), () => { page++; load(); })] :
        rows.childElementCount ? [] : [empty(t('liveEmpty'))]));
    } catch (error) {
      if (!request.signal.aborted) status.replaceChildren(problem(error, () => load()));
    }
  }

  async function drawGuide() {
    guideRequest?.abort();
    const request = new AbortController();
    guideRequest = request;
    const batch = channels.slice(guideCursor, guideCursor + 8);
    if (!batch.length) {
      if (!guideBlock.childElementCount) guideBlock.append(empty(t('liveEmpty')));
      return;
    }
    const previous = guideBlock.querySelector('.guide-more');
    previous?.remove();
    const slot = h('div.guide-page', skeletonLines(1));
    guideBlock.append(slot);
    try {
      const rows = await api.guideRows(batch.map((channel) => channel.id), { signal: request.signal });
      if (request.signal.aborted || signal.aborted) return;
      const clock = new Intl.DateTimeFormat(document.documentElement.lang, { hour: 'numeric', minute: '2-digit' });
      const now = Date.now();
      slot.replaceChildren(...rows.map(({ channel, programs }) => h('article.guide-channel',
        h('h3', h('button.btn-text', { type: 'button', onClick: () => openChannel(channel) }, channel.name),
          channel.archiveDays ? h('a.btn-text.live-replay', { href: '#/tv/replay/' + channel.id + '?label=' + encodeURIComponent(channel.name) }, t('replay')) : null),
        programs.length ? h('div.guide-timeline', programs.map((program) => h('div.guide-program', {
          class: program.start <= now && program.stop > now ? 'guide-now' : null,
        }, h('time.list-detail', { datetime: new Date(program.start).toISOString() }, clock.format(program.start)),
        h('strong', program.title)))) : h('p.list-detail', t('guideEmpty')))));
      guideCursor += batch.length;
      if (guideCursor < channels.length) {
        guideBlock.append(h('div.guide-more.page-actions', secondaryButton(t('loadMore'), drawGuide)));
      }
    } catch (error) {
      if (!request.signal.aborted) slot.replaceChildren(problem(error, () => { slot.remove(); drawGuide(); }));
    }
  }

  search.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => load(true), 250);
  });
  groups.addEventListener('change', () => {
    clearTimeout(timer);
    collectionId = collections.find(({ id }) => groups.value === 'personal:' + id)?.id || '';
    load(true);
  });
  signal.addEventListener('abort', () => { active?.abort(); guideRequest?.abort(); clearTimeout(timer); }, { once: true });
  load();
  return section;
}

function sportsSection(signal, open) {
  const events = h('ul.list');
  const choices = h('div.sport-choices');
  const status = h('div', skeletonLines(4));
  const section = h('section.live-section', h('h2.section-title', t('liveToday')), events, choices, status);
  let selection;
  signal.addEventListener('abort', () => selection?.abort(), { once: true });

  async function servers(event) {
    selection?.abort();
    const request = new AbortController();
    selection = request;
    choices.replaceChildren(h('h3', event.name), skeletonLines(1));
    choices.scrollIntoView({ block: 'nearest' });
    try {
      const result = await api.sportsServers(event.id, { signal: request.signal });
      if (request.signal.aborted) return;
      choices.replaceChildren(h('h3', event.name),
        result.length ? h('div.form-actions', result.map((server) =>
          secondaryButton(server.title, () => open(event.name, (opts) => api.startSport(event.id, server.key, opts), null), 'play'))) :
          empty(t('noVersions')));
    } catch (error) {
      if (!request.signal.aborted) choices.replaceChildren(h('h3', event.name), problem(error, () => servers(event)));
    }
  }

  async function load() {
    try {
      const result = await api.sportsEvents({ signal });
      if (signal.aborted) return;
      const date = new Intl.DateTimeFormat(document.documentElement.lang, { dateStyle: 'short', timeStyle: 'short' });
      events.replaceChildren(...result.map((event) => h('li',
        h('button.list-row.list-link.live-row', { type: 'button', onClick: () => servers(event) },
          icon('trophy'), h('div.list-main', h('div.list-label', event.name), event.sport ? h('div.list-detail', event.sport) : null),
          h('div.list-detail', event.live ? t('onAir') : event.startsAt ? date.format(event.startsAt) : null)))));
      status.replaceChildren(...(result.length ? [] : [empty(t('liveEmpty'))]));
    } catch (error) {
      if (!signal.aborted) status.replaceChildren(problem(error, load));
    }
  }
  load();
  return section;
}
