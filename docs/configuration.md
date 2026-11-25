# Configuration and secrets

## Rule

No key, token, password, `.env`, keystore or device file is ever committed. `.gitignore` excludes them, and `Redact` masks
keys in the app's logs. Private notes about the owner's devices live outside the repository
(`%USERPROFILE%\.sokoul-private`).

## Keys used by the apps

Keys come, in this order, from:

1. **Settings > Accounts** in the app (stored in the local database, table `settings`, `api_key_<id>`);
2. keys built into a private package: `local.properties` at the root, or the file named by `SOKOUL_KEYS_FILE`. The build
   turns them into a generated class under `build-output/` (never in Git).

`python tools/export-private-build-keys.py --output <file outside the checkout>` writes the keys already typed on this PC
into such a file without printing them.

| Id | Service |
| --- | --- |
| `tmdb` | The Movie Database: catalogue, artwork, people |
| `realdebrid` | Real-Debrid account |
| `omdb`, `tvdb`, `fanart` | Ratings and artwork |
| `opensubtitles` | Subtitles |
| `mainapi`, `mainapi_url` | Sokoul server key and address |

## Environment variables

### Build

| Variable | Effect |
| --- | --- |
| `ANDROID_HOME` | Android SDK; `:tv` is only included when it is set |
| `SOKOUL_KEYS_FILE` | Key file for a private package |
| `SOKOUL_BUILD_ROOT` | Alternative writable build root (default `build-output/`) |
| `SOKOUL_GRADLE_HOME` | Alternative Gradle cache (default `%TEMP%/sokoul-gradle`) |
| `SOKOUL_VLC_DIR` | libVLC folder used for the PC build |
| `SOKOUL_DEBUG_KEYSTORE` | Keystore that signs the APK |

### PC app at run time

| Variable | Effect |
| --- | --- |
| `SOKOUL_DEBUG` | Debug driver for `tools/verify.mjs`, timing logs |
| `SOKOUL_MCP` | Starts the MCP server (`1` = default port 8765, or a port number); token in `SOKOUL_MCP_TOKEN` |
| `SOKOUL_LANG` | Forces the interface language (with `SOKOUL_DEBUG`) |
| `SOKOUL_FAULTS` | Fault-injecting sources for live checks (with `SOKOUL_DEBUG`) |
| `SOKOUL_NO_TRAILERS` | Disables automatic trailers |
| `SOKOUL_ROUTE` | Logs which stream route the player opened |

### Tools and tests

| Variable | Effect |
| --- | --- |
| `SOKOUL_TV` | ADB address of the TV used by `tools/tv-*.mjs` |
| `SOKOUL_RENDER_SECTIONS` | Sections drawn by `renderScreens` in `phone` mode |
| `SOKOUL_NETWORK_TESTS`, `SOKOUL_XT_URL`, `SOKOUL_XT_USER`, `SOKOUL_XT_PASS` | Opt-in tests against live services |

### Backend

Run directly (without Docker), each service reads its own `.env` (copy the `.env.example` next to it). In Docker, those
files are left out of the images: Mainapi and proxiesembed read only `local.env` (`%LOCALAPPDATA%\SokoulApi` on Windows,
`~/Library/Application Support/SokoulApi` on macOS), which `start-backend.ps1` / `start-backend.sh` pass through
`SOKOUL_ENV_FILE`. On macOS, `start-backend.sh` also adds the keys of `API/local.env.template` that `local.env` lacks
(TMDB, debrid, Xtream, optional sources, and personal-setup switches that turn off Discord reports and payments); values
already in `local.env` are never changed.

Mainapi variables that decide who may reach the private instance:

| Variable | Effect |
| --- | --- |
| `SOKOUL_ALLOWED_ORIGINS` | Browser origins allowed with credentials, comma separated: full origins (`https://sokoul.example`, `http://192.168.0.10:8088`, matched exactly) or bare hosts (any scheme and port, no subdomains). Loopback origins (`localhost`, `127.0.0.1`, `[::1]`) are always allowed; empty means loopback only. One list for CORS and the Origin/Referer check (`API/Mainapi/utils/allowedOrigins.js`) |
| `PUBLIC_SITE_URL` | Site that opens the TV link code (`<site>/tv?code=XXXXX`); empty means Mainapi's own origin as the TV reached it |
| `SOKOUL_OAUTH_CLIENTS_JSON`, `SOKOUL_OAUTH_DEBUG` | Local OAuth client override and detailed OAuth logs |

`SOKOUL_MAINAPI_CONTEXT`, `SOKOUL_PROXY_CONTEXT`, `SOKOUL_WATCHPARTY_CONTEXT` and `SOKOUL_WEB_CONTEXT`
override the Docker build contexts in `API/docker-compose.yml`.

## Not in the repository

| Item | Where it lives |
| --- | --- |
| libVLC binaries | Downloaded by `tools/fetch-vlc.ps1` |
| Third-party reference sources | `../Sokoul-reference/` next to the checkout; licences in [third-party/README.md](third-party/README.md) |
| `API/proxiesembed/drmproxy/` | Local only; never published |
| Device connection notes, key files | `%USERPROFILE%\.sokoul-private` |
