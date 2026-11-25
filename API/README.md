# Sokoul backend services

`API/` holds the server side of Sokoul.
Derived third-party code (CC BY-NC 4.0): see docs/third-party/README.md. The native apps use three services; MySQL and Redis back them.

| Folder | Role | Port | Stack | Docs |
| --- | --- | --- | --- | --- |
| `Mainapi/` | Accounts and profiles, sync, catalogue and source routes, native extraction, live TV | 25565 | Node.js, CommonJS, Express, MySQL, Redis | [README](Mainapi/README.md) |
| `watchpartyAPI/` | Real-time watch party rooms | 25566 | Node.js, ESM, Socket.IO | [README](watchpartyAPI/README.md) |
| `proxiesembed/` | Media proxy for embeds and streams | 25569 | Python, aiohttp | [README](proxiesembed/README.md) |

Root files:

| File | Purpose |
| --- | --- |
| `docker-compose.yml` | MySQL, Redis and the three services; optional `web` profile (8088) built from the third-party web reference kept outside the repository (`SOKOUL_WEB_CONTEXT`) |
| `start-backend.ps1` | Starts, checks (`-Status`) and stops (`-Stop`) the local stack |
| `prepare-build-context.js` | Copies source bytes into a temporary Docker build context (avoids OneDrive reparse points) |
| `init-local-schema.js`, `seed-local-key.js` | Create missing tables, seed the local access key |
| `backup-backend.js` | Backup and restore of the database and private configuration |
| `SOURCE-INTEGRATION.md` | Per-provider integration audit |
| `tests/` | Tests of the scripts above (`node --test API/tests`) |

Rules: `Mainapi` stays CommonJS with parameterised SQL; `proxiesembed` stays aiohttp; a `.env` is never committed (copy the
`.env.example` next to each service).

## Local backend for Windows and Android TV

The native applications use MySQL 8.4, Redis 7, Mainapi (25565), the aiohttp media proxy (25569), and WatchParty (25566). Docker Desktop must have its Linux engine running; Node.js and Mainapi's installed dependencies are needed for schema initialization and access-key seeding. Existing module READMEs describe individual services.

```powershell
powershell -ExecutionPolicy Bypass -File API/start-backend.ps1
powershell -ExecutionPolicy Bypass -File API/start-backend.ps1 -Status
powershell -ExecutionPolicy Bypass -File API/start-backend.ps1 -Stop
# Optional legacy web product; it is excluded from the native delivery:
powershell -ExecutionPolicy Bypass -File API/start-backend.ps1 -WithWeb
# Choose the PC's reachable LAN address on machines with multiple adapters:
powershell -ExecutionPolicy Bypass -File API/start-backend.ps1 -LanAddress 192.168.0.100
```

Startup preserves existing credentials, initializes missing SQL tables and seeds the local access key. It waits for MySQL/Redis health and all three HTTP health checks before printing Ready. HTTP checks prove service liveness, not external provider availability or successful playback. `-Status` reports container state and the three HTTP checks and exits unsuccessfully when a check fails. Port conflicts with native Node/Python services are reported without terminating those processes. `-Stop` retains the MySQL volume.

`prepare-build-context.js` materializes source bytes from this checkout into a new `%TEMP%/sokoul-api-build-*` directory before each native Docker build. This avoids BuildKit's `invalid file request Dockerfile` failure on Windows cloud reparse points. Credentials, dependencies, caches and tests are excluded. The temporary context is disposable and does not contain user database volumes. The optional historical web build still uses its own source directory. Compose 5.6.1 and Docker Engine 29.9.2 were used for this workflow.

Configuration is stored in `%LOCALAPPDATA%/SokoulApi/local.env` and the application access key in `access.key` alongside it. Neither is printed or baked into Docker images. Public media URLs are refreshed with the PC's LAN address at startup. The PC app uses `127.0.0.1:25565`; configure the TV's Sokoul server address with the reachable PC address and its key through the credentials screen or `set_sokoul_server`. Check Windows firewall access from the TV; an HTTP check on the PC alone does not prove LAN reachability. If the router changes the PC address, restart the backend and update the TV setting.

### Backup and restore

```powershell
# Defaults to a new private directory in %LOCALAPPDATA%/SokoulApi/backups:
node API/backup-backend.js
# Or use a new directory outside the source checkout:
node API/backup-backend.js D:/PrivateBackups/Sokoul-2026-11-14
# Explicitly replace SQL data from a completed backup on this installation:
node API/backup-backend.js --restore D:/PrivateBackups/Sokoul-2026-11-14 --confirm-restore
```

A completed backup contains `database.sql`, `local.env`, the access key if present, and `complete.json`. A missing completion manifest means the snapshot must not be used. Backups contain secrets and must remain in private storage. SQL bytes are streamed without shell redirection or console transcoding. The snapshot uses a single transaction; stop application writes for a fully quiescent backup if nontransactional tables are present.

Restore requires the same database name and password as the archived configuration. It stops application containers, creates a separate completed pre-restore recovery backup, imports the saved tables, and restores the access-key file. MySQL remains running; applications stay stopped on success or failure. Run `start-backend.ps1` after a successful restore. For migration to a different installation, provision a fresh MySQL volume with the archived private configuration before importing; do not overwrite credentials of an unrelated existing database.

The backend backup does not include desktop/TV SQLite profiles, recordings, optional source snapshots, or container-local provider caches. Back those up separately when moving devices. WatchParty rooms are transient and are not restored. A restore imports captured tables; tables introduced after the snapshot may remain and schema initialization is additive.

### Verification and current limits

```powershell
node --test API/tests/backup-backend.test.cjs API/tests/build-context.test.cjs
node --check API/backup-backend.js
```

The recovery regression uses a fake Docker subprocess and checks UTF-8 preservation, confirmation gating, backup-before-restore ordering, access-key recovery and rejection of a mismatched database configuration. It does not validate a real MySQL dump/import. Use `-Status` and real playback on both platforms to validate an actual deployment.

Some external sources can refuse requests or require their own credentials. Optional anime playback also needs a separately deployed backend URL; it is not supplied by this stack. Provider failure must not be reported as a successful playback check.
