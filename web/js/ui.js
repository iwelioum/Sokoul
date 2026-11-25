// DOM helpers and the shared components (buttons, cards, rows, skeletons, messages).
// Everything is built with the DOM API: no HTML strings, no style attributes, so the strict CSP holds.

import { t } from './i18n.js';

/** h('div.a.b', {attrs}, ...children): an element with classes, attributes, listeners (onX) and children. */
export function h(tag, attrs, ...children) {
  const [name, ...classes] = tag.split('.');
  const el = document.createElement(name || 'div');
  if (classes.length) el.className = classes.join(' ');
  if (attrs && (typeof attrs !== 'object' || attrs instanceof Node || Array.isArray(attrs))) {
    children.unshift(attrs);
    attrs = null;
  }
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'class') el.className += (el.className ? ' ' : '') + v;
    else if (k === 'text') el.textContent = v;
    else if (k === 'vars') Object.entries(v).forEach(([n, val]) => el.style.setProperty(n, val));
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, String(v));
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

const SVG = 'http://www.w3.org/2000/svg';
const PATHS = {
  play: 'M8 5.6v12.8c0 .9 1 1.4 1.7.9l9.6-6.4a1.1 1.1 0 0 0 0-1.8L9.7 4.7C9 4.2 8 4.7 8 5.6z',
  back: 'M15 5l-7 7 7 7',
  next: 'M9 5l7 7-7 7',
  prev: 'M15 5l-7 7 7 7',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 4.2a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6zM13.3 17h-2.6v-5.6h2.6z',
  search: 'M10.5 4a6.5 6.5 0 1 0 3.9 11.7l4.4 4.4M10.5 4a6.5 6.5 0 0 1 0 13',
  close: 'M6 6l12 12M18 6L6 18',
  person: 'M12 12a4.2 4.2 0 1 0 0-8.4 4.2 4.2 0 0 0 0 8.4zm0 2c-4.3 0-8 2.2-8 5.2 0 .7.6 1.3 1.3 1.3h13.4c.7 0 1.3-.6 1.3-1.3 0-3-3.7-5.2-8-5.2z',
  layers: 'M12 3.5l8.5 4.5-8.5 4.5L3.5 8zM3.5 12.2l8.5 4.5 8.5-4.5M3.5 16.2l8.5 4.5 8.5-4.5',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  chevron: 'M9 5l7 7-7 7',
  restart: 'M4.5 12a7.5 7.5 0 1 0 2.2-5.3M4.5 4v4.5H9',
};
const STROKED = new Set(['back', 'next', 'prev', 'search', 'close', 'layers', 'check', 'chevron', 'restart']);

/** A monochrome icon from the Sokoul set (filled, soft corners, round strokes). */
export function icon(name, cls = '') {
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('class', `icon ${cls}`.trim());
  const path = document.createElementNS(SVG, 'path');
  path.setAttribute('d', PATHS[name] || '');
  if (STROKED.has(name)) {
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '2.2');
    path.setAttribute('stroke-linecap', 'round');
    path.setAttribute('stroke-linejoin', 'round');
  } else {
    path.setAttribute('fill', 'currentColor');
  }
  svg.append(path);
  return svg;
}

/** An <img> that leaves the grey plaque behind it when the picture is missing or fails. */
export function img(src, alt = '', cls = '', eager = false) {
  if (!src) return null;
  const el = h('img', { src, alt, class: cls, loading: eager ? 'eager' : 'lazy', decoding: 'async', draggable: 'false' });
  el.addEventListener('error', () => el.remove(), { once: true });
  el.addEventListener('load', () => el.classList.add('is-loaded'), { once: true });
  return el;
}

/** The white capsule: one per surface. */
export function primaryButton(label, onClick, glyph = 'play') {
  return h('button.btn.btn-primary', { type: 'button', onClick }, glyph ? icon(glyph) : null, h('span', label));
}

/** A grey capsule for a secondary action with a label. */
export function secondaryButton(label, onClick, glyph = null) {
  return h('button.btn.btn-secondary', { type: 'button', onClick }, glyph ? icon(glyph) : null, h('span', label));
}

/** The round button: a glyph, its label for assistive tech and as a tooltip. */
export function roundButton(glyph, label, onClick, cls = '') {
  return h(`button.btn-round${cls ? '.' + cls : ''}`, { type: 'button', 'aria-label': label, title: label, onClick }, icon(glyph));
}

export const titleHref = (item) => `#/title/${item.kind}/${item.id}`;

/** Poster card 2:3. [withTitle] puts the title under it (grids only). */
export function posterCard(item, withTitle = false) {
  return h('a.card.card-poster', { href: titleHref(item), 'aria-label': item.year ? `${item.title}, ${item.year}` : item.title },
    h('div.art.art-poster', img(item.poster, '')),
    withTitle ? h('div.card-text', h('div.card-title', item.title), item.year ? h('div.card-sub', String(item.year)) : null) : null,
  );
}

