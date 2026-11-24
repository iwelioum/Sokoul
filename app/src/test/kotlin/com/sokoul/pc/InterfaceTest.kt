package com.sokoul.pc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.Resume
import com.sokoul.pc.data.MyList
import com.sokoul.pc.data.TrailerResolver
import com.sokoul.pc.data.TrailerStream
import com.sokoul.pc.data.YtDlp
import com.sokoul.pc.i18n.Strings
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.playback.session.PlayRequest

import com.sokoul.pc.playback.session.TrailerController
import com.sokoul.pc.playback.ui.clockLabel
import com.sokoul.pc.search.SearchTab
import com.sokoul.pc.search.filterResults
import com.sokoul.pc.startup.SetupState
import com.sokoul.pc.startup.StartupRoute
import com.sokoul.pc.ui.DominantColor
import com.sokoul.pc.ui.scaledDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

class InterfaceTest {
    private lateinit var dir: File
    private lateinit var db: DesktopDb

    @Before
    fun setUp() {
        ProviderConfig.builtIn = { emptyMap() }
        dir = Files.createTempDirectory("sokoul-ui").toFile()
        db = DesktopDb(File(dir, "t.db"))
        StartupRoute.set(db, SetupState.Done)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private val validator get() = com.sokoul.pc.data.CredentialValidator(OkHttpClient())

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val request = PlayRequest("movie", 1, null, null, "Title", null, null)

    private fun waitUntil(what: String, check: () -> Boolean) = runBlocking {
        withTimeout(5_000) {
            while (!check()) delay(20)
        }
        assertTrue(what, check())
    }

    // --- Scaling and color

    @Test
    fun one_dp_is_one_reference_pixel_at_any_width() {
        assertEquals(1f, scaledDensity(1920).density, 0.001f)
        assertEquals(2560f / 1920f, scaledDensity(2560).density, 0.001f)
    }

    @Test
    fun dominant_color_follows_saturated_pixels() {
        val c = DominantColor.fromPixels(10, 10) { x, _ -> if (x < 5) Color(0xFFD02020) else Color(0xFF808080) }
        assertTrue("red should win: $c", c.red > c.green && c.red > c.blue)
    }

    // --- Catalog

    @Test
    fun titles_without_poster_or_twice_are_dropped() {
        val a = sampleItem(1).copy(poster = "p")
        assertEquals(listOf(a), com.sokoul.pc.data.usableTitles(listOf(a, a, sampleItem(2))))
    }

    @Test
    fun rows_and_moods_exist_in_both_languages() {
        val all = com.sokoul.pc.data.RowBook.movies + com.sokoul.pc.data.RowBook.series + com.sokoul.pc.data.Mood.entries.flatMap { listOf(it.spec("movie"), it.spec("tv")) }
        assertTrue(all.size > 80)
        all.forEach { assertTrue(it.id, it.title.en.isNotBlank() && it.title.fr.isNotBlank() && it.tagline.en.isNotBlank() && it.tagline.fr.isNotBlank()) }
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }

    @Test
    fun every_page_has_moods_categories_and_no_duplicate_row() {
        com.sokoul.pc.data.PageKind.entries.forEach { kind ->
            val plan = com.sokoul.pc.data.PagePlans.of(kind)
            assertEquals(kind.name, plan.size, plan.map { it.id }.distinct().size)
            // The Anime page has its own genres (AnimeGenres) in place of moods and categories.
            if (kind == com.sokoul.pc.data.PageKind.Anime) {
                assertTrue(plan.any { it is com.sokoul.pc.data.PageRow.AnimeGenres })
            } else {
                assertTrue(kind.name, plan.any { it is com.sokoul.pc.data.PageRow.Moods })
                assertTrue(kind.name, plan.last() is com.sokoul.pc.data.PageRow.Categories)
            }
        }
        assertTrue(com.sokoul.pc.data.PagePlans.series.none { it is com.sokoul.pc.data.PageRow.Sagas })
    }

    @Test
    fun row_query_becomes_discover_parameters() {
        val q = com.sokoul.pc.data.TitleQuery(genres = setOf(28, 53), noGenres = setOf(16), minRating = 7.0, minVotes = 100, minYear = 1990, maxYear = 1999, language = "ko")
        val movie = com.sokoul.pc.data.RowQueries.params(q, "movie", listOf(5, 6))
        assertEquals("28|53", movie["with_genres"])
        assertEquals("16", movie["without_genres"])
        assertEquals("5|6", movie["with_keywords"])
        assertEquals("1990-01-01", movie["primary_release_date.gte"])
        assertEquals("1999-12-31", com.sokoul.pc.data.RowQueries.params(q, "tv", emptyList())["first_air_date.lte"])
        assertEquals("ko", movie["with_original_language"])
        assertEquals("2", com.sokoul.pc.data.RowQueries.params(com.sokoul.pc.data.TitleQuery(miniSeries = true), "tv", emptyList())["with_type"])
    }

    @Test
    fun story_order_rearranges_and_release_order_follows_the_years() {
        val a = sampleItem(1, "movie").copy(year = 1977)
        val b = sampleItem(2, "movie").copy(year = 1980)
        val c = sampleItem(3, "movie").copy(year = 1999)
        val d = sampleItem(4, "movie").copy(year = 2005)
        val T = com.sokoul.pc.data.SagaTimeline
        assertEquals(listOf(1, 2, 3, 4), T.ordered(listOf(c, a, d, b), listOf(3, 1, 2), story = false).map { it.id })
        assertEquals("a film the order doesn't list comes after", listOf(3, 1, 2, 4), T.ordered(listOf(a, b, c, d), listOf(3, 1, 2), story = true).map { it.id })
        assertEquals("no story data keeps release order", listOf(1, 2), T.ordered(listOf(b, a), emptyList(), story = true).map { it.id })
    }

    @Test
    fun every_saga_is_listed_once_and_curated_ones_are_not_repeated() {
        val all = com.sokoul.pc.data.AllSagas.all
        assertTrue("found ${all.size}", all.size > 500)
        assertEquals(all.size, all.map { it.id }.distinct().size)
        val curated = com.sokoul.desktop.data.CuratedSagas.all.flatMap { it.collectionIds }.toSet()
        assertTrue(all.none { it.id in curated })
        assertTrue(all.all { it.films >= 2 })
    }

    @Test
    fun an_actors_page_lists_each_work_once_best_known_first() {
        val known = sampleItem(1, "movie").copy(poster = "p", voteCount = 900)
        val other = sampleItem(2, "tv").copy(poster = "p", voteCount = 10)
        val person = com.sokoul.iptv.data.net.TmdbPersonInfo(5, "A", null, null, null, null, null, 0, null, listOf(other, known, sampleItem(3, "movie")), listOf(known))
        assertEquals(listOf(1, 2), com.sokoul.pc.data.PersonCredits.of(person).map { it.id })
    }

    @Test
    fun platforms_have_their_own_productions() {
        com.sokoul.pc.data.Platform.entries.forEach { assertTrue(it.name, it.networks.isNotBlank()) }
        assertTrue(com.sokoul.pc.data.PagePlans.movies.filterIsInstance<com.sokoul.pc.data.PageRow.Titles>().filter { it.id.startsWith("plat-") }
            .all { (it.source as com.sokoul.pc.data.RowSource.OnPlatform).platform.companies != null })
    }

    @Test
    fun the_main_saga_button_follows_what_was_watched() {
        val a = sampleItem(1, "movie").copy(year = 2000)
        val b = sampleItem(2, "movie").copy(year = 2001)
        val T = com.sokoul.pc.data.SagaTimeline
        val films = listOf(a, b)
        assertEquals(com.sokoul.pc.data.SagaAction.Start, T.action(films, emptySet(), null, T.next(films, emptySet())))
        assertEquals(com.sokoul.pc.data.SagaAction.Next(b), T.action(films, setOf(1), null, T.next(films, setOf(1))))
        assertEquals(com.sokoul.pc.data.SagaAction.Resume(a), T.action(films, setOf(2), a, T.next(films, setOf(2))))
        assertEquals(com.sokoul.pc.data.SagaAction.Rewatch, T.action(films, setOf(1, 2), null, T.next(films, setOf(1, 2))))
        val future = sampleItem(3, "movie").copy(year = 3000)
        assertNull("an unreleased film is never up next", T.next(listOf(future), emptySet()))
    }

    @Test
    fun english_is_the_base_for_names_and_regions() {
        assertEquals("Wizarding", Strings.pick("Wizarding", "Sorciers", Locale.US))
        assertEquals("Sorciers", Strings.pick("Wizarding", "Sorciers", Locale.FRANCE))
        assertEquals("FR", Strings.region(Locale.FRANCE))
        assertEquals("US", Strings.region(Locale.ENGLISH))
        val sagas = com.sokoul.desktop.data.CuratedSagas.all
        assertEquals("Wizarding World", sagas.first { it.key == "wizarding-world" }.name)
        assertEquals("Le monde des sorciers", sagas.first { it.key == "wizarding-world" }.nameFr)
        assertTrue(sagas.count { it.logo != null } >= 24)
    }

    @Test
    fun catalog_language_follows_the_system() {
        assertEquals("fr-FR", Strings.catalogLanguage(Locale.FRANCE))
        assertEquals("en-US", Strings.catalogLanguage(Locale.US))
    }

    // --- Library

    @Test
    fun list_survives_a_restart() {
        val first = MyList(db)
        first.toggle(sampleItem(7, "movie"))
        val again = MyList(db)
        assertTrue(again.contains(sampleItem(7, "movie")))
        again.toggle(sampleItem(7, "movie"))
        assertFalse(MyList(db).contains(sampleItem(7, "movie")))
    }

    // --- Search

    @Test
    fun tabs_filter_by_type() {
        val all = listOf(sampleItem(1, "movie"), sampleItem(2, "tv"))
        assertEquals(1, filterResults(all, SearchTab.Movies).single().id)
        assertEquals(2, filterResults(all, SearchTab.Series).single().id)
        assertEquals(2, filterResults(all, SearchTab.Popular).size)
    }

    // --- Trailers

    @Test
    fun only_a_checked_id_reaches_yt_dlp_or_the_browser() {
        assertTrue(YtDlp.isValidKey("dQw4w9WgXcQ"))
        listOf("", "--exec=calc", "a b c d e f", "../../etc/passwd", "x;calc.exe", "https://evil.test").forEach {
            assertFalse(it, YtDlp.isValidKey(it))
            assertNull(it, trailerWebUrl(it))
        }
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", trailerWebUrl("dQw4w9WgXcQ"))
    }

    @Test
    fun yt_dlp_output_gives_separate_video_and_audio() {
        val s = YtDlp.parse("https://v.test/video\nhttps://a.test/audio\n")!!
        assertEquals("https://v.test/video", s.videoUrl)
        assertEquals("https://a.test/audio", s.audioUrl)
        assertNull(YtDlp.parse("ERROR: nothing"))
        assertNull(YtDlp.parse("file:///c:/windows"))
    }

    @Test
    fun yt_dlp_output_gives_the_real_height() {
        assertEquals(1080, YtDlp.parse("1080\nhttps://v.test/video\nhttps://a.test/audio\n")?.height)
        assertEquals("https://v.test/video", YtDlp.parse("NA\nhttps://v.test/video\n")?.videoUrl)
    }

    @Test
    fun a_scope_trailer_in_full_hd_is_good_enough_though_only_800_high() = runBlocking {
        val parsed = YtDlp.parse("800\n1920\nhttps://v.test/video\nhttps://a.test/audio\n")!!
        assertEquals(800, parsed.height)
        assertEquals(1080, parsed.sharpness)
        val asked = mutableListOf<String>()
        val resolver = TrailerResolver(OkHttpClient(), { null }, { key -> asked += key; parsed })
        resolver.best(listOf("scopevideo", "otherone"))
        assertEquals(listOf("scopevideo"), asked)
    }

    @Test
    fun a_low_quality_video_gives_way_to_a_better_one() = runBlocking {
        val heights = mapOf("low" to 360, "good" to 1080, "other" to 1080)
        val asked = mutableListOf<String>()
        val resolver = TrailerResolver(OkHttpClient(), { null }, { key ->
            asked += key
            TrailerStream("https://v.test/$key", null, "yt-dlp", heights.getValue(key))
        })
        assertEquals("good", resolver.best(listOf("low", "good", "other"))?.key)
        assertEquals("stops at the first good one", listOf("low", "good"), asked)
    }

    @Test
    fun only_low_quality_keeps_the_best_available() = runBlocking {
        val heights = mapOf("a" to 240, "b" to 480)
        val resolver = TrailerResolver(OkHttpClient(), { null }, { key -> TrailerStream("https://v.test/$key", null, "yt-dlp", heights.getValue(key)) })
        assertEquals("b", resolver.best(listOf("a", "b"))?.key)
    }

    @Test
    fun missing_yt_dlp_is_not_fatal() {
        assertNull(YtDlp(executable = { null }).streams("dQw4w9WgXcQ"))
    }

    @Test
    fun real_debrid_stands_in_when_yt_dlp_cannot() = runBlocking {
        val server = MockWebServer().also { it.start() }
        server.enqueue(MockResponse().setBody("""{"download":"https://rd.test/video.mp4"}"""))
        val fine = TrailerResolver(OkHttpClient(), { "token" }, { null }, server.url("/").toString().trimEnd('/'))
        assertEquals("real-debrid", fine.resolve("dQw4w9WgXcQ")?.method)
        // Cached: no second call to the service.
        fine.resolve("dQw4w9WgXcQ")
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"hoster_unavailable"}"""))
        val refused = TrailerResolver(OkHttpClient(), { "token" }, { null }, server.url("/").toString().trimEnd('/'))
        assertNull(refused.resolve("aaaaaaaaaaa"))
        server.shutdown()
    }

    @Test
    fun trailer_waits_then_plays_with_sound_settings_untouched_and_stops() = runBlocking {
        val engine = FakeEngine()
        val trailer = TrailerController(engine, { _: List<String> -> TrailerStream("https://v.test/x", "https://a.test/y", "yt-dlp", 1080) }, scope(), delayMs = 150)
        trailer.prepare("dQw4w9WgXcQ", "T")
        assertTrue("must wait before starting", engine.played.isEmpty())
        waitUntil("trailer started") { trailer.playing }
        assertEquals("https://a.test/y", engine.played.single().audioUrl)
        trailer.stop()
        assertFalse(trailer.playing)
        assertEquals("stop", engine.calls.last())
    }

    @Test
    fun a_trailer_that_ends_at_once_gives_way_to_the_next() = runBlocking {
        val engine = FakeEngine()
        val trailer = TrailerController(engine, { keys: List<String> -> TrailerStream("https://v.test/${keys.first()}", null, "yt-dlp", 1080, key = keys.first()) }, scope(), delayMs = 10)
        trailer.prepare(listOf("brokenlink", "goodvideo1"), "T")
        val end = System.currentTimeMillis() + 3_000
        while (engine.played.isEmpty() && System.currentTimeMillis() < end) delay(10)
        delay(100)
        engine.report(com.sokoul.desktop.player.PlayerStatus(ended = true, timeMs = 300, session = engine.currentSession))
        while (engine.played.size < 2 && System.currentTimeMillis() < end) delay(10)
        assertEquals("https://v.test/goodvideo1", engine.played.last().url)
        assertEquals("not counted as played to its end", 0, trailer.finishedCount)
        trailer.stop()
    }

    @Test
    fun a_trailer_below_full_hd_never_plays_by_itself() = runBlocking {
        val engine = FakeEngine()
        val trailer = TrailerController(engine, { TrailerStream("https://v.test/x", null, "yt-dlp", 720, key = "softvideo1") }, scope(), delayMs = 10)
        trailer.prepare(listOf("softvideo1"), "Soft")
        val end = System.currentTimeMillis() + 1_000
        while (trailer.pending && System.currentTimeMillis() < end) delay(10)
        assertEquals(false, trailer.pending)
        assertEquals(0, engine.played.size)
        // A scope trailer 1920x800 is full HD.
        assertEquals(1080, TrailerStream("https://v.test/x", null, "yt-dlp", 800, width = 1920).sharpness)
    }

    @Test
    fun a_page_below_cannot_stop_the_trailer_of_the_page_on_top() = runBlocking {
        val engine = FakeEngine()
        val trailer = TrailerController(engine, { TrailerStream("https://v.test/x", null, "yt-dlp", 1080) }, scope(), delayMs = 10)
        val below = Any()
        val top = Any()
        trailer.prepare(listOf("belowvideo"), "Below", below)
        trailer.prepare(listOf("topvideo01"), "Top", top)
        trailer.release(below)
        val end = System.currentTimeMillis() + 3_000
        while (engine.played.isEmpty() && System.currentTimeMillis() < end) delay(10)
        assertEquals(1, engine.played.size)
        trailer.release(top)
        delay(50)
        assertEquals(false, trailer.playing)
    }

    @Test
    fun a_saga_keeps_its_plain_name_to_find_its_page() {
        assertEquals("Moi, moche et méchant", com.sokoul.pc.data.SagaNames.plain("Moi, moche et méchant - Saga"))
        assertEquals("Doom", com.sokoul.pc.data.SagaNames.plain("Doom Collection"))
    }

    @Test
    fun interaction_before_the_delay_cancels_the_trailer() = runBlocking {
        val engine = FakeEngine()
        val trailer = TrailerController(engine, { TrailerStream("https://v.test/x", null, "yt-dlp", 1080) }, scope(), delayMs = 300)
        trailer.prepare("dQw4w9WgXcQ", "T")
        trailer.stop()
        delay(500)
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun no_trailer_keeps_the_image() = runBlocking {
        val engine = FakeEngine()
        val trailer = TrailerController(engine, { _: List<String> -> null }, scope(), delayMs = 10)
        trailer.prepare("dQw4w9WgXcQ", "T")
        delay(150)
        assertFalse(trailer.playing)
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun clock_labels() {
        assertEquals("00:24", clockLabel(24_000))
        assertEquals("1:02:03", clockLabel(3_723_000))
    }

    // --- Interface

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun home_focuses_the_main_button_and_arrows_reach_the_rows() = runComposeUiTest {
        val withHero = FakeCatalog(hero = listOf(com.sokoul.pc.data.HeroItem(sampleItem(100), sampleInfo(100))))
        setContent { SokoulPcApp(testDeps(db, validator, withHero), skipSplash = true) }
        waitUntil { onAll("hero-watch") }
        onNodeWithTag("hero-watch").assertIsFocused()
        onNodeWithTag("hero-watch").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("card-top-series-0").assertIsFocused()
        onNodeWithTag("card-top-series-0").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag("card-top-series-1").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hover_gives_the_same_focus_as_the_arrows() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) }
        waitUntil { onAll("card-top-series-2") }
        onNodeWithTag("card-top-series-2").performMouseInput { moveTo(Offset(40f, 40f)) }
        waitForIdle()
        onNodeWithTag("card-top-series-2").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun catalog_without_key_shows_base_icons_only() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog(on = false)), skipSplash = true) }
        waitForIdle()
        onNodeWithTag("main-empty").assertExists()
        onNodeWithTag("home").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun card_opens_the_title_compact_then_full_and_escape_returns_to_the_card() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) }
        waitUntil { onAll("card-top-series-1") }
        onNodeWithTag("card-top-series-1").performClick()
        waitForIdle()
        onNodeWithTag("title").assertExists()
        onNodeWithTag("neighbor-left").assertExists()
        onNodeWithTag("title").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("title-play").assertIsFocused()
        onNodeWithTag("title-play").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("title").assertDoesNotExist()
        onNodeWithTag("card-top-series-1").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun arrows_change_title_in_compact_mode() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) }
        waitUntil { onAll("card-top-series-0") }
        onNodeWithTag("card-top-series-0").performClick()
        waitForIdle()
        onNodeWithTag("neighbor-left").assertDoesNotExist()
        onNodeWithTag("title").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        onNodeWithTag("neighbor-left").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun modal_opens_and_escape_returns_focus_to_its_button() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) }
        waitUntil { onAll("card-top-series-0") }
        onNodeWithTag("card-top-series-0").performClick()
        waitForIdle()
        onNodeWithTag("title").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("title-doc").performClick()
        waitForIdle()
        onNodeWithTag("modal").assertExists()
        onNodeWithTag("modal").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("modal").assertDoesNotExist()
        onNodeWithTag("title-doc").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun plus_adds_the_title_to_the_library() = runComposeUiTest {
        val deps = testDeps(db, validator, FakeCatalog())
        setContent { SokoulPcApp(deps, skipSplash = true) }
        waitUntil { onAll("card-top-series-0") }
        onNodeWithTag("card-top-series-0").performClick()
        waitForIdle()
        onNodeWithTag("title").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("title-list").performClick()
        waitForIdle()
        assertTrue(deps.myList.contains(sampleItem(1)))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun play_without_source_shows_the_message_and_keeps_navigation() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog(), engine = FakeEngine()), skipSplash = true) }
        waitUntil { onAll("card-top-series-0") }
        onNodeWithTag("card-top-series-0").performClick()
        waitForIdle()
        onNodeWithTag("title").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag("title-play").performClick()
        waitUntil { onAll("player-error") }
        onNodeWithTag("player").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("title").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test(timeout = 60_000)
    fun menu_reaches_search_library_and_settings() = runComposeUiTest {
        // Scaled as Main shows it: unscaled in the small test window, the centred tabs cover the avatar that opens Settings.
        setContent { com.sokoul.pc.ui.ScaledRoot { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) } }
        // Apple TV bar: the pages are in the menu behind the left icon, search on the right.
        waitUntil { onAll("menu-open") }
        onNodeWithTag("menu-open").performClick()
        waitUntil { onAll("menu-library") }
        onNodeWithTag("menu-library").performClick()
        waitForIdle()
        onNodeWithTag("library-empty").assertExists()
        onNodeWithTag("menu-open").performClick()
        waitUntil { onAll("menu-settings") }
        onNodeWithTag("menu-settings").performClick()
        waitForIdle()
        assertTrue(tagsOnScreen().toString(), onAll("settings"))
        onNodeWithTag("menu-search").performClick()
        waitForIdle()
        onNodeWithTag("search").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tv_group_picker_saves_multiple_channels_in_an_existing_collection() = runComposeUiTest {
        val playlist = db.upsertPlaylist(com.sokoul.desktop.data.Playlist(0, "Test", com.sokoul.desktop.data.PlaylistType.M3U,
            "https://example.test/playlist", null, null, null, null))
        val channels = listOf("sport", "culture", "news").mapIndexed { index, id ->
            com.sokoul.desktop.data.Channel(playlist, id, index + 1, id, null, "provider-group", null,
                id, "https://example.test/$id", null, null, index)
        }
        db.replaceLive(playlist, listOf(com.sokoul.desktop.data.Category(playlist, "provider-group", "Provider group", 0)), channels)
        val deps = testDeps(db, validator, FakeCatalog())
        val collection = deps.personalTv.create("Mine")
        val groups = deps.personalTv.groups()
        setContent {
            com.sokoul.pc.ui.ScaledRoot {
                com.sokoul.pc.live.GroupChannelPicker(deps, groups, com.sokoul.pc.live.GroupChannelRequest("provider-group"),
                    onClose = {}, onSearch = null, onSaved = { _, _ -> })
            }
        }
        waitUntil { onAll("tv-group-picker-all") }
        onNodeWithTag("tv-group-picker-all").performClick()
        onNodeWithTag("tv-group-picker-channel-news").performClick()
        onNodeWithTag("tv-group-picker-continue").performClick()
        waitUntil { onAll("tv-group-picker-dest-$collection") }
        onNodeWithTag("tv-group-picker-dest-$collection").performClick()
        waitUntil { deps.personalTv.state().collections.single().members.size == 2 }
        assertEquals(setOf("sport", "culture"), deps.personalTv.state().collections.single().members.map { it.channelId }.toSet())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @org.junit.Ignore("The old Streamed sports page (SportsScreen) is no longer shown: Sport opens SportHome (ESPN) since 2026-11-23. Covered by SportScoresTest and SportGuideMatchTest.")
    fun tv_opens_the_sports_page_with_filters_and_returns_to_the_guide() = runComposeUiTest {
        MockWebServer().use { server ->
            val serverKey = "a".repeat(24)
            server.enqueue(MockResponse().setBody("""{"metas":[{"id":"streamed_match","name":"Test match","_sport":"Football","_isLive":true},{"id":"streamed_race","name":"Test race","_sport":"Racing"}]}"""))
            server.enqueue(MockResponse().setBody("""{"streams":[{"_streamedKey":"$serverKey","title":"Test server"}]}"""))
            server.enqueue(MockResponse().setResponseCode(503))
            ProviderConfig.setKey(db, "mainapi_url", server.url("/").toString())
            setContent { com.sokoul.pc.ui.ScaledRoot { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) } }
            waitUntil { onAll("menu-open") }
            onNodeWithTag("menu-open").performClick()
            waitUntil { onAll("menu-sports") }
            onNodeWithTag("menu-sports").performClick()
            waitUntil { onAll("sports-category-Football") }
            onNodeWithTag("sports-category-Football").performClick()
            onNodeWithTag("sports-event-streamed_race").assertDoesNotExist()
            onNodeWithTag("sports-event-streamed_match").performClick()
            waitUntil { onAll("sports-server-$serverKey") }
            onNodeWithTag("sports-back").performClick()
            waitUntil { onAll("sports-event-streamed_match") }
            onNodeWithTag("sports-refresh").performKeyInput { pressKey(Key.Escape) }
            waitUntil { onAll("tv") }
            assertEquals(2, server.requestCount)
            // Only the Sport fallback needs IPTV data. The guide's live preview timer does not run in this UI test.
            com.sokoul.desktop.data.DesktopPrefs(db).livePreview = false
            val playlist = db.upsertPlaylist(com.sokoul.desktop.data.Playlist(0, "Test", com.sokoul.desktop.data.PlaylistType.M3U,
                "https://example.test/playlist", null, null, null, null))
            db.replaceLive(playlist, listOf(
                com.sokoul.desktop.data.Category(playlist, "sports", "FR | Sports", 0),
                com.sokoul.desktop.data.Category(playlist, "general", "FR | General", 1),
            ), listOf(
                com.sokoul.desktop.data.Channel(playlist, "test-sport", 1, "Test Sport", null, "sports", null,
                    "test-sport", "https://example.test/sports", null, null, 0),
                com.sokoul.desktop.data.Channel(playlist, "test-general", 2, "Test General", null, "general", null,
                    "test-general", "https://example.test/general", null, null, 1),
            ))
            onNodeWithTag("tv-sports-open").performClick()
            waitUntil { server.requestCount == 3 && onAll("sports-channel-test-sport") }
            onNodeWithTag("sports-event-streamed_match").assertDoesNotExist()
            onNodeWithTag("sports-channel-test-general").assertDoesNotExist()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test(timeout = 120_000)
    fun grid_keys_and_direct_typing_both_search() = runComposeUiTest {
        val catalog = FakeCatalog()
        setContent { SokoulPcApp(testDeps(db, validator, catalog), skipSplash = true) }
        waitUntil { onAll("menu-search") }
        onNodeWithTag("menu-search").performClick()
        waitForIdle()
        // The search starts from two letters: one letter only gave noise.
        onNodeWithTag("key-a").performClick()
        onNodeWithTag("key-b").performClick()
        mainClock.advanceTimeBy(400)
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { catalog.searched.isNotEmpty() }
        assertEquals("ab", catalog.searched.last())
        waitUntil(timeoutMillis = 5_000) { onAll("search-results") }
        onNodeWithTag("key-a").performKeyInput { pressKey(Key.C) }
        mainClock.advanceTimeBy(400)
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { catalog.searched.last() == "abc" }
    }

    @Test
    fun collection_names_lose_the_generic_words() {
        assertEquals("Iron Man", com.sokoul.pc.data.CollectionRules.cleanName("Iron Man - Saga"))
        assertEquals("Rocky", com.sokoul.pc.data.CollectionRules.cleanName("La saga Rocky"))
        assertEquals("Avatar", com.sokoul.pc.data.CollectionRules.cleanName("Avatar Collection"))
    }

    @Test
    fun a_logo_only_comes_from_a_film_carrying_the_saga_name() {
        val films = listOf(sampleItem(1, "movie").copy(title = "Le Réveil de la Force"), sampleItem(2, "movie").copy(title = "Star Wars : Un nouvel espoir"))
        assertEquals(1, com.sokoul.pc.data.CollectionRules.leadFilm("Star Wars", films))
        assertNull(com.sokoul.pc.data.CollectionRules.leadFilm("Le monde des sorciers", films))
    }

    @Test
    fun a_saga_logo_never_shows_a_film_subtitle() {
        val subtitled = sampleItem(1, "movie").copy(title = "Fast & Furious : Tokyo Drift")
        val exact = sampleItem(2, "movie").copy(title = "Fast & Furious")
        assertEquals(com.sokoul.pc.data.LogoSource(1, false), com.sokoul.pc.data.CollectionRules.logoFilm("Fast & Furious", listOf(subtitled, exact)))
        assertNull(com.sokoul.pc.data.CollectionRules.logoFilm("Fast & Furious", listOf(subtitled)))
        val original = sampleItem(3, "movie").copy(title = "La Guerre des étoiles", originalTitle = "Star Wars")
        assertEquals(com.sokoul.pc.data.LogoSource(0, true), com.sokoul.pc.data.CollectionRules.logoFilm("Star Wars", listOf(original)))
    }

    @Test
    fun collections_page_keeps_universes_apart_and_shelves_every_saga_once() {
        val tiles = (1..12).map {
            com.sokoul.pc.data.SagaTile("s$it", "S$it", listOf(sampleItem(it, "movie")), null, null, null, category = if (it % 2 == 0) com.sokoul.pc.data.SagaCategory.Horror else com.sokoul.pc.data.SagaCategory.SciFi)
        }
        val marvel = com.sokoul.pc.data.SagaTile("m", "Univers", listOf(sampleItem(99, "movie")), null, null, null)
        val empty = com.sokoul.pc.data.SagaTile("e", "Vide", emptyList(), null, null, null)
        val refs = listOf(com.sokoul.pc.data.SagaRef(5, "Other", 2, com.sokoul.pc.data.SagaCategory.Horror))
        val page = com.sokoul.pc.data.CollectionRules.page(tiles + marvel + empty, listOf(com.sokoul.pc.data.CollectionUniverse("Univers", listOf(marvel))), refs)
        assertEquals(8, page.featured.size)
        assertEquals("s1", page.spotlight?.key)
        assertTrue(page.featured.none { it.name == "Univers" || it.name == "Vide" })
        assertEquals(listOf(com.sokoul.pc.data.SagaCategory.SciFi, com.sokoul.pc.data.SagaCategory.Horror), page.shelves.map { it.category })
        val horror = page.shelves.last().entries.map { it.key }
        assertEquals("all-5", horror.last())
        assertEquals(13, page.shelves.flatMap { it.entries }.map { it.key }.distinct().size)
    }

    @Test
    fun a_saga_is_shelved_by_what_most_of_its_films_are() {
        val of = com.sokoul.pc.data.SagaCategories::of
        assertEquals(com.sokoul.pc.data.SagaCategory.Superheroes, of(listOf(setOf(28)), listOf("Superhero", "superhero")))
        assertEquals(com.sokoul.pc.data.SagaCategory.SciFi, of(listOf(setOf(878)), listOf("superhero")))
        assertEquals(com.sokoul.pc.data.SagaCategory.Animation, of(listOf(setOf(16, 35), setOf(16)), emptyList()))
        assertEquals(com.sokoul.pc.data.SagaCategory.Horror, of(listOf(setOf(27, 53), setOf(27), setOf(53)), emptyList()))
        assertEquals(com.sokoul.pc.data.SagaCategory.SciFi, of(listOf(setOf(12, 28, 878), setOf(12, 878)), emptyList()))
        assertEquals(com.sokoul.pc.data.SagaCategory.Action, of(listOf(setOf(28, 80), setOf(28, 53)), emptyList()))
        assertEquals(com.sokoul.pc.data.SagaCategory.Crime, of(listOf(setOf(80, 18), setOf(80)), emptyList()))
        assertEquals(com.sokoul.pc.data.SagaCategory.Action, of(emptyList(), emptyList()))
    }

    @Test
    fun a_sagas_facts_add_up_only_what_is_known() {
        val cast = listOf(com.sokoul.iptv.data.net.TmdbPerson("Both", null, null, 1))
        fun film(id: Int, revenue: Long?, imdb: Double?, oscars: Int, people: List<com.sokoul.iptv.data.net.TmdbPerson>) = com.sokoul.pc.data.FilmFacts(
            sampleItem(id, "movie"),
            sampleInfo(id, null).copy(revenue = revenue, budget = null, runtimeMin = 100, cast = people),
            com.sokoul.pc.data.OmdbFacts(imdb = imdb, oscarsWon = oscars),
        )
        val profile = com.sokoul.pc.data.SagaFacts.of("  ", listOf(film(1, 1_000_000_000, 8.0, 2, cast), film(2, null, null, 0, cast + com.sokoul.iptv.data.net.TmdbPerson("One", null, null, 2)), film(3, 500_000_000, 6.0, 1, emptyList())))
        assertNull(profile.overview)
        assertEquals(1_500_000_000L, profile.boxOffice)
        assertNull(profile.budget)
        assertEquals(300, profile.runtimeMin)
        assertEquals(7.0, profile.imdb)
        assertEquals(3, profile.oscarsWon)
        assertEquals(1, profile.best?.film?.id)
        assertEquals(1, profile.biggest?.film?.id)
        assertEquals(listOf(1), profile.cast.map { it.person.id })
        assertEquals(2, profile.cast.single().films)
    }

    @Test
    fun omdb_awards_and_money_read_as_expected() {
        val text = """{"Response":"True","imdbRating":"8.6","Awards":"Won 6 Oscars. 65 wins & 31 nominations total","Ratings":[{"Source":"Rotten Tomatoes","Value":"93%"}]}"""
        val facts = com.sokoul.pc.data.OmdbClient.parse(text)
        assertEquals(8.6, facts?.imdb)
        assertEquals(93, facts?.rottenTomatoes)
        assertEquals(6, facts?.oscarsWon)
        assertEquals(65, facts?.awardsWon)
        assertEquals(0, com.sokoul.pc.data.OmdbClient.parse("""{"imdbRating":"N/A","Awards":"N/A"}""")?.oscarsWon)
        assertEquals("$10.4B", com.sokoul.pc.data.SagaFacts.money(10_400_000_000, french = false))
        assertEquals("775 M$", com.sokoul.pc.data.SagaFacts.money(775_000_000, french = true))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun collections_tab_opens_a_saga_order_changes_and_escape_comes_back() = runComposeUiTest {
        setContent { SokoulPcApp(testDeps(db, validator, FakeCatalog()), skipSplash = true) }
        waitUntil { onAll("menu-open") }
        onNodeWithTag("menu-open").performClick()
        waitUntil { onAll("menu-collections") }
        onNodeWithTag("menu-collections").performClick()
        waitUntil { onAll("saga-saga-a") }
        onNodeWithTag("saga-saga-a").performClick()
        // First the reduced card, as on Apple TV; the second activation opens the full page.
        waitUntil { onAll("saga-name") }
        assertTrue(onAllNodes(androidx.compose.ui.test.hasTestTag("saga-timeline")).fetchSemanticsNodes().isEmpty())
        onNodeWithTag("saga").performKeyInput { pressKey(Key.Enter) }
        waitUntil { onAll("saga-timeline") }
        onNodeWithTag("order-story").performClick()
        waitForIdle()
        onNodeWithTag("saga").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        onNodeWithTag("saga").assertDoesNotExist()
        onNodeWithTag("collections").assertExists()
    }

    @Test
    fun no_interface_text_mentions_apple() {
        val all = Strings.english.values + Strings.french.values
        assertTrue(all.none { it.contains("apple", ignoreCase = true) })
    }

    /** Every test tag on screen, to say what was there when an expected one is missing. */
    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.tagsOnScreen(): List<String> =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tagged") { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.TestTag) })
            .fetchSemanticsNodes().mapNotNull { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null } }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.onAll(tag: String): Boolean =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.waitUntil(check: () -> Boolean) = waitUntil(timeoutMillis = 8_000, condition = check)

    @Suppress("unused")
    private val silence: Any = Resume::class
}
