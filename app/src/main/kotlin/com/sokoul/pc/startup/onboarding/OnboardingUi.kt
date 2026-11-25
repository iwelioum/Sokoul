package com.sokoul.pc.startup.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.pc.theme.Glass
import com.sokoul.pc.theme.Inter
import com.sokoul.pc.theme.InterDisplay
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.ui.Label

/*
 * The first launch's own scale and parts. Sizes are in the tablet frame of [OnboardingScreen], where the short side of
 * the screen counts 1200 dp: 30 dp of body text reads like 16 sp on a tablet, and 80 dp is a comfortable touch target.
 */

internal object OnbType {
    val Display = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 80.sp, letterSpacing = (-1.4).sp)
    val Title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 54.sp, lineHeight = 62.sp, letterSpacing = (-0.9).sp)
    val Headline = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = (-0.4).sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 42.sp)
    val Secondary = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 36.sp)
    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
    val Field = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 32.sp)
    val Code = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 84.sp, letterSpacing = 10.sp)
}

internal object OnbMetrics {
    val Target = 92.dp
    val Gutter = 56.dp
    val Corner = 28.dp
    val RowCorner = 22.dp
    const val PressScale = 0.97f
    const val FocusScale = 1.03f
}

internal val OnbEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * True where these parts are drawn inside the TV's monochrome chrome (Settings › Sources and keys, DESIGN.md §1): states
 * and notes lose their green and red, and quiet buttons fill white under focus. The first launch keeps its own colours.
 */
internal val LocalMonochrome = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Brighter, warmer text on glass than [Palette.Secondary]: AA contrast on the darkest panel. */
internal val Soft = Color(0xFFB8B8BC)

/** The wide white action of a step. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    PressSurface(
        onClick = { if (enabled && !busy) onClick() },
        modifier = modifier.heightIn(min = OnbMetrics.Target),
        shape = RoundedCornerShape(50),
        rest = if (enabled) Color.White else Color(0x33FFFFFF),
        focus = Color.White,
    ) { _ ->
        Label(text, OnbType.Button, if (enabled) Palette.OnFocus else Soft, Modifier.padding(horizontal = 52.dp), maxLines = 1)
        if (busy) Spinner(Palette.OnFocus, Modifier.padding(end = 40.dp).size(30.dp))
    }
}

/** A quiet action: text on translucent glass. */
@Composable
internal fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mono = LocalMonochrome.current
    PressSurface(onClick = onClick, modifier = modifier.heightIn(min = OnbMetrics.Target), shape = RoundedCornerShape(50), rest = Color(0x1AFFFFFF), focus = if (mono) Color(0xFFF5F5F7) else Color(0x40FFFFFF)) { focused ->
        Label(text, OnbType.Button, if (mono && focused) Palette.OnFocus else Color.White, Modifier.padding(horizontal = 44.dp), maxLines = 1)
    }
}

/**
 * Anything that is pressed: it sinks a little under the finger, lifts and lightens under keyboard focus. Touch-first,
 * still complete with a hardware keyboard (Tab, Enter).
 */
@Composable
internal fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(OnbMetrics.RowCorner),
    rest: Color = Glass.RowRest,
    focus: Color = Color(0x33FFFFFF),
    selected: Boolean = false,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        when { pressed -> OnbMetrics.PressScale; focused -> OnbMetrics.FocusScale; else -> 1f },
        tween(140, easing = OnbEase), label = "press-scale",
    )
    val fill by animateColorAsState(if (focused || selected) focus else rest, tween(140), label = "press-fill")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .then(if (focused) Modifier.border(3.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content(focused) }
}

