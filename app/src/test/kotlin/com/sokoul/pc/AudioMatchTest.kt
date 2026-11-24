package com.sokoul.pc

import com.sokoul.desktop.data.AudioFallback
import com.sokoul.desktop.data.AudioIntent
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.PlaybackPrefs
import com.sokoul.desktop.data.SubtitlePreference
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.sources.AudioMatch
import com.sokoul.pc.playback.sources.AudioProof
import com.sokoul.pc.playback.sources.DeviceCaps
import com.sokoul.pc.playback.sources.SourceFact
import com.sokoul.pc.playback.sources.SourcePreferenceOrder
import com.sokoul.pc.playback.sources.SourcePreferences
import com.sokoul.pc.playback.sources.VersionRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Scenarios of openspec/changes/rework-sources-audio-start/specs/playback-audio-selection, for any language. */
class AudioMatchTest {
    private val original = AudioIntent.ORIGINAL
    private fun prefs(vararg languages: String) = PlaybackPrefs(intent = AudioIntent(languages.toList()))

    private fun version(key: String, name: String, language: AudioLanguage? = null, audio: List<String> = emptyList(), dub: Boolean = false, origin: VersionOrigin = VersionOrigin.Debrid, size: Long? = null) =
        PlayVersion(key, origin, name, language = language, readiness = Readiness.Ready, announcedAudio = audio, foreignDub = dub, sizeBytes = size,
            open = { _, _ -> StreamRequest("https://fixture.test/a.mkv", null, null, "Fixture") })

    // --- Tracks

    @Test fun frenchPreferredInAFileWhoseDefaultTrackIsEnglish() {
        val tracks = listOf(1 to "English [en] (default)", 2 to "Français [fr]", 3 to "Italiano [it]")
        assertEquals(2, AudioMatch.pickTrack(tracks, listOf("fr", original), "en"))
    }

    @Test fun franceFrenchBeforeQuebecFrenchWhenBothAreThere() {
        val tracks = listOf(1 to "English · [en] · 5.1", 2 to "Français · [fr-ca] · 5.1", 3 to "Français · [fr-fr] · 5.1")
        assertEquals(3, AudioMatch.pickTrack(tracks, listOf("fr", original), "en"))
        assertEquals(2, AudioMatch.pickTrack(listOf(1 to "English [en]", 2 to "Français VFQ"), listOf("fr", original), "en"))
    }

    @Test fun italianPreferredOnAnyInterfacePicksTheItalianTrack() {
        val tracks = listOf(1 to "English [en] (default)", 2 to "Français [fr]", 3 to "Italiano [it]")
        assertEquals(3, AudioMatch.pickTrack(tracks, listOf("it", original), "en"))
        // Without Italian, the original (the work's language) comes next.
        assertEquals(1, AudioMatch.pickTrack(tracks.filterNot { it.first == 3 }, listOf("it", original), "en"))
    }

    @Test fun aDubListedBeforeTheOriginalIsNotTakenForIt() {
        val tracks = listOf(1 to "Français", 2 to "Español", 3 to "English")
        assertEquals(3, AudioMatch.pickTrack(tracks, listOf(original), "en"))
    }

    @Test fun inceptionRemuxPicksTheSixthTrackForFrenchAndTheFirstForTheOriginal() {
        // Inception.2010.1080p.BluRay.REMUX.ENG.LATINO.CASTELLANO.HINDI.ITA.GER.FRE…: English by default, French sixth.
        val tracks = listOf("English", "English", "Spanish", "Spanish", "Hindi", "French", "Italian", "German", "Chinese", "Russian", "Thai", "Ukrainian")
            .mapIndexed { i, lang -> i + 1 to "BTM DD 5.1 - [$lang]" }
        assertEquals(6, AudioMatch.pickTrack(tracks, listOf("fr", original), "en"))
        assertEquals(1, AudioMatch.pickTrack(tracks, listOf(original), "en"))
        assertEquals(7, AudioMatch.pickTrack(tracks, listOf("it", original), "en"))
    }

    @Test fun frenchPreferredSkipsACommentaryTrack() {
        val tracks = listOf(1 to "Français - Commentaire du réalisateur", 2 to "Français 5.1", 3 to "English")
        assertEquals(2, AudioMatch.pickTrack(tracks, listOf("fr", original), "en"))
    }

    @Test fun originalLabelOnEnglishDoesNotBeatTheJapaneseTrackOfAJapaneseWork() {
        val tracks = listOf(1 to "English (Original)", 2 to "Japanese [ja]")
        assertEquals(2, AudioMatch.pickTrack(tracks, listOf(original), "ja"))
    }

