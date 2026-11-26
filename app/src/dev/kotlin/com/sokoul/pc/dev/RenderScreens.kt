package com.sokoul.pc.dev

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.Density
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.pc.AppDeps
import com.sokoul.pc.SokoulPcApp
import com.sokoul.pc.data.MyList
import com.sokoul.pc.data.CredentialValidator
import com.sokoul.pc.realDeps
import com.sokoul.pc.startup.SetupState
import com.sokoul.pc.startup.StartupRoute
import com.sokoul.pc.startup.SplashScreen
import com.sokoul.pc.ui.ScaledRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders screens to PNG with no window. Setup screens use a throwaway database with sample values only. Catalog screens
 * use the real TMDB key, and those screens never display a credential.
 */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "renders").also { it.mkdirs() }
    val realKeys = ownerKeys(ProviderConfig.builtIn)

    fun scene(deps: AppDeps, width: Int = 1920, height: Int = 1080, splash: Boolean = false) =
        ImageComposeScene(width, height, Density(1f)) {
            ScaledRoot { if (splash) SplashScreen(onFinished = {}) else SokoulPcApp(deps, skipSplash = true) }
        }

    fun fakeDeps(db: DesktopDb) = realDeps(db, java.util.concurrent.CompletableFuture.completedFuture(null)) { null }

    fun save(name: String, s: ImageComposeScene, time: Long) {
        File(out, "$name.png").writeBytes(s.render(time).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println(name)
    }

    fun setupRender(name: String, w: Int, h: Int, prepare: (DesktopDb) -> Unit, splash: Boolean = false) {
        val file = File.createTempFile("render", ".db")
        ProviderConfig.builtIn = { emptyMap() }
        DesktopDb(file).use { db ->
            prepare(db)
            val s = scene(fakeDeps(db), w, h, splash)
            var t = 0L
            repeat(6) { s.render(t); t += 500_000_000L }
            save(name, s, t)
            s.close()
        }
        file.delete()
    }

    // Native workspace fixtures use only a throwaway M3U catalogue and never resolve media or credentials.
    if (args.getOrNull(1) == "guide") {
        com.sokoul.pc.live.ChannelLogos.index = { emptyMap() }
        listOf("en", "fr").forEach { language ->
            com.sokoul.pc.i18n.Strings.applyLanguage(language)
            listOf(1920 to 1080, 1280 to 720).forEach { (width, height) ->
                listOf("normal", "long", "short", "empty", "preview").forEach { scenario ->
                    val s = ImageComposeScene(width, height, Density(1f)) {
                        ScaledRoot { com.sokoul.pc.live.FormulerGuideFixture(scenario) }
                    }
                    var time = 0L
                    repeat(8) { s.render(time); time += 100_000_000L; Thread.sleep(15) }
                    save("guide-$scenario-$language-${width}x$height", s, time)
                    s.close()
                }
            }
        }
        kotlin.system.exitProcess(0)
    }
    if (args.getOrNull(1) == "groups") {
        // The channel-group sidebar alone, with groups like the owner's provider (2026-11-24).
        com.sokoul.pc.i18n.Strings.applyLanguage("fr")
        val sample = listOf(
            com.sokoul.desktop.data.LiveLogic.Group(com.sokoul.desktop.data.SpecialGroup.ALL, "Toutes", false),
            com.sokoul.desktop.data.LiveLogic.Group(com.sokoul.desktop.data.SpecialGroup.FAVORITES, "Favoris", false),
            com.sokoul.desktop.data.LiveLogic.Group(com.sokoul.pc.live.TvCatalog.RECENT, "Récentes", false),
            com.sokoul.desktop.data.LiveLogic.Group(com.sokoul.pc.live.TvPersonalization.group("c1"), "Mon foot", false),
        ) + listOf("FRANCE GENERALE", "FRANCE SPORT", "Bein Sports", "Bein Sports FHD / 4k", "Bein Sports Premium", "Dstv Generale", "Dstv Sport",
            "Dstv Cinema", "Africa Canal +", "Yemen", "Christian", "Senegal", "Cameroon", "Ghana").mapIndexed { i, n ->
            com.sokoul.desktop.data.LiveLogic.Group("g$i", n, false)
        }
        listOf(1280 to 720, 1920 to 1080).forEach { (width, height) ->
            listOf("g4", com.sokoul.desktop.data.SpecialGroup.ALL).forEach { chosen ->
                val s = ImageComposeScene(width, height, Density(1f)) {
                    ScaledRoot {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                            com.sokoul.pc.live.TvGroupColumn(sample, chosen,
                                androidx.compose.ui.Modifier.padding(start = 16.dp, top = 24.dp, bottom = 20.dp).width(com.sokoul.pc.live.TV_COLUMN_WIDTH).fillMaxHeight(),
                                onApply = {}, onGroupActions = {}, onNewCollection = {}, onManageCollection = {}, onClose = {})
                        }
                    }
                }
                var time = 0L
                repeat(8) { s.render(time); time += 100_000_000L; Thread.sleep(15) }
                save("groups-$chosen-${width}x$height", s, time)
                s.close()
            }
        }
        kotlin.system.exitProcess(0)
    }
    if (args.getOrNull(1) == "workspace") {
        ProviderConfig.builtIn = { emptyMap() }
        listOf("workspace", "organization", "pin", "kids", "recordings-empty").forEach { surface ->
            val file = File.createTempFile("render-workspace", ".db")
            DesktopDb(file).use { db ->
                val pid = db.upsertPlaylist(com.sokoul.desktop.data.Playlist(0, "Sample provider", com.sokoul.desktop.data.PlaylistType.M3U,
                    "https://example.invalid/fixture", null, null, null, null))
                db.replaceLive(pid, listOf(
                    com.sokoul.desktop.data.Category(pid, "family", "Family", 0),
                    com.sokoul.desktop.data.Category(pid, "cinema", "Cinema", 1),
                    com.sokoul.desktop.data.Category(pid, "sports", "Sports", 2),
                ), emptyList())
                // realDeps owns a process recorder; each fixture must bind it to this fixture database.
                val recordingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                com.sokoul.desktop.data.DesktopRecorder(db, com.sokoul.desktop.data.DesktopSync(db), recordingScope,
                    File(file.parentFile, file.name + "-recordings"))
                val deps = fakeDeps(db)
                if (surface == "organization") {
                    deps.tv.organization.setPin("4826")
                    deps.tv.organization.setGroupLocked("cinema", true)
                    deps.tv.organization.setGroupHidden("sports", true)
                }
                if (surface == "kids") deps.profiles.switchTo(deps.profiles.add("Kids", kids = true))
                listOf("en", "fr").forEach { language ->
                    com.sokoul.pc.i18n.Strings.applyLanguage(language)
                    listOf(1920 to 1080, 1280 to 720).forEach { (width, height) ->
                        val s = ImageComposeScene(width, height, Density(1f)) {
                            ScaledRoot {
                                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(com.sokoul.pc.theme.Palette.Background),
                                    contentAlignment = androidx.compose.ui.Alignment.Center) {
                                    when (surface) {
                                        "workspace" -> com.sokoul.pc.live.TvWorkspacePanel(onSelect = {}, onClose = {})
                                        "recordings-empty" -> com.sokoul.pc.live.TvRecordingsPanel(deps, onPlay = { _, _, _, _, _ -> }, onClose = {})
                                        else -> com.sokoul.pc.live.TvOrganizationPanel(deps, onChanged = {}, onClose = {})
                                    }
                                }
                            }
                        }
                        var time = 0L
                        repeat(12) {
                            s.render(time)
                            time += 100_000_000L
                            Thread.sleep(15) // Allow bounded local SQLite work to settle before capturing.
                        }
                        save("$surface-$language-${width}x$height", s, time)
                        s.close()
                    }
                }
                recordingScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
            file.delete()
        }
        kotlin.system.exitProcess(0)
    }

    if (args.getOrNull(1) == "panels") {
        renderPanels(out)
        kotlin.system.exitProcess(0)
    }

    if (args.getOrNull(1) == "sources") {
        val versions = (0 until 150).map { index ->
            com.sokoul.pc.playback.session.PlayVersion(
                "preview-$index", com.sokoul.pc.playback.session.VersionOrigin.entries[index % 3],
                "Example.Series.S01E01.${if (index % 2 == 0) "1080p.WEB-DL" else "720p.WEB"}",
                com.sokoul.iptv.domain.AudioLanguage.entries[index % 4], com.sokoul.iptv.domain.VideoQuality.FHD_1080,
                sizeBytes = if (index == 0) 20_000_000 else 1_600_000_000,
                readiness = com.sokoul.pc.playback.session.Readiness.Ready,
                announcedAudio = listOf(listOf("FR", "EN"), listOf("PL"), listOf("SV"), emptyList())[index % 4],
                rejected = com.sokoul.pc.playback.session.Doubt.OtherEpisode.takeIf { index % 10 == 9 },
                open = { _, _ -> error("Preview sources never open a stream") },
            )
        }
        listOf("fr", "en").forEach { language ->
            com.sokoul.pc.i18n.Strings.applyLanguage(language)
            listOf(1920 to 1080, 1280 to 720).forEach { (width, height) ->
                val s = ImageComposeScene(width, height, Density(1f)) {
                    ScaledRoot {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(com.sokoul.pc.theme.Palette.Background)) {
                            com.sokoul.pc.playback.ui.SourceBrowserContent(versions, versions[0].key, false,
                                notices = listOf(com.sokoul.pc.playback.session.SourceNotice.AnimeSamaVipRequired),
                                modifier = Modifier.padding(start = 950.dp, top = 80.dp), verified = { it.key == versions[0].key })
                        }
                    }
                }
                var time = 0L
                repeat(6) { s.render(time); time += 100_000_000L }
                save("sources-$language-${width}x$height", s, time)
                s.close()
            }
        }
        kotlin.system.exitProcess(0)
    }

    if (args.getOrNull(1) == "search") {
        val item = com.sokoul.iptv.data.net.TmdbItem(1, "The Grand Budapest Hotel", null, 2014, null, null, 8.1, null)
        val results = com.sokoul.pc.search.SearchResults<com.sokoul.iptv.data.net.TmdbItem, com.sokoul.pc.live.TvCatalog.Hit, com.sokoul.pc.data.anime.AniTitle, com.sokoul.pc.data.anime.AniTitle>(
            titles = com.sokoul.pc.search.SearchSourceResults(true, items = listOf(item)),
            live = com.sokoul.pc.search.SearchSourceResults<com.sokoul.pc.live.TvCatalog.Hit>(false),
            anime = com.sokoul.pc.search.SearchSourceResults<com.sokoul.pc.data.anime.AniTitle>(false),
            manga = com.sokoul.pc.search.SearchSourceResults<com.sokoul.pc.data.anime.AniTitle>(false),
        )
        listOf("en", "fr").forEach { language ->
            com.sokoul.pc.i18n.Strings.applyLanguage(language)
            listOf(1920 to 1080, 1280 to 720).forEach { (width, height) ->
                val s = ImageComposeScene(width, height, Density(1f)) {
                    ScaledRoot {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(com.sokoul.pc.theme.Palette.Background).padding(64.dp)) {
                            com.sokoul.pc.search.SearchContent(results, com.sokoul.pc.search.SearchTab.All, { _, _, _ -> }, {}, { _, _ -> })
                        }
                    }
                }
                var time = 0L
                repeat(4) { s.render(time); time += 150_000_000L }
                save("search-$language-${width}x$height", s, time)
                s.close()
            }
        }
        return
    }

    if (args.getOrNull(1) == "watchlist") {
        val now = System.currentTimeMillis()
        val entries = listOf(
            com.sokoul.pc.live.Reminder("sample-1", "TF1", "Journal de 20 heures", now + 45 * 60_000L),
            com.sokoul.pc.live.Reminder("sample-2", "Arte", "Cinéma de soirée", now + 2 * 3_600_000L),
            com.sokoul.pc.live.Reminder("sample-3", "France 2", "Documentaire", now + 24 * 3_600_000L),
        )
        listOf(1920 to 1080, 1280 to 720).forEach { (width, height) ->
            listOf("populated" to entries, "empty" to emptyList()).forEach { (state, reminders) ->
                val scene = ImageComposeScene(width, height, Density(1f)) {
                    ScaledRoot {
                        androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier.fillMaxSize().background(com.sokoul.pc.theme.Palette.Background),
                        ) {
                            com.sokoul.pc.live.WatchlistDrawer(
                                reminders,
                                androidx.compose.ui.Modifier.width(500.dp),
                                onWatch = {},
                                onRemove = {},
                                onClose = {},
                            )
                        }
                    }
                }
                var time = 0L
                repeat(8) { scene.render(time); time += 80_000_000L }
                save("watchlist_${state}_${width}x$height", scene, time)
                scene.close()
            }
        }
        kotlin.system.exitProcess(0)
    }

    // "sources-page": Settings › Sources and keys with one source ready, one with a problem and the rest empty, then the
    // TMDB guide and its key sheet open; TV 1920x1080 and a tablet held upright, in a throwaway database.
    if (args.getOrNull(1) == "sources-page") {
        ProviderConfig.builtIn = { emptyMap() }
        val file = File.createTempFile("render-sources-page", ".db")
        DesktopDb(file).use { db ->
            val pid = db.upsertPlaylist(com.sokoul.desktop.data.Playlist(0, "IPTV", com.sokoul.desktop.data.PlaylistType.XTREAM,
                "http://exemple.invalid:8080", "sample", "sample", null, null))
            db.putSetting(com.sokoul.desktop.data.Settings.ACTIVE_PLAYLIST, pid.toString())
            ProviderConfig.setKey(db, "tmdb", "0123456789abcdef0123456789ab4f2c")
            ProviderConfig.setKey(db, "opensubtitles", "sample-subtitles-k9Q1")
            val model = com.sokoul.pc.startup.onboarding.OnboardingModel(
                db, CredentialValidator(OkHttpClient()), com.sokoul.iptv.data.debrid.RealDebridOAuth(OkHttpClient()),
                CoroutineScope(Dispatchers.Unconfined),
            )
            model.sources[com.sokoul.pc.startup.onboarding.SourceKind.RealDebrid] = com.sokoul.pc.startup.onboarding.SourceView(
                com.sokoul.pc.startup.onboarding.SourceStatus.Problem, problem = com.sokoul.pc.startup.onboarding.Copy.debridRefused,
            )
            val wanted = args.getOrNull(2)?.split(',')
            data class Shot(val name: String, val w: Int, val h: Int, val portrait: Boolean, val guide: String?, val entry: String?, val scrolled: Boolean = false)
            val shots = listOf(
                Shot("page", 1920, 1080, false, null, null),
                Shot("page-scrolled", 1920, 1080, false, null, null, scrolled = true),
                Shot("guide-tmdb", 1920, 1080, false, "tmdb", null),
                Shot("guide-subtitles", 1920, 1080, false, "subtitles", null),
                Shot("guide-iptv", 1920, 1080, false, "iptv", null),
                Shot("entry-tmdb", 1920, 1080, false, null, "tmdb"),
                Shot("tablet-page", 1600, 2560, true, null, null),
                Shot("tablet-guide-tmdb", 1600, 2560, true, "tmdb", null),
            ).filter { wanted == null || it.name in wanted }
            for (language in listOf("fr", "en")) {
                com.sokoul.pc.i18n.Strings.applyLanguage(language)
                for (shot in shots) {
                    com.sokoul.pc.ui.Form.tablet = shot.portrait
                    com.sokoul.pc.ui.Form.tabletPortrait = shot.portrait
                    model.keyErrors.clear()
                    model.keyErrors["omdb"] = com.sokoul.pc.startup.onboarding.Copy.keyRefused
                    val s = ImageComposeScene(shot.w, shot.h, Density(1f)) {
                        ScaledRoot {
                            com.sokoul.pc.setup.SourcesScreen(model, onBack = {}, backButton = shot.portrait, initialGuide = shot.guide, initialEntry = shot.entry)
                        }
                    }
                    var t = 0L
                    repeat(12) { s.render(t); t += 50_000_000L }
                    if (shot.scrolled) {
                        repeat(5) {
                            s.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown)); s.render(t)
                            s.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyUp)); s.render(t)
                            t += 200_000_000L
                        }
                        repeat(8) { s.render(t); t += 50_000_000L }
                    }
                    save("sources-page_${shot.name}_$language", s, t)
                    s.close()
                }
            }
            com.sokoul.pc.ui.Form.tablet = false
            com.sokoul.pc.ui.Form.tabletPortrait = false
        }
        file.delete()
        kotlin.system.exitProcess(0)
    }

    // "account-panel": Settings › Sokoul account. Signed out with the code and its QR, signed in with three devices,
    // the address step and the signed-out choices; TV 1920x1080 and a tablet held upright, FR and EN. No server is asked.
    if (args.getOrNull(1) == "account-panel") {
        val now = 1_764_000_000_000L
        val serverUrl = "http://192.168.0.10:25570"
        val store = com.sokoul.pc.account.MemoryAccountStore(serverUrl)
        val account = com.sokoul.pc.account.SokoulAccount(store, OkHttpClient(), clock = { now })
        val code = com.sokoul.pc.account.LinkCode(
            com.sokoul.pc.account.Secret("render"), "BCDF-GHJK", "$serverUrl/link?code=BCDF-GHJK", now + 9 * 60_000L + 42_000L, 3,
        )
        val day = 86_400_000L
        val devices = listOf(
            com.sokoul.pc.account.DeviceSession("s1", "Google TV Streamer", now - 30 * day, now, current = true),
            com.sokoul.pc.account.DeviceSession("s2", "Galaxy Tab S7 FE", now - 20 * day, now - day, current = false),
            com.sokoul.pc.account.DeviceSession("s3", "SOKOUL-PC", now - 40 * day, now - 3 * day, current = false),
        )
        // On the tablet, the tablet is this device.
        val tabletDevices = devices.map { it.copy(current = it.id == "s2", lastUsedAt = if (it.id == "s2") now else if (it.id == "s1") now - day else it.lastUsedAt) }
        val user = com.sokoul.pc.account.AccountUser("u1", "Ana", "owner")
        data class Shot(val name: String, val w: Int, val h: Int, val portrait: Boolean, val view: com.sokoul.pc.account.AccountView, val keyboard: Boolean)
        val wanted = args.getOrNull(2)?.split(',')
        val shots = listOf(
            Shot("linking", 1920, 1080, false, com.sokoul.pc.account.AccountView.Linking(code), false),
            Shot("signed-in", 1920, 1080, false, com.sokoul.pc.account.AccountView.SignedIn(user, devices, justConnected = true), false),
            Shot("signed-out", 1920, 1080, false, com.sokoul.pc.account.AccountView.SignedOut(), false),
            Shot("server", 1920, 1080, false, com.sokoul.pc.account.AccountView.NeedsServer(""), false),
            Shot("tablet-linking", 1600, 2560, true, com.sokoul.pc.account.AccountView.Linking(code), true),
            Shot("tablet-signed-in", 1600, 2560, true, com.sokoul.pc.account.AccountView.SignedIn(user, tabletDevices), true),
            Shot("tablet-sign-in", 1600, 2560, true, com.sokoul.pc.account.AccountView.SignIn(problem = com.sokoul.pc.account.Problems.forCode("invalid_credentials")), true),
        ).filter { wanted == null || it.name in wanted }
        for (language in listOf("fr", "en")) {
            com.sokoul.pc.i18n.Strings.applyLanguage(language)
            for (shot in shots) {
                com.sokoul.pc.ui.Form.tablet = shot.portrait
                com.sokoul.pc.ui.Form.tabletPortrait = shot.portrait
                val model = com.sokoul.pc.account.AccountPanelModel(
                    account, CoroutineScope(Dispatchers.Unconfined), shot.keyboard, if (shot.portrait) "Galaxy Tab S7 FE" else "Google TV Streamer",
                    serverPc = false, clock = { now },
                    kind = if (shot.portrait) com.sokoul.pc.account.DeviceKind.Tablet else com.sokoul.pc.account.DeviceKind.Tv,
                )
                model.preview(shot.view)
                val s = ImageComposeScene(shot.w, shot.h, Density(1f)) {
                    ScaledRoot { com.sokoul.pc.account.AccountScreen(model, onBack = {}, backButton = shot.portrait, openOnStart = false) }
                }
                var t = 0L
                repeat(12) { s.render(t); t += 50_000_000L }
                save("account-panel_${shot.name}_$language", s, t)
                s.close()
            }
        }
        com.sokoul.pc.ui.Form.tablet = false
        com.sokoul.pc.ui.Form.tabletPortrait = false
        kotlin.system.exitProcess(0)
    }

    // "intro": the animated intro (Figma "breaking the evil eye"), one picture per key moment.
    if (args.getOrNull(1) == "intro") {
        val s = ImageComposeScene(1920, 1080, Density(1f)) { ScaledRoot { com.sokoul.pc.startup.SokoulIntro() } }
        var at = 0L
        for (ms in listOf(300L, 700L, 1000L, 1300L, 1440L, 1520L, 1650L, 1900L, 2200L, 2700L)) {
            while (at < ms) { at += 16; s.render(at * 1_000_000L) }
            save("intro_%04d".format(ms), s, at * 1_000_000L)
        }
        s.close()
        kotlin.system.exitProcess(0)
    }

    // "settings": every category of the settings page, in a throwaway database.
    if (args.getOrNull(1) == "settings") {
        val file = File.createTempFile("render-settings", ".db")
        DesktopDb(file).use { db ->
            val prefs = com.sokoul.desktop.data.DesktopPrefs(db)
            prefs.accent = com.sokoul.desktop.data.AccentColor.BLUE
            val hooks = com.sokoul.pc.library.SettingsHooks(
                menu = listOf("home" to "Accueil", "library" to "Ma liste", "movies" to "Films", "series" to "Séries", "tv" to "TV"),
                services = {
                    listOf("TMDB" to "Clé intégrée", "Real-Debrid" to "Clé intégrée", "OMDb" to "Clé saisie", "fanart.tv" to null, "OpenSubtitles" to "Clé intégrée", "IPTV" to "Configuré")
                        .map { (n, src) -> com.sokoul.pc.library.ServiceInfo(n, src != null, src) }
                },
            )
            for (pane in listOf("accounts", "appearance", "subtitles", "playback", "about")) {
                val s = ImageComposeScene(1920, 1080, Density(1f)) {
                    ScaledRoot { com.sokoul.pc.library.SettingsScreen(androidx.compose.ui.focus.FocusRequester(), prefs, {}, hooks = hooks, initialPane = pane) }
                }
                var t = 0L
                repeat(12) { s.render(t); t += 50_000_000L }
                save("settings_$pane", s, t)
                s.close()
            }
        }
        file.delete()
        kotlin.system.exitProcess(0)
    }

    // "languages": the playback and language settings and the player's language notice in every shipped language, to see
    // long words (German) and right to left (Arabic), in a throwaway database.
    if (args.getOrNull(1) == "languages") {
        val file = File.createTempFile("render-languages", ".db")
        DesktopDb(file).use { db ->
            val prefs = com.sokoul.desktop.data.DesktopPrefs(db)
            prefs.audioIntent = com.sokoul.desktop.data.AudioIntent(listOf("it", com.sokoul.desktop.data.AudioIntent.ORIGINAL))
            for (language in com.sokoul.pc.i18n.InterfaceLanguages.all) {
                com.sokoul.pc.i18n.Strings.applyLanguage(language.code)
                for (pane in listOf("playback", "language")) {
                    val s = ImageComposeScene(1920, 1080, Density(1f)) {
                        ScaledRoot { com.sokoul.pc.library.SettingsScreen(androidx.compose.ui.focus.FocusRequester(), prefs, {}, initialPane = pane) }
                    }
                    var t = 0L
                    repeat(12) { s.render(t); t += 50_000_000L }
                    save("lang_${language.code}_$pane", s, t)
                    s.close()
                }
                val n = ImageComposeScene(1920, 1080, Density(1f)) {
                    ScaledRoot {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A3F4B))) {
                            com.sokoul.pc.playback.ui.LanguageNoticeBanner(
                                com.sokoul.pc.playback.session.LanguageNotice("it", "Track 1 - [English]"), {}, {},
                                Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top = 44.dp, end = 60.dp),
                                androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() },
                            )
                        }
                    }
                }
                var nt = 0L
                repeat(12) { n.render(nt); nt += 50_000_000L }
                save("lang_${language.code}_notice", n, nt)
                n.close()
            }
        }
        file.delete()
        kotlin.system.exitProcess(0)
    }

    // "rows": the home rows editor, with one row hidden and one moved, in a throwaway database.
    if (args.getOrNull(1) == "rows") {
        val file = File.createTempFile("render-rows", ".db")
        DesktopDb(file).use { db ->
            val prefs = com.sokoul.desktop.data.DesktopPrefs(db)
            val plan = com.sokoul.pc.data.PagePlans.of(com.sokoul.pc.data.PageKind.Home)
            prefs.homeRowsJson = com.sokoul.pc.data.HomeLayout().toggle(plan[1].id).move(plan, plan[3].id, -2).encode()
            val s = ImageComposeScene(1920, 1080, Density(1f)) { ScaledRoot { com.sokoul.pc.library.HomeRowsScreen(prefs, {}, {}) } }
            var t = 0L
            repeat(20) { s.render(t); t += 50_000_000L }
            save("rows_1080", s, t)
            s.close()
            // The audio languages editor: an Italian viewer who falls back on the original, in both interface languages.
            prefs.audioIntent = com.sokoul.desktop.data.AudioIntent(listOf("it", com.sokoul.desktop.data.AudioIntent.ORIGINAL, "en"))
            for (lang in listOf("fr", "en")) {
                com.sokoul.pc.i18n.Strings.applyLanguage(lang)
                val a = ImageComposeScene(1920, 1080, Density(1f)) { ScaledRoot { com.sokoul.pc.library.AudioLanguagesScreen(prefs, {}) } }
                var at = 0L
                repeat(20) { a.render(at); at += 50_000_000L }
                save("audio_languages_$lang", a, at)
                a.close()
            }
        }
        file.delete()
        kotlin.system.exitProcess(0)
    }

    // "profiles": the profile picker with sample profiles, in a throwaway database.
    if (args.getOrNull(1) == "profiles") {
        val file = File.createTempFile("render-profiles", ".db")
        DesktopDb(file).use { db ->
            val profiles = com.sokoul.pc.data.Profiles(db)
            profiles.add("Anna", kids = false)
            profiles.add("Léo", kids = true)
            val s = ImageComposeScene(1920, 1080, Density(1f)) { ScaledRoot { com.sokoul.pc.startup.ProfilesScreen(profiles) {} } }
            var t = 0L
            repeat(20) { s.render(t); t += 50_000_000L }
            save("profiles_1080", s, t)
            s.close()
        }
        file.delete()
        kotlin.system.exitProcess(0)
    }

    // "loading": the home page in its first instants, before the catalog answers (loading shapes), then the shapes alone.
    if (args.getOrNull(1) == "loading") {
        ProviderConfig.builtIn = realKeys
        val file = File.createTempFile("render-loading", ".db")
        DesktopDb(file).use { db ->
            StartupRoute.set(db, SetupState.Done)
            val s = scene(fakeDeps(db))
            var t = 0L
            repeat(3) { s.render(t); t += 16_000_000L }
            save("loading_home_first_frames", s, t)
            s.close()
        }
        file.delete()
        val shapes = ImageComposeScene(1920, 1080, Density(1f)) {
            ScaledRoot {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.background(com.sokoul.pc.theme.Palette.Background)) {
                    com.sokoul.pc.ui.SkeletonHero(androidx.compose.ui.Modifier.height(620.dp))
                    com.sokoul.pc.ui.SkeletonRow(landscape = true)
                }
            }
        }
        save("loading_shapes", shapes, 0L)
        shapes.close()
        kotlin.system.exitProcess(0)
    }

    // "phone": every section as a phone shows it, upright then sideways (Pixel-like 1080x2400). Optional third argument:
    // a comma-separated list of sections; "title" opens the first title of Home.
    if (args.getOrNull(1) == "phone") {
        ProviderConfig.builtIn = realKeys
        val file = File.createTempFile("render-phone", ".db")
        val wanted = (args.getOrNull(2) ?: System.getenv("SOKOUL_RENDER_SECTIONS"))?.split(',') ?: listOf("home", "title", "library", "movies", "series", "anime", "collections", "tv", "search", "settings")
        DesktopDb(file).use { db ->
            StartupRoute.set(db, SetupState.Done)
            val deps = fakeDeps(db)
            for ((form, size) in listOf(com.sokoul.pc.ui.FormFactor.PhonePortrait to (1080 to 2400), com.sokoul.pc.ui.FormFactor.PhoneLandscape to (2400 to 1080))) {
                com.sokoul.pc.ui.Form.current = form
                val (w, h) = size
                val s = scene(deps, w, h)
                var t = 0L
                fun pump(seconds: Int) = repeat(seconds * 4) { s.render(t); t += 250_000_000L; Thread.sleep(250) }
                val tag = if (form == com.sokoul.pc.ui.FormFactor.PhonePortrait) "portrait" else "landscape"
                pump(15)
                for (section in wanted) {
                    val nav = deps.bridge.navigation ?: continue
                    while (nav.pages.isNotEmpty()) { nav.back(); pump(1) }
                    if (section == "title") {
                        nav.goTo("home"); pump(4)
                        val item = com.sokoul.pc.dev.RenderSample.firstTitle(deps) ?: continue
                        nav.openTitle(listOf(item), 0)
                        pump(6)
                    } else {
                        nav.goTo(section)
                        pump(6)
                    }
                    save("phone_${tag}_$section", s, t)
                }
                s.close()
            }
        }
        file.delete()
        kotlin.system.exitProcess(0)
    }

    // "hero": the home page's hero alone, in windows of other shapes than the TV's (full height, text in proportion).
    if (args.getOrNull(1) == "hero") {
        ProviderConfig.builtIn = realKeys
        val file = File.createTempFile("render-hero", ".db")
        DesktopDb(file).use { db ->
            StartupRoute.set(db, SetupState.Done)
            val deps = fakeDeps(db)
            for ((w, h) in listOf(1920 to 1080, 1440 to 900, 1280 to 960)) {
                val s = scene(deps, w, h)
                var t = 0L
                repeat(40) { s.render(t); t += 250_000_000L; Thread.sleep(250) }
                save("hero_${w}x$h", s, t)
                s.close()
            }
        }
        file.delete()
        return
    }

    val prefilled: (DesktopDb) -> Unit = { db ->
        ProviderConfig.setKey(db, "tmdb", "sample-key-0000")
        ProviderConfig.setKey(db, "realdebrid", "sample-key-1111")
    }
    setupRender("splash_1080", 1920, 1080, {}, splash = true)
    setupRender("setup_prefilled_1080", 1920, 1080, prefilled)
    setupRender("main_deferred_1080", 1920, 1080, { StartupRoute.set(it, SetupState.Deferred) })

    // Catalog screens: real keys, separate throwaway database (nothing touches %APPDATA%\SokoulPC).
    ProviderConfig.builtIn = realKeys
    val file = File.createTempFile("render-catalog", ".db")
    DesktopDb(file).use { db ->
        StartupRoute.set(db, SetupState.Done)
        // The app applies the saved interface language when it starts: save the one asked for (-PrenderLanguage).
        System.getProperty("sokoul.renderLanguage")?.let { com.sokoul.desktop.data.DesktopPrefs(db).language = it }
        val deps = fakeDeps(db)
        val s = scene(deps)
        var t = 0L

        fun pump(seconds: Int) {
            repeat(seconds * 4) {
                s.render(t)
                t += 250_000_000L
                Thread.sleep(250)
            }
        }

        fun key(k: Key) {
            s.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(k, KeyEventType.KeyDown))
            s.render(t)
            s.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(k, KeyEventType.KeyUp))
            s.render(t)
        }

        pump(10)
        save("home_top_1080", s, t)
        key(Key.DirectionDown)
        pump(3)
        save("home_rows_1080", s, t)
        key(Key.DirectionDown)
        pump(2)
        key(Key.DirectionRight)
        pump(2)
        save("home_focus_1080", s, t)
        key(Key.Enter)
        pump(1)
        save("title_transition_1080", s, t)
        pump(4)
        save("title_compact_1080", s, t)
        key(Key.DirectionDown)
        pump(2)
        save("title_full_1080", s, t)
        key(Key.DirectionDown)
        pump(3)
        save("title_rows_1080", s, t)
        s.close()
    }
    file.delete()
    kotlin.system.exitProcess(0)
}

