package com.sokoul.pc.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.AccentColor
import com.sokoul.desktop.data.AudioIntent
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.desktop.data.BufferProfile
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.MaxQuality
import com.sokoul.desktop.data.MaxSize
import com.sokoul.desktop.data.PreferredOrigin
import com.sokoul.desktop.data.StartMode
import com.sokoul.desktop.data.SubtitleBackground
import com.sokoul.desktop.data.SubtitleColor
import com.sokoul.desktop.data.SubtitleFont
import com.sokoul.desktop.data.SubtitlePosition
import com.sokoul.desktop.data.SubtitlePreference
import com.sokoul.desktop.data.SubtitleSize
import com.sokoul.desktop.data.VideoFormat
import com.sokoul.pc.data.L
import com.sokoul.pc.data.MenuEntries
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.playback.ui.CueText
import com.sokoul.pc.playback.sources.gigabytes
import com.sokoul.pc.playback.ui.label
import com.sokoul.pc.playback.sources.label
import com.sokoul.pc.theme.AccentTheme
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.delay

/** One service of the app and whether it is set up: its account or key. */
class ServiceInfo(val label: String, val configured: Boolean, val source: String?)

/** What the settings need from the app besides the preferences: the menu, the services, and what a choice or an action does. */
class SettingsHooks(
    /** Section id and name of each entry of the top menu. */
    val menu: List<Pair<String, String>> = emptyList(),
    /** A setting that changes what is on screen (menu, home rows, accent) was changed. */
    val onChanged: () -> Unit = {},
    /** The language was changed: the whole interface is drawn again. */
    val onLanguage: () -> Unit = {},
    val onHomeRows: () -> Unit = {},
    /** Opens the ordered list of audio languages. */
    val onAudioLanguages: () -> Unit = {},
    val onLanguages: () -> Unit = {},
    val onDebridTools: (() -> Unit)? = null,
    /** Every service with its state, read when the page opens. */
    val services: () -> List<ServiceInfo> = { emptyList() },
    val onClearCache: () -> Unit = {},
    val onForgetSourceFailures: () -> Unit = {},
    val onClearHistory: () -> Unit = {},
    /** Accent, menu, home rows, subtitle style and screensaver back to what they were at first. */
    val onResetDisplay: () -> Unit = {},
)

private val ON_OFF = listOf(true, false)

private fun onOff(on: Boolean) = tr(if (on) Text.PrefOn else Text.PrefOff)

private val GREEN = Color(0xFF30D158)

/** How Settings is laid out: the TV and PC, a tablet held sideways (two panels), a tablet held upright (one column). */
internal enum class SettingsLook { Tv, TabletWide, TabletUpright }

internal val LocalSettingsLook = androidx.compose.runtime.staticCompositionLocalOf { SettingsLook.Tv }

/**
 * Rows at rest carry no fill (Figma "V2 · Réglages", TV and tablet): the glass panel or the group's plaque is the surface,
 * and the white focus is the only shape that stands out.
 */
@Composable
internal fun settingsRowRest(): Color = Color.Transparent

/** The plaque behind a group of rows on a tablet held upright (DESIGN.md surface/plaque). */
private val Plaque = Color(0xFF1C1C1E)

/**
 * A group of rows under an optional small title. Upright on a tablet the rows share one rounded plaque, as in the Figma
 * "Tablette portrait" boards; elsewhere they sit straight on the panel.
 */
@Composable
internal fun RowGroup(title: String? = null, content: @Composable () -> Unit) {
    title?.let { GroupTitle(it) }
    if (LocalSettingsLook.current == SettingsLook.TabletUpright) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Plaque)) { content() }
    } else {
        content()
    }
}

/** A small title inside a pane, between groups of rows. */
@Composable
internal fun GroupTitle(text: String) {
    Label(text, com.sokoul.pc.theme.PanelType.Section, Palette.Secondary, Modifier.padding(top = 26.dp, start = com.sokoul.pc.ui.PanelMetrics.RowPad, bottom = 4.dp))
}

