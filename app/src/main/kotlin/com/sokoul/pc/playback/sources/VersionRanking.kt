package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.MaxQuality
import com.sokoul.desktop.data.MaxSize
import com.sokoul.desktop.data.PlaybackPrefs
import com.sokoul.desktop.data.PreferredOrigin
import com.sokoul.desktop.data.StartMode
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality

/**
 * The order of versions, pure and tested: playable here, then language, then ready or quality according to the start
 * mode. SDR comes before HDR because the picture goes through Compose as RV32 with no tone mapping, which would wash out
 * HDR colors.
 */
object VersionRanking {
    /** Most wanted first. "According to the system" follows the system's language. */
    fun audioOrder(preference: AudioPreference, frenchSystem: Boolean): List<AudioLanguage> = when (preference) {
        AudioPreference.System -> if (frenchSystem) listOf(AudioLanguage.VF, AudioLanguage.MULTI, AudioLanguage.VOSTFR, AudioLanguage.VO) else listOf(AudioLanguage.VO, AudioLanguage.MULTI, AudioLanguage.VOSTFR, AudioLanguage.VF)
        AudioPreference.Vf -> listOf(AudioLanguage.VF, AudioLanguage.MULTI, AudioLanguage.VOSTFR, AudioLanguage.VO)
        AudioPreference.Multi -> listOf(AudioLanguage.MULTI, AudioLanguage.VF, AudioLanguage.VO, AudioLanguage.VOSTFR)
        AudioPreference.Vostfr -> listOf(AudioLanguage.VOSTFR, AudioLanguage.VO, AudioLanguage.MULTI, AudioLanguage.VF)
        AudioPreference.Vo -> listOf(AudioLanguage.VO, AudioLanguage.VOSTFR, AudioLanguage.MULTI, AudioLanguage.VF)
    }

    fun overLimits(v: PlayVersion, prefs: PlaybackPrefs): Boolean =
        (v.sizeBytes ?: 0) > prefs.maxSize.bytes || qualityValue(v.quality) > prefs.maxQuality.height

    /** How wanted what one hears is, lower first. A MULTI file holds the French and the original track: it is as good as whichever of the two is wanted most. */
    fun heard(v: PlayVersion, prefs: PlaybackPrefs, frenchSystem: Boolean, fact: SourceFact? = null, workLanguage: String? = null): Int =
        AudioMatch.verdict(v, prefs.intent, fact, workLanguage).heard

