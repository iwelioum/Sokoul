# Native extractor adapter

This source directory contains only the Node transport broker and worker. The canonical eleven-host algorithms live in `extraction/runtime/extractors-core.js`. Build the deployable module using `extraction/scripts/build-native-extractors.mjs` into a temporary context; do not copy only this source directory into a running container.

The module exports `extract(embedUrl, { fetch, timeoutMs, signal })`, `detectHoster(embedUrl)` and `supportedHosts`. A successful result contains the direct upstream media URL, format (`hls` or `mp4`), playback headers, subtitles and source name. Failure returns a stable code, never an upstream error message or signed URL. Fetch is required so extraction and media relay share an egress. The caller owns VIP authorization, approved embed provenance and signed media relay registration; this module does not create a public extraction endpoint.

The worker receives HTTP responses through parent RPC and cannot open its own network requests. It has a 128 MiB old-generation limit, a bounded lifetime (15 seconds by default; at most 30 seconds), two-MiB response bodies and the existing QuickJS script/memory/stack/deadline limits. No extraction state or signed-media cache is retained after the worker terminates. The bundle carries QuickJS dependency licenses.

Verification: `npm test` in `extraction/` exercises all eleven hosts with fixtures through the bare core and the Node worker. Live-host availability is separate from deterministic parser coverage.
