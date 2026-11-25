package com.sokoul.pc.startup.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.AudioPreference
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.QrCode
import kotlinx.coroutines.launch

/** The short side of a tablet counts this many dp in the first launch, whatever the orientation. */
private const val TABLET_SHORT_SIDE = 1200f

internal enum class Sheet { Iptv, RealDebrid, Subtitles, Server, Advanced }

/**
 * The first launch on a touch tablet: Welcome → Sources → Viewing → Ready. Landscape keeps the steps in a column on
 * the left; portrait shows them on top and keeps the action bar above the keyboard. Every source opens in a sheet
 * (from the bottom in portrait, from the side in landscape) and Back always closes the sheet first, then goes back a step.
 */
@Composable
fun OnboardingScreen(model: OnboardingModel, onFinished: () -> Unit, onLater: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("onboarding")) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val landscape = w > h
        val density = Density(minOf(w, h) / TABLET_SHORT_SIDE, fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.3f))
        CompositionLocalProvider(LocalDensity provides density) {
            OnboardingFrame(model, landscape, onFinished, onLater)
        }
    }
}

@Composable
private fun OnboardingFrame(model: OnboardingModel, landscape: Boolean, onFinished: () -> Unit, onLater: () -> Unit) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val focus = LocalFocusManager.current
    fun goBack() {
        when {
            sheet != null -> { model.cancelDeviceLink(); sheet = null; focus.clearFocus() }
            !model.back() -> Unit
        }
    }
    val finish = { model.finish(); onFinished() }
    val later = { model.later(); onLater() }
    // Back (mapped to Escape by the activity) must reach this screen even when nothing inside has focus yet.
    val root = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { root.requestFocus() } }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF26262A), 0.6f to Color(0xFF1C1C1F), 1f to Color(0xFF141416)))
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) goBack()
                    true
                } else false
            }
            .focusRequester(root)
            .focusable(),
    ) {
        // One cool light from the top corner, fading over the whole screen: depth with no visible edge.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x382B6FFF), Color(0x142B6FFF), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, 0f),
                        radius = maxOf(size.width, size.height) * 0.75f,
                    ),
                )
            },
        )
        if (landscape) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                StepRail(model.step, Modifier.width(560.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().imePadding()) {
                    StepBody(model, landscape, onOpen = { sheet = it }, modifier = Modifier.weight(1f))
                    ActionBar(model, landscape, onBack = ::goBack, onLater = later, onFinish = finish)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                // The welcome page carries the large logo itself; the header starts with the first real step.
                if (model.step != OnboardingStep.Welcome) StepHeader(model.step)
                StepBody(model, landscape, onOpen = { sheet = it }, modifier = Modifier.weight(1f))
                ActionBar(model, landscape, onBack = ::goBack, onLater = later, onFinish = finish)
            }
        }

        // The sheet of a source, over a scrim that closes it.
        AnimatedVisibility(sheet != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { goBack() },
            )
        }
        AnimatedVisibility(
            sheet != null,
            modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = if (landscape) slideInHorizontally(tween(320, easing = OnbEase)) { it / 2 } + fadeIn(tween(220))
            else slideInVertically(tween(320, easing = OnbEase)) { it / 2 } + fadeIn(tween(220)),
            exit = if (landscape) slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(160))
            else slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(160)),
        ) {
            var shown by remember { mutableStateOf(sheet ?: Sheet.Iptv) }
            LaunchedEffect(sheet) { sheet?.let { shown = it } }
            SourceSheet(shown, model, landscape, onClose = ::goBack)
        }
    }
}

// ---- Frame ------------------------------------------------------------------------------------------------------------

private fun stepTitle(s: OnboardingStep) = when (s) {
    OnboardingStep.Welcome -> Copy.stepWelcome
    OnboardingStep.Sources -> Copy.stepSources
    OnboardingStep.Preferences -> Copy.stepPreferences
    OnboardingStep.Ready -> Copy.stepReady
}

