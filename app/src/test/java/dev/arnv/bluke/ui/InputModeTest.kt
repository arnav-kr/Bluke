package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeTest {
    @Test
    fun oldThreeModeDefaultGainsCombinedMode() {
        val normalized = normalizeInputModeKeys(setOf("keyboard", "touchpad", "gamepad"))

        assertEquals(defaultInputModeKeys, normalized)
        assertTrue(normalized.contains(InputMode.KEYBOARD_TOUCHPAD.preferenceKey))
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
}
