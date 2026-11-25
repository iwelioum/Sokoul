package com.sokoul.pc.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.iptv.discovery.ViewerStyle
import com.sokoul.iptv.discovery.TitleTime
import com.sokoul.iptv.discovery.YearSummary
import com.sokoul.pc.anime.Choices
import com.sokoul.pc.data.DiscoverData
import com.sokoul.pc.data.L
import com.sokoul.pc.theme.CardMetrics
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.CardRow
import com.sokoul.pc.ui.FocusCard
import com.sokoul.pc.ui.Label
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** The year in review: figures, viewing style, most watched titles, genres and the year's chapters. */
@Composable
internal fun ReviewPage(data: DiscoverData, onOpenTitle: (List<TmdbItem>, Int, CardOrigin?) -> Unit) {
    val now = LocalDate.now().year
    var year by remember { mutableIntStateOf(now) }
    val state by produceState<Pair<Int, YearSummary?>?>(null, year) {
        value = year to runCatching { data.review(year) }.getOrNull()
    }
    val summary = state?.takeIf { it.first == year }?.second
    val loading = state?.first != year
    LazyColumn(Modifier.fillMaxSize().testTag("review"), contentPadding = PaddingValues(top = 110.dp, bottom = 80.dp)) {
        item {
            Column(Modifier.padding(horizontal = Metrics.PageMargin), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label(L("Your year in review", "Votre année en revue").text(), Type.Meta.copy(fontWeight = FontWeight.SemiBold), Palette.Secondary)
                Label("$year", Type.RowTitle.copy(fontSize = Type.RowTitle.fontSize * 2f))
                Choices(listOf(now to L("This year", "Cette année"), now - 1 to L("Last year", "L'an dernier")), year, "review-year") { year = it }
            }
        }
        when {
            loading -> item { Label(L("Counting…", "Calcul en cours…").text(), Type.Body, Palette.Secondary, Modifier.padding(Metrics.PageMargin)) }
            summary == null -> item {
                Label(L("Nothing watched this year yet.", "Rien de regardé cette année pour l'instant.").text(), Type.Body, Palette.Secondary, Modifier.padding(Metrics.PageMargin))
            }
            else -> {
                item { Figures(summary) }
                item { StyleCard(summary.style) }
                if (summary.topTitles.isNotEmpty()) item {
                    val items = summary.topTitles.map { it.asItem() }
                    CardRow(L("Your most watched", "Vos plus regardés").text(), summary.topTitles, key = { "${it.kind}-${it.id}" }, testTag = "review-top") { i, t ->
                        TitleTile(t, i + 1) { origin -> onOpenTitle(items, i, origin) }
                    }
                }
                if (summary.topGenres.isNotEmpty()) item {
                    Section(L("Your genres", "Vos genres").text()) {
                        summary.topGenres.forEach { g -> Bar(g.name, g.percent, "${hours(g.minutes)}") }
                    }
                }
                if (summary.chapters.isNotEmpty()) item {
                    Section(L("Your chapters", "Vos chapitres").text()) {
                        summary.chapters.forEach { c ->
                            val months = if (c.fromMonth == c.toMonth) month(c.fromMonth) else "${month(c.fromMonth)} – ${month(c.toMonth)}"
                            Label("$months · ${c.genre} (${c.percent} %) · ${c.title.title}", Type.Body, Palette.OnBackground, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Figures(s: YearSummary) {
    Row(Modifier.padding(horizontal = Metrics.PageMargin, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Figure(hours(s.totalMinutes), L("watched", "regardées"))
        Figure("${s.uniqueTitles}", L("titles", "titres"))
        Figure("${s.activeDays}", L("days in front of the screen", "jours devant l'écran"))
        Figure("${s.longestStreak}", L("days in a row, at best", "jours d'affilée, au mieux"))
        Figure("${s.peakHour} h", L("your favourite hour", "votre heure préférée"))
        Figure(month(s.busiestMonth), L("your busiest month", "votre mois le plus chargé"))
    }
}

@Composable
private fun Figure(value: String, label: L) {
    Column(Modifier.width(250.dp).clip(RoundedCornerShape(18.dp)).background(Palette.ButtonFill).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(value, Type.RowTitle.copy(fontSize = Type.RowTitle.fontSize * 1.3f, fontWeight = FontWeight.Bold), maxLines = 1)
        Label(label.text(), Type.Meta, Palette.BarText, maxLines = 2)
    }
}

private val STYLES = mapOf(
    ViewerStyle.Nocturnal to (L("Night viewer", "Spectateur de la nuit") to L("Most of your viewing happened after 10 pm.", "L'essentiel de vos séances a eu lieu après 22 h.")),
    ViewerStyle.Morning to (L("Morning viewer", "Spectateur du matin") to L("You often pressed play before 11 am.", "Vous avez souvent lancé un titre avant 11 h.")),
    ViewerStyle.Marathon to (L("Marathon runner", "Marathonien") to L("Four episodes or more in a day, more than once.", "Quatre épisodes ou plus dans la journée, et pas qu'une fois.")),
    ViewerStyle.Faithful to (L("Regular", "Habitué") to L("Ten days in a row or more with something on screen.", "Dix jours d'affilée ou plus avec quelque chose à l'écran.")),
    ViewerStyle.Curious to (L("Curious", "Curieux") to L("Many titles, and no genre took over.", "Beaucoup de titres, et aucun genre n'a pris le dessus.")),
    ViewerStyle.FilmBuff to (L("Film lover", "Amateur de films") to L("Films took most of your time.", "Les films ont pris l'essentiel de votre temps.")),
    ViewerStyle.SeriesBuff to (L("Series lover", "Amateur de séries") to L("Series took most of your time.", "Les séries ont pris l'essentiel de votre temps.")),
    ViewerStyle.Eclectic to (L("Eclectic", "Éclectique") to L("A bit of everything, at your own pace.", "Un peu de tout, à votre rythme.")),
)

@Composable
private fun StyleCard(style: ViewerStyle) {
    val (title, line) = STYLES.getValue(style)
    Box(
        Modifier.padding(horizontal = Metrics.PageMargin, vertical = 8.dp).fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp))
            .background(Palette.ButtonFill).testTag("review-style"),
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Label(L("Your viewing style", "Votre façon de regarder").text(), Type.Meta, Palette.BarText)
            Label(title.text(), Type.RowTitle.copy(fontSize = Type.RowTitle.fontSize * 1.4f, fontWeight = FontWeight.Bold), maxLines = 1)
            Label(line.text(), Type.Body, Palette.OnBackground, maxLines = 1)
        }
    }
}

@Composable
private fun TitleTile(t: TitleTime, rank: Int, onClick: (CardOrigin) -> Unit) {
    FocusCard(CardMetrics.EpisodeWidth, 200.dp, onClick) { _ ->
        Art(t.image, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color(0x22000000), 1f to Color(0xF2000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Label("#$rank · ${hours(t.minutes)}", Type.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Label(t.title, Type.Meta.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Metrics.PageMargin, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(title, Type.RowTitle, maxLines = 1)
        content()
    }
}

@Composable
private fun Bar(name: String, percent: Int, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Label(name, Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(260.dp), maxLines = 1)
        Box(Modifier.width(700.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.ButtonFill)) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(2, 100) / 100f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.OnBackground))
        }
        Label("$percent % · $detail", Type.Meta, Palette.BarText, maxLines = 1)
    }
}

private fun TitleTime.asItem() = TmdbItem(id, title, null, null, image, image, null, null, mediaType = kind)

private fun hours(minutes: Long): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h"

private fun month(m: Int): String = Month.of(m).getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }

