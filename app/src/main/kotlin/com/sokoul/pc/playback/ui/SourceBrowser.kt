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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.iptv.domain.VideoQuality
import com.sokoul.pc.data.L
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.*
import com.sokoul.pc.ui.focusIfPresent

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

/** The versions panel shows wanted languages first and exposes 4K across languages when available. */
internal enum class VersionsView { ForYou, All, UltraHd, French, Multi, Subtitled, Original, Unknown;
    fun keeps(language: AudioLanguage?, wanted: Boolean, quality: VideoQuality?) = when (this) {
        ForYou -> wanted
        All -> true
        UltraHd -> quality == VideoQuality.UHD_4K
        French -> language == AudioLanguage.VF
        Multi -> language == AudioLanguage.MULTI
        Subtitled -> language == AudioLanguage.VOSTFR
        Original -> language == AudioLanguage.VO
        Unknown -> language == null
    }
    fun label() = when (this) {
        ForYou -> L("For you", "Pour vous").text()
        All -> L("All", "Toutes").text()
        UltraHd -> "4K"
        French -> "VF"
        Multi -> "MULTI"
        Subtitled -> "VOSTFR"
        Original -> "VO"
        Unknown -> L("Unspecified", "Non précisée").text()
    }
}

/** What the list holds, in order: section titles and versions. */
private sealed interface VersionEntry {
    val key: String
    data class Head(val label: String) : VersionEntry { override val key get() = "head:$label" }
    data class Item(val version: PlayVersion, val index: Int) : VersionEntry { override val key get() = "${version.origin}:${version.key}" }
}

private val QUALITY_ORDER = listOf(com.sokoul.iptv.domain.VideoQuality.UHD_4K, com.sokoul.iptv.domain.VideoQuality.FHD_1080, com.sokoul.iptv.domain.VideoQuality.HD_720, com.sokoul.iptv.domain.VideoQuality.SD, null)

/** The languages of a version in words: what its name announces, else what its tag says. */
private fun spokenLanguages(version: PlayVersion, heard: AudioLanguage?): String =
    audioLanguagesLabel(version.announcedAudio) ?: when (heard) {
        AudioLanguage.VF -> languageName("fr")
        AudioLanguage.MULTI -> L("Several languages", "Plusieurs langues").text()
        AudioLanguage.VOSTFR -> L("Original, French subtitles", "VO sous-titrée français").text()
        AudioLanguage.VO -> L("Original version", "Version originale").text()
        null -> L("Language not stated", "Langue non précisée").text()
    }

/**
 * The versions panel (Figma "V3 · Lecteur VOD · TV · versions"): one row of language segments, the version playing,
 * the recommended one, then the others by sharpness. Each row says the sharpness and the picture, the languages, then
 * size · service · state; the file's name shows only under focus. Lazy, so hundreds of versions stay cheap.
 */
