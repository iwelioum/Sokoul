package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl

/** Vidmoly (and its `ansembed.net` alias): a plain jwplayer setup, asked with a partner site as Referer. */
class Vidmoly(
    private val fetcher: PageFetcher,
    private val partner: String = "https://voirdrama.to",
) : HostExtractor {
    override val host = "Vidmoly"

    override fun handles(url: HttpUrl): Boolean = HostUrls.nameOf(url).let { it == "vidmoly" || it == "ansembed" }

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val asked = mapOf("User-Agent" to BROWSER_UA, "Accept" to "text/html,*/*", "Referer" to "$partner/")
        val page = fetcher.followHops(fetcher.get(embedUrl, asked), asked) { it.text.contains("sources", ignoreCase = true) }
        val address = listOfNotNull(
            MediaText.firstSourceFile(page.text),
            MediaText.quotedPlaylists(page.text).firstOrNull(),
            MediaText.addresses(page.text).firstOrNull(MediaText::isPlaylist),
        ).firstOrNull { it.startsWith("http") && !it.substringBefore('?').endsWith(".mpd", ignoreCase = true) }
            ?: fail(HostFailure.NotFound)
        media(address, mapOf("Referer" to "$partner/", "Origin" to partner), LinkFormat.Hls)
    }
}
