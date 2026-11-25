# Contributing

## Language

- Code, comments, KDoc, logs and documentation are in **English**.
- French appears only as the French half of a translated interface text: `L("en", "fr")` for one-off texts, or the
  `Text` tables of `i18n/Strings.kt` for shared ones. Every new text has both halves.

## Design

- Keep the Apple TV look: [design/cartography.md](design/cartography.md) and `theme/Theme.kt` are the source of truth.
- Build with Sokoul's components (`ui/Controls.kt`, `ui/Cards.kt`, `ui/Panels.kt`, the player's glass panels). A feature
  taken from third-party code is redrawn with them, never with its original look.
- Every screen works with a remote (D-pad focus, Back), a mouse and touch. On phones, read `Form` (`ui/FormFactor.kt`)
  rather than testing the platform.
- Brand assets come from the Figma file `sokoul` (app icon node `15:2`, intro node `3:1461`).
- The home page follows [design/tv-home.md](design/tv-home.md); update its status table when a principle changes.

## Code

- Shared screens live in `app/src/main/kotlin`. Anything that uses AWT, Skia, libVLC or `com.sun.net.httpserver` must be
  excluded from the TV build in `tv/build.gradle.kts` (`syncShared`) and given an Android counterpart under `tv/` when the
  TV needs it ([architecture.md](architecture.md#one-codebase-two-platforms)).
- Development-only code (renderers, audits, trials) goes to `app/src/dev/kotlin`, never to the main source set.
- An endless loop in a composable runs off the UI dispatcher (`withContext(Dispatchers.Default)`).
- Backend: `Mainapi` stays CommonJS with parameterised SQL; `proxiesembed` stays aiohttp.
- Never print, log or commit a key ([configuration.md](configuration.md)).

## Changes (OpenSpec)

Non-trivial work goes through OpenSpec (`openspec/`):

1. Propose a change under `openspec/changes/<name>` (proposal, design, tasks, delta specs).
2. Implement it task by task; each task names its check (test, render or tool command).
3. When every task is done, archive the change and sync its specs into `openspec/specs/`.

## Checks

State which checks really ran ([testing.md](testing.md)). A compile does not prove a screen looks right: render it, or
check it on the device.

## Third-party code

Derived third-party code and its licences are listed in [third-party/README.md](third-party/README.md); a derived file
carries at most one pointer line to it (`Derived third-party code (CC BY-NC 4.0): see docs/third-party/README.md.`). The
reference sources themselves are kept outside the repository.
