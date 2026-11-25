package com.sokoul.sources

import com.sokoul.iptv.domain.AudioLanguage

/** What a source is asked: a film, or one episode of a series, by its TMDB id. */
data class SourceQuery(
    /** "movie" or "tv". */
    val kind: String,
    val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val movie: Boolean get() = kind == "movie"
}

/** How a link is opened. */
enum class LinkFormat {
    /** An HLS playlist (.m3u8). */
    Hls,
    /** A media file (.mp4, .mkv, .webm). */
    File,
    /** A host's player page: its media is found by host extraction (the extractor module), never opened as is. */
    Embed,
}

/** One link a source found for the query. */
data class SourceLink(
    /** The source that found it ("Coflix"). */
    val source: String,
    /** The player or release name the site gives ("Uqload", "pulse | 1080p | MULTI"); the host when it gives none. */
    val name: String,
    val url: String,
    val language: AudioLanguage?,
    /** The quality the site announces beside the link ("HD"), as written. */
    val quality: String? = null,
    val format: LinkFormat,
    /** Headers the link needs ("Referer", "User-Agent"). */
    val headers: Map<String, String> = emptyMap(),
)

/** A source's answer. */
sealed interface SourceResult {
    /** The site has the work: its links, never empty. */
    data class Found(val links: List<SourceLink>) : SourceResult

    /** The site answered and has no such work, or no link for this episode. */
    data object NotFound : SourceResult

    /** The site could not be read: [reason] is one of [BLOCKED], [TIMEOUT], [NETWORK], [HOST_CHANGED]. */
    data class Unavailable(val reason: String) : SourceResult

    companion object {
        /** The site refused the request (403, 429, 503, an anti-bot challenge). */
        const val BLOCKED = "blocked"
        /** The site did not answer in time. */
        const val TIMEOUT = "timeout"
        /** The connection failed (no route, DNS, reset, a server error). */
        const val NETWORK = "network"
        /** The site sent the request to another address: it has probably moved. */
        const val HOST_CHANGED = "host_changed"
    }
}

/** A site that finds playable links for a film or an episode. */
interface WebSource {
    /** The source's name, as the apps show it ("Coflix"). */
    val name: String

    suspend fun find(query: SourceQuery): SourceResult
}
