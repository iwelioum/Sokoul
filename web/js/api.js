// The Sokoul server's JSON API. The session token lives in localStorage under "sokoul.session" (the same key as the
// server's /link page, so signing in on one signs in on the other). A 401 on any call but sign-in clears it and
// sends the person back to the sign-in screen.

const TOKEN_KEY = 'sokoul.session';

/** An error answer: [status] is the HTTP status (0 when the server did not answer), [code] the server's stable code. */
export class ApiError extends Error {
  constructor(status, code, body = {}) {
    super(code);
    this.status = status;
    this.code = code;
    this.body = body;
  }

  get needsKey() { return this.code === 'needs_key'; }
  get offline() { return this.status === 0; }
}

function readToken() {
  try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
}

function writeToken(value) {
  try {
    if (value) localStorage.setItem(TOKEN_KEY, value);
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* private mode: the session lasts as long as the page */ }
}

let token = readToken();
let onUnauthorized = () => {};

export const session = {
  get signedIn() { return Boolean(token); },
  set(value) { token = value || null; writeToken(token); },
  clear() { token = null; writeToken(null); },
  /** Called once with the handler for a 401 (the app routes to sign-in). */
  onUnauthorized(fn) { onUnauthorized = fn; },
};

async function request(method, path, body, { auth = true, signal, keepalive = false } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
      cache: 'no-store',
      signal,
      keepalive,
    });
  } catch (e) {
    if (e && e.name === 'AbortError') throw e;
    throw new ApiError(0, 'offline');
  }
  if (res.status === 204) return null;
  let data = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }
  // A missing key can come with any status; the body says so.
  if (data && typeof data === 'object' && !Array.isArray(data) && data.error === 'needs_key') {
    throw new ApiError(res.status, 'needs_key', data);
  }
  if (!res.ok) {
    const code = (data && data.error) || `http_${res.status}`;
    if (res.status === 401 && auth) {
      session.clear();
      onUnauthorized();
    }
    throw new ApiError(res.status, code, data || {});
  }
  return data;
}

const q = (params) => {
  const s = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') s.set(k, String(v));
  const str = s.toString();
  return str ? `?${str}` : '';
};

const enc = encodeURIComponent;

export const api = {
  login: (name, passphrase, deviceName) => request('POST', '/api/auth/login', { name, passphrase, deviceName }, { auth: false }),
  logout: () => request('POST', '/api/auth/logout'),
  me: () => request('GET', '/api/me'),
  sessions: () => request('GET', '/api/sessions'),
  revokeSession: (id) => request('DELETE', `/api/sessions/${enc(id)}`),
  syncPull: (since) => request('GET', `/api/sync${q({ since })}`),
  syncPush: (changes, opts) => request('POST', '/api/sync', { changes }, opts),
  home: (profile, opts) => request('GET', `/api/catalog/home${q({ profile })}`, undefined, opts),
  title: (kind, id, opts) => request('GET', `/api/catalog/title/${enc(kind)}/${enc(id)}`, undefined, opts),
  season: (id, season, opts) => request('GET', `/api/catalog/season/${enc(id)}/${enc(season)}`, undefined, opts),
  search: (text, opts) => request('GET', `/api/catalog/search${q({ q: text })}`, undefined, opts),
  versions: (params, opts) => request('GET', `/api/play/versions${q(params)}`, undefined, opts),
  start: (versionId) => request('POST', '/api/play/start', { versionId }),
  credentials: () => request('GET', '/api/credentials'),
};
