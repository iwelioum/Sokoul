package com.sokoul.pc

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.testTag
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.data.CatalogApi
import com.sokoul.pc.data.CredentialValidator
import com.sokoul.pc.data.MyList
import com.sokoul.pc.data.TrailerStream
import com.sokoul.pc.data.trailerKeys
import com.sokoul.pc.home.HomeScreen
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.library.LibraryScreen
import com.sokoul.pc.library.SettingsScreen
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.pc.playback.session.PlaybackSession
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.PlayerEngine
import com.sokoul.pc.playback.ui.PlayerScreen
import com.sokoul.pc.playback.session.SourceFinder
import com.sokoul.pc.playback.session.TrailerController
import com.sokoul.pc.search.SearchScreen
import com.sokoul.pc.shell.NavEntry
import com.sokoul.pc.shell.NavShell
import com.sokoul.pc.shell.SECTION_HOME
import com.sokoul.pc.shell.SECTION_SEARCH
import com.sokoul.pc.shell.SECTION_SETTINGS
import com.sokoul.pc.collections.CollectionDetailScreen
import com.sokoul.pc.collections.CollectionRequest
import com.sokoul.pc.collections.CollectionsScreen
import com.sokoul.iptv.data.net.TmdbPerson
import com.sokoul.pc.collections.PersonScreen
import com.sokoul.pc.collections.SagaScreen
import com.sokoul.pc.collections.SagaShelfScreen
import com.sokoul.pc.data.SagaEntry
import com.sokoul.pc.data.SagaShelf
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.data.L
import com.sokoul.pc.data.GenreTile
import com.sokoul.pc.data.SagaTile
import com.sokoul.pc.ui.NavIcons
import com.sokoul.pc.startup.SetupController
import com.sokoul.pc.startup.SetupScreen
import com.sokoul.pc.startup.SetupState
import com.sokoul.pc.startup.SplashScreen
import com.sokoul.pc.startup.StartupRoute
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.title.TitleScreen
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.FocusSource
import com.sokoul.pc.ui.LocalPinch
import com.sokoul.pc.ui.PinchActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sokoul.pc.ui.focusIfPresent

