package dev.arnv.bluke.ui

object TouchpadGesturePolicy {
    const val TAP_TIMEOUT_MILLIS = 250L
    const val DOUBLE_TAP_TIMEOUT_MILLIS = 300L
    const val TAP_SLOP_DP = 12f
    const val DOUBLE_TAP_SLOP_DP = 48f

    fun isTap(durationMillis: Long, distanceSquaredPx: Float, tapSlopPx: Float): Boolean =
        durationMillis in 0 until TAP_TIMEOUT_MILLIS &&
            distanceSquaredPx <= tapSlopPx * tapSlopPx

    fun isSecondTap(
        elapsedSinceReleaseMillis: Long,
        distanceSquaredPx: Float,
        doubleTapSlopPx: Float,
    ): Boolean = elapsedSinceReleaseMillis in 0..DOUBLE_TAP_TIMEOUT_MILLIS &&
        distanceSquaredPx <= doubleTapSlopPx * doubleTapSlopPx
}
