package com.sokoul.pc.title

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.ui.ImageLoader
import com.sokoul.iptv.data.net.TmdbBrand
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbPerson
import com.sokoul.pc.ui.CardOrigin
import com.sokoul.pc.ui.FocusCard
import kotlinx.coroutines.delay
import com.sokoul.pc.data.L
import com.sokoul.pc.data.OmdbFacts
import com.sokoul.pc.data.providers.WatchOffer
import com.sokoul.pc.i18n.durationLabel
import com.sokoul.pc.playback.ui.clock
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Art
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.OutlinedBadge
import com.sokoul.pc.ui.tvFocusable
import java.util.Locale

private val LETTER = 4.sp

/** Below this mean luminance (0 black, 1 white), an opaque logo does not show on the page. */
private const val DARK_LOGO = 0.22f

/** The mean luminance of a grid of points across [image], cheap enough to run once per logo. */
private fun meanLuminance(image: androidx.compose.ui.graphics.ImageBitmap): Float {
    val w = image.width
    val h = image.height
    if (w < 2 || h < 2) return 1f
    val px = IntArray(1)
    var sum = 0f
    var n = 0
    for (i in 1..8) for (j in 1..4) {
        image.readPixels(px, (w * i / 9).coerceIn(0, w - 1), (h * j / 5).coerceIn(0, h - 1), 1, 1)
        val c = px[0]
        sum += (0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
        n++
    }
    return sum / n
}

/** "A24 PRESENTS", with the studio's own logo in white when the catalog has it. */
@Composable
fun PresentedBy(brands: List<TmdbBrand>) {
    val first = brands.firstOrNull() ?: return
    Row(Modifier.testTag("title-presented"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val withLogo = brands.filter { it.logo != null }.take(2)
        withLogo.forEach { b ->
            val image by produceState(b.logo?.let(ImageLoader::cached), b.logo) { value = b.logo?.let { ImageLoader.load(it) } }
            // Painted white, a logo on an opaque background would be a white rectangle: it is shown as it is.
            image?.let {
                val clear = remember(it) { IntArray(1).also { px -> it.readPixels(px, 0, 0, 1, 1) }[0] ushr 24 < OPAQUE }
                // An opaque dark logo (Syncopy's black box) is a black patch on the dark page: left out.
                val dark = remember(it) { !clear && meanLuminance(it) < DARK_LOGO }
                if (!dark) Image(it, b.name, Modifier.height(36.dp).width(76.dp).clip(RoundedCornerShape(6.dp)), Alignment.CenterStart, ContentScale.Fit, colorFilter = if (clear) ColorFilter.tint(Palette.OnBackground) else null)
            }
        }
        Label(
            // A wordmark already says the name: only the verb is left to write.
            ((if (withLogo.isEmpty()) first.name + "  " else "") + L("PRESENTS", "PRÉSENTE").text()).uppercase(),
            Type.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = LETTER),
            Palette.BarText,
            maxLines = 1,
        )
    }
}

/** The one-line promise of the film, under the logo. */
@Composable
fun Tagline(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(44.dp, 2.dp).background(Palette.BarText))
        Label(text.uppercase(), Type.Body.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp), Palette.BarText, maxLines = 1)
    }
}

/** Five stars, filled to the rating out of ten, then runtime, year and when it ends if started now. */
@Composable
fun Facts(info: TmdbInfo?, remainingMin: Int?) {
    val rating = info?.rating?.takeIf { it > 0 }
    val minutes = remainingMin ?: info?.runtimeMin?.takeIf { it > 0 }
    // Wraps rather than cuts: on a phone held upright the languages, last, were pushed out and only their globe showed
    // (Galaxy Z Fold2, 2026-11-24).
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.testTag("title-facts"),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (rating != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { i ->
                    val value = rating / 2 - i
                    val icon: ImageVector = when {
                        value >= 0.75 -> Icons.Filled.Star
                        value >= 0.25 -> Icons.Filled.StarHalf
                        else -> Icons.Filled.StarOutline
                    }
                    var on by remember(info?.id) { mutableStateOf(false) }
                    LaunchedEffect(info?.id) {
                        delay(STAR_START_MS + i * STAR_GAP_MS)
                        on = true
                    }
                    val pop by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    Icon(icon, null, Modifier.size(30.dp).graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) }, tint = if (value >= 0.25) Palette.OnBackground else Palette.Secondary)
                }
            }
        }
        val year = info?.year ?: info?.releaseDate?.take(4)?.toIntOrNull()
        val parts = listOfNotNull(
            year?.toString(),
            minutes?.let { durationLabel(it) },
            info?.seasons?.takeIf { it > 0 && info.runtimeMin == null }?.let { if (it == 1) L("1 season", "1 saison").text() else L("$it seasons", "$it saisons").text() },
        )
        Label(parts.joinToString("   ·   "), Type.Meta, Palette.OnBackground, maxLines = 1)
        if (minutes != null && minutes > 0 && (info?.seasons == null)) {
            Label(L("Ends at ", "Finit à ").text() + clock(System.currentTimeMillis() + minutes * 60_000L), Type.Meta, Palette.Secondary, maxLines = 1)
        }
        languages(info)?.let { langs ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, null, Modifier.size(24.dp), tint = Palette.Secondary)
                Label(langs, Type.Meta, Palette.Secondary, maxLines = 1, modifier = Modifier.testTag("title-languages"))
            }
        }
    }
}