    @Test fun withoutTheWorksLanguageTheFilesOwnHintsDecide() {
        // A track named Original (PR #5).
        assertEquals(2, AudioMatch.pickTrack(listOf(1 to "English [en]", 2 to "Japanese [ja] Original"), listOf(original), null))
        // Italian wanted first: the Italian track, whatever the original is.
        assertEquals(1, AudioMatch.pickTrack(listOf(1 to "Italiano", 2 to "English"), listOf("it", original), null))
        // Original wanted after French, in a MULTI holding French: the other track is the original one (PR #2).
        assertEquals(2, AudioMatch.pickTrack(listOf(1 to "Français", 2 to "English"), listOf(original), null))
        // No clue at all: the engine's choice stays.
        assertNull(AudioMatch.pickTrack(listOf(1 to "Spanish", 2 to "English"), listOf(original), null))
    }

    @Test fun nothingWantedInTheFileKeepsTheEngineChoice() {
        assertNull(AudioMatch.pickTrack(listOf(1 to "Spanish", 2 to "German"), listOf("it"), "en"))
    }

    // --- Versions

    @Test fun anItalianWorkWithItalianAndEnglishHoldsTheOriginal() {
        val release = version("ita", "Film.2019.ITA.ENG.1080p", audio = listOf("IT", "EN"))
        val verdict = AudioMatch.verdict(release, AudioIntent(listOf(original)), null, "it")
        assertEquals(0, verdict.heard)
        assertEquals(true, verdict.original)
        assertEquals(AudioProof.Declared, verdict.proof)
    }

    @Test fun aForeignDubRememberedAsTheOriginalIsNotTheOriginal() {
        val dub = version("dub", "Film.2020.1080p [spa]", audio = listOf("ES"))
        val verdict = AudioMatch.verdict(dub, AudioIntent(listOf(original)), SourceFact(language = AudioLanguage.VO), "en")
        assertEquals(false, verdict.original)
        assertNotEquals(0, verdict.heard)
        assertEquals(verdict.heard, VersionRanking.heard(dub, prefs(original), true, SourceFact(language = AudioLanguage.VO), "en"))
    }

    @Test fun languagesHeardInAPastViewingCountOverTheName() {
        val multi = version("m", "Film.MULTi.1080p", AudioLanguage.MULTI, listOf("FR"))
        val verdict = AudioMatch.verdict(multi, AudioIntent(listOf("it", original)), SourceFact(codes = listOf("fr", "it")), "en")
        assertEquals(0, verdict.heard)
        assertEquals(AudioProof.Observed, verdict.proof)
    }

    @Test fun italianPreferredRanksTheItalianVersionFirstAndHidesNothing() {
        val vf = version("vf", "Film.FRENCH.1080p", AudioLanguage.VF, listOf("FR"))
        val ita = version("ita", "Film.ITA.1080p", audio = listOf("IT"))
        val unknown = version("u", "Film.1080p")
        val ranked = VersionRanking.rank(listOf(vf, unknown, ita), prefs("it", original), true, caps = DeviceCaps.Pc, workLanguage = "en")
        assertEquals(listOf(ita, unknown, vf), ranked)
        assertEquals(3, ranked.size)
    }

    @Test fun aDualAudioAnimeReleaseIsNoFrenchVersion() {
        // Frieren, French then original wanted: a French MULTi from Real-Debrid, then the Anime-Sama VF (a web link comes
        // after Real-Debrid at the same language, 2026-11-23), both before an English and Japanese "dual audio".
        val dual = version("dual", "[Sokudo] Sousou no Frieren S01E04 [1080p EAC3 AV1][dual audio].mkv", AudioLanguage.MULTI, dub = true)
        val vf = version("vf", "Anime-Sama · VF · ansembed.net", AudioLanguage.VF, audio = listOf("FR"), origin = VersionOrigin.Web)
        val multi = version("multi", "Frieren S01E04 CUSTOM MULTi 1080p 10bits BluRay x265 AAC -Punisher694.mkv", AudioLanguage.MULTI)
        val ranked = VersionRanking.rank(listOf(dual, multi, vf), prefs("fr", original), true, caps = DeviceCaps.Pc, workLanguage = "ja")
        assertEquals(listOf(multi, vf, dual), ranked)
        assertFalse("fr" in AudioMatch.verdict(dual, prefs("fr", original).intent, null, "ja").codes)
    }

