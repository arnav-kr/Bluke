package dev.arnv.bluke.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge keeps no copy of the host's text, so these check that each IME action maps to exactly
 * the keystrokes it stands for - nothing replayed, nothing reconciled.
 */
class PhoneKeyboardBridgeTest {

    private val sent = mutableListOf<Pair<Int, Boolean>>()
    private val dropped = mutableListOf<String>()
    private val latch = ModifierLatch()
    private val bridge = PhoneKeyboardBridge(
        hostLayout = { HostLayouts.DEFAULT },
        unicodeMode = { UnicodeEntry.UnicodeEntryMode.OFF },
        modifiers = latch,
        emit = { sent += it },
        onDropped = { dropped += it },
    )

    private fun taps(): List<Int> = sent.filter { it.second && it.first !in 0xE0..0xE7 }.map { it.first }

    /** Modifiers that are down at the moment [key] is pressed. */
    private fun heldAround(key: Int): List<Int> {
        val held = linkedSetOf<Int>()
        for ((code, press) in sent.subList(0, sent.indexOf(key to true))) {
            if (code !in 0xE0..0xE7) continue
            if (press) held += code else held -= code
        }
        return held.toList()
    }

    @Test
    fun committedTextIsTypedOnce() {
        bridge.typeText("hi")
        assertEquals(listOf(KeyboardLayouts.KEY_H, KeyboardLayouts.KEY_I), taps())
    }

    @Test
    fun deletesAreSentEvenWhenThePhoneNeverSawTheText() {
        // A shell completed "gi" to "git status" on the host; the phone only ever sent "gi".
        // Backspace must still reach the host rather than being swallowed by a stale local copy.
        bridge.typeText("gi")
        sent.clear()
        bridge.deleteBefore(3)
        assertEquals(List(3) { KeyboardLayouts.KEY_BACKSPACE }, taps())
    }

    @Test
    fun forwardDeleteUsesTheDeleteKey() {
        bridge.deleteAfter(1)
        assertEquals(listOf(KeyboardLayouts.KEY_DELETE), taps())
    }

    @Test
    fun everyPressIsReleased() {
        bridge.typeText("Hello, World!")
        bridge.tapKey(KeyboardLayouts.KEY_ENTER, listOf(KeyboardLayouts.MOD_LCTRL))
        val presses = sent.filter { it.second }.groupingBy { it.first }.eachCount()
        val releases = sent.filter { !it.second }.groupingBy { it.first }.eachCount()
        assertEquals(presses, releases)
    }

    @Test
    fun oneShotCtrlAppliesToTheNextCharacterOnly() {
        latch.tap(KeyboardLayouts.MOD_LCTRL)
        bridge.typeText("cd")
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL), heldAround(KeyboardLayouts.KEY_C))
        assertEquals(emptyList<Int>(), heldAround(KeyboardLayouts.KEY_D))
        assertEquals(ModifierLatch.State.OFF, latch.stateOf(KeyboardLayouts.MOD_LCTRL))
    }

    @Test
    fun lockedCtrlAppliesUntilUnlocked() {
        latch.tap(KeyboardLayouts.MOD_LCTRL)
        latch.tap(KeyboardLayouts.MOD_LCTRL)
        bridge.typeText("cd")
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL), heldAround(KeyboardLayouts.KEY_D))
        assertEquals(ModifierLatch.State.LOCKED, latch.stateOf(KeyboardLayouts.MOD_LCTRL))
        latch.tap(KeyboardLayouts.MOD_LCTRL)
        assertEquals(ModifierLatch.State.OFF, latch.stateOf(KeyboardLayouts.MOD_LCTRL))
    }

    @Test
    fun latchedModifierAppliesToKeyBarKeysToo() {
        latch.tap(KeyboardLayouts.MOD_LALT)
        bridge.tapKey(KeyboardLayouts.KEY_TAB)
        assertEquals(listOf(KeyboardLayouts.MOD_LALT), heldAround(KeyboardLayouts.KEY_TAB))
    }

    @Test
    fun latchedShiftIsNotCutShortByAnUppercaseLettersOwnShift() {
        latch.tap(KeyboardLayouts.MOD_LSHIFT)
        bridge.typeText("A")
        // Exactly one press and one release of Shift, wrapping the key.
        val shift = sent.filter { it.first == KeyboardLayouts.MOD_LSHIFT }
        assertEquals(listOf(KeyboardLayouts.MOD_LSHIFT to true, KeyboardLayouts.MOD_LSHIFT to false), shift)
        assertEquals(KeyboardLayouts.MOD_LSHIFT to false, sent.last())
    }

    @Test
    fun untypableCharactersAreReported() {
        bridge.typeText("a😀")
        assertEquals(listOf(KeyboardLayouts.KEY_A), taps())
        assertEquals(listOf("😀"), dropped)
    }

    @Test
    fun rawKeyEventsMapToHostKeys() {
        assertTrue(bridge.onAndroidKey(KeyEvent.KEYCODE_DEL, null))
        assertTrue(bridge.onAndroidKey(KeyEvent.KEYCODE_ENTER, null))
        assertTrue(bridge.onAndroidKey(KeyEvent.KEYCODE_DPAD_LEFT, null))
        assertTrue(bridge.onAndroidKey(KeyEvent.KEYCODE_F5, null))
        assertEquals(
            listOf(
                KeyboardLayouts.KEY_BACKSPACE,
                KeyboardLayouts.KEY_ENTER,
                KeyboardLayouts.KEY_LEFT,
                KeyboardLayouts.KEY_F5,
            ),
            taps(),
        )
    }

    @Test
    fun printableKeyEventsGoThroughTheHostLayout() {
        assertTrue(bridge.onAndroidKey(KeyEvent.KEYCODE_A, 'a'))
        assertEquals(listOf(KeyboardLayouts.KEY_A), taps())
    }

    @Test
    fun ctrlOnAKeyEventIsHeldAroundTheCharacter() {
        assertTrue(bridge.onAndroidKey(KeyEvent.KEYCODE_C, 'c', ctrl = true))
        assertEquals(listOf(KeyboardLayouts.MOD_LCTRL), heldAround(KeyboardLayouts.KEY_C))
    }

    @Test
    fun keyEventsWithNoHostMeaningAreIgnored() {
        assertFalse(bridge.onAndroidKey(KeyEvent.KEYCODE_VOLUME_UP, null))
        assertTrue(sent.isEmpty())
    }
}
