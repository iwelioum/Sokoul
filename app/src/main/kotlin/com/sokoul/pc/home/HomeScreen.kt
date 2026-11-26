package com.sokoul.pc.home

import androidx.compose.ui.draw.blur
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.Resume
import com.sokoul.desktop.ui.ImageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.HeroItem
import com.sokoul.pc.data.GenreTile
import com.sokoul.pc.data.Mood
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.data.PageRow
import com.sokoul.pc.data.Platform
import com.sokoul.pc.data.RowSource
import com.sokoul.pc.data.SagaEntry
import com.sokoul.pc.data.SagaTile
import com.sokoul.pc.collections.CollectionRequest
import com.sokoul.pc.collections.GenreCard
import com.sokoul.pc.collections.SagaLandscapeTile
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.SmartLandscape
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.sokoul.pc.data.RowStyle
import com.sokoul.pc.data.kind
import com.sokoul.pc.data.autoplayKeys
import com.sokoul.pc.data.trailerKeys
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.durationLabel
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.InterDisplay
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusSource
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.LandscapeCard
import com.sokoul.pc.ui.LocalActiveTab
import com.sokoul.pc.ui.LocalHoverFocus
import com.sokoul.pc.ui.OutlinedBadge
import com.sokoul.pc.ui.PlayPill
import com.sokoul.pc.ui.PosterCard
import com.sokoul.pc.ui.RoundButton
import com.sokoul.pc.ui.VideoFill
import com.sokoul.pc.ui.rememberHasFrames
import com.sokoul.pc.ui.focusIfPresent

/** The design the hero's text block was drawn for: its sizes follow the hero's own from there. */
private const val HERO_DESIGN_WIDTH = 1920f
private const val HERO_DESIGN_HEIGHT = 1080f
private const val HERO_MIN_SCALE = 0.7f
private const val HERO_TEXT_WIDTH = 0.49f
private const val HERO_TEXT_BOTTOM = 0.09f
private const val HERO_RETURN_MS = 120L
/** On a tablet held upright the hero is a little taller than wide: the image on top, its text over the bottom. */
private const val HERO_UPRIGHT_RATIO = 1.08f
/** The share of an upright hero's width its text may take: an overview of three short lines, not one long one. */
private const val HERO_UPRIGHT_TEXT = 0.86f
private const val LOGO_WAIT_MS = 2_000L
private const val SLIDE_WITHOUT_TRAILER_MS = 12_000L
private val RowTop = 120.dp
private const val ALIGN_AFTER_MS = 80L

/**
 * A page: hero, then every row of its plan. Rows load in the background, top first, and appear as they arrive; a row
 * with too few titles never shows.
 */
