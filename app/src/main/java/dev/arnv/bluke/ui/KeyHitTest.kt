package dev.arnv.bluke.ui

/**
 * Finds the one key a finger is pressing.
 *
 * A touch inside a key picks that key. A touch in the gap between keys picks the
 * nearest key, as long as it is within [touchRadiusPx]. One finger never presses
 * two keys at once.
 */
internal fun findKeyAt(
    keys: List<KeyLayoutInfo>,
    x: Float,
    y: Float,
    unitWidthPx: Float,
    unitHeightPx: Float,
    touchRadiusPx: Float,
): KeyLayoutInfo? {
    var nearest: KeyLayoutInfo? = null
    var nearestDistanceSq = Float.MAX_VALUE
    for (key in keys) {
        val left = unitWidthPx * key.x
        val right = left + unitWidthPx * key.widthRatio
        val top = unitHeightPx * key.y
        val bottom = top + unitHeightPx * key.heightRatio
        val dx = x - x.coerceIn(left, right)
        val dy = y - y.coerceIn(top, bottom)
        val distanceSq = dx * dx + dy * dy
        // "<=" lets later keys win ties, matching how overlapping keys are drawn.
        if (distanceSq <= nearestDistanceSq) {
            nearest = key
            nearestDistanceSq = distanceSq
        }
    }
    return nearest?.takeIf { nearestDistanceSq <= touchRadiusPx * touchRadiusPx }
}
