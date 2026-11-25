package com.sokoul.pc.playback.ui
import com.sokoul.pc.playback.session.Doubt
import com.sokoul.pc.playback.session.Phase
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.PlaybackSession
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.UpgradeOffer
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.session.doubtText
import com.sokoul.pc.playback.sources.OnlineSubtitles
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.glassSurface
import com.sokoul.pc.ui.panelEnter
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.tvFocusable
import com.sokoul.pc.ui.focusIfPresent

/** The sentence for each cause of failure. */
fun failureText(f: PlayFailure): String = when (f) {
    PlayFailure.DeadLink -> tr(Text.FailDeadLink)
    PlayFailure.Blocked -> com.sokoul.pc.data.L("Real-Debrid refuses this file: its rights holder had it blocked.", "Real-Debrid refuse ce fichier : ses ayants droit l'ont fait bloquer.").text()
    is PlayFailure.Refused -> tr(Text.FailRefused, f.code)
    is PlayFailure.ConnectionsFull -> tr(Text.FailConnectionsFull, f.active, f.max)
    is PlayFailure.Cooldown -> tr(Text.FailCooldown, f.seconds)
    is PlayFailure.NotReady -> if (f.percent != null) tr(Text.FailNotReadyPercent, f.percent) else tr(Text.FailNotReady)
    PlayFailure.FakeFile -> tr(Text.FailFakeFile)
    PlayFailure.TimedOut -> tr(Text.FailTimedOut)
    PlayFailure.Cut -> tr(Text.FailCut)
    PlayFailure.DebridLimit -> tr(Text.FailDebridLimit)
    PlayFailure.KeyRefused -> tr(Text.FailKeyRefused)
    PlayFailure.Network -> tr(Text.FailNetwork)
    PlayFailure.WrongLanguage -> tr(Text.FailWrongLanguage)
    PlayFailure.NoSource -> tr(Text.NoSource)
    is PlayFailure.Doubtful -> when (f.doubt) {
        Doubt.OtherWork -> com.sokoul.pc.data.L("The only version left seems to be another work with the same name.", "La seule version restante semble être une autre œuvre du même nom.")
        Doubt.OtherEpisode -> com.sokoul.pc.data.L("The only version left seems to be another episode.", "La seule version restante semble être un autre épisode.")
        Doubt.NotAnEpisode -> com.sokoul.pc.data.L("The only version left seems to be a film, not this episode.", "La seule version restante semble être un film, pas cet épisode.")
        Doubt.WrongLength -> com.sokoul.pc.data.L("The only version left is not as long as this episode.", "La seule version restante n'a pas la durée de cet épisode.")
    }.text()
    PlayFailure.EngineMissing -> tr(Text.EngineMissing)
    PlayFailure.Unknown -> tr(Text.PlaybackFailed)
}

private fun speedLabel(bps: Long): String? = bps.takeIf { it > 0 }?.let { "%.1f %s".format(it / 1e6, tr(Text.MegabytesPerSecond)) }

private fun etaLabel(seconds: Long?): String? = seconds?.let { if (it < 60) tr(Text.SecondsLeft, it) else tr(Text.MinutesLeft, it / 60 + 1) }

/** What is happening, in one line, and how far along it is (0 to 1, null when it cannot be told). */
internal fun phaseLine(phase: Phase, bufferPercent: Int, videoReady: Boolean = false): Pair<String, Float?>? = when (phase) {
    is Phase.Searching -> (if (phase.found > 0) tr(Text.FoundVersions, phase.found) else tr(Text.FindingSource)) to null
    // One steady line while versions are tried: "Version 2 of 793", then "Version 13 of 793" read as the app changing its
    // mind (2026-11-22). Which version plays shows once it plays; the list stays one button away.
    is Phase.Preparing -> preparingText() to null
    is Phase.Downloading -> listOfNotNull(
        preparingText(), tr(Text.DownloadPercent, phase.transfer.percent),
        speedLabel(phase.transfer.speedBps), etaLabel(phase.transfer.etaSeconds),
    ).joinToString(" · ") to phase.transfer.percent / 100f
    is Phase.Tuning -> if (videoReady) null else "${phase.title} · ${tr(Text.LoadingPercent, bufferPercent)}" to bufferPercent / 100f
    is Phase.Opening -> if (videoReady) null else "${preparingText()} · ${tr(Text.LoadingPercent, bufferPercent)}" to bufferPercent / 100f
    else -> null
}