@Composable
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
    var chosenView by remember(searchRevision) { mutableStateOf<VersionsView?>(null) }
    var origin by remember(searchRevision) { mutableStateOf<VersionOrigin?>(null) }
    var excludedView by remember(searchRevision) { mutableStateOf(false) }
    // Alexis, PR #10: a link found dead leaves the list while it is remembered, behind a counter; it can play again later
    // (The Handmaid's Tale 4x05), so it is hidden, not dropped, and "Retry" puts them all back in the running.
    var deadView by remember(searchRevision) { mutableStateOf(false) }
    // Read at each recomposition: a link found dead while the panel is open leaves the list at once. One map lookup each.
    val deadKeys = versions.mapNotNullTo(HashSet()) { v -> v.key.takeIf { dead(v) } }
    val deadCount = deadKeys.size
    LaunchedEffect(deadCount) { if (deadCount == 0) deadView = false }
    val aside = excludedView || deadView
    // Worked out once per list, not at every recomposition: the panel recomposes with the player, and filtering 548
    // versions several times over made the list stutter on the TV (53 ms frames, 2026-11-18). Identity maps: a
    // version's hash walks all its fields.
    val languages = remember(versions, currentKey) { identityMap(versions, languageOf) }
    val wantedOf = remember(versions, currentKey) { identityMap(versions, wanted) }
    val eligible = remember(versions, deadKeys) { versions.filter { it.rejected == null && it.key !in deadKeys } }
    val excludedCount = remember(versions, deadKeys) { versions.count { it.rejected != null && it.key !in deadKeys } }
    val inOrigin = remember(eligible, origin) { eligible.filter { origin == null || it.origin == origin } }
    val originCounts = remember(eligible) { eligible.groupingBy { it.origin }.eachCount() }
    val counts = remember(inOrigin, languages, wantedOf) {
        VersionsView.entries.associateWith { v -> inOrigin.count { v.keeps(languages[it], wantedOf[it] == true, it.quality) } }
    }
    // "For you" when something is in the wanted languages; a film only found in English opens on all of them.
    val view = chosenView ?: if ((counts[VersionsView.ForYou] ?: 0) > 0) VersionsView.ForYou else VersionsView.All
    val shown = remember(inOrigin, versions, view, excludedView, deadView, deadKeys, languages, wantedOf) {
        when {
            deadView -> versions.filter { it.key in deadKeys }
            excludedView -> versions.filter { it.rejected != null && it.key !in deadKeys }
            else -> inOrigin.filter { view.keeps(languages[it], wantedOf[it] == true, it.quality) }
        }
    }
    val entries = remember(shown, currentKey, aside, wantedOf) {
        buildList<VersionEntry> {
            var index = 0
            val current = if (aside) null else shown.firstOrNull { it.key == currentKey }
            val rest = shown.filter { it !== current }
            if (current != null) { add(VersionEntry.Head(L("PLAYING", "EN LECTURE").text())); add(VersionEntry.Item(current, index++)) }
            val recommended = if (aside) null else rest.firstOrNull { wantedOf[it] == true }
            if (recommended != null) { add(VersionEntry.Head(L("RECOMMENDED", "RECOMMANDÉE").text())); add(VersionEntry.Item(recommended, index++)) }
            val others = rest.filter { it !== recommended }.groupBy { it.quality }
            QUALITY_ORDER.forEach { q ->
                val group = others[q] ?: return@forEach
                add(VersionEntry.Head((qualityLabel(q) ?: L("Other", "Autres").text()).uppercase()))
                group.forEach { add(VersionEntry.Item(it, index++)) }
            }
        }
    }
    val outside = if (aside) 0 else inOrigin.size - shown.size
    val first = remember { FocusRequester() }
    val playing = remember { FocusRequester() }
    val state = rememberLazyListState()
    // Focus starts on the version playing, else on the first segment.
    LaunchedEffect(Unit) {
        withFrameNanos { }; withFrameNanos { }
        if (!playing.focusIfPresent()) runCatching { first.requestFocus() }
    }
    LaunchedEffect(searchRevision, view, excludedView, deadView, origin) { state.scrollToItem(0) }
    TvPanel(
        when {
            deadView -> L("Dead links", "Liens morts").text()
            excludedView -> L("Excluded versions", "Versions écartées").text()
            else -> L("Versions", "Versions").text()
        },
        L("${versions.size} versions · ${if (searching) "searching…" else "search complete"}",
            "${versions.size} versions · ${if (searching) "recherche en cours…" else "recherche terminée"}").text(),
        modifier.testTag("player-versions"), width = 900.dp, height = 1000.dp,
        action = { PanelChip(L("Refresh", "Actualiser").text(), Modifier.testTag("versions-refresh"), icon = Icons.Filled.Refresh, onClick = onRefresh) },
    ) {
        notices.forEach { notice ->
            TvPanelNotice(when (notice) {
                SourceNotice.AnimeSamaVipRequired -> L("Anime-Sama: server key requires VIP access", "Anime-Sama : cl\u00e9 serveur sans acc\u00e8s VIP").text()
                SourceNotice.AnimeSamaUnavailable -> L("Anime-Sama: no resolved stream available", "Anime-Sama : aucun flux r\u00e9solu disponible").text()
                SourceNotice.DebridNoVersions -> L("Real-Debrid search found no version for this title", "La recherche Real-Debrid n’a trouvé aucune version pour ce titre").text()
                SourceNotice.DebridUnavailable -> L("Real-Debrid search is temporarily unavailable", "La recherche Real-Debrid est temporairement indisponible").text()
            })
        }
        if (!aside) {
            // 4K always has a target, including before the first result arrives.
            Row(
                Modifier.padding(horizontal = PanelMetrics.RowPad, vertical = 8.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(0x14FFFFFF))
                    .horizontalScroll(rememberScrollState()).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                var firstShown = true
                VersionsView.entries.forEach { item ->
                    val count = counts[item] ?: 0
                    if (count == 0 && item != view && item != VersionsView.UltraHd && item != VersionsView.All) return@forEach
                    val tag = if (item == VersionsView.UltraHd) "versions-quality-4k" else "versions-language-${item.name.lowercase()}"
                    PanelChip("${item.label()} · $count", Modifier.testTag(tag), selected = item == view,
                        requester = if (firstShown) first else remember(item) { FocusRequester() }) { chosenView = item }
                    firstShown = false
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = PanelMetrics.RowPad, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelChip(L("All sources", "Toutes les sources").text(), Modifier.testTag("versions-origin-all"), selected = origin == null) {
                    origin = null
                }
                VersionOrigin.entries.forEach { source ->
                    val count = originCounts[source] ?: 0
                    PanelChip("${sourceOriginLabel(source)} · $count", Modifier.testTag("versions-origin-${source.name.lowercase()}"),
                        selected = origin == source) { origin = source }
                }
            }
        }
        LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), state = state, contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (entries.isEmpty()) item {
                TvPanelNotice(if (searching) L("Versions are still arriving…", "Les versions arrivent encore…").text()
                    else if (view == VersionsView.UltraHd) L("No 4K version from these sources", "Aucune version 4K pour ces sources").text()
                    else if (origin != null && inOrigin.isEmpty()) L("No version from this source", "Aucune version pour cette source").text()
                    else L("No version in these languages. Choose All, or refresh.", "Aucune version dans ces langues. Choisissez Toutes, ou actualisez.").text())
            }
            items(entries.size, key = { entries[it].key }) { i ->
                when (val entry = entries[i]) {
                    is VersionEntry.Head -> TvSectionLabel(entry.label, Modifier.padding(top = if (i == 0) 0.dp else 12.dp))
                    is VersionEntry.Item -> VersionRow(
                        entry.version, entry.index, entry.version.key == currentKey, languages[entry.version], verified(entry.version), stateOf(entry.version),
                        if (entry.version.key == currentKey) playing else remember(entry.key) { FocusRequester() },
                    ) { onChoose(entry.version) }
                }
            }
        }
        // What is left out, in one line: first the other languages, then the excluded versions.
        val more = when {
            aside -> L("Back to the compatible versions", "Revenir aux versions compatibles").text()
            outside > 0 || excludedCount > 0 -> listOfNotNull(
                outside.takeIf { it > 0 }?.let {
                    when (view) {
                        VersionsView.ForYou -> L("$it more outside your languages", "$it autres hors de vos langues").text()
                        VersionsView.UltraHd -> "${VersionsView.All.label()} · ${inOrigin.size}"
                        else -> L("$it in other languages", "$it dans d'autres langues").text()
                    }
                },
                excludedCount.takeIf { it > 0 }?.let { L("$it excluded", "$it écartées").text() },
            ).joinToString(" · ")
            else -> null
        }
        if (more != null) {
            Spacer(Modifier.height(8.dp))
            PanelOption(more, Modifier.testTag("versions-more"), chevron = true, restFill = androidx.compose.ui.graphics.Color(0x14FFFFFF)) {
                when {
                    aside -> { excludedView = false; deadView = false }
                    outside > 0 -> chosenView = VersionsView.All
                    else -> excludedView = true
                }
            }
        }
        if (deadView) {
            PanelOption(L("Retry the $deadCount dead links", "Réessayer les $deadCount liens morts").text(), Modifier.testTag("versions-retry-dead"),
                chevron = true, restFill = androidx.compose.ui.graphics.Color(0x14FFFFFF)) {
                onRetryDead()
                deadView = false
            }
        } else if (deadCount > 0 && !excludedView) {
            PanelOption(if (deadCount == 1) L("1 dead link", "1 lien mort").text() else L("$deadCount dead links", "$deadCount liens morts").text(),
                Modifier.testTag("versions-dead"), chevron = true, restFill = androidx.compose.ui.graphics.Color(0x14FFFFFF)) { deadView = true }
        }
    }
}

