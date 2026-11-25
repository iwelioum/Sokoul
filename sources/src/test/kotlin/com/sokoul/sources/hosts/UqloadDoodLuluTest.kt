package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UqloadTest {
    private val setup = """jwplayer("vplayer").setup({sources:["https://m12.uqload.to/hls/abc/master.m3u8"],""" +
        """tracks:[{file:"https://uqload.to/sub/demo_fre.vtt",label:"Français",kind:"captions","default":true},""" +
        """{file:"/empty.srt",label:"Upload captions",kind:"captions"},""" +
        """{file:"https://uqload.to/thumbs/demo.vtt",kind:"thumbnails"},""" +
        """{file:"https://elsewhere.test/sub.vtt",label:"English"}]});"""

    @Test fun anEmptyEmbedShellFallsBackToThePlainPage() = runTest {
        FakeHost().use { host ->
            // As served on 2026-11-25: the embed page is a 38-byte shell, the plain page holds the player.
            host.page("/embed-demo.html", "<!DOCTYPE html>\n<html lang=\"en\">\n<head")
            host.page("/demo.html", """<html><script>${pack(setup)}</script></html>""")
            val media = Uqload(host.fetcher).extract(host.url("/embed-demo.html")).asMedia()
            assertEquals("https://m12.uqload.to/hls/abc/master.m3u8", media.url)
        }
    }

    @Test fun thePackedSetupGivesTheMasterAndItsCaptions() = runTest {
        FakeHost().use { host ->
            host.page("/embed-demo.html", """<html><script>${pack(setup)}</script><a href="https://example.test/">x</a></html>""")
            val media = Uqload(host.fetcher).extract(host.url("/demo")).asMedia()
            assertEquals("https://m12.uqload.to/hls/abc/master.m3u8", media.url)
            assertEquals(LinkFormat.Hls, media.format)
            assertEquals(listOf(HostSubtitle("https://uqload.to/sub/demo_fre.vtt", "Français", "fr", default = true)), media.subtitles)
        }
    }

    @Test fun whenTheEmbedPageFailsTheVideoPageIsTried() = runTest {
        FakeHost().use { host ->
            host.status("/embed-demo.html", 500)
            host.page("/demo.html", """<script>sources: ["https://m1.uqload.cx/abc/v.mp4"]</script>""")
            val media = Uqload(host.fetcher).extract(host.url("/embed-demo.html")).asMedia()
            assertEquals("https://m1.uqload.cx/abc/v.mp4", media.url)
            assertEquals(LinkFormat.File, media.format)
        }
    }

    @Test fun deletedFilesForeignMediaAndSilence() = runTest {
        FakeHost().use { host ->
            host.page("/embed-deleted.html", "<h1>File was deleted</h1>")
            host.page("/embed-foreign.html", """sources: ["https://cdn.elsewhere.test/master.m3u8"]""")
            host.silent("/embed-slow.html")
            host.silent("/slow.html")
            val uqload = Uqload(host.fetcher)
            assertFailed(HostFailure.Removed, uqload.extract(host.url("/embed-deleted.html")))
            assertFailed(HostFailure.NotFound, uqload.extract(host.url("/embed-foreign.html")))
            assertFailed(HostFailure.Timeout, uqload.extract(host.url("/embed-slow.html")))
            assertFailed(HostFailure.Unsupported, uqload.extract(host.url("/embed-bad%20id.html")))
        }
    }
}

class DoodStreamTest {
    private val player = """<script>${'$'}.get('/pass_md5/12345-6789/abcdef', function(d){ makePlay(d) });""" +
        """function makePlay(){ return a + "?token=tok123&expiry=" + Date.now(); }</script>"""

    @Test fun thePassAnswerIsCompletedWithSuffixTokenAndExpiry() = runTest {
        FakeHost().use { host ->
            host.page("/e/demo", player)
            host.page("/pass_md5/12345-6789/abcdef", "https://cdn.test/base/file")
            val media = DoodStream(host.fetcher, now = { 42L }).extract(host.url("/e/demo")).asMedia()
            assertTrue(media.url, Regex("""https://cdn\.test/base/file[A-Za-z0-9]{10}\?token=tok123&expiry=42""").matches(media.url))
            assertEquals(host.url("/e/demo").toString(), host.requestTo("/pass_md5/12345-6789/abcdef").getHeader("Referer"))
        }
    }

    @Test fun aShellPageLeadsToItsPlayerAndR2AddressesStayAsTheyAre() = runTest {
        FakeHost().use { host ->
            host.page("/d/demo", """<iframe src="/e/demo" allowfullscreen></iframe>""")
            host.page("/e/demo", "/pass_md5/a/demo")
            host.page("/pass_md5/a/demo", "https://fixture.cloudflarestorage.com/video.mp4")
            val media = DoodStream(host.fetcher).extract(host.url("/d/demo")).asMedia()
            assertEquals("https://fixture.cloudflarestorage.com/video.mp4", media.url)
            assertEquals(LinkFormat.File, media.format)
        }
    }

    @Test fun noPassPathIsADeletedFileAndAnOddPassAnswerIsAChangedHost() = runTest {
        FakeHost().use { host ->
            host.page("/e/gone", "<html>Video not found or has been removed</html>")
            host.page("/e/odd", "/pass_md5/a/odd")
            host.page("/pass_md5/a/odd", "<html>RELOAD</html>")
            host.silent("/e/slow")
            val dood = DoodStream(host.fetcher)
            assertFailed(HostFailure.Removed, dood.extract(host.url("/e/gone")))
            assertFailed(HostFailure.HostChanged, dood.extract(host.url("/e/odd")))
            assertFailed(HostFailure.Timeout, dood.extract(host.url("/e/slow")))
        }
    }
}

class LuluStreamTest {
    @Test fun theUnpackedSetupGivesTheMasterWithThePinnedLanguage() = runTest {
        FakeHost().use { host ->
            host.page("/e/demo", "<script>${pack("""jwplayer("p").setup({sources:[{file:"$MASTER"}]});""")}</script>")
            val media = LuluStream(host.fetcher).extract(host.url("/demo.html")).asMedia()
            assertEquals(MASTER, media.url)
            assertEquals(PINNED_ACCEPT_LANGUAGE, media.headers["Accept-Language"])
            assertEquals(PINNED_ACCEPT_LANGUAGE, host.requestTo("/e/demo").getHeader("Accept-Language"))
        }
    }

    @Test fun noSourceRemovedRefusedAndSilent() = runTest {
        FakeHost().use { host ->
            host.page("/e/none", "<html>File is no longer available</html>")
            host.status("/e/gone", 404)
            host.status("/e/refused", 403)
            host.silent("/e/slow")
            val lulu = LuluStream(host.fetcher)
            assertFailed(HostFailure.NotFound, lulu.extract(host.url("/e/none")))
            assertFailed(HostFailure.Removed, lulu.extract(host.url("/e/gone")))
            assertFailed(HostFailure.Blocked, lulu.extract(host.url("/e/refused")))
            assertFailed(HostFailure.Timeout, lulu.extract(host.url("/e/slow")))
        }
    }
}
