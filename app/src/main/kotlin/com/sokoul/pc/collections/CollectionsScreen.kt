package com.sokoul.pc.collections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.ui.ImageLoader
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.CollectionsPage
import com.sokoul.pc.data.GenreTile
import com.sokoul.pc.data.SagaEntry
import com.sokoul.pc.data.SagaShelf
import com.sokoul.pc.data.SagaTile
import com.sokoul.pc.data.kind
import com.sokoul.pc.home.typeLabel
import com.sokoul.pc.i18n.Text
import com.sokoul.pc.i18n.tr
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.InterDisplay
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.heavyBlur
import com.sokoul.pc.ui.BackButton
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.FocusSource
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.LocalActiveTab
import com.sokoul.pc.ui.LocalHoverFocus
import com.sokoul.pc.ui.keepClearOfEdges
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.RowHeading
import com.sokoul.pc.ui.SingleLine
import com.sokoul.pc.ui.SmartLandscape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sokoul.pc.ui.focusIfPresent

private val TileWidth = 414.dp
private val TileHeight = 232.dp
private val TallHeight = 618.dp
private val TileGap = 30.dp
private val SpotlightHeight = 570.dp
private val SpotlightCardWidth = 550.dp
private val SpotlightCardHeight = 310.dp
private val HeaderHeight = 470.dp
private val RowTop = 120.dp
private val SpotlightTop = 60.dp
private val TallPadding = 40.dp
private const val ALIGN_AFTER_MS = 80L
private const val SHELF_ROW_SIZE = 20
private const val PAGE_AHEAD = 12

/** What opening a saga or genre needs. The items load when the page opens. */
data class CollectionRequest(
    val name: String, val logo: String?, val backdrop: String?,
    /** A studio's page opens as its hub (hero and sections) instead of a plain grid. */
    val studio: com.sokoul.pc.data.Studio? = null,
    val load: suspend (page: Int) -> List<TmdbItem>,
)

/** Loaded pages and navigation survive while details are opened above the collection. */
class CollectionState {
    val items = mutableStateListOf<TmdbItem>()
    var page by mutableIntStateOf(0)
    var done by mutableStateOf(false)
    val grid = androidx.compose.foundation.lazy.grid.LazyGridState()
    val focusRequesters = mutableMapOf<Int, FocusRequester>()
}

internal val LabelStyle = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 7.sp, textAlign = TextAlign.Center)
private val TileFade = Brush.verticalGradient(0f to Color(0x4D000000), 0.5f to Color(0x66000000), 1f to Color(0xCC000000))

/** Spaced capitals, as the page title and for sagas that have no logo. */
private fun bigName(size: Int) = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * 1.18f).sp,
    letterSpacing = (size * 0.11f).sp,
    textAlign = TextAlign.Center,
    color = Color.White,
)

@Composable
private fun SagaName(tile: SagaTile, size: Int, logoHeight: Dp, modifier: Modifier = Modifier) = SagaName(tile.name, tile.logo, size, logoHeight, modifier)

@Composable
internal fun SagaName(name: String, logo: String?, size: Int, logoHeight: Dp, modifier: Modifier = Modifier) {
    val image by produceState(logo?.let(ImageLoader::cached), logo) { value = logo?.let { ImageLoader.load(it) } }
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, name, modifier.height(logoHeight), Alignment.Center, ContentScale.Fit)
    } else {
        BasicText(name.uppercase(), modifier, style = bigName(size), maxLines = 3)
    }
}

/** The logo in the middle of the tile, the word COLLECTION at its foot. */
@Composable
private fun BoxScope.TileName(tile: SagaTile, logoHeight: Dp, textSize: Int, bottom: Dp) {
    SagaName(tile, textSize, logoHeight, Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 26.dp))
    BasicText(tr(Text.CollectionLabel), Modifier.align(Alignment.BottomCenter).padding(bottom = bottom), style = LabelStyle.copy(color = Color.White), maxLines = 1)
}