    @Test fun bestQualityFirstPutsA4kHdrMultiBeforeA1080pMulti() {
        val hd = PlayVersion("hd", VersionOrigin.Debrid, "Rings.of.Power.S01E01.MULTi.1080p.WEB", language = AudioLanguage.MULTI, quality = com.sokoul.iptv.domain.VideoQuality.FHD_1080,
            readiness = Readiness.Ready, announcedAudio = listOf("FR", "EN"), open = { _, _ -> StreamRequest("https://fixture.test/a.mkv", null, null, "Fixture") })
        val uhd = PlayVersion("uhd", VersionOrigin.Debrid, "Rings.of.Power.S01E01.MULTi.2160p.DV.HDR.WEB", language = AudioLanguage.MULTI, quality = com.sokoul.iptv.domain.VideoQuality.UHD_4K,
            hdr = com.sokoul.iptv.domain.HdrFormat.DOLBY_VISION, readiness = Readiness.Ready, announcedAudio = listOf("FR", "EN"), open = { _, _ -> StreamRequest("https://fixture.test/b.mkv", null, null, "Fixture") })
        // On a PC, SDR first unless the viewer asks for the best quality.
        assertEquals(listOf(hd, uhd), VersionRanking.rank(listOf(uhd, hd), prefs("fr", original), true, caps = DeviceCaps.Pc, workLanguage = "en"))
        assertEquals(listOf(uhd, hd), VersionRanking.rank(listOf(hd, uhd), prefs("fr", original).copy(bestQualityFirst = true), true, caps = DeviceCaps.Pc, workLanguage = "en"))
    }

    @Test fun aProbableFrenchMultiInBetterPictureBeatsADeclaredVfInSd() {
        // Spartacus S01E01 on the TV (2026-11-19): a 442 MB SD VF and IPTV at 720x406 played before a 1080p Blu-ray MULTI
        // that does not name French. At the same picture, the declared French still wins.
        fun v(key: String, name: String, language: AudioLanguage, quality: com.sokoul.iptv.domain.VideoQuality?, origin: VersionOrigin = VersionOrigin.Debrid, audio: List<String> = emptyList()) =
            PlayVersion(key, origin, name, language = language, quality = quality, readiness = if (origin == VersionOrigin.Iptv) Readiness.Ready else Readiness.Unknown,
                announcedAudio = audio, open = { _, _ -> StreamRequest("https://fixture.test/$key.mkv", null, null, "Fixture") })
        val sdVf = v("sd", "Spartacus.2010.S01E01.FRENCH.WEBRiP.x264-KiRa.mkv", AudioLanguage.VF, com.sokoul.iptv.domain.VideoQuality.SD, audio = listOf("FR"))
        val multi = v("multi", "Spartacus.S01E01.(Blood And Sand).Bluray.1080p.Multi.AC3.x264", AudioLanguage.MULTI, com.sokoul.iptv.domain.VideoQuality.FHD_1080)
        val iptv = v("iptv", "FR| Spartacus", AudioLanguage.VF, null, origin = VersionOrigin.Iptv, audio = listOf("FR"))
        for (best in listOf(false, true)) {
            val ranked = VersionRanking.rank(listOf(sdVf, iptv, multi), prefs("fr", original).copy(bestQualityFirst = best), true, caps = DeviceCaps.Pc, workLanguage = "en")
            assertEquals("best quality first = $best", multi, ranked.first())
        }
        val multiVf = v("multivf", "Spartacus.S01E01.MULTi.VFF.1080p.BluRay", AudioLanguage.MULTI, com.sokoul.iptv.domain.VideoQuality.FHD_1080, audio = listOf("FR", "EN"))
        assertEquals(multiVf, VersionRanking.rank(listOf(multi, multiVf), prefs("fr", original), true, caps = DeviceCaps.Pc, workLanguage = "en").first())
    }

    @Test fun aVersionWithoutSizeRanksBesideOnesWithASize() {
        // Same language, picture and readiness: only the size tells them apart, and one has none (a web source).
        val sized = version("sized", "Frieren S01E07 VF.mkv", AudioLanguage.VF, audio = listOf("FR"), size = 1_400_000_000L)
        val web = version("web", "Anime-Sama · VF · ansembed.net", AudioLanguage.VF, audio = listOf("FR"))
        assertEquals(listOf(sized, web), VersionRanking.rank(listOf(web, sized), prefs("fr", original), true, caps = DeviceCaps.Pc, workLanguage = "ja"))
    }

