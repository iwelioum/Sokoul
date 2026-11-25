package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VeevTest {
    private fun hex(text: String) = text.toByteArray().joinToString("") { "%02x".format(it) }

    /** Challenge "111": one group holding operation 1 (reverse, then hex). */
    private val page = " var cfg = { fc:'${lzwPack("111")}' };"
    private fun answer(status: String) =
        """{"file":{"file_status":"$status","dv":[{"s":"${lzwPack(hex(MASTER).reversed())}"}]}}"""

    @Test fun theChallengeOpensTheApiAndItsOperationsDecodeTheAddress() = runTest {
        FakeHost().use { host ->
            host.page("/e/demo", page)
            host.page("/dl", answer("OK"))
            val media = Veev(host.fetcher).extract(host.url("/demo")).asMedia()
            assertEquals(MASTER, media.url)
            assertEquals(LinkFormat.Hls, media.format)
            val api = host.requestTo("/dl").requestUrl!!
            assertEquals("gi", api.queryParameter("cmd"))
            assertEquals("demo", api.queryParameter("file_code"))
            assertEquals("111", api.queryParameter("ch"))
        }
    }

    @Test fun challengeGroupsAreCountsThenReversedDigits() {
        assertEquals(listOf(listOf(3, 2, 1), listOf(5, 4)), Veev.operationsOf("3123245"))
        assertEquals(listOf(listOf(1)), Veev.operationsOf("11x9"))
    }

    @Test fun anUnavailableFileAMalformedApiAPageWithoutChallengeAndSilence() = runTest {
        FakeHost().use { host ->
            host.page("/e/gone", page)
            host.page("/e/odd", page)
            host.page("/e/plain", "<html>no challenge</html>")
            host.silent("/e/slow")
            val veev = Veev(host.fetcher)
            host.page("/dl", answer("DELETED"))
            assertFailed(HostFailure.Removed, veev.extract(host.url("/e/gone")))
            host.page("/dl", "<html>oops</html>")
            assertFailed(HostFailure.NotFound, veev.extract(host.url("/e/odd")))
            assertFailed(HostFailure.NotFound, veev.extract(host.url("/e/plain")))
            assertFailed(HostFailure.Timeout, veev.extract(host.url("/e/slow")))
        }
    }
}

class VidaraTest {
    @Test fun theStreamApiAnswersTheMaster() = runTest {
        FakeHost().use { host ->
            host.page("/api/stream", """{"streaming_url":"$MASTER","title":"x"}""")
            val media = Vidara(host.fetcher).extract(host.url("/e/abc123")).asMedia()
            assertEquals(MASTER, media.url)
            assertEquals(LinkFormat.Hls, media.format)
            val request = host.requestTo("/api/stream")
            assertEquals("POST", request.method)
            assertEquals("""{"filecode":"abc123","device":"web"}""", request.body.readUtf8())
            assertTrue(request.getHeader("Referer")!!.endsWith("/e/abc123"))
        }
    }

    @Test fun noStreamingUrlIsRemovedAndGarbageIsAChangedHost() = runTest {
        FakeHost().use { host ->
            val vidara = Vidara(host.fetcher)
            host.page("/api/stream", """{"error":"not found"}""")
            assertFailed(HostFailure.Removed, vidara.extract(host.url("/e/abc")))
            host.page("/api/stream", "<html>maintenance</html>")
            assertFailed(HostFailure.HostChanged, vidara.extract(host.url("/e/abc")))
            assertFailed(HostFailure.Unsupported, vidara.extract(host.url("/e/a-b")))
            host.silent("/api/stream")
            assertFailed(HostFailure.Timeout, vidara.extract(host.url("/e/abc")))
        }
    }
}

