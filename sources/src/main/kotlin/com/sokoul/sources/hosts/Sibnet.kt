package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Sibnet: the `shell.php` player names a relative media path, which redirects to a numbered CDN host. */
class Sibnet(
    private val fetcher: PageFetcher,
    private val origin: HttpUrl = "https://video.sibnet.ru/".toHttpUrlOrNull()!!,
) : HostExtractor {
    override val host = "Sibnet"

    override fun handles(url: HttpUrl): Boolean = HostUrls.isUnder(url, "sibnet.ru")

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val player = playerOf(embedUrl) ?: fail(HostFailure.Unsupported)
        val headers = mapOf("User-Agent" to BROWSER_UA, "Accept" to "text/html,*/*", "Referer" to REFERER)
        val page = fetcher.get(player, headers)
        val path = (srcCall.find(page.text) ?: relativeSrc.find(page.text))?.groupValues?.get(1) ?: fail(HostFailure.NotFound)
        val first = origin.resolve(MediaText.clean(path)) ?: fail(HostFailure.HostChanged)
        // The CDN host is only known by following the redirect; a failure there still leaves a playable address.
        val landed = try {
            fetcher.landing(first, mapOf("User-Agent" to BROWSER_UA, "Accept" to "*/*", "Referer" to REFERER))
        } catch (e: HostError) {
            first
        }
        media(landed.toString(), mapOf("Referer" to REFERER, "Origin" to REFERER.trimEnd('/')), LinkFormat.File)
    }

    /** `<origin>/shell.php?videoid=<n>` for a `videoid` query or a `/video<n>-…` permalink. */
    internal fun playerOf(url: HttpUrl): HttpUrl? {
        val id = url.queryParameter("videoid")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: permalink.find(url.encodedPath)?.groupValues?.get(1)
            ?: return null
        return origin.newBuilder().encodedPath("/shell.php").query(null).addQueryParameter("videoid", id).build()
    }

    private companion object {
        const val REFERER = "https://video.sibnet.ru/"
        val permalink = Regex("""/video(\d+)""")
        val srcCall = Regex("""player\.src\(\s*\[\s*\{\s*src\s*:\s*["']([^"']+)["']""")
        val relativeSrc = Regex("""\bsrc\s*:\s*["'](/[^"']+)["']""")
    }
}
