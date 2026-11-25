# Testing and checks

Say which checks really ran. A compile does not prove a screen looks right: render it.

## Kotlin

| Module | Command | Notes |
| --- | --- | --- |
| `:parser` | `./gradlew.bat :parser:test` | `RealWorldCheck` and `XtreamLiveCheck` only run with `SOKOUL_NETWORK_TESTS` / `SOKOUL_XT_*` |
| `:core` | `./gradlew.bat :core:test` | |
| `:extractor` | `./gradlew.bat :extractor:test` | Runs the QuickJS bundle against fixtures |
| `:app` | `./gradlew.bat :app:test --tests "com.sokoul.pc.<Test>"` | Compose UI tests run headless |
| `:tv` | `./gradlew.bat :tv:testReleaseUnitTest` | Needs `ANDROID_HOME` |

Endless loops in a composable (`while (true) { delay() }`) must run off the UI dispatcher
(`withContext(Dispatchers.Default)`), or UI tests never reach idle.

## Screens without a window

```powershell
./gradlew.bat :app:renderScreens -PrenderOutput=renders -PrenderMode=<mode>
```

| Mode | Renders |
| --- | --- |
| *(none)* | Setup, home and title page at 1920×1080 |
| `hero` | The home hero at several window shapes |
| `phone` | Every section on a phone, upright and sideways (`SOKOUL_RENDER_SECTIONS` narrows the list) |
| `intro`, `settings`, `rows`, `profiles`, `search`, `guide`, `workspace`, `panels`, `sources`, `watchlist`, `loading` | One screen family each |

Catalogue renders need a TMDB key (see [configuration.md](configuration.md)).

## The running app

- `node tools/verify.mjs <part>` drives the PC app started with `SOKOUL_DEBUG=1`.
- With `SOKOUL_MCP=1`, the app's tools are served over MCP (`control/McpServer.kt`).
- `node tools/tv-perf.mjs` measures frame times on a connected TV.

## Backend and extraction runtime

| Part | Command |
| --- | --- |
| Mainapi | `cd API/Mainapi; node --test routes/__tests__ utils/__tests__` (Node test runner); `node --check <file>` for a quick syntax check |
| API scripts | `node --test API/tests` |
| proxiesembed | `cd API/proxiesembed; python -m pytest tests` |
| Extraction runtime | `cd extraction; npm test` |
| Private TV config | `python -m pytest tools/test_prepare_private_tv_config.py` |
