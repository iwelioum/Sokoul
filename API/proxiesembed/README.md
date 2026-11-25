# proxiesembed

Derived third-party code (CC BY-NC 4.0): see docs/third-party/README.md.

Python media proxy (aiohttp, port 25569): a generic stream proxy, per-host extractors and proxies,
debrid unlocking, and the bounded direct-egress transport used by Mainapi's native extraction. It is built for load:
concurrent connections, memory caches with TTL eviction, request coalescing and SOCKS5 pools.

## Running

The Docker stack started by [`../start-backend.ps1`](../README.md) runs it. Alone:

```powershell
cd API/proxiesembed
copy .env.example .env
pip install -r requirements.txt
python server.py          # http://localhost:25569
```

The `/drm/*` routes depend on local utilities that are not part of the repository; without them the server starts and
those routes are unavailable.

## Access model

Nothing here is freely callable. Both locks fail closed: a missing secret refuses the surface instead of opening it.

1. **Streaming surfaces need a signed URL.** `/proxy`, every `/*-proxy` and `/drm/*` accept a target only with `exp` and
   `sig`, an HMAC-SHA256 of `route\ntarget\nexpiry` keyed with `MEDIA_SIGNING_SECRET`. Mainapi signs
   (`Mainapi/utils/mediaSigning.js`), or this service when it rewrites a playlist. `/proxy?url=<anything>` answers 403,
   which closes SSRF. The route is part of the signed message, so a URL signed for one proxy cannot be replayed on another.
   No route is exempt (`SELF_VALIDATED_PROXY_ROUTES` is empty).
2. **Extraction surfaces need the internal key and a VIP key.** `/api/extract-*`, `/api/voe/m3u8`, `/drm/extract` and
   `/api/debrid/unlock` require `x-internal-key` (`INTERNAL_API_KEY`, known only to Mainapi) **and** a valid
   `x-access-key` forwarded by Mainapi.

Clients go through Mainapi. Catalogue routes resolve playable URLs on demand when the request carries `?resolve=1` and a
valid VIP key, only for what is asked (one episode or the film, never a whole season):

| Catalogue | Film | Series |
| --- | --- | --- |
| fstream | `/api/fstream/movie/:id` | `/api/fstream/tv/:id/season/:s?episode=N` |
| wiflix | `/api/wiflix/movie/:id` | `/api/wiflix/tv/:id/:season` |
| 1jour1film | `/api/j1f/movie/:id` | `/api/j1f/tv/:id/season/:s?episode=N` |
| swiftflow | `/api/swiftflow/movie/:id` | `/api/swiftflow/tv/:id/season/:s?episode=N` |
| cpasmal | `/api/cpasmal/movie/:id` | `/api/cpasmal/tv/:id/:season/:episode` |
| voirdrama | — | `/api/drama/tv/:id?season=&episode=` |
| coflix | `/api/tmdb/movie/:id` | `/api/tmdb/tv/:id?season=&episode=` |
| frenchstream | `/api/imdb/movie/:id` | — |

Each resolved player gets an `m3u8Url` next to its embed link. Anime-Sama lists players as plain strings, so its route adds
a parallel `m3u8ByPlayer` table (`?resolve=1&season=N&episode=M`). Frenchstream series are not resolved: the endpoint
returns every season and takes no episode.

Deliberately absent: an endpoint that signs any URL (a signing oracle), and an endpoint that extracts any URL. Playable URLs
are resolved only from links the server scraped itself; clients send identifiers (TMDB id, season, episode). France.tv page
addresses travel as signed `url_token`s issued by `/api/ftv/search`; a forged, altered or expired token is rejected.

## Environment

| Group | Variables |
| --- | --- |
| Database | `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` |
| Public address | `PROXY_BASE` |
| Security | `MEDIA_SIGNING_SECRET`, `INTERNAL_API_KEY`, `MEDIA_SIGNATURE_TTL` — both secrets must equal Mainapi's |
| Proxy pool | `PROXIES_SOCKS5_JSON` |
| France.tv | `FRANCETV_EMAIL`, `FRANCETV_PASSWORD` |
| Debrid | `DEEPBRID_API_KEY` |

## Routes

🔏 signed URL required · 🔑 internal key and VIP key required

| Route | Purpose |
| --- | --- |
| `GET /proxy`, `GET /proxy/{path}` | 🔏 Generic stream proxy |
| `GET /streamed-proxy` | 🔏 Streamed relay over SOCKS5, HD playlists and PNG/WebP segments ([STREAMED.md](STREAMED.md)) |
| `GET /health`, `GET /stats` | Health and runtime statistics |
| `GET /api/voe/m3u8`, `/api/extract-fsvid`, `-vidzy`, `-vidmoly`, `-sibnet`, `-uqload`, `-doodstream`, `-seekstreaming` | 🔑 Per-host extraction |
| `GET /voe-proxy`, `/fsvid-proxy`, `/vidzy-proxy`, `/vidmoly-proxy`, `/sibnet-proxy`, `/uqload-proxy`, `/doodstream-proxy`, `/cinep-proxy`, `/kisskh-proxy`, `/seekstreaming-proxy` | 🔏 Per-host proxies |
| `POST /api/debrid/unlock` | 🔑 Debrid link unlocking |
| `POST /api/native-fetch`, `POST /api/native-media` | 🔑 Transport of Mainapi's native extraction (below) |

## Native extraction transport

The shared extraction core (`extraction/runtime`) runs inside Mainapi and fetches through this service. `native-fetch`
accepts public HTTP(S) destinations on standard ports, checks every redirect, resolves through a public-only DNS
resolver and never forwards the internal or VIP headers; bodies are base64 and capped at 4 MiB, and a `Range: bytes=0-0`
probe reads one byte then closes upstream.

`native-media` returns `/native-proxy?sid=...&url=...&exp=...&sig=...`. The signature binds the target and an opaque
state id; approved upstream headers stay in a bounded, expiring in-memory registry (512 entries, at most 12 hours), never
in URLs. A restart expires them. HLS playlist, key and segment references are re-signed with the same state; MP4 keeps
byte ranges. Extraction and relay share one direct-egress session, so IP-bound tokens do not switch exit. Cross-origin
redirects and child resources drop Cookie and Authorization headers.

## Tests

```powershell
cd API/proxiesembed
python -m pytest tests
```