@Composable
fun HomeScreen(
    deps: AppDeps,
    kind: PageKind,
    active: Boolean,
    primary: FocusRequester,
    onOpen: (items: List<TmdbItem>, index: Int, origin: CardOrigin?) -> Unit,
    onResume: (Resume) -> Unit,
    onOpenSaga: (List<SagaEntry>, Int, CardOrigin?) -> Unit,
    onOpenRequest: (CollectionRequest, CardOrigin?) -> Unit,
    onOpenGenre: (GenreTile, CardOrigin?) -> Unit,
    onAnimeHub: (com.sokoul.pc.anime.AnimeHubView, com.sokoul.pc.data.anime.MediaKind, String?, CardOrigin?) -> Unit = { _, _, _, _ -> },
    onWork: (com.sokoul.pc.data.anime.AniTitle, CardOrigin?) -> Unit = { _, _ -> },
    onRead: (com.sokoul.pc.data.anime.ReaderRequest) -> Unit = {},
    onDiscover: (com.sokoul.pc.discover.DiscoverView, CardOrigin?) -> Unit = { _, _ -> },
) {
    // Kept here: the hero leaves composition when scrolled away, and must come back on the same slide.
    val slide = rememberSaveable(kind) { mutableIntStateOf(0) }
    val heroAction = rememberSaveable(kind) { mutableIntStateOf(0) }
    val firstRow = remember(kind) { FocusRequester() }
    val scope = rememberCoroutineScope()
    // A failed hero is no hero, never a page that waits forever for it.
    val hero by produceState<List<HeroItem>?>(null, kind) {
        value = runCatching { deps.catalog.hero(kind) }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else emptyList() }
    }
    // The home page follows the order and the hidden rows the user chose in the settings.
    val layoutJson = deps.uiRevision.let { if (kind == PageKind.Home) deps.prefs.homeRowsJson else null }
    val plan = remember(kind, layoutJson) { com.sokoul.pc.data.HomeLayout.decode(layoutJson).apply(deps.catalog.plan(kind)) }
    val loaded = remember(kind) { mutableStateMapOf<String, List<TmdbItem>>() }
    var animeRowLimit by remember(kind) { mutableIntStateOf(ANIME_FIRST_ROWS) }
    // Read off the interface thread: read on it, it waited for the database while the TV guide was being written, and the
    // Chromecast reported the app as not responding (ANR, 2026-11-22).
    val resumes by produceState(emptyList<Resume>(), active, kind) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { deps.catalog.continueWatching() }
            .filter { kind != PageKind.Anime && (kind == PageKind.Home || it.kind == kind.kind) }
    }
    val wantsSagas = plan.any { it is PageRow.Sagas }
    val sagas by produceState(emptyList<SagaTile>(), kind, wantsSagas) {
        if (wantsSagas) value = deps.catalog.collections().curated
    }
    val wantsPlatforms = plan.any { it is PageRow.Platforms }
    val logos by produceState(emptyMap<Int, String>(), kind, wantsPlatforms) {
        if (wantsPlatforms) value = deps.catalog.platformLogos(if (kind == PageKind.Series) "tv" else "movie")
    }
    val wantsStudios = plan.any { it is PageRow.Studios }
    val studioLogos by produceState(emptyMap<Int, String>(), wantsStudios) {
        if (wantsStudios) value = runCatching { deps.catalog.studioLogos() }.getOrDefault(emptyMap())
    }
    val wantsCategories = plan.any { it is PageRow.Categories }
    val categories by produceState(emptyList<GenreTile>(), kind, wantsCategories) {
        if (wantsCategories) value = deps.catalog.genreTiles(kind.kind)
    }

    // Keyed on the plan too: a row hidden in the settings and shown again is loaded then, and one already loaded is kept.
    // The rows load from the start, alongside the hero: on the Anime page they waited for it, and its first rows came that
    // much later (2026-11-24). Only their display waits for the hero (see the list below).
    LaunchedEffect(kind, plan) {
        val gate = Semaphore(PARALLEL_ROWS)
        val scheduled = mutableSetOf<String>()
        androidx.compose.runtime.snapshotFlow { if (kind == PageKind.Anime) animeRowLimit else Int.MAX_VALUE }.collect { limit ->
            plan.filterIsInstance<PageRow.Titles>().take(limit).filter { loaded[it.id] == null && scheduled.add(it.id) }.forEach { row ->
                launch {
                    // A row that fails is an empty row, which does not show: its failure no longer cancelled every other
                    // row's loading with it, leaving their loading shapes for good.
                    loaded[row.id] = try {
                        gate.withPermit { deps.catalog.rowItems(row.source) }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        }
    }
    val because = resumes.firstOrNull()
    LaunchedEffect(because?.tmdbId) {
        if (because != null) loaded[BECAUSE] = deps.catalog.similar(because.kind, because.tmdbId)
    }

    // A row still on its way keeps its place in the plan's order as a loading shape: rows arriving out of order were
    // inserted above the one in focus and moved it (2026-11-24). A row that arrives too short leaves its place.
    val present = plan.filter { row ->
        when (row) {
            is PageRow.Titles -> loaded[row.id]?.let { it.size >= row.minItems } ?: true
            is PageRow.Because -> (loaded[BECAUSE]?.size ?: 0) >= PageRow.MIN_ITEMS
            is PageRow.Sagas -> sagas.isNotEmpty()
            is PageRow.Categories -> categories.isNotEmpty()
            is PageRow.Moods, is PageRow.Platforms, is PageRow.Studios, is PageRow.AnimeTools, is PageRow.AnimeGenres, is PageRow.Books -> true
            is PageRow.Reading -> !deps.anime?.readingList().isNullOrEmpty()
            is PageRow.Discover -> deps.catalog.discover != null
        }
    }
    fun pending(row: PageRow) = row is PageRow.Titles && loaded[row.id] == null
    // The shapes between rows already there all stay; after the last one, only the next few, so the page never fills
    // with them.
    val lastArrived = present.indexOfLast { !pending(it) }
    var shapesAfter = 0
    val visible = present.filterIndexed { i, row -> !pending(row) || i < lastArrived || shapesAfter++ < PENDING_SHOWN }
    val firstReal = visible.indexOfFirst { !pending(it) }
    // One choice for the undecided (docs/design/tv-home.md, 1.1): titles like the last one watched, not already begun,
    // in an order that stays the same all day. Hidden when there is no history to start from.
    val tonight = remember(kind, because?.tmdbId, loaded[BECAUSE], resumes) {
        val pool = loaded[BECAUSE].orEmpty().filter { t -> resumes.none { it.tmdbId == t.id } }
        if (kind != PageKind.Home || because == null || pool.isEmpty()) null
        else Tonight(because.title, pool.shuffled(kotlin.random.Random(java.time.LocalDate.now().toEpochDay())))
    }
    val current = hero
    val hasHero = !current.isNullOrEmpty()
    val hasContinue = resumes.isNotEmpty()

    val list = rememberLazyListState()
    val reportHeroAtTop = com.sokoul.pc.shell.LocalReportHomeHeroAtTop.current
    val waitsForHero = current == null
    LaunchedEffect(kind, list, hasHero, waitsForHero, reportHeroAtTop) {
        if (kind == PageKind.Home) {
            androidx.compose.runtime.snapshotFlow {
                // The page at its top, hero or not: a Home without a hero (no catalogue yet) said "not at the top", and the
                // bar, which now stays at the top, never showed there (2026-11-24).
                list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
            }.collect { reportHeroAtTop(it) }
        }
    }
    LaunchedEffect(kind, visible.size) {
        if (kind != PageKind.Anime) return@LaunchedEffect
        val totalRows = plan.count { it is PageRow.Titles }
        androidx.compose.runtime.snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= (visible.size - ANIME_AHEAD).coerceAtLeast(1)) {
                animeRowLimit = (animeRowLimit + ANIME_STEP).coerceAtMost(totalRows)
            }
        }
    }
    // The height the page shows: the hero takes all of it. fillParentMaxHeight left out the list's bottom padding, and the
    // top of the next row showed under the hero. Read through derivedStateOf: it changes with the window, not the scroll.
    val pageHeight by remember { androidx.compose.runtime.derivedStateOf { list.layoutInfo.viewportSize.height } }
    val heroFocusAsked = remember(kind) { booleanArrayOf(false) }
    // A tablet held upright draws the page at its own scale: a square hero and larger cards, as the Apple TV app there.
    val upright = com.sokoul.pc.ui.Form.tabletPortrait
    // The first poster row is the page's showcase there: tall posters, the others stay medium.
    val largeRow = if (upright) visible.firstOrNull { it is PageRow.Titles && it.style == RowStyle.Poster }?.id else null
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val pageDensity = if (upright) com.sokoul.pc.ui.uprightDensity(constraints.maxWidth) else LocalDensity.current
    // Keyboard moves put the active row in a steady place; mouse moves never make the page jump.
    val rowTopPx = with(pageDensity) { RowTop.roundToPx() }
    var alignJob by remember { mutableStateOf<Job?>(null) }
    fun align(index: Int) {
        if (FocusSource.lastWasMouse) return
        alignJob?.cancel()
        alignJob = scope.launch {
            // After the scroll that focus itself asks for, which would otherwise cancel this one.
            delay(ALIGN_AFTER_MS)
            if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.animateScrollToItem(index)
            val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
            list.animateScrollBy((row.offset - rowTopPx).toFloat())
        }
    }
    CompositionLocalProvider(LocalHoverFocus provides { !list.isScrollInProgress }, LocalDensity provides pageDensity) {
        LazyColumn(
            Modifier.fillMaxSize().background(Palette.Background).testTag("home"),
            state = list,
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            // Rows wait for the hero: inserted above them later, it would leave the page scrolled past it.
            if (current == null) {
                item(key = "hero-skeleton") { com.sokoul.pc.ui.SkeletonHero(Modifier.fillParentMaxHeight()) }
                return@LazyColumn
            }
            if (hasHero) {
                item(key = "hero") {
                    Hero(deps, kind, current, active, primary, firstRow, slide, heroAction, onOpen, focusAsked = heroFocusAsked, tonight = tonight, modifier = if (upright) Modifier.fillParentMaxWidth().aspectRatio(1f / HERO_UPRIGHT_RATIO) else if (pageHeight > 0) Modifier.height(with(LocalDensity.current) { pageHeight.toDp() }) else Modifier.fillParentMaxHeight(), onFocused = {
                        if (!FocusSource.lastWasMouse) scope.launch {
                            delay(HERO_RETURN_MS)
                            list.animateScrollToItem(0)
                        }
                    })
                }
            }
            if (hasContinue) {
                item(key = "continue") {
                    CardRow(
                        tr(Text.RowContinue), resumes, key = { it.key }, testTag = "row-continue",
                        onActive = { if (it) align(1) },
                        upTarget = primary,
                    ) { i, r ->
                        LandscapeCard(
                            image = r.image,
                            meta = continueMeta(r),
                            progress = r.fraction,
                            onClick = { onResume(r) },
                            requester = if (i == 0) firstRow else remember { FocusRequester() },
                        )
                    }
                }
            }
            // One key per row whether loading or loaded: the row replaces its loading shape in the same place.
            itemsIndexed(visible, key = { _, row -> row.id }, contentType = { _, row -> if (pending(row)) SKELETON else row.javaClass }) { rowIndex, row ->
                if (row is PageRow.Titles && loaded[row.id] == null) {
                    com.sokoul.pc.ui.SkeletonRow(landscape = row.style == RowStyle.Landscape, square = row.style == RowStyle.Square)
                    return@itemsIndexed
                }
                val first = rowIndex == firstReal && !hasContinue
                val listIndex = rowIndex + (if (hasHero) 1 else 0) + (if (hasContinue) 1 else 0)
                val up = if (first) primary else null
                fun focusFor(index: Int): FocusRequester? = if (first && index == 0) firstRow else null
                when (row) {
                    is PageRow.Titles -> TitleRow(
                        deps, row.id, row.title.text(), row.style, loaded[row.id].orEmpty(),
                        up, { if (it) align(listIndex) }, ::focusFor, onOpen, large = row.id == largeRow,
                        onSeeAll = { origin -> onOpenRequest(CollectionRequest(row.title.text(), null, null) { page -> deps.catalog.rowItems(row.source, page) }, origin) },
                    )
                    is PageRow.Because -> TitleRow(
                        deps, row.id, tr(Text.BecauseWatched, because?.title.orEmpty()), RowStyle.Landscape,
                        loaded[BECAUSE].orEmpty(), up, { if (it) align(listIndex) }, ::focusFor, onOpen, large = upright,
                        onSeeAll = { origin ->
                            val all = loaded[BECAUSE].orEmpty()
                            onOpenRequest(CollectionRequest(tr(Text.BecauseWatched, because?.title.orEmpty()), null, null) { page -> if (page == 1) all else emptyList() }, origin)
                        },
                    )
                    is PageRow.Discover -> com.sokoul.pc.discover.DiscoverToolsRow(up, { if (it) align(listIndex) }, ::focusFor, onDiscover)
                    is PageRow.Moods -> CardRow(
                        tr(Text.RowMoods), Mood.entries, key = { it.name }, testTag = "row-moods", spacing = TileGap,
                        onActive = { if (it) align(listIndex) }, upTarget = up,
                    ) { i, mood ->
                        MoodTile(mood, "mood-${mood.name}", focusFor(i) ?: remember { FocusRequester() }) { origin ->
                            val spec = mood.spec(row.kind)
                            onOpenRequest(CollectionRequest(spec.title.text(), null, null) { page -> deps.catalog.rowItems(RowSource.Spec(spec), page) }, origin)
                        }
                    }
                    is PageRow.Platforms -> CardRow(
                        tr(Text.RowPlatforms), Platform.entries, key = { it.name }, testTag = "row-platforms", spacing = TileGap,
                        onActive = { if (it) align(listIndex) }, upTarget = up,
                    ) { i, platform ->
                        PlatformTile(platform, logos[platform.providerId], "platform-${platform.name}", focusFor(i) ?: remember { FocusRequester() }) { origin ->
                            onOpenRequest(CollectionRequest(com.sokoul.pc.data.L("${platform.label} catalog", "Catalogue ${platform.label}").text(), null, null) { page -> deps.catalog.available(platform, null, page) }, origin)
                        }
                    }
                    is PageRow.Studios -> CardRow(
                        com.sokoul.pc.data.L("Studios", "Studios").text(), com.sokoul.pc.data.Studio.entries, key = { it.name }, testTag = "row-studios", spacing = TileGap,
                        onActive = { if (it) align(listIndex) }, upTarget = up,
                    ) { i, studio ->
                        StudioTile(studio, studioLogos[studio.companyId], "studio-${studio.name}", focusFor(i) ?: remember { FocusRequester() }) { origin ->
                            onOpenRequest(CollectionRequest(studio.label, studioLogos[studio.companyId], null, studio) { page -> deps.catalog.studio(studio, page) }, origin)
                        }
                    }
                    is PageRow.Sagas -> CardRow(
                        tr(Text.RowSagas), sagas, key = { it.key }, testTag = "row-sagas", spacing = TileGap,
                        onActive = { if (it) align(listIndex) }, upTarget = up,
                    ) { i, tile ->
                        SagaLandscapeTile(tile, "home-saga-${tile.key}", focusFor(i) ?: remember { FocusRequester() }) { _, origin -> onOpenSaga(sagas.map { SagaEntry(it) }, i, origin) }
                    }
                    is PageRow.AnimeTools, is PageRow.AnimeGenres, is PageRow.Reading, is PageRow.Books -> com.sokoul.pc.anime.AnimePageRow(deps, row, up, { if (it) align(listIndex) }, ::focusFor, onAnimeHub, onWork, onRead)
                    is PageRow.Categories -> CardRow(
                        tr(Text.RowCategories), categories, key = { it.id }, testTag = "row-categories", spacing = TileGap,
                        onActive = { if (it) align(listIndex) }, upTarget = up,
                    ) { i, genre ->
                        GenreCard(genre, i, focusFor(i) ?: remember { FocusRequester() }) { g, origin -> onOpenGenre(g, origin) }
                    }
                }
            }
        }
    }
    }
}

