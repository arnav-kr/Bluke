package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroMouseMotionTest {
    @Test
    fun remapsSensorAxesForLandscapeDisplays() {
        assertEquals(-2f to 1f, remapToScreenAxes(1f, 2f, displayRotation = 1))
        assertEquals(2f to -1f, remapToScreenAxes(1f, 2f, displayRotation = 3))
    }

    @Test
    fun emitsRelativeMotionOnEightMillisecondCadence() {
        val motion = GyroMouseMotion()
        assertNull(motion.addSample(0f, 1f, 1_000_000_000L, 0, 1f))
        val delta = motion.addSample(0f, 1f, 1_008_000_000L, 0, 1f)

        assertTrue(delta != null)
        assertTrue(delta!!.x < 0)
        assertEquals(0, delta.y)
    }

    @Test
    fun filtersStationarySensorNoise() {
        val motion = GyroMouseMotion()
        assertNull(motion.addSample(0.01f, 0.01f, 1_000_000_000L, 0, 2.5f))
        assertNull(motion.addSample(0.01f, 0.01f, 1_010_000_000L, 0, 2.5f))
    }
}
