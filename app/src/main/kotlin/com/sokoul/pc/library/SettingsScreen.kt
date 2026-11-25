package com.sokoul.pc.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.pc.data.L
import com.sokoul.pc.library.settings.SettingsCategory
import com.sokoul.pc.library.settings.SettingsFamily
import com.sokoul.pc.library.settings.SettingsNavigation
import com.sokoul.pc.theme.Glass
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.PanelType
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.PanelMetrics
import com.sokoul.pc.ui.tvFocusable
import com.sokoul.pc.ui.focusIfPresent

/** Graphite canvas of the redesigned Settings (Figma "Sokoul — Réglages", variable color/bg/canvas). */
private val Canvas = Color(0xFF090909)

/** The silver light, eased: alpha follows (1 - t)^2.2 so the falloff has no visible step on an 8-bit panel. */
private val SilverLight: List<Pair<Float, Color>> = (0..12).map { i ->
    val t = i / 12f
    t to Color(0xFFD7DBE3).copy(alpha = 0.26f * Math.pow((1.0 - t), 2.2).toFloat())
}

/**
 * The settings panel: glass nearly opaque (the page's light does not show through as bands), one silver hairline brighter
 * on the top-right edge where the light comes from, and a sheen that fades over the whole height. Depth comes from the
 * fill and the rim only: no shadow slab, which on the TV is a single offset rectangle that reads as a second border.
 */
private fun Modifier.settingsGlass(radius: Dp): Modifier {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Color(0xFA19191B))
        .drawBehind {
            drawRect(Brush.verticalGradient(0f to Color(0x14FFFFFF), 0.18f to Color(0x08FFFFFF), 0.45f to Color(0x02FFFFFF), 1f to Color.Transparent))
        }
        .border(
            1.dp,
            Brush.linearGradient(
                0f to Color(0x8CE6E9EF), 0.3f to Color(0x33C9CCD3), 0.7f to Color(0x14FFFFFF), 1f to Color(0x0FFFFFFF),
                start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY),
            ),
            shape,
        )
}

/**
 * The two-panel layout's sizes. The TV keeps its own; a tablet held sideways takes the Figma board
 * "V2 · Réglages · Tablette paysage · deux panneaux" (1280 × 800, drawn here at 1.5 dp per board pixel).
 */
private class WideMetrics(
    val side: Dp, val bottom: Dp, val column: Dp, val gap: Dp,
    val title: TextUnit, val family: TextUnit, val category: TextUnit,
    val panelRadius: Dp, val panelPadH: Dp, val panelPadTop: Dp,
    /** Tablet: "Réglages › Lecture" above the title, no summary line, a fade at the bottom, the current category outlined. */
    val tablet: Boolean,
)

/** Figma "V2 · Réglages · TV · Lecture (focus Qualité)", 1920 × 1080: the same composition as the tablet's. */
private val TvWide = WideMetrics(80.dp, 48.dp, 460.dp, 32.dp, 64.sp, 25.sp, 31.sp, 40.dp, 19.dp, 35.dp, tablet = true)
private val TabletWide = WideMetrics(72.dp, 48.dp, 558.dp, 48.dp, 60.sp, 22.sp, 31.sp, 42.dp, 18.dp, 34.dp, tablet = true)

/** Below the top bar and its shade (NavMetrics: 26 + 68, shade to 150): nothing of Settings slides under them. */
private val BelowBar = 140.dp

