package com.sokoul.pc

import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.data.CineLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rows of links through people on a title page. */
class CineLinksTest {
    private fun film(id: Int, votes: Int = 100) = TmdbItem(id = id, title = "F$id", originalTitle = null, year = null, poster = null, backdrop = null, rating = null, overview = null, voteCount = votes, mediaType = "movie")

    @Test
    fun each_title_lands_in_its_strongest_row_and_never_links_to_itself() {
        val links = CineLinks.build(
            kind = "movie", id = 1, director = "Lana",
            directed = listOf(film(1), film(2), film(3)),
            cast = listOf(
                "Keanu" to listOf(film(1), film(2), film(4), film(5)),
                "Carrie" to listOf(film(1), film(4), film(6)),
            ),
        )
        assertEquals(listOf(2), links.sameTeam.map { it.item.id })
        assertEquals(listOf("Lana", "Keanu"), links.sameTeam.single().people)
        assertEquals(listOf(4), links.sharedCast.map { it.item.id })
        assertEquals(listOf(3), links.byDirector.map { it.id })
        val all = links.sameTeam.map { it.item.id } + links.sharedCast.map { it.item.id } + links.byDirector.map { it.id }
        assertTrue(1 !in all)
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun one_shared_actor_is_not_enough_for_the_cast_row() {
        val links = CineLinks.build("movie", 1, null, emptyList(), listOf("A" to listOf(film(7)), "B" to listOf(film(8))))
        assertTrue(links.isEmpty)
    }

    @Test
    fun more_shared_people_come_first() {
        val links = CineLinks.build(
            "movie", 1, null, emptyList(),
            listOf("A" to listOf(film(10, 5), film(11, 999)), "B" to listOf(film(10, 5), film(11, 999)), "C" to listOf(film(10, 5))),
        )
        assertEquals(listOf(10, 11), links.sharedCast.map { it.item.id })
    }

    @Test
    fun making_ofs_do_not_link() {
        val doc = TmdbItem(id = 20, title = "Making of", originalTitle = null, year = null, poster = null, backdrop = null, rating = null, overview = null, genreIds = listOf(99), voteCount = 50, mediaType = "movie")
        val links = CineLinks.build("movie", 1, null, emptyList(), listOf("A" to listOf(doc), "B" to listOf(doc)))
        assertTrue(links.isEmpty)
    }
}
