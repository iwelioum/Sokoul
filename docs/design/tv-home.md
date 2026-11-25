# TV home page design: living status

Adaptation to Sokoul of the UX report [*Conception UX Accueil App TV*](tv-home-ux-report.pdf) (15 pages, French). The report
gives the principles; this file says, for each one, **where Sokoul stands** and **what remains**. Update it with every change
to the home page, as [cartography.md](cartography.md) is updated for the look (rule in `openspec/config.yaml`).

Legend: **Done** (checked in code) · **Partial** · **To do** · **To check** (not found in code, to confirm on screen) ·
**Dropped** (deliberate choice).

Rules that override the report: the look stays Apple TV's ([cartography.md](cartography.md), `theme/Theme.kt`); nothing is
taken from other streaming services in appearance.

## 1. The report's principles and their status

### 1.1 Choice overload (§2)

Fewer visible options, more decisions. Services lose viewers to menus.

| Principle | Status | Where / note |
| --- | --- | --- |
| Few rows, order chosen by the user | **Done** | `data/HomeLayout.kt` (order, hiding), `library/HomeRowsScreen.kt` |
| *Continue* row first (one click to decide) | **Done** | Row plan in `data/RowBook.kt` (`PageRow`) |
| 7–10 titles per row | **Done** | `ROW_CAP = 10` in `home/HomeScreen.kt`, then a *See all* card that opens the full list (checked: `node tools/verify.mjs home`). Top 10 unchanged, *Continue* not capped |
| A row with too few titles is not shown | **Done** | `home/HomeScreen.kt` |
| *Kids* profile, *Who is watching?* | **Done** | `data/Profiles.kt`, `startup/ProfilesScreen.kt` |
| *Satisficer* mode (one suggestion: "Tonight, watch this") | **Done** | Hero star button "Tonight: like X" (titles close to the last one watched, not started, stable order through the day; Right gives another suggestion). Checked in the app |

### 1.2 Interaction ethics: autoplay (§3)

Never sound by default, a muted trailer only after a focus delay, immediate stop available.

| Principle | Status | Where / note |
| --- | --- | --- |
| No sound by default | **Done** | `heroSound`; the home used to play **with** sound, fixed (`HomeScreen.kt`, muted like the title page). Checked: `muted=true`, audio peak 0 |
| Trailer only while the hero has focus | **Done** | `HomeScreen.kt`: `if (focused && active) trailer.prepare(...) else trailer.release(...)` |
| Setting to turn off automatic trailers | **Done** | `DesktopPrefs.autoTrailers` (off by default on TV) |
| ~3 s of focus before starting | **Done** | `TrailerController.START_DELAY_MS = 3_000`; cancelled when focus leaves the hero |
| Visual cue and immediate stop button | **Done** | During a trailer the hero shows *Sound off/on* and *Stop trailer*; it does not come back on the same slide (checked in the app) |
| End of trailer → next slide (12 s without trailer) | **Done** | `SLIDE_WITHOUT_TRAILER_MS = 12_000L` |
| *Still watching* / next episode | **Done** | `PlayerStyle.kt`, `AutoNextTest` |

### 1.3 Multi-list interface, diversity, serendipity (§4)

| Principle | Status | Where / note |
| --- | --- | --- |
| Stacked thematic rows | **Done** | `PageRow`: Trending, Moods, Categories, Platforms, Sagas, Because, Discover… |
| "Because you watched…" (explanatory label) | **Done** | `PageRow.Because`, text `BecauseWatched` |
| Moods | **Done** | `PageRow.Moods` |
| Discovery outside habits | **Partial** | `PageRow.Discover` exists; the relevance / novelty / diversity balance is not measured |
| "Liked by similar viewers" label | **To do** | No such signal without a server account |
| Top 10 | **Done** | `RowStyle.Top10` |
| Rows per profile | **Done** | `data/Profiles.kt` (resume points, history, list, alerts per profile) |

### 1.4 Focus and remote engineering (§5)

| Principle | Status | Where / note |
| --- | --- | --- |
| Visible focus (scale, highlight, elevation) | **Done** | `ui/Focus.kt`, `ui/Cards.kt` (`FocusCard`) |
| Return to the last item of a row | **Done** | `ui/Cards.kt` (`CardRow`, `enter` targets the last card still shown). `focusRestorer` did not work with the row's left/right rule. Test `HomeFocusTest` |
| Safe margins (overscan, ~5 %) | **Done, unchanged** | `PageMargin` 80 dp (4.2 %) and `keepClearOfEdges` keep the focused card off the edge; Google TV has no overscan |
| Dead-end nudge (focus never disappears) | **Done** | `ui/Focus.kt` (`Nudge`) on cards and round buttons, towards Right and Down, in the existing graphics layer. Test `HomeFocusTest` |
| Remote navigation tests | **Done** | `PlayerNavTest`, `HoldScrollTest`, `InterfaceTest` |
| Long press to scroll | **Done** | `HoldScrollTest` |

