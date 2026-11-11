package dev.arnv.bluke.ui

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
