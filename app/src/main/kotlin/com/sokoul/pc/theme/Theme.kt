package com.sokoul.pc.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colors read from the reference captures (estimates, see docs/design/cartography.md). */
object Palette {
    val Background = Color.Black
    val OnBackground = Color.White
    val Secondary = Color(0xFFA0A0A2)
    val FocusFill = Color(0xFFEBEBEB)
    val OnFocus = Color(0xFF111111)
    val ButtonFill = Color(0xFF1C1C1E)
    val Hairline = Color(0x26FFFFFF)
    val RowFill = Color(0xFF474749)
    val PillFill = Color(0xFF3A3A3C)
    val MenuFill = Color(0xE62A2A2C)
    val ActiveFill = Color(0x33FFFFFF)
    val Error = Color(0xFFFF6961)
    val BarFill = Color(0xE62C2C2E)
    val BarScrim = Color(0xF21C1C1E)
    val BarText = Color(0xFFD6D6D8)
    val AvatarFill = Color(0xFF636366)
}

/**
 * The accent of progress bars. White is Sokoul's own look; another colour is the user's choice in the
 * settings. A state, so what draws with it follows a change at once.
 */
object AccentTheme {
    var color by androidx.compose.runtime.mutableStateOf(Color.White)

    /** A progress bar at rest: grey for white, which is the usual look, and the accent a little softer otherwise. */
    val rest: Color get() = if (color == Color.White) Color(0xFF636366) else color.copy(alpha = 0.8f)
}

/** Sizes in reference pixels: one dp equals one pixel of a 1920x1080 capture (see ScaledRoot). */
object Metrics {
    /** The page's left edge: narrower on a tablet held upright, where the screen is that much narrower. */
    val PageMargin get() = if (com.sokoul.pc.ui.Form.tabletPortrait) 56.dp else 80.dp
    val ButtonHeight = 66.dp
    const val ButtonFocusScale = 1.12f
    val RowHeight = 65.dp
    val RowGap = 13.dp
    val PillHeight = 60.dp
    val PillIcon = 50.dp
    val MenuWidth = 356.dp
    val MenuHeight = 600.dp
    val MenuInset = 36.dp
    val MenuCorner = 44.dp
    val MenuItemHeight = 66.dp
    val MenuItemFocusHeight = 74.dp
    val MenuItemStep = 76.dp
    val SettingsColumnX = 1058.dp
    val SettingsColumnWidth = 744.dp
}

/** The top navigation bar. */
object NavMetrics {
    val Top = 26.dp
    val Side = 46.dp
    val Height = 68.dp
    val Icon = 26.dp
    val ScrimHeight = 150.dp
    /** The tab bar of a phone held upright. */
    val BottomBar = 76.dp
}

/** Card sizes measured on 1920x1080 captures. */
object CardMetrics {
    const val FocusScale = 1.10f
    val Corner = 20.dp
    val Gap = 40.dp
    val RowVerticalPadding = 26.dp
    val PosterWidth = 260.dp
    val PosterHeight = 389.dp
    val ContinueWidth = 320.dp
    val ContinueHeight = 230.dp
    val EpisodeWidth = 410.dp
    val EpisodeHeight = 230.dp
    val TrailerHeight = 279.dp
    val PersonSize = 260.dp
}

val Inter = FontFamily(
    Font("font/Inter-Regular.ttf", FontWeight.Normal),
    Font("font/Inter-Medium.ttf", FontWeight.Medium),
    Font("font/Inter-SemiBold.ttf", FontWeight.SemiBold),
    Font("font/Inter-Bold.ttf", FontWeight.Bold),
)

val InterDisplay = FontFamily(
    Font("font/InterDisplay-Medium.ttf", FontWeight.Medium),
    Font("font/InterDisplay-SemiBold.ttf", FontWeight.SemiBold),
    Font("font/InterDisplay-Bold.ttf", FontWeight.Bold),
)

/**
 * The glass of every panel, sheet, popover and toast (tvOS "material"). Compose has no backdrop blur on both targets, so the
 * depth comes from a dark translucent base, a light sheen at the top edge, a hairline rim and a soft shadow.
 */
object Glass {
    val Base = Color(0xE01C1C1E)
    val Sheen = Color(0x1AFFFFFF)
    val Rim = Color(0x1FFFFFFF)
    val Shadow = Color(0x99000000)
    /** Settings-style rows at rest: barely there, so the white focus is the only strong shape. */
    val RowRest = Color(0x17FFFFFF)
    /** The category in use when the focus is elsewhere. */
    val RowCurrent = Color(0x29FFFFFF)
    /** Behind modals only: the picture stays readable around the panel. */
    val Scrim = Color(0x8C000000)
    /** Denser glass for lists opened over a panel (Figma color/surface/glass-dense). */
    val Dense = Color(0xF5141416)
    /** The silver hairline and light of the Settings redesign (Figma color/border/silver). */
    val Silver = Color(0x73C9CCD3)
}

/** Tokens of the Settings redesign (openspec settings-redesign D9; Figma file "Sokoul — Réglages"). */
object Nacre {
    /** The graphite canvas behind Settings. */
    val Canvas = Color(0xFF090909)
    /** The lit focus: pearly white at the top, a touch warmer at the bottom. */
    val Focus = Color(0xFFF8F5ED)
    val FocusEnd = Color(0xFFE4E0D6)
    val Ink = Color(0xFF141416)
}

/** Full pages (settings, tools): a quiet vertical light, brighter at the top, instead of a flat grey. */
val PageGradient = androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0xFF333336), 0.55f to Color(0xFF2A2A2D), 1f to Color(0xFF1F1F21))

/** Corner radii shared by every surface, so a row inside a panel follows the panel's curve. */
object Corners {
    val Panel = 36.dp
    val Modal = 40.dp
    val Row = 18.dp
    val Toast = 50
    val Chip = 12.dp
}

/** tvOS text sizes at Sokoul's 1920 reference (1 dp ≈ 1 pt): the same on PC and TV. Titles use Inter Display. */
object PanelType {
    val Title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 46.sp)
    val Headline = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 36.sp)
    val Option = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 29.sp)
    val Callout = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 24.sp)
    val Section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
}

/** Sizes matched against captures at equal text width (docs/design/font-comparison/comparison.png). */
object Type {
    val RowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 31.sp)
    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val Meta = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 24.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp)
    val Menu = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 26.sp)
    val Nav = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 23.sp)
    val Pill = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val SectionLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 26.sp)
    /** Names under the icons of a phone's bottom bar. */
    val TabLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp)
}
