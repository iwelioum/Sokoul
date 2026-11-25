package com.sokoul.pc.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.L
import com.sokoul.pc.data.anime.AnimeLink
import com.sokoul.pc.data.anime.AnimeTheme
import com.sokoul.pc.data.anime.MangaChapter
import com.sokoul.pc.data.anime.ReaderRequest
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.launch

/**
 * On the page of a title, below its episodes and trailers: where to watch it, the book and its reader, the calendar, the
 * music and the films around it. The episodes are not part of it: drawn here, they were made again when the anime's link
 * arrived (back to season 1, reloaded, focus lost), and these rows came in above the cast (2026-11-24).
 */
@Composable
fun AnimeSections(
    deps: AppDeps,
    item: TmdbItem,
    link: AnimeLink,
    onRead: (ReaderRequest) -> Unit,
    onPlayThemes: (List<AnimeTheme>, Int, com.sokoul.pc.ui.CardOrigin?) -> Unit,
    onOpenWork: (com.sokoul.pc.data.anime.AniTitle, com.sokoul.pc.ui.CardOrigin) -> Unit = { _, _ -> },
) {
    // One page for the whole work, every part one row below the other: tabs could not be reached with the remote, so
    // the book, the calendar and the music were out of reach on the TV.
    Column(Modifier.fillMaxWidth().testTag("anime-sections"), verticalArrangement = Arrangement.spacedBy(36.dp)) {
        SourceLinks(L("Where to watch", "Où regarder").text(), link.streamingLinks, "anime-streaming-sources")
        if (link.manga != null) MangaSection(deps, item, link, onRead)
        CalendarRow(link)
        ThemesRow(link.themes, onPlayThemes)
        if (link.adaptations.isNotEmpty()) FansRow(deps, link, onOpenWork)
    }
}

/** External destinations keep their provider and language; the existing player handles in-app sources. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SourceLinks(title: String, links: List<com.sokoul.pc.data.anime.AniLink>, tag: String) {
    val sources = links.filter { it.site.isNotBlank() }.distinctBy { it.url }
    if (sources.isEmpty()) return
    CardRow(title, sources, key = { it.url }, testTag = tag) { _, source ->
        val reveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        val scope = rememberCoroutineScope()
        val bounds = with(androidx.compose.ui.platform.LocalDensity.current) {
            androidx.compose.ui.geometry.Rect(-64.dp.toPx(), -64.dp.toPx(), 344.dp.toPx(), 200.dp.toPx())
        }
        FocusCard(280.dp, 136.dp, { com.sokoul.pc.Platform.openUrl(source.url) },
            modifier = Modifier.bringIntoViewRequester(reveal),
            onFocus = { if (it && !com.sokoul.pc.ui.FocusSource.lastWasMouse) scope.launch { reveal.bringIntoView(bounds) } }) { focused ->
            Column(Modifier.fillMaxSize().background(if (focused) Palette.FocusFill else Palette.PillFill).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(source.site, Type.Meta.copy(fontWeight = FontWeight.Bold), if (focused) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
                Label(listOfNotNull(source.language, L("Open website", "Ouvrir le site").text()).joinToString(" · "),
                    Type.Body, if (focused) Palette.OnFocus else Palette.Secondary, maxLines = 2)
            }
        }
    }
}

/**
 * Where the work stands: the next episode and when it airs, and how far the book has got. Nothing to show for a work
 * that is over on both sides.
 */
