package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadScrollTest {
    @Test
    fun naturalScrollKeepsTheOriginalDirection() {
        // Fingers moving down send a positive wheel value, which scrolls up (content follows the fingers).
        assertEquals(6f, scrollWheelDelta(fingerDeltaY = 100f, scrollSpeed = 1f, naturalScroll = true), 0.0001f)
        assertTrue(scrollWheelDelta(-100f, 1f, naturalScroll = true) < 0f)
    }

    @Test
    fun traditionalScrollReversesDirectionOnly() {
        listOf(-250f, -3f, 0f, 40f, 300f).forEach { delta ->
            listOf(1f, 1.5f, 2.5f).forEach { speed ->
                assertEquals(
                    -scrollWheelDelta(delta, speed, naturalScroll = true),
                    scrollWheelDelta(delta, speed, naturalScroll = false),
                    0.0001f,
                )
            }
        }
    }

    @Test
    fun scrollSpeedScalesTheDistance() {
        assertEquals(
            2.5f * scrollWheelDelta(100f, 1f, naturalScroll = false),
            scrollWheelDelta(100f, 2.5f, naturalScroll = false),
            0.0001f,
        )
    }

    @Test
    fun naturalHorizontalScrollFollowsTheFingers() {
        // Fingers moving right drag the content right, which scrolls left (negative pan).
        assertEquals(-6f, scrollPanDelta(fingerDeltaX = 100f, scrollSpeed = 1f, naturalScroll = true), 0.0001f)
        assertEquals(6f, scrollPanDelta(fingerDeltaX = 100f, scrollSpeed = 1f, naturalScroll = false), 0.0001f)
    }

    @Test
    fun axisLockKeepsAMostlyVerticalSwipeVertical() {
        val lock = TwoFingerScrollAxisLock(lockDistancePx = 24f)
        val moves = List(10) { 3f to 10f } + List(5) { 8f to 2f }
        val filtered = moves.map { (dx, dy) -> lock.filter(dx, dy) }
        assertEquals(0f, filtered.sumOf { it.first.toDouble() }.toFloat(), 0.0001f)
        assertEquals(100f + 10f, filtered.sumOf { it.second.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun axisLockAllowsHorizontalSwipes() {
        val lock = TwoFingerScrollAxisLock(lockDistancePx = 24f)
        val filtered = List(10) { 10f to 2f }.map { (dx, dy) -> lock.filter(dx, dy) }
        assertEquals(100f, filtered.sumOf { it.first.toDouble() }.toFloat(), 0.0001f)
        assertEquals(0f, filtered.sumOf { it.second.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun axisLockStartsOverForEachGesture() {
        val lock = TwoFingerScrollAxisLock(lockDistancePx = 24f)
        repeat(5) { lock.filter(0f, 10f) }
        lock.reset()
        repeat(5) { lock.filter(10f, 0f) }
        assertEquals(10f to 0f, lock.filter(10f, 9f))
    }
}
