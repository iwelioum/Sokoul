package com.sokoul.pc.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import com.sokoul.pc.data.L
import com.sokoul.pc.ui.glassSurface
import com.sokoul.pc.ui.panelEnter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.sokoul.pc.theme.NavMetrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.FocusSource
import com.sokoul.pc.ui.Form
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.LocalActiveTab
import com.sokoul.pc.ui.NavIcons
import com.sokoul.pc.ui.tvFocusable
import com.sokoul.pc.ui.focusIfPresent

data class NavEntry(val id: String, val label: String, val icon: ImageVector)

const val SECTION_SEARCH = "search"
const val SECTION_SETTINGS = "settings"
const val SECTION_HOME = "home"

/** Whether the top bar is on screen; immersive TV pages reclaim its height while it is hidden. */
val LocalNavBarVisible = androidx.compose.runtime.compositionLocalOf { true }

internal val LocalReportHomeHeroAtTop = androidx.compose.runtime.compositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Top navigation: avatar on the left, sections in a floating translucent capsule in the middle, search on the right.
 * It stays above the page. Home reports whether its full-screen hero is at rest.
 */
@Composable
fun NavShell(
    entries: List<NavEntry>,
    active: String,
    onSelect: (String) -> Unit,
    defaultFocus: FocusRequester? = null,
    onBack: (() -> Unit)? = null,
    /** Opens "Who is watching?", the first line of the menu. */
    onProfiles: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // A tablet is held and touched: its bar stays on screen, as in the Apple TV and Disney+ apps on an iPad. Hidden as on
    // the TV, it had to be called back with the remote's Up key, which a tablet has not (2026-11-23).
    val immersive = com.sokoul.pc.Platform.IS_TV && !Form.isPhone && !Form.tablet
    // Every form has the Apple TV bar, the menu and search, a phone held upright too: its bottom tabs covered the
    // hero's button and dots, and turning the phone changed the whole navigation (the user, 2026-11-24).
    var menuOpen by remember { mutableStateOf(false) }
    // How far the page has scrolled, read from the scrolls that pass through the shell: the bar's black backdrop
    // fades in once the page has left its top, and out when it is back.
    var scrolled by remember(active) { mutableStateOf(0f) }
    // The way the finger or the wheel last went: up brings the bar back at once, as in Safari or YouTube.
    var scrollingUp by remember(active) { mutableStateOf(false) }
    val scrollWatch = remember(active) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (consumed.y != 0f) scrollingUp = consumed.y > 0f
                scrolled = (scrolled - consumed.y).coerceAtLeast(0f)
                // Pulled past the top: the page is at its top, whatever the sum of the moves said (it drifted, and the
                // veil stayed behind the icons at the top of the page).
                if (available.y > 0f && consumed.y == 0f) scrolled = 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    val homeControlsBar = active == SECTION_HOME && defaultFocus != null && !Form.tablet
    var homeHeroAtTop by remember(active) { mutableStateOf(true) }
    // At the top of the page: Home says it (its hero at rest), the other pages by their scroll.
    val atTop = if (homeControlsBar) homeHeroAtTop else scrolled <= SCROLL_SHOW_PX
    LaunchedEffect(atTop) { if (atTop) { scrolled = 0f; scrollingUp = false } }
    val backdrop by animateFloatAsState(if (!atTop || menuOpen) 1f else 0f, tween(260), label = "bar-backdrop")
    var homeNavRequested by remember(active) { mutableStateOf(false) }
    var barFocused by remember(active) { mutableStateOf(false) }
    var timedBarVisible by remember(active) { mutableStateOf(true) }
    LaunchedEffect(active, barFocused) {
        if (barFocused || !immersive) timedBarVisible = true
        else {
            delay(if (active == "tv") 700 else 1200)
            timedBarVisible = false
        }
    }
    /*
     * The bar (owner, 2026-11-24): at the top of a page, the two icons over the picture; going down, it leaves so the page
     * has the whole screen; on a touch screen or with a mouse, going up a little brings it back on its veil. On the TV
     * Home it is there at the top (it was hidden there and only came once the rows were reached); Up from the hero, or
     * Back, reaches it. The other TV pages keep the quick hiding they had.
     */
    val barVisible = when {
        menuOpen || barFocused || homeNavRequested -> true
        homeControlsBar -> homeHeroAtTop || (!immersive && scrollingUp)
        immersive -> timedBarVisible
        else -> atTop || scrollingUp
    }
    val reportHomeHeroAtTop: (Boolean) -> Unit = remember(active) { { homeHeroAtTop = it } }
    LaunchedEffect(barFocused) {
        if (homeNavRequested && !barFocused) homeNavRequested = false
    }
    val visibilityState = animateFloatAsState(if (barVisible) 1f else 0f, tween(220))
    val visibility by visibilityState
    // Whether the bar is drawn at all changes twice per animation; read as the float, the shell recomposed at each frame.
    val barDrawn by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { visibilityState.value > 0.01f } }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }
    // Up from a page reaches the menu button: there are no tabs on the bar any more.
    val homeNavTarget = MENU

    LaunchedEffect(homeNavRequested) {
        if (homeNavRequested && !requester(homeNavTarget).focusIfPresent()) {
            homeNavRequested = false
        }
    }

    // The page's first element when it exists, otherwise the section's own tab: focus is never lost.
    LaunchedEffect(active, defaultFocus) {
        if (runCatching { defaultFocus?.requestFocus() ?: error("none") }.isFailure && !homeControlsBar) {
            runCatching { requester(MENU).requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                // Back anywhere on Home, or Up while the bar is away: straight to the menu icon.
                if (homeControlsBar && e.type == KeyEventType.KeyDown && !barFocused && !menuOpen &&
                    // Up from the hero has nothing above it but the bar: always the menu icon, never the search one.
                    (e.key == Key.Escape || (e.key == Key.DirectionUp && homeHeroAtTop))
                ) {
                    FocusSource.lastWasMouse = false
                    homeNavRequested = true
                    true
                } else false
            }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && (onBack != null || (immersive && !barVisible))) {
                    // A page that can go back does so on the first press, even with the bar hidden: revealing the
                    // bar first made Back from the TV guide take two presses to reach Home.
                    if (immersive && !barVisible && onBack == null) {
                        timedBarVisible = true
                        runCatching { requester(MENU).requestFocus() }
                        return@onKeyEvent true
                    }
                    onBack?.invoke()
                    true
                } else {
                    false
                }
            },
    ) {
        Box(Modifier.fillMaxSize().nestedScroll(scrollWatch)) {
            CompositionLocalProvider(
                LocalActiveTab provides requester(homeNavTarget),
                LocalNavBarVisible provides barVisible,
                LocalReportHomeHeroAtTop provides reportHomeHeroAtTop,
            ) { content() }
        }
        if (barDrawn || barVisible) {
            val onBarFocus: (FocusState) -> Unit = {
                barFocused = it.hasFocus
                if (it.hasFocus) timedBarVisible = true
            }
            // At the top of a page only the two icons show, over the picture; scrolled, a black veil comes behind them.
            Box(
                Modifier.fillMaxWidth().height(NavMetrics.ScrimHeight)
                    .graphicsLayer { alpha = visibility * backdrop }
                    // Opaque down to under the icons, then fading: the hero's text showed through the lighter veil
                    // behind the menu icon when the page was not at its top (PC, 2026-11-24).
                    .background(Brush.verticalGradient(0f to Color.Black, 0.72f to Color(0xF5000000), 1f to Color.Transparent)),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = NavMetrics.Top)
                    .graphicsLayer { alpha = visibility; translationY = -(NavMetrics.Top + NavMetrics.Height).toPx() * (1f - visibility) }
                    // Leaving, it is not reachable: the remote's Up stays in the page.
                    .focusProperties { canFocus = barVisible }
                    .onFocusChanged(onBarFocus)
                    .onPreviewKeyEvent { e ->
                        // Down always returns to the page.
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && defaultFocus != null && !menuOpen) {
                            FocusSource.lastWasMouse = false
                            scope.launch {
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { defaultFocus.requestFocus() }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    .focusGroup(),
            ) {
                BarIcon(
                    onClick = { menuOpen = !menuOpen },
                    requester = requester(MENU),
                    tag = "menu-open",
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = NavMetrics.Side),
                ) { tint -> Icon(Icons.Filled.Menu, L("Menu", "Menu").text(), tint = tint, modifier = Modifier.size(NavMetrics.Icon + 4.dp)) }
                BarIcon(
                    onClick = { onSelect(SECTION_SEARCH) },
                    requester = requester(SECTION_SEARCH),
                    tag = "menu-$SECTION_SEARCH",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = NavMetrics.Side),
                ) { tint -> Icon(NavIcons.Search, L("Search", "Rechercher").text(), tint = tint, modifier = Modifier.size(NavMetrics.Icon + 2.dp)) }
            }
        }
        if (menuOpen) SectionMenu(
            entries, active,
            onPick = { id -> menuOpen = false; onSelect(id) },
            onProfiles = { menuOpen = false; onProfiles() },
            onClose = {
                menuOpen = false
                scope.launch { androidx.compose.runtime.withFrameNanos { }; runCatching { requester(MENU).requestFocus() } }
            },
        )
    }
}