private fun preparingText() = com.sokoul.pc.data.L("Getting the film ready…", "Préparation de la lecture…").text()

/** Why the film went on with another version, in a few words, for the notice that says so. */
internal fun switchReason(f: PlayFailure): String = when (f) {
    PlayFailure.TimedOut -> com.sokoul.pc.data.L("the stream had frozen", "le flux s'était figé")
    PlayFailure.Network, PlayFailure.Cut -> com.sokoul.pc.data.L("the stream was cut", "le flux a été coupé")
    PlayFailure.FakeFile -> com.sokoul.pc.data.L("the file was not the film", "le fichier n'était pas le film")
    is PlayFailure.Doubtful -> com.sokoul.pc.data.L("the file was not the right length", "le fichier n'avait pas la bonne durée")
    else -> com.sokoul.pc.data.L("the previous one failed", "la précédente a échoué")
}.text()

/** What the episode panel reads: seasons, episodes and what was watched of a series. */
interface EpisodeSource {
    suspend fun seasonCount(tvId: Int): Int?
    suspend fun episodes(tvId: Int, season: Int): List<com.sokoul.iptv.data.net.TmdbEpisode>
    /** Progress from 0 to 1 by (season, episode); 1 when finished. */
    suspend fun watched(tvId: Int): Map<Pair<Int, Int>, Float>
}

/** The episodes of the season playing, in the style of the versions panel; another season is one row away. */
@Composable
internal fun EpisodesPanel(source: EpisodeSource, request: PlayRequest, modifier: Modifier = Modifier, onPick: (PlayRequest) -> Unit) {
    val tvId = request.tmdbId
    var season by remember { mutableStateOf(request.season ?: 1) }
    val seasons by androidx.compose.runtime.produceState(0, tvId) { value = runCatching { source.seasonCount(tvId) }.getOrNull() ?: 0 }
    val episodes by androidx.compose.runtime.produceState(emptyList<com.sokoul.iptv.data.net.TmdbEpisode>(), tvId, season) {
        value = runCatching { source.episodes(tvId, season) }.getOrDefault(emptyList())
    }
    val watched by androidx.compose.runtime.produceState(emptyMap<Pair<Int, Int>, Float>(), tvId) { value = runCatching { source.watched(tvId) }.getOrDefault(emptyMap()) }
    val scroll = rememberScrollState()
    val rows = remember { HashMap<Int, FocusRequester>() }
    val first = remember { FocusRequester() }
    // Focus starts on the episode playing, as the versions panel starts on the version playing.
    LaunchedEffect(episodes) {
        if (episodes.isEmpty()) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        val here = if (season == request.season) request.episode?.let(rows::get) else null
        if (here?.focusIfPresent() != true) runCatching { first.requestFocus() }
    }
    com.sokoul.pc.ui.GlassMenu(
        com.sokoul.pc.data.L("Episodes", "Épisodes").text(),
        modifier.keepFocusInside().testTag("player-episodes"), width = 820.dp, maxHeight = 1000.dp, scroll = scroll,
    ) {
        if (seasons > 1) {
            androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 18.dp, bottom = 12.dp, top = 4.dp)) {
                com.sokoul.pc.anime.Choices((1..seasons).map { it to com.sokoul.pc.data.L("Season $it", "Saison $it") }, season, "episodes-season") { season = it }
            }
        }
        episodes.sortedBy { it.episode }.forEachIndexed { i, e ->
            val isCurrent = season == request.season && e.episode == request.episode
            val progress = watched[season to e.episode]
            val detail = listOfNotNull(
                e.runtimeMin?.let { "$it min" },
                when {
                    isCurrent -> com.sokoul.pc.data.L("Playing", "En cours").text()
                    progress != null && progress >= 0.92f -> com.sokoul.pc.data.L("Watched", "Vu").text()
                    progress != null && progress >= 0.01f -> "${(progress * 100).toInt()} %"
                    else -> null
                },
            ).joinToString(" · ").ifEmpty { null }
            PanelRow(
                "${e.episode}. ${e.name ?: com.sokoul.pc.data.L("Episode ${e.episode}", "Épisode ${e.episode}").text()}", detail, "episode-$i",
                rows.getOrPut(e.episode) { if (i == 0) first else FocusRequester() },
                selected = isCurrent,
                image = e.still,
                onClick = {
                    if (!isCurrent) onPick(request.copy(season = season, episode = e.episode, kicker = "S${season}E${e.episode} · ${e.name.orEmpty()}", chooseFirst = false))
                },
            )
        }
        if (episodes.isEmpty()) com.sokoul.pc.ui.PanelNote(com.sokoul.pc.data.L("Loading…", "Chargement…").text())
    }
}