class SeekStreamingTest {
    private fun encrypt(json: String, key: String = "kiemtienmua911ca"): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec("1234567890oiuytr".toByteArray()))
        return cipher.doFinal(json.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test fun theEncryptedAnswerIsDecryptedAndTheNativeStreamComesFirst() = runTest {
        FakeHost().use { host ->
            host.page("/api/v1/video", "\"${encrypt("""{"source":"https://cdn.test/other.m3u8","cfNative":"$MASTER"}""")}\"")
            val embed = host.url("/").newBuilder().fragment("abc_12").build()
            val media = SeekStreaming(host.fetcher).extract(embed).asMedia()
            assertEquals(MASTER, media.url)
            assertEquals(LinkFormat.Hls, media.format)
            val origin = HostUrls.originOf(host.url("/"))
            assertEquals(mapOf("Origin" to origin, "Referer" to "$origin/"), media.headers)
            assertEquals("abc_12", host.requestTo("/api/v1/video").requestUrl!!.queryParameter("id"))
        }
    }

    @Test fun aWrongKeyNoSourceRemovedAndSilent() = runTest {
        FakeHost().use { host ->
            val seek = SeekStreaming(host.fetcher)
            val embed = host.url("/").newBuilder().fragment("abc").build()
            host.page("/api/v1/video", encrypt("""{"source":"$MASTER"}""", key = "0000000000000000"))
            assertFailed(HostFailure.HostChanged, seek.extract(embed))
            host.page("/api/v1/video", encrypt("""{"title":"x"}"""))
            assertFailed(HostFailure.NotFound, seek.extract(embed))
            host.status("/api/v1/video", 404)
            assertFailed(HostFailure.Removed, seek.extract(embed))
            host.silent("/api/v1/video")
            assertFailed(HostFailure.Timeout, seek.extract(embed))
            assertFailed(HostFailure.Unsupported, seek.extract(host.url("/video/abc")))
        }
    }
}

class FsvidVidzyTest {
    private val page = """<script>sources:[{file:"$MASTER?t=abcdefghijklmnop"}]</script>"""

    @Test fun theStaticMasterGetsTheGateKeyAndTheSessionCookie() = runTest {
        FakeHost().use { host ->
            host.page("/embed-demo.html", page)
            host.page("/gk", """{"gk":"key0123456789abcdef.1700000000.3600"}""")
            val media = Fsvid(host.fetcher, gates = listOf(host.url("/gk"))).extract(host.url("/embed-demo.html")).asMedia()
            assertEquals("$MASTER?t=abcdefghijklmnop&gk=key0123456789abcdef.1700000000.3600", media.url)
            assertEquals("vz_pe=abcdefghijklmnop; fsv_pe=abcdefghijklmnop", media.headers["Cookie"])
            assertEquals("https://fsvid.lol/", media.headers["Referer"])
            assertEquals("https://fsmirror46.lol/", host.requestTo("/embed-demo.html").getHeader("Referer"))
            assertEquals("p1", host.requestTo("/gk").requestUrl!!.queryParameter("v"))
        }
    }

    @Test fun aPackedMasterWithoutGateStillPlays() = runTest {
        FakeHost().use { host ->
            host.page("/embed/demo", "<script>${pack("""player.src({src:"$MASTER?t=abcdefghijklmnop",type:"application/x-mpegURL"});""")}</script>")
            val media = Vidzy(host.fetcher, gates = listOf(host.url("/gate.php"))).extract(host.url("/embed/demo")).asMedia()
            assertEquals("$MASTER?t=abcdefghijklmnop", media.url)
            assertEquals("https://vidzy.org/", media.headers["Referer"])
        }
    }

    @Test fun decoysDynamicPagesOtherPathsAndSilence() = runTest {
        FakeHost().use { host ->
            host.page("/embed-decoy.html", """<script>var f="https://cdn.test/troll.m3u8";</script>""")
            host.page("/embed-dynamic.html", """<script>var s=atob(p);for(var i=0;i<s.length;i++){o+=String.fromCharCode(s.charCodeAt(i)^k)}</script>""")
            host.silent("/embed-slow.html")
            val fsvid = Fsvid(host.fetcher, gates = emptyList())
            assertFailed(HostFailure.NotFound, fsvid.extract(host.url("/embed-decoy.html")))
            assertFailed(HostFailure.Unsupported, fsvid.extract(host.url("/embed-dynamic.html")))
            assertFailed(HostFailure.Unsupported, fsvid.extract(host.url("/watch/demo")))
            assertFailed(HostFailure.Timeout, fsvid.extract(host.url("/embed-slow.html")))
        }
    }
}
