# Web sites still read only through the old server

The sources the apps still ask of the old Node server (`WebRoutes` in
`app/src/main/kotlin/com/sokoul/pc/playback/sources/WebSources.kt`): Cpasmal, Wiflix, Omega, J1F, SwiftFlow, KissKH, and
Darkiworld. Each section gives what the site is, how a TMDB work is found on it, where its players sit, and what a plain
HTTP client got from it on **2026-11-25** (a few read-only GETs, desktop browser User-Agent, no login, no proxy, no
challenge solving).

The facts were learnt by reading the old server's routes and checked against the live sites; nothing here is copied
from that code. A site gets a `WebSource` in this module only when a plain HTTP client reads it end to end. None does
today: every section ends with what would have to change first.

| Site | Server route | Status on 2026-11-25 | Implemented |
| --- | --- | --- | --- |
| Cpasmal | `/api/cpasmal` | Blocked: the search answers a Cloudflare challenge (403) | No |
| Wiflix | `/api/wiflix` | Blocked: the domain sends to a JS bot gate; the film mirror refuses TLS | No |
| Omega | `/api/omega` | Dead: the embed domain's registration expired | No |
| J1F | `/api/j1f` | Blocked: home page readable, search answers a Cloudflare challenge (403) | No |
| SwiftFlow | `/api/swiftflow` | Unreachable (connection times out) and needs a partner API key | No |
| KissKH | `/api/kisskh` | Catalogue readable; streams refused (403) without a signed per-episode key | No |
| Darkiworld | `/api/darkiworld` | Dead: closed for good (410) | No |

## Cpasmal

- **Site**: `https://www.cpasmal.rip` (a DataLife Engine site; the domain rotates).
- **Finding a work**: the DLE search form, posted to `/index.php` (`do=search`, `subaction=search`, `story=<title>`,
  `search_start=<page>`), with the site as Origin and Referer. Results are `div.thumb` cards: link `a.th-img`, title
  `.th-desc .th-capt`, year `.th-desc .th-year`, kind by a `.th-Film` or `.th-Serie` badge. The server tried the French
  title, then title + year, then a full-text search, and weighted an exact title and a matching year; a film page's
  `<h1>` title was checked again before reading its players.
- **Players**: on a film page, `.liens-c .lien` buttons whose `onclick` calls `getxfield(id, xfield, token)`; the
  xfield name carries the host and the language (`<host>_vf`, `<host>_vostfr`). A series has one page per season
  (`.th-seas` with "Saison N"), episodes at `<season page>/<n>-episode.html`, and each episode button leads through
  `/episode/<id>/<xfield>`, which answers a redirect to the host's player page.
- **Anti-bot**: Cloudflare in front of the whole site; the server noted that the GET search is refused and that the
  film `getxfield` step sits behind a Turnstile check.
- **Live, 2026-11-25**: `GET /` → 200, HTML. `POST /index.php` search for "Inception" → **403 "Just a moment…"**
  (Cloudflare managed challenge).
- **Status**: blocked. Without the search no work can be matched, and a challenge is not something Sokoul solves.
- **Needed**: a search that answers plain HTTP (or a public listing keyed by title/year) and players reachable without
  a Turnstile token.

## Wiflix (Flemmix, with Cinestream for films)

- **Site**: series on `https://flemmix.rip` (rotating: flemmix.fast, flemmix.rip…); films on `https://cinestream.info`.
- **Finding a work**: Flemmix is DLE too (search posted to `/index.php` with `do=search&story=<title>`, series pages
  under `/serie-…`). Cinestream is a Next.js site: `/search?q=<title>` lists `/film/<slug>` links, and the film page's
  embedded data carries the work's **TMDB id** and its ordered player names, so the match is exact.
- **Players**: Flemmix pages list hosts in `.tabs-sel a[data-v]`, the address encoded as base64 then ROT13 (an older
  `onclick="loadVideo('…')"` form also existed). On Cinestream, `/player/<tmdbId>/<index>` holds the host's `<iframe>`;
  a player whose name starts with "vostfr" is VOSTFR, the others VF.
- **Live, 2026-11-25**: `GET https://flemmix.rip/` → **301 to `https://www.neufneuf.space/`**, which answers 200 with an
  "Acces securise" page: a script that checks the browser for bots (`navigator.webdriver`, crawler User-Agents) before
  revealing the real address, which it keeps encoded. `GET https://cinestream.info/search?q=Inception` → the TLS
  handshake fails (server alert "internal error", with both curl/schannel and Python/OpenSSL, TLS 1.2 or 1.3); DNS
  answers `2.57.91.92`.
- **Status**: blocked (Flemmix: JS bot gate) and unreachable (Cinestream: TLS refused).
- **Needed**: a Flemmix address published plainly (not behind the gate), or Cinestream answering TLS again. Cinestream
  would then be the easy one: its TMDB id makes the match certain.

## Omega (FrenchCloud)

- **Site**: `https://frenchcloud.cam`, an embed site keyed by the **IMDb id** (the server took it from TMDB
  `external_ids`; the TMDB id alone is not enough).
- **Finding a work**: no search. A film is `/film/<imdb digits>`, a series `/serial/<imdb digits>`; a 404 means the
  site does not have it.
