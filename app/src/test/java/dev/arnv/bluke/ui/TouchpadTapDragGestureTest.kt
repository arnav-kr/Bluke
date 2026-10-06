package dev.arnv.bluke.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TouchpadTapDragGestureTest {
    @Test fun singleClickWaitsForMainDelay() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.queueClick(100, 20f, 20f)
        advanceTimeBy(179)
        assertTrue(reports.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf<Byte>(1, 0), reports)
    }

    @Test fun tapHoldAndDragDoesNotSendAnInitialClickOrEarlyRelease() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.queueClick(100, 20f, 20f)
        advanceTimeBy(100)
        gesture.down(200, 22f, 22f)
        advanceTimeBy(1_000) // Holding the second touch must cancel the queued click.
        assertTrue(reports.isEmpty())
        assertTrue(gesture.move())
        assertEquals(listOf<Byte>(1), reports)
        assertTrue(gesture.move())
        assertEquals(listOf<Byte>(1), reports)
        assertTrue(gesture.release())
        assertEquals(listOf<Byte>(1, 0), reports)
    }

    @Test fun doubleTapWithoutMovementSendsTwoCompleteClicks() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.queueClick(100, 20f, 20f)
        advanceTimeBy(100)
        gesture.down(200, 20f, 20f)
        assertTrue(gesture.release())
        advanceTimeBy(1_000)
        assertEquals(listOf<Byte>(1, 0, 1, 0), reports)
    }

    @Test fun landingAfterClickHasFiredDoesNotArmDrag() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.queueClick(100, 20f, 20f)
        advanceTimeBy(181)
        gesture.down(281, 20f, 20f)
        assertFalse(gesture.move())
        assertFalse(gesture.release())
        assertEquals(listOf<Byte>(1, 0), reports)
    }

    @Test fun distantSecondLandingDoesNotDragOrLoseFirstClick() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.queueClick(100, 20f, 20f)
        gesture.down(150, 200f, 200f)
        assertFalse(gesture.move())
        advanceTimeBy(1_000)
        assertEquals(listOf<Byte>(1, 0), reports)
    }

    @Test fun cancellationDropsDeferredClickAndReleasesActiveDrag() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.queueClick(100, 20f, 20f)
        gesture.cancel()
        advanceTimeBy(1_000)
        assertTrue(reports.isEmpty())
        gesture.queueClick(1_100, 20f, 20f)
        gesture.down(1_150, 20f, 20f)
        assertTrue(gesture.move())
        gesture.cancel()
        gesture.cancel()
        assertEquals(listOf<Byte>(1, 0), reports)
        assertFalse(gesture.release())
    }

    @Test fun movementWithoutFirstTapDoesNotPressMouseButton() = runTest {
        val reports = mutableListOf<Byte>()
        val gesture = TouchpadTapDragGesture(this, reports::add, {})
        gesture.down(100, 20f, 20f)
        assertFalse(gesture.move())
        assertFalse(gesture.release())
        assertTrue(reports.isEmpty())
    }
}
