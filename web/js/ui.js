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
/*
 * The apps' icons: Sokoul's own (ui/NavIcons.kt, filled parts and round lines on a 24 grid) and the Material ones the
 * apps use elsewhere (filled). A string is one filled path; an array mixes filled paths and lines [d, strokeWidth].
 */
const PATHS = {
  // NavIcons.kt
  home: 'M12 2.6c.5 0 1 .2 1.4.5l7 6c.4.4.6.9.6 1.4v8.8c0 1.2-1 2.2-2.2 2.2H15v-5.8c0-.7-.5-1.2-1.2-1.2h-3.6c-.7 0-1.2.5-1.2 1.2v5.8H5.2C4 21.5 3 20.5 3 19.3V10.5c0-.5.2-1 .6-1.4l7-6c.4-.3.9-.5 1.4-.5z',
  myList: ['M5.5 7h10.5A2.5 2.5 0 0 1 18.5 9.5v9.5A2.5 2.5 0 0 1 16 21.5H5.5A2.5 2.5 0 0 1 3 19V9.5A2.5 2.5 0 0 1 5.5 7zM9.75 10.25v3h-3v2h3v3h2v-3h3v-2h-3v-3z', ['M7.5 3.5h10.5a3 3 0 0 1 3 3v10', 1.9]],
  movies: 'M5 3.5h14A2.5 2.5 0 0 1 21.5 6v12a2.5 2.5 0 0 1-2.5 2.5H5A2.5 2.5 0 0 1 2.5 18V6A2.5 2.5 0 0 1 5 3.5zM4.6 6v2.4h2V6zM17.4 6v2.4h2V6zM4.6 10.8v2.4h2v-2.4zM17.4 10.8v2.4h2v-2.4zM4.6 15.6V18h2v-2.4zM17.4 15.6V18h2v-2.4zM8.7 6v5h6.6V6zM8.7 13v5h6.6v-5z',
  series: ['M5 3.8h14A2.5 2.5 0 0 1 21.5 6.3v9.4a2.5 2.5 0 0 1-2.5 2.5H5a2.5 2.5 0 0 1-2.5-2.5V6.3A2.5 2.5 0 0 1 5 3.8zM10 7.6v6.8l5.6-3.4z', ['M8 20.8h8', 2]],
  collections: ['M5.5 9.5h13A2.5 2.5 0 0 1 21 12v7a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 19v-7a2.5 2.5 0 0 1 2.5-2.5z', ['M6 6.3h12', 2], ['M9 3.1h6', 2]],
  anime: ['M10 3l2.2 6.3L18.5 11.5l-6.3 2.2L10 20l-2.2-6.3L1.5 11.5l6.3-2.2L10 3z', 'M18.5 2.5l1 2.8 2.8 1-2.8 1-1 2.8-1-2.8-2.8-1 2.8-1 1-2.8z'],
  tv: ['M4.5 8h15A2.5 2.5 0 0 1 22 10.5v8a2.5 2.5 0 0 1-2.5 2.5h-15A2.5 2.5 0 0 1 2 18.5v-8A2.5 2.5 0 0 1 4.5 8z', ['M7.5 2.8L12 7.2l4.5-4.4', 2]],
  search: [['M10.5 3.5a7 7 0 1 1 0 14 7 7 0 0 1 0-14z', 2.3], ['M15.8 15.8l4.8 4.8', 2.6]],
  // Material (filled)
  menu: 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  trophy: 'M19 5h-2V3H7v2H5c-1.1 0-2 .9-2 2v1c0 2.55 1.92 4.63 4.39 4.94.63 1.5 1.98 2.63 3.61 2.96V19H7v2h10v-2h-4v-3.1c1.63-.33 2.98-1.46 3.61-2.96C19.08 12.63 21 10.55 21 8V7c0-1.1-.9-2-2-2zM5 8V7h2v3.82C5.84 10.4 5 9.3 5 8zm14 0c0 1.3-.84 2.4-2 2.82V7h2v1z',
  manga: 'M19 1l-5 5v11l5-4.5V1zM1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5V6c-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6zm22 13.5V6c-.6-.45-1.25-.75-2-1v13.5c-1.1-.35-2.3-.5-3.5-.5-1.7 0-4.15.65-5.5 1.5v2c1.35-.85 3.8-1.5 5.5-1.5 1.65 0 3.35.3 4.75 1.05.1.05.15.05.25.05.25 0 .5-.25.5-.5v-1.1c-.75-.25-1.5-.5-2-.5z',
  play: 'M8 5v14l11-7z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  info: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
  chevron: 'M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z',
  next: 'M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z',
  prev: 'M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z',
  back: 'M15.41 16.59L10.83 12l4.58-4.59L14 6l-6 6 6 6 1.41-1.41z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  star: 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  starHalf: 'M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4V6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z',
  starOutline: 'M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z',
  language: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zm6.93 6h-2.95a15.65 15.65 0 0 0-1.38-3.56A8.03 8.03 0 0 1 18.92 8zM12 4.04c.83 1.2 1.48 2.53 1.91 3.96h-3.82c.43-1.43 1.08-2.76 1.91-3.96zM4.26 14C4.1 13.36 4 12.69 4 12s.1-1.36.26-2h3.38c-.08.66-.14 1.32-.14 2s.06 1.34.14 2H4.26zm.82 2h2.95c.32 1.25.78 2.45 1.38 3.56A7.99 7.99 0 0 1 5.08 16zm2.95-8H5.08a7.99 7.99 0 0 1 4.33-3.56A15.65 15.65 0 0 0 8.03 8zM12 19.96c-.83-1.2-1.48-2.53-1.91-3.96h3.82c-.43 1.43-1.08 2.76-1.91 3.96zM14.34 14H9.66c-.09-.66-.16-1.32-.16-2s.07-1.35.16-2h4.68c.09.65.16 1.32.16 2s-.07 1.34-.16 2zm.25 5.56c.6-1.11 1.06-2.31 1.38-3.56h2.95a8.03 8.03 0 0 1-4.33 3.56zM16.36 14c.08-.66.14-1.32.14-2s-.06-1.34-.14-2h3.38c.16.64.26 1.31.26 2s-.1 1.36-.26 2h-3.38z',
  bell: 'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
  bellOn: 'M7.58 4.08L6.15 2.65C3.75 4.48 2.17 7.3 2.03 10.5h2a8.445 8.445 0 0 1 3.55-6.42zm12.39 6.42h2c-.15-3.2-1.73-6.02-4.12-7.85l-1.42 1.43a8.495 8.495 0 0 1 3.54 6.42zM18 11c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2v-5zm-6 11c.14 0 .27-.01.4-.04.65-.14 1.18-.58 1.44-1.18.1-.24.15-.5.15-.78h-4c.01 1.1.9 2 2.01 2z',
  doc: 'M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z',
  film: 'M18 3v2h-2V3H8v2H6V3H4v18h2v-2h2v2h8v-2h2v2h2V3h-2zM8 17H6v-2h2v2zm0-4H6v-2h2v2zm0-4H6V7h2v2zm10 8h-2v-2h2v2zm0-4h-2v-2h2v2zm0-4h-2V7h2v2z',
  restart: 'M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  fullscreen: 'M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z',
  person: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  server: 'M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zM7 19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM20 3H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z',
  volumeUp: 'M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z',
  volumeOff: 'M16.5 12A4.5 4.5 0 0 0 14 7.97v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.796 8.796 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06a8.99 8.99 0 0 0 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z',
  pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
  replay10: 'M11.99 5V1l-5 5 5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6h-2c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8zm-1.1 11h-.85v-3.26l-1.01.31v-.69l1.77-.63h.09V16zm4.28-1.76c0 .32-.03.6-.1.82s-.17.42-.29.57-.28.26-.45.33-.37.1-.59.1-.41-.03-.59-.1-.33-.18-.46-.33-.23-.34-.3-.57-.11-.5-.11-.82v-.74c0-.32.03-.6.1-.82s.17-.42.29-.57.28-.26.45-.33.37-.1.59-.1.41.03.59.1.33.18.46.33.23.34.3.57.11.5.11.82v.74zm-.85-.86c0-.19-.01-.35-.04-.48s-.07-.23-.12-.31-.11-.14-.19-.17-.16-.05-.25-.05-.18.02-.25.05-.14.09-.19.17-.09.18-.12.31-.04.29-.04.48v.97c0 .19.01.35.04.48s.07.24.12.32.11.14.19.17.16.05.25.05.18-.02.25-.05.14-.09.19-.17.09-.19.11-.32.04-.29.04-.48v-.97z',
  forward10: 'M18 13c0 3.31-2.69 6-6 6s-6-2.69-6-6 2.69-6 6-6v4l5-5-5-5v4c-4.42 0-8 3.58-8 8s3.58 8 8 8 8-3.58 8-8h-2zm-7.46 2.22c-.06.05-.12.09-.2.12s-.17.04-.27.04a.69.69 0 0 1-.45-.15c-.06-.05-.1-.1-.13-.17s-.05-.14-.05-.22h-.85c0 .21.04.39.12.55s.19.28.33.38.29.18.46.23.35.07.53.07c.21 0 .41-.03.6-.08s.34-.14.48-.24.24-.24.32-.39.12-.33.12-.53c0-.23-.06-.44-.18-.61s-.3-.3-.54-.39c.1-.05.2-.1.28-.17s.15-.14.2-.22.1-.16.13-.25.04-.18.04-.27c0-.2-.04-.37-.11-.53s-.17-.28-.3-.38-.28-.18-.46-.23-.37-.08-.59-.08c-.19 0-.38.03-.54.08s-.32.13-.44.23-.23.21-.3.34-.11.29-.11.45h.85c0-.07.02-.14.05-.2s.07-.11.12-.15.11-.07.18-.1.14-.03.22-.03c.1 0 .18.01.25.04s.13.06.18.11.08.11.11.17.04.14.04.22c0 .18-.05.32-.16.43s-.26.16-.48.16h-.43v.66h.45c.11 0 .2.01.29.04s.16.06.22.11.11.12.14.2.05.18.05.29c0 .09-.01.17-.04.24s-.08.11-.13.17zm3.9-3.44c-.18-.07-.37-.1-.59-.1s-.41.03-.59.1-.33.18-.45.33-.23.34-.29.57-.1.5-.1.82v.74c0 .32.04.6.11.82s.17.42.3.57.28.26.46.33.37.1.59.1.41-.03.59-.1.33-.18.45-.33.22-.34.29-.57.1-.5.1-.82v-.74c0-.32-.04-.6-.11-.82s-.17-.42-.3-.57-.29-.26-.46-.33zm.01 2.57c0 .19-.01.35-.04.48s-.06.24-.11.32-.11.14-.19.17-.16.05-.25.05-.18-.02-.25-.05-.14-.09-.19-.17-.09-.19-.12-.32-.04-.29-.04-.48v-.97c0-.19.01-.35.04-.48s.06-.23.12-.31.11-.14.19-.17.16-.05.25-.05.18.02.25.05.14.09.19.17.09.18.12.31.04.29.04.48v.97z',
  calendar: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.11 0-1.99.9-1.99 2L3 20a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V10h14v10zM9 14H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2zm-8 4H7v-2h2v2zm4 0h-2v-2h2v2zm4 0h-2v-2h2v2z',
  casino: 'M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM7.5 18c-.83 0-1.5-.67-1.5-1.5S6.67 15 7.5 15s1.5.67 1.5 1.5S8.33 18 7.5 18zm0-9C6.67 9 6 8.33 6 7.5S6.67 6 7.5 6 9 6.67 9 7.5 8.33 9 7.5 9zm4.5 4.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm4.5 4.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm0-9c-.83 0-1.5-.67-1.5-1.5S15.67 6 16.5 6s1.5.67 1.5 1.5S17.33 9 16.5 9z',
  history: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
  subtitles: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z',
  audio: 'M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z',
};
// The names the screens used before (kept so every caller finds its glyph).
Object.assign(PATHS, { bookmark: PATHS.myList, screen: PATHS.series, sparkles: PATHS.anime, layers: PATHS.collections });

