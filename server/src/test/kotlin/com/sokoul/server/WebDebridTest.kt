package com.sokoul.server

import com.sokoul.iptv.data.net.TmdbClient
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDebridTest {
    @Test fun `a ready season pack offers only the requested selected episode`() = runBlocking {
        MockWebServer().use { rd ->
            rd.enqueue(MockResponse().setBody("""[{"id":"PACK1","filename":"Example.Show.S01","status":"downloaded"}]"""))
            rd.enqueue(MockResponse().setBody("""
                {"id":"PACK1","filename":"Example.Show.S01","status":"downloaded","files":[
                    {"id":1,"path":"/Example.Show.S01E01.VF.1080p.mkv","selected":1},
                    {"id":2,"path":"/Example.Show.S01E02.VF.1080p.mkv","selected":1}
                ],"links":["https://real-debrid.com/d/episode1","https://real-debrid.com/d/episode2"]}
            """.trimIndent()))
            rd.enqueue(MockResponse().setBody("""{"download":"https://cdn.real-debrid.com/movie.mkv"}"""))
            val account = WebDebrid(OkHttpClient(), { "test-token" }, rd.url("/").toString().trimEnd('/'))
            val work = WorkNames(TmdbClient.NamesAndImages("Example Show", null, 2020, emptySet()), false)
            val source = checkNotNull(account.source("owner", work))
            val found = source.find(SourceQuery("tv", 42, 1, 2)) as SourceResult.Found
            val selected = found.links.single()
            assertEquals("https://real-debrid.com/d/episode2", selected.url)
            assertEquals("1080p", selected.quality)
            assertEquals("https://cdn.real-debrid.com/movie.mkv", account.resolve("owner", selected.url).url)
            assertEquals("/torrents?limit=100", rd.takeRequest().path)
            assertEquals("/torrents/info/PACK1", rd.takeRequest().path)
            assertEquals("/unrestrict/link", rd.takeRequest().path)
        }
    }
}
