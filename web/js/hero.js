// What every hero says about a work, in the apps' order (title/HeroDetails.kt HeroInfo): who presents it, its logo, its
// tagline, the facts (stars, year, length, when it ends, languages), who made it, where else it is, its kind and genres,
// the summary; then the buttons (PlayCard, LabeledRoundButton).

import { lang, t, runtime } from './i18n.js';
import { h, img, icon } from './ui.js';

const languageNames = (() => {
  try { return new Intl.DisplayNames([lang], { type: 'language' }); } catch { return null; }
})();

function languageName(code) {
  if (!code || !languageNames) return null;
  try {
    const name = languageNames.of(code);
    return name && name !== code ? name.charAt(0).toUpperCase() + name.slice(1) : null;
  } catch {
    return null;
  }
}

/** The original language first, then the others, two at most (HeroDetails.languages). */
function languages(data) {
  const names = [data.originalLanguage, ...(data.spokenLanguages || [])].map(languageName).filter(Boolean);
  return [...new Set(names)].slice(0, 2).join(' · ') || null;
}

function clock(ms) {
  return new Intl.DateTimeFormat(lang === 'en' ? 'en' : 'fr-FR', { hour: '2-digit', minute: '2-digit' }).format(new Date(ms));
}

export function formatDate(iso) {
  if (!iso) return null;
  const date = new Date(`${iso.slice(0, 10)}T12:00:00`);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(lang === 'en' ? 'en' : 'fr-FR', { day: 'numeric', month: 'short', year: 'numeric' }).format(date);
}

/** A film not out yet: "Prochainement" · "Au cinéma le 16 déc. 2026" (TitleScreen.cinemaLabels). */
export function cinemaLabels(kind, data) {
  if (kind !== 'movie' || !data?.releaseDate) return null;
  const out = new Date(`${data.releaseDate.slice(0, 10)}T00:00:00`);
  if (Number.isNaN(out.getTime()) || out <= new Date()) return null;
  return [t('comingSoon'), t('inCinemasOn', { date: formatDate(data.releaseDate) })];
}

/** "A24 PRÉSENTE", with the studios' logos in white when TMDB has them. */
function presentedBy(brands) {
  const first = brands?.[0];
  if (!first) return null;
  const withLogo = brands.filter((b) => b.logo).slice(0, 2);
  const logos = withLogo.map((b) => img(b.logo, b.name, 'presents-logo', true)).filter(Boolean);
  return h('div.presents', logos,
    h('span.presents-label', (withLogo.length ? '' : first.name + '  ') + t('presents')));
}

/** The title's logo with a dark halo of its own shape; the title in bold when there is none or it fails. */
export function titleLogo(logo, title) {
  const text = () => h('h1.hero-title', title);
  if (!logo) return text();
  const image = img(logo, title, 'hero-logo', true);
  if (!image) return text();
  const wrap = h('h1.hero-logo-wrap', image);
  image.addEventListener('error', () => wrap.replaceWith(text()), { once: true });
  return wrap;
}

/** Five stars filled to the rating out of ten; each pops in after the one before. */
function stars(rating) {
  return h('span.stars', { role: 'img', 'aria-label': `${(rating / 2).toFixed(1)} / 5` }, Array.from({ length: 5 }, (_, i) => {
    const value = rating / 2 - i;
    const glyph = value >= 0.75 ? 'star' : value >= 0.25 ? 'starHalf' : 'starOutline';
    const star = icon(glyph, value >= 0.25 ? 'star-on' : 'star-off');
    star.style.setProperty('--i', String(i));
    return star;
  }));
}

function facts(kind, data, remainingMin) {
  const minutes = remainingMin ?? (data.runtime > 0 ? data.runtime : null);
  const seasons = kind === 'tv' && !data.runtime && data.seasonCount > 0
    ? (data.seasonCount === 1 ? t('oneSeason') : t('seasonsCount', { n: data.seasonCount })) : null;
  const parts = [data.year, minutes ? runtime(minutes) : null, seasons].filter(Boolean);
  const langs = languages(data);
  const items = [
    data.rating > 0 ? stars(data.rating) : null,
    parts.length ? h('span.facts-main', parts.flatMap((p, i) => (i ? [h('span.facts-dot', '·'), String(p)] : [String(p)]))) : null,
    minutes && kind === 'movie' ? h('span.facts-soft', t('endsAt', { time: clock(Date.now() + minutes * 60_000) })) : null,
    langs ? h('span.facts-soft.facts-lang', icon('language'), langs) : null,
  ].filter(Boolean);
  return items.length ? h('div.facts', items) : null;
}

