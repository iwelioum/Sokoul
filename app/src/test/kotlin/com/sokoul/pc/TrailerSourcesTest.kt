package com.sokoul.pc

import com.sokoul.pc.data.TrailerOption
import com.sokoul.pc.data.TrailerParse
import com.sokoul.pc.data.TrailerSource
import com.sokoul.pc.data.TrailerSources
import com.sokoul.pc.data.TrailerStream
import com.sokoul.pc.title.trailerCaption
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trailers from Apple's and AlloCiné's pages, read from small copies of their answers (no network). */
class TrailerSourcesTest {
    private val wikidata = """{"head":{"vars":["apple","applesh"]},"results":{"bindings":[{"apple":{"type":"literal","value":"umc.cmc.363aycnv6vy9qgekvew6fveb9"}}]}}"""

    private val movieUrl = "https://play-edge.itunes.apple.com/WebObjects/MZPlayLocal.woa/hls/playlist.m3u8?cc=FR&a=1725365997&id=787976813&l=fr-FR&aec=HD"
    private val heroUrl = "https://play-edge.itunes.apple.com/WebObjects/MZPlayLocal.woa/hls/subscription/playlist.m3u8?id=1555088263&aec=UHD"
    private val seasonUrl = "https://play-edge.itunes.apple.com/WebObjects/MZPlayLocal.woa/hls/subscription/playlist.m3u8?id=1&aec=UHD"

    /** A film: the hero's link is also the one trailer's, kept in the item's play action. */
    private val moviePage = """<html><script type="application/json" id="serialized-server-data">{"data":[{"intent":{}},{"data":{"shelves":[
        {"id":"canonical-header-umc.cmc.363","items":[{"title":"Dune : Deuxième Partie","videoUrl":{"wide":"$movieUrl","tall":"$movieUrl"}}]},
        {"id":"uts.col.Trailers.umc.cmc.363","items":[{"${'$'}kind":"ExtrasLockup","title":"Dune : Deuxième Partie",
          "artwork":{"template":"https://is1-ssl.mzstatic.com/image/thumb/a/{w}x{h}KF.TVALC02.{f}?color=080808"},
          "playAction":{"contentDescriptor":{"items":[{"playable":{"assets":{"hlsUrl":"$movieUrl"}}}]}}}]},
        {"id":"uts.col.CastAndCrew.umc.cmc.363","items":[]}]}}]}</script></html>"""

    /** A series: the shelf's links are in its playlist, in the order of its items; the hero is a trailer of its own. */
    private val showPage = """<script type="application/json" id="serialized-server-data">{"data":[{},{"data":{"shelves":[
        {"id":"canonical-header","items":[{"title":"Severance","videoUrl":{"wide":"$heroUrl"}}]},
        {"id":"uts.col.Trailers.umc.cmc.1srk","items":[{"title":"Saison 2","playAction":{"${'$'}kind":"playPlaylistAction"}},{"title":"Saison 1"}],
         "playlistItems":[{"playable":{"title":"Saison 2","assets":{"hlsUrl":"$seasonUrl"}}},{"playable":{"assets":{"hlsUrl":"https://evil.example.com/x.m3u8"}}}]}]}}]}</script>"""

    private val master1080 = """#EXTM3U
#EXT-X-VERSION:7
#EXT-X-MEDIA:TYPE=AUDIO,LANGUAGE="fr-FR",GROUP-ID="audio",NAME="Français",DEFAULT=YES,URI="a.m3u8"
#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=185512,RESOLUTION=3840x2160,URI="iframes.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1058x556,AUDIO="audio"
v1.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1920x1010,AUDIO="audio"
v2.m3u8
"""

    @Test fun `wikidata gives the apple id of the film`() {
        val ids = TrailerParse.wikidataAppleIds(wikidata)
        assertEquals("umc.cmc.363aycnv6vy9qgekvew6fveb9", ids?.movie)
        assertNull(ids?.show)
        assertNull(TrailerParse.wikidataAppleIds("""{"results":{"bindings":[]}}"""))
        assertNull(TrailerParse.wikidataAppleIds("""{"results":{"bindings":[{"apple":{"value":"javascript:alert(1)"}}]}}"""))
        assertNull(TrailerParse.wikidataAppleIds("not json"))
    }

    @Test fun `a film's trailer is read once from its apple page`() {
        val found = TrailerParse.appleTrailers(moviePage)
        assertEquals(1, found.size)
        assertEquals(movieUrl, found[0].url)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/a/800x450KF.TVALC02.jpg?color=080808", found[0].image)
    }

    @Test fun `a series keeps its hero then its playlist, and only apple links`() {
        val found = TrailerParse.appleTrailers(showPage)
        assertEquals(listOf(heroUrl, seasonUrl), found.map { it.url })
        assertEquals(listOf("", "Saison 2"), found.map { it.title })
        assertTrue(TrailerParse.appleTrailers("<html>nothing</html>").isEmpty())
    }