### 1.5 Ambient aesthetics, performance (§6)

| Principle | Status | Where / note |
| --- | --- | --- |
| Dynamic background from the artwork | **Done (title page)** | `title/TitleScreen.kt`: 12 % of the image's vivid colour over the dark background, flat and set at once (an animated colour recomposed the page and cost frames on TV). Measured on TV: not slower than without tint. No mesh gradient |
| Limited blur (low fill-rate TV GPUs) | **Done** | No live blur outside the intro; panels use pre-darkened fills |
| Images decoded at display size | **Done** | `ui/Art.kt`: `ImageLoader.bucket(width)`, `cachedAny`; test `ImageSizeTest` |
| Desaturated → colour image fade | **Done** | `ui/Art.kt` |
| Animated skeletons while loading | **Done** | `ui/Skeleton.kt` (hero and two waiting rows); they never take focus. Seen in `renderScreens … loading` |
| Android audio focus (ducking) | **Done** | `tv/.../DesktopPlayer.kt`: with sound, Sokoul takes audio focus (seen in `dumpsys audio`) and gives it back on close; a muted image does not ask for it |
| Hover sounds | **Dropped** | Apple TV has none worth noting; the report only says they "reinforce perception" |

### 1.6 Server-driven UI (§7)

| Principle | Status | Where / note |
| --- | --- | --- |
| Home described by a row plan | **Partial** | `PageRow`: the plan is data, but defined in the app and reordered by the user (`HomeLayout`) |
| Plan sent by the server, schema version, fallback component | **Dropped for now** | Sokoul has no app store to bypass; the value would be A/B testing (3 vs 8 rows). Reopen if `API/Mainapi` hosts profiles |
| Load by priority (top first) | **Done** | `HomeScreen.kt`: rows load in the background, top first |
| Multi-level cache | **Partial** | Search and request caches (`SearchCacheTest`, `purgeCache`); no server cache of the home |

## 2. Verification records

- **tv-home-redesign** (archived OpenSpec change): 20/20 tasks, checked in the PC app (`node tools/verify.mjs home`), on the
  TV (`tools/tv-perf.mjs`, `dumpsys audio`) and by `HomeFocusTest`. Follow-ups on 2026-11-10: the first hero slide was black
  at start-up because the fade in `ui/Art.kt` stalled when the window resized during it (fixed, seen on PC and TV); *See
  all* works with the remote on TV.
- **Formuler hero focus**: the hero remembers its last action when returning from a row, the navbar or a title page.
  Verified on a Formuler Z11 Pro Max with ten About/row/About cycles, Next/row/Next, title/Back and a six-row lazy-disposal
  round trip (OpenSpec `stabilize-formuler-tv`).
- **Saga animation**: decorative saga animation suspends while another overlay is on top. On the Formuler, the X-Men 2
  detail screen rendered 184/185/182 frames over three idle three-second samples before, 0/0/0 after. Back and D-pad
  navigation unchanged.
- **Collection grids**: vertical focus-safe scrolling; the selected card is fully visible (68 px below it, previously
  clipped). Twenty D-pad moves across category and platform grids plus rapid Down/Up.
- **Anime loading** (`optimize-anime-sources`): hero candidate batch reduced from 24 to 8, title matching shared across
  rows, title rows started in groups of three with scroll-driven prefetch. No fluidity principle is promoted from these
  measurements: first-scroll jank remains; a two-row idle preparation was measured, rejected and removed. Details in the
  change's `research.md`.
- **Immersive TV navigation**: on TV the shell withdraws the navigation capsule and top scrim once content has focus
  (220 ms opacity/translation); Up and Back bring it back; the guide hides it sooner. Desktop and phones keep the bar.
  Formuler visual acceptance and frame measurements are pending.

## 3. How to update this file

- After a change to the home page, set the row to **Done** and name the file.
- A **To check** row becomes **Done** or **To do** after a render (`:app:renderScreens`) or a TV check
  (`tools/tv-perf.mjs`, `tools/verify.mjs home`).
- A dropped idea says why (note column).
- The report's figures (Iyengar and Lepper: 3 % → 30 %; autoplay: −21 min of viewing per day; 7–10 titles per carousel)
  are arguments, not targets measured in Sokoul.
