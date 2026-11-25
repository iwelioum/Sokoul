package com.sokoul.sources

import com.sokoul.iptv.domain.AudioLanguage
import java.net.URI
import java.text.Normalizer

/**
 * The audio a site's label announces ("VF", "VOSTFR", "French", "VO sous-titrée FR", "MULTI"), or null when it says
 * nothing. One rule for the sources read here and the Sokoul server's answers the apps read.
 */
fun audioLanguageOf(label: String): AudioLanguage? {
    val normalized = Normalizer.normalize(label.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    val tokens = normalized.split(Regex("[^a-z0-9]+")).filter(String::isNotEmpty).toSet()
    val compact = tokens.joinToString("")
    val explicitDub = tokens.any { it in FRENCH_DUB || NUMBERED_FRENCH.matches(it) }
    val french = explicitDub || tokens.any { it in FRENCH_AUDIO } || compact in FRENCH_AUDIO_COMPACT
    val original = tokens.any { it in ORIGINAL_AUDIO }
    val subtitles = tokens.any { it in FRENCH_SUBTITLES } || (french && tokens.any { it in SUBTITLE_HINTS })

    if (tokens.any { it in MULTI_AUDIO }) return AudioLanguage.MULTI
    // “VO sous-titrée FR” names one audio track. Only an explicit VF marker can also promise a dub.
    if (subtitles) return if (explicitDub) AudioLanguage.MULTI else AudioLanguage.VOSTFR
    // Some provider payloads list both audio tracks explicitly instead of labelling the release "MULTI".
    if (french && original) return AudioLanguage.MULTI
    if (french) return AudioLanguage.VF
    if (original) return AudioLanguage.VO
    return null
}

private val FRENCH_DUB = setOf("vf", "vff", "vfq", "vfi", "truefrench")
private val NUMBERED_FRENCH = Regex("vf[0-9]+")
private val FRENCH_AUDIO = setOf("fr", "fra", "fre", "french", "francais", "francaise")
private val FRENCH_AUDIO_COMPACT = setOf("truefrench", "frenchdub", "francaisdub", "frfr", "frenchvf", "francaisvf")
private val ORIGINAL_AUDIO = setOf("voeng", "va", "vj", "vkr", "vo", "original", "japanese", "japonais", "ja", "jpn", "en", "eng", "english")
private val FRENCH_SUBTITLES = setOf("vostfr", "vost", "stfr", "subfr", "sousstitresfrancais")
private val MULTI_AUDIO = setOf("multi", "multiaudio", "dual", "dualaudio")
private val SUBTITLE_HINTS = setOf("sub", "subs", "subtitle", "subtitles", "subbed", "sous", "sous-titres")

/** The player's site ("uqload.cx"), to tell links apart when the site gives no player name. */
fun hostOf(url: String?): String? =
    url?.let { runCatching { URI(it).host }.getOrNull() }?.removePrefix("www.")?.takeIf { it.isNotBlank() }

/** What a media link's address says about it, by its path: never resolved, never opened. */
object MediaUrls {
    private val extensions = setOf("m3u8", "mp4", "mkv", "webm")

    /** An http(s) address with a host, no credentials, no fragment and no blank: anything else is refused. */
    fun parse(raw: String): URI? = runCatching { URI(raw) }.getOrNull()?.takeIf {
        it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null && it.fragment == null && raw.none { c -> c.isWhitespace() || c == '\u0000' }
    }

    /** The address's query parameters, decoded; a parameter that cannot be decoded is left out. */
    fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').mapNotNull {
        val key = it.substringBefore('=')
        val value = runCatching { java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: return@mapNotNull null
        key to value
    }.toMap()

    /** A media file or playlist a player opens as is (.m3u8, .mp4, .mkv, .webm, or an HLS folder), never a player page. */
    fun file(url: String, format: String? = null): Boolean {
        val media = parse(url) ?: return false
        val path = media.path.orEmpty().lowercase()
        if (Regex("/(embed|player)(/|$)").containsMatchIn(path)) return false
        return path.substringAfterLast('.', "") in extensions || (format == "hls" && "/hls/" in path)
    }

    fun hls(url: String, format: String?): Boolean = when (format?.lowercase()) {
        "hls", "m3u8", "application/vnd.apple.mpegurl", "application/x-mpegurl" -> true
        "mp4", "video/mp4" -> false
        else -> {
            val media = parse(url)
            media?.path.orEmpty().endsWith(".m3u8", true) || media?.let(::query)?.get("url")?.substringBefore('?')?.endsWith(".m3u8", true) == true
        }
    }

    /** The player's media type hint: "hls", "mp4", or null when the address does not say. */
    fun type(url: String, format: String?): String? {
        if (hls(url, format)) return "hls"
        if (format?.lowercase() in setOf("mp4", "video/mp4")) return "mp4"
        val media = parse(url)
        return "mp4".takeIf { media?.path.orEmpty().endsWith(".mp4", true) || media?.let(::query)?.get("url")?.substringBefore('?')?.endsWith(".mp4", true) == true }
    }

    /** [LinkFormat.Hls] or [LinkFormat.File] for a link [file] accepts. */
    fun format(url: String, format: String?): LinkFormat = if (hls(url, format)) LinkFormat.Hls else LinkFormat.File
}
