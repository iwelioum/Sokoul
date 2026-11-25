# Mainapi

Derived third-party code (CC BY-NC 4.0): see docs/third-party/README.md.

Sokoul's main server (port 25565). It handles accounts and profiles, sync of user state, catalogue
and source routes, native extraction, live TV, and the inherited community features (comments, likes, shared
lists, wishboard, Top 10, wrapped, link submissions, VIP).

`server.js` runs a cluster: the primary starts `NUM_WORKERS` workers, restarts them and shuts down gracefully. `app.js`
sets up Express, Redis, the MySQL pool, disk caches and the routes, which receive their dependencies through
`configure(deps)`.

## Running

The usual way is the Docker stack started by [`../start-backend.ps1`](../README.md). To run the service alone:

```powershell
cd API/Mainapi
copy .env.example .env    # fill it in: MySQL, Redis, JWT and TMDB are required
npm install
npm run dev               # http://localhost:25565
```

## Layout

```text
API/Mainapi/
|-- server.js                 # cluster primary, workers, graceful shutdown
|-- app.js                    # Express bootstrap, middleware, shared dependencies
|-- mysqlPool.js              # single MySQL pool
|-- config/                   # Redis and other clients
|-- middleware/               # CORS, security, auth
|-- routes/                   # route modules exposing configure(deps); tests in routes/__tests__
|-- services/                 # Anime-Sama catalogue, KissKH, media segments, native extractors
|-- utils/                    # caches, proxies, HTTP helpers, history, VIP; tests in utils/__tests__
|-- db/schema/                # tables created at start-up
|-- *Routes.js                # comments, likes, shared lists, live TV, wishboard, Top 10, wrapped, link submissions
|-- test-fixtures/            # inputs of the KissKH and Streamed tests
`-- cache/                    # provider answers cached on disk (not versioned)
```

## Environment

`.env.example` is the complete reference. The groups to fill in first:

| Group | Variables |
| --- | --- |
| Core | `JWT_SECRET`, `TMDB_API_KEY`, `FRONTEND_BASE_URL` |
| Data | `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` |
| Cache | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `NUM_WORKERS` |
| Outbound routes | `PROXY_SERVER_URL`, `IPTV_STREAM_PROXY`, `SOCKS5_PROXIES`, `HTTP_PROXIES` |
| Anti-abuse | `TURNSTILE_SECRET_KEY`, `TURNSTILE_INVISIBLE_SECRETKEY`, and `TURNSTILE_DOMAINS_n` / `TURNSTILE_SECRET_KEY_n` / `TURNSTILE_INVISIBLE_SECRETKEY_n` (n = 2–10); the key follows the request's `Origin` (`utils/turnstile.js`) |
| VIP | `VIP_*`, `BLOCKCYPHER_TOKEN` |

Some providers need their own values (`FSTREAM_BASE_URL`, `FSTREAM_LOGIN_*`, `DARKIWORLD_*`, `XTREAM_*`). Source results are
cached in Redis (or in the worker's memory without Redis) and go out through `SOCKS5_PROXIES` / `HTTP_PROXIES`. A Cpasmal
route without a cached answer replies `202` with `Retry-After`: call the same URL again.

The download-links source (`routes/darkiworld.js`, `utils/darkiworldSqlite.js`) reads local SQLite snapshots
(`mirror.sqlite`, `darkino.sqlite`, `links_small.sqlite`) from `API/Mainapi/darkino-backups/` or `DARKIWORLD_SQLITE_DIR`.
They are not in the repository. Without them, links missing from the local cache answer `sqlite_miss`; with
`HYDRACKER_BLACKOUT=true` (the default) nothing is resolved live instead.

## Routes worth knowing

| Area | Files |
| --- | --- |
| Accounts and profiles | `routes/authRoutes.js`, `routes/sessions.js`, `routes/profiles.js` |
| Client state sync | `routes/sync.js` |
| History and progress | `routes/history.js`, `utils/historyStore.js`, `utils/historyOverview.js`, `utils/historyImport.js`, `utils/mediaStore.js` |
| Search and catalogue | `routes/search.js`, `routes/tmdb.js` |
| Sources and playback | `routes/cpasmal.js`, `routes/fstream.js`, `routes/wiflix.js`, `routes/kisskh.js`, `liveTvRoutes.js`, `services/nativeExtractors/` |
| Community | `commentsRoutes.js`, `likesRoutes.js`, `sharedListsRoutes.js`, `wishboardRoutes.js`, `linkSubmissionsRoutes.js` |
| TV linking | `routes/tvLink.js`: a 5-character code on the TV (`POST /api/tv/link/start`, `/poll`), confirmed from the site (`GET /info`, `POST /confirm`); needs Redis |
| VIP | `utils/vipDonations.js`, `routes/vipDonations.js`, `utils/vipDonationSettings.js` |

### History

History, progress and "already seen" marks are stored in MySQL (`db/schema/history.js`: `watch_progress`, `watch_history`,
`watch_marks`, plus the permanent TMDB cache `tmdb_media`, `tmdb_episodes`). Clients still write through `/api/sync`; the
server copies those keys into the tables and imports each profile's older data once.

| Route under `/api/history` | Purpose |
| --- | --- |
| `GET /continue` | *Continue watching* row |
| `GET /list` | History tab (`status` filter, pagination) |
| `GET /title` | Progress of a film or series |
| `GET` / `POST /progress` | Playback position |
| `DELETE /continue/:type/:id`, `DELETE /continue` | Remove one card, or all |
| `DELETE /title/:type/:id` | Remove a title from history |

### Native VOD sources

`GET /api/omega/movie/:tmdbId?resolve=1` reuses the FrenchCloud/Omega IMDb catalogue after a cached TMDB external-id
lookup; TV uses `/api/omega/tv/:tmdbId?season=N&episode=M&resolve=1`, and only the requested episode is copied into
`current_episode.languages` and enriched. Signed media is never stored in catalogue caches. `/api/imdb/:type/:imdbId`
supports the same episode selection.

The native client also uses the Coflix (`/api/tmdb/:type/:id`), J1F, SwiftFlow and VoirDrama routes. CineStream
supplements Wiflix rather than being a separate route. KissKH has its own signed-media contract. France.tv uses signed
page tokens and DRM; DarkiWorld returns download links identified by its own title ids.

The Streamed native decoder depends on the pinned `streamed-pk-hls-stream-resolver` package, whose upstream tarball
returned HTTP 404 on 2026-11-15; it is absent from this installation, so the native Streamed handshake is unavailable.
See [`../SOURCE-INTEGRATION.md`](../SOURCE-INTEGRATION.md) for the status of every provider.

## Tests

```powershell
cd API/Mainapi
node --test routes/__tests__ utils/__tests__
```

Several tables are created automatically at start-up. A failure can come from a disk cache or an outbound proxy rather
than from the route itself; playback problems often involve [`../proxiesembed`](../proxiesembed/README.md) as well.