/** A single suggestion for tonight and why it is made. */
private class Tonight(val because: String, val titles: List<TmdbItem>)

private const val PENDING_SHOWN = 2
private const val SKELETON = "skeleton"
/**
 * The Anime page's rows asked at first, then how many more each time the list nears its end (within [ANIME_AHEAD] rows).
 * AniList allows few calls a minute, so they are not all asked at once; four fill the first screens.
 */
private const val ANIME_FIRST_ROWS = 4
private const val ANIME_STEP = 3
private const val ANIME_AHEAD = 4
/** Titles a row shows before "See All": seven to ten keep a choice easy (docs/design/tv-home.md, 1.1). */
private const val ROW_CAP = 10
private const val TOP_COUNT = 10
private const val SEE_ALL = "see-all"

/** The last card of a long row: the same size as its neighbours, it opens the whole list. */
@Composable
private fun SeeAllCard(style: RowStyle, large: Boolean, tag: String, requester: FocusRequester, onClick: (CardOrigin) -> Unit) {
    val (width, height) = style.cardSize(large)
    FocusCard(width, height, onClick, tag = tag, requester = requester) { focused ->
        Box(Modifier.fillMaxSize().background(if (focused) Palette.OnBackground else Palette.PillFill), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.size(54.dp), tint = if (focused) Palette.OnFocus else Palette.OnBackground)
                Spacer(Modifier.height(8.dp))
                Label(com.sokoul.pc.data.L("See All", "Tout voir").text(), Type.Button, if (focused) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
            }
        }
    }
}
/** A large card is a showcase row's: about 2.7 tall posters or 1.8 wide images across a tablet held upright. */
private fun RowStyle.cardSize(large: Boolean = false) = when (this) {
    RowStyle.Landscape -> if (large) 600.dp to 338.dp else 414.dp to 232.dp
    RowStyle.Square -> CardMetrics.PosterWidth to CardMetrics.PosterWidth
    RowStyle.Poster, RowStyle.Top10 -> if (large) 400.dp to 600.dp else CardMetrics.PosterWidth to CardMetrics.PosterHeight
}

