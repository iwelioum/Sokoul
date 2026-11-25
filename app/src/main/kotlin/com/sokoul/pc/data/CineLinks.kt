package com.sokoul.pc.data

import com.sokoul.iptv.data.net.TmdbItem

/** A title linked to the one on screen, and who links them. */
data class LinkedTitle(val item: TmdbItem, val people: List<String>)

/**
 * What ties a title to others through the people who made it, laid out as rows: the same director with some of the same actors, at least two of the same actors, and the director's
 * other works. Each title appears in one row only, the strongest link first.
 */
data class CineLinks(
    val director: String?,
    val sameTeam: List<LinkedTitle>,
    val sharedCast: List<LinkedTitle>,
    val byDirector: List<TmdbItem>,
) {
    val isEmpty get() = sameTeam.isEmpty() && sharedCast.isEmpty() && byDirector.isEmpty()

    companion object {
        const val CAST_LOOKED_AT = 6
        private const val ROW_MAX = 15
        private const val SHARED_MIN = 2
        /** TMDB's genre id for documentaries. */
        private const val DOCUMENTARY = 99

        /**
         * [directed]: the director's own works. [cast]: each lead actor's name with their works, in billing order.
         * The title itself ([kind], [id]) never links to itself.
         */
        fun build(kind: String, id: Int, director: String?, directed: List<TmdbItem>, cast: List<Pair<String, List<TmdbItem>>>): CineLinks {
            fun key(t: TmdbItem) = "${t.mediaType ?: "movie"}:${t.id}"
            val self = "$kind:$id"
            val byKey = HashMap<String, TmdbItem>()
            val actorsOf = LinkedHashMap<String, MutableList<String>>()
            cast.forEach { (name, works) ->
                // A making-of where the actors play themselves is no link between stories.
                works.distinctBy(::key).filter { DOCUMENTARY !in it.genreIds }.forEach { w ->
                    val k = key(w)
                    if (k == self) return@forEach
                    byKey.putIfAbsent(k, w)
                    actorsOf.getOrPut(k) { mutableListOf() }.add(name)
                }
            }
            val directedKeys = directed.map(::key).toSet()
            fun votes(k: String) = byKey[k]?.voteCount ?: 0
            val team = actorsOf.filterKeys { it in directedKeys }
                .entries.sortedWith(compareByDescending<Map.Entry<String, MutableList<String>>> { it.value.size }.thenByDescending { votes(it.key) })
                .take(ROW_MAX)
                .map { (k, names) -> LinkedTitle(byKey.getValue(k), listOfNotNull(director) + names) }
            val used = team.map { key(it.item) }.toMutableSet()
            val shared = actorsOf.filter { (k, names) -> k !in used && names.size >= SHARED_MIN }
                .entries.sortedWith(compareByDescending<Map.Entry<String, MutableList<String>>> { it.value.size }.thenByDescending { votes(it.key) })
                .take(ROW_MAX)
                .map { (k, names) -> LinkedTitle(byKey.getValue(k), names) }
            used += shared.map { key(it.item) }
            val others = directed.distinctBy(::key).filter { key(it) != self && key(it) !in used && DOCUMENTARY !in it.genreIds }.take(ROW_MAX)
            return CineLinks(director, team, shared, others)
        }

        /** "Avec A et B", "With A, B and C". */
        fun reason(people: List<String>): String {
            val names = people.distinct()
            if (names.isEmpty()) return ""
            val joinedEn = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
            val joinedFr = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " et " + names.last()
            return L("With $joinedEn", "Avec $joinedFr").text()
        }
    }
}
