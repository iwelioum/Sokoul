package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * What Fsvid and Vidzy share: the master address is read statically from the page or its packed script, then given the
 * gate key its CDN wants. Pages whose master is only built by the player's JavaScript are [HostFailure.Unsupported].
 */
abstract class GatedPlayer(private val fetcher: PageFetcher) : HostExtractor {
    /** Headers for the embed page. */
    protected abstract val pageHeaders: Map<String, String>

    /** Referer and Origin of the media and its segments, for [master]. */
    protected abstract fun playbackSite(master: HttpUrl): String

    /** The gates to ask for a key, in order (the page may name a newer one first). */
    protected abstract fun gatesFor(page: String): List<HttpUrl>

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        if (!embedPath.containsMatchIn(embedUrl.encodedPath)) fail(HostFailure.Unsupported)
        val page = fetcher.get(embedUrl, pageHeaders)
        val master = PackedScript.withUnpacked(page.text).firstNotNullOfOrNull { staticMaster(it, page.url) }
            ?: fail(if (page.text.contains("atob", ignoreCase = true)) HostFailure.Unsupported else HostFailure.NotFound)
        val keyed = if (master.queryParameter("gk") != null) master else gateKey(page.text)?.let { master.newBuilder().addQueryParameter("gk", it).build() } ?: master
        val site = playbackSite(keyed)
        val headers = buildMap<String, String> {
            put("Referer", "$site/")
            put("Origin", site)
            put("User-Agent", BROWSER_UA)
            sessionToken(keyed)?.let { put("Cookie", "vz_pe=$it; fsv_pe=$it") }
        }
        media(keyed.toString(), headers, LinkFormat.Hls)
    }

    /** A master written as such in [text]: https, an `.m3u8` path, not a decoy. */
    private fun staticMaster(text: String, page: HttpUrl): HttpUrl? {
        val written = listOfNotNull(MediaText.firstSourceFile(text)) + MediaText.quotedPlaylists(text)
        return written.asSequence()
            .filter { MediaText.isPlaylist(it) && !it.contains("troll", ignoreCase = true) }
            .mapNotNull { candidate -> if (candidate.startsWith("https://", ignoreCase = true)) candidate.toHttpUrlOrNull() else if (candidate.startsWith("/") && !candidate.startsWith("//")) page.resolve(candidate) else null }
            .firstOrNull { it.encodedPath.contains(".m3u8", ignoreCase = true) && it.username.isEmpty() }
    }

    /** The CDN key from the first gate that gives one; null when none does (the master then plays, but stops early). */
    private suspend fun gateKey(page: String): String? {
        for (gate in gatesFor(page)) {
            val url = gate.newBuilder().apply { FINGERPRINT.forEach { (name, value) -> addQueryParameter(name, value) } }.build()
            val key = try {
                val answer = Json.parseToJsonElement(fetcher.get(url, mapOf("User-Agent" to BROWSER_UA, "Accept" to "*/*")).text) as? JsonObject
                (answer?.get("gk") as? JsonPrimitive)?.takeIf { it.isString }?.content
            } catch (e: HostError) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            if (key != null && gateKeyShape.matches(key)) return key
        }
        return null
    }

    private fun sessionToken(master: HttpUrl): String? = master.queryParameter("t")?.takeIf { sessionShape.matches(it) }

    private companion object {
        private val embedPath = Regex("/embed[-/]", RegexOption.IGNORE_CASE)
        private val gateKeyShape = Regex("[A-Za-z0-9_.-]{16,512}")
        private val sessionShape = Regex("[A-Za-z0-9_-]{16,128}")

        /** The browser fingerprint the gates accept (text-probe widths of a desktop Chrome, one-pixel ratio, 1920 screen). */
        private val FINGERPRINT = listOf(
            "v" to "p1", "cw" to "159.56", "cw2" to "170.34", "wg" to "1", "dpr" to "1",
            "sw" to "1920", "hw" to "8", "ft" to "TOP", "rf" to "",
        )
    }
}

private val inlineGate = Regex("""fetch\(\s*['"](https://[a-z0-9.-]+/gate\.php)\?""", RegexOption.IGNORE_CASE)

/** A gate named by the page itself, when it sits on a Vidzy or Fsvid host. */
internal fun gateNamedIn(page: String): HttpUrl? = inlineGate.find(page)?.groupValues?.get(1)?.toHttpUrlOrNull()
    ?.takeIf { url -> HostUrls.nameOf(url) == "vidzy" || HostUrls.nameOf(url) == "fsvid" }

/** Fsvid, French-Stream's own host: it refuses its own Referer and wants a French-Stream mirror. */
class Fsvid(
    fetcher: PageFetcher,
    private val gates: List<HttpUrl> = listOfNotNull("https://fsvid.lol/gk".toHttpUrlOrNull(), "https://v6.vidzy.cc/gate.php".toHttpUrlOrNull()),
    private val mirror: String = "https://fsmirror46.lol",
) : GatedPlayer(fetcher) {
    override val host = "Fsvid"

    override fun handles(url: HttpUrl): Boolean = HostUrls.nameOf(url) == "fsvid"

    override val pageHeaders = mapOf(
        "User-Agent" to BROWSER_UA,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Referer" to "$mirror/",
    )

    /** The CDN hosts want `https://fsvid.lol` itself. */
    override fun playbackSite(master: HttpUrl) = "https://fsvid.lol"

    override fun gatesFor(page: String): List<HttpUrl> = gates
}

/** Vidzy: the same player as Fsvid, with its own Referer and a gate its page may name. */
class Vidzy(
    fetcher: PageFetcher,
    private val gates: List<HttpUrl> = listOfNotNull("https://v6.vidzy.cc/gate.php".toHttpUrlOrNull(), "https://fsvid.lol/gk".toHttpUrlOrNull()),
) : GatedPlayer(fetcher) {
    override val host = "Vidzy"

    override fun handles(url: HttpUrl): Boolean = HostUrls.nameOf(url) == "vidzy"

    override val pageHeaders = mapOf("User-Agent" to BROWSER_UA, "Accept" to "text/html,*/*", "Referer" to "https://vidzy.org/")

    override fun playbackSite(master: HttpUrl) = "https://vidzy.org"

    override fun gatesFor(page: String): List<HttpUrl> = (listOfNotNull(gateNamedIn(page)) + gates).distinct()
}