/** A row that only informs: nothing to press, so nothing takes the focus. */
@Composable
private fun InfoRow(label: String, value: String, dot: Color? = null, tag: String? = null) {
    Row(
        Modifier.fillMaxWidth().height(Metrics_ROW).clip(RoundedCornerShape(com.sokoul.pc.theme.Corners.Row)).background(settingsRowRest())
            .let { if (tag != null) it.testTag(tag) else it }.padding(horizontal = com.sokoul.pc.ui.PanelMetrics.RowPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(18.dp))
        }
        Label(label, com.sokoul.pc.theme.PanelType.Option, Palette.OnBackground, Modifier.weight(1f), maxLines = 1)
        Label(value, com.sokoul.pc.theme.PanelType.Callout, Palette.Secondary, maxLines = 1)
    }
}

private val Metrics_ROW = com.sokoul.pc.ui.PanelMetrics.RowHeight

/** An action that asks twice: the first press arms it, the second does it (clearing something cannot be undone). */
@Composable
private fun ConfirmRow(label: String, tag: String, doneText: String, onConfirm: () -> Unit) {
    var state by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        if (state == 1) delay(4_000) else if (state == 2) delay(2_500) else return@LaunchedEffect
        state = 0
    }
    SettingsRow(
        label,
        when (state) {
            1 -> L("Press again to confirm", "Appuyer à nouveau pour confirmer").text()
            2 -> doneText
            else -> null
        },
        false, remember { FocusRequester() },
        {
            when (state) {
                0 -> state = 1
                1 -> {
                    onConfirm()
                    state = 2
                }
            }
        },
        tag,
    )
}

/** Who is watching and whether Sokoul asks at start (settings-redesign: "For this profile › Profile"). */
@Composable
internal fun ProfilePane(prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?) = RowGroup {
    if (onProfiles != null) {
        SettingsRow(L("Who is watching", "Qui regarde").text(), profile?.name, true, remember { FocusRequester() }, onProfiles, "settings-profiles")
    }
    if (prefs != null) {
        ChoiceRow(L("Ask at start", "Demander au lancement").text(), "pref-profile-prompt", ON_OFF, prefs.profilePrompt, ::onOff) { prefs.profilePrompt = it }
    }
}

/** Every service with its state, and the way to change them ("For this device › Content sources"). */
@Composable
internal fun SourcesPane(onCredentials: () -> Unit, hooks: SettingsHooks) {
    val services = remember { hooks.services() }
    RowGroup {
        services.forEach { s ->
            InfoRow(s.label, if (s.configured) (s.source ?: L("Set up", "Configuré").text()) else L("Not set", "Non renseigné").text(), if (s.configured) GREEN else Color(0xFF636366), "service-${s.label}")
        }
    }
    Spacer(Modifier.height(10.dp))
    RowGroup { SettingsRow(tr(Text.SettingsCredentials), null, true, remember { FocusRequester() }, onCredentials, "settings-credentials") }
}

/** The link tools, then housekeeping and credits ("For this device › Tools and maintenance"). */
@Composable
internal fun ToolsPane(profile: com.sokoul.pc.data.Profile?, hooks: SettingsHooks) {
    if (profile?.kids != true) hooks.onDebridTools?.let { open ->
        RowGroup { SettingsRow(L("Unlock a download link", "Débrider un lien de téléchargement").text(), null, true, remember { FocusRequester() }, open, "settings-debrid-tools") }
    }
    StartLogSection()
    AboutPane(hooks)
}

/**
 * The last starts, newest first: the title, how it ended (image time and version, or the failure), and on a press every
 * step, to see why a film fell to the web or IPTV (StartLog). Nothing in it is a key or a link.
 */
@Composable
private fun StartLogSection() {
    val entries = remember { com.sokoul.pc.playback.session.StartLog.recent().take(8) }
    var open by remember { mutableStateOf<Int?>(null) }
    RowGroup(L("Last starts", "Derniers lancements").text()) {
        if (entries.isEmpty()) InfoRow(L("Nothing started yet", "Rien n'a encore été lancé").text(), "", tag = "settings-start-log-empty")
        entries.forEachIndexed { i, e ->
            SettingsRow("${e.at} · ${e.title}", e.outcome(), true, remember { FocusRequester() }, { open = if (open == i) null else i }, "settings-start-log-$i")
            if (open == i) androidx.compose.foundation.layout.Column(Modifier.padding(start = 30.dp, end = 30.dp, bottom = 12.dp).testTag("settings-start-log-steps")) {
                e.steps.forEach { line -> Label(line, Type.Body.copy(fontSize = 20.sp), Palette.Secondary, maxLines = 2) }
            }
        }
    }
}

