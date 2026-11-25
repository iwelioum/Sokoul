package com.sokoul.pc.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** False while a list is scrolling: a card sliding under a still mouse must not grab focus and fight the scroll. */
val LocalHoverFocus = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * True on pages read with the mouse resting on them (Settings): hovering highlights a row without giving it the focus,
 * so the page neither scrolls to it nor switches category, and the mouse stays in control. A click focuses and activates.
 */
val LocalHoverOnly = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Rows only realign themselves for keyboard moves: a page must not jump because the mouse crossed a card. */
object FocusSource {
    @Volatile var lastWasMouse = false

    /** Set by the real app, which sees real mouse moves. Tests have no such moves, so they leave it off. */
    @Volatile var trackRealMouse = false
    @Volatile private var lastMoveAt = 0L

    fun mouseMoved() {
        lastMoveAt = System.currentTimeMillis()
    }

    /**
     * A page opening or closing under a still mouse also reports "pointer entered": that must not take focus away from
     * the keyboard. Only a pointer that really just moved counts.
     */
    fun pointerReallyMoved() = !trackRealMouse || System.currentTimeMillis() - lastMoveAt < RECENT_MOVE_MS

    private const val RECENT_MOVE_MS = 150L
}

/** The active section's tab, where Up from the top of a page leads. */
val LocalActiveTab = androidx.compose.runtime.staticCompositionLocalOf<FocusRequester?> { null }

/** Moves focus for an arrow key. Returns false when nothing lies that way, so a parent can react (e.g. open the menu). */
fun FocusManager.moveWithArrow(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    FocusSource.lastWasMouse = false
    val direction = when (event.key) {
        Key.DirectionLeft -> FocusDirection.Left
        Key.DirectionRight -> FocusDirection.Right
        Key.DirectionUp -> FocusDirection.Up
        Key.DirectionDown -> FocusDirection.Down
        else -> return false
    }
    return moveFocus(direction)
}

/**
 * Moves focus to this requester and says whether it got there. Since Compose 1.8 a requester whose element is not
 * composed (a row scrolled away, a panel not open yet) no longer throws: requestFocus() only logs, so
 * runCatching { requestFocus() }.isSuccess was always true and the fallback never ran.
 */
fun FocusRequester.focusIfPresent(): Boolean = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

/**
 * One focus state for mouse and keyboard: hovering gives real focus, so survol and arrows share the same
 * highlight and only one element is ever focused. Enter activates like a click.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvFocusable(
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    requester: FocusRequester = remember { FocusRequester() },
    onDeadEnd: ((FocusDirection) -> Unit)? = null,
): Modifier {
    val focusManager = LocalFocusManager.current
    val hoverAllowed = LocalHoverFocus.current
    val hoverOnly = LocalHoverOnly.current
    val click = androidx.compose.runtime.rememberUpdatedState(onClick)
    val report = androidx.compose.runtime.rememberUpdatedState(onFocusChanged)
    // Hover and focus seen by this element, for the highlight of a hover that takes no focus; [2] = the last press was a touch.
    val seen = remember { BooleanArray(3) }
    // A finger never hovers: on a touch screen a row lit by a touch never received "Exit" and stayed lit (settings-redesign D1).
    fun touch(e: androidx.compose.ui.input.pointer.PointerEvent) = e.changes.any { it.type == androidx.compose.ui.input.pointer.PointerType.Touch }
    fun clearHover() { if (seen[0]) { seen[0] = false; if (!seen[1]) report.value(false) } }
    return this
        .onPointerEvent(PointerEventType.Press) { seen[2] = touch(it) }
        .onPointerEvent(PointerEventType.Release) { if (touch(it)) clearHover() }
        .onPointerEvent(PointerEventType.Enter) {
            if (touch(it)) return@onPointerEvent
            if (hoverOnly) {
                if (FocusSource.pointerReallyMoved() && !seen[0]) {
                    FocusSource.lastWasMouse = true
                    seen[0] = true
                    if (!seen[1]) report.value(true)
                }
            } else if (hoverAllowed() && FocusSource.pointerReallyMoved()) {
                FocusSource.lastWasMouse = true
                runCatching { requester.requestFocus() }
            }
        }
        // A pointer already inside when it starts moving again (or entering right after a pause) still highlights.
        .onPointerEvent(PointerEventType.Move) {
            if (touch(it)) { clearHover(); return@onPointerEvent }
            if (hoverOnly && !seen[0]) {
                FocusSource.lastWasMouse = true
                seen[0] = true
                if (!seen[1]) report.value(true)
            }
        }
        .onPointerEvent(PointerEventType.Exit) {
            if (seen[0]) {
                seen[0] = false
                if (!seen[1]) report.value(false)
            }
        }
        .focusRequester(requester)
        .onFocusChanged {
            seen[1] = it.isFocused
            report.value(it.isFocused || seen[0])
        }
        .onKeyEvent { event ->
            when {
                event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                    onClick()
                    true
                }
                else -> focusManager.moveWithArrow(event).also { moved ->
                    // Nothing lies that way: focus stays, and the element says so with a short nudge (Right and Down
                    // only: Left opens the menu and Up goes to the tabs).
                    if (!moved && event.type == KeyEventType.KeyDown && onDeadEnd != null) {
                        when (event.key) {
                            Key.DirectionRight -> onDeadEnd(FocusDirection.Right)
                            Key.DirectionDown -> onDeadEnd(FocusDirection.Down)
                        }
                    }
                }
            }
        }
        .focusable()
        // Not clickable(): it holds a second focus target, and a press on it breaks Compose's focus tree ("ActiveParent with
        // no focused child"), which closes the window.
        .pointerInput(hoverOnly) {
            detectTapGestures(onTap = {
                // Hovering gave no focus: the click does, so the keyboard goes on from the row the mouse chose.
                // A finger is not a mouse: the remote and keyboard keep their own behaviour after a tap.
                // A tap only activates: it takes no focus, and it drops a remote focus left elsewhere (the row a page
                // focused when it opened), so nothing stays lit once the finger is up.
                if (seen[2]) runCatching { focusManager.clearFocus() }
                else if (hoverOnly) {
                    FocusSource.lastWasMouse = true
                    runCatching { requester.requestFocus() }
                }
                click.value()
            })
        }
        .semantics {
            this.onClick {
                click.value()
                true
            }
        }
}

/**
 * The short nudge of an element that cannot move further: it slides a few pixels toward the press and springs back.
 * Read its values in a graphicsLayer the element already has, so that it costs no extra layer.
 */
class Nudge internal constructor(private val scope: kotlinx.coroutines.CoroutineScope, private val px: Float) {
    val x = androidx.compose.animation.core.Animatable(0f)
    val y = androidx.compose.animation.core.Animatable(0f)

    fun kick(direction: FocusDirection) {
        val axis = if (direction == FocusDirection.Right || direction == FocusDirection.Left) x else y
        val sign = if (direction == FocusDirection.Left || direction == FocusDirection.Up) -1f else 1f
        scope.launch {
            axis.animateTo(sign * px, androidx.compose.animation.core.tween(NUDGE_OUT_MS))
            axis.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 900f))
        }
    }
}

@Composable
fun rememberNudge(): Nudge {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { NUDGE_DP.dp.toPx() }
    return remember(scope, px) { Nudge(scope, px) }
}

private const val NUDGE_DP = 10
private const val NUDGE_OUT_MS = 90
