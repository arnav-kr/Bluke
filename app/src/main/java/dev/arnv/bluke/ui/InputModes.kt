package dev.arnv.bluke.ui

import android.content.SharedPreferences

/**
 * The input modes the launch button cycles through.
 *
 * These were previously three bare ints (0/1/2) with the mapping to preference strings repeated at
 * every call site. Adding System Keyboard as a fourth mode made that duplication the main source of
 * risk - a mode missing from one of the copies silently drops out of the rotation - so the mapping
 * lives here once.
 */
object InputModes {
    const val KEYBOARD = 0
    const val TOUCHPAD = 1
    const val GAMEPAD = 2

    /** Relays text from the phone's own IME instead of drawing keycaps. */
    const val SYSTEM_KEYBOARD = 3

    const val ALL_COUNT = 4

    private const val PREF_CYCLE_MODES = "cycle_connection_modes"

    /** Preference token for a mode, as stored in [PREF_CYCLE_MODES]. */
    fun prefKey(mode: Int): String = when (mode) {
        TOUCHPAD -> "touchpad"
        GAMEPAD -> "gamepad"
        SYSTEM_KEYBOARD -> "system_keyboard"
        else -> "keyboard"
    }

    /** Every mode is in the rotation unless the user has explicitly removed it. */
    val DEFAULT_ENABLED: Set<String> = setOf(
        prefKey(KEYBOARD),
        prefKey(TOUCHPAD),
        prefKey(GAMEPAD),
        prefKey(SYSTEM_KEYBOARD)
    )

    /**
     * Names as shown on the launch button and mode picker.
     *
     * "Keycaps" and "Phone Keyboard" rather than "Keyboard" and "System Keyboard": the latter pair
     * differ by one word and read as variants of the same thing, when in fact one draws a mechanical
     * keyboard and the other hands over to the phone's own IME.
     */
    fun displayName(mode: Int): String = when (mode) {
        TOUCHPAD -> "Touchpad"
        GAMEPAD -> "Gamepad"
        SYSTEM_KEYBOARD -> "Phone Keyboard"
        else -> "Keycaps"
    }

    /**
     * Modes currently in the cycle, in ascending order.
     *
     * Falls back to [KEYBOARD] alone so the launch button is never left with nothing to do - an
     * empty selection would otherwise crash the modulo arithmetic at the call sites.
     */
    fun enabled(prefs: SharedPreferences): List<Int> {
        val selected = prefs.getStringSet(PREF_CYCLE_MODES, DEFAULT_ENABLED) ?: DEFAULT_ENABLED
        return (0 until ALL_COUNT)
            .filter { selected.contains(prefKey(it)) }
            .ifEmpty { listOf(KEYBOARD) }
    }

    /** The mode after [current] in the cycle, wrapping around. */
    fun next(prefs: SharedPreferences, current: Int): Int {
        val modes = enabled(prefs)
        val index = modes.indexOf(current).coerceAtLeast(0)
        return modes[(index + 1) % modes.size]
    }
}
