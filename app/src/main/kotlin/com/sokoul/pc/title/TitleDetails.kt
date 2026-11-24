package com.sokoul.pc.title

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.data.net.TmdbVideo
import com.sokoul.pc.data.L
import com.sokoul.pc.data.TrailerOption
import com.sokoul.pc.data.TrailerSource
import com.sokoul.pc.data.trailerVideos
import com.sokoul.pc.data.kind
import com.sokoul.pc.i18n.durationLabel
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.RowHeading
import com.sokoul.pc.ui.tvFocusable

/** The videos that are not trailers, as Disney+'s "Extras": making-of, behind the scenes, clips, bloopers. */
internal fun bonusVideos(info: TmdbInfo?): List<TmdbVideo> =
    info?.videos.orEmpty().filter { it.type in BONUS_ORDER }.distinctBy { it.key }
        .sortedWith(compareBy<TmdbVideo>({ BONUS_ORDER.indexOf(it.type) }, { !it.official }))
        .take(BONUS_MAX)

private val BONUS_ORDER = listOf("Behind the Scenes", "Featurette", "Clip", "Bloopers", "Opening Credits")
private const val BONUS_MAX = 12

private fun bonusKind(type: String): String = when (type) {
    "Behind the Scenes" -> L("Behind the scenes", "Coulisses")
    "Featurette" -> L("Featurette", "Making-of")
    "Clip" -> L("Clip", "Extrait")
    "Bloopers" -> L("Bloopers", "Bêtisier")
    "Opening Credits" -> L("Opening credits", "Générique")
    else -> L(type, type)
}.text()

/** The bonus row: each video in 16:9, its kind above its name, played like a trailer. */
@Composable
internal fun BonusRow(info: TmdbInfo?, onPlay: (String) -> Unit) {
    val videos = remember(info) { bonusVideos(info) }
    if (videos.isEmpty()) return
    CardRow(L("Bonus", "Bonus").text(), videos, key = { it.key }, testTag = "row-bonus") { i, v ->
        FocusCard(BONUS_W, BONUS_H, { onPlay(v.key) }, tag = "bonus-$i") {
            Art("https://i.ytimg.com/vi/${v.key}/hqdefault.jpg", Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD9000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(bonusKind(v.type).uppercase(), Type.Body, Palette.Secondary, maxLines = 1)
                Label(v.name, Type.Meta, Palette.OnBackground, maxLines = 2)
            }
        }
    }
}

/**
 * The trailers row (the user, 2026-11-24: every source found, in French where there is a French one): the other
 * sources' trailers, sharpest first, then TMDB's YouTube ones. No source is named: each card says its quality and its
 * language. A direct link plays as it is; a YouTube key goes through the usual resolver.
 */
@Composable
internal fun TrailersRow(
    deps: com.sokoul.pc.AppDeps,
    kind: String,
    info: TmdbInfo?,
    backdrop: String?,
    onPlayKey: (String) -> Unit,
    onPlayStream: (com.sokoul.pc.data.TrailerStream) -> Unit,
) {
    // Null while the other sources are asked: the row waits for them (a few seconds at most) rather than showing
    // YouTube's cards and then adding the sharper ones in front, off screen to the left, under the viewer's focus
    // (Oppenheimer on the Google TV, 2026-11-24). Past the wait, YouTube's alone; the next visit finds the rest cached.
    val found by produceState<List<TrailerOption>?>(null, kind, info?.imdbId, info?.title) {
        value = null
        val i = info ?: return@produceState
        value = deps.trailerSources?.let { s -> kotlinx.coroutines.withTimeoutOrNull(OTHER_SOURCES_WAIT_MS) { runCatching { s.options(kind, i) }.getOrNull() } }.orEmpty()
    }
    val ready = found ?: return
    val options = remember(ready, info) { (ready + youtubeOptions(info)).distinctBy { it.url ?: it.youtubeKey }.take(TRAILERS_MAX) }
    if (options.isEmpty()) return
    CardRow(L("Trailers", "Bandes-annonces").text(), options, key = { it.url ?: it.youtubeKey ?: it.title }, testTag = "row-trailers") { i, o ->
        FocusCard(BONUS_W, BONUS_H, { o.youtubeKey?.let(onPlayKey) ?: o.stream()?.let(onPlayStream) }, tag = "trailer-$i") {
            Art(o.image ?: backdrop, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD9000000))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                trailerCaption(o)?.let { Label(it, Type.Body, Palette.Secondary, maxLines = 1) }
                Label(o.title.ifBlank { L("Trailer", "Bande-annonce").text() }, Type.Meta, Palette.OnBackground, maxLines = 2)
            }
        }
    }
}

