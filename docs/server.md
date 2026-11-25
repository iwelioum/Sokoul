# Sokoul server

`server/` is Sokoul's own private server (Kotlin, Ktor on Netty, one SQLite file). It replaces `API/` lot by lot; see
[`openspec/changes/sokoul-server`](../openspec/changes/sokoul-server). Today it has the skeleton (config,
database with migrations, structured logs, `/health`, both access locks), accounts, sessions, TV linking, sync, the
credentials vault, and the web client with its catalogue, sources and stream relay (see Web client API).

## Run it

```powershell
.\tools\server.ps1 run          # Windows   (tools/server.sh run on macOS / Linux)
curl.exe http://127.0.0.1:25570/health
# {"status":"ok","version":"0.1.0"}
```

The log prints one line `Sokoul server on http://127.0.0.1:25570`, then one line per request: status, method and path.
The log never includes query strings, headers, tokens or the Access audience.

Tests: `.\gradlew.bat :server:test`.

## Configuration

The server reads a properties file. Its path comes from `SOKOUL_SERVER_CONFIG`. Without it, the file is
`%LOCALAPPDATA%\SokoulServer\server.properties` on Windows and `~/.sokoul-server/server.properties` elsewhere. A missing
file means every setting takes its default. Environment variables override the file.

| Key | Environment | Default | Meaning |
| --- | --- | --- | --- |
| `port` | `SOKOUL_SERVER_PORT` | `25570` | TCP port |
| `bind` | `SOKOUL_SERVER_BIND` | `127.0.0.1` | Listen address |
| `lan` | `SOKOUL_SERVER_LAN` | `false` | `true`: listen on `0.0.0.0` (unless `bind` is set) and accept private-LAN callers |
| `dataDir` | `SOKOUL_SERVER_DATA_DIR` | the config file's folder | Holds `sokoul-server.db` and `backups/` |
| `access.teamDomain` | `SOKOUL_SERVER_ACCESS_TEAM_DOMAIN` | unset | e.g. `myteam.cloudflareaccess.com` |
| `access.audience` | `SOKOUL_SERVER_ACCESS_AUDIENCE` | unset | The Access application's AUD tag |
| `vault.keyFile` | `SOKOUL_SERVER_VAULT_KEY_FILE` | `vault.key` next to the config file | The credentials vault's master key (see Credentials vault) |
| `web.dir` | `SOKOUL_SERVER_WEB_DIR` | `web/` of the checkout or beside the install | The web client's files (see Web client API) |
| `ffmpeg.path` | `SOKOUL_SERVER_FFMPEG` | `ffmpeg` on the `PATH` | Remux for Safari; without it MKV/AC3 files are relayed as they are |

The two `access.*` keys must be set together. Set both, or leave both unset.

## The two locks

The server has two locks (design D4). Either one alone is one mistake away from exposing the IPTV account and the keys.

1. **Network lock** (`AccessGate`), checked before routing on every path except `/health`:
   - **Local mode** (no `access.*`). Only loopback callers get through, plus private-LAN callers (10/8, 172.16/12,
     192.168/16, link-local, IPv6 ULA) when `lan=true`. A request that carries Cloudflare headers (`Cf-Connecting-IP`,
     `Cf-Ray`, `Cf-Access-*`) is refused. So a tunnel set up before Access is configured cannot open the server to the
     internet: `cloudflared` connects from `127.0.0.1`, but Cloudflare stamps those headers.
   - **Access mode** (`access.*` set). Every request must carry `Cf-Access-Jwt-Assertion`. The server checks four things:
     - the signature is RS256, from one of the team's keys (`https://<team>/cdn-cgi/access/certs`). Keys are cached; an
       unknown `kid` refreshes the cache at most once every 5 minutes;
     - `aud` contains the audience;
     - `iss` is `https://<team>`;
     - `exp` and `nbf` are valid, with 60 s of leeway.

     Verification uses only the JDK, with no JWT library. Requests made directly from this PC, or from the LAN when
     `lan=true`, carry no Cloudflare header and pass without an assertion: only what came through the tunnel needs one.
   - A refused request gets `403 {"error":"access_denied"}`. The log records the reason (`missing_assertion`,
     `signature`, `audience`…), never the token.
