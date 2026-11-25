# Sokoul server

`server/` is Sokoul's own private server (Kotlin, Ktor on Netty, one SQLite file). It replaces `API/` lot by lot; see
[`openspec/changes/sokoul-server`](../openspec/changes/sokoul-server). Today (Lot 3) it has the skeleton: config,
database with migrations, structured logs, `/health`, and both access locks. Accounts arrive in Lot 4.

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
2. **Session lock**: every `/api/*` route needs a Sokoul session. Until Lot 4, `RefuseAllSessions` refuses everyone, so
   `GET /api/me` answers `401 {"error":"unauthorized"}` even behind a valid Access assertion.

Errors are always `{"error":"<stable_code>"}`. Responses never include stack traces or exception messages.

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
   - `/api/me` answers 401.

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
- migration 1 creates `users` and `sessions`.

To change the schema, append a migration. Never edit a shipped one.

## Next

- **Lot 4**: the owner account (passphrase, then passkey), sessions with hashed tokens and a 30-day sliding expiry, the
  device list with revocation, and TV linking by code and QR. A real `SessionAuthenticator` replaces
  `RefuseAllSessions`.
- **Lot 5 onwards**: the credentials vault, sync, streams, and the watch party.