/** A translucent panel with every version and its state, in the same style as the subtitle and audio menus. */
@Composable
internal fun VersionsPanel(session: PlaybackSession, modifier: Modifier = Modifier, onPicked: () -> Unit) {
    SourceBrowser(session, modifier, onPicked)
}

/** A failure in a few words, for a row of the versions list; the player's message keeps the whole sentence. */
internal fun failureBrief(f: PlayFailure): String = when (f) {
    PlayFailure.FakeFile -> com.sokoul.pc.data.L("Not the film", "Pas le film").text()
    PlayFailure.DeadLink -> com.sokoul.pc.data.L("Dead link", "Lien mort").text()
    PlayFailure.Blocked -> com.sokoul.pc.data.L("Blocked by its rights holder", "Bloqué par les ayants droit").text()
    is PlayFailure.Refused -> com.sokoul.pc.data.L("Refused by the host", "Refusé par l'hébergeur").text()
    else -> failureText(f)
}

internal fun stateText(session: PlaybackSession, version: PlayVersion): String {
    val origin = tr(when (version.origin) { VersionOrigin.Debrid -> Text.SourceRealDebrid; VersionOrigin.Iptv -> Text.SourceIptv; VersionOrigin.Web -> Text.SourceWeb })
    val offer = session.upgrade?.takeIf { it.version.key == version.key }
    val state = when {
        session.failures[version.key] != null -> failureBrief(session.failures.getValue(version.key))
        // Its reason in the detail line, so that the title keeps the quality and size to choose by.
        version.rejected != null -> doubtText(version.rejected)
        offer?.transfer != null -> tr(Text.DownloadPercent, offer.transfer.percent)
        offer?.stream != null -> tr(Text.StateReady)
        version.preparingPercent != null -> tr(Text.DownloadPercent, version.preparingPercent)
        version.readiness == Readiness.Ready && version.origin == VersionOrigin.Iptv -> com.sokoul.pc.data.L("Provider link", "Lien fournisseur").text()
        version.readiness == Readiness.Ready -> tr(Text.StateReady)
        version.readiness == Readiness.Likely -> tr(Text.StateLikely)
        version.readiness == Readiness.NotCached -> tr(Text.StateToDownload)
        else -> tr(Text.StateUnknown)
    }
    return "$origin · $state"
}

@Composable
private fun PanelRow(
    title: String,
    detail: String?,
    tag: String,
    requester: FocusRequester,
    selected: Boolean = false,
    failed: Boolean = false,
    image: String? = null,
    onClick: () -> Unit,
) {
    com.sokoul.pc.ui.PanelOption(
        title, Modifier.testTag(tag), subtitle = detail, selected = selected, subtitleError = failed, requester = requester,
        leadingWidth = if (image != null) EPISODE_THUMB_W else 40.dp, leadingHeight = if (image != null) EPISODE_THUMB_H else 40.dp,
        leading = image?.let { url -> { _ -> com.sokoul.pc.ui.Art(url, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) } },
        onClick = onClick,
    )
}

/** An episode's picture in the player's list: 16:9, as on the mock-up. */
private val EPISODE_THUMB_W = 176.dp
private val EPISODE_THUMB_H = 99.dp

/**
 * libVLC hands some track names over as UTF-8 bytes read as Latin-1 ("FranÃ§ais" for "Français"). When the text carries the
 * telltale "Ã" or "Â" and reads back cleanly as UTF-8, it is the repaired text that is shown.
 */
internal fun repairText(text: String): String {
    if (!text.contains('Ã') && !text.contains('Â')) return text
    val bytes = runCatching { text.toByteArray(Charsets.ISO_8859_1) }.getOrNull() ?: return text
    val fixed = String(bytes, Charsets.UTF_8)
    return if (fixed.contains('�') || fixed.length >= text.length) text else fixed
}

