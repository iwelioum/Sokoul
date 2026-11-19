package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.desktop.player.AudioChoice
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality
import java.util.concurrent.ConcurrentHashMap

/** What this screen and this decoder can really show. */
data class DeviceCaps(val hdr10: Boolean, val dolbyVision: Boolean, val maxHeight: Int) {
    companion object {
        /** Windows draws through RV32 with no tone mapping: HDR would be washed out. */
        val Pc = DeviceCaps(hdr10 = false, dolbyVision = false, maxHeight = 2160)
    }
}

/** What was seen of a version when it was actually played. */
data class SourceFact(
    val failure: PlayFailure? = null,
    val until: Long = 0,
    val language: AudioLanguage? = null,
    val height: Int? = null,
    /** The track languages heard when it played (ISO 639-1); empty when unknown or recorded before they were kept. */
    val codes: List<String> = emptyList(),
)

/** The rules that judge a source. Pure and tested: nothing here touches the network or the screen. */
object SourceRules {
    private const val DAY = 24 * 3_600_000L
    private const val MIN_RATIO = 0.6
    private const val MAX_RATIO = 1.7
    private const val MIN_EPISODE_RATIO = 0.4
    // A double episode is twice as long; 68 min for a 24 min anime episode was the 2023 live-action series (2026-11-08).
    private const val MAX_EPISODE_RATIO = 2.5
    private const val MAX_ANIME_EPISODE_RATIO = 2.1
    private const val BITS = 8
    private const val KBPS = 1000.0

    /** How long a failure counts against a version: what is in the file stays true, what comes from a server passes. */
    fun memoryMs(f: PlayFailure): Long = when (f) {
        PlayFailure.FakeFile, PlayFailure.WrongLanguage -> 30 * DAY
        PlayFailure.Copyright -> 7 * DAY
        PlayFailure.DeadLink, is PlayFailure.Refused -> DAY
        // "Unknown": the player could not open the file itself (a codec the TV lacks): 7 s lost at each start otherwise.
        PlayFailure.Cut, PlayFailure.TimedOut, PlayFailure.Unknown -> 6 * 3_600_000L
        else -> 0
    }

    /** The file is another work, or a piece of one, when its length is far from what the catalog says. */
    fun wrongLength(kind: String, expectedMinutes: Int?, lengthMs: Long, anime: Boolean = false): Boolean {
        if (expectedMinutes == null || expectedMinutes <= 0 || lengthMs <= 0) return false
        val ratio = lengthMs / (expectedMinutes * 60_000.0)
        // An anime episode is at most a double one: 60 min for 24 is the live-action series with the same name.
        val max = if (anime) MAX_ANIME_EPISODE_RATIO else MAX_EPISODE_RATIO
        return if (kind == "movie") ratio < MIN_RATIO || ratio > MAX_RATIO else ratio < MIN_EPISODE_RATIO || ratio > max
    }

    /** Average bitrate of a file, in kilobits per second. */
    fun kbps(sizeBytes: Long, minutes: Int): Double = sizeBytes * BITS / KBPS / (minutes * 60.0)

    /** A "4K" of 800 MB for two hours is no 4K: it is a re-encode or a lie. It only goes after the others. */
    fun suspicious(v: PlayVersion, expectedMinutes: Int?): Boolean {
        val size = v.sizeBytes ?: return false
        if (expectedMinutes == null || expectedMinutes <= 0) return false
        val floor = when (v.quality) {
            VideoQuality.UHD_4K -> 2500
            VideoQuality.FHD_1080 -> 600
            VideoQuality.HD_720 -> 250
            else -> return false
        }
        return kbps(size, expectedMinutes) < floor
    }

    /** The languages of a file's tracks (ISO 639-1), only when every track names one: a partial list would prove a lack falsely. */
    fun heardCodes(tracks: List<Pair<Int, String>>): List<String> {
        val codes = tracks.map { AudioChoice.languageOf(it.second) }
        return if (codes.isEmpty() || codes.any { it == null }) emptyList() else codes.filterNotNull().distinct()
    }

    /** What the tracks of the file say, when they say something. A track with no known language proves nothing. */
    fun heardLanguage(tracks: List<Pair<Int, String>>): AudioLanguage? {
        val known = tracks.mapNotNull { AudioChoice.languageOf(it.second) }.toSet()
        if (known.isEmpty() || tracks.any { AudioChoice.languageOf(it.second) == null }) {
            // One unnamed track next to a French one: French is certain, the rest is not.
            return if ("fr" in known) AudioLanguage.MULTI else null
        }
        return when {
            "fr" in known && known.size > 1 -> AudioLanguage.MULTI
            "fr" in known -> AudioLanguage.VF
            // Spanish and English, Italian and English: a dub beside the original, and which one is original is unknown.
            known.size > 1 -> null
            else -> AudioLanguage.VO
        }
    }

    /** Rank of a picture on this device, lower first. A format it cannot show comes after plain SDR. */
    fun pictureRank(hdr: HdrFormat?, caps: DeviceCaps): Int = when (hdr) {
        HdrFormat.DOLBY_VISION -> if (caps.dolbyVision) 0 else 3
        HdrFormat.HDR10 -> if (caps.hdr10) 1 else 3
        null -> 2
    }

