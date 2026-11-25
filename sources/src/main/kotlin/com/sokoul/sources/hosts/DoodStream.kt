package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl
import java.security.SecureRandom

/** DoodStream: the player page names a `pass_md5` path whose answer is the start of the media address. */
class DoodStream(
    private val fetcher: PageFetcher,
    private val now: () -> Long = System::currentTimeMillis,
) : HostExtractor {
    override val host = "DoodStream"

    override fun handles(url: HttpUrl): Boolean {
        val name = HostUrls.nameOf(url)
        val tld = HostUrls.tldOf(url)
        if (name == "doods" && tld in Veev.TLDS) return false
        return (doodName.matches(name) && tld in TLDS) || HostUrls.registrable(url) in OTHER_DOMAINS
    }

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val origin = HostUrls.originOf(embedUrl)
        val headers = mapOf("User-Agent" to BROWSER_UA, "Referer" to "$origin/")
        var page = fetcher.get(embedUrl, headers)
        if (passPath.find(page.text) == null) page = playerBehindShell(page, headers) ?: page

        val path = passPath.find(page.text)?.value ?: fail(HostFailure.Removed)
        val token = tokenParam.find(page.text)?.groupValues?.get(1) ?: path.trimEnd('/').substringAfterLast('/')
        val passUrl = page.url.resolve(path) ?: fail(HostFailure.HostChanged)
        val start = fetcher.get(passUrl, mapOf("User-Agent" to BROWSER_UA, "Referer" to page.url.toString())).text.trim()
        if (!start.startsWith("http")) fail(HostFailure.HostChanged)

        // R2 answers come signed and complete; the others take the player's random suffix and token.
        val address = if ("cloudflarestorage." in start) start else "$start${randomSuffix()}?token=$token&expiry=${now()}"
        val playerOrigin = HostUrls.originOf(page.url)
        media(address, mapOf("Referer" to "$playerOrigin/", "Origin" to playerOrigin, "User-Agent" to BROWSER_UA), LinkFormat.File)
    }

    /** A `/d/<id>` shell holds the player in an iframe (or at the same address with `/e/`). */
    private suspend fun playerBehindShell(shell: Page, headers: Map<String, String>): Page? {
        val frame = iframe.find(shell.text)?.groupValues?.get(1)?.let { shell.url.resolve(it) }
        val next = frame ?: shell.url.toString().takeIf { "/d/" in it }?.let { shell.url.resolve(it.replaceFirst("/d/", "/e/")) }
        if (next == null || next == shell.url) return null
        return try {
            fetcher.get(next, headers + ("Referer" to shell.url.toString()))
        } catch (e: HostError) {
            null
        }
    }

    private fun randomSuffix(): String = buildString { repeat(10) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    internal companion object {
        private val random = SecureRandom()
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val passPath = Regex("""/pass_md5/[\w-]+(?:/[\w-]+)*""")
        private val tokenParam = Regex("""[?&]token=([\w-]+)""")
        private val iframe = Regex("""<iframe[^>]*\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val doodName = Regex("""d[o0]*ds?(?:tream|ter|cdn)?""")
        val TLDS = setOf("com", "to", "so", "sh", "cx", "la", "ws", "pm", "wf", "re", "yt", "li", "work", "stream", "io", "net", "pro")
        val OTHER_DOMAINS = setOf(
            "ds2play.com", "ds2video.com", "dsvplay.com", "dsvvideo.com", "vidply.com", "vidplay.com", "myvidply.com",
            "myvidplay.com", "vide0.net", "vide0.com", "myvide0.net", "vvide0.com", "all3do.com", "do7go.com", "doply.net",
            "d-s.io", "playmogo.com",
        )
    }
}
