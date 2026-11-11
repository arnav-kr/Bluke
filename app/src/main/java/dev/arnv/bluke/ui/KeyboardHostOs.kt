package dev.arnv.bluke.ui

import android.content.SharedPreferences

const val KEYBOARD_HOST_OS_PREFERENCE = "keyboard_host_os"

/**
 * Which modifier arrangement the keyboard uses.
 *
 * A Mac keyboard has Command next to the space bar and Option outside it, where a PC
 * keyboard has Alt next to the space bar and Win outside it. macOS reads the HID GUI
 * modifier as Command and Alt as Option, so the Mac arrangement swaps what those two
 * keys send and how they are labelled. Each key keeps its position and theme colors.
 */
enum class KeyboardHostOs(
    val preferenceValue: String,
    val displayName: String,
    val shortName: String,
) {
    PC("pc", "Windows / Linux", "PC"),
    MAC("mac", "Mac", "Mac");

    fun toggled(): KeyboardHostOs = if (this == PC) MAC else PC

    companion object {
        fun fromPreference(value: String?): KeyboardHostOs =
            entries.firstOrNull { it.preferenceValue == value } ?: PC
    }
}

fun SharedPreferences.keyboardHostOs(): KeyboardHostOs =
    KeyboardHostOs.fromPreference(getString(KEYBOARD_HOST_OS_PREFERENCE, null))

internal fun List<List<KeyLayoutInfo>>.withHostOs(hostOs: KeyboardHostOs): List<List<KeyLayoutInfo>> {
    if (hostOs == KeyboardHostOs.PC) return this
    return map { row -> row.map(KeyLayoutInfo::asMacKey) }
}

private fun KeyLayoutInfo.asMacKey(): KeyLayoutInfo = when (keyCode) {
    KeyboardLayouts.MOD_LALT -> macModifier("Cmd", KeyboardLayouts.MOD_LWIN)
    KeyboardLayouts.MOD_RALT -> macModifier("Cmd", KeyboardLayouts.MOD_RWIN)
    KeyboardLayouts.MOD_LWIN -> macModifier("Opt", KeyboardLayouts.MOD_LALT)
    KeyboardLayouts.MOD_RWIN -> macModifier("Opt", KeyboardLayouts.MOD_RALT)
    else -> this
}

// styleId is left alone so per-key theme colors stay with the key's position.
private fun KeyLayoutInfo.macModifier(label: String, code: Int): KeyLayoutInfo =
    copy(legend = label, shiftedLegend = "", keyCode = code, physicalKeyCode = code)