@Composable
private fun CalendarRow(link: AnimeLink) {
    val zone = java.time.ZoneId.systemDefault()
    val airing = (link.seasons + link.anime).filter { it.nextAt != null && it.nextEpisode != null }.minByOrNull { it.nextAt!! }
    val book = link.manga
    val cards = listOfNotNull(
        airing?.let { a ->
            val at = java.time.Instant.ofEpochMilli(a.nextAt!!).atZone(zone)
            val day = java.time.format.DateTimeFormatter.ofPattern(if (com.sokoul.pc.i18n.Strings.isFrench()) "EEEE d MMMM 'à' HH:mm" else "EEEE, MMMM d 'at' HH:mm", java.util.Locale.getDefault()).format(at)
            CalendarCard(L("Next episode", "Prochain épisode").text(), L("Episode ${a.nextEpisode}", "Épisode ${a.nextEpisode}").text(), day.replaceFirstChar { it.uppercase() })
        },
        book?.let { b ->
            val running = b.status == "RELEASING"
            val count = b.chapters?.let { L("$it chapters", "$it chapitres").text() }
            CalendarCard(
                L("The manga", "Le manga").text(),
                if (running) L("Still coming out", "Toujours en cours").text() else L("Finished", "Terminé").text(),
                count ?: if (running) L("New chapters every week or month", "De nouveaux chapitres chaque semaine ou chaque mois").text() else "",
            )
        },
        (link.anime.status == "FINISHED" && airing == null).takeIf { it }?.let {
            CalendarCard(L("The anime", "L'animé").text(), L("Finished", "Terminé").text(), link.anime.episodes?.let { L("$it episodes", "$it épisodes").text() }.orEmpty())
        },
    )
    if (cards.isEmpty()) return
    CardRow(L("Calendar", "Calendrier").text(), cards, key = { it.kicker }, testTag = "row-calendar") { i, c ->
        FocusCard(CALENDAR_W, CALENDAR_H, { }, tag = "calendar-$i") { focused ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(c.kicker.uppercase(), Type.Body.copy(fontWeight = FontWeight.Bold), Palette.Secondary, maxLines = 1)
                Label(c.title, Type.Meta.copy(fontWeight = FontWeight.SemiBold), if (focused) Palette.OnBackground else Palette.BarText, maxLines = 1)
                if (c.detail.isNotEmpty()) Label(c.detail, Type.Body, Palette.Secondary, maxLines = 2)
            }
        }
    }
}

private data class CalendarCard(val kicker: String, val title: String, val detail: String)

private val CALENDAR_W = 460.dp
private val CALENDAR_H = 180.dp

/** Films, OVAs and other works tied to this one. */
@Composable
private fun FansRow(deps: AppDeps, link: AnimeLink, onOpenWork: (com.sokoul.pc.data.anime.AniTitle, com.sokoul.pc.ui.CardOrigin) -> Unit) {
    CardRow(L("Films, OVAs and more", "Films, OAV et plus").text(), link.adaptations, key = { it.id }, testTag = "row-fans") { _, w ->
        AniCard(w) { origin -> onOpenWork(w, origin) }
    }
}

/** Openings and endings, each with the episodes it plays on. Choosing one goes on through the others. */
@Composable
private fun ThemesRow(themes: List<AnimeTheme>, onPlay: (List<AnimeTheme>, Int, com.sokoul.pc.ui.CardOrigin?) -> Unit) {
    if (themes.isEmpty()) return
    CardRow(L("Music", "Musiques").text(), themes, key = { it.slug + it.video }, testTag = "row-themes") { i, t ->
        FocusCard(CardMetrics.EpisodeWidth, THEME_H, { origin -> onPlay(themes, i, origin) }, tag = "theme-$i") { focused ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(if (t.isOpening) Color(0xFF3A3A3C) else Color(0xFF2C2C2E), Color(0xFF1C1C1E)))))
            Icon(Icons.Filled.MusicNote, null, Modifier.align(Alignment.TopEnd).padding(16.dp).size(34.dp), tint = if (focused) Palette.OnBackground else Palette.Secondary)
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(t.slug, Type.Body.copy(fontWeight = FontWeight.Bold), Palette.Secondary, maxLines = 1)
                Label(t.song.ifEmpty { t.slug }, Type.Meta.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                t.artist?.let { Label(it, Type.Body, Palette.BarText, maxLines = 1) }
                t.episodes?.let { e ->
                    val many = themes.any { it.season != t.season }
                    val season = if (many) L("Season ${t.season + 1} · ", "Saison ${t.season + 1} · ").text() else ""
                    Label(season + (if (e.any { !it.isDigit() }) L("Episodes $e", "Épisodes $e") else L("Episode $e", "Épisode $e")).text(), Type.Body, Palette.Secondary, maxLines = 1)
                }
            }
        }
    }
}

private val THEME_H = 220.dp

