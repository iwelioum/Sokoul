/**
 * Browser origins allowed to call this private Sokoul server with credentials.
 *
 * One list, shared by the CORS middleware and the Origin/Referer restriction.
 * It comes from `SOKOUL_ALLOWED_ORIGINS` (comma separated). Each entry is either
 * a full origin (`https://sokoul.example`, `http://192.168.0.10:8088`), matched
 * exactly on scheme, host and port, or a bare host (`sokoul.example`), matched on
 * that host only, any scheme or port. Subdomains are never implied.
 *
 * Loopback origins (localhost, 127.0.0.1, [::1]) on any port are always allowed;
 * without the variable they are the only ones.
 */

const ENV_NAME = 'SOKOUL_ALLOWED_ORIGINS';
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

function parseEntry(raw) {
  const value = String(raw || '').trim().toLowerCase().replace(/\/+$/, '');
  if (!value) return null;
  if (/^[a-z][a-z0-9+.-]*:\/\//.test(value)) {
    try {
      const url = new URL(value);
      if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
      return { origin: url.origin };
    } catch {
      return null;
    }
  }
  if (/^[a-z0-9.-]+$|^\[[0-9a-f:]+\]$/.test(value)) return { host: value };
  return null;
}

function readAllowedOrigins(env = process.env) {
  const raw = String(env[ENV_NAME] || '').trim();
  if (!raw) return [];
  return raw.split(',').map(parseEntry).filter(Boolean);
}

/**
 * True when `value` (an Origin header or any URL, e.g. a Referer) belongs to an
 * allowed origin. Unparseable values and the literal `null` origin are refused.
 */
function isAllowedOrigin(value, env = process.env) {
  if (!value || value === 'null') return false;
  let url;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return false;
  const hostname = url.hostname.toLowerCase();
  if (LOOPBACK_HOSTS.has(hostname)) return true;
  const entries = readAllowedOrigins(env);
  const origin = url.origin.toLowerCase();
  return entries.some((entry) => (entry.origin ? entry.origin === origin : entry.host === hostname));
}

module.exports = {
  ENV_NAME,
  isAllowedOrigin,
  readAllowedOrigins,
};
