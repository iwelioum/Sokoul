package com.sokoul.pc

import com.sokoul.iptv.data.anime.AnimeEntry
import com.sokoul.iptv.data.anime.AnimeType
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.data.anime.AniBatch
import com.sokoul.pc.data.anime.AniListClient
import com.sokoul.pc.data.anime.AniQuery
import com.sokoul.pc.data.anime.AniTitle
import com.sokoul.pc.data.anime.AnimeLink
import com.sokoul.pc.data.anime.AnimeLinkCodec
import com.sokoul.pc.data.anime.AnimeLists
import com.sokoul.pc.data.anime.AnimeMatch
import com.sokoul.pc.data.anime.AnimeMatchCache
import com.sokoul.pc.data.anime.AnimeTheme
import com.sokoul.pc.data.anime.EpisodeKind
import com.sokoul.pc.data.anime.FanParse
import com.sokoul.pc.data.anime.MatchCodec
import com.sokoul.pc.data.anime.MediaKind
import com.sokoul.pc.data.providers.Pacer
import com.sokoul.pc.data.providers.ProviderHttp
import com.sokoul.pc.data.providers.RequestPriority
import com.sokoul.iptv.data.net.SingleFlight
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** The anime data layer's queue, caches and batching, without any network (2026-11-24). */
class AnimeDataLayerTest {
    private fun response(request: Request, code: Int, body: String, retryAfter: String? = null) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .apply { retryAfter?.let { header("Retry-After", it) } }
        .body(body.toResponseBody("application/json".toMediaType())).build()

    // ---- The queue

    @Test fun cancelled_callers_leave_no_slot_behind() = runBlocking {
        val pacer = Pacer(200)
        pacer.turn()
        val abandoned = (1..5).map { launch { pacer.turn() } }
        delay(30)
        abandoned.forEach { it.cancel() }
        val started = System.nanoTime()
        pacer.turn()
        val waited = (System.nanoTime() - started) / 1_000_000
        // Before: each abandoned call kept its 200 ms slot, and this one waited past 1.2 s.
        assertTrue("waited $waited ms", waited < 400)
        assertEquals(0, pacer.waiting)
    }

    @Test fun an_urgent_call_goes_before_the_rows_waiting() = runBlocking {
        val pacer = Pacer(80)
        pacer.turn()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val rows = (1..3).map { i -> launch { pacer.turn(RequestPriority.Normal); order += "row$i" } }
        delay(20)
        val title = launch { pacer.turn(RequestPriority.Urgent); order += "title" }
        (rows + title).forEach { it.join() }
        assertEquals("title", order.first())
        assertEquals(listOf("row1", "row2", "row3"), order.drop(1))
    }

    @Test fun a_flight_whose_owner_is_cancelled_is_taken_over() = runBlocking {
        val flights = SingleFlight<String, Int>()
        val owner = launch(start = CoroutineStart.UNDISPATCHED) { flights.run("k") { awaitCancellation() } }
        val waiter = async { flights.run("k") { 7 } }
        delay(20)
        owner.cancel()
        assertEquals(7, withTimeout(1_000) { waiter.await() })
        assertEquals(0, flights.size)
    }

    @Test fun a_refusal_pauses_minutes_then_longer_if_it_goes_on() {
        assertEquals(10 * 60_000L, ProviderHttp.refusedPause(1))
        assertEquals(20 * 60_000L, ProviderHttp.refusedPause(2))
        assertEquals(24 * 3_600_000L, ProviderHttp.refusedPause(30))
    }

