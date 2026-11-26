// Dev mock of the Sokoul server for the web client: serves web/ and fake JSON for every endpoint the client calls.
// No dependencies. Data is clearly fake: invented titles, grey placeholder pictures, no ratings.
//
//   node web/dev/mock-server.mjs [port]        then open http://127.0.0.1:8090/
//
// Sign in with any name and a passphrase of at least 12 characters. The profile "Marc" is protected by the code 1234.
// With ffmpeg on the PATH, a 30-second test pattern is made once as HLS so the player has something to play.

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const PORT = Number(process.argv[2] || process.env.PORT || 8090);
const MEDIA = path.join(os.tmpdir(), 'sokoul-web-mock-media');

const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.webmanifest': 'application/manifest+json',
  '.png': 'image/png', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.txt': 'text/plain; charset=utf-8',
  '.m3u8': 'application/vnd.apple.mpegurl', '.ts': 'video/mp2t', '.vtt': 'text/vtt; charset=utf-8',
};

// The CSP the real server is expected to send: everything from this origin, nothing inline.
const CSP = [
  "default-src 'self'", "script-src 'self'", "style-src 'self'", "img-src 'self' data: blob:", "font-src 'self'",
  "media-src 'self' blob:", "connect-src 'self'", "worker-src 'none'", "object-src 'none'", "base-uri 'none'",
  "frame-ancestors 'none'", "form-action 'self'",
].join('; ');

// ---------------------------------------------------------------------------------------------------- fake catalogue

const MOVIES = [
  ['Le Phare du Nord', 2024, 142, ['Drame', 'Mystère']],
  ['Orbite basse', 2023, 166, ['Science-fiction', 'Aventure']],
  ['La Dernière Marée', 2022, 118, ['Drame']],
  ['Néons sur la ville', 2024, 104, ['Policier', 'Thriller']],
  ['Les Jardins de verre', 2021, 97, ['Comédie', 'Romance']],
  ['Altitude 8000', 2023, 129, ['Aventure']],
  ['Le Bureau des rêves', 2020, 111, ['Comédie']],
  ['Signal faible', 2024, 98, ['Thriller', 'Science-fiction']],
  ['Une saison à Tanger', 2022, 124, ['Drame', 'Romance']],
  ['Le Dernier Train pour Oslo', 2019, 133, ['Thriller']],
  ['Petites Machines', 2023, 88, ['Animation', 'Familial']],
  ['Ciel de cendre', 2021, 151, ['Guerre', 'Drame']],
];
const SERIES = [
  ['Les Veilleurs', 2023, 3], ['Station Polaris', 2024, 2], ['Maison Delorme', 2022, 4], ['Hors Ligne', 2024, 1],
  ['Les Cartographes', 2021, 2], ['Brigade Littoral', 2023, 3], ['Quartier Libre', 2020, 5], ['Le Septième Étage', 2024, 1],
];

const items = [
  ...MOVIES.map(([title, year, runtime, genres], i) => ({ kind: 'movie', id: String(101 + i), title, year, runtime, genres })),
  ...SERIES.map(([title, year, seasons], i) => ({ kind: 'tv', id: String(201 + i), title, year, seasons, genres: ['Drame', 'Série policière'].slice(0, 1 + (i % 2)) })),
];
const byKey = new Map(items.map((it) => [`${it.kind}/${it.id}`, it]));
const pic = (type, id) => `/dev/img/${type}/${id}.svg`;
const toItem = (it) => ({ kind: it.kind, id: it.id, title: it.title, year: it.year, poster: pic('poster', it.id), backdrop: pic('backdrop', it.id) });

const OVERVIEW = 'Texte de démonstration : ce résumé remplace celui que le serveur lira chez TMDB. Il fait la longueur d’un vrai synopsis pour vérifier la mise en page, le retour à la ligne et la coupure sur plusieurs lignes, sans rien raconter de réel.';
const PEOPLE = ['Camille Martin', 'Hugo Bernard', 'Inès Moreau', 'Yanis Laurent', 'Chloé Garnier', 'Malik Robert', 'Sarah Lefèvre', 'Tom Girard', 'Nora Fontaine', 'Paul Chevalier'];
const ROLES = ['Le gardien', 'Élise', 'Le commandant', 'Nadia', 'Victor', 'L’inconnue', 'Bastien', 'La juge', 'Simon', 'Rose'];