    @Test fun aSitePriorityDoesNotLiftADubAboveAPlainRelease() {
        val dub = version("dub", "PurStream · Film [spa, eng]", audio = listOf("ES", "EN"), dub = true, origin = VersionOrigin.Web)
        val plain = version("plain", "Wiflix · Film", origin = VersionOrigin.Web)
        val ranked = VersionRanking.rank(listOf(dub, plain), prefs(original), true)
        assertEquals(listOf(plain, dub), ranked)
        val order = SourcePreferenceOrder(sources = listOf("PurStream", "Wiflix"))
        assertEquals(listOf(plain, dub), SourcePreferences.rank(ranked, { order }, prefs(original), true, DeviceCaps.Pc, { null }, { false }))
    }

    // --- Session checks and subtitles

    @Test fun aMissingLanguageIsToldForAnyLanguage() {
        val intent = AudioIntent(listOf("it", original))
        assertTrue(AudioMatch.missingWanted(listOf(1 to "Français", 2 to "Español"), intent, "en"))
        assertFalse(AudioMatch.missingWanted(listOf(1 to "Français", 2 to "English"), intent, "en"))
        assertFalse("an unnamed track may hold it", AudioMatch.missingWanted(listOf(1 to "Français", 2 to "Track 2"), intent, "en"))
        assertEquals("it", AudioMatch.firstWanted(intent, "en"))
    }

    @Test fun subtitlesAreOffAutomaticOrOneLanguage() {
        val subs = listOf(-1 to "Disabled", 4 to "Français", 5 to "English", 6 to "Italiano")
        assertEquals(4, AudioMatch.pickSubtitle(subs, AudioIntent.SUBTITLES_AUTO, "English", "fr"))
        assertNull("the audio is already the interface language", AudioMatch.pickSubtitle(subs, AudioIntent.SUBTITLES_AUTO, "Français", "fr"))
        assertNull(AudioMatch.pickSubtitle(subs, AudioIntent.SUBTITLES_OFF, "English", "fr"))
        assertEquals(6, AudioMatch.pickSubtitle(subs, "it", "Français", "fr"))
    }

    // --- Migration

    @Test fun earlierPreferencesMigrateWithoutLoss() {
        assertEquals(listOf("fr", original), AudioIntent.fromLegacy(AudioPreference.Vf, SubtitlePreference.System, "fr").languages)
        assertEquals(listOf("fr", original), AudioIntent.fromLegacy(AudioPreference.Multi, SubtitlePreference.System, "fr").languages)
        assertEquals(listOf(original), AudioIntent.fromLegacy(AudioPreference.Vo, SubtitlePreference.System, "fr").languages)
        val vostfr = AudioIntent.fromLegacy(AudioPreference.Vostfr, SubtitlePreference.System, "fr")
        assertEquals(listOf(original), vostfr.languages)
        assertEquals("fr", vostfr.subtitles)
        assertEquals(listOf("it", original), AudioIntent.fromLegacy(AudioPreference.System, SubtitlePreference.System, "it").languages)
        assertEquals(AudioIntent.SUBTITLES_OFF, AudioIntent.fromLegacy(AudioPreference.Vf, SubtitlePreference.Off, "fr").subtitles)
    }

    @Test fun theStoredPreferenceIsMigratedOnceAndThenKept() {
        val db = DesktopDb(Files.createTempDirectory("sokoul-audio").toFile().resolve("t.db"))
        val prefs = DesktopPrefs(db)
        prefs.audioPreference = AudioPreference.Vostfr
        assertEquals(listOf(original), prefs.audioIntent.languages)
        assertEquals("fr", prefs.audioIntent.subtitles)
        // Changing the earlier key afterwards no longer moves the new preference: it was migrated once.
        prefs.audioPreference = AudioPreference.Vf
        assertEquals(listOf(original), prefs.audioIntent.languages)
        prefs.audioIntent = AudioIntent(listOf("ja", "en"), AudioFallback.Ask, AudioIntent.SUBTITLES_OFF)
        assertEquals(AudioIntent(listOf("ja", "en"), AudioFallback.Ask, AudioIntent.SUBTITLES_OFF), prefs.audioIntent)
        assertEquals(listOf("ja", "en"), prefs.engine().audioLanguages)
        db.close()
    }

    @Test fun decodingKeepsOnlyLanguageCodesAndOriginal() {
        assertEquals(listOf("it", original), AudioIntent.decode(" IT, original ,xx1, it"))
        assertNull(AudioIntent.decode(""))
        assertEquals(listOf("ja"), AudioIntent(listOf(original)).resolved("ja"))
        assertEquals(listOf("ja", "en"), AudioIntent(listOf("ja", original)).resolved("en"))
        assertEquals(listOf("ja"), AudioIntent(listOf("ja", original)).resolved("ja"))
    }
}
