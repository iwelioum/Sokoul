# Native extraction runtime

This shared Kotlin JVM module runs the extraction runtime on QuickJS. Its QuickJS bridge is derived third-party code (CC BY-NC 4.0): see [docs/third-party/README.md](../docs/third-party/README.md). QuickJS Kotlin binding sources and native JNI binaries are pinned to `io.github.dokar3:quickjs-kt:1.0.15`, under the upstream Apache 2.0 license, copied to `LICENSE-QUICKJS-KT`: https://github.com/dokar3/quickjs-kt.

The published Kotlin library requires Kotlin 2.4 metadata while Sokoul uses Kotlin 2.0.21. To retain the existing app toolchain and runtime interruption API, `src/quickjs/kotlin` rebuilds the upstream Maven 1.0.15 source JAR with Sokoul's compiler. The port flattens common/JNI expect/actual declarations into JVM declarations, copies inherited default arguments to the JNI implementation, and replaces Kotlin's newer AtomicBoolean with Java's equivalent. Native method names and JNI contracts remain unchanged. Only `jni/**` resources are extracted from the published JVM JAR; no Kotlin 2.4 classes or transitive standard library enter the app. Android builds separately extract ABI libraries from the same-version AAR and exclude desktop resources from the APK. The Android loader first calls `System.loadLibrary`, which uses the app's packaged JNI libraries.

Source artifacts inspected on 2026-11-15, under `https://repo.maven.apache.org/maven2/io/github/dokar3/`:

| Artifact, version 1.0.15 | SHA-256 |
| --- | --- |
| `quickjs-kt-jvm-1.0.15-sources.jar` | `df8f743f2409d195b64bdf0fa169476ed399a2832663dcbdd8ffd59b3c774763` |
| `quickjs-kt-jvm-1.0.15.jar` | `c8a8d0b47e2c3cd6fb21ea618204ba0a04027e09e41dc036c0f60518823f3d6d` |
| `quickjs-kt-android-1.0.15.aar` | `406c3efb80d5fa3495df4a7f2663be8cc30c974fe5c2368effa929de77cf0ed8` |

The AAR declares Android API 23 minimum and contains arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries. Sokoul TV's existing API 26 minimum and ARM ABI filters apply.

The build reads `extraction/runtime/extractors-core.js` and its dynamic Fsvid/Vidzy helper directly. It generates a private build resource using the local native sandbox instead of WASM. No remote JavaScript download is needed. Canonical algorithms are explicit build inputs.

`LocalExtractor.supports(embedUrl)` reads detector patterns generated from the canonical core without starting QuickJS. The eleven-host parity test reads the same Node fixtures as the server extraction tests; this includes SeekStreaming AES, Veev's LZW payload and Dood's secondary request. Additional native fixtures exercise dynamic Fsvid sandbox evaluation, cancellation cleanup and detector aliases.

`LocalExtractor(OkHttpClient).extract(embedUrl)` returns `Extraction?`, including playback URL, headers, format hint and external subtitles. The caller checks server authorization before extracting an embed. `close()` releases the runtime dispatcher. Each extraction is serialized, limited to 25 seconds, 96 MiB main memory and 2 seconds per JS evaluation. Fsvid/Vidzy child scripts have 1.5 second evaluation limits. Completion or cancellation destroys the JS context and cancels network jobs. Cookies remain in a bounded session jar; only cookies matching the returned media host/path enter playback metadata.

The default transport refuses private DNS destinations, validates every redirect, drops manually supplied credentials across origins, limits page bodies to 4 MiB and closes one-byte media probes without downloading the stream. DNS is checked on the connection used by OkHttp; TLS validation stays enabled.

Verification: `./gradlew.bat :extractor:test :app:compileKotlin`; build TV with `powershell -NoProfile -ExecutionPolicy Bypass -File tools/build-tv.ps1`. Tests exercise the actual native runtime with fixture transports, the generated canonical core, cancellation and network policy. Live host availability requires separate device checks.
