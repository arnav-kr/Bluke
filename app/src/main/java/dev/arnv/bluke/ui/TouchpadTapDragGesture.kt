package dev.arnv.bluke.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The delayed-click/tap-drag sequence from main, isolated for report-order tests. */
internal class TouchpadTapDragGesture(
    private val scope: CoroutineScope,
    private val sendButton: (Byte) -> Unit,
    private val vibrate: (Long) -> Unit,
) {
    companion object {
        // Preserve main's single-click delay and second-landing tolerance.
        const val SINGLE_CLICK_DELAY_MILLIS = 180L
        private const val SECOND_TAP_DISTANCE_SQUARED = 10_000f
    }

    private var pendingClick: Job? = null
    private var lastReleaseTime: Long? = null
    private var lastX = 0f
    private var lastY = 0f
    var isArmed = false
        private set
    private var moved = false

    fun down(timeMillis: Long, x: Float, y: Float) {
        val release = lastReleaseTime
        val dx = x - lastX
        val dy = y - lastY
        val secondTap = release != null && TouchpadGesturePolicy.isSecondTap(timeMillis - release) &&
            dx * dx + dy * dy < SECOND_TAP_DISTANCE_SQUARED
        pendingClick?.cancel()
        pendingClick = null
        lastReleaseTime = null
        // An unrelated landing must not silently swallow the pending first click.
        if (release != null && !secondTap) click()
        isArmed = secondTap
        moved = false
        if (secondTap) vibrate(15)
    }

    fun queueClick(timeMillis: Long, x: Float, y: Float) {
        pendingClick?.cancel()
        lastReleaseTime = timeMillis
        lastX = x
        lastY = y
        pendingClick = scope.launch {
            delay(SINGLE_CLICK_DELAY_MILLIS)
            click()
            pendingClick = null
            lastReleaseTime = null
        }
    }

    /** Call before the movement report, so button-down precedes the first drag delta. */
    fun move(): Boolean {
        if (!isArmed) return false
        if (!moved) {
            sendButton(1)
            moved = true
        }
        return true
    }

    fun release(): Boolean {
        if (!isArmed) return false
        if (moved) {
            sendButton(0)
            vibrate(15)
        } else {
            click()
            click()
            vibrate(20)
        }
        isArmed = false
        moved = false
        return true
    }

    fun cancel() {
        pendingClick?.cancel()
        pendingClick = null
        lastReleaseTime = null
        if (moved) sendButton(0)
        isArmed = false
        moved = false
    }

    private fun click() {
        sendButton(1)
        sendButton(0)
    }
}
