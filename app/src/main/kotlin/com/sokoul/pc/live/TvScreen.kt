package com.sokoul.pc.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.Program
import com.sokoul.desktop.data.SpecialGroup
import com.sokoul.pc.AppDeps
import com.sokoul.pc.Platform
import com.sokoul.pc.data.L
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.playback.session.LiveChannel
import com.sokoul.pc.playback.ui.clock
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.FocusSource
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.LocalPinch
import com.sokoul.pc.ui.VideoFill
import com.sokoul.pc.ui.rememberHasFrames
import com.sokoul.pc.ui.tvFocusable
import com.sokoul.pc.ui.TvActionRow
import com.sokoul.pc.ui.TvPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SELECT_DELAY_MS = 350L
private const val PREVIEW_DELAY_MS = 1_200L
private const val FOCUS_BACK_MS = 120L
private const val CLOCK_TICK_MS = 15_000L
private const val ZOOM_MS = 420
private const val IDLE_MS = 15 * 60_000L
private val HERO_GUIDE = FormulerGuideStyle.headerHeight
private val HERO_GUIDE_TV = FormulerGuideStyle.headerHeight
private val HERO_PANORAMA = 214.dp
/** Groups offered as chips on a tablet held upright; the rest is one tap away in the group list. */
private val FULL_HEIGHT = 1080.dp

/**
 * The live channels and their guide are one page. The focused programme fills the top with its channel playing muted;
 * below, the guide. Left from what is on now pulls out the groups as a drawer that pushes the guide aside. Pinching
 * changes how much is seen: together for a panorama of the evening, apart for the picture to grow to full screen.
 */