@Composable
fun SagaLandscapeTile(tile: SagaTile, tag: String, requester: FocusRequester, onOpen: (SagaTile, CardOrigin) -> Unit) {
    FocusCard(TileWidth, TileHeight, { onOpen(tile, it) }, tag = tag, requester = requester) {
        Art(tile.backdrop ?: tile.poster, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(TileFade))
        TileName(tile, logoHeight = 118.dp, textSize = 25, bottom = 16.dp)
    }
}

@Composable
private fun SagaTallTile(
    tile: SagaTile,
    tag: String,
    onOpen: (SagaTile, CardOrigin) -> Unit,
    requester: FocusRequester = remember { FocusRequester() },
) {
    FocusCard(TileWidth, TallHeight, { onOpen(tile, it) }, tag = tag, requester = requester) {
        Art(tile.poster ?: tile.backdrop, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x1A000000), 0.5f to Color(0x66000000), 1f to Color(0xCC000000))))
        TileName(tile, logoHeight = 190.dp, textSize = 38, bottom = 34.dp)
    }
}

/** A saga on a shelf: its image and logo load when it comes into view. */
@Composable
private fun SagaEntryTile(
    deps: AppDeps,
    entry: SagaEntry,
    onOpen: (CardOrigin) -> Unit,
    requester: FocusRequester = remember { FocusRequester() },
) {
    val tile by produceState(entry.tile, entry.key) { if (value == null) value = entry.load(deps.catalog) }
    FocusCard(TileWidth, TileHeight, onOpen, tag = "shelf-${entry.key}", requester = requester) {
        val loaded = tile
        Art(loaded?.backdrop ?: loaded?.poster, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(TileFade))
        if (loaded != null) TileName(loaded, logoHeight = 118.dp, textSize = 25, bottom = 16.dp)
    }
}

/** Colors for the genre tiles: Sokoul's own abstract shapes, one pair per tile. */
private val GenreColors = listOf(
    Color(0xFF7A3B12) to Color(0xFFE8A04A),
    Color(0xFF3F0F63) to Color(0xFFC13ACB),
    Color(0xFF0E1E4A) to Color(0xFF7CA8E8),
    Color(0xFF0F4A44) to Color(0xFF7FE0C0),
    Color(0xFF5A0F18) to Color(0xFFE53A4A),
    Color(0xFF123F6B) to Color(0xFF58C0F0),
    Color(0xFF4A1A5A) to Color(0xFFF07AB0),
    Color(0xFF3A3A12) to Color(0xFFD8C84A),
)

@Composable
fun GenreCard(genre: GenreTile, index: Int, requester: FocusRequester = remember { FocusRequester() }, onOpen: (GenreTile, CardOrigin) -> Unit) {
    val (dark, bright) = GenreColors[index % GenreColors.size]
    FocusCard(TileWidth, TileHeight, { onOpen(genre, it) }, tag = "genre-$index", requester = requester) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(dark, dark.copy(alpha = 0.85f)), Offset.Zero, Offset(size.width, size.height)))
            val w = size.width
            val h = size.height
            // The shape changes with the tile, so that the row does not look stamped.
            val lean = 0.30f + 0.08f * (index % 4)
            val shape = Path().apply {
                moveTo(w * lean, h)
                lineTo(w * (lean + 0.34f), 0f)
                lineTo(w, 0f)
                lineTo(w, h)
                close()
            }
            drawPath(shape, Brush.linearGradient(listOf(bright.copy(alpha = 0.15f), bright), Offset(w * lean, h), Offset(w, 0f)))
            val edge = Path().apply {
                moveTo(w * (lean + 0.18f), h)
                lineTo(w * (lean + 0.52f), 0f)
                lineTo(w * (lean + 0.60f), 0f)
                lineTo(w * (lean + 0.26f), h)
                close()
            }
            drawPath(edge, Color.White.copy(alpha = 0.16f))
        }
        Label(genre.name, Type.RowTitle, modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp).width(260.dp), maxLines = 2)
    }
}

