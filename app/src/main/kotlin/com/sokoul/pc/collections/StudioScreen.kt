package com.sokoul.pc.collections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.ui.ImageLoader
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.L
import com.sokoul.pc.data.Studio
import com.sokoul.pc.data.StudioSection
import com.sokoul.pc.home.TitleRow
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.BackButton
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.PillButton
import kotlinx.coroutines.launch

/**
 * A studio's page, after Disney+'s brand pages (Disney, Pixar, Marvel…) and adapted to Sokoul: the picture of its most
 * watched title behind the studio's logo in white, a line saying what the page holds and "See All", then its sections —
 * the most watched, the latest, the best rated, its series, its classics. A section with too few titles does not show.
 */
@Composable
fun StudioScreen(
    deps: AppDeps,
    studio: Studio,
    logo: String?,
    onClose: () -> Unit,
    onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit,
    onOpenRequest: (CollectionRequest, CardOrigin?) -> Unit,
    active: Boolean = true,
) {
    val rows = remember(studio) { mutableStateMapOf<StudioSection, List<TmdbItem>>() }
    LaunchedEffect(studio) {
        StudioSection.entries.forEach { s ->
            launch { rows[s] = runCatching { deps.catalog.studioSection(studio, s, 1) }.getOrDefault(emptyList()) }
        }
    }
    val shownLogo by produceState(logo, studio) { if (value == null) value = runCatching { deps.catalog.studioLogos()[studio.companyId] }.getOrNull() }
    val hero = rows[StudioSection.Popular]?.firstOrNull { it.backdrop != null }
    val sections = StudioSection.entries.filter { (rows[it]?.size ?: 0) >= MIN_ITEMS }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val seeAll = remember { FocusRequester() }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        withFrameNanos { }
        runCatching { seeAll.requestFocus() }
    }
    val everything = { origin: CardOrigin? ->
        onOpenRequest(CollectionRequest(studio.label, shownLogo, hero?.backdrop) { page -> deps.catalog.studio(studio, page) }, origin)
    }
    Box(
        Modifier.fillMaxSize().background(Palette.Background).testTag("studio-page")
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false }
            .focusable(),
    ) {
        // The picture stays where it is while the sections scroll over it, as under a title page.
        // It fades as the sections come up, so that a bright picture never sits under a row of posters.
        Box(
            Modifier.fillMaxWidth().height(HERO_HEIGHT).graphicsLayer {
                alpha = if (list.firstVisibleItemIndex > 0) 0f else (1f - list.firstVisibleItemScrollOffset / HERO_FADE_PX).coerceIn(0f, 1f)
            },
        ) {
            Art(hero?.backdrop, Modifier.fillMaxSize(), placeholder = Palette.Background)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE6000000), 0.55f to Color(0x66000000), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.Background)))
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "head") {
                Column(
                    Modifier.fillMaxWidth().height(HEAD_HEIGHT).padding(start = Metrics.PageMargin, bottom = 56.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Label(L("STUDIO", "STUDIO").text(), Type.Meta, Palette.Secondary, maxLines = 1)
                    StudioLogo(shownLogo, studio, Modifier.padding(top = 18.dp, bottom = 22.dp))
                    Label(
                        L("Its films and series, from the most watched to its classics.", "Ses films et ses séries, des plus regardés à ses classiques.").text(),
                        Type.Body, Palette.Secondary, Modifier.widthIn(max = 900.dp), maxLines = 2,
                    )
                    PillButton(
                        L("See All", "Tout voir").text(), { everything(null) },
                        Modifier.padding(top = 30.dp).testTag("studio-see-all"), seeAll,
                    )
                }
            }
            sections.forEachIndexed { index, section ->
                item(key = section.name) {
                    TitleRow(
                        deps, "studio-${section.name.lowercase()}", section.title.text(), section.style, rows[section].orEmpty(),
                        up = if (index == 0) seeAll else null,
                        onActive = { on -> if (on) scope.launch { list.animateScrollToItem(index + 1) } },
                        focusFor = { null },
                        onOpen = onOpenTitle,
                        onSeeAll = { origin ->
                            onOpenRequest(CollectionRequest(section.title.text() + " · " + studio.label, null, null) { page -> deps.catalog.studioSection(studio, section, page) }, origin)
                        },
                    )
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("studio-back"))
    }
}

/** The studio's logo in white, as on its tile; its name in display type when the catalog has no logo. */
@Composable
private fun StudioLogo(logo: String?, studio: Studio, modifier: Modifier) {
    val image by produceState(logo?.let(ImageLoader::cached), logo) { value = logo?.let { ImageLoader.load(it) } }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap, studio.label, modifier.height(LOGO_HEIGHT).widthIn(max = 720.dp), Alignment.CenterStart, ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.White),
        )
    } else {
        Label(studio.label, Type.RowTitle.copy(fontSize = Type.RowTitle.fontSize * 2.2f), modifier = modifier, maxLines = 1)
    }
}

private val HERO_HEIGHT = 900.dp
private val HEAD_HEIGHT = 760.dp
private val LOGO_HEIGHT = 150.dp
private const val MIN_ITEMS = 4
/** Scrolled pixels over which the picture fades out. */
private const val HERO_FADE_PX = 700f