@Composable
fun TvScreen(
    deps: AppDeps,
    firstRow: FocusRequester,
    active: Boolean,
    onPlay: (LiveChannel, List<LiveChannel>) -> Unit,
    onPlayProvider: (String, com.sokoul.desktop.player.StreamRequest, String, String?, () -> Boolean) -> Unit = { key, stream, title, _, allowed -> deps.playback.playDirect(key, stream, title, allowed) },
    onDownloadProvider: (com.sokoul.desktop.player.StreamRequest, String, String) -> Unit = { stream, key, title ->
        deps.notice = com.sokoul.pc.data.L("Open the TV workspace on the app window to manage downloads.", "Ouvrez l’espace TV dans l’application pour gérer les téléchargements.").text()
    },
    onSports: (() -> Unit)? = null,
) {
    val tv = deps.tv
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var searchWasOpen by remember { mutableStateOf(false) }
    /** The control row under the banner: Up from the guide's first row lands here before the navigation bar. */
    val controlsFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    val television = Platform.IS_TV && !com.sokoul.pc.ui.Form.tablet && !com.sokoul.pc.ui.Form.isPhone
    val activeTab = com.sokoul.pc.ui.LocalActiveTab.current
    var addReturn by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var revision by remember { mutableStateOf(0) }
    val personalRevision by deps.personalTv.revision.collectAsState()
    val loadedGroups by produceState<List<LiveLogic.Group>?>(null, revision, personalRevision, deps.profiles.current.id) {
        // The first synchronization can still be running: the list fills in as soon as it is done.
        while (true) {
            val current = withContext(Dispatchers.IO) { deps.personalTv.groups() }
            value = current
            if (current.any { it.id != SpecialGroup.ALL && it.id != SpecialGroup.FAVORITES && it.id != TvCatalog.RECENT } ||
                withContext(Dispatchers.IO) { tv.playlist() } == null) break
            withContext(Dispatchers.IO) { delay(5_000) }
        }
    }
    val groups = loadedGroups.orEmpty()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    // Back where it was left: the page is a television, not a menu.
    var restoreChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreProgramStart by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(if (Platform.IS_TV) groups.isNotEmpty() else active, groups.isNotEmpty(), deps.profiles.current.id) {
        if (!active) return@LaunchedEffect
        val last = withContext(Dispatchers.IO) { tv.lastPosition() } ?: return@LaunchedEffect
        if (groups.any { it.id == last.group }) {
            selected = last.group
            restoreChannel = last.channelId
            restoreProgramStart = last.programStart
        }
    }
    val group = selected?.takeIf { id -> groups.any { it.id == id } }
        ?: groups.firstOrNull { it.id != SpecialGroup.FAVORITES && it.id != TvCatalog.RECENT && it.id != SpecialGroup.ALL }?.id ?: groups.firstOrNull()?.id
    val collectionId = TvPersonalization.collectionId(group)
    val channelPage by produceState<Pair<String?, List<Channel>?>>(null to null, group,
        if (group == SpecialGroup.FAVORITES || group == TvCatalog.RECENT) revision else 0, if (collectionId != null) personalRevision else 0L) {
        val loaded = if (group == null) emptyList() else withContext(Dispatchers.IO) {
            if (collectionId != null) deps.personalTv.channels(collectionId) else tv.channels(group)
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        value = group to loaded
    }
    val channels = channelPage.second
    val groupPending = channelPage.first != group
    var favorites by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(revision) { favorites = withContext(Dispatchers.IO) { tv.favorites() } }
    fun favorite(channel: Channel) {
        if (!Platform.IS_TV) { tv.toggleFavorite(channel.id); revision++; return }
        val wanted = channel.id !in favorites
        favorites = if (wanted) favorites + channel.id else favorites - channel.id
        scope.launch {
            withContext(Dispatchers.IO) { tv.setFavorite(channel.id, wanted) }
            revision++
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        // Tests leave real mouse tracking disabled so the composition can reach idle.
        if (!active || !FocusSource.trackRealMouse) return@LaunchedEffect
        now = System.currentTimeMillis()
        while (true) {
            delay(CLOCK_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    var savedGuideRow by rememberSaveable { mutableIntStateOf(0) }
    var savedGuideAnchor by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val focus = remember { GuideFocus().apply { row = savedGuideRow; anchor = savedGuideAnchor } }
    LaunchedEffect(focus.row) { savedGuideRow = focus.row }
    LaunchedEffect(focus.anchor) { savedGuideAnchor = focus.anchor }
    var previousGroup by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(group, channelPage.first) {
        if (groupPending) return@LaunchedEffect
        if (previousGroup != null && previousGroup != group) {
            focus.row = 0
            focus.channelId = null
            focus.anchor = System.currentTimeMillis()
        }
        previousGroup = group
    }
    LaunchedEffect(channels, restoreChannel) {
        val list = channels ?: return@LaunchedEffect
        val wanted = restoreChannel ?: return@LaunchedEffect
        list.indexOfFirst { it.id == wanted }.takeIf { it >= 0 }?.let {
            focus.row = it
            if (Platform.IS_TV) focus.channelId = wanted
        }
        restoreProgramStart?.let { focus.anchor = it }
        restoreChannel = null
        restoreProgramStart = null
    }
    var workspace by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionSearch by remember { mutableStateOf<String?>(null) }
    var groupPicker by remember { mutableStateOf<GroupChannelRequest?>(null) }
    var contextOkHeld by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    val workspaceOpen = workspace != null || scheduling != null || groupPicker != null
    // After the player: on the channel it ended on, even after zapping through others.
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(active, workspaceOpen) {
        if (!active) wasCovered = true
        if (active && (!Platform.IS_TV || wasCovered)) deps.playback.lastLiveKey?.let { key -> channels?.indexOfFirst { it.id == key }?.takeIf { it >= 0 }?.let {
            focus.row = it
            if (Platform.IS_TV) focus.channelId = key
        } }
        // Back from the player: the remote goes on in the guide, not nowhere.
        if (active && wasCovered && !workspaceOpen) {
            delay(FOCUS_BACK_MS)
            runCatching { firstRow.requestFocus() }
            if (Platform.IS_TV) wasCovered = false
        }
    }
    // The tab may receive the initial focus while channels are still loading. Enter the guide once its focus target exists.
    LaunchedEffect(active, channels != null, workspaceOpen) {
        if (active && channels != null && !wasCovered && !workspaceOpen) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { firstRow.requestFocus() }
        }
    }
    var searching by remember { mutableStateOf<String?>(null) }
    val schedules = remember { mutableStateMapOf<String, List<Program>>() }
    val shown = channels?.getOrNull(focus.row)
    val program = shown?.let { GuideLogic.programAt(schedules[it.id].orEmpty(), focus.anchor) }

    var level by remember { mutableStateOf(GuideLevel.Guide) }
    /** Direct (the list of what is on, as on the mock-up) or Guide (the grid); Direct first. */
    var view by rememberSaveable { mutableStateOf(TvView.Direct) }
    /** The "More" menu: reminders, recording, My TV, TV settings. */
    var moreOpen by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var watchlistOpen by remember { mutableStateOf(false) }
    val watchlist = deps.reminders.upcoming(now)
    var guideFocused by remember { mutableStateOf(false) }
    val heroHeight = remember { Animatable((if (Platform.IS_TV) HERO_GUIDE_TV else HERO_GUIDE).value) }
    val guideShown = remember { Animatable(1f) }
    val drawer = remember { Animatable(0f) }
    LaunchedEffect(drawerOpen) {
        drawer.animateTo(if (drawerOpen) 1f else 0f,
            if (Platform.IS_TV) tween(FormulerGuideStyle.panelMs) else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }
    val targetHero = when {
        groups.isEmpty() -> HERO_GUIDE
        television -> HERO_GUIDE_TV
        view == TvView.Direct -> directHeaderHeight()
        Platform.IS_TV -> HERO_GUIDE_TV
        level == GuideLevel.Guide -> HERO_GUIDE
        else -> HERO_PANORAMA
    }
    LaunchedEffect(active, targetHero) {
        if (active) {
            launch { guideShown.animateTo(1f, tween(ZOOM_MS)) }
            // Direct is immersive: the channel playing takes the top of the page; the Guide keeps the grid's room.
            heroHeight.animateTo(targetHero.value, tween(ZOOM_MS, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(active, drawerOpen, workspaceOpen, moreOpen, if (Platform.IS_TV) searching else null, if (Platform.IS_TV) groupPending else false) {
        if (active && !drawerOpen && !workspaceOpen && !moreOpen && (!Platform.IS_TV || (searching == null && !groupPending))) {
            if (Platform.IS_TV) androidx.compose.runtime.withFrameNanos { }
            runCatching { firstRow.requestFocus() }
        }
    }

    // The muted preview waits a moment, so that going through a list never opens a stream per channel.
    val previews = deps.prefs.livePreview
    val asleep = deps.playback.previewAsleep
    val browsingBlocked = drawerOpen || workspaceOpen || moreOpen || searching != null || groupPending
    val inFront by com.sokoul.pc.ui.AppFocus.active.collectAsState()
    LaunchedEffect(shown?.id, if (Platform.IS_TV) focus.anchor else 0L, if (Platform.IS_TV) group else null, active, previews, asleep, browsingBlocked, inFront) {
        val channel = shown
        // Behind another window the muted preview only costs a connection and the decoder.
        if (!inFront) { deps.playback.stopPreview(); return@LaunchedEffect }
        if (channel == null || !active || !previews || asleep || browsingBlocked) return@LaunchedEffect
        if (Platform.IS_TV) {
            settledGuidePreview({ withContext(Dispatchers.IO) { tv.toLive(channel) } }, deps.playback::preview)
        } else {
            delay(PREVIEW_DELAY_MS)
            withContext(Dispatchers.IO) { tv.toLive(channel) }?.let(deps.playback::preview)
        }
        // Nobody touched anything for a long time: the account's only connection is not kept for an empty room.
        withContext(Dispatchers.Default) { delay(IDLE_MS) }
        deps.playback.sleepPreview()
    }
    DisposableEffect(Unit) { onDispose { deps.playback.stopPreview() } }

    val watch: (Channel) -> Unit = { c ->
        // Carry the guide data already on screen into the player. A fresh lookup there can miss
        // the just-loaded EPG window while the guide still shows the correct current programme.
        val loadedSchedules = schedules.toMap()
        scope.launch {
        val sourceGroup = if (channels.orEmpty().any { it.id == c.id }) group else c.categoryId
        val around = withContext(Dispatchers.IO) { tv.toLive(if (sourceGroup == group) channels.orEmpty() else tv.channels(c.categoryId)) }.map { channel ->
            channel.copy(schedule = {
                (loadedSchedules[channel.key].orEmpty() + channel.schedule())
                    .distinctBy { it.channelKey to it.start }
                    .sortedBy { it.start }
            })
        }
        around.firstOrNull { it.key == c.id }?.let { channel ->
            scope.launch(Dispatchers.IO) {
                if (!Platform.IS_TV) tv.noteWatched(c.id)
                tv.rememberPosition(sourceGroup, c.id)
            }
            onPlay(channel, around)
        }
        }
    }

    // Spreading the fingers makes the picture grow until it is the player, with the same stream going on.
    val pinch = LocalPinch.current
    DisposableEffect(pinch, active, level, shown?.id, drawerOpen) {
        if (active && !drawerOpen && !Platform.IS_TV) {
            pinch.screenOut = { if (level == GuideLevel.Guide) level = GuideLevel.Panorama else pinch.back?.invoke() }
            pinch.screenIn = {
                if (level == GuideLevel.Panorama) {
                    level = GuideLevel.Guide
                } else {
                    shown?.let { c ->
                        scope.launch {
                            launch { guideShown.animateTo(0f, tween(ZOOM_MS)) }
                            heroHeight.animateTo(FULL_HEIGHT.value, tween(ZOOM_MS, easing = FastOutSlowInEasing))
                            watch(c)
                        }
                    }
                }
            }
        }
        onDispose {
            pinch.screenIn = null
            pinch.screenOut = null
        }
    }

    // Read here and not in the effect: an effect's reads ask for no new composition, and the report would stay behind.
    val report = TvReport(group, level.name, drawerOpen, focus.row, channels?.size ?: 0, shown?.name, program?.title, guideFocused)
    SideEffect { deps.tvReport = report }

    // The guide takes the whole screen while the navigation bar is hidden, and makes room when it comes back.
    val barShown = com.sokoul.pc.shell.LocalNavBarVisible.current
    val topSpace by androidx.compose.animation.core.animateDpAsState(
        if (barShown && !com.sokoul.pc.ui.Form.isPortrait) 118.dp else 24.dp, tween(220), label = "tv-top")
    BoxWithConstraints(Modifier.fillMaxSize().background(FormulerGuideStyle.background).testTag("tv")) {
        val portrait = com.sokoul.pc.ui.Form.tabletPortrait || com.sokoul.pc.ui.Form.isPortrait
        val drawerWidth = if (portrait) (maxWidth - 48.dp).coerceAtLeast(0.dp) else minOf(TV_COLUMN_WIDTH, maxWidth)
        val drawerTop = topSpace + 12.dp
        val drawerDirection = if (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) -1f else 1f
        Column(
            Modifier.fillMaxSize()
                .padding(top = topSpace)
                // The covered guide must not regain keyboard/hover focus while a workspace owns input.
                .focusProperties { canFocus = !workspaceOpen && !browsingBlocked }
                .onPreviewKeyEvent { browsingBlocked },
        ) {
            Box(
                Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val h = heroHeight.value.dp.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                },
            ) {
                val channel = shown
                if (channel == null) {
                    Column(Modifier.align(Alignment.CenterStart).padding(horizontal = Metrics.PageMargin),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label(L("Live TV", "Télévision en direct").text(), Type.RowTitle.copy(fontSize = 64.sp))
                        Label(if (loadedGroups == null || groupPending) L("Loading channels…", "Chargement des chaînes…").text()
                            else if (groups.isEmpty()) tr(Text.LiveNoPlaylist) else tr(Text.LiveNoChannels),
                            Type.Body, Palette.Secondary, maxLines = 2)
                    }
                } else if (view == TvView.Direct) {
                    TvEditorialHero(deps, channel, program, now, watchFocus, immersive = !television) { watch(channel) }
                } else if (Platform.IS_TV || level == GuideLevel.Guide) {
                    TvEditorialHero(deps, channel, program, now, watchFocus) { watch(channel) }
                } else {
                    Hero(deps, channel, program, channel.id in favorites, now, level, active)
                }
            }
            // Without a provider there is nothing to sort or jump through: the row would only say "0 channels".
            if (groups.isNotEmpty()) TvControlRow(
                groupName = groups.firstOrNull { it.id == group }?.let { if (Platform.IS_TV) columnGroupName(it) else TvCatalog.label(groupName(it)) } ?: L("Groups", "Groupes").text(),
                count = channels?.size,
                view = view,
                groupRequester = when {
                    channels.isNullOrEmpty() -> firstRow
                    television -> null
                    else -> controlsFocus
                },
                viewRequester = controlsFocus.takeIf { television && !channels.isNullOrEmpty() },
                modifier = Modifier
                    // Up visits the watch action before returning to the navigation bar.
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            e.key == Key.DirectionDown && television && !channels.isNullOrEmpty() -> {
                                runCatching { firstRow.requestFocus() }; true
                            }
                            e.key == Key.DirectionUp && activeTab != null -> {
                                val target = if (shown != null && (Platform.IS_TV || level == GuideLevel.Guide)) watchFocus else activeTab
                                runCatching { target.requestFocus() }; true
                            }
                            else -> false
                        }
                    },
                onGroups = { watchlistOpen = false; drawerOpen = !drawerOpen },
                onView = { v ->
                    view = v
                    if (v == TvView.Direct) focus.anchor = System.currentTimeMillis()
                    if (television && !channels.isNullOrEmpty()) scope.launch {
                        androidx.compose.runtime.withFrameNanos { }
                        runCatching { firstRow.requestFocus() }
                    }
                },
                onNow = { focus.anchor = System.currentTimeMillis() },
                onTonight = { focus.anchor = GuideLogic.jumpTarget(shown?.let { schedules[it.id] }.orEmpty(), GuideLogic.tonight(System.currentTimeMillis())) },
                onTomorrow = { focus.anchor = GuideLogic.jumpTarget(shown?.let { schedules[it.id] }.orEmpty(), GuideLogic.tomorrowEvening(System.currentTimeMillis())) },
            ) {
                IconPill(Icons.Filled.Search, L("Search", "Rechercher").text(), "tv-search-open") { searching = "" }
                com.sokoul.pc.ui.PanelChip(L("More", "Plus").text(), Modifier.testTag("tv-more"), icon = Icons.Filled.MoreHoriz) { moreOpen = true }
            }
            Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = guideShown.value }) {
                val list = channels
                when {
                    groups.isEmpty() -> Unit
                    list != null && list.isEmpty() -> {
                        Column(Modifier.padding(start = Metrics.PageMargin, top = 60.dp).width(1000.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Label(if (group == SpecialGroup.FAVORITES) L("Your favorite channels belong here", "Vos chaînes favorites vous attendent ici").text() else tr(Text.LiveNoChannels), Type.Meta, Palette.Secondary)
                            if (Platform.IS_TV) {
                                Label(L("Hold OK on a channel to add it to favorites or a collection.", "Maintenez OK sur une chaîne pour l’ajouter aux favoris ou à une collection.").text(), Type.Body, Palette.Secondary)
                                TvActionRow(L("Choose another group", "Choisir un autre groupe").text()) { watchlistOpen = false; drawerOpen = true }
                                TvActionRow(L("TV settings", "Réglages TV").text()) { focusManager.clearFocus(force = true); workspace = "settings" }
                            }
                        }
                    }
                    list != null && view == TvView.Direct -> TvDirectList(
                        tv, list, focus.row, favorites, now, firstRow, controlsFocus,
                        onSchedules = { schedules.putAll(it) },
                        onFocusRow = { focus.row = it; guideFocused = true },
                        onWatch = { c -> watch(c) },
                        onFavorite = ::favorite,
                        onOpenDrawer = { watchlistOpen = false; drawerOpen = true },
                        onOpenActions = { workspace = "guide-actions" },
                    )
                    list != null -> GuideGrid(
                        tv, list, focus, favorites, level, now, firstRow,
                        schedule = { schedules[it].orEmpty() },
                        onSchedules = { schedules.putAll(it) },
                        reminded = deps.reminders::has,
                        onActivity = deps.playback::wakePreview,
                        onSearch = { searching = it },
                        // Left always means the groups, even if the reminders drawer was the last one shown.
                        onOpenDrawer = { watchlistOpen = false; drawerOpen = true },
                        onOpenActions = { workspace = "guide-actions" },
                        onLongOk = { contextOkHeld = true; workspace = "guide-actions" },
                        onOpenDetails = { workspace = "guide-details" },
                        onWatch = { c ->
                            // Nothing to watch yet in a programme to come: it is asking to be told.
                            val p = GuideLogic.programAt(schedules[c.id].orEmpty(), focus.anchor)
                            if (p != null && p.start > System.currentTimeMillis()) deps.reminders.toggle(Reminder(c.id, TvCatalog.shortLabel(c.name), p.title, p.start)) else watch(c)
                        },
                        onFavorite = ::favorite,
                        onFocusChanged = { guideFocused = it },
                        loadingEnabled = !browsingBlocked,
                        upTarget = controlsFocus,
                    )
                }
                // A short group leaves most of the screen empty: say why, and where the rest is.
                val shortList = channels
                if (Platform.IS_TV && shortList != null && shortList.size in 1..4) {
                    Column(Modifier.align(Alignment.BottomStart).padding(start = Metrics.PageMargin, bottom = 56.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Label((if (shortList.size == 1) L("1 channel in this group", "1 chaîne dans ce groupe")
                            else L("${shortList.size} channels in this group", "${shortList.size} chaînes dans ce groupe")).text(),
                            Type.Meta, FormulerGuideStyle.secondary, maxLines = 1)
                        KeyHint("◀", L("Open your other groups", "Ouvrir vos autres groupes").text())
                    }
                }
            }
        }
        if (drawerOpen) {
            Box(Modifier.fillMaxSize().padding(top = topSpace).background(FormulerGuideStyle.scrim.copy(alpha = .54f))
                .pointerInput(Unit) { detectTapGestures(onTap = { drawerOpen = false }) })
        }
        if (moreOpen) {
            Box(
                Modifier.fillMaxSize().background(com.sokoul.pc.theme.Glass.Scrim)
                    .pointerInput(Unit) { detectTapGestures(onTap = { moreOpen = false }) }
                    .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { moreOpen = false; true } else false },
            ) {
                TvPanel(L("TV actions", "Actions TV").text(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = drawerTop, start = 20.dp, end = 20.dp).testTag("tv-more-menu"),
                    width = 690.dp, action = { com.sokoul.pc.ui.PanelChip(L("Close", "Fermer").text()) { moreOpen = false } }) {
                    TvActionRow(L("On air now", "À l’antenne").text(), L("Programmes airing on your channels", "Programmes diffusés sur vos chaînes").text(), Modifier.testTag("tv-discover")) {
                        moreOpen = false; workspace = "discover"
                    }
                    TvActionRow(L("Reminders", "Rappels").text(), L("Programmes you marked to watch later", "Émissions que vous avez marquées pour plus tard").text(), Modifier.testTag("tv-watchlist-open"),
                        trailing = watchlist.size.takeIf { it > 0 }?.toString()) { moreOpen = false; watchlistOpen = true; drawerOpen = true }
                    if (shown != null && program != null && program.end > now) {
                        TvActionRow(L("Record this programme", "Enregistrer l’émission").text(), program.title, Modifier.testTag("tv-record-programme")) { moreOpen = false; scheduling = shown to program }
                    }
                    TvActionRow(L("Recordings", "Enregistrements").text(), L("Scheduled, active and saved programmes", "Programmes planifiés, en cours et enregistrés").text(), Modifier.testTag("tv-recordings")) { moreOpen = false; focusManager.clearFocus(force = true); workspace = "recordings" }
                    TvActionRow(L("TV settings", "Réglages TV").text(), L("Zapping, guide order, preview and your provider", "Zapping, ordre du guide, aperçu et fournisseur").text(), Modifier.testTag("tv-settings-open")) {
                        moreOpen = false; focusManager.clearFocus(force = true); workspace = "settings"
                    }
                }
            }
        }
        // Read through a derived state: read directly, the slide's value recomposed the whole page at each of its frames.
        val drawerShown by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { drawer.value > 0f } }
        if ((drawerShown || drawerOpen) && !watchlistOpen && !workspaceOpen) {
            TvGroupColumn(
                groups, group,
                Modifier.padding(start = if (portrait) 24.dp else 16.dp, top = drawerTop, bottom = 20.dp)
                    .width(drawerWidth).fillMaxHeight().graphicsLayer { translationX = (drawer.value - 1f) * drawerWidth.toPx() * drawerDirection },
                onApply = { selected = it; drawerOpen = false },
                onGroupActions = { id -> drawerOpen = false; focusManager.clearFocus(force = true); groupPicker = GroupChannelRequest(group = id) },
                onNewCollection = { drawerOpen = false; focusManager.clearFocus(force = true); workspace = "collection-new" },
                onManageCollection = { id -> drawerOpen = false; focusManager.clearFocus(force = true); workspace = "collection:$id" },
                onClose = { drawerOpen = false },
            )
        }
        if ((drawerShown || drawerOpen) && watchlistOpen) {
            WatchlistDrawer(
                reminders = watchlist,
                modifier = Modifier.padding(start = 16.dp, top = drawerTop, bottom = 20.dp).width(drawerWidth).graphicsLayer {
                    translationX = (drawer.value - 1f) * drawerWidth.toPx() * drawerDirection
                },
                onWatch = { reminder ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val channel = tv.channelById(reminder.channelId) ?: return@withContext null
                            val live = tv.toLive(tv.channels(channel.categoryId))
                            channel to live
                        }
                        val (channel, live) = result ?: return@launch
                        val selectedLive = live.firstOrNull { it.key == channel.id } ?: return@launch
                        withContext(Dispatchers.IO) {
                            if (!Platform.IS_TV) tv.noteWatched(channel.id)
                            tv.rememberPosition(channel.categoryId, channel.id)
                        }
                        drawerOpen = false
                        onPlay(selectedLive, live)
                    }
                },
                onRemove = deps.reminders::remove,
                onClose = { drawerOpen = false },
            )
        }
        searching?.let { first ->
            GuideSearch(
                tv, first,
                onPick = { hit ->
                    scope.launch {
                        // A channel can be in several groups: the guide goes to the one it comes from.
                        selected = hit.channel.categoryId
                        restoreChannel = hit.channel.id
                        val family = withContext(Dispatchers.IO) { tv.channels(hit.channel.categoryId) }.firstOrNull { it.nameKey == hit.channel.nameKey }
                        restoreChannel = family?.id ?: hit.channel.id
                        focus.anchor = hit.program?.start ?: System.currentTimeMillis()
                        searching = null
                        runCatching { firstRow.requestFocus() }
                    }
                },
                onClose = {
                    searching = null
                    runCatching { firstRow.requestFocus() }
                },
            )
        }
        (scheduling?.let { "schedule" } ?: workspace)?.let { route ->
            androidx.compose.foundation.layout.Box(
                // Dense guide text behind a panel competes with it: keep the guide as a faint context only.
                Modifier.fillMaxSize().background(FormulerGuideStyle.scrim)
                    .focusProperties { canFocus = collectionSearch == null && groupPicker == null }
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .onPreviewKeyEvent { event ->
                        if (contextOkHeld && event.key in listOf(Key.Enter, Key.NumPadEnter)) {
                            if (event.type == KeyEventType.KeyUp) contextOkHeld = false
                            true
                        } else collectionSearch != null || groupPicker != null
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            when {
                                route == "schedule" -> scheduling = null
                                route in setOf("home", "guide-actions", "guide-details", "settings", "collection-new", "discover", "recordings") ||
                                    route.startsWith("collection:") -> workspace = null
                                route == "collection-picker" -> workspace = "guide-actions"
                                // On TV the provider's controls live behind the gear: Back returns there.
                                route in setOf("organize", "account", "movies", "series") -> workspace = "settings"
                                else -> workspace = "home"
                            }
                            true
                        } else event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.Escape)
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (route) {
                    "schedule" -> scheduling?.let { (channel, selectedProgram) ->
                        TvRecordSchedulePanel(deps, channel, selectedProgram, Modifier, onClose = { scheduling = null }, onScheduled = { revision++; scheduling = null })
                    }
                    "guide-details", "guide-actions" -> shown?.let { channel ->
                        GuideContextPanel(
                            channel = channel,
                            program = program,
                            details = route == "guide-details",
                            favorite = channel.id in favorites,
                            reminded = program?.let { deps.reminders.has(channel.id, it.start) } == true,
                            now = now,
                            onRecord = { p -> scheduling = channel to p; workspace = null },
                            onReminder = { p -> deps.reminders.toggle(Reminder(channel.id, TvCatalog.shortLabel(channel.name), p.title, p.start)); revision++ },
                            onFavorite = { favorite(channel) },
                            onCollection = { workspace = "collection-picker" },
                            onWatchlist = { workspace = null; watchlistOpen = true; drawerOpen = true },
                            onMyTv = { workspace = "settings" },
                            onClose = { workspace = null },
                        )
                    }
                    "movies" -> ProviderLibraryPanel(deps, com.sokoul.desktop.data.ProviderKind.Movies,
                        onPlay = onPlayProvider, onDownload = onDownloadProvider, onClose = { workspace = "settings" })
                    "series" -> ProviderLibraryPanel(deps, com.sokoul.desktop.data.ProviderKind.Series,
                        onPlay = onPlayProvider, onDownload = onDownloadProvider, onClose = { workspace = "settings" })
                    "account" -> ProviderAccountPanel(deps, onChanged = { revision++ }, onClose = { workspace = "settings" })
                    "organize" -> TvOrganizationPanel(deps, onChanged = { revision++ }, onClose = { workspace = "settings" })
                    "recordings" -> TvRecordingsPanel(deps, onPlayProvider, onClose = { workspace = null })
                    "settings" -> TvSettingsPanel(deps, onOpen = { focusManager.clearFocus(force = true); workspace = it }, onClose = { workspace = null })
                    "collection-picker" -> shown?.let { TvCollectionPicker(deps, it) { workspace = "guide-actions" } }
                    // On TV the old "My TV" window is gone: its parts live in the group column, the gear and here.
                    "home" -> if (Platform.IS_TV) TvSettingsPanel(deps, onOpen = { focusManager.clearFocus(force = true); workspace = it }, onClose = { workspace = null })
                        else TvWorkspacePanel(Modifier, onSelect = { workspace = it }, onClose = { workspace = null })
                    "discover" -> TvDiscoveryPanel(deps, onClose = { workspace = null }) { targetGroup, channel, targetProgram ->
                        selected = targetGroup; restoreChannel = channel?.id; restoreProgramStart = targetProgram?.start
                        workspace = null
                    }
                    else -> if (route == "collection-new" || route.startsWith("collection:")) {
                        val id = route.removePrefix("collection:").takeIf { route.startsWith("collection:") }
                        val collectionName by produceState<String?>(null, id, personalRevision) {
                            value = id?.let { wanted -> withContext(Dispatchers.IO) { deps.personalTv.state().collections.firstOrNull { it.id == wanted }?.name } }
                        }
                        TvPanel(if (id == null) L("New collection", "Nouvelle collection").text() else collectionName ?: L("Collection", "Collection").text(),
                            if (id == null) L("Give it a short name", "Donnez-lui un nom court").text() else L("Your channels, in your order", "Vos chaînes, dans votre ordre").text(),
                            width = (if (id == null) com.sokoul.pc.ui.TvPanelSize.Standard else com.sokoul.pc.ui.TvPanelSize.Wide).width,
                            height = (if (id == null) com.sokoul.pc.ui.TvPanelSize.Standard else com.sokoul.pc.ui.TvPanelSize.Wide).height,
                            modifier = Modifier.testTag("tv-collection-panel"),
                            action = { com.sokoul.pc.ui.PanelChip(L("Close", "Fermer").text()) { workspace = null } },
                            footer = L("OK to choose · Back to guide", "OK pour choisir · Retour au guide").text()) {
                            PersonalCollections(deps,
                                onGuide = { targetGroup, channel, targetProgram ->
                                    selected = targetGroup; restoreChannel = channel?.id; restoreProgramStart = targetProgram?.start
                                    workspace = null
                                },
                                onWatch = { workspace = null; watch(it) },
                                onAddChannels = {
                                    focusManager.clearFocus(force = true)
                                    groupPicker = GroupChannelRequest(destination = it)
                                },
                                addReturn = addReturn, initial = id, startNew = id == null, onExit = { workspace = null })
                        }
                    } else TvWorkspacePanel(Modifier, onSelect = { workspace = it }, onClose = { workspace = "home" })
                }
            }
        }
        groupPicker?.let { request ->
            Box(Modifier.fillMaxSize().background(FormulerGuideStyle.scrim).padding(top = topSpace, start = 18.dp, end = 18.dp, bottom = 18.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }, contentAlignment = Alignment.Center) {
                GroupChannelPicker(deps, groups, request,
                    onClose = { groupPicker = null; if (request.destination == null) drawerOpen = true else addReturn++ },
                    onSearch = request.destination?.let { id -> { groupPicker = null; collectionSearch = id } },
                    onSaved = { id, count ->
                        groupPicker = null
                        deps.notice = L("Added $count channels", "$count chaîne(s) ajoutée(s)").text()
                        if (request.destination == null) selected = TvPersonalization.group(id) else addReturn++
                    },
                    onFavorited = { count ->
                        groupPicker = null
                        deps.notice = L("$count channels added to favourites", "$count chaîne(s) ajoutée(s) aux favoris").text()
                        revision++
                        selected = com.sokoul.desktop.data.SpecialGroup.FAVORITES
                    })
            }
        }
        // Focus was released to open the search: My TV takes it back on the button that opened it.
        LaunchedEffect(collectionSearch) {
            if (collectionSearch != null) searchWasOpen = true
            else if (searchWasOpen) { searchWasOpen = false; addReturn++ }
        }
        collectionSearch?.let { target ->
            // Opens over My TV: dim that panel too, or its actions read through the search.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(FormulerGuideStyle.scrim), contentAlignment = Alignment.Center) {
                PersonalGuideSearch(tv, "", onPick = { hit ->
                    scope.launch { withContext(Dispatchers.IO) { deps.personalTv.add(target, hit.channel) }; collectionSearch = null }
                }, onClose = { collectionSearch = null })
            }
        }
    }
}