@Composable
private fun StepRail(current: OnboardingStep, modifier: Modifier) {
    Column(modifier.padding(start = 72.dp, top = 72.dp, end = 32.dp, bottom = 56.dp)) {
        Image(painterResource("sokoul.png"), "Sokoul", Modifier.size(96.dp))
        Spacer(Modifier.height(72.dp))
        OnboardingStep.entries.forEach { s ->
            val done = s.ordinal < current.ordinal
            val active = s == current
            Row(Modifier.fillMaxWidth().heightIn(min = 84.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50))
                        .background(when { active -> Color.White; done -> Color(0x337BE3A0); else -> Color(0x14FFFFFF) }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Rounded.Check, null, Modifier.size(26.dp), tint = Color(0xFF7BE3A0))
                    else Label("${s.ordinal + 1}", OnbType.Secondary.copy(fontWeight = FontWeight.Bold), if (active) Palette.OnFocus else Soft)
                }
                Label(stepTitle(s), OnbType.Body.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium), if (active) Color.White else Soft, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, null, Modifier.padding(top = 4.dp).size(26.dp), tint = Soft)
            Label(Copy.privacy, OnbType.Secondary.copy(fontSize = OnbType.Secondary.fontSize * 0.92f), Soft)
        }
    }
}

@Composable
private fun StepHeader(current: OnboardingStep) {
    Row(
        Modifier.fillMaxWidth().padding(start = OnbMetrics.Gutter, end = OnbMetrics.Gutter, top = 44.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Image(painterResource("sokoul.png"), "Sokoul", Modifier.size(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(Copy.stepOf(current.ordinal + 1, OnboardingStep.entries.size), OnbType.Secondary, Soft, maxLines = 1)
            Label(stepTitle(current), OnbType.Body.copy(fontWeight = FontWeight.Bold), Color.White, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingStep.entries.forEach { s ->
                Box(
                    Modifier.height(10.dp).width(if (s == current) 44.dp else 10.dp).clip(RoundedCornerShape(50))
                        .background(if (s.ordinal <= current.ordinal) Color.White else Color(0x33FFFFFF)),
                )
            }
        }
    }
}

@Composable
private fun ActionBar(model: OnboardingModel, landscape: Boolean, onBack: () -> Unit, onLater: () -> Unit, onFinish: () -> Unit) {
    val step = model.step
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (landscape) 80.dp else OnbMetrics.Gutter, vertical = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (step) {
            OnboardingStep.Welcome -> QuietButton(Copy.later, onLater, Modifier.testTag("onb-later"))
            else -> QuietButton(Copy.back, onBack, Modifier.testTag("onb-back"))
        }
        Spacer(Modifier.weight(1f))
        when (step) {
            OnboardingStep.Welcome -> PrimaryButton(Copy.start, { model.next() }, Modifier.testTag("onb-start"))
            OnboardingStep.Ready -> PrimaryButton(Copy.enter, onFinish, Modifier.testTag("onb-enter"))
            else -> PrimaryButton(Copy.next, { model.next() }, Modifier.testTag("onb-next"))
        }
    }
}

@Composable
private fun StepBody(model: OnboardingModel, landscape: Boolean, onOpen: (Sheet) -> Unit, modifier: Modifier) {
    AnimatedContent(
        model.step,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(tween(360, easing = OnbEase)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(260, 80)))
                .togetherWith(slideOutHorizontally(tween(260)) { if (forward) -it / 10 else it / 10 } + fadeOut(tween(180)))
        },
        label = "onboarding-step",
    ) { step ->
        if (step == OnboardingStep.Welcome) {
            // A first page that sits in the middle of the screen, on the same left edge as every other step.
            Column(
                Modifier.fillMaxSize().padding(horizontal = if (landscape) 80.dp else OnbMetrics.Gutter),
                verticalArrangement = Arrangement.Center,
            ) { Column(Modifier.widthIn(max = 1180.dp)) { WelcomeStep(landscape) } }
            return@AnimatedContent
        }
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(horizontal = if (landscape) 80.dp else OnbMetrics.Gutter, vertical = if (landscape) 96.dp else 40.dp),
        ) {
            Column(Modifier.widthIn(max = 1180.dp)) {
                when (step) {
                    OnboardingStep.Welcome -> WelcomeStep(landscape)
                    OnboardingStep.Sources -> SourcesStep(model, onOpen)
                    OnboardingStep.Preferences -> PreferencesStep(model)
                    OnboardingStep.Ready -> ReadyStep(model)
                }
            }
        }
    }
}