/** A round icon of the bar: a dark translucent disc over the picture, white when focused, as on the Apple TV app. */
@Composable
private fun BarIcon(onClick: () -> Unit, requester: FocusRequester, tag: String, modifier: Modifier, icon: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Color(0x59000000), tween(140))
    val scale by animateFloatAsState(if (focused) 1.1f else 1f)
    Box(
        modifier
            .size(NavMetrics.Height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fill)
            .testTag(tag)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester),
        contentAlignment = Alignment.Center,
    ) { icon(if (focused) Palette.OnFocus else Palette.OnBackground) }
}

/**
 * The menu behind the bar's left icon: the profile first, then every page, then the settings, in a list that scrolls.
 * Focus starts on the page shown; Back closes.
 */
@Composable
private fun SectionMenu(entries: List<NavEntry>, active: String, onPick: (String) -> Unit, onProfiles: () -> Unit, onClose: () -> Unit) {
    val here = remember { FocusRequester() }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { here.requestFocus() } }
    val profile = com.sokoul.pc.data.LocalProfile.current
    val name = profile?.name ?: remember { com.sokoul.pc.data.Profiles.defaultName() }
    val own = profile?.let { Color(com.sokoul.pc.data.Profiles.COLORS[it.color % com.sokoul.pc.data.Profiles.COLORS.size]) } ?: Palette.AvatarFill
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false }
            .testTag("menu-list"),
    ) {
        Column(
            Modifier.padding(start = NavMetrics.Side, top = NavMetrics.Top + NavMetrics.Height + 14.dp).width(MENU_WIDTH)
                .heightIn(max = 900.dp)
                .panelEnter(com.sokoul.pc.ui.PanelEntry.FromStart)
                .glassSurface(RoundedCornerShape(com.sokoul.pc.theme.Corners.Modal), com.sokoul.pc.theme.Corners.Modal)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            com.sokoul.pc.ui.PanelOption(
                name, Modifier.testTag("menu-profile"), subtitle = L("Change profile", "Changer de profil").text(),
                leadingWidth = 52.dp, leadingHeight = 52.dp,
                leading = { _ ->
                    Box(Modifier.size(52.dp).clip(CircleShape).background(own), contentAlignment = Alignment.Center) {
                        Label(name.take(1).uppercase(), Type.Pill, Palette.OnBackground)
                    }
                },
                onClick = onProfiles,
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Palette.Hairline))
            entries.forEach { e ->
                com.sokoul.pc.ui.PanelOption(
                    e.label, Modifier.testTag("menu-${e.id}"), selected = e.id == active,
                    requester = if (e.id == active || (active !in entries.map { it.id } && e === entries.first())) here else remember { FocusRequester() },
                    leading = { ink -> Icon(e.icon, null, tint = ink, modifier = Modifier.size(30.dp)) },
                ) { onPick(e.id) }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Palette.Hairline))
            com.sokoul.pc.ui.PanelOption(
                L("Settings", "Réglages").text(), Modifier.testTag("menu-$SECTION_SETTINGS"), selected = active == SECTION_SETTINGS,
                leading = { ink -> Icon(Icons.Filled.Settings, null, tint = ink, modifier = Modifier.size(30.dp)) },
            ) { onPick(SECTION_SETTINGS) }
        }
    }
}

