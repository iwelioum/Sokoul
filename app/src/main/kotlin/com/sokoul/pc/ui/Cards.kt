package com.sokoul.pc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.InterDisplay
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.data.kind
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/** Where a card sits when activated: the transition starts there, and focus returns there. */
data class CardOrigin(val requester: FocusRequester, val rect: Rect)

/** The shared card behavior: focus by hover or arrows, scale ×1.10, shadow. */
@Composable
fun FocusCard(
    width: Dp,
    height: Dp,
    onClick: (CardOrigin) -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = CardMetrics.Corner,
    circle: Boolean = false,
    tag: String? = null,
    requester: FocusRequester = remember { FocusRequester() },
    onFocus: (Boolean) -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Where the card is, read only when it is activated: kept as a plain field, it costs nothing while rows scroll.
    val place = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val pinch = LocalPinch.current
    val activate = { onClick(CardOrigin(requester, place[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero)) }
    val scale by animateFloatAsState(if (focused) CardMetrics.FocusScale else 1f)
    val shape = if (circle) CircleShape else RoundedCornerShape(corner)
    val nudge = rememberNudge()
    Box(
        modifier
            .size(width, height)
            // One layer per card: zoom, nudge, the outline clip and the focused card's shadow. No offscreen texture: every
            // card at rest was rendered into its own one, and as images arrived and faded in while a title page scrolled,
            // each changed card cost one more render pass before the frame (68–86 % janky frames on the Google TV
            // Streamer, 2026-11-24; jetpack-compose-performance audit). The shadow only under the focused card, as before.
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = nudge.x.value; translationY = nudge.y.value
                shadowElevation = if (focused) 24.dp.toPx() else 0f
                this.shape = shape
                clip = true
            }
            .onPlaced { place[0] = it }
            .let { if (tag != null) it.testTag(tag) else it }
            .tvFocusable(
                onFocusChanged = {
                    focused = it
                    if (it) pinch.focusedCard = activate
                    onFocus(it)
                },
                onClick = activate,
                requester = requester,
                onDeadEnd = nudge::kick,
            ),
    ) {
        content(focused)
    }
}

private const val ROW_PREFETCH = 7
/** The cards of a row are of one kind: their compositions are reused as the row scrolls. */
private const val ROW_CARD = "card"

private val BottomFade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6000000))

@Composable
fun PosterCard(
    catalog: com.sokoul.pc.data.CatalogApi,
    item: com.sokoul.iptv.data.net.TmdbItem,
    caption: String?,
    captionAlways: Boolean,
    rank: Int?,
    onClick: (CardOrigin) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    onFocus: (Boolean) -> Unit = {},
    requester: FocusRequester = remember { FocusRequester() },
    width: Dp = CardMetrics.PosterWidth,
    height: Dp = CardMetrics.PosterHeight,
) {
    // Asked here, not in the poster: the card has to know whether the poster says which work it is.
    val info by produceState<com.sokoul.iptv.data.net.TmdbInfo?>(null, item.id) { value = catalog.artwork(item.kind(), item.id) }
    val art = posterArt(item, info)
    // A poster lettered in a script the viewer cannot read (an anime's, in Japanese) gets its title under it, as the
    // Apple TV app captions its rows: a row of them said nothing about which work each one was (Google TV, 2026-11-24).
    val named = art is PosterArt.Original && unreadableScript(item.originalLanguage ?: info?.originalLanguage)
    FocusCard(width, height, onClick, modifier, tag = tag, onFocus = onFocus, requester = requester) { focused ->
        val showCaption = caption != null && (captionAlways || focused)
        SmartPoster(item, art)
        if (named) {
            Box(Modifier.fillMaxSize().background(BottomFade))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(item.title, Type.Body.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), Palette.OnBackground, Modifier.fillMaxWidth(), maxLines = 2)
                if (showCaption) Label(caption!!, Type.Body.copy(textAlign = TextAlign.Center), if (focused) Palette.OnBackground else Palette.Secondary, Modifier.fillMaxWidth(), maxLines = 1)
            }
        } else if (showCaption) {
            Label(
                caption!!,
                Type.Body.copy(textAlign = TextAlign.Center),
                if (focused) Palette.OnBackground else Palette.Secondary,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                maxLines = 1,
            )
        }
        if (rank != null) {
            BasicText(
                rank.toString(),
                Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 6.dp),
                style = Type.RowTitle.copy(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 72.sp, color = Color(0xE6FFFFFF)),
            )
        }
    }
}

