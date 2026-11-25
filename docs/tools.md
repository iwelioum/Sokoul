# Tools

Scripts in [`tools/`](../tools). None of them prints a key; private inputs go to `%TEMP%` or `%USERPROFILE%\.sokoul-private`.

## Build and install

| Script | Purpose |
| --- | --- |
| `gradle-windows.ps1` | Windows bootstrap behind `gradlew.bat`: caches outside OneDrive, signing key, libVLC materialisation ([GRADLE.md](../tools/GRADLE.md)) |
| `build-tv.ps1`, `build-tv.sh` | Build the Android release APK |
| `fetch-vlc.ps1` | Download libVLC 3.0.21 (checksum-verified) into `app/app-resources/windows/vlc` |
| `materialize-vlc.cjs` | Copy cloud-only libVLC files into ordinary temporary files for Gradle |
| `install-desktop.ps1` | Install the PC distribution for the current user |
| `adb-wifi-install.ps1` | Pair, connect and install the APK over Wi-Fi ADB |
| `export-private-build-keys.py` | Write the keys typed on this PC into a private build input, without printing them |
| `prepare-private-tv-config.py`, `configure-tv-private.py` | Prepare and import a private IPTV source on a TV (owner's devices only) |
| `prepare-curl-impersonate.py` | Fetch the pinned curl-impersonate Android binaries into the build output |
| `server.ps1`, `server.sh` | Sokoul server: `run`, `backup`, and printed steps for `install-service` and `tunnel` ([server.md](server.md)) |

## Live checks

| Script | Purpose |
| --- | --- |
| `verify.mjs`, `verify-lib.mjs` | Drive the running PC app (`SOKOUL_DEBUG=1`) and report what really happened: `node tools/verify.mjs <part>` |
| `live.ps1` | Start, drive and capture the PC window through the debug driver |
| `screen-shot.ps1` | Capture what is really on screen in the app's window |
| `audio-peak.ps1` | Read Windows' output meter to prove sound is playing |
| `tv-lib.mjs`, `tv-perf.mjs` | Remote, tools and frame-time measurements on a TV over ADB (`SOKOUL_TV`) |
| `verify-formuler-guide.py`, `GuideKeyDriver.java`, `build-guide-keydriver.ps1`, `CompareGuideRenders.java` | Guide checks on a physical TV and pixel comparison of guide renders |

## Tests

| Script | Purpose |
| --- | --- |
| `test_prepare_private_tv_config.py` | Tests for `prepare-private-tv-config.py` (`python -m pytest tools`) |
