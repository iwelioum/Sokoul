package com.sokoul.pc.dev

import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.pc.playback.sources.NativeEmbedResolver
import com.sokoul.sources.LinkFormat
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.SourceResult
import com.sokoul.sources.WebSource
import com.sokoul.sources.hosts.HostResult
import com.sokoul.sources.hosts.Hosts
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Live check of the host extractors: player pages found by the sites for a few titles are extracted by the current
 * JavaScript runtime and by Sokoul's own Kotlin extractors. Prints host names and outcomes only, never a link.
 */
fun main(): Unit = runBlocking {
    val catalog = Catalog(DesktopDb.open())
    val sites = listOf<WebSource>(
        com.sokoul.sources.coflix.Coflix(catalog.http, tmdb = { k, id, l -> catalog.tmdb.namesAndImages(catalog.key(), k, id, l) }),
        com.sokoul.sources.fstream.FStream(catalog.http, tmdb = { k, id, l -> catalog.tmdb.namesAndImages(catalog.key(), k, id, l) }),
        com.sokoul.sources.voirdrama.VoirDrama(catalog.http, tmdb = { k, id, l -> catalog.tmdb.namesAndImages(catalog.key(), k, id, l) }),
    )
    val queries = listOf(SourceQuery("movie", 27205), SourceQuery("movie", 872585), SourceQuery("movie", 1184918),
        SourceQuery("tv", 1396, 1, 1), SourceQuery("tv", 93405, 1, 1), SourceQuery("tv", 119051, 1, 1))
    val embeds = queries.flatMap { q -> sites.flatMap { s ->
        ((withTimeoutOrNull(60_000) { runCatching { s.find(q) }.getOrNull() } as? SourceResult.Found)?.links.orEmpty())
            .filter { it.format == LinkFormat.Embed }.map { it.url }
    } }.distinct()
    val old = com.sokoul.extract.LocalExtractor(catalog.http, com.sokoul.pc.Platform.localExtractionBridge(catalog.http))
    val tally = sortedMapOf<String, IntArray>() // host -> [tried, oldOk, newOk]
    for (url in embeds) {
        val host = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: "?"
        val ours = Hosts.forUrl(url)
        var oldWhy = if (NativeEmbedResolver.supported(url)) "?" else "unsupported"
        val oldOk = NativeEmbedResolver.supported(url) &&
            withTimeoutOrNull(30_000) {
                val r = runCatching { old.extract(url) }
                oldWhy = r.exceptionOrNull()?.javaClass?.simpleName ?: (old.lastError ?: "none").replace(Regex("""https?://\S+"""), "<url>").take(80)
                r.getOrNull()?.url != null
            } == true
        val newResult = ours?.let { withTimeoutOrNull(30_000) { runCatching { it.extract(Hosts.embedUrl(url)!!) }.getOrNull() } }
        val newOk = newResult is HostResult.Media
        val why = when (newResult) { is HostResult.Failed -> newResult.reason.name; null -> if (ours == null) "no-extractor" else "timeout"; else -> "media" }
        println("HOST|$host|old=${if (oldOk) "ok" else "fail:$oldWhy"}|new=${if (newOk) "ok" else "fail"}|$why|${ours?.host ?: "-"}")
        if (oldOk && !newOk) System.getProperty("sokoul.failedEmbeds")?.let { java.io.File(it).appendText(url + System.lineSeparator()) }
        tally.getOrPut(ours?.host ?: host) { IntArray(3) }.let { it[0]++; if (oldOk) it[1]++; if (newOk) it[2]++ }
    }
    tally.forEach { (h, n) -> println("TALLY|$h|tried=${n[0]}|old=${n[1]}|new=${n[2]}") }
    old.close()
    kotlin.system.exitProcess(0)
}