/** Thumbnail card 16:9 with an optional progress bar and two lines below. */
export function thumbCard({ href, image, title, subtitle, progress, onClick, label }) {
  const art = h('div.art.art-thumb', img(image, ''));
  if (progress != null && progress > 0) {
    art.append(h('div.progress', h('div.progress-fill', { vars: { '--p': String(Math.min(1, Math.max(0.03, progress))) } })));
  }
  const tag = href ? 'a' : 'button';
  return h(`${tag}.card.card-thumb`, { href, type: href ? null : 'button', onClick, 'aria-label': label || title },
    art,
    h('div.card-text', h('div.card-title', title), subtitle ? h('div.card-sub', subtitle) : null),
  );
}

/**
 * A titled horizontal rail: snap scrolling, bleeding past the right margin; with a fine pointer, arrows show on hover.
 */
export function rail(title, cards, kind = 'poster') {
  const track = h(`div.rail-track.rail-${kind}`, { role: 'list' }, cards.map((c) => {
    c.setAttribute('role', 'listitem');
    return c;
  }));
  const scroll = (dir) => track.scrollBy({ left: dir * track.clientWidth * 0.8, behavior: 'smooth' });
  const prev = roundButton('prev', t('previous'), () => scroll(-1), 'rail-arrow rail-prev');
  const next = roundButton('next', t('next'), () => scroll(1), 'rail-arrow rail-next');
  prev.tabIndex = -1;
  next.tabIndex = -1;
  const updateArrows = () => {
    prev.classList.toggle('is-hidden', track.scrollLeft < 8);
    next.classList.toggle('is-hidden', track.scrollLeft + track.clientWidth >= track.scrollWidth - 8);
  };
  track.addEventListener('scroll', updateArrows, { passive: true });
  requestAnimationFrame(updateArrows);
  return h('section.rail', title ? h('h2.rail-title', title) : null, h('div.rail-body', track, prev, next));
}

/** Skeleton rail: grey plaques with a shimmer while a row loads. */
export function skeletonRail(kind = 'poster', count = 8, withTitle = true) {
  const cards = Array.from({ length: count }, () => h(`div.card.card-${kind}`, h(`div.art.art-${kind}.skeleton`)));
  return h('section.rail', { 'aria-hidden': 'true' },
    withTitle ? h('div.skeleton.skeleton-line.rail-title-skeleton') : null,
    h('div.rail-body', h(`div.rail-track.rail-${kind}`, cards)));
}

export function skeletonLines(n = 3, cls = '') {
  return h(`div.skeleton-lines${cls ? '.' + cls : ''}`, { 'aria-hidden': 'true' },
    Array.from({ length: n }, (_, i) => h('div.skeleton.skeleton-line', { vars: { '--w': `${i === n - 1 ? 55 : 90}%` } })));
}

/** A plain sentence for a failed load, with the way out: Sources et clés for a missing key, otherwise retry. */
export function problem(error, retry) {
  if (error && error.needsKey) {
    const service = serviceName(error.body?.service);
    return h('div.message', { role: 'status' },
      h('p.message-text', t('needsKey', { service })),
      h('a.btn.btn-secondary', { href: '#/sources' }, h('span', t('needsKeyLink'))));
  }
  const text = error && error.offline ? t('noAnswer') : t('loadFailed');
  return h('div.message', { role: 'status' },
    h('p.message-text', text),
    retry ? secondaryButton(t('retry'), retry) : null);
}

export function empty(text) {
  return h('div.message', { role: 'status' }, h('p.message-text', text));
}

const SERVICES = {
  tmdb: 'TMDB', realdebrid: 'Real-Debrid', omdb: 'OMDb', fanart: 'fanart.tv', tvdb: 'TheTVDB', watchmode: 'Watchmode',
  tastedive: 'TasteDive', motn: 'Movie of the Night', opensubtitles: 'OpenSubtitles', subdl: 'Subdl', iptv: 'IPTV',
};
export const SERVICE_ORDER = Object.keys(SERVICES);
export function serviceName(id) {
  return SERVICES[id] || id || '';
}

/** Avatar colours of the apps' profiles (Profiles.COLORS). */
const AVATARS = ['#5E5CE6', '#FF9F0A', '#30D158', '#FF375F', '#64D2FF', '#BF5AF2', '#FFD60A', '#AC8E68'];
export function avatar(profile, cls = '') {
  const el = h(`span.avatar${cls ? '.' + cls : ''}`, { 'aria-hidden': 'true', vars: { '--avatar': AVATARS[(profile.avatar || 0) % AVATARS.length] } },
    (profile.name || '?').trim().charAt(0).toUpperCase());
  return el;
}

/** "2024 · 2 h 46 · Science-fiction": one line of metadata, empty parts dropped. */
export function metaLine(parts, cls = 'meta') {
  const text = parts.filter((p) => p !== null && p !== undefined && p !== '').join(' · ');
  return text ? h(`p.${cls}`, text) : null;
}

/** Sets the document title for the screen. */
export function setTitle(text) {
  document.title = text ? `${text} · Sokoul` : 'Sokoul';
}
