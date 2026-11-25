package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.PlayFailureException
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.SourceNotice
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.pc.data.anime.AnimeCategories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.util.concurrent.TimeUnit

/** Catalogue ranks retain the server order, including specials that do not count toward absolute episodes. */
internal object AnimeSamaParse {
    data class Target(val anime: Int, val season: Int, val episode: Int)
    fun text(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
    fun objects(o: JsonObject, key: String) = (o[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    private fun matches(work: JsonObject, normalized: List<String>): Boolean {
        val aliases = listOfNotNull(text(work, "name")) + (work["alternative_names"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return aliases.any { AnimeCategories.plain(it) in normalized }
    }
    fun pending(root: JsonArray?, names: List<String>): Boolean {
        val normalized = names.map(AnimeCategories::plain)
        return root.orEmpty().filterIsInstance<JsonObject>().any { matches(it, normalized) && (it["cataloguePending"] as? JsonPrimitive)?.contentOrNull == "true" }
    }
    private fun seasonNumber(season: JsonObject): Int? {
        val name = AnimeCategories.plain(text(season, "name").orEmpty())
        if (Regex("""\b(films?|oavs?|ovas?|specials?|speciales?|speciaux)\b""").containsMatchIn(name)) return null
        return (season["number"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            ?: Regex("""^(?:saison|saga)\s+(\d+)\b""").find(name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    }
    /** "Saison 1 Partie 2", "Part 2", "Cour 2": the part of a season split in two; null for a whole season. */
    private fun partNumber(season: JsonObject): Int? =
        Regex("""\b(?:partie|part|cour)\s*(\d+)\b""").find(AnimeCategories.plain(text(season, "name").orEmpty()))?.groupValues?.get(1)?.toIntOrNull()

    fun target(root: JsonArray, names: List<String>, movie: Boolean, season: Int?, episode: Int?, absolute: Int?): Target? {
        val normalized = names.map(AnimeCategories::plain)
        root.forEachIndexed { ai, element ->
            val work = element as? JsonObject ?: return@forEachIndexed
            if (!matches(work, normalized)) return@forEachIndexed
            if ((work["cataloguePending"] as? JsonPrimitive)?.contentOrNull == "true") return@forEachIndexed
            val seasons = objects(work, "seasons")
            if (movie) {
                // A catalogue with several films cannot identify the requested film by its parent title alone.
                seasons.forEachIndexed { si, s ->
                    if (text(s, "name").orEmpty().contains("film", true)) {
                        val episodes = objects(s, "episodes")
                        val exact = episodes.indexOfFirst { text(it, "name")?.let(AnimeCategories::plain) in normalized }
                        if (exact >= 0) return Target(ai + 1, si + 1, exact + 1)
                        if (episodes.size == 1) return Target(ai + 1, si + 1, 1)
                    }
                }
            } else {
                // One card per season and part: "Saison 1 Partie 2" has the number 1 too, and dropping it as a duplicate
                // lost its episodes and shifted every later season of the absolute count. A second card of the same
                // season without a part ("East Blue alternate") is the same episodes again and still goes.
                val numbered = seasons.withIndex().mapNotNull { entry -> seasonNumber(entry.value)?.let { number -> entry to number } }
                    .distinctBy { (entry, number) -> number to partNumber(entry.value) }
                    .sortedWith(compareBy({ it.second }, { partNumber(it.first.value) ?: 1 }, { it.first.index })).map { it.first }
                var left = absolute
                if (left != null) for ((si, s) in numbered) {
                    val count = objects(s, "episodes").size
                    if (left in 1..count) return Target(ai + 1, si + 1, left)
                    left -= count
                }
                // The season's own numbering, across its parts: episode 14 of a 12 + 12 season is the second part's 2nd.
                // Only without an absolute number: one that runs past the catalogue is a wrong mapping, and its season and
                // episode would then play another episode.
                if (absolute == null && season != null && episode != null) {
                    var inSeason = episode
                    for ((si, s) in numbered.filter { seasonNumber(it.value) == season }) {
                        val count = objects(s, "episodes").size
                        if (inSeason in 1..count) return Target(ai + 1, si + 1, inSeason)
                        inSeason -= count
                    }
                }
            }
        }
        return null
    }

    fun links(root: JsonArray, target: Target, serverBase: String? = null, allowLocal: Boolean = false): List<WebLink> {
        val work = root.getOrNull(target.anime - 1) as? JsonObject ?: return emptyList()
        val s = objects(work, "seasons").getOrNull(target.season - 1) ?: return emptyList()
        val e = objects(s, "episodes").getOrNull(target.episode - 1) ?: return emptyList()
        val resolved = (e["m3u8ByPlayer"] as? JsonObject).orEmpty()
        val metadata = e["mediaByPlayer"] as? JsonObject
        return objects(e, "streaming_links").flatMap { link ->
            val language = text(link, "language")?.let(WebParse::languageOf)
            (link["players"] as? JsonArray).orEmpty().mapNotNull { player ->
                val embed = (player as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val url = (resolved[embed] as? JsonPrimitive)?.contentOrNull
                if (url == null) {
                    if (!allowLocal || text(work, "extractionStatus") !in setOf("resolved", "unavailable") || !NativeEmbedResolver.supported(embed)) return@mapNotNull null
                    val host = runCatching { URI(embed).host }.getOrNull() ?: return@mapNotNull null
                    return@mapNotNull WebLink("Anime-Sama", "${language ?: "?"} · $host", embed, language, false, localExtraction = true)
                }
                val media = (metadata?.get(embed) as? JsonObject)?.takeIf { text(it, "url") == null || text(it, "url") == url }
                val format = media?.let { text(it, "format") }
                if (!SourceMedia.playable(url, format, serverBase)) return@mapNotNull null
                val host = runCatching { URI(embed).host }.getOrNull() ?: return@mapNotNull null
                WebLink("Anime-Sama", "${language ?: "?"} \u00b7 $host", url, language, SourceMedia.hls(url, format),
                    media?.let(SourceMedia::headers).orEmpty(), media?.let { SourceMedia.subtitles(it, serverBase) }.orEmpty(), SourceMedia.type(url, format))
            }
        }.distinctBy { it.url to it.language }
    }
}

/**
 * Uses Mainapi for catalogue lookup and host extraction; never opens embed HTML in the player. When Mainapi cannot be
 * reached, [direct] reads the site from the app and its players go to the local extractor.
 */
class AnimeSamaSource(http: OkHttpClient, private val baseUrl: () -> String?, private val accessKey: () -> String?, private val probe: StreamProbe, private val localResolver: NativeEmbedResolver? = null,
                      private val direct: AnimeSamaDirect? = null) {
    data class Result(val versions: List<PlayVersion> = emptyList(), val notices: List<SourceNotice> = emptyList())
    // A server that is off on the network may drop the connection instead of refusing it: 3 s to connect, so the
    // TV turns to the site without waiting the whole call time (29 s seen on the Formuler with Docker off).
    private val client = http.newBuilder().callTimeout(25, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun find(request: PlayRequest, names: List<String>, absolute: Int?): Result = withTimeoutOrNull(60_000L) {
        var unavailable = false
        val note = com.sokoul.pc.playback.session.StartClock::note
        val distinct = names.distinct()
        note("anime-sama names ${distinct.size}")
        fun mapped(payload: JsonArray?) = payload?.let { AnimeSamaParse.target(it, names, request.kind == "movie", request.season, request.episode, absolute) }
        // Every name is looked up at once, a few at a time; the first name in order that finds the episode wins, as when
        // they were tried one after the other (each one waited for the one before, up to the server's 8 s; 2026-11-24).
        val gate = Semaphore(NAMES_AT_ONCE)
        val found = coroutineScope {
            val lookups = distinct.map { name -> async { gate.withPermit { get(name) } } }
            var winner: Pair<String, Pair<Lookup, AnimeSamaParse.Target>>? = null
            for ((i, name) in distinct.withIndex()) {
                var lookup = lookups[i].await()
                if (lookup.unavailable) unavailable = true
                var catalogue = lookup.payload
                var target = mapped(catalogue)
                var retries = 0
                while (target == null && AnimeSamaParse.pending(catalogue, names) && retries < 8) {
                    delay(1_000L)
                    retries++
                    lookup = get(name)
                    if (lookup.unavailable) { unavailable = true; break }
                    catalogue = lookup.payload
                    target = mapped(catalogue)
                }
                if (target == null && AnimeSamaParse.pending(catalogue, names)) unavailable = true
                // The site read by the app counts each season cheaply first: an episode past that count gets the full count.
                if (target == null && lookup.direct && catalogue != null) {
                    val thorough = try { direct?.search(name, thorough = true) } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
                    target = mapped(thorough)
                    if (thorough != null) lookup = Lookup(thorough, direct = true)
                }
                note("anime-sama lookup ${if (lookup.unavailable) "unavailable" else "ok"}${if (lookup.direct) " (site)" else ""}, ${if (target == null) "no match" else "matched"}")
                if (target != null) { winner = name to (lookup to target); break }
            }
            lookups.forEach { it.cancel() }
            winner
        }
        if (found == null) return@withTimeoutOrNull Result(notices = if (unavailable) listOf(SourceNotice.AnimeSamaUnavailable) else emptyList())
        val (name, chosen) = found
        val (lookup, selected) = chosen
        // The episode is resolved where it was found: the server's and the site's indexes may differ.
        val resolved = get(name, selected, lookup.direct).payload ?: run {
            note("anime-sama resolve unavailable")
            return@withTimeoutOrNull Result(notices = listOf(SourceNotice.AnimeSamaUnavailable))
        }
        val work = resolved.getOrNull(selected.anime - 1) as? JsonObject
        val status = work?.let { AnimeSamaParse.text(it, "extractionStatus") }
        note("anime-sama resolve $status")
        if (status == "vip_required") return@withTimeoutOrNull Result(notices = listOf(SourceNotice.AnimeSamaVipRequired))
        val versions = AnimeSamaParse.links(resolved, selected, baseUrl(), localResolver != null).filter { !lookup.direct || it.localExtraction }.map { link ->
            PlayVersion(key = "anime-sama-${link.url.hashCode().toUInt()}", origin = VersionOrigin.Web,
                name = "Anime-Sama · ${link.name}", language = link.language, readiness = Readiness.Likely,
                anime = true, announcedAudio = if (link.language == AudioLanguage.VF) listOf("FR") else emptyList(),
                providerHost = URI(link.url).host,
                open = { _, _ ->
                    if (link.localExtraction) return@PlayVersion localResolver!!.resolve(link.url, request.title)
                    failureOf(probe.check(link.url, link.headers["User-Agent"], link.headers["Referer"]), request.kind == "movie")?.let { throw PlayFailureException(it) }
                    StreamRequest(link.url, link.headers["User-Agent"], link.headers["Referer"], request.title, live = false, mediaType = link.mediaType, externalSubtitles = link.subtitles)
                })
        }
        note("anime-sama links ${versions.size}")
        Result(versions, if (versions.isEmpty()) listOf(SourceNotice.AnimeSamaUnavailable) else emptyList())
    } ?: Result(notices = listOf(SourceNotice.AnimeSamaUnavailable)).also { com.sokoul.pc.playback.session.StartClock.note("anime-sama timed out") }

    private data class Lookup(val payload: JsonArray? = null, val unavailable: Boolean = false, val direct: Boolean = false)

    /**
     * When the server was last found unreachable. Within [SERVER_DOWN_MS] it is not asked again: each name waited the
     * server's 8 s in turn before going to the site (2026-11-24).
     */
    @Volatile private var serverDownAt = Long.MIN_VALUE / 2

    /** The server's answer; when the server cannot be reached and the app can read the site itself, the site's. */
    private suspend fun get(name: String, target: AnimeSamaParse.Target? = null, fromSite: Boolean = false): Lookup {
        val canReadSite = direct != null && localResolver != null
        val serverKnownDown = canReadSite && System.currentTimeMillis() - serverDownAt < SERVER_DOWN_MS
        if (!fromSite && !serverKnownDown) {
            val server = server(name, target)
            if (!server.unavailable || !canReadSite) return server
            serverDownAt = System.currentTimeMillis()
            com.sokoul.pc.playback.session.StartClock.note("anime-sama server unavailable, reading the site")
        }
        val site = direct ?: return Lookup(unavailable = true)
        val payload = try {
            if (target == null) site.search(name) else site.resolve(name, target)
        } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
        return Lookup(payload, unavailable = payload == null, direct = true)
    }

    // The search answers at once even for a new work ("pending" while the server builds it): past 8 s the server is
    // taken as unreachable (seen on the TV: 22 s before an off server failed). Resolving an episode keeps the call time.
    private suspend fun server(name: String, target: AnimeSamaParse.Target?): Lookup =
        withTimeoutOrNull(if (target == null) SEARCH_MS else Long.MAX_VALUE) { serverCall(name, target) } ?: Lookup(unavailable = true)

    private companion object {
        const val SEARCH_MS = 8_000L
        const val SERVER_DOWN_MS = 60_000L
        const val NAMES_AT_ONCE = 3
    }

    private suspend fun serverCall(name: String, target: AnimeSamaParse.Target?): Lookup = withContext(Dispatchers.IO) {
        val base = baseUrl()?.trim()?.trimEnd('/')?.toHttpUrlOrNull() ?: return@withContext Lookup(unavailable = true)
        val url = base.newBuilder().addPathSegments("anime/search").addPathSegment(name).apply {
            target?.let { addQueryParameter("resolve", "1"); addQueryParameter("anime", "${it.anime}"); addQueryParameter("season", "${it.season}"); addQueryParameter("episode", "${it.episode}") }
        }.build()
        try {
            client.newCall(Request.Builder().url(url).apply { accessKey()?.let { header("x-access-key", it) } }.build()).awaitSourceResponse().use { response ->
                if (!response.isSuccessful) Lookup(unavailable = true) else {
                    val payload = response.body?.string()?.let { json.parseToJsonElement(it) as? JsonArray }
                    Lookup(payload, unavailable = payload == null)
                }
            }
        } catch (c: CancellationException) { throw c } catch (_: Exception) { Lookup(unavailable = true) }
    }
}
