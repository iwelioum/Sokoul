package com.sokoul.sources.hosts

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** The packer reader, the decoders and the page fetcher's limits. */
class BuildingBlocksTest {
    private val setup = """jwplayer("vplayer").setup({sources:[{file:"$MASTER"}],image:"https://img.test/poster.jpg"});"""

    @Test fun aPackedScriptUnpacksToTheOriginal() {
        val page = "<html><script type='text/javascript'>${pack(setup)}</script></html>"
        assertEquals(listOf(setup), PackedScript.unpackAll(page))
    }

    @Test fun packedScriptsAboveBase36UseCapitalDigits() {
        val words = (0 until 40).map { "w$it" }
        assertEquals("w10 w11 w36 w39", PackedScript.expand("a b A D", 62, words))
        // A word that is not a number in the radix, or has an empty entry, stays as it is.
        assertEquals("zz _x keep", PackedScript.expand("zz _x keep", 62, words.take(3)))
    }

    @Test fun packedScriptsInEitherQuoteAndWithEscapesAreRead() {
        val packed = "eval(function(p,a,c,k,e,r){return p}(\"0 \\\"1\\\"\",10,2,\"say|hi\".split(\"|\"),0,{}))"
        assertEquals(listOf("say \"hi\""), PackedScript.unpackAll(packed))
    }

    @Test fun aPageWithoutPackerHasNoScripts() {
        assertEquals(emptyList<String>(), PackedScript.unpackAll("<script>var a = 1;</script>"))
        // Truncated packer: no dictionary.
        assertEquals(emptyList<String>(), PackedScript.unpackAll("eval(function(p,a,c,k,e,d){}('0 1',10,2"))
    }

    @Test fun lzwRoundTrips() {
        for (text in listOf("111", "abababababab", MASTER, "TOBEORNOTTOBEORTOBEORNOT")) {
            assertEquals(text, Codecs.lzw(lzwPack(text)))
        }
        assertEquals("", Codecs.lzw(""))
    }

    @Test fun hexMustBeEvenAndValidUtf8() {
        assertEquals("hi", Codecs.hexToUtf8("6869"))
        assertNull(Codecs.hexToUtf8("686"))
        assertNull(Codecs.hexToUtf8("zz"))
        assertNull(Codecs.hexToUtf8("ff"))
    }

    @Test fun theFetcherMapsStatusesToFailures() = runTest {
        FakeHost().use { host ->
            host.status("/gone", 404)
            host.status("/refused", 403)
            host.status("/broken", 502)
            host.status("/odd", 418)
            assertEquals(HostFailure.Removed, failureOf { host.fetcher.get(host.url("/gone"), emptyMap()) })
            assertEquals(HostFailure.Blocked, failureOf { host.fetcher.get(host.url("/refused"), emptyMap()) })
            assertEquals(HostFailure.Network, failureOf { host.fetcher.get(host.url("/broken"), emptyMap()) })
            assertEquals(HostFailure.HostChanged, failureOf { host.fetcher.get(host.url("/odd"), emptyMap()) })
        }
    }

    @Test fun theFetcherFollowsABoundedNumberOfRedirects() = runTest {
        FakeHost().use { host ->
            host.redirect("/a", "/b")
            host.page("/b", "here")
            host.redirect("/loop", "/loop")
            val page = host.fetcher.get(host.url("/a"), emptyMap())
            assertEquals("here", page.text)
            assertEquals(host.url("/b"), page.url)
            assertEquals(HostFailure.HostChanged, failureOf { host.fetcher.get(host.url("/loop"), emptyMap()) })
        }
    }

    @Test fun theFetcherRefusesOversizedBodiesAndTimesOut() = runTest {
        FakeHost().use { host ->
            host.page("/big", "x".repeat(2048))
            host.silent("/slow")
            val small = PageFetcher(OkHttpClient(), timeout = 1.seconds, maxBytes = 1024, allowTarget = { true })
            assertEquals(HostFailure.HostChanged, failureOf { small.get(host.url("/big"), emptyMap()) })
            assertEquals(HostFailure.Timeout, failureOf { host.fetcher.get(host.url("/slow"), emptyMap()) })
        }
    }

    @Test fun byDefaultTheFetcherNeverAsksALocalOrLiteralAddress() = runTest {
        FakeHost().use { host ->
            host.page("/x", "secret")
            val strict = PageFetcher(OkHttpClient())
            assertEquals(HostFailure.HostChanged, failureOf { strict.get(host.url("/x"), emptyMap()) })
            assertTrue(host.requests.isEmpty())
        }
    }

    private suspend fun failureOf(block: suspend () -> Unit): HostFailure? = try {
        block()
        null
    } catch (e: HostError) {
        e.reason
    }
}
