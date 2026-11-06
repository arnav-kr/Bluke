package dev.arnv.bluke.ui

import dev.arnv.bluke.ui.KeystrokeEcho.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class KeystrokeEchoTest {

    private val echo = KeystrokeEcho(maxEntries = 4, maxTextLength = 8)
    private val ctrl = listOf(KeyboardLayouts.MOD_LCTRL)
    private val shift = listOf(KeyboardLayouts.MOD_LSHIFT)

    @Test
    fun plainTypingCollectsIntoOneRun() {
        "ls -la".forEach { echo.typed(it.toString(), emptyList()) }
        assertEquals(listOf(Entry.Text("ls -la")), echo.entries)
    }

    @Test
    fun longRunsKeepTheNewestText() {
        "abcdefghij".forEach { echo.typed(it.toString(), emptyList()) }
        assertEquals(listOf(Entry.Text("cdefghij")), echo.entries)
    }

    @Test
    fun shiftedTextStaysText() {
        echo.typed("a", shift)
        assertEquals(listOf(Entry.Text("A")), echo.entries)
    }

    @Test
    fun ctrlTurnsACharacterIntoAShortcut() {
        echo.typed("c", ctrl)
        assertEquals(listOf(Entry.Key("Ctrl+C")), echo.entries)
    }

    @Test
    fun modifiersAreNamedInDesktopOrder() {
        val mods = listOf(KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_LALT, KeyboardLayouts.MOD_LCTRL)
        echo.pressed(KeyboardLayouts.KEY_DELETE, mods)
        assertEquals(listOf(Entry.Key("Ctrl+Alt+Shift+Del")), echo.entries)
    }

    @Test
    fun repeatedKeysCollapseIntoACount() {
        repeat(3) { echo.pressed(KeyboardLayouts.KEY_BACKSPACE, emptyList()) }
        assertEquals(listOf(Entry.Key("⌫", count = 3)), echo.entries)
    }

    @Test
    fun keysSplitTextRuns() {
        echo.typed("a", emptyList())
        echo.pressed(KeyboardLayouts.KEY_ENTER, emptyList())
        echo.typed("b", emptyList())
        assertEquals(listOf(Entry.Text("a"), Entry.Key("⏎"), Entry.Text("b")), echo.entries)
    }

    @Test
    fun oldEntriesFallOff() {
        listOf(KeyboardLayouts.KEY_UP, KeyboardLayouts.KEY_DOWN, KeyboardLayouts.KEY_LEFT,
            KeyboardLayouts.KEY_RIGHT, KeyboardLayouts.KEY_ESC).forEach { echo.pressed(it, emptyList()) }
        assertEquals(listOf("↓", "←", "→", "Esc"), echo.entries.map { (it as Entry.Key).label })
    }

    @Test
    fun bridgeReportsWhatItSends() {
        val latch = ModifierLatch()
        val bridge = PhoneKeyboardBridge(
            hostLayout = { HostLayouts.DEFAULT },
            unicodeMode = { UnicodeEntry.UnicodeEntryMode.OFF },
            modifiers = latch,
            emit = {},
            echo = echo,
        )
        bridge.typeText("hi")
        latch.tap(KeyboardLayouts.MOD_LCTRL)
        bridge.typeText("c")
        bridge.tapKey(KeyboardLayouts.KEY_ENTER)
        assertEquals(listOf(Entry.Text("hi"), Entry.Key("Ctrl+C"), Entry.Key("⏎")), echo.entries)
    }

    @Test
    fun pasteShowsTheTextOnOneLine() {
        echo.pasted("ls\r\ncd\tx")
        assertEquals(listOf(Entry.Pasted("ls⏎cd⇥x", length = 8, truncated = false)), echo.entries)
    }

    @Test
    fun longPasteShowsItsStartAndFullLength() {
        val echo = KeystrokeEcho(maxPastePreview = 4)
        echo.pasted("password123")
        assertEquals(listOf(Entry.Pasted("pass…", length = 11, truncated = true)), echo.entries)
    }

    @Test
    fun typingAfterAPasteStartsItsOwnRun() {
        echo.pasted("abc")
        echo.typed("d", emptyList())
        assertEquals(Entry.Text("d"), echo.entries.last())
    }
}