    @Test fun `the master playlist tells the sharpest picture and the french sound`() {
        val hls = TrailerParse.hlsMaster(master1080)!!
        assertEquals(1920, hls.width)
        assertEquals(1010, hls.height)
        assertTrue(hls.french)
        // Scope 1920x1010 counts as full HD, as TrailerStream measures it.
        assertEquals(1080, TrailerStream("u", null, "apple", hls.height, width = hls.width).sharpness)
        assertNull(TrailerParse.hlsMaster(master1080.replace("#EXT-X-VERSION:7", "#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"")))
        assertNull(TrailerParse.hlsMaster("<html>"))
        assertFalse(TrailerParse.hlsMaster(master1080.replace("fr-FR", "en"))!!.french)
        // French listed, English played first: that is no French trailer.
        val english = master1080.replace("#EXT-X-VERSION:7", "#EXT-X-VERSION:7\n#EXT-X-MEDIA:TYPE=AUDIO,LANGUAGE=\"en\",GROUP-ID=\"audio\",DEFAULT=YES,URI=\"e.m3u8\"")
            .replace("Français\",DEFAULT=YES", "Français\",DEFAULT=NO")
        assertFalse(TrailerParse.hlsMaster(english)!!.french)
    }

    @Test fun `the windows app keeps the sdr variants only`() {
        val mixed = """#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,LANGUAGE="en",GROUP-ID="audio",DEFAULT=YES,URI="https://a.apple.com/a.m3u8"
#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=1,CODECS="dvh1.05.06",RESOLUTION=3840x1606,VIDEO-RANGE=PQ,URI="https://a.apple.com/if.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=2,CODECS="ac-3,avc1.640028",RESOLUTION=1920x804,VIDEO-RANGE=SDR,AUDIO="audio"
https://a.apple.com/sdr.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3,CODECS="ac-3,hvc1.2.20000000.H150.B0",RESOLUTION=3840x1606,VIDEO-RANGE=PQ,AUDIO="audio"
https://a.apple.com/hdr10.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=4,CODECS="dvh1.05.06,mp4a.40.2",RESOLUTION=3840x1606,AUDIO="audio"
https://a.apple.com/dv.m3u8
"""
        val sdr = TrailerParse.sdrOnly(mixed)!!
        assertTrue("sdr.m3u8" in sdr && "a.m3u8" in sdr)
        assertFalse("hdr10" in sdr || "dv.m3u8" in sdr || "if.m3u8" in sdr)
        assertEquals(1920, TrailerParse.hlsMaster(sdr)!!.width)
        assertEquals(3840, TrailerParse.hlsMaster(mixed)!!.width)
        assertNull(TrailerParse.sdrOnly(master1080))
    }

    @Test fun `allocine's search skips sponsored and other titles`() {
        val json = """{"error":false,"results":[
            {"entity_type":"movie","entity_id":"312529","label":"Billie","original_label":"Disney's Hexed","sponsored":true,"data":{"year":"2026"}},
            {"entity_type":"series","entity_id":"1","label":"Dune : Deuxième Partie","data":{"year":"2024"}},
            {"entity_type":"movie","entity_id":"9","label":"Dune : Deuxième Partie","original_label":"Dune: Part Two","data":{"year":"1990"}},
            {"entity_type":"movie","entity_id":"278742","label":"Dune : Deuxième Partie","original_label":"Dune: Part Two","sponsored":false,"data":{"year":"2024"}}]}"""
        assertEquals("278742", TrailerParse.allocineMatch(json, "movie", listOf("Dune : deuxième partie", "Dune: Part Two"), 2024))
        assertEquals("1", TrailerParse.allocineMatch(json, "series", listOf("Dune - Deuxieme partie"), 2025))
        assertNull(TrailerParse.allocineMatch(json, "movie", listOf("Arrival"), 2016))
    }

    @Test fun `allocine's list keeps trailers and teasers, french first`() {
        val html = """
            <a class="meta-title-link" href="/video/player_gen_cmedia=19604610&amp;cfilm=278742.html" >Dune : Deuxième Partie Bande-annonce VO</a>
            <a class="meta-title-link" href="/video/player_gen_cmedia=19604611&amp;cfilm=278742.html" >Dune : Deuxième Partie Bande-annonce (2) VF</a>
            <a class="meta-title-link" href="/video/player_gen_cmedia=19605416&amp;cfilm=278742.html" >Dune : Deuxième Partie BONUS VF &quot;Sortie de salles Dolby&quot;</a>
            <a class="meta-title-link" href="/video/player_gen_cmedia=20613332&amp;cserie=26062.html" >Severance - saison 2 Teaser VO STFR</a>
            <a class="meta-title-link" href="/video/player_gen_cmedia=19595722&amp;cserie=26062.html" >Severance Saison 1 Episode 1 EXTRAIT VO</a>"""
        val kept = TrailerParse.allocineKeep(TrailerParse.allocineVideos(html))
        assertEquals(listOf("19604611", "19604610", "20613332"), kept.map { it.mediaId })
        assertEquals(listOf("film", "film", "serie"), kept.map { it.type })
        assertEquals(listOf("VF", "VO", "VOST"), kept.map { TrailerParse.allocineLanguage(it.text) })
        assertEquals(listOf("Bande-annonce (2)", "Bande-annonce", "Saison 2 · Teaser"), kept.map { TrailerParse.allocineName(it.text) })
    }

    @Test fun `allocine's video page gives its mp4 and picture`() {
        val html = """<script type="application/ld+json">{"@type":"VideoObject","thumbnailUrl": "https://fr.web.img5.acsta.net/videothumbnails/23/12/12/18/06/4947617.jpg",
            "thumbnail": {"@type": "ImageObject","contentUrl": "https://fr.web.img5.acsta.net/videothumbnails/4947617.jpg"},
            "contentUrl": "https:\/\/fr.vid.web.acsta.net\/nmedia\/33\/23\/12\/12\/18\/19604611_hd_013.mp4?source=www&platform=graphql"}</script>"""
        val (mp4, thumb) = TrailerParse.allocineVideo(html)!!
        assertEquals("https://fr.vid.web.acsta.net/nmedia/33/23/12/12/18/19604611_hd_013.mp4?source=www&platform=graphql", mp4)
        assertEquals("https://fr.web.img5.acsta.net/videothumbnails/23/12/12/18/06/4947617.jpg", thumb)
        assertNull(TrailerParse.allocineVideo("""{"contentUrl": "http://x.test/a.mp4"}"""))
    }

    @Test fun `title keys stand for an imdb id and nothing else`() {
        assertEquals("movie" to "tt15239678", TrailerSources.parseTitleKey(TrailerSources.titleKey("movie", "tt15239678")))
        assertNull(TrailerSources.parseTitleKey("dQw4w9WgXcQ"))
        assertNull(TrailerSources.parseTitleKey("title:movie:../etc"))
    }

    @Test fun `captions say the measured quality and the language`() {
        assertEquals("1080p · VF", trailerCaption(TrailerOption("", TrailerSource.APPLE, "VF", "u", height = 800, width = 1920)))
        assertEquals("4K · VO", trailerCaption(TrailerOption("", TrailerSource.APPLE, "VO", "u", height = 2160, width = 3840)))
        assertEquals("720p · VF", trailerCaption(TrailerOption("", TrailerSource.ALLOCINE, "VF", "u", height = 720, width = 1280)))
        assertEquals("VO", trailerCaption(TrailerOption("x", TrailerSource.YOUTUBE, "VO", youtubeKey = "k")))
    }

    /** Answers by host, as the real sites would; anything else fails. */
    private fun fakeHttp(answers: (String) -> String?) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val body = answers(request.url.toString())
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (body == null) 404 else 200).message("")
            .body((body ?: "").toResponseBody("text/plain".toMediaType())).build()
    }.build()

    @Test fun `a full hd apple trailer plays without asking youtube`() = runBlocking {
        val asked = mutableListOf<String>()
        val sources = TrailerSources(fakeHttp { url ->
            asked += url
            when {
                url.startsWith("https://query.wikidata.org/") -> wikidata
                url.startsWith("https://tv.apple.com/fr/movie/x/umc.cmc.363aycnv6vy9qgekvew6fveb9") -> moviePage
                url == movieUrl -> master1080
                else -> null
            }
        }, storefront = { "fr" })
        var youtube = 0
        val key = TrailerSources.titleKey("movie", "tt15239678")
        val stream = sources.resolve(listOf(key, "softvideo1")) { youtube++; TrailerStream("https://yt.test/x", null, "yt-dlp", 1080) }
        assertEquals(movieUrl, stream?.videoUrl)
        assertNull(stream?.audioUrl)
        assertEquals(key, stream?.key)
        assertEquals(0, youtube)
        // Kept: a second request opens nothing.
        val before = asked.size
        sources.resolve(listOf(key)) { null }
        assertEquals(before, asked.size)
    }

    @Test fun `without apple the youtube trailer is resolved as before`() = runBlocking {
        val sources = TrailerSources(fakeHttp { null }, storefront = { "fr" })
        val yt = TrailerStream("https://yt.test/x", "https://yt.test/a", "yt-dlp", 1080, key = "softvideo1")
        assertEquals(yt, sources.resolve(listOf(TrailerSources.titleKey("tv", "tt11280740"), "softvideo1")) { keys -> yt.takeIf { keys == listOf("softvideo1") } })
        assertEquals(yt, sources.resolve(listOf("softvideo1")) { yt })
    }
}
