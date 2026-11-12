package dev.arnv.bluke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchpadScrollTest {
    private fun TwoFingerScrollAxisAssist.total(moves: List<Pair<Float, Float>>, allowDiagonal: Boolean = true): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        moves.forEach { (dx, dy) ->
            val (fx, fy) = filter(dx, dy, allowDiagonal)
            x += fx
            y += fy
        }
        return x to y
    }

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
    fun nothingScrollsUntilTheFingersPassTheStartSlop() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f)
        assertEquals(0f to 0f, assist.filter(2f, 5f))
        assertFalse(assist.isScrolling)
        assertEquals(0f to 0f, assist.project(100f, 100f))
        assist.filter(0f, 8f)
        assertTrue(assist.isScrolling)
    }

    @Test
    fun aMostlyVerticalSwipeStaysVertical() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f, unlockDistancePx = 72f)
        val (x, y) = assist.total(List(40) { 3f to 10f })
        assertEquals(0f, x, 0.0001f)
        // The first two moves only cross the start slop.
        assertEquals(390f, y, 0.0001f)
    }

    @Test
    fun aHorizontalSwipeScrollsSideways() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f, unlockDistancePx = 72f)
        val (x, y) = assist.total(List(10) { -10f to 2f })
        assertEquals(-90f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)
        assertEquals(-500f to 0f, assist.project(-500f, 80f))
    }

    @Test
    fun aDiagonalSwipeScrollsBothWays() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f, unlockDistancePx = 72f)
        val (x, y) = assist.total(List(10) { 8f to 9f })
        assertEquals(80f, x, 0.0001f)
        assertEquals(90f, y, 0.0001f)
    }

    @Test
    fun turningSidewaysUnlocksAStraightSwipe() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f, unlockDistancePx = 72f)
        assist.total(List(10) { 0f to 10f })
        val (x, _) = assist.total(List(20) { 10f to 1f })
        // 72 px of sideways travel are spent unlocking; the rest scrolls.
        assertTrue(x > 100f)
    }

    @Test
    fun withoutDiagonalsEverySwipeKeepsItsAxis() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f, unlockDistancePx = 72f)
        val (x, y) = assist.total(List(10) { 9f to 8f } + List(30) { 0f to 10f }, allowDiagonal = false)
        assertEquals(90f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)
    }

    @Test
    fun resetStartsOverForEachGesture() {
        val assist = TwoFingerScrollAxisAssist(startSlopPx = 12f)
        repeat(5) { assist.filter(0f, 10f) }
        assist.reset()
        assertFalse(assist.isScrolling)
        repeat(5) { assist.filter(10f, 0f) }
        assertEquals(10f to 0f, assist.filter(10f, 4f))
    }

    @Test
    fun momentumGlidesAndSettles() {
        val momentum = ScrollMomentum(decayMillis = 300f, stopSpeedPxPerMs = 0.1f)
        assertTrue(momentum.start(0f, 3000f, minSpeedPxPerSecond = 500f, maxSpeedPxPerSecond = 10_000f))
        var travelled = 0f
        var previousStep = Float.MAX_VALUE
        var frames = 0
        while (momentum.isActive) {
            momentum.advance(12f)
            assertTrue(momentum.stepY <= previousStep)
            assertEquals(0f, momentum.stepX, 0f)
            previousStep = momentum.stepY
            travelled += momentum.stepY
            frames++
        }
        // A speed v that decays over time t covers at most v·t.
        assertTrue(travelled in 800f..900f)
        assertTrue(frames * 12 < 1500)
    }

    @Test
    fun momentumIgnoresASlowLiftAndCapsAFastOne() {
        val momentum = ScrollMomentum(decayMillis = 300f)
        assertFalse(momentum.start(100f, 100f, minSpeedPxPerSecond = 500f, maxSpeedPxPerSecond = 10_000f))
        assertFalse(momentum.isActive)

        assertTrue(momentum.start(40_000f, 0f, minSpeedPxPerSecond = 500f, maxSpeedPxPerSecond = 10_000f))
        momentum.advance(1f)
        assertTrue(momentum.stepX < 10.01f)
        momentum.stop()
        assertFalse(momentum.isActive)
    }

    private class RecordingOutput {
        val events = mutableListOf<String>()
        val output = TouchpadScrollOutput(
            sendScroll = { wheel, pan -> events += "scroll($wheel,$pan)" },
            setShift = { events += if (it) "shift down" else "shift up" },
        )
    }

    @Test
    fun standardOutputSendsWholeStepsOnBothAxes() {
        val recording = RecordingOutput()
        recording.output.add(wheelSteps = 0.3f, panSteps = 0.2f)
        assertFalse(recording.output.flush(TouchpadHorizontalScroll.STANDARD))
        recording.output.add(wheelSteps = 2.2f, panSteps = -1.4f)
        assertTrue(recording.output.flush(TouchpadHorizontalScroll.STANDARD))
        // 2.5 rounds to 3 and -1.2 to -1; the fractions carry over.
        assertEquals(listOf("scroll(3,-1)"), recording.events)
    }

    @Test
    fun shiftWheelOutputHoldsShiftOnlyWhileScrollingSideways() {
        val recording = RecordingOutput()
        val mode = TouchpadHorizontalScroll.SHIFT_WHEEL
        recording.output.add(wheelSteps = 0f, panSteps = 2f)
        recording.output.flush(mode)
        recording.output.add(wheelSteps = 0f, panSteps = 1f)
        recording.output.flush(mode)
        recording.output.add(wheelSteps = -3f, panSteps = 0f)
        recording.output.flush(mode)
        recording.output.add(wheelSteps = 0f, panSteps = -1f)
        recording.output.flush(mode)
        recording.output.end()
        recording.output.end()
        assertEquals(
            listOf(
                "shift down", "scroll(-2,0)", "scroll(-1,0)",
                "shift up", "scroll(-3,0)",
                "shift down", "scroll(1,0)",
                "shift up",
            ),
            recording.events,
        )
    }

    @Test
    fun endingAScrollDropsTheLeftoverFraction() {
        val recording = RecordingOutput()
        recording.output.add(wheelSteps = 0.4f, panSteps = 0f)
        recording.output.end()
        recording.output.add(wheelSteps = 0.4f, panSteps = 0f)
        assertFalse(recording.output.flush(TouchpadHorizontalScroll.STANDARD))
        assertTrue(recording.events.isEmpty())
    }

    @Test
    fun speedsSnapToQuarterSteps() {
        assertEquals(1.25f, snapTouchpadSpeed(1.3f), 0f)
        assertEquals(0.25f, snapTouchpadSpeed(0f), 0f)
        assertEquals(3f, snapTouchpadSpeed(9f), 0f)
    }
}
