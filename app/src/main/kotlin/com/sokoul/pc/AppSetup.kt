package com.sokoul.pc

import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.AppDirs
import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopDebrid
import com.sokoul.desktop.data.DesktopSync
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.Settings
import com.sokoul.desktop.player.DesktopPlayer
import com.sokoul.iptv.data.net.CoreLog
import com.sokoul.iptv.data.net.Redact
import com.sokoul.pc.data.CredentialValidator
import com.sokoul.pc.data.DbTrailerStore
import com.sokoul.pc.data.LegacyImport
import com.sokoul.pc.data.MyList
import com.sokoul.pc.data.PcLog
import com.sokoul.pc.data.TmdbCatalog
import com.sokoul.pc.data.TrailerResolver
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.iptv.data.debrid.RealDebridApi
import com.sokoul.pc.data.providers.ProviderHttp
import com.sokoul.pc.data.providers.SubtitleResult
import com.sokoul.pc.data.providers.SubtitlesClient
import com.sokoul.pc.dev.FaultSources
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.sources.SubtitleSource
import com.sokoul.pc.i18n.Strings
import com.sokoul.pc.playback.session.ConnectionGate
import com.sokoul.pc.playback.sources.DebridTransfers
import com.sokoul.pc.playback.sources.LocalServerKey
import com.sokoul.pc.playback.sources.RealSources
import com.sokoul.pc.playback.sources.WebSources
import com.sokoul.pc.playback.sources.StreamProbe
import com.sokoul.pc.playback.session.VlcEngine
import com.sokoul.pc.startup.SetupState
import com.sokoul.pc.startup.SplashScreen
import com.sokoul.pc.startup.StartupRoute
import com.sokoul.pc.ui.FocusSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** The installed app has no console: errors go to a file in the owner-only data folder, with credentials removed. */
fun logCrash(t: Throwable) = runCatching {
    File(AppDirs.data, "crash.log").appendText("${LocalDateTime.now()} ${Redact.text(t.stackTraceToString())}\n")
}

/** First launch only: picks up what the existing Sokoul PC app already knows, so nothing has to be typed again. */
fun importOnce(db: DesktopDb, source: File?, workDir: File) {
    LegacyImport.purgeLeftovers(workDir)
    if (db.setting(StartupRoute.IMPORT_DONE) != null) return
    val summary = LegacyImport.run(source, db, workDir)
    if (summary.playlists > 0 || summary.keys > 0) db.putSetting(StartupRoute.IMPORT_DONE, "1")
}