/** The book: where to go on from, its chapters, and where to read it when they are not here. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun MangaSection(deps: AppDeps, item: TmdbItem?, link: AnimeLink, onRead: (ReaderRequest) -> Unit) {
    val anime = deps.anime ?: return
    val scope = rememberCoroutineScope()
    val book = link.manga ?: return
    val chapters by produceState<List<MangaChapter>?>(null, book.id) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { anime.chaptersOf(book) }.getOrDefault(emptyList()) } }
    val resume = remember(book.id, deps.uiRevision) { anime.readingOf(book.id) }
    // "You stopped at episode 25: the story goes on at chapter 80."
    val goOn by produceState<Pair<Int, Int>?>(null, item?.id, link) {
        value = item?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { anime.chapterAfterWatched(it, link) }.getOrNull() } }
    }
    val list = chapters
    val first = remember { FocusRequester() }
    val readReveal = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var readSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val readRoom = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    Column(Modifier.fillMaxWidth().testTag("manga-section"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.padding(horizontal = Metrics.PageMargin), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Art(book.cover, Modifier.size(240.dp, 340.dp).clip(RoundedCornerShape(CardMetrics.Corner)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label(book.title, Type.RowTitle, maxLines = 2, modifier = Modifier.testTag("manga-title"))
                Label(mangaMeta(book, list?.size), Type.Body.copy(fontWeight = FontWeight.SemiBold), Palette.BarText, maxLines = 1)
                book.description?.let { Label(it, Type.Body, Palette.Secondary, maxLines = 4) }
                goOn?.let { (episode, chapter) ->
                    Label(L("You stopped at episode $episode: the story goes on at chapter $chapter.", "Tu t'es arrêté à l'épisode $episode : l'histoire continue au chapitre $chapter.").text(), Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.testTag("manga-go-on"), maxLines = 1)
                }
                Row(Modifier.padding(top = 8.dp).bringIntoViewRequester(readReveal)
                    .onSizeChanged { readSize = it }
                    .onFocusChanged { state ->
                        if (state.hasFocus && !com.sokoul.pc.ui.FocusSource.lastWasMouse) scope.launch {
                            readReveal.bringIntoView(androidx.compose.ui.geometry.Rect(
                                -readRoom, -readRoom, readSize.width + readRoom, readSize.height + readRoom))
                        }
                    }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!list.isNullOrEmpty()) {
                        val start = resume?.let { r -> list.indexOfFirst { it.number == r.chapter }.takeIf { it >= 0 } }
                        val fromAnime = goOn?.let { (_, chapter) -> list.indexOfFirst { it.number?.toDoubleOrNull()?.toInt() == chapter }.takeIf { it >= 0 } }
                        PillButton(if (start != null) L("Resume chapter ${resume.chapter}", "Reprendre le chapitre ${resume.chapter}").text() else L("Read", "Lire").text(), {
                            onRead(anime.readerRequest(book, list, start ?: 0, resume?.page ?: 0))
                        }, Modifier.testTag("manga-read"), first)
                        if (fromAnime != null) {
                            PillButton(L("Go on after the anime", "Continuer après l'animé").text(), { onRead(anime.readerRequest(book, list, fromAnime, 0)) }, Modifier.testTag("manga-after-anime"))
                        }
                    }
                    if (!com.sokoul.pc.Platform.IS_TV) PillButton(L("Open a file", "Ouvrir un fichier").text(), {
                        scope.launch { com.sokoul.pc.Platform.pickComicFile()?.let { f -> anime.readerFromFile(f)?.let(onRead) } }
                    }, Modifier.testTag("manga-open-file"))
                }
                if (list != null) readableNote(book, list, link.mangaLinks)?.let {
                    Label(it, Type.Body, Palette.Secondary, maxLines = 2, modifier = Modifier.testTag(if (list.isEmpty()) "manga-none" else "manga-partial"))
                }
            }
        }
        SourceLinks(L("Manga sources", "Sources du manga").text(), link.mangaLinks, "manga-official-sources")
        if (!list.isNullOrEmpty()) {
            val volumes = remember(list) { list.mapNotNull { it.volume }.distinct() }
            var volume by remember(list) { mutableStateOf(list.firstOrNull { it.number == resume?.chapter }?.volume) }
            if (volumes.size > 1) {
                // Counted once per book, not on each card as it is composed.
                val sizes = remember(list) { list.groupingBy { it.volume }.eachCount() }
                CardRow(L("Volumes", "Tomes").text(), volumes, key = { it }, testTag = "row-volumes") { _, v ->
                    val inside = sizes[v] ?: 0
                    FocusCard(VOLUME_W, VOLUME_H, { volume = if (volume == v) null else v }, tag = "volume-$v") { focused ->
                        Art(book.cover, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color(0x33000000), 1f to Color(0xF2000000))))
                        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                            Label(L("Volume $v", "Tome $v").text(), Type.Meta.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                            Label(L("$inside chapters", "$inside chapitres").text(), Type.Body, if (focused || volume == v) Palette.OnBackground else Palette.Secondary, maxLines = 1)
                        }
                    }
                }
            }
            val shown = remember(list, volume) { if (volume != null) list.filter { it.volume == volume } else list.takeLast(MAX_ROW).asReversed() }
            // Where each chapter sits in the book, found once: indexOf on each press went through the whole book.
            val at = remember(list) { list.withIndex().associate { (i, c) -> c.id to i } }
            CardRow((volume?.let { L("Chapters of volume $it", "Chapitres du tome $it") } ?: L("Chapters", "Chapitres")).text(), shown, key = { it.id }, testTag = "row-chapters") { _, c ->
                ChapterCard(c, c.number == resume?.chapter) { onRead(anime.readerRequest(book, list, at[c.id] ?: list.indexOf(c), 0)) }
            }
        }
    }
}

/**
 * Says plainly what can be read here when it is not the whole book: a publisher keeps the rest, as with One Piece,
 * where only the latest chapters are free. Null when everything published is here.
 */