/** libVLC's "Track 1 - [French]" as a person would say it: the language, and what else the name tells. */
internal fun trackLabel(raw: String): String {
    val bare = repairText(raw).replace(Regex("""(?i)^(track|piste)\s*\d+\s*(-\s*)?"""), "").trim()
    // "und", "mul", "zxx": codes that say the language is not known; a viewer reads them as noise.
    if (bare.lowercase().trim('[', ']', ' ') in UNKNOWN_LANGUAGE_CODES) return com.sokoul.pc.data.L("Unknown language", "Langue inconnue").text()
    val code = com.sokoul.desktop.player.AudioChoice.languageOf(bare)?.takeIf { it.lowercase() !in UNKNOWN_LANGUAGE_CODES }
        ?: return bare.ifEmpty { raw }
    val extra = bare.replace(Regex("""\[[^]]*]"""), "").replace(Regex("""\s+"""), " ").trim(' ', '-')
    val language = languageName(code)
    return if (extra.isEmpty() || extra.equals(language, ignoreCase = true) || com.sokoul.desktop.player.AudioChoice.languageOf(extra) == code) language else "$language · $extra"
}

private val UNKNOWN_LANGUAGE_CODES = setOf("und", "mul", "zxx", "mis", "unknown")

/**
 * The track heard, as the player's badge says it: its language and its channels ("Français 5.1"), not the file's own
 * words ("Surround 5.1 - [French]"). Null when the track tells neither.
 */
internal fun audioBadgeText(raw: String): String? {
    val label = repairText(raw)
    val code = com.sokoul.desktop.player.AudioChoice.languageOf(label)?.takeIf { it.lowercase() !in UNKNOWN_LANGUAGE_CODES }
    val channels = Regex("""(?<![\d.])([1-9]\.[0-2])(?![\d.])""").find(label)?.value
        ?: when {
            Regex("(?i)st[eé]r[eé]o").containsMatchIn(label) -> "2.0"
            Regex("(?i)\\bmono\\b").containsMatchIn(label) -> "1.0"
            else -> null
        }
    return listOfNotNull(code?.let(::languageName), channels).joinToString(" ").ifEmpty { null }
}

/** "S1 · É1 · L'Ombre du Passé": the episode under the title's logo, null for a film. */
internal fun episodeLine(r: PlayRequest?): String? {
    val s = r?.season ?: return null
    val e = r.episode ?: return null
    val kicker = r.kicker?.trim().orEmpty()
    // The kicker is either the episode's name, or "S1E1 · name" when it was picked in the player.
    val name = (if (Regex("""^S\d+E\d+""").containsMatchIn(kicker)) kicker.substringAfter(" · ", "") else kicker).trim().takeIf { it.isNotEmpty() && it != r.title }
    return listOfNotNull("S$s", com.sokoul.pc.data.L("E$e", "É$e").text(), name).joinToString(" · ")
}

internal fun languageName(code: String): String {
    val name = java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
    return name.ifBlank { code }.replaceFirstChar { it.uppercase() }
}

/** The tracks of the stream, then the subtitles found online, with a way to fix one that is out of sync. */
@Composable
internal fun SubtitlesMenu(
    tracks: List<Pair<Int, String>>,
    currentTrack: Int,
    online: OnlineSubtitles?,
    onTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val first = remember { FocusRequester() }
    // The rows exist only after the first frame of the sheet: asking earlier fails, and OK then falls to the player.
    LaunchedEffect(Unit) {
        repeat(4) {
            androidx.compose.runtime.withFrameNanos { }
            if (first.focusIfPresent()) return@LaunchedEffect
        }
    }
    val active = online?.active
    com.sokoul.pc.ui.GlassMenu(tr(Text.PlayerSubtitles), modifier.keepFocusInside().testTag("player-subtitles-menu"), width = 720.dp, maxHeight = 1000.dp, scroll = scroll) {
        PanelRow(tr(Text.SubtitlesOff), null, "subtitle-off", first, active == null && currentTrack < 0) {
            online?.clear()
            onTrack(-1)
        }
        tracks.filter { it.first >= 0 }.forEach { (id, label) ->
            PanelRow(trackLabel(label), null, "subtitle-track-$id", remember(id) { FocusRequester() }, active == null && id == currentTrack) {
                online?.clear()
                onTrack(id)
            }
        }
        if (online == null) return@GlassMenu
        com.sokoul.pc.ui.PanelSection(tr(Text.SubtitlesOnline))
        if (online.searching) com.sokoul.pc.ui.PanelNote(tr(Text.Searching))
        if (!online.searching && online.results.isEmpty()) com.sokoul.pc.ui.PanelNote(tr(Text.SubtitlesNoneOnline))
        online.results.forEachIndexed { i, r ->
            val detail = when {
                online.loading == r -> tr(Text.Searching)
                r.ref in online.failed -> tr(Text.SubtitleUnavailable)
                else -> listOfNotNull(r.release, tr(Text.SubtitleHearing).takeIf { r.hearingImpaired }).joinToString(" · ").ifEmpty { null }
            }
            PanelRow(languageName(r.language), detail, "subtitle-online-$i", remember(r.ref) { FocusRequester() }, active == r, failed = r.ref in online.failed) {
                onTrack(-1)
                online.choose(r)
            }
        }
        if (active != null) {
            com.sokoul.pc.ui.PanelSection(tr(Text.SubtitleDelay, "%+.1f".format(online.delayMs / 1000f)))
            PanelRow(tr(Text.SubtitleEarlier), null, "subtitle-earlier", remember { FocusRequester() }) { online.shift(-SHIFT_MS) }
            PanelRow(tr(Text.SubtitleLater), null, "subtitle-later", remember { FocusRequester() }) { online.shift(SHIFT_MS) }
        }
    }
}

