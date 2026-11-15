package dev.arnv.bluke.ui

import android.content.SharedPreferences

import android.content.pm.ActivityInfo

const val KEYBOARD_ORIENTATION_PREFERENCE = "keyboard_orientation"
const val PORTRAIT_KEYBOARD_LAYOUT_PREFERENCE = "portrait_keyboard_layout"
const val KEY_PREVIEW_PREFERENCE = "key_preview_enabled"

/** How the keyboard screen picks its rotation. */
enum class KeyboardOrientation(val preferenceValue: String, val displayName: String) {
    AUTO("auto", "Follow phone rotation"),
    PORTRAIT("portrait", "Always portrait"),
    LANDSCAPE("landscape", "Always landscape");

    val activityOrientation: Int
        get() = when (this) {
            AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

    companion object {
        fun fromPreference(value: String?): KeyboardOrientation =
            entries.firstOrNull { it.preferenceValue == value } ?: AUTO
    }
}

/** Which layout the keyboard uses when the phone is upright. */
enum class PortraitKeyboardLayout(val preferenceValue: String, val displayName: String) {
    TOUCH("touch", "Touch layout (bigger keys)"),
    SAME_AS_LANDSCAPE("same", "Same as landscape");

    companion object {
        fun fromPreference(value: String?): PortraitKeyboardLayout =
            entries.firstOrNull { it.preferenceValue == value } ?: TOUCH
    }
}

const val KEYBOARD_STYLE_PREFERENCE = "keyboard_style"
const val KEYBOARD_TRACKPAD_PREFERENCE = "keyboard_show_trackpad"
const val KEYBOARD_LAPTOP_LAYOUT_PREFERENCE = "keyboard_laptop_layout"
const val KEYBOARD_LAPTOP_TRACKPAD_PREFERENCE = "keyboard_laptop_trackpad_position"

/** Where the trackpad sits in the laptop layout: always across the full width, never beside. */
enum class LaptopTrackpadPosition(val preferenceValue: String, val displayName: String) {
    ABOVE("above", "Above the keyboard"),
    BELOW("below", "Below the keyboard (like a laptop)");

    fun toggled(): LaptopTrackpadPosition = if (this == ABOVE) BELOW else ABOVE

    companion object {
        fun fromPreference(value: String?): LaptopTrackpadPosition =
            entries.firstOrNull { it.preferenceValue == value } ?: BELOW
    }
}

/** Narrowest screen, in dp, that fits the full layout next to or above a trackpad. */
const val LAPTOP_LAYOUT_MIN_WIDTH_DP = 600

/** True when the screen is wide enough for the laptop layout (tablets, unfolded foldables). */
fun isWideEnoughForLaptopLayout(screenWidthDp: Int): Boolean = screenWidthDp >= LAPTOP_LAYOUT_MIN_WIDTH_DP

private const val KEYBOARD_HEIGHT_PORTRAIT_PREFERENCE = "keyboard_height_portrait"
private const val KEYBOARD_HEIGHT_LANDSCAPE_PREFERENCE = "keyboard_height_landscape"

/** Smallest and largest keyboard height, as a share of the screen height. */
const val MIN_KEYBOARD_HEIGHT_FRACTION = 0.15f
const val MAX_KEYBOARD_HEIGHT_FRACTION = 0.9f

/**
 * Keyboard height the user set by dragging its handle, as a share of the screen height,
 * kept separately for portrait and landscape. Null means the automatic height.
 */
fun SharedPreferences.keyboardHeightFraction(portrait: Boolean): Float? {
    val key = if (portrait) KEYBOARD_HEIGHT_PORTRAIT_PREFERENCE else KEYBOARD_HEIGHT_LANDSCAPE_PREFERENCE
    return if (contains(key)) {
        getFloat(key, 0.5f).coerceIn(MIN_KEYBOARD_HEIGHT_FRACTION, MAX_KEYBOARD_HEIGHT_FRACTION)
    } else {
        null
    }
}

fun SharedPreferences.setKeyboardHeightFraction(portrait: Boolean, fraction: Float?) {
    val key = if (portrait) KEYBOARD_HEIGHT_PORTRAIT_PREFERENCE else KEYBOARD_HEIGHT_LANDSCAPE_PREFERENCE
    val editor = edit()
    if (fraction == null) editor.remove(key) else editor.putFloat(key, fraction)
    editor.apply()
}

/** Earlier versions kept a separate trackpad switch for hardware keyboard mode. */
private const val LEGACY_HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE = "hardware_keyboard_show_trackpad"

/**
 * The keyboard trackpad switch, shared by the on-screen keyboard and hardware keyboard mode.
 * Null until the user picks; callers then show it in hardware keyboard mode, where the screen
 * above the special keys would otherwise be empty, and hide it otherwise.
 */
fun SharedPreferences.keyboardTrackpadChoice(): Boolean? =
    if (contains(KEYBOARD_TRACKPAD_PREFERENCE)) getBoolean(KEYBOARD_TRACKPAD_PREFERENCE, false) else null

fun SharedPreferences.setKeyboardTrackpadEnabled(enabled: Boolean) {
    edit()
        .putBoolean(KEYBOARD_TRACKPAD_PREFERENCE, enabled)
        .remove(LEGACY_HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE)
        .apply()
}
const val KEYBOARD_TRACKPAD_SIDE_PREFERENCE = "keyboard_trackpad_side"

/** How keys are drawn. */
enum class KeyboardStyle(val preferenceValue: String, val displayName: String) {
    MECHANICAL("mechanical", "Mechanical (realistic keycaps)"),
    MINIMAL("minimal", "Minimal (flat keys, less padding)");

    companion object {
        fun fromPreference(value: String?): KeyboardStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: MINIMAL
    }
}

/** Where the trackpad sits next to the keyboard in landscape. */
enum class TrackpadSide(val preferenceValue: String, val displayName: String) {
    LEFT("left", "Left"),
    RIGHT("right", "Right");

    fun flipped(): TrackpadSide = if (this == LEFT) RIGHT else LEFT

    companion object {
        fun fromPreference(value: String?): TrackpadSide =
            entries.firstOrNull { it.preferenceValue == value } ?: RIGHT
    }
}