private fun readableNote(book: com.sokoul.pc.data.anime.AniTitle, list: List<MangaChapter>, official: List<com.sokoul.pc.data.anime.AniLink> = emptyList()): String? {
    if (list.isEmpty()) {
        // Where the publisher does let it be read, named on AniList (MANGA Plus, VIZ, Piccoma…): the legal way to go on.
        val sites = official.map { it.site }.distinct().take(OFFICIAL_SITES)
        if (sites.isNotEmpty()) {
            val names = sites.joinToString(", ")
            return L("No chapter can be read here: its publisher holds it. Read it on $names.", "Aucun chapitre lisible ici : son éditeur en garde les droits. À lire sur $names.").text()
        }
        // The TV cannot open one's own files: it does not offer them.
        return if (com.sokoul.pc.Platform.IS_TV) {
            L("No chapter can be read here: its publisher holds it.", "Aucun chapitre lisible ici : son éditeur en garde les droits.").text()
        } else {
            L(
                "No chapter can be read here: its publisher holds it. Your own files open too.",
                "Aucun chapitre lisible ici : son éditeur en garde les droits. Tes propres fichiers s'ouvrent aussi.",
            ).text()
        }
    }
    val first = list.first().number.orEmpty()
    val last = list.last().number.orEmpty()
    val range = if (first == last) first else "$first–$last"
    // A finished book gives its count; one still running (One Piece) has none, but then it misses its first chapters.
    val total = book.chapters
    val partial = if (total != null) list.size * 10 < total * 9 else (first.toDoubleOrNull() ?: 1.0) > 1.0
    if (!partial) return null
    return if (total != null) {
        L(
            "Only ${list.size} of $total chapters can be read here ($range): its publisher holds the rest.",
            "Seuls ${list.size} chapitres sur $total sont lisibles ici ($range) : son éditeur garde les autres.",
        ).text()
    } else if (first == last) {
        L(
            "Only chapter $first can be read here: its publisher holds the others.",
            "Seul le chapitre $first est lisible ici : son éditeur garde les autres.",
        ).text()
    } else {
        L(
            "Only chapters $range can be read here: its publisher holds the others.",
            "Seuls les chapitres $range sont lisibles ici : son éditeur garde les autres.",
        ).text()
    }
}
private val VOLUME_W = 210.dp
private val VOLUME_H = 300.dp
private const val MAX_ROW = 200

private fun mangaMeta(book: com.sokoul.pc.data.anime.AniTitle, readable: Int?): String = listOfNotNull(
    book.year?.toString(),
    book.score?.let { "$it %" },
    book.chapters?.let { L("$it chapters", "$it chapitres").text() },
    book.volumes?.let { L("$it volumes", "$it tomes").text() },
    readable?.takeIf { it > 0 }?.let { L("$it to read here", "$it à lire ici").text() },
).joinToString(" · ")

@Composable
private fun ChapterCard(c: MangaChapter, current: Boolean, onClick: () -> Unit) {
    FocusCard(CHAPTER_W, CHAPTER_H, { onClick() }, tag = "chapter-${c.number}") { focused ->
        Box(Modifier.fillMaxSize().background(if (focused) Palette.RowFill else if (current) Palette.ActiveFill else Palette.ButtonFill))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(L("Chapter ${c.number.orEmpty()}", "Chapitre ${c.number.orEmpty()}").text(), Type.Meta.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            Label(listOfNotNull(c.volume?.let { L("Volume $it", "Tome $it").text() }, c.language.uppercase(), "${c.pages} p.").joinToString(" · "), Type.Body, Palette.Secondary, maxLines = 1)
            c.title?.let { Label(it, Type.Body, Palette.BarText, maxLines = 1) }
        }
    }
}

private val CHAPTER_W = 320.dp
private val CHAPTER_H = 150.dp

private const val OFFICIAL_SITES = 4
