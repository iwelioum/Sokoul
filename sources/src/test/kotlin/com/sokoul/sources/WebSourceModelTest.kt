package com.sokoul.sources

import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.sources.coflix.Coflix
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared model: labels, media addresses, and "no such work" told apart from "could not ask". */
class WebSourceModelTest {
    @Test fun labelsNameTheirAudio() {
        assertEquals(AudioLanguage.VF, audioLanguageOf("VFF"))
        assertEquals(AudioLanguage.VF, audioLanguageOf("French"))
        assertEquals(AudioLanguage.VO, audioLanguageOf("English"))
        assertEquals(AudioLanguage.VO, audioLanguageOf("VOENG"))
        assertEquals(AudioLanguage.VOSTFR, audioLanguageOf("VOSTFR"))
        assertEquals(AudioLanguage.VOSTFR, audioLanguageOf("VO sous-titrée FR"))
        assertEquals(AudioLanguage.MULTI, audioLanguageOf("VF + VO"))
        assertNull(audioLanguageOf("Unknown"))
        assertNull(audioLanguageOf("default"))
    }

    @Test fun mediaAddressesAreToldFromPlayerPages() {
        assertTrue(MediaUrls.file("https://cdn.example/a/master.m3u8"))
        assertTrue(MediaUrls.file("https://cdn.example/b.mp4"))
        assertTrue(MediaUrls.file("https://cdn.example/hls/stream", "hls"))
        assertFalse(MediaUrls.file("https://uqload.cx/embed-x.html"))
        assertFalse(MediaUrls.file("https://cdn.example/embed/a.mp4"))
        assertFalse(MediaUrls.file("javascript:alert(1).m3u8"))
        assertEquals(LinkFormat.Hls, MediaUrls.format("https://cdn.example/a/master.m3u8", null))
        assertEquals(LinkFormat.File, MediaUrls.format("https://cdn.example/b.mp4", null))
        assertEquals("mp4", MediaUrls.type("https://cdn.example/b.mp4", null))
        assertEquals("uqload.cx", hostOf("https://www.uqload.cx/embed-x.html"))
    }

    private fun coflix(site: MockWebServer) = Coflix(
        OkHttpClient(),
        tmdb = { _, _, _ -> TmdbClient.NamesAndImages("Inception", null, 2010, setOf("/aej3.jpg")) },
        site = site.url("/").toString().trimEnd('/'),
        dns = Dns.SYSTEM,
    )

    @Test fun aSiteThatAnswersWithoutTheWorkHasNotFoundIt() = runTest {
        val site = MockWebServer().apply { repeat(3) { enqueue(MockResponse().setBody("<html><body>Aucun résultat</body></html>")) }; start() }
        try {
            assertEquals(SourceResult.NotFound, coflix(site).find(SourceQuery("movie", 27205)))
        } finally { site.shutdown() }
    }

    @Test fun aSiteThatRefusesIsBlocked() = runTest {
        val site = MockWebServer().apply { repeat(3) { enqueue(MockResponse().setResponseCode(503)) }; start() }
        try {
            assertEquals(SourceResult.Unavailable(SourceResult.BLOCKED), coflix(site).find(SourceQuery("movie", 27205)))
        } finally { site.shutdown() }
    }

    @Test fun aSiteThatCannotBeReachedIsANetworkFailure() = runTest {
        val site = MockWebServer().apply { start() }
        val source = coflix(site)
        site.shutdown()
        assertEquals(SourceResult.Unavailable(SourceResult.NETWORK), source.find(SourceQuery("movie", 27205)))
    }

    @Test fun aSiteThatFindsTheWorkGivesItsPlayersForExtraction() = runTest {
        val site = MockWebServer()
        site.start()
        val base = site.url("/").toString().trimEnd('/')
        val uqload = java.util.Base64.getEncoder().encodeToString("https://uqload.cx/embed-x.html".toByteArray())
        site.enqueue(MockResponse().setBody("""<div class="md-manga-card"><a href="$base/film/inception/"><img data-src="//image.tmdb.org/t/p/w342/aej3.jpg"></a>
            <div class="md-manga-card-name">Inception</div><span class="md-card-badge year">2010</span></div>"""))
        site.enqueue(MockResponse().setBody("""<script>var cfServers = [{"embed_url":"$base/embed.php?id=1","idioma":"Autre"}];</script>"""))
        site.enqueue(MockResponse().setBody("""<ul><li onclick="showVideo('$uqload')"><span>HD</span><p>Uqload / French</p></li></ul>"""))
        try {
            val link = (coflix(site).find(SourceQuery("movie", 27205)) as SourceResult.Found).links.single()
            assertEquals(SourceLink("Coflix", "uqload.cx", "https://uqload.cx/embed-x.html", AudioLanguage.VF, "HD", LinkFormat.Embed), link)
        } finally { site.shutdown() }
    }
}
