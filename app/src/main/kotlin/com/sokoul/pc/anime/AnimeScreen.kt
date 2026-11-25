package com.sokoul.pc.anime

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.AppDeps
import com.sokoul.pc.data.L
import com.sokoul.pc.data.anime.AniAiring
import com.sokoul.pc.data.anime.AniCategory
import com.sokoul.pc.data.anime.AniQuery
import com.sokoul.pc.data.anime.AniTitle
import com.sokoul.pc.data.anime.AnimeCatalog
import com.sokoul.pc.data.anime.AnimeCategories
import com.sokoul.pc.data.anime.AnimeLink
import com.sokoul.pc.data.anime.CategoryGroup
import com.sokoul.pc.data.anime.MediaKind
import com.sokoul.pc.data.anime.ReaderRequest
import com.sokoul.pc.playback.ui.clock
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.BackButton
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.tvFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val CARD_W = 210.dp
private val CARD_H = 300.dp
/** Long enough to run down the list of categories without asking AniList for each one passed on the way (2026-11-24). */
private const val SELECT_DELAY_MS = 600L
private const val CALENDAR_DAYS = 7L
private const val MIN_POPULARITY_ON_CALENDAR = 800
private const val FOCUS_SETTLE_MS = 32L

/** What opens over the page: everything to sort by, or the week's episodes. */
enum class AnimeHubView { Categories, Calendar }

/** What the page shows, for the tools. */
data class AnimeReport(val view: String, val categories: Int, val shownInGrid: Int, val pinned: List<String>, val detail: String?)

/** A cover. On the anime page, the same card as everywhere. */
@Composable
fun AniCard(t: AniTitle, modifier: Modifier = Modifier, tag: String? = null, onClick: (CardOrigin) -> Unit) {
    FocusCard(CARD_W, CARD_H, onClick, modifier, tag = tag) { focused ->
        Art(t.cover, Modifier.fillMaxSize())
        // The title always shows, in English or romaji: AniList's covers are mostly lettered in Japanese only, and a row of
        // them did not say which work each one was until it was focused (Google TV, 2026-11-24).
        Box(Modifier.fillMaxSize().background(CardFade))
        Label(t.title, Type.Body.copy(fontWeight = FontWeight.SemiBold), if (focused) Palette.OnBackground else Palette.BarText, Modifier.align(Alignment.BottomStart).padding(12.dp), maxLines = 2)
    }
}

private val CardFade = Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xE6000000))

/** A work: to its title page when it has one, on its book when asked for, else on a page of its own. */
fun openWork(anime: AnimeCatalog, t: AniTitle, scope: kotlinx.coroutines.CoroutineScope, onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit, onDetail: (AniTitle) -> Unit, origin: CardOrigin? = null) {
    // One page per work: a book with an anime opens the anime's page, where the book has its own part (chapters, and
    // the chapters of each episode). A book without one opens its own page.
    scope.launch {
        val item = anime.titleOf(t)
        if (item != null) {
            onOpenTitle(listOf(item), 0, origin)
        } else {
            onDetail(t)
        }
    }
}

