package com.sokoul.sources.hosts

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The address rules host extraction applies before asking anything. */
object HostUrls {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * An embed link Sokoul may ask: http(s), a host name (no IP literal, no `localhost`), no credentials, the scheme's
     * default port, and no blank or control character anywhere. Anything else is null.
     */
    fun parseEmbed(raw: String): HttpUrl? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return null
        val url = text.toHttpUrlOrNull() ?: return null
        return url.takeIf { isPublicName(it) && it.username.isEmpty() && it.password.isEmpty() && it.port == HttpUrl.defaultPort(it.scheme) }
    }

    /** A host name a request may go to: not an IP literal, not the local machine. */
    fun isPublicName(url: HttpUrl): Boolean {
        val host = url.host.lowercase()
        return host.isNotEmpty() && ':' !in host && !ipv4.matches(host) && host != "localhost" &&
            !host.endsWith(".localhost") && '.' in host
    }

    /** A media address a host answered: http(s), no credentials, not absurdly long. */
    internal fun parseMedia(raw: String): HttpUrl? {
        val text = raw.trim()
        if (text.length > 16_384 || text.any { it.isWhitespace() || it.isISOControl() }) return null
        return text.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
    }

    /** The name just before the top-level domain ("uqload" for `cdn.uqload.to`), lowercase. */
    internal fun nameOf(url: HttpUrl): String = labels(url).let { if (it.size >= 2) it[it.size - 2] else "" }

    /** The top-level domain ("to" for `cdn.uqload.to`), lowercase. */
    internal fun tldOf(url: HttpUrl): String = labels(url).lastOrNull().orEmpty()

    /** Whether the host is [domain] or one of its sub-domains. */
    internal fun isUnder(url: HttpUrl, domain: String): Boolean {
        val host = url.host.lowercase().trimEnd('.')
        return host == domain || host.endsWith(".$domain")
    }

    /** `name.tld` of the host ("uqload.to" for `cdn.uqload.to`). */
    internal fun registrable(url: HttpUrl): String = "${nameOf(url)}.${tldOf(url)}"

    /** `scheme://host[:port]` without a trailing slash. */
    internal fun originOf(url: HttpUrl): String = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().trimEnd('/')

    private fun labels(url: HttpUrl): List<String> = url.host.lowercase().trimEnd('.').split('.').filter { it.isNotEmpty() }
}