/**
 * Settings after the owner's brief (openspec settings-redesign): the title and the profile, then the two families of
 * categories down the left; the chosen category in a glass panel on the right, the same material as the player's audio
 * panel. Moving between categories shows each at once; Right goes into the panel and Left comes back. A tablet held
 * upright shows the categories as an index and pushes the chosen one as its own page (Figma "Tablette portrait").
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SettingsScreen(
    firstRow: FocusRequester,
    prefs: DesktopPrefs?,
    onCredentials: () -> Unit,
    profile: com.sokoul.pc.data.Profile? = null,
    onProfiles: (() -> Unit)? = null,
    hooks: SettingsHooks = SettingsHooks(),
    /** The category shown first: a current id ("playback") or a former pane id ("accounts", "subtitles"…). */
    initialPane: String = "profile",
) {
    // Live TV joins once its rows move here from the TV page (settings-redesign task 5.3).
    val available = remember(prefs) {
        if (prefs == null) listOf(SettingsCategory.SOURCES) else SettingsCategory.entries.filter { it != SettingsCategory.LIVE_TV }
    }
    val start = remember { SettingsCategory.of(initialPane).takeIf { it in available } ?: available.first() }
    val nav = remember(available) { SettingsNavigation(start, available) }
    val look = when {
        !com.sokoul.pc.ui.Form.tablet -> SettingsLook.Tv
        com.sokoul.pc.ui.Form.tabletPortrait -> SettingsLook.TabletUpright
        else -> SettingsLook.TabletWide
    }
    // Upright, a category asked for by name (coming back from the home rows or the language list) opens as its page;
    // otherwise the index shows first. Kept across a rotation: sideways the same category stays in the panel.
    var uprightPage by remember { mutableStateOf(initialPane != "profile" && initialPane != "accounts") }
    androidx.compose.runtime.CompositionLocalProvider(com.sokoul.pc.ui.LocalHoverOnly provides true, LocalSettingsLook provides look) {
        Box(
            Modifier.fillMaxSize().background(Canvas).drawBehind {
                // One silver light from the top right corner. Many stops on an ease-out curve: a two-stop fade shows bands.
                drawRect(Brush.radialGradient(*SilverLight.toTypedArray(), center = Offset(size.width * 0.92f, -size.height * 0.08f), radius = maxOf(size.width, size.height) * 0.78f))
            }.testTag("settings"),
        ) {
            if (look == SettingsLook.TabletUpright) {
                UprightSettings(nav, available, uprightPage, { uprightPage = it }, prefs, profile, onProfiles, onCredentials, hooks)
            } else {
                WideSettings(if (look == SettingsLook.TabletWide) TabletWide else TvWide, nav, available, start, firstRow, prefs, profile, onProfiles, onCredentials, hooks)
            }
        }
    }
    // A remote or a keyboard starts on the first category; a touch tablet starts with nothing lit.
    LaunchedEffect(Unit) { if (!com.sokoul.pc.ui.Form.tablet) runCatching { firstRow.requestFocus() } }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun WideSettings(
    m: WideMetrics, nav: SettingsNavigation, available: List<SettingsCategory>, start: SettingsCategory, firstRow: FocusRequester,
    prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?, onCredentials: () -> Unit, hooks: SettingsHooks,
) {
    val panelEntry = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().padding(start = m.side, end = m.side, top = BelowBar, bottom = m.bottom), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        Column(Modifier.width(m.column).fillMaxHeight().let { if (m.tablet) it.verticalScroll(rememberScrollState()) else it }) {
            Label(com.sokoul.pc.i18n.tr(com.sokoul.pc.i18n.Text.MenuSettings), PanelType.Title.copy(fontSize = m.title, letterSpacing = (-1.5).sp), modifier = Modifier.padding(start = 6.dp))
            ProfileHeader(profile, onProfiles)
            // Coming back into the list from the right lands on the category in use, not on whichever lies nearest.
            Column(Modifier.focusRestorer().focusGroup()) {
                SettingsFamily.entries.forEach { family ->
                    val items = available.filter { it.family == family }
                    if (items.isEmpty()) return@forEach
                    Label(
                        family.label.text().uppercase(), PanelType.Section.copy(fontSize = m.family, letterSpacing = 1.sp), Palette.Secondary,
                        Modifier.padding(start = PanelMetrics.RowPad, top = 26.dp, bottom = 8.dp),
                    )
                    items.forEach { c ->
                        CategoryRow(
                            c, c == nav.current.category, if (c == start) firstRow else remember { FocusRequester() }, m.category, m.tablet,
                            onEnter = { panelEntry.focusIfPresent() },
                        ) { nav.select(c) }
                    }
                }
            }
        }
        key(nav.current) {
            Box(Modifier.weight(1f).fillMaxHeight().settingsGlass(m.panelRadius)) {
                Column(
                    Modifier.fillMaxSize().focusRequester(panelEntry).focusGroup().verticalScroll(rememberScrollState())
                        .padding(start = m.panelPadH, end = m.panelPadH, top = m.panelPadTop, bottom = if (m.tablet) 120.dp else 40.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val category = nav.current.category
                    if (m.tablet) {
                        Label(nav.breadcrumb { it }.joinToString(" › "), PanelType.Callout, Palette.Secondary, Modifier.padding(start = PanelMetrics.RowPad), maxLines = 1)
                        Label(category.label.text(), PanelType.Title.copy(fontSize = 48.sp, letterSpacing = (-0.5).sp), modifier = Modifier.padding(start = PanelMetrics.RowPad, bottom = 6.dp).testTag("settings-title"))
                    } else {
                        Label(category.label.text(), PanelType.Title.copy(fontSize = 48.sp), modifier = Modifier.padding(start = PanelMetrics.RowPad).testTag("settings-title"))
                        Label(category.summary.text(), PanelType.Callout, Palette.Secondary, Modifier.padding(start = PanelMetrics.RowPad, bottom = 10.dp, end = 30.dp))
                    }
                    CategoryContent(category, prefs, profile, onProfiles, onCredentials, hooks)
                }
                // The rows fade into the glass at the bottom edge instead of being cut by it (tablet board).
                if (m.tablet) Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x00121214), Color(0xF2121214)))),
                )
            }
        }
    }
}

