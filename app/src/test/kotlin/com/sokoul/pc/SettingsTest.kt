package com.sokoul.pc

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.sokoul.desktop.data.AccentColor
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.SubtitleBackground
import com.sokoul.desktop.data.SubtitleColor
import com.sokoul.desktop.data.SubtitleFont
import com.sokoul.desktop.data.SubtitleSize
import com.sokoul.pc.data.HomeLayout
import com.sokoul.pc.data.PageKind
import com.sokoul.pc.data.PagePlans
import com.sokoul.pc.library.HomeRowsScreen
import com.sokoul.pc.library.ServiceInfo
import com.sokoul.pc.library.SettingsHooks
import com.sokoul.pc.library.SettingsScreen
import com.sokoul.pc.theme.AccentTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** The settings page used as a person would: pick a category, click a row, and the choice is kept. */
class SettingsTest {
    private lateinit var file: File
    private lateinit var db: DesktopDb
    private lateinit var prefs: DesktopPrefs
    private var changes = 0
    private var cleared = 0
    private var forgotten = 0
    private var erased = 0
    private var reset = 0
    private val menu = listOf("home" to "Home", "library" to "My list", "movies" to "Movies")

    private var audioOpened = 0

    private fun hooks() = SettingsHooks(
        menu, { changes++ },
        services = { listOf(ServiceInfo("TMDB", true, "Built-in key"), ServiceInfo("OMDb", false, null)) },
        onClearCache = { cleared++ }, onClearHistory = { erased++ }, onResetDisplay = { reset++ },
        onForgetSourceFailures = { forgotten++ },
        onAudioLanguages = { audioOpened++ },
    )

    @Before
    fun open() {
        file = File.createTempFile("settings", ".db")
        db = DesktopDb(file)
        prefs = DesktopPrefs(db)
        changes = 0
        // Tools shows the last starts: other tests' plays must not lengthen the page this class scrolls through.
        com.sokoul.pc.playback.session.StartLog.open { null }
    }

