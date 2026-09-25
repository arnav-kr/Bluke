package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeTest {
    @Test
    fun oldThreeModeDefaultGainsNewModes() {
        val normalized = normalizeInputModeKeys(setOf("keyboard", "touchpad", "gamepad"))

        assertEquals(defaultInputModeKeys, normalized)
        assertTrue(normalized.contains(InputMode.KEYBOARD_TOUCHPAD.preferenceKey))
        assertTrue(normalized.contains(InputMode.MEDIA_PRESENTATION.preferenceKey))
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
}
