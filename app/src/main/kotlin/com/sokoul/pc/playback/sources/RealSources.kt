package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.ConnectionGate
import com.sokoul.pc.playback.session.Doubt
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayFailureException
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.SearchUpdate
import com.sokoul.pc.playback.session.SourceFinder
import com.sokoul.pc.playback.session.SourceNotice
import com.sokoul.pc.playback.session.StartClock
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.Platform
import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopDebrid
import com.sokoul.desktop.data.Movie
import com.sokoul.desktop.data.Playlist
import com.sokoul.desktop.data.PlaylistType
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.debrid.RdTorrent
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.net.XtreamAuth
import com.sokoul.iptv.data.net.XtreamClient
import com.sokoul.iptv.parser.XtreamUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** Which entries of an IPTV catalog are a given title. Pure and tested. */
object IptvMatch {
    private val tag = Regex("""\[[^\]]*\]|\{[^}]*\}|\|[^|]*\|""")
    private val languagePrefix = Regex("""(?i)^\s*(?:(?:4k|uhd)(?:[-_ ][a-z0-9]{2,5})?|[a-z]{2,3})\s*[-:|]\s*""")
    private val year = Regex("""\((19|20)\d{2}\)|\b(19|20)\d{2}\b""")
    private val quality = Regex("""(?i)\b(4k|uhd|fhd|hd|sd|1080[pi]?|720p?|2160[pi]?|(?:3840|4096|1920|1280)\s*[x×]\s*\d{3,4}|multi|vf|vff|vfq|vostfr|vo|truefrench|french|hevc|x265|h265)\b""")
    private val numberedSequel = Regex("""^(.*\D)\s+(\d+)$""")

    /** The title alone: "FR - Dune (2021) 4K MULTI" gives "dune". */
    fun clean(name: String): String {
        val bare = quality.replace(year.replace(languagePrefix.replace(tag.replace(name, " "), ""), " "), " ")
        return Normalizer.normalize(bare, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase()
            .replace('&', ' ').replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    /** The TMDB id decides when the catalog has one. Otherwise the same title, and the same year when both are known. */
    fun matches(entryName: String, entryTmdbId: String?, entryYear: Int?, tmdbId: Int, titles: List<String>, year: Int?): Boolean {
        val cleanName = clean(entryName)
        if (cleanName.isEmpty()) return false
        val exactTitle = titles.any { clean(it) == cleanName }
        val hasMatchingId = !entryTmdbId.isNullOrBlank() && entryTmdbId != "0" && entryTmdbId == tmdbId.toString()
        if (!exactTitle && (!hasMatchingId || sequelConflict(cleanName, titles))) return false
        if (!entryTmdbId.isNullOrBlank() && entryTmdbId != "0" && !hasMatchingId) return false
        return entryYear == null || year == null || kotlin.math.abs(entryYear - year) <= 1
    }

    private fun sequelConflict(entry: String, titles: List<String>): Boolean {
        val candidate = numberedSequel.matchEntire(entry) ?: return false
        val base = candidate.groupValues[1].trim()
        val number = candidate.groupValues[2].toIntOrNull() ?: return false
        return titles.map(::clean).any { title ->
            val target = numberedSequel.matchEntire(title) ?: return@any false
            target.groupValues[1].trim() == base && target.groupValues[2].toIntOrNull() != number
        }
    }
}

/** The audio language an IPTV provider's country prefix stands for ("FR|", "GE -", "4K-EN|"). */
object IptvLanguage {
    private val prefix = Regex("""(?i)^\s*(?:(?:4k|uhd|fhd|hd)[-_ ])?([a-z]{2,3})\s*[|:\-]""")
    private val codes = mapOf(
        "FR" to "FR", "VF" to "FR", "BE" to "FR", "CA" to "FR", "CH" to "FR", "EN" to "EN", "UK" to "EN", "US" to "EN",
        "DE" to "DE", "GE" to "DE", "GER" to "DE", "IT" to "IT", "ITA" to "IT", "ES" to "ES", "SP" to "ES", "LAT" to "ES",
        "PT" to "PT", "BR" to "PT", "NL" to "NL", "TR" to "TR", "AR" to "AR", "JP" to "JA", "JA" to "JA", "KO" to "KO",
        "KR" to "KO", "PL" to "PL", "RU" to "RU", "PH" to "TL", "IN" to "HI", "HI" to "HI", "ALB" to "SQ", "AL" to "SQ",
        "KU" to "KU", "GR" to "EL", "SE" to "SV", "SW" to "SV", "NO" to "NO", "DK" to "DA", "FI" to "FI", "RO" to "RO",
        "EX" to "SR", "YU" to "SR", "IR" to "FA", "PK" to "UR",
    )

    /** The language code, or null when the name has no country prefix or one not listed. */
    fun fromPrefix(name: String): String? = prefix.find(name)?.groupValues?.get(1)?.uppercase()?.let(codes::get)
}

/** What a server answers about a link, read with a one-byte request. */
data class Probe(val code: Int, val totalBytes: Long?)

/** Reads the first byte of a link, to tell a dead link or a refusal apart. Null when the server does not answer at all. */
class StreamProbe(http: OkHttpClient) {
    private val client = http.newBuilder().callTimeout(PROBE_SECONDS, TimeUnit.SECONDS).build()

    suspend fun check(url: String, userAgent: String?, referrer: String? = null): Probe? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("Range", "bytes=0-0").apply {
                if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent)
                if (!referrer.isNullOrBlank()) header("Referer", referrer)
            }.build()
            client.newCall(request).execute().use { r ->
                val total = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: r.header("Content-Length")?.toLongOrNull()
                // An HLS playlist is a few kilobytes whatever the film's length: its size says nothing (PurStream's
                // master.m3u8 was taken for a fake file).
                val playlist = r.header("Content-Type").orEmpty().contains("mpegurl", ignoreCase = true) ||
                    url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
                Probe(r.code, total?.takeIf { r.code == 206 && !playlist })
            }
        }.getOrNull()
    }

    private companion object {
        const val PROBE_SECONDS = 8L
    }
}

