package com.sokoul.pc.playback.ui
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.session.PlaybackSession
import com.sokoul.pc.playback.session.Readiness
import com.sokoul.pc.playback.session.SourceNotice
import com.sokoul.pc.playback.session.VersionOrigin
import com.sokoul.pc.playback.session.hdrLabel
import com.sokoul.pc.playback.session.qualityLabel
import com.sokoul.pc.playback.session.sizeLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.pc.data.L
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.*

internal enum class SourceLanguageFilter(val audio: AudioLanguage?) {
    All(null), French(AudioLanguage.VF), Multi(AudioLanguage.MULTI), Subtitled(AudioLanguage.VOSTFR), Original(AudioLanguage.VO), Unknown(null);
    fun keeps(language: AudioLanguage?) = when (this) { All -> true; Unknown -> language == null; else -> audio == language }
    fun label() = when (this) {
        All -> L("All languages", "Toutes langues").text()
        Unknown -> L("Unspecified", "Non précisée").text()
        else -> audio!!.name
    }
}

internal enum class SourceEligibility {
    Compatible, Excluded, Dead;
    /** A dead link shows only among the dead ones, whatever else is said of it. */
    fun keeps(version: PlayVersion, dead: Boolean = false) = when (this) {
        Dead -> dead
        Excluded -> !dead && version.rejected != null
        Compatible -> !dead && version.rejected == null
    }
}

internal fun sourceName(name: String): String = name.replace(Regex("https?://\\S+"), "")
    .lineSequence().filter { it.isNotBlank() }.joinToString(" · ").trim().take(180)

internal fun sourceOriginLabel(origin: VersionOrigin): String = when (origin) {
    VersionOrigin.Debrid -> "Real-Debrid"
    VersionOrigin.Iptv -> "IPTV"
    VersionOrigin.Web -> "Web"
}