/** Accent colour of the progress bars as swatches to pick from; white keeps Sokoul's own look. */
@Composable
internal fun AppearancePane(prefs: DesktopPrefs, hooks: SettingsHooks) {
    var current by remember { mutableStateOf(prefs.accent) }
    GroupTitle(L("Accent colour", "Couleur d'accent").text())
    Row(Modifier.padding(start = 14.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentColor.entries.forEach { c ->
            Swatch(c, c == current) {
                current = c
                prefs.accent = c
                AccentTheme.color = Color(c.argb)
                hooks.onChanged()
            }
        }
    }
    Label(L(current.english, current.french).text(), Type.Meta.copy(fontWeight = FontWeight.SemiBold), Palette.OnBackground, Modifier.padding(start = 30.dp, top = 18.dp).testTag("accent-name"))
    Label(
        L("Colours the progress of what you watch: the player's bar and the cards of Continue Watching.", "Colore la progression de ce que vous regardez : la barre du lecteur et les cartes « Reprendre ».").text(),
        Type.Body, Palette.Secondary, Modifier.padding(start = 30.dp, top = 4.dp, end = 30.dp),
    )
    // The same bar as the player's, in the colour chosen.
    Box(Modifier.padding(start = 30.dp, end = 30.dp, top = 22.dp).fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF3A3A3C)).testTag("accent-sample")) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.62f).background(Color(current.argb)))
    }
}

@Composable
private fun Swatch(c: AccentColor, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f)
    val ring = if (focused) Palette.FocusFill else if (selected) Color.White else Color.Transparent
    Box(
        Modifier.size(76.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(Color(c.argb))
            .border(if (focused || selected) 5.dp else 0.dp, ring, CircleShape).testTag("accent-${c.id}")
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = remember { FocusRequester() }),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = if (c == AccentColor.WHITE || c == AccentColor.YELLOW) Color(0xFF111111) else Color.White, modifier = Modifier.size(36.dp))
    }
}

/** The home page's rows, and the choices that belong to it. */
@Composable
internal fun HomePane(prefs: DesktopPrefs, hooks: SettingsHooks) {
    RowGroup { SettingsRow(L("Home rows", "Rangées de l'accueil").text(), L("Order and hiding", "Ordre et masquage").text(), true, remember { FocusRequester() }, hooks.onHomeRows, "pref-home-rows") }
    RowGroup(L("Behaviour", "Comportement").text()) {
        ChoiceRow(L("Automatic trailers", "Bandes-annonces automatiques").text(), "pref-auto-trailers", ON_OFF, prefs.autoTrailers, ::onOff) { prefs.autoTrailers = it }
        ChoiceRow(
            L("Screensaver", "Économiseur d'écran").text(), "pref-screensaver", listOf(0, 3, 5, 10, 15, 30), prefs.screensaverMinutes,
            { if (it == 0) tr(Text.PrefOff) else L("After $it min", "Après $it min").text() },
        ) { prefs.screensaverMinutes = it }
    }
}

/** Which entries of the top menu are shown. At least one stays. */
@Composable
internal fun MenuPane(prefs: DesktopPrefs, hooks: SettingsHooks, title: String? = null) {
    var rev by remember { mutableIntStateOf(0) }
    val all = hooks.menu.map { it.first }
    RowGroup(title) { key(rev) {
        hooks.menu.forEach { (id, name) ->
            ChoiceRow(name, "pref-menu-$id", ON_OFF, id !in prefs.railHidden, { if (it) L("Shown", "Affichée").text() else L("Hidden", "Masquée").text() }) { show ->
                val before = prefs.railHidden
                val after = if (show) before - id else MenuEntries.toggle(before, all, id)
                prefs.railHidden = after
                hooks.onChanged()
                // The last entry cannot go: the row is drawn again showing it still is.
                if (!show && id !in after) rev++
            }
        }
    } }
    Label(
        L("Settings always stays: it is how you get back here.", "Les Réglages restent toujours : c'est par là que vous revenez ici.").text(),
        Type.Body, Palette.Secondary, Modifier.padding(start = 30.dp, top = 12.dp, end = 30.dp),
    )
}