    /** A sharpness the screen cannot show brings nothing: it only costs. */
    fun usefulQuality(q: VideoQuality?, caps: DeviceCaps): VideoQuality? =
        if (q == VideoQuality.UHD_4K && caps.maxHeight < 2160) VideoQuality.FHD_1080 else q

    /** Only stable identities prove duplicates; matching size and picture metadata do not. */
    fun dedupe(versions: List<PlayVersion>): List<PlayVersion> {
        return versions.distinctBy { it.origin to it.key }
    }

    /** A failure of the link itself says the service is the problem: the next attempt comes from another one. */
    fun blamesHost(f: PlayFailure) = f === PlayFailure.FakeFile || dead(f) || f is PlayFailure.Refused

    /** A link that is gone leaves the list of versions while it is remembered: it can come back, so it is hidden, not dropped. */
    fun dead(f: PlayFailure?) = f === PlayFailure.DeadLink || f === PlayFailure.Copyright
}

/** What is known of each version from one launch to the next. A version that lied or broke does not get to do it twice. */
class SourceMemory(private val read: () -> String?, private val write: (String) -> Unit, private val now: () -> Long = System::currentTimeMillis) {
    private val facts = ConcurrentHashMap<String, SourceFact>()
    private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        read()?.lines()?.forEach { line ->
            val p = line.split('\t')
            // Five fields before the track languages were kept, six since.
            if (p.size == 5 || p.size == 6) facts[p[0]] = SourceFact(failureOf(p[1]), p[2].toLongOrNull() ?: 0, AudioLanguage.entries.firstOrNull { it.name == p[3] }, p[4].toIntOrNull(),
                p.getOrNull(5)?.split(',')?.filter { it.isNotBlank() }.orEmpty())
        }
    }

    private fun save() {
        write(facts.entries.toList().takeLast(MAX).joinToString("\n") { (k, f) -> listOf(k, codeOf(f.failure), f.until, f.language?.name.orEmpty(), f.height ?: "", f.codes.joinToString(",")).joinToString("\t") })
    }

    fun fact(key: String): SourceFact? {
        load()
        return facts[key]
    }

    /** The failure still counting against a version, if any. */
    fun failure(key: String): PlayFailure? = fact(key)?.takeIf { it.until > now() }?.failure

    fun noteFailure(key: String, f: PlayFailure) {
        load()
        val ms = SourceRules.memoryMs(f)
        if (ms == 0L) return
        facts[key] = (facts[key] ?: SourceFact()).copy(failure = f, until = now() + ms)
        save()
    }

    /**
     * A fresh search clears temporary link trouble while keeping proven audio/quality and stable content errors.
     * Dead links keep their own day of memory: they stay hidden until then, or until [clearDeadLinks].
     */
    fun clearTransientFailures(prefix: String) = clear(prefix) {
        it != PlayFailure.FakeFile && it != PlayFailure.WrongLanguage && !SourceRules.dead(it)
    }

    /** The viewer asks to try the dead links of a title again. */
    fun clearDeadLinks(prefix: String) = clear(prefix, SourceRules::dead)

    private fun clear(prefix: String, which: (PlayFailure) -> Boolean) {
        load()
        var changed = false
        facts.entries.toList().filter { it.key.startsWith(prefix) }.forEach { (key, fact) ->
            if (fact.failure != null && which(fact.failure)) {
                facts[key] = fact.copy(failure = null, until = 0)
                changed = true
            }
        }
        if (changed) save()
    }

    /** Forget every failure while keeping the observed audio and picture metadata. */
    fun forgetFailures() {
        load()
        var changed = false
        facts.entries.toList().forEach { (key, fact) ->
            if (fact.failure != null || fact.until != 0L) {
                facts[key] = fact.copy(failure = null, until = 0)
                changed = true
            }
        }
        if (changed) save()
    }

    /** It played: whatever was held against it is over, and what it really contains is known. */
    fun noteGood(key: String, language: AudioLanguage?, height: Int?, codes: List<String> = emptyList()) {
        load()
        val before = facts[key]
        val after = SourceFact(language = language ?: before?.language, height = height ?: before?.height, codes = codes.ifEmpty { before?.codes.orEmpty() })
        if (before != after) {
            facts[key] = after
            save()
        }
    }

    private fun codeOf(f: PlayFailure?) = when (f) {
        PlayFailure.FakeFile -> "fake"
        PlayFailure.WrongLanguage -> "lang"
        PlayFailure.DeadLink -> "dead"
        PlayFailure.Copyright -> "copy"
        PlayFailure.Cut -> "cut"
        PlayFailure.TimedOut -> "time"
        PlayFailure.Unknown -> "unknown"
        is PlayFailure.Refused -> "ref${f.code}"
        else -> ""
    }

    private fun failureOf(c: String): PlayFailure? = when {
        c == "fake" -> PlayFailure.FakeFile
        c == "lang" -> PlayFailure.WrongLanguage
        c == "dead" -> PlayFailure.DeadLink
        c == "copy" -> PlayFailure.Copyright
        c == "cut" -> PlayFailure.Cut
        c == "time" -> PlayFailure.TimedOut
        c == "unknown" -> PlayFailure.Unknown
        c.startsWith("ref") -> PlayFailure.Refused(c.drop(REF).toIntOrNull() ?: 0)
        else -> null
    }

    private companion object {
        const val MAX = 800
        const val REF = 3
    }
}