/** TMDB's YouTube trailers as row cards. Their announced size can be wrong, so no quality is shown for them. */
internal fun youtubeOptions(info: TmdbInfo?): List<TrailerOption> {
    val i = info ?: return emptyList()
    val original = i.originalLanguage?.take(2)?.lowercase()
    return i.trailerVideos().map { v ->
        val language = when {
            v.language?.lowercase() == "fr" && original != "fr" -> "VF"
            v.language != null -> "VO"
            else -> null
        }
        TrailerOption(v.name, TrailerSource.YOUTUBE, language, youtubeKey = v.key, image = "https://i.ytimg.com/vi/${v.key}/hqdefault.jpg")
    }
}

/** "1080P · VF": the quality when it was measured, then the language. */
internal fun trailerCaption(o: TrailerOption): String? {
    val s = o.sharpness
    val quality = when {
        s >= 2160 -> "4K"
        s >= 1440 -> "1440p"
        s > 0 -> "${s}p"
        else -> null
    }
    return listOfNotNull(quality, o.language).joinToString(" · ").takeIf { it.isNotEmpty() }
}

private const val TRAILERS_MAX = 12
private const val OTHER_SOURCES_WAIT_MS = 8_000L

private val BONUS_W = 448.dp
private val BONUS_H = 252.dp

/**
 * The Details section, after the Disney+ tab of the same name and laid out as Apple TV's "Information": the synopsis on
 * the left, then two columns of facts — what it is (length, release, genres, rating, languages, studios) and who made it
 * (direction, writing, production, music, cast). OK opens the full synopsis.
 */
@Composable
internal fun DetailsBlock(item: TmdbItem, info: TmdbInfo?, onOpen: () -> Unit) {
    val series = item.kind() == "tv"
    val facts = listOfNotNull(
        (if (series) info?.seasons?.takeIf { it > 0 }?.let { L("Seasons", "Saisons") to "$it" } else info?.runtimeMin?.takeIf { it > 0 }?.let { L("Length", "Durée") to durationLabel(it) }),
        (info?.releaseDate ?: info?.year?.toString())?.let { L(if (series) "First aired" else "Released", if (series) "Première diffusion" else "Sortie") to releaseLabel(it) },
        info?.genres?.takeIf { it.isNotEmpty() }?.let { L("Genres", "Genres") to it.joinToString(", ") },
        info?.certification?.takeIf { it.isNotBlank() }?.let { L("Rating", "Classification") to it },
        info?.originalLanguage?.let { L("Original language", "Langue originale") to com.sokoul.pc.playback.ui.languageName(it) },
        info?.studios?.takeIf { it.isNotEmpty() }?.let { L("Studios", "Studios") to it.take(3).joinToString(", ") },
    )
    val people = listOfNotNull(
        (info?.directors?.map { it.name }?.takeIf { it.isNotEmpty() } ?: info?.director?.let(::listOf))?.let { L(if (series) "Created by" else "Directed by", if (series) "Création" else "Réalisation") to it.joinToString(", ") },
        info?.writers?.takeIf { it.isNotEmpty() }?.let { L("Written by", "Scénario") to it.joinToString(", ") },
        info?.producers?.takeIf { it.isNotEmpty() }?.let { L("Produced by", "Production") to it.joinToString(", ") },
        info?.composers?.takeIf { it.isNotEmpty() }?.let { L("Music", "Musique") to it.joinToString(", ") },
        info?.cast?.takeIf { it.isNotEmpty() }?.let { L("Starring", "Avec") to it.take(5).joinToString(", ") { p -> p.name } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(CardMetrics.RowVerticalPadding)) {
        RowHeading(L("Details", "Détails").text(), false)
        var focused by remember { mutableStateOf(false) }
        val fill by animateColorAsState(if (focused) Color(0x33FFFFFF) else Color(0x14FFFFFF))
        Row(
            Modifier.padding(horizontal = Metrics.PageMargin).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(fill)
                .tvFocusable(onFocusChanged = { focused = it }, onClick = onOpen).padding(horizontal = 40.dp, vertical = 36.dp)
                .testTag("title-details"),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            Column(Modifier.width(SYNOPSIS_W), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Label(item.title, Type.Button, maxLines = 2)
                info?.tagline?.takeIf { it.isNotBlank() }?.let { Label(it, Type.Body, Palette.Secondary, maxLines = 2) }
                Label(item.overview?.takeIf { it.isNotBlank() } ?: info?.overview.orEmpty(), Type.Body, maxLines = 9)
            }
            FactColumn(facts, Modifier.weight(1f))
            FactColumn(people, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FactColumn(rows: List<Pair<L, String>>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        rows.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(label.text(), Type.Body, Palette.Secondary, maxLines = 1)
                Label(value, Type.Body, Palette.OnBackground, maxLines = 3)
            }
        }
        if (rows.isEmpty()) Spacer(Modifier.height(1.dp))
    }
}

/** "2021-09-15" as "15 September 2021" in the interface language; a bare year stays a year. */
internal fun releaseLabel(date: String): String = runCatching {
    val d = java.time.LocalDate.parse(date)
    val locale = java.util.Locale.getDefault()
    d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
}.getOrDefault(date)

private val SYNOPSIS_W = 760.dp
