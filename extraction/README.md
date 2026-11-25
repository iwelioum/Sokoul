# Shared extraction runtime

Derived third-party code (CC BY-NC 4.0): see [docs/third-party/README.md](../docs/third-party/README.md).

This folder holds the host extraction algorithms that two Sokoul consumers run:

| Consumer | How |
| --- | --- |
| `:extractor` Gradle module (PC and TV apps) | Runs the QuickJS bundle that `extractor/scripts/build-native-core.mjs` builds from `runtime/` (`extractor/build.gradle.kts`) |
| Mainapi native extraction | Bundle built by `scripts/build-native-extractors.mjs`, packaged by `API/prepare-build-context.js` |

## Layout

| Path | Content |
| --- | --- |
| `runtime/extractors-core.js` | Canonical implementation of the host extractors; edit this, never a generated copy |
| `runtime/fsvid-vidzy-quickjs-entry.js` | Entry of the QuickJS bundle that decodes dynamic players |
| `scripts/build-native-extractors.mjs` | Builds the self-contained Mainapi module |
| `tests/` | `native-extractors.test.mjs` and its fixtures (also read by the `:extractor` parity test) |

## Commands

```powershell
cd extraction
npm ci --no-audit --no-fund
npm test
node scripts/build-native-extractors.mjs --output <temporary-directory>
```

The native builder exports `buildNativeExtractors(outputDirectory)`: a self-contained CommonJS adapter, an isolated
worker, the shared algorithms, the bundled QuickJS runtime, a pinned local WASM and dependency licences. Production
containers never download WASM from a CDN. QuickJS 0.32.0 and esbuild 0.28.1 are locked in `package-lock.json`.

Native `extract(url, { fetch, timeoutMs, signal })` returns `{ success, url, format, headers, subtitles, source }` or
`{ success: false, source, error }`; `detectHoster(url)` and `supportedHosts` are exported too. The caller supplies a
fetch-compatible transport using the same outbound route as playback (Mainapi uses proxiesembed's `native-fetch`); without
it extraction returns `transport_unavailable`. Response size, redirects, worker lifetime and QuickJS memory, stack and
deadline are bounded, and errors are stable codes without signed URLs.

Some hosts bind tokens to IP, user agent, language or HTTP fingerprint: matching fixtures proves parser compatibility, not
availability.
