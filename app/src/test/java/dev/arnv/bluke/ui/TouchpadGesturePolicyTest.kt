package dev.arnv.bluke.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadGesturePolicyTest {
    @Test
    fun tapAllowsNormalFingerMotionButRejectsLongPressOrDrag() {
        assertTrue(TouchpadGesturePolicy.isTap(120, distanceSquaredPx = 100f, tapSlopPx = 12f))
        assertFalse(TouchpadGesturePolicy.isTap(250, distanceSquaredPx = 0f, tapSlopPx = 12f))
        assertFalse(TouchpadGesturePolicy.isTap(120, distanceSquaredPx = 145f, tapSlopPx = 12f))
    }

    @Test
    fun secondTapTimeEligibilityIsBounded() {
        assertTrue(TouchpadGesturePolicy.isSecondTap(300))
        assertFalse(TouchpadGesturePolicy.isSecondTap(301))
        assertFalse(TouchpadGesturePolicy.isSecondTap(-1))
    }

    @Test
    fun timingHelperAcceptsAQuickSecondTap() {
        assertTrue(
            TouchpadGesturePolicy.isSecondTap(elapsedSinceReleaseMillis = 120)
        )
    }

    @Test
    fun mediumSensitivityMatchesTheOriginalTapRules() {
        val medium = TouchpadGestureSensitivity.MEDIUM
        assertTrue(medium.tapSlopDp == TouchpadGesturePolicy.TAP_SLOP_DP)
        assertTrue(medium.tapTimeoutMillis == TouchpadGesturePolicy.TAP_TIMEOUT_MILLIS)
        assertTrue(TouchpadGestureSensitivity.fromPreference(null) == medium)
        assertTrue(TouchpadGestureSensitivity.fromPreference("unknown") == medium)
    }

    @Test
    fun higherSensitivityAcceptsSlowerAndLooserTaps() {
        val levels = TouchpadGestureSensitivity.entries
        levels.zipWithNext().forEach { (lower, higher) ->
            assertTrue(higher.tapSlopDp > lower.tapSlopDp)
            assertTrue(higher.tapTimeoutMillis > lower.tapTimeoutMillis)
            assertTrue(higher.scrollStartSlopDp < lower.scrollStartSlopDp)
        }
        val high = TouchpadGestureSensitivity.HIGH
        assertTrue(TouchpadGesturePolicy.isTap(300, 0f, 12f, high.tapTimeoutMillis))
        assertFalse(TouchpadGesturePolicy.isTap(300, 0f, 12f, TouchpadGestureSensitivity.LOW.tapTimeoutMillis))
    }
}