private const val PARALLEL_ROWS = 6
private const val BECAUSE = "because"
private val TileGap = 30.dp

@Composable
internal fun TitleRow(
    deps: AppDeps,
    id: String,
    title: String,
    style: RowStyle,
    items: List<TmdbItem>,
    up: FocusRequester?,
    onActive: (Boolean) -> Unit,
    focusFor: (Int) -> FocusRequester?,
    onOpen: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    onSeeAll: ((CardOrigin?) -> Unit)? = null,
    large: Boolean = false,
) {
    // A row shows a few titles to choose from, not a catalogue: the rest is one press away, behind "See All".
    val shown = items.take(if (style == RowStyle.Top10) TOP_COUNT else ROW_CAP)
    val more = style != RowStyle.Top10 && onSeeAll != null && items.size > shown.size
    val cells: List<TmdbItem?> = if (more) shown + null else shown
    CardRow(title, cells, key = { it?.id ?: SEE_ALL }, testTag = "row-$id", onActive = onActive, upTarget = up) { index, cell ->
        val requester = focusFor(index) ?: remember { FocusRequester() }
        val item = cell ?: run {
            SeeAllCard(style, large, "card-$id-see-all", requester) { onSeeAll?.invoke(it) }
            return@CardRow
        }
        if (style == RowStyle.Landscape || style == RowStyle.Square) {
            val (width, height) = style.cardSize(large)
            FocusCard(width, height, { onOpen(shown, index, it) }, tag = "card-$id-$index", requester = requester, onFocus = { if (it) deps.warmTrailer(item) }) {
                SmartLandscape(deps.catalog, item)
            }
        } else {
            val caption by produceState<String?>(null, item.id) { value = captionFor(deps, item, style) }
            PosterCard(
                catalog = deps.catalog,
                item = item,
                caption = caption,
                captionAlways = style == RowStyle.Top10,
                rank = if (style == RowStyle.Top10) index + 1 else null,
                onClick = { onOpen(shown, index, it) },
                tag = "card-$id-$index",
                onFocus = { if (it) deps.warmTrailer(item) },
                requester = requester,
                width = style.cardSize(large).first,
                height = style.cardSize(large).second,
            )
        }
    }
}