    @After
    fun close() {
        AccentTheme.color = androidx.compose.ui.graphics.Color.White
        db.close()
        file.delete()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.page() {
        setContent { com.sokoul.pc.ui.ScaledRoot { SettingsScreen(FocusRequester(), prefs, onCredentials = {}, hooks = hooks()) } }
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.pane(id: String) {
        onNodeWithTag("settings-pane-$id").performClick()
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.click(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    /** A choice opens its list; the option at [index] is pressed and the list closes (settings-redesign D4). */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.choose(tag: String, index: Int) {
        click(tag)
        onNodeWithTag("$tag-option-$index").performClick()
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun playbackShowsTheWantedLanguagesAndOpensTheirEditor() = runComposeUiTest {
        prefs.audioIntent = com.sokoul.desktop.data.AudioIntent(listOf("nl", com.sokoul.desktop.data.AudioIntent.ORIGINAL))
        page()
        pane("playback")
        onNodeWithText(com.sokoul.pc.library.audioIntentSummary(prefs.audioIntent), substring = true).assertExists()
        click("pref-audio")
        assertEquals(1, audioOpened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun searchServicesKeepAtLeastOneServiceEnabled() = runComposeUiTest {
        com.sokoul.pc.i18n.Strings.applyLanguage("en")
        val torrentio = com.sokoul.iptv.data.debrid.SourceAddon.TORRENTIO
        assertEquals(com.sokoul.iptv.data.debrid.SourceAddon.entries.toSet(), prefs.searchServices)
        page()
        pane("playback")
        click("pref-service-comet")
        assertEquals(setOf(torrentio), prefs.searchServices)
        assertEquals("TORRENTIO", db.setting(com.sokoul.desktop.data.DesktopDebrid.SERVICES))
        click("pref-service-torrentio")
        assertEquals(setOf(torrentio), prefs.searchServices)
        val rowText = onNodeWithTag("pref-service-torrentio").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty()
        assertTrue("the last service still reads On", rowText.any { it.text == "On" })
        prefs.searchServices = emptySet()
        assertEquals(setOf(torrentio), prefs.searchServices)
        assertEquals(setOf(torrentio), DesktopPrefs(db).searchServices)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun audioLanguagesAreAddedMovedAndRemovedLikeHomeRows() = runComposeUiTest {
        com.sokoul.pc.i18n.Strings.applyLanguage("fr")
        prefs.audioIntent = com.sokoul.desktop.data.AudioIntent(listOf("fr", com.sokoul.desktop.data.AudioIntent.ORIGINAL))
        setContent { com.sokoul.pc.ui.ScaledRoot { com.sokoul.pc.library.AudioLanguagesScreen(prefs) {} } }
        waitForIdle()
        assertEquals(com.sokoul.desktop.data.AudioFallback.EngineDefault, prefs.audioIntent.fallback)
        click("audio-language-fallback")
        assertEquals(com.sokoul.desktop.data.AudioFallback.Ask, prefs.audioIntent.fallback)
        // Spanish is added at the end, then moved to the top: the first one a version offers is played.
        click("audio-language-add-es")
        assertEquals(listOf("fr", "original", "es"), prefs.audioIntent.languages)
        click("audio-language-up-es")
        click("audio-language-up-es")
        assertEquals(listOf("es", "fr", "original"), prefs.audioIntent.languages)
        // Dutch too, and French removed: any language, in any order.
        click("audio-language-add-nl")
        click("audio-language-remove-fr")
        assertEquals(listOf("es", "original", "nl"), prefs.audioIntent.languages)
        // The last language cannot be removed: something must say what to play.
        click("audio-language-remove-es")
        click("audio-language-remove-nl")
        click("audio-language-remove-original")
        assertEquals(listOf("original"), prefs.audioIntent.languages)
        assertEquals(com.sokoul.desktop.data.AudioFallback.Ask, prefs.audioIntent.fallback)
        click("audio-language-fallback")
        assertEquals(com.sokoul.desktop.data.AudioFallback.EngineDefault, prefs.audioIntent.fallback)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun subtitlesAreOffAutomaticOrOneLanguage() = runComposeUiTest {
        page()
        pane("languages")
        assertEquals("auto", prefs.audioIntent.subtitles)
        choose("pref-subtitles", 2 + com.sokoul.pc.library.AUDIO_LANGUAGE_CHOICES.indexOf("fr"))
        assertEquals("fr", prefs.audioIntent.subtitles)
        choose("pref-subtitles", 2 + com.sokoul.pc.library.AUDIO_LANGUAGE_CHOICES.indexOf("en"))
        assertEquals("en", prefs.audioIntent.subtitles)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accountsShowEveryServiceAndTheWayToChangeThem() = runComposeUiTest {
        page()
        pane("sources")
        onNodeWithTag("service-TMDB").assertExists()
        onNodeWithTag("service-OMDb").assertExists()
        onNodeWithTag("settings-credentials").assertExists()
        onNodeWithTag("settings-title").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accentIsPickedFromSwatchesAndKept() = runComposeUiTest {
        page()
        pane("display")
        click("accent-red")
        assertEquals(AccentColor.RED, prefs.accent)
        assertEquals(AccentColor.RED.argb.toInt(), AccentTheme.color.toArgb())
        assertTrue(changes > 0)
        click("accent-blue")
        assertEquals(AccentColor.BLUE, prefs.accent)
        assertEquals(AccentColor.BLUE.argb.toInt(), AccentTheme.color.toArgb())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aChoiceListOpensOnTheSavedValueAndBackLeavesItUnchanged() = runComposeUiTest {
        page()
        pane("languages")
        click("pref-sub-size")
        onNodeWithTag("pref-sub-size-list").assertExists()
        onNodeWithTag("pref-sub-size-option-${SubtitleSize.LARGE.ordinal}").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Escape) }
        waitForIdle()
        onNodeWithTag("pref-sub-size-list").assertDoesNotExist()
        assertEquals(SubtitleSize.NORMAL, prefs.subtitleSize)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun subtitleStyleIsChosenAndReset() = runComposeUiTest {
        page()
        pane("languages")
        choose("pref-sub-size", SubtitleSize.LARGE.ordinal)
        choose("pref-sub-color", SubtitleColor.YELLOW.ordinal)
        choose("pref-sub-background", SubtitleBackground.BOX.ordinal)
        choose("pref-sub-font", SubtitleFont.ATKINSON.ordinal)
        assertEquals(SubtitleSize.LARGE, prefs.subtitleSize)
        assertEquals(SubtitleColor.YELLOW, prefs.subtitleColor)
        assertEquals(SubtitleBackground.BOX, prefs.subtitleBackground)
        assertEquals(SubtitleFont.ATKINSON, prefs.subtitleFont)

        click("pref-sub-reset")
        assertEquals(SubtitleSize.NORMAL, prefs.subtitleSize)
        assertEquals(SubtitleColor.WHITE, prefs.subtitleColor)
        assertEquals(SubtitleBackground.OUTLINE, prefs.subtitleBackground)
        assertEquals(SubtitleFont.STANDARD, prefs.subtitleFont)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun menuEntriesCanBeHiddenButNotAll() = runComposeUiTest {
        page()
        pane("display")
        click("pref-menu-library")
        click("pref-menu-movies")
        assertEquals(setOf("library", "movies"), prefs.railHidden)
        // The last one stays, whatever is clicked.
        click("pref-menu-home")
        assertEquals(setOf("library", "movies"), prefs.railHidden)
        // And a hidden one comes back.
        click("pref-menu-library")
        assertFalse("library" in prefs.railHidden)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun maintenanceActionsAskTwiceBeforeDoingAnything() = runComposeUiTest { maintenance() }

    /** With eight starts listed above it, the maintenance group sits below the fold and has to be scrolled to. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun maintenanceWorksBelowALongStartLog() {
        val log = File.createTempFile("start-log", ".txt").apply { deleteOnExit() }
        log.writeText((1..8).joinToString("\n") { "## 25/11 20:0$it · Title $it\n+0 ms play movie $it\n+900 ms first image" })
        com.sokoul.pc.playback.session.StartLog.open { log }
        runComposeUiTest { maintenance() }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.maintenance() {
        page()
        pane("tools")
        // Bring the whole maintenance group into view while the clock runs: a page made longer by the start log above
        // otherwise had to scroll with the clock frozen, and the scroll waited for ever.
        onNodeWithTag("about-reset-display").performScrollTo()
        waitForIdle()
        // The test clock would run the four seconds the confirmation waits for while a click settles: it is held still.
        mainClock.autoAdvance = false
        fun press(tag: String) {
            onNodeWithTag(tag).performClick()
            mainClock.advanceTimeBy(300)
        }
        press("about-clear-history")
        assertEquals("one press only arms the action", 0, erased)
        press("about-clear-history")
        assertEquals(1, erased)
        press("about-clear-cache")
        press("about-clear-cache")
        assertEquals(1, cleared)
        press("about-forget-failures")
        assertEquals(0, forgotten)
        press("about-forget-failures")
        assertEquals(1, forgotten)
        press("about-reset-display")
        assertEquals(0, reset)
        press("about-reset-display")
        assertEquals(1, reset)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theHomeRowsAreHiddenMovedAndRestored() = runComposeUiTest {
        val plan = PagePlans.of(PageKind.Home)
        val ids = plan.map { it.id }
        setContent { com.sokoul.pc.ui.ScaledRoot { HomeRowsScreen(prefs, onChanged = { changes++ }, onBack = {}) } }
        waitForIdle()

        click("home-row-${ids[0]}")
        assertEquals(setOf(ids[0]), HomeLayout.decode(prefs.homeRowsJson).hidden)

        click("home-row-down-${ids[1]}")
        val shown = HomeLayout.decode(prefs.homeRowsJson).apply(plan).map { it.id }
        assertEquals(listOf(ids[2], ids[1]), shown.take(2))

        click("home-rows-reset")
        assertTrue(HomeLayout.decode(prefs.homeRowsJson).isDefault)
        assertTrue(changes >= 3)
    }

    @Test
    fun resetDisplayGivesBackTheFirstLook() {
        prefs.accent = AccentColor.PINK
        prefs.railHidden = setOf("tv")
        prefs.homeRowsJson = HomeLayout().toggle("moods").encode()
        prefs.subtitleSize = SubtitleSize.HUGE
        prefs.screensaverMinutes = 30
        prefs.resetDisplay()
        assertEquals(AccentColor.WHITE, prefs.accent)
        assertTrue(prefs.railHidden.isEmpty())
        assertEquals(null, prefs.homeRowsJson)
        assertEquals(SubtitleSize.NORMAL, prefs.subtitleSize)
        assertEquals(5, prefs.screensaverMinutes)
    }

    @Test
    fun theLanguageChoiceAndPromptAreKept() {
        assertEquals("auto", prefs.language)
        prefs.language = "fr"
        assertEquals("fr", prefs.language)
        prefs.language = "auto"
        assertEquals("auto", prefs.language)
        assertTrue(prefs.profilePrompt)
        prefs.profilePrompt = false
        assertFalse(DesktopPrefs(db).profilePrompt)
    }
}