function title(it) {
  const body = {
    title: it.title, overview: OVERVIEW, year: it.year, runtime: it.runtime || null, genres: it.genres,
    backdrop: pic('backdrop', it.id), poster: pic('poster', it.id), logo: null,
    cast: PEOPLE.map((name, i) => ({ name, character: ROLES[i], photo: i % 3 === 2 ? null : pic('person', `${it.id}-${i}`) })),
  };
  if (it.kind === 'tv') body.seasons = Array.from({ length: it.seasons }, (_, s) => ({ number: s + 1, name: `Saison ${s + 1}`, episodeCount: 8 }));
  return body;
}

function season(id, number) {
  const names = ['Le départ', 'Sous la surface', 'Ce que l’on tait', 'Nuit blanche', 'Le relais', 'Contre-jour', 'Tout recommencer', 'La traversée'];
  return {
    episodes: names.map((name, i) => ({
      number: i + 1, name, runtime: 44 + ((i * 7) % 14), still: pic('still', `${id}-${number}-${i + 1}`),
      overview: 'Résumé de démonstration de l’épisode, assez long pour tenir sur deux lignes dans la vignette.',
    })),
  };
}

// --------------------------------------------------------------------------------------------- grey placeholder art

function svgPicture(type, id) {
  const seed = [...id].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);
  const g1 = 34 + (seed % 40);
  const g2 = 14 + ((seed >> 3) % 22);
  const dims = { poster: [400, 600], backdrop: [1280, 720], still: [640, 360], person: [300, 300] }[type] || [400, 400];
  const [w, h] = dims;
  const grey = (v) => `rgb(${v},${v},${v + 2})`;
  const label = type === 'poster' || type === 'backdrop' ? (byKey.get(`movie/${id}`) || byKey.get(`tv/${id}`))?.title || '' : '';
  const text = label && type === 'poster'
    ? `<text x="50%" y="88%" text-anchor="middle" font-family="sans-serif" font-size="30" fill="rgba(255,255,255,0.45)">${label.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</text>`
    : '';
  const shape = type === 'person'
    ? `<circle cx="150" cy="120" r="56" fill="${grey(g1 + 30)}"/><rect x="62" y="196" width="176" height="140" rx="80" fill="${grey(g1 + 30)}"/>`
    : `<circle cx="${w * (0.3 + (seed % 40) / 100)}" cy="${h * 0.42}" r="${Math.min(w, h) * 0.22}" fill="${grey(g1 + 22)}"/>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${grey(g1)}"/><stop offset="1" stop-color="${grey(g2)}"/></linearGradient></defs>` +
    `<rect width="${w}" height="${h}" fill="url(#g)"/>${shape}${text}</svg>`;
}

// ------------------------------------------------------------------------------------------------- accounts and sync

const sessions = new Map(); // token → session
let users = new Map(); // lower name → user
const sync = new Map(); // user id → { seq, items: Map(type|profile|key → change) }