/**
 * A tablet held upright (Figma "V2 · Réglages · Tablette portrait"): the index of the categories in two grouped plaques,
 * then the chosen one pushed as a page with a way back. Drawn at the upright scale of the browse pages, 1.4 dp per
 * pixel of the 800-wide boards.
 */
@Composable
private fun UprightSettings(
    nav: SettingsNavigation, available: List<SettingsCategory>, page: Boolean, onPage: (Boolean) -> Unit,
    prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?, onCredentials: () -> Unit, hooks: SettingsHooks,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val outer = LocalDensity.current
        val inner = com.sokoul.pc.ui.uprightDensity(constraints.maxWidth)
        // The top bar stays drawn at the app's scale: clear it in this page's own units.
        val top = (with(outer) { BelowBar.toPx() } / inner.density).dp
        val pageFocus = remember { FocusRequester() }
        androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides inner) {
            Column(
                Modifier.fillMaxSize()
                    // Back, from the remote, a keyboard or the system gesture (mapped to Escape), leaves the page first.
                    .onPreviewKeyEvent { e ->
                        if (page && (e.key == Key.Escape || e.key == Key.Back)) {
                            if (e.type == KeyEventType.KeyUp) onPage(false)
                            true
                        } else false
                    }
                    .focusRequester(pageFocus).focusable(enabled = page)
                    .verticalScroll(key(page, nav.current) { rememberScrollState() })
                    .padding(start = UprightMargin, end = UprightMargin, top = top, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (page) UprightPage(nav, prefs, profile, onProfiles, onCredentials, hooks) { onPage(false) }
                else UprightIndex(available, prefs, profile, onProfiles, hooks) { c -> nav.select(c); onPage(true) }
            }
        }
        // Holding the (invisible) page focus lets Back reach this page on a touch tablet, where nothing else is lit.
        LaunchedEffect(page) { if (page) runCatching { pageFocus.requestFocus() } }
    }
}

private val UprightMargin = 44.dp
private val UprightTitle = PanelType.Title.copy(fontSize = 48.sp, letterSpacing = (-0.7).sp)
private val UprightPlaque = Color(0xFF1C1C1E)

@Composable
private fun UprightIndex(
    available: List<SettingsCategory>, prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?, hooks: SettingsHooks,
    onOpen: (SettingsCategory) -> Unit,
) {
    Label(com.sokoul.pc.i18n.tr(com.sokoul.pc.i18n.Text.MenuSettings), UprightTitle, modifier = Modifier.padding(bottom = 18.dp))
    profile?.let { p ->
        UprightProfileCard(p, prefs, onProfiles)
    }
    // Read once, when the index opens: the state of each service on this device.
    val services = remember { hooks.services() }
    SettingsFamily.entries.forEach { family ->
        val items = available.filter { it.family == family }
        if (items.isEmpty()) return@forEach
        Label(
            family.label.text().uppercase(), PanelType.Section.copy(fontSize = 21.sp, letterSpacing = 0.8.sp), Palette.Secondary,
            Modifier.padding(start = 22.dp, top = 30.dp, bottom = 2.dp),
        )
        Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp)).background(UprightPlaque)) {
            items.forEach { c ->
                com.sokoul.pc.ui.PanelOption(
                    c.label.text(),
                    Modifier.heightIn(min = 84.dp).testTag("settings-pane-${c.id}"),
                    value = indexValue(c, prefs, profile, services),
                    chevron = true,
                    titleStyle = PanelType.Option.copy(fontSize = 30.sp),
                    leading = { tint -> Icon(c.icon, null, tint = tint, modifier = Modifier.size(33.dp)) },
                    onClick = { onOpen(c) },
                )
            }
        }
    }
    Label(
        L("Settings marked \u201cFor this device\u201d apply to every profile on this tablet.", "Les réglages marqués « Pour cet appareil » s'appliquent à tous les profils de cette tablette.").text(),
        PanelType.Section.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium), Palette.Secondary, Modifier.padding(start = 22.dp, top = 22.dp, end = 22.dp), maxLines = 3,
    )
}

/** The value an index row shows, only where it helps to know it before opening the page (the Figma board's choices). */
private fun indexValue(c: SettingsCategory, prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, services: List<ServiceInfo>): String? = when (c) {
    SettingsCategory.PROFILE -> profile?.name
    SettingsCategory.SOURCES -> services.takeIf { it.isNotEmpty() }?.let { list ->
        val missing = list.count { !it.configured }
        if (missing == 0) L("All connected", "Tout est connecté").text() else L("$missing to set up", "$missing à configurer").text()
    }
    SettingsCategory.INTERFACE_LANGUAGE -> prefs?.let { interfaceLanguageName(it.language) }
    else -> null
}

/** The person on a plaque at the top of the index: their initial, their name, and what pressing it does. */
@Composable
private fun UprightProfileCard(p: com.sokoul.pc.data.Profile, prefs: DesktopPrefs?, onProfiles: (() -> Unit)?) {
    var focused by remember { mutableStateOf(false) }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (focused) Palette.FocusFill else UprightPlaque)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = { onProfiles?.invoke() })
            .padding(horizontal = 22.dp, vertical = 20.dp).testTag("settings-profile-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
            Label(p.name.take(1).uppercase(), PanelType.Option.copy(fontSize = 30.sp), Color.White)
        }
        Column(Modifier.weight(1f)) {
            Label(p.name, PanelType.Option.copy(fontSize = 28.sp), if (focused) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
            val ask = prefs?.takeIf { it.profilePrompt }?.let { " · " + L("Ask at start", "Demander au lancement").text() } ?: ""
            Label(L("Switch profile", "Changer de profil").text() + ask, PanelType.Section.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium), if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Secondary, maxLines = 1)
        }
    }
}

/** One category pushed as its own page: the way back, its title and where it sits, then its groups of rows. */
@Composable
private fun UprightPage(
    nav: SettingsNavigation, prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?, onCredentials: () -> Unit, hooks: SettingsHooks,
    onBack: () -> Unit,
) {
    val category = nav.current.category
    var backFocused by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(bottom = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(if (backFocused) Palette.FocusFill else Color.Transparent)
            .tvFocusable(onFocusChanged = { backFocused = it }, onClick = onBack)
            .padding(start = 2.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).testTag("settings-back"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink = if (backFocused) Palette.OnFocus else Palette.OnBackground
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = ink, modifier = Modifier.size(36.dp))
        Label(L("Settings", "Réglages").text(), PanelType.Option.copy(fontSize = 25.sp, fontWeight = FontWeight.Medium), ink, maxLines = 1)
    }
    Label(category.label.text(), UprightTitle, modifier = Modifier.testTag("settings-title"))
    Label(nav.breadcrumb { it }.joinToString(" › "), PanelType.Section.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium), Palette.Secondary, Modifier.padding(bottom = 8.dp), maxLines = 1)
    CategoryContent(category, prefs, profile, onProfiles, onCredentials, hooks)
}

