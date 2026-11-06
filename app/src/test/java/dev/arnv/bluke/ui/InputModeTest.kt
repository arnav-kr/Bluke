package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeTest {
    @Test
    fun oldThreeModeDefaultGainsNewModes() {
        val normalized = normalizeInputModeKeys(setOf("keyboard", "touchpad", "gamepad"))

        assertEquals(defaultInputModeKeys, normalized)
        assertTrue(normalized.contains(InputMode.MEDIA_PRESENTATION.preferenceKey))
        assertFalse(normalized.contains("keyboard_touchpad"))
    }

    @Test
    fun deliberateSubsetIsPreserved() {
        assertEquals(
            setOf("keyboard", "touchpad"),
            normalizeInputModeKeys(setOf("keyboard", "touchpad")),
        )
    }

    @Test
    fun invalidOrEmptySelectionFallsBackToKeyboard() {
        assertEquals(setOf("keyboard"), normalizeInputModeKeys(setOf("future_mode")))
    }

    @Test
    fun removedMouseModeMigratesToMediaPresentation() {
        assertEquals(
            setOf("keyboard", "media_presentation"),
            normalizeInputModeKeys(setOf("keyboard", "mouse")),
        )
    }

    @Test
    fun removedKeyboardTouchpadModeFallsBackSafely() {
        assertEquals(
            setOf("keyboard"),
            normalizeInputModeKeys(setOf("keyboard_touchpad")),
        )
        assertEquals(
            setOf("touchpad"),
            normalizeInputModeKeys(setOf("touchpad", "keyboard_touchpad")),
        )
        assertEquals(InputMode.KEYBOARD, InputMode.fromId(3))
    }

    @Test
    fun phoneKeyboardIsInTheDefaultCycle() {
        assertTrue(normalizeInputModeKeys(null).contains(InputMode.PHONE_KEYBOARD.preferenceKey))
        assertEquals(InputMode.PHONE_KEYBOARD, InputMode.fromId(InputMode.PHONE_KEYBOARD.id))
    }

    @Test
    fun untouchedPreviousDefaultGainsPhoneKeyboard() {
        assertEquals(
            defaultInputModeKeys,
            normalizeInputModeKeys(setOf("keyboard", "touchpad", "gamepad", "media_presentation")),
        )
    }

    @Test
    fun previousDefaultIsKeptOnceUpgraded() {
        // After the one-time upgrade, this set means the user unticked Phone Keyboard.
        val withoutPhoneKeyboard = setOf("keyboard", "touchpad", "gamepad", "media_presentation")
        assertEquals(
            withoutPhoneKeyboard,
            normalizeInputModeKeys(withoutPhoneKeyboard, upgradeLegacyDefaults = false),
        )
    }
}