private const val SHIFT_MS = 500L

/** A failure of the file itself, which the same version gives again: not the film, a dead link, a refusal. */
internal fun retryUseless(f: PlayFailure): Boolean = com.sokoul.pc.playback.sources.SourceRules.blamesHost(f)

/** The failure stays inside the player, with what can be done about it. */
@Composable
internal fun FailurePanel(session: PlaybackSession, failure: PlayFailure, onVersions: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(failure) { repeat(4) { androidx.compose.runtime.withFrameNanos { }; if (first.focusIfPresent()) return@LaunchedEffect } }
    // As on the mock-up: a small card, "Playback unavailable" and the reason under it, then what helps first.
    Column(
        modifier.widthIn(min = 720.dp, max = 1240.dp).panelEnter().glassSurface(RoundedCornerShape(com.sokoul.pc.theme.Corners.Modal), com.sokoul.pc.theme.Corners.Modal).padding(horizontal = 56.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Label(
            if (session.live != null) com.sokoul.pc.data.L("Channel unavailable", "Chaîne indisponible").text() else com.sokoul.pc.data.L("Playback unavailable", "Lecture indisponible").text(),
            com.sokoul.pc.theme.PanelType.Title, modifier = Modifier.testTag("player-error-title"), maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        Label(failureText(failure), com.sokoul.pc.theme.PanelType.Callout, com.sokoul.pc.theme.Palette.Secondary, Modifier.widthIn(max = 1120.dp).testTag("player-error"), maxLines = 3)
        Spacer(Modifier.height(36.dp))
        // Wide enough for a focused button grown by its scale not to touch the next one.
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            if (session.live != null && failure !is PlayFailure.EngineMissing) {
                PillButton(tr(Text.Retry), session::retry, Modifier.testTag("failure-retry"), first)
                PillButton(tr(Text.NextChannel), session::nextVersion, Modifier.testTag("failure-next"))
                PillButton(tr(Text.Close), onClose, Modifier.testTag("failure-close"))
            } else if (failure is PlayFailure.Doubtful) {
                // Nothing played on its own: the viewer decides, with the remote as well as the mouse.
                // The focus starts on the safe choice: one press of OK never plays a doubtful version.
                PillButton(com.sokoul.pc.data.L("Play Anyway", "Lire quand même").text(), session::playAnyway, Modifier.testTag("failure-anyway"))
                PillButton(com.sokoul.pc.data.L("Choose Another Version", "Choisir une autre version").text(), onVersions, Modifier.testTag("failure-versions"), first)
                PillButton(tr(Text.Retry), session::retry, Modifier.testTag("failure-retry"))
                PillButton(tr(Text.Close), onClose, Modifier.testTag("failure-close"))
            } else if (failure !is PlayFailure.EngineMissing && retryUseless(failure)) {
                // The file itself is wrong (not the film, dead link, refused): retrying it gives the same answer, so the
                // card offers another version first. Back closes, as everywhere.
                PillButton(com.sokoul.pc.data.L("Choose Another Version", "Choisir une autre version").text(), onVersions, Modifier.testTag("failure-versions"), first)
                if (session.versions.size > 1) PillButton(tr(Text.NextVersion), session::nextVersion, Modifier.testTag("failure-next"))
            } else if (failure !is PlayFailure.EngineMissing) {
                PillButton(tr(Text.Retry), session::retry, Modifier.testTag("failure-retry"), first)
                PillButton(com.sokoul.pc.data.L("Choose Another Version", "Choisir une autre version").text(), onVersions, Modifier.testTag("failure-versions"))
            } else {
                PillButton(tr(Text.Close), onClose, Modifier.testTag("failure-close"), first)
            }
        }
    }
}

/** A short bar under the title line: a thin progress when it can be told. */
@Composable
internal fun StatusBar(line: String, fraction: Float?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Label(line, Type.Meta.copy(fontWeight = FontWeight.SemiBold), Palette.OnBackground, Modifier.testTag("player-step"), maxLines = 1)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3A3A3C))) {
            if (fraction != null) Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Palette.OnBackground))
        }
    }
}

