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

    /** Signed in, the page shows what the account's vault has, and an accepted key is saved there too (never read back). */
    @Test fun theAccountVaultShowsItsMasksAndReceivesAcceptedKeys() {
        val puts = java.util.concurrent.LinkedBlockingQueue<okhttp3.mockwebserver.RecordedRequest>()
        val server = okhttp3.mockwebserver.MockWebServer()
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = when {
                request.method == "GET" && request.path == "/api/credentials" ->
                    okhttp3.mockwebserver.MockResponse().setBody("""[{"service":"omdb","hint":"•••• 9f2a","verdict":"accepted","checkedAt":1},{"service":"iptv","hint":"•••• more","verdict":"accepted","checkedAt":1}]""")
                request.method == "PUT" -> { puts.add(request); okhttp3.mockwebserver.MockResponse().setBody("""{"service":"tmdb","hint":"•••• cdef","verdict":"accepted","checkedAt":2}""") }
                request.path!!.startsWith("/3/configuration") -> okhttp3.mockwebserver.MockResponse().setResponseCode(200)
                else -> okhttp3.mockwebserver.MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            val store = com.sokoul.pc.account.MemoryAccountStore(base, com.sokoul.pc.account.StoredSession(com.sokoul.pc.account.Secret("session-token"), "s1", null))
            val vault = com.sokoul.pc.account.AccountVault(com.sokoul.pc.account.SokoulAccount(store, OkHttpClient()))
            val db = db()
            val model = OnboardingModel(
                db, CredentialValidator(OkHttpClient(), tmdbBase = base), RealDebridOAuth(OkHttpClient()), CoroutineScope(Dispatchers.Unconfined),
                vault = vault,
            )
            val deadline = System.currentTimeMillis() + 5_000
            while (model.vaultHints.size < 2 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            val omdb = SourcesPage.state(SourcesPage.cards.first { it.id == "omdb" }, model)
            assertEquals(SourceStatus.Empty, omdb.status)
            assertTrue(omdb.value!!, omdb.value!!.contains("•••• 9f2a"))
            assertTrue(SourcesPage.state(SourcesPage.cards.first { it.id == "iptv" }, model).value!!.contains("•••• more"))
            assertNull(SourcesPage.state(SourcesPage.cards.first { it.id == "fanart" }, model).value)

            val key = "0123456789abcdef0123456789abcdef"
            assertTrue(kotlinx.coroutines.runBlocking { model.saveKey("tmdb", key, com.sokoul.pc.startup.onboarding.SourceKind.Advanced) })
            val put = puts.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!
            assertEquals("/api/credentials/tmdb", put.path)
            assertEquals("Bearer session-token", put.getHeader("Authorization"))
            assertEquals("""{"value":"$key"}""", put.body.readUtf8())
            db.close()
        } finally {
            server.shutdown()
        }
    }
}
