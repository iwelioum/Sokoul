# Sokoul server

`server/` is Sokoul's own private server (Kotlin, Ktor on Netty, one SQLite file). It replaces `API/` lot by lot; see
[`openspec/changes/sokoul-server`](../openspec/changes/sokoul-server). Today (Lot 4) it has the skeleton (config,
database with migrations, structured logs, `/health`, both access locks), accounts, sessions and TV linking.

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
- **Next lots**: streams and the watch party.