    /**
     * @param caps what this device can show
     * @param fact what a past viewing proved about a version: it counts over what its name announces
     * @param expectedMinutes the length of the work, to judge a size
     */
    fun rank(
        versions: List<PlayVersion>,
        prefs: PlaybackPrefs,
        frenchSystem: Boolean,
        // The best picture first means what the account already holds does not jump ahead of a better version.
        readyFirst: Boolean = prefs.startMode != StartMode.WaitBest && !prefs.bestQualityFirst,
        caps: DeviceCaps = DeviceCaps.Pc,
        fact: (PlayVersion) -> SourceFact? = { null },
        expectedMinutes: Int? = null,
        /** The work's original language, which "original" in the wanted languages stands for. */
        workLanguage: String? = null,
    ): List<PlayVersion> {
        // One audio judgement for the ranking, the source preferences and the upgrade offer (AudioMatch), in any language.
        val verdicts = java.util.IdentityHashMap<PlayVersion, AudioVerdict>()
        fun verdict(v: PlayVersion) = verdicts.getOrPut(v) { AudioMatch.verdict(v, prefs.intent, fact(v), workLanguage) }
        // At the same wanted language: what a viewing proved, then what the name declares, then what a label implies.
        fun exact(v: PlayVersion) = verdict(v).proof.ordinal
        val heard = { v: PlayVersion -> verdict(v).heard }
        // Original wanted: a foreign dub of unknown language comes after a plain release, which is most often the
        // original alone, even when it is ready and the plain one is not.
        val foreignDub = { v: PlayVersion -> verdict(v).dubLast }
        val now = System.currentTimeMillis()
        // A version that failed for a lasting reason keeps its place in the list, after all the others.
        // A version of another work or episode (anime) goes after everything, even what failed.
        val base = compareBy<PlayVersion> { if (it.rejected != null) 1 else 0 }
            .thenBy { if (fact(it)?.let { f -> f.failure != null && f.until > now } == true) 1 else 0 }
            // The wanted language first: a VF above the size or quality limits still comes before a VOSTFR within them.
            .thenBy(heard)
            .thenBy(foreignDub)
            .thenBy { if (overLimits(it, prefs)) 1 else 0 }
            .thenBy { originRank(it, prefs.origin) }
            // Anime, at the same language: a file that says it is the episode, then one that cannot be read, then a doubtful one.
            .thenBy { it.confidence }
            .thenBy { if (SourceRules.suspicious(it, expectedMinutes)) 1 else 0 }
        // IPTV is always there but counts on the account's connections and its picture is often poor (720x406 for a
        // Spartacus episode, 2026-11-19): it comes after every Real-Debrid and web version that may still play, as a last
        // resort. "Preferred source: IPTV" puts it first again.
        // A web link is never in the account: it comes after every Real-Debrid version that may still play (Unknown
        // included). Ranked "Likely", it went before the Torrentio results not yet checked, and a streaming site played
        // where Real-Debrid had the film (2026-11-23).
        val byReady = compareBy<PlayVersion> {
            when {
                it.origin == VersionOrigin.Iptv -> IPTV_READY_RANK
                it.origin == VersionOrigin.Web -> WEB_READY_RANK
                else -> readyRank(it.readiness)
            }
        }
        // Windows wants SDR first. A TV that shows HDR wants the sharpest, then the richest; a format it cannot show goes after SDR.
        // The viewer's choice "best quality first": the sharpest first, then the richest picture, whatever the screen.
        val byPicture = if (prefs.bestQualityFirst) {
            // Only the sharpness here: at the same sharpness what is ready goes first, then the picture this screen shows.
            compareBy<PlayVersion> { qualityRank(SourceRules.usefulQuality(it.quality, caps)) }
        } else if (caps.hdr10 || caps.dolbyVision) {
            compareBy<PlayVersion> { if (SourceRules.pictureRank(it.hdr, caps) > 2) 1 else 0 }
                .thenBy { qualityRank(SourceRules.usefulQuality(it.quality, caps)) }
                .thenBy { SourceRules.pictureRank(it.hdr, caps) }
        } else {
            compareBy<PlayVersion> { if (it.hdr == null) 0 else hdrRank(it.hdr) }.thenBy { qualityRank(SourceRules.usefulQuality(it.quality, caps)) }
        }
        // At the same language and picture, the release the SeaDex community keeps comes first.
        val rest = compareBy<PlayVersion> { if (it.community) 0 else 1 }
            .thenBy(::exact)
            .thenBy { it.ripRank }
            .thenBy { it.frenchVariantRank }
            .thenByDescending { it.seeders ?: 0 }
            // 0L, not 0: an Int beside the Longs broke the sort (ClassCastException) as soon as a version without a size
            // (Anime-Sama, the web sources) met one with a size, and the session kept the list without them.
            .thenByDescending { it.sizeBytes ?: 0L }
            .thenBy { it.key }
        // Best quality first: the sharpest, then at that sharpness what is ready (a 4K not in the cache cost 17 s before
        // the 4K in the account played, Google TV Streamer, 2026-11-23), then SDR before HDR10 on an SDR set and Dolby
        // Vision only where it plays.
        val byFormat = compareBy<PlayVersion> { SourceRules.pictureRank(it.hdr, caps) }
        val order = when {
            readyFirst -> base.then(byReady).then(byPicture)
            prefs.bestQualityFirst -> base.then(byPicture).then(byReady).then(byFormat)
            else -> base.then(byPicture).then(byReady)
        }.then(rest)
        return versions.sortedWith(order)
    }

