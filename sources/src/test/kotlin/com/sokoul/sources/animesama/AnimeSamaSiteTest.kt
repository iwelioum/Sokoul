package com.sokoul.sources.animesama

import com.sokoul.sources.CatalogueDns
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anime-Sama's pages read with the Sokoul server's parsing rules and fixtures. */
class AnimeSamaSiteTest {
    private fun fixture(name: String) = AnimeSamaSiteTest::class.java.getResource("/animesama/$name")!!.readText()
    private val page = "https://anime-sama.to/catalogue/one-piece/saison1/vf/"

    @Test fun seasonCardsExcludeCommentedAndTemplateCardsAndNumberOnlyPlainSeasons() {
        val panels = AnimeSamaSite.seasonPanels(fixture("catalogue.html"), "https://anime-sama.to/catalogue/one-piece/")
        assertEquals(
            listOf(
                Triple("Saga 1 East Blue [Episode 1 à 61]", "saison1", 1),
                Triple("Saga 2 Alabasta [Episode 62 à 143]", "saison2", 2),
                Triple("Films", "film", null),
                Triple("One Piece Heroines", "saison2hs", null),
                Triple("Kai - Saga 1 (East Blue)", "kai", null),
            ),
            panels.map { Triple(it.name, it.url.removePrefix("https://anime-sama.to/catalogue/one-piece/"), it.number) },
        )
    }

    @Test fun declaredLanguagesAreReadFromTheSeasonPage() {
        assertEquals(listOf("vostfr", "vf", "vf2"), AnimeSamaSite.availableLanguages(fixture("season-vf.html")))
        assertNull(AnimeSamaSite.availableLanguages("<html></html>"))
    }

    @Test fun aFrenchOnlySeasonKeepsItsLanguageWhenTheCatalogueLinksStraightToTheDub() {
        val panels = AnimeSamaSite.seasonPanels("""panneauAnime("Saison 1", "saison1/vf3");""", "https://anime-sama.to/catalogue/example/")
        assertEquals(1, panels.single().number)
        assertEquals("https://anime-sama.to/catalogue/example/saison1", panels.single().url)
        assertEquals("vf3", panels.single().language)
        assertTrue("vf3" in AnimeSamaSite.seasonLanguages(null, panels.single().language))
    }

    @Test fun referencesKeepEpisodePositionsSkipBlanksAndPutRemovedVideosLast() {
        val episodes = AnimeSamaSite.episodePlayers(fixture("season-vf.html"), page)
        assertEquals(3, episodes.size)
        assertEquals(listOf("272886", "109465"), episodes[0].map { AnimeSamaSite.reference(it)!!.id })
        assertEquals(listOf("109468"), episodes[1].map { AnimeSamaSite.reference(it)!!.id })
        assertEquals(listOf("109471", "272887"), episodes[2].map { AnimeSamaSite.reference(it)!!.id })
        val reference = episodes[1][0].toHttpUrl()
        assertNull("the expiring token is never stored", reference.queryParameter("j"))
        assertEquals("ansembed.net", reference.queryParameter("h"))
        assertEquals(page, AnimeSamaSite.reference(episodes[1][0])!!.page)
    }

    @Test fun legacyPlayerUrlsRemainSupported() {
        val legacy = "var eps1 = ['https://vidmoly.to/embed-a.html', 'https://proxy.liyao.space/------https://video.sibnet.ru/shell.php?videoid=1'];\nvar eps2 = ['https://sendvid.com/embed/x'];"
        assertEquals(
            listOf(listOf("https://vidmoly.to/embed-a.html", "https://sendvid.com/embed/x"), listOf("https://video.sibnet.ru/shell.php?videoid=1")),
            AnimeSamaSite.episodePlayers(legacy, page),
        )
    }

    @Test fun blankEmbedTemplatesDoNotAnnounceDubbedEpisodes() {
        val blank = """var eps1 = ['https://vidmoly.org/embed-.html', 'https://video.sibnet.ru/shell.php?videoid='];"""
        assertTrue(AnimeSamaSite.episodePlayers(blank, page).isEmpty())
    }

    @Test fun referencesOutsideCataloguePagesAreRejected() {
        assertNull(AnimeSamaSite.reference("https://evil.test/api/lien.php?l=1&p=/catalogue/x/saison1/vf/"))
        assertNull(AnimeSamaSite.reference("https://anime-sama.to/api/lien.php?l=1&p=https://evil.test/"))
        assertNull(AnimeSamaSite.reference("https://anime-sama.to/api/lien.php?l=abc&p=/catalogue/x/saison1/vf/"))
    }

    @Test fun secureLookupKeepsPublicAnswersOnlyAndOnlyForTheCatalogue() {
        val answer = CatalogueDns.parse("""{"Status":0,"Answer":[{"type":1,"data":"104.26.13.154","TTL":2},{"type":1,"data":"127.0.0.1","TTL":2}]}""")!!
        assertEquals(listOf("104.26.13.154"), answer.first.map { it.hostAddress })
        assertEquals(60L, answer.second)
        assertNull(CatalogueDns.parse("""{"Status":0,"Answer":[{"type":1,"data":"192.168.0.1","TTL":60}]}"""))
        assertNull(CatalogueDns.parse("""{"Status":3}"""))
        assertTrue(CatalogueDns.CATALOGUE.matches("anime-sama.to"))
        assertTrue(!CatalogueDns.CATALOGUE.matches("anime-sama.to.evil.test") && !CatalogueDns.CATALOGUE.matches("api.themoviedb.org"))
    }

    @Test fun aSeasonCardNamesItsEpisodes() {
        assertEquals(61, AnimeSamaSite.episodesInName("Saga 1 East Blue [Episode 1 à 61]"))
        assertEquals(82, AnimeSamaSite.episodesInName("Saga 2 Alabasta [Episode 62 à 143]"))
        assertEquals(12, AnimeSamaSite.episodesInName("Saison 2 (Épisodes 13 - 24)"))
        assertNull(AnimeSamaSite.episodesInName("Saison 1"))
        assertNull(AnimeSamaSite.episodesInName("Kai - Saga 1 (East Blue)"))
    }

    @Test fun theSiteAddressIsReadFromTheOfficialPageOnlyWhenItListsIt() {
        val page = """<a class="btn-primary" href="https://anime-sama.si">Accéder</a>
            <li>anime-sama.store</li> const domains = [ { name: 'anime-sama.si' }, { name: 'anime-sama.to' }, ];"""
        assertEquals(setOf("anime-sama.si", "anime-sama.to"), AnimeSamaSite.listedHosts(page))
        assertEquals("anime-sama.si", AnimeSamaSite.officialHost(page))
        assertNull(AnimeSamaSite.officialHost("""<a class="btn-primary" href="https://evil.example">x</a>"""))
    }
}
