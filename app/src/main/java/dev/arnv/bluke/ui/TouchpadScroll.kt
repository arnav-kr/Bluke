package dev.arnv.bluke.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

const val NATURAL_SCROLL_PREFERENCE = "touchpad_natural_scroll"

/** Wheel steps sent per pixel of two-finger movement, before the scroll speed setting. */
private const val SCROLL_STEPS_PER_PIXEL = 0.06f

/** On by default, which is how two-finger scrolling has always behaved. */
fun SharedPreferences.isNaturalScrollEnabled(): Boolean = getBoolean(NATURAL_SCROLL_PREFERENCE, true)

/**
 * Converts a two-finger vertical move (pixels, positive = fingers moved down) into
 * HID wheel steps, where positive scrolls up.
 *
 * Natural: the content follows the fingers, like a phone screen, so moving down scrolls up.
 * Traditional: moving the fingers down scrolls down, like turning a mouse wheel towards you.
 */
internal fun scrollWheelDelta(fingerDeltaY: Float, scrollSpeed: Float, naturalScroll: Boolean): Float {
    val steps = fingerDeltaY * SCROLL_STEPS_PER_PIXEL * scrollSpeed
    return if (naturalScroll) steps else -steps
}

/**
 * Converts a two-finger horizontal move (pixels, positive = fingers moved right) into
 * HID AC Pan steps, where positive scrolls right.
 *
 * Natural: the content follows the fingers, so moving right scrolls left.
 * Traditional: moving the fingers right scrolls right.
 */
internal fun scrollPanDelta(fingerDeltaX: Float, scrollSpeed: Float, naturalScroll: Boolean): Float {
    val steps = fingerDeltaX * SCROLL_STEPS_PER_PIXEL * scrollSpeed
    return if (naturalScroll) -steps else steps
}

/**
 * Keeps a two-finger scroll on one axis, so a slightly diagonal swipe scrolls straight.
 * The axis is chosen once the fingers have travelled [lockDistancePx]; until then each
 * move counts only along its larger direction.
 */
internal class TwoFingerScrollAxisLock(private val lockDistancePx: Float = 24f) {
    private enum class Axis { HORIZONTAL, VERTICAL }

    private var axis: Axis? = null
    private var travelX = 0f
    private var travelY = 0f

    fun reset() {
        axis = null
        travelX = 0f
        travelY = 0f
    }

    /** Returns the part of the move (dx, dy) that should scroll. */
    fun filter(dx: Float, dy: Float): Pair<Float, Float> {
        val locked = axis ?: run {
            travelX += kotlin.math.abs(dx)
            travelY += kotlin.math.abs(dy)
            if (maxOf(travelX, travelY) >= lockDistancePx) {
                axis = if (travelX > travelY) Axis.HORIZONTAL else Axis.VERTICAL
            }
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) Axis.HORIZONTAL else Axis.VERTICAL
        }
        return if (locked == Axis.HORIZONTAL) dx to 0f else 0f to dy
    }
}

/** The natural scroll setting, kept up to date when it changes on a settings screen. */
@Composable
fun rememberNaturalScroll(preferences: SharedPreferences): State<Boolean> {
    val state = remember(preferences) { mutableStateOf(preferences.isNaturalScrollEnabled()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == NATURAL_SCROLL_PREFERENCE) state.value = prefs.isNaturalScrollEnabled()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