    private fun originRank(v: PlayVersion, preferred: PreferredOrigin) = when (preferred) {
        PreferredOrigin.Automatic -> 0
        PreferredOrigin.Debrid -> if (v.origin == VersionOrigin.Debrid) 0 else 1
        PreferredOrigin.Iptv -> if (v.origin == VersionOrigin.Iptv) 0 else 1
    }

    // After Real-Debrid, the sites, then IPTV as the last resort (its picture is often poor). A dead site is left after
    // three failed links (PlaybackSession.MAX_WEB_FAILS), so it no longer holds the start for long. The owner expected the
    // web before IPTV (2026-11-24).
    private const val WEB_READY_RANK = 4
    private const val IPTV_READY_RANK = 5

    private fun readyRank(r: Readiness) = when (r) {
        Readiness.Ready -> 0
        Readiness.Likely -> 1
        Readiness.Unknown -> 3
        Readiness.Preparing -> 6
        Readiness.NotCached -> 7
    }

    private fun qualityRank(q: VideoQuality?) = when (q) {
        VideoQuality.UHD_4K -> 0
        VideoQuality.FHD_1080 -> 1
        VideoQuality.HD_720, null -> 2
        VideoQuality.SD -> 3
    }

    private fun qualityValue(q: VideoQuality?) = when (q) {
        VideoQuality.UHD_4K -> 2160
        VideoQuality.FHD_1080 -> 1080
        VideoQuality.HD_720, null -> 720
        VideoQuality.SD -> 480
    }

    private fun hdrRank(h: HdrFormat) = when (h) {
        HdrFormat.HDR10 -> 1
        HdrFormat.DOLBY_VISION -> 2
    }

    /** True when [a] shows a sharper picture than [b]. */
    fun sharper(a: PlayVersion, b: PlayVersion) = qualityRank(a.quality) < qualityRank(b.quality)
}

/** A version to try, and whether to wait for its download if it is not ready. */
data class StartChoice(val version: PlayVersion, val keep: Boolean)

/** Which version comes next, according to the start preference. Pure and tested. */
object StartPolicy {
    /** Tries without waiting, each costing seconds on the account. A version known to be ready is always worth it. */
    private const val MAX_LIKELY_TRIES = 10
    private const val MAX_UNKNOWN_TRIES = 5

    /**
     * @param ranked the versions, best first
     * @param failed versions that must not be tried again
     * @param quickTried versions already tried without waiting
     * @param blocked origins that cannot work at the moment
     * @param readyOnly origins where only a version already in the account is tried. After a Real-Debrid server
     *   answered 509, the next tries added torrent after torrent, and many adds in a row get the account paused.
     */
    fun next(
        ranked: List<PlayVersion>,
        mode: StartMode,
        failed: Set<String>,
        quickTried: Set<String>,
        blocked: Set<VersionOrigin>,
        failedHosts: Set<String> = emptySet(),
        readyOnly: Set<VersionOrigin> = emptySet(),
    ): StartChoice? {
        if (mode == StartMode.Ask) return null
        // A contradicted version (anime) is only ever played when the viewer picks it.
        val open = ranked.filter { it.key !in failed && it.origin !in blocked && it.rejected == null && (it.origin !in readyOnly || it.readiness == Readiness.Ready) }
        // After a service gave a bad link, its other links are likely as bad: those of other services come first.
        val usable = open.filter { it.providerHost == null || it.providerHost !in failedHosts } + open.filter { it.providerHost != null && it.providerHost in failedHosts }
        if (mode == StartMode.WaitBest) {
            // Waiting for the best still begins with a quick look: the best ones not known to be in the cache are asked
            // at once (in a race), and only when none is ready does the best one download. One by one, each that nobody
            // shares cost 16 s before the next (Breaking Bad 4K on the Google TV Streamer, 2026-11-23).
            val best = usable.firstOrNull() ?: return null
            val look = usable.firstOrNull { it.origin == VersionOrigin.Debrid && it.quality == best.quality && it.readiness != Readiness.Ready &&
                it.key !in quickTried && worthATry(it.readiness, quickTried.size) }
            if (best.origin == VersionOrigin.Debrid && best.readiness != Readiness.Ready && look != null) return StartChoice(look, keep = false)
            return StartChoice(best, keep = true)
        }
        val quick = usable.firstOrNull { it.key !in quickTried && worthATry(it.readiness, quickTried.size) }
        if (quick != null) return StartChoice(quick, keep = false)
        // Nothing can start right away: the best version is downloaded, with its progress on screen.
        return usable.firstOrNull { it.origin == VersionOrigin.Debrid }?.let { StartChoice(it, keep = true) }
    }