function alsoOn(watch) {
  const offers = [];
  for (const offer of watch || []) {
    if (!offers.some((o) => o.name === offer.name)) offers.push(offer);
  }
  if (!offers.length) return null;
  return h('div.also-on', h('span.also-on-label', t('alsoOn')), offers.slice(0, 5).map((o) =>
    (o.logo ? img(o.logo, o.name, 'also-on-logo', true) : null) || h('span.also-on-name', o.name)));
}

/** As many genres as fit on the row: three short ones, two long ones (HeroDetails.fitGenres). */
function fitGenres(all) {
  const out = [];
  let chars = 0;
  for (const g of (all || []).slice(0, 3)) {
    if (out.length && chars + g.length > 50) break;
    out.push(g);
    chars += g.length;
  }
  return out;
}

function genrePills(kind, data) {
  return h('div.genre-pills',
    h('span.kind-pill', kind === 'tv' ? t('typeSeries') : kind === 'movie' ? t('movie') : t(kind)),
    fitGenres(data.genres).map((g) => h('span.genre-pill', g)),
    data.certification ? h('span.badge', data.certification) : null);
}

/** The summary on three lines, and the rest behind a link that says so. */
function summary(text) {
  if (!text) return null;
  const body = h('p.summary', text);
  if (text.length <= 210) return h('div.summary-block', body);
  const more = h('button.summary-more', { type: 'button', 'aria-expanded': 'false' }, t('readMore'));
  more.addEventListener('click', () => {
    const open = body.classList.toggle('is-open');
    more.textContent = t(open ? 'readLess' : 'readMore');
    more.setAttribute('aria-expanded', String(open));
  });
  return h('div.summary-block', body, more);
}

/** HeroInfo: every piece arrives a moment after the one before it (Reveal, 70 ms apart). */
export function heroInfo(kind, data, { remainingMin = null, title = data.title } = {}) {
  const tagline = data.tagline ? h('p.tagline', h('span.tagline-dash', { 'aria-hidden': 'true' }), h('span.tagline-text', data.tagline)) : null;
  const signature = data.director ? h('p.signature', h('span', t(kind === 'tv' ? 'createdBy' : 'directedBy')), h('strong', data.director)) : null;
  const pieces = [
    presentedBy(data.brands), titleLogo(data.logo, title), tagline, facts(kind, data, remainingMin), signature,
    alsoOn((data.watch || []).filter((w) => w.type === 'flatrate' || w.type === 'free' || w.type === 'ads')),
    genrePills(kind, data), summary(data.overview),
  ];
  return h('div.hero-info', pieces.filter(Boolean).map((piece, i) => {
    piece.classList.add('reveal');
    piece.style.setProperty('--order', String(i));
    return piece;
  }));
}

/** PlayCard: a capsule with a circled play glyph, what it will do, what remains, and how far one has got. */
export function playCard({ label, sub = null, progress = null, onClick, href = null }) {
  const tag = href ? 'a' : 'button';
  return h(`${tag}.play-card`, { href, type: href ? null : 'button', onClick },
    h('span.play-card-disc', icon('play')),
    h('span.play-card-text', h('span.play-card-label', label), sub ? h('span.play-card-sub', sub) : null),
    progress != null && progress > 0 ? h('span.play-card-bar', h('span.play-card-fill', { vars: { '--p': String(Math.min(1, progress)) } })) : null);
}

/** LabeledRoundButton: a round button that opens into a pill with its name when pointed at or focused. */
export function labeledRound(glyph, label, onClick, cls = '') {
  const button = h(`button.labeled-round${cls ? '.' + cls : ''}`, { type: 'button', 'aria-label': label, onClick },
    icon(glyph), h('span.labeled-round-text', { 'aria-hidden': 'true' }, label));
  button.setLabel = (text, newGlyph) => {
    button.setAttribute('aria-label', text);
    button.querySelector('.labeled-round-text').textContent = text;
    if (newGlyph) button.querySelector('svg').replaceWith(icon(newGlyph));
  };
  return button;
}

/** The slides' dots in a dark pill; the current one is a longer white bar. */
export function dots(count, active, onPick, labels = []) {
  return h('div.hero-dots', Array.from({ length: count }, (_, i) => h('button.hero-dot', {
    type: 'button', 'aria-label': labels[i] || String(i + 1), 'aria-pressed': String(i === active), onClick: () => onPick(i),
  })));
}