@Composable
private fun JumpPill(label: String, tag: String, onClick: () -> Unit) {
    var has by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (has) Palette.FocusFill else Palette.ButtonFill)
    Box(
        // Capsules, like the navigation bar and every other Sokoul control; square corners belong to the guide's cells.
        Modifier.height(if (Platform.IS_TV) 52.dp else 36.dp).clip(RoundedCornerShape(50)).background(fill)
            .tvFocusable(onFocusChanged = { has = it }, onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, Type.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = if (Platform.IS_TV) 29.sp else Type.Body.fontSize), if (has) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
    }
}

/** A remote key drawn as a key, then what it does: read at a glance from the sofa instead of a sentence. */
@Composable
private fun KeyHint(key: String, action: String) {
    Row(Modifier.padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.height(30.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, FormulerGuideStyle.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
            Label(key, Type.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 25.sp), FormulerGuideStyle.ink, maxLines = 1)
        }
        Label(action, Type.Body.copy(fontSize = 25.sp), FormulerGuideStyle.secondary, maxLines = 1)
    }
}

/** A round icon button of the TV control row (search, settings): same focus fill as the pills beside it. */
@Composable
private fun IconPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    var has by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (has) Palette.FocusFill else Palette.ButtonFill)
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(fill)
            .tvFocusable(onFocusChanged = { has = it }, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (has) Palette.OnFocus else Palette.OnBackground)
    }
}

