package com.sokoul.pc.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.desktop.data.Channel
import com.sokoul.desktop.player.StreamRequest
import com.sokoul.pc.AppDeps
import com.sokoul.pc.Platform
import com.sokoul.pc.data.L
import com.sokoul.pc.playback.session.LiveChannel
import com.sokoul.pc.playback.sources.LocalServerKey
import com.sokoul.pc.shell.LocalNavBarVisible
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.sokoul.pc.ui.focusIfPresent

internal fun SportsFailure.label(): String = when (this) {
    SportsFailure.NotConfigured -> L("Set up the Sokoul server in Settings > Content sources.", "Configurez le serveur Sokoul dans Réglages > Sources de contenu.")
    SportsFailure.VipRequired -> L("This server requires a VIP account for native sports playback.", "Ce serveur exige un compte VIP pour la lecture native du sport.")
    SportsFailure.Unavailable -> L("This sports source is unavailable. Try another server or refresh later.", "Cette source sportive est indisponible. Essayez un autre serveur ou actualisez plus tard.")
    SportsFailure.InvalidResponse -> L("The sports server did not provide a playable native stream.", "Le serveur de sport n’a pas fourni de flux natif lisible.")
}.text()

private val sportsSurface = Color(0xFF101E24)
private val sportsAccent = Color(0xFF85E8CB)
private val sportsGroup = Regex("\\bsports?\\b", RegexOption.IGNORE_CASE)
private data class SportsChannel(val channel: Channel, val airing: String?)

