// What the client knows about the signed-in account: the user, its synced profiles and resume points.
// Everything comes from GET /api/sync (paged from cursor 0 on start, then from the last cursor); resume points this
// browser makes are pushed with POST /api/sync, the same change shape the apps use.

import { api, ApiError } from './api.js';
import { nameUuid, sha256Hex } from './hash.js';

const PROFILE_KEY = 'sokoul.profile';

const state = {
  user: null,
  cursor: 0,
  loaded: false,
  /** uuid → profile change */
  profiles: new Map(),
  /** `${profile}|${key}` → resume change */
  resume: new Map(),
  myList: new Map(),
  alerts: new Map(),
};
let generation = 0;

function remember(key, value) {
  try {
    if (value) localStorage.setItem(key, value);
    else localStorage.removeItem(key);
  } catch { /* storage refused */ }
}

function recall(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}

function apply(change) {
  if (change.type === 'profile') {
    const old = state.profiles.get(change.profile);
    if (!old || old.updatedAt <= change.updatedAt) state.profiles.set(change.profile, change);
  } else if (change.type === 'resume' || change.type === 'my_list' || change.type === 'title_alert') {
    const entries = { resume: state.resume, my_list: state.myList, title_alert: state.alerts }[change.type];
    const k = `${change.profile}|${change.key}`;
    const old = entries.get(k);
    if (old && old.updatedAt > change.updatedAt) return;
    entries.set(k, change);
  }
}

/** Pulls every change after the stored cursor. A replaced server database (409) starts again from 0. */
export async function pull() {
  const current = generation;
  for (let page = 0; page < 500; page++) {
    let body;
    try {
      body = await api.syncPull(state.cursor);
    } catch (e) {
      if (current !== generation) return;
      if (e instanceof ApiError && e.code === 'cursor_ahead') {
        state.cursor = 0;
        state.profiles.clear();
        state.resume.clear();
        state.myList.clear();
        state.alerts.clear();
        continue;
      }
      throw e;
    }
    if (current !== generation) return;
    (body.changes || []).forEach(apply);
    state.cursor = Math.max(state.cursor, body.cursor ?? 0);
    if (!body.more) break;
  }
  state.loaded = true;
}

/** Loads the user and the synced data once per session (again after [force]). */
export async function load(force = false) {
  if (state.loaded && state.user && !force) return;
  state.user = await api.me();
  await pull();
}

export function reset() {
  generation++;
  state.user = null;
  state.cursor = 0;
  state.loaded = false;
  state.profiles.clear();
  state.resume.clear();
  state.myList.clear();
  state.alerts.clear();
  pending.clear();
  remember(PROFILE_KEY, null);
}

export const user = () => state.user;

/** The account profile every device shares (Java UUID.nameUUIDFromBytes of "sokoul-main-profile:<user id>"). */
export const mainProfileId = () => (state.user ? nameUuid(`sokoul-main-profile:${state.user.id}`) : null);

/** The synced profiles, the account profile first; one account profile when nothing is synced yet. */
export function profiles() {
  const main = mainProfileId();
  const list = [...state.profiles.values()].filter((p) => !p.deleted).map((p) => ({
    id: p.profile,
    name: p.name || 'Sokoul',
    avatar: p.avatar || 0,
    kids: Boolean(p.kids),
    pinHash: p.pinHash || null,
    pinSalt: p.pinSalt || p.profile,
  }));
  if (main && !state.profiles.has(main)) {
    list.unshift({ id: main, name: state.user?.name || 'Sokoul', avatar: 0, kids: false, pinHash: null, pinSalt: main });
  }
  list.sort((a, b) => (a.id === main ? -1 : b.id === main ? 1 : 0));
  return list;
}

export function pinMatches(profile, code) {
  return !profile.pinHash || profile.pinHash === sha256Hex(`sokoul-profile:${profile.pinSalt}:${code}`);
}