2. **Session lock** (`SessionStore`): every `/api/*` route needs a Sokoul session (`Authorization: Bearer <token>`),
   except first-start setup, sign-in, joining and the TV side of linking. Without one, the answer is
   `401 {"error":"unauthorized"}`, even behind a valid Access assertion.

Errors are always `{"error":"<stable_code>"}`. Responses never include stack traces or exception messages.

## Accounts

The instance is private: one owner, plus family members the owner invites. There is no sign-up and no payment.

- **Owner (first start).** While the database has no user, the server logs `No account yet` and `GET /api/setup`
  answers `{"needsOwner":true}`. `POST /api/setup`
  `{"name","passphrase","deviceName"?}` creates the owner and returns a session. It is accepted only from this PC
  itself (loopback, no Cloudflare header), and only once: afterwards it answers `409 already_set_up`.

  ```powershell
  curl.exe -s -X POST http://127.0.0.1:25570/api/setup -H "Content-Type: application/json" `
    -d '{\"name\":\"Ana\",\"passphrase\":\"<at least 12 characters>\",\"deviceName\":\"PC\"}'
  ```
- **Passphrases**: 12 to 1024 characters, stored as PBKDF2-HMAC-SHA256 (JDK, 310 000 iterations, 16-byte random salt,
  constant-time comparison). The iteration count is stored per user. Names are unique, ignoring case.
- **Sign-in**: `POST /api/auth/login` `{"name","passphrase","deviceName"?}` returns
  `{"token","sessionId","user":{"id","name","role"}}`. A wrong name and a wrong passphrase get the same
  `401 invalid_credentials`. After 5 failures in 15 minutes from one address, or on one account, sign-in answers
  `429 too_many_attempts` with `Retry-After`. The counters live in memory. Behind the tunnel the address is
  `Cf-Connecting-IP`, which only Access-verified requests can carry.
- **Invitations**: the owner calls `POST /api/invites` and gets `{"code":"XXXX-XXXX-XXXX","expiresAt"}`. The code
  works once, for 24 hours, and is stored hashed. The new member calls `POST /api/auth/join`
  `{"code","name","passphrase","deviceName"?}`: a wrong, used or expired code is `400 invalid_invite`, and a taken name
  is `409 name_taken`. Members cannot invite (`403 owner_only`).
- `GET /api/me` returns `{"id","name","role"}` (`owner` or `member`).
- Passkeys (WebAuthn) come later.

## Sessions and devices

- A session token is 32 random bytes in Base64 URL. The device keeps it; the server stores only its SHA-256.
- A session expires 30 days after its last use. Use is recorded at most once an hour.
- `GET /api/sessions` lists the caller's devices: `{"sessions":[{"id","deviceName","createdAt","lastUsedAt","current"}]}`
  (times in epoch milliseconds, most recent first).
- `DELETE /api/sessions/{id}` revokes one of the caller's devices (`204`, or `404` for an unknown or foreign id). The
  device's next call gets `401`.
- `POST /api/auth/logout` revokes the current session.

## Linking a TV

The TV never shows a keyboard. It shows a code and a QR; a signed-in phone or computer confirms.

1. The TV calls `POST /api/link/start` `{"deviceName":"Salon TV"}` (no session). The answer is
   `{"deviceCode","userCode":"XXXX-XXXX","verifyPath":"/link?code=XXXX-XXXX","expiresIn":600,"interval":3}`.
   - `deviceCode` is a 32-byte secret that only the TV knows.
   - `userCode` uses only `BCDFGHJKLMNPQRSTVWXZ23456789` (no vowels, no 0/O, 1/I/L).
   - The QR encodes the server address followed by `verifyPath`.
2. On the phone, `GET /link` opens a small page (monochrome, French or English from the browser, no external
   assets). It signs in with name and passphrase if needed, keeps the token in `localStorage`, and confirms the code
   through `POST /api/link/confirm` `{"userCode"}`, with a session. A wrong, used or expired code is
   `404 invalid_code`. The page is temporary until the web client exists.
3. The TV calls `POST /api/link/poll` `{"deviceCode"}` every 3 seconds:
   - `428 pending` until the code is confirmed;
   - `200 {"token","sessionId"}` once confirmed, exactly once;
   - `410 expired` after 10 minutes, after the token was handed out, or for an unknown code;
   - `429 slow_down` when polled faster than every 3 seconds.

Both codes are stored hashed. At most 50 links can wait at once (`429 too_many_links`). `/link`, `/api/link/start` and
`/api/link/poll` need no session but stay behind the network lock.

## Sync

One account carries its profiles and progress across the TV, the tablet and the PC (openspec task 7). Both routes
need a session and only ever see the caller's account.

What syncs, per account:

- **profiles**: id (a UUID chosen by the device that created it), name, avatar colour, kids flag, and the profile
  code's digest with the id it was salted with. The clear code never leaves the device. The first profile of every
  device is the same account profile (its UUID is derived from the user id).
- per profile: **resume points** (they are also the history: kind, TMDB id, season, episode, position, duration,
  finished, title, subtitle, picture), **My List** entries and **title alerts** (with the last news told).

What stays on the device: playback settings, IPTV playlists, favourites and the IPTV providers' own resume points
(their ids belong to one provider account), service keys, and the server address and session.

Every item has a stable key (the app's resume key, or `movie:603` for a list entry or an alert), an `updatedAt` in
milliseconds and a `deleted` flag. A deletion is kept as a tombstone, so it beats older copies on other devices.

- `GET /api/sync?since=<cursor>` returns `{"changes":[…],"cursor":N,"more":bool}`: the changes after `cursor`, oldest
  first, at most 1,000 per page. The cursor is the account's own sequence number, raised by every applied change, not
  a clock. `409 cursor_ahead` means the server never gave that cursor (its database was replaced): the device starts
  again from 0 and sends everything it has.
- `POST /api/sync` with `{"changes":[…]}` applies each change whose `updatedAt` is later than the stored one (last
  writer wins per item; on a tie the server keeps its copy) and answers `{"applied":n,"ignored":n,"cursor":N}`.
  Limits: 1,000 changes per request, 8 MiB per body, 64 profiles and 200,000 items per account, and string lengths
  (key 200, name 40, title 300, address 1,000, overview 4,000). Anything outside them refuses the whole request
  (`invalid_change`, `too_many_changes`, `too_many_items`, `too_many_profiles`, `too_large`). A device clock more
  than a minute ahead is brought back to the server's time.

The apps (`com.sokoul.pc.sync.SyncEngine`) keep a `sync_shadow` table: comparing it with the live data finds local
changes, which wait there while the device is offline. A cycle pushes them, then pulls and merges, never overwriting a
newer local value. It runs at start, every 5 minutes while the app is in front, when the app comes back to the front,
when the account panel opens, and 5 s after saved progress. The account panel shows "Last synced at …" or the reason.

## Remote access: Cloudflare Tunnel + Access (free plan)

`.\tools\server.ps1 tunnel -Hostname sokoul.example.com -Email you@example.com` prints every command with your values
filled in (`tools/server.sh tunnel <host> <email>` on macOS / Linux). It runs nothing. The steps are:

1. Install `cloudflared`, then run:
   - `cloudflared tunnel login`
   - `cloudflared tunnel create sokoul`
   - `cloudflared tunnel route dns sokoul <host>`
2. Write `~/.cloudflared/config.yml`. Add an ingress rule that sends `<host>` to `http://127.0.0.1:25570`, then a final
   rule, `http_status:404`.