/** The cause a probe reveals, or null when the link looks fine. */
fun failureOf(probe: Probe?, film: Boolean): PlayFailure? {
    val p = probe ?: return null
    return when {
        p.code == 404 || p.code == 410 -> PlayFailure.DeadLink
        p.code == 429 || p.code == 509 -> PlayFailure.DebridLimit
        p.code in 400..499 -> PlayFailure.Refused(p.code)
        p.code in 500..599 -> PlayFailure.Network
        film && p.totalBytes != null && p.totalBytes < MIN_FILM_BYTES -> PlayFailure.FakeFile
        else -> null
    }
}

private const val MIN_FILM_BYTES = 50L * 1024 * 1024

/** The versions of a title: Real-Debrid's, as each search service answers, and every matching entry of the IPTV catalog. */
class RealSources(
    private val db: DesktopDb,
    private val debrid: DesktopDebrid,
    private val catalog: Catalog,
    private val transfers: DebridTransfers,
    private val gate: ConnectionGate,
    private val probe: StreamProbe,
    /** Links anime to their ids; null leaves every title on the plain IMDb search. */
    private val anime: com.sokoul.pc.data.anime.AnimeIdentityResolver? = null,
    /** The links the Sokoul server finds on streaming sites; null leaves only Real-Debrid and IPTV. */
    private val web: WebSources? = null,
    /** Optional Aniwatch and Consumet sources from the separately configured AniProject backend. */
    private val animeWeb: AniProjectSources? = null,
    private val animeSama: AnimeSamaSource? = null,
    private val kidsProfile: () -> Boolean = { false },
) : SourceFinder {
    init {
        anime?.trace = { debrid.note("identity: $it") }
    }

    private val xtream = XtreamClient(catalog.http, Json { ignoreUnknownKeys = true; isLenient = true })

    /** The provider's own short guide of a channel. */
    suspend fun shortGuide(c: com.sokoul.desktop.data.Channel): List<com.sokoul.desktop.data.Program> {
        val p = withContext(Dispatchers.IO) { playlist() } ?: run {
            Platform.guideDiagnostic("short guide channel=${c.id} skipped=no_active_playlist")
            return emptyList()
        }
        if (p.type != PlaylistType.XTREAM) {
            Platform.guideDiagnostic("short guide channel=${c.id} skipped=playlist_type_${p.type}")
            return emptyList()
        }
        val id = c.id.toLongOrNull() ?: run {
            Platform.guideDiagnostic("short guide channel=${c.id} skipped=non_numeric_stream_id")
            return emptyList()
        }
        return try {
            val guideOffset = com.sokoul.desktop.data.ProviderCatalog.guideOffsetMinutes(db, p.id) * 60_000L
            xtream.shortEpg(XtreamAuth(p.url.trimEnd('/'), p.username.orEmpty(), p.password.orEmpty(), p.userAgent), id)
                .map { com.sokoul.desktop.data.Program(c.epgKey ?: c.nameKey, it.start + guideOffset, it.stop + guideOffset, it.title, it.description) }
                .sortedBy { it.start }
                .also { Platform.guideDiagnostic("provider short guide channel=${c.id} programmes=${it.size}") }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Platform.guideDiagnostic("provider short guide channel=${c.id} request_failed=${error.javaClass.simpleName}")
            throw error
        }
    }

    /** How many connections the IPTV account allows and has in use. Null without an Xtream account or an answer. */
    suspend fun connectionUse(): Pair<Int, Int>? {
        val p = withContext(Dispatchers.IO) { playlist() } ?: return null
        if (p.type != PlaylistType.XTREAM) return null
        val a = runCatching { xtream.account(XtreamAuth(p.url.trimEnd('/'), p.username.orEmpty(), p.password.orEmpty(), p.userAgent)) }
            .getOrElse { if (it is CancellationException) throw it else null } ?: return null
        return (a.activeConnections ?: return null) to (a.maxConnections ?: return null)
    }

    /** Waits until the account can take a live stream, or throws why it cannot. */
    suspend fun clearForLive() {
        if (com.sokoul.desktop.data.DesktopRecorder.connectionInUse.get()) throw PlayFailureException(PlayFailure.ConnectionsFull(1, 1))
        val p = withContext(Dispatchers.IO) { playlist() } ?: return
        if (p.type != PlaylistType.XTREAM) return
        // Only the pause after a real refusal and the gap after closing a stream. The account's counter does not decide:
        // measured on a real provider, it said 1/1 for minutes with nothing open, and 0/1 with a channel playing.
        gate.clear { null }
    }

    private fun playlist(): Playlist? {
        val all = db.playlists()
        val active = db.setting(Settings.ACTIVE_PLAYLIST)?.toLongOrNull()
        return all.firstOrNull { it.id == active } ?: all.firstOrNull()
    }

    /** One search per title running, shared by whoever asks for it (the title page's badges, the start of the film). */
    private class Running(val state: kotlinx.coroutines.flow.MutableStateFlow<SearchUpdate?>) {
        @Volatile var job: kotlinx.coroutines.Job? = null
        @Volatile var finishedAt: Long = 0
        var foreground = false
    }
    private val running = HashMap<String, Running>()
    private val sharing = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    /**
     * The title page searched once for its badges, and pressing Play searched a second time from the start: both ran
     * together on the Chromecast and the film started after 24 s (Inception, 2026-11-22). Play now joins the search
     * under way and gets at once what it has found; a search finished less than [REUSE_MS] ago is used as it is.
     * [refresh] (Retry) always searches again.
     */
    override fun find(request: PlayRequest, refresh: Boolean): Flow<SearchUpdate> {
        if (refresh) return search(request, true, withIptv = true)
        val run = shared(request, foreground = true)
        // Until the update that says the search is done, that one included.
        return run.state.filterNotNull().transformWhile { u -> emit(u); !u.done }
    }

    /** The search of this title under way or just finished, started when there is none. */
    private fun shared(request: PlayRequest, foreground: Boolean): Running {
        val key = sharedKey(request)
        return synchronized(running) {
            val now = System.currentTimeMillis()
            running.values.removeAll { it.finishedAt != 0L && now - it.finishedAt > REUSE_MS }
            running[key]?.let {
                it.foreground = it.foreground || foreground
                return@synchronized it
            }
            if (!foreground) running.entries.removeAll { (_, old) ->
                (!old.foreground && old.finishedAt == 0L).also { if (it) old.job?.cancel() }
            }
            Running(kotlinx.coroutines.flow.MutableStateFlow(null)).also { r ->
                r.foreground = foreground
                running[key] = r
                r.job = sharing.launch {
                    try {
                        search(request, false, withIptv = true).collect { r.state.value = it }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        // Whoever waits must not wait for ever: what was found stays, the search ends.
                    } finally {
                        val last = r.state.value
                        if (last?.done != true) r.state.value = last?.copy(done = true) ?: SearchUpdate(emptyList(), true, PlayFailure.Network)
                        r.finishedAt = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    /**
     * [withIptv] false for a search ahead: the IPTV catalog search is the long database query, and run while a page was
     * shown it held the database the page was reading from (a freeze of seconds on the TV). It is quick at the start itself.
     */
    private fun search(request: PlayRequest, refresh: Boolean, withIptv: Boolean): Flow<SearchUpdate> = channelFlow {
        val series = request.kind == "tv"
        val info = runCatching { catalog.details(request.kind, request.tmdbId) }.getOrElse { if (it is CancellationException) throw it else null }
        val titles = listOfNotNull(request.title, info?.originalTitle).distinct()
        val animeCandidate = info == null || 16 in info.genreIds || info.originalLanguage?.take(2) in setOf("ja", "zh", "ko")
        // The Sokoul server's links arrive as each of its sources answers; the others do not wait for them.
        // A search ahead skips them: it would ask the scraping sites for an episode nobody has chosen yet.
        val webSearch = web?.takeIf { withIptv && it.configured() }
        val animeWebSearch = animeWeb?.takeIf { withIptv && series && it.configured() }
        val lock = kotlinx.coroutines.sync.Mutex()
        // The IPTV catalogue is a long database query: it runs beside the other services instead of before them, so
        // Real-Debrid and the streaming sites are asked from the first moment (they waited for it before).
        var fromIptv = emptyList<PlayVersion>()
        var iptvDone = !withIptv
        var fromDebrid = emptyList<PlayVersion>()
        var fromWeb = emptyList<PlayVersion>()
        var fromAnimeWeb = emptyList<PlayVersion>()
        var fromAnimeSama = emptyList<PlayVersion>()
        var animeNotices = emptyList<SourceNotice>()
        var debridNotice: SourceNotice? = null
        // Anime-Sama no longer needs the Sokoul server (the app reads the site itself); only a search ahead skips it.
        var animeSamaDone = animeSama == null || !withIptv
        var debridDone = !debrid.configured()
        // What the Real-Debrid account holds marks the versions ready to play. Its list came after the versions themselves
        // (4.3 s, then 9.2 s, for The Matrix on the Chromecast): the versions are shown at once and marked when it comes;
        // the search is only said done once it is known, so that no choice is made without it.
        var accountKnown = debridDone
        var accountHeld: Map<String, RdTorrent> = emptyMap()
        var lastFound = emptyList<DebridStream>()
        var webDone = webSearch == null
        var animeWebDone = animeWebSearch == null
        suspend fun publish() {
            val all = fromIptv + fromDebrid + fromWeb + fromAnimeWeb + fromAnimeSama
            val done = iptvDone && debridDone && accountKnown && webDone && animeWebDone && animeSamaDone
            send(SearchUpdate(all, done, PlayFailure.NoSource.takeIf { done && all.isEmpty() },
                animeNotices + listOfNotNull(debridNotice), debridPending = !debridDone || !accountKnown))
        }
        if (!iptvDone) launch {
            val found = try {
                // Providers name series in English ("FR| Frieren: Beyond Journey's End") where the app has the French
                // and original titles: the English title and an anime's known names are looked for as well.
                val identity = if (series && animeCandidate) runCatching { anime?.identity(request.kind, request.tmdbId, info) }.getOrNull() else null
                val english = runCatching { catalog.tmdb.namesAndImages(catalog.key(), request.kind, request.tmdbId, "en-US")?.title }.getOrNull()
                val names = (titles + listOfNotNull(english) + identity?.names.orEmpty()).distinct().take(IPTV_NAMES)
                val absolute = identity?.let { runCatching { episodeRef(request, it)?.absolute }.getOrNull() }
                iptv(request, names, info?.year, absolute)
            } catch (c: CancellationException) { throw c } catch (_: Exception) { emptyList() }
            StartClock.note("iptv " + found.size + found.take(3).joinToString("") { " | " + it.name.take(60) + " [" + (it.language?.name ?: "?") + "]" })
            lock.withLock { fromIptv = found; iptvDone = true; publish() }
        }
        if (!animeSamaDone) launch {
            // The identity only adds names: past LOOKUP_MS the site is asked with the titles known (2026-11-24; it waited
            // for every identity service before, while Real-Debrid already had its own limit).
            val identity = try {
                kotlinx.coroutines.withTimeoutOrNull(LOOKUP_MS) { anime?.identity(request.kind, request.tmdbId, info) }
            } catch (c: CancellationException) { throw c } catch (_: Exception) { null }
            val asked = identity != null || info?.originalLanguage?.take(2) in setOf("ja", "zh", "ko")
            com.sokoul.pc.playback.session.StartClock.note("anime-sama ${if (asked) "asked" else "skipped: not an anime"}")
            val result = try {
                if (asked) animeSama!!.find(request, (listOf(request.title) + identity?.names.orEmpty() + listOfNotNull(info?.originalTitle)).distinct(), episodeRef(request, identity)?.absolute)
                else AnimeSamaSource.Result()
            } catch (c: CancellationException) { throw c } catch (_: Exception) { AnimeSamaSource.Result() }
            lock.withLock { fromAnimeSama = result.versions; animeNotices = result.notices; animeSamaDone = true; publish() }
        }
        if (webSearch != null) {
            launch {
                try {
                    webSearch.find(request) { versions -> lock.withLock { fromWeb = fromWeb + versions; publish() } }
                } catch (c: CancellationException) { throw c } catch (_: Exception) { StartClock.note("web search failed") }
                lock.withLock { webDone = true; publish() }
            }
        }
        if (animeWebSearch != null) {
            launch {
                val versions = try {
                    animeWebSearch.find(request, info)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    emptyList()
                }
                lock.withLock { fromAnimeWeb = versions; animeWebDone = true; publish() }
            }
        }
        if (debridDone) {
            lock.withLock { publish() }
            return@channelFlow
        }
        lock.withLock { publish() }
        val query = DebridQuery(request.tmdbId, series, request.season, request.episode)
        // The identity is looked up while the services are already asked: it never adds its time to theirs.
        val lookup: (suspend () -> com.sokoul.desktop.data.AnimeLookup?)? = if (series && animeCandidate) {
            { kotlinx.coroutines.withTimeoutOrNull(LOOKUP_MS) { animeLookup(request, info, titles, query) } }
        } else null
        var lastQuery = query
        launch {
            val held = transfers.account()
            StartClock.note("real-debrid account snapshot: ${held.size} active entries")
            lock.withLock {
                accountHeld = held
                accountKnown = true
                if (lastFound.isNotEmpty()) fromDebrid = lastFound.map { version(it, lastQuery, held, request) }
                publish()
            }
        }
        try {
            debrid.search(query, info, refresh, lookup).collect { found ->
                val q = found.query ?: query
                lock.withLock {
                    lastFound = found.streams
                    lastQuery = q
                    fromDebrid = found.streams.map { version(it, q, accountHeld, request) }
                    debridDone = found.done
                    if (found.done && found.streams.isEmpty()) debridNotice = if (found.failed) SourceNotice.DebridUnavailable else SourceNotice.DebridNoVersions
                    publish()
                }
            }
        } catch (c: CancellationException) { throw c } catch (_: Exception) {
            lock.withLock { debridDone = true; debridNotice = SourceNotice.DebridUnavailable; publish() }
        }
    }

    /** What the anime identity adds to the search of this episode. Null for anything else than an anime. */
    private suspend fun animeLookup(request: PlayRequest, info: com.sokoul.iptv.data.net.TmdbInfo?, titles: List<String>, query: DebridQuery): com.sokoul.desktop.data.AnimeLookup? {
        if (request.kind != "tv") return null
        // "off" leaves every title on the plain IMDb search, as before the anime identity.
        val identity = anime?.takeIf { db.setting(ANIME_IDENTITY) != "off" }?.let { a -> runCatching { a.identity(request.kind, request.tmdbId, info) }.getOrElse { if (it is CancellationException) throw it else null } }
        debrid.note("identity resolved")
        val ref = episodeRef(request, identity)
        // The number since the first episode is how anime files are named; in another series, "S02E13" would match it.
        val absolute = ref?.absolute?.takeIf { identity != null || info?.originalLanguage?.take(2) in setOf("ja", "zh", "ko") }
        val q = query.copy(absolute = absolute, kitsu = ref?.kitsu)
        val search = identity?.let { id ->
            val seaDex = id.main.anilist?.let { al -> runCatching { anime?.seaDex(al) }.getOrElse { if (it is CancellationException) throw it else null } }.orEmpty()
            debrid.note("seadex " + seaDex.size)
            com.sokoul.desktop.data.AnimeSearch(
                com.sokoul.iptv.data.debrid.AnimeCheck((titles + id.names).distinct(), info?.year, request.season, request.episode, ref?.absolute, id.homonyms),
                seaDex,
            )
        }
        return if (search == null && absolute == null) null else com.sokoul.desktop.data.AnimeLookup(q, search)
    }

    /**
     * The episode's number from the start of the series, what anime files carry ("One Piece - 062" is S02E01): the
     * episodes of the seasons before, counted from the catalog (cached). Null when unknown, and then only "SxxEyy" names
     * are judged past season 1.
     */
    private suspend fun episodeRef(request: PlayRequest, identity: com.sokoul.iptv.data.anime.AnimeIdentity?): com.sokoul.iptv.data.anime.EpisodeRef? {
        val season = request.season ?: return null
        val episode = request.episode ?: return null
        if (request.kind != "tv") return null
        val resolver = anime
        if (resolver != null) return runCatching { resolver.episode(identity, request.tmdbId, season, episode) }.getOrElse { if (it is CancellationException) throw it else null }
        val sizes = (1..season).map { s -> runCatching { catalog.season(request.tmdbId, s).size }.getOrElse { if (it is CancellationException) throw it else 0 } }
        return com.sokoul.iptv.data.anime.EpisodeMapper.map(season, episode, sizes, emptyList())
    }

    private fun version(s: DebridStream, query: DebridQuery, account: Map<String, RdTorrent>, request: PlayRequest): PlayVersion {
        val held = s.infoHash?.let(account::get)
        val readiness = when {
            held?.ready == true -> Readiness.Ready
            held != null -> Readiness.Preparing
            s.release.cached == true -> Readiness.Likely
            s.release.cached == false -> Readiness.NotCached
            else -> Readiness.Unknown
        }
        return PlayVersion(
            key = s.key, origin = VersionOrigin.Debrid, name = s.title, language = s.release.language, quality = s.release.quality, hdr = s.release.hdr,
            rip = s.release.source?.label, sizeBytes = s.release.sizeBytes, seeders = s.release.seeders, readiness = readiness,
            preparingPercent = held?.takeIf { !it.ready }?.progress, providerHost = com.sokoul.desktop.data.PlayAttempts.host(s),
            ripRank = s.release.source?.ordinal ?: UNKNOWN_RIP_RANK,
            rejected = (s.verdict as? com.sokoul.iptv.data.debrid.Verdict.Rejected)?.let { r ->
                when (r.reason) {
                    com.sokoul.iptv.data.debrid.Verdict.Reason.OtherWork -> Doubt.OtherWork
                    com.sokoul.iptv.data.debrid.Verdict.Reason.OtherEpisode -> Doubt.OtherEpisode
                    com.sokoul.iptv.data.debrid.Verdict.Reason.NotAnEpisode -> Doubt.NotAnEpisode
                }
            },
            confidence = when (val v = s.verdict) {
                null, com.sokoul.iptv.data.debrid.Verdict.Confirmed -> 0
                is com.sokoul.iptv.data.debrid.Verdict.Unknown -> if (v.doubt != null) 2 else 1
                is com.sokoul.iptv.data.debrid.Verdict.Rejected -> 2
            },
            community = s.community,
            anime = s.verdict != null,
            announcedAudio = s.release.audio,
            foreignDub = s.release.dub,
            frenchVariantRank = when (s.release.frenchVariant) { "TRUEFRENCH", "VFF" -> 0; null -> 1; else -> 2 },
            discard = { s.infoHash?.let { transfers.forget(it) } },
            open = { keep, onProgress ->
                s.infoHash?.let { h -> runCatching { db.rdNoteAdded(h) } }
                val url = if (s.infoHash != null) transfers.link(s, query, keep, onProgress) else viaSearchService(s)
                s.infoHash?.let { h -> runCatching { db.rdNotePlayed(h, request.resumeKey) } }
                // Checked before opening: it costs no IPTV connection and catches a dead link or a waiting clip at once.
                StartClock.note("link " + (s.infoHash?.take(6) ?: "url"))
                failureOf(probe.check(url, null), request.kind == "movie")?.let { throw PlayFailureException(it) }
                StartClock.note("probed " + (s.infoHash?.take(6) ?: "url"))
                StreamRequest(url, null, null, request.title, live = false)
            },
        )
    }

    private suspend fun viaSearchService(s: DebridStream): String = debrid.resolve(s).getOrElse { e ->
        throw PlayFailureException(if (e is DesktopDebrid.NotReady) PlayFailure.NotReady(null) else PlayFailure.Network)
    }

    private suspend fun iptv(request: PlayRequest, titles: List<String>, year: Int?, absolute: Int? = null): List<PlayVersion> = withContext(Dispatchers.IO) {
        val p = playlist() ?: return@withContext emptyList()
        val base = p.url.trimEnd('/')
        val user = p.username.orEmpty()
        val pass = p.password.orEmpty()
        val auth = XtreamAuth(base, user, pass, p.userAgent)
        fun allowed(category: String?, kind: String, title: String) =
            canOpenIptvCategory(db, p, category, kind, title, kidsProfile())
        if (request.kind == "movie") {
            titles.flatMap { db.providerMoviesPage(p.id, search = it, limit = SEARCH_LIMIT) }.distinctBy { it.id }
                .filter { IptvMatch.matches(it.name, it.tmdbId, it.year, request.tmdbId, titles, year) }
                .filter { allowed(it.categoryId, "movie", it.name) }
                .mapNotNull { movie -> movieVersion(movie, p, base, user, pass, request) }
        } else {
            if (p.type != PlaylistType.XTREAM) return@withContext emptyList()
            titles.flatMap { db.providerSeriesPage(p.id, search = it, limit = SEARCH_LIMIT) }.distinctBy { it.id }
                .filter { IptvMatch.matches(it.name, it.tmdbId, it.year, request.tmdbId, titles, year) }
                .filter { allowed(it.categoryId, "series", it.name) }
                .mapNotNull { s ->
                    val seriesId = s.id.toLongOrNull() ?: return@mapNotNull null
                    iptvVersion("iptv-s-${s.id}", s.name) {
                        if (!allowed(s.categoryId, "series", s.name)) throw PlayFailureException(PlayFailure.NoSource)
                        gate.clear { null }
                        val episodes = xtream.seriesInfo(auth, seriesId).episodes
                        // An anime is often filed as one long season numbered from the first episode on: the absolute
                        // number then names the episode when the season and episode do not exist there.
                        val found = episodes.firstOrNull { it.season == request.season && it.episodeNum == request.episode }
                            ?: absolute?.takeIf { episodes.map { e -> e.season }.distinct().size == 1 }?.let { n -> episodes.firstOrNull { it.episodeNum == n } }
                            ?: throw PlayFailureException(PlayFailure.NoSource)
                        StreamRequest(XtreamUrls.episode(base, user, pass, found.id, found.extension), p.userAgent, null, request.title, live = false)
                    }
                }
        }
    }

    private fun movieVersion(movie: Movie, p: Playlist, base: String, user: String, pass: String, request: PlayRequest): PlayVersion? {
        val url = movie.streamUrl ?: if (p.type == PlaylistType.XTREAM) {
            XtreamUrls.movie(base, user, pass, movie.id.toLongOrNull() ?: return null, movie.extension)
        } else {
            return null
        }
        // A film shot in a cinema ("[HDCAM]", "TS") is dropped as on Real-Debrid: an IPTV one was played first, in
        // English, for L'Odyssée (2026-11-18).
        if (ReleaseName.parse(movie.name).cam) return null
        return iptvVersion("iptv-m-${movie.id}", movie.name) {
            if (!canOpenIptvCategory(db, p, movie.categoryId, "movie", movie.name, kidsProfile()))
                throw PlayFailureException(PlayFailure.NoSource)
            // Xtream connection counters can lag behind the actual stream, including on VOD.
            gate.clear { null }
            StreamRequest(url, p.userAgent, null, request.title, live = false)
        }
    }

    private fun iptvVersion(key: String, name: String, open: suspend () -> StreamRequest): PlayVersion {
        val release = ReleaseName.parse(name)
        return PlayVersion(
            key = key, origin = VersionOrigin.Iptv, name = name, language = release.language, quality = release.quality, hdr = release.hdr,
            readiness = Readiness.Ready, ripRank = UNKNOWN_RIP_RANK + 1,
            // "TR| Jujutsu Kaisen", "GE| Solo Leveling": the provider's country prefix is the dub's language. Unread, a
            // Turkish dub ranked as a version of unknown language, beside the French ones.
            announcedAudio = release.audio.ifEmpty { IptvLanguage.fromPrefix(name)?.let { listOf(it) }.orEmpty() },
            foreignDub = release.dub,
            open = { _, _ ->
                if (com.sokoul.desktop.data.DesktopRecorder.connectionInUse.get()) throw PlayFailureException(PlayFailure.ConnectionsFull(1, 1))
                open()
            },
        )
    }


    /**
     * The search of what would play next, run ahead: its answers stay in the caches, so that the start lists them at once.
     * One at a time, the newest wins. Nothing is added to the Real-Debrid account and no IPTV connection is opened.
     */
    private fun sharedKey(request: PlayRequest) = listOf(request.kind, request.tmdbId, request.season, request.episode, kidsProfile()).joinToString(":")

    /**
     * The title page, a moment after it is shown: the full search of what Play would start begins, and Play joins it.
     * A reduced search here (no IPTV, no sites) and a full one at Play ran side by side and parsed the same releases
     * twice on the Chromecast's CPU (Avatar, 2026-11-22). The IPTV query runs on the IO threads beside the others.
     */
    override fun prefetch(request: PlayRequest) {
        shared(request, foreground = false)
    }

    /** When each step of the last search ended. */
    fun timeline(): List<String> = StartClock.steps()

    /** After a stream failed, its link tells why. The refusal of an IPTV link makes the gate hold off. */
    suspend fun diagnose(version: PlayVersion?, stream: StreamRequest): PlayFailure? {
        val found = failureOf(probe.check(stream.url, stream.userAgent), film = false)
        if (version == null) return iptvCause(found)
        if (version.origin == VersionOrigin.Iptv && found is PlayFailure.Refused) {
            gate.noteRefusal()
            return iptvCause(found)
        }
        return found
    }

    /** Once a stream fails to open, the connection count can explain a provider refusal. */
    private suspend fun iptvCause(found: PlayFailure?): PlayFailure? {
        if (found != null && found !is PlayFailure.Refused) return found
        val use = connectionUse() ?: return found
        if (use.second <= 0 || use.first < use.second) return found
        if (found is PlayFailure.Refused) gate.noteRefusal()
        return PlayFailure.ConnectionsFull(use.first, use.second)
    }

    private companion object {
        const val SEARCH_LIMIT = 50
        /** A finished search of the same title is used again for this long (the title page, then Play). */
        const val REUSE_MS = 120_000L
        /** The identity's longest wait: past it, the search goes on as for any series. */
        const val LOOKUP_MS = 15_000L
        const val ANIME_IDENTITY = "pc.anime.identity"
        const val UNKNOWN_RIP_RANK = 2
    }
}

/** Recheck at open time: a cached version must not outlive an unlock or a profile switch. */
internal fun canOpenIptvCategory(db: DesktopDb, playlist: Playlist, category: String?, kind: String, title: String, kids: Boolean): Boolean {
    if (kids && com.sokoul.pc.live.TvOrganization.adultName(title)) return false
    if (category.isNullOrBlank()) return !kids
    val providerKind = if (kind == "movie") com.sokoul.desktop.data.ProviderKind.Movies else com.sokoul.desktop.data.ProviderKind.Series
    val name = db.providerCategories(playlist.id, providerKind).firstOrNull { it.id == category }?.title
    if (kids && (name.isNullOrBlank() || name == category)) return false
    return com.sokoul.pc.live.TvOrganization(db, { playlist }, { kids }).canAccessCategory(category, name, kind)
}

/** How many names the IPTV catalogue is searched with: each one is a database query. */
private const val IPTV_NAMES = 6