export function currentProfile() {
  const id = recall(PROFILE_KEY);
  return profiles().find((p) => p.id === id) || null;
}

const ADULT_RATINGS = new Set(['16', '18', '-16', '-18', 'R', 'NC-17', 'TV-MA', 'X']);

export function canPlayCertification(certification) {
  return !currentProfile()?.kids || !ADULT_RATINGS.has(certification?.trim().toUpperCase());
}

export function chooseProfile(id) {
  remember(PROFILE_KEY, id);
}

function profileId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const value = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  return value.replace(/^(.{8})(.{4})(.{4})(.{4})(.+)$/, '$1-$2-$3-$4-$5');
}

const laterThan = (entry) => Math.max(Date.now(), (entry?.updatedAt || 0) + 1);

async function saveChanges(changes) {
  const current = generation;
  const result = await api.syncPush(changes);
  if (current !== generation) return;
  if (result.applied === changes.length) changes.forEach(apply);
  else await pull();
}

export async function saveProfile(profile, { name, avatar, kids, pin }) {
  const id = profile?.id || profileId();
  const salt = profile?.pinSalt || id;
  const pinHash = pin === undefined ? profile?.pinHash :
    pin ? sha256Hex(`sokoul-profile:${salt}:${pin}`) : null;
  await saveChanges([{
    type: 'profile', profile: id, key: id, updatedAt: laterThan(state.profiles.get(id)),
    name, avatar, kids, pinHash, pinSalt: pinHash ? salt : null,
  }]);
  return id;
}

export async function removeProfile(profile) {
  if (profiles().length < 2) return;
  await saveChanges([{
    type: 'profile', profile: profile.id, key: profile.id,
    updatedAt: laterThan(state.profiles.get(profile.id)), deleted: true,
  }]);
  if (recall(PROFILE_KEY) === profile.id) remember(PROFILE_KEY, null);
}

/** The app's resume key: "tv:<id>:<s>:<e>" for an episode, "<kind>:<id>" otherwise. */
export function resumeKey(kind, tmdbId, season, episode) {
  return kind === 'tv' && season != null && episode != null ? `tv:${tmdbId}:${season}:${episode}` : `${kind}:${tmdbId}`;
}

export function resumeOf(key) {
  const p = currentProfile();
  const entry = p && state.resume.get(`${p.id}|${key}`);
  return entry && !entry.deleted ? entry : null;
}

/** The latest unfinished resume point of a series, or of a film. */
export function latestResumeFor(kind, tmdbId) {
  const p = currentProfile();
  if (!p) return null;
  let best = null;
  for (const r of state.resume.values()) {
    if (r.deleted || r.profile !== p.id || r.kind !== kind || Number(r.tmdbId) !== Number(tmdbId)) continue;
    if (!best || r.updatedAt > best.updatedAt) best = r;
  }
  return best;
}

/** Unfinished resume points of the current profile, most recent first. */
export function continueWatching(limit = 20) {
  const p = currentProfile();
  if (!p) return [];
  return [...state.resume.values()]
    .filter((r) => !r.deleted && r.profile === p.id && !r.finished && (r.positionMs || 0) > 0)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, limit);
}

export function viewingHistory() {
  const id = currentProfile()?.id;
  return [...state.resume.values()].filter((r) => !r.deleted && r.profile === id)
    .sort((a, b) => b.updatedAt - a.updatedAt);
}

export async function forgetHistory(entries) {
  const active = currentProfile()?.id;
  const owned = entries.filter((r) => r.profile === active);
  const current = generation;
  for (let i = 0; i < owned.length && current === generation; i += 250) {
    const changes = owned.slice(i, i + 250).map((r) => {
      pending.delete(`${active}|${r.key}`);
      return { type: 'resume', profile: active, key: r.key, updatedAt: laterThan(r), deleted: true };
    });
    await saveChanges(changes);
  }
}

export function follows(kind, id) {
  const entry = state.alerts.get(`${currentProfile()?.id}|${kind}:${id}`);
  return Boolean(entry && !entry.deleted);
}

