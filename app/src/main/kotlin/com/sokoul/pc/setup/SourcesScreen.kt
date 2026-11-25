package com.sokoul.pc.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.pc.data.L
import com.sokoul.pc.startup.onboarding.Copy
import com.sokoul.pc.startup.onboarding.KeysSheet
import com.sokoul.pc.startup.onboarding.LocalMonochrome
import com.sokoul.pc.startup.onboarding.OnboardingModel
import com.sokoul.pc.startup.onboarding.Sheet
import com.sokoul.pc.startup.onboarding.SourceSheetBody
import com.sokoul.pc.startup.onboarding.SourceStatus
import com.sokoul.pc.startup.onboarding.Spinner
import com.sokoul.pc.theme.Inter
import com.sokoul.pc.theme.InterDisplay
import com.sokoul.pc.ui.Form
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.QrCode
import com.sokoul.pc.ui.moveWithArrow
import com.sokoul.pc.ui.softShadow
import com.sokoul.pc.ui.tvFocusable

/** DESIGN.md §4 on the 1920 frame: the floors are body 29, secondary 25, titles 48. */
private object SrcType {
    val Page = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 72.sp, letterSpacing = (-0.96).sp)
    val Title = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.48).sp)
    val Strong = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 31.sp, lineHeight = 40.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 29.sp, lineHeight = 40.sp)
    val Secondary = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 32.sp)
}

private val Plaque = Color(0xFF1C1C1E)
private val Pill = Color(0xFF2C2C2E)
private val FocusFill = Color(0xFFF5F5F7)
private val Ink = Color(0xFF0B0B0C)
private val Secondary = Color(0x99FFFFFF)

/** What sits over the page: a service's guide, then the sheet that asks for its key. */
private sealed interface Layer {
    data class Guide(val card: SourceCardSpec) : Layer
    data class Entry(val card: SourceCardSpec, val fromGuide: Boolean) : Layer
}

private fun icon(card: SourceCardSpec): ImageVector = when (card.id) {
    "iptv" -> Icons.Rounded.LiveTv
    "realdebrid" -> Icons.Rounded.Movie
    "subtitles" -> Icons.Rounded.ClosedCaption
    "mainapi" -> Icons.Rounded.Dns
    "tmdb" -> Icons.Rounded.Theaters
    "omdb" -> Icons.Rounded.Star
    "fanart" -> Icons.Rounded.Image
    "tvdb" -> Icons.AutoMirrored.Rounded.ViewList
    "tvmaze" -> Icons.Rounded.Event
    "watchmode" -> Icons.Rounded.Tv
    "motn" -> Icons.Rounded.Public
    "tastedive" -> Icons.Rounded.ThumbUp
    else -> Icons.Rounded.Key
}

private val pageTitle = L("Sources and keys", "Sources et clés")

/** The words of the guide button that opens a card's entry sheet. */
private fun entryLabel(card: SourceCardSpec): String? = when (card.entry) {
    SourceEntry.Iptv -> L("Enter my subscription", "Saisir mon abonnement").text()
    SourceEntry.RealDebrid -> Copy.debridConnect
    SourceEntry.Server -> L("Enter the address", "Saisir l'adresse").text()
    SourceEntry.Subtitles, SourceEntry.Key -> L("Enter the key", "Saisir la clé").text()
    SourceEntry.None -> null
}

/**
 * Settings › Sources and keys (openspec sokoul-server 6.4, credentials-vault): every source and key a person can bring,
 * what each one adds and where it stands, and per service a guide in plain words with the steps, the cost, what stays
 * private and a QR of the official page. "Enter…" opens the same sheets as the first launch, which check with the service
 * before storing anything. Back closes the sheet on top first, then leaves the page.
 */