/**
 * The build's keys, completed with the ones saved in the PC app's own database when the build has none (no
 * local.properties on this machine): opened read-only, kept in memory, never printed.
 */
private fun ownerKeys(built: () -> Map<String, String>): () -> Map<String, String> {
    val saved: Map<String, String> by lazy {
        val db = File(System.getenv("APPDATA") ?: return@lazy emptyMap(), "SokoulPC/sokoul.db")
        if (!db.isFile) return@lazy emptyMap()
        runCatching {
            java.sql.DriverManager.getConnection("jdbc:sqlite:file:${db.absolutePath.replace('\\', '/')}?mode=ro").use { conn ->
                conn.prepareStatement("SELECT key, value FROM settings WHERE key LIKE 'api_key_%'").use { st ->
                    st.executeQuery().use { rs ->
                        buildMap { while (rs.next()) rs.getString(2)?.takeIf { it.isNotBlank() }?.let { put(rs.getString(1).removePrefix("api_key_"), it) } }
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }
    return { saved + built().filterValues { it.isNotBlank() } }
}

/** A title for the phone renders: the first trending film, read with the render's own key. */
internal object RenderSample {
    fun firstTitle(deps: AppDeps): com.sokoul.iptv.data.net.TmdbItem? =
        kotlinx.coroutines.runBlocking { deps.catalog.hero(com.sokoul.pc.data.PageKind.Home).firstOrNull()?.item }
}
