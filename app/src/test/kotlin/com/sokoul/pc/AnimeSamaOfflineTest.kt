package com.sokoul.pc

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.sources.AnimeSamaSource
import com.sokoul.pc.playback.sources.NativeEmbedResolver
import com.sokoul.pc.playback.sources.StreamProbe
import com.sokoul.sources.animesama.AnimeSama
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Anime-Sama read by the app (the sources module), and used only when the Sokoul server is off. */
class AnimeSamaOfflineTest {
    /** A small Anime-Sama: one work, one season in VOSTFR and VF, players given directly. */
    private fun site(extra: Map<String, String> = emptyMap()): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                request.path?.let(extra::get)?.let { return MockResponse().setBody(it) }
                return when (request.path) {
                    "/template-php/defaut/fetch.php" -> MockResponse().setBody(
                        """<a class="asn-search-result" href="/catalogue/frieren/"><h3>Frieren</h3><p class="asn-search-result-subtitle">Sousou no Frieren</p></a>""")
                    "/catalogue/frieren/" -> MockResponse().setBody("""<script>panneauAnime("Saison 1", "saison1/vostfr");</script>""")
                    "/catalogue/frieren/saison1/vostfr/" -> MockResponse().setBody("""<script>var languesDispo = ["vostfr","vf"]; var eps1 = ["https://vidmoly.to/embed-a.html"];</script>""")
                    "/catalogue/frieren/saison1/vf/" -> MockResponse().setBody("""<script>var eps1 = ["https://video.sibnet.ru/shell.php?videoid=7"];</script>""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }

    // The players are inline in the season page: the separate episodes.js answers 403 since 2026-11 and is not asked.
    // The quick search counts the season by its original page; episode 2, past it, gets the thorough count (one page more).
    @Test fun aNumberedFrenchEditionCanPublishMoreEpisodes() = runBlocking {
        val site = site(mapOf(
            "/catalogue/frieren/saison1/vostfr/" to """<script>var languesDispo = ["vostfr","vf3"]; var eps1 = ["https://vidmoly.to/embed-sub.html"];</script>""",
            "/catalogue/frieren/saison1/vf3/" to """<script>var eps1 = ["https://video.sibnet.ru/shell.php?videoid=7", "https://video.sibnet.ru/shell.php?videoid=8"];</script>""",
        ))
        try {
            val http = OkHttpClient()
            val source = AnimeSamaSource(http, { null }, { null }, StreamProbe(http), NativeEmbedResolver(http),
                AnimeSama(http, site.url("/").toString().trimEnd('/')))
            val result = source.find(PlayRequest("tv", 209867, 1, 2, "Frieren", null, null), listOf("Frieren"), 2)
            assertEquals(AudioLanguage.VF, result.versions.single().language)
            assertEquals(listOf("FR"), result.versions.single().announcedAudio)
            assertEquals(4, site.requestCount)
        } finally {
            site.shutdown()
        }
    }

    @Test fun withTheServerOffTheSiteIsReadAndItsPlayersGoToTheLocalExtractor() = runBlocking {
        val site = site()
        try {
            val http = OkHttpClient()
            // Port 9 on the loopback: nothing listens, as when Docker is off.
            val source = AnimeSamaSource(http, { "http://127.0.0.1:9" }, { null }, StreamProbe(http), NativeEmbedResolver(http),
                AnimeSama(http, site.url("/").toString().trimEnd('/')))
            val result = source.find(PlayRequest("tv", 209867, 1, 1, "Frieren", null, null), listOf("Frieren"), 1)
            assertEquals(setOf(AudioLanguage.VF, AudioLanguage.VOSTFR), result.versions.map { it.language }.toSet())
            assertEquals(setOf("video.sibnet.ru", "vidmoly.to"), result.versions.map { it.providerHost }.toSet())
        } finally {
            site.shutdown()
        }
    }

    @Test fun withTheServerOnTheSiteIsNotAsked() = runBlocking {
        val site = site()
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("""[{"name":"Frieren","seasons":[{"name":"Saison 1","episodes":[{}]}]}]"""))
            enqueue(MockResponse().setBody("""[{"name":"Frieren","extractionStatus":"resolved","seasons":[{"name":"Saison 1","episodes":[{"streaming_links":[]}]}]}]"""))
            start()
        }
        try {
            val http = OkHttpClient()
            val source = AnimeSamaSource(http, { server.url("/").toString() }, { null }, StreamProbe(http), NativeEmbedResolver(http),
                AnimeSama(http, site.url("/").toString().trimEnd('/')))
            source.find(PlayRequest("tv", 209867, 1, 1, "Frieren", null, null), listOf("Frieren"), 1)
            assertEquals(2, server.requestCount)
            assertEquals(0, site.requestCount)
        } finally {
            site.shutdown()
            server.shutdown()
        }
    }
}