const javaNameUuid = (text) => {
  const b = crypto.createHash('md5').update(text, 'utf8').digest();
  b[6] = (b[6] & 0x0f) | 0x30;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = b.toString('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
};

function seedSync(user) {
  const state = { seq: 0, items: new Map() };
  const now = Date.now();
  const main = javaNameUuid(`sokoul-main-profile:${user.id}`);
  const lea = crypto.randomUUID();
  const marc = crypto.randomUUID();
  const kids = crypto.randomUUID();
  const put = (c) => { c.seq = ++state.seq; state.items.set(`${c.type}|${c.profile}|${c.key}`, c); };
  put({ type: 'profile', profile: main, key: main, updatedAt: now - 9e6, deleted: false, name: user.name, avatar: 0, kids: false });
  put({ type: 'profile', profile: lea, key: lea, updatedAt: now - 8e6, deleted: false, name: 'Léa', avatar: 3, kids: false });
  const pinHash = crypto.createHash('sha256').update(`sokoul-profile:${marc}:1234`).digest('hex');
  put({ type: 'profile', profile: marc, key: marc, updatedAt: now - 7e6, deleted: false, name: 'Marc', avatar: 4, kids: false, pinHash, pinSalt: marc });
  put({ type: 'profile', profile: kids, key: kids, updatedAt: now - 6e6, deleted: false, name: 'Enfants', avatar: 6, kids: true });
  put({ type: 'resume', profile: main, key: 'movie:102', updatedAt: now - 3.6e6, deleted: false, kind: 'movie', tmdbId: 102, positionMs: 3_960_000, durationMs: 9_960_000, finished: false, title: 'Orbite basse', subtitle: null, image: pic('backdrop', '102') });
  put({ type: 'resume', profile: main, key: 'tv:201:1:3', updatedAt: now - 7.2e6, deleted: false, kind: 'tv', tmdbId: 201, season: 1, episode: 3, positionMs: 900_000, durationMs: 2_820_000, finished: false, title: 'Les Veilleurs', subtitle: 'S1 · É3', image: pic('still', '201-1-3') });
  put({ type: 'resume', profile: main, key: 'movie:108', updatedAt: now - 9e7, deleted: false, kind: 'movie', tmdbId: 108, positionMs: 4_100_000, durationMs: 5_880_000, finished: false, title: 'Signal faible', subtitle: null, image: pic('backdrop', '108') });
  return state;
}

function newSession(user, deviceName) {
  const token = crypto.randomBytes(32).toString('base64url');
  const s = { id: crypto.randomUUID(), token, userId: user.id, deviceName: deviceName || 'Navigateur', createdAt: Date.now(), lastUsedAt: Date.now() };
  sessions.set(token, s);
  return s;
}

function seedDevices(user) {
  const day = 86_400_000;
  for (const [name, ago] of [['Salon TV', 2 * 3_600_000], ['PC du bureau', 3 * day], ['Galaxy Tab', 12 * day]]) {
    const s = newSession(user, name);
    s.lastUsedAt = Date.now() - ago;
    s.createdAt = s.lastUsedAt - 20 * day;
  }
}

// -------------------------------------------------------------------------------------------------------- media

function ensureMedia() {
  const playlist = path.join(MEDIA, 'pattern.m3u8');
  if (fs.existsSync(playlist)) return true;
  fs.mkdirSync(MEDIA, { recursive: true });
  const r = spawnSync('ffmpeg', [
    '-hide_banner', '-loglevel', 'error', '-y',
    '-f', 'lavfi', '-i', 'testsrc2=size=1280x720:rate=25:duration=30',
    '-f', 'lavfi', '-i', 'sine=frequency=440:duration=30',
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-preset', 'veryfast', '-g', '50', '-c:a', 'aac', '-b:a', '96k',
    '-hls_time', '4', '-hls_playlist_type', 'vod', '-hls_segment_filename', path.join(MEDIA, 'seg%02d.ts'), playlist,
  ], { stdio: 'inherit' });
  if (r.status !== 0) return false;
  fs.writeFileSync(path.join(MEDIA, 'fr.vtt'), 'WEBVTT\n\n00:00:01.000 --> 00:00:06.000\nSous-titre de démonstration.\n\n00:00:08.000 --> 00:00:14.000\nLa vidéo est une mire de test.\n');
  fs.writeFileSync(path.join(MEDIA, 'en.vtt'), 'WEBVTT\n\n00:00:01.000 --> 00:00:06.000\nDemo subtitle.\n\n00:00:08.000 --> 00:00:14.000\nThe video is a test pattern.\n');
  return true;
}
const hasMedia = (() => { try { return ensureMedia(); } catch { return false; } })();

// --------------------------------------------------------------------------------------------------------- routing

function send(res, status, body, headers = {}) {
  const data = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...headers });
  res.end(status === 204 ? undefined : data);
}

async function readJson(req) {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}'); } catch { return {}; }
}

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