/** Builds the real dependencies. Only this function knows about the network and libVLC. */
fun realDeps(db: DesktopDb, player: CompletableFuture<DesktopPlayer?>, loaded: () -> DesktopPlayer?): AppDeps {
    val http = OkHttpClient.Builder().dns(com.sokoul.iptv.data.net.Ipv4FirstDns).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    val catalog = Catalog(db)
    val pcCatalog = TmdbCatalog(db, catalog) { StartupRoute.state(db) == SetupState.Done }
    val debrid = DesktopDebrid(db, catalog)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val trailerSources = com.sokoul.pc.data.TrailerSources(http)
    val trailers = TrailerResolver(catalog.http, { ProviderConfig.key(db, "realdebrid") }, store = DbTrailerStore(db))
    val prefs = DesktopPrefs(db)
    val profiles = com.sokoul.pc.data.Profiles(db)
    val gate = ConnectionGate()
    val nativeEmbedResolver = com.sokoul.pc.playback.sources.NativeEmbedResolver(catalog.http)
    appScope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { nativeEmbedResolver.close() }
    val realSources = RealSources(
        db, debrid, catalog,
        DebridTransfers(RealDebridApi(catalog.http, { ProviderConfig.key(db, "realdebrid") })),
        gate,
        StreamProbe(catalog.http),
        anime = pcCatalog.animeIdentity,
        web = WebSources(
            catalog.http,
            baseUrl = { ProviderConfig.key(db, "mainapi_url") ?: ProviderConfig.MAINAPI_DEFAULT_URL },
            // On the PC that runs the server, `API\seed-local-key.js` leaves the key where the app finds it without typing.
            accessKey = { ProviderConfig.key(db, "mainapi") ?: LocalServerKey.read() },
            probe = StreamProbe(catalog.http),
            localResolver = nativeEmbedResolver,
            // Sources the app reads itself, without the Sokoul server (Docker): the others follow one by one.
            // Each is keyed by its name, the name of its server route in WebRoutes.
            direct = listOf<com.sokoul.sources.WebSource>(
                com.sokoul.sources.purstream.PurStream(
                    catalog.http, tmdb = { kind, id -> catalog.tmdb.namesAndImages(catalog.key(), kind, id) },
                ),
                com.sokoul.sources.coflix.Coflix(
                    catalog.http, tmdb = { kind, id, language -> catalog.tmdb.namesAndImages(catalog.key(), kind, id, language) },
                ),
                com.sokoul.sources.fstream.FStream(
                    catalog.http, tmdb = { kind, id, language -> catalog.tmdb.namesAndImages(catalog.key(), kind, id, language) },
                ),
                com.sokoul.sources.voirdrama.VoirDrama(
                    catalog.http, tmdb = { kind, id, language -> catalog.tmdb.namesAndImages(catalog.key(), kind, id, language) },
                ),
            ).associateBy { it.name },
        ),
        animeWeb = com.sokoul.pc.playback.sources.AniProjectSources(
            catalog.http,
            backendUrl = { ProviderConfig.animeBackendUrl(db) },
            animeIdentity = pcCatalog.animeIdentity,
            probe = StreamProbe(catalog.http),
        ),
        animeSama = com.sokoul.pc.playback.sources.AnimeSamaSource(
            catalog.http,
            baseUrl = { ProviderConfig.key(db, "mainapi_url") ?: ProviderConfig.MAINAPI_DEFAULT_URL },
            accessKey = { ProviderConfig.key(db, "mainapi") ?: LocalServerKey.read() },
            probe = StreamProbe(catalog.http),
            localResolver = nativeEmbedResolver,
            // Without the Sokoul server (Docker off, or a TV away from the PC), the app reads Anime-Sama itself.
            direct = com.sokoul.sources.animesama.AnimeSama(catalog.http),
        ),
        kidsProfile = { profiles.current.kids },
    )
    val subtitles = SubtitlesClient(
        ProviderHttp(catalog.http, db::tmdbGet, db::tmdbPut),
        { ProviderConfig.key(db, "opensubtitles") }, { ProviderConfig.key(db, "subdl") },
        db::tmdbGet, db::tmdbPut,
    )
    val subtitleSource = object : SubtitleSource {
        override fun available() = subtitles.available()
        override suspend fun search(request: PlayRequest) =
            subtitles.search(request.kind, request.tmdbId, request.season, request.episode, if (Strings.isFrench()) listOf("fr", "en") else listOf("en"))
        override suspend fun cues(result: SubtitleResult) = subtitles.cues(result)
    }
    val deps = AppDeps(
        db = db,
        profiles = profiles,
        validator = CredentialValidator(http),
        catalog = pcCatalog,
        myList = MyList(db),
        playerProvider = loaded,
        engine = VlcEngine(player, appScope),
        sources = if (!System.getenv("SOKOUL_DEBUG").isNullOrBlank() && !System.getenv("SOKOUL_FAULTS").isNullOrBlank()) FaultSources(realSources) else realSources,
        diagnose = realSources::diagnose,
        onStreamClosed = gate::noteStopped,
        clearForLive = realSources::clearForLive,
        connectionUse = realSources::connectionUse,
        shortGuide = realSources::shortGuide,
        subtitleSource = subtitleSource,
        // Apple's trailer first when it is full HD (French sound, no DRM), else YouTube's as before (2026-11-24).
        resolveTrailer = { keys -> trailerSources.resolve(keys) { if (Platform.IS_TV) Platform.resolveTrailer(it) else trailers.best(it) } },
        trailerSources = trailerSources,
        sourceTimeline = realSources::timeline,
        sourceLabels = {
            listOfNotNull(
                tr(Text.SourceRealDebrid).takeIf { debrid.configured() },
                tr(Text.SourceIptv).takeIf { db.playlists().isNotEmpty() },
            )
        },
        scope = appScope,
    )
    deps.tv.kidsProfile = { deps.profiles.current.kids }
    deps.tv.recorder.configure(
        beforeCapture = {
            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) { deps.playback.stopForRecording() }
            kotlinx.coroutines.delay(1_500)
        },
        keepAlive = Platform::recordingKeepAlive,
    )
    deps.tv.recorder.start(deps.tv::playlist)
    deps.clearSearchCache = debrid::clearCache
    // Account sync (Lot 6): in the background from start, idle while this device is signed out.
    deps.sync.start(appScope, com.sokoul.pc.ui.AppFocus.active)
    // A Real-Debrid sign-in made with a device code is renewed before its token ends.
    appScope.launch(Dispatchers.IO) {
        runCatching { com.sokoul.desktop.data.RealDebridAuth(db, com.sokoul.iptv.data.debrid.RealDebridOAuth(http)).refreshIfNeeded() }
    }
    // Once a day, what can never play leaves the Real-Debrid account (RdCleanup); a minute after start, out of the way.
    appScope.launch(Dispatchers.IO) {
        kotlinx.coroutines.delay(60_000)
        if (ProviderConfig.key(db, "realdebrid") != null) runCatching {
            com.sokoul.pc.playback.sources.RdCleanup(RealDebridApi(catalog.http, { ProviderConfig.key(db, "realdebrid") }), db).runIfDue()
        }
    }
    return deps
}

