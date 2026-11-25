package com.sokoul.pc.live

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.desktop.data.SpecialGroup
import com.sokoul.pc.data.L
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.TvPanelNotice
import com.sokoul.pc.ui.tvFocusable
import com.sokoul.pc.ui.focusIfPresent

internal val TV_COLUMN_WIDTH = 690.dp

/**
 * The channel groups, as the Apple TV app's sidebar: a short title and a real search field on top, the three ways to see
 * channels (all, favourites, recent) always in sight, then one list that scrolls with its section title kept on top: the
 * owner's collections, then the provider's groups with their number. The "⋯" of a group shows on the focused line only.
 * Back closes on the TV; a touch screen has a close button. (Owner, 2026-11-24: the sections looked alike, the shortcuts
 * vanished as the list scrolled to the group shown, and a "⋯" on every line was only noise.)
 */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun TvGroupColumn(
    groups: List<LiveLogic.Group>,
    selected: String?,
    modifier: Modifier,
    onApply: (String) -> Unit,
    onGroupActions: (String) -> Unit,
    onNewCollection: () -> Unit,
    onManageCollection: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editingQuery by remember { mutableStateOf(false) }
    val all = groups.firstOrNull { it.id == SpecialGroup.ALL }
    val favorites = groups.firstOrNull { it.id == SpecialGroup.FAVORITES }
    val recent = groups.firstOrNull { it.id == TvCatalog.RECENT }
    val collections = groups.filter { TvPersonalization.collectionId(it.id) != null }
    val provider = groups.filter {
        it.id != SpecialGroup.ALL && it.id != SpecialGroup.FAVORITES && it.id != TvCatalog.RECENT && TvPersonalization.collectionId(it.id) == null
    }
    val found = if (query.isBlank()) emptyList() else groups.filter { GuideLogic.matches(columnGroupName(it), query) }
    val list = rememberLazyListState()
    val search = remember { FocusRequester() }
    val activeGroup = remember { FocusRequester() }
    var focusedGroup by remember { mutableStateOf<String?>(selected) }
    val television = com.sokoul.pc.Platform.IS_TV && !com.sokoul.pc.ui.Form.tablet && !com.sokoul.pc.ui.Form.isPhone
    val shortcuts = listOfNotNull(all, favorites, recent)
    // The row of the group shown in the scrolling list: title, collections, "new collection", title, provider groups.
    val sectionTitlePx = with(androidx.compose.ui.platform.LocalDensity.current) { SECTION_TITLE_HEIGHT.roundToPx() }
    val listIndex = remember(collections, provider, selected) {
        buildList { add("h:mine"); addAll(collections.map { it.id }); add("new"); add("h:provider"); addAll(provider.map { it.id }) }.indexOf(selected)
    }
    LaunchedEffect(editingQuery, query, listIndex) {
        if (editingQuery) return@LaunchedEffect
        // The group shown right under its section title, which stays on top: nothing half hidden behind it.
        if (query.isBlank() && listIndex > 0) list.scrollToItem(listIndex, -sectionTitlePx)
        withFrameNanos { }
        // A touch screen has no focus to show: nothing lit before a finger chooses (the search field looked selected).
        if (com.sokoul.pc.ui.Form.tablet || com.sokoul.pc.ui.Form.isPhone) return@LaunchedEffect
        val restore = television && query.isBlank() && selected != null
        if (!restore || !activeGroup.focusIfPresent()) runCatching { search.requestFocus() }
    }
    fun actions(group: LiveLogic.Group) {
        val id = TvPersonalization.collectionId(group.id)
        if (id != null) onManageCollection(id) else onGroupActions(group.id)
    }
    Column(
        modifier.background(FormulerGuideStyle.sheet, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 22.dp).testTag("tv-drawer")
            .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val group = groups.firstOrNull { it.id == focusedGroup }
                when {
                    e.key == Key.Escape && editingQuery -> { editingQuery = false; true }
                    e.key == Key.Escape && query.isNotEmpty() -> { query = ""; true }
                    e.key == Key.Escape -> { onClose(); true }
                    e.key == Key.Menu && group != null && group.id != SpecialGroup.ALL -> { actions(group); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(L("Channels", "Chaînes").text(), Type.RowTitle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).testTag("tv-drawer-title"), maxLines = 1)
            if (!television) RoundIcon(Icons.Filled.Close, L("Close", "Fermer").text(), Modifier.testTag("tv-groups-close")) { onClose() }
        }
        if (editingQuery) {
            TvTextKeyboard(query, { query = it }, { editingQuery = false }, Modifier.fillMaxWidth(), limit = 64)
            return@Column
        }
        SearchField(query, search, onOpen = { editingQuery = true }, onClear = { query = "" })
        if (query.isNotBlank()) {
            if (found.isEmpty()) TvPanelNotice(L("No matching group. Back clears the search.", "Aucun groupe trouvé. Retour efface la recherche.").text())
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(found, key = { it.id }) { g ->
                    GroupLine(g, g.id == selected, focusedGroup == g.id, television, null, { focusedGroup = g.id }, { onApply(g.id) }) { actions(g) }
                }
            }
            return@Column
        }
        // Always in sight: how to see the channels, whatever group the list has scrolled to.
        shortcuts.forEach { g ->
            val icon = when (g.id) {
                SpecialGroup.FAVORITES -> Icons.Filled.Star
                TvCatalog.RECENT -> Icons.Filled.History
                else -> Icons.Filled.LiveTv
            }
            GroupLine(g, g.id == selected, focusedGroup == g.id, television, icon, { focusedGroup = g.id }, { onApply(g.id) },
                requester = if (g.id == selected) activeGroup else null, actions = null)
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Palette.Hairline))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
            verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            stickyHeader("h:mine") { SectionTitle(L("My collections", "Mes collections").text(), collections.size.takeIf { it > 0 }) }
            items(collections, key = { it.id }) { g ->
                GroupLine(g, g.id == selected, focusedGroup == g.id, television, Icons.Filled.Folder, { focusedGroup = g.id }, { onApply(g.id) },
                    requester = if (g.id == selected) activeGroup else null) { actions(g) }
            }
            item("new") {
                com.sokoul.pc.ui.PanelOption(L("New collection…", "Nouvelle collection…").text(), Modifier.testTag("tv-column-new-collection"),
                    leading = { ink -> Icon(Icons.Filled.Add, null, tint = ink, modifier = Modifier.size(28.dp)) }, onClick = onNewCollection)
            }
            if (provider.isNotEmpty()) {
                stickyHeader("h:provider") { SectionTitle(L("Provider groups", "Groupes du fournisseur").text(), provider.size) }
                items(provider, key = { it.id }) { g ->
                    GroupLine(g, g.id == selected, focusedGroup == g.id, television, null, { focusedGroup = g.id }, { onApply(g.id) },
                        requester = if (g.id == selected) activeGroup else null) { actions(g) }
                }
            }
        }
    }
}