/** Everything the screens depend on. Tests and renders supply fakes for the network and the player. */
class AppDeps(
    val db: DesktopDb,
    val validator: CredentialValidator,
    val catalog: CatalogApi,
    val myList: MyList,
    private val playerProvider: () -> DesktopPlayer? = { null },
    engine: PlayerEngine?,
    val sources: SourceFinder,
    val resolveTrailer: suspend (List<String>) -> TrailerStream?,
    /** Trailers beyond TMDB's YouTube keys, listed on the title page. Null in tests and renders. */
    val trailerSources: com.sokoul.pc.data.TrailerSources? = null,
    val sourceLabels: () -> List<String>,
    /** When each step of the last search for versions ended, for the tools. */
    val sourceTimeline: () -> List<String> = { emptyList() },
    private val scope: CoroutineScope,
    diagnose: suspend (PlayVersion?, StreamRequest) -> PlayFailure? = { _, _ -> null },
    onStreamClosed: () -> Unit = {},
    clearForLive: suspend () -> Unit = {},
    /** Connections of the IPTV account in use, and allowed. */
    val connectionUse: suspend () -> Pair<Int, Int>? = { null },
    shortGuide: suspend (com.sokoul.desktop.data.Channel) -> List<com.sokoul.desktop.data.Program> = { emptyList() },
    subtitleSource: com.sokoul.pc.playback.sources.SubtitleSource? = null,
    /** Who is watching: resume points, history, list and alerts are theirs. */
    val profiles: com.sokoul.pc.data.Profiles = com.sokoul.pc.data.Profiles(db),
) {
    /** The light HTTP client of features that only fetch small JSON (translation, release dates). */
    val smallHttp: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder().dns(com.sokoul.iptv.data.net.Ipv4FirstDns)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
    }
    val subtitles = subtitleSource?.let { com.sokoul.pc.playback.sources.OnlineSubtitles(it, scope, com.sokoul.pc.data.providers.GoogleTranslator(smallHttp)) }
    /** The engine's state, for what describes the app from outside the screens. */
    val engineStatus: () -> com.sokoul.desktop.player.PlayerStatus? = { engine?.status?.value }
    /** The app as tools and state: used by the live checks, and the way in for a future assistant. */
    val bridge by lazy { com.sokoul.pc.control.AppBridge(this) }
    /** This device's Sokoul account on the private server; `account.session()` is what sync will call with. */
    val account by lazy { com.sokoul.pc.account.SokoulAccount(com.sokoul.pc.account.DbAccountStore(db), smallHttp) }
    /**
     * Profiles, resume points, My List and alerts across the account's devices (Lot 6). Does nothing signed out; only
     * [realDeps] starts it, so tests and renders never reach a server.
     */
    val sync by lazy {
        com.sokoul.pc.sync.SyncEngine(
            db, com.sokoul.pc.sync.HttpSyncTransport(account),
            userId = {
                account.session()?.let { s -> s.user?.id ?: (account.me() as? com.sokoul.pc.account.AccountResult.Ok)?.value?.id }
            },
            onUi = { block -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() } },
            afterRemoteChange = {
                profiles.reload()
                myList.reload()
                alerts.reload()
            },
        )
    }
    /** Pictures of live programmes: the guide's, else TMDB's for an unambiguous title. */
    val programArt by lazy { com.sokoul.pc.live.ProgramArtCache { title -> catalog.search(title) } }

    /** Null until libVLC has loaded. Reading it from a composable recomposes when it arrives. */
    val player: DesktopPlayer? get() = playerProvider()
    val trailer = TrailerController(engine, resolveTrailer, scope).also { c -> c.enabled = { prefs.autoTrailers && playback.clip == null } }
    val pinch = PinchActions()

    /** Fetches a trailer's link ahead of time. Failures are silent: the artwork simply stays. */
    fun warmKeys(keys: List<String>) {
        // On a TV, only for who wants them to start alone: each one costs a request to YouTube.
        if (keys.isNotEmpty() && (!Platform.IS_TV || prefs.autoTrailers)) scope.launch(Dispatchers.IO) { runCatching { resolveTrailer(keys) } }
    }

    /** Called when a card gets focus: its trailer is likely next. */
    fun warmTrailer(item: TmdbItem) {
        scope.launch(Dispatchers.IO) {
            if (Platform.IS_TV && !prefs.autoTrailers) return@launch
            runCatching { warmKeys(catalog.details(item.mediaType ?: "movie", item.id)?.trailerKeys().orEmpty()) }
        }
    }

    val prefs = com.sokoul.desktop.data.DesktopPrefs(db)
    val tv = com.sokoul.pc.live.TvCatalog(db, shortGuide)
    val personalTv by lazy { com.sokoul.pc.live.TvPersonalization(db, tv, { profiles.current.id }, { profiles.current.kids }) }
    val anime get() = catalog.anime
    /** What the reader shows, for the tools. */
    @Volatile var readerReport: com.sokoul.pc.anime.ReaderReport? = null
    /** What the block of a title page found, for the tools. */
    val reminders = com.sokoul.pc.live.Reminders(db)
    /** Followed titles (alerts): told when a film comes out or a new episode airs. */
    val alerts = com.sokoul.pc.data.TitleAlerts(db) { profiles.key(com.sokoul.pc.data.TitleAlerts.SETTING) }

    /** Raised when a setting that changes what is on screen (menu, home rows, accent) was changed. */
    var uiRevision by androidx.compose.runtime.mutableStateOf(0)
        private set

    /** Raised when the language changes: the whole interface is drawn again in the new one. */
    var languageRevision by androidx.compose.runtime.mutableStateOf(0)
        private set

    fun languageChanged() {
        languageRevision++
    }

    fun uiChanged() {
        uiRevision++
    }

    init {
        tv.kidsProfile = { profiles.current.kids }
        com.sokoul.pc.i18n.Strings.applyLanguage(prefs.language)
        com.sokoul.pc.theme.AccentTheme.color = androidx.compose.ui.graphics.Color(prefs.accent.argb)
        myList.key = { profiles.key(MyList.SETTING) }
        myList.reload()
        // Another profile: its own list and alerts, and nothing of the previous one left playing or on offer.
        profiles.onSwitch = {
            tv.organization.lockSession()
            myList.reload()
            alerts.reload()
            playback.close()
        }
    }
    /** A short message over every screen (a refused title, for instance); cleared after a few seconds. */
    var notice by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** The watch party in progress, if any. When the room changes title, the player opens it. */
    val party = com.sokoul.pc.party.WatchParty(smallHttp).also { p ->
        p.onMedia = { m ->
            // Already on that title (the guest joined from its player): nothing to open.
            val now = playback.request
            if (now == null || now.kind != m.kind || now.tmdbId != m.tmdbId || now.season != m.season || now.episode != m.episode) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { bridge.navigation?.play(m.request()) }
            }
        }
    }

    /** The latest episode aired of a series, from its last two seasons (a new season may be announced with nothing aired yet). */
    suspend fun latestAired(tvId: Int): Pair<Int, Int>? {
        val seasons = catalog.details("tv", tvId)?.seasons ?: return null
        val episodes = (maxOf(1, seasons - 1)..seasons).flatMap { catalog.season(tvId, it) }
        return com.sokoul.pc.data.AlertRules.latestAired(episodes, java.time.LocalDate.now())
    }

    /** What already exists of a title when it starts being followed, so only later news is announced. */
    suspend fun alertBaseline(kind: String, id: Int): String? = if (kind == "movie") {
        com.sokoul.pc.data.AlertRules.movie(com.sokoul.pc.data.TitleAlert(kind, id, ""), catalog.details("movie", id)?.releaseDate, java.time.LocalDate.now())?.key
    } else {
        latestAired(id)?.let { (s, e) -> "S${s}E$e" }
    }

    suspend fun alertNews(): List<com.sokoul.pc.data.AlertNews> = alerts.check(
        java.time.LocalDate.now(),
        releaseDate = { a -> catalog.details("movie", a.id)?.releaseDate },
        latestAired = { a -> latestAired(a.id) },
    )
    /** Set by the window: full screen or not. */
    var toggleFullscreen: () -> Unit = {}
    /** Set at startup: forgets the version searches kept in memory, next to the database copy cleared with the cache. */
    var clearSearchCache: () -> Unit = {}
    var forgetSourceFailures: () -> Unit = {}
    /** Set when the language list changes the language: the interface is rebuilt in it and reopens on that list. */
    var languageReturn = false
    /** Set by the PC window: whether it is full screen. Null where there is no window to change (TV, phone). */
    var isFullscreen: (() -> Boolean)? = null
    /** What the anime page shows, for the tools. */
    @Volatile var animeReport: com.sokoul.pc.anime.AnimeReport? = null
    /** What the TV page shows, for the tools. */
    @Volatile var tvReport: com.sokoul.pc.live.TvReport? = null
    /** Intros and credits of series and films that are not anime, answers kept in the database cache. */
    private val introDb = com.sokoul.iptv.data.net.IntroDb(
        okhttp3.OkHttpClient.Builder().dns(com.sokoul.iptv.data.net.Ipv4FirstDns)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS).readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build(),
        object : com.sokoul.iptv.data.net.TmdbCache {
            override suspend fun get(key: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.tmdbGet(key)?.let { com.sokoul.iptv.data.net.TmdbCached(it.first, it.second) }
            }
            override suspend fun put(key: String, json: String, fetchedAt: Long) =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.tmdbPut(key, json, fetchedAt) }
        },
    )
    private val sourceMemory = com.sokoul.pc.playback.sources.SourceMemory(
        { db.setting("pc.source.memory") }, { db.putSetting("pc.source.memory", it) },
    ).also { forgetSourceFailures = it::forgetFailures }
    val playback = PlaybackSession(
        engine, sources, scope,
        sourcePreferences = prefs::sourceOrder,
        rememberSourceHoster = prefs::rememberSourceHoster,
        prefs = prefs::playback,
        // Saved progress reaches the account's other devices a few seconds after the last save.
        saveResume = { r -> db.saveResume(r); sync.noteLocalChange() },
        readResume = db::resume,
        saveDirectResume = { key, position, length ->
            db.putSetting(profiles.key("provider.resume.$key"), "$position\t$length")
        },
        readDirectResume = { key ->
            db.setting(profiles.key("provider.resume.$key"))?.split('\t')?.takeIf { it.size == 2 }
                ?.let { (it[0].toLongOrNull() ?: 0L) to (it[1].toLongOrNull() ?: 0L) }
        },
        diagnose = diagnose,
        onStreamClosed = onStreamClosed,
        clearForLive = clearForLive,
        onStreamResult = { key, height, failed -> tv.noteStream(key, height, failed) },
        resolveLiveScope = if (Platform.IS_TV) ({ group ->
            val state = personalTv.state()
            when (state.zapScope) {
                com.sokoul.pc.live.TvZapScope.All -> tv.toLive(tv.channels(com.sokoul.desktop.data.SpecialGroup.ALL))
                com.sokoul.pc.live.TvZapScope.CurrentGroup -> group
                com.sokoul.pc.live.TvZapScope.Favorites -> tv.toLive(tv.channels(com.sokoul.desktop.data.SpecialGroup.FAVORITES))
                com.sokoul.pc.live.TvZapScope.ActiveCollection -> tv.toLive(state.activeCollectionId?.let(personalTv::channels).orEmpty())
            }
        }) else null,
        previousLive = { current -> if (Platform.IS_TV) tv.channels(com.sokoul.pc.live.TvCatalog.RECENT).firstOrNull { it.id != current }?.let(tv::toLive) else null },
        onLiveStarted = { id -> if (Platform.IS_TV) {
            tv.noteWatched(id)
            tv.channelById(id)?.let { channel ->
                val lastGroup = tv.lastPosition()?.group
                val collection = com.sokoul.pc.live.TvPersonalization.collectionId(lastGroup)
                val inGroup = if (collection != null) personalTv.channels(collection) else tv.channels(lastGroup)
                tv.rememberPosition(if (inGroup.any { it.id == id }) lastGroup else channel.categoryId, id)
            }
        } },
        caps = Platform.deviceCaps(),
        memory = sourceMemory,
        expectedMinutes = { r ->
            if (r.kind == "movie") {
                catalog.details("movie", r.tmdbId)?.runtimeMin
            } else {
                catalog.season(r.tmdbId, r.season ?: 1).firstOrNull { it.episode == r.episode }?.runtimeMin
                    // An anime episode the catalog gives no length: Kitsu knows it.
                    ?: catalog.animeIdentity?.let { a -> a.identity("tv", r.tmdbId)?.let { id -> a.episodeMinutes(a.episode(id, r.tmdbId, r.season ?: 1, r.episode ?: 1)) } }
            }
        },
        originalLanguage = { r -> catalog.details(r.kind, r.tmdbId)?.originalLanguage },
        skipTimes = { r ->
            val a = catalog.animeIdentity
            val id = if (r.kind == "tv" && a != null) a.identity("tv", r.tmdbId) else null
            val mal = if (id != null) a!!.episode(id, r.tmdbId, r.season ?: 1, r.episode ?: 1).mal else null
            val anime = if (mal == null) emptyList() else a!!.skips(mal)
            // Series and films that are not anime: IntroDB, by the IMDb id TMDB gives.
            anime.ifEmpty {
                val imdb = catalog.details(r.kind, r.tmdbId)?.imdbId
                if (imdb == null) emptyList() else introDb.segments(imdb, r.season, r.episode)
            }
        },
        // Three Real-Debrid versions at once on the TV, five on the PC; "off" tries them one after the other.
        // Two at a time: every version raced is a torrent added to Real-Debrid then removed, and after a few dozen of those
        // it refused every add for minutes (24 titles mapped on the Google TV, 2026-11-24).
        raceSize = { if (db.setting("pc.start.race") == "off") 1 else 2 },
    )
}