@Composable
fun SourcesScreen(
    model: OnboardingModel,
    onBack: () -> Unit,
    /** A round back button for touch and mouse; the remote's Back key needs none. */
    backButton: Boolean = !com.sokoul.pc.Platform.IS_TV || Form.tablet,
    /** Renders only: the layer open from the start. */
    initialGuide: String? = null,
    initialEntry: String? = null,
) {
    val startCard = (initialEntry ?: initialGuide)?.let { id -> SourcesPage.cards.firstOrNull { it.id == id } }
    var layer by remember {
        mutableStateOf<Layer?>(
            when {
                startCard == null -> null
                initialEntry != null -> Layer.Entry(startCard, fromGuide = false)
                else -> Layer.Guide(startCard)
            },
        )
    }
    // Read by every card: a sheet that closes may have stored a key, which the model's state alone may not report.
    var revision by remember { mutableStateOf(0) }
    val requesters = remember { SourcesPage.cards.associate { it.id to FocusRequester() } }
    var lastCard by remember { mutableStateOf(SourcesPage.cards.first().id) }
    val narrow = Form.tabletPortrait || Form.isPhone

    fun close() {
        when (val l = layer) {
            is Layer.Entry -> { model.cancelDeviceLink(); revision++; layer = if (l.fromGuide) Layer.Guide(l.card) else null }
            is Layer.Guide -> layer = null
            null -> onBack()
        }
    }
    // Focus comes back to the card a guide was opened from; the page starts on the first card.
    LaunchedEffect(layer == null) {
        // A touch tablet opens with nothing lit (settings-redesign touch-input): a finger needs no focus to come back to.
        if (layer != null || Form.tablet) return@LaunchedEffect
        withFrameNanos { }
        runCatching { requesters.getValue(lastCard).requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).testTag("sources-page")
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Escape || e.key == Key.Back) {
                    if (e.type == KeyEventType.KeyDown) close()
                    true
                } else false
            },
    ) {
        val margin = if (narrow) 56.dp else 80.dp
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().navigationBarsPadding()
                .padding(start = margin, end = margin, top = if (narrow) 56.dp else 64.dp, bottom = 80.dp),
        ) {
            if (backButton) {
                RoundBack(enabled = layer == null, onClick = ::close)
                Spacer(Modifier.height(24.dp))
            }
            Label(pageTitle.text(), SrcType.Page, Color.White, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Label(
                L(
                    "Everything is optional. Choose a service to see what it adds and how to get it.",
                    "Tout est facultatif. Choisissez un service pour voir ce qu'il apporte et comment l'obtenir.",
                ).text(),
                SrcType.Body, Secondary, Modifier.widthIn(max = 1560.dp),
            )
            val columns = if (narrow) 1 else 2
            SourcesSection.entries.forEach { section ->
                Spacer(Modifier.height(56.dp))
                Label(
                    if (section == SourcesSection.Yours) Copy.stepSources else Copy.advancedTitle,
                    SrcType.Title, Color.White, maxLines = 1,
                )
                Spacer(Modifier.height(if (section == SourcesSection.Expert) 8.dp else 20.dp))
                if (section == SourcesSection.Expert) {
                    Label(Copy.advancedLead, SrcType.Secondary, Secondary, Modifier.padding(bottom = 20.dp))
                }
                SourcesPage.section(section).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        row.forEach { card ->
                            val state = revision.let { SourcesPage.state(card, model) }
                            SourceCard(
                                card, state, requesters.getValue(card.id), enabled = layer == null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) { lastCard = card.id; layer = Layer.Guide(card) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        // The scrim closes what is on top, like Back.
        AnimatedVisibility(layer != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(remember { MutableInteractionSource() }, indication = null) { close() })
        }
        when (val l = layer) {
            is Layer.Guide -> GuideSheet(
                l.card, narrow,
                Modifier.align(if (narrow) Alignment.BottomCenter else Alignment.Center),
                onEnter = entryLabel(l.card)?.let { label -> label to { layer = Layer.Entry(l.card, fromGuide = true) } },
                onClose = ::close,
            )
            is Layer.Entry -> CompositionLocalProvider(LocalMonochrome provides true) {
                EntrySheet(
                    l.card, model, narrow,
                    Modifier.align(if (narrow) Alignment.BottomCenter else Alignment.CenterEnd),
                    onClose = ::close,
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun RoundBack(enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) FocusFill else Pill, tween(140), label = "back-fill")
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(fill).testTag("sources-back")
            .then(if (enabled) Modifier.tvFocusable(onFocusChanged = { focused = it }, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, L("Back", "Retour").text(), Modifier.size(34.dp), tint = if (focused) Ink else Color.White)
    }
}

/** A source: its name and what it adds on the left, where it stands on the right. Focus fills it white and lifts it. */
@Composable
private fun SourceCard(card: SourceCardSpec, state: SourceCardState, requester: FocusRequester, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val lit = focused && enabled
    val fill by animateColorAsState(if (lit) FocusFill else Plaque, tween(140), label = "card-fill")
    val scale by animateFloatAsState(if (lit) 1.03f else 1f, tween(140), label = "card-scale")
    val ink = if (lit) Ink else Color.White
    val soft = if (lit) Ink.copy(alpha = 0.72f) else Secondary
    val guide = ServiceGuides.of(card.guides.first())
    Row(
        modifier
            .heightIn(min = 150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (lit) Modifier.softShadow(24.dp, spread = 20.dp, strength = 0.5f) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .testTag("source-${card.id}")
            .then(
                if (enabled) Modifier.tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester)
                else Modifier.focusRequester(requester),
            )
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon(card), null, Modifier.padding(top = 2.dp).size(40.dp), tint = ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Label(card.title(), SrcType.Strong, ink, Modifier.weight(1f), maxLines = 1)
                StateLine(state, ink, soft)
            }
            guide?.let { Label(adds(card, it), SrcType.Secondary, soft, maxLines = 3) }
            if (state.status == SourceStatus.Problem) state.problem?.let { problem ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.padding(top = 2.dp).size(28.dp), tint = ink)
                    Label(problem, SrcType.Secondary, ink, maxLines = 2)
                }
            }
        }
    }
}

/** The subtitles card speaks for its two services; the others say what their own service adds. */
private fun adds(card: SourceCardSpec, guide: ServiceGuide): String =
    if (card.id == "subtitles") Copy.subtitlesBenefit else guide.adds.text()

/** "✓ •••• 1234", "Checking…", "! To fix" or "Not added": monochrome, the icon carries the difference. */
@Composable
private fun StateLine(state: SourceCardState, ink: Color, soft: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state.status) {
            SourceStatus.Ready -> {
                Icon(Icons.Rounded.Check, null, Modifier.size(28.dp), tint = ink)
                Label(state.value ?: Copy.statusReady, SrcType.Secondary, ink, Modifier.widthIn(max = 380.dp), maxLines = 1)
            }
            SourceStatus.Checking -> {
                Spinner(soft, Modifier.size(24.dp))
                Label(Copy.statusChecking, SrcType.Secondary, soft, maxLines = 1)
            }
            SourceStatus.Problem -> Label(Copy.statusProblem, SrcType.Secondary.copy(fontWeight = FontWeight.SemiBold), ink, maxLines = 1)
            SourceStatus.Empty -> Label(L("Not added", "Non ajouté").text(), SrcType.Secondary, soft, maxLines = 1)
        }
    }
}