/** A monochrome icon from the apps' set. */
export function icon(name, cls = '') {
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('class', `icon ${cls}`.trim());
  const spec = PATHS[name] || '';
  for (const part of Array.isArray(spec) ? spec : [spec]) {
    const path = document.createElementNS(SVG, 'path');
    if (Array.isArray(part)) {
      path.setAttribute('d', part[0]);
      path.setAttribute('fill', 'none');
      path.setAttribute('stroke', 'currentColor');
      path.setAttribute('stroke-width', String(part[1]));
      path.setAttribute('stroke-linecap', 'round');
      path.setAttribute('stroke-linejoin', 'round');
    } else {
      path.setAttribute('d', part);
      path.setAttribute('fill', 'currentColor');
    }
    svg.append(path);
  }
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

export const titleHref = (item) => item.kind === 'anime' || item.kind === 'manga'
  ? `#/work/${item.id}` : `#/title/${item.kind}/${item.id}`;

/** Poster card 2:3. [withTitle] puts the title under it (grids only). */
export function posterCard(item, withTitle = false) {
  return h('a.card.card-poster', { href: titleHref(item), 'aria-label': item.year ? `${item.title}, ${item.year}` : item.title },
    h('div.art.art-poster', img(item.poster, '')),
    withTitle ? h('div.card-text', h('div.card-title', item.title), item.year ? h('div.card-sub', String(item.year)) : null) : null,
  );
}

export function personCard(person) {
  const initials = (person.name || '?').split(/\s+/).slice(0, 2).map((word) => word[0]).join('').toUpperCase();
  return h(person.id ? 'a.card.card-person' : 'div.card.card-person',
    person.id ? { href: `#/person/${person.id}` } : null,
    h('div.art.art-person', h('span.art-initials', { 'aria-hidden': 'true' }, initials), img(person.photo, '')),
    h('div.card-text', h('div.card-title', person.name), person.character ? h('div.card-sub', person.character) : null));
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
      h('a.btn.btn-secondary', { href: error.body?.service ? `#/sources/${encodeURIComponent(error.body.service)}` : '#/sources' }, h('span', t('needsKeyLink'))));
  }
  const message = { iptv_unavailable: 'iptvUnavailable', sports_unavailable: 'sportUnavailable',
    sports_access_required: 'sportsAccessRequired', channel_not_found: 'channelUnavailable',
    anime_unavailable: 'animeUnavailable', manga_unavailable: 'mangaUnavailable',
    iptv_vod_unavailable: 'vodUnavailable', replay_unavailable: 'replayUnavailable' }[error?.code];
  const text = t(message || (error?.offline ? 'noAnswer' : 'loadFailed'));
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
