package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadInputTest {
    @Test
    fun visibleCardinalArmsNeverLeakADiagonal() {
        val size = 114f
        val center = size / 2f
        for (offset in -15..15) {
            assertEquals(1, determineDpadBit(center + offset, center - 30f, size, size))
            assertEquals(8, determineDpadBit(center + 30f, center + offset, size, size))
            assertEquals(2, determineDpadBit(center + offset, center + 30f, size, size))
            assertEquals(4, determineDpadBit(center - 30f, center + offset, size, size))
        }
    }

    @Test
    fun cornersProduceDiagonalsAndCenterIsNeutral() {
        val size = 114f
        assertEquals(0x05, determineDpadBit(10f, 10f, size, size))
        assertEquals(0x09, determineDpadBit(104f, 10f, size, size))
        assertEquals(0x06, determineDpadBit(10f, 104f, size, size))
        assertEquals(0x0A, determineDpadBit(104f, 104f, size, size))
        assertEquals(0, determineDpadBit(57f, 57f, size, size))
    }

    @Test
    fun nonSquareBoundsUseIndependentCenters() {
        assertEquals(1, determineDpadBit(100f, 20f, 200f, 100f))
        assertEquals(8, determineDpadBit(180f, 50f, 200f, 100f))
    }

    @Test
    fun everyReachableTouchProducesAValidMask() {
        val valid = setOf(0, 1, 2, 4, 5, 6, 8, 9, 10)
        for (x in 0..114 step 3) {
            for (y in 0..114 step 3) {
                assertTrue(determineDpadBit(x.toFloat(), y.toFloat(), 114f, 114f) in valid)
            }
        }
    }
}
