package com.sokoul.sources.hosts

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.net.URLDecoder

/** The embed hosts Sokoul extracts itself, and which one a link belongs to. */
object Hosts {
    /** Every host, on a default client. Apps that have their own client use [create]. */
    val all: List<HostExtractor> by lazy { create(PageFetcher(OkHttpClient())) }

    /**
     * The hosts over [fetcher]. Order matters where domains overlap: Veev (`doods.to`) before DoodStream, and
     * SeekStreaming, recognised by shape rather than domain, last.
     */
    fun create(fetcher: PageFetcher): List<HostExtractor> = listOf(
        Voe(fetcher),
        Vidmoly(fetcher),
        Sibnet(fetcher),
        Uqload(fetcher),
        Veev(fetcher),
        DoodStream(fetcher),
        LuluStream(fetcher),
        Vidara(fetcher),
        Fsvid(fetcher),
        Vidzy(fetcher),
        SeekStreaming(fetcher),
    )

    /** The host of [url], or null when the link is not a valid public http(s) address or no host claims it. */
    fun forUrl(url: String): HostExtractor? = embedUrl(url)?.let { parsed -> all.firstOrNull { it.handles(parsed) } }

    /** [raw] as an embed address, once or twice URL-decoded when it arrives encoded (`https%3A%2F%2F…`). */
    fun embedUrl(raw: String): HttpUrl? {
        var text = raw.trim()
        repeat(2) {
            if (!text.startsWith("http%3a", ignoreCase = true) && !text.startsWith("https%3a", ignoreCase = true)) return@repeat
            text = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrNull() ?: return null
        }
        return HostUrls.parseEmbed(text)
    }
}