/** The kind of work, its genres and its age rating, as small outlined pills. */
@Composable
fun GenrePills(kind: String, info: TmdbInfo?) {
    // Wraps rather than squeezes: on a phone held upright the last genre shrank to an empty pill and the age rating
    // stood one digit per line (render, 2026-11-26).
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(Palette.PillFill).padding(horizontal = 22.dp, vertical = 9.dp)) {
            Label(com.sokoul.pc.home.typeLabel(kind), Type.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        }
        fitGenres(info?.genres.orEmpty()).forEach { g ->
            Box(Modifier.clip(RoundedCornerShape(50)).border(1.dp, Palette.Hairline, RoundedCornerShape(50)).padding(horizontal = 22.dp, vertical = 9.dp)) {
                Label(g, Type.Body, Palette.OnBackground, maxLines = 1)
            }
        }
        info?.certification?.takeIf { it.isNotBlank() }?.let { Box(Modifier.padding(start = 6.dp).align(Alignment.CenterVertically)) { OutlinedBadge(it) } }
    }
}

/** Critics' scores and the prizes, as chips. Absent when the services know nothing. */
@Composable
fun ScoreChips(omdb: OmdbFacts?) {
    val chips = listOfNotNull(
        omdb?.imdb?.let { "IMDb" to "$it" },
        omdb?.rottenTomatoes?.let { "Rotten Tomatoes" to "$it %" },
        omdb?.metacritic?.let { "Metacritic" to "$it" },
    )
    val oscars = omdb?.oscarsWon?.takeIf { it > 0 }
    // The Oscars say more than a count of prizes: the count shows only when there are none.
    val prizes = omdb?.awardsWon?.takeIf { it > 0 && oscars == null }
    if (chips.isEmpty() && oscars == null && prizes == null) return
    Row(Modifier.testTag("title-ratings"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        chips.forEach { (name, value) ->
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(name, Type.Body.copy(fontSize = 18.sp, letterSpacing = 1.sp), Palette.BarText, maxLines = 1)
                Label(value, Type.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        if (oscars != null) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, Modifier.size(24.dp), tint = Palette.OnBackground)
                Label(if (oscars == 1) com.sokoul.pc.i18n.tr(com.sokoul.pc.i18n.Text.OneOscar) else com.sokoul.pc.i18n.tr(com.sokoul.pc.i18n.Text.OscarsCount, oscars), Type.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        if (prizes != null) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, Modifier.size(24.dp), tint = Palette.Secondary)
                Label(L("$prizes awards", "$prizes récompenses").text(), Type.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

/** Where else to watch it, as the services' own logos; a name stands in when there is no logo. */
@Composable
fun AlsoOn(offers: List<WatchOffer>, logos: Map<String, String>) {
    if (offers.isEmpty()) return
    Row(Modifier.testTag("title-also-on"), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(L("Also on", "Aussi sur").text(), Type.Body, Palette.Secondary, maxLines = 1)
        offers.take(5).forEach { o ->
            val logo = providerLogo(o.service, logos)
            if (logo != null) {
                Art(logo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Label(o.service, Type.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
    }
}

private fun serviceKey(name: String) = name.lowercase(Locale.ROOT).replace("amazon", "").replace("plus", "").replace("+", "").filter { it.isLetterOrDigit() }

/** The logo of a service from its name, whichever way each source spells it ("Prime Video", "Amazon Prime Video"). */
fun providerLogo(service: String, logos: Map<String, String>): String? {
    val key = serviceKey(service)
    if (key.isEmpty()) return null
    return logos.entries.firstOrNull { serviceKey(it.key) == key }?.value
        ?: logos.entries.firstOrNull { val k = serviceKey(it.key); k.length >= 4 && key.length >= 4 && (k.startsWith(key) || key.startsWith(k)) }?.value
}

/** The summary on three lines, and the rest behind a link that says so. */
@Composable
fun Summary(text: String, onFocused: (Boolean) -> Unit) {
    var open by remember(text) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(text, Type.Meta, Palette.Secondary, Modifier.testTag("title-summary"), maxLines = if (open) 12 else 3)
        if (text.length > 210) {
            var focused by remember { mutableStateOf(false) }
            val color by animateColorAsState(if (focused) Palette.OnBackground else Palette.BarText)
            Label(
                (if (open) L("LESS", "RÉDUIRE") else L("READ MORE", "LIRE LA SUITE")).text(),
                Type.Body.copy(fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = 3.sp),
                color,
                Modifier.clip(RoundedCornerShape(8.dp)).tvFocusable(onFocusChanged = { focused = it; onFocused(it) }, onClick = { open = !open })
                    .padding(vertical = 4.dp).testTag("title-summary-more"),
                maxLines = 1,
            )
        }
    }
}

/** The main action: a circled play icon, what it will do, what remains, and how far one has got. */
@Composable
fun PlayCard(label: String, sub: String?, progress: Float?, onClick: () -> Unit, modifier: Modifier, requester: FocusRequester, onFocused: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Metrics.ButtonFocusScale else 1f)
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.ButtonFill)
    val tint = if (focused) Palette.OnFocus else Palette.OnBackground
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(Metrics.ButtonHeight + 18.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape).background(fill)
            .border(1.dp, if (focused) Color.Transparent else Palette.Hairline, shape)
            .tvFocusable(onFocusChanged = { focused = it; onFocused(it) }, onClick = onClick, requester = requester),
    ) {
        Row(Modifier.fillMaxHeight().padding(start = 16.dp, end = 38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(36.dp), tint = fill)
            }
            Column {
                Label(label, Type.Button.copy(fontWeight = FontWeight.Bold), tint, maxLines = 1)
                sub?.let { Label(it, Type.Body.copy(fontSize = 19.sp), if (focused) Palette.OnFocus.copy(alpha = 0.7f) else Palette.Secondary, maxLines = 1) }
            }
        }
        progress?.let { p ->
            var filled by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(BAR_START_MS)
                filled = true
            }
            val shown by animateFloatAsState(if (filled) p.coerceIn(0f, 1f) else 0f, tween(BAR_MS, easing = FastOutSlowInEasing))
            // matchParentSize: the track must not make the button as wide as the row.
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomStart) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(tint.copy(alpha = 0.18f)))
                Box(Modifier.fillMaxWidth(shown).height(5.dp).background(tint))
            }
        }
    }
}

