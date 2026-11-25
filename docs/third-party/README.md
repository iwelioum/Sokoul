# Third-party code and licences

| Component | Licence | Where it is used | Notice |
| --- | --- | --- | --- |
| Movix (upstream project) | CC BY-NC 4.0 | The derived files listed below | [MOVIX-LICENSE](MOVIX-LICENSE) |
| quickjs-kt 1.0.15 | Apache 2.0 | `:extractor` | `extractor/LICENSE-QUICKJS-KT` |
| Inter, Inter Display | SIL OFL 1.1 | App font | [../design/font-comparison/Inter-OFL.txt](../design/font-comparison/Inter-OFL.txt) |
| libVLC 3.0.21 | LGPL 2.1+ | PC playback (downloaded by `tools/fetch-vlc.ps1`, not versioned) | VideoLAN |
| curl-impersonate | See its bundled LICENSE files | Android HTTP transport (fetched by `tools/prepare-curl-impersonate.py`) | Copied to `licenses/curl-impersonate` in the build output |

## Derived code still to be rewritten

These files are adapted from CC BY-NC 4.0 material: attribution is required and commercial use is not allowed. Each
carries the header `Derived third-party code (CC BY-NC 4.0): see docs/third-party/README.md.` Sokoul replaces them with
its own implementations; when a file is rewritten, remove its header and its line here. When the list is empty, this
section, the table row above and `MOVIX-LICENSE` go too.

| Area | Files |
| --- | --- |
| Server | `API/Mainapi/` (most routes, middleware and services), `API/watchpartyAPI/`, `API/proxiesembed/` (including `fsvid_vidzy_js_worker.py`) — to be replaced by the lean Sokoul server |
| Extraction runtime | `extraction/runtime/extractors-core.js`, `extraction/runtime/fsvid-vidzy-quickjs-entry.js` |
| Extractor bridge | `extractor/src/main/kotlin/com/sokoul/extract/ExtractorRuntime.kt`, `extractor/src/main/resources/sokoul-extract/host.js`, `extractor/src/main/resources/sokoul-extract/polyfills.js` |
| Playback transport | `core/src/main/kotlin/com/sokoul/desktop/player/PngWrappedMpegTs.kt`, `tv/src/main/kotlin/com/sokoul/desktop/player/PngTsDataSource.kt` and its test |
| Discovery | `core/src/main/kotlin/com/sokoul/iptv/discovery/Taste.kt`, `core/src/main/kotlin/com/sokoul/iptv/discovery/YearReview.kt` |
| Series data | `app/src/main/kotlin/com/sokoul/pc/data/SegmentedSeasons.kt` |
| Watch party | `app/src/main/kotlin/com/sokoul/pc/party/PartySyncEngine.kt` |
