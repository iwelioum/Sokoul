package com.sokoul.pc

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.sources.WebParse
import com.sokoul.pc.playback.sources.WebRoutes
import com.sokoul.pc.playback.sources.StreamProbe
import com.sokoul.pc.playback.sources.WebSources
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSourcesTest {
    @Test fun `French subtitles do not make the original Japanese audio a dub`() {
        assertEquals(AudioLanguage.VOSTFR, WebParse.languageOf("VO sous-titrée FR"))
        assertEquals(AudioLanguage.VOSTFR, WebParse.languageOf("Japanese + French subtitles"))
        assertEquals(AudioLanguage.VF, WebParse.languageOf("VF3"))
        assertEquals(AudioLanguage.MULTI, WebParse.languageOf("VF + VO"))
    }

    @Test fun `local extraction requires an explicit grant and a supported selected embed`() {
        fun links(status: String) = WebParse.links("Coflix", Json.parseToJsonElement("""{"extractionStatus":"$status","current_episode":{"player_links":[{"decoded_url":"https://ansembed.net/embed-one.html","language":"vf"},{"decoded_url":"https://unknown.example/e/2"}]}}"""), 1, allowLocal = true)
        assertTrue(links("vip_required").isEmpty())
        assertTrue(links("unavailable").isEmpty())
        assertEquals(1, links("authorized").size)
        assertTrue(links("authorized").single().localExtraction)
        assertEquals(AudioLanguage.VF, links("authorized").single().language)
    }

    @Test fun `coflix root links and omega selected episode retain languages`() {
        val coflix = parse("Coflix", """{"player_links":[{"decoded_url":"https://embed.example/1","m3u8Url":"https://cdn.example/1.m3u8","language":"vf"}]}""")
        assertEquals(AudioLanguage.VF, coflix.single().language)
        // Without a player name, the player's site tells the versions apart (not "Coflix" four times).
        assertEquals("embed.example", coflix.single().name)
        assertEquals("ansembed.net", WebParse.links("Coflix", Json.parseToJsonElement("""{"extractionStatus":"authorized","player_links":[{"decoded_url":"https://www.ansembed.net/embed-one.html"}]}"""), null, allowLocal = true).single().name)
        val omega = parse("Omega", """{"current_episode":{"languages":{"vostfr":[{"link":"https://embed.example/1","m3u8Url":"https://cdn.example/2.m3u8"}]}}}""", 3)
        assertEquals(AudioLanguage.VOSTFR, omega.single().language)
    }
    private fun parse(source: String, json: String, episode: Int? = null) =
        WebParse.links(source, Json.parseToJsonElement(json), episode)

    @Test
    fun `purstream sources are read with their format`() {
        val links = parse("PurStream", """{"purstream_id":1,"sources":[{"url":"https://cdn.example/a/master.m3u8","name":"VF 1080p","format":"hls"},{"url":"https://cdn.example/b.mp4","name":"VOSTFR","format":"mp4"}]}""")
        assertEquals(2, links.size)
        assertTrue(links[0].hls)
        assertEquals(AudioLanguage.VF, links[0].language)
        assertEquals(AudioLanguage.VOSTFR, links[1].language)
    }

    @Test
    fun `an embed page is not a playable link`() {
        val links = parse("FStream", """{"players":{"VF":[{"player":"Premium","url":"https://embed.example/e/123"}]}}""")
        assertTrue(links.isEmpty())
    }

    @Test
    fun `a resolved player keeps the language of its key`() {
        val links = parse("FStream", """{"players":{"VF":[{"player":"Premium","url":"https://embed.example/e/1","m3u8Url":"https://cdn.example/x.m3u8"}],"VOSTFR":[{"player":"Uqload","m3u8Url":"https://cdn.example/y.m3u8"}]}}""")
        assertEquals(listOf(AudioLanguage.VF, AudioLanguage.VOSTFR), links.map { it.language })
        assertEquals("Premium", links[0].name)
    }

    @Test
    fun `a series reads only the requested episode`() {
        val json = """{"episodes":{"1":{"languages":{"VF":[{"player":"A","m3u8Url":"https://cdn.example/1.m3u8"}]}},"2":{"languages":{"VF":[{"player":"B","m3u8Url":"https://cdn.example/2.m3u8"}]}}}}"""
        assertEquals(listOf("https://cdn.example/2.m3u8"), parse("FStream", json, episode = 2).map { it.url })
        assertTrue(parse("FStream", json, episode = null).isEmpty())
    }

    @Test
    fun `duplicates, errors and junk give nothing`() {
        assertTrue(parse("PurStream", """{"error":"Film non trouvé sur PurStream"}""").isEmpty())
        assertTrue(parse("PurStream", "[]").isEmpty())
        assertEquals(1, parse("PurStream", """{"sources":[{"url":"https://cdn.example/a.m3u8"},{"url":"https://cdn.example/a.m3u8"}]}""").size)
        assertTrue(parse("PurStream", """{"sources":[{"url":"javascript:alert(1).m3u8"}]}""").isEmpty())
    }

    @Test
    fun `routes cover a film and an episode`() {
        val movie = PlayRequest("movie", 603, null, null, "The Matrix", null, null)
        assertEquals("/api/purstream/movie/603/stream", WebRoutes.of(movie).first().path)
        val episode = PlayRequest("tv", 1399, 2, 3, "Got", null, null)
        assertTrue(WebRoutes.of(episode).any { it.path == "/api/purstream/tv/1399/stream?season=2&episode=3" })
        assertTrue(WebRoutes.of(PlayRequest("tv", 1399, null, null, "Got", null, null)).isEmpty())
    }
    @Test
    fun `only signed relays on the configured server are media`() {
        val signature = "a".repeat(43)
        val url = "http://192.168.0.240:25569/vidmoly-proxy?url=https%3A%2F%2Fcdn.example%2Fmaster.m3u8%3Ftoken%3Dx&exp=2000000000&sig=$signature"
        fun relay(candidate: String, base: String? = "http://192.168.0.240:25565") =
            WebParse.links("FStream", Json.parseToJsonElement("""{"sources":[{"m3u8Url":"$candidate","format":"hls"}]}"""), null, base)
        assertEquals("hls", relay(url).single().mediaType)
        assertTrue(relay(url.replace("192.168.0.240", "attacker.example")).isEmpty())
        assertTrue(relay(url.replace(":25569", ":25570")).isEmpty())
        assertTrue(relay(url.replace("&sig=$signature", "")).isEmpty())
        assertTrue(relay(url, null).isEmpty())
        assertTrue(relay("https://embed.example/player/1?url=https%3A%2F%2Fcdn.example%2Fmaster.m3u8").isEmpty())
        assertTrue(relay("https://embed.example/page?m3u8=1").isEmpty())
    }

    @Test
    fun `a server reached on loopback also owns relays on this computer's LAN address`() {
        val signature = "c".repeat(43)
        val url = "http://192.168.0.240:25569/cinep-proxy?url=https%3A%2F%2Fcdn.example%2Fmaster.m3u8&exp=2000000000&sig=$signature"
        fun relay(candidate: String, base: String) =
            WebParse.links("PurStream", Json.parseToJsonElement("""{"sources":[{"url":"$candidate","format":"m3u8"}]}"""), null, base)
        val media = com.sokoul.pc.playback.sources.SourceMedia
        val previous = media.ownAddress
        media.ownAddress = { it.isLoopbackAddress || it.hostAddress == "192.168.0.240" }
        try {
            assertEquals(1, relay(url, "http://127.0.0.1:25565").size)
            assertEquals(1, relay(url, "http://localhost:25565").size)
            // another computer of the network is not this server
            assertTrue(relay(url.replace("192.168.0.240", "192.168.0.99"), "http://127.0.0.1:25565").isEmpty())
            // a name is never resolved to be trusted
            assertTrue(relay(url.replace("192.168.0.240", "attacker.example"), "http://127.0.0.1:25565").isEmpty())
            // a server reached by its network address keeps the exact-host rule
            assertTrue(relay(url, "http://192.168.0.50:25565").isEmpty())
        } finally {
            media.ownAddress = previous
        }
    }

    @Test
    fun `opaque signed native relay preserves format headers and subtitle metadata`() {
        val signature = "b".repeat(43)
        val url = "http://localhost:25569/native-proxy?id=fixture&exp=2000000000&sig=$signature"
        val payload = Json.parseToJsonElement("""{"players":{"VF":[{"player":"Vidmoly","m3u8Url":"$url","media":{"url":"$url","format":"hls","headers":{"Referer":"https://vidmoly.example/","User-Agent":"Fixture agent","Cookie":"private","X-Injected":"no"},"subtitles":[{"url":"https://cdn.example/fr.vtt","lang":"fr","label":"French","format":"vtt","default":true}]}}]}}""")
        val link = WebParse.links("FStream", payload, null, "http://localhost:25565").single()
        assertEquals("hls", link.mediaType)
        assertEquals(mapOf("Referer" to "https://vidmoly.example/", "User-Agent" to "Fixture agent"), link.headers)
        assertEquals("fr", link.subtitles.single().language)
        assertTrue(link.subtitles.single().default)
        val mp4 = WebParse.links("FStream", Json.parseToJsonElement(payload.toString().replace("\"hls\"", "\"mp4\"")), null, "http://localhost:25565").single()
        assertEquals("mp4", mp4.mediaType)
        assertTrue(!mp4.hls)
    }

    @Test
    fun `the ported answers give local extraction links with their language`() {
        val answer = Json.parseToJsonElement("""{"extractionStatus":"authorized","players":{"VFF":[{"url":"https://uqload.vc/embed-a.html","player":"Uqload"}],"VOSTFR":[{"url":"https://voe.sx/e/b","player":"Voe"}]}}""")
        val links = WebParse.links("FStream", answer, null, allowLocal = true)
        assertEquals(listOf(AudioLanguage.VF, AudioLanguage.VOSTFR), links.map { it.language })
        assertTrue(links.all { it.localExtraction })
    }

    @Test
    fun `a source read by the app gives versions from its typed links`() = kotlinx.coroutines.runBlocking {
        val http = okhttp3.OkHttpClient()
        val purstream = object : WebSource {
            override val name = "PurStream"
            override suspend fun find(query: SourceQuery) = SourceResult.Found(listOf(
                SourceLink(name, "pulse | 1080p", "https://cdn.example/a/master.m3u8", AudioLanguage.VF, format = LinkFormat.Hls),
                // A player page goes to the local extractor, which this search has not: it is left out.
                SourceLink(name, "uqload.cx", "https://uqload.cx/embed-x.html", AudioLanguage.VF, format = LinkFormat.Embed),
                SourceLink(name, "page", "https://cdn.example/embed/b.m3u8", null, format = LinkFormat.Hls),
            ))
        }
        val sources = WebSources(http, { null }, { null }, StreamProbe(http), localResolver = null, direct = mapOf("PurStream" to purstream))
        assertTrue(sources.configured())
        val found = ArrayList<com.sokoul.pc.playback.session.PlayVersion>()
        sources.find(PlayRequest("movie", 603, null, null, "The Matrix", null, null)) { found += it }
        assertEquals(listOf("PurStream · pulse | 1080p"), found.map { it.name })
        assertEquals(AudioLanguage.VF, found.single().language)
    }
}
