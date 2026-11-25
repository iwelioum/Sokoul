# :tv

The Android app for TVs and phones (`com.sokoul.tv`, min SDK 26, target 35). It compiles the shared screens of
`app/src/main/kotlin` (copied by `syncShared`, minus desktop-only files) and adds the Android pieces.

| Path | Content |
| --- | --- |
| `com/sokoul/tv/MainActivity.kt` | Activity: remote keys mapped to the keyboard the screens know, form factor (TV, phone sideways or upright), phone orientation while playing, Watch Next links |
| `com/sokoul/tv/TvApp.kt` | Application: native libraries, context |
| `com/sokoul/tv/WatchNext.kt` | *Continue watching* row of the Android TV home screen |
| `com/sokoul/tv/RecordingService.kt`, `RecordingWakeReceiver.kt` | Recordings in the background and at a scheduled time |
| `com/sokoul/tv/A11yGate.kt`, `TvDebug.kt`, `TvTrailers.kt` | Accessibility cost control, debug tools, trailer streams |
| `com/sokoul/pc/Platform.kt`, `com/sokoul/pc/ui/VideoFill.kt` | Android counterparts of the PC files |
| `com/sokoul/desktop/player/` | Media3 player, curl-impersonate transport, cookie jar, PNG-wrapped TS (derived third-party code, see `docs/third-party/README.md`) |
| `FontShim.kt`, `ResShim.kt`, `Shims.kt` | Compose shims for APIs the shared code takes from Compose Desktop |

Build: `powershell -NoProfile -File tools/build-tv.ps1` (needs `ANDROID_HOME`). Install over Wi-Fi:
[docs/building.md](../docs/building.md#installing-over-wi-fi-adb).