/** A trailer opens in the browser only as a last resort, and only ever a YouTube page for a checked id. */
fun trailerWebUrl(key: String): String? =
    if (com.sokoul.pc.data.YtDlp.isValidKey(key)) com.sokoul.pc.data.TrailerResolver.youtubeUrl(key) else null

private fun openInBrowser(key: String) {
    val url = trailerWebUrl(key) ?: return
    Platform.openUrl(url)
}

private enum class Route { Splash, Setup, Profiles, Main }

@Composable
fun SokoulPcApp(deps: AppDeps, skipSplash: Boolean = false) {
    val controller = remember { SetupController(deps.db, deps.validator) }
    val setupScope = androidx.compose.runtime.rememberCoroutineScope()
    fun syncSavedPlaylist() {
        if (!controller.playlistChanged && !controller.guideSourceChanged) return
        val playlists = deps.db.playlists()
        val active = deps.db.setting(com.sokoul.desktop.data.Settings.ACTIVE_PLAYLIST)?.toLongOrNull()
        val playlist = playlists.firstOrNull { it.id == active } ?: playlists.firstOrNull() ?: return
        val playlistChanged = controller.playlistChanged
        setupScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val sync = com.sokoul.desktop.data.DesktopSync(deps.db)
            if (playlistChanged) sync.sync(playlist) else sync.syncGuide(playlist)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { deps.uiChanged() }
        }
    }
    /** Loads the active playlist's channels and guide in the background, after a subscription was saved. */
    fun syncActivePlaylist() {
        val playlists = deps.db.playlists()
        val active = deps.db.setting(com.sokoul.desktop.data.Settings.ACTIVE_PLAYLIST)?.toLongOrNull()
        (playlists.firstOrNull { it.id == active } ?: playlists.firstOrNull())?.let { playlist ->
            setupScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.sokoul.desktop.data.DesktopSync(deps.db).sync(playlist) }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { deps.uiChanged() }
            }
        }
    }
    fun afterSplash() = when {
        StartupRoute.needsSetup(StartupRoute.state(deps.db)) -> Route.Setup
        // Several profiles: who is watching decides what Home shows.
        deps.profiles.asksAtStart && deps.prefs.profilePrompt -> Route.Profiles
        else -> Route.Main
    }
    var route by remember { mutableStateOf(if (skipSplash) afterSplash() else Route.Splash) }

    when (route) {
        Route.Splash -> SplashScreen(onFinished = { route = afterSplash() })
        Route.Setup -> if (com.sokoul.pc.ui.Form.tablet) {
            // A touch tablet gets the guided first launch; the TV and the PC keep the remote-driven form for now.
            val model = remember {
                com.sokoul.pc.startup.onboarding.OnboardingModel(
                    deps.db, deps.validator, com.sokoul.iptv.data.debrid.RealDebridOAuth(okhttp3.OkHttpClient()), setupScope,
                )
            }
            com.sokoul.pc.startup.onboarding.OnboardingScreen(
                model,
                onFinished = {
                    // The catalogue opens at once and fills in while the channels arrive.
                    syncActivePlaylist()
                    route = Route.Main
                },
                onLater = { route = Route.Main },
            )
        } else {
            SetupScreen(controller, onDone = { syncSavedPlaylist(); route = Route.Main }, onLater = { route = Route.Main })
        }
        Route.Profiles -> com.sokoul.pc.startup.ProfilesScreen(deps.profiles, onChosen = { route = Route.Main })
        Route.Main -> androidx.compose.runtime.CompositionLocalProvider(com.sokoul.pc.live.LocalProgramArt provides deps.programArt, com.sokoul.pc.data.LocalProfile provides deps.profiles.current) {
            androidx.compose.runtime.key(deps.languageRevision) {
                com.sokoul.pc.home.ScreensaverHost(deps) { MainInterface(deps, onProfiles = { route = Route.Profiles }, onSourcesSaved = ::syncActivePlaylist) }
            }
        }
    }
}