@Composable
private fun GuideContextPanel(
    channel: Channel,
    program: Program?,
    details: Boolean,
    favorite: Boolean,
    reminded: Boolean,
    now: Long,
    onRecord: (Program) -> Unit,
    onReminder: (Program) -> Unit,
    onFavorite: () -> Unit,
    onWatchlist: () -> Unit,
    onMyTv: () -> Unit,
    onClose: () -> Unit,
    onCollection: (() -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; runCatching { first.requestFocus() } }
    val title = if (details) program?.title ?: TvCatalog.shortLabel(channel.name)
        else L("Programme actions", "Actions du programme").text()
    val subtitle = buildString {
        append(TvCatalog.shortLabel(channel.name))
        if (program != null) append(" · ${clock(program.start)} – ${clock(program.end)}")
    }
    TvPanel(
        title = title,
        subtitle = subtitle,
        width = if (Platform.IS_TV) com.sokoul.pc.ui.TvPanelSize.Standard.width else 760.dp,
        height = if (Platform.IS_TV) com.sokoul.pc.ui.TvPanelSize.Standard.height else 760.dp,
        footer = L("Back closes · Menu opens actions · Info shows details", "Retour ferme · Menu ouvre les actions · Info affiche les détails").text(),
        modifier = Modifier.testTag(if (details) "tv-guide-details" else "tv-guide-actions"),
    ) {
        Column(Modifier.fillMaxWidth().then(if (Platform.IS_TV) Modifier.verticalScroll(rememberScrollState()) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (details) {
            val description = program?.description?.takeIf { it.isNotBlank() }
                ?: L("No programme description is available.", "Aucun résumé n’est disponible pour ce programme.").text()
            Label(description, Type.Body, Palette.BarText, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), maxLines = 5)
        }
        if (Platform.IS_TV) {
            TvActionRow(if (favorite) L("Remove channel from favorites", "Retirer la chaîne des favoris").text()
                else L("Add channel to favorites", "Ajouter la chaîne aux favoris").text(),
                requester = first, modifier = Modifier.testTag("tv-guide-favorite"), onClick = onFavorite)
            onCollection?.let { TvActionRow(L("Add to collection", "Ajouter à une collection").text(), modifier = Modifier.testTag("tv-guide-collection"), onClick = it) }
        }
        if (program != null && program.start > now) {
            TvActionRow(
                if (reminded) L("Remove reminder", "Supprimer le rappel").text() else L("Remind me", "Me prévenir").text(),
                requester = if (Platform.IS_TV) remember { FocusRequester() } else first,
                modifier = Modifier.testTag("tv-guide-reminder"),
            ) { onReminder(program) }
        }
        if (program != null && program.end > now) {
            TvActionRow(L("Record programme", "Enregistrer l’émission").text(),
                modifier = Modifier.testTag("tv-guide-record"), requester = if (!Platform.IS_TV && program.start <= now) first else remember { FocusRequester() }) {
                onRecord(program)
            }
        }
        if (!Platform.IS_TV) TvActionRow(
            if (favorite) L("Remove channel from favorites", "Retirer la chaîne des favoris").text()
            else L("Add channel to favorites", "Ajouter la chaîne aux favoris").text(),
            modifier = Modifier.testTag("tv-guide-favorite"),
            requester = if (program == null) first else remember { FocusRequester() },
        ) { onFavorite() }
        // One name for one feature: the group column calls these reminders too.
        TvActionRow((if (Platform.IS_TV) L("My reminders", "Mes rappels") else L("Watchlist", "À suivre")).text(),
            modifier = Modifier.testTag("tv-watchlist-open"), onClick = onWatchlist)
        // On TV everything personal is in the group column and settings behind the gear: no extra window here.
        if (!Platform.IS_TV) TvActionRow(L("TV settings", "Réglages TV").text(), modifier = Modifier.testTag("tv-workspace-open"), onClick = onMyTv)
        // The Back key already does this on TV; the footer says so.
        if (!Platform.IS_TV) TvActionRow(L("Back to guide", "Retour au guide").text(), onClick = onClose)
        }
    }
}

