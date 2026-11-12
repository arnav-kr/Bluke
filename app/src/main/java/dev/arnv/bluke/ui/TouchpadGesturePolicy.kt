package dev.arnv.bluke.ui

/**
 * How readily the touchpad recognises taps and the start of a two-finger scroll.
 * Medium is the behaviour the touchpad has always had.
 */
enum class TouchpadGestureSensitivity(
    val preferenceValue: String,
    val displayName: String,
    val description: String,
    /** How far a finger may move and still count as a tap. */
    val tapSlopDp: Float,
    /** How long a finger may stay down and still count as a tap. */
    val tapTimeoutMillis: Long,
    /** How far two fingers must travel before scrolling starts. */
    val scrollStartSlopDp: Float,
) {
    LOW("low", "Low", "Needs crisp, deliberate taps and swipes. Best if you get accidental clicks.", 8f, 180L, 8f),
    MEDIUM("medium", "Medium", "Balanced taps and scrolling.", 12f, 250L, 4f),
    HIGH("high", "High", "Picks up light or slow taps and starts scrolling sooner.", 18f, 350L, 2f),
    ;

    fun next(): TouchpadGestureSensitivity = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromPreference(value: String?): TouchpadGestureSensitivity =
            entries.firstOrNull { it.preferenceValue == value } ?: MEDIUM
    }
}

object TouchpadGesturePolicy {
    const val TAP_TIMEOUT_MILLIS = 250L
    const val DOUBLE_TAP_TIMEOUT_MILLIS = 300L
    const val TAP_SLOP_DP = 12f

    fun isTap(
        durationMillis: Long,
        distanceSquaredPx: Float,
        tapSlopPx: Float,
        tapTimeoutMillis: Long = TAP_TIMEOUT_MILLIS,
    ): Boolean =
        durationMillis in 0 until tapTimeoutMillis &&
            distanceSquaredPx <= tapSlopPx * tapSlopPx

    // Time eligibility only; the tap-drag sequencer also checks main's landing tolerance.
    fun isSecondTap(elapsedSinceReleaseMillis: Long): Boolean =
        elapsedSinceReleaseMillis in 0..DOUBLE_TAP_TIMEOUT_MILLIS
}