3. Run `cloudflared tunnel run sokoul`. Once that works, install it with `cloudflared service install`.
4. In the Zero Trust dashboard:
   - add a self-hosted Access application for `<host>`;
   - give it one policy: Allow, Emails = your address, with One-time PIN login;
   - copy the application's AUD tag.
5. Put `access.teamDomain` and `access.audience` in `server.properties`, then restart the server.
6. Check from a phone on mobile data:
   - the address asks for the e-mail code first;
   - after the code, `/health` answers;
   - `/api/me` answers 401 (no session yet).

Tailscale stays a documented alternative for devices that cannot use Access. In that case, keep local mode and use
`lan=true` with `bind` set to the Tailscale address.

## LAN mode

Set `lan=true` so devices at home (TV, tablet) can reach the server directly at `http://<pc-ip>:25570`. Allow the port
in the Windows firewall for private networks only. Public addresses are still refused. With Access configured too, home devices keep
reaching the server directly on the LAN, while everything that arrives through the tunnel must pass Access.

## Start automatically

`.\tools\server.ps1 install-service` prints the steps:

- **Windows**: `:server:installDist`, copy the result out of OneDrive, then a log-on task with `schtasks`.
- **macOS**: a launchd agent.
- **Linux**: a systemd user unit.

It installs nothing itself.

