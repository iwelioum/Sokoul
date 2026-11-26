import { api } from '../api.js';
import { t } from '../i18n.js';
import { currentProfile, user } from '../store.js';
import { h, img, empty, icon, problem, rail, posterCard, primaryButton, roundButton, secondaryButton, setTitle, skeletonLines, titleHref } from '../ui.js';

const bookmarkKey = (id) => ['sokoul.reading', user()?.id, currentProfile()?.id, id].join('.');
function bookmark(id) {
  try { return JSON.parse(localStorage.getItem(bookmarkKey(id)) || 'null'); } catch { return null; }
}

export async function render({ root, params, signal, go }) {
  setTitle(t('anime'));
  const { id } = params;
  const page = h('div.page', skeletonLines(4));
  root.replaceChildren(page);

  async function load() {
    try {
      const work = await api.animation(id, { signal });
      if (signal.aborted) return;
      const { item } = work;
      setTitle(item.title);
      const chapters = h('div');
      const actions = h('div.actions');
      if (work.watch) actions.append(primaryButton(t('play'), () => go(titleHref(work.watch))));
      page.replaceChildren(
        h('div.work-heading',
          h('div.art.art-poster', img(item.poster, '')),
          h('div', h('h1.page-title', item.title), h('p.lead', [item.year, work.format, ...(work.genres || [])].filter(Boolean).join(' · ')),
            work.overview ? h('p.synopsis', work.overview) : null, actions)),
        item.kind === 'manga' ? h('section.rail', h('h2.section-title', t('chapters')), chapters) : null,
        work.related?.length ? rail(t('relatedWorks'), work.related.map((item) => posterCard(item, true))) : null,
        work.links?.length ? h('section', h('h2.section-title', t('officialLinks')),
          h('ul.list', work.links.map((link) => h('li', h('a.list-row.list-link', {
            href: link.url, target: '_blank', rel: 'noopener noreferrer',
          }, h('div.list-main', h('div.list-label', link.name)), icon('chevron')))))) : null);
      if (item.kind === 'manga') await loadChapters(id, actions, chapters, signal, go);
    } catch (error) {
      if (!signal.aborted) page.replaceChildren(problem(error, load));
    }
  }
  await load();
}

async function loadChapters(id, actions, list, signal, go) {
  list.replaceChildren(skeletonLines(3));
  try {
    const chapters = await api.chapters(id, { signal });
    if (signal.aborted) return;
    if (!chapters.length) {
      list.replaceChildren(empty(t('noChapters')));
      return;
    }
    const saved = bookmark(id);
    const current = chapters.some((row) => row.id === saved?.chapter) ? saved.chapter : chapters[0].id;
    actions.append(primaryButton(t(saved ? 'resumeReading' : 'open'), () => go(readerHref(id, current)), null));
    list.replaceChildren(h('ul.list.chapter-list', chapters.map((chapter) => h('li',
      h('a.list-row.list-link', { href: readerHref(id, chapter.id) },
        h('div.list-main', h('div.list-label', chapterLabel(chapter)), chapter.title ? h('div.list-detail', chapter.title) : null),
        h('span.list-detail', chapter.language.toUpperCase()), icon('chevron'))))));
  } catch (error) {
    if (!signal.aborted) list.replaceChildren(problem(error, () => loadChapters(id, actions, list, signal, go)));
  }
}

const chapterLabel = (chapter) => chapter.number ? t('chapterNumber', { n: chapter.number }) : chapter.title || t('chapters');
const readerHref = (id, chapter) => '#/read/' + id + '/' + chapter;

export async function read({ root, params, signal, go }) {
  const { id, chapter } = params;
  const toolbar = h('div.reader-toolbar');
  const stage = h('div.reader-stage');
  const status = h('div', { role: 'status' }, skeletonLines(3));
  root.append(h('div.reader', toolbar, stage, status));
  try {
    const [list, pages] = await Promise.all([api.chapters(id, { signal }), api.chapterPages(id, chapter, { signal })]);
    if (signal.aborted) return;
    const ci = list.findIndex((row) => row.id === chapter);
    if (!pages.length || ci < 0) {
      status.replaceChildren(empty(t('noChapters')));
      return;
    }
    setTitle(chapterLabel(list[ci]));
    const saved = bookmark(id);
    let page = saved?.chapter === chapter ? Math.max(0, Math.min(pages.length - 1, saved.page || 0)) : 0;
    let mode = saved?.mode || 'rtl';
    const picker = h('select.field', { 'aria-label': t('chapters'), onChange: (event) => go(readerHref(id, event.target.value), true) },
      list.map((row) => h('option', { value: row.id, selected: row.id === chapter }, chapterLabel(row))));
    const modes = h('select.field', { 'aria-label': t('playbackSettings'), onChange: (event) => { mode = event.target.value; draw(); } },
      Object.entries({ rtl: 'readerRtl', ltr: 'readerLtr', scroll: 'readerScroll' }).map(([value, label]) =>
        h('option', { value, selected: value === mode }, t(label))));
    const count = h('span.reader-count', { 'aria-live': 'polite' });
    const backward = roundButton('prev', t('previous'), () => move(-1));
    const forward = roundButton('next', t('next'), () => move(1));
    toolbar.append(h('div.reader-pickers', picker, modes), h('div.reader-navigation', backward, count, forward),
      h('div.reader-chapters', ci > 0 ? secondaryButton(t('prevChapter'), () => go(readerHref(id, list[ci - 1].id), true)) : null,
        ci + 1 < list.length ? secondaryButton(t('nextChapter'), () => go(readerHref(id, list[ci + 1].id), true)) : null));
    function remember() {
      try { localStorage.setItem(bookmarkKey(id), JSON.stringify({ chapter, page, mode })); } catch { /* storage is optional */ }
    }
    let observer;
    function draw() {
      observer?.disconnect();
      status.replaceChildren();
      count.textContent = t('readerPage', { n: page + 1, total: pages.length });
      backward.disabled = page === 0;
      forward.disabled = page === pages.length - 1;
      stage.dataset.mode = mode;
      if (mode === 'scroll') {
        const images = pages.map((url, i) => img(url, t('readerPage', { n: i + 1, total: pages.length }), 'reader-sheet', i === page));
        stage.replaceChildren(...images);
        observer = new IntersectionObserver((entries) => {
          const visible = entries.find((entry) => entry.isIntersecting);
          if (!visible) return;
          page = images.indexOf(visible.target);
          count.textContent = t('readerPage', { n: page + 1, total: pages.length });
          remember();
        }, { threshold: .5 });
        images.forEach((image) => observer.observe(image));
        images[page]?.scrollIntoView({ block: 'start' });
      } else stage.replaceChildren(img(pages[page], count.textContent, 'reader-sheet', true) ||
        empty(t('loadFailed')));
      remember();
    }
    function move(delta) {
      page = Math.min(pages.length - 1, Math.max(0, page + delta));
      draw();
    }
    window.addEventListener('keydown', (event) => {
      if (event.target instanceof HTMLSelectElement || !['ArrowLeft', 'ArrowRight'].includes(event.key) || mode === 'scroll') return;
      event.preventDefault();
      const advance = event.key === (mode === 'rtl' ? 'ArrowLeft' : 'ArrowRight');
      move(advance ? 1 : -1);
    }, { signal });
    signal.addEventListener('abort', () => observer?.disconnect(), { once: true });
    draw();
  } catch (error) {
    if (!signal.aborted) status.replaceChildren(problem(error, () => { root.replaceChildren(); read({ root, params, signal, go }); }));
  }
}
