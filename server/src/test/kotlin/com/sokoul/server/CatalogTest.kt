package com.sokoul.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList

/** web-client 1.2: TMDB through the server with the caller's own key, FR/EN, cached, plus "Continue watching". */
class CatalogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key = "0123456789abcdef0123456789abcdef"
    private val tmdb = MockWebServer()
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile private var refuse = false

    private fun list(kind: String, id: Int, title: String) =
        """{"results":[{"id":$id,${if (kind == "movie") "\"title\":\"$title\",\"release_date\":\"1999-03-31\"" else "\"name\":\"$title\",\"first_air_date\":\"2011-04-17\""},"poster_path":"/p$id.jpg","backdrop_path":"/b$id.jpg"}]}"""

    @Before fun start() {
        tmdb.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                if (refuse) return MockResponse().setResponseCode(401).setBody("""{"status_code":7}""")
                val path = request.requestUrl!!.encodedPath
                val body = when (path) {
                    "/3/trending/all/week" -> """{"results":[{"media_type":"movie","id":603,"title":"Matrix","release_date":"1999-03-31","poster_path":"/m.jpg"},{"media_type":"person","id":1,"name":"Keanu"},{"media_type":"tv","id":1399,"name":"Game of Thrones","first_air_date":"2011-04-17"}]}"""
                    "/3/movie/popular" -> list("movie", 27205, "Inception")
                    "/3/tv/popular" -> list("tv", 1396, "Breaking Bad")
                    "/3/movie/top_rated" -> list("movie", 238, "Le Parrain")
                    "/3/movie/603" -> """{"id":603,"title":"Matrix","original_title":"The Matrix","overview":"Neo.","release_date":"1999-03-31","runtime":136,
                        "vote_average":8.2,"genres":[{"id":28,"name":"Action"}],"poster_path":"/p.jpg","backdrop_path":"/b.jpg",
                        "images":{"logos":[{"iso_639_1":"en","file_path":"/logo-en.png"},{"iso_639_1":"fr","file_path":"/logo-fr.png"}]},
                        "credits":{"cast":[${(1..12).joinToString(",") { """{"name":"Actor $it","character":"Role $it","profile_path":"/a$it.jpg"}""" }}]}}"""
                    "/3/tv/1399" -> """{"id":1399,"name":"Game of Thrones","first_air_date":"2011-04-17","episode_run_time":[60],
                        "seasons":[{"season_number":0,"name":"Specials","episode_count":3},{"season_number":2,"name":"Saison 2","episode_count":10},{"season_number":1,"name":"Saison 1","episode_count":10,"poster_path":"/s1.jpg"}]}"""
                    "/3/tv/1399/season/1" -> """{"name":"Saison 1","episodes":[{"episode_number":1,"name":"L'hiver vient","still_path":"/e1.jpg","runtime":62,"air_date":"2011-04-17"},{"episode_number":2,"name":"La route royale"}]}"""
                    "/3/search/multi" -> """{"results":[{"media_type":"tv","id":1399,"name":"Game of Thrones"},{"media_type":"person","id":5,"name":"X"}]}"""
                    else -> return MockResponse().setResponseCode(404).setBody("{}")
                }
                return MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body)
            }
        }
        tmdb.start()
    }

    @After fun stop() = tmdb.shutdown()

    private fun server() = TestServer(tmp, tmdbBaseUrl = tmdb.url("/").toString())

    private suspend fun io.ktor.client.HttpClient.owner(s: TestServer, withKey: Boolean = true): Pair<String, String> {
        val token = setupOwner()
        val userId = getWith("/api/me", token).json().str("id")
        if (withKey) s.services.vault.put(userId, "tmdb", key)
        return token to userId
    }

    @Test fun `without a TMDB key every catalogue route asks for one`() = testApplication {
        val s = server().also { it.install(this) }
        val (token, _) = client.owner(s, withKey = false)
        for (path in listOf("/api/catalog/home", "/api/catalog/title/movie/603", "/api/catalog/season/1399/1", "/api/catalog/search?q=x")) {
            val r = client.getWith(path, token)
            assertEquals(path, HttpStatusCode.Conflict, r.status)
            assertEquals("""{"error":"needs_key","service":"tmdb"}""", r.bodyAsText())
        }
        assertEquals(0, seen.size)
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/catalog/home", null).status)
    }

    @Test fun `home rows come in French by default and in English when asked, cached per language`() = testApplication {
        val s = server().also { it.install(this) }
        val (token, _) = client.owner(s)
        val fr = client.getWith("/api/catalog/home", token)
        assertEquals(HttpStatusCode.OK, fr.status)
        val rows = fr.json()["rows"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("trending", "popular_movies", "popular_tv", "top_rated"), rows.map { it.str("id") })
        assertEquals("Tendances", rows[0].str("title"))
        // People are not titles.
        val trending = rows[0]["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("movie:603", "tv:1399"), trending.map { "${it.str("kind")}:${it.str("id")}" })
        assertEquals("https://image.tmdb.org/t/p/w342/m.jpg", trending[0].str("poster"))
        assertEquals("1999", trending[0].str("year"))
        assertEquals(4, seen.size)
        assertTrue(seen.all { it.requestUrl!!.queryParameter("language") == "fr-FR" && it.requestUrl!!.queryParameter("api_key") == key })
        assertEquals("FR", seen.first { it.requestUrl!!.encodedPath == "/3/movie/popular" }.requestUrl!!.queryParameter("region"))
        // The key never comes back to the client.
        assertFalse(fr.bodyAsText().contains(key))

        client.getWith("/api/catalog/home", token)
        assertEquals("cached", 4, seen.size)

        val en = client.get("/api/catalog/home") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.AcceptLanguage, "en-GB,en;q=0.9")
        }
        assertEquals("Trending", en.json()["rows"]!!.jsonArray[0].jsonObject.str("title"))
        assertEquals(8, seen.size)
        assertTrue(seen.drop(4).all { it.requestUrl!!.queryParameter("language") == "en-US" })

        // A few hours later the cache is refreshed.
        s.advance(4 * TestServer.HOUR)
        client.getWith("/api/catalog/home", token)
        assertEquals(12, seen.size)
    }

    @Test fun `continue watching comes from the profile's synced resume points`() = testApplication {
        val s = server().also { it.install(this) }
        val (token, userId) = client.owner(s)
        fun resume(profile: String, key: String, at: Long, kind: String, id: Int, finished: Boolean = false, season: Int? = null, episode: Int? = null) =
            SyncChange("resume", profile, key, at, kind = kind, tmdbId = id, season = season, episode = episode, positionMs = 600_000,
                durationMs = 3_600_000, finished = finished, title = "T$id", subtitle = "S1E2", image = "/still$id.jpg")
        s.services.sync.push(userId, listOf(
            resume("P1", "movie:603", 1_000, "movie", 603),
            resume("P1", "tv:1399:1:2", 2_000, "tv", 1399, season = 1, episode = 2),
            resume("P1", "tv:1399:1:1", 500, "tv", 1399, season = 1, episode = 1),
            resume("P1", "movie:238", 3_000, "movie", 238, finished = true),
            resume("P2", "movie:27205", 4_000, "movie", 27205),
        ))
        val rows = client.getWith("/api/catalog/home?profile=P1", token).json()["rows"]!!.jsonArray.map { it.jsonObject }
        assertEquals("continue", rows[0].str("id"))
        assertEquals("Reprendre", rows[0].str("title"))
        val items = rows[0]["items"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("tv:1399", "movie:603"), items.map { "${it.str("kind")}:${it.str("id")}" })
        val resume = items[0]["resume"]!!.jsonObject
        assertEquals("2", resume.str("episode"))
        assertEquals("600000", resume.str("positionMs"))
        assertEquals("https://image.tmdb.org/t/p/w780/still1399.jpg", items[0].str("backdrop"))
        // Another profile, and an unknown one, see nothing of P1.
        assertEquals("trending", client.getWith("/api/catalog/home?profile=P3", token).json()["rows"]!!.jsonArray[0].jsonObject.str("id"))
        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/catalog/home?profile=a%20b", token).status)
    }

    @Test fun `title, season and search answer their shapes`() = testApplication {
        val s = server().also { it.install(this) }
        val (token, _) = client.owner(s)
        val movie = client.getWith("/api/catalog/title/movie/603", token).json()
        assertEquals("Matrix", movie.str("title"))
        assertEquals("The Matrix", movie.str("originalTitle"))
        assertEquals("136", movie.str("runtime"))
        assertEquals("[\"Action\"]", movie["genres"].toString())
        assertEquals("https://image.tmdb.org/t/p/w500/logo-fr.png", movie.str("logo"))
        assertEquals(10, movie["cast"]!!.jsonArray.size)
        assertEquals("Role 1", movie["cast"]!!.jsonArray[0].jsonObject.str("character"))
        assertNull(movie["seasons"])
        assertEquals("credits,images", seen.last().requestUrl!!.queryParameter("append_to_response"))

        val tv = client.getWith("/api/catalog/title/tv/1399", token).json()
        assertEquals("60", tv.str("runtime"))
        assertEquals(listOf(1, 2, 0), tv["seasons"]!!.jsonArray.map { it.jsonObject["number"]!!.jsonPrimitive.content.toInt() })

        val season = client.getWith("/api/catalog/season/1399/1", token).json()
        assertEquals(2, season["episodes"]!!.jsonArray.size)
        assertEquals("https://image.tmdb.org/t/p/w300/e1.jpg", season["episodes"]!!.jsonArray[0].jsonObject.str("still"))

        val found = client.getWith("/api/catalog/search?q=game%20of", token).json()["items"]!!.jsonArray
        assertEquals(1, found.size)
        assertEquals("game of", seen.last().requestUrl!!.queryParameter("query"))
        assertEquals("[]", client.getWith("/api/catalog/search?q=", token).json()["items"].toString())

        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/catalog/title/person/1", token).status)
        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/catalog/title/movie/abc", token).status)
        assertEquals(HttpStatusCode.NotFound, client.getWith("/api/catalog/title/movie/999", token).status)
    }

    @Test fun `a key TMDB refuses asks for a new one`() = testApplication {
        val s = server().also { it.install(this) }
        val (token, _) = client.owner(s)
        refuse = true
        val r = client.getWith("/api/catalog/search?q=x", token)
        assertEquals(HttpStatusCode.Conflict, r.status)
        assertEquals("""{"error":"needs_key","service":"tmdb","reason":"refused"}""", r.bodyAsText())
    }

    @Test fun `each user uses their own key`() = testApplication {
        val s = server().also { it.install(this) }
        val (owner, ownerId) = client.owner(s)
        val invite = client.postJson("/api/invites", token = owner).json().str("code")
        val member = client.postJson("/api/auth/join", """{"code":"$invite","name":"Bo","passphrase":"another long phrase"}""").json().str("token")
        val memberId = client.getWith("/api/me", member).json().str("id")
        assertEquals(HttpStatusCode.Conflict, client.getWith("/api/catalog/search?q=x", member).status)
        runBlocking { s.services.vault.put(memberId, "tmdb", "ffffffffffffffffffffffffffffffff") }
        client.getWith("/api/catalog/search?q=x", member)
        assertEquals("ffffffffffffffffffffffffffffffff", seen.last().requestUrl!!.queryParameter("api_key"))
        // Same request, other key: not served from the other user's cache.
        client.getWith("/api/catalog/search?q=x", owner)
        assertEquals(key, seen.last().requestUrl!!.queryParameter("api_key"))
        assertTrue(ownerId != memberId)
    }

    @Test fun `accept-language picks French or English`() {
        assertEquals("fr-FR", TmdbGateway.languageOf(null))
        assertEquals("fr-FR", TmdbGateway.languageOf("fr-CA,fr;q=0.9"))
        assertEquals("en-US", TmdbGateway.languageOf("en-GB"))
        assertEquals("en-US", TmdbGateway.languageOf("de-DE,en;q=0.5"))
        assertEquals("fr-FR", TmdbGateway.languageOf("de-DE"))
        assertNull(TmdbGateway.image("https://evil/x.jpg", "w342"))
        assertNull(TmdbGateway.image("/../x.jpg", "w342"))
        Json.parseToJsonElement("{}")
    }
}
