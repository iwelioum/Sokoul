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

- **Owner (first start).** While the database has no user, the server logs `No account yet`. `POST /api/setup`
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

## Backup

`.\tools\server.ps1 backup [-To <folder>]` (or `tools/server.sh backup [folder]`) writes
`sokoul-server-YYYYMMDD-HHMMSS.db` to `<dataDir>/backups` by default.

- If `sqlite3` is on the `PATH`, the script uses SQLite's online backup (`.backup`). The copy is consistent while the
  server runs.
- Without `sqlite3`, the script refuses to run while the server is listening. Stop the server first; the script then
  copies the file, plus any leftover `-wal`/`-shm` files.

To restore, stop the server and copy the backup over `sokoul-server.db`. Delete any `-wal`/`-shm` files next to it.

## Database

There is one file, `sokoul-server.db`, set up as follows:

- WAL mode, foreign keys on, 5 s busy timeout;
- plain JDBC, with every value bound as a parameter;
- migrations run in order at start-up, each in its own transaction, recorded in `schema_version`;
- a database newer than the server is refused;
- migration 1 creates `users` and `sessions`;
- migration 2 adds the users' name (unique, ignoring case), role (`owner` or `member`, one owner at most) and
  passphrase hash, salt and iterations, plus the `invites` and `device_links` tables.

To change the schema, append a migration. Never edit a shipped one.

## Next

- **Lot 4, still open**: passkeys, a real TV → phone link check, and the app side (task 5.4).
- **Lot 5 onwards**: the credentials vault, sync, streams, and the watch party.