/** Size, colour, background, position and font of the subtitles, with a preview, and the way back to the defaults. */
@Composable
internal fun SubtitlesPane(prefs: DesktopPrefs) {
    // Recreated after a reset, so each row shows the value it was given back.
    var rev by remember { mutableIntStateOf(0) }
    var look by remember { mutableIntStateOf(0) }
    // A film's still, dark at the bottom, with the line written as it will be: first, so it stays in view while the rows change.
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(28.dp)).testTag("pref-sub-preview")
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF111113)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        key(look, rev) {
            CueText(
                L("See you tomorrow at dawn.\nAnd this time, don't be late.", "On se retrouve demain à l'aube.\nEt cette fois, ne sois pas en retard.").text(),
                prefs, Modifier.padding(bottom = 20.dp, start = 24.dp, end = 24.dp), scale = 0.66f,
            )
        }
    }
    key(rev) {
        RowGroup(L("Look", "Aspect").text()) {
            ChoiceRow(L("Size", "Taille").text(), "pref-sub-size", SubtitleSize.entries, prefs.subtitleSize, { sizeText(it) }) { prefs.subtitleSize = it; look++ }
            ChoiceRow(L("Colour", "Couleur").text(), "pref-sub-color", SubtitleColor.entries, prefs.subtitleColor, { colorText(it) }) { prefs.subtitleColor = it; look++ }
            ChoiceRow(L("Background", "Fond").text(), "pref-sub-background", SubtitleBackground.entries, prefs.subtitleBackground, { backgroundText(it) }) { prefs.subtitleBackground = it; look++ }
            ChoiceRow(L("Position", "Position").text(), "pref-sub-position", SubtitlePosition.entries, prefs.subtitlePosition, { positionText(it) }) { prefs.subtitlePosition = it; look++ }
            ChoiceRow(L("Font", "Police").text(), "pref-sub-font", SubtitleFont.entries, prefs.subtitleFont, { it.label() }) { prefs.subtitleFont = it; look++ }
        }
        // Off, automatic (the interface language when the audio heard is another one), or always one language.
        RowGroup(L("Automatic", "Automatiques").text()) { ChoiceRow(
            tr(Text.PrefSubtitles), "pref-subtitles", listOf(AudioIntent.SUBTITLES_OFF, AudioIntent.SUBTITLES_AUTO) + AUDIO_LANGUAGE_CHOICES, prefs.audioIntent.subtitles,
            { mode -> when (mode) {
                AudioIntent.SUBTITLES_OFF -> tr(Text.SubtitlesOff)
                AudioIntent.SUBTITLES_AUTO -> L("Automatic", "Automatiques").text()
                else -> com.sokoul.pc.playback.ui.languageName(mode)
            } },
        ) { prefs.audioIntent = prefs.audioIntent.copy(subtitles = it) } }
    }
    Spacer(Modifier.height(10.dp))
    RowGroup {
        SettingsRow(
            L("Reset the default style", "Rétablir le style par défaut").text(), null, false, remember { FocusRequester() },
            {
                prefs.resetSubtitleStyle()
                rev++
            },
            "pref-sub-reset",
        )
    }
}

