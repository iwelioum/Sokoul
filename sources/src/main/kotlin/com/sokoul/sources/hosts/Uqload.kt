package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Uqload: a jwplayer page (often packed) on a rotating `uqload.<tld>`, with its subtitle tracks. */
class Uqload(private val fetcher: PageFetcher) : HostExtractor {
    override val host = "Uqload"

    override fun handles(url: HttpUrl): Boolean = isUqload(url)

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val id = embedUrl.pathSegments.lastOrNull { it.isNotEmpty() }
            ?.removePrefix("embed-")?.removeSuffix(".html")
            ?.takeIf { videoId.matches(it) }
            ?: fail(HostFailure.Unsupported)
        val site = siteOf(embedUrl)
        val headers = mapOf("User-Agent" to BROWSER_UA, "Accept" to "text/html,*/*", "Referer" to "$site/", "Origin" to site)
        // The embed page is sometimes an empty shell (38 bytes, 2026-11-25) while the plain page carries the player:
        // each page is tried until one holds a media address.
        var failure: HostError? = null
        var removed = false
        var loaded = false
        for (url in listOf(embedUrl.withPath("/embed-$id.html"), embedUrl.withPath("/$id.html"))) {
            val page = try {
                fetcher.get(url, headers)
            } catch (e: HostError) {
                if (failure == null) failure = e
                continue
            }
            loaded = true
            if (page.text.contains("File was deleted", ignoreCase = true)) removed = true
            val texts = PackedScript.withUnpacked(page.text)
            val address = mediaIn(texts) ?: continue
            return@hostResult media(address, mapOf("Referer" to "$site/", "Origin" to site), LinkFormat.File, subtitlesIn(texts, page.url))
        }
        // A page that loaded without media says more than the other page's error.
        fail(if (removed) HostFailure.Removed else if (loaded) HostFailure.NotFound else failure?.reason ?: HostFailure.NotFound)
    }

    /** The stream in [texts]: the master playlist first, then any playlist, then the MP4, on an Uqload host only. */
    private fun mediaIn(texts: List<String>): String? {
        val candidates = texts.flatMap(MediaText::addresses).filter { address -> address.toHttpUrlOrNull()?.let { isUqload(it) } == true }
        return candidates.firstOrNull { it.substringBefore('?').endsWith("/master.m3u8", ignoreCase = true) }
            ?: candidates.firstOrNull(MediaText::isPlaylist)
            ?: candidates.firstOrNull { it.substringBefore('?').endsWith("/v.mp4", ignoreCase = true) }
    }

    /** The caption tracks of the jwplayer setup, on an Uqload host, without thumbnails or the empty placeholder. */
    internal fun subtitlesIn(texts: List<String>, page: HttpUrl): List<HostSubtitle> {
        val found = LinkedHashMap<String, HostSubtitle>()
        for (text in texts) {
            for (block in tracksBlock.findAll(text.replace("\\/", "/"))) {
                for (entry in trackEntry.findAll(block.groupValues[1])) {
                    val fields = entry.value
                    val file = MediaText.stringField(fields, "file") ?: MediaText.stringField(fields, "src") ?: continue
                    val kind = MediaText.stringField(fields, "kind")?.lowercase() ?: "captions"
                    if (kind != "captions" && kind != "subtitles") continue
                    val url = page.resolve(file)?.takeIf { isUqload(it) } ?: continue
                    val name = url.pathSegments.lastOrNull().orEmpty()
                    if (!subtitleFile.matches(name) || name.startsWith("empty.", ignoreCase = true)) continue
                    val label = MediaText.stringField(fields, "label").orEmpty()
                    val language = languageOf("$label ${name.replace(Regex("[_.-]"), " ")}")
                    found.getOrPut(url.toString()) {
                        HostSubtitle(
                            url = url.toString(),
                            label = label.ifBlank { language?.uppercase() ?: "Track ${found.size + 1}" },
                            language = language,
                            default = defaultFlag.containsMatchIn(fields),
                        )
                    }
                    if (found.size >= 20) return found.values.toList()
                }
            }
        }
        return found.values.toList()
    }

    private fun HttpUrl.withPath(path: String): HttpUrl = newBuilder().encodedPath(path).query(null).fragment(null).build()

    private companion object {
        val videoId = Regex("[A-Za-z0-9_-]+")
        val uqloadName = Regex("[a-z]{2,24}")
        val tracksBlock = Regex("""tracks\s*:\s*\[([\s\S]{0,8000}?)]""")
        val trackEntry = Regex("""\{[^{}]*}""")
        val subtitleFile = Regex(""".+\.(vtt|srt)""", RegexOption.IGNORE_CASE)
        val defaultFlag = Regex("""["']?default["']?\s*:\s*["']?true""", RegexOption.IGNORE_CASE)
        val languages = listOf(
            "fr" to Regex("""\b(fr|fra|fre|french|fran[cç]ais|vf|vostfr)\b""", RegexOption.IGNORE_CASE),
            "en" to Regex("""\b(en|eng|english|anglais)\b""", RegexOption.IGNORE_CASE),
            "es" to Regex("""\b(es|spa|spanish|espa[nñ]ol)\b""", RegexOption.IGNORE_CASE),
            "de" to Regex("""\b(de|ger|deu|german|deutsch)\b""", RegexOption.IGNORE_CASE),
            "it" to Regex("""\b(it|ita|italian|italiano)\b""", RegexOption.IGNORE_CASE),
            "pt" to Regex("""\b(pt|por|portuguese|portugu[eê]s)\b""", RegexOption.IGNORE_CASE),
            "ar" to Regex("""\b(ar|ara|arabic|arabe)\b""", RegexOption.IGNORE_CASE),
        )

        fun languageOf(text: String): String? = languages.firstOrNull { it.second.containsMatchIn(text) }?.first

        /** A host whose registrable name is `uqload.<tld>`. */
        fun isUqload(url: HttpUrl): Boolean = HostUrls.nameOf(url) == "uqload" && uqloadName.matches(HostUrls.tldOf(url))

        /** `https://uqload.<tld>` for an Uqload host, the link's own origin otherwise (a mirror, or a test server). */
        fun siteOf(url: HttpUrl): String = if (isUqload(url)) "https://${HostUrls.registrable(url)}" else HostUrls.originOf(url)
    }
}
