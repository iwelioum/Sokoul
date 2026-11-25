# Building and running

Everything is built from this checkout. Build outputs go to `build-output/` (ignored by Git); Gradle's dependency and project
caches stay in `%TEMP%` so that OneDrive never syncs them. The Windows bootstrap behind `gradlew.bat` is described in
[tools/GRADLE.md](../tools/GRADLE.md).

## Requirements

| For | Needs |
| --- | --- |
| PC app | Windows 10/11 x64 or macOS (arm64/x64), JDK 17+, libVLC 3.0.21 (see below) |
| TV / phone app | Android SDK (`ANDROID_HOME`, default `%LOCALAPPDATA%\Android\Sdk`, or `~/Library/Android/sdk` on macOS), platform 36, build-tools 36 |
| Backend | Docker Desktop (MySQL, Redis), or Node.js 20+ and Python 3.11+ for running services directly |
| Extension and extractor bundle | Node.js 20+ |

## API keys

Catalogue and provider keys are never stored in Git. A build reads them from the untracked `local.properties` at the root,
or from the file named by `SOKOUL_KEYS_FILE`. Without built-in keys the app still runs: keys can be typed in
**Settings > Accounts**. See [configuration.md](configuration.md).

## PC app (Windows)

```powershell
powershell -NoProfile -File tools/fetch-vlc.ps1        # once: downloads libVLC into app/app-resources/windows/vlc
./gradlew.bat :app:run                                 # run from sources
./gradlew.bat :app:createDistributable                 # build the app folder
powershell -NoProfile -File tools/install-desktop.ps1  # install it for the current user
```

The distribution is written to `build-output/app/compose/binaries/main/app/Sokoul PC`. `SOKOUL_VLC_DIR` points the build at
another libVLC folder.

libVLC is not versioned. `tools/fetch-vlc.ps1` downloads the official VideoLAN archive (`vlc-3.0.21-win64.zip`), checks its
published SHA-256 and keeps `libvlc.dll`, `libvlccore.dll` and `plugins/`.

## PC app (macOS)

Needs a JDK 17+ (`brew install openjdk@17`) and Node.js 20+ (`brew install node`). Apple silicon and Intel are supported.

```bash
tools/fetch-vlc.sh       # once: downloads libVLC into app/app-resources/macos/vlc (run-desktop.sh does it if missing)
tools/run-desktop.sh     # run from sources; extra arguments go to Gradle
```

`Sokoul.command`, at the root, does the same on a Finder double-click.

To install it as a regular Mac app, `tools/install-desktop.sh` builds `Sokoul PC.app` (Java runtime and libVLC inside) and
copies it to `/Applications` (or `~/Applications`), keeping the previous version in a temporary folder. Run it again after
pulling changes. Compose refuses to package with Homebrew's JDK, so packaging needs another vendor's JDK 17+ (for
example `brew install --cask corretto@17`); the script picks it. On macOS, libVLC enters the bundle through Compose's app
resources (`build-output/app/vlc-app-resources/macos/vlc`) before the bundle is signed. `tools/fetch-vlc.sh` downloads the official
VideoLAN disk image matching the Mac (`vlc-3.0.21-arm64.dmg` or `-intel64.dmg`), checks its published SHA-256 and keeps
`lib/` and `plugins/` from `VLC.app/Contents/MacOS`. Without it, the app also uses an installed `/Applications/VLC.app`.
`SOKOUL_VLC_DIR` can point at another folder with the same layout. The scripts pick a JDK with `/usr/libexec/java_home`
unless `JAVA_HOME` is set.

On macOS the app keeps its data in `~/.sokoul/SokoulPC`, and the local backend's settings and access key in
`~/Library/Application Support/SokoulApi`. Trailers need `yt-dlp` (`brew install yt-dlp`).

## Android TV and phone app

```powershell
powershell -NoProfile -File tools/build-tv.ps1
```

The release APK is `build-output/tv/outputs/apk/release/tv-release.apk`. It is signed with the local debug key (it is
installed by hand on the owner's devices), or with the keystore named by `SOKOUL_DEBUG_KEYSTORE`.

The same APK runs on Android TV and on phones: the activity detects the form factor at run time (see
[architecture.md](architecture.md#form-factors)).

### Installing over Wi-Fi (ADB)

1. On the device, enable *Developer options > Wireless debugging*.
2. Pair once with the code shown under *Pair device with pairing code*:
   `adb pair <ip>:<pairing-port> <code>`.
3. Connect with the address of the main *Wireless debugging* screen (a different port):
   `adb connect <ip>:<port>`.
4. Install without touching the app's data: `adb -s <ip>:<port> install -r build-output/tv/outputs/apk/release/tv-release.apk`.

`tools/adb-wifi-install.ps1 -PairAddress <ip>:<pairing-port>` runs these steps. The PC and the device must be on the same
network.
On macOS: `tools/adb-wifi-install.sh --pair <ip>:<pairing-port> --connect <ip>:<port>` (or `--connect <ip>` for an older
Android TV on port 5555).

To build the keys already typed in the PC app into a private APK, export them outside the checkout and point the build at
them (works on Windows and macOS):
`python3 tools/export-private-build-keys.py --output <file>`, then build with `SOKOUL_KEYS_FILE=<file>`.

## Backend (`API/`)

```powershell
cd API
copy Mainapi\.env.example Mainapi\.env           # then fill in the values
copy proxiesembed\.env.example proxiesembed\.env
powershell -NoProfile -File start-backend.ps1     # MySQL, Redis, Mainapi, proxiesembed and watch party in Docker
```

On macOS: `API/start-backend.sh` (options `--status`, `--stop`, `--with-web`, `--lan-address <ip>`), or a double-click on
`API/Start Backend.command`. It installs the npm dependencies the Node helpers need on first run.

| Service | Port | Source |
| --- | --- | --- |
| Mainapi (Node, CommonJS) | 25565 | `API/Mainapi` |
| Watch party (Node, ESM) | 25566 | `API/watchpartyAPI` |
| proxiesembed (Python, aiohttp) | 25569 | `API/proxiesembed` |
| Web site (optional, `--profile web`) | 8088 | Third-party web reference outside the repository, `SOKOUL_WEB_CONTEXT` |

The PC app talks to `http://127.0.0.1:25565` by default; a TV or phone needs the PC's address on the local network
(Settings > Accounts > Sokoul server).

## Extraction runtime and extractor bundle

`extraction/` holds the shared extraction runtime used by two consumers: the Mainapi native extraction and the
`:extractor` Gradle module (QuickJS). Gradle rebuilds the QuickJS bundle from `extraction/runtime` when it changes;
`npm ci` in `extraction/` is needed once. Details in [extraction/README.md](../extraction/README.md).

```powershell
cd extraction; npm ci --no-audit --no-fund
npm test                    # extractor fixtures
npm run build:native        # bundle used by Mainapi's native extraction
```

There is no browser extension or userscript any more.