## Credentials vault

Each user brings their own keys: the server has no server-wide service key, and one user's keys are never used for
another (openspec task 6, design D5). The routes need a session and only see the caller's own keys.

- `GET /api/credentials` returns `[{"service","hint","verdict","checkedAt"}]`. The hint is a mask (`•••• 1234`, fewer
  characters for short values, the password's end for an Xtream login).
- `PUT /api/credentials/{service}` with `{"value":"…"}` checks the value with the real service first (the same
  `com.sokoul.credentials.ServiceCheck` the apps use), then stores it and answers its hint and verdict. A refusal stores
  nothing (an older accepted value stays) and answers `{"error","reason"}`: `400 bad_format` (not a value of that
  service), `422 refused` (the service said no), `502 unreachable` (it did not answer, or asked to wait). `404
  unknown_service`, `411 length_required`, `413 too_large` (8 KiB per body).
- `DELETE /api/credentials/{service}`: `204`, or `404 not_found`.

Services: `tmdb`, `realdebrid`, `omdb`, `fanart`, `tvdb`, `watchmode`, `tastedive`, `motn`, `opensubtitles`, `subdl`
(a key of 1–256 visible ASCII characters), and `iptv`: an Xtream login as JSON
(`{"server":"http://…","username":"…","password":"…"}`) or an http(s) M3U link, up to 2,048 characters. TVmaze needs
no key. No route ever returns a stored value; server code reads one with `Vault.reveal(user, service)`. Values are never
logged.

Encryption: each value is sealed with AES-256-GCM under the server's data key, with a random 96-bit nonce and
`user_id|service` as associated data (a blob copied to another row does not open). The data key is stored in
`vault_keys`, wrapped by the master key: 32 random bytes in the key file, created on the first start with owner-only
rights (0600, or a Windows ACL keeping only the owner). The server refuses to start when the database holds a data
key and the key file is missing or is another one; it never makes new keys over old ones.

> **Back up the key file, apart from the database.** Lose `vault.key` and every stored key is lost: users enter them
> again. A database backup alone cannot be read; keep the key file somewhere else (a password manager, a USB stick).
> By default the key file sits next to `server.properties`, which is also the default data folder: set `dataDir` or
> `vault.keyFile` so the two live apart, and the server warns at start while they do not. To start the vault over
> after losing the file, stop the server and run `DELETE FROM credentials; DELETE FROM vault_keys;` on the database.

The apps: signed in to an account, Settings › Sources and keys also saves each key it accepted to the vault, and a card
this device has not set up shows "In your account · •••• 1234" when the account has that key, so a new device knows
which keys to enter. Keys never come back from the server.

## Web client API

The web client (`web/`, openspec `web-client`) is served by this server and talks only to it. Everything below sits
behind the network lock; the static files need no session (the page signs in through the API), every JSON route needs
`Authorization: Bearer <token>`. The server has **no key of its own**: catalogue and sources use the caller's keys from
the vault. Errors are `{"error":"<code>"}` as everywhere else, with `Retry-After` on `429`.

### Static files

- `web.dir` (`SOKOUL_SERVER_WEB_DIR`) sets the folder; otherwise the server looks for `web/` in its working folder, one
  level up, then beside the install (`<install>/web`). Without one, `/` answers 404.
- `GET /` and every path without an extension that is not a file (client routes such as `/title/movie/603`) answer
  `index.html`. A missing file with an extension, a folder (no listing), a hidden name (`.env`) or any path leaving the
  folder (`..`, `%2e%2e`, `%2f`, `%5c`, NUL, symbolic links out) answers 404. `/api/…` never falls back to the page.
- Cache: `index.html` and unhashed files `no-cache` with an `ETag` (`304` on `If-None-Match`); `vendor/`, `fonts/` and
  hashed names (`app.3f2a9c1d.js`) `public, max-age=31536000, immutable`.
- Headers on every static answer: `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `X-Frame-Options: DENY` and

  ```
  Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; connect-src 'self';
    img-src 'self' data: blob: https://image.tmdb.org; media-src 'self' blob:; worker-src 'self' blob:;
    manifest-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'
  ```

  No inline script or style, no third-party script. Images may come from TMDB's CDN (the addresses carry no key).

### Catalogue

Language from `Accept-Language`: the first `fr*` or `en*` tag gives `fr-FR` or `en-US`, French by default. Region FR.
TMDB answers are cached in memory 3 hours per key, path, parameters and language (the key enters the cache only as its
SHA-256). Images are TMDB CDN addresses (`https://image.tmdb.org/t/p/<size>/<file>`).

Without a TMDB key in the caller's vault, or when TMDB refuses the stored one, every catalogue and play route answers
`409`:

```json
{"error":"needs_key","service":"tmdb"}
{"error":"needs_key","service":"tmdb","reason":"refused"}
```

`Item` (rows and search):

```json
{"kind":"movie","id":603,"title":"Matrix","year":1999,
 "poster":"https://image.tmdb.org/t/p/w342/….jpg","backdrop":"https://image.tmdb.org/t/p/w780/….jpg"}
```

`kind` is `movie` or `tv`; `year`, `poster`, `backdrop` may be missing.

- `GET /api/catalog/home[?profile=<profile uuid>]` →
  `{"rows":[{"id","title","items":[Item]}]}`. Row ids in order: `continue` (only with `profile`, only when it has
  something: "Reprendre" / "Continue watching"), `trending` (all, week), `popular_movies`, `popular_tv`, `top_rated`
  (films). Empty rows are left out. A `continue` item has no poster; its `backdrop` is the synced image and it adds
  `"resume":{"season":1,"episode":2,"positionMs":600000,"durationMs":3600000,"subtitle":"S1E2"}` (`season`/`episode`
  for series). It holds the 20 most recent unfinished resume points of that profile in the caller's account, one per
  title. `400 invalid_profile` when the id is not 1–64 of `[A-Za-z0-9_-]`.
- `GET /api/catalog/title/{movie|tv}/{id}` →

  ```json
  {"kind":"tv","id":1399,"title":"Game of Thrones","originalTitle":"Game of Thrones","overview":"…","year":2011,
   "runtime":60,"genres":["Drame"],"rating":8.4,"backdrop":"…/w1280/….jpg","poster":"…/w500/….jpg",
   "logo":"…/w500/….png","cast":[{"name":"…","character":"…","photo":"…/w185/….jpg"}],
   "seasons":[{"number":1,"name":"Saison 1","episodeCount":10,"poster":"…/w342/….jpg","airDate":"2011-04-17"}]}
  ```

  `runtime` is minutes (a series' usual episode length); `logo` prefers the request language, then English; `cast` is
  the first 10; `seasons` only for `tv`, specials (`number` 0) last. `400 invalid_kind`, `400 invalid_id`,
  `404 not_found`.
- `GET /api/catalog/season/{id}/{season}` →
  `{"id":1399,"season":1,"name":"Saison 1","episodes":[{"number":1,"name":"…","overview":"…","still":"…/w300/….jpg","runtime":62,"airDate":"2011-04-17"}]}`.
- `GET /api/catalog/search?q=<text>` → `{"items":[Item]}` (films and series only; an empty `q` gives `[]`;
  `400 query_too_long` over 100 characters).

### Play

- `GET /api/play/versions?kind=movie&id=603` or `?kind=tv&id=1399&season=1&episode=2` →

  ```json
  {"versions":[{"id":"<opaque>","label":"Coflix · Uqload","quality":"1080p","language":"VF","origin":"web","source":"Coflix"}],
   "sources":[{"name":"Coflix","status":"found"},{"name":"FStream","status":"not_found"},
              {"name":"PurStream","status":"unavailable","reason":"blocked"}]}
  ```

  The sites of `sources/` (PurStream, Coflix, FStream, VoirDrama, and Anime-Sama for Japanese animation) are asked in
  parallel, 25 s at most each. `quality` is `2160p`, `1080p`, `720p`, `480p` or missing; `language` is `VF`, `MULTI`,
  `VOSTFR`, `VO` or missing. Order: French audio first (VF, MULTI, VOSTFR, VO, unknown), then quality, then direct links
  before player pages. A player page no Sokoul host extractor reads is left out. `reason` is `blocked`, `timeout`,
  `network` or `host_changed`. A version id is valid 2 hours, for the session that listed it only. Real-Debrid is not
  asked yet (`origin` will then be `realdebrid`). `400 invalid_kind|invalid_id|episode_required`.
- `POST /api/play/start` with `{"versionId":"…"}` →

  ```json
  {"url":"/api/stream/<id>/index.m3u8","type":"hls","remux":false,"compatible":true,
   "subtitles":[{"url":"/api/stream/<id>/u/…/…","label":"Français","language":"fr"}]}
  ```

  - `type` `hls`: `url` ends in `index.m3u8` (a relayed playlist, or ffmpeg's remux when `remux` is true).
  - `type` `file`: `url` is `/api/stream/<id>/media`, an MP4 (byte ranges supported).
  - `compatible: false`: the file needs a remux (MKV, or AC3/E-AC3/DTS/TrueHD/FLAC audio) and this server has no
    ffmpeg; it is served as it is and Safari will probably not play it, so tell the person.
  - Subtitles come as WebVTT (SRT is converted).
  - Errors: `404 unknown_version`; `422 unsupported_host`; `502 host_removed|host_not_found|host_changed|host_blocked|
    host_timeout|host_network|host_unsupported`; `504 host_timeout`; `502 bad_media`.

### Stream relay

`/api/stream/<id>/…` needs no `Authorization` (a `<video>` element and Safari's native HLS cannot send one): the id is a
random 256-bit capability bound to the session that started it.

- An unknown id, a stream older than 6 hours, a session revoked or expired since (sign-out ends its streams), or a
  request that carries another session's token: `404 not_found`. A session keeps its 6 newest streams.
- Only addresses the server resolved itself are fetched. Playlists (recognised by `#EXTM3U` or their type) are
  rewritten so every segment, variant, key and map address becomes `/api/stream/<id>/u/<payload>/<signature>` (HMAC-SHA256
  with the stream's own key); anything else answers 404. Private, loopback and link-local addresses are never reached.
- Upstream requests carry the extractor's headers (Referer, User-Agent…) and the player's `Range`, nothing of the caller.
- Segments disguised as images (a PNG/JPEG/GIF before the TS) are unwrapped.
- Remux: with ffmpeg (`ffmpeg.path` / `SOKOUL_SERVER_FFMPEG`, else `ffmpeg` on PATH; `ffprobe` beside it), a file
  whose container is not MP4/MOV or whose audio is not AAC/MP3/ALAC becomes fMP4 HLS on the fly in
  `<dataDir>/remux/<stream>`: video copied (`hvc1` tag for HEVC), audio copied when Safari plays it, AAC stereo
  192 kb/s otherwise. The playlist grows while ffmpeg works (`EVENT`): the player can seek only within what is done.
  ffmpeg reads http(s) only, and stops and its folder is deleted after 30 minutes without a request (or when the
  stream expires, or the server stops).

### Limits

- `GET /api/play/versions`: 30 per 10 minutes per account; `POST /api/play/start`: 20 per 10 minutes;
  `PUT /api/credentials/{service}`: 20 per 15 minutes (each one asks the real service). Over: `429 too_many_requests`.
- At most 2 resolutions (versions or start) at once per account: `429 busy`.
- Timeouts: TMDB 20 s per call, each site 25 s, each host 25 s, relay connect 10 s / read 30 s; playlists 4 MiB and
  subtitles 2 MiB at most.

## Backup)

`.\tools\server.ps1 backup [-To <folder>]` (or `tools/server.sh backup [folder]`) writes
`sokoul-server-YYYYMMDD-HHMMSS.db` to `<dataDir>/backups` by default.

- If `sqlite3` is on the `PATH`, the script uses SQLite's online backup (`.backup`). The copy is consistent while the
  server runs.
- Without `sqlite3`, the script refuses to run while the server is listening. Stop the server first; the script then
  copies the file, plus any leftover `-wal`/`-shm` files.

To restore, stop the server and copy the backup over `sokoul-server.db`. Delete any `-wal`/`-shm` files next to it.

The backup does not hold the vault key file (`vault.key`). Back that file up once, somewhere else: without it the
stored keys of the database cannot be read (see Credentials vault).

## Database

There is one file, `sokoul-server.db`, set up as follows:

- WAL mode, foreign keys on, 5 s busy timeout;
- plain JDBC, with every value bound as a parameter;
- migrations run in order at start-up, each in its own transaction, recorded in `schema_version`;
- a database newer than the server is refused;
- migration 1 creates `users` and `sessions`;
- migration 2 adds the users' name (unique, ignoring case), role (`owner` or `member`, one owner at most) and
  passphrase hash, salt and iterations, plus the `invites` and `device_links` tables.
- migration 3 adds `sync_cursor` (each account's sequence number) and one table per synced kind: `sync_profiles`,
  `sync_resume`, `sync_my_list` and `sync_title_alerts`, keyed by (user, profile, item), with `updated_at`, `deleted`
  and `seq`.
- migration 4 adds the credentials vault: `vault_keys` (the wrapped data key, one row) and `credentials` (user, service,
  sealed blob, hint, verdict, checked_at, updated_at; keyed by user and service).

To change the schema, append a migration. Never edit a shipped one.

## Next

- **Lot 4, still open**: passkeys, a real TV → phone link check, and the app side (task 5.4).
- **Lot 5, still open**: the "API et sources" settings page (6.4) and the OpenSubtitles user login (6.3b).
- **Lot 7, still open**: Real-Debrid versions on the web (its search lives in the PC app, not in a shared module),
  HLS sources with AC3 audio (relayed, not remuxed), the iPhone check of an MKV with AC3 (8.2), the IPTV lease (8.3)
  and the watch party (8.4).
