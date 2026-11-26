package com.sokoul.pc

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.player.PlayerStatus
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.data.net.TmdbEpisode
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.net.TmdbVideo
import com.sokoul.pc.data.CatalogApi
import com.sokoul.pc.data.CredentialValidator
import com.sokoul.pc.data.GenreTile
import com.sokoul.pc.data.HeroItem
import com.sokoul.pc.data.L
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.data.PageRow
import com.sokoul.pc.data.RowSource
import com.sokoul.pc.data.MyList
import com.sokoul.pc.data.RowStyle
import com.sokoul.pc.data.TrailerStream
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayFailureException
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.SearchUpdate
import com.sokoul.pc.playback.session.VersionOrigin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.sokoul.pc.playback.session.PlayerEngine
import com.sokoul.pc.playback.session.SourceFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

fun sampleItem(id: Int, kind: String = "tv") = TmdbItem(id, "Title $id", null, 2026, null, null, null, "Overview $id", listOf(1), mediaType = kind)

fun sampleInfo(id: Int, seasons: Int? = 2) = TmdbInfo(
    id, "Title $id", "Overview $id", null, null, null, rating = null, year = 2026, runtimeMin = 42, genres = listOf("Drama"),
    cast = emptyList(), director = "Someone", trailerYoutubeKey = "abcdefgh123", seasons = seasons,
    videos = listOf(TmdbVideo("abcdefgh123", "Trailer", "Trailer")), releaseDate = "2026-09-23",
)

class FakeCatalog(
    private val on: Boolean = true,
    private val rows: List<PageRow> = listOf(
        // With artwork: the title page shows its neighbours' pictures beside it only when they have one.
        PageRow.Titles("top-series", L("Top", "Top"), RowStyle.Top10, RowSource.Fixed((1..6).map { sampleItem(it).copy(backdrop = "/backdrop-$it.jpg") })),
    ),
    private val hero: List<HeroItem> = emptyList(),
    private val resumes: List<Resume> = emptyList(),
    private val finished: Set<Int> = emptySet(),
) : CatalogApi {
    var searched = mutableListOf<String>()
    override fun enabled() = on
    override suspend fun hero(kind: PageKind) = hero
    override fun plan(kind: PageKind) = rows
    override suspend fun rowItems(source: RowSource, page: Int) = if (page == 1) (source as? RowSource.Fixed)?.items.orEmpty() else emptyList()
    override suspend fun originals(platform: com.sokoul.pc.data.Platform, kind: String?, page: Int) = emptyList<TmdbItem>()
    override fun allSagas() = emptyList<com.sokoul.pc.data.SagaRef>()
    override suspend fun sagaTile(ref: com.sokoul.pc.data.SagaRef) = null
    override suspend fun person(id: Int) = null
    override suspend fun platformLogos(kind: String?) = emptyMap<Int, String>()
    override suspend fun collections() = com.sokoul.pc.data.CollectionRules.page(
        listOf(com.sokoul.pc.data.SagaTile("saga-a", "Saga A", (1..5).map { sampleItem(it, "movie") }, null, null, null, chronological = listOf(3, 1, 2))),
        emptyList(),
    )
    override suspend fun sagaDetail(tile: com.sokoul.pc.data.SagaTile) = com.sokoul.pc.data.SagaDetail(tile.films, emptyList())
    override suspend fun sagaProfile(tile: com.sokoul.pc.data.SagaTile) = com.sokoul.pc.data.SagaFacts.of(null, emptyList())
    override suspend fun sagaOfTitle(kind: String, id: Int, info: com.sokoul.iptv.data.net.TmdbInfo?) = null
    override fun finishedMovies() = finished
    override suspend fun details(kind: String, id: Int) = sampleInfo(id, if (kind == "tv") 2 else null)
    override suspend fun artwork(kind: String, id: Int) = sampleInfo(id, if (kind == "tv") 2 else null)
    override suspend fun season(tvId: Int, season: Int) = listOf(TmdbEpisode(season, 1, "Pilot", "Plot", null, 40, "2026-09-23"))
    override suspend fun similar(kind: String, id: Int) = listOf(sampleItem(id + 1000, kind))
    override suspend fun search(query: String): List<TmdbItem> {
        searched += query
        return listOf(sampleItem(1, "movie"), sampleItem(2, "tv"))
    }
    override suspend fun genreName(kind: String, id: Int) = "Drama"
    override suspend fun genreTiles(kind: String) = listOf(GenreTile(1, "movie", "Action", null))
    override suspend fun byGenre(kind: String, id: Int, page: Int) = if (page == 1) listOf(sampleItem(1, kind)) else emptyList()
    override fun continueWatching() = resumes
    override fun resumeOf(kind: String, id: Int) = null
}

