package com.sokoul.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WebLiveTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `playlist credentials stay on the server and a second channel revokes the first`() = testApplication {
        val provider = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl?.encodedPath) {
                        "/playlist" -> "#EXTM3U\n#EXTINF:-1 group-title=\"Sports\",Arena\n/live/username/password/1.m3u8\n"
                        "/live/username/password/1.m3u8" -> "#EXTM3U\n#EXTINF:3,\nsegment.ts?secret=password\n#EXT-X-ENDLIST\n"
                        "/live/username/password/segment.ts" -> "segment"
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setBody(body)
                }
            }
            start()
        }
        provider.use {
            val server = TestServer(tmp).also { it.install(this) }
            val token = client.setupOwner()
            val userId = client.getWith("/api/me", token).json().str("id")
            assertEquals(HttpStatusCode.Conflict, client.getWith("/api/live/channels", token).status)
            server.services.vault.put(userId, "iptv", provider.url("/playlist?secret=password").toString())
            val channels = client.getWith("/api/live/channels?sports=true", token)
            assertFalse(channels.bodyAsText().contains("password"))
            val channel = channels.json().getValue("items").jsonArray.single().jsonObject
            assertEquals("Arena", channel.str("name"))
            val guide = client.postJson("/api/live/guide", """{"ids":["${channel.str("id")}","unknown"]}""", token)
            val guideRows = Json.parseToJsonElement(guide.bodyAsText()).jsonArray
            assertEquals("Arena", guideRows.single().jsonObject.getValue("channel").jsonObject.str("name"))
            val payload = """{"channelId":"${channel.str("id")}"}"""
            val first = client.postJson("/api/live/start", payload, token).json().str("url")
            val segment = client.get(first).bodyAsText().lines().first { it.startsWith("/api/stream/") }
            assertTrue(segment.startsWith(first.substringBeforeLast('/') + "/u/"))
            val encoded = segment.split('/')[5]
            assertFalse(Secrets.fromB64url(encoded)!!.decodeToString().contains("password"))
            assertEquals("segment", client.get(segment).bodyAsText())

            val next = client.postJson("/api/live/start", payload, token).json().str("url")
            assertEquals(HttpStatusCode.NotFound, client.get(first).status)
            assertEquals(HttpStatusCode.OK, client.get(next).status)
        }
    }

    @Test fun `guides and shared collection index are available to signed in accounts`() = testApplication {
        TestServer(tmp).install(this)
        val token = client.setupOwner()
        val guides = client.getWith("/api/service-guides", token)
        val items = Json.parseToJsonElement(guides.bodyAsText()).jsonArray
        val guide = items.first { it.jsonObject.str("id") == "tmdb" }.jsonObject
        assertTrue(guide.getValue("steps").jsonArray.isNotEmpty())
        assertEquals("true", guide.str("editable"))
        val first = client.getWith("/api/catalog/collections", token).json()
        assertEquals("star-wars", first.getValue("items").jsonArray.first().jsonObject.str("id"))
        assertEquals("true", first.str("more"))
        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/catalog/collections?page=0", token).status)
    }

    @Test fun `Xtream VOD lists provider movies and only opens items in the account catalogue`() = testApplication {
        MockWebServer().use { provider ->
            provider.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl ?: return MockResponse().setResponseCode(404)
                    val body = when {
                        url.encodedPath == "/movie/user/secret/20.mp4" -> "mp4-content"
                        url.queryParameter("action") == "get_vod_categories" -> """[{"category_id":"3","category_name":"Cinema"}]"""
                        url.queryParameter("action") == "get_vod_streams" ->
                            """[{"stream_id":20,"name":"Example film","category_id":"3","container_extension":"mp4"}]"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setBody(body)
                }
            }
            provider.start()
            val server = TestServer(tmp).also { it.install(this) }
            val token = client.setupOwner()
            val userId = client.getWith("/api/me", token).json().str("id")
            val value = com.sokoul.credentials.ServiceCheck.xtreamValue(provider.url("/").toString().trimEnd('/'), "user", "secret")
            server.services.vault.put(userId, "iptv", value)
            val response = client.getWith("/api/live/vod/movies?group=Cinema", token)
            val film = response.json().getValue("items").jsonArray.single().jsonObject
            assertEquals("Example film", film.str("title"))
            assertFalse(response.bodyAsText().contains("secret"))
            assertEquals(HttpStatusCode.NotFound, client.postJson("/api/live/vod/movies/start", """{"id":21}""", token).status)
            val started = client.postJson("/api/live/vod/movies/start", """{"id":20}""", token).json().str("url")
            assertTrue(started.startsWith("/api/stream/"))
            assertEquals("mp4-content", client.get(started).bodyAsText())
        }
    }

    @Test fun `Xtream catch up accepts only a programme in the channel archive`() = testApplication {
        val ended = 1_799_999_000L
        val aired = ended - 1_800
        val current = 1_800_000_000L
        MockWebServer().use { provider ->
            provider.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl ?: return MockResponse().setResponseCode(404)
                    val body = when {
                        url.encodedPath.startsWith("/timeshift/user/secret/30/") -> "replay-content"
                        url.queryParameter("action") == null -> """{"user_info":{"auth":1,"status":"Active"},"server_info":{"timezone":"UTC"}}"""
                        url.queryParameter("action") == "get_live_categories" -> """[{"category_id":"1","category_name":"General"}]"""
                        url.queryParameter("action") == "get_live_streams" ->
                            """[{"stream_id":10,"name":"Channel 10","category_id":"1","tv_archive":1,"tv_archive_duration":2}]"""
                        url.queryParameter("action") == "get_simple_data_table" ->
                            """{"epg_listings":[{"start_timestamp":"$aired","stop_timestamp":"$ended","title":"TmV3cyBCdWxsZXRpbg=="},
                                {"start_timestamp":"$current","stop_timestamp":"${current + 900}","title":"Current programme"}]}"""
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setBody(body)
                }
            }
            provider.start()
            val server = TestServer(tmp).also { it.install(this) }
            val token = client.setupOwner()
            val userId = client.getWith("/api/me", token).json().str("id")
            val value = com.sokoul.credentials.ServiceCheck.xtreamValue(provider.url("/").toString().trimEnd('/'), "user", "secret")
            server.services.vault.put(userId, "iptv", value)
            val response = client.getWith("/api/live/replay/10", token)
            val replay = Json.parseToJsonElement(response.bodyAsText()).jsonArray.single().jsonObject
            assertEquals("News Bulletin", replay.str("title"))
            assertEquals((aired * 1_000).toString(), replay.str("start"))
            val guide = client.getWith("/api/live/guide/10", token)
            val program = Json.parseToJsonElement(guide.bodyAsText()).jsonArray.single().jsonObject
            assertEquals((current * 1_000).toString(), program.str("start"))
            val expired = client.postJson("/api/live/replay/start", """{"channelId":"10","start":1000}""", token)
            assertEquals(HttpStatusCode.NotFound, expired.status)
            val relayed = client.postJson("/api/live/replay/start", """{"channelId":"10","start":${aired * 1_000}}""", token)
            assertEquals("replay-content", client.get(relayed.json().str("url")).bodyAsText())
        }
    }
}