/** What the page shows, for the tools that describe the app. */
data class TvReport(
    val group: String?,
    val level: String,
    val drawerOpen: Boolean,
    val row: Int,
    val channels: Int,
    val channel: String?,
    val program: String?,
    val focused: Boolean,
)

/** The channel in large, its picture behind the text. The picture and the text change by opacity and movement only. */
@Composable
private fun Hero(deps: AppDeps, c: Channel, program: Program?, favorite: Boolean, now: Long, level: GuideLevel, active: Boolean) {
    val compact = Platform.IS_TV
    val player = deps.player
    val framesHere = deps.playback.previewKey == c.id && rememberHasFrames(player)
    val videoAlpha by animateFloatAsState(if (framesHere) 1f else 0f, tween(500))
    val enter = remember(c.id, program?.start) { Animatable(0f) }
    LaunchedEffect(c.id, program?.start) { enter.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    val details by animateFloatAsState(if (level == GuideLevel.Guide) 1f else 0f, tween(ZOOM_MS))
    val onNow = program != null && program.start <= now && program.end > now

    // The programme's picture fills the preview until the channel's own picture arrives; without one, the big logo.
    val picture = rememberProgramImage(program?.takeIf { onNow })
    Box(Modifier.fillMaxSize().clipToBounds().testTag("tv-hero")) {
        if (picture != null) {
            Art(picture, Modifier.fillMaxSize().graphicsLayer { alpha = (1f - videoAlpha) }.testTag("tv-hero-picture"), scale = ContentScale.Crop, placeholder = Color.Transparent)
        } else {
            ChannelLogo(
                c.name,
                c.logo,
                Modifier.align(Alignment.CenterEnd)
                    .padding(end = if (compact) 120.dp else 160.dp, top = if (compact) 0.dp else 70.dp)
                    .size(if (compact) 240.dp else 460.dp, if (compact) 132.dp else 250.dp)
                    .graphicsLayer { alpha = (1f - videoAlpha) * if (compact) 0.55f else 1f },
                guideId = c.epgKey,
            )
        }
        if (player != null && videoAlpha > 0f) {
            VideoFill(player, Modifier.fillMaxSize().graphicsLayer { alpha = videoAlpha }.testTag("tv-preview"), fit = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.Background, 0.34f to Palette.Background.copy(alpha = 0.82f), 0.62f to Palette.Background.copy(alpha = 0.22f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.3f to Color.Transparent, 0.8f to Color.Transparent, 1f to Palette.Background)))
        Column(
            Modifier.padding(start = Metrics.PageMargin, top = if (compact) 4.dp else 112.dp)
                .width(if (compact) 1_240.dp else 900.dp).graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 16f
            },
            verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // With the programme's picture in the background, the logo moves beside the channel's name.
                if (picture != null || compact) {
                    ChannelLogo(c.name, c.logo, Modifier.size(if (compact) 42.dp else 84.dp, if (compact) 22.dp else 48.dp).testTag("tv-hero-logo"), guideId = c.epgKey)
                }
                if (onNow) LiveDot(active)
                Label(
                    listOfNotNull(
                        TvCatalog.shortLabel(c.name),
                        if (program == null) null else if (onNow) tr(Text.LiveBadge) else tr(Text.LiveAt, clock(program.start)),
                    ).joinToString(" · "),
                    Type.Body.copy(fontSize = if (compact) 15.sp else Type.Body.fontSize, fontWeight = FontWeight.SemiBold),
                    Palette.BarText,
                    Modifier.testTag("tv-hero-name"),
                    maxLines = 1,
                )
            }
            Label(program?.title ?: TvCatalog.shortLabel(c.name), Type.RowTitle.copy(fontSize = if (compact) 20.sp else Type.RowTitle.fontSize * 1.45f), maxLines = 1, modifier = Modifier.testTag("tv-hero-title"))
            if (details > 0f) {
                    Column(Modifier.graphicsLayer { alpha = details }, verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 8.dp)) {
                    program?.let { p ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Label("${clock(p.start)} – ${clock(p.end)}", Type.Body.copy(fontSize = if (compact) 14.sp else Type.Body.fontSize), Palette.BarText, maxLines = 1)
                            if (onNow) ProgramBar(p, now, Modifier.width(if (compact) 220.dp else 260.dp).then(if (compact) Modifier.height(2.dp) else Modifier))
                        }
                        p.description?.takeIf { it.isNotBlank() }?.let { Label(it, Type.Body.copy(fontSize = if (compact) 13.sp else Type.Body.fontSize), Palette.BarText, maxLines = if (compact) 1 else 2) }
                    }
                    // The grid owns browse actions; this hint only documents the remote shortcuts.
                    if (!compact) Label(
                        if (Platform.IS_TV) com.sokoul.pc.data.L("OK  Watch · Menu  Actions · F  Favorite", "OK  Regarder · Menu  Actions · F  Favori").text()
                        else com.sokoul.pc.data.L("OK  Watch · Menu  Favorite", "OK  Regarder · Menu  Favori").text(),
                        Type.Meta.copy(fontSize = 19.sp), Palette.Secondary, maxLines = 1, modifier = Modifier.padding(top = 6.dp).testTag("tv-hero-hint"),
                    )
                }
            }
        }
    }
}