/** Starting a title, which version to prefer, quality, the next episode and the picture. */
@Composable
internal fun PlaybackPane(prefs: DesktopPrefs, hooks: SettingsHooks = SettingsHooks()) {
    RowGroup(L("Starting", "Démarrage").text()) {
        ChoiceRow(tr(Text.PrefStart), "pref-start", StartMode.entries, prefs.startMode, ::startLabel) { prefs.startMode = it }
        ChoiceRow(L("Next episode automatically", "Épisode suivant automatique").text(), "pref-auto-next", ON_OFF, prefs.autoNext, ::onOff) { prefs.autoNext = it }
    }
    RowGroup(L("Which version", "Quelle version").text()) {
        // Any language, in order: the first one a version offers is played (book section 3).
        SettingsRow(L("Audio languages", "Langues audio").text(), audioIntentSummary(prefs.audioIntent), true, remember { FocusRequester() }, hooks.onAudioLanguages, "pref-audio")
        ChoiceRow(tr(Text.PrefOrigin), "pref-origin", PreferredOrigin.entries, prefs.preferredOrigin, ::originLabel) { prefs.preferredOrigin = it }
        ChoiceRow(L("Best quality first", "Meilleure qualité d'abord").text(), "pref-best-quality", ON_OFF, prefs.bestQualityFirst, ::onOff) { prefs.bestQualityFirst = it }
        ChoiceRow(L("Remember hoster chosen for each source", "Mémoriser le lecteur choisi pour chaque source").text(), "pref-remember-hoster", ON_OFF, prefs.rememberSourceHosters, ::onOff) { prefs.rememberSourceHosters = it }
    }
    var services by remember { mutableStateOf(prefs.searchServices) }
    RowGroup(L("Search services", "Services de recherche").text()) { com.sokoul.iptv.data.debrid.SourceAddon.entries.forEach { service ->
        // Keep the final active service on, including the choice row's own remembered value.
        val choices = if (services.size == 1 && service in services) listOf(true) else ON_OFF
        ChoiceRow(service.label, "pref-service-${service.name.lowercase()}", choices, service in services, ::onOff) { enabled ->
            services = if (enabled) services + service else services - service
            prefs.searchServices = services
        }
    } }
    SourcePriorityRows(prefs, false)
    SourcePriorityRows(prefs, true)
    RowGroup(L("Quality and size", "Qualité et taille").text()) {
        ChoiceRow(tr(Text.PrefMaxQuality), "pref-quality", MaxQuality.entries, prefs.maxQuality, { it.label }) { prefs.maxQuality = it }
        ChoiceRow(tr(Text.PrefMaxSize), "pref-size", MaxSize.entries, prefs.maxSize, { it.gigabytes?.let { g -> "$g GB" } ?: tr(Text.PrefNoLimit) }) { prefs.maxSize = it }
    }
    RowGroup(L("Picture and sound", "Image et son").text()) {
        ChoiceRow(L("Picture format", "Format de l'image").text(), "pref-video-format", VideoFormat.entries, prefs.videoFormat, { it.label() }) { prefs.videoFormat = it }
        ChoiceRow(tr(Text.PrefBuffer), "pref-buffer", BufferProfile.entries, prefs.buffer, ::bufferLabel) { prefs.buffer = it }
    }
    // The help line of the Figma board, under the row it explains.
    Label(
        L("Playback stability: a larger reserve avoids cuts on an unsteady connection; playback starts a little later.",
            "Stabilité de la lecture : une réserve plus grande évite les coupures sur une connexion instable ; la lecture démarre un peu plus tard.").text(),
        Type.Body, Palette.Secondary, Modifier.padding(start = com.sokoul.pc.ui.PanelMetrics.RowPad, top = 4.dp, end = 30.dp),
    )
    RowGroup(L("Live TV", "Télévision en direct").text()) {
        ChoiceRow(tr(Text.PrefLivePreview), "pref-live-preview", ON_OFF, prefs.livePreview, ::onOff) { prefs.livePreview = it }
    }
}

/** Picking a priority position moves that source/hoster there without hiding any candidates. */
@Composable
private fun SourcePriorityRows(prefs: DesktopPrefs, anime: Boolean) {
    var revision by remember { mutableIntStateOf(0) }
    val label = if (anime) L("Anime source priorities", "Priorités des sources anime") else L("Movie and series source priorities", "Priorités des sources films et séries")
    RowGroup(label.text()) { key(revision) {
        val order = prefs.sourceOrder(anime)
        fun move(values: List<String>, index: Int, selected: String): List<String> = values.filterNot { it == selected }.toMutableList().apply { add(index, selected) }
        order.sources.forEachIndexed { index, value ->
            ChoiceRow(L("Source ${index + 1}", "Source ${index + 1}").text(), "pref-source-$anime-$index", order.sources, value, { it }) { selected ->
                prefs.setSourceOrder(anime, move(order.sources, index, selected), order.hosters); revision++
            }
        }
        order.hosters.forEachIndexed { index, value ->
            ChoiceRow(L("Hoster ${index + 1}", "Hébergeur ${index + 1}").text(), "pref-hoster-$anime-$index", order.hosters, value, { it }) { selected ->
                prefs.setSourceOrder(anime, order.sources, move(order.hosters, index, selected)); revision++
            }
        }
    } }
}