/** A round button that opens into a pill with its name when it is pointed at or focused. */
@Composable
fun LabeledRoundButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, requester: FocusRequester = remember { FocusRequester() }, onFocused: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) Metrics.ButtonFocusScale else 1f)
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.ButtonFill)
    val tint = if (focused) Palette.OnFocus else Palette.OnBackground
    val shape = RoundedCornerShape(50)
    val nudge = com.sokoul.pc.ui.rememberNudge()
    Row(
        modifier.height(Metrics.ButtonHeight).graphicsLayer { scaleX = scale; scaleY = scale; translationX = nudge.x.value; translationY = nudge.y.value }.clip(shape).background(fill)
            .border(1.dp, if (focused) Color.Transparent else Palette.Hairline, shape)
            .tvFocusable(onFocusChanged = { focused = it; onFocused(it) }, onClick = onClick, requester = requester, onDeadEnd = nudge::kick)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
        AnimatedVisibility(
            focused,
            enter = expandHorizontally(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(260)),
            exit = shrinkHorizontally(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
        ) {
            Label(label, Type.Body.copy(fontWeight = FontWeight.SemiBold), tint, Modifier.padding(start = 12.dp, end = 6.dp), maxLines = 1)
        }
        Spacer(Modifier.width(16.dp))
    }
}