// ---- Steps ------------------------------------------------------------------------------------------------------------

@Composable
private fun WelcomeStep(landscape: Boolean) {
    // The intro ends on the logo: it settles here, smaller, as the page arrives.
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrive.animateTo(1f, tween(700, easing = OnbEase)) }
    Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
        if (!landscape) {
            Image(
                painterResource("sokoul.png"), null,
                Modifier.padding(top = 40.dp).size(220.dp).graphicsLayer {
                    val s = 1.35f - 0.35f * arrive.value
                    scaleX = s; scaleY = s; alpha = arrive.value
                },
            )
        }
        Label(Copy.welcomeTitle, OnbType.Display, Color.White, Modifier.graphicsLayer { alpha = arrive.value; translationY = (1f - arrive.value) * 40f })
        Label(Copy.welcomeLead, OnbType.Headline.copy(fontWeight = FontWeight.Medium), Color(0xFFDADADF), Modifier.widthIn(max = 1000.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF)).padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(Color(0xFF7BE3A0)))
            Label(Copy.welcomeTime, OnbType.Secondary, Color.White, maxLines = 1)
        }
        if (!landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, null, Modifier.padding(top = 4.dp).size(26.dp), tint = Soft)
                Label(Copy.privacy, OnbType.Secondary, Soft)
            }
        }
    }
}

private data class SourceCard(val kind: SourceKind, val sheet: Sheet, val icon: ImageVector, val tint: Color, val title: () -> String, val benefit: () -> String)

private val Cards = listOf(
    SourceCard(SourceKind.Iptv, Sheet.Iptv, Icons.Rounded.LiveTv, Color(0xFF6CB4FF), { Copy.iptvTitle }, { Copy.iptvBenefit }),
    SourceCard(SourceKind.RealDebrid, Sheet.RealDebrid, Icons.Rounded.Movie, Color(0xFFFFB86B), { Copy.debridTitle }, { Copy.debridBenefit }),
    SourceCard(SourceKind.Subtitles, Sheet.Subtitles, Icons.Rounded.ClosedCaption, Color(0xFFB79CFF), { Copy.subtitlesTitle }, { Copy.subtitlesBenefit }),
    SourceCard(SourceKind.Server, Sheet.Server, Icons.Rounded.Dns, Color(0xFF7BE3A0), { Copy.serverTitle }, { Copy.serverBenefit }),
    SourceCard(SourceKind.Advanced, Sheet.Advanced, Icons.Rounded.Key, Color(0xFFC9C9CF), { Copy.advancedTitle }, { Copy.advancedBenefit }),
)

@Composable
private fun SourcesStep(model: OnboardingModel, onOpen: (Sheet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Label(Copy.sourcesTitle, OnbType.Title, Color.White)
        Label(Copy.sourcesLead, OnbType.Body, Soft, Modifier.widthIn(max = 1000.dp))
        Spacer(Modifier.height(20.dp))
        Cards.forEach { c ->
            val view = model.sources[c.kind] ?: SourceView(SourceStatus.Empty)
            PressSurface(
                onClick = { onOpen(c.sheet) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp).testTag("card-${c.kind.name}"),
                shape = RoundedCornerShape(OnbMetrics.Corner),
                rest = Color(0x12FFFFFF),
                focus = Color(0x29FFFFFF),
            ) { _ ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    SourceGlyph(c.icon, c.tint)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(c.title(), OnbType.Body.copy(fontWeight = FontWeight.Bold, fontSize = OnbType.Body.fontSize * 1.08f), Color.White, maxLines = 1)
                        val line = when (view.status) {
                            SourceStatus.Ready -> view.summary ?: c.benefit()
                            SourceStatus.Problem -> view.problem ?: c.benefit()
                            else -> c.benefit()
                        }
                        Label(line, OnbType.Secondary, if (view.status == SourceStatus.Problem) Color(0xFFFFB7B0) else Soft, maxLines = 2)
                    }
                    // The chevron already says "open": a tag appears only when there is a state to report.
                    if (view.status != SourceStatus.Empty) StatusTag(view.status, onCard = true)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(40.dp), tint = Soft)
                }
            }
        }
    }
}