/** One saga in a wide band: logo and details on the left, its films on the right. */
@Composable
private fun Spotlight(
    deps: AppDeps,
    tile: SagaTile,
    onOpenSaga: (List<SagaEntry>, Int, CardOrigin?) -> Unit,
    onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    firstRequester: FocusRequester? = null,
) {
    Box(Modifier.fillMaxWidth().height(SpotlightHeight).testTag("spotlight")) {
        Art(tile.backdrop, Modifier.fillMaxSize().heavyBlur(70.dp), placeholder = Palette.Background)
        Box(Modifier.fillMaxSize().background(Color(0xB3101012)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Palette.Background, 0.2f to Color.Transparent, 0.8f to Color.Transparent, 1f to Palette.Background)))
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(560.dp).padding(horizontal = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SagaName(tile, 42, 120.dp, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                BasicText(tr(Text.CollectionLabel), style = LabelStyle.copy(color = Color.White, fontSize = 21.sp, letterSpacing = 9.sp))
                Spacer(Modifier.height(34.dp))
                PillButton(tr(Text.Details), { onOpenSaga(listOf(SagaEntry(tile)), 0, null) }, Modifier.testTag("spotlight-details"))
            }
            LazyRow(
                Modifier.fillMaxHeight().focusGroup(),
                contentPadding = PaddingValues(end = Metrics.PageMargin),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(tile.films, key = { _, film -> film.id }) { i, film ->
                    Column(Modifier.width(SpotlightCardWidth)) {
                        FocusCard(
                            SpotlightCardWidth,
                            SpotlightCardHeight,
                            { onOpenTitle(tile.films, i, it) },
                            requester = if (i == 0) firstRequester ?: remember { FocusRequester() } else remember { FocusRequester() },
                        ) {
                            SmartLandscape(deps.catalog, film)
                        }
                        Spacer(Modifier.height(16.dp))
                        SingleLine(film.title, Type.Meta, Palette.OnBackground)
                        Spacer(Modifier.height(4.dp))
                        SingleLine(listOfNotNull(typeLabel(film.kind()), film.year?.toString()).joinToString(" · "), Type.Body, Palette.Secondary)
                    }
                }
            }
        }
    }
}