@Composable
fun LandscapeCard(
    image: String?,
    meta: String,
    progress: Float?,
    onClick: (CardOrigin) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = CardMetrics.ContinueWidth,
    height: Dp = CardMetrics.ContinueHeight,
    title: String? = null,
    requester: FocusRequester = remember { FocusRequester() },
) {
    FocusCard(width, height, onClick, modifier, requester = requester) { focused ->
        Art(image, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(BottomFade))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp, vertical = 14.dp)) {
            if (title != null) {
                Label(title, Type.Meta, Palette.OnBackground, Modifier.padding(bottom = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.PlayArrow, null, tint = if (focused) Palette.OnBackground else Palette.Secondary, modifier = Modifier.size(26.dp))
                if (progress != null) {
                    Box(Modifier.size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Secondary.copy(alpha = 0.5f))) {
                        Box(Modifier.fillMaxSize(progress.coerceIn(0.05f, 1f)).background(com.sokoul.pc.theme.AccentTheme.color))
                    }
                }
                Label(meta, Type.Body, if (focused) Palette.OnBackground else Palette.Secondary, maxLines = 1)
            }
        }
    }
}

/**
 * Keeps a row's item clear of the screen edges when the arrows reach it. Compose only scrolls until the item touches the
 * edge, where the focus zoom would be cut off. A mouse hovering a card leaves the row where it is.
 */
@Composable
fun Modifier.keepClearOfEdges(state: LazyListState, index: Int, margin: Dp = Metrics.PageMargin): Modifier {
    val scope = rememberCoroutineScope()
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    return onFocusChanged { focus ->
        if (focus.hasFocus && !FocusSource.lastWasMouse) {
            scope.launch {
                withFrameNanos { }
                val layout = state.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
                val start = item.offset + layout.beforeContentPadding
                val end = start + item.size
                val width = layout.viewportSize.width
                val delta = when {
                    end > width - marginPx -> end - (width - marginPx)
                    start < marginPx -> start - marginPx
                    else -> 0f
                }
                if (delta != 0f) state.animateScrollBy(delta)
            }
        }
    }
}

/** Keeps the focused grid card and its zoom inside the vertical safe area. */
@Composable
fun Modifier.keepClearOfEdges(state: LazyGridState, index: Int, margin: Dp = Metrics.PageMargin): Modifier {
    val scope = rememberCoroutineScope()
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    var alignmentJob by remember { mutableStateOf<Job?>(null) }
    return onFocusChanged { focus ->
        alignmentJob?.cancel()
        if (focus.hasFocus && !FocusSource.lastWasMouse) {
            alignmentJob = scope.launch {
                withFrameNanos { }
                val layout = state.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@launch
                val start = item.offset.y
                val end = start + item.size.height
                val height = layout.viewportSize.height
                val delta = when {
                    end > height - marginPx -> end - (height - marginPx)
                    start < marginPx -> start - marginPx
                    else -> 0f
                }
                if (delta != 0f) state.animateScrollBy(delta)
            }
        }
    }
}