@Composable
private fun PreferencesStep(model: OnboardingModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Label(Copy.prefsTitle, OnbType.Title, Color.White)
        Label(Copy.prefsLead, OnbType.Body, Soft, Modifier.widthIn(max = 1000.dp))
        Spacer(Modifier.height(28.dp))
        Label(Copy.audioTitle, OnbType.Headline, Color.White)
        ChoiceGroup {
            Choice(Copy.audioVf, model.audio == AudioPreference.Vf) { model.chooseAudio(AudioPreference.Vf) }
            Choice(Copy.audioVostfr, model.audio == AudioPreference.Vostfr) { model.chooseAudio(AudioPreference.Vostfr) }
            Choice(Copy.audioVo, model.audio == AudioPreference.Vo) { model.chooseAudio(AudioPreference.Vo) }
            Choice(Copy.audioAuto, model.audio == AudioPreference.System) { model.chooseAudio(AudioPreference.System) }
        }
        Spacer(Modifier.height(28.dp))
        Label(Copy.trailersTitle, OnbType.Headline, Color.White)
        ChoiceGroup {
            Choice(Copy.trailersOn, model.trailers) { model.chooseTrailers(true) }
            Choice(Copy.trailersOff, !model.trailers) { model.chooseTrailers(false) }
        }
    }
}

@Composable
private fun ChoiceGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(OnbMetrics.Corner)).background(Color(0x12FFFFFF)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    PressSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = OnbMetrics.Target), rest = Color.Transparent, focus = Color(0x29FFFFFF), selected = selected) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(text, OnbType.Body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), Color.White, Modifier.weight(1f), maxLines = 2)
            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(36.dp), tint = Color.White)
        }
    }
}

@Composable
private fun ReadyStep(model: OnboardingModel) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        DrawnCheck(160.dp)
        Spacer(Modifier.height(8.dp))
        Label(Copy.readyTitle, OnbType.Display, Color.White)
        Label(Copy.readyLead(model.readyCount()), OnbType.Body, Soft, Modifier.widthIn(max = 1000.dp))
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(OnbMetrics.Corner)).background(Color(0x12FFFFFF)).padding(horizontal = 36.dp, vertical = 16.dp),
        ) {
            Cards.filter { it.kind != SourceKind.Advanced }.forEach { c ->
                val view = model.sources[c.kind]
                val ready = view?.status == SourceStatus.Ready
                Row(Modifier.fillMaxWidth().heightIn(min = 96.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(c.icon, null, Modifier.size(40.dp), tint = if (ready) c.tint else Color(0x66FFFFFF))
                    Column(Modifier.weight(1f)) {
                        Label(c.title(), OnbType.Body.copy(fontWeight = FontWeight.SemiBold), if (ready) Color.White else Soft, maxLines = 1)
                        Label(if (ready) view?.summary ?: Copy.statusReady else Copy.notAdded, OnbType.Secondary, Soft, maxLines = 1)
                    }
                    if (ready) Icon(Icons.Rounded.Check, null, Modifier.size(36.dp), tint = Color(0xFF7BE3A0))
                }
            }
        }
    }
}

// ---- Sheets -----------------------------------------------------------------------------------------------------------

@Composable
private fun SourceSheet(sheet: Sheet, model: OnboardingModel, landscape: Boolean, onClose: () -> Unit) {
    val shape = if (landscape) RoundedCornerShape(topStart = 40.dp, bottomStart = 40.dp) else RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
    Column(
        (if (landscape) Modifier.width(980.dp).fillMaxHeight() else Modifier.fillMaxWidth().heightIn(max = 1700.dp))
            .clip(shape)
            .background(Color(0xFF232327))
            .clickable(remember { MutableInteractionSource() }, indication = null) { }
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        if (!landscape) {
            Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(96.dp).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 40.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val card = Cards.first { it.sheet == sheet }
            SourceGlyph(card.icon, card.tint)
            Spacer(Modifier.width(26.dp))
            Label(card.title(), OnbType.Title.copy(fontSize = OnbType.Title.fontSize * 0.85f), Color.White, Modifier.weight(1f), maxLines = 1)
            QuietButton(Copy.done, onClose, Modifier.testTag("sheet-done"))
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            SourceSheetBody(sheet, model, onClose)
        }
    }
}

