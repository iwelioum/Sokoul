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
};

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
    if (change.deleted) state.profiles.delete(change.profile);
    else state.profiles.set(change.profile, change);
  } else if (change.type === 'resume') {
    const k = `${change.profile}|${change.key}`;
    const old = state.resume.get(k);
    if (old && old.updatedAt > change.updatedAt) return;
    if (change.deleted) state.resume.delete(k);
    else state.resume.set(k, change);
  }
}

/** Pulls every change after the stored cursor. A replaced server database (409) starts again from 0. */
export async function pull() {
  for (let page = 0; page < 500; page++) {
    let body;
    try {
      body = await api.syncPull(state.cursor);
    } catch (e) {
      if (e instanceof ApiError && e.code === 'cursor_ahead') {
        state.cursor = 0;
        state.profiles.clear();
        state.resume.clear();
        continue;
      }
      throw e;
    }
    (body.changes || []).forEach(apply);
    state.cursor = body.cursor ?? state.cursor;
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
  state.user = null;
  state.cursor = 0;
  state.loaded = false;
  state.profiles.clear();
  state.resume.clear();
  remember(PROFILE_KEY, null);
}

export const user = () => state.user;

/** The account profile every device shares (Java UUID.nameUUIDFromBytes of "sokoul-main-profile:<user id>"). */
export const mainProfileId = () => (state.user ? nameUuid(`sokoul-main-profile:${state.user.id}`) : null);

/** The synced profiles, the account profile first; one account profile when nothing is synced yet. */
export function profiles() {
  const main = mainProfileId();
  const list = [...state.profiles.values()].map((p) => ({
    id: p.profile,
    name: p.name || 'Sokoul',
    avatar: p.avatar || 0,
    kids: Boolean(p.kids),
    pinHash: p.pinHash || null,
    pinSalt: p.pinSalt || p.profile,
  }));
  if (main && !list.some((p) => p.id === main)) {
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

export function chooseProfile(id) {
  remember(PROFILE_KEY, id);
}

/** The app's resume key: "tv:<id>:<s>:<e>" for an episode, "<kind>:<id>" otherwise. */
export function resumeKey(kind, tmdbId, season, episode) {
  return kind === 'tv' && season != null && episode != null ? `tv:${tmdbId}:${season}:${episode}` : `${kind}:${tmdbId}`;
}

export function resumeOf(key) {
  const p = currentProfile();
  return p ? state.resume.get(`${p.id}|${key}`) || null : null;
}

/** The latest unfinished resume point of a series, or of a film. */
export function latestResumeFor(kind, tmdbId) {
  const p = currentProfile();
  if (!p) return null;
  let best = null;
  for (const r of state.resume.values()) {
    if (r.profile !== p.id || r.kind !== kind || Number(r.tmdbId) !== Number(tmdbId)) continue;
    if (!best || r.updatedAt > best.updatedAt) best = r;
  }
  return best;
}

/** Unfinished resume points of the current profile, most recent first. */
export function continueWatching(limit = 20) {
  const p = currentProfile();
  if (!p) return [];
  return [...state.resume.values()]
    .filter((r) => r.profile === p.id && !r.finished && (r.positionMs || 0) > 0)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, limit);
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
  try {
    await api.syncPush(changes, { keepalive: leaving });
  } catch {
    changes.forEach((c) => {
      const k = `${c.profile}|${c.key}`;
      if (!pending.has(k)) pending.set(k, c);
    });
  }
}