/** Language, as a choice: the system's, French or English. */
@Composable
internal fun LanguagePane(prefs: DesktopPrefs, hooks: SettingsHooks) {
    // A list to choose from, each language named in itself (Español, Deutsch, العربية): see InterfaceLanguageScreen.
    RowGroup { SettingsRow(tr(Text.SettingsLanguage), interfaceLanguageName(prefs.language), true, remember { FocusRequester() }, hooks.onLanguages, "pref-language") }
    Label(
        L("The whole interface changes at once. Titles and descriptions follow the language too.", "Toute l'interface change aussitôt. Les titres et les descriptions suivent aussi la langue.").text(),
        Type.Body, Palette.Secondary, Modifier.padding(start = 30.dp, top = 12.dp, end = 30.dp),
    )
}

/** What this is, where its data lives, the credits, and the actions that clean up. */
@Composable
internal fun AboutPane(hooks: SettingsHooks) {
    RowGroup { InfoRow(L("Application", "Application").text(), if (com.sokoul.pc.Platform.IS_TV) "Sokoul TV" else "Sokoul PC") }
    val done = L("Done", "Fait").text()
    RowGroup(L("Maintenance", "Maintenance").text()) {
        ConfirmRow(L("Clear the catalogue cache", "Vider le cache du catalogue").text(), "about-clear-cache", done, hooks.onClearCache)
        ConfirmRow(L("Forget source failures", "Oublier les échecs des sources").text(), "about-forget-failures", done, hooks.onForgetSourceFailures)
        ConfirmRow(L("Erase this profile's watch history", "Effacer l'historique de ce profil").text(), "about-clear-history", done, hooks.onClearHistory)
        ConfirmRow(L("Reset the display settings", "Réinitialiser l'affichage").text(), "about-reset-display", done, hooks.onResetDisplay)
    }
    Label(
        L(
            "The display settings are the accent, the menu, the home rows, the subtitle style and the screensaver. Keys, accounts and playback choices are kept.",
            "L'affichage, ce sont l'accent, le menu, les rangées de l'accueil, le style des sous-titres et l'économiseur. Clés, comptes et choix de lecture sont conservés.",
        ).text(),
        Type.Body, Palette.Secondary, Modifier.padding(start = 30.dp, top = 8.dp, end = 30.dp),
    )
    GroupTitle(L("Credits", "Crédits").text())
    // The licences of the anime data ask for their credit (manami's database is under ODbL).
    Label(
        L(
            "Data: TMDB. Anime: Fribb anime-lists, manami-project anime-offline-database (ODbL), ARM, animeapi, Kitsu, Jikan, AniList, SeaDex (releases.moe), AniSkip. Sokoul is not endorsed by these services.",
            "Données : TMDB. Animés : Fribb anime-lists, manami-project anime-offline-database (ODbL), ARM, animeapi, Kitsu, Jikan, AniList, SeaDex (releases.moe), AniSkip. Sokoul n'est pas approuvé par ces services.",
        ).text(),
        Type.Meta, Palette.Secondary, Modifier.padding(start = 30.dp, end = 30.dp, bottom = 60.dp).testTag("settings-credits"),
    )
}

private fun sizeText(s: SubtitleSize) = when (s) {
    SubtitleSize.SMALL -> L("Small", "Petite")
    SubtitleSize.NORMAL -> L("Normal", "Normale")
    SubtitleSize.LARGE -> L("Large", "Grande")
    SubtitleSize.HUGE -> L("Very large", "Très grande")
}.text()

private fun colorText(c: SubtitleColor) = when (c) {
    SubtitleColor.WHITE -> L("White", "Blanc")
    SubtitleColor.YELLOW -> L("Yellow", "Jaune")
    SubtitleColor.CYAN -> L("Cyan", "Cyan")
    SubtitleColor.GREEN -> L("Green", "Vert")
}.text()

private fun backgroundText(b: SubtitleBackground) = when (b) {
    SubtitleBackground.OUTLINE -> L("Black outline", "Contour noir")
    SubtitleBackground.BOX -> L("Dark box", "Fond sombre")
    SubtitleBackground.NONE -> L("Shadow only", "Ombre seule")
}.text()

private fun positionText(p: SubtitlePosition) = when (p) {
    SubtitlePosition.LOW -> L("Bottom", "En bas")
    SubtitlePosition.HIGH -> L("Raised", "Plus haut")
}.text()