/** What a source's sheet asks for, without its frame: the first launch and Settings › Sources and keys share it. */
@Composable
internal fun SourceSheetBody(sheet: Sheet, model: OnboardingModel, onClose: () -> Unit) {
    when (sheet) {
        Sheet.Iptv -> IptvSheet(model, onClose)
        Sheet.RealDebrid -> DebridSheet(model, onClose)
        Sheet.Subtitles -> KeysSheet(model, if (model.sources[SourceKind.Subtitles]?.status == SourceStatus.Ready) Copy.subtitlesLead else Copy.subtitlesNeedKey, listOf("opensubtitles" to "OpenSubtitles", "subdl" to "Subdl"), SourceKind.Subtitles)
        Sheet.Server -> ServerSheet(model, onClose)
        Sheet.Advanced -> KeysSheet(
            model, Copy.advancedLead,
            OnboardingModel.ADVANCED_KEYS.map { it.id to "${it.benefit.text()} · ${it.provider}" },
            SourceKind.Advanced,
        )
    }
}

@Composable
internal fun IptvSheet(model: OnboardingModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    val view = model.sources[SourceKind.Iptv]
    val busy = view?.status == SourceStatus.Checking
    fun submit() = scope.launch {
        val ok = if (tab == 0) model.saveXtream(server, user, password) else model.saveM3u(link)
        if (ok) onClose()
    }
    Label(Copy.iptvSheetLead, OnbType.Body, Soft)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Copy.iptvTabAccount, Copy.iptvTabLink).forEachIndexed { i, t ->
            PressSurface(
                onClick = { tab = i },
                modifier = Modifier.weight(1f).heightIn(min = 80.dp),
                shape = RoundedCornerShape(50),
                rest = if (tab == i) Color.White else Color.Transparent,
                focus = if (tab == i) Color.White else Color(0x29FFFFFF),
            ) { _ ->
                Label(t, OnbType.Secondary.copy(fontWeight = FontWeight.SemiBold), if (tab == i) Palette.OnFocus else Color.White, maxLines = 1)
            }
        }
    }
    if (tab == 0) {
        TouchField(Copy.fieldServer, server, { server = it }, hint = Copy.fieldServerHint, error = view?.status == SourceStatus.Problem)
        TouchField(Copy.fieldUser, user, { user = it }, keyboard = KeyboardType.Text)
        TouchField(Copy.fieldPassword, password, { password = it }, secret = true, imeAction = ImeAction.Done, onDone = { submit() })
    } else {
        TouchField(Copy.fieldLink, link, { link = it }, hint = Copy.fieldLinkHint, imeAction = ImeAction.Done, onDone = { submit() }, error = view?.status == SourceStatus.Problem)
    }
    if (view?.status == SourceStatus.Problem) view.problem?.let { ProblemNote(it) }
    if (view?.status == SourceStatus.Ready) view.summary?.let { ReadyNote(it) }
    PrimaryButton(if (busy) Copy.checking else Copy.checkAndSave, { submit() }, Modifier.fillMaxWidth().testTag("iptv-save"), busy = busy)
}

@Composable
internal fun ReadyNote(text: String) {
    val mono = LocalMonochrome.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, null, Modifier.size(32.dp), tint = if (mono) Color.White else Color(0xFF7BE3A0))
        Label(text, OnbType.Secondary, if (mono) Color.White else Color(0xFFB9F0CC))
    }
}

@Composable
internal fun DebridSheet(model: OnboardingModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tokenMode by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    val view = model.sources[SourceKind.RealDebrid]
    Label(Copy.debridSheetLead, OnbType.Body, Soft)
    if (view?.status == SourceStatus.Ready) view.summary?.let { ReadyNote(it) }
    if (view?.status == SourceStatus.Problem) view.problem?.let { ProblemNote(it) }
    if (!tokenMode) {
        when (val link = model.deviceLink) {
            DeviceLink.Idle, is DeviceLink.Failed -> {
                if (link is DeviceLink.Failed) ProblemNote(link.message)
                PrimaryButton(Copy.debridConnect, { model.startDeviceLink() }, Modifier.fillMaxWidth().testTag("rd-connect"))
            }
            DeviceLink.Starting -> PrimaryButton(Copy.debridConnect, {}, Modifier.fillMaxWidth(), busy = true)
            is DeviceLink.Waiting -> DeviceCodePanel(link)
            DeviceLink.Expired -> {
                ProblemNote(Copy.debridExpired)
                PrimaryButton(Copy.debridNewCode, { model.startDeviceLink() }, Modifier.fillMaxWidth())
            }
        }
        QuietButton(Copy.debridUseToken, { model.cancelDeviceLink(); tokenMode = true })
    } else {
        TouchField(Copy.debridTokenField, token, { token = it }, secret = true, imeAction = ImeAction.Done, onDone = {
            scope.launch { if (model.saveDebridToken(token)) onClose() }
        })
        PrimaryButton(Copy.checkAndSave, { scope.launch { if (model.saveDebridToken(token)) onClose() } }, Modifier.fillMaxWidth(), busy = view?.status == SourceStatus.Checking)
    }
}