/** The playing preview stays independent of the programme currently being explored. */
@Composable
private fun TvEditorialHero(deps: AppDeps, channel: Channel, program: Program?, now: Long, watchFocus: FocusRequester, immersive: Boolean = false, onWatch: () -> Unit) {
    val previewKey = deps.playback.previewKey
    val previewChannel by produceState<Channel?>(null, previewKey) {
        value = previewKey?.let { withContext(Dispatchers.IO) { deps.tv.channelById(it) } }
    }
    val player = deps.player
    val frames = rememberHasFrames(player)
    val picture = rememberProgramImage(program)
    val previewContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        if (player != null && previewKey != null && frames) {
            VideoFill(player, Modifier.fillMaxSize().testTag("tv-preview"), fit = ContentScale.Crop)
        } else if (picture != null) {
            Art(picture, Modifier.fillMaxSize().testTag("tv-hero-picture"), scale = ContentScale.Crop, placeholder = Color.Transparent)
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF15171C)), contentAlignment = Alignment.Center) {
                val source = previewChannel ?: channel
                ChannelLogo(source.name, source.logo, Modifier.size(260.dp, 140.dp), guideId = source.epgKey)
            }
        }
    }
    if (immersive) {
        ImmersiveLiveHeader(channel, program, now, watchFocus, onWatch, previewContent)
        return
    }
    FormulerGuideHeader(channel, program, now,
        previewName = previewChannel?.takeIf { it.id != channel.id }?.let { TvCatalog.shortLabel(it.name) },
        modifier = Modifier.testTag("tv-hero"), watchFocus = watchFocus, onWatch = onWatch) {
        if (player != null && previewKey != null && frames) {
            VideoFill(player, Modifier.fillMaxSize().testTag("tv-preview"), fit = ContentScale.Fit)
        } else if (picture != null) {
            Art(picture, Modifier.fillMaxSize().testTag("tv-hero-picture"), scale = ContentScale.Crop, placeholder = Color.Transparent)
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                val source = previewChannel ?: channel
                ChannelLogo(source.name, source.logo, Modifier.size(116.dp, 64.dp), guideId = source.epgKey)
            }
        }
    }
}