/** A capsule button: white with dark ink when it is the main action or focused, graphite otherwise. */
@Composable
private fun Capsule(text: String, primary: Boolean, tag: String, requester: FocusRequester = remember { FocusRequester() }, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused || primary) FocusFill else Pill, tween(140), label = "capsule-fill")
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "capsule-scale")
    Box(
        Modifier.heightIn(min = 72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.softShadow(36.dp, spread = 18.dp, strength = 0.5f) else Modifier)
            .clip(RoundedCornerShape(50)).background(fill).testTag(tag)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester)
            .padding(horizontal = 44.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, SrcType.Strong, if (focused || primary) Ink else Color.White, maxLines = 1)
    }
}

/**
 * "How to get it": what the service adds, the numbered steps, the cost, what stays private, and the official page as a
 * QR to open on a phone. The main button opens the entry sheet; Back or Close returns to the cards.
 */
@Composable
private fun GuideSheet(card: SourceCardSpec, narrow: Boolean, modifier: Modifier, onEnter: Pair<String, () -> Unit>?, onClose: () -> Unit) {
    var chosen by remember(card.id) { mutableStateOf(0) }
    val guide = ServiceGuides.of(card.guides[chosen]) ?: return
    val first = remember { FocusRequester() }
    LaunchedEffect(card.id) {
        if (Form.tablet) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val shape = if (narrow) RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp) else RoundedCornerShape(40.dp)
    Column(
        modifier
            .then(if (narrow) Modifier.fillMaxWidth().heightIn(max = 1700.dp) else Modifier.padding(vertical = 40.dp).width(1640.dp))
            .overlayGlass(shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .navigationBarsPadding()
            .testTag("guide-${guide.id}"),
    ) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(start = 56.dp, end = 56.dp, top = 52.dp),
        ) {
            Label(if (card.guides.size > 1) card.title() else guide.name, SrcType.Title, Color.White, maxLines = 1)
            Spacer(Modifier.height(12.dp))
            Label(guide.adds.text(), SrcType.Body, Secondary)
            if (card.guides.size > 1) {
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    card.guides.forEachIndexed { i, id ->
                        Choice(ServiceGuides.of(id)?.name ?: id, i == chosen, "guide-tab-$id") { chosen = i }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            val link = guide.link
            if (narrow) {
                GuideSteps(guide)
                if (link != null) {
                    Spacer(Modifier.height(40.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrCode(link, scanCaption(), size = 260.dp)
                        QrCaption(link, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(36.dp))
                GuideFacts(guide)
            } else {
                // Steps on the text edge; the phone's way in and the facts beside them, so a guide fits the TV screen
                // whole: the remote cannot scroll text that holds no focus.
                Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                    Column(Modifier.weight(1f)) {
                        GuideSteps(guide)
                        if (link != null) {
                            Spacer(Modifier.height(32.dp))
                            GuideFacts(guide)
                        }
                    }
                    Column(Modifier.width(420.dp)) {
                        if (link != null) {
                            QrCode(link, scanCaption(), size = 300.dp)
                            Spacer(Modifier.height(20.dp))
                            QrCaption(link)
                        } else {
                            GuideFacts(guide)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().focusGroup().padding(start = 56.dp, end = 56.dp, top = 36.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (onEnter != null) Capsule(onEnter.first, primary = true, tag = "guide-enter", requester = first, onClick = onEnter.second)
            Capsule(L("Close", "Fermer").text(), primary = false, tag = "guide-close", requester = if (onEnter == null) first else remember { FocusRequester() }, onClick = onClose)
        }
    }
}

private fun scanCaption() = L("Scan with your phone", "Scannez avec votre téléphone").text()

@Composable
private fun QrCaption(link: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(scanCaption(), SrcType.Strong, Color.White)
        Label(SourcesPage.shortLink(link), SrcType.Secondary, Secondary)
    }
}

@Composable
private fun GuideSteps(guide: ServiceGuide) {
    Label(L("How to get it", "Comment l'obtenir").text(), SrcType.Strong, Color.White)
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        guide.steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Pill), contentAlignment = Alignment.Center) {
                    Label("${i + 1}", SrcType.Secondary.copy(fontWeight = FontWeight.SemiBold), Color.White, maxLines = 1)
                }
                Label(step.text(), SrcType.Body, Color.White, Modifier.weight(1f).padding(top = 2.dp))
            }
        }
    }
}

/** The cost and what stays private: one line each, an icon on the text edge of the steps' numbers. */
@Composable
private fun GuideFacts(guide: ServiceGuide) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Fact(Icons.Rounded.Payments, guide.cost.text())
        Fact(Icons.Rounded.Lock, guide.privacy.text())
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp), tint = Secondary) }
        Label(text, SrcType.Secondary, Secondary, Modifier.weight(1f).padding(top = 6.dp))
    }
}

