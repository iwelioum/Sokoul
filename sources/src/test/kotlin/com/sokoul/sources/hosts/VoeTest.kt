package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class VoeTest {
    /** The obfuscation of HOSTS.md run forwards: JSON, Base64, reversed, every code + 3, Base64, markers in, ROT13. */
    private fun obfuscate(json: String, markers: List<String>): String {
        val inner = Base64.getEncoder().encodeToString(json.toByteArray()).reversed().map { it + 3 }.joinToString("")
        val outer = Base64.getEncoder().encodeToString(inner.toByteArray(Charsets.ISO_8859_1))
        val marked = outer.chunked(5).mapIndexed { i, chunk -> chunk + markers[i % markers.size] }.joinToString("")
        return marked.map { c ->
            when (c) {
                in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                else -> c
            }
        }.joinToString("")
    }

    @Test fun anObfuscatedPageIsDecodedWithTheMarkersOfItsBundle() = runTest {
        FakeHost().use { host ->
            val markers = listOf("<%", "&]")
            val payload = obfuscate("""{"file":"https://cdn.test/v.mp4","source":"$MASTER"}""", markers)
            host.page("/e/demo", """<script type="application/json">["$payload"]</script><script src="/js/player.js"></script>""")
            host.page("/js/player.js", "var x=1;var m=['<%','&]'];function f(){}")
            val media = Voe(host.fetcher).extract(host.url("/e/demo")).asMedia()
            assertEquals(MASTER, media.url)
            assertEquals(LinkFormat.Hls, media.format)
            assertEquals("https://voe.sx/", media.headers["Referer"])
        }
    }

    @Test fun withoutItsBundleTheOlderMarkersAreTried() = runTest {
        FakeHost().use { host ->
            val payload = obfuscate("""{"direct_access_url":"https://cdn.test/v.mp4"}""", listOf("@$", "#&"))
            host.page("/e/demo", """<script type="application/json">["$payload"]</script><script src="/js/missing.js"></script>""")
            val media = Voe(host.fetcher).extract(host.url("/e/demo")).asMedia()
            assertEquals("https://cdn.test/v.mp4", media.url)
            assertEquals(LinkFormat.File, media.format)
        }
    }

    @Test fun aPlainPageAfterAScriptHopGivesItsHlsField() = runTest {
        FakeHost().use { host ->
            host.page("/e/start", "<script>window.location.href = '/e/real';</script>")
            host.page("/e/real", """{"hls":"${MASTER.replace("/", "\\/")}"}""")
            assertEquals(MASTER, Voe(host.fetcher).extract(host.url("/e/start")).asMedia().url)
        }
    }

    @Test fun absentMediaRemovedPagesBrokenPayloadsAndSilence() = runTest {
        FakeHost().use { host ->
            host.page("/e/empty", "<html>Video not found</html>")
            host.status("/e/gone", 404)
            host.page("/e/broken", """<script type="application/json">["%%%not-a-payload%%%"]</script><script src="/js/p.js"></script>""")
            host.silent("/e/slow")
            val voe = Voe(host.fetcher)
            assertFailed(HostFailure.NotFound, voe.extract(host.url("/e/empty")))
            assertFailed(HostFailure.Removed, voe.extract(host.url("/e/gone")))
            assertFailed(HostFailure.NotFound, voe.extract(host.url("/e/broken")))
            assertFailed(HostFailure.Timeout, voe.extract(host.url("/e/slow")))
        }
    }
}