@Composable
private fun DeviceCodePanel(link: DeviceLink.Waiting) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(OnbMetrics.Corner)).background(Color(0x12FFFFFF)).padding(36.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrCode(link.qrUrl, Copy.debridScan, size = 300.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Label(Copy.debridScan, OnbType.Body.copy(fontWeight = FontWeight.Bold), Color.White)
            Label(Copy.debridOrVisit(link.verificationUrl), OnbType.Secondary, Soft)
            Label(link.userCode, OnbType.Code, Color.White, Modifier.testTag("rd-code"), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Spinner(Soft, Modifier.size(28.dp))
                Label(Copy.debridWaiting, OnbType.Secondary, Soft)
            }
        }
    }
}

@Composable
internal fun ServerSheet(model: OnboardingModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    val view = model.sources[SourceKind.Server]
    Label(Copy.serverLead, OnbType.Body, Soft)
    TouchField(Copy.serverField, address, { address = it }, hint = "192.168.1.20", error = view?.status == SourceStatus.Problem)
    TouchField(Copy.serverKeyField, key, { key = it }, secret = true, imeAction = ImeAction.Done, onDone = {
        scope.launch { if (model.saveServer(address, key)) onClose() }
    })
    if (view?.status == SourceStatus.Problem) view.problem?.let { ProblemNote(it) }
    if (view?.status == SourceStatus.Ready) view.summary?.let { ReadyNote(it) }
    PrimaryButton(Copy.checkAndSave, { scope.launch { if (model.saveServer(address, key)) onClose() } }, Modifier.fillMaxWidth(), busy = view?.status == SourceStatus.Checking)
}

@Composable
internal fun KeysSheet(
    model: OnboardingModel,
    lead: String,
    keys: List<Pair<String, String>>,
    kind: SourceKind,
    /**
     * A plain-language warning when a value cannot be this service's key (the long TMDB token pasted for the short key):
     * the first press shows it instead of asking the service, a second press with the same value checks anyway.
     */
    formatWarning: ((id: String, value: String) -> String?)? = null,
) {
    val scope = rememberCoroutineScope()
    Label(lead, OnbType.Body, Soft)
    keys.forEach { (id, label) ->
        var value by remember(id) { mutableStateOf("") }
        var busy by remember(id) { mutableStateOf(false) }
        var saved by remember(id) { mutableStateOf(false) }
        var warned by remember(id) { mutableStateOf<String?>(null) }
        val hint = model.keyHint(id)
        val warning = warned?.takeIf { it == value }?.let { formatWarning?.invoke(id, it) }
        fun submit() {
            if (warning == null && formatWarning?.invoke(id, value) != null) {
                warned = value
                return
            }
            scope.launch { busy = true; saved = model.saveKey(id, value, kind); busy = false; if (saved) value = "" }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TouchField(label, value, { value = it; saved = false }, hint = hint ?: Copy.keyField, secret = true, imeAction = ImeAction.Done, error = model.keyErrors[id] != null || warning != null, onDone = ::submit)
            warning?.let { ProblemNote(it, Modifier.testTag("key-format-$id")) }
            model.keyErrors[id]?.let { ProblemNote(it) }
            if (saved) ReadyNote(Copy.keySaved)
            if (value.isNotBlank()) {
                PrimaryButton(if (warning != null) Copy.checkAnyway else Copy.save, ::submit, Modifier.testTag("key-save-$id"), busy = busy)
            }
        }
    }
}