/** A labelled text field for the finger: label above, a tall rounded box, a show/hide switch for secrets. */
@Composable
internal fun TouchField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Uri,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    error: Boolean = false,
) {
    var reveal by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline by animateColorAsState(
        when { error && !LocalMonochrome.current -> Palette.Error; error || focused -> Color.White; else -> Color(0x26FFFFFF) }, tween(140), label = "field-outline",
    )
    val shape = RoundedCornerShape(OnbMetrics.RowCorner)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Label(label, OnbType.Secondary.copy(fontWeight = FontWeight.SemiBold), Soft)
        Row(
            Modifier.fillMaxWidth().heightIn(min = OnbMetrics.Target).clip(shape).background(Color(0x14FFFFFF))
                .border(if (focused || error) 3.dp else 2.dp, outline, shape).padding(start = 30.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 22.dp)) {
                if (value.isEmpty() && hint != null) Label(hint, OnbType.Field, Color(0x66FFFFFF), maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    interactionSource = interaction,
                    textStyle = OnbType.Field.copy(color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secret && !reveal) KeyboardType.Password else keyboard,
                        autoCorrectEnabled = false,
                        imeAction = imeAction,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
                    visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().semantics { },
                )
            }
            if (secret) {
                PressSurface(onClick = { reveal = !reveal }, rest = Color.Transparent, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 72.dp)) { _ ->
                    Label(if (reveal) Copy.hide else Copy.show, OnbType.Secondary.copy(fontWeight = FontWeight.SemiBold), Color.White, Modifier.padding(horizontal = 22.dp))
                }
            }
        }
    }
}

/** The state of a source on the right of its card: a quiet "Add", a spinner, a check or an alert. */
@Composable
internal fun StatusTag(status: SourceStatus, onCard: Boolean = false) {
    val (text, ink, fill) = when (status) {
        SourceStatus.Ready -> Triple(Copy.statusReady, Color(0xFF0B3D1E), Color(0xFF7BE3A0))
        SourceStatus.Checking -> Triple(Copy.statusChecking, Color.White, Color(0x29FFFFFF))
        SourceStatus.Problem -> Triple(Copy.statusProblem, Color(0xFF4A0E0B), Color(0xFFFF9C94))
        SourceStatus.Empty -> Triple(Copy.statusEmpty, Color.White, Color(if (onCard) 0x29FFFFFF else 0x1FFFFFFF))
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(fill).padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (status) {
            SourceStatus.Ready -> Icon(Icons.Rounded.Check, null, Modifier.size(26.dp), tint = ink)
            SourceStatus.Problem -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(26.dp), tint = ink)
            SourceStatus.Checking -> Spinner(ink, Modifier.size(24.dp))
            SourceStatus.Empty -> Unit
        }
        Label(text, OnbType.Secondary.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp), ink, maxLines = 1)
    }
}

/** A thin arc that turns: the only spinner of the flow. */
@Composable
internal fun Spinner(color: Color, modifier: Modifier = Modifier) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            turn.snapTo(0f)
            turn.animateTo(360f, tween(900, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    androidx.compose.foundation.Canvas(modifier.graphicsLayer { rotationZ = turn.value }) {
        val w = size.minDimension * 0.12f
        drawArc(color, 0f, 270f, false, style = Stroke(w, cap = StrokeCap.Round), topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
    }
}

/** The check of the last step, drawn stroke by stroke: the one authored moment of the flow. */
@Composable
internal fun DrawnCheck(size: Dp, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(620, easing = OnbEase))
        tick.animateTo(1f, tween(380, easing = OnbEase))
    }
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.06f
        drawCircle(Color(0x1F7BE3A0), radius = this.size.minDimension / 2 * ring.value)
        drawArc(
            Color(0xFF7BE3A0), -90f, 360f * ring.value, false,
            style = Stroke(stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
        )
        if (tick.value > 0f) {
            val w = this.size.width
            val a = Offset(w * 0.30f, w * 0.52f)
            val b = Offset(w * 0.45f, w * 0.66f)
            val c = Offset(w * 0.71f, w * 0.38f)
            val first = (tick.value / 0.4f).coerceAtMost(1f)
            val second = ((tick.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
            val path = Path().apply {
                moveTo(a.x, a.y)
                lineTo(a.x + (b.x - a.x) * first, a.y + (b.y - a.y) * first)
                if (second > 0f) lineTo(b.x + (c.x - b.x) * second, b.y + (c.y - b.y) * second)
            }
            drawPath(path, Color(0xFF7BE3A0), style = Stroke(stroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** An icon in a soft rounded square, at the head of a source card. */
@Composable
internal fun SourceGlyph(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(42.dp), tint = tint)
    }
}

/** A plain-language problem under a form. */
@Composable
internal fun ProblemNote(text: String, modifier: Modifier = Modifier) {
    val mono = LocalMonochrome.current
    Row(modifier.fillMaxWidth().semantics { role = Role.Image }, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.padding(top = 4.dp).size(32.dp), tint = if (mono) Color.White else Color(0xFFFF9C94))
        Label(text, OnbType.Secondary, if (mono) Color.White else Color(0xFFFFB7B0))
    }
}
