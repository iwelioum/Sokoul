package com.sokoul.pc

import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.MaxQuality
import com.sokoul.desktop.data.MaxSize
import com.sokoul.desktop.data.PlaybackPrefs
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.data.StartMode
import com.sokoul.desktop.player.PlayerStatus
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.RdException
import com.sokoul.iptv.data.debrid.RealDebridApi
import com.sokoul.iptv.data.debrid.Release
import com.sokoul.iptv.data.debrid.ReleaseName
import com.sokoul.iptv.data.net.TmdbVideo
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.iptv.parser.XtAccount
import com.sokoul.pc.data.TrailerOrder
import com.sokoul.pc.data.TrailerResolver
import com.sokoul.pc.data.TrailerStream
import com.sokoul.pc.playback.session.ConnectionGate
import com.sokoul.pc.playback.sources.DebridTransfers
import com.sokoul.pc.playback.session.Doubt
import com.sokoul.pc.playback.sources.IptvMatch
import com.sokoul.pc.playback.session.Phase
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayFailureException
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.PlaybackBudgets
import com.sokoul.pc.playback.session.PlaybackSession
import com.sokoul.pc.playback.sources.Probe
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.SearchUpdate
import com.sokoul.pc.playback.session.SourceFinder
import com.sokoul.pc.playback.sources.StartPolicy
import com.sokoul.pc.playback.session.Transfer
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.sources.VersionRanking
import com.sokoul.pc.playback.sources.failureOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTest {
    // PlaybackSession runs its search and playback coroutines on the UI dispatcher in the app.
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val testJobs = SupervisorJob()
    private fun scope() = CoroutineScope(SupervisorJob(testJobs) + dispatcher)

    @After
    fun cancelPlaybackJobs() = runBlocking { testJobs.cancelAndJoin() }
    private val request = PlayRequest("movie", 1, null, null, "Title", null, null)
    private val fast = PlaybackBudgets(firstChoiceMs = 200, searchMs = 300, firstImageMs = 400, trackMs = 150, stallMs = 300, tickMs = 20)

    private fun waitUntil(what: String, check: () -> Boolean) = runBlocking {
        val end = System.currentTimeMillis() + 5_000
        while (!check()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Timed out: $what")
            delay(10)
        }
    }

    /** French first, then the original: fixed here so that no test depends on the machine's language. */
    private val frenchFirst = PlaybackPrefs(intent = com.sokoul.desktop.data.AudioIntent(listOf("fr", com.sokoul.desktop.data.AudioIntent.ORIGINAL)))

    private fun session(engine: com.sokoul.pc.playback.session.PlayerEngine?, finder: SourceFinder, prefs: PlaybackPrefs = frenchFirst, read: (String) -> Resume? = { null }) =
        PlaybackSession(engine, finder, scope(), prefs = { prefs }, frenchSystem = { true }, readResume = read, budgets = fast)

    // --- The session

    // --- Live channels

    @Test
    fun recording_blocks_provider_playback_but_allows_a_saved_local_file() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        val gate = com.sokoul.desktop.data.DesktopRecorder.connectionInUse
        val previous = gate.getAndSet(true)
        try {
            assertFalse(s.playDirect("provider", StreamRequest("https://provider.test/video.ts", null, null, "Provider"), "Provider"))
            assertTrue(engine.played.isEmpty())
            assertTrue(s.playDirect("saved", StreamRequest("file:///saved/programme.ts", null, null, "Saved", live = false), "Saved"))
            waitUntil("saved local file plays during capture") { engine.played.any { it.url == "file:///saved/programme.ts" } }
            assertTrue(gate.get())
            assertFalse(s.playDirect("provider-again", StreamRequest("https://provider.test/second.ts", null, null, "Provider"), "Provider"))
            assertTrue(engine.played.all { it.url == "file:///saved/programme.ts" })
        } finally {
            gate.set(previous)
            s.close()
        }
    }

    private fun channel(key: String) = com.sokoul.pc.playback.session.LiveChannel(key, key, null, null, StreamRequest(key, null, null, key))

    private fun family(vararg keys: String): List<com.sokoul.pc.playback.session.LiveChannel> {
        lateinit var all: List<com.sokoul.pc.playback.session.LiveChannel>
        all = keys.map { k -> com.sokoul.pc.playback.session.LiveChannel(k, k, null, null, StreamRequest(k, null, null, k), variants = { all }) }
        return all
    }

    @Test
    fun a_dead_stream_gives_way_to_another_of_the_same_channel() {
        val engine = FakeEngine(failing = setOf("fhd"))
        val s = session(engine, finder())
        val streams = family("fhd", "hd")
        s.playLive(streams[0], listOf(streams[0]))
        waitUntil("the other stream plays") { s.phase is Phase.Playing && engine.played.lastOrNull()?.url == "hd" }
        assertEquals("hd", s.live?.key)
        assertEquals("zapping still knows the channel of the list", "fhd", s.liveBaseKey)
        assertEquals("hd", s.liveNotice)
    }

    @Test
    fun when_every_stream_of_a_channel_is_dead_the_failure_shows_after_trying_each_once() {
        val engine = FakeEngine(failing = setOf("fhd", "hd"))
        val s = session(engine, finder())
        val streams = family("fhd", "hd")
        s.playLive(streams[0], listOf(streams[0]))
        waitUntil("the failure") { s.phase is Phase.Failed }
        assertEquals(listOf("fhd", "hd"), engine.played.map { it.url })
    }

    @Test
    fun previous_channel_returns_to_last_distinct_channel_after_a_quality_change() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        val a = channel("a")
        val b = channel("b")
        val bVariant = channel("b-hd")
        val list = listOf(a, b)

        s.playLive(a, list)
        waitUntil("channel a plays") { s.phase is Phase.Playing }
        s.playLive(b, list)
        waitUntil("channel b plays") { s.phase is Phase.Playing && s.liveBaseKey == "b" }
        s.playLive(bVariant, list, sameChannel = true)
        waitUntil("channel b variant plays") { s.phase is Phase.Playing && s.live?.key == "b-hd" }
        s.previous()

        waitUntil("the last distinct channel plays") { s.phase is Phase.Playing && s.liveBaseKey == "a" && engine.played.lastOrNull()?.url == "a" }
        assertEquals("a", engine.played.last().url)
    }

    @Test
    fun source_advertised_replay_returns_to_the_original_live_stream() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        val program = com.sokoul.desktop.data.Program("a", 1_700_000_000_000, 1_700_000_100_000, "Earlier programme", null)
        val live = com.sokoul.pc.playback.session.LiveChannel(
            "a", "Channel A", null, null, StreamRequest("live-a", null, null, "Channel A"),
            capabilities = com.sokoul.pc.playback.session.LiveCapabilities(3),
            replay = { StreamRequest("replay-a", null, null, program.title, live = false, startMs = program.start) },
        )
        s.playLive(live)
        waitUntil("live stream starts") { s.phase is Phase.Playing }
        s.playReplay(program)

        waitUntil("finite replay starts") { s.phase is Phase.Playing && s.isLiveReplay && engine.played.lastOrNull()?.url == "replay-a" }
        s.backToLive()

        waitUntil("live returns") { s.phase is Phase.Playing && !s.isLiveReplay && engine.played.lastOrNull()?.url == "live-a" }
    }

    @Test
    fun a_full_account_tries_no_other_stream() {
        val engine = FakeEngine()
        val s = PlaybackSession(engine, finder(), scope(), prefs = { PlaybackPrefs() }, frenchSystem = { true }, budgets = fast,
            clearForLive = { throw com.sokoul.pc.playback.session.PlayFailureException(PlayFailure.ConnectionsFull(2, 2)) })
        s.playLive(family("https://fhd.test", "https://hd.test")[0])
        waitUntil("the failure") { s.phase is Phase.Failed }
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun a_stream_that_stops_moving_is_left_for_another() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        val streams = family("frozen", "hd")
        s.playLive(streams[0], listOf(streams[0]))
        waitUntil("the other stream") { engine.played.lastOrNull()?.url == "hd" }
    }

    @Test
    fun a_paused_channel_is_not_taken_for_a_frozen_one() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        val streams = family("a", "b")
        s.playLive(streams[0], listOf(streams[0]))
        waitUntil("plays") { s.phase is Phase.Playing }
        engine.report(com.sokoul.desktop.player.PlayerStatus(playing = true, paused = true, timeMs = 1_000, session = engine.currentSession))
        Thread.sleep(fast.stallMs * 3)
        assertEquals("a", engine.played.last().url)
    }

    private fun sample(session: Long, displayed: Int, lost: Int = 0, kbps: Int? = 5000) = com.sokoul.desktop.player.StreamSample(session, 1920, 1080, 25f, "AVC", "AAC", 48000, 2, kbps, displayed, lost, 0, 0)

    private fun readings(vararg samples: com.sokoul.desktop.player.StreamSample, buffering: Boolean = false) =
        samples.fold(com.sokoul.pc.playback.session.StreamReading()) { r, x -> com.sokoul.pc.playback.session.StreamMeter.next(r, x, buffering) }

    @Test
    fun the_meter_judges_a_stream_by_what_it_really_shows() {
        val steady = (1..5).map { sample(1, it * 25) }.toTypedArray()
        assertEquals(com.sokoul.pc.playback.session.StreamHealth.Good, readings(*steady).health)
        assertEquals("too early to tell", com.sokoul.pc.playback.session.StreamHealth.Measuring, readings(sample(1, 25)).health)
        val frozen = (1..6).map { sample(1, 100) }.toTypedArray()
        assertEquals(com.sokoul.pc.playback.session.StreamHealth.Bad, readings(*frozen).health)
        val losing = (1..5).map { sample(1, it * 22, lost = it * 3) }.toTypedArray()
        assertEquals(com.sokoul.pc.playback.session.StreamHealth.Weak, readings(*losing).health)
        assertEquals(com.sokoul.pc.playback.session.StreamHealth.Bad, readings(*steady, buffering = true).let { r -> (1..3).fold(r) { x, i -> com.sokoul.pc.playback.session.StreamMeter.next(x, sample(1, 200 + i), true) } }.health)
    }

    @Test
    fun the_bitrate_shown_is_an_average_of_the_last_seconds() {
        val r = readings(sample(1, 25, kbps = 0), sample(1, 50, kbps = 16000), sample(1, 75, kbps = 8000))
        assertEquals(8000, r.averageKilobits)
    }

    @Test
    fun a_new_stream_starts_its_measures_over() {
        val r = readings(sample(1, 100), sample(1, 200), sample(2, 25))
        assertEquals(listOf(5000), r.history)
        assertEquals(com.sokoul.pc.playback.session.StreamHealth.Measuring, r.health)
    }

    @Test
    fun a_channel_reads_without_its_list_prefix() {
        assertEquals("TF1 FHD", com.sokoul.pc.live.TvCatalog.label("FR| TF1 FHD"))
        assertEquals("TF1", com.sokoul.pc.live.TvCatalog.label("TF1"))
        assertEquals("fr", com.sokoul.pc.live.TvCatalog.region("FR| TF1 FHD"))
    }

    @Test
    fun channels_rank_sharpest_first() {
        val names = listOf("FR| TF1 SD", "FR| TF1", "FR| TF1 UHD", "FR| TF1 FHD")
        assertEquals(listOf("FR| TF1 UHD", "FR| TF1 FHD", "FR| TF1", "FR| TF1 SD"), names.sortedBy(com.sokoul.pc.live.TvCatalog::qualityRank))
    }

    @Test
    fun leaving_a_channel_for_the_guide_keeps_its_stream_going_muted() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        s.playLive(channel("a"))
        waitUntil("plays") { s.phase is Phase.Playing }
        val opened = engine.played.size
        assertTrue(s.returnLiveToGuide())
        assertEquals(Phase.Idle, s.phase)
        assertEquals("a", s.previewKey)
        assertEquals(null, s.live)
        assertEquals("a", s.lastLiveKey)
        s.playLive(channel("a"))
        waitUntil("plays again") { s.phase is Phase.Playing }
        assertEquals("the same stream, with no new connection", opened, engine.played.size)
    }

    @Test
    fun a_sleeping_preview_opens_nothing_until_someone_comes_back() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        s.sleepPreview()
        s.preview(channel("a"))
        Thread.sleep(100)
        assertTrue(engine.played.isEmpty())
        s.wakePreview()
        s.preview(channel("a"))
        waitUntil("preview") { s.previewKey == "a" }
    }

    @Test
    fun a_stream_that_fails_is_remembered() {
        val noted = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        val engine = FakeEngine(failing = setOf("bad"))
        val s = PlaybackSession(engine, finder(), scope(), prefs = { PlaybackPrefs() }, frenchSystem = { true }, budgets = fast,
            onStreamResult = { key, _, failed -> noted += key to failed })
        s.playLive(family("bad")[0])
        waitUntil("the failure") { s.phase is Phase.Failed }
        assertEquals(listOf("bad" to true), noted.toList())
    }

    @Test
    fun episodes_follow_each_other_and_the_end_is_announced() {
        fun e(n: Int) = com.sokoul.iptv.data.net.TmdbEpisode(1, n, "e$n", null, null, null)
        val order = com.sokoul.pc.playback.ui.EpisodeOrder
        assertEquals(3, order.nextIn(listOf(e(1), e(3), e(2)), 2)?.episode)
        assertEquals("the end of a season", null, order.nextIn(listOf(e(1), e(2)), 2)?.episode)
        assertTrue(order.cardShown(2_690_000, 2_700_000))
        assertEquals(false, order.cardShown(100_000, 2_700_000))
        assertEquals("a short clip has none", false, order.cardShown(50_000, 60_000))
    }

    @Test
    fun a_film_ends_later_at_a_slower_speed() {
        assertEquals(1000L + 60_000, com.sokoul.pc.playback.ui.endsAt(1000, 0, 60_000, 1f))
        assertEquals(1000L + 30_000, com.sokoul.pc.playback.ui.endsAt(1000, 0, 60_000, 2f))
    }

    @Test
    fun a_title_line_of_the_provider_is_no_channel() {
        assertTrue(com.sokoul.pc.live.TvCatalog.isSeparator("✦●✦| GENERAL FHD |✦●✦"))
        assertEquals(false, com.sokoul.pc.live.TvCatalog.isSeparator("FR| TF1 FHD"))
        assertTrue(com.sokoul.pc.live.TvCatalog.isAccountInfo("Accout Information"))
        assertEquals(false, com.sokoul.pc.live.TvCatalog.isAccountInfo("Account of a life"))
    }

    @Test
    fun a_channel_opens_and_zapping_goes_through_its_list_in_a_loop() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        val list = listOf(channel("a"), channel("b"))
        s.playLive(list[0], list)
        waitUntil("a plays") { s.phase is Phase.Playing && engine.played.lastOrNull()?.url == "a" }
        s.zap(1)
        waitUntil("b plays") { s.phase is Phase.Playing && engine.played.lastOrNull()?.url == "b" }
        s.zap(1)
        waitUntil("back to a") { s.phase is Phase.Playing && engine.played.lastOrNull()?.url == "a" }
        assertEquals("a", s.live?.key)
    }

    @Test
    fun watching_the_previewed_channel_does_not_open_a_second_stream() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        s.preview(channel("a"))
        waitUntil("preview") { s.previewKey == "a" }
        val opened = engine.played.size
        s.playLive(channel("a"))
        waitUntil("plays") { s.phase is Phase.Playing }
        assertEquals("the same stream goes on", opened, engine.played.size)
        assertEquals(null, s.previewKey)
    }

    @Test
    fun a_new_preview_closes_the_previous_stream_first_and_leaving_closes_it() {
        val engine = FakeEngine()
        val s = session(engine, finder())
        s.preview(channel("a"))
        waitUntil("a") { s.previewKey == "a" }
        s.preview(channel("b"))
        waitUntil("b") { s.previewKey == "b" }
        val calls = engine.calls.toList()
        assertTrue("stopped before opening b: $calls", calls.lastIndexOf("stop") in calls.indexOf("play:a") until calls.indexOf("play:b"))
        s.stopPreview()
        assertEquals("stop", engine.calls.last())
        assertEquals(null, s.previewKey)
    }

    @Test
    fun a_full_account_says_so_for_a_channel_and_opens_nothing() {
        val engine = FakeEngine()
        val s = PlaybackSession(engine, finder(), scope(), prefs = { PlaybackPrefs() }, frenchSystem = { true }, budgets = fast,
            clearForLive = { throw com.sokoul.pc.playback.session.PlayFailureException(PlayFailure.ConnectionsFull(2, 2)) })
        s.playLive(channel("https://a.test"))
        waitUntil("the failure") { s.phase is Phase.Failed }
        assertEquals(PlayFailure.ConnectionsFull(2, 2), (s.phase as Phase.Failed).failure)
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun a_film_after_a_channel_is_no_longer_live() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("v0", "film")))
        s.playLive(channel("a"))
        waitUntil("a plays") { s.phase is Phase.Playing }
        s.play(request)
        assertEquals(null, s.live)
        waitUntil("film") { engine.played.lastOrNull()?.url == "film" }
    }

    @Test
    fun the_time_the_engine_takes_to_load_counts_against_no_version() = runBlocking {
        val engine = object : com.sokoul.pc.playback.session.PlayerEngine by FakeEngine() {
            override suspend fun awaitLoaded() = delay(fast.firstImageMs * 3)
        }
        val s = session(engine, finder(testVersion("v0", "a"), testVersion("v1", "b")))
        s.play(request)
        waitUntil("the first version plays") { s.phase is Phase.Playing }
        assertEquals("v0", (s.phase as Phase.Playing).version?.key)
    }

    @Test
    fun the_old_error_of_a_slow_player_no_longer_skips_a_version() = runBlocking {
        val engine = SlowEngine(failing = setOf("a"), scope = scope())
        // The cause reproduced: right after asking for b, the status still shows a's error.
        engine.play(StreamRequest("a", null, null, "t", live = false, session = 1))
        waitUntil("a failed") { engine.status.value.error != null }
        engine.play(StreamRequest("b", null, null, "t", live = false, session = 2))
        assertEquals("the status still describes a", 1L, engine.status.value.session)
        assertTrue(engine.status.value.error != null)
        waitUntil("b played") { engine.played.size == 2 }

        val slow = SlowEngine(failing = setOf("a"), scope = scope())
        val s = session(slow, finder(testVersion("v0", "a"), testVersion("v1", "b")))
        s.play(request)
        waitUntil("the second version really plays") { s.phase is Phase.Playing }
        assertEquals(listOf("a", "b"), slow.played)
        assertEquals("v1", s.current?.key)
        s.close()
    }

    @Test
    fun versions_are_tried_in_order_until_one_plays() = runBlocking {
        val engine = FakeEngine(failing = setOf("https://a.test"))
        val s = session(engine, fixedSources("https://a.test", "https://b.test"))
        s.play(request)
        waitUntil("second played") { s.phase is Phase.Playing }
        assertEquals(listOf("https://a.test", "https://b.test"), engine.played.map { it.url })
        s.close()
    }

    @Test
    fun no_version_shows_why() {
        val s = session(FakeEngine(), finder())
        s.play(request)
        waitUntil("no source") { (s.phase as? Phase.Failed)?.failure == PlayFailure.NoSource }
    }

    @Test
    fun a_failure_keeps_its_cause_and_stays_in_the_player() {
        val s = session(FakeEngine(), finder(testVersion("a", failure = PlayFailure.ConnectionsFull(2, 2), origin = VersionOrigin.Iptv)))
        s.play(request)
        waitUntil("failed") { s.phase is Phase.Failed }
        assertEquals(PlayFailure.ConnectionsFull(2, 2), (s.phase as Phase.Failed).failure)
        assertEquals(PlayFailure.ConnectionsFull(2, 2), s.failures["a"])
    }

    @Test
    fun full_iptv_connections_move_straight_to_real_debrid_without_trying_other_iptv_versions() {
        val engine = FakeEngine()
        var secondIptvTried = false
        val iptv2 = PlayVersion("i2", VersionOrigin.Iptv, "i2", AudioLanguage.VF, VideoQuality.FHD_1080, readiness = Readiness.Ready) { _, _ ->
            secondIptvTried = true
            StreamRequest("iptv2", null, null, "t", live = false)
        }
        val s = session(engine, finder(testVersion("i1", failure = PlayFailure.ConnectionsFull(1, 1), origin = VersionOrigin.Iptv), iptv2, testVersion("rd", "rd", readiness = Readiness.Likely)))
        s.play(request)
        waitUntil("real-debrid plays") { s.phase is Phase.Playing }
        assertEquals("rd", engine.played.single().url)
        assertFalse(secondIptvTried)
        s.close()
    }

    @Test
    fun retry_and_refresh_put_every_version_back_in_the_running() {
        var searches = 0
        var refreshed = false
        var allow = false
        val version = PlayVersion("a", VersionOrigin.Debrid, "a", readiness = Readiness.Ready) { _, _ ->
            if (!allow) throw PlayFailureException(PlayFailure.DeadLink)
            StreamRequest("a", null, null, "t", live = false)
        }
        val f = SourceFinder { _, refresh ->
            flow {
                searches++
                refreshed = refreshed || refresh
                emit(SearchUpdate(listOf(version), done = true))
            }
        }
        val s = session(FakeEngine(), f)
        s.play(request)
        waitUntil("failed") { s.phase is Phase.Failed }
        allow = true
        s.retry()
        waitUntil("retried") { s.phase is Phase.Playing }
        s.refresh()
        waitUntil("searched again") { searches == 2 }
        assertTrue(refreshed)
        assertTrue("a film that plays goes on", s.phase is Phase.Playing)
        s.close()
    }

    @Test
    fun a_download_shows_its_percentage_then_plays() {
        val engine = FakeEngine()
        val progressSeen = CompletableDeferred<Unit>()
        val v = PlayVersion("a", VersionOrigin.Debrid, "a", readiness = Readiness.NotCached) { keep, progress ->
            assertTrue("a version that is not ready is waited for", keep)
            progress(Transfer(60, speedBps = 1_000_000, bytes = 1_000_000_000))
            progressSeen.await()
            StreamRequest("a", null, null, "t", live = false)
        }
        val s = session(engine, finder(v))
        s.play(request)
        waitUntil("60 %") {
            (s.phase as? Phase.Downloading)?.transfer?.percent == 60
        }
        progressSeen.complete(Unit)
        waitUntil("plays") { s.phase is Phase.Playing }
        assertEquals("a", engine.played.single().url)
        s.close()
    }

    @Test
    fun a_stall_moves_to_the_next_version_at_the_same_position() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("v0", "a"), testVersion("v1", "b")))
        s.play(request)
        waitUntil("first plays") { s.phase is Phase.Playing && s.current?.key == "v0" }
        engine.report(PlayerStatus(playing = true, timeMs = 600_000, lengthMs = 10_000_000, session = engine.currentSession))
        waitUntil("second plays") { s.current?.key == "v1" && s.phase is Phase.Playing }
        assertEquals(600_000, engine.played.last().startMs)
        s.close()
    }

    @Test
    fun an_early_end_is_a_cut_and_a_real_end_finishes_the_film() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("v0", "a"), testVersion("v1", "b")))
        s.play(request)
        waitUntil("first plays") { s.phase is Phase.Playing && s.current?.key == "v0" }
        engine.report(PlayerStatus(ended = true, timeMs = 900_000, lengthMs = 10_000_000, session = engine.currentSession))
        waitUntil("cut moves on") { s.current?.key == "v1" && s.phase is Phase.Playing }
        assertEquals(PlayFailure.Cut, s.failures["v0"])
        engine.report(PlayerStatus(ended = true, timeMs = 9_500_000, lengthMs = 10_000_000, session = engine.currentSession))
        waitUntil("finished") { s.phase is Phase.Finished }
    }

    private fun tracks(vararg names: String) = names.mapIndexed { i, n -> i to "Track ${i + 1} - [$n]" }

    @Test
    fun ask_fallback_holds_sound_until_the_viewer_chooses_to_listen() {
        val engine = FakeEngine(tracks = mapOf("a" to tracks("English")))
        val prefs = PlaybackPrefs(intent = com.sokoul.desktop.data.AudioIntent(listOf("fr"), com.sokoul.desktop.data.AudioFallback.Ask))
        val s = session(engine, finder(testVersion("v0", "a")), prefs)
        try {
            s.play(request)
            waitUntil("muted playback and language notice") { s.phase is Phase.Playing && s.languageNotice != null }
            assertEquals("fr", s.languageNotice?.wanted)
            assertTrue(s.languageNotice!!.heard.contains("English"))
            assertFalse("sound stays held", "endPreview" in engine.calls)
            s.listenAnyway()
            assertEquals(1, engine.calls.count { it == "endPreview" })
            runBlocking { delay(fast.tickMs * 3) }
            assertNull("the watch loop must not repeat the accepted notice", s.languageNotice)
            assertEquals(listOf("a"), engine.played.map { it.url })
        } finally { s.close() }
    }

    @Test
    fun engine_default_releases_sound_when_the_wanted_language_is_missing() {
        val engine = FakeEngine(tracks = mapOf("a" to tracks("English")))
        val prefs = PlaybackPrefs(intent = com.sokoul.desktop.data.AudioIntent(listOf("fr"), com.sokoul.desktop.data.AudioFallback.EngineDefault))
        val s = session(engine, finder(testVersion("v0", "a")), prefs)
        try {
            s.play(request)
            waitUntil("playing with language notice") { s.phase is Phase.Playing && s.languageNotice != null }
            assertEquals(1, engine.calls.count { it == "endPreview" })
        } finally { s.close() }
    }

    @Test
    fun ask_fallback_releases_sound_when_a_wanted_language_is_available() {
        val engine = FakeEngine(tracks = mapOf("a" to tracks("French")))
        val prefs = PlaybackPrefs(intent = com.sokoul.desktop.data.AudioIntent(listOf("fr"), com.sokoul.desktop.data.AudioFallback.Ask))
        val s = session(engine, finder(testVersion("v0", "a")), prefs)
        try {
            s.play(request)
            waitUntil("wanted audio plays") { s.phase is Phase.Playing }
            assertEquals(1, engine.calls.count { it == "endPreview" })
            assertNull(s.languageNotice)
        } finally { s.close() }
    }

    @Test
    fun a_multi_file_without_french_gives_way_before_it_starts_with_the_sound_held() {
        val engine = FakeEngine(tracks = mapOf("a" to tracks("English", "Spanish"), "b" to tracks("French", "English")))
        val s = session(engine, finder(testVersion("v0", "a", language = AudioLanguage.MULTI), testVersion("v1", "b", language = AudioLanguage.MULTI)))
        s.play(request)
        waitUntil("second plays") { s.current?.key == "v1" && s.phase is Phase.Playing }
        assertEquals(PlayFailure.WrongLanguage, s.failures["v0"])
        assertEquals("each version opened once", listOf("a", "b"), engine.played.map { it.url })
        runBlocking { delay(150) }
        assertEquals("it has French: it stays", "v1", s.current?.key)
        assertNull(s.languageNotice)
        s.close()
    }

    @Test
    fun an_active_version_is_never_replaced_for_its_language() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("v0", "a", language = AudioLanguage.MULTI), testVersion("v1", "b", language = AudioLanguage.MULTI)))
        s.play(request)
        waitUntil("first plays") { s.current?.key == "v0" && s.phase is Phase.Playing }
        // Tracks described only after the start: the viewer is told, nothing reopens.
        engine.report(PlayerStatus(playing = true, timeMs = 200_000, lengthMs = 10_000_000, audio = tracks("English", "Spanish"), audioId = 0, session = engine.currentSession))
        waitUntil("notice") { s.languageNotice != null }
        runBlocking { delay(200) }
        assertEquals("v0", s.current?.key)
        assertEquals(listOf("a"), engine.played.map { it.url })
        // French first in this session's preference (a French system): the notice names it and the track heard.
        assertEquals("fr", s.languageNotice!!.wanted)
        assertTrue(s.languageNotice!!.heard.contains("English"))
        s.close()
    }

    @Test
    fun a_version_left_for_its_language_comes_back_when_every_other_fails() {
        val engine = FakeEngine(tracks = mapOf("a" to tracks("Japanese", "English")))
        val s = session(engine, finder(
            testVersion("v0", "a", language = AudioLanguage.MULTI),
            testVersion("v1", "b", language = AudioLanguage.MULTI, failure = PlayFailure.DeadLink),
        ))
        s.play(request)
        // v1 is dead: the Japanese one plays again rather than nothing.
        waitUntil("back to the first") { s.current?.key == "v0" && s.phase is Phase.Playing && engine.played.size >= 2 }
        runBlocking { delay(200) }
        assertEquals("it stays this time", "v0", s.current?.key)
        assertTrue(s.phase is Phase.Playing)
        s.close()
    }

    @Test
    fun two_plays_then_another_episode_open_only_the_last_one() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("v0", "a")))
        s.play(request)
        s.play(request)
        val other = PlayRequest("tv", 2, 1, 2, "Other", null, null)
        s.play(other)
        waitUntil("plays") { s.phase is Phase.Playing && s.request == other }
        val opened = engine.played.size
        runBlocking { delay(200) }
        assertEquals("no late opening from the earlier requests", opened, engine.played.size)
        assertEquals(engine.currentSession, engine.status.value.session)
        assertEquals(other, s.request)
        assertTrue(s.phase is Phase.Playing)
        s.close()
    }

    @Test
    fun the_language_check_leaves_alone_what_it_cannot_improve() {
        // The only version: better in another language than nothing.
        val alone = FakeEngine()
        val a = session(alone, finder(testVersion("v0", "a", language = AudioLanguage.MULTI)))
        a.play(request)
        waitUntil("plays") { a.phase is Phase.Playing }
        alone.report(PlayerStatus(playing = true, timeMs = 5_000, lengthMs = 10_000_000, audio = tracks("English"), session = alone.currentSession))
        runBlocking { delay(150) }
        assertEquals("v0", a.current?.key)
        a.close()
        // Tracks with no language named tell nothing.
        val mute = FakeEngine()
        val b = session(mute, finder(testVersion("v0", "a", language = AudioLanguage.MULTI), testVersion("v1", "b", language = AudioLanguage.MULTI)))
        b.play(request)
        waitUntil("plays") { b.phase is Phase.Playing }
        mute.report(PlayerStatus(playing = true, timeMs = 5_000, lengthMs = 10_000_000, audio = listOf(0 to "Track 1"), session = mute.currentSession))
        runBlocking { delay(150) }
        assertEquals("v0", b.current?.key)
        b.close()
        // Picked by hand: the user's choice stands.
        val hand = FakeEngine()
        val c = session(hand, finder(testVersion("v0", "a", language = AudioLanguage.MULTI), testVersion("v1", "b", language = AudioLanguage.MULTI)))
        c.play(request)
        waitUntil("plays") { c.phase is Phase.Playing }
        c.choose(c.versions[1])
        waitUntil("second plays") { c.current?.key == "v1" && c.phase is Phase.Playing }
        hand.report(PlayerStatus(playing = true, timeMs = 5_000, lengthMs = 10_000_000, audio = tracks("English"), session = hand.currentSession))
        runBlocking { delay(150) }
        assertEquals("v1", c.current?.key)
        c.close()
        // Wanting the original: English is fine.
        val vo = FakeEngine()
        val d = session(vo, finder(testVersion("v0", "a", language = AudioLanguage.MULTI), testVersion("v1", "b", language = AudioLanguage.MULTI)), PlaybackPrefs(audio = AudioPreference.Vo))
        d.play(request)
        waitUntil("plays") { d.phase is Phase.Playing }
        vo.report(PlayerStatus(playing = true, timeMs = 5_000, lengthMs = 10_000_000, audio = tracks("English"), session = vo.currentSession))
        runBlocking { delay(150) }
        assertEquals("v0", d.current?.key)
        d.close()
    }

    @Test
    fun a_short_file_is_no_film() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("v0", "a")))
        s.play(request)
        waitUntil("plays") { s.phase is Phase.Playing }
        engine.report(PlayerStatus(playing = true, timeMs = 1_000, lengthMs = 30_000, session = engine.currentSession))
        waitUntil("rejected") { (s.phase as? Phase.Failed)?.failure == PlayFailure.FakeFile }
    }

    private val episode = PlayRequest("tv", 37854, 1, 1, "One Piece", null, null)

    private fun episodeSession(engine: FakeEngine, vararg versions: PlayVersion) =
        PlaybackSession(engine, finder(*versions), scope(), prefs = { PlaybackPrefs() }, frenchSystem = { true }, budgets = fast, expectedMinutes = { 24 })

    @Test
    fun the_last_version_of_the_wrong_length_does_not_play() {
        // 68 min for an episode of 24: the 2023 live-action series. Alone, it is not played either.
        val engine = FakeEngine()
        val s = episodeSession(engine, testVersion("netflix", "a"))
        s.play(episode)
        waitUntil("plays") { s.phase is Phase.Playing }
        engine.report(PlayerStatus(playing = true, timeMs = 1_000, lengthMs = 68 * 60_000L, session = engine.currentSession))
        waitUntil("held back") { (s.phase as? Phase.Failed)?.failure == PlayFailure.Doubtful(Doubt.WrongLength) }
        assertEquals("netflix", (s.phase as Phase.Failed).version?.key)
    }

    @Test
    fun play_anyway_plays_it_this_once() {
        val engine = FakeEngine()
        val s = episodeSession(engine, testVersion("long", "a"))
        s.play(episode)
        waitUntil("plays") { s.phase is Phase.Playing }
        engine.report(PlayerStatus(playing = true, timeMs = 1_000, lengthMs = 68 * 60_000L, session = engine.currentSession))
        waitUntil("held back") { (s.phase as? Phase.Failed)?.failure is PlayFailure.Doubtful }
        s.playAnyway()
        waitUntil("plays again") { s.phase is Phase.Playing }
        engine.report(PlayerStatus(playing = true, timeMs = 2_000, lengthMs = 68 * 60_000L, session = engine.currentSession))
        runBlocking { delay(200) }
        assertTrue(s.phase is Phase.Playing)
        // Retry forgets the choice: the length counts again.
        s.retry()
        waitUntil("plays after retry") { s.phase is Phase.Playing }
        engine.report(PlayerStatus(playing = true, timeMs = 3_000, lengthMs = 68 * 60_000L, session = engine.currentSession))
        waitUntil("held back again") { (s.phase as? Phase.Failed)?.failure is PlayFailure.Doubtful }
    }

    @Test
    fun a_version_of_another_work_is_never_started_on_its_own() {
        val engine = FakeEngine()
        val other = PlayVersion("tt2023", VersionOrigin.Debrid, "ONE PIECE 2023 S01E01", rejected = Doubt.OtherWork, readiness = Readiness.Ready) { _, _ ->
            StreamRequest("x", null, null, "t", live = false)
        }
        val s = episodeSession(engine, testVersion("bad", "a", failure = PlayFailure.DeadLink), other)
        s.play(episode)
        waitUntil("held back") { (s.phase as? Phase.Failed)?.failure == PlayFailure.Doubtful(Doubt.OtherWork) }
        assertEquals("tt2023", (s.phase as Phase.Failed).version?.key)
        assertFalse(engine.played.any { it.url == "x" })
    }

    @Test
    fun the_community_release_goes_first_at_the_same_language_and_picture() {
        fun anime(key: String, lang: AudioLanguage, community: Boolean = false, confidence: Int = 0) =
            PlayVersion(key, VersionOrigin.Debrid, key, lang, VideoQuality.FHD_1080, readiness = Readiness.Unknown, community = community, confidence = confidence) { _, _ -> StreamRequest(key, null, null, "t") }
        val ranked = VersionRanking.rank(
            listOf(anime("plain-vo", AudioLanguage.VO), anime("seadex-vo", AudioLanguage.VO, community = true), anime("vf", AudioLanguage.VF), anime("doubt-vf", AudioLanguage.VF, confidence = 2)),
            frenchFirst, frenchSystem = true,
        ).map { it.key }
        // The language wanted first; at the same language, a file that says it is the episode, then SeaDex's choice.
        assertEquals(listOf("vf", "doubt-vf", "seadex-vo", "plain-vo"), ranked)
    }

    @Test
    fun closing_during_a_slow_resolution_opens_nothing() = runBlocking {
        val engine = FakeEngine()
        val v = PlayVersion("a", VersionOrigin.Iptv, "a", readiness = Readiness.Ready) { _, _ ->
            delay(100)
            StreamRequest("a", null, null, "t", live = false)
        }
        val s = session(engine, finder(v))
        s.play(request)
        waitUntil("preparing") { s.phase is Phase.Preparing }
        s.close()
        delay(250)
        assertTrue(engine.played.isEmpty())
    }

    @Test
    fun a_new_title_closes_the_previous_stream_first() {
        val engine = FakeEngine()
        val s = session(engine, fixedSources("https://a.test"))
        s.play(request)
        waitUntil("first") { engine.played.size == 1 }
        s.play(request.copy(tmdbId = 2))
        waitUntil("second") { engine.played.size == 2 }
        val second = engine.calls.lastIndexOf("play:https://a.test")
        assertTrue(engine.calls.subList(0, second).contains("stop"))
        s.close()
    }

    @Test
    fun playback_resumes_from_the_saved_position() {
        val engine = FakeEngine()
        val saved = Resume(request.resumeKey, "movie", 1, null, null, "Title", null, null, 120_000, 600_000, 1, false)
        val s = session(engine, fixedSources("https://a.test"), read = { saved })
        s.play(request)
        waitUntil("played") { engine.played.isNotEmpty() }
        assertEquals(120_000, engine.played.single().startMs)
        s.close()
    }

    @Test
    fun missing_engine_shows_a_message() {
        val s = session(null, fixedSources("https://a.test"))
        s.play(request)
        assertEquals(PlayFailure.EngineMissing, (s.phase as Phase.Failed).failure)
    }

    @Test
    fun a_trailer_never_uses_the_sources() {
        var asked = false
        val engine = FakeEngine()
        val s = session(engine, { _, _ -> asked = true; flow { } })
        s.playTrailer("T", TrailerStream("https://v.test/x", null, "yt-dlp"))
        assertFalse(asked)
        assertEquals("https://v.test/x", engine.played.single().url)
    }

    @Test
    fun ask_mode_starts_nothing_until_a_version_is_chosen() {
        val engine = FakeEngine()
        val s = session(engine, finder(testVersion("a", "a")), PlaybackPrefs(startMode = StartMode.Ask))
        s.play(request)
        waitUntil("choosing") { s.phase is Phase.Choosing && s.versions.isNotEmpty() }
        assertTrue(engine.played.isEmpty())
        s.choose(s.versions.single())
        waitUntil("plays") { s.phase is Phase.Playing }
        s.close()
    }

    @Test
    fun a_sharper_version_is_fetched_behind_the_film_and_keeps_the_position() {
        val engine = FakeEngine()
        val ready = testVersion("hd", "hd", quality = VideoQuality.FHD_1080)
        val better = PlayVersion("4k", VersionOrigin.Debrid, "4k", AudioLanguage.VF, VideoQuality.UHD_4K, readiness = Readiness.NotCached) { keep, progress ->
            if (!keep) throw PlayFailureException(PlayFailure.NotReady(null))
            progress(Transfer(50))
            delay(50)
            StreamRequest("uhd", null, null, "t", live = false)
        }
        val s = session(engine, finder(ready, better))
        s.play(request)
        waitUntil("hd plays") { s.phase is Phase.Playing && s.current?.key == "hd" }
        assertEquals("hd", engine.played.single().url)
        waitUntil("4k offered") { s.upgrade?.stream != null }
        engine.report(PlayerStatus(playing = true, timeMs = 300_000, lengthMs = 10_000_000, session = engine.currentSession))
        s.acceptUpgrade()
        waitUntil("4k plays") { s.current?.key == "4k" && s.phase is Phase.Playing }
        assertEquals(300_000, engine.played.last().startMs)
        s.close()
    }

    // --- Ranking and start modes

    private fun v(key: String, language: AudioLanguage?, quality: VideoQuality?, readiness: Readiness = Readiness.Unknown, size: Long? = null, origin: VersionOrigin = VersionOrigin.Debrid) =
        PlayVersion(key, origin, key, language, quality, sizeBytes = size, readiness = readiness, announcedAudio = if (language == AudioLanguage.MULTI) listOf("FR", "EN") else emptyList()) { _, _ -> StreamRequest(key, null, null, "t", live = false) }

    @Test
    fun wanting_the_original_a_foreign_dub_comes_after_a_plain_release_even_ready() {
        fun rel(key: String, audio: List<String>, readiness: Readiness) =
            PlayVersion(key, VersionOrigin.Debrid, key, null, VideoQuality.FHD_1080, readiness = readiness, announcedAudio = audio) { _, _ -> StreamRequest(key, null, null, "t", live = false) }
        // "El cuento de la criada ... [spa, eng]" (🇬🇧 / 🇪🇸) against "The.Handmaids.Tale.S04E05.1080p.WEB".
        val all = listOf(rel("spa-eng", listOf("EN", "ES"), Readiness.Ready), rel("plain", emptyList(), Readiness.Likely))
        assertEquals("plain", VersionRanking.rank(all, PlaybackPrefs(audio = AudioPreference.Vo), frenchSystem = true).first().key)
        assertEquals("plain", VersionRanking.rank(all, PlaybackPrefs(audio = AudioPreference.Vostfr), frenchSystem = true).first().key)
        assertEquals("one other language may be the original", "en-only",
            VersionRanking.rank(listOf(rel("en-only", listOf("EN"), Readiness.Ready), rel("plain", emptyList(), Readiness.Likely)), PlaybackPrefs(audio = AudioPreference.Vo), true).first().key)
        // French wanted: a release that names Spanish and English declares no French, so it goes after one that may have it.
        assertEquals("French wanted: declared without French goes after unknown", "plain", VersionRanking.rank(all, PlaybackPrefs(audio = AudioPreference.Vf), true).first().key)
        val dual = PlayVersion("dual", VersionOrigin.Debrid, "S04E005_Episode 5.mkv", null, VideoQuality.FHD_1080, readiness = Readiness.Ready, foreignDub = true) { _, _ -> StreamRequest("dual", null, null, "t", live = false) }
        assertEquals("a Dual Audio release names no language yet is a dub", "plain",
            VersionRanking.rank(listOf(dual, rel("plain", emptyList(), Readiness.Likely)), PlaybackPrefs(audio = AudioPreference.Vo), true).first().key)
        val remembered = { x: PlayVersion -> if (x.key == "spa-eng") com.sokoul.pc.playback.sources.SourceFact(language = AudioLanguage.VO) else null }
        assertEquals("a dub remembered as VO before the fix stays behind", "plain",
            VersionRanking.rank(all, PlaybackPrefs(audio = AudioPreference.Vo), true, fact = remembered).first().key)
    }

    @Test
    fun a_version_picked_by_hand_that_fails_shows_its_failure_instead_of_going_back() {
        val engine = FakeEngine(failing = setOf("https://b.test"))
        val s = session(engine, finder(testVersion("a", "https://a.test"), testVersion("b", "https://b.test", quality = VideoQuality.HD_720)))
        s.play(request)
        waitUntil("a plays") { s.phase is Phase.Playing && s.current?.key == "a" }
        s.choose(s.versions.first { it.key == "b" })
        waitUntil("b failed") { (s.phase as? Phase.Failed)?.version?.key == "b" }
        runBlocking { delay(200) }
        assertEquals("b", (s.phase as Phase.Failed).version?.key)
        assertEquals("nothing else was started", "https://b.test", engine.played.last().url)
        s.close()
    }

    @Test
    fun a_web_result_gives_an_in_flight_debrid_version_time_to_arrive() {
        val engine = FakeEngine()
        val web = testVersion("web", "web", origin = VersionOrigin.Web)
        val debrid = testVersion("rd", "rd", origin = VersionOrigin.Debrid)
        val finder = SourceFinder { _, _ -> flow {
            emit(SearchUpdate(listOf(web), done = false, debridPending = true))
            delay(100)
            emit(SearchUpdate(listOf(web, debrid), done = true))
        } }
        val session = PlaybackSession(engine, finder, scope(), prefs = { frenchFirst },
            budgets = fast.copy(settleMs = 25, debridSettleMs = 250))
        session.play(request)
        waitUntil("Real-Debrid plays") { session.phase is Phase.Playing }
        assertEquals(listOf("rd"), engine.played.map { it.url })
    }

    @Test
    fun ready_comes_first_by_default_and_quality_when_waiting_for_the_best() {
        val all = listOf(v("4k", AudioLanguage.VF, VideoQuality.UHD_4K, Readiness.NotCached), v("hd", AudioLanguage.VF, VideoQuality.FHD_1080, Readiness.Ready))
        assertEquals("hd", VersionRanking.rank(all, PlaybackPrefs(), true).first().key)
        assertEquals("4k", VersionRanking.rank(all, PlaybackPrefs(startMode = StartMode.WaitBest), true).first().key)
    }

    @Test
    fun language_follows_the_wanted_languages_whatever_the_system() {
        // The wanted languages decide, not the system's: the same French system can want French, the original or English.
        val all = listOf(v("vo", AudioLanguage.VO, VideoQuality.FHD_1080), v("vf", AudioLanguage.VF, VideoQuality.FHD_1080))
        fun intent(vararg l: String) = PlaybackPrefs(intent = com.sokoul.desktop.data.AudioIntent(l.toList()))
        val original = com.sokoul.desktop.data.AudioIntent.ORIGINAL
        assertEquals("vf", VersionRanking.rank(all, intent("fr", original), frenchSystem = true).first().key)
        assertEquals("vo", VersionRanking.rank(all, intent(original), frenchSystem = true).first().key)
        assertEquals("an English film, English wanted", "vo", VersionRanking.rank(all, intent("en", original), frenchSystem = true, workLanguage = "en").first().key)
        assertEquals("vo", VersionRanking.rank(all, PlaybackPrefs(audio = AudioPreference.Vo), frenchSystem = true).first().key)
    }

    @Test
    fun a_multi_file_counts_as_the_language_wanted_and_a_sharper_one_wins() {
        val all = listOf(v("sd-vf", AudioLanguage.VF, VideoQuality.SD), v("4k-multi", AudioLanguage.MULTI, VideoQuality.UHD_4K), v("hd-vo", AudioLanguage.VO, VideoQuality.FHD_1080))
        assertEquals(listOf("4k-multi", "sd-vf", "hd-vo"), VersionRanking.rank(all, frenchFirst, frenchSystem = true).map { it.key })
        // A MULTI naming French and English holds the French wanted and the original after it: at equal picture it is as
        // good as the VF and says more of itself.
        val same = listOf(v("multi", AudioLanguage.MULTI, VideoQuality.FHD_1080), v("vf", AudioLanguage.VF, VideoQuality.FHD_1080))
        assertEquals("at equal picture, the release that declares the most wanted languages", "multi", VersionRanking.rank(same, frenchFirst, frenchSystem = true).first().key)
    }

    @Test
    fun a_sharper_version_is_offered_when_it_is_heard_the_same() {
        val playing = v("hd-multi", AudioLanguage.MULTI, VideoQuality.FHD_1080, Readiness.Ready)
        val best = listOf(v("4k-vf", AudioLanguage.VF, VideoQuality.UHD_4K, Readiness.NotCached), v("4k-vo", AudioLanguage.VO, VideoQuality.UHD_4K, Readiness.Ready), playing)
        val heard = { x: PlayVersion -> VersionRanking.heard(x, frenchFirst, true) }
        assertEquals("4k-vf", StartPolicy.upgrade(best, playing, emptySet(), heard)?.key)
        val vfPlaying = v("hd-vf", AudioLanguage.VF, VideoQuality.FHD_1080, Readiness.Ready)
        assertEquals("never to a language less wanted", null, StartPolicy.upgrade(listOf(best[1], vfPlaying), vfPlaying, emptySet(), heard)?.key)
    }

    @Test
    fun after_a_busy_server_only_versions_in_the_account_are_tried() {
        val ranked = listOf(
            v("cached", AudioLanguage.VF, VideoQuality.UHD_4K, Readiness.Likely),
            v("unknown", AudioLanguage.VF, VideoQuality.UHD_4K),
            v("held", AudioLanguage.VF, VideoQuality.FHD_1080, Readiness.Ready),
            v("iptv", AudioLanguage.MULTI, null, Readiness.Ready, origin = VersionOrigin.Iptv),
        )
        val debrid = setOf(VersionOrigin.Debrid)
        assertEquals("cached", StartPolicy.next(ranked, StartMode.ReadyFirst, emptySet(), emptySet(), emptySet())?.version?.key)
        assertEquals("held", StartPolicy.next(ranked, StartMode.ReadyFirst, emptySet(), emptySet(), emptySet(), readyOnly = debrid)?.version?.key)
        assertTrue("no race adds torrents", StartPolicy.race(ranked, StartMode.ReadyFirst, emptySet(), emptySet(), emptySet(), emptySet(), 3, readyOnly = debrid).isEmpty())
        // Nothing in the account: another origin plays, and no Real-Debrid download starts.
        assertEquals("iptv", StartPolicy.next(ranked, StartMode.ReadyFirst, setOf("held"), emptySet(), emptySet(), readyOnly = debrid)?.version?.key)
        assertNull(StartPolicy.next(ranked, StartMode.WaitBest, setOf("held", "iptv"), emptySet(), emptySet(), readyOnly = debrid))
    }

    @Test
    fun a_ready_version_is_never_skipped_by_the_limit_of_tries() {
        val tried = (1..12).map { "t$it" }.toSet()
        val ranked = listOf(v("unknown", AudioLanguage.VF, VideoQuality.FHD_1080), v("iptv", AudioLanguage.MULTI, null, Readiness.Ready, origin = VersionOrigin.Iptv))
        assertEquals("iptv", StartPolicy.next(ranked, StartMode.ReadyFirst, emptySet(), tried, emptySet())?.version?.key)
    }

    @Test
    fun preferred_language_precedes_size_and_quality_limits() {
        val all = listOf(v("vf", AudioLanguage.VF, VideoQuality.UHD_4K, size = 60_000_000_000), v("sub", AudioLanguage.VOSTFR, VideoQuality.HD_720, size = 1_000_000_000))
        assertEquals("vf", VersionRanking.rank(all, frenchFirst.copy(maxSize = MaxSize.G25, maxQuality = MaxQuality.P1080), true).first().key)
    }

    @Test
    fun limits_send_a_version_to_the_end_without_removing_it() {
        val all = listOf(v("big", AudioLanguage.VF, VideoQuality.UHD_4K, size = 60_000_000_000), v("ok", AudioLanguage.VF, VideoQuality.FHD_1080, size = 8_000_000_000))
        assertEquals(listOf("ok", "big"), VersionRanking.rank(all, PlaybackPrefs(maxSize = MaxSize.G25), true).map { it.key })
        assertEquals("ok", VersionRanking.rank(all, PlaybackPrefs(maxQuality = MaxQuality.P1080, maxSize = MaxSize.None), true).first().key)
    }

    @Test
    fun start_policy_tries_what_may_be_ready_then_waits_for_the_best() {
        val ranked = listOf(v("a", AudioLanguage.VF, VideoQuality.FHD_1080, Readiness.NotCached), v("b", AudioLanguage.VF, VideoQuality.HD_720, Readiness.Unknown))
        val first = StartPolicy.next(ranked, StartMode.ReadyFirst, emptySet(), emptySet(), emptySet())
        assertEquals("b", first?.version?.key)
        assertFalse(first!!.keep)
        val then = StartPolicy.next(ranked, StartMode.ReadyFirst, emptySet(), setOf("b"), emptySet())
        assertEquals("a", then?.version?.key)
        assertTrue(then!!.keep)
        assertTrue(StartPolicy.next(ranked, StartMode.WaitBest, emptySet(), emptySet(), emptySet())!!.keep)
        assertNull(StartPolicy.next(ranked, StartMode.Ask, emptySet(), emptySet(), emptySet()))
        assertNull(StartPolicy.next(ranked, StartMode.WaitBest, setOf("a", "b"), emptySet(), emptySet()))
        assertNull(StartPolicy.next(ranked, StartMode.ReadyFirst, emptySet(), emptySet(), setOf(VersionOrigin.Debrid)))
    }

    // --- Failures read from a link

    @Test
    fun a_probe_tells_the_cause() {
        assertEquals(PlayFailure.DeadLink, failureOf(Probe(404, null), film = true))
        assertEquals(PlayFailure.Refused(403), failureOf(Probe(403, null), film = true))
        assertEquals(PlayFailure.ServerBusy, failureOf(Probe(509, null), film = true))
        assertFalse("one busy server leaves the other versions of the origin", PlayFailure.ServerBusy.blocksOrigin)
        assertEquals(PlayFailure.FakeFile, failureOf(Probe(206, 1_000_000), film = true))
        assertNull(failureOf(Probe(206, 1_000_000), film = false))
        assertNull(failureOf(Probe(206, 8_000_000_000), film = true))
        assertNull(failureOf(null, film = true))
    }

    // --- The connection gate

    private fun account(active: Int, max: Int) = XtAccount(true, "Active", null, max, active, false, null, null)

    @Test
    fun the_gate_refuses_a_full_account_and_lets_a_free_one_through() = runBlocking {
        val gate = ConnectionGate(recheckMs = 1)
        gate.clear { account(0, 1) }
        gate.clear { null }
        val refused = runCatching { gate.clear { account(2, 2) } }.exceptionOrNull() as PlayFailureException
        assertEquals(PlayFailure.ConnectionsFull(2, 2), refused.failure)
    }

    @Test
    fun the_gate_waits_for_our_own_stream_to_be_seen_as_closed() = runBlocking {
        val gate = ConnectionGate(gapMs = 1, recheckMs = 1)
        gate.noteStopped()
        var asks = 0
        gate.clear {
            asks++
            if (asks == 1) account(1, 1) else account(0, 1)
        }
        assertEquals(2, asks)
    }

    @Test
    fun the_gate_holds_off_after_a_refusal() = runBlocking {
        var now = 1_000L
        val gate = ConnectionGate(clock = { now })
        gate.noteRefusal()
        val held = runCatching { gate.clear { account(0, 1) } }.exceptionOrNull() as PlayFailureException
        assertTrue(held.failure is PlayFailure.Cooldown)
        now += ConnectionGate.COOLDOWN_MS + 1
        gate.clear { account(0, 1) }
    }

    // --- IPTV titles

    @Test
    fun iptv_entries_match_by_tmdb_id_else_by_title_and_year() {
        val titles = listOf("Dune", "Dune: Part One")
        assertTrue(IptvMatch.matches("FR - Dune (2021) 4K", null, 2021, 438631, titles, 2021))
        assertTrue(IptvMatch.matches("4K-NF| Dune (2021) 3840x1600", null, 2021, 438631, titles, 2021))
        assertFalse(IptvMatch.matches("4K-NF| Dune (1984) 3840x1600", null, 1984, 438631, titles, 2021))
        val gumballTitles = listOf("The Amazing World of Gumball", "Le Monde incroyable de Gumball")
        assertEquals("the amazing world of gumball", IptvMatch.clean("FR| The Amazing World of Gumball"))
        assertTrue(IptvMatch.matches("FR| The Amazing World of Gumball", null, 2011, 37606, gumballTitles, 2011))
        assertEquals(AudioLanguage.VF, ReleaseName.parse("FR| The Amazing World of Gumball").language)
        assertTrue(IptvMatch.matches("[VOSTFR] Dune", "", null, 438631, titles, 2021))
        assertTrue(IptvMatch.matches("Whatever", "438631", null, 438631, titles, 2021))
        assertFalse(IptvMatch.matches("Dune", "99", 2021, 438631, titles, 2021))
        assertFalse("another film of that name", IptvMatch.matches("Dune (1984)", null, 1984, 438631, titles, 2021))
        assertFalse(IptvMatch.matches("Dune Part Two", null, 2024, 438631, titles, 2021))
    }

    @Test
    fun iptv_movie_matching_keeps_numbered_sequels_and_rejects_stale_ids() {
        val titles = listOf("Resident Evil 3", "Resident Evil: Extinction")

        assertTrue(IptvMatch.matches("Resident Evil 3 (2007) VF", null, 2007, 4567, titles, 2007))
        assertFalse(IptvMatch.matches("Resident Evil 1 (2002) VF", null, 2002, 4567, titles, 2007))
        assertFalse(IptvMatch.matches("Resident Evil 2 (2004) VF", null, 2004, 4567, titles, 2007))
        assertFalse(
            "a provider id must not override a different film title",
            IptvMatch.matches("Resident Evil 1 (2002) VF", "4567", 2002, 4567, titles, 2007),
        )
        assertTrue(
            "a matching provider id remains valid when the title is exact",
            IptvMatch.matches("Resident Evil 3 (2007) VF", "4567", 2007, 4567, titles, 2007),
        )
    }

    // --- Real-Debrid through the account

    private fun stream(hash: String) = DebridStream("$hash:-1", "Film", null, hash, null, null, Release())

    private class FakeRd(private val onDownload: () -> String) : Dispatcher() {
        val deleted = mutableListOf<String>()
        var links = 0
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path.orEmpty()
            return when {
                path.startsWith("/torrents?") -> MockResponse().setBody("[]")
                path.startsWith("/torrents/addMagnet") -> MockResponse().setBody("""{"id":"T1"}""")
                path.startsWith("/torrents/selectFiles") -> MockResponse().setResponseCode(204)
                path.startsWith("/torrents/delete") -> {
                    deleted += path
                    MockResponse().setResponseCode(204)
                }
                path.startsWith("/torrents/info") -> MockResponse().setBody(onDownload())
                path.startsWith("/unrestrict/link") -> {
                    links++
                    MockResponse().setBody("""{"download":"https://x.download.real-debrid.com/film.mkv","filename":"film.mkv","filesize":1}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun torrentJson(status: String, progress: Int) =
        """{"id":"T1","filename":"f","hash":"abc","status":"$status","progress":$progress,"bytes":1000,"speed":2000000,"seeders":7,"files":[{"id":1,"path":"/film.mkv","bytes":1000,"selected":1}],"links":["https://real-debrid.com/d/1"]}"""

    private fun transfers(server: MockWebServer) = DebridTransfers(RealDebridApi(OkHttpClient(), { "token" }, server.url("/").toString().trimEnd('/'), spacingMs = 0), pollMs = 5, quickPollMs = 1)

    @Test
    fun a_ready_torrent_gives_a_link_at_once() = runBlocking {
        val server = MockWebServer()
        val rd = FakeRd { torrentJson("downloaded", 100) }
        server.dispatcher = rd
        server.start()
        assertEquals("https://x.download.real-debrid.com/film.mkv", transfers(server).link(stream("abc"), DebridQuery(1, false), false) { })
        assertEquals(1, rd.links)
        assertTrue(rd.deleted.isEmpty())
        server.shutdown()
    }

    @Test
    fun already_active_hash_reconciles_with_later_account_pages_without_adding_a_duplicate() = runBlocking {
        val server = MockWebServer()
        val requests = mutableListOf<String>()
        var accountQueries = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += path
                return when {
                    path.startsWith("/torrents?limit=100") -> { accountQueries++; MockResponse().setBody("[]") }
                    path.startsWith("/torrents/addMagnet") -> MockResponse().setResponseCode(403).setBody("""{"error":"magnet already active","error_code":33}""")
                    path.startsWith("/torrents?limit=5000") -> MockResponse().setBody("[${torrentJson("downloaded", 100)}]")
                    path.startsWith("/torrents/info/T1") -> MockResponse().setBody(torrentJson("downloaded", 100))
                    path.startsWith("/unrestrict/link") -> MockResponse().setBody("""{"download":"https://x.download.real-debrid.com/film.mkv","filename":"film.mkv","filesize":1}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val url = DebridTransfers(RealDebridApi(OkHttpClient(), { "token" }, server.url("/").toString().trimEnd('/'), spacingMs = 0))
            .link(stream("abc"), DebridQuery(1, false), keep = false) { }
        assertEquals("https://x.download.real-debrid.com/film.mkv", url)
        assertEquals(1, accountQueries)
        assertEquals(1, requests.count { it.startsWith("/torrents/addMagnet") })
        assertEquals(1, requests.count { it.startsWith("/torrents?limit=5000") })
        assertFalse(requests.any { it.startsWith("/torrents/delete") })
        server.shutdown()
    }

    @Test
    fun a_pack_already_in_the_account_never_plays_the_episode_selected_last_time() = runBlocking {
        // The Handmaid's Tale S04E06 played the S04E005 file kept from an earlier viewing (2026-11-17).
        val pack = """{"id":"T1","filename":"pack","hash":"abc","status":"downloaded","progress":100,"bytes":1000,"speed":0,"seeders":0,""" +
            """"files":[{"id":5,"path":"/S04/S04E005_Episode 5.mkv","bytes":1000,"selected":1},{"id":6,"path":"/S04/S04E006_Episode 6.mkv","bytes":1000,"selected":0}],"links":["https://real-debrid.com/d/5"]}"""
        val server = MockWebServer()
        val requests = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requests += path
                return when {
                    path.startsWith("/torrents?limit=100") -> MockResponse().setBody("[]")
                    path.startsWith("/torrents/addMagnet") -> MockResponse().setResponseCode(403).setBody("""{"error":"magnet already active","error_code":33}""")
                    path.startsWith("/torrents?limit=5000") -> MockResponse().setBody("[$pack]")
                    path.startsWith("/torrents/info/T1") -> MockResponse().setBody(pack)
                    path.startsWith("/unrestrict/link") -> MockResponse().setBody("""{"download":"https://x.download.real-debrid.com/e5.mkv","filename":"e5.mkv","filesize":1}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val episode6 = DebridStream("abc:6", "pack", null, "abc", 6, "S04E006_Episode 6.mkv", Release())
        val failure = runCatching {
            DebridTransfers(RealDebridApi(OkHttpClient(), { "token" }, server.url("/").toString().trimEnd('/'), spacingMs = 0))
                .link(episode6, DebridQuery(1, true, 4, 6), keep = false) { }
        }.exceptionOrNull()
        assertTrue("no link to episode 5", failure is PlayFailureException)
        assertFalse(requests.any { it.startsWith("/unrestrict/link") })
        server.shutdown()
    }

    @Test
    fun a_file_real_debrid_names_as_another_episode_is_never_played() = runBlocking {
        // The pack's file has no readable episode, but the link Real-Debrid gives for it is S04E05 while S04E06 was asked.
        val pack = """{"id":"T2","filename":"pack","hash":"def","status":"downloaded","progress":100,"bytes":1000,"speed":0,"seeders":0,""" +
            """"files":[{"id":1,"path":"/S04/episode.mkv","bytes":1000,"selected":1}],"links":["https://real-debrid.com/d/1"]}"""
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/torrents?limit=") -> MockResponse().setBody("[$pack]")
                    path.startsWith("/torrents/info/T2") -> MockResponse().setBody(pack)
                    path.startsWith("/unrestrict/link") -> MockResponse().setBody("""{"download":"https://x.download.real-debrid.com/e5.mkv","filename":"The.Handmaids.Tale.S04E05.1080p.mkv","filesize":1}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val episode6 = DebridStream("def:1", "pack", null, "def", 1, "episode.mkv", Release())
        val failure = runCatching {
            DebridTransfers(RealDebridApi(OkHttpClient(), { "token" }, server.url("/").toString().trimEnd('/'), spacingMs = 0))
                .link(episode6, DebridQuery(1, true, 4, 6), keep = false) { }
        }.exceptionOrNull()
        assertEquals(PlayFailure.Doubtful(Doubt.OtherEpisode), (failure as? PlayFailureException)?.failure)
        server.shutdown()
    }

    @Test
    fun a_quick_try_leaves_nothing_in_the_account() = runBlocking {
        val server = MockWebServer()
        val rd = FakeRd { torrentJson("downloading", 12) }
        server.dispatcher = rd
        server.start()
        val failure = runCatching { transfers(server).link(stream("abc"), DebridQuery(1, false), false) { } }.exceptionOrNull() as PlayFailureException
        assertEquals(PlayFailure.NotReady(12), failure.failure)
        assertEquals(1, rd.deleted.size)
        server.shutdown()
    }

    @Test
    fun keeping_a_download_reports_it_until_it_is_ready() = runBlocking {
        val server = MockWebServer()
        var calls = 0
        val rd = FakeRd {
            calls++
            if (calls < 8) torrentJson("downloading", calls * 10) else torrentJson("downloaded", 100)
        }
        server.dispatcher = rd
        server.start()
        val seen = mutableListOf<Transfer>()
        assertEquals("https://x.download.real-debrid.com/film.mkv", transfers(server).link(stream("abc"), DebridQuery(1, false), true) { seen += it })
        assertTrue(seen.isNotEmpty())
        assertEquals(2_000_000, seen.last().speedBps)
        assertEquals(7, seen.last().seeders)
        assertTrue(rd.deleted.isEmpty())
        server.shutdown()
    }

    @Test
    fun a_download_going_nowhere_gives_way_and_leaves_the_account_clean() = runBlocking {
        val server = MockWebServer()
        val rd = FakeRd { torrentJson("downloading", 0) }
        server.dispatcher = rd
        server.start()
        val transfers = DebridTransfers(RealDebridApi(OkHttpClient(), { "token" }, server.url("/").toString().trimEnd('/'), spacingMs = 0), pollMs = 5, quickPollMs = 1, stallMs = 100)
        val failure = runCatching { transfers.link(stream("abc"), DebridQuery(1, false), true) { } }.exceptionOrNull() as PlayFailureException
        assertEquals(PlayFailure.NotReady(0), failure.failure)
        assertEquals(1, rd.deleted.size)
        server.shutdown()
    }

    @Test
    fun real_debrid_codes_become_causes() {
        assertEquals(PlayFailure.KeyRefused, DebridTransfers.failureOf(RdException(401, "x")))
        assertEquals(PlayFailure.DebridDownloadsFull, DebridTransfers.failureOf(RdException(403, "x", 21)))
        assertEquals(PlayFailure.DebridLimit, DebridTransfers.failureOf(RdException(403, "x", 36)))
        assertEquals("asked to slow down is not a quota", PlayFailure.DebridBusy, DebridTransfers.failureOf(RdException(429, "x", 34)))
        // 35, "infringing_file" (HTTP 451): blocked by a rights holder, for good; remembered a month, not a day.
        assertEquals(PlayFailure.Blocked, DebridTransfers.failureOf(RdException(451, "x", 35)))
        assertEquals(PlayFailure.DeadLink, DebridTransfers.failureOf(RdException(403, "x", 28)))
    }

    @Test
    fun transfer_estimates_the_time_left() {
        assertEquals(50L, Transfer(50, speedBps = 10_000_000, bytes = 1_000_000_000).etaSeconds)
        assertNull(Transfer(50).etaSeconds)
    }

    // --- Enrichment services

    private fun json(text: String) = kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject

    @Test
    fun wikipedia_only_accepts_a_page_carrying_the_saga_name() {
        val wiki = com.sokoul.pc.data.providers.WikipediaClient
        fun results(vararg titles: String) = json("""{"query":{"search":[${titles.joinToString(",") { """{"title":"$it"}""" }}]}}""")
        assertEquals("Star Wars", wiki.bestTitle(results("Unrelated", "Star Wars, épisode IV : Un nouvel espoir", "Star Wars"), "Star Wars"))
        assertEquals("the saga's page before the film of the same name", "Rocky (série de films)", wiki.bestTitle(results("Rocky", "Rocky (série de films)", "Rocky Balboa"), "Rocky"))
        assertNull("another subject", wiki.bestTitle(results("Rocky Balboa", "Rocky IV"), "Rocky"))
        fun page(text: String) = json("""{"query":{"pages":{"1":{"extract":"$text"}}}}""")
        val long = "x".repeat(300)
        assertEquals(long, wiki.extract(page(long)))
        assertNull("too short", wiki.extract(page("short")))
        assertNull("a disambiguation page tells nothing", wiki.extract(page("Rocky peut désigner : $long")))
    }

    @Test
    fun fanart_watch_offers_taste_and_next_episode_are_read() {
        val art = com.sokoul.pc.data.providers.FanartClient.parse(json("""{"moviebackground":[{"url":"https://a/1 2.jpg","likes":"1"},{"url":"https://a/3.jpg","likes":"9"},{"url":"http://bad","likes":"99"}]}"""))
        assertEquals(listOf("https://a/3.jpg", "https://a/1%202.jpg"), art.backgrounds)
        val offers = com.sokoul.pc.data.providers.MovieOfTheNightClient.parse(json("""{"streamingOptions":{"fr":[{"type":"rent","service":{"name":"Apple"}},{"type":"subscription","service":{"name":"Netflix"}},{"type":"buy","service":{"name":"Netflix"}}]}}"""), "fr")
        assertEquals(listOf("Netflix", "Apple"), offers.map { it.service })
        assertEquals(listOf("A", "B"), com.sokoul.pc.data.providers.TasteDiveClient.parse(json("""{"similar":{"results":[{"name":"A"},{"name":"B"}]}}""")))
        assertEquals(com.sokoul.pc.data.providers.NextAiring(2, 5, "2027-01-03"), com.sokoul.pc.data.providers.TvmazeClient.parseNext(json("""{"_embedded":{"nextepisode":{"season":2,"number":5,"airdate":"2027-01-03"}}}""")))
    }

    @Test
    fun a_refused_key_is_not_asked_again_and_the_cache_answers_first() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.start()
        val store = HashMap<String, Pair<String, Long>>()
        val http = com.sokoul.pc.data.providers.ProviderHttp(OkHttpClient(), { store[it] }, { k, t, at -> store[k] = t to at })
        assertNull(http.get("svc", server.url("/a"), "a", 1000))
        assertNull(http.get("svc", server.url("/a"), "a", 1000))
        assertEquals("paused after the refusal", 1, server.requestCount)
        store["other:b"] = """{"x":1}""" to System.currentTimeMillis()
        assertTrue(http.get("other", server.url("/b"), "b", 1000) != null)
        assertEquals("answered by the cache", 1, server.requestCount)
        server.shutdown()
    }

    @Test
    fun a_saga_opens_only_the_rooms_it_can_fill() {
        val empty = com.sokoul.pc.data.SagaFacts.of(null, emptyList())
        assertTrue(com.sokoul.pc.collections.roomsOf(empty, emptyList()).isEmpty())
        val film = com.sokoul.pc.data.FilmFacts(sampleItem(1, "movie"), null, null)
        assertEquals(listOf(com.sokoul.pc.collections.SagaRoom.Work, com.sokoul.pc.collections.SagaRoom.Films), com.sokoul.pc.collections.roomsOf(com.sokoul.pc.data.SagaFacts.of("Story", listOf(film)), emptyList()))
        assertEquals(listOf(com.sokoul.pc.collections.SagaRoom.Next), com.sokoul.pc.collections.roomsOf(null, listOf(sampleItem(1, "tv"))))
    }

    // --- The last services

    private fun cue(start: Long, end: Long, text: String) = com.sokoul.pc.data.providers.Cue(start, end, text)

    @Test
    fun the_right_subtitle_line_shows_at_each_moment() {
        val cues = listOf(cue(1000, 2000, "a"), cue(3000, 4000, "b"))
        val at = com.sokoul.pc.data.providers.CueLookup::at
        assertNull(at(cues, 500))
        assertEquals("a", at(cues, 1000))
        assertNull(at(cues, 2500))
        assertEquals("b", at(cues, 3999))
        assertNull(at(cues, 4000))
        assertNull(at(emptyList(), 1))
    }

    @Test
    fun a_subtitle_is_fetched_only_when_chosen_and_never_touches_the_stream() {
        var searches = 0
        val fetched = mutableListOf<String>()
        val found = listOf(
            com.sokoul.pc.data.providers.SubtitleResult("s", "ok", "fr", "r", false),
            com.sokoul.pc.data.providers.SubtitleResult("s", "quota", "en", null, false),
        )
        val online = com.sokoul.pc.playback.sources.OnlineSubtitles(
            object : com.sokoul.pc.playback.sources.SubtitleSource {
                override fun available() = true
                override suspend fun search(request: PlayRequest) = found.also { searches++ }
                override suspend fun cues(result: com.sokoul.pc.data.providers.SubtitleResult): List<com.sokoul.pc.data.providers.Cue> {
                    fetched += result.ref
                    return if (result.ref == "ok") listOf(cue(1000, 2000, "line")) else emptyList()
                }
            },
            scope(),
        )
        online.load(request)
        online.load(request)
        waitUntil("found") { online.results.size == 2 }
        assertEquals(1, searches)
        assertTrue(fetched.isEmpty())
        online.choose(found[1])
        waitUntil("failed") { "quota" in online.failed }
        assertNull(online.active)
        online.choose(found[0])
        waitUntil("active") { online.active != null }
        assertEquals("line", online.lineAt(1500))
        online.shift(1000)
        assertNull("shown later", online.lineAt(1500))
        assertEquals("line", online.lineAt(2500))
        online.clear()
        assertNull(online.lineAt(2500))
    }

    @Test
    fun subtitle_services_are_read_and_the_key_never_stays_in_a_link() {
        val os = com.sokoul.pc.data.providers.SubtitlesClient.parseOpenSubtitles(json("""{"data":[{"attributes":{"language":"FR","release":"Dune","hearing_impaired":true,"files":[{"file_id":42}]}}]}"""))
        assertEquals(com.sokoul.pc.data.providers.SubtitleResult("opensubtitles", "42", "fr", "Dune", true), os.single())
        val sd = com.sokoul.pc.data.providers.SubtitlesClient.parseSubdl(json("""{"status":true,"subtitles":[{"url":"/subtitle/1.zip?api_key=SECRET","lang":"FR","release_name":"x","hi":false},{"url":"/../evil","lang":"FR"}]}"""))
        assertEquals("/subtitle/1.zip", sd.single().ref)
        assertTrue(com.sokoul.pc.data.providers.SubtitlesClient.parseSubdl(json("""{"status":false}""")).isEmpty())
        val ranked = com.sokoul.pc.data.providers.SubtitlesClient.rank(listOf(sd.single().copy(language = "en"), sd.single()), listOf("fr", "en"))
        assertEquals("fr", ranked.first().language)
    }

    @Test
    fun a_downloaded_subtitle_survives_the_round_trip_through_the_cache() {
        val cues = listOf(cue(1_000, 2_500, "Bonjour\nà tous"), cue(3_661_001, 3_662_000, "Fin"))
        val text = com.sokoul.pc.data.providers.SubtitlesClient.toSrt(cues)
        assertEquals(cues, com.sokoul.pc.data.providers.SubtitleFile.parse(text))
    }

    @Test
    fun tvdb_fills_only_what_tmdb_lacks() {
        val fill = com.sokoul.pc.data.providers.EpisodeFill
        val full = com.sokoul.iptv.data.net.TmdbEpisode(1, 1, "Pilot", "Plot", "still", 40, "2020-01-01")
        val poor = com.sokoul.iptv.data.net.TmdbEpisode(1, 2, "Épisode 2", null, null, 40, "2020-01-01")
        assertFalse(fill.needed(listOf(full)))
        assertTrue(fill.needed(listOf(full, poor)))
        val tv = listOf(
            com.sokoul.pc.data.providers.TvdbEpisode(1, 1, "Other", "Other plot", "other", ),
            com.sokoul.pc.data.providers.TvdbEpisode(1, 2, "Vrai nom", "Résumé", "image"),
        )
        val out = fill.fill(listOf(full, poor), tv)
        assertEquals(full, out[0])
        assertEquals("Vrai nom", out[1].name)
        assertEquals("Résumé", out[1].overview)
        assertEquals("image", out[1].still)
        val parsed = com.sokoul.pc.data.providers.TvdbClient.parseEpisodes(json("""{"data":{"episodes":[{"seasonNumber":0,"number":1},{"seasonNumber":1,"number":2,"name":" N ","image":"/a.jpg"}]}}"""))
        assertEquals(com.sokoul.pc.data.providers.TvdbEpisode(1, 2, "N", null, "https://artworks.thetvdb.com/a.jpg"), parsed.single())
    }

    @Test
    fun watchmode_keeps_the_region_and_one_line_per_service() {
        val sources = kotlinx.serialization.json.Json.parseToJsonElement("""[{"name":"Netflix","type":"sub","region":"FR"},{"name":"Netflix","type":"buy","region":"FR"},{"name":"Hulu","type":"sub","region":"US"},{"name":"Orange","type":"rent","region":"FR"}]""") as kotlinx.serialization.json.JsonArray
        val offers = com.sokoul.pc.data.providers.WatchmodeClient.parse(sources, "FR")
        assertEquals(listOf("Netflix", "Orange"), offers.map { it.service })
        assertTrue(offers.first().subscription)
    }

    // --- Trailers

    private fun video(key: String, lang: String?, name: String = "Trailer", official: Boolean = true, type: String = "Trailer", size: Int = 1080) = TmdbVideo(key, name, type, size, official, lang)

    @Test
    fun the_original_language_trailer_wins_whatever_the_system() {
        val sorted = TrailerOrder.sort(listOf(video("fr", "fr", "Bande-annonce VF"), video("en", "en"), video("teaser", "en", type = "Teaser")), "en")
        assertEquals(listOf("en", "teaser", "fr"), sorted.map { it.key })
    }

    @Test
    fun a_local_reupload_comes_last_even_tagged_in_english() {
        val sorted = TrailerOrder.sort(listOf(video("canal", "en", "Dune - Canal+ HD"), video("real", "en", official = false)), "en")
        assertEquals("real", sorted.first().key)
    }

    @Test
    fun a_french_film_keeps_its_french_trailer() {
        assertEquals("fr", TrailerOrder.sort(listOf(video("en", "en"), video("fr", "fr", "Bande-annonce VF")), "fr").first().key)
    }

    @Test
    fun the_sharpest_trailer_wins_and_unknown_height_ranks_last() = runBlocking {
        val heights = mapOf("a" to null, "b" to 720, "c" to 1080, "d" to 1080)
        val asked = mutableListOf<String>()
        val resolver = TrailerResolver(OkHttpClient(), { null }, { key ->
            asked += key
            TrailerStream("https://v.test/$key", null, "yt-dlp", heights[key])
        })
        assertEquals("c", resolver.best(listOf("a", "b", "c", "d"))?.key)
        assertEquals(listOf("a", "b", "c"), asked)
    }
}