/** One version: sharpness and picture, then the languages, then size · service · state; the file's name under focus. */
@Composable
private fun VersionRow(
    version: PlayVersion, index: Int, playing: Boolean, heard: AudioLanguage?, verified: Boolean, state: String,
    requester: FocusRequester, onClick: () -> Unit,
) {
    PanelCard(Modifier.testTag("version-$index"), requester = requester, selected = playing, onClick = onClick) { focused ->
        val ink = if (focused) Palette.OnFocus else Palette.OnBackground
        val soft = if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Secondary
        Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(qualityLabel(version.quality) ?: "—", Type.RowTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp)), ink, maxLines = 1)
            Label(hdrLabel(version.hdr) ?: "SDR", com.sokoul.pc.theme.PanelType.Caption, soft, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(spokenLanguages(version, heard), com.sokoul.pc.theme.PanelType.Option, ink, Modifier.testTag("version-$index-audio"), maxLines = 1)
            // The TV shows no search service names (Comet, Torrentio): they mean nothing from the sofa.
            val addon = if (com.sokoul.pc.Platform.IS_TV) null else sourceAddonLabel(version.providerHost)
            val evidence = when {
                version.rejected != null -> com.sokoul.pc.playback.session.doubtText(version.rejected)
                heard == null -> L("audio not identified", "audio non identifié").text()
                verified -> L("audio verified", "audio vérifié").text()
                else -> null
            }
            Label(listOfNotNull(sizeLabel(version.sizeBytes), state, addon, evidence, "SeaDex".takeIf { version.community }).joinToString(" · "),
                com.sokoul.pc.theme.PanelType.Callout, soft, maxLines = 1)
            // The file's own name only where the eye already is: under focus.
            if (focused) Label(sourceName(version.name), com.sokoul.pc.theme.PanelType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), soft, maxLines = 1)
        }
        when {
            playing -> Icon(Icons.Filled.Check, L("Playing", "En lecture").text(), Modifier.size(32.dp), tint = ink)
            version.rejected != null -> Icon(Icons.Filled.Warning, null, Modifier.size(30.dp), tint = if (focused) ink else Palette.Error)
            version.preparingPercent != null -> Label("${version.preparingPercent} %", com.sokoul.pc.theme.PanelType.Option, ink, maxLines = 1)
        }
    }
}

private fun <T> identityMap(versions: List<PlayVersion>, of: (PlayVersion) -> T): java.util.IdentityHashMap<PlayVersion, T> =
    java.util.IdentityHashMap<PlayVersion, T>(versions.size * 2).apply { versions.forEach { put(it, of(it)) } }
