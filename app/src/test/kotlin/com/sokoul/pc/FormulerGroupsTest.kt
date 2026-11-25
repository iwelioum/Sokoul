package com.sokoul.pc

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.sokoul.desktop.data.LiveLogic
import com.sokoul.pc.live.GroupDrawer
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class FormulerGroupsTest {
    private val groups = (0..60).map { LiveLogic.Group("g$it", "Group $it", false) }

    @Test
    fun browsing_thirty_groups_then_back_never_applies_a_group() = runComposeUiTest {
        val applied = mutableListOf<String>()
        var closed = false
        setContent { GroupDrawer(groups, "g0", Modifier.width(500.dp).height(1080.dp), applied::add, { closed = true }, confirmOnly = true) }
        awaitDrawerFocus()
        repeat(30) { onRoot().performKeyInput { pressKey(Key.DirectionDown) }; waitForIdle() }
        assertTrue(applied.isEmpty())
        onRoot().performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertTrue(closed); assertTrue(applied.isEmpty()) }
    }

    @Test
    fun only_ok_commits_the_highlighted_group_once() = runComposeUiTest {
        val applied = mutableListOf<String>()
        setContent { GroupDrawer(groups, "g0", Modifier.width(500.dp).height(1080.dp), applied::add, {}, confirmOnly = true) }
        awaitDrawerFocus()
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertTrue(applied.isEmpty())
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(listOf("g1"), applied) }
    }

    @Test
    fun confirming_the_active_group_closes_without_reloading() = runComposeUiTest {
        val applied = mutableListOf<String>()
        var closed = false
        setContent { GroupDrawer(groups, "g0", Modifier.width(500.dp).height(1080.dp), applied::add, { closed = true }, confirmOnly = true) }
        awaitDrawerFocus()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertTrue(closed); assertTrue(applied.isEmpty()) }
    }

    // The drawer takes its first focus one frame after it opens: wait for it, as waitForIdle() no longer runs that frame.
    private fun ComposeUiTest.awaitDrawerFocus() = waitUntil(timeoutMillis = 3_000) { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
}
