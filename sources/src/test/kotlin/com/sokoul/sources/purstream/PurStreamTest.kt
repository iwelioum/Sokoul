package com.sokoul.sources.purstream

import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/** PurStream: the work recognised by a TMDB image, then its streams as typed links. */
class PurStreamTest {
    private fun site(apiCode: Int = 200) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/status" -> MockResponse().setResponseCode(404)
                apiCode != 200 -> MockResponse().setResponseCode(apiCode)
                else -> when (request.path) {
                    "/api/v1/search-bar/search/Inception" -> MockResponse().setBody(
                        """{"type":"success","data":{"items":{"movies":{"items":[
                            {"id":11,"type":"movie","title":"Inception (other)","large_poster_path":"https://img.test/w500/other.jpg"},
                            {"id":2458,"type":"movie","title":"Inception","large_poster_path":"https://img.test/w500/aej3.jpg?x=1"}]}}}}""")
                    "/api/v1/stream/2458" -> MockResponse().setBody(
                        """{"type":"success","data":{"items":{"sources":[{"stream_url":"https://cdn.test/movies/27205/master.m3u8","source_name":"pulse | 1080p | MULTI","format":"m3u8"}]}}}""")
                    "/api/v1/search-bar/search/The%20Handmaid%27s%20Tale" -> MockResponse().setBody(
                        """{"type":"success","data":{"items":{"movies":{"items":[{"id":77,"type":"tv","title":"The Handmaid's Tale","wallpaper_poster_path":"https://img.test/hm.jpg"}]}}}}""")
                    "/api/v1/stream/77/episode?season=4&episode=6" -> MockResponse().setBody(
                        """{"type":"success","data":{"items":{"sources":[{"stream_url":"https://cdn.test/tv/77/4/6/master.m3u8","source_name":"pulse | 1080p | MULTI","format":"m3u8"}]}}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }

    private fun source(site: MockWebServer, images: Set<String>) = PurStream(
        OkHttpClient(),
        tmdb = { kind, _ -> TmdbClient.NamesAndImages(if (kind == "movie") "Inception" else "The Handmaid's Tale", null, null, images) },
        statusUrl = site.url("/status").toString(),
        fallbackApi = site.url("/api/v1").toString(),
    )

    private val inception = SourceQuery("movie", 27205)

    @Test fun theFilmIsTheResultWithATmdbImageAndItsStreamsAreTypedLinks() = runTest {
        val site = site()
        try {
            val link = (source(site, setOf("/aej3.jpg")).find(inception) as SourceResult.Found).links.single()
            assertEquals("https://cdn.test/movies/27205/master.m3u8", link.url)
            assertEquals(LinkFormat.Hls, link.format)
            assertEquals("PurStream", link.source)
            assertEquals("pulse | 1080p | MULTI", link.name)
            // The CDN wants the site of the day as Referer: the site behind the API host in use (here the fallback).
            assertEquals(mapOf("Referer" to "https://${site.url("/").host}/"), link.headers)
        } finally { site.shutdown() }
    }

    @Test fun anEpisodeAsksItsSeasonAndNumber() = runTest {
        val site = site()
        try {
            val result = source(site, setOf("/hm.jpg")).find(SourceQuery("tv", 69478, 4, 6)) as SourceResult.Found
            assertEquals(listOf("https://cdn.test/tv/77/4/6/master.m3u8"), result.links.map { it.url })
        } finally { site.shutdown() }
    }

    @Test fun withoutAMatchingImageTheSiteHasNoSuchWork() = runTest {
        val site = site()
        try {
            assertEquals(SourceResult.NotFound, source(site, setOf("/unrelated.jpg")).find(inception))
        } finally { site.shutdown() }
    }

    @Test fun aRefusingApiIsBlockedNotAbsent() = runTest {
        val site = site(apiCode = 403)
        try {
            assertEquals(SourceResult.Unavailable(SourceResult.BLOCKED), source(site, setOf("/aej3.jpg")).find(inception))
        } finally { site.shutdown() }
    }

    @Test fun anUnreachableApiIsANetworkFailure() = runTest {
        val site = site()
        val source = source(site, setOf("/aej3.jpg"))
        site.shutdown()
        assertEquals(SourceResult.Unavailable(SourceResult.NETWORK), source.find(inception))
    }
}
