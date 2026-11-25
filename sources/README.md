# `:sources` — Sokoul's web sources

The streaming sites Sokoul reads itself, without the old Node server: Coflix, French-Stream, PurStream, VoirDrama and
Anime-Sama. Plain JVM (OkHttp, jsoup, kotlinx.serialization), so the PC app, the Android app (through `:app`'s shared
code) and the future Kotlin server use the same scrapers.

## Model (`com.sokoul.sources`)

```kotlin
data class SourceQuery(val kind: String /* "movie" | "tv" */, val tmdbId: Int, val season: Int? = null, val episode: Int? = null)

enum class LinkFormat { Hls, File, Embed }

data class SourceLink(
    val source: String, val name: String, val url: String, val language: AudioLanguage?,
    val quality: String? = null, val format: LinkFormat, val headers: Map<String, String> = emptyMap(),
)

sealed interface SourceResult {
    data class Found(val links: List<SourceLink>) : SourceResult  // never empty
    data object NotFound : SourceResult                           // the site answered: no such work or episode
    data class Unavailable(val reason: String) : SourceResult     // "blocked", "timeout", "network", "host_changed"
}

interface WebSource {
    val name: String
    suspend fun find(query: SourceQuery): SourceResult
}
```

- `Hls` and `File` links are opened as they are, with `headers` (Referer, User-Agent).
- `Embed` links are a host's player page (Uqload, Voe…): the caller hands them to host extraction (`:extractor`), and
  leaves out those it cannot extract.
- `audioLanguageOf(label)`, `hostOf(url)` and `MediaUrls` (is this address a media file, HLS or MP4?) are the rules the
  apps also apply to the Sokoul server's answers: one implementation.

| Class | Package | What |
| --- | --- | --- |
| `Coflix` | `coflix` | Search by titles, TMDB poster decides; the page's lecteurvideo hosts → `Embed` |
| `FStream` | `fstream` | Search (or the season list keyed by the TMDB id); players by version (VF, VOSTFR…) → `Embed`, or `Hls`/`File` when a player is already a media file |
| `PurStream` | `purstream` | API host from the status page; work recognised by a TMDB image; streams → `Hls`/`File` with the site as Referer |
| `VoirDrama` | `voirdrama` | Series only (VOSTFR): season page by first air date → `Embed` |
| `AnimeSama` | `animesama` | Episode-oriented, not a `WebSource`: `search(name)` then `resolve(name, anime, season, episode)`, answers in the server's `/anime/search` JSON shape that the apps' `AnimeSamaParse` reads |

`CatalogueDns` resolves the catalogue sites (anime-sama, coflix, french-stream) over DNS-over-HTTPS, because some
providers answer a blocking address for them.

## Adding a source

1. Create `com.sokoul.sources.<site>.<Site>` implementing `WebSource`, with an `OkHttpClient` (and a TMDB lookup
   `suspend (kind, id, language) -> TmdbClient.NamesAndImages?` when the site is searched by title). Keep the page
   parsing in an `internal object <Site>Site` of pure functions, tested on saved HTML.
2. Write `find` as `override suspend fun find(query) = traced { links(query) }`, where `links` returns the links, or
   null/empty when there are none. In every page reader, take `val trace = siteTrace()` before switching to
   `Dispatchers.IO`, then call `trace?.answer(url, response)` on each response and `trace?.error(e)` on each exception.
   `traced` turns that into `Found`, `Unavailable(reason)` (a page could not be read) or `NotFound` (the site answered).
   A request with a fallback (PurStream's status page) is not traced.
3. Give the source the name of its Sokoul server route (`WebRoutes` in `app/.../playback/sources/WebSources.kt`) and add
   it to the `direct` sources in `app/src/main/kotlin/com/sokoul/pc/AppSetup.kt`: `WebSources` turns its links into
   versions exactly as it does the server's.
4. Test it in `sources/src/test` with `MockWebServer`: links found, `NotFound`, and `Unavailable`.

## Tests

```powershell
.\gradlew.bat :sources:test
```