/** Beats only in the real app: an endless animation would keep a test waiting for the interface to be idle. */
@Composable
private fun LiveDot(active: Boolean) {
    val pulse = remember { Animatable(1f) }
    if (FocusSource.trackRealMouse) {
        LaunchedEffect(active) {
            if (active) pulse.animateTo(0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
        }
    }
    Box(Modifier.size(14.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(Palette.Error))
}

@Composable
private fun ProgramBar(program: Program, now: Long, modifier: Modifier) {
    val fraction = ((now - program.start).toFloat() / (program.end - program.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.PillFill)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Palette.OnBackground))
    }
}

private fun groupName(g: LiveLogic.Group) = when (g.id) {
    SpecialGroup.FAVORITES -> tr(Text.LiveFavorites)
    SpecialGroup.ALL -> tr(Text.LiveAll)
    TvCatalog.RECENT -> tr(Text.LiveRecent)
    else -> g.name
}

/** Says what the guide shows, and opens the drawer with the mouse. It sits in the corner of the time ruler. */
@Composable
private fun GroupChip(group: LiveLogic.Group?, count: Int?, modifier: Modifier, requester: FocusRequester? = null, onClick: () -> Unit) {
    var has by remember { mutableStateOf(false) }
    val own = remember { FocusRequester() }
    val fill by animateColorAsState(if (has) Palette.FocusFill else Color.Transparent)
    val text = if (has) Palette.OnFocus else Palette.OnBackground
    Row(
        modifier.width(280.dp).height(if (Platform.IS_TV) 52.dp else 38.dp).clip(RoundedCornerShape(50)).background(fill)
            .tvFocusable(onFocusChanged = { has = it }, onClick = onClick, requester = requester ?: own)
            .padding(horizontal = 10.dp)
            .testTag("tv-group-chip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Label((if (Platform.IS_TV) group?.let(::columnGroupName) else group?.let(::groupName)?.let(TvCatalog::label))
            ?: L("Groups", "Groupes").text(),
            Type.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = if (Platform.IS_TV) 29.sp else Type.Body.fontSize), text, Modifier.weight(1f, fill = false), maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = text)
    }
}

