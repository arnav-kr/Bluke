package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteControlPreferencesTest {
    @Test
    fun touchpadSpeedIncludesSlowPrecisionStepsAndWraps() {
        assertEquals(0.5f, nextTouchpadSpeed(0.25f))
        assertEquals(1f, nextTouchpadSpeed(0.5f))
        assertEquals(1.5f, nextTouchpadSpeed(1f))
        assertEquals(0.25f, nextTouchpadSpeed(2.5f))
    }

    @Test
    fun unknownModifierPositionFallsBackToOff() {
        assertEquals(TouchpadModifierPosition.OFF, TouchpadModifierPosition.fromPreference("future"))
        assertEquals(TouchpadModifierPosition.LEFT, TouchpadModifierPosition.OFF.next())
    }
}
