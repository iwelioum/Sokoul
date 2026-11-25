# Sokoul

A media centre for films, series, anime and live TV, with an Apple TV look. One Kotlin codebase runs on:

- **Windows PC** — Compose Desktop, playback through libVLC, mouse, keyboard and remote;
- **Android TV and phones** — the same screens with Media3; D-pad focus on TV, touch on phones (all navigation in the top menu
  behind the avatar, full-screen landscape player).

Optional services in [`API/`](API/README.md) add accounts, server-side sources, a signed media proxy and watch parties.

## Features

- Catalogue from TMDB: home rows, title pages, sagas and collections, people, search, discovery and a year review.
- Sources: IPTV providers (M3U, Xtream, XMLTV guide), Real-Debrid, server and web sources; versions ranked by quality and
  language.
- Player: subtitles (online search, styling, translation), audio tracks, skip passages, next episode, picture format,
  sleep timer.
- Live TV: guide grid, favourites and personal collections, reminders, replay, recordings.
- Anime and manga pages with a manga reader.
- Local profiles (kids profile, *who is watching*), My List, title alerts, watch parties, screensaver.
- English and French interface.

## Quick start

```powershell
# PC app
powershell -NoProfile -File tools/fetch-vlc.ps1
./gradlew.bat :app:run

# Android TV / phone APK (needs the Android SDK)
powershell -NoProfile -File tools/build-tv.ps1

# Backend (needs Docker Desktop)
powershell -NoProfile -File API/start-backend.ps1
```

On macOS (Apple silicon or Intel, JDK 17+ and Node.js 20+):

```bash
tools/run-desktop.sh            # PC app; downloads libVLC on first run (or double-click Sokoul.command)
tools/install-desktop.sh        # builds "Sokoul PC.app" and installs it in Applications
tools/build-tv.sh               # Android TV / phone APK (needs the Android SDK)
API/start-backend.sh            # Backend (needs Docker Desktop; or double-click "API/Start Backend.command")
```

Keys (TMDB, Real-Debrid…) are never in the repository: type them in **Settings > Accounts**, or see
[docs/configuration.md](docs/configuration.md).

## Documentation

| Document | Content |
| --- | --- |
| [Architecture](docs/architecture.md) | Modules, PC/Android split, form factors, packages, playback flow |
| [Building and running](docs/building.md) | PC, TV and phone (Wi-Fi ADB), backend, extraction runtime |
| [Configuration](docs/configuration.md) | Keys, environment variables, secrets policy |
| [Data](docs/data.md) | Where the apps and the backend store data |
| [Testing](docs/testing.md) | Unit tests, screen renders, live checks |
| [Contributing](docs/contributing.md) | Language, design, code rules, OpenSpec workflow |
| [Tools](docs/tools.md) | Scripts in `tools/` |
| [Design](docs/design/cartography.md) | Apple TV reference measurements; [TV home status](docs/design/tv-home.md) |
| [Third-party](docs/third-party/README.md) | Licences and attributions |

Module notes: [parser](parser/README.md), [core](core/README.md), [extractor](extractor/README.md),
[app](app/README.md), [tv](tv/README.md), [API](API/README.md), [extraction](extraction/README.md).

## Licence

Third-party code and its licences (some of it CC BY-NC 4.0, not for commercial use): see [docs/third-party](docs/third-party/README.md).
