package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl

/** LuluStream and its mirrors: a packed jwplayer setup at `<origin>/e/<id>`. */
class LuluStream(private val fetcher: PageFetcher) : HostExtractor {
    override val host = "LuluStream"

    override fun handles(url: HttpUrl): Boolean =
        (luluName.matches(HostUrls.nameOf(url)) && HostUrls.tldOf(url) in TLDS) || DOMAINS.any { HostUrls.isUnder(url, it) }

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val id = embedUrl.pathSegments.lastOrNull { it.isNotEmpty() }?.removeSuffix(".html")?.takeIf { fileCode.matches(it) }
            ?: fail(HostFailure.Unsupported)
        val origin = HostUrls.originOf(embedUrl)
        val headers = mapOf(
            "User-Agent" to BROWSER_UA,
            "Accept" to "text/html,*/*",
            "Referer" to "$origin/",
            "Accept-Language" to PINNED_ACCEPT_LANGUAGE,
        )
        val page = fetcher.get(embedUrl.newBuilder().encodedPath("/e/$id").query(null).fragment(null).build(), headers)
        // Most mirrors pack the player: read the unpacked scripts before the raw page.
        val texts = PackedScript.unpackAll(page.text) + page.text
        val address = texts.firstNotNullOfOrNull { text ->
            MediaText.firstSourceFile(text) ?: MediaText.addresses(text).firstOrNull(MediaText::isPlaylist)
        } ?: fail(HostFailure.NotFound)
        media(address, PLAYBACK, LinkFormat.Hls)
    }

    private companion object {
        val fileCode = Regex("[0-9A-Za-z]+")
        val luluName = Regex("""lulu(?:stream|vi?do{0,2})?""")
        val TLDS = setOf("com", "sbs", "site", "st")
        val DOMAINS = listOf("streamhihi.com", "cdn1.site", "d00ds.site", "732eg54de642sa.sbs")
        val PLAYBACK = mapOf(
            "Referer" to "https://lulustream.com/",
            "Origin" to "https://lulustream.com",
            "Accept-Language" to PINNED_ACCEPT_LANGUAGE,
            "User-Agent" to BROWSER_UA,
        )
    }
}
