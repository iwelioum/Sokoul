package com.sokoul.credentials

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** One check per service against a local server: the request each one sends, and how answers become verdicts. */
class ServiceCheckTest {
    private lateinit var server: MockWebServer
    private val key = "k3y&with=odd/chars"

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun check() = ServiceCheck(OkHttpClient(), ServiceCheck.SERVICES.associateWith { base() })

    @Test fun `every keyed service sends its key where the service expects it, encoded`() = runBlocking {
        val expect = mapOf(
            "tmdb" to { p: String, h: (String) -> String? -> p.startsWith("/3/configuration?") && "api_key=k3y%26with%3Dodd%2Fchars" in p },
            "realdebrid" to { p, h -> p == "/rest/1.0/user" && h("Authorization") == "Bearer $key" },
            "omdb" to { p, _ -> "apikey=k3y%26with%3Dodd%2Fchars" in p && "i=tt1375666" in p },
            "fanart" to { p, _ -> p.startsWith("/v3/movies/27205?api_key=") },
            "watchmode" to { p, _ -> p.startsWith("/v1/status/?apiKey=") },
            "tastedive" to { p, _ -> p.startsWith("/api/similar?k=") },
            "motn" to { p, h -> p.startsWith("/v4/shows/movie/27205") && h("X-API-Key") == key },
            "opensubtitles" to { p, h -> p == "/api/v1/infos/formats" && h("Api-Key") == key && h("User-Agent") != null },
            "subdl" to { p, _ -> p.startsWith("/api/v1/subtitles?api_key=") && "tmdb_id=27205" in p },
            "tvmaze" to { p, _ -> p.startsWith("/singlesearch/shows?q=lupin") },
        )
        for ((service, ok) in expect) {
            server.enqueue(MockResponse().setResponseCode(200))
            assertEquals(service, Verdict.Accepted, check().key(service, key).verdict)
            val r = server.takeRequest()
            assertTrue("$service sent ${r.path}", ok(r.path!!) { r.getHeader(it) })
        }
        // TheTVDB logs in with the key in a JSON body, quotes and all.
        server.enqueue(MockResponse().setResponseCode(200))
        assertEquals(Verdict.Accepted, check().key("tvdb", "a\"b").verdict)
        val login = server.takeRequest()
        assertEquals("/v4/login", login.path)
        assertEquals("a\"b", Json.parseToJsonElement(login.body.readUtf8()).jsonObject.getValue("apikey").jsonPrimitive.content)
    }

    @Test fun `answers become verdicts`() = runBlocking {
        val cases = listOf(200 to Verdict.Accepted, 401 to Verdict.Refused, 403 to Verdict.Refused, 400 to Verdict.Refused,
            429 to Verdict.Unreachable, 500 to Verdict.Unreachable, 503 to Verdict.Unreachable)
        for ((code, verdict) in cases) {
            server.enqueue(MockResponse().setResponseCode(code))
            val r = check().key("omdb", "abcd1234")
            assertEquals("HTTP $code", verdict, r.verdict)
            assertEquals(code, r.httpCode)
        }
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(check().key("omdb", "abcd1234").quota)
    }

    @Test fun `no answer is unreachable, without the key in the reason`() = runBlocking {
        val c = check()
        server.shutdown()
        val r = c.key("tmdb", "0123456789abcdef0123456789abcdef")
        assertEquals(Verdict.Unreachable, r.verdict)
        assertNull(r.httpCode)
        assertTrue(!r.reason("TMDB").contains("0123"))
    }

    @Test fun `a key with a line break cannot go in a header and is refused`() = runBlocking {
        assertEquals(Verdict.Refused, check().key("realdebrid", "abc\r\nX-Evil: 1").verdict)
        assertEquals(0, server.requestCount)
    }

    @Test fun `iptv values are an Xtream login or an M3U link`() = runBlocking {
        val login = ServiceCheck.xtreamValue(base(), "lou", "p@ss")
        assertEquals(IptvValue.Xtream(base(), "lou", "p@ss"), ServiceCheck.parseIptv(login))
        assertEquals(IptvValue.M3u("https://x.example/list.m3u"), ServiceCheck.parseIptv("https://x.example/list.m3u"))
        assertNull(ServiceCheck.parseIptv("ftp://x.example/list"))
        assertNull(ServiceCheck.parseIptv("""{"server":"${base()}","username":"lou"}"""))
        assertTrue(!IptvValue.Xtream("s", "u", "p@ss").toString().contains("p@ss"))

        server.enqueue(xtream(1))
        assertEquals(Verdict.Accepted, check().check(ServiceCheck.IPTV, login).verdict)
        assertTrue(server.takeRequest().path!!.contains("username=lou"))
        server.enqueue(xtream(0))
        assertEquals(Verdict.Refused, check().check(ServiceCheck.IPTV, login).verdict)
        server.enqueue(MockResponse().setBody("not json at all"))
        assertEquals(Verdict.Unreachable, check().check(ServiceCheck.IPTV, login).verdict)

        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1,One\nhttp://a/1.ts\n"))
        assertEquals(Verdict.Accepted, check().check(ServiceCheck.IPTV, base() + "/list.m3u").verdict)
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(Verdict.Refused, check().check(ServiceCheck.IPTV, base() + "/list.m3u").verdict)
        server.enqueue(MockResponse().setBody("<html>"))
        assertEquals(Verdict.Refused, check().check(ServiceCheck.IPTV, base() + "/list.m3u").verdict)
        assertEquals(Verdict.Refused, check().check(ServiceCheck.IPTV, "nope").verdict)
    }

    @Test fun `real-debrid reads the account`() = runBlocking {
        val now = java.time.Instant.parse("2026-11-25T00:00:00Z").toEpochMilli()
        server.enqueue(MockResponse().setBody("""{"username":"lou","type":"premium","expiration":"2026-12-05T00:00:00.000Z"}"""))
        assertEquals(DebridCheck(Verdict.Accepted, "lou", 10), check().realDebrid("t", now))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(Verdict.Refused, check().realDebrid("t", now).verdict)
    }

    private fun xtream(auth: Int) = MockResponse().setBody(
        """{"user_info":{"auth":$auth,"status":"${if (auth == 1) "Active" else "Disabled"}","exp_date":"1900000000","max_connections":"1","is_trial":"0"},"server_info":{}}""",
    )
}
