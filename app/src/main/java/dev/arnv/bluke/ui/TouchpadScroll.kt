package dev.arnv.bluke.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

const val NATURAL_SCROLL_PREFERENCE = "touchpad_natural_scroll"
const val TOUCHPAD_POINTER_SPEED_PREFERENCE = "touchpad_sensitivity"
const val TOUCHPAD_SCROLL_SPEED_PREFERENCE = "touchpad_scroll_sensitivity"
const val TOUCHPAD_GESTURE_SENSITIVITY_PREFERENCE = "touchpad_gesture_sensitivity"
const val TOUCHPAD_SCROLL_INERTIA_PREFERENCE = "touchpad_scroll_inertia"
const val TOUCHPAD_HORIZONTAL_SCROLL_PREFERENCE = "touchpad_horizontal_scroll"

const val DEFAULT_TOUCHPAD_POINTER_SPEED = 1.5f
const val DEFAULT_TOUCHPAD_SCROLL_SPEED = 1f
const val MIN_TOUCHPAD_SPEED = 0.25f
const val MAX_TOUCHPAD_SPEED = 3f
const val TOUCHPAD_SPEED_STEP = 0.25f

/** Wheel steps sent per pixel of two-finger movement, before the scroll speed setting. */
private const val SCROLL_STEPS_PER_PIXEL = 0.06f

/** On by default, which is how two-finger scrolling has always behaved. */
fun SharedPreferences.isNaturalScrollEnabled(): Boolean = getBoolean(NATURAL_SCROLL_PREFERENCE, true)

/** Snaps a speed to the slider's quarter steps, so it always reads cleanly (1.25x, 1.5x…). */
fun snapTouchpadSpeed(value: Float): Float =
    ((value / TOUCHPAD_SPEED_STEP).roundToInt() * TOUCHPAD_SPEED_STEP).coerceIn(MIN_TOUCHPAD_SPEED, MAX_TOUCHPAD_SPEED)

/** How a sideways two-finger swipe reaches the host. */
enum class TouchpadHorizontalScroll(val preferenceValue: String, val displayName: String) {
    /** The HID "AC Pan" field. Works everywhere once the host has the current pairing. */
    STANDARD("standard", "Standard"),

    /** Holds Shift and turns the wheel, which most apps treat as sideways scrolling. */
    SHIFT_WHEEL("shift_wheel", "Shift + wheel"),
    ;

    fun next(): TouchpadHorizontalScroll = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromPreference(value: String?): TouchpadHorizontalScroll =
            entries.firstOrNull { it.preferenceValue == value } ?: STANDARD
    }
}

/** The touchpad settings that live on the settings screen rather than in a toolbar. */
data class TouchpadSettings(
    val naturalScroll: Boolean = true,
    val scrollInertia: Boolean = true,
    val horizontalScroll: TouchpadHorizontalScroll = TouchpadHorizontalScroll.STANDARD,
    val gestureSensitivity: TouchpadGestureSensitivity = TouchpadGestureSensitivity.MEDIUM,
)

fun SharedPreferences.touchpadSettings() = TouchpadSettings(
    naturalScroll = isNaturalScrollEnabled(),
    scrollInertia = getBoolean(TOUCHPAD_SCROLL_INERTIA_PREFERENCE, true),
    horizontalScroll = TouchpadHorizontalScroll.fromPreference(
        getString(TOUCHPAD_HORIZONTAL_SCROLL_PREFERENCE, null),
    ),
    gestureSensitivity = TouchpadGestureSensitivity.fromPreference(
        getString(TOUCHPAD_GESTURE_SENSITIVITY_PREFERENCE, null),
    ),
)

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
 * Decides which way a two-finger scroll goes, the way a laptop trackpad does:
 *
 * - Nothing scrolls until the fingers have travelled [startSlopPx], so a two-finger
 *   tap never scrolls.
 * - A swipe that starts close to straight is held on that axis, so it doesn't drift.
 * - A swipe that starts diagonally scrolls freely in both directions.
 * - A straight swipe that deliberately turns sideways (by [unlockDistancePx]) becomes free.
 */
internal class TwoFingerScrollAxisAssist(
    private var startSlopPx: Float = 12f,
    private var unlockDistancePx: Float = 72f,
) {
    private enum class Mode { PENDING, HORIZONTAL, VERTICAL, FREE }

    private companion object {
        /** A start within about 27° of an axis counts as straight. */
        const val STRAIGHT_RATIO = 0.5f
    }

    private var mode = Mode.PENDING
    private var netX = 0f
    private var netY = 0f
    private var cross = 0f

    /** True once the fingers have moved far enough to scroll. */
    val isScrolling: Boolean get() = mode != Mode.PENDING

    fun reset(startSlopPx: Float = this.startSlopPx, unlockDistancePx: Float = this.unlockDistancePx) {
        this.startSlopPx = startSlopPx
        this.unlockDistancePx = unlockDistancePx
        mode = Mode.PENDING
        netX = 0f
        netY = 0f
        cross = 0f
    }

    /**
     * Returns the part of the move (dx, dy) that should scroll. With [allowDiagonal]
     * off, every swipe stays on the axis it started on.
     */
    fun filter(dx: Float, dy: Float, allowDiagonal: Boolean = true): Pair<Float, Float> {
        if (mode == Mode.PENDING) {
            netX += dx
            netY += dy
            if (hypot(netX, netY) < startSlopPx) return 0f to 0f
            val horizontal = abs(netX) > abs(netY)
            val major = if (horizontal) abs(netX) else abs(netY)
            val minor = if (horizontal) abs(netY) else abs(netX)
            mode = when {
                allowDiagonal && minor >= major * STRAIGHT_RATIO -> Mode.FREE
                horizontal -> Mode.HORIZONTAL
                else -> Mode.VERTICAL
            }
            return project(dx, dy)
        }
        if (mode == Mode.FREE) return dx to dy

        // Sideways travel counts towards unlocking only while it outpaces the main
        // direction, so a long straight swipe that wanders slightly stays locked.
        val along = if (mode == Mode.HORIZONTAL) dx else dy
        val across = if (mode == Mode.HORIZONTAL) dy else dx
        cross += across
        val leak = abs(along) * STRAIGHT_RATIO
        cross = if (abs(cross) <= leak) 0f else cross - leak * if (cross > 0f) 1f else -1f
        if (allowDiagonal && abs(cross) >= unlockDistancePx) {
            mode = Mode.FREE
            return dx to dy
        }
        return project(dx, dy)
    }

    /** Applies the current lock to a vector, for example the lift-off velocity. */
    fun project(x: Float, y: Float): Pair<Float, Float> = when (mode) {
        Mode.PENDING -> 0f to 0f
        Mode.HORIZONTAL -> x to 0f
        Mode.VERTICAL -> 0f to y
        Mode.FREE -> x to y
    }
}