private sealed interface Overlay {
    data class Title(val items: List<TmdbItem>, val index: Int, val origin: CardOrigin?) : Overlay
    data class Collection(val request: CollectionRequest, val origin: CardOrigin?) : Overlay {
        val state = com.sokoul.pc.collections.CollectionState()
    }
    data class Saga(val entries: List<SagaEntry>, val index: Int, val origin: CardOrigin?) : Overlay
    data class Shelf(val shelf: SagaShelf, val origin: CardOrigin?) : Overlay {
        val gridState = androidx.compose.foundation.lazy.grid.LazyGridState()
        val focusRequesters = mutableMapOf<Int, FocusRequester>()
    }
    data class Person(val person: TmdbPerson, val origin: CardOrigin?) : Overlay
    data class Player(val title: String, val kicker: String?, val image: String?, val origin: com.sokoul.pc.ui.CardOrigin? = null) : Overlay
    data class AnimeHub(val view: com.sokoul.pc.anime.AnimeHubView, val kind: com.sokoul.pc.data.anime.MediaKind, val preset: String?, val origin: com.sokoul.pc.ui.CardOrigin? = null) : Overlay
    data class Work(val title: com.sokoul.pc.data.anime.AniTitle, val origin: com.sokoul.pc.ui.CardOrigin? = null) : Overlay
    data class Reader(val request: com.sokoul.pc.data.anime.ReaderRequest) : Overlay
    data class Discover(val view: com.sokoul.pc.discover.DiscoverView, val origin: com.sokoul.pc.ui.CardOrigin? = null) : Overlay
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MainInterface(deps: AppDeps, onProfiles: () -> Unit = {}, onSourcesSaved: () -> Unit = {}) {
    // The user may hide entries of the menu (settings); reading the revision redraws it after a change.
    val hiddenEntries = deps.uiRevision.let { deps.prefs.railHidden }
    val allEntries = listOf(
        NavEntry(SECTION_HOME, tr(Text.MenuHome), NavIcons.Home),
        NavEntry("library", tr(Text.MenuMyList), NavIcons.MyList),
        NavEntry("movies", tr(Text.MenuMovies), NavIcons.Movies),
        NavEntry("series", tr(Text.MenuSeries), NavIcons.Series),
        NavEntry("anime", tr(Text.MenuAnime), NavIcons.Anime),
        NavEntry("collections", tr(Text.MenuCollections), NavIcons.Collections),
        NavEntry("tv", tr(Text.MenuTv), NavIcons.Tv),
        NavEntry("sports", L("Sports", "Sport").text(), NavIcons.Sports),
    )
    val entries = allEntries.filter { it.id !in hiddenEntries && (it.id != "sports" || !deps.profiles.current.kids) }
    var section by remember { mutableStateOf(if (deps.languageReturn) SECTION_SETTINGS else "home") }
    var searchOrigin by remember { mutableStateOf(SECTION_HOME) }
    var sportsOrigin by remember { mutableStateOf(SECTION_HOME) }
    var editingCredentials by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf(false) }
    var editingRows by remember { mutableStateOf(false) }
    var editingAudio by remember { mutableStateOf(false) }
    var editingLanguage by remember { mutableStateOf(deps.languageReturn) }
    // The settings category a sub-page was opened from, shown again when Back leaves it.
    var settingsPane by remember { mutableStateOf(if (deps.languageReturn) "language" else "accounts") }
    LaunchedEffect(Unit) { deps.languageReturn = false }
    var catalogRevision by remember { mutableStateOf(0) }
    val overlays = remember { mutableStateListOf<Overlay>() }
    // Pages that fill the screen: nothing below them is drawn (it stays composed, with its state and focus). The TV drew
    // Home under an open title page at every frame, and the GPU could not keep up (17 to 24 ms per frame, 2026-11-08).
    val covering = remember { androidx.compose.runtime.mutableStateMapOf<Overlay, Boolean>() }
    fun coveredAbove(i: Int) = (i + 1 until overlays.size).any { j -> overlays[j].let { it is Overlay.Player || covering[it] == true } }
    var trailerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val overlayFocus = remember { HashMap<Overlay, FocusRequester>() }
    val scope = rememberCoroutineScope()
    val homeFocus = remember { FocusRequester() }
    val moviesFocus = remember { FocusRequester() }
    val seriesFocus = remember { FocusRequester() }
    val collectionsFocus = remember { FocusRequester() }
    val tvFocus = remember { FocusRequester() }
    val sportsFocus = remember { FocusRequester() }
    val animeFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val sectionStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    val enabled = catalogRevision >= 0 && StartupRoute.state(deps.db) == SetupState.Done && deps.catalog.enabled()

    /** The first control of the section on screen: where keys go when nothing else is left to hold them. */
    fun sectionFocus(): FocusRequester? = when (section) {
        SECTION_HOME -> homeFocus
        "movies" -> moviesFocus
        "series" -> seriesFocus
        "collections" -> collectionsFocus
        "tv" -> tvFocus
        "sports" -> sportsFocus
        "anime" -> animeFocus
        SECTION_SEARCH -> searchFocus
        SECTION_SETTINGS -> settingsFocus
        else -> null
    }

    fun close() {
        val top = overlays.lastOrNull() ?: return
        overlays.removeAt(overlays.lastIndex)
        covering.remove(top)
        val origin = when (top) {
            is Overlay.Player -> {
                // A trailer still resolving must not start once the player has been left.
                trailerJob?.cancel()
                // Music goes on behind the app; a film or a channel stops.
                if (deps.playback.clip == null && (section != "tv" || !deps.playback.returnLiveToGuide())) deps.playback.close()
                top.origin
            }
            is Overlay.Title -> {
                deps.trailer.stop()
                top.origin
            }
            is Overlay.Collection -> top.origin
            is Overlay.AnimeHub -> top.origin
            is Overlay.Work -> top.origin
            is Overlay.Discover -> top.origin
            is Overlay.Reader -> {
                deps.uiChanged()
                null
            }
            is Overlay.Saga -> {
                deps.trailer.stop()
                top.origin
            }
            is Overlay.Shelf -> top.origin
            is Overlay.Person -> top.origin
        }
        overlayFocus.remove(top)
        val revealedOverlay = overlays.lastOrNull()
        val revealedSection = section
        // Once the page has left the composition: until then its own focus group holds on to focus.
        scope.launch {
            withFrameNanos { }
            if (section != revealedSection || overlays.lastOrNull() !== revealedOverlay) return@launch
            if (origin != null) {
                FocusSource.lastWasMouse = true
                if (origin.requester.focusIfPresent()) return@launch
            }
            val below = revealedOverlay?.let(overlayFocus::get) ?: sectionFocus()
            below?.let { runCatching { it.requestFocus() } }
        }
    }

