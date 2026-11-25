package com.sokoul.pc.anime

import androidx.compose.animation.animateColorAsState
import com.sokoul.pc.ui.glassSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.L
import com.sokoul.pc.data.PageRow
import com.sokoul.pc.data.anime.AniTitle
import com.sokoul.pc.data.anime.AnimeCategories
import com.sokoul.pc.data.anime.AnimeLink
import com.sokoul.pc.data.anime.EpisodeKind
import com.sokoul.pc.data.anime.FanParse
import com.sokoul.pc.data.anime.MediaKind
import com.sokoul.pc.data.anime.ReaderRequest
import com.sokoul.pc.data.anime.openingOn
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.launch

private data class Tool(val name: L, val tag: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val action: (CardOrigin) -> Unit)

/** The rows only this page has, drawn with the same cards and the same headings as every other row. */
@Composable
fun AnimePageRow(
    deps: AppDeps,
    row: PageRow,
    up: FocusRequester?,
    onActive: (Boolean) -> Unit,
    focusFor: (Int) -> FocusRequester?,
    onHub: (AnimeHubView, MediaKind, String?, CardOrigin?) -> Unit,
    onWork: (AniTitle, CardOrigin?) -> Unit,
    onRead: (ReaderRequest) -> Unit,
) {
    val anime = deps.anime ?: return
    val scope = rememberCoroutineScope()
    when (row) {
        is PageRow.AnimeTools -> {
            val tools = listOfNotNull(
                Tool(L("This week's calendar", "Calendrier de la semaine"), "tool-calendar", androidx.compose.material.icons.Icons.Filled.CalendarMonth) { onHub(AnimeHubView.Calendar, MediaKind.ANIME, null, it) },
                Tool(L("Sort anime by category", "Trier les animés par catégorie"), "tool-categories", androidx.compose.material.icons.Icons.Filled.Category) { onHub(AnimeHubView.Categories, MediaKind.ANIME, null, it) },
                if (com.sokoul.pc.Platform.IS_TV) null else Tool(L("Open a comic file", "Ouvrir un fichier de manga"), "tool-file", androidx.compose.material.icons.Icons.Filled.FolderOpen) {
                    scope.launch { com.sokoul.pc.Platform.pickComicFile()?.let { f -> anime.readerFromFile(f)?.let(onRead) } }
                },
            )
            CardRow(L("Explore", "Explorer").text(), tools, key = { it.tag }, testTag = "row-anime-tools", onActive = onActive, upTarget = up) { i, t ->
                ToolTile(t.name.text(), t.tag, focusFor(i), t.icon) { origin -> t.action(origin) }
            }
        }
        is PageRow.AnimeGenres -> CardRow(L("Genres", "Genres").text(), AnimeCategories.GENRES, key = { it }, testTag = "row-anime-genres", onActive = onActive, upTarget = up) { i, g ->
            // The coloured genre tiles of the other pages, rather than grey boxes.
            com.sokoul.pc.collections.GenreCard(com.sokoul.pc.data.GenreTile(i, "anime", AnimeCategories.label(g), null), i, focusFor(i) ?: remember { FocusRequester() }) { _, origin ->
                onHub(AnimeHubView.Categories, MediaKind.ANIME, g, origin)
            }
        }
        is PageRow.Reading -> {
            // A file of one's own has no page to go back to: it opens from "Open a comic file".
            val list = remember(deps.uiRevision) { anime.readingList().filter { it.key.toIntOrNull() != null } }
            CardRow(L("Keep reading", "Continuer la lecture").text(), list, key = { it.key }, testTag = "row-reading", onActive = onActive, upTarget = up) { _, e ->
                FocusCard(READ_W, READ_H, { origin -> onWork(placeholder(e.key, e.title, e.cover), origin) }) { focused ->
                    Art(e.cover, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xE6000000))))
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
                        Label(e.title, Type.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                        Label(e.chapter, Type.Body, if (focused) Palette.OnBackground else Palette.Secondary, maxLines = 1)
                    }
                }
            }
        }
        is PageRow.Books -> {
            val titles by produceState<List<AniTitle>?>(null, row.query) {
                value = try {
                    anime.client.titles(row.query)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }.distinctBy { it.id }
            }
            val list = titles
            // A loading shape keeps the row's place: it showed nothing until its books came, then pushed the rows below
            // down (2026-11-24).
            if (list == null) {
                com.sokoul.pc.ui.SkeletonRow(landscape = false, cardWidth = READ_W, cardHeight = READ_H)
            } else if (list.isNotEmpty()) {
                CardRow(row.title.text(), list, key = { it.id }, testTag = "row-${row.id}", onActive = onActive, upTarget = up) { i, t ->
                    AniCard(t, tag = "${row.id}-$i") { origin -> onWork(t, origin) }
                }
            }
        }
        else -> Unit
    }
}

private val READ_W = 210.dp
private val READ_H = 300.dp

private fun placeholder(key: String, title: String, cover: String?) = AniTitle(
    key.toIntOrNull() ?: 0, MediaKind.MANGA, title, null, null, cover, null, "MANGA", null, null, null, null, null, null, null, emptyList(), emptyList(), null, null, null, null, null,
)