/** Takes the keys when the page opens, then hands them to its first control as soon as it has one. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun takeFocusOnOpen(ready: Boolean = true): FocusRequester {
    val root = remember { FocusRequester() }
    val manager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(ready) {
        delay(FOCUS_SETTLE_MS)
        runCatching { root.requestFocus() }
        if (ready) {
            delay(FOCUS_SETTLE_MS)
            runCatching { manager.moveFocus(androidx.compose.ui.focus.FocusDirection.Enter) }
        }
    }
    return root
}

/** Everything to sort by, over the page: every category, combined, with what fans filter on. */
@Composable
fun AnimeCategoriesPage(deps: AppDeps, startKind: MediaKind, preset: String?, onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit, onRead: (ReaderRequest) -> Unit, onClose: () -> Unit) {
    val anime = deps.anime ?: return
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(startKind) }
    var query by remember { mutableStateOf("") }
    val pinned = remember { mutableStateListOf<AniCategory>() }
    // Null until the tags are known. The preset is pinned in the same step, so the grid asks once, for the category it was
    // opened on: it asked for everything first, then again with the category (2026-11-24).
    val known by produceState<List<AniCategory>?>(null) {
        val tags = try {
            anime.client.tags()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val all = AnimeCategories.all(tags)
        if (preset != null && pinned.isEmpty()) all.firstOrNull { it.name == preset }?.let { pinned += it }
        value = all
    }
    val categories = known.orEmpty()
    var looking by remember { mutableStateOf<AniCategory?>(null) }
    var sort by remember { mutableStateOf("POPULARITY_DESC") }
    var format by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var country by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<AniTitle?>(null) }
    val groups = remember(categories, query) { categories.filter { AnimeCategories.matches(it, query) }.groupBy { it.group } }
    var wanted by remember { mutableStateOf<AniCategory?>(null) }
    LaunchedEffect(wanted) {
        val c = wanted ?: return@LaunchedEffect
        delay(SELECT_DELAY_MS)
        looking = c
    }
    val active = (pinned.toList() + listOfNotNull(looking)).distinct()
    var count by remember { mutableStateOf(0) }
    SideEffect { deps.animeReport = AnimeReport(AnimeHubView.Categories.name, categories.size, count, pinned.map { it.name }, detail?.title) }
    val root = takeFocusOnOpen(categories.isNotEmpty())
    var moved by remember { mutableStateOf(false) }
    val toggle = { c: AniCategory -> if (c in pinned) pinned.remove(c) else pinned.add(c) }

    Box(Modifier.fillMaxSize().background(Palette.Background).testTag("anime-categories")) {
        Row(
            Modifier.fillMaxSize().padding(top = 112.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val char = e.utf16CodePoint.toChar()
                    if (e.key == Key.DirectionUp || e.key == Key.DirectionDown) moved = true
                    when {
                        detail != null -> false
                        e.key == Key.Escape && query.isNotEmpty() -> {
                            query = ""
                            true
                        }
                        e.key == Key.Escape -> {
                            onClose()
                            true
                        }
                        e.key == Key.Backspace && query.isNotEmpty() -> {
                            query = query.dropLast(1)
                            true
                        }
                        char.isLetterOrDigit() -> {
                            query += char
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(root).focusable(),
        ) {
            Column(Modifier.width(500.dp).fillMaxHeight().padding(start = Metrics.PageMargin, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(query.ifEmpty { L("Search ${categories.size} categories", "Chercher parmi ${categories.size} catégories").text() }, Type.Body.copy(fontWeight = FontWeight.SemiBold), if (query.isEmpty()) Palette.Secondary else Palette.OnBackground, Modifier.padding(start = 20.dp).testTag("anime-category-count"), maxLines = 1)
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    groups.forEach { (group, list) ->
                        item(key = "g-$group", contentType = "group") { Label(groupName(group).text(), Type.Body.copy(fontWeight = FontWeight.Bold), Palette.Secondary, Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp), maxLines = 1) }
                        list.forEach { c ->
                            item(key = c.name, contentType = "category") {
                                CategoryRow(c, c in pinned, c == looking, onFocus = { if (moved) wanted = c }) { toggle(c) }
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(active.joinToString(" + ") { AnimeCategories.label(it.name) }.ifEmpty { L("Everything", "Tout").text() }, Type.RowTitle, modifier = Modifier.padding(start = 8.dp).testTag("anime-category-name"), maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Choices(listOf(MediaKind.ANIME to L("Anime", "Animés"), MediaKind.MANGA to L("Manga", "Mangas")), kind, "kind") { kind = it }
                    Choices(listOf("POPULARITY_DESC" to L("Popular", "Populaires"), "TRENDING_DESC" to L("Trending", "Tendances"), "SCORE_DESC" to L("Best rated", "Mieux notés"), "START_DATE_DESC" to L("Newest", "Récents")), sort, "sort") { sort = it }
                    Choices(listOf(null to L("Everywhere", "Partout"), "JP" to L("Japan", "Japon"), "KR" to L("Korea", "Corée"), "CN" to L("China", "Chine")), country, "country") { country = it }
                }
                val formats = if (kind == MediaKind.ANIME) {
                    listOf(null to L("All formats", "Tous formats"), "TV" to L("Series", "Séries"), "MOVIE" to L("Films", "Films"), "OVA" to L("OVA", "OAV"), "ONA" to L("Web", "Web"))
                } else {
                    listOf(null to L("All formats", "Tous formats"), "MANGA" to L("Manga", "Manga"), "NOVEL" to L("Light novels", "Light novels"), "ONE_SHOT" to L("One shots", "One shots"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Choices(formats, format, "format") { format = it }
                    Choices(listOf(null to L("Any status", "Tous états"), "RELEASING" to L("Ongoing", "En cours"), "FINISHED" to L("Finished", "Terminés")), status, "status") { status = it }
                }
                val q = AniQuery(
                    kind, sort = sort, format = format, status = status, country = country,
                    genres = active.filter { it.isGenre }.map { it.name }, tags = active.filter { !it.isGenre }.map { it.name }, perPage = 30,
                )
                if (known != null) {
                    ResultGrid(anime, q, onCount = { count = it }) { t, origin ->
                        openWork(anime, t, scope, onOpenTitle, { detail = it }, origin)
                    }
                } else {
                    Label(L("Loading…", "Chargement…").text(), Type.Meta, Palette.Secondary, Modifier.padding(start = 8.dp, top = 16.dp).testTag("anime-grid-loading"), maxLines = 1)
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("anime-categories-back"))
        detail?.let { t -> WorkPage(deps, t, onOpenTitle, onRead) { detail = null } }
    }
}

private fun groupName(g: CategoryGroup) = when (g) {
    CategoryGroup.Genres -> L("Genres", "Genres")
    CategoryGroup.Audience -> L("Audience", "Public")
    CategoryGroup.Characters -> L("Characters", "Personnages")
    CategoryGroup.Worlds -> L("Worlds and places", "Univers et lieux")
    CategoryGroup.Action -> L("Action", "Action")
    CategoryGroup.Fantasy -> L("Fantasy", "Fantastique")
    CategoryGroup.SciFi -> L("Science fiction", "Science-fiction")
    CategoryGroup.Drama -> L("Drama", "Drame")
    CategoryGroup.Comedy -> L("Comedy", "Comédie")
    CategoryGroup.Romance -> L("Romance", "Romance")
    CategoryGroup.SliceOfLife -> L("Slice of life", "Tranche de vie")
    CategoryGroup.Sports -> L("Sports and games", "Sports et jeux")
    CategoryGroup.Arts -> L("Arts and music", "Arts et musique")
    CategoryGroup.Society -> L("Society and more", "Société et autres")
}

@Composable
internal fun <T> Choices(options: List<Pair<T, L>>, current: T, tag: String, onPick: (T) -> Unit) {
    Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEachIndexed { i, (value, label) ->
            com.sokoul.pc.ui.PanelChip(label.text(), Modifier.testTag("$tag-$i"), selected = value == current) { onPick(value) }
        }
    }
}

@Composable
private fun CategoryRow(c: AniCategory, pinned: Boolean, looking: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    var has by remember { mutableStateOf(false) }
    // Read when drawing: read in composition, the fading fill recomposed the row at every frame of its fade.
    val fill = animateColorAsState(if (has) Palette.FocusFill else if (pinned) Palette.RowFill else if (looking) Palette.ActiveFill else Color.Transparent)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).drawBehind { drawRect(fill.value) }
            .tvFocusable(
                onFocusChanged = {
                    has = it
                    if (it) onFocus()
                },
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Label(AnimeCategories.label(c.name), Type.Meta, if (has) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
        if (pinned) Label("✓", Type.Meta, if (has) Palette.OnFocus else Palette.OnBackground, maxLines = 1)
    }
}

/** The titles found for one query, each once, in the order AniList gave them. */
private class GridAnswer(val query: AniQuery?, val titles: List<AniTitle>)

/**
 * Every title for the categories chosen, page after page as the grid nears its end. The answer to the last query stays on
 * screen, dimmed, until the first page of the new one arrives: the grid emptied at each step of a focus move and the page
 * looked broken. A refused first page says so, with a way to ask again, rather than leaving a blank page (2026-11-24).
 */
@Composable
private fun ResultGrid(anime: AnimeCatalog, q: AniQuery, onCount: (Int) -> Unit, onOpen: (AniTitle, CardOrigin) -> Unit) {
    var answer by remember { mutableStateOf(GridAnswer(null, emptyList())) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val count by rememberUpdatedState(onCount)
    val state = rememberLazyGridState()
    LaunchedEffect(q, retry) {
        failed = false
        loading = true
        // Ids already in the grid: a page can repeat a title of the page before, or hold the same one twice, and two cards
        // with one key crash the grid. A set, not a search of the list for each title found.
        val seen = HashSet<Int>()
        val titles = ArrayList<AniTitle>()
        var page = 1
        try {
            while (true) {
                val found = try {
                    anime.client.titlesOrNull(q.copy(page = page))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                // An answer that comes back after the query changed is not added to the new one's grid.
                currentCoroutineContext().ensureActive()
                if (found == null) {
                    if (page == 1) failed = true
                    break
                }
                found.filterTo(titles) { seen.add(it.id) }
                val first = page == 1
                answer = GridAnswer(q, titles.toList())
                count(titles.size)
                if (first) state.scrollToItem(0)
                if (found.size < GRID_PAGE_MIN) break
                loading = false
                snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.first { it >= titles.size - GRID_AHEAD }
                loading = true
                page++
            }
        } finally {
            loading = false
        }
    }
    val current = answer.query == q
    when {
        failed && !current -> GridMessage(
            L("AniList did not answer. It may be busy for a moment.", "AniList n'a pas répondu. Il est peut-être occupé un instant.").text(),
            L("Try again", "Réessayer").text(),
        ) { retry++ }
        current && answer.titles.isEmpty() && !loading -> GridMessage(L("Nothing matches all of these.", "Rien ne correspond à tout cela.").text(), null) {}
        answer.titles.isEmpty() -> Label(L("Loading…", "Chargement…").text(), Type.Meta, Palette.Secondary, Modifier.padding(start = 8.dp, top = 16.dp).testTag("anime-grid-loading"), maxLines = 1)
        else -> LazyVerticalGrid(
            GridCells.Adaptive(CARD_W + CardMetrics.Gap),
            // The previous answer, dimmed while the new one is on its way.
            Modifier.fillMaxSize().graphicsLayer { alpha = if (current) 1f else STALE_ALPHA }.testTag("anime-grid"),
            state = state,
            contentPadding = PaddingValues(start = 8.dp, end = 40.dp, top = 16.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(CardMetrics.Gap),
        ) {
            itemsIndexed(answer.titles, key = { _, t -> t.id }, contentType = { _, _ -> "anime" }) { i, t -> AniCard(t, tag = "anime-grid-$i") { origin -> onOpen(t, origin) } }
        }
    }
}

@Composable
private fun GridMessage(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.padding(start = 8.dp, top = 24.dp).testTag("anime-grid-message"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Label(text, Type.Meta, Palette.Secondary, maxLines = 2)
        if (action != null) com.sokoul.pc.ui.PillButton(action, onAction, Modifier.testTag("anime-grid-retry"))
    }
}

private const val STALE_ALPHA = 0.45f
private const val GRID_AHEAD = 10
private const val GRID_PAGE_MIN = 20

/** The next seven days at local time. Your own titles can stand alone. */
@Composable
fun AnimeCalendarPage(deps: AppDeps, onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit, onRead: (ReaderRequest) -> Unit, onClose: () -> Unit) {
    val anime = deps.anime ?: return
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { LocalDate.now(zone) }
    // Each day once it is known; a day still on its way has no entry and keeps its place with a loading row.
    var week by remember { mutableStateOf<Map<LocalDate, List<AniAiring>>>(emptyMap()) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(anime, today) {
        // Day by day, each with its own pages: asked for the whole week at once, the pages stopped at 300 airings and a
        // page AniList refused (its per-minute limit) took whole days away with it (2026-11-10). The seven days are asked
        // together and each shows as it arrives: one after the other, with their retries, the week took 14 s at best and
        // two minutes at worst (2026-11-24). AniList's queue spaces the calls; the gate keeps only a few in flight.
        val gate = Semaphore(CALENDAR_PARALLEL)
        coroutineScope {
            for (d in 0 until CALENDAR_DAYS) {
                val day = today.plusDays(d)
                launch {
                    val found = dayAirings(anime, day, zone, gate)
                    week = week + (day to found)
                }
            }
        }
        done = true
    }
    var onlyMine by remember { mutableStateOf(false) }
    var onlyToday by remember { mutableStateOf(false) }
    val mine by remember(deps.myList) { derivedStateOf { deps.myList.items.map { AnimeCategories.plain(it.title) }.toSet() } }
    val shown = remember(week, onlyMine, mine) {
        if (!onlyMine) week
        else week.mapValues { (_, list) -> list.filter { a -> listOfNotNull(a.title.english, a.title.romaji).any { AnimeCategories.plain(it) in mine } } }
    }
    // A day still loading stays in the list, in its place: days arriving out of order no longer push the row under focus.
    val days = remember(shown, onlyToday, today) {
        (0 until if (onlyToday) 1L else CALENDAR_DAYS).map { today.plusDays(it) }.filter { shown[it]?.isNotEmpty() ?: true }
    }
    val total = remember(shown) { shown.values.sumOf { it.size } }
    var detail by remember { mutableStateOf<AniTitle?>(null) }
    // Read only by the hero: the page itself no longer recomposes, nor sorts the week again, at each move of the focus.
    val focused = remember { mutableStateOf<AniAiring?>(null) }
    SideEffect { deps.animeReport = AnimeReport(AnimeHubView.Calendar.name, 0, total, emptyList(), detail?.title) }
    val root = takeFocusOnOpen(week.isNotEmpty() || done)
    Box(Modifier.fillMaxSize().background(Palette.Background).testTag("anime-calendar")) {
        CalendarBanner(focused)
        Box(Modifier.fillMaxWidth().height(CALENDAR_HERO).background(Brush.verticalGradient(0f to Color(0x99000000), 1f to Palette.Background)))
        Column(
            Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && detail == null) {
                    onClose()
                    true
                } else {
                    false
                }
            }.focusRequester(root).focusable(),
        ) {
            Column(Modifier.fillMaxWidth().height(CALENDAR_HERO).padding(start = Metrics.PageMargin, end = Metrics.PageMargin, top = 110.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarHeading(focused)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Choices(listOf(false to L("This week", "Cette semaine"), true to L("Today", "Aujourd'hui")), onlyToday, "calendar-when") { onlyToday = it }
                    Choices(listOf(false to L("All", "Tout"), true to L("My List", "Ma liste")), onlyMine, "calendar-filter") { onlyMine = it }
                }
            }
            if (done && days.isEmpty()) {
                Label(
                    if (onlyMine) L("Nothing from your list airs on these days.", "Rien de ta liste n'est diffusé ces jours-là.").text()
                    else L("AniList did not give this week's episodes. Try again in a moment.", "AniList n'a pas donné les épisodes de la semaine. Réessaie dans un instant.").text(),
                    Type.Meta, Palette.Secondary, Modifier.padding(start = Metrics.PageMargin, top = 24.dp).testTag("calendar-empty"), maxLines = 2,
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 60.dp)) {
                items(days, key = { it.toString() }, contentType = { if (shown[it] == null) "pending" else "day" }) { day ->
                    val name = if (day == today) L("Today", "Aujourd'hui").text() else day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }
                    val list = shown[day]
                    if (list == null) {
                        com.sokoul.pc.ui.SkeletonRow(landscape = true, cardWidth = CardMetrics.EpisodeWidth, cardHeight = CALENDAR_CARD_H)
                    } else {
                        CardRow("$name ${day.dayOfMonth}", list, key = { "${it.title.id}-${it.episode}" }, testTag = "calendar-$day") { _, x ->
                            CalendarCard(x, onFocus = { focused.value = x }) { openWork(anime, x.title, scope, onOpenTitle, { detail = it }) }
                        }
                    }
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("anime-calendar-back"))
        detail?.let { t -> WorkPage(deps, t, onOpenTitle, onRead) { detail = null } }
    }
}

/** The focused work's banner behind the page's top. */
@Composable
private fun CalendarBanner(focused: State<AniAiring?>) {
    focused.value?.title?.banner?.let { Art(it, Modifier.fillMaxWidth().height(CALENDAR_HERO), placeholder = Palette.Background) }
}

/** The focused work's name, its time and episode, and what it is about. */
@Composable
private fun CalendarHeading(focused: State<AniAiring?>) {
    val a = focused.value
    Label(a?.title?.title ?: L("This week", "Cette semaine").text(), Type.RowTitle.copy(fontSize = Type.RowTitle.fontSize * 1.4f), maxLines = 1, modifier = Modifier.testTag("calendar-title"))
    a?.let { Label("${clock(it.at)} · ${L("Episode ${it.episode}", "Épisode ${it.episode}").text()}", Type.Meta.copy(fontWeight = FontWeight.SemiBold), maxLines = 1) }
    a?.title?.description?.let { Label(it, Type.Body, Palette.BarText, maxLines = 2, modifier = Modifier.width(1100.dp)) }
}

/** One day's popular airings, each once: two pages can repeat an airing, and two cards with one key crash the row. */
private suspend fun dayAirings(anime: AnimeCatalog, day: LocalDate, zone: ZoneId, gate: Semaphore): List<AniAiring> {
    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val all = mutableListOf<AniAiring>()
    for (page in 1..CALENDAR_PAGES_PER_DAY) {
        var found = scheduleOf(anime, from, to, page, gate)
        // An empty first page is more often a refusal than a day without anime: asked again after a pause, the gate free
        // for the other days meanwhile.
        var tries = 0
        while (found.isEmpty() && page == 1 && tries < CALENDAR_RETRIES) {
            tries++
            delay(CALENDAR_RETRY_MS)
            found = scheduleOf(anime, from, to, page, gate)
        }
        all += found
        if (found.size < CALENDAR_PAGE_SIZE) break
    }
    return all.filter { (it.title.popularity ?: 0) >= MIN_POPULARITY_ON_CALENDAR }.distinctBy { it.title.id to it.episode }
}

private suspend fun scheduleOf(anime: AnimeCatalog, from: Long, to: Long, page: Int, gate: Semaphore): List<AniAiring> = try {
    gate.withPermit { anime.client.schedule(from, to, page) }
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    emptyList()
}

private const val CALENDAR_PARALLEL = 3

private const val CALENDAR_PAGES_PER_DAY = 3
/** AniList's page size in [com.sokoul.pc.data.anime.AniList.SCHEDULE]: a shorter page is the last one of the day. */
private const val CALENDAR_PAGE_SIZE = 50
private const val CALENDAR_RETRIES = 2
private const val CALENDAR_RETRY_MS = 8_000L
private val CALENDAR_HERO = 400.dp

@Composable
private fun CalendarCard(a: AniAiring, onFocus: () -> Unit, onClick: () -> Unit) {
    val past = a.at < System.currentTimeMillis()
    FocusCard(CardMetrics.EpisodeWidth, CALENDAR_CARD_H, { onClick() }, onFocus = { if (it) onFocus() }) { focused ->
        Art(a.title.banner ?: a.title.cover, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color(0x22000000), 1f to Color(0xF2000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Label("${clock(a.at)} · ${L("Ep. ${a.episode}", "Ép. ${a.episode}").text()}", Type.Body.copy(fontWeight = FontWeight.Bold), if (past && !focused) Palette.Secondary else Palette.OnBackground, maxLines = 1)
            Label(a.title.title, Type.Meta.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

private val CALENDAR_CARD_H = 200.dp

/** A work the catalog does not have, or a book with no anime: its own page, in the same language as the others. */
@Composable
fun WorkPage(deps: AppDeps, t: AniTitle, onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit, onRead: (ReaderRequest) -> Unit, onClose: () -> Unit) {
    val anime = deps.anime ?: return
    val scope = rememberCoroutineScope()
    val link by produceState<AnimeLink?>(null, t.id) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { anime.linkOf(t) }.getOrNull() } }
    val root = takeFocusOnOpen(link != null)
    Box(
        Modifier.fillMaxSize().background(Palette.Background).testTag("anime-detail").onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) {
                onClose()
                true
            } else {
                false
            }
        }.focusRequester(root).focusable(),
    ) {
        t.banner?.let { Art(it, Modifier.fillMaxWidth().height(620.dp), placeholder = Palette.Background) }
        Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(0f to Color(0x99000000), 1f to Palette.Background)))
        Column(Modifier.fillMaxSize().padding(top = 130.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(30.dp)) {
            val l = link
            if (l?.manga != null) {
                MangaSection(deps, null, l, onRead)
                if (l.adaptations.isNotEmpty()) {
                    CardRow(L("Adaptations", "Adaptations").text(), l.adaptations, key = { it.id }) { _, r ->
                        AniCard(r) { origin -> openWork(anime, r, scope, onOpenTitle, {}, origin) }
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = Metrics.PageMargin), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    Art(t.cover, Modifier.size(300.dp, 430.dp).clip(RoundedCornerShape(CardMetrics.Corner)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label(t.title, Type.RowTitle.copy(fontSize = Type.RowTitle.fontSize * 1.4f), maxLines = 2, modifier = Modifier.testTag("anime-detail-title"))
                        Label((t.genres + t.tags.take(6)).joinToString(" · ") { AnimeCategories.label(it) }, Type.Body, Palette.BarText, maxLines = 2)
                        t.description?.let { Label(it, Type.Body, maxLines = 7) }
                    }
                }
            }
        }
        BackButton(onClick = onClose, modifier = Modifier.padding(start = 40.dp, top = 44.dp).testTag("anime-detail-back"))
    }
}
