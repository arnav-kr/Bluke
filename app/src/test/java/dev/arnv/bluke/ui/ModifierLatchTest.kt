package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierLatchTest {
    private val ctrl = KeyboardLayouts.MOD_LCTRL
    private val shift = KeyboardLayouts.MOD_LSHIFT

    @Test
    fun tapLatchesForTheNextKeyOnly() {
        val latch = ModifierLatch()
        assertTrue("first press reaches the host", latch.onModifierDown(ctrl))
        assertFalse("a tap keeps the modifier down on the host", latch.onModifierUp(ctrl, 0))
        assertEquals(LatchState.ONE_SHOT, latch.state(ctrl))

        latch.onOtherKeyDown()
        assertEquals(listOf(ctrl), latch.onOtherKeyUp())
        assertEquals(LatchState.OFF, latch.state(ctrl))
        assertEquals(emptyList<Int>(), latch.onOtherKeyUp())
    }

    @Test
    fun quickDoubleTapLocksUntilTappedAgain() {
        val latch = ModifierLatch()
        latch.onModifierDown(ctrl)
        latch.onModifierUp(ctrl, 0)
        assertFalse("already down on the host", latch.onModifierDown(ctrl))
        assertFalse(latch.onModifierUp(ctrl, 200))
        assertEquals(LatchState.LOCKED, latch.state(ctrl))

        latch.onOtherKeyDown()
        assertEquals("locked modifiers survive other keys", emptyList<Int>(), latch.onOtherKeyUp())

        latch.onModifierDown(ctrl)
        assertTrue("third tap releases", latch.onModifierUp(ctrl, 5_000))
        assertEquals(LatchState.OFF, latch.state(ctrl))
    }

    @Test
    fun slowSecondTapCancelsTheLatch() {
        val latch = ModifierLatch()
        latch.onModifierDown(ctrl)
        latch.onModifierUp(ctrl, 0)
        latch.onModifierDown(ctrl)
        assertTrue(latch.onModifierUp(ctrl, 1_000))
        assertEquals(LatchState.OFF, latch.state(ctrl))
    }

    @Test
    fun holdingWhilePressingAnotherKeyWorksLikeANormalKeyboard() {
        val latch = ModifierLatch()
        latch.onModifierDown(ctrl)
        latch.onOtherKeyDown()
        assertEquals("held modifiers are not one-shot", emptyList<Int>(), latch.onOtherKeyUp())
        assertTrue("release goes straight to the host", latch.onModifierUp(ctrl, 0))
        assertEquals(LatchState.OFF, latch.state(ctrl))
    }

    @Test
    fun severalTappedModifiersCombine() {
        val latch = ModifierLatch()
        latch.onModifierDown(ctrl)
        latch.onModifierUp(ctrl, 0)
        latch.onModifierDown(shift)
        latch.onModifierUp(shift, 10)
        latch.onOtherKeyDown()
        assertEquals(setOf(ctrl, shift), latch.onOtherKeyUp().toSet())
    }

    @Test
    fun disabledLatchPassesEverythingThrough() {
        val latch = ModifierLatch(enabled = false)
        assertTrue(latch.onModifierDown(ctrl))
        assertTrue(latch.onModifierUp(ctrl, 0))
        assertEquals(LatchState.OFF, latch.state(ctrl))
    }

    @Test
    fun clearReleasesEverythingAndIgnoresLateLifts() {
        val latch = ModifierLatch()
        latch.onModifierDown(ctrl)
        latch.onModifierUp(ctrl, 0)
        latch.onModifierDown(shift)
        assertEquals(setOf(ctrl, shift), latch.clear().toSet())
        assertFalse("a lift after clear() is ignored", latch.onModifierUp(shift, 10))
        assertEquals(LatchState.OFF, latch.state(shift))
        assertTrue(latch.latched.isEmpty())
    }
}
