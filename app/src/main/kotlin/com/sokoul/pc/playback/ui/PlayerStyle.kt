package com.sokoul.pc.playback.ui
import com.sokoul.pc.playback.session.sizeLabel
import com.sokoul.pc.playback.sources.OnlineSubtitles
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.SubtitleBackground
import com.sokoul.desktop.data.SubtitleColor
import com.sokoul.desktop.data.SubtitleFont
import com.sokoul.desktop.data.SubtitlePosition
import com.sokoul.desktop.data.SubtitleSize
import com.sokoul.desktop.data.VideoFormat
import com.sokoul.iptv.data.anime.SkipInterval
import com.sokoul.pc.data.L
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.glassSurface
import com.sokoul.pc.ui.panelEnter
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.tvFocusable

/*
 * Player features: picture format, subtitle style,
 * "Are you still watching?" and the coloured passages on the timeline. They follow Sokoul's own look: the same glass
 * panels, rows and pill buttons as the rest of the player.
 */

/** How the picture fills the screen: Fit / Fill / Stretch. */
fun VideoFormat.scale(): ContentScale = when (this) {
    VideoFormat.FIT -> ContentScale.Fit
    VideoFormat.FILL -> ContentScale.Crop
    VideoFormat.STRETCH -> ContentScale.FillBounds
}

fun VideoFormat.label(): String = when (this) {
    VideoFormat.FIT -> L("Fit", "Ajuster").text()
    VideoFormat.FILL -> L("Fill (crop)", "Remplir (rogner)").text()
    VideoFormat.STRETCH -> L("Stretch", "Étirer").text()
}

/** Episodes that may follow one another with nobody touching anything before the player asks. */
const val STILL_WATCHING_AFTER = 3

/** Colours of the passages on the timeline: intro amber, end violet. */
private val OpeningColor = Color(0xFFFBBF24)
private val EndingColor = Color(0xFFA78BFA)

/** The passages of [skips] as coloured marks over the timeline, at their place in a film of [lengthMs]. */
@Composable
internal fun SegmentMarks(skips: List<SkipInterval>, lengthMs: Long, height: Dp, modifier: Modifier = Modifier) {
    if (skips.isEmpty() || lengthMs <= 0) return
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val total = lengthMs / 1000.0
        skips.forEach { s ->
            val from = (s.start / total).coerceIn(0.0, 1.0).toFloat()
            val to = (s.end / total).coerceIn(0.0, 1.0).toFloat()
            if (to > from) {
                Box(
                    Modifier.offset(x = maxWidth * from).width(maxWidth * (to - from)).fillMaxHeight()
                        .background((if (s.opening) OpeningColor else EndingColor).copy(alpha = 0.75f))
                        .testTag(if (s.opening) "player-segment-opening" else "player-segment-ending"),
                )
            }
        }
    }
}

/** The face a subtitle font is drawn in; null keeps the app's own. */
internal fun SubtitleFont.family(): FontFamily? = when (this) {
    SubtitleFont.STANDARD -> null
    SubtitleFont.ATKINSON -> FontFamily(
        androidx.compose.ui.text.platform.Font("font/AtkinsonHyperlegible-Regular.ttf", FontWeight.Normal),
        androidx.compose.ui.text.platform.Font("font/AtkinsonHyperlegible-Bold.ttf", FontWeight.Bold),
    )
    SubtitleFont.SYSTEM -> FontFamily.SansSerif
    SubtitleFont.SERIF -> FontFamily.Serif
    SubtitleFont.MONOSPACE -> FontFamily.Monospace
}

fun SubtitleFont.label(): String = L(english, french).text()

