package com.sokoul.pc

import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.pc.data.L
import com.sokoul.pc.setup.ServiceGuides
import com.sokoul.pc.startup.onboarding.OnboardingModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceGuidesTest {
    /** Every id the app stores or tests: Settings keys, expert keys, the IPTV source and the anime server. */
    private val storedIds = ProviderConfig.providers.map { it.first } +
        OnboardingModel.ADVANCED_KEYS.map { it.id } +
        listOf("iptv", "animeBackend")

    private fun texts(): List<L> = ServiceGuides.all.flatMap { listOfNotNull(it.adds, it.cost, it.linkLabel, it.privacy) + it.steps }

    /** Words quoted from a service's own site («…» or “…”) may keep their jargon; the rest must not. */
    private fun unquoted(s: String) = s.replace(Regex("«[^»]*»"), "").replace(Regex("“[^”]*”"), "")

    @Test fun everyStoredIdHasAGuide() {
        storedIds.distinct().forEach { assertNotNull("no guide for $it", ServiceGuides.of(it)) }
    }

    @Test fun idsAreUniqueAndMostNeededComeFirst() {
        val ids = ServiceGuides.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("iptv", "realdebrid", "tmdb", "opensubtitles", "subdl"), ids.take(5))
    }

    @Test fun linksAreHttps() {
        ServiceGuides.all.forEach { g -> g.link?.let { assertTrue(it, it.startsWith("https://")) } }
    }

    @Test fun everyTextHasFrenchAndEnglish() {
        texts().forEach {
            assertTrue("empty English: $it", it.en.isNotBlank())
            assertTrue("empty French: $it", it.fr.isNotBlank())
            assertFalse("untranslated: $it", it.en == it.fr)
        }
    }

    @Test fun stepsAreShort() {
        ServiceGuides.all.forEach { g ->
            val range = if (g.id == "tvmaze") 1..1 else 3..6 // TVmaze needs no key: one step says so
            assertTrue("${g.id}: ${g.steps.size} steps", g.steps.size in range)
        }
    }

    @Test fun noBareJargon() {
        texts().forEach { l ->
            listOf(l.en, l.fr).map(::unquoted).forEach { s ->
                assertFalse("bare API in: $s", Regex("\\bAPI\\b").containsMatchIn(s))
                assertFalse("bare Xtream in: $s", Regex("\\bXtream\\b(?! Codes)").containsMatchIn(s))
            }
        }
    }

    @Test fun everyGuideSaysWhenItWasChecked() {
        ServiceGuides.all.forEach { assertTrue(it.id, Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it.verified)) }
    }

    @Test fun tmdbFormatCatchesTheLongTokenPastedByMistake() {
        val format = ServiceGuides.of("tmdb")!!.keyFormat!!
        assertTrue(format.matches("0123456789abcdef0123456789abcdef"))
        assertFalse(format.matches("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIwMTIzIn0.abc"))
        assertFalse(format.matches("0123456789abcdef0123456789abcde")) // one character missing
    }

    @Test fun serverAddressBelongsToTheSokoulServerGuide() {
        assertEquals("mainapi", ServiceGuides.of("mainapi_url")?.id)
    }
}
