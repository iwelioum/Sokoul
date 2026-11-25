package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import okhttp3.HttpUrl
import kotlin.coroutines.cancellation.CancellationException

/** One embed host (Voe, Uqload…): turns its player page into a playable media address. */
interface HostExtractor {
    /** The host's name, as the apps show it ("Uqload"). */
    val host: String

    /** Whether [url] is one of this host's player pages (by domain, or by shape for hosts without fixed domains). */
    fun handles(url: HttpUrl): Boolean

    suspend fun extract(embedUrl: HttpUrl): HostResult
}

/** A subtitle track a host declares beside its media. */
data class HostSubtitle(
    val url: String,
    val label: String,
    /** ISO 639-1 code ("fr"), or null when nothing says. */
    val language: String?,
    val default: Boolean = false,
)

/** What a host extraction gives. */
sealed interface HostResult {
    /** A playable address: [format] is [LinkFormat.Hls] or [LinkFormat.File]; [headers] go with it and its segments. */
    data class Media(
        val url: String,
        val format: LinkFormat,
        val headers: Map<String, String>,
        val subtitles: List<HostSubtitle> = emptyList(),
    ) : HostResult

    data class Failed(val reason: HostFailure) : HostResult
}

/** Why a host gave no media. Stable codes: never an upstream message or address. */
enum class HostFailure {
    /** The host says the video is gone (deleted, 404/410, an unavailable status). */
    Removed,
    /** The page answered but carries no media address. */
    NotFound,
    /** The answer no longer has the expected shape, or the host sent us somewhere unexpected. */
    HostChanged,
    /** The host refused the request (401/403/429/503). */
    Blocked,
    /** The host did not answer in time. */
    Timeout,
    /** The connection failed (DNS, reset, a server error). */
    Network,
    /** The link's shape, or the page's protocol, is one Sokoul does not read. */
    Unsupported,
}

/** Thrown inside an extraction to stop it with [reason]; [hostResult] turns it into [HostResult.Failed]. */
internal class HostError(val reason: HostFailure) : Exception(null, null, false, false)

internal fun fail(reason: HostFailure): Nothing = throw HostError(reason)

/** Runs one extraction: a [HostError] becomes its failure, an unexpected answer (bad JSON, bad Base64…) [HostFailure.HostChanged]. */
internal suspend fun hostResult(block: suspend () -> HostResult): HostResult = try {
    block()
} catch (e: HostError) {
    HostResult.Failed(e.reason)
} catch (e: CancellationException) {
    throw e
} catch (e: RuntimeException) {
    HostResult.Failed(HostFailure.HostChanged)
}

/** A media result for [raw], or [HostFailure.HostChanged] when it is not an http(s) address. */
internal fun media(
    raw: String,
    headers: Map<String, String>,
    fallback: LinkFormat = LinkFormat.File,
    subtitles: List<HostSubtitle> = emptyList(),
): HostResult {
    val url = HostUrls.parseMedia(raw) ?: return HostResult.Failed(HostFailure.HostChanged)
    return HostResult.Media(url.toString(), MediaText.formatOf(url, fallback), headers, subtitles)
}

/** A plain desktop browser, for hosts that serve nothing to unknown clients. */
internal const val BROWSER_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

/** The Accept-Language LuluStream and Veev bind their tokens to: the same on extraction and playback. */
internal const val PINNED_ACCEPT_LANGUAGE = "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7"