/** A subtitle line in the chosen style: used over the picture, and in the preview of the settings (at [scale]). */
@Composable
internal fun CueText(text: String, prefs: DesktopPrefs?, modifier: Modifier = Modifier, scale: Float = 1f) {
    val size = prefs?.subtitleSize ?: SubtitleSize.NORMAL
    val color = prefs?.subtitleColor ?: SubtitleColor.WHITE
    val background = prefs?.subtitleBackground ?: SubtitleBackground.OUTLINE
    val font = prefs?.subtitleFont ?: SubtitleFont.STANDARD
    val fontSize = when (size) {
        SubtitleSize.SMALL -> 34
        SubtitleSize.NORMAL -> 42
        SubtitleSize.LARGE -> 52
        SubtitleSize.HUGE -> 64
    } * scale
    val box = background == SubtitleBackground.BOX
    BasicText(
        text,
        modifier.then(if (box) Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xA0000000)).padding(horizontal = 18.dp, vertical = 6.dp) else Modifier),
        style = Type.Meta.copy(
            fontFamily = font.family() ?: Type.Meta.fontFamily,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.24f).sp,
            color = Color(0xFF000000L or color.vlcColor.toLong()),
            textAlign = TextAlign.Center,
            shadow = when (background) {
                SubtitleBackground.OUTLINE -> Shadow(Color.Black, blurRadius = 10f)
                SubtitleBackground.NONE -> Shadow(Color(0xA0000000), androidx.compose.ui.geometry.Offset(2f, 2f), 4f)
                SubtitleBackground.BOX -> null
            },
        ),
    )
}

/** The subtitles Sokoul draws itself (those found online), in the style chosen in the player. */
@Composable
internal fun StyledCue(text: String, prefs: DesktopPrefs?, controlsShown: Boolean, modifier: Modifier = Modifier) {
    val raised = (prefs?.subtitlePosition ?: SubtitlePosition.LOW) == SubtitlePosition.HIGH
    val bottom = (if (controlsShown) 300 else 90) + (if (raised) 120 else 0)
    CueText(text, prefs, modifier.padding(start = 200.dp, end = 200.dp, bottom = bottom.dp).testTag("player-cue"))
}

/** One row of a style panel: its name on the left, the current choice on the right; OK moves to the next choice. */
@Composable
private fun CycleRow(title: String, value: String, tag: String, requester: FocusRequester, onNext: () -> Unit) {
    com.sokoul.pc.ui.PanelOption(title, Modifier.testTag(tag), value = value, requester = requester, onClick = onNext)
}

@Composable
private fun firstFocus(): FocusRequester {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    return first
}

/** Size, colour, background and position of the subtitles, kept in the settings and shown at once on the cues. */
@Composable
internal fun SubtitleStylePanel(prefs: DesktopPrefs, modifier: Modifier = Modifier, online: OnlineSubtitles? = null) {
    // Read through state so the rows and the cue on screen change as soon as a choice is made.
    var tick by remember { mutableStateOf(0) }
    val bump = { tick++ }
    val first = firstFocus()
    fun <E : Enum<E>> next(values: Array<E>, now: E): E = values[(now.ordinal + 1) % values.size]
    com.sokoul.pc.ui.GlassMenu(L("Subtitle style", "Style des sous-titres").text(), modifier.testTag("player-subtitle-style"), width = 640.dp) {
        // Reading the counter here recomposes the rows after each choice, so they show the new values.
        if (tick < 0) return@GlassMenu
        CycleRow(L("Size", "Taille").text(), sizeLabel(prefs.subtitleSize), "style-size", first) { prefs.subtitleSize = next(SubtitleSize.entries.toTypedArray(), prefs.subtitleSize); bump() }
        CycleRow(L("Colour", "Couleur").text(), colorLabel(prefs.subtitleColor), "style-color", remember { FocusRequester() }) { prefs.subtitleColor = next(SubtitleColor.entries.toTypedArray(), prefs.subtitleColor); bump() }
        CycleRow(L("Background", "Fond").text(), backgroundLabel(prefs.subtitleBackground), "style-background", remember { FocusRequester() }) { prefs.subtitleBackground = next(SubtitleBackground.entries.toTypedArray(), prefs.subtitleBackground); bump() }
        CycleRow(L("Position", "Position").text(), positionLabel(prefs.subtitlePosition), "style-position", remember { FocusRequester() }) { prefs.subtitlePosition = next(SubtitlePosition.entries.toTypedArray(), prefs.subtitlePosition); bump() }
        CycleRow(L("Font", "Police").text(), prefs.subtitleFont.label(), "style-font", remember { FocusRequester() }) { prefs.subtitleFont = next(SubtitleFont.entries.toTypedArray(), prefs.subtitleFont); bump() }
        // Translation of the subtitles found online (auto-translate), into the next language at each OK.
        if (online != null && online.canTranslate() && online.active != null) {
            val targets = listOf<String?>(null) + com.sokoul.pc.data.providers.GoogleTranslator.TARGETS
            val now = online.translatedTo
            val shown = when {
                online.translating -> L("Translating…", "Traduction…").text()
                now == null -> L("Off", "Non").text()
                else -> java.util.Locale.forLanguageTag(now).let { it.getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar(Char::uppercase) }
            }
            CycleRow(L("Translate to", "Traduire en").text(), shown, "style-translate", remember { FocusRequester() }) {
                online.translate(targets[(targets.indexOf(now) + 1) % targets.size])
            }
        }
        com.sokoul.pc.ui.PanelNote(
            L("Subtitles of the stream itself take this style from the next title.", "Les sous-titres intégrés au flux prennent ce style dès le titre suivant.").text(),
        )
    }
}