- **Players**: on a film page, `._source_list li[data-link]` and `._player-mirrors li[data-link]` (the item's text names
  the host, a `fullhd` class marks HD). On a series page, `._stab[data-season]` tabs give the season numbers ("S1"…),
  `._grp[data-season]` groups hold `._ep[data-link]` episodes whose `data-label` reads like "S1 E3" (VOSTFR when it
  says so, VF otherwise). Links back to frenchcloud itself are not players.
- **Live, 2026-11-25**: `GET /` → 200, but it is a **registrar parking page ("Domain registration has expired")**.
  `GET /film/1375666` (Inception) → 404 from the parking server.
- **Status**: dead.
- **Needed**: the embed service back on a live domain. The shape above would then make a small source (IMDb id from
  TMDB, one page, players → `Embed`).

## J1F (1JOUR1FILM)

- **Site**: a WordPress/DooPlay site whose domain rotates monthly. `https://1jour1film2026.site/go/` is the stable entry:
  its page sets `TARGET_URL` to the current domain (on 2026-11-25: `https://1jour1film1126.shop`).
- **Finding a work**: DooPlay's live search, `/wp-json/dooplay/search/?keyword=<title>&nonce=<nonce>`, answers a map of
  posts (title, url, `extra.date` year); the nonce is published in the pages' inline settings. Films are under
  `/films/<slug>`, series under `/tvshows/<slug>` with `/saisons/…saison-<n>…` pages. The server compared the French and
  original titles to the post title (and to the slug), with a bonus for the year.
- **Players**: inline base64 `data:` scripts hold `J1F_SRV` (a film's servers) or `j1fEpsData` (a season's episodes,
  each with `num` and `servers`); each server has a `url` (sometimes base64), a `label` (VF, VOSTFR, "VF + VOSTFR"), a
  `type` (`mp4` or iframe) and a `source` tag. Some servers are the site's own wrapper (`onregardeou…/video/<slug>/`)
  whose page lists the real hosts in a `"servers":[…]` array.
- **Anti-bot**: Cloudflare; the server needed TLS-fingerprint impersonation and rotating proxies to get through.
- **Live, 2026-11-25**: `GET /go/` → 200 (TARGET_URL read). `GET https://1jour1film1126.shop/` → 200, HTML (no search
  nonce in the home page's settings). `GET /?s=Inception` → **403 "Just a moment…"** (Cloudflare challenge).
- **Status**: blocked.
- **Needed**: a search reachable by plain HTTP. The pages themselves may be readable, but without the search no work is
  matched.

## SwiftFlow

- **Site**: `https://swiftflow.lol`, a partner JSON API keyed by TMDB id (no scraping):
  `/api/v1/index.php?route=movies/<id>` and `route=series/<id>` with an `api_key` parameter; a 404 means not on
  SwiftFlow. The player page is `route=movies/<id>/player` (or `series/<id>/player&season=&episode=`), and the API also
  lists MP4 files (language, quality, size) that the old server handed out only after a Turnstile check.
- **Live, 2026-11-25**: `https://swiftflow.lol/` and `http://swiftflow.lol/` → **connection timed out** (the name
  resolves to Cloudflare addresses, nothing answers), also when resolved through 1.1.1.1.
- **Status**: unreachable, and not configured: Sokoul has no partner key of its own in this module.
- **Needed**: the API answering again and a partner key given to Sokoul (then: a `WebSource` taking the key, player page
  → `Embed`, MP4 entries → `File`).

## KissKH

- **Site**: `https://kisskh.do`, an Asian drama catalogue with a JSON API.
- **Finding a work**: `/api/DramaList/Search?q=<title>&type=0` answers `[{id, title, episodesCount, thumbnail}]` (the
  thumbnail is often a TMDB image, which would allow a poster check); `/api/DramaList/Drama/<id>?isq=false` gives the
  work's episodes (`episodes[].id`, number).
- **Streams**: `/api/DramaList/Episode/<episodeId>.png?…&kkey=<key>`, where `kkey` is a per-episode signature that the
  site's web app computes with keys embedded in its JavaScript bundle; subtitles (`/api/Sub/<episodeId>?kkey=…`) are
  signed the same way and their files are encrypted.
- **Live, 2026-11-25**: the system resolver answers an address with the wrong certificate (an ISP block, like the one
  `CatalogueDns` works around); through Cloudflare's address (from 1.1.1.1): `GET /` → 200, search → 200 JSON,
  drama → 200 JSON, **episode stream without `kkey` → 403**.
- **Status**: blocked for streams. Reproducing the signature means lifting secrets from the site's bundle to forge its
  app's requests: request-signing evasion, which Sokoul does not do.
- **Needed**: a stream endpoint that answers without the signature (or an official API).

## Darkiworld

- **Site**: `https://darkiworld2026.com`, a download-link catalogue (hydracker). The old server already stopped calling
  it on 2026-05-15 and served links from local snapshots only.
- **Live, 2026-11-25**: `GET /` → 301 to `https://hydracker.com/`, which answers **410 "Fermeture définitive"**.
- **Status**: dead (closed for good).
- **Needed**: nothing to implement; the site is gone.
