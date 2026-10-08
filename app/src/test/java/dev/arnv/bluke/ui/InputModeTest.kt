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
}
