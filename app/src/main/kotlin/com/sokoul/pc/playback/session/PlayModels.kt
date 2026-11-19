package com.sokoul.pc.playback.session
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.HdrFormat
import com.sokoul.iptv.domain.VideoQuality
import kotlinx.coroutines.flow.Flow

data class PlayRequest(
    val kind: String,
    val tmdbId: Int,
    val season: Int?,
    val episode: Int?,
    val title: String,
    val kicker: String?,
    val image: String?,
    /** Opens on the list of versions and starts nothing, whatever the start preference. */
    val chooseFirst: Boolean = false,
) {
    val resumeKey get() = Resume.key(kind, tmdbId, season, episode)
}

/** [Web]: a link found by the Sokoul server (Mainapi) on a streaming site. */
enum class VersionOrigin { Debrid, Iptv, Web }

/** What is known about a version before trying it. Only [Ready] is certain: Real-Debrid no longer reports its cache. */
enum class Readiness { Ready, Likely, Unknown, Preparing, NotCached }

/** A Real-Debrid download as it progresses. */
data class Transfer(val percent: Int, val speedBps: Long = 0, val seeders: Int = 0, val bytes: Long = 0) {
    /** Seconds left at the current speed, null when it cannot be told. */
    val etaSeconds: Long? get() = if (speedBps <= 0 || bytes <= 0) null else ((bytes * (100 - percent).coerceAtLeast(0) / 100) / speedBps)
}

/** One way to watch the title. [open] does the work lazily, so a version that is never reached costs nothing. */
class PlayVersion(
    val key: String,
    val origin: VersionOrigin,
    val name: String,
    val language: AudioLanguage? = null,
    val quality: VideoQuality? = null,
    val hdr: HdrFormat? = null,
    val rip: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val readiness: Readiness = Readiness.Unknown,
    val preparingPercent: Int? = null,
    val providerHost: String? = null,
    /** Lower is better among otherwise equal versions: Remux before Blu-ray before WEB. */
    val ripRank: Int = 2,
    val frenchVariantRank: Int = 1,
    /** Anime: why its identity contradicts the request. Such a version is listed, never started on its own. */
    val rejected: Doubt? = null,
    /** Anime: 0 when its file says it is the episode, 1 when it cannot be read, 2 when something makes it doubtful. */
    val confidence: Int = 0,
    /** Kept by the SeaDex community as the best release of the anime. */
    val community: Boolean = false,
    /** A version of an anime linked by its ids: its length is judged more strictly. */
    val anime: Boolean = false,
    /** ISO audio codes explicitly announced by this release, distinct from player-verified tracks. */
    val announcedAudio: List<String> = emptyList(),
    /** A foreign dub beside the original and no French ("Dual Audio", "spa, eng"): not the original alone. */
    val foreignDub: Boolean = false,
    /** Undoes what [open] added to an account, when this version got its link but another one is played. */
    val discard: suspend () -> Unit = {},
    val open: suspend (keep: Boolean, onProgress: (Transfer) -> Unit) -> StreamRequest,
) {
    /** "1080p · VF · WEB · 8.4 GB", from what is known. */
    fun summary(): String = listOfNotNull(
        if (community) "★ SeaDex" else null,
        qualityLabel(quality), hdrLabel(hdr), language?.name, rip, sizeLabel(sizeBytes),
    ).joinToString(" · ").ifEmpty { name }
}

/** Why a version is not the episode asked for. */
enum class Doubt { OtherWork, OtherEpisode, NotAnEpisode, WrongLength }

fun doubtText(d: Doubt): String = when (d) {
    Doubt.OtherWork -> com.sokoul.pc.data.L("Another work with the same name", "Une autre œuvre du même nom").text()
    Doubt.OtherEpisode -> com.sokoul.pc.data.L("Another episode", "Un autre épisode").text()
    Doubt.NotAnEpisode -> com.sokoul.pc.data.L("A film, not this episode", "Un film, pas cet épisode").text()
    Doubt.WrongLength -> com.sokoul.pc.data.L("Its length is not this episode's", "Sa durée n'est pas celle de l'épisode").text()
}

fun qualityLabel(q: VideoQuality?): String? = when (q) {
    VideoQuality.UHD_4K -> "4K"
    VideoQuality.FHD_1080 -> "1080p"
    VideoQuality.HD_720 -> "720p"
    VideoQuality.SD -> "SD"
    null -> null
}

fun hdrLabel(h: HdrFormat?): String? = when (h) {
    HdrFormat.HDR10 -> "HDR"
    HdrFormat.DOLBY_VISION -> "Dolby Vision"
    null -> null
}

fun sizeLabel(bytes: Long?): String? = bytes?.takeIf { it > 0 }?.let {
    if (it < 1_000_000_000L) "%.0f MB".format((it / 1e6).coerceAtLeast(1.0)) else "%.1f GB".format(it / 1e9)
}