export function followedTitles() {
  const active = currentProfile()?.id;
  return [...state.alerts.values()].filter((row) => row.profile === active && !row.deleted)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .map((row) => ({ ...row, id: row.tmdbId, poster: row.image }));
}

export async function toggleFollow(item) {
  const profile = currentProfile()?.id;
  if (!profile) return;
  const key = `${item.kind}:${item.id}`;
  const old = state.alerts.get(`${profile}|${key}`);
  const change = {
    type: 'title_alert', profile, key, updatedAt: laterThan(old),
    deleted: Boolean(old && !old.deleted), kind: item.kind, tmdbId: Number(item.id),
    title: item.title?.slice(0, 300), image: item.poster || item.backdrop,
    told: item.alertMarker,
  };
  await saveChanges([change]);
}

export async function markFollowedNews(item) {
  const profile = currentProfile()?.id;
  const entry = state.alerts.get(`${profile}|${item.kind}:${item.id}`);
  if (!entry || entry.deleted || entry.told === item.marker) return;
  await saveChanges([{ ...entry, told: item.marker, updatedAt: laterThan(entry) }]);
}

export function myList() {
  const profile = currentProfile()?.id;
  return [...state.myList.values()].filter((item) => item.profile === profile && !item.deleted)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .map((item) => ({
      ...item,
      id: item.tmdbId,
      poster: item.poster?.startsWith('/') ? 'https://image.tmdb.org/t/p/w342' + item.poster : item.poster,
    }));
}

export function inMyList(kind, id) {
  const entry = state.myList.get(`${currentProfile()?.id}|${kind}:${id}`);
  return Boolean(entry && !entry.deleted);
}

export async function toggleMyList(item) {
  const profile = currentProfile()?.id;
  if (!profile) return;
  const key = `${item.kind}:${item.id}`;
  const old = state.myList.get(`${profile}|${key}`);
  const change = {
    type: 'my_list', profile, key, updatedAt: laterThan(old),
    deleted: inMyList(item.kind, item.id), kind: item.kind, tmdbId: Number(item.id),
    title: item.title?.slice(0, 300), poster: item.poster, backdrop: item.backdrop,
    overview: item.overview?.slice(0, 4000), year: item.year,
  };
  await saveChanges([change]);
}

/** Resume points not yet accepted by the server, kept for the next push. */
const pending = new Map();

/**
 * Records a resume point locally and pushes it (with keepalive when [leaving], so the request outlives the page).
 * A failed push keeps the change for the next one.
 */
export async function saveResume({ kind, tmdbId, season, episode, positionMs, durationMs, title, subtitle, image }, leaving = false) {
  const p = currentProfile();
  if (!p || !durationMs || !Number.isFinite(durationMs)) return;
  const key = resumeKey(kind, tmdbId, season, episode);
  const change = {
    type: 'resume',
    profile: p.id,
    key,
    updatedAt: Date.now(),
    deleted: false,
    kind,
    tmdbId: Number(tmdbId),
    season: season ?? null,
    episode: episode ?? null,
    positionMs: Math.max(0, Math.round(positionMs)),
    durationMs: Math.round(durationMs),
    finished: positionMs / durationMs >= 0.92,
    title: (title || '').slice(0, 300),
    subtitle: subtitle ? subtitle.slice(0, 300) : null,
    image: image ? image.slice(0, 1000) : null,
  };
  apply(change);
  pending.set(`${p.id}|${key}`, change);
  await flush(leaving);
}

export async function flush(leaving = false) {
  if (!pending.size) return;
  const changes = [...pending.values()];
  pending.clear();
  const current = generation;
  try {
    await api.syncPush(changes, { keepalive: leaving });
  } catch {
    if (current !== generation) return;
    changes.forEach((c) => {
      const k = `${c.profile}|${c.key}`;
      if (!pending.has(k)) pending.set(k, c);
    });
  }
}
