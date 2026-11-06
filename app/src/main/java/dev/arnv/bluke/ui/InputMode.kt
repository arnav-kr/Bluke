package dev.arnv.bluke.ui

import android.content.SharedPreferences
import androidx.core.content.edit

private const val INPUT_MODE_CYCLE_PREFERENCE = "cycle_connection_modes"
private const val INPUT_MODE_CYCLE_VERSION_PREFERENCE = "cycle_connection_modes_version"

// Bumped whenever a mode joins the defaults. An untouched older default is upgraded once per bump,
// so a mode the user unticks afterwards stays unticked even though the set now looks like an old default.
private const val INPUT_MODE_CYCLE_VERSION = 1

enum class InputMode(
    val id: Int,
    val preferenceKey: String,
    val displayName: String,
) {
    KEYBOARD(0, "keyboard", "Keyboard"),
    TOUCHPAD(1, "touchpad", "Touchpad"),
    GAMEPAD(2, "gamepad", "Gamepad"),
    MEDIA_PRESENTATION(4, "media_presentation", "Multimedia"),
    // Id 3 belonged to a removed mode and is migrated away, so it is not reused.
    PHONE_KEYBOARD(5, "phone_keyboard", "Phone Keyboard"),
    ;

    companion object {
        fun fromId(id: Int): InputMode = entries.firstOrNull { it.id == id } ?: KEYBOARD
    }
}

// Earlier full defaults: users who never changed them get the modes added since.
private val legacyDefaultModeKeySets = setOf(
    setOf("keyboard", "touchpad", "gamepad"),
    setOf("keyboard", "touchpad", "gamepad", "media_presentation"),
)
private const val REMOVED_MOUSE_MODE_KEY = "mouse"
private const val REMOVED_KEYBOARD_TOUCHPAD_MODE_KEY = "keyboard_touchpad"
val defaultInputModeKeys: Set<String> = InputMode.entries.mapTo(linkedSetOf()) { it.preferenceKey }

fun normalizeInputModeKeys(savedKeys: Set<String>?, upgradeLegacyDefaults: Boolean = true): Set<String> {
    val normalized = when {
        savedKeys == null -> defaultInputModeKeys
        upgradeLegacyDefaults && savedKeys in legacyDefaultModeKeySets -> defaultInputModeKeys
        else -> savedKeys.mapNotNullTo(linkedSetOf()) { key ->
            when (key) {
                REMOVED_MOUSE_MODE_KEY -> InputMode.MEDIA_PRESENTATION.preferenceKey
                REMOVED_KEYBOARD_TOUCHPAD_MODE_KEY -> null
                else -> key
            }
        }.filterTo(linkedSetOf()) { key ->
            InputMode.entries.any { it.preferenceKey == key }
        }
    }
    return normalized.ifEmpty { setOf(InputMode.KEYBOARD.preferenceKey) }
}

fun SharedPreferences.enabledInputModes(): List<InputMode> {
    val saved = getStringSet(INPUT_MODE_CYCLE_PREFERENCE, null)?.toSet()
    val upgrade = getInt(INPUT_MODE_CYCLE_VERSION_PREFERENCE, 0) < INPUT_MODE_CYCLE_VERSION
    val normalized = normalizeInputModeKeys(saved, upgradeLegacyDefaults = upgrade)
    if (saved != normalized || upgrade) {
        edit {
            putStringSet(INPUT_MODE_CYCLE_PREFERENCE, normalized)
            putInt(INPUT_MODE_CYCLE_VERSION_PREFERENCE, INPUT_MODE_CYCLE_VERSION)
        }
    }
    return InputMode.entries.filter { normalized.contains(it.preferenceKey) }
}