private fun sizeLabel(s: SubtitleSize) = when (s) {
    SubtitleSize.SMALL -> L("Small", "Petite")
    SubtitleSize.NORMAL -> L("Normal", "Normale")
    SubtitleSize.LARGE -> L("Large", "Grande")
    SubtitleSize.HUGE -> L("Very large", "Très grande")
}.text()

private fun colorLabel(c: SubtitleColor) = when (c) {
    SubtitleColor.WHITE -> L("White", "Blanc")
    SubtitleColor.YELLOW -> L("Yellow", "Jaune")
    SubtitleColor.CYAN -> L("Cyan", "Cyan")
    SubtitleColor.GREEN -> L("Green", "Vert")
}.text()

private fun backgroundLabel(b: SubtitleBackground) = when (b) {
    SubtitleBackground.OUTLINE -> L("Black outline", "Contour noir")
    SubtitleBackground.BOX -> L("Dark box", "Fond sombre")
    SubtitleBackground.NONE -> L("Shadow only", "Ombre seule")
}.text()

private fun positionLabel(p: SubtitlePosition) = when (p) {
    SubtitlePosition.LOW -> L("Bottom", "En bas")
    SubtitlePosition.HIGH -> L("Raised", "Plus haut")
}.text()

/** The picture's format, as a list with a check mark on the current one. */
@Composable
internal fun FormatPanel(current: VideoFormat, onPick: (VideoFormat) -> Unit, modifier: Modifier = Modifier) {
    val first = firstFocus()
    com.sokoul.pc.ui.GlassMenu(L("Picture format", "Format de l'image").text(), modifier.testTag("player-format"), width = 520.dp) {
        VideoFormat.entries.forEach { f ->
            com.sokoul.pc.ui.PanelOption(
                f.label(), Modifier.testTag("format-${f.name.lowercase()}"), selected = f == current,
                requester = if (f == current) first else remember { FocusRequester() },
            ) { onPick(f) }
        }
    }
}

/** "Are you still watching?": after several episodes in a row with nobody there, the next one waits for a yes. */
@Composable
internal fun StillWatchingPrompt(onContinue: () -> Unit, onStop: () -> Unit, requester: FocusRequester, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    Column(
        modifier.width(860.dp).panelEnter().glassSurface(RoundedCornerShape(com.sokoul.pc.theme.Corners.Modal), com.sokoul.pc.theme.Corners.Modal).padding(horizontal = 64.dp, vertical = 52.dp).testTag("player-still-watching"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Label(L("Are you still watching?", "Vous regardez toujours ?").text(), com.sokoul.pc.theme.PanelType.Title.copy(textAlign = TextAlign.Center))
        Label(
            L("Playback paused after several episodes in a row without any action.", "La lecture s'est arrêtée après plusieurs épisodes enchaînés sans aucune action.").text(),
            com.sokoul.pc.theme.PanelType.Callout.copy(textAlign = TextAlign.Center), Palette.Secondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            PillButton(L("Continue", "Continuer").text(), onContinue, Modifier.testTag("still-continue"), requester)
            PillButton(L("Stop", "Arrêter").text(), onStop, Modifier.testTag("still-stop"))
        }
    }
}
