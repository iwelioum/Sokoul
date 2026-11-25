package com.sokoul.pc.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.pc.data.HomeLayout
import com.sokoul.pc.data.L
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.data.PagePlans
import com.sokoul.pc.theme.Metrics
import com.sokoul.pc.theme.Palette
import com.sokoul.pc.theme.Type
import com.sokoul.pc.ui.Label
import com.sokoul.pc.ui.PillButton
import com.sokoul.pc.ui.SectionPill
import com.sokoul.pc.ui.tvFocusable

/**
 * The home page's rows as a list the user arranges ("Rangées de l'accueil"): OK shows or hides a row, the two round
 * buttons beside it move it up or down, and the last button goes back to the default page. Every change is kept at once.
 */
@Composable
fun HomeRowsScreen(prefs: DesktopPrefs, onChanged: () -> Unit, onBack: () -> Unit) {
    val plan = remember { PagePlans.of(PageKind.Home) }
    var layout by remember { mutableStateOf(HomeLayout.decode(prefs.homeRowsJson)) }
    val change = { next: HomeLayout ->
        layout = next
        prefs.homeRowsJson = next.encode()
        onChanged()
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Palette.Background).testTag("home-rows")
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onBack(); true } else false },
    ) {
        SectionPill(Icons.Filled.ViewList, L("Home rows", "Rangées de l'accueil").text(), onClick = onBack, modifier = Modifier.padding(start = 40.dp, top = 58.dp), onChevron = onBack)
        Label(
            L("OK shows or hides a row; the arrows beside it change its place.", "OK affiche ou cache une rangée ; les flèches à côté changent sa place.").text(),
            Type.Meta, Palette.Secondary, Modifier.offset(x = 80.dp, y = 150.dp).width(560.dp),
        )
        Column(
            Modifier.offset(x = Metrics.SettingsColumnX - 160.dp).width(Metrics.SettingsColumnWidth + 160.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 90.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.RowGap),
        ) {
            val rows = layout.listed(plan)
            rows.forEachIndexed { i, row ->
                val hidden = row.id in layout.hidden
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowToggle(HomeLayout.label(row), hidden, if (i == 0) first else remember { FocusRequester() }, Modifier.weight(1f).testTag("home-row-${row.id}")) { change(layout.toggle(row.id)) }
                    MoveButton(Icons.Filled.KeyboardArrowUp, i > 0, "home-row-up-${row.id}") { change(layout.move(plan, row.id, -1)) }
                    MoveButton(Icons.Filled.KeyboardArrowDown, i < rows.lastIndex, "home-row-down-${row.id}") { change(layout.move(plan, row.id, 1)) }
                }
            }
            Box(Modifier.padding(top = 16.dp)) {
                PillButton(L("Restore the default order", "Rétablir l'ordre par défaut").text(), { change(HomeLayout()) }, Modifier.testTag("home-rows-reset"))
            }
        }
    }
}

/** A row's name, and whether it is hidden. */
@Composable
private fun RowToggle(name: String, hidden: Boolean, requester: FocusRequester, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.RowFill)
    val text = if (focused) Palette.OnFocus else if (hidden) Palette.Secondary else Palette.OnBackground
    Row(
        modifier.height(Metrics.RowHeight).clip(RoundedCornerShape(50)).background(fill)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = onClick, requester = requester).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(name, Type.Button.copy(fontSize = Type.Button.fontSize * 0.96f), text, Modifier.weight(1f), maxLines = 1)
        Label(
            if (hidden) L("Hidden", "Cachée").text() else L("Shown", "Affichée").text(),
            Type.Button.copy(fontSize = Type.Button.fontSize * 0.96f), if (focused) Palette.OnFocus.copy(alpha = 0.6f) else Palette.Secondary,
        )
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(if (focused) Palette.FocusFill else Palette.ButtonFill)
    Box(
        Modifier.size(Metrics.RowHeight).clip(CircleShape).background(fill).testTag(tag)
            .tvFocusable(onFocusChanged = { focused = it }, onClick = { if (enabled) onClick() }, requester = remember { FocusRequester() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (focused) Palette.OnFocus else if (enabled) Palette.OnBackground else Color(0x55FFFFFF), modifier = Modifier.size(36.dp))
    }
}