@Composable
internal fun ToolTile(label: String, tag: String, requester: FocusRequester?, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: (CardOrigin) -> Unit) {
    val own = remember { FocusRequester() }
    FocusCard(TOOL_W, TOOL_H, onClick, tag = tag, requester = requester ?: own) { focused ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))))
        icon?.let { androidx.compose.material3.Icon(it, null, Modifier.padding(18.dp).size(38.dp), tint = if (focused) Palette.OnBackground else Palette.BarText) }
        Label(label, Type.Meta.copy(fontWeight = FontWeight.SemiBold), if (focused) Palette.OnBackground else Palette.BarText, modifier = Modifier.align(Alignment.BottomStart).padding(18.dp), maxLines = 2)
    }
}

private val TOOL_W = 320.dp
private val TOOL_H = 150.dp

/**
 * Which chapters an episode adapts, whether it is filler, and which opening it plays. The chapters are asked once for the
 * whole season by the row: asked by each card, every card made ahead of the scroll started a request of its own.
 */
@Composable
fun EpisodeNote(link: AnimeLink, absolute: Int, kind: EpisodeKind?, chapters: List<Int>) {
    val theme = link.openingOn(absolute)
    val parts = listOfNotNull(
        chapters.takeIf { it.isNotEmpty() }?.let { c -> FanParse.chapterRuns(c).let { if (c.size == 1) L("Chapter $it", "Chapitre $it").text() else L("Chapters $it", "Chapitres $it").text() } },
        when (kind) {
            EpisodeKind.Filler -> L("Filler", "Hors-série").text()
            EpisodeKind.Mixed -> L("Partly filler", "En partie hors-série").text()
            EpisodeKind.AnimeCanon -> L("Anime only", "Propre à l'animé").text()
            else -> null
        },
        theme?.song?.takeIf { it.isNotEmpty() }?.let { "♪ $it" },
    )
    if (parts.isNotEmpty()) Label(parts.joinToString(" · "), Type.Body.copy(fontWeight = FontWeight.SemiBold), Palette.BarText, maxLines = 1, modifier = Modifier.testTag("episode-note-$absolute"))
}

@Composable
fun FillerPill(hide: Boolean, onClick: () -> Unit) {
    var has by remember { mutableStateOf(false) }
    // Read when drawing, not in composition: the pill is not composed again at each frame of its fade.
    val fill = animateColorAsState(if (has) Palette.FocusFill else if (hide) Palette.ButtonFill else Color.Transparent)
    Box(
        Modifier.height(Metrics.ButtonHeight).clip(RoundedCornerShape(50)).drawBehind { drawRect(fill.value) }
            .tvFocusable(onFocusChanged = { has = it }, onClick = onClick).padding(horizontal = 30.dp).testTag("hide-filler"),
        contentAlignment = Alignment.Center,
    ) {
        Label(if (hide) L("Fillers hidden", "Hors-séries masqués").text() else L("Hide fillers", "Masquer les hors-séries").text(), Type.Button, if (has) Palette.OnFocus else if (hide) Palette.OnBackground else Palette.Secondary, maxLines = 1)
    }
}

/** On pause: "This episode adapts chapters 2–3", and the book opened at that place. Only when the wiki records it. */
@Composable
fun PauseChapterCard(deps: AppDeps, onRead: (ReaderRequest) -> Unit) {
    val anime = deps.anime ?: return
    val player = deps.player ?: return
    val status by player.status.collectAsState()
    val request = deps.playback.request
    val link = request?.let { anime.linkKnown(it.tmdbId) }
    val book = link?.manga
    val chapters by produceState(emptyList<Int>(), request?.tmdbId, request?.season, request?.episode) {
        val q = request
        value = if (q?.season == null || q.episode == null || link == null) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { anime.absoluteEpisode(q.tmdbId, q.season, q.episode)?.let { anime.chaptersOf(link, it) }.orEmpty() }.getOrDefault(emptyList()) }
    }
    if (!status.paused || chapters.isEmpty() || book == null) return
    // Asked now and not on the press: a button that does nothing is worse than no button.
    val readable by produceState<List<com.sokoul.pc.data.anime.MangaChapter>?>(null, book.id) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { anime.chaptersOf(book) }.getOrDefault(emptyList()) } }
    val at = readable?.indexOfFirst { it.number?.toDoubleOrNull()?.toInt() == chapters.first() } ?: -1
    // Below the "better version ready" offer, which sits at the top right too and hid the card's title.
    Box(Modifier.fillMaxSize().padding(top = 170.dp, end = 60.dp), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier.width(430.dp).glassSurface(RoundedCornerShape(28.dp), 28.dp).padding(24.dp).testTag("pause-chapter"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val runs = FanParse.chapterRuns(chapters)
            Label(L("This episode adapts", "Cet épisode adapte").text(), Type.Body, Palette.Secondary, maxLines = 1)
            Label((if (chapters.size == 1) L("Chapter $runs", "Chapitre $runs") else L("Chapters $runs", "Chapitres $runs")).text(), Type.RowTitle, maxLines = 2)
            if (at >= 0) {
                com.sokoul.pc.ui.PillButton(L("Read the chapter", "Lire le chapitre").text(), { readable?.let { onRead(anime.readerRequest(book, it, at, 0)) } }, Modifier.testTag("pause-read"))
            } else if (readable != null) {
                Label(L("Not available to read here", "Pas disponible à la lecture ici").text(), Type.Body, Palette.Secondary, maxLines = 2, modifier = Modifier.testTag("pause-unreadable"))
            }
        }
    }
}
