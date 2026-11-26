const KEY = 'sokoul.web.preferences';
const defaults = { rows: {}, menu: {}, language: '', autoplayNext: true, subtitles: '', audio: 'french', seek: 10 };

function saved() {
  try { return JSON.parse(localStorage.getItem(KEY) || '{}'); } catch { return {}; }
}

export function preferences() {
  return { ...defaults, ...saved() };
}

export function setPreference(name, value) {
  try { localStorage.setItem(KEY, JSON.stringify({ ...saved(), [name]: value })); } catch { /* storage can be disabled */ }
}

export function orderRows(rows, section) {
  const config = preferences().rows?.[section];
  const byId = new Map(rows.map((row) => [row.id, row]));
  if (!config) return rows;
  const order = [...new Set([...(config.order || []), ...byId.keys()])];
  const hidden = new Set(config.hidden || []);
  return order.filter((id) => !hidden.has(id)).map((id) => byId.get(id)).filter(Boolean);
}