private const val MENU = "menu"
private val MENU_WIDTH = 560.dp
/** Pixels scrolled before the bar's backdrop shows. */
private const val SCROLL_SHOW_PX = 40f


@Composable
private fun Tab(entry: NavEntry, isActive: Boolean, onClick: () -> Unit, requester: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        when {
            focused -> Palette.FocusFill
            isActive -> Palette.ActiveFill
            else -> Color.Transparent
        },
    )
    val scale by animateFloatAsState(if (focused) 1.05f else 1f)
    val tint = when {
        focused -> Palette.OnFocus
        isActive -> Palette.OnBackground
        else -> Palette.BarText
    }
    Row(
        Modifier
            .height(NavMetrics.Height - 12.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(fill)
            .testTag("menu-${entry.id}")
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(entry.icon, null, tint = tint, modifier = Modifier.size(NavMetrics.Icon))
        Spacer(Modifier.width(11.dp))
        Label(entry.label, Type.Nav, tint, maxLines = 1)
    }
}

@Composable
private fun RoundBarButton(icon: ImageVector, onClick: () -> Unit, isActive: Boolean, requester: FocusRequester, tag: String, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        when {
            focused -> Palette.FocusFill
            isActive -> Palette.ActiveFill
            else -> Palette.BarFill
        },
    )
    Box(modifier) {
        Box(
            Modifier
                .size(NavMetrics.Height)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, Palette.Hairline, CircleShape)
                .testTag(tag)
                .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) Palette.OnFocus else Palette.OnBackground, modifier = Modifier.size(NavMetrics.Icon + 2.dp))
        }
    }
}

/** The profile's initial on its colour; it leads to the settings. */
@Composable
private fun Avatar(onClick: () -> Unit, isActive: Boolean, requester: FocusRequester, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val profile = com.sokoul.pc.data.LocalProfile.current
    val name = profile?.name ?: remember { com.sokoul.pc.data.Profiles.defaultName() }
    val own = profile?.let { androidx.compose.ui.graphics.Color(com.sokoul.pc.data.Profiles.COLORS[it.color % com.sokoul.pc.data.Profiles.COLORS.size]) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f)
    val fill by animateColorAsState(if (focused) Palette.FocusFill else if (isActive) Palette.ActiveFill else own ?: Palette.AvatarFill)
    Box(modifier) {
        Box(
            Modifier
                .size(NavMetrics.Height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(fill)
                .testTag("menu-$SECTION_SETTINGS")
                .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester),
            contentAlignment = Alignment.Center,
        ) {
            Label(name.take(1).uppercase(), Type.Pill, if (focused) Palette.OnFocus else Palette.OnBackground)
        }
    }
}