/** Row title that turns white while the row holds focus, above a horizontally scrolling row. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun <T> CardRow(
    title: String,
    items: List<T>,
    key: ((T) -> Any)? = null,
    spacing: Dp = CardMetrics.Gap,
    testTag: String? = null,
    onActive: (Boolean) -> Unit = {},
    upTarget: FocusRequester? = null,
    onUp: () -> Unit = {},
    verticalPadding: Dp = CardMetrics.RowVerticalPadding,
    /** The row's kind in small capitals above its title ("TRENDING"), and one line saying what it holds under it. */
    card: @Composable (index: Int, item: T) -> Unit,
) {
    var active by remember { mutableStateOf(false) }
    // A row about to come into view has its visible cards made ahead, while the page is idle: by default only two were,
    // and the others were made in the middle of the scroll.
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val rowState = rememberLazyListState(prefetchStrategy = remember { androidx.compose.foundation.lazy.LazyListPrefetchStrategy(nestedPrefetchItemCount = ROW_PREFETCH) })
    // The card that had focus last, and a way to reach each card on screen: plain holders, read only on a key press.
    val lastFocused = remember { intArrayOf(-1) }
    val itemRequesters = remember { HashMap<Int, FocusRequester>() }
    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.hasFocus != active) onActive(it.hasFocus)
                active = it.hasFocus
            }
            .onPreviewKeyEvent { e ->
                if (upTarget != null && e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                    FocusSource.lastWasMouse = false
                    onUp()
                    upTarget.focusIfPresent()
                } else {
                    false
                }
            }
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        RowHeading(title, active)
        LazyRow(
            state = rowState,
            // Left and right stay in the row: at its ends nothing moves, and at the left edge the menu can open.
            modifier = Modifier
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Left || direction == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default
                    }
                    // Coming back into the row lands on the card left there, not on whichever card lies nearest. Only
                    // while that card is still laid out: one scrolled away gives the usual nearest card.
                    enter = {
                        val i = lastFocused[0]
                        val r = itemRequesters[i]
                        if (r != null && rowState.layoutInfo.visibleItemsInfo.any { it.index == i }) r else FocusRequester.Default
                    }
                }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = Metrics.PageMargin, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }, contentType = { _, _ -> ROW_CARD }) { index, item ->
                val itemRequester = remember { FocusRequester() }
                androidx.compose.runtime.DisposableEffect(index, itemRequester) {
                    itemRequesters[index] = itemRequester
                    onDispose { if (itemRequesters[index] === itemRequester) itemRequesters.remove(index) }
                }
                Box(
                    Modifier
                        .focusRequester(itemRequester)
                        .onFocusChanged { if (it.hasFocus) lastFocused[0] = index }
                        .keepClearOfEdges(rowState, index),
                ) { card(index, item) }
            }
        }
    }
}

@Composable
fun RowHeading(title: String, active: Boolean) {
    // One title per row, a word or a short phrase: no kicker above, no line under (the owner's rule, 2026-11-26).
    val color by animateColorAsState(if (active) Palette.OnBackground else Palette.Secondary)
    Label(title, Type.RowTitle, color, Modifier.padding(start = Metrics.PageMargin), maxLines = 1)
}

/** Round action button: dark circle at rest, white and slightly larger with focus. */
@Composable
fun RoundButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester = remember { FocusRequester() },
    onFocused: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Metrics.ButtonFocusScale else 1f)
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.ButtonFill)
    Box(
        modifier
            .size(Metrics.ButtonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, if (focused) Color.Transparent else Palette.Hairline, CircleShape)
            .tvFocusable(onFocusChanged = { focused = it; onFocused(it) }, onClick = onClick, requester = requester),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (focused) Palette.OnFocus else Palette.OnBackground, modifier = Modifier.size(34.dp))
    }
}

/** Main action: pill with a play icon. */
@Composable
fun PlayPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    requester: FocusRequester = remember { FocusRequester() },
    onFocused: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Metrics.ButtonFocusScale else 1f)
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.ButtonFill)
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(Metrics.ButtonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .border(1.dp, if (focused) Color.Transparent else Palette.Hairline, shape)
            .tvFocusable(onFocusChanged = { focused = it; onFocused(it) }, onClick = onClick, requester = requester)
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (focused) Palette.OnFocus else Palette.OnBackground
        Icon(Icons.Filled.PlayArrow, null, tint = tint, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(8.dp))
        BasicText(label, style = Type.Button.copy(color = tint))
    }
}

/** Bordered age-rating badge such as [-16]. */
@Composable
fun OutlinedBadge(text: String) {
    Box(Modifier.border(2.dp, Palette.OnBackground, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 1.dp)) {
        BasicText(text, style = Type.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.OnBackground))
    }
}

@Composable
fun SingleLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = style.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}