    fun setSection(value: String) {
        if (value == SECTION_SEARCH && section != SECTION_SEARCH) searchOrigin = section
        if (value == "sports" && section != "sports" && section != SECTION_SEARCH) sportsOrigin = if (section == "tv") "tv" else SECTION_HOME
        // A trailer of the page left is not played over the next one: on the PC its video window stayed above the
        // Sport page (2026-11-23).
        if (value != section && value in setOf("tv", "sports", SECTION_SETTINGS, SECTION_SEARCH)) deps.trailer.stop()
        section = value
    }

    fun openLiveSearchResult(hit: com.sokoul.pc.live.TvCatalog.Hit) {
        scope.launch {
            if (hit.program != null) {
                val channel = kotlinx.coroutines.withContext(Dispatchers.IO) { deps.tv.channelById(hit.channel.id) }
                if (channel == null) {
                    deps.notice = com.sokoul.pc.data.L("This channel is no longer available.", "Cette chaîne n'est plus disponible.").text()
                    setSection("tv")
                    return@launch
                }
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    deps.tv.rememberPosition(channel.categoryId, channel.id, hit.program.start)
                }
                setSection("tv")
                return@launch
            }
            val destination = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val channel = deps.tv.channelById(hit.channel.id) ?: return@withContext null
                channel to deps.tv.toLive(deps.tv.channels(channel.categoryId))
            } ?: run {
                deps.notice = com.sokoul.pc.data.L("This channel is no longer available.", "Cette chaîne n'est plus disponible.").text()
                setSection("tv")
                return@launch
            }
            val (channel, live) = destination
            val selected = live.firstOrNull { it.key == channel.id } ?: run {
                setSection("tv")
                return@launch
            }
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                deps.tv.noteWatched(channel.id)
                deps.tv.rememberPosition(channel.categoryId, channel.id)
            }
            deps.trailer.stop()
            overlays.add(Overlay.Player(selected.title, null, null))
            deps.playback.playLive(selected, live)
        }
    }

    fun openTitle(items: List<TmdbItem>, index: Int, origin: CardOrigin?) {
        deps.trailer.stop()
        overlays.add(Overlay.Title(items, index, origin))
    }

    fun openSagas(entries: List<SagaEntry>, index: Int, origin: CardOrigin?) {
        deps.trailer.stop()
        overlays.add(Overlay.Saga(entries, index, origin))
    }

    fun openSaga(tile: SagaTile, origin: CardOrigin?) = openSagas(listOf(SagaEntry(tile)), 0, origin)

    fun openShelf(shelf: SagaShelf, origin: CardOrigin?) {
        deps.trailer.stop()
        overlays.add(Overlay.Shelf(shelf, origin))
    }

    fun openGenre(genre: GenreTile, origin: CardOrigin?) {
        deps.trailer.stop()
        overlays.add(Overlay.Collection(CollectionRequest(genre.name, null, null) { page -> deps.catalog.byGenre(genre.kind, genre.id, page) }, origin))
    }

    fun openPerson(person: TmdbPerson, origin: CardOrigin) {
        deps.trailer.stop()
        overlays.add(Overlay.Person(person, origin))
    }

    fun openRequest(request: CollectionRequest, origin: CardOrigin?) {
        deps.trailer.stop()
        overlays.add(Overlay.Collection(request, origin))
    }

    fun startPlayer(request: PlayRequest) {
        deps.trailer.stop()
        overlays.add(Overlay.Player(request.title, request.kicker, request.image))
        deps.playback.play(request)
    }

    fun play(request: PlayRequest) {
        // A kids profile does not start titles rated for adults: the rating is read before anything opens.
        if (deps.profiles.current.kids) {
            scope.launch {
                val rating = runCatching { deps.catalog.details(request.kind, request.tmdbId)?.certification }.getOrNull()
                if (com.sokoul.pc.data.Profiles.allowedForKids(rating)) {
                    startPlayer(request)
                } else {
                    deps.notice = com.sokoul.pc.data.L("Not available on a kids profile ($rating)", "Indisponible sur un profil enfant ($rating)").text()
                }
            }
        } else {
            startPlayer(request)
        }
    }


    fun playLive(channel: com.sokoul.pc.playback.session.LiveChannel, around: List<com.sokoul.pc.playback.session.LiveChannel>) {
        deps.trailer.stop()
        if (overlays.lastOrNull() is Overlay.Player) overlays.removeAt(overlays.lastIndex)
        overlays.add(Overlay.Player(channel.title, null, null))
        deps.playback.playLive(channel, around)
    }

    fun playProvider(key: String, stream: StreamRequest, title: String, poster: String?, allowed: () -> Boolean) {
        deps.trailer.stop()
        if (!allowed()) {
            deps.notice = com.sokoul.pc.data.L("This title is restricted for this profile.", "Ce titre est restreint pour ce profil.").text()
            return
        }
        if (deps.playback.playDirect(key, stream, title, allowed)) {
            overlays.add(Overlay.Player(title, null, poster))
        } else {
            deps.notice = deps.playback.liveNotice ?: com.sokoul.pc.data.L("This title could not be opened.", "Impossible d’ouvrir ce titre.").text()
        }
    }

    fun downloadProvider(stream: StreamRequest, key: String, title: String) {
        val playlist = deps.tv.playlist() ?: return
        val extension = stream.url.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it in setOf("mp4", "mkv", "ts", "webm", "avi") }
        if (extension == null) {
            deps.notice = com.sokoul.pc.data.L("This entry is not a direct video file and cannot be saved offline.", "Cette source n’est pas un fichier vidéo direct téléchargeable.").text()
            return
        }
        scope.launch(Dispatchers.IO) {
            val verdict = runCatching { deps.tv.recorder.saveStream(playlist, stream, title.ifEmpty { key }, extension) }.getOrNull()
            deps.notice = when (verdict) {
                is com.sokoul.desktop.data.PcRecordingRules.Verdict.Refused -> verdict.reason
                is com.sokoul.desktop.data.PcRecordingRules.Verdict.Ok -> com.sokoul.pc.data.L("Download started.", "Téléchargement démarré.").text()
                else -> com.sokoul.pc.data.L("Could not start the download.", "Impossible de démarrer le téléchargement.").text()
            }
        }
    }

    fun resume(r: Resume) = play(PlayRequest(r.kind, r.tmdbId, r.season, r.episode, r.title, r.subtitle, r.image))

    fun playTrailer(title: String, key: String) {
        deps.trailer.stop()
        overlays.add(Overlay.Player(title, null, null))
        trailerJob = scope.launchTrailer(deps, title, key) {
            if (overlays.lastOrNull() is Overlay.Player) overlays.removeAt(overlays.lastIndex)
        }
    }

    /** A trailer whose link is already known (the title page's other sources): played as it is, nothing to resolve. */
    fun playTrailerStream(title: String, stream: TrailerStream) {
        deps.trailer.stop()
        trailerJob?.cancel()
        overlays.add(Overlay.Player(title, null, null))
        deps.playback.playTrailer(title, stream)
    }

    val currentSection = rememberUpdatedState(section)
    deps.bridge.navigation = remember {
        object : com.sokoul.pc.control.AppNavigation {
            override val section get() = currentSection.value
            override val pages get() = overlays.map { it.javaClass.simpleName }
            override fun goTo(section: String) {
                while (overlays.isNotEmpty()) close()
                setSection(section)
            }
            override fun openTitle(items: List<TmdbItem>, index: Int) = openTitle(items, index, null)
            override fun openSaga(tile: SagaTile) = openSaga(tile, null)
            override fun openPerson(person: TmdbPerson) {
                deps.trailer.stop()
                overlays.add(Overlay.Person(person, null))
            }
            override fun play(request: PlayRequest) = play(request)
            override fun playTrailer(title: String, key: String) = playTrailer(title, key)
            override fun openReader(request: com.sokoul.pc.data.anime.ReaderRequest) { overlays.add(Overlay.Reader(request)) }
            override fun openAnimeCalendar() { overlays.add(Overlay.AnimeHub(com.sokoul.pc.anime.AnimeHubView.Calendar, com.sokoul.pc.data.anime.MediaKind.ANIME, null)) }
            override fun playLive(channel: com.sokoul.pc.playback.session.LiveChannel, around: List<com.sokoul.pc.playback.session.LiveChannel>) = playLive(channel, around)
            override fun back() = close()
        }
    }

    deps.pinch.back = {
        when {
            overlays.isNotEmpty() -> close()
            section != SECTION_HOME -> section = SECTION_HOME
        }
    }

    CompositionLocalProvider(LocalPinch provides deps.pinch) {
    Box(
        Modifier
            .fillMaxSize()
            // The remote's own keys drive the music while one reads or browses.
            .onPreviewKeyEvent { e ->
                val music = deps.playback.clip != null && overlays.lastOrNull() !is Overlay.Player
                if (!music || e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    androidx.compose.ui.input.key.Key.Spacebar, androidx.compose.ui.input.key.Key.MediaPlayPause -> {
                        deps.player?.togglePause()
                        true
                    }
                    androidx.compose.ui.input.key.Key.MediaNext, androidx.compose.ui.input.key.Key.MediaFastForward -> {
                        deps.playback.nextClip()
                        true
                    }
                    androidx.compose.ui.input.key.Key.MediaStop -> {
                        deps.playback.close()
                        true
                    }
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Press) { if (Platform.isBackButton(it)) close() }
            // A precision touchpad's pinch arrives as Ctrl + wheel.
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { e ->
                if (e.keyboardModifiers.isCtrlPressed) {
                    deps.pinch.onDelta(e.changes.first().scrollDelta.y)
                    e.changes.forEach { it.consume() }
                }
            },
    ) {
        // Sub-pages of Settings read with the mouse like Settings: hovering highlights, only a click takes the focus.
        if (editingRows) {
            androidx.compose.runtime.CompositionLocalProvider(com.sokoul.pc.ui.LocalHoverOnly provides true) {
                com.sokoul.pc.library.HomeRowsScreen(deps.prefs, onChanged = deps::uiChanged, onBack = { editingRows = false })
            }
            return@Box
        }
        if (editingAudio) {
            androidx.compose.runtime.CompositionLocalProvider(com.sokoul.pc.ui.LocalHoverOnly provides true) {
                com.sokoul.pc.library.AudioLanguagesScreen(deps.prefs, onBack = { editingAudio = false })
            }
            return@Box
        }
        if (editingLanguage) {
            com.sokoul.pc.library.InterfaceLanguageScreen(
                deps.prefs,
                onChanged = { deps.languageReturn = true; deps.languageChanged() },
                onBack = { editingLanguage = false },
            )
            return@Box
        }
        if (editingAccount) {
            // Settings › Sokoul account: a code and a QR on a TV, sign-in by name where there is a keyboard.
            val accountScope = androidx.compose.runtime.rememberCoroutineScope()
            val model = remember {
                com.sokoul.pc.account.AccountPanelModel(
                    deps.account, accountScope,
                    keyboard = !Platform.IS_TV || com.sokoul.pc.ui.Form.tablet,
                    deviceName = com.sokoul.pc.account.defaultDeviceName(),
                    serverPc = !Platform.IS_TV,
                    syncStatus = deps.sync.status,
                )
            }
            // Opening the panel, and signing in from it, syncs at once (a first sign-in uploads this device's data).
            val signedIn = model.view is com.sokoul.pc.account.AccountView.SignedIn
            androidx.compose.runtime.LaunchedEffect(signedIn) { deps.sync.request() }
            com.sokoul.pc.account.AccountScreen(model, onBack = { editingAccount = false })
            return@Box
        }
        if (editingCredentials) {
            // Settings › Sources and keys: the first launch's cards and sheets, with a guide per service.
            val sourcesScope = androidx.compose.runtime.rememberCoroutineScope()
            val model = remember {
                com.sokoul.pc.startup.onboarding.OnboardingModel(
                    deps.db, deps.validator, com.sokoul.iptv.data.debrid.RealDebridOAuth(okhttp3.OkHttpClient()), sourcesScope,
                )
            }
            val iptvAtOpen = remember { model.sources[com.sokoul.pc.startup.onboarding.SourceKind.Iptv] }
            com.sokoul.pc.setup.SourcesScreen(model, onBack = {
                model.cancelDeviceLink()
                editingCredentials = false
                catalogRevision++
                // A subscription saved here fills the TV pages at once, as after the first launch.
                val iptv = model.sources[com.sokoul.pc.startup.onboarding.SourceKind.Iptv]
                if (iptv != iptvAtOpen && iptv?.status == com.sokoul.pc.startup.onboarding.SourceStatus.Ready) onSourcesSaved()
            })
            return@Box
        }
        // On a touch tablet, Settings opens with nothing lit (settings-redesign touch-input); a remote or keyboard starts on it.
        val defaultFocus = if (section in setOf(SECTION_SETTINGS, SECTION_SEARCH, "sports") && com.sokoul.pc.ui.Form.tablet) null
            else if (enabled || section == SECTION_SETTINGS) sectionFocus() else null
        Box(Modifier.fillMaxSize().drawWithContent { if (!coveredAbove(-1)) drawContent() }) {
        NavShell(
            entries, section, onSelect = ::setSection, defaultFocus = defaultFocus, onProfiles = onProfiles,
            onBack = if (section != SECTION_HOME) ({
                setSection(when (section) {
                    SECTION_SEARCH -> searchOrigin
                    "sports" -> sportsOrigin
                    else -> SECTION_HOME
                })
            }) else null,
        ) {
            sectionStates.SaveableStateProvider(section) {
            when {
                section == "debrid-tools" && !deps.profiles.current.kids -> com.sokoul.pc.library.DebridToolsScreen(
                    remember(deps) { com.sokoul.pc.playback.sources.DebridToolsService(
                        deps.smallHttp,
                        { com.sokoul.desktop.data.ProviderConfig.key(deps.db, "mainapi_url") ?: com.sokoul.desktop.data.ProviderConfig.MAINAPI_DEFAULT_URL },
                        { com.sokoul.desktop.data.ProviderConfig.key(deps.db, "mainapi") ?: com.sokoul.pc.playback.sources.LocalServerKey.read() },
                    ) },
                    onPlay = { stream -> playProvider("host-link-${stream.url.hashCode().toUInt()}", stream, stream.title, null) { !deps.profiles.current.kids } },
                    onOpenDownload = { url -> if (!deps.profiles.current.kids) Platform.openUrl(url) },
                    onBack = { setSection(SECTION_SETTINGS) },
                )
                section == SECTION_SETTINGS -> SettingsScreen(
                    settingsFocus, deps.prefs, onCredentials = { editingCredentials = true }, profile = deps.profiles.current, onProfiles = onProfiles, initialPane = settingsPane,
                    hooks = com.sokoul.pc.library.SettingsHooks(
                        menu = allEntries.map { it.id to it.label },
                        onChanged = deps::uiChanged,
                        onLanguage = deps::languageChanged,
                        onHomeRows = { settingsPane = "home"; editingRows = true },
                        onAudioLanguages = { settingsPane = "playback"; editingAudio = true },
                        onLanguages = { settingsPane = "language"; editingLanguage = true },
                        onDebridTools = { setSection("debrid-tools") },
                        services = {
                            com.sokoul.desktop.data.ProviderConfig.statuses(deps.db).map { com.sokoul.pc.library.ServiceInfo(it.label, it.configured, it.source) } +
                                com.sokoul.pc.library.ServiceInfo("IPTV", deps.db.playlists().isNotEmpty(), null) +
                                listOfNotNull(com.sokoul.desktop.data.ProviderConfig.animeBackendUrl(deps.db)?.let { url ->
                                    com.sokoul.pc.library.ServiceInfo("AniProject anime sources", true, url.toHttpUrlOrNull()?.host)
                                }) +
                                com.sokoul.pc.library.ServiceInfo(
                                    "Web sources (PurStream…)",
                                    com.sokoul.desktop.data.ProviderConfig.key(deps.db, "mainapi") != null || com.sokoul.pc.playback.sources.LocalServerKey.read() != null,
                                    com.sokoul.desktop.data.ProviderConfig.key(deps.db, "mainapi_url") ?: com.sokoul.desktop.data.ProviderConfig.MAINAPI_DEFAULT_URL,
                                )
                        },
                        onClearCache = {
                            deps.clearSearchCache()
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) { deps.db.purgeCache("", Long.MAX_VALUE) }
                        },
                        onForgetSourceFailures = { deps.forgetSourceFailures() },
                        onClearHistory = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                deps.db.clearHistory()
                                deps.uiChanged()
                            }
                        },
                        onSokoulAccount = { settingsPane = "sources"; editingAccount = true },
                        sokoulAccountName = { deps.account.session()?.user?.name },
                        onResetDisplay = {
                            deps.prefs.resetDisplay()
                            com.sokoul.pc.theme.AccentTheme.color = androidx.compose.ui.graphics.Color.White
                            deps.uiChanged()
                        },
                    ),
                )
                !enabled -> Box(Modifier.fillMaxSize().background(Palette.Background).testTag("main-empty"))
                section == SECTION_HOME -> HomeScreen(
                    deps, PageKind.Home, overlays.isEmpty(), homeFocus, ::openTitle, ::resume, ::openSagas, ::openRequest, ::openGenre,
                    onDiscover = { view, origin -> overlays.add(Overlay.Discover(view, origin)) },
                )
                section == "movies" -> HomeScreen(deps, PageKind.Movies, overlays.isEmpty(), moviesFocus, ::openTitle, ::resume, ::openSagas, ::openRequest, ::openGenre)
                section == "series" -> HomeScreen(deps, PageKind.Series, overlays.isEmpty(), seriesFocus, ::openTitle, ::resume, ::openSagas, ::openRequest, ::openGenre)
                section == "collections" -> CollectionsScreen(deps, collectionsFocus, ::openSagas, ::openShelf, ::openTitle)
                section == "anime" -> HomeScreen(
                    deps, PageKind.Anime, overlays.isEmpty(), animeFocus, ::openTitle, ::resume, ::openSagas, ::openRequest, ::openGenre,
                    onAnimeHub = { view, kind, preset, origin -> overlays.add(Overlay.AnimeHub(view, kind, preset, origin)) },
                    onWork = { t, origin ->
                        deps.anime?.let { a -> com.sokoul.pc.anime.openWork(a, t, scope, ::openTitle, { w -> overlays.add(Overlay.Work(w, origin)) }, origin) }
                    },
                    onRead = { overlays.add(Overlay.Reader(it)) },
                )
                section == "tv" -> com.sokoul.pc.live.TvScreen(deps, tvFocus, overlays.isEmpty(), ::playLive, ::playProvider, ::downloadProvider,
                    onSports = if (deps.profiles.current.kids) null else ({ setSection("sports") }))
                section == "sports" -> com.sokoul.pc.live.sport.SportHome(deps, sportsFocus, overlays.isEmpty(), ::playProvider, ::playLive)
                section == SECTION_SEARCH -> SearchScreen(
                    deps,
                    searchFocus,
                    ::openTitle,
                    ::openLiveSearchResult,
                    onOpenWork = { work, origin ->
                        deps.anime?.let { anime ->
                            com.sokoul.pc.anime.openWork(anime, work, scope, ::openTitle, { found -> overlays.add(Overlay.Work(found, origin)) }, origin)
                        }
                    },
                )
                section == "library" -> LibraryScreen(deps.catalog, deps.myList, ::openTitle)
            }
            }
        }
        }
        com.sokoul.pc.live.ReminderHost(deps, Modifier.align(androidx.compose.ui.Alignment.TopCenter)) { channel -> playLive(channel, listOf(channel)) }
        overlays.forEachIndexed { i, overlay ->
            if (i >= overlays.lastIndex - 1) {
                val itemFocus = overlayFocus.getOrPut(overlay) { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (overlay is Overlay.Player) Modifier else Modifier.focusRequester(itemFocus))
                        // Only the page on top keeps focus inside. A page below must let it go, or the one above never gets the keys.
                        .focusProperties { exit = { if (i == overlays.lastIndex) FocusRequester.Cancel else FocusRequester.Default } }
                        .focusGroup()
                        .drawWithContent { if (!coveredAbove(i)) drawContent() },
                ) {
                    when (overlay) {
                        is Overlay.Title -> TitleScreen(
                            deps, overlay.items, overlay.index, overlay.origin,
                            onClose = ::close,
                            onPlay = ::play,
                            onPlayTrailer = ::playTrailer,
                            onPlayTrailerStream = ::playTrailerStream,
                            onOpenSimilar = ::openTitle,
                            onOpenPerson = ::openPerson,
                            onOpenSaga = ::openSaga,
                            onTop = i == overlays.lastIndex,
                            onRead = { overlays.add(Overlay.Reader(it)) },
                            onCovers = { covering[overlay] = it },
                            onPlayThemes = { themes, index, origin ->
                                deps.trailer.stop()
                                overlays.add(Overlay.Player(themes[index].song.ifEmpty { themes[index].slug }, themes[index].slug, null, origin))
                                deps.playback.playClips(themes.map { t -> com.sokoul.pc.playback.session.Clip(t.song.ifEmpty { t.slug }, t.slug, t.video) }, index)
                            },
                        )
                        is Overlay.Collection -> if (overlay.request.studio != null) com.sokoul.pc.collections.StudioScreen(
                            deps, overlay.request.studio, overlay.request.logo, onClose = ::close, onOpenTitle = ::openTitle, onOpenRequest = ::openRequest,
                            active = i == overlays.lastIndex,
                        ) else CollectionDetailScreen(
                            deps, overlay.request, onClose = ::close, onOpenTitle = ::openTitle,
                            state = overlay.state, active = i == overlays.lastIndex,
                        )
                        is Overlay.Saga -> SagaScreen(deps, overlay.entries, overlay.index, overlay.origin, onClose = ::close, onPlay = ::play, onOpenTitle = ::openTitle, onOpenPerson = ::openPerson, onPlayTrailer = ::playTrailer, onTop = i == overlays.lastIndex)
                        is Overlay.Shelf -> SagaShelfScreen(
                            deps, overlay.shelf, onClose = ::close, onOpenSaga = ::openSagas,
                            gridState = overlay.gridState,
                            focusRequesters = overlay.focusRequesters,
                            active = i == overlays.lastIndex,
                        )
                        is Overlay.Person -> PersonScreen(deps, overlay.person.id ?: 0, overlay.person.name, overlay.person.photo, onClose = ::close, onOpenTitle = ::openTitle)
                        is Overlay.AnimeHub -> if (overlay.view == com.sokoul.pc.anime.AnimeHubView.Categories) {
                            com.sokoul.pc.anime.AnimeCategoriesPage(deps, overlay.kind, overlay.preset, ::openTitle, { overlays.add(Overlay.Reader(it)) }, ::close)
                        } else {
                            com.sokoul.pc.anime.AnimeCalendarPage(deps, ::openTitle, { overlays.add(Overlay.Reader(it)) }, ::close)
                        }
                        is Overlay.Work -> com.sokoul.pc.anime.WorkPage(deps, overlay.title, ::openTitle, { overlays.add(Overlay.Reader(it)) }, ::close)
                        is Overlay.Reader -> com.sokoul.pc.anime.MangaReader(deps, overlay.request, { deps.toggleFullscreen() }, ::close)
                        is Overlay.Discover -> com.sokoul.pc.discover.DiscoverPage(deps, overlay.view, ::openTitle, ::close)
                        is Overlay.Player -> PlayerScreen(deps.player, deps.playback, overlay.title, overlay.kicker, overlay.image, onClose = ::close, subtitles = deps.subtitles,
                            focusRequester = itemFocus,
                            prefs = deps.prefs,
                            reminders = deps.reminders,
                            onToggleFullscreen = { deps.toggleFullscreen() },
                            fullscreen = deps.isFullscreen?.invoke(),
                            nextEpisode = { r -> nextEpisodeOf(deps, r) },
                            logoOf = { r -> runCatching { deps.catalog.details(r.kind, r.tmdbId)?.logo }.getOrNull() },
                            episodes = remember { episodeSourceOf(deps) },
                            extra = { com.sokoul.pc.anime.PauseChapterCard(deps) { overlays.add(Overlay.Reader(it)) } },
                            party = deps.party,
                        )
                    }
                }
            }
        }
        if (overlays.lastOrNull() !is Overlay.Player) com.sokoul.pc.playback.ui.MiniMusic(deps, Modifier.align(androidx.compose.ui.Alignment.BottomEnd))
    }
    }
    LaunchedEffect(section) { deps.trailer.stop() }
}