    /**
     * The versions to try at once, when the next choice would be a quick try of a Real-Debrid version that is not in the
     * account: it and the next ones of the same kind, [size] at most. Empty when the choice is anything else (a version
     * in the account, IPTV, a download to wait for): that one goes alone, as before.
     */
    fun race(
        ranked: List<PlayVersion>,
        mode: StartMode,
        failed: Set<String>,
        quickTried: Set<String>,
        blocked: Set<VersionOrigin>,
        failedHosts: Set<String>,
        size: Int,
        /** Only versions of the first one's quality race: else the quickest link, often a 1080p, beat the 4K. */
        sameQuality: Boolean = false,
        /**
         * How wanted what one hears is (lower first): only versions heard like the first one race. A VO raced beside the
         * last French ones and, quickest, won (Les Infiltrés, 2026-11-22).
         */
        heard: (PlayVersion) -> Int = { 0 },
        readyOnly: Set<VersionOrigin> = emptySet(),
    ): List<PlayVersion> {
        if (size < 2) return emptyList()
        val first = next(ranked, mode, failed, quickTried, blocked, failedHosts, readyOnly) ?: return emptyList()
        if (first.keep || first.version.origin != VersionOrigin.Debrid || first.version.readiness == Readiness.Ready) return emptyList()
        val open = ranked.filter { it.key !in failed && it.origin !in blocked && it.rejected == null && (it.origin !in readyOnly || it.readiness == Readiness.Ready) }
        val usable = open.filter { it.providerHost == null || it.providerHost !in failedHosts } + open.filter { it.providerHost != null && it.providerHost in failedHosts }
        val firstHeard = heard(first.version)
        val group = usable.filter {
            it.origin == VersionOrigin.Debrid && it.key !in quickTried && it.readiness != Readiness.Ready && worthATry(it.readiness, quickTried.size) &&
                (!sameQuality || it.quality == first.version.quality) && heard(it) == firstHeard
        }.take(size)
        return if (group.size > 1) group else emptyList()
    }

    private fun worthATry(readiness: Readiness, tries: Int) = when (readiness) {
        Readiness.Ready -> true
        Readiness.Likely -> tries < MAX_LIKELY_TRIES
        Readiness.Unknown -> tries < MAX_UNKNOWN_TRIES
        Readiness.Preparing, Readiness.NotCached -> false
    }

    /** The version worth fetching behind the film: sharper than the one playing, in a language at least as wanted. */
    fun upgrade(best: List<PlayVersion>, playing: PlayVersion, failed: Set<String>, heard: (PlayVersion) -> Int): PlayVersion? =
        best.firstOrNull { it.key != playing.key && it.key !in failed && it.origin == VersionOrigin.Debrid && heard(it) <= heard(playing) }
            ?.takeIf { VersionRanking.sharper(it, playing) }
}

/** Keeps the name of the size limits in one place for the settings. */
val MaxSize.gigabytes: Int? get() = if (bytes == Long.MAX_VALUE) null else (bytes / 1_000_000_000L).toInt()

val MaxQuality.label: String get() = when (this) {
    MaxQuality.P720 -> "720p"
    MaxQuality.P1080 -> "1080p"
    MaxQuality.P2160 -> "4K"
}