/** Why a version did not play. Each cause has its own message and its own reaction. */
sealed interface PlayFailure {
    /** The link no longer exists. */
    data object DeadLink : PlayFailure
    /** Real-Debrid took the file down for copyright (code 35): it will not come back tomorrow. */
    data object Copyright : PlayFailure
    data class Refused(val code: Int) : PlayFailure
    /** Every IPTV connection is in use, on this account. */
    data class ConnectionsFull(val active: Int, val max: Int) : PlayFailure
    /** The provider refused recently: asking again too soon can get the account blocked. */
    data class Cooldown(val seconds: Long) : PlayFailure
    data class NotReady(val percent: Int?) : PlayFailure
    /** A short placeholder video or a file that is no film. */
    data object FakeFile : PlayFailure
    data object TimedOut : PlayFailure
    /** The stream ended long before the end of the film. */
    data object Cut : PlayFailure
    /** Real-Debrid's limits: traffic, or too many downloads at once. */
    data object DebridLimit : PlayFailure
    data object KeyRefused : PlayFailure
    data object Network : PlayFailure
    /** The file is not in the language wanted, whatever its name announced. */
    data object WrongLanguage : PlayFailure
    data object NoSource : PlayFailure
    /** Only versions whose identity is doubtful are left: none is played unless the viewer says so. */
    data class Doubtful(val doubt: Doubt) : PlayFailure
    data object EngineMissing : PlayFailure
    data object Unknown : PlayFailure

    /** Nothing from the same origin can work for now, so it is pointless to go through them all. */
    val blocksOrigin: Boolean get() = this is KeyRefused || this is ConnectionsFull || this is Cooldown || this is DebridLimit
}

class PlayFailureException(val failure: PlayFailure) : Exception(failure.toString())

/** A piece of music to play in a list. */
data class Clip(val title: String, val slug: String, val url: String)

/** A live channel, ready to open. */
data class LiveChannel(
    val key: String,
    val title: String,
    val number: Int?,
    val logo: String?,
    val stream: StreamRequest,
    val program: suspend () -> com.sokoul.desktop.data.LiveLogic.NowNext = { com.sokoul.desktop.data.LiveLogic.NowNext(null, null) },
    /** What it shows in the coming hours. */
    val schedule: suspend () -> List<com.sokoul.desktop.data.Program> = { emptyList() },
    /** The same channel in every quality the provider has, itself included, sharpest first. */
    val variants: () -> List<LiveChannel> = { emptyList() },
    val recording: () -> LiveRecording? = { null },
    /** Rechecked at preview and playback time so stale guide rows cannot bypass parental restrictions. */
    val canOpen: () -> Boolean = { true },
    /** Provider-declared finite replay support. */
    val capabilities: LiveCapabilities = LiveCapabilities(),
    /** Builds a replay stream only when the source metadata and programme window are valid. */
    val replay: suspend (com.sokoul.desktop.data.Program) -> StreamRequest? = { null },
)

data class LiveCapabilities(val catchupDays: Int = 0) {
    val catchup: Boolean get() = catchupDays > 0
}

/** Everything the player shows comes from this single state. */
sealed interface Phase {
    data object Idle : Phase
    data class Searching(val found: Int) : Phase
    /** The list of versions is open and nothing starts until one is picked. */
    data class Choosing(val failure: PlayFailure?) : Phase
    data class Preparing(val version: PlayVersion, val position: Int, val total: Int) : Phase
    data class Downloading(val version: PlayVersion, val transfer: Transfer, val position: Int, val total: Int) : Phase
    data class Opening(val version: PlayVersion, val position: Int, val total: Int) : Phase
    /** A live channel being opened. */
    data class Tuning(val title: String) : Phase
    data class Playing(val version: PlayVersion?) : Phase
    data class Failed(val failure: PlayFailure, val version: PlayVersion?) : Phase
    data object Finished : Phase
}

/** A better version being fetched behind the film, offered once it can play. */
data class UpgradeOffer(val version: PlayVersion, val transfer: Transfer?, val stream: StreamRequest?)

/** Results so far: more arrive as each search service answers. */
enum class SourceNotice { AnimeSamaVipRequired, AnimeSamaUnavailable }

data class SearchUpdate(val versions: List<PlayVersion>, val done: Boolean, val problem: PlayFailure? = null, val notices: List<SourceNotice> = emptyList())

/** Lists the versions of a title as they are found. [refresh] ignores what earlier searches remembered. */
fun interface SourceFinder {
    fun find(request: PlayRequest, refresh: Boolean): Flow<SearchUpdate>

    /** Searches ahead, opening nothing: what would play next is then known at once. */
    fun prefetch(request: PlayRequest) {}
}

/** Local recorder binding; provider archive support is independent. */
data class LiveRecording(
    val recorder: com.sokoul.desktop.data.DesktopRecorder,
    val playlist: com.sokoul.desktop.data.Playlist,
    val channel: com.sokoul.desktop.data.Channel,
    val canPlay: (com.sokoul.desktop.data.Recording) -> Boolean = { false },
)

/** The active version has no track in [wanted] (ISO 639-1); [heard] is the label of the track playing. */
data class LanguageNotice(val wanted: String, val heard: String)
