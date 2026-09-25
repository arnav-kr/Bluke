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
    fun secondTapUsesTheSameThreeHundredMillisecondWindowThatUiPromises() {
        assertTrue(TouchpadGesturePolicy.isSecondTap(300))
        assertFalse(TouchpadGesturePolicy.isSecondTap(301))
        assertFalse(TouchpadGesturePolicy.isSecondTap(-1))
    }

    @Test
    fun secondTapOnRelativeTouchpadDoesNotDependOnFingerLandingPosition() {
        assertTrue(
            TouchpadGesturePolicy.isSecondTap(elapsedSinceReleaseMillis = 120)
        )
    }
}
