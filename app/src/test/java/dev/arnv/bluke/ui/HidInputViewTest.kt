package dev.arnv.bluke.ui

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real InputConnection the way soft keyboards do.
 *
 * The earlier text-field design replayed text when the IME rewrote or re-committed a word; these
 * pin down that each committed character reaches the host exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HidInputViewTest {

    private val sent = mutableListOf<Pair<Int, Boolean>>()
    private val composing = mutableListOf<String>()
    private var keystrokes = 0
    private var pastes = 0

    private val connection: InputConnection
    private val editorInfo = EditorInfo()

    init {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = PhoneKeyboardBridge(
            hostLayout = { HostLayouts.DEFAULT },
            unicodeMode = { UnicodeEntry.UnicodeEntryMode.OFF },
            modifiers = ModifierLatch(),
            emit = { sent += it },
        )
        val view = HidInputView(
            context,
            bridge,
            onComposingChanged = { composing += it },
            onKeystroke = { keystrokes++ },
            onPaste = { pastes++ },
        )
        connection = view.onCreateInputConnection(editorInfo)
    }

    private fun taps(): List<Int> = sent.filter { it.second && it.first !in 0xE0..0xE7 }.map { it.first }

    private fun keys(text: String): List<Int> = text.map { KeyboardLayouts.KEY_A + (it - 'a') }

    @Test
    fun asksTheImeForRawInput() {
        assertEquals(InputType.TYPE_NULL, editorInfo.inputType)
    }

    @Test
    fun keyboardPasteGoesToTheClipboardSendAfterAnyComposingWord() {
        connection.setComposingText("hi", 1)
        assertEquals(true, connection.performContextMenuAction(android.R.id.paste))
        assertEquals(1, pastes)
        assertEquals(keys("hi"), taps())

        // Nothing local to cut, copy or select.
        assertEquals(false, connection.performContextMenuAction(android.R.id.copy))
        assertEquals(1, pastes)
    }

    @Test
    fun commitIsSentImmediately() {
        connection.commitText("a", 1)
        connection.commitText("b", 1)
        assertEquals(keys("ab"), taps())
    }

    @Test
    fun composingTextIsHeldUntilCommittedThenSentOnce() {
        connection.setComposingText("he", 1)
        connection.setComposingText("hel", 1)
        assertEquals(emptyList<Int>(), taps())
        assertEquals("hel", composing.last())

        // The IME replaces its composing word with the final one.
        connection.commitText("help", 1)
        connection.finishComposingText()
        assertEquals(keys("help"), taps())
        assertEquals("", composing.last())
    }

    @Test
    fun finishingAComposingWordSendsItOnce() {
        connection.setComposingText("hi", 1)
        connection.finishComposingText()
        connection.finishComposingText()
        assertEquals(keys("hi"), taps())
    }

    @Test
    fun deletingInsideAComposingWordStaysLocal() {
        connection.setComposingText("hel", 1)
        connection.deleteSurroundingText(1, 0)
        assertEquals(emptyList<Int>(), taps())
    }

    @Test
    fun deletingWithNothingLocalBackspacesTheHost() {
        connection.commitText("ab", 1)
        sent.clear()
        connection.deleteSurroundingText(2, 0)
        assertEquals(List(2) { KeyboardLayouts.KEY_BACKSPACE }, taps())
    }

    @Test
    fun keyEventsFireOnPressOnly() {
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        assertEquals(listOf(KeyboardLayouts.KEY_BACKSPACE), taps())
        assertEquals(1, keystrokes)
    }

    @Test
    fun keyEventFlushesComposingTextFirst() {
        connection.setComposingText("ab", 1)
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        assertEquals(keys("ab") + KeyboardLayouts.KEY_ENTER, taps())
    }

    @Test
    fun editorActionSendsEnter() {
        connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
        assertEquals(listOf(KeyboardLayouts.KEY_ENTER), taps())
    }
}