const mockGuides = [
  ['iptv', 'IPTV'], ['realdebrid', 'Real-Debrid'], ['opensubtitles', 'OpenSubtitles'],
  ['subdl', 'Subdl'], ['mainapi', 'Serveur Sokoul'], ['tmdb', 'TMDB'], ['omdb', 'OMDb'],
  ['fanart', 'fanart.tv'], ['tvdb', 'TheTVDB'], ['tvmaze', 'TVmaze'], ['watchmode', 'Watchmode'],
  ['tastedive', 'TasteDive'], ['motn', 'Movie of the Night'],
].map(([id, name]) => ({
  id, name, editable: !['tvmaze', 'mainapi'].includes(id),
  adds: `Configurer ${name} dans Sokoul.`, steps: ['Saisissez un identifiant de démonstration.'],
  privacy: 'Ce serveur de développement conserve uniquement le masque.',
}));
const mockCredentials = new Map();
const mockChannel = { id: 'arena', name: 'Arena', group: 'Sports' };
const mockEvent = { id: 'streamed_arena', name: 'Match de démonstration', sport: 'Football', live: true };

async function api(req, res, url) {
  const p = url.pathname;
  await delay(Number(process.env.MOCK_DELAY_MS || 250));

  if (p === '/api/auth/login' && req.method === 'POST') {
    const { name, passphrase, deviceName } = await readJson(req);
    if (!name || !passphrase || String(passphrase).length < 12) return send(res, 401, { error: 'invalid_credentials' });
    const lower = String(name).trim().toLowerCase();
    let user = users.get(lower);
    if (!user) {
      user = { id: crypto.randomUUID(), name: String(name).trim(), role: users.size === 0 ? 'owner' : 'member' };
      users.set(lower, user);
      sync.set(user.id, seedSync(user));
      seedDevices(user);
    }
    const s = newSession(user, deviceName);
    return send(res, 200, { token: s.token, sessionId: s.id, user });
  }

  const token = (req.headers.authorization || '').replace(/^Bearer /, '');
  const s = sessions.get(token);
  if (!s) return send(res, 401, { error: 'unauthorized' });
  s.lastUsedAt = Date.now();
  const user = [...users.values()].find((u) => u.id === s.userId);

  if (p === '/api/me') return send(res, 200, user);
  if (p === '/api/auth/logout' && req.method === 'POST') { sessions.delete(token); return send(res, 204); }
  if (p === '/api/sessions') {
    const list = [...sessions.values()].filter((x) => x.userId === user.id).sort((a, b) => b.lastUsedAt - a.lastUsedAt)
      .map((x) => ({ id: x.id, deviceName: x.deviceName, createdAt: x.createdAt, lastUsedAt: x.lastUsedAt, current: x.token === token }));
    return send(res, 200, { sessions: list });
  }
  const del = p.match(/^\/api\/sessions\/([\w-]+)$/);
  if (del && req.method === 'DELETE') {
    const victim = [...sessions.values()].find((x) => x.id === del[1] && x.userId === user.id);
    if (!victim) return send(res, 404, { error: 'not_found' });
    sessions.delete(victim.token);
    return send(res, 204);
  }

  if (p === '/api/sync') {
    const state = sync.get(user.id);
    if (req.method === 'GET') {
      const since = Number(url.searchParams.get('since') || 0);
      if (since > state.seq) return send(res, 409, { error: 'cursor_ahead' });
      const changes = [...state.items.values()].filter((c) => c.seq > since).sort((a, b) => a.seq - b.seq);
      return send(res, 200, { changes, cursor: changes.length ? changes[changes.length - 1].seq : since, more: false });
    }
    const { changes = [] } = await readJson(req);
    let applied = 0;
    let ignored = 0;
    for (const c of changes) {
      const k = `${c.type}|${c.profile}|${c.key}`;
      const old = state.items.get(k);
      if (old && old.updatedAt >= c.updatedAt) { ignored++; continue; }
      state.items.set(k, { ...c, seq: ++state.seq });
      applied++;
    }
    console.log(`  sync: applied ${applied}, ignored ${ignored}`);
    return send(res, 200, { applied, ignored, cursor: state.seq });
  }

  if (p === '/api/catalog/home') {
    const movies = items.filter((i) => i.kind === 'movie');
    const series = items.filter((i) => i.kind === 'tv');
    return send(res, 200, {
      rows: [
        { id: 'trending', title: 'Tendances de la semaine', items: [movies[1], series[0], movies[0], series[1], movies[3], movies[7], series[2], movies[5]].map(toItem) },
        { id: 'movies', title: 'Films populaires', items: movies.map(toItem) },
        { id: 'series', title: 'Séries populaires', items: series.map(toItem) },
        { id: 'new', title: 'Nouveautés', items: [...items].sort((a, b) => b.year - a.year).map(toItem) },
      ],
    });
  }
  const section = p.match(/^\/api\/catalog\/sections\/(home|movies|series|anime)(?:\/rows\/(top-movie|top-tv))?$/);
  if (section) {
    if (section[2]) {
      const kind = section[2] === 'top-movie' ? 'movie' : 'tv';
      return send(res, 200, { items: items.filter((item) => item.kind === kind).map(toItem), more: false });
    }
    const mode = ['series', 'anime'].includes(section[1]) ? 'tv' : 'movie';
    return send(res, 200, [
      { id: 'top-' + mode, title: 'À la une', style: 'Top10' },
      { id: 'platforms', title: 'Explorer', style: 'Poster', links: [{ title: 'Collections', route: 'collections' }] },
    ]);
  }
  if (p === '/api/catalog/followed/news' || p === '/api/catalog/animation/calendar') return send(res, 200, []);
  const t = p.match(/^\/api\/catalog\/title\/(movie|tv)\/([\w-]+)$/);
  if (t) {
    const it = byKey.get(`${t[1]}/${t[2]}`);
    return it ? send(res, 200, title(it)) : send(res, 404, { error: 'not_found' });
  }
  const se = p.match(/^\/api\/catalog\/season\/([\w-]+)\/(\d+)$/);
  if (se) return send(res, 200, season(se[1], Number(se[2])));
  if (p === '/api/catalog/search') {
    const q = (url.searchParams.get('q') || '').trim().toLowerCase();
    const norm = (s) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
    return send(res, 200, { items: q ? items.filter((i) => norm(i.title).includes(norm(q))).map(toItem) : [] });
  }
  const browse = p.match(/^\/api\/catalog\/browse\/(movies|series|anime)$/);
  if (browse) {
    return send(res, 200, { items: items.filter((item) => item.kind === (browse[1] === 'movies' ? 'movie' : 'tv')).map(toItem), more: false });
  }
  if (p === '/api/catalog/collections') return send(res, 200, {
    items: [{ id: 'demo', title: 'Films de démonstration' }], more: false,
  });
  if (p === '/api/catalog/collections/demo') return send(res, 200, {
    title: 'Films de démonstration', items: items.filter((item) => item.kind === 'movie').map(toItem),
  });
  if (p === '/api/live/channels') return send(res, 200, { items: [mockChannel], groups: [mockChannel.group], more: false });
  if (p === '/api/live/guide/arena') return send(res, 200, []);
  if (p === '/api/live/guide' && req.method === 'POST') return send(res, 200, [{ channel: mockChannel, programs: [] }]);
  if (p === '/api/sports/events') return send(res, 200, [mockEvent]);
  if (p === '/api/sports/scores') return send(res, 200, [{
    title: mockEvent.name, league: 'Football', start: Date.now(), state: 'Live', homeScore: '1', awayScore: '0',
  }]);
  if (p === '/api/sports/events/streamed_arena/servers') return send(res, 200, [{ key: 'demo', title: 'Démo HLS' }]);
  if (p === '/api/live/start' || p === '/api/sports/events/streamed_arena/start') {
    return send(res, 200, { url: '/dev/media/pattern.m3u8', type: 'hls', subtitles: [] });
  }

  if (p === '/api/play/versions') {
    // "Ciel de cendre" shows what happens when the account lacks a key.
    if (url.searchParams.get('id') === '112') return send(res, 424, { error: 'needs_key', service: 'realdebrid' });
    return send(res, 200, {
      versions: [
        { id: 'v1', label: '1080p · VF', quality: '1080p', language: 'VF', origin: 'Real-Debrid' },
        { id: 'v2', label: '2160p · MULTI', quality: '2160p', language: 'MULTI', origin: 'Real-Debrid' },
        { id: 'v3', label: '720p · VOSTFR', quality: '720p', language: 'VOSTFR', origin: 'Source web' },
      ],
    });
  }
  if (p === '/api/play/start' && req.method === 'POST') {
    const { versionId } = await readJson(req);
    return send(res, 200, {
      url: hasMedia ? '/dev/media/pattern.m3u8' : '/dev/media/missing.m3u8',
      subtitles: [
        { url: '/dev/media/fr.vtt', label: 'Français', language: 'fr' },
        { url: '/dev/media/en.vtt', label: 'English', language: 'en' },
      ],
      remux: versionId !== 'v1',
    });
  }
  if (p === '/api/service-guides') return send(res, 200, mockGuides);
  if (!mockCredentials.has(user.id)) {
    const day = 86_400_000;
    mockCredentials.set(user.id, new Map([
      { service: 'tmdb', hint: '•••• 4f2a', verdict: 'accepted', checkedAt: Date.now() - 2 * day },
      { service: 'realdebrid', hint: '•••• 9KQ7', verdict: 'accepted', checkedAt: Date.now() - day },
      { service: 'iptv', hint: '•••• ab12', verdict: 'unreachable', checkedAt: Date.now() - 5 * day },
      { service: 'opensubtitles', hint: '•••• 77', verdict: 'refused', checkedAt: Date.now() - 9 * day },
    ].map((entry) => [entry.service, entry])));
  }
  const credentials = mockCredentials.get(user.id);
  if (p === '/api/credentials') return send(res, 200, [...credentials.values()]);
  const credential = p.match(/^\/api\/credentials\/([a-z]+)$/);
  if (credential && mockGuides.some((guide) => guide.id === credential[1] && guide.editable)) {
    const service = credential[1];
    if (req.method === 'DELETE') return credentials.delete(service) ? send(res, 204) : send(res, 404, { error: 'not_found' });
    if (req.method === 'PUT') {
      const { value } = await readJson(req);
      if (typeof value !== 'string' || !value.trim()) return send(res, 400, { error: 'bad_format' });
      const entry = { service, hint: '•••• DEMO', verdict: 'accepted', checkedAt: Date.now() };
      credentials.set(service, entry);
      return send(res, 200, entry);
    }
  }
  return send(res, 404, { error: 'not_found' });
}

