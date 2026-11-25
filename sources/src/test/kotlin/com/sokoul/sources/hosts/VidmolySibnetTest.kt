package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class VidmolyTest {
    @Test fun theJwplayerSourceIsTheMediaAskedWithThePartnerReferer() = runTest {
        FakeHost().use { host ->
            host.page("/embed-demo.html", """<script>player.setup({ sources: [{file: "$MASTER"}], image: "x.jpg" });</script>""")
            val media = Vidmoly(host.fetcher).extract(host.url("/embed-demo.html")).asMedia()
            assertEquals(MASTER, media.url)
            assertEquals(LinkFormat.Hls, media.format)
            assertEquals("https://voirdrama.to/", host.requestTo("/embed-demo.html").getHeader("Referer"))
            assertEquals("https://voirdrama.to/", media.headers["Referer"])
        }
    }

    @Test fun aDashManifestIsNotPlayableAndAPageWithoutSourceHasNone() = runTest {
        FakeHost().use { host ->
            host.page("/embed-dash.html", """sources: [{file: "https://cdn.test/manifest.mpd"}]""")
            host.page("/embed-none.html", "<html>Not found</html>")
            host.status("/embed-gone.html", 410)
            host.silent("/embed-slow.html")
            val vidmoly = Vidmoly(host.fetcher)
            assertFailed(HostFailure.NotFound, vidmoly.extract(host.url("/embed-dash.html")))
            assertFailed(HostFailure.NotFound, vidmoly.extract(host.url("/embed-none.html")))
            assertFailed(HostFailure.Removed, vidmoly.extract(host.url("/embed-gone.html")))
            assertFailed(HostFailure.Timeout, vidmoly.extract(host.url("/embed-slow.html")))
        }
    }
}

class SibnetTest {
    private val permalink = "https://video.sibnet.ru/video123-Some_title.html".toHttpUrl()

    @Test fun aPermalinkIsAskedAsThePlayerAndTheMediaRedirectIsResolvedWithoutDownloading() = runTest {
        FakeHost().use { host ->
            host.page("/shell.php?videoid=123", """<script>player.src([{src: "/v/abc/123.mp4", type: "video/mp4"}]);</script>""")
            host.redirect("/v/abc/123.mp4", host.url("/cdn/dv97/123.mp4").toString())
            host.answer("/cdn/dv97/123.mp4") { okhttp3.mockwebserver.MockResponse().setResponseCode(206).setBody("x") }
            val media = Sibnet(host.fetcher, origin = host.url("/")).extract(permalink).asMedia()
            assertEquals(host.url("/cdn/dv97/123.mp4").toString(), media.url)
            assertEquals(LinkFormat.File, media.format)
            assertEquals("bytes=0-0", host.requestTo("/v/abc/123.mp4").getHeader("Range"))
            assertEquals("https://video.sibnet.ru/", media.headers["Referer"])
        }
    }

    @Test fun whenTheRedirectFailsTheFirstAddressStillPlays() = runTest {
        FakeHost().use { host ->
            host.page("/shell.php?videoid=9", """player.src([{src:"/v/9.mp4"}])""")
            host.status("/v/9.mp4", 500)
            val shell = "https://video.sibnet.ru/shell.php?videoid=9".toHttpUrl()
            assertEquals(host.url("/v/9.mp4").toString(), Sibnet(host.fetcher, origin = host.url("/")).extract(shell).asMedia().url)
        }
    }

    @Test fun unknownLinksRemovedVideosPagesWithoutSourceAndSilence() = runTest {
        FakeHost().use { host ->
            host.status("/shell.php?videoid=1", 404)
            host.page("/shell.php?videoid=2", "<html>Видео не найдено</html>")
            host.silent("/shell.php?videoid=3")
            val sibnet = Sibnet(host.fetcher, origin = host.url("/"))
            assertFailed(HostFailure.Unsupported, sibnet.extract("https://video.sibnet.ru/users/someone/".toHttpUrl()))
            assertFailed(HostFailure.Removed, sibnet.extract("https://video.sibnet.ru/video1".toHttpUrl()))
            assertFailed(HostFailure.NotFound, sibnet.extract("https://video.sibnet.ru/video2".toHttpUrl()))
            assertFailed(HostFailure.Timeout, sibnet.extract("https://video.sibnet.ru/video3".toHttpUrl()))
        }
    }
}