/**
 * The glide after a two-finger flick. Speed decays exponentially, so the scroll
 * coasts and settles instead of stopping dead when the fingers lift.
 */
internal class ScrollMomentum(
    private val decayMillis: Float = 325f,
    private val stopSpeedPxPerMs: Float = 0.1f,
) {
    private var vx = 0f
    private var vy = 0f

    /** Finger-equivalent travel produced by the latest [advance]. */
    var stepX = 0f
        private set
    var stepY = 0f
        private set

    val isActive: Boolean get() = vx != 0f || vy != 0f

    /** Starts gliding at ([vxPxPerSecond], [vyPxPerSecond]); false if that is too slow to glide. */
    fun start(vxPxPerSecond: Float, vyPxPerSecond: Float, minSpeedPxPerSecond: Float, maxSpeedPxPerSecond: Float): Boolean {
        stop()
        val speed = hypot(vxPxPerSecond, vyPxPerSecond)
        if (speed.isNaN() || speed < minSpeedPxPerSecond) return false
        val scale = if (speed > maxSpeedPxPerSecond) maxSpeedPxPerSecond / speed else 1f
        vx = vxPxPerSecond * scale / 1000f
        vy = vyPxPerSecond * scale / 1000f
        return true
    }

    fun advance(elapsedMillis: Float) {
        if (elapsedMillis <= 0f || !isActive) {
            stepX = 0f
            stepY = 0f
            return
        }
        // Exact distance covered by a speed that decays as v·e^(−t/decay).
        val remaining = exp(-elapsedMillis / decayMillis)
        val travel = decayMillis * (1f - remaining)
        stepX = vx * travel
        stepY = vy * travel
        vx *= remaining
        vy *= remaining
        if (hypot(vx, vy) < stopSpeedPxPerMs) {
            vx = 0f
            vy = 0f
        }
    }

    fun stop() {
        vx = 0f
        vy = 0f
        stepX = 0f
        stepY = 0f
    }
}

/**
 * Collects fractional scroll steps and sends whole ones. In Shift + wheel mode a
 * sideways step is sent as a wheel step with Shift held; [setShift] is only called
 * when the held state changes.
 */
internal class TouchpadScrollOutput(
    private val sendScroll: (wheel: Int, pan: Int) -> Unit,
    private val setShift: (Boolean) -> Unit,
) {
    private var wheelRemainder = 0f
    private var panRemainder = 0f
    private var shiftHeld = false

    fun add(wheelSteps: Float, panSteps: Float) {
        wheelRemainder += wheelSteps
        panRemainder += panSteps
    }

    /** Sends the whole steps collected so far. Returns true if anything was sent. */
    fun flush(horizontalScroll: TouchpadHorizontalScroll): Boolean {
        val wheel = wheelRemainder.roundToInt().coerceIn(-127, 127)
        val pan = panRemainder.roundToInt().coerceIn(-127, 127)
        if (wheel == 0 && pan == 0) return false
        wheelRemainder -= wheel
        panRemainder -= pan
        if (horizontalScroll == TouchpadHorizontalScroll.STANDARD) {
            shift(false)
            sendScroll(wheel, pan)
        } else {
            if (wheel != 0) {
                shift(false)
                sendScroll(wheel, 0)
            }
            if (pan != 0) {
                shift(true)
                // Shift + wheel up scrolls left, so a pan to the right is a wheel down.
                sendScroll(-pan, 0)
            }
        }
        return true
    }

    /** Ends the scroll: drops any fraction left over and lets go of Shift. */
    fun end() {
        wheelRemainder = 0f
        panRemainder = 0f
        shift(false)
    }

    private fun shift(held: Boolean) {
        if (shiftHeld == held) return
        shiftHeld = held
        setShift(held)
    }
}

/** The touchpad settings, kept up to date when they change on a settings screen. */
@Composable
fun rememberTouchpadSettings(preferences: SharedPreferences): State<TouchpadSettings> {
    val state = remember(preferences) { mutableStateOf(preferences.touchpadSettings()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key.startsWith("touchpad_")) state.value = prefs.touchpadSettings()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.value = preferences.touchpadSettings()
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/**
 * A pointer or scroll speed that follows its preference, so the toolbar buttons and
 * the settings screen always agree.
 */
@Composable
fun rememberTouchpadSpeed(preferences: SharedPreferences, key: String, default: Float): MutableFloatState {
    val state = remember(preferences, key) { mutableFloatStateOf(preferences.getFloat(key, default)) }
    DisposableEffect(preferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changed ->
            if (changed == null || changed == key) state.floatValue = prefs.getFloat(key, default)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.floatValue = preferences.getFloat(key, default)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}