@Composable
private fun CategoryContent(
    category: SettingsCategory, prefs: DesktopPrefs?, profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?, onCredentials: () -> Unit, hooks: SettingsHooks,
) {
    when (category) {
        SettingsCategory.PROFILE -> ProfilePane(prefs, profile, onProfiles)
        SettingsCategory.PLAYBACK -> prefs?.let { PlaybackPane(it, hooks) }
        SettingsCategory.LANGUAGES -> prefs?.let { SubtitlesPane(it) }
        SettingsCategory.DISPLAY -> prefs?.let {
            HomePane(it, hooks)
            MenuPane(it, hooks, L("Menu", "Menu").text())
            AppearancePane(it, hooks)
        }
        SettingsCategory.SOURCES -> SourcesPane(onCredentials, hooks)
        SettingsCategory.LIVE_TV -> Unit
        SettingsCategory.INTERFACE_LANGUAGE -> prefs?.let { LanguagePane(it, hooks) }
        SettingsCategory.TOOLS -> ToolsPane(profile, hooks)
    }
}

/** The person's initial and name under the title; pressing it changes who is watching. */
@Composable
private fun ProfileHeader(profile: com.sokoul.pc.data.Profile?, onProfiles: (() -> Unit)?) {
    val name = profile?.name ?: return
    com.sokoul.pc.ui.PanelCard(
        Modifier.padding(top = 14.dp, bottom = 6.dp).testTag("settings-profile-header"),
        onClick = { onProfiles?.invoke() },
    ) { focused ->
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8C939F), Color(0xFF474A54)))),
            contentAlignment = Alignment.Center,
        ) { Label(name.take(1).uppercase(), PanelType.Option, Color.White) }
        Column {
            Label(name, PanelType.Option, if (focused) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
            Label(L("Switch profile", "Changer de profil").text(), PanelType.Callout, if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Secondary, maxLines = 1)
        }
    }
}

/** One category: its icon and name; it stays marked as the current one even when the focus has gone into its rows. */
@Composable
private fun CategoryRow(
    c: SettingsCategory, current: Boolean, requester: FocusRequester, fontSize: TextUnit, outlined: Boolean,
    onEnter: () -> Boolean, onSelect: () -> Unit,
) {
    com.sokoul.pc.ui.PanelOption(
        c.label.text(),
        Modifier.testTag("settings-pane-${c.id}")
            // On a tablet the category in use is a raised graphite plate with a silver hairline (Figma "Réglages · Catégorie").
            .let { if (outlined && current) it.border(1.dp, Color(0x47D8D8DE), androidx.compose.foundation.shape.RoundedCornerShape(com.sokoul.pc.theme.Corners.Row)) else it }
            .onPreviewKeyEvent { e -> e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && onEnter() },
        requester = requester,
        restFill = if (current) (if (outlined) Color(0x1AFFFFFF) else Glass.RowCurrent) else Color.Transparent,
        // 28 px like the Figma "Réglages/Catégorie" style: the longest name ("Languages and subtitles") fits the 440 dp column.
        titleStyle = PanelType.Option.copy(fontSize = fontSize, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium),
        titleLines = 2,
        // The keyboard shows a category as it reaches it; the mouse only by a click, so that crossing the list changes nothing.
        onFocused = { if (it && !com.sokoul.pc.ui.FocusSource.lastWasMouse) onSelect() },
        leading = { tint -> Icon(c.icon, null, tint = tint, modifier = Modifier.size(if (outlined) 34.dp else 32.dp)) },
        onClick = onSelect,
    )
}