/** One of two services in a guide (OpenSubtitles or Subdl): white when chosen. */
@Composable
private fun Choice(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused || selected) FocusFill else Pill, tween(140), label = "choice-fill")
    Box(
        Modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(50)).background(fill).testTag(tag)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(text, SrcType.Secondary.copy(fontWeight = FontWeight.SemiBold), if (focused || selected) Ink else Color.White, maxLines = 1)
    }
}

/**
 * The first launch's own sheet for this source (fields, Real-Debrid's QR sign-in, the subtitles keys), in the page's
 * monochrome frame. A single expert key gets the key sheet for that key alone, with the format check of its guide.
 */
@Composable
private fun EntrySheet(card: SourceCardSpec, model: OnboardingModel, narrow: Boolean, modifier: Modifier, onClose: () -> Unit) {
    val focus = LocalFocusManager.current
    val body = remember { FocusRequester() }
    LaunchedEffect(card.id) {
        if (Form.tablet) return@LaunchedEffect
        withFrameNanos { }
        runCatching { body.requestFocus() }
    }
    val shape = if (narrow) RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp) else RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp)
    Column(
        modifier
            .then(if (narrow) Modifier.fillMaxWidth().heightIn(max = 1700.dp) else Modifier.width(1000.dp).fillMaxHeight())
            .overlayGlass(shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding().navigationBarsPadding().imePadding()
            // The first launch's parts take focus as buttons and fields: the remote's arrows move between them here.
            .onKeyEvent { focus.moveWithArrow(it) }
            .testTag("entry-${card.id}"),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 52.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon(card), null, Modifier.size(40.dp), tint = Color.White)
            Spacer(Modifier.width(24.dp))
            Label(card.title(), SrcType.Title, Color.White, Modifier.weight(1f), maxLines = 1)
        }
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                .focusRequester(body).focusGroup()
                .padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            when (card.entry) {
                SourceEntry.Iptv -> SourceSheetBody(Sheet.Iptv, model, onClose)
                SourceEntry.RealDebrid -> SourceSheetBody(Sheet.RealDebrid, model, onClose)
                SourceEntry.Server -> SourceSheetBody(Sheet.Server, model, onClose)
                SourceEntry.Subtitles -> KeysSheet(
                    model, Copy.subtitlesNeedKey, listOf("opensubtitles" to "OpenSubtitles", "subdl" to "Subdl"),
                    card.kind, formatWarning = SourcesPage::keyFormatWarning,
                )
                SourceEntry.Key -> KeysSheet(
                    model, ServiceGuides.of(card.id)?.adds?.text() ?: Copy.advancedLead,
                    listOf(card.id to card.title()), card.kind, formatWarning = SourcesPage::keyFormatWarning,
                )
                SourceEntry.None -> Unit
            }
            SheetDone(onClose)
        }
    }
}

@Composable
private fun SheetDone(onClose: () -> Unit) {
    com.sokoul.pc.startup.onboarding.QuietButton(Copy.done, onClose, Modifier.testTag("entry-done"))
}

/**
 * The glass of the page's sheets, dense enough that the cards behind never read through the text (glass is for
 * overlays, DESIGN.md §1): a soft shadow, a near-opaque graphite fill, the top sheen and one hairline.
 */
private fun Modifier.overlayGlass(shape: RoundedCornerShape): Modifier = this
    .softShadow(40.dp)
    .clip(shape)
    .background(Color(0xFA1C1C1E))
    .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x14FFFFFF), 0.25f to Color.Transparent))
    .border(1.dp, Color(0x1FFFFFFF), shape)
