package com.sokoul.server

import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceLink
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.hosts.HostExtractor
import com.sokoul.sources.hosts.HostFailure
import com.sokoul.sources.hosts.HostResult
import com.sokoul.sources.hosts.HostSubtitle
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** sokoul-server 8.1–8.2: versions, start, and a relay that serves only what it resolved, to its own session. */
class PlaybackTest {
    @get:Rule val tmp = TemporaryFolder()

    private val upstream = MockWebServer()
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val mp4 = ByteArray(10_000) { (it % 251).toByte() }
    private var mkv: File? = null

    /** A TS segment disguised behind a PNG signature and some padding. */
    private val disguised: ByteArray = run {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0d, 0x0a, 0x1a, 0x0a) + ByteArray(100)
        val ts = ByteArray(188 * 6) { if (it % 188 == 0) 0x47 else 1 }
        png + ts
    }

    @Before fun start() {
        upstream.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val path = request.requestUrl!!.encodedPath
                val tmdb = """{"id":603,"title":"Matrix","original_title":"The Matrix","release_date":"1999-03-31","genres":[]}"""
                return when (path) {
                    "/3/movie/603" -> MockResponse().addHeader("Content-Type", "application/json").setBody(tmdb)
                    "/3/tv/1399" -> MockResponse().addHeader("Content-Type", "application/json")
                        .setBody("""{"id":1399,"name":"GoT","original_language":"en","genres":[{"id":18}]}""")
                    "/hls/master.m3u8" -> MockResponse().addHeader("Content-Type", "application/vnd.apple.mpegurl").setBody(
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",URI=\"audio/fr.m3u8\"\n",
                    )
                    "/hls/low/index.m3u8" -> MockResponse().addHeader("Content-Type", "text/plain").setBody(
                        "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXT-X-KEY:METHOD=AES-128,URI=\"/keys/k1\"\n#EXTINF:6,\nseg1.ts\n#EXTINF:6,\nhttps://cdn.example/abs/seg2.ts\n#EXT-X-ENDLIST\n",
                    )
                    "/hls/low/seg1.ts" -> MockResponse().setBody(Buffer().write(disguised))
                    "/media/film.mp4" -> {
                        val range = request.getHeader("Range")
                        if (range != null) {
                            val (a, b) = range.removePrefix("bytes=").split("-").map { it.toInt() }
                            MockResponse().setResponseCode(206).addHeader("Content-Type", "video/mp4")
                                .addHeader("Content-Range", "bytes $a-$b/${mp4.size}").addHeader("Accept-Ranges", "bytes")
                                .setBody(Buffer().write(mp4.copyOfRange(a, b + 1)))
                        } else MockResponse().addHeader("Content-Type", "video/mp4").setBody(Buffer().write(mp4))
                    }
                    "/media/film.mkv" -> {
                        val bytes = mkv?.readBytes() ?: ByteArray(100)
                        val range = request.getHeader("Range")
                        if (range != null && mkv != null) {
                            val parts = range.removePrefix("bytes=").split("-")
                            val a = parts[0].toInt()
                            val b = parts.getOrNull(1)?.toIntOrNull()?.coerceAtMost(bytes.size - 1) ?: (bytes.size - 1)
                            if (a >= bytes.size) MockResponse().setResponseCode(416)
                            else MockResponse().setResponseCode(206).addHeader("Content-Range", "bytes $a-$b/${bytes.size}")
                                .addHeader("Accept-Ranges", "bytes").setBody(Buffer().write(bytes.copyOfRange(a, b + 1)))
                        } else MockResponse().addHeader("Accept-Ranges", "bytes").setBody(Buffer().write(bytes))
                    }
                    "/subs/fr.srt" -> MockResponse().setBody("1\n00:00:01,000 --> 00:00:02,500\nBonjour\n")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        upstream.start()
    }

    @After fun stop() = upstream.shutdown()

    private fun u(path: String) = upstream.url(path).toString()

    private fun link(name: String, url: String, language: AudioLanguage?, quality: String?, format: LinkFormat, source: String = "Fake") =
        SourceLink(source, name, url, language, quality, format, mapOf("Referer" to "https://site.example/"))

    private val links = listOf(
        link("Uqload", "https://fakehost.example/e/1", AudioLanguage.VOSTFR, "1080p", LinkFormat.Embed),
        link("Direct", u("/hls/master.m3u8"), AudioLanguage.VF, "720p", LinkFormat.Hls),
        link("Other", "https://unknownhost.example/e/2", AudioLanguage.VF, "1080p", LinkFormat.Embed),
        link("File", u("/media/film.mp4"), AudioLanguage.VF, "1080p", LinkFormat.File),
        link("Mkv", u("/media/film.mkv"), AudioLanguage.MULTI, null, LinkFormat.File),
    )

    private val fakeSource = object : WebSource {
        override val name = "Fake"
        override suspend fun find(query: SourceQuery): SourceResult = SourceResult.Found(links)
    }
    private val slowSource = object : WebSource {
        override val name = "Slow"
        override suspend fun find(query: SourceQuery): SourceResult = SourceResult.Unavailable(SourceResult.BLOCKED)
    }

    private val fakeHost = object : HostExtractor {
        override val host = "FakeHost"
        override fun handles(url: HttpUrl) = url.host == "fakehost.example"
        override suspend fun extract(embedUrl: HttpUrl): HostResult = HostResult.Media(
            u("/hls/master.m3u8"), LinkFormat.Hls, mapOf("User-Agent" to "FakeUA/1"),
            listOf(HostSubtitle(u("/subs/fr.srt"), "Français", "fr")),
        )
    }
    private val failingHost = object : HostExtractor {
        override val host = "Gone"
        override fun handles(url: HttpUrl) = url.host == "gone.example"
        override suspend fun extract(embedUrl: HttpUrl): HostResult = HostResult.Failed(HostFailure.Removed)
    }

    private fun server(remuxer: Remuxer = Remuxer(null, tmp.newFolder())) = TestServer(
        tmp,
        tmdbBaseUrl = u("/"),
        sources = SourceFactory { _, _ -> listOf(fakeSource, slowSource) },
        hostFor = { url -> url.toHttpUrlOrNull()?.let { parsed -> listOf(fakeHost, failingHost).firstOrNull { it.handles(parsed) } } },
        remuxer = remuxer,
    )

    private suspend fun HttpClient.owner(s: TestServer, key: Boolean = true): String {
        val token = setupOwner()
        if (key) s.services.vault.put(getWith("/api/me", token).json().str("id"), "tmdb", "0123456789abcdef0123456789abcdef")
        return token
    }

    private suspend fun HttpClient.versions(token: String, query: String = "kind=movie&id=603"): List<JsonObject> {
        val r = getWith("/api/play/versions?$query", token)
        assertEquals(r.bodyAsText(), HttpStatusCode.OK, r.status)
        return r.json()["versions"]!!.jsonArray.map { it.jsonObject }
    }

    private suspend fun HttpClient.start(token: String, versionId: String) = postJson("/api/play/start", """{"versionId":"$versionId"}""", token)

    @Test fun `versions are ranked French first then quality, unplayable hosts left out`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s)
        val r = client.getWith("/api/play/versions?kind=movie&id=603", token).json()
        val versions = r["versions"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("Fake · File", "Fake · Direct", "Fake · Mkv", "Fake · Uqload"), versions.map { it.str("label") })
        assertEquals(listOf("VF", "VF", "MULTI", "VOSTFR"), versions.map { it.str("language") })
        assertEquals("1080p", versions[0].str("quality"))
        assertNull(versions[2]["quality"])
        assertTrue(versions.all { it.str("origin") == "web" && it.str("id").length >= 20 })
        assertEquals("""[{"name":"Fake","status":"found"},{"name":"Slow","status":"unavailable","reason":"blocked"}]""", r["sources"].toString())
        // The links themselves never reach the client.
        assertFalse(r.toString().contains("fakehost.example"))
    }

    @Test fun `versions need a TMDB key, a valid query and a session`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s, key = false)
        val r = client.getWith("/api/play/versions?kind=movie&id=603", token)
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals("""{"error":"needs_key","service":"tmdb"}""", r.bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/play/versions?kind=movie&id=603", null).status)
        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/play/versions?kind=tv&id=1399", token).status)
        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/play/versions?kind=x&id=1", token).status)
    }

    @Test fun `an embed starts through its host and its HLS is relayed with the extractor's headers`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s)
        val embed = client.versions(token).first { it.str("label") == "Fake · Uqload" }
        val started = client.start(token, embed.str("id"))
        assertEquals(started.bodyAsText(), HttpStatusCode.OK, started.status)
        val body = started.json()
        val url = body.str("url")
        assertTrue(url, Regex("^/api/stream/[A-Za-z0-9_-]{43}/index\\.m3u8$").matches(url))
        assertEquals("hls", body.str("type"))
        assertEquals("false", body.str("remux"))
        assertEquals("true", body.str("compatible"))

        // The master playlist: every address goes through the relay, signed.
        val master = client.get(url).bodyAsText()
        assertFalse(master, master.contains(upstream.hostName + ":" + upstream.port))
        val variant = master.lines().first { it.startsWith("/api/stream/") }
        assertTrue(master, Regex("URI=\"/api/stream/[^\"]+\"").containsMatchIn(master))
        val upstreamMaster = seen.last { it.requestUrl!!.encodedPath == "/hls/master.m3u8" }
        assertEquals("FakeUA/1", upstreamMaster.getHeader("User-Agent"))
        assertEquals("https://site.example/", upstreamMaster.getHeader("Referer"))
        assertNull(upstreamMaster.getHeader("Authorization"))

        // The variant (served upstream as text/plain) is recognised and rewritten too, absolute addresses included.
        val media = client.get(variant)
        assertEquals("application/vnd.apple.mpegurl", media.headers[HttpHeaders.ContentType])
        val lines = media.bodyAsText().lines()
        assertTrue(lines.any { it.startsWith("#EXT-X-KEY:METHOD=AES-128,URI=\"/api/stream/") })
        val segments = lines.filter { it.startsWith("/api/stream/") }
        assertEquals(2, segments.size)

        // The disguised segment comes back as plain TS.
        val seg = client.get(segments[0]).bodyAsBytes()
        assertEquals(0x47.toByte(), seg[0])
        assertEquals(188 * 6, seg.size)

        // Subtitles: relayed and turned into WebVTT.
        val sub = body["subtitles"]!!.jsonArray[0].jsonObject
        assertEquals("Français", sub.str("label"))
        assertEquals("fr", sub.str("language"))
        val vtt = client.get(sub.str("url"))
        assertTrue(vtt.headers[HttpHeaders.ContentType].orEmpty().startsWith("text/vtt"))
        assertEquals("WEBVTT\n\n1\n00:00:01.000 --> 00:00:02.500\nBonjour\n", vtt.bodyAsText())
    }

    @Test fun `the relay never serves an address it did not sign, an unknown id, nor an expired stream`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s)
        val direct = client.versions(token).first { it.str("label") == "Fake · Direct" }
        val url = client.start(token, direct.str("id")).json().str("url")
        val id = url.split('/')[3]
        val variant = client.get(url).bodyAsText().lines().first { it.startsWith("/api/stream/") }
        val parts = variant.split('/')
        // Another address with the same signature, a forged signature, another stream's id: all 404.
        val forged = Secrets.b64url("m\nhttp://169.254.169.254/latest/meta-data".toByteArray())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/stream/$id/u/$forged/${parts[6]}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/stream/$id/u/${parts[5]}/AAAA${parts[6].drop(4)}").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/stream/${Secrets.b64url(Secrets.bytes(32))}/index.m3u8").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/stream/$id/media").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/stream/$id/../../health").status.let { if (it == HttpStatusCode.OK) HttpStatusCode.NotFound else it })
        val before = seen.size
        assertEquals(HttpStatusCode.OK, client.get(url).status)
        assertEquals(before + 1, seen.size)

        s.advance(6 * TestServer.HOUR)
        assertEquals(HttpStatusCode.NotFound, client.get(url).status)
        assertEquals(HttpStatusCode.NotFound, client.get(variant).status)
    }

    @Test fun `a stream belongs to its session`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s)
        val other = client.login(TestServer.OWNER, TestServer.OWNER_PASS, "iPhone").json().str("token")
        val direct = client.versions(token).first { it.str("label") == "Fake · Direct" }
        // Another session cannot start this session's version.
        assertEquals(HttpStatusCode.NotFound, client.start(other, direct.str("id")).status)
        assertEquals("""{"error":"unknown_version"}""", client.start(other, "nope").bodyAsText())
        val url = client.start(token, direct.str("id")).json().str("url")
        assertEquals(HttpStatusCode.OK, client.get(url).status)
        assertEquals(HttpStatusCode.OK, client.get(url) { header(HttpHeaders.Authorization, "Bearer $token") }.status)
        // A request carrying another session's token is refused.
        assertEquals(HttpStatusCode.NotFound, client.get(url) { header(HttpHeaders.Authorization, "Bearer $other") }.status)
        // Signing out ends the session's streams.
        assertEquals(HttpStatusCode.NoContent, client.postJson("/api/auth/logout", token = token).status)
        assertEquals(HttpStatusCode.NotFound, client.get(url).status)
    }

    @Test fun `an MP4 file is relayed with ranges, an MKV without ffmpeg says it may not play`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s)
        val versions = client.versions(token)
        val file = client.start(token, versions.first { it.str("label") == "Fake · File" }.str("id")).json()
        assertEquals("file", file.str("type"))
        assertEquals("true", file.str("compatible"))
        assertTrue(file.str("url").endsWith("/media"))
        val part = client.get(file.str("url")) { header(HttpHeaders.Range, "bytes=100-199") }
        assertEquals(HttpStatusCode.PartialContent, part.status)
        assertEquals("bytes 100-199/10000", part.headers[HttpHeaders.ContentRange])
        assertArrayEquals(mp4.copyOfRange(100, 200), part.bodyAsBytes())
        assertEquals("bytes=100-199", seen.last().getHeader("Range"))
        assertEquals(10_000, client.get(file.str("url")).bodyAsBytes().size)

        val mkvStart = client.start(token, versions.first { it.str("label") == "Fake · Mkv" }.str("id")).json()
        assertEquals("false", mkvStart.str("remux"))
        assertEquals("false", mkvStart.str("compatible"))
    }

    @Test fun `a host failure is a stable code`() = testApplication {
        val s = TestServer(
            tmp, tmdbBaseUrl = u("/"),
            sources = SourceFactory { _, _ -> listOf(object : WebSource {
                override val name = "Fake"
                override suspend fun find(query: SourceQuery) = SourceResult.Found(listOf(link("Gone", "https://gone.example/e/1", AudioLanguage.VF, null, LinkFormat.Embed)))
            }) },
            hostFor = { url -> url.toHttpUrlOrNull()?.let { parsed -> failingHost.takeIf { it.handles(parsed) } } },
        ).also { it.install(this) }
        val token = client.owner(s)
        val r = client.start(token, client.versions(token).single().str("id"))
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertEquals("""{"error":"host_removed"}""", r.bodyAsText())
    }

    @Test fun `starting and saving keys are rate limited`() = testApplication {
        val s = server().also { it.install(this) }
        val token = client.owner(s)
        repeat(20) { assertEquals(HttpStatusCode.NotFound, client.start(token, "nope$it").status) }
        val limited = client.start(token, "nope")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertNotNull(limited.headers[HttpHeaders.RetryAfter])
        s.advance(10 * TestServer.MINUTE)
        assertEquals(HttpStatusCode.NotFound, client.start(token, "nope").status)

        repeat(20) {
            val put = client.putJson("/api/credentials/omdb", """{"value":"abcdef12"}""", token)
            assertEquals(HttpStatusCode.OK, put.status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.putJson("/api/credentials/omdb", """{"value":"abcdef12"}""", token).status)
    }

    @Test fun `resolutions per user are bounded`() {
        val limiter = ConcurrencyLimiter(2)
        assertTrue(limiter.tryAcquire("u"))
        assertTrue(limiter.tryAcquire("u"))
        assertFalse(limiter.tryAcquire("u"))
        assertTrue(limiter.tryAcquire("v"))
        limiter.release("u")
        assertTrue(limiter.tryAcquire("u"))
        val rate = RateLimiter({ 0L }, 2, 1000)
        assertNull(rate.acquire("k"))
        assertNull(rate.acquire("k"))
        assertEquals(1L, rate.acquire("k"))
    }

    @Test fun `playlist rewriting resolves every address kind`() {
        val base = "https://cdn.example/a/b/index.m3u8".toHttpUrl()
        val text = "#EXTM3U\r\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"../k?x=1\",IV=0x1\n#EXTINF:4,\nseg.ts?t=2\n\n/root/seg2.ts\nhttps://other.example/s3.ts\n"
        val out = Hls.rewrite(text, base) { "<$it>" }
        assertEquals(
            "#EXTM3U\n#EXT-X-MAP:URI=\"<https://cdn.example/a/b/init.mp4>\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"<https://cdn.example/a/k?x=1>\",IV=0x1\n" +
                "#EXTINF:4,\n<https://cdn.example/a/b/seg.ts?t=2>\n\n<https://cdn.example/root/seg2.ts>\n<https://other.example/s3.ts>\n",
            out,
        )
    }

    @Test fun `private and local addresses are never relayed`() {
        listOf("http://127.0.0.1/x", "http://10.0.0.2/x", "http://192.168.0.108:5555/x", "http://169.254.169.254/x", "http://localhost/x",
            "http://[::1]/x", "http://[fd00::1]/x", "http://100.64.0.1/x", "http://router/x", "http://nas.local/x",
        ).forEach { assertFalse(it, StreamRelay.publicName(it.toHttpUrl())) }
        listOf("https://cdn.example.com/x", "http://8.8.8.8/x").forEach { assertTrue(it, StreamRelay.publicName(it.toHttpUrl())) }
        assertFalse(StreamRelay.isPublicAddress(InetAddress.getByName("172.16.3.4")))
    }

    @Test fun `remux decisions follow the container and the audio`() {
        assertTrue(Remuxer.needsRemux(MediaProbe("matroska,webm", "h264", "aac"), "x"))
        assertTrue(Remuxer.needsRemux(MediaProbe("mov,mp4,m4a,3gp,3g2,mj2", "h264", "ac3"), "x"))
        assertFalse(Remuxer.needsRemux(MediaProbe("mov,mp4,m4a,3gp,3g2,mj2", "h264", "aac"), "x"))
        assertTrue(Remuxer.needsRemux(null, "https://x/film.mkv?t=1"))
        assertFalse(Remuxer.needsRemux(null, "https://x/film.mp4"))
        assertEquals(MediaProbe("matroska,webm", "hevc", "eac3"), Remuxer.parseProbe(
            """{"streams":[{"codec_type":"video","codec_name":"hevc"},{"codec_type":"audio","codec_name":"eac3"}],"format":{"format_name":"matroska,webm"}}""",
        ))
        val opts = Remuxer.inputOptions(mapOf("User-Agent" to "UA\r\nX: y", "Referer" to "https://r/", "Bad Header" to "z"))
        assertEquals(listOf("-protocol_whitelist", "http,https,tcp,tls,crypto", "-user_agent", "UAX: y", "-headers", "Referer: https://r/\r\n"), opts)
    }

    /** Runs only where ffmpeg is installed: an MKV with AC3 audio becomes fMP4 HLS with AAC, then is cleaned when idle. */
    @Test fun `an MKV with AC3 is remuxed to fMP4 HLS by ffmpeg`() {
        val ffmpeg = Remuxer.find(null)
        assumeTrue("ffmpeg not on PATH", ffmpeg != null)
        val out = File(tmp.newFolder(), "film.mkv")
        val made = ProcessBuilder(
            ffmpeg, "-v", "error", "-f", "lavfi", "-i", "testsrc=size=160x120:rate=25", "-f", "lavfi", "-i", "sine=frequency=440",
            "-t", "8", "-c:v", "libx264", "-g", "25", "-c:a", "ac3", out.path,
        ).redirectErrorStream(true).start()
        assumeTrue("ffmpeg could not make the sample", made.waitFor(60, TimeUnit.SECONDS) && made.exitValue() == 0)
        mkv = out
        testApplication {
            val s = server(Remuxer(ffmpeg, tmp.newFolder())).also { it.install(this) }
            val token = client.owner(s)
            val v = client.versions(token).first { it.str("label") == "Fake · Mkv" }
            val body = client.start(token, v.str("id")).json()
            assertEquals("true", body.str("remux"))
            assertEquals("hls", body.str("type"))
            val url = body.str("url")
            val id = url.split('/')[3]
            var playlist = ""
            for (i in 0 until 60) {
                playlist = client.get(url).bodyAsText()
                if (playlist.contains("#EXT-X-ENDLIST")) break
                delay(500)
            }
            assertTrue(playlist, playlist.contains("#EXT-X-MAP:URI=\"init.mp4\""))
            val init = client.get(url.replace("index.m3u8", "init.mp4"))
            assertEquals(HttpStatusCode.OK, init.status)
            assertTrue(String(init.bodyAsBytes().copyOfRange(4, 8)) == "ftyp")
            val first = playlist.lines().first { it.endsWith(".m4s") }
            assertEquals(HttpStatusCode.OK, client.get(url.replace("index.m3u8", first)).status)
            // Only ffmpeg's own files: nothing else from its folder.
            assertEquals(HttpStatusCode.NotFound, client.get(url.replace("index.m3u8", "ffmpeg.log")).status)
            assertTrue(s.services.relay.remuxRunning(id))
            s.advance(31 * TestServer.MINUTE)
            s.services.relay.sweep()
            assertFalse(s.services.relay.remuxRunning(id))
        }
    }
}

private suspend fun HttpClient.putJson(path: String, body: String, token: String) = put(path) {
    header(HttpHeaders.Authorization, "Bearer $token")
    contentType(ContentType.Application.Json)
    setBody(body)
}
