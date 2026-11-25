package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl

/** Finding media addresses in player pages and scripts. */
internal object MediaText {
    private val absolute = Regex("""https?://[^\s"'<>\\`]+""", RegexOption.IGNORE_CASE)

    /** A JavaScript-written address made plain: `\/` → `/`, `&amp;` → `&`, trailing backslashes and punctuation dropped. */
    fun clean(raw: String): String = raw.replace("\\/", "/").replace("&amp;", "&", ignoreCase = true).trim().trimEnd('\\')

    /** Every absolute http(s) address written in [text], cleaned, in order. */
    fun addresses(text: String): List<String> =
        absolute.findAll(text.replace("\\/", "/")).map { clean(it.value).trimEnd(')', ',', ';') }.toList()

    /** The first value of `"key": "…"` / `key: '…'` in [text] (the key quoted or not), cleaned. */
    fun stringField(text: String, key: String): String? =
        Regex("""["']?${Regex.escape(key)}["']?\s*:\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(2)?.let { clean(it) }

    /** The `file` of the first entry of a `sources: [{ … }]` player setup. */
    fun firstSourceFile(text: String): String? {
        val list = Regex("""sources\s*:\s*\[\s*\{([^}]*)\}""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return stringField(list.groupValues[1], "file") ?: stringField(list.groupValues[1], "src")
    }

    /** Any quoted string containing `.m3u8`, cleaned, in order. */
    fun quotedPlaylists(text: String): List<String> =
        Regex("""(["'])([^"'\s]*\.m3u8[^"'\s]*)\1""", RegexOption.IGNORE_CASE).findAll(text).map { clean(it.groupValues[2]) }.toList()

    fun isPlaylist(url: String): Boolean = url.substringBefore('?').contains(".m3u8", ignoreCase = true)

    /** [LinkFormat.Hls] when the path names a playlist, [LinkFormat.File] when it names a video file, else [fallback]. */
    fun formatOf(url: HttpUrl, fallback: LinkFormat): LinkFormat {
        val path = url.encodedPath.lowercase()
        return when {
            ".m3u8" in path -> LinkFormat.Hls
            path.substringAfterLast('.', "") in setOf("mp4", "mkv", "webm") -> LinkFormat.File
            else -> fallback
        }
    }

    private val hopPatterns = listOf(
        Regex("""window\.location(?:\.href)?\s*=\s*["']([^"']+)["']"""),
        Regex("""http-equiv\s*=\s*["']refresh["'][^>]*content\s*=\s*["'][^"';]*;\s*url\s*=\s*([^"'>\s]+)""", RegexOption.IGNORE_CASE),
        Regex("""https?://[a-z0-9.-]+/e/[a-z0-9]+""", RegexOption.IGNORE_CASE),
    )

    /** Where a small page sends the browser next (script or meta redirect, or a printed `/e/<id>` player address). */
    fun hopTarget(page: Page): HttpUrl? {
        for (pattern in hopPatterns) {
            val match = pattern.find(page.text) ?: continue
            val raw = match.groups[1]?.value ?: match.value
            return page.url.resolve(clean(raw))
        }
        return null
    }
}

/**
 * Follows up to [limit] script hops from [first] until [arrived] says the page is the player. A hop that fails keeps
 * the page reached so far: the caller decides from what it has.
 */
internal suspend fun PageFetcher.followHops(
    first: Page,
    headers: Map<String, String>,
    limit: Int = 3,
    arrived: (Page) -> Boolean,
): Page {
    var page = first
    repeat(limit) {
        if (arrived(page)) return page
        val next = MediaText.hopTarget(page)?.takeIf { it != page.url } ?: return page
        page = try {
            get(next, headers + ("Referer" to page.url.toString()))
        } catch (e: HostError) {
            return page
        }
    }
    return page
}