/** Records what the player was asked to do; each URL can be made to fail. */
class FakeEngine(private val failing: Set<String> = emptySet(), private val tracks: Map<String, List<Pair<Int, String>>> = emptyMap()) : PlayerEngine {
    private val state = MutableStateFlow(PlayerStatus())
    val played = mutableListOf<StreamRequest>()
    val calls = mutableListOf<String>()
    override val status: StateFlow<PlayerStatus> = state

    override fun play(request: StreamRequest) {
        calls += "stop-previous"
        calls += "play:${request.url}"
        played += request
        state.value = if (request.url in failing) PlayerStatus(error = "failed", session = request.session) else PlayerStatus(playing = true, lengthMs = 10_000_000, timeMs = 1_000, session = request.session, audio = tracks[request.url].orEmpty())
    }

    override fun endPreview() { calls += "endPreview" }

    /** Lets a test say what the player does next. */
    fun report(status: PlayerStatus) {
        state.value = status
    }

    val currentSession get() = played.lastOrNull()?.session ?: 0

    override fun stop() {
        calls += "stop"
        state.value = PlayerStatus()
    }
}

/**
 * A slow player, as libVLC is: a command takes effect a moment later, and until then the status still describes the
 * previous stream, its error included.
 */
class SlowEngine(private val failing: Set<String> = emptySet(), private val lagMs: Long = 150, private val scope: CoroutineScope) : PlayerEngine {
    private val state = MutableStateFlow(PlayerStatus())
    val played = mutableListOf<String>()
    override val status: StateFlow<PlayerStatus> = state

    override fun play(request: StreamRequest) {
        scope.launch {
            kotlinx.coroutines.delay(lagMs)
            played += request.url
            state.value = if (request.url in failing) PlayerStatus(error = "failed", session = request.session) else PlayerStatus(playing = true, lengthMs = 10_000_000, timeMs = 1_000, session = request.session)
        }
    }

    override fun stop() {
        // On purpose nothing happens at once: the old error stays visible.
    }
}

fun testVersion(key: String, url: String = key, readiness: Readiness = Readiness.Ready, origin: VersionOrigin = VersionOrigin.Debrid, quality: com.sokoul.iptv.domain.VideoQuality? = com.sokoul.iptv.domain.VideoQuality.FHD_1080, language: com.sokoul.iptv.domain.AudioLanguage? = com.sokoul.iptv.domain.AudioLanguage.VF, failure: PlayFailure? = null) =
    PlayVersion(key, origin, key, language, quality, readiness = readiness) { _, _ ->
        if (failure != null) throw PlayFailureException(failure)
        StreamRequest(url, null, null, "t", live = false)
    }

fun finder(vararg versions: PlayVersion) = SourceFinder { _, _ -> flowOf(SearchUpdate(versions.toList(), done = true)) }

/** Keeps the order given: the ranking has its own tests. */
fun fixedSources(vararg urls: String) = finder(*urls.mapIndexed { i, url -> testVersion("v${i.toString().padStart(2, '0')}", url) }.toTypedArray())

fun testDeps(
    db: DesktopDb,
    validator: CredentialValidator,
    catalog: CatalogApi = FakeCatalog(on = false),
    engine: PlayerEngine? = null,
    sources: SourceFinder = finder(),
    resolveTrailer: suspend (List<String>) -> TrailerStream? = { null },
): AppDeps {
    // Tests stay local: no channel logo is ever looked up in iptv-org's online base.
    com.sokoul.pc.live.ChannelLogos.index = { null }
    return testAppDeps(db, validator, catalog, engine, sources, resolveTrailer)
}

private fun testAppDeps(
    db: DesktopDb,
    validator: CredentialValidator,
    catalog: CatalogApi,
    engine: PlayerEngine?,
    sources: SourceFinder,
    resolveTrailer: suspend (List<String>) -> TrailerStream?,
) = AppDeps(
    db = db,
    validator = validator,
    catalog = catalog,
    myList = MyList(db),
    engine = engine,
    sources = sources,
    resolveTrailer = resolveTrailer,
    sourceLabels = { listOf("IPTV") },
    scope = CoroutineScope(Dispatchers.Unconfined),
)