/** The offer of a better version while the film plays. */
@Composable
internal fun UpgradeBanner(offer: UpgradeOffer, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, requester: androidx.compose.ui.focus.FocusRequester) {
    Row(
        modifier.panelEnter(com.sokoul.pc.ui.PanelEntry.FromBelow).glassSurface(RoundedCornerShape(50), 40.dp).padding(start = 34.dp, end = 14.dp, top = 12.dp, bottom = 12.dp).testTag("player-upgrade"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        Label(tr(Text.UpgradeReady, offer.version.summary()), com.sokoul.pc.theme.PanelType.Callout, maxLines = 1)
        PillButton(tr(Text.Switch), onAccept, Modifier.testTag("upgrade-accept"), requester)
        PillButton(tr(Text.NotNow), onDismiss, Modifier.testTag("upgrade-dismiss"))
    }
}

/**
 * The version playing has none of the wanted languages: the viewer is told which language plays and chooses. Nothing reopens on its
 * own. Same glass capsule and place as [UpgradeBanner]; focus starts on keeping the film going, the likeliest wish.
 */
@Composable
internal fun LanguageNoticeBanner(notice: com.sokoul.pc.playback.session.LanguageNotice, onKeep: () -> Unit, onVersions: () -> Unit, modifier: Modifier = Modifier, requester: androidx.compose.ui.focus.FocusRequester, subtitled: Boolean = false) {
    // Inside a sentence, French, Spanish, Italian and Portuguese write language names in lower case ("en italiano").
    val locale = java.util.Locale.getDefault()
    val inSentence = { name: String -> if (locale.language in setOf("fr", "es", "it", "pt")) name.lowercase(locale) else name }
    val language = inSentence(com.sokoul.desktop.player.AudioChoice.languageOf(notice.heard)?.let(::languageName) ?: trackLabel(notice.heard))
    val wanted = inSentence(languageName(notice.wanted))
    Row(
        modifier.panelEnter(com.sokoul.pc.ui.PanelEntry.FromBelow).glassSurface(RoundedCornerShape(50), 40.dp).padding(start = 34.dp, end = 14.dp, top = 12.dp, bottom = 12.dp).testTag("player-language-notice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
    ) {
        // "No Italian audio · Playing in English", for whichever language is wanted and heard.
        Label(
            if (subtitled) com.sokoul.pc.data.L("No $wanted audio · Playing in $language, $wanted subtitles", "Pas de piste en $wanted · Lecture en $language, sous-titres en $wanted").text()
            else com.sokoul.pc.data.L("No $wanted audio · Playing in $language", "Pas de piste en $wanted · Lecture en $language").text(),
            com.sokoul.pc.theme.PanelType.Callout, maxLines = 1,
        )
        PillButton(com.sokoul.pc.data.L("Keep Watching", "Continuer").text(), onKeep, Modifier.testTag("language-keep"), requester)
        PillButton(com.sokoul.pc.data.L("See Versions", "Voir les versions").text(), onVersions, Modifier.testTag("language-versions"))
    }
}

/** Keeps the whole screen dark behind a panel. */
@Composable
internal fun Scrim() = Box(Modifier.fillMaxSize().background(com.sokoul.pc.theme.Glass.Scrim))

/** Inside a panel, the arrows stay in the panel: what lies behind cannot be reached by accident. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.keepFocusInside(): Modifier = this
    .focusProperties { exit = { FocusRequester.Cancel } }
    .focusGroup()
