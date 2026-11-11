package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyHitTestTest {
    private val left = KeyLayoutInfo(legend = "A", x = 0f, keyCode = KeyboardLayouts.KEY_A)
    private val right = KeyLayoutInfo(legend = "B", x = 1.1f, keyCode = KeyboardLayouts.KEY_B)
    private val keys = listOf(left, right)

    private fun hit(x: Float, y: Float = 50f, radius: Float = 20f) =
        findKeyAt(keys, x, y, unitWidthPx = 100f, unitHeightPx = 100f, touchRadiusPx = radius)

    @Test
    fun touchInsideAKeyPicksIt() {
        assertEquals(left, hit(50f))
        assertEquals(right, hit(150f))
    }

    @Test
    fun touchInTheGapPicksOnlyTheNearestKey() {
        assertEquals(left, hit(103f))
        assertEquals(right, hit(108f))
    }

    @Test
    fun touchOutsideTheRadiusPicksNothing() {
        assertNull(hit(-30f))
        assertNull(hit(50f, y = 130f))
    }

    @Test
    fun keysCanBeTallerThanTheyAreWide() {
        val tall = findKeyAt(keys, 50f, 140f, unitWidthPx = 100f, unitHeightPx = 150f, touchRadiusPx = 0f)
        assertEquals(left, tall)
    }
}
