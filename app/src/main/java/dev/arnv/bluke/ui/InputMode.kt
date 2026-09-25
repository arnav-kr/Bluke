package dev.arnv.bluke.ui

import android.content.SharedPreferences
import androidx.core.content.edit

private const val INPUT_MODE_CYCLE_PREFERENCE = "cycle_connection_modes"

enum class InputMode(
    val id: Int,
    val preferenceKey: String,
    val displayName: String,
) {
    KEYBOARD(0, "keyboard", "Keyboard"),
    TOUCHPAD(1, "touchpad", "Touchpad"),
    GAMEPAD(2, "gamepad", "Gamepad"),
    KEYBOARD_TOUCHPAD(3, "keyboard_touchpad", "Keyboard + Touchpad"),
    ;

    companion object {
        fun fromId(id: Int): InputMode = entries.firstOrNull { it.id == id } ?: KEYBOARD
    }
}

private val legacyDefaultModeKeys = setOf("keyboard", "touchpad", "gamepad")
val defaultInputModeKeys: Set<String> = InputMode.entries.mapTo(linkedSetOf()) { it.preferenceKey }

fun normalizeInputModeKeys(savedKeys: Set<String>?): Set<String> {
    val normalized = when {
        savedKeys == null || savedKeys == legacyDefaultModeKeys -> defaultInputModeKeys
        else -> savedKeys.filterTo(linkedSetOf()) { key ->
            InputMode.entries.any { it.preferenceKey == key }
        }
    }
    return normalized.ifEmpty { setOf(InputMode.KEYBOARD.preferenceKey) }
}

fun SharedPreferences.enabledInputModes(): List<InputMode> {
    val saved = getStringSet(INPUT_MODE_CYCLE_PREFERENCE, null)?.toSet()
    val normalized = normalizeInputModeKeys(saved)
    if (saved != normalized) {
        edit { putStringSet(INPUT_MODE_CYCLE_PREFERENCE, normalized) }
    }
    return InputMode.entries.filter { normalized.contains(it.preferenceKey) }
}
