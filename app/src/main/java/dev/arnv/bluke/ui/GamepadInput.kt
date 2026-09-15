package dev.arnv.bluke.ui

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

internal fun determineDpadBit(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): Int {
    val minimumDimension = min(width, height)
    val dx = x - width / 2f
    val dy = y - height / 2f
    if (sqrt(dx * dx + dy * dy) < minimumDimension * 0.08f) return 0

    val cardinalHalfWidth = minimumDimension * 0.15f
    val horizontal = when {
        dx < -cardinalHalfWidth -> 4
        dx > cardinalHalfWidth -> 8
        else -> 0
    }
    val vertical = when {
        dy < -cardinalHalfWidth -> 1
        dy > cardinalHalfWidth -> 2
        else -> 0
    }

    if (horizontal != 0 && vertical != 0) return horizontal or vertical
    if (horizontal != 0) return horizontal
    if (vertical != 0) return vertical

    return if (abs(dx) > abs(dy)) {
        if (dx > 0) 8 else 4
    } else {
        if (dy > 0) 2 else 1
    }
}