/**
 * The groups, pulled out over the guide. Going through them already shows each one in the guide beside it, and typing
 * narrows hundreds of them down to a few: the drawer is a way to look, not a form to fill in.
 */
@Composable
internal fun GroupDrawer(groups: List<LiveLogic.Group>, selected: String?, modifier: Modifier, onBrowse: (String) -> Unit, onClose: () -> Unit, confirmOnly: Boolean = Platform.IS_TV) {
    var query by remember { mutableStateOf("") }
    val shown = remember(groups, query) { if (query.isBlank()) groups else groups.filter { GuideLogic.matches(groupName(it), query) } }
    val state = rememberLazyListState()
    var wanted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(wanted) {
        if (confirmOnly) return@LaunchedEffect
        val id = wanted ?: return@LaunchedEffect
        delay(SELECT_DELAY_MS)
        onBrowse(id)
    }
    val first = remember { FocusRequester() }
    val startAt = remember(shown) { shown.indexOfFirst { it.id == selected }.coerceAtLeast(0) }
    val emptyFocus = remember { FocusRequester() }
    LaunchedEffect(shown) {
        state.scrollToItem(startAt)
        delay(16)
        runCatching { if (confirmOnly && shown.isEmpty()) emptyFocus.requestFocus() else first.requestFocus() }
    }
    Column(
        modifier.background(FormulerGuideStyle.sheet).padding(start = Metrics.PageMargin, end = 24.dp, top = 120.dp, bottom = 40.dp).testTag("tv-drawer")
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val char = e.utf16CodePoint.toChar()
                when {
                    e.key == Key.Escape && query.isNotEmpty() -> {
                        query = ""
                        true
                    }
                    e.key == Key.Escape || e.key == Key.DirectionRight -> {
                        onClose()
                        true
                    }
                    e.key == Key.Backspace && query.isNotEmpty() -> {
                        query = query.dropLast(1)
                        true
                    }
                    char.isLetterOrDigit() || (char == ' ' && query.isNotEmpty()) -> {
                        query += char
                        true
                    }
                    else -> false
                }
            }.let { if (confirmOnly) it.focusRequester(emptyFocus).focusable() else it },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label(query.ifEmpty { tr(Text.LiveAllGroups) }, Type.RowTitle, if (query.isEmpty()) Palette.Secondary else Palette.OnBackground, Modifier.testTag("tv-drawer-title"), maxLines = 1)
        if (confirmOnly && shown.isEmpty()) Label(L("No matching groups. Back clears the search.", "Aucun groupe trouvé. Retour efface la recherche.").text(),
            Type.Body, FormulerGuideStyle.secondary, maxLines = 2)
        LazyColumn(if (confirmOnly) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(shown, key = { _, g -> g.id }) { i, g ->
                var has by remember { mutableStateOf(false) }
                val own = remember { FocusRequester() }
                val fill by animateColorAsState(if (has) Palette.FocusFill else if (g.id == selected) Palette.ActiveFill else Color.Transparent)
                Box(
                    Modifier.fillMaxWidth().height(Metrics.MenuItemHeight).clip(RoundedCornerShape(if (confirmOnly) 8.dp else 33.dp)).background(fill)
                        .testTag("tv-group-${g.id}")
                        .tvFocusable(
                            onFocusChanged = {
                                has = it
                                if (it) wanted = g.id
                            },
                            onClick = {
                                if (!confirmOnly || g.id != selected) onBrowse(g.id)
                                onClose()
                            },
                            requester = if (i == startAt) first else own,
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Label(groupName(g), Type.Meta, if (has) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
                }
            }
        }
        if (confirmOnly) Label(L("OK  Apply group   ·   Back  Cancel", "OK  Choisir le groupe   ·   Retour  Annuler").text(),
            Type.Body.copy(fontSize = 18.sp), FormulerGuideStyle.secondary, Modifier.padding(top = 12.dp), maxLines = 2)
    }
}