private fun continueMeta(r: Resume): String {
    val left = ((r.durationMs - r.positionMs) / 60_000).toInt().coerceAtLeast(1)
    val time = durationLabel(left)
    return if (r.season != null && r.episode != null) "${tr(Text.EpisodeShort, r.season!!, r.episode!!)} · $time" else time
}

private suspend fun captionFor(deps: AppDeps, item: TmdbItem, style: RowStyle): String? {
    val genre = item.genreIds.firstOrNull()?.let { deps.catalog.genreName(item.kind(), it) }
    return if (style == RowStyle.Top10) genre else listOfNotNull(typeLabel(item.kind()), genre).joinToString(" · ")
}

fun typeLabel(kind: String) = tr(if (kind == "tv") Text.TypeSeries else Text.TypeMovie)

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Hero(
    deps: AppDeps,
    kind: PageKind,
    items: List<HeroItem>,
    active: Boolean,
    primary: FocusRequester,
    firstRow: FocusRequester,
    slide: MutableIntState,
    selectedAction: MutableIntState,
    onOpen: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    onFocused: () -> Unit,
    focusAsked: BooleanArray,
    modifier: Modifier = Modifier,
    tonight: Tonight? = null,
) {
    var index by slide
    val activeTab = LocalActiveTab.current
    var focused by remember { mutableStateOf(false) }
    val hero = items[index % items.size]
    var heroInfo by remember(kind, hero.item.kind(), hero.item.id) { mutableStateOf(hero.info) }
    // On desktop anime, show the matched AniList hero immediately and resolve its richer TMDB metadata in the background.
    LaunchedEffect(kind, hero.item.id) {
        if (kind == PageKind.Anime && !com.sokoul.pc.Platform.IS_TV && heroInfo == null) {
            heroInfo = runCatching {
                deps.catalog.details(hero.item.kind(), hero.item.id)
            }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }
        }
    }
    val trailer = deps.trailer
    val actions = remember { List(7) { FocusRequester() } }
    val playAction = actions[0]

    // The hero is composed lazily, so focus can only be asked for once it exists. Once: it leaves and comes back with the
    // scrolling, and asking again would take focus from the row being reached.
    LaunchedEffect(Unit) {
        if (!focusAsked[0]) {
            focusAsked[0] = true
            withFrameNanos { }
            runCatching { primary.requestFocus() }
        }
    }
    val trailerOwner = remember { Any() }
    // A trailer the viewer stopped does not come back on the same slide; the next slide may have its own.
    var stoppedFor by remember { mutableStateOf<Int?>(null) }
    var heroSound by remember { mutableStateOf(deps.prefs.heroSound) }
    val inFront by com.sokoul.pc.ui.AppFocus.active.collectAsState()
    LaunchedEffect(hero.item.id, focused, active, stoppedFor, inFront) {
        // TrailerController waits a few seconds of steady focus before it opens anything: crossing the hero starts nothing.
        if (focused && active && inFront && stoppedFor != hero.item.id) trailer.prepare(heroInfo?.autoplayKeys(hero.item.kind()).orEmpty(), hero.item.title, trailerOwner) else trailer.release(trailerOwner)
    }
    // Silent until the viewer asks for sound, as behind a title page. Whatever plays next finds the sound as it was.
    val silent = active && trailer.playing && !heroSound
    LaunchedEffect(silent) { deps.player?.setMutedSafely(silent) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { deps.player?.setMutedSafely(false) } }
    // Links are fetched ahead for every slide, so a slide's trailer starts on time instead of seconds late.
    LaunchedEffect(items, heroInfo, hero.item.id) {
        if (kind == PageKind.Anime && !com.sokoul.pc.Platform.IS_TV) {
            deps.warmKeys(heroInfo?.trailerKeys().orEmpty())
        } else {
            // Apple's page only for the slide shown: each one weighs half a megabyte.
            items.forEach { h -> deps.warmKeys((if (h === hero) heroInfo?.autoplayKeys(h.item.kind()) else h.info?.trailerKeys()).orEmpty()) }
        }
    }
    // Next slide when the trailer ends, or after a while when there is none.
    LaunchedEffect(trailer.finishedCount) { if (trailer.finishedCount > 0) index = (index + 1) % items.size }
    // A slide waits for its trailer as long as one is on its way.
    LaunchedEffect(index, focused, active, inFront, trailer.playing, trailer.pending) {
        // In the real app only: a carousel with no end keeps a test waiting for the interface to be idle.
        if (com.sokoul.pc.ui.FocusSource.trackRealMouse && focused && active && inFront && !trailer.playing && !trailer.pending) {
            delay(SLIDE_WITHOUT_TRAILER_MS)
            index = (index + 1) % items.size
        }
    }

    // No black under the image: the image's own waiting color is black, and it is drawn only until the image is there.
    // The whole height of the page: nothing of the next row shows until the page goes down.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().then(modifier).testTag("hero")) {
        // The text block, the logo and the buttons follow the hero's size: as drawn on a 1920 x 1080 screen, smaller
        // in a smaller window, never larger.
        // A phone held upright lays its hero out like a tablet held upright: at 49 % of its width the text block cut the
        // last buttons, squeezed the genres and wrapped the age rating one digit per line (renders, 2026-11-26).
        val phoneUpright = com.sokoul.pc.ui.Form.isPortrait
        val upright = com.sokoul.pc.ui.Form.tabletPortrait || phoneUpright
        val heroScale = if (upright) 1f else minOf(maxWidth.value / HERO_DESIGN_WIDTH, maxHeight.value / HERO_DESIGN_HEIGHT).coerceIn(HERO_MIN_SCALE, 1f)
        val textWidth = when {
            phoneUpright -> maxWidth - Metrics.PageMargin * 2
            upright -> maxWidth * HERO_UPRIGHT_TEXT
            else -> maxWidth * HERO_TEXT_WIDTH / heroScale
        }
        val textBottom = if (upright) 64.dp else maxHeight * HERO_TEXT_BOTTOM
        val density = LocalDensity.current
        val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
        val engine = deps.player
        // Image and shades rendered once into a layer, then drawn in one pass: four full-screen fills a frame were more
        // than the TV's GPU could draw in time. Not while a trailer plays: the layer would be redone at every frame.
        Box(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = if (trailer.playing || !com.sokoul.pc.Platform.IS_TV) CompositingStrategy.Auto else CompositingStrategy.Offscreen }) {
            Crossfade(hero) { slide ->
                Art(slide.info?.backdrop ?: slide.item.backdrop, Modifier.fillMaxSize(), placeholder = Color.Black)
            }
            AnimatedVisibility(trailer.playing && engine != null && rememberHasFrames(engine), Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                VideoFill(engine!!, Modifier.fillMaxSize())
            }
            if (upright) {
                // The text sits over the lower half, the full width: the image fades into the page from its middle down.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Color(0x66000000), 0.16f to Color.Transparent, 0.4f to Color.Transparent,
                    0.7f to Palette.Background.copy(alpha = 0.78f), 1f to Palette.Background,
                )))
            } else {
                Box(Modifier.fillMaxSize().background(
                    if (rtl) Brush.horizontalGradient(0.38f to Color.Transparent, 1f to Color(0xD9000000))
                    else Brush.horizontalGradient(0f to Color(0xD9000000), 0.62f to Color.Transparent),
                ))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 0.82f to Palette.Background.copy(alpha = 0.72f), 0.96f to Palette.Background)))
            }
        }
        val deferAnimeExtras = kind == PageKind.Anime && !com.sokoul.pc.Platform.IS_TV
        val heroExtras by produceState(com.sokoul.pc.data.TitleExtras(), hero.item.kind(), hero.item.id, heroInfo, deferAnimeExtras) {
            value = if (deferAnimeExtras && heroInfo == null) com.sokoul.pc.data.TitleExtras()
            else runCatching { deps.catalog.titleExtras(hero.item.kind(), hero.item.id, heroInfo) }.getOrDefault(com.sokoul.pc.data.TitleExtras())
        }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalDensity provides androidx.compose.ui.unit.Density(density.density * heroScale, density.fontScale),
        ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = Metrics.PageMargin, bottom = textBottom / heroScale).width(textWidth)) {
            com.sokoul.pc.title.HeroInfo(deps, hero.item.kind(), hero.item.id, hero.item.title, hero.item.overview, heroInfo, heroExtras, null, {})
            Spacer(Modifier.height(26.dp))
            Row(
                Modifier
                    .focusRequester(primary)
                    .focusProperties {
                        enter = {
                            val requested = selectedAction.intValue
                            val available = requested in 0..3 ||
                                (requested == 4 && tonight != null) ||
                                (requested in 5..6 && trailer.playing)
                            actions[if (available) requested else 0]
                        }
                    }
                    .onFocusChanged {
                        if (it.hasFocus && !focused) onFocused()
                        focused = it.hasFocus
                    }
                    .focusGroup()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        // Down from any button goes to the first row, Up to the active tab, whatever lies in between.
                        val target = when (e.key) {
                            Key.DirectionDown -> firstRow
                            Key.DirectionUp -> activeTab
                            else -> null
                        } ?: return@onPreviewKeyEvent false
                        FocusSource.lastWasMouse = false
                        target.focusIfPresent()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                val inList = deps.myList.contains(hero.item)
                // Off the main thread, as everything that reads the database while the page is shown.
                val lastRead by produceState<com.sokoul.desktop.data.Resume?>(null, hero.item.id) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { deps.catalog.lastWatched(hero.item.kind(), hero.item.id) } }
                val last = lastRead
                // A film still in cinemas says so here too, as on its page.
                val cinema = if (last == null) com.sokoul.pc.title.cinemaLabels(hero.item.kind(), hero.info) else null
                com.sokoul.pc.title.PlayCard(
                    cinema?.first ?: if (last != null && !last.finished) tr(Text.Resume) else com.sokoul.pc.data.L("Play", "Lecture").text(),
                    cinema?.second ?: last?.takeIf { !it.finished && it.durationMs > 0 }?.let { com.sokoul.pc.data.L("${com.sokoul.pc.i18n.durationLabel(((it.durationMs - it.positionMs) / 60_000L).toInt().coerceAtLeast(1))} left", "Reste ${com.sokoul.pc.i18n.durationLabel(((it.durationMs - it.positionMs) / 60_000L).toInt().coerceAtLeast(1))}").text() },
                    last?.takeIf { !it.finished && it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs },
                    { onOpen(items.map { it.item }, index, null) },
                    Modifier.testTag("hero-watch"),
                    playAction,
                ) { if (it) selectedAction.intValue = 0 }
                com.sokoul.pc.title.LabeledRoundButton(
                    if (inList) Icons.Filled.Check else Icons.Filled.Add,
                    if (inList) com.sokoul.pc.data.L("In My List", "Dans Ma liste").text() else com.sokoul.pc.data.L("My List", "Ma liste").text(),
                    { deps.myList.toggle(hero.item) },
                    requester = actions[1],
                    onFocused = { if (it) selectedAction.intValue = 1 },
                )
                com.sokoul.pc.title.LabeledRoundButton(
                    Icons.Filled.Info,
                    com.sokoul.pc.data.L("About", "À propos").text(),
                    { onOpen(items.map { it.item }, index, null) },
                    requester = actions[2],
                    onFocused = { if (it) selectedAction.intValue = 2 },
                )
                com.sokoul.pc.title.LabeledRoundButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    com.sokoul.pc.data.L("Next", "Suivant").text(),
                    { index = (index + 1) % items.size },
                    Modifier.testTag("hero-next"),
                    requester = actions[3],
                    onFocused = { if (it) selectedAction.intValue = 3 },
                )
                if (tonight != null) {
                    com.sokoul.pc.title.LabeledRoundButton(
                        Icons.Filled.Star,
                        com.sokoul.pc.data.L("Tonight: like ${tonight.because}", "Ce soir : comme ${tonight.because}").text(),
                        // The title page shows its neighbours: Right gives another suggestion.
                        { onOpen(tonight.titles, 0, null) },
                        Modifier.testTag("hero-tonight"),
                        requester = actions[4],
                        onFocused = { if (it) selectedAction.intValue = 4 },
                    )
                }
                // Only while a trailer plays: stop it in one press, or give it sound.
                if (trailer.playing) {
                    com.sokoul.pc.title.LabeledRoundButton(
                        if (heroSound) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        if (heroSound) com.sokoul.pc.data.L("Sound on", "Son activé").text() else com.sokoul.pc.data.L("Sound off", "Son coupé").text(),
                        {
                            heroSound = !heroSound
                            deps.prefs.heroSound = heroSound
                        },
                        Modifier.testTag("hero-sound"),
                        requester = actions[5],
                        onFocused = { if (it) selectedAction.intValue = 5 },
                    )
                    com.sokoul.pc.title.LabeledRoundButton(
                        Icons.Filled.Stop, com.sokoul.pc.data.L("Stop Trailer", "Arrêter la bande-annonce").text(),
                        {
                            stoppedFor = hero.item.id
                            // The button goes away with the trailer: focus goes back to Play rather than nowhere.
                            runCatching { playAction.requestFocus() }
                        },
                        Modifier.testTag("hero-stop-trailer"),
                        requester = actions[6],
                        onFocused = { if (it) selectedAction.intValue = 6 },
                    )
                }
            }
        }
        }
        Dots(items.size, index, Modifier.align(Alignment.BottomCenter).padding(bottom = if (upright) 20.dp else 32.dp))
    }
}