private const val STAR_START_MS = 420L
private const val STAR_GAP_MS = 110L
private const val BAR_START_MS = 500L
private const val BAR_MS = 900

/** The languages of the work: the original one first, then the others, in the language of the interface. */
private fun languages(info: TmdbInfo?): String? {
    val codes = (listOfNotNull(info?.originalLanguage) + info?.spokenLanguages.orEmpty()).distinct()
    val names = codes.mapNotNull(HeroLogic::languageName).distinct().take(2)
    if (names.isEmpty()) return null
    return names.joinToString(" · ")
}

/** Each piece of the header arrives a moment after the one before it: the page builds itself. */
@Composable
fun Reveal(order: Int, key: Any?, content: @Composable () -> Unit) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(order * REVEAL_GAP_MS)
        progress.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * REVEAL_RISE }) { content() }
}

private const val REVEAL_GAP_MS = 70L
private const val REVEAL_MS = 500
private const val REVEAL_RISE = 22f

/** "Directed by", "Created by": the signature of the work, in one quiet line. */
@Composable
fun Signature(kind: String, name: String?) {
    if (name.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("title-signature")) {
        Label(if (kind == "tv") L("Created by", "Créé par").text() else L("Directed by", "Réalisé par").text(), Type.Body, Palette.Secondary, maxLines = 1)
        Label(name, Type.Body.copy(fontWeight = FontWeight.Bold), Palette.OnBackground, maxLines = 1)
    }
}

private val CAST_PHOTO = 96.dp
private val CAST_W = 160.dp

private const val GLOW_SAMPLE = 200
private const val GLOW_FADE_MS = 900
private const val GLOW_ALPHA = 0.30f
private const val GLOW_X = 520f
private const val GLOW_Y = 720f
private const val GLOW_RADIUS = 1100f

private const val GENRE_CHARS = 50

/** As many genres as fit on the row: three short ones, two long ones. */
fun fitGenres(all: List<String>): List<String> {
    val out = mutableListOf<String>()
    var chars = 0
    for (g in all.take(3)) {
        if (out.isNotEmpty() && chars + g.length > GENRE_CHARS) break
        out += g
        chars += g.length
    }
    return out
}

/** What every hero says about a work, in the same order on every page. */
@Composable
fun HeroInfo(deps: com.sokoul.pc.AppDeps, kind: String, itemId: Int, title: String, overview: String?, info: TmdbInfo?, extras: com.sokoul.pc.data.TitleExtras, remainingMin: Int?, onFocused: (Boolean) -> Unit) {
    Reveal(0, itemId) { PresentedBy(info?.brands.orEmpty()) }
    Spacer(Modifier.height(12.dp))
    // A logo in a script the viewer cannot read (an anime's Japanese one) gives way to the title in text (2026-11-24).
    Reveal(1, itemId) { com.sokoul.pc.home.TitleLogo(com.sokoul.pc.ui.readableLogo(info), title) }
    info?.tagline?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(8.dp))
        Reveal(2, itemId) { Tagline(it) }
    }
    Spacer(Modifier.height(22.dp))
    Reveal(3, itemId) { Facts(info, remainingMin) }
    if (!info?.director.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Reveal(4, itemId) { Signature(kind, info?.director) }
    }
    Spacer(Modifier.height(18.dp))
    val providers by produceState(emptyMap<String, String>(), kind) { value = runCatching { deps.catalog.providerLogos(kind) }.getOrDefault(emptyMap()) }
    AlsoOn(extras.offers, providers)
    if (extras.offers.isNotEmpty()) Spacer(Modifier.height(16.dp))
    ScoreChips(extras.omdb)
    if (extras.omdb != null) Spacer(Modifier.height(16.dp))
    GenrePills(kind, info)
    Spacer(Modifier.height(20.dp))
    Summary(overview?.takeIf { it.isNotBlank() } ?: info?.overview?.takeIf { it.isNotBlank() } ?: info?.overviewEn.orEmpty(), onFocused)
    extras.next?.let { n -> Label(com.sokoul.pc.i18n.tr(com.sokoul.pc.i18n.Text.NextEpisodeOn, n.season, n.episode, formatDate(n.date) ?: n.date), Type.Meta, Palette.Secondary, Modifier.padding(top = 10.dp), maxLines = 1) }
}

private const val OPAQUE = 200
