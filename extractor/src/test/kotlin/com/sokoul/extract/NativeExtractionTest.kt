package com.sokoul.extract

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class NativeExtractionTest {
    @Test fun canonicalVidmolyAndAliasUseNativeBridgeAndMetadata() = runBlocking {
        val http = HttpBridge { request ->
            assertTrue(request.url.contains("ansembed.net"))
            BridgeResponse(200, "OK", listOf("Content-Type" to "text/html"), request.url,
                "<html><script>jwplayer('v').setup({sources:[{file:'https://cdn.example/live/master.m3u8'}]});</script></html>".toByteArray())
        }
        val runtime = ExtractorRuntime(http, { LocalExtractor.canonicalScript() }, "Fixture UA")
        try {
            val result = runtime.extract("https://ansembed.net/embed-fixture.html")
            assertEquals(result.error, "https://cdn.example/live/master.m3u8", result.url)
            assertEquals(true, result.isHls)
            assertTrue(result.headers["User-Agent"].orEmpty().startsWith("Mozilla/"))
            assertTrue(result.headers.containsKey("Referer"))
        } finally { runtime.close() }
    }

    @Test fun dynamicFsvidPlayerUsesIsolatedNativeSandbox() = runBlocking {
        val http = HttpBridge { request -> BridgeResponse(200, "OK", listOf("Content-Type" to "text/html"), request.url,
            """<script>var config = {sources:[{file: atob('aHR0cHM6Ly9jZG4uZXhhbXBsZS9tYXN0ZXIubTN1OA==')}]}; jwplayer('v').setup(config);</script>""".toByteArray()) }
        val runtime = ExtractorRuntime(http, { LocalExtractor.canonicalScript() }, "Fixture UA")
        try { assertEquals("https://cdn.example/master.m3u8", runtime.extract("https://fsvid.lol/embed-fixture.html").url) }
        finally { runtime.close() }
    }

    @Test fun cancellationStopsTransportAndNextExtractionWorks() = runBlocking {
        var cancelled = false
        // The transport must have been reached before the cancel: a fixed pause raced the runtime's start on a busy machine.
        val reached = kotlinx.coroutines.CompletableDeferred<Unit>()
        val http = HttpBridge { request ->
            reached.complete(Unit)
            try { delay(60_000) } finally { cancelled = true }
            BridgeResponse(200, "OK", emptyList(), request.url, ByteArray(0))
        }
        val runtime = ExtractorRuntime(http, { LocalExtractor.canonicalScript() }, "Fixture UA")
        try {
            val job = launch { runtime.extract("https://ansembed.net/embed-fixture.html") }
            kotlinx.coroutines.withTimeout(30_000) { reached.await() }
            job.cancelAndJoin()
            assertTrue(cancelled)
            assertFalse(runtime.extract("https://unknown.example/embed-fixture.html").ok)
        } finally { runtime.close() }
    }

    @Test fun publicNetworkRejectsPrivateAndCredentialDestinations() {
        for (address in listOf("127.0.0.1", "10.0.0.1", "192.168.0.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1")) assertFalse(address, PublicNetwork.isPublic(InetAddress.getByName(address)))
        assertTrue(PublicNetwork.isPublic(InetAddress.getByName("1.1.1.1")))
        assertFalse(PublicNetwork.validUrl("https://user:secret@cdn.example/a"))
        assertFalse(PublicNetwork.validUrl("https://localhost/a"))
        assertFalse(PublicNetwork.validUrl("file:///tmp/a"))
    }
}