/** A real field to type in, not a chip: magnifier, the text or its hint, and a way to clear it. */
@Composable
private fun SearchField(query: String, requester: FocusRequester, onOpen: () -> Unit, onClear: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) Palette.FocusFill else FormulerGuideStyle.surface, tween(140))
    val ink = if (focused) Palette.OnFocus else Palette.Secondary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(30.dp)).background(fill)
                .tvFocusable(onFocusChanged = { focused = it }, onClick = onOpen, requester = requester)
                .testTag("tv-groups-search").padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = ink, modifier = Modifier.size(28.dp))
            Label(query.ifBlank { L("Search a group", "Rechercher un groupe").text() }, Type.Body.copy(fontSize = 25.sp),
                if (query.isBlank() || focused) ink else Palette.OnBackground, Modifier.weight(1f), maxLines = 1)
        }
        if (query.isNotBlank()) RoundIcon(Icons.Filled.Close, L("Clear the search", "Effacer la recherche").text(), Modifier.testTag("tv-groups-clear"), onClear)
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) Palette.FocusFill else FormulerGuideStyle.surface, tween(140))
    Box(
        modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(fill)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (focused) Palette.OnFocus else Palette.OnBackground, modifier = Modifier.size(28.dp)) }
}

private val SECTION_TITLE_HEIGHT = 56.dp

@Composable
private fun SectionTitle(text: String, count: Int?) {
    // Opaque: it stays on top while the rows scroll under it.
    Row(
        Modifier.fillMaxWidth().height(SECTION_TITLE_HEIGHT).background(FormulerGuideStyle.sheet).padding(start = 12.dp, end = 12.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text.uppercase(), Type.Body.copy(fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp), FormulerGuideStyle.secondary,
            Modifier.weight(1f), maxLines = 1)
        count?.let { Label(it.toString(), Type.Body.copy(fontSize = 21.sp), FormulerGuideStyle.muted, maxLines = 1) }
    }
}

/** One group: its name, a check on the group shown; its "⋯" beside it only while it has the focus (always on touch). */
@Composable
private fun GroupLine(
    group: LiveLogic.Group,
    current: Boolean,
    focusedHere: Boolean,
    television: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    requester: FocusRequester? = null,
    actions: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        com.sokoul.pc.ui.PanelOption(
            columnGroupName(group),
            Modifier.weight(1f).onFocusChanged { if (it.hasFocus) onFocus() }.testTag("tv-group-${group.id}"),
            selected = current,
            requester = requester ?: remember { FocusRequester() },
            // Every line keeps the icon's room, empty for a provider group: one text edge down the whole column.
            leading = { ink -> if (icon != null) Icon(icon, null, tint = ink, modifier = Modifier.size(28.dp)) },
            onClick = onClick,
        )
        if (actions != null && (focusedHere || !television)) TvGroupActions(group, onFocus, actions)
    }
}

@Composable
private fun TvGroupActions(group: LiveLogic.Group, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) Palette.FocusFill else androidx.compose.ui.graphics.Color.Transparent, tween(140))
    val label = if (TvPersonalization.collectionId(group.id) == null) L("Select channels", "Sélectionner des chaînes").text()
        else L("Edit collection", "Gérer la collection").text()
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).background(fill)
            .tvFocusable(onFocusChanged = { focused = it; if (it) onFocus() }, onClick = onClick)
            .testTag("tv-group-actions-${group.id}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.MoreHoriz, "$label · ${columnGroupName(group)}", tint = if (focused) Palette.OnFocus else FormulerGuideStyle.secondary)
    }
}

/** Provider groups often come in capitals: normalise their casing for display only. */
internal fun columnGroupName(g: LiveLogic.Group): String = when (g.id) {
    SpecialGroup.FAVORITES -> L("Favorites", "Favoris").text()
    SpecialGroup.ALL -> L("All channels", "Toutes les chaînes").text()
    TvCatalog.RECENT -> L("Recent channels", "Chaînes récentes").text()
    else -> TvCatalog.label(g.name).let { name ->
        if (name.any { it.isLowerCase() }) name
        else name.lowercase().split(' ').joinToString(" ") { word ->
            if (word in setOf("tv", "hd", "uhd", "fhd", "4k")) word.uppercase()
            else word.replaceFirstChar { it.titlecase() }
        }
    }
}
