package com.sokoul.pc

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.iptv.data.debrid.RealDebridOAuth
import com.sokoul.pc.data.CredentialValidator
import com.sokoul.pc.setup.ServiceGuides
import com.sokoul.pc.setup.SourceEntry
import com.sokoul.pc.setup.SourcesPage
import com.sokoul.pc.setup.SourcesSection
import com.sokoul.pc.startup.onboarding.OnboardingModel
import com.sokoul.pc.startup.onboarding.SourceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class SourcesPageTest {
    private val savedBuiltIn = ProviderConfig.builtIn

    @Before fun noBuiltInKeys() { ProviderConfig.builtIn = { emptyMap() } }

    @After fun restore() { ProviderConfig.builtIn = savedBuiltIn }

    private fun db() = DesktopDb(Files.createTempDirectory("sokoul-sources").toFile().resolve("t.db"))

    private fun model(db: DesktopDb) = OnboardingModel(
        db, CredentialValidator(OkHttpClient()), RealDebridOAuth(OkHttpClient()), CoroutineScope(Dispatchers.Unconfined),
    )

    @Test fun everyCardMapsToAGuide() {
        SourcesPage.cards.forEach { card ->
            assertTrue("${card.id} has no guide", card.guides.isNotEmpty())
            card.guides.forEach { assertNotNull("no guide $it for ${card.id}", ServiceGuides.of(it)) }
        }
    }

    @Test fun theFourSourcesComeFirstThenEveryExpertKey() {
        assertEquals(listOf("iptv", "realdebrid", "subtitles", "mainapi"), SourcesPage.section(SourcesSection.Yours).map { it.id })
        assertEquals(OnboardingModel.ADVANCED_KEYS.map { it.id }, SourcesPage.section(SourcesSection.Expert).map { it.id })
        assertEquals(SourcesPage.cards.size, SourcesPage.cards.map { it.id }.toSet().size)
    }

    @Test fun onlyTvmazeHasNothingToEnter() {
        assertEquals(listOf("tvmaze"), SourcesPage.cards.filter { it.entry == SourceEntry.None }.map { it.id })
    }

    @Test fun theLongTmdbTokenIsCaughtBeforeAskingTmdb() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIwMTIzIn0.abc"
        val warning = SourcesPage.keyFormatWarning("tmdb", jwt)
        assertNotNull(warning)
        assertTrue(warning!!, warning.contains("32"))
        assertNotNull(SourcesPage.keyFormatWarning("tmdb", "0123456789abcdef0123456789abcde")) // one character short
        assertNull(SourcesPage.keyFormatWarning("tmdb", " 0123456789abcdef0123456789abcdef "))
        assertNull(SourcesPage.keyFormatWarning("tmdb", ""))
        // No known format: the service decides.
        assertNull(SourcesPage.keyFormatWarning("omdb", "anything"))
    }

    @Test fun cardsShowAMaskNeverTheKey() {
        val db = db()
        ProviderConfig.setKey(db, "tmdb", "0123456789abcdef0123456789ab4f2c")
        val m = model(db)
        val tmdb = SourcesPage.state(SourcesPage.cards.first { it.id == "tmdb" }, m)
        assertEquals(SourceStatus.Ready, tmdb.status)
        assertEquals("•••• 4f2c", tmdb.value)
        assertEquals(SourceStatus.Empty, SourcesPage.state(SourcesPage.cards.first { it.id == "omdb" }, m).status)
        m.keyErrors["omdb"] = "refused"
        val omdb = SourcesPage.state(SourcesPage.cards.first { it.id == "omdb" }, m)
        assertEquals(SourceStatus.Problem, omdb.status)
        assertEquals("refused", omdb.problem)
    }

    @Test fun shortLinksReadLikeTyped() {
        assertEquals("themoviedb.org/settings/api", SourcesPage.shortLink("https://www.themoviedb.org/settings/api"))
        assertEquals("fanart.tv/get-an-api-key", SourcesPage.shortLink("https://fanart.tv/get-an-api-key/"))
    }
}
