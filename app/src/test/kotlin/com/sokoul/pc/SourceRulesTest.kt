package com.sokoul.pc

import com.sokoul.desktop.data.PlaybackPrefs
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.pc.playback.sources.DeviceCaps
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.sources.SourceFact
import com.sokoul.pc.playback.sources.SourceMemory
import com.sokoul.pc.playback.ui.isAccountReady
import com.sokoul.pc.playback.sources.SourceRules
import com.sokoul.pc.playback.sources.StartPolicy
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.sources.VersionRanking
import com.sokoul.desktop.data.StartMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRulesTest {
    @Test fun `track languages include ISO names outside French and English`() {
        assertEquals("pl", com.sokoul.desktop.player.AudioChoice.languageOf("Polski [pol] · 5.1"))
        assertEquals("sv", com.sokoul.desktop.player.AudioChoice.languageOf("Svenska [sv]"))
        assertNull(com.sokoul.desktop.player.AudioChoice.languageOf("Track 2 · 7.1"))
    }
    private val gb = 1_000_000_000L
    /** French first, then the original: fixed so that no test depends on the machine's language. */
    private val frenchFirst = PlaybackPrefs(intent = com.sokoul.desktop.data.AudioIntent(listOf("fr", com.sokoul.desktop.data.AudioIntent.ORIGINAL)))

    private fun v(
        key: String,
        q: VideoQuality? = VideoQuality.FHD_1080,
        hdr: HdrFormat? = null,
        lang: AudioLanguage? = AudioLanguage.VF,
        size: Long? = 4 * gb,
        host: String? = "a",
        origin: VersionOrigin = VersionOrigin.Debrid,
    ) = PlayVersion(key, origin, key, lang, q, hdr, sizeBytes = size, readiness = Readiness.Ready, providerHost = host, open = { _, _ -> error("not opened") })

    private val tv = DeviceCaps(hdr10 = true, dolbyVision = true, maxHeight = 2160)
    private val tvHd = DeviceCaps(hdr10 = true, dolbyVision = false, maxHeight = 2160)
    private val keys = { l: List<PlayVersion> -> l.map { it.key } }

    @Test
    fun a_tv_that_shows_hdr_prefers_the_sharpest_then_the_richest() {
        val list = listOf(v("sdr1080"), v("sdr4k", VideoQuality.UHD_4K), v("hdr4k", VideoQuality.UHD_4K, HdrFormat.HDR10), v("dv4k", VideoQuality.UHD_4K, HdrFormat.DOLBY_VISION))
        assertEquals(listOf("dv4k", "hdr4k", "sdr4k", "sdr1080"), keys(VersionRanking.rank(list, frenchFirst, true, caps = tv)))
    }

    @Test
    fun a_format_the_device_cannot_show_comes_after_plain_picture() {
        val list = listOf(v("dv4k", VideoQuality.UHD_4K, HdrFormat.DOLBY_VISION), v("sdr1080"))
        assertEquals(listOf("sdr1080", "dv4k"), keys(VersionRanking.rank(list, frenchFirst, true, caps = tvHd)))
        assertEquals("windows keeps sdr first", listOf("sdr1080", "dv4k"), keys(VersionRanking.rank(list, frenchFirst, true)))
    }

    @Test
    fun what_a_viewing_proved_counts_over_what_a_name_announces() {
        val lied = v("lied", VideoQuality.UHD_4K, lang = AudioLanguage.VF)
        val honest = v("honest", lang = AudioLanguage.VF)
        val facts = mapOf("lied" to SourceFact(language = AudioLanguage.VO))
        assertEquals(listOf("honest", "lied"), keys(VersionRanking.rank(listOf(lied, honest), frenchFirst, true, fact = { facts[it.key] })))
    }

    @Test
    fun a_version_that_failed_for_a_lasting_reason_goes_last() {
        val facts = mapOf("bad" to SourceFact(failure = PlayFailure.FakeFile, until = Long.MAX_VALUE))
        assertEquals(listOf("good", "bad"), keys(VersionRanking.rank(listOf(v("bad"), v("good")), frenchFirst, true, fact = { facts[it.key] })))
    }

    @Test
    fun a_failure_that_passed_no_longer_counts() {
        var now = 1000L
        val mem = SourceMemory({ null }, {}) { now }
        mem.noteFailure("x", PlayFailure.TimedOut)
        assertEquals(PlayFailure.TimedOut, mem.failure("x"))
        now += SourceRules.memoryMs(PlayFailure.TimedOut) + 1
        assertNull(mem.failure("x"))
    }

    @Test
    fun a_network_failure_says_nothing_of_the_version() {
        val mem = SourceMemory({ null }, {})
        mem.noteFailure("x", PlayFailure.Network)
        assertNull(mem.failure("x"))
    }

    @Test
    fun memory_survives_a_restart_and_playing_clears_it() {
        var stored: String? = null
        val a = SourceMemory({ stored }, { stored = it })
        a.noteFailure("x", PlayFailure.Refused(403))
        val b = SourceMemory({ stored }, { stored = it })
        assertEquals(PlayFailure.Refused(403), b.failure("x"))
        b.noteGood("x", AudioLanguage.MULTI, 2160)
        val c = SourceMemory({ stored }, { stored = it })
        assertNull(c.failure("x"))
        assertEquals(AudioLanguage.MULTI, c.fact("x")?.language)
        assertEquals(2160, c.fact("x")?.height)
    }

    @Test
    fun forgetting_failures_keeps_metadata_and_saves_only_changes() {
        var stored: String? = null
        var writes = 0
        val mem = SourceMemory({ stored }, { stored = it; writes++ })
        mem.noteGood("movie|wrong", AudioLanguage.MULTI, 2160, listOf("fr"))
        mem.noteFailure("movie|wrong", PlayFailure.WrongLanguage)
        mem.noteFailure("other|fake", PlayFailure.FakeFile)
        val before = writes

        mem.forgetFailures()

        assertNull(mem.failure("movie|wrong"))
        assertNull(mem.failure("other|fake"))
        assertEquals(0L, mem.fact("movie|wrong")?.until)
        assertEquals(AudioLanguage.MULTI, mem.fact("movie|wrong")?.language)
        assertEquals(2160, mem.fact("movie|wrong")?.height)
        assertEquals(listOf("fr"), mem.fact("movie|wrong")?.codes)
        assertEquals(before + 1, writes)
        mem.forgetFailures()
        assertEquals(before + 1, writes)
        val restored = SourceMemory({ stored }, {})
        assertNull(restored.failure("movie|wrong"))
        assertEquals(listOf("fr"), restored.fact("movie|wrong")?.codes)
    }

    @Test
    fun fresh_search_clears_only_temporary_failures_for_the_title() {
        var stored: String? = null
        val mem = SourceMemory({ stored }, { stored = it })
        mem.noteFailure("movie|dead", PlayFailure.DeadLink)
        mem.noteGood("movie|wrong", AudioLanguage.VO, 1080)
        mem.noteFailure("movie|wrong", PlayFailure.WrongLanguage)
        mem.noteFailure("other|dead", PlayFailure.DeadLink)

        mem.noteFailure("movie|cut", PlayFailure.Cut)

        mem.clearTransientFailures("movie|")

        assertNull(mem.failure("movie|cut"))
        // A dead link stays hidden for its day, even through a fresh search.
        assertEquals(PlayFailure.DeadLink, mem.failure("movie|dead"))
        assertEquals(PlayFailure.WrongLanguage, mem.failure("movie|wrong"))
        assertEquals(AudioLanguage.VO, mem.fact("movie|wrong")?.language)
        assertEquals(PlayFailure.DeadLink, mem.failure("other|dead"))
    }

    @Test
    fun retrying_dead_links_clears_only_them_for_the_title() {
        var now = 0L
        var stored: String? = null
        val mem = SourceMemory({ stored }, { stored = it }, { now })
        mem.noteFailure("movie|dead", PlayFailure.DeadLink)
        mem.noteFailure("movie|copy", PlayFailure.Copyright)
        mem.noteFailure("movie|fake", PlayFailure.FakeFile)
        mem.noteFailure("other|dead", PlayFailure.DeadLink)

        // Read back from disk: the copyright refusal keeps its own cause.
        assertEquals(PlayFailure.Copyright, SourceMemory({ stored }, { }, { now }).failure("movie|copy"))
        now += SourceRules.memoryMs(PlayFailure.DeadLink) + 1
        assertNull(mem.failure("movie|dead"))
        assertEquals(PlayFailure.Copyright, mem.failure("movie|copy"))

        mem.clearDeadLinks("movie|")

        assertNull(mem.failure("movie|copy"))
        assertEquals(PlayFailure.FakeFile, mem.failure("movie|fake"))
        assertEquals(PlayFailure.DeadLink, mem.fact("other|dead")?.failure)
    }

    @Test
    fun dead_links_only_show_among_the_dead() {
        val ok = v("ok")
        val doubtful = PlayVersion("doubt", VersionOrigin.Debrid, "Another work", rejected = com.sokoul.pc.playback.session.Doubt.OtherWork, open = { _, _ -> error("not opened") })
        val eligibility = com.sokoul.pc.playback.ui.SourceEligibility.entries
        assertEquals(listOf(com.sokoul.pc.playback.ui.SourceEligibility.Compatible), eligibility.filter { it.keeps(ok) })
        assertEquals(listOf(com.sokoul.pc.playback.ui.SourceEligibility.Excluded), eligibility.filter { it.keeps(doubtful) })
        assertEquals(listOf(com.sokoul.pc.playback.ui.SourceEligibility.Dead), eligibility.filter { it.keeps(ok, dead = true) })
        assertEquals(listOf(com.sokoul.pc.playback.ui.SourceEligibility.Dead), eligibility.filter { it.keeps(doubtful, dead = true) })
        assertTrue(SourceRules.dead(PlayFailure.Copyright))
        assertFalse(SourceRules.dead(PlayFailure.Cut))
    }

    @Test
    fun account_ready_excludes_direct_provider_links() {
        val rd = v("rd")
        val iptv = v("iptv", origin = VersionOrigin.Iptv)
        assertTrue(isAccountReady(rd))
        assertFalse(isAccountReady(iptv))
        assertFalse(isAccountReady(PlayVersion("rejected", VersionOrigin.Debrid, "Rejected", readiness = Readiness.Ready, rejected = com.sokoul.pc.playback.session.Doubt.WrongLength, open = { _, _ -> error("not opened") })))
    }

    @Test
    fun a_file_far_from_the_length_of_the_work_is_not_the_work() {
        assertTrue("half a film", SourceRules.wrongLength("movie", 148, 60 * 60_000L))
        assertTrue("another work", SourceRules.wrongLength("movie", 90, 180 * 60_000L))
        assertFalse("an extended cut", SourceRules.wrongLength("movie", 148, 180 * 60_000L))
        assertFalse("unknown length", SourceRules.wrongLength("movie", null, 60 * 60_000L))
        assertTrue("a clip for an episode", SourceRules.wrongLength("tv", 45, 5 * 60_000L))
        assertFalse("a double episode", SourceRules.wrongLength("tv", 24, 48 * 60_000L))
        assertTrue("the live-action series for an anime episode", SourceRules.wrongLength("tv", 24, 68 * 60_000L))
        assertTrue("an hour for an anime episode", SourceRules.wrongLength("tv", 24, 60 * 60_000L, anime = true))
        assertFalse("a double anime episode", SourceRules.wrongLength("tv", 24, 48 * 60_000L, anime = true))
        assertFalse("an hour for a drama episode", SourceRules.wrongLength("tv", 24, 60 * 60_000L))
    }

    @Test
    fun a_size_impossible_for_its_quality_is_suspicious() {
        assertTrue(SourceRules.suspicious(v("fake4k", VideoQuality.UHD_4K, size = 1 * gb), 120))
        assertFalse(SourceRules.suspicious(v("real4k", VideoQuality.UHD_4K, size = 15 * gb), 120))
        assertFalse("unknown length", SourceRules.suspicious(v("x", VideoQuality.UHD_4K, size = 1 * gb), null))
        val ranked = VersionRanking.rank(listOf(v("fake4k", VideoQuality.UHD_4K, size = 1 * gb), v("real1080", size = 6 * gb)), frenchFirst, true, caps = tv, expectedMinutes = 120)
        assertEquals("real1080", ranked.first().key)
    }

    @Test
    fun the_tracks_say_what_is_heard() {
        assertEquals(AudioLanguage.MULTI, SourceRules.heardLanguage(listOf(1 to "Français · 5.1", 2 to "Anglais · 5.1")))
        assertEquals(AudioLanguage.VF, SourceRules.heardLanguage(listOf(1 to "[French]")))
        assertEquals(AudioLanguage.VO, SourceRules.heardLanguage(listOf(1 to "Japonais · Stéréo")))
        assertNull("nothing named", SourceRules.heardLanguage(listOf(1 to "Piste 1")))
        assertNull("a dub beside the original", SourceRules.heardLanguage(listOf(1 to "Español · 5.1", 2 to "English · 5.1")))
        assertNull("nothing proved", SourceRules.heardLanguage(listOf(1 to "English", 2 to "Piste 2")))
    }

    @Test
    fun distinct_sources_are_not_removed_by_size_or_host_similarity() {
        val list = listOf(v("a1", host = "a"), v("a2", host = "a"), v("b1", host = "b"), v("c1", host = "c"), v("other", size = 9 * gb))
        assertEquals(keys(list), keys(SourceRules.dedupe(list + list.first())))
    }

    @Test
    fun wanted_audio_precedes_preferred_provider() {
        val vo = v("iptv-original", lang = AudioLanguage.VO, origin = VersionOrigin.Iptv)
        val vf = v("french", lang = AudioLanguage.VF)
        val prefs = PlaybackPrefs(audio = com.sokoul.desktop.data.AudioPreference.Vf, origin = com.sokoul.desktop.data.PreferredOrigin.Iptv)
        assertEquals(listOf("french", "iptv-original"), keys(VersionRanking.rank(listOf(vo, vf), prefs, true)))
    }

    @Test
    fun generic_multi_does_not_outrank_explicit_french_without_french_evidence() {
        val multi = v("multi", q = VideoQuality.UHD_4K, lang = AudioLanguage.MULTI)
        val vf = v("vf", q = VideoQuality.SD)
        assertEquals(listOf("vf", "multi"), keys(VersionRanking.rank(listOf(multi, vf), frenchFirst, true)))
        assertEquals(listOf("multi", "vf"), keys(VersionRanking.rank(listOf(multi, vf), frenchFirst, true,
            fact = { if (it.key == "multi") SourceFact(language = AudioLanguage.MULTI) else null })))
    }

    @Test
    fun french_filter_does_not_claim_unverified_multi_is_french_audio() {
        val filter = com.sokoul.pc.playback.ui.SourceLanguageFilter.French
        assertTrue(filter.keeps(AudioLanguage.VF))
        assertFalse(filter.keeps(AudioLanguage.MULTI))
        assertFalse(filter.keeps(null))
    }

    @Test
    fun compact_episode_size_remains_readable() {
        assertEquals("20 MB", com.sokoul.pc.playback.session.sizeLabel(20_000_000))
        assertNull(com.sokoul.pc.playback.session.sizeLabel(null))
    }

    @Test
    fun excluded_sources_stay_inspectable_without_polluting_compatible_choices() {
        val valid = v("valid")
        val wrong = PlayVersion("wrong", VersionOrigin.Debrid, "Another episode", rejected = com.sokoul.pc.playback.session.Doubt.OtherEpisode, open = { _, _ -> error("not opened") })
        val all = listOf(valid, wrong)
        assertEquals(listOf(valid), all.filter(com.sokoul.pc.playback.ui.SourceEligibility.Compatible::keeps))
        assertEquals(listOf(wrong), all.filter(com.sokoul.pc.playback.ui.SourceEligibility.Excluded::keeps))
    }

    @Test
    fun after_a_bad_link_the_next_try_comes_from_another_service() {
        val ranked = listOf(v("a2", host = "a"), v("b1", host = "b"))
        assertEquals("b1", StartPolicy.next(ranked, StartMode.ReadyFirst, setOf("a1"), setOf("a1"), emptySet(), setOf("a"))?.version?.key)
        assertEquals("the only service left is still tried", "a2", StartPolicy.next(ranked.take(1), StartMode.ReadyFirst, emptySet(), emptySet(), emptySet(), setOf("a"))?.version?.key)
        assertTrue(SourceRules.blamesHost(PlayFailure.FakeFile))
        assertFalse(SourceRules.blamesHost(PlayFailure.Network))
    }

    @Test
    fun a_season_pack_never_gives_another_episode() {
        val p = com.sokoul.iptv.data.debrid.ReleaseName
        fun f(id: Int, name: String) = com.sokoul.iptv.data.debrid.RdFile(id, "/$name", 1_000_000, true)
        assertEquals("the very bug: only episode 1 chosen in the account", null, p.pickFile(listOf(f(1, "Ted.Lasso.S01E01.mkv")), 1, 2))
        assertEquals(1, p.pickFile(listOf(f(1, "Ted.Lasso.S01E01.mkv")), 1, 1)?.id)
        assertEquals(2, p.pickFile(listOf(f(1, "Ted.Lasso.S01E01.mkv"), f(2, "Ted.Lasso.S01E02.mkv")), 1, 2)?.id)
        assertEquals("a file with no episode in its name may be it", 3, p.pickFile(listOf(f(3, "episode.mkv")), 1, 2)?.id)
    }
}
