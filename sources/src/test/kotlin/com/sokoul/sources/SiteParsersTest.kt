package com.sokoul.sources

import com.sokoul.sources.coflix.CoflixSite
import com.sokoul.sources.fstream.FStreamSite
import com.sokoul.sources.voirdrama.VoirDramaSite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pages of Coflix, French-Stream and VoirDrama read as the Sokoul server reads them (shapes seen on 2026-11-18). */
class SiteParsersTest {
    @Test fun coflixSearchCardsServersAndHosts() {
        val search = """<div class="md-manga-card"><a href="https://coflix.esq/film/inception/"><img data-src="//image.tmdb.org/t/p/w342/aej3.jpg"></a>
            <div class="md-manga-card-name">Inception</div><span class="md-card-badge year">2010</span></div>
            <div class="md-manga-card"><a href="https://coflix.esq/collection/nolan/">x</a><div class="md-manga-card-name">Nolan</div></div>"""
        val cards = CoflixSite.cards(search, "https://coflix.esq")
        assertEquals(listOf(CoflixSite.Card("Inception", "https://coflix.esq/film/inception/", 2010, "/aej3.jpg", "movies")), cards)
        assertEquals(listOf("https://coflix.esq/film/inception/"), CoflixSite.matches(cards, "Inception", "movie", 2010).map { it.first.url })
        val page = """<script>var cfPlayerToken = "tok.en"; var cfServers = [{"nombre":"Lecteurvideo","embed_url":"https://lecteurvideo.com/embed.php?id=8689","idioma":"Autre"}];</script>"""
        assertEquals(listOf("https://lecteurvideo.com/embed.php?id=8689&t=tok.en"), CoflixSite.embeds(page).map { it.url })
        val uqload = java.util.Base64.getEncoder().encodeToString("https://uqload.cx/embed-bat0b9dlv6rx.html".toByteArray())
        val embed = """<ul><li onclick="showVideo('$uqload')"><span>HD</span><p>Uqload Vidéo 8 / French</p></li></ul>"""
        assertEquals(listOf(CoflixSite.Player("https://uqload.cx/embed-bat0b9dlv6rx.html", "HD", "French")), CoflixSite.players(embed))
        assertEquals(1.0, CoflixSite.similarity("Inception", "inception"), 0.0)
        assertTrue(CoflixSite.similarity("Breaking Bad", "Les Bad Guys cassent la baraque") < 0.8)
    }

    @Test fun frenchStreamRowsFilmAndEpisodePlayers() {
        val search = """<div class='search-item' onclick="location.href='/1022-inception-film-streaming-complet-vf.html'"><div class='search-poster'><img src='https://image.tmdb.org/t/p/w400/aej3.jpg'></div><div class='search-info'><div class='search-title'>Inception (2010)</div></div></div>"""
        val row = FStreamSite.candidates(search, "https://french-stream.net").single()
        assertEquals("https://french-stream.net/1022-inception-film-streaming-complet-vf.html", row.link)
        assertEquals(2010, row.year)
        assertEquals("/aej3.jpg", row.poster)
        assertEquals("1022", FStreamSite.pageId(row.link))
        assertEquals("Breaking Bad", FStreamSite.baseTitle("Breaking Bad - Saison 1"))
        val film = Json.parseToJsonElement("""{"players":{"uqload":{"vff":"https://uqload.vc/embed-a.html","vfq":"https://uqload.vc/embed-b.html"},"netu":{"vff":"BafWadqiVSI2"}}}""") as JsonObject
        assertEquals(mapOf("VFF" to listOf("uqload" to "https://uqload.vc/embed-a.html"), "VFQ" to listOf("uqload" to "https://uqload.vc/embed-b.html")), FStreamSite.filmPlayers(film))
        val episodes = Json.parseToJsonElement("""{"vf":{"1":{"uqload":"https://uqload.vc/embed-e1.html"},"2":{"uqload":"https://uqload.vc/embed-e2.html"}},"vostfr":{"2":{"voe":"https://voe.sx/e/x"}},"vo":[]}""") as JsonObject
        assertEquals(mapOf("VF" to listOf("uqload" to "https://uqload.vc/embed-e2.html"), "VOSTFR" to listOf("voe" to "https://voe.sx/e/x")), FStreamSite.episodePlayers(episodes, 2))
    }

    @Test fun voirDramaDatesResultsAndSources() {
        assertEquals("2021-09-17", VoirDramaSite.date("Sep 17, 2021"))
        val raw = """xx___ASPSTART_HTML___<div class="item"><h3><a class="asp_res_url" href="https://voirdrama.to/drama/squid-game/">Squid Game</a></h3><div class="summary-content">Sep 17, 2021</div></div>___ASPEND_HTML___yy"""
        assertEquals(listOf("https://voirdrama.to/drama/squid-game/" to "Sep 17, 2021"), VoirDramaSite.results(raw))
        val page = """<script>var thisChapterSources = {"LECTEUR 1 VOE":"<iframe src=\"https://voe.sx/e/abc\"></iframe>","UQLOAD":"https://uqload.cx/embed-x.html"};</script>"""
        assertEquals(listOf("Voe" to "https://voe.sx/e/abc", "Uqload" to "https://uqload.cx/embed-x.html"), VoirDramaSite.sources(page))
    }
}