    @Test fun too_many_requests_shows_the_kept_answer_and_asks_nobody_meanwhile() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain -> calls.incrementAndGet(); response(chain.request(), 429, "{}", "30") }.build()
        val store = ConcurrentHashMap<String, Pair<String, Long>>()
        store["fixture:old"] = """{"kept":true}""" to 0L
        val http = ProviderHttp(client, store::get, { k, v, t -> store[k] = v to t })
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("fixture.invalid").build()
        assertNotNull(http.get("fixture", url, "old", 1_000))
        assertNotNull(http.get("fixture", url, "old", 1_000))
        assertNull(http.get("fixture", url, "new", 1_000))
        assertEquals(1, calls.get())
        assertTrue(http.paused("fixture"))
    }

    // ---- AniList rows

    private val trending = AniQuery(MediaKind.ANIME)
    private val season = AniQuery(MediaKind.ANIME, sort = "POPULARITY_DESC", season = "FALL", year = 2026)
    private val films = AniQuery(MediaKind.ANIME, sort = "POPULARITY_DESC", format = "MOVIE")

    @Test fun grid_can_tell_an_unavailable_page_from_an_empty_page() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (calls.incrementAndGet() == 1) response(chain.request(), 503, "{}")
            else response(chain.request(), 200, """{"data":{"Page":{"media":[]}}}""")
        }.build()
        val aniList = AniListClient(ProviderHttp(client, { null }, { _, _, _ -> }), batchWindowMs = 0)
        assertNull(aniList.titlesOrNull(trending))
        assertEquals(emptyList<AniTitle>(), aniList.titlesOrNull(trending))
    }

    @Test fun rows_are_asked_with_aliases_and_only_the_given_variables() {
        val (text, variables) = AniBatch.request(listOf(trending, season), "id")
        assertTrue(text.contains("r0: Page(page:\$page_0,perPage:\$perPage_0)"))
        assertTrue(text.contains("r1: Page("))
        assertTrue(text.contains("season:\$season_1"))
        assertTrue(text.contains("\$seasonYear_1:Int"))
        assertTrue(!text.contains("season_0"))
        assertTrue(text.endsWith("fragment F on Media { id }"))
        assertEquals("FALL", (variables["season_1"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertNull(variables["search_0"])
    }

    private fun media(id: Int) = """{"id":$id,"type":"ANIME","title":{"romaji":"Work $id"},"format":"TV"}"""

    @Test fun rows_asked_together_go_out_as_one_request_and_are_kept_each() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            // Each alias answers by what it asked: the rows may reach the request in any order.
            val sent = okio.Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
            val variables = (Json.parseToJsonElement(sent) as JsonObject)["variables"] as JsonObject
            val rows = (0..2).joinToString(",") { i ->
                val id = when { variables["season_$i"] != null -> 2; variables["format_$i"] != null -> 3; else -> 1 }
                "\"r$i\":{\"media\":[${media(id)}]}"
            }
            response(chain.request(), 200, """{"data":{$rows}}""")
        }.build()
        val store = ConcurrentHashMap<String, Pair<String, Long>>()
        val aniList = AniListClient(ProviderHttp(client, store::get, { k, v, t -> store[k] = v to t }))
        val rows = aniList.titlesBatch(listOf(trending, season, films))
        assertEquals(1, calls.get())
        assertEquals(listOf(1), rows.getValue(trending).map { it.id })
        assertEquals(listOf(3), rows.getValue(films).map { it.id })
        // Each row is kept as if asked alone: asked again, it comes from the device.
        assertEquals(listOf(2), aniList.titles(season).map { it.id })
        assertEquals(1, calls.get())
        assertTrue(store.keys.any { it == "anilist:titles:$season" })
    }

    @Test fun an_old_row_shows_at_once_and_is_asked_again_behind() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            response(chain.request(), 200, """{"data":{"Page":{"media":[${media(9)}]}}}""")
        }.build()
        val store = ConcurrentHashMap<String, Pair<String, Long>>()
        store["anilist:titles:$trending"] = """{"data":{"Page":{"media":[${media(5)}]}}}""" to 0L
        val aniList = AniListClient(ProviderHttp(client, store::get, { k, v, t -> store[k] = v to t }))
        val started = System.nanoTime()
        assertEquals(listOf(5), aniList.titles(trending).map { it.id })
        assertTrue((System.nanoTime() - started) / 1_000_000 < 500)
        withTimeout(3_000) { while (store.getValue("anilist:titles:$trending").second == 0L) delay(20) }
        assertEquals(1, calls.get())
        assertEquals(listOf(9), aniList.titles(trending).map { it.id })
    }

    // ---- Matching AniList with the catalog

    private val item = TmdbItem(37854, "One Piece", "ワンピース", 1999, "/p.jpg", "/b.jpg", 8.7, "A pirate.", listOf(16, 10759), "ja", 5000, "tv")

    @Test fun a_match_is_kept_on_the_device_and_a_doubtful_miss_is_not() = runBlocking {
        val store = ConcurrentHashMap<String, Pair<String, Long>>()
        val first = AnimeMatchCache(read = store::get, write = { k, v, t -> store[k] = v to t })
        assertEquals(item, first.resolve("anilist:21:fr-FR") { item })
        assertNull(first.resolve("anilist:22:fr-FR", keepMiss = { false }) { null })
        assertNull(first.resolve("anilist:23:fr-FR", keepMiss = { true }) { null })
        // A relaunch: a new cache over the same table.
        val again = AnimeMatchCache(read = store::get, write = { k, v, t -> store[k] = v to t })
        assertEquals(item, again.resolve("anilist:21:fr-FR") { error("kept: no search") })
        assertNull(again.resolve("anilist:23:fr-FR") { error("kept miss: no search") })
        assertEquals(item, again.resolve("anilist:22:fr-FR") { item })
    }

    @Test fun a_catalog_title_survives_being_kept() {
        assertEquals(item, MatchCodec.decode(MatchCodec.encode(item)))
        val bare = TmdbItem(1, "X", null, null, null, null, null, null)
        assertEquals(bare, MatchCodec.decode(MatchCodec.encode(bare)))
    }

    private fun entry(anilist: Int, type: AnimeType, season: Int?, offset: Int = 0, tv: Int? = 37854, movies: List<Int> = emptyList()) =
        AnimeEntry(type, anilist = anilist, tmdbTv = tv, tmdbMovies = movies, tmdbSeason = season, tmdbOffset = offset)

    @Test fun the_list_on_the_device_places_a_work_and_orders_its_seasons() {
        assertEquals("tv" to 37854, AnimeMatch.listed(entry(21, AnimeType.TV, 1), "TV"))
        assertEquals("movie" to 12, AnimeMatch.listed(entry(5, AnimeType.MOVIE, null, tv = null, movies = listOf(12)), "MOVIE"))
        // A film the list only places in a series: the search decides.
        assertNull(AnimeMatch.listed(entry(5, AnimeType.MOVIE, 0), "MOVIE"))
        val entries = listOf(
            entry(30, AnimeType.TV, 3), entry(10, AnimeType.TV, 1), entry(99, AnimeType.MOVIE, 0),
            entry(31, AnimeType.TV, 3, offset = 12), entry(20, AnimeType.ONA, 2), entry(11, AnimeType.SPECIAL, 0),
        )
        assertEquals(listOf(10, 20, 30, 31), AnimeMatch.seasonIds(entries, 10))
        assertEquals(listOf(30, 31), AnimeMatch.seasonIds(entries, 30))
        assertNull(AnimeMatch.seasonIds(entries, 99))
    }

    private fun work(id: Int) = AniTitle(id, MediaKind.ANIME, "Work", "Sakuhin", null, "c", null, "TV", "FINISHED", 2020, 12, null, null, 80, 1000,
        listOf("Action"), listOf("Isekai"), "Text.", "JP", "Studio", null, null)

    @Test fun a_link_survives_being_kept() {
        val link = AnimeLink(work(1), work(2).copy(kind = MediaKind.MANGA), listOf(com.sokoul.pc.data.anime.AniLink("Site", "https://x", "Japanese")),
            listOf(AnimeTheme("OP1", "Song", "Artist", "1-12", "https://v", null, 0)), listOf(work(1), work(3)), listOf(work(4)))
        assertEquals(link, AnimeLinkCodec.decode(AnimeLinkCodec.encode(link)))
        assertNull(AnimeLinkCodec.decode("{\"broken\":true}"))
    }

    // ---- Fans' sources and the lists on the device

    @Test fun filler_kinds_are_kept_as_a_small_object() {
        val kinds = mapOf(1 to EpisodeKind.Canon, 2 to EpisodeKind.Filler, 3 to EpisodeKind.Mixed)
        val kept = FanParse.kindsJson(kinds)
        assertEquals(kinds, FanParse.kindsFrom(Json.parseToJsonElement(kept.toString())))
        assertTrue(FanParse.kindsFrom(JsonObject(emptyMap())).isEmpty())
    }

    @Test fun manami_is_asked_again_monthly_and_fribb_weekly() {
        assertEquals(30 * 24 * 3_600_000L, AnimeLists.maxAge(AnimeLists.MANAMI))
        assertEquals(7 * 24 * 3_600_000L, AnimeLists.maxAge(AnimeLists.FRIBB))
    }

    // ---- Anime-Sama (the site's page rules are tested in the sources module: AnimeSamaSiteTest)

    @Test fun three_lookups_at_once_share_one_flight() = runBlocking {
        val flights = SingleFlight<String, Int>()
        val runs = AtomicInteger()
        val all = (1..3).map { async { withContext(RequestPriority.Urgent) { flights.run("tv:1") { runs.incrementAndGet(); delay(50); 1 } } } }.awaitAll()
        assertEquals(listOf(1, 1, 1), all)
        assertEquals(1, runs.get())
    }
}