/** Full-screen trailer: inside the app whenever it resolves, otherwise in the browser. */
private fun CoroutineScope.launchTrailer(deps: AppDeps, title: String, key: String, onFailed: () -> Unit) = launch {
    val stream = deps.resolveTrailer(listOf(key))
    if (stream != null) {
        deps.playback.playTrailer(title, stream)
    } else {
        openInBrowser(key)
        onFailed()
    }
}

/** The next episode of a series: in its season, else the first of the following one. */
/** The player's episode panel reads the catalog; what was watched is read off the interface thread. */
private fun episodeSourceOf(deps: AppDeps) = object : com.sokoul.pc.playback.ui.EpisodeSource {
    override suspend fun seasonCount(tvId: Int) = deps.catalog.details("tv", tvId)?.seasons
    override suspend fun episodes(tvId: Int, season: Int) = deps.catalog.season(tvId, season)
    override suspend fun watched(tvId: Int) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        deps.catalog.watchedEpisodes(tvId).mapNotNull { r ->
            val s = r.season ?: return@mapNotNull null
            val e = r.episode ?: return@mapNotNull null
            (s to e) to if (r.finished) 1f else r.fraction
        }.toMap()
    }
}

private suspend fun nextEpisodeOf(deps: AppDeps, r: PlayRequest): PlayRequest? {
    if (r.kind != "tv") return null
    val season = r.season ?: return null
    val episode = r.episode ?: return null
    val next = com.sokoul.pc.playback.ui.EpisodeOrder.next(season, episode) { deps.catalog.season(r.tmdbId, it) }
        ?: return null
    return r.copy(season = next.season, episode = next.episode, kicker = "S${next.season}E${next.episode} · ${next.name}", chooseFirst = false)
}