function serveFile(res, file, extraHeaders = {}) {
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); res.end('not found'); return; }
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache', ...extraHeaders });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const p = decodeURIComponent(url.pathname);
  try {
    if (p.startsWith('/api/')) return await api(req, res, url);
    const img = p.match(/^\/dev\/img\/(poster|backdrop|still|person)\/([\w-]+)\.svg$/);
    if (img) {
      res.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'max-age=3600' });
      return res.end(svgPicture(img[1], img[2]));
    }
    if (p.startsWith('/dev/media/')) return serveFile(res, path.join(MEDIA, path.basename(p)));
    if (p === '/link') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end('<!doctype html><meta charset="utf-8"><title>/link</title><p>Ici, le vrai serveur affiche sa page /link.</p>');
    }
    const rel = p === '/' ? 'index.html' : p.replace(/^\/+/, '');
    const file = path.resolve(WEB, rel);
    if (!file.startsWith(WEB) || file.includes(`${path.sep}dev${path.sep}`)) { res.writeHead(404); return res.end(); }
    return serveFile(res, file, file.endsWith('index.html') ? { 'Content-Security-Policy': CSP, 'Referrer-Policy': 'no-referrer' } : {});
  } catch (e) {
    console.error(e);
    send(res, 500, { error: 'internal' });
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Sokoul web mock on http://127.0.0.1:${PORT}/  (media: ${hasMedia ? 'test pattern ready' : 'none, ffmpeg missing'})`);
});