/** Reinvented with Sokoul's look: the same arrangement of a large title, tile rows, a spotlight and franchise rows. */
@Composable
fun CollectionsScreen(
    deps: AppDeps,
    primary: FocusRequester,
    onOpenSaga: (List<SagaEntry>, Int, CardOrigin?) -> Unit,
    onOpenShelf: (SagaShelf, CardOrigin?) -> Unit,
    onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
) {
    val page by produceState<CollectionsPage?>(null) { value = deps.catalog.collections() }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var alignJob by remember { mutableStateOf<Job?>(null) }
    val activeTab = LocalActiveTab.current

    // Keyboard moves put the active row just under the navigation bar; the mouse never makes the page jump.
    fun align(index: Int, top: Dp = RowTop) {
        if (FocusSource.lastWasMouse) return
        val topPx = with(density) { top.roundToPx() }
        alignJob?.cancel()
        alignJob = scope.launch {
            delay(ALIGN_AFTER_MS)
            if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.animateScrollToItem(index)
            val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
            list.animateScrollBy((row.offset - topPx).toFloat())
        }
    }

    LaunchedEffect(page != null) {
        if (page?.featured?.isNotEmpty() == true) {
            withFrameNanos { }
            FocusSource.lastWasMouse = true
            runCatching { primary.requestFocus() }
        }
    }

    val current = page
    Box(Modifier.fillMaxSize().background(Palette.Background).testTag("collections")) {
        current?.featured?.firstOrNull()?.let { first ->
            Box(
                Modifier.fillMaxWidth().height(760.dp).graphicsLayer {
                    val atTop = list.firstVisibleItemIndex == 0
                    alpha = if (atTop) 1f else 0f
                    translationY = if (atTop) -list.firstVisibleItemScrollOffset.toFloat() else 0f
                },
            ) {
                Art(first.backdrop, Modifier.fillMaxSize().heavyBlur(90.dp), placeholder = Palette.Background)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x8C000000), 0.6f to Color(0xA6101012), 1f to Palette.Background)))
            }
        }
        CompositionLocalProvider(LocalHoverFocus provides { !list.isScrollInProgress }) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(34.dp)) {
                item(key = "header") {
                    Box(Modifier.fillMaxWidth().height(HeaderHeight), contentAlignment = Alignment.Center) {
                        BasicText(tr(Text.AllCollections), Modifier.padding(top = 90.dp), style = bigName(72))
                    }
                }
                if (current == null) return@LazyColumn
                var index = 1
                if (current.featured.isNotEmpty()) {
                    val at = index++
                    item(key = "featured") {
                        CardRow(tr(Text.RowFeatured), current.featured, key = { it.key }, spacing = TileGap, testTag = "row-featured", onActive = { if (it) align(at) }, upTarget = activeTab, onUp = {
                            alignJob?.cancel()
                            scope.launch { list.animateScrollToItem(0) }
                        }) { i, tile ->
                            val entries = remember(current.featured) { current.featured.map { SagaEntry(it) } }
                            SagaLandscapeTile(tile, "saga-${tile.key}", if (i == 0) primary else remember { FocusRequester() }) { _, origin -> onOpenSaga(entries, i, origin) }
                        }
                    }
                }
                if (current.shelves.isNotEmpty()) {
                    val at = index++
                    item(key = "categories") {
                        CardRow(tr(Text.RowCategories), current.shelves, key = { it.category.name }, spacing = TileGap, testTag = "row-categories", onActive = { if (it) align(at) }) { i, shelf ->
                            GenreCard(
                                GenreTile(shelf.category.ordinal, "saga", shelf.category.title.text(), null),
                                i,
                                requester = if (current.featured.isEmpty() && i == 0) primary else remember { FocusRequester() },
                            ) { _, origin -> onOpenShelf(shelf, origin) }
                        }
                    }
                }
                current.spotlight?.let { tile ->
                    val at = index++
                    item(key = "spotlight") {
                        Box(Modifier.onFocusChanged { if (it.hasFocus) align(at, SpotlightTop) }) {
                            Spotlight(
                                deps,
                                tile,
                                onOpenSaga,
                                onOpenTitle,
                                firstRequester = if (current.featured.isEmpty() && current.shelves.isEmpty()) primary else null,
                            )
                        }
                    }
                }
                current.universes.forEach { universe ->
                    val at = index++
                    item(key = "universe-${universe.name}") {
                        CardRow(universe.name, universe.tiles, key = { it.key }, spacing = TileGap, testTag = "row-universe", onActive = { if (it) align(at) }, verticalPadding = TallPadding) { i, tile ->
                            val entries = remember(universe) { universe.tiles.map { SagaEntry(it) } }
                            SagaTallTile(
                                tile,
                                "saga-${tile.key}",
                                { _, origin -> onOpenSaga(entries, i, origin) },
                                requester = if (current.featured.isEmpty() && current.shelves.isEmpty() && current.spotlight == null && i == 0) primary else remember { FocusRequester() },
                            )
                        }
                    }
                }
                current.shelves.forEach { shelf ->
                    val at = index++
                    item(key = "shelf-${shelf.category.name}") {
                        val shown = remember(shelf) { shelf.entries.take(SHELF_ROW_SIZE) }
                        CardRow(shelf.category.title.text(), shown, key = { it.key }, spacing = TileGap, testTag = "shelf-${shelf.category.name}", onActive = { if (it) align(at) }) { i, entry ->
                            SagaEntryTile(
                                deps,
                                entry,
                                { origin -> onOpenSaga(shelf.entries, i, origin) },
                                requester = if (current.featured.isEmpty() && current.shelves.isEmpty() && current.spotlight == null && current.universes.isEmpty() && i == 0) primary else remember { FocusRequester() },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A category's page: all its sagas on four columns, the most voted first. */
@Composable
fun SagaShelfScreen(
    deps: AppDeps,
    shelf: SagaShelf,
    onClose: () -> Unit,
    onOpenSaga: (List<SagaEntry>, Int, CardOrigin?) -> Unit,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    focusRequesters: MutableMap<Int, FocusRequester> = remember { mutableMapOf() },
    active: Boolean = true,
) {
    val root = remember { FocusRequester() }
    val first = focusRequesters.getOrPut(0) { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!active) return@LaunchedEffect
        withFrameNanos { }
        FocusSource.lastWasMouse = false
        if (!first.focusIfPresent()) runCatching { root.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .testTag("saga-shelf")
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .focusRequester(root)
            .focusable(),
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(4),
            Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(start = Metrics.PageMargin + 8.dp, end = Metrics.PageMargin + 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap + 4.dp),
        ) {
            item(key = "name", span = { GridItemSpan(4) }) {
                Box(Modifier.fillMaxWidth().height(300.dp).padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    BasicText(shelf.category.title.text(), style = bigName(62), maxLines = 2)
                }
            }
            items(shelf.entries.size, key = { shelf.entries[it].key }) { i ->
                val entry = shelf.entries[i]
                val tile by produceState(entry.tile, entry.key) { if (value == null) value = entry.load(deps.catalog) }
                FocusCard(
                    TileWidth, TileHeight, { onOpenSaga(shelf.entries, i, it) },
                    modifier = Modifier.keepClearOfEdges(gridState, i + 1),
                    tag = "shelf-item-$i",
                    requester = focusRequesters.getOrPut(i) { FocusRequester() },
                ) {
                    val loaded = tile
                    Art(loaded?.backdrop ?: loaded?.poster, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(TileFade))
                    if (loaded != null) TileName(loaded, logoHeight = 118.dp, textSize = 25, bottom = 16.dp)
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("shelf-back"))
    }
}

/** A genre's, mood's or platform's page: its name, then four columns of titles, loading more on the way down. */
@Composable
fun CollectionDetailScreen(
    deps: AppDeps,
    request: CollectionRequest,
    onClose: () -> Unit,
    onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    state: CollectionState = remember(request) { CollectionState() },
    active: Boolean = true,
) {
    val items = state.items
    val grid = state.grid
    val nearEnd by remember(request, state) {
        derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= items.size - PAGE_AHEAD }
    }
    val root = remember { FocusRequester() }
    val first = state.focusRequesters.getOrPut(0) { FocusRequester() }

    LaunchedEffect(request, state, nearEnd, state.page, state.done, active) {
        if (!active || state.done || !nearEnd) return@LaunchedEffect
        val next = request.load(state.page + 1)
        if (next.isEmpty()) {
            state.done = true
        } else {
            val known = items.map { it.kind() to it.id }.toSet()
            items.addAll(next.filter { (it.kind() to it.id) !in known })
            state.page++
        }
    }
    LaunchedEffect(state) { if (active) runCatching { root.requestFocus() } }
    LaunchedEffect(state, items.isNotEmpty()) {
        if (active && items.isNotEmpty()) {
            withFrameNanos { }
            FocusSource.lastWasMouse = false
            runCatching { first.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .testTag("collection-detail")
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .focusRequester(root)
            .focusable(),
    ) {
        Box(Modifier.fillMaxWidth().height(760.dp)) {
            Art(request.backdrop ?: items.firstOrNull { it.backdrop != null }?.backdrop, Modifier.fillMaxSize().heavyBlur(90.dp), placeholder = Palette.Background)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x8C000000), 1f to Palette.Background)))
        }
        LazyVerticalGrid(
            GridCells.Fixed(4),
            Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(start = Metrics.PageMargin + 8.dp, end = Metrics.PageMargin + 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap + 4.dp),
        ) {
            item(key = "name", span = { GridItemSpan(4) }) {
                Box(Modifier.fillMaxWidth().height(300.dp).padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    SagaName(request.name, request.logo, 62, 190.dp, Modifier.fillMaxWidth().padding(horizontal = 160.dp).testTag("collection-name"))
                }
            }
            items(items.size, key = { items[it].kind() + items[it].id }) { i ->
                val item = items[i]
                FocusCard(
                    TileWidth, TileHeight, { onOpenTitle(items.toList(), i, it) },
                    modifier = Modifier.keepClearOfEdges(grid, i + 1),
                    tag = "collection-item-$i",
                    requester = state.focusRequesters.getOrPut(i) { FocusRequester() },
                ) {
                    SmartLandscape(deps.catalog, item)
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("collection-back"))
    }
}
