package dev.arnv.bluke.ui

import kotlin.math.abs
import kotlin.math.roundToInt

data class MouseDelta(val x: Int, val y: Int)

class GyroMouseMotion {
    companion object {
        const val REPORT_INTERVAL_NANOS = 8_000_000L
        const val MAX_SAMPLE_INTERVAL_NANOS = 50_000_000L
        const val PIXELS_PER_RADIAN = 900f
        const val ANGULAR_DEAD_ZONE_RADIANS_PER_SECOND = 0.015f
    }

    private var previousTimestampNanos = 0L
    private var lastReportTimestampNanos = 0L
    private var accumulatedX = 0f
    private var accumulatedY = 0f

    @Synchronized
    fun addSample(
        angularVelocityX: Float,
        angularVelocityY: Float,
        timestampNanos: Long,
        displayRotation: Int,
        sensitivity: Float,
    ): MouseDelta? {
        if (previousTimestampNanos == 0L) {
            previousTimestampNanos = timestampNanos
            lastReportTimestampNanos = timestampNanos
            return null
        }

        val elapsedNanos = (timestampNanos - previousTimestampNanos)
            .coerceIn(0L, MAX_SAMPLE_INTERVAL_NANOS)
        previousTimestampNanos = timestampNanos
        if (elapsedNanos == 0L) return null

        val (screenX, screenY) = remapToScreenAxes(
            angularVelocityX,
            angularVelocityY,
            displayRotation,
        )
        val filteredX = screenX.withDeadZone()
        val filteredY = screenY.withDeadZone()
        val seconds = elapsedNanos / 1_000_000_000f

        // Turning around the screen's vertical axis moves horizontally; pitching around
        // its horizontal axis moves vertically.
        accumulatedX += -filteredY * seconds * PIXELS_PER_RADIAN * sensitivity
        accumulatedY += -filteredX * seconds * PIXELS_PER_RADIAN * sensitivity

        if (timestampNanos - lastReportTimestampNanos < REPORT_INTERVAL_NANOS) return null
        lastReportTimestampNanos = timestampNanos

        val deltaX = accumulatedX.roundToInt().coerceIn(-127, 127)
        val deltaY = accumulatedY.roundToInt().coerceIn(-127, 127)
        accumulatedX -= deltaX
        accumulatedY -= deltaY
        return if (deltaX == 0 && deltaY == 0) null else MouseDelta(deltaX, deltaY)
    }

    @Synchronized
    fun reset() {
        previousTimestampNanos = 0L
        lastReportTimestampNanos = 0L
        accumulatedX = 0f
        accumulatedY = 0f
    }

    private fun Float.withDeadZone(): Float =
        if (abs(this) < ANGULAR_DEAD_ZONE_RADIANS_PER_SECOND) 0f else this
}

internal fun remapToScreenAxes(x: Float, y: Float, displayRotation: Int): Pair<Float, Float> =
    when (displayRotation) {
        1 -> -y to x
        2 -> -x to -y
        3 -> y to -x
        else -> x to y
    }