@Composable
internal fun SportsScreen(
    deps: AppDeps,
    first: FocusRequester,
    active: Boolean,
    onPlay: (String, StreamRequest, String, String?, () -> Boolean) -> Unit,
    onLive: (LiveChannel, List<LiveChannel>) -> Unit,
) {
    val source = remember(deps) {
        SportsSources(deps.smallHttp,
            { ProviderConfig.key(deps.db, "mainapi_url") ?: ProviderConfig.MAINAPI_DEFAULT_URL.takeUnless { Platform.IS_TV } },
            { ProviderConfig.key(deps.db, "mainapi") ?: LocalServerKey.read() })
    }
    val profile = deps.profiles.current
    val scope = rememberCoroutineScope()
    val canOpenLive by rememberUpdatedState(active)
    var category by rememberSaveable(profile.id) { mutableStateOf("") }
    var selected by remember(profile.id) { mutableStateOf<SportsEvent?>(null) }
    var lastEvent by remember(profile.id) { mutableStateOf<String?>(null) }
    val eventList = rememberLazyListState()
    val eventFocus = remember { FocusRequester() }
    var catalogRefresh by remember { mutableIntStateOf(0) }
    var serverRefresh by remember { mutableIntStateOf(0) }
    var requested by remember(profile.id) { mutableStateOf<SportsServer?>(null) }
    var resolveFailure by remember(profile.id) { mutableStateOf<SportsFailure?>(null) }

    val catalog by produceState<SportsResult<List<SportsEvent>>?>(null, source, profile.id, catalogRefresh) {
        value = null
        if (!profile.kids) value = source.events()
    }
    val channels by produceState(emptyList<SportsChannel>(), profile.id, catalogRefresh) {
        value = if (profile.kids) emptyList() else withContext(Dispatchers.IO) {
            val sports = deps.tv.groups().asSequence()
                .filter { !it.locked && sportsGroup.containsMatchIn(it.name) }
                .flatMap { deps.tv.channels(it.id).asSequence() }
                .distinctBy { it.nameKey }.toList()
            val programs = deps.tv.programs(sports)
            sports.map { SportsChannel(it, programs[it.id]?.now?.title) }
        }
    }
    val event = selected
    val servers by produceState<SportsResult<List<SportsServer>>?>(null, event, profile.id, serverRefresh) {
        value = null
        if (event != null && !profile.kids) value = source.servers(event)
    }
    val back = {
        requested = null
        resolveFailure = null
        selected = null
    }
    LaunchedEffect(event, requested, profile.id, active) {
        val server = requested ?: return@LaunchedEffect
        if (event == null || !active || profile.kids) return@LaunchedEffect
        val result = source.resolve(event, server)
        if (deps.profiles.current.id != profile.id || deps.profiles.current.kids) return@LaunchedEffect
        requested = null
        resolveFailure = result.failure
        result.value?.let { stream ->
            onPlay("sports:${event.id}:${server.key}", stream, event.name, event.poster) {
                deps.profiles.current.id == profile.id && !deps.profiles.current.kids
            }
        }
    }

    val tab = LocalActiveTab.current
    val focusModifier = Modifier.focusProperties { if (tab != null) up = tab }
    val control = remember { FocusRequester() }
    val openEvent: (SportsEvent) -> Unit = { match ->
        lastEvent = match.id
        selected = match
    }
    LaunchedEffect(event?.id, active) {
        if (active && !Form.isPhone && !Form.tablet) {
            withFrameNanos { }
            if (event != null) runCatching { control.requestFocus() }
            else if (lastEvent == null || !eventFocus.focusIfPresent()) {
                runCatching { first.requestFocus() }
            }
        }
    }
    val margin = Form.pick(Metrics.PageMargin, 32.dp, 24.dp)
    val top = if (LocalNavBarVisible.current && !Form.isPortrait) 118.dp else 36.dp
    val shown = catalog?.value.orEmpty()
    val categories = remember(shown) { shown.map { it.sport }.filter(String::isNotBlank).distinct().sorted() }
    LaunchedEffect(catalog, category) {
        if (catalog?.value != null && category.isNotEmpty() && category !in categories) category = ""
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF122B32), Palette.Background, Palette.Background)))
            .testTag("sports-screen")
            .onPreviewKeyEvent { key ->
                if (event != null && key.type == KeyEventType.KeyDown && key.key == Key.Escape) {
                    back()
                    true
                } else false
            },
    ) {
        if (event == null) LazyColumn(
            Modifier.fillMaxSize().testTag("sports-events"),
            state = eventList,
            contentPadding = PaddingValues(start = margin, end = margin, top = top, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("header") {
                SportsHeading(L("Sports", "Sport").text()) {
                    PillButton(L("Refresh", "Actualiser").text(), { catalogRefresh++ },
                        focusModifier.testTag("sports-refresh"), requester = first)
                }
            }
            if (profile.kids) {
                item { TvPanelNotice(L("Choose an adult profile to browse these external sports sources.", "Choisissez un profil adulte pour consulter ces sources sportives externes.").text()) }
            } else if (catalog == null || catalog?.failure != null) {
                item { TvPanelNotice(catalog?.failure?.label() ?: L("Loading sports…", "Chargement du sport…").text(), error = catalog?.failure != null) }
            } else {
                if (categories.isNotEmpty()) item("filters") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PanelChip(L("All sports", "Tous les sports").text(), Modifier.testTag("sports-category"), selected = category.isEmpty()) { category = "" }
                        categories.forEach { sport -> key(sport) {
                            PanelChip(sport, Modifier.testTag("sports-category-$sport"), selected = category == sport) { category = sport }
                        } }
                    }
                }
                val visible = shown.filter { category.isEmpty() || it.sport == category }
                val live = visible.filter { it.live }
                val upcoming = visible.filterNot { it.live }.sortedBy { it.startsAt ?: Long.MAX_VALUE }
                if (visible.isEmpty()) item { TvPanelNotice(L("No events are listed right now.", "Aucune rencontre n’est annoncée pour le moment.").text()) }
                if (live.isNotEmpty()) {
                    item("live-heading") { Label(L("Live", "En direct").text(), Type.RowTitle) }
                    items(live, key = { it.id }) { match ->
                        SportsEventRow(match, eventFocus.takeIf { match.id == lastEvent }) { openEvent(match) }
                    }
                }
                if (upcoming.isNotEmpty()) {
                    item("upcoming-heading") { Label(L("Upcoming", "À venir").text(), Type.RowTitle, modifier = Modifier.padding(top = 12.dp)) }
                    items(upcoming, key = { it.id }) { match ->
                        SportsEventRow(match, eventFocus.takeIf { match.id == lastEvent }) { openEvent(match) }
                    }
                }
            }
            if (!profile.kids && channels.isNotEmpty()) {
                item("sports-channel-heading") {
                    Label(L("Sports channels", "Chaînes sportives").text(), Type.RowTitle, modifier = Modifier.padding(top = 12.dp))
                }
                items(channels, key = { "channel:${it.channel.id}" }) { (channel, airing) ->
                    TvActionRow(TvCatalog.shortLabel(channel.name), airing, Modifier.testTag("sports-channel-${channel.id}"),
                        leading = { ChannelLogo(channel.name, channel.logo, Modifier.size(88.dp, 48.dp), guideId = channel.epgKey) }) {
                        scope.launch {
                            val live = withContext(Dispatchers.IO) { deps.tv.toLive(channels.map { it.channel }) }
                            if (deps.profiles.current.id == profile.id && !deps.profiles.current.kids && canOpenLive) {
                                live.firstOrNull { it.key == channel.id }?.let { onLive(it, live) }
                            }
                        }
                    }
                }
            }
        } else LazyColumn(
            Modifier.fillMaxSize().testTag("sports-event-details"),
            contentPadding = PaddingValues(start = margin, end = margin, top = top, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("event") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton(L("Back", "Retour").text(), back, focusModifier.testTag("sports-back"), requester = control)
                    SportsEventRow(event, onOpen = null)
                }
            }
            item("server-heading") {
                SportsHeading(L("Servers", "Serveurs").text(), compact = true) {
                    PillButton(L("Refresh", "Actualiser").text(), {
                        if (requested == null) { resolveFailure = null; serverRefresh++ }
                    }, Modifier.testTag("sports-refresh-servers"))
                }
            }
            val error = resolveFailure ?: servers?.failure
            when {
                requested != null -> item { TvPanelNotice(L("Preparing the selected server…", "Préparation du serveur choisi…").text()) }
                servers == null -> item { TvPanelNotice(L("Loading sports…", "Chargement du sport…").text()) }
                else -> {
                    if (error != null) item { TvPanelNotice(error.label(), error = true) }
                    if (servers?.value.isNullOrEmpty() && error == null) item {
                        TvPanelNotice(L("No server is available for this event yet.", "Aucun serveur n’est encore disponible pour cette rencontre.").text())
                    }
                    items(servers?.value.orEmpty(), key = { it.key }) { server ->
                        TvActionRow(server.title, L("Play in Sokoul", "Lire dans Sokoul").text(), Modifier.testTag("sports-server-${server.key}")) {
                            resolveFailure = null
                            requested = server
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsHeading(title: String, compact: Boolean = false, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Label(title, Type.RowTitle.copy(fontSize = if (compact) 34.sp else Form.pick(76.sp, 52.sp)),
            modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun SportsEventRow(event: SportsEvent, requester: FocusRequester? = null, onOpen: (() -> Unit)?) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = requester ?: remember { FocusRequester() }
    val shape = RoundedCornerShape(20.dp)
    val text = if (focused) Palette.OnFocus else Palette.OnBackground
    val time = if (event.live) L("Live", "En direct").text() else event.startsAt?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = Form.pick(122.dp, 100.dp))
            .clip(shape)
            .background(if (focused) Palette.FocusFill else sportsSurface)
            .border(1.dp, if (focused) Color.Transparent else Color(0xFF2B434B), shape)
            .then(if (onOpen == null) Modifier else Modifier.tvFocusable(onFocusChanged = { focused = it }, onClick = onOpen, requester = focusRequester))
            .testTag("sports-event-${event.id}")
            .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (event.poster != null && !Form.isPortrait) {
            Art(event.poster, Modifier.size(136.dp, 84.dp).clip(RoundedCornerShape(10.dp)),
                scale = ContentScale.Crop, placeholder = Color.Transparent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val metadata = listOfNotNull(event.sport.takeIf(String::isNotBlank), time).joinToString(" · ")
            if (metadata.isNotEmpty()) {
                Label(metadata, Type.Meta.copy(fontWeight = FontWeight.SemiBold),
                    if (focused) text else if (event.live) sportsAccent else Palette.Secondary, maxLines = 1)
            }
            Label(event.name, Type.Body.copy(fontWeight = FontWeight.SemiBold), text, maxLines = 2)
        }
        if (onOpen != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(34.dp), tint = text)
    }
}