internal fun sourceAddonLabel(host: String?): String? = when {
    host.isNullOrBlank() -> null
    // A relay on the Sokoul server (an address, localhost) says nothing about the source to a viewer.
    host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}(:\d+)?""")) || host.startsWith("localhost") || host.startsWith("[") -> null
    host.contains("torrentio", ignoreCase = true) -> "Torrentio"
    host.contains("comet", ignoreCase = true) -> "Comet"
    else -> host.substringBefore('/').take(36)
}

/** "FR", "EN", "ES" as "Français · Anglais · Espagnol" in the interface language; null when the name declares none. */
internal fun audioLanguagesLabel(codes: List<String>): String? =
    codes.distinct().map { languageName(it.lowercase()) }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

internal fun isAccountReady(version: PlayVersion): Boolean =
    version.origin == VersionOrigin.Debrid && version.readiness == Readiness.Ready && version.rejected == null

@Composable
internal fun SourceBrowser(session: PlaybackSession, modifier: Modifier = Modifier, onPicked: () -> Unit) {
    SourceBrowserContent(session.versions, session.current?.key, session.searching, modifier,
        searchRevision = session.sourceSearchRevision, notices = session.sourceNotices,
        languageOf = session::sourceLanguage, verified = session::sourceLanguageVerified,
        wanted = session::holdsWantedLanguage,
        stateOf = { stateText(session, it) }, onRefresh = session::refresh,
        dead = session::deadLink, onRetryDead = session::retryDeadLinks,
        onChoose = { session.choose(it); onPicked() })
}

/** Fixed filters and lazy source rows keep all candidates reachable without composing hundreds of cards. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SourceBrowserContent(
    versions: List<PlayVersion>, currentKey: String?, searching: Boolean,
    modifier: Modifier = Modifier,
    searchRevision: Int = 0,
    notices: List<SourceNotice> = emptyList(),
    languageOf: (PlayVersion) -> AudioLanguage? = { it.language },
    verified: (PlayVersion) -> Boolean = { false },
    wanted: (PlayVersion) -> Boolean = { true },
    stateOf: (PlayVersion) -> String = { sourceOriginLabel(it.origin) },
    onRefresh: () -> Unit = {}, onChoose: (PlayVersion) -> Unit = {},
    dead: (PlayVersion) -> Boolean = { false }, onRetryDead: () -> Unit = {},
) {
    var origin by remember(searchRevision) { mutableStateOf<VersionOrigin?>(null) }
    var language by remember(searchRevision) { mutableStateOf(SourceLanguageFilter.All) }
    var audioCode by remember(searchRevision) { mutableStateOf<String?>(null) }
    var readyOnly by remember(searchRevision) { mutableStateOf(false) }
    var wantedOnly by remember(searchRevision) { mutableStateOf(false) }
    var eligibility by remember(searchRevision) { mutableStateOf(SourceEligibility.Compatible) }
    // Worked out once per list and filters, not at every recomposition: the panel recomposes with the player (its
    // position, its state), and filtering and counting 548 versions several times over, each asking the preferences
    // again, made the list stutter on the TV (53 ms frames, 2026-11-18). Identity maps: a version's hash walks all its
    // fields.
    val languages = remember(versions, currentKey) { identityMap(versions, languageOf) }
    val wantedOf = remember(versions, currentKey) { identityMap(versions, wanted) }
    val isWanted = { v: PlayVersion -> wantedOf[v] ?: wanted(v) }
    // Read at each recomposition: a link found dead while the panel is open leaves the list at once. One map lookup each.
    val deadKeys = versions.mapNotNullTo(HashSet()) { v -> v.key.takeIf { dead(v) } }
    val deadCount = deadKeys.size
    val liveCount = versions.size - deadCount
    // The last dead link tried again: nothing left to show here.
    LaunchedEffect(deadCount) { if (deadCount == 0 && eligibility == SourceEligibility.Dead) eligibility = SourceEligibility.Compatible }
    val accountReady = ::isAccountReady
    val eligible = remember(versions, eligibility, deadKeys) { versions.filter { eligibility.keeps(it, it.key in deadKeys) } }
    val excludedCount = remember(versions, deadKeys) { versions.count { it.rejected != null && it.key !in deadKeys } }
    val byOrigin = remember(eligible, origin, readyOnly) { eligible.filter { (origin == null || it.origin == origin) && (!readyOnly || accountReady(it)) } }
    val visible = remember(byOrigin, wantedOnly, language, audioCode, languages) {
        byOrigin.filter { (!wantedOnly || isWanted(it)) && language.keeps(languages[it]) && (audioCode == null || audioCode in it.announcedAudio) }
    }
    val readyCount = remember(eligible) { eligible.count(accountReady) }
    val originCounts = remember(eligible) { VersionOrigin.entries.associateWith { o -> eligible.count { it.origin == o } } }
    val wantedCount = remember(byOrigin, wantedOf) { byOrigin.count(isWanted) }
    val languageCounts = remember(byOrigin, languages) { SourceLanguageFilter.entries.associateWith { f -> byOrigin.count { f.keeps(languages[it]) } } }
    val audioCounts = remember(byOrigin) {
        byOrigin.flatMap { it.announcedAudio }.distinct().sorted().map { code ->
            Triple(code, java.util.Locale.forLanguageTag(code.lowercase()).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }, byOrigin.count { code in it.announcedAudio })
        }
    }
    val first = remember { FocusRequester() }
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { withFrameNanos { }; withFrameNanos { }; runCatching { first.requestFocus() } }
    LaunchedEffect(searchRevision, origin, language, audioCode, readyOnly, wantedOnly, eligibility) { state.scrollToItem(0) }
    val total = if (eligibility == SourceEligibility.Dead) deadCount else liveCount
    TvPanel(when (eligibility) {
            SourceEligibility.Compatible -> L("Playback sources", "Sources de lecture")
            SourceEligibility.Excluded -> L("Excluded sources", "Sources écartées")
            SourceEligibility.Dead -> L("Dead links", "Liens morts")
        }.text(),
        L("${visible.size} of $total versions · ${if (searching) "Searching…" else "Search complete"}",
            "${visible.size} sur $total versions · ${if (searching) "Recherche en cours…" else "Recherche terminée"}").text(),
        modifier.testTag("player-versions"), width = 860.dp, height = 1000.dp) {
        notices.forEach { notice ->
            TvPanelNotice(when (notice) {
                SourceNotice.AnimeSamaVipRequired -> L("Anime-Sama: server key requires VIP access", "Anime-Sama : cl\u00e9 serveur sans acc\u00e8s VIP").text()
                SourceNotice.AnimeSamaUnavailable -> L("Anime-Sama: no resolved stream available", "Anime-Sama : aucun flux r\u00e9solu disponible").text()
            })
        }
        // Actions and filters as capsules on the panel's text edge; the list below keeps all the height it can.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PanelMetrics.RowPad, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelChip(L("Refresh", "Actualiser").text(), requester = first, icon = Icons.Filled.Refresh, onClick = onRefresh)
            if (eligibility != SourceEligibility.Compatible) {
                PanelChip(L("Compatible · ${liveCount - excludedCount}", "Compatibles · ${liveCount - excludedCount}").text(), Modifier.testTag("versions-compatible"), icon = Icons.Filled.FilterList) {
                    eligibility = SourceEligibility.Compatible
                }
            }
            if (eligibility == SourceEligibility.Dead) {
                PanelChip(L("Retry · $deadCount", "Réessayer · $deadCount").text(), Modifier.testTag("versions-retry-dead"), icon = Icons.Filled.Replay) {
                    onRetryDead()
                    eligibility = SourceEligibility.Compatible
                }
            }
            if (excludedCount > 0 && eligibility != SourceEligibility.Excluded) {
                PanelChip(L("Excluded · $excludedCount", "Écartées · $excludedCount").text(), Modifier.testTag("versions-excluded"), icon = Icons.Filled.FilterList) {
                    eligibility = SourceEligibility.Excluded
                    readyOnly = false
                }
            }
            // Hidden, not dropped: a link dead a minute ago can play now.
            if (deadCount > 0 && eligibility != SourceEligibility.Dead) {
                PanelChip(if (deadCount == 1) L("1 dead link", "1 lien mort").text() else L("$deadCount dead links", "$deadCount liens morts").text(),
                    Modifier.testTag("versions-dead"), icon = Icons.Filled.LinkOff) {
                    eligibility = SourceEligibility.Dead
                    readyOnly = false
                }
            }
            if (origin != null || language != SourceLanguageFilter.All || audioCode != null || readyOnly || wantedOnly || eligibility != SourceEligibility.Compatible) {
                PanelChip(L("Reset filters", "Réinitialiser").text(), icon = Icons.Filled.Close) { origin = null; language = SourceLanguageFilter.All; audioCode = null; readyOnly = false; wantedOnly = false; eligibility = SourceEligibility.Compatible }
            }
            SourceChip(L("All providers", "Tous fournisseurs").text() + " · ${eligible.size}", origin == null, "versions-filter-all") { origin = null }
            if (eligibility == SourceEligibility.Compatible) SourceChip(L("In account", "Dans le compte").text() + " · $readyCount", readyOnly, "versions-filter-ready") { readyOnly = !readyOnly }
            VersionOrigin.entries.forEach { item ->
                val count = originCounts[item] ?: 0
                if (count > 0) SourceChip("${sourceOriginLabel(item)} · $count", origin == item, "versions-filter-${item.name.lowercase()}") { origin = item }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PanelMetrics.RowPad, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceChip(L("Your languages", "Vos langues").text() + " · $wantedCount", wantedOnly, "versions-language-wanted") { wantedOnly = !wantedOnly }
            SourceLanguageFilter.entries.forEach { item ->
                val count = languageCounts[item] ?: 0
                if (count > 0 || item == language || item == SourceLanguageFilter.All) {
                    SourceChip("${item.label()} · $count", item == language && audioCode == null, "versions-language-${item.name.lowercase()}") { language = item; audioCode = null }
                }
            }
            // Announced audio tracks follow the language filters on the same line.
            audioCounts.forEach { (code, name, count) ->
                SourceChip("$name · $count", audioCode == code, "versions-audio-${code.lowercase()}") {
                    language = SourceLanguageFilter.All
                    audioCode = if (audioCode == code) null else code
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = state, contentPadding = PaddingValues(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (visible.isEmpty()) item {
                TvPanelNotice(if (searching) L("Sources are still arriving…", "Les sources arrivent encore…").text()
                    else L("No version matches these filters. Reset them or refresh the sources.", "Aucune version ne correspond. Réinitialisez les filtres ou actualisez les sources.").text())
            }
            itemsIndexed(visible, key = { _, v -> "${v.origin}:${v.key}" }) { index, version ->
                val heard = languages[version]
                // Only what helps to choose: a language heard in the file, or none at all. "Announced" is the usual case
                // and said nothing.
                val evidence = if (heard == null) L("Audio not identified", "Audio non identifié").text() else if (verified(version)) L("Audio verified", "Audio vérifié").text() else null
                val selected = version.key == currentKey
                PanelCard(Modifier.testTag("version-$index"), selected = selected, onClick = { onChoose(version) }) { focused ->
                    val ink = if (focused) Palette.OnFocus else Palette.OnBackground
                    val soft = if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Secondary
                    val icon = when {
                        version.rejected != null -> Icons.Filled.Warning
                        version.origin == VersionOrigin.Iptv -> Icons.Filled.LiveTv
                        version.origin == VersionOrigin.Debrid -> Icons.Filled.CloudQueue
                        else -> Icons.Filled.Public
                    }
                    Icon(icon, null, Modifier.size(30.dp), tint = if (version.rejected != null && !focused) Palette.Error else soft)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PanelBadge(heard?.name ?: "?", strong = true, onFocus = focused)
                            listOfNotNull(qualityLabel(version.quality), hdrLabel(version.hdr), "SeaDex".takeIf { version.community }).forEach { PanelBadge(it, onFocus = focused) }
                            sizeLabel(version.sizeBytes)?.let { Label(it, com.sokoul.pc.theme.PanelType.Option, ink, maxLines = 1) }
                        }
                        // Every audio language the name declares, in words and never cut: a MULTI may hold ten of them.
                        audioLanguagesLabel(version.announcedAudio)?.let { Label(it, com.sokoul.pc.theme.PanelType.Option, ink, modifier = Modifier.testTag("version-$index-audio")) }
                        // The TV shows no search service names (Comet, Torrentio): they mean nothing from the sofa.
                        val addon = if (com.sokoul.pc.Platform.IS_TV) null else sourceAddonLabel(version.providerHost)
                        Label(listOfNotNull(addon, stateOf(version), evidence).joinToString(" · "), com.sokoul.pc.theme.PanelType.Callout, soft, maxLines = 2)
                        Label(sourceName(version.name), com.sokoul.pc.theme.PanelType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), soft, maxLines = if (com.sokoul.pc.Platform.IS_TV) 1 else 2)
                    }
                    if (selected) Icon(Icons.Filled.Check, L("Playing", "En lecture").text(), Modifier.size(30.dp), tint = ink)
                }
            }
        }
    }
}

private fun <T> identityMap(versions: List<PlayVersion>, of: (PlayVersion) -> T): java.util.IdentityHashMap<PlayVersion, T> =
    java.util.IdentityHashMap<PlayVersion, T>(versions.size * 2).apply { versions.forEach { put(it, of(it)) } }

@Composable
private fun SourceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    PanelChip(label, Modifier.testTag(tag), selected = selected, onClick = onClick)
}
