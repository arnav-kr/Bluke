package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedInputLayoutTest {
    @Test
    fun touchpadFractionIsBoundedForUsablePanels() {
        assertEquals(0.25f, normalizeCombinedTouchpadFraction(0.05f))
        assertEquals(0.42f, normalizeCombinedTouchpadFraction(0.42f))
        assertEquals(0.60f, normalizeCombinedTouchpadFraction(0.95f))
    }

    @Test
    fun defaultKeepsKeyboardLargerThanTouchpad() {
        assertEquals(0.38f, DEFAULT_COMBINED_TOUCHPAD_FRACTION)
    }
}