@Composable
fun TitleLogo(logo: String?, title: String, modifier: Modifier = Modifier, align: Alignment = Alignment.CenterStart) {
    // A logo that is missing or cannot be decoded falls back to the title in bold text, at the same place.
    val image by produceState(logo?.let(ImageLoader::cached), logo) { value = logo?.let { ImageLoader.load(it) } }
    var waited by remember(logo) { mutableStateOf(logo == null) }
    LaunchedEffect(logo) {
        if (logo == null) return@LaunchedEffect
        delay(LOGO_WAIT_MS)
        waited = true
    }
    val bitmap = image
    if (bitmap != null) {
        // A dark halo of the logo's own shape under it: a grey-blue logo on a blue picture ("L'Odyssée") stays readable.
        // Without blur (older Android), the halo sits exactly under the logo and does not show.
        Box(modifier.height(130.dp).width(430.dp)) {
            Image(
                bitmap, null, Modifier.matchParentSize().blur(14.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.75f },
                align, ContentScale.Fit, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black),
            )
            Image(bitmap, title, Modifier.matchParentSize(), align, ContentScale.Fit)
        }
    } else if (!waited) {
        Spacer(modifier.height(130.dp))
    } else {
        Label(title, Type.RowTitle.copy(fontFamily = InterDisplay, fontSize = Type.RowTitle.fontSize * 1.8f), modifier = modifier, maxLines = 2)
    }
}

/** Logo · type · genres · age rating, as on the reference. */
@Composable
fun MetaLine(kind: String, info: TmdbInfo?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource("sokoul.png"), null, Modifier.size(40.dp).clip(CircleShape))
        val parts = listOf(typeLabel(kind)) + info?.genres.orEmpty().take(2)
        Label(parts.joinToString(" · "), Type.Meta)
        info?.certification?.takeIf { it.isNotBlank() }?.let { OutlinedBadge(it) }
    }
}

@Composable
private fun Dots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 30.dp else 10.dp)
            Box(
                Modifier.size(width, 10.dp).clip(CircleShape).background(if (i == current) Palette.OnBackground else Palette.Secondary),
            )
        }
    }
}
