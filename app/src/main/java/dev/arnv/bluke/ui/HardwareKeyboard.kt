package dev.arnv.bluke.ui

import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.KeyEvent

const val HARDWARE_KEYBOARD_MODE_PREFERENCE = "hardware_keyboard_mode"
const val HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE = "hardware_keyboard_show_trackpad"

/**
 * When the keyboard screen hides its letter keys and takes key presses from the
 * phone's own keyboard (Clicks Communicator, Unihertz Titan, or an attached keyboard).
 */
enum class HardwareKeyboardMode(val preferenceValue: String, val displayName: String) {
    AUTO("auto", "Automatic"),
    ON("on", "Always on"),
    OFF("off", "Off");

    fun isActive(hasPhysicalKeyboard: Boolean): Boolean = when (this) {
        AUTO -> hasPhysicalKeyboard
        ON -> true
        OFF -> false
    }

    companion object {
        fun fromPreference(value: String?): HardwareKeyboardMode =
            entries.firstOrNull { it.preferenceValue == value } ?: AUTO
    }
}

fun SharedPreferences.hardwareKeyboardMode(): HardwareKeyboardMode =
    HardwareKeyboardMode.fromPreference(getString(HARDWARE_KEYBOARD_MODE_PREFERENCE, null))

fun SharedPreferences.isHardwareKeyboardTrackpadEnabled(): Boolean =
    getBoolean(HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE, true)

/** True when Android reports a usable physical letter keyboard (built in or attached). */
fun Configuration.hasPhysicalKeyboard(): Boolean =
    keyboard == Configuration.KEYBOARD_QWERTY &&
        hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES

/**
 * What one physical key press sent to the host, so the release undoes exactly that.
 *
 * [throughKeyboard] presses go through the on-screen keyboard's key handling (sticky
 * modifiers, lock keys, Fn). Other presses are characters typed with the phone's
 * Alt layer and are sent directly, with Shift adjusted for that character.
 */
internal data class HardwareKeyPress(
    val usage: Int,
    val throughKeyboard: Boolean,
    val addedModifiers: List<Int> = emptyList(),
    val suppressedModifiers: List<Int> = emptyList(),
) {
    val isModifier: Boolean get() = usage in KeyboardLayouts.MOD_LCTRL..KeyboardLayouts.MOD_RWIN
}

/** Host modifiers that turn a key press into a shortcut rather than typing. */
internal val HOST_SHORTCUT_MODIFIERS = listOf(
    KeyboardLayouts.MOD_LCTRL, KeyboardLayouts.MOD_RCTRL,
    KeyboardLayouts.MOD_LALT, KeyboardLayouts.MOD_RALT,
    KeyboardLayouts.MOD_LWIN, KeyboardLayouts.MOD_RWIN,
)
internal val HOST_SHIFT_MODIFIERS = listOf(KeyboardLayouts.MOD_LSHIFT, KeyboardLayouts.MOD_RSHIFT)

object HardwareKeyMap {
    /** Local-only code for the phone's Alt/Sym layer key. Never sent to the host. */
    const val PHONE_LAYER_KEY = 0x120

    /** Alt and Sym pick the symbols printed on the phone's keys instead of being sent. */
    fun isPhoneLayerKey(androidKeyCode: Int): Boolean = when (androidKeyCode) {
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_SYM -> true
        else -> false
    }

    /** HID modifier for a physical Shift, Ctrl or Meta key. */
    fun modifierUsageFor(androidKeyCode: Int): Int? = when (androidKeyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT -> KeyboardLayouts.MOD_LSHIFT
        KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyboardLayouts.MOD_RSHIFT
        KeyEvent.KEYCODE_CTRL_LEFT -> KeyboardLayouts.MOD_LCTRL
        KeyEvent.KEYCODE_CTRL_RIGHT -> KeyboardLayouts.MOD_RCTRL
        KeyEvent.KEYCODE_META_LEFT -> KeyboardLayouts.MOD_LWIN
        KeyEvent.KEYCODE_META_RIGHT -> KeyboardLayouts.MOD_RWIN
        else -> null
    }

    /** HID usage for the key at the same position on a US keyboard, or null. */
    fun usageFor(androidKeyCode: Int): Int? = when (androidKeyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
            KeyboardLayouts.KEY_A + (androidKeyCode - KeyEvent.KEYCODE_A)
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> KeyboardLayouts.KEY_0
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
            KeyboardLayouts.KEY_1 + (androidKeyCode - KeyEvent.KEYCODE_1)
        in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 ->
            KeyboardLayouts.KEY_1 + (androidKeyCode - KeyEvent.KEYCODE_NUMPAD_1)
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ->
            KeyboardLayouts.KEY_F1 + (androidKeyCode - KeyEvent.KEYCODE_F1)
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyboardLayouts.KEY_ENTER
        KeyEvent.KEYCODE_ESCAPE -> KeyboardLayouts.KEY_ESC
        KeyEvent.KEYCODE_DEL -> KeyboardLayouts.KEY_BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> KeyboardLayouts.KEY_DELETE
        KeyEvent.KEYCODE_TAB -> KeyboardLayouts.KEY_TAB
        KeyEvent.KEYCODE_SPACE -> KeyboardLayouts.KEY_SPACE
        KeyEvent.KEYCODE_MINUS -> KeyboardLayouts.KEY_MINUS
        KeyEvent.KEYCODE_EQUALS -> KeyboardLayouts.KEY_EQUAL
        KeyEvent.KEYCODE_LEFT_BRACKET -> KeyboardLayouts.KEY_LBRACKET
        KeyEvent.KEYCODE_RIGHT_BRACKET -> KeyboardLayouts.KEY_RBRACKET
        KeyEvent.KEYCODE_BACKSLASH -> KeyboardLayouts.KEY_BACKSLASH
        KeyEvent.KEYCODE_SEMICOLON -> KeyboardLayouts.KEY_SEMICOLON
        KeyEvent.KEYCODE_APOSTROPHE -> KeyboardLayouts.KEY_APOSTROPHE
        KeyEvent.KEYCODE_GRAVE -> KeyboardLayouts.KEY_GRAVE
        KeyEvent.KEYCODE_COMMA -> KeyboardLayouts.KEY_COMMA
        KeyEvent.KEYCODE_PERIOD -> KeyboardLayouts.KEY_PERIOD
        KeyEvent.KEYCODE_SLASH -> KeyboardLayouts.KEY_SLASH
        KeyEvent.KEYCODE_CAPS_LOCK -> KeyboardLayouts.KEY_CAPSLOCK
        KeyEvent.KEYCODE_NUM_LOCK -> KeyboardLayouts.KEY_NUMLOCK
        KeyEvent.KEYCODE_SCROLL_LOCK -> KeyboardLayouts.KEY_SCROLLLOCK
        KeyEvent.KEYCODE_SYSRQ -> KeyboardLayouts.KEY_PRINTSCREEN
        KeyEvent.KEYCODE_BREAK -> KeyboardLayouts.KEY_PAUSE
        KeyEvent.KEYCODE_INSERT -> KeyboardLayouts.KEY_INSERT
        KeyEvent.KEYCODE_MOVE_HOME -> KeyboardLayouts.KEY_HOME
        KeyEvent.KEYCODE_MOVE_END -> KeyboardLayouts.KEY_END
        KeyEvent.KEYCODE_PAGE_UP -> KeyboardLayouts.KEY_PAGEUP
        KeyEvent.KEYCODE_PAGE_DOWN -> KeyboardLayouts.KEY_PAGEDOWN
        KeyEvent.KEYCODE_DPAD_UP -> KeyboardLayouts.KEY_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyboardLayouts.KEY_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyboardLayouts.KEY_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyboardLayouts.KEY_RIGHT
        else -> null
    }

    private const val US_UNSHIFTED = "`1234567890-=[]\\;',./"
    private const val US_SHIFTED = "~!@#$%^&*()_+{}|:\"<>?"
    private val US_SYMBOL_USAGES = intArrayOf(
        KeyboardLayouts.KEY_GRAVE,
        KeyboardLayouts.KEY_1, KeyboardLayouts.KEY_2, KeyboardLayouts.KEY_3, KeyboardLayouts.KEY_4,
        KeyboardLayouts.KEY_5, KeyboardLayouts.KEY_6, KeyboardLayouts.KEY_7, KeyboardLayouts.KEY_8,
        KeyboardLayouts.KEY_9, KeyboardLayouts.KEY_0,
        KeyboardLayouts.KEY_MINUS, KeyboardLayouts.KEY_EQUAL,
        KeyboardLayouts.KEY_LBRACKET, KeyboardLayouts.KEY_RBRACKET, KeyboardLayouts.KEY_BACKSLASH,
        KeyboardLayouts.KEY_SEMICOLON, KeyboardLayouts.KEY_APOSTROPHE,
        KeyboardLayouts.KEY_COMMA, KeyboardLayouts.KEY_PERIOD, KeyboardLayouts.KEY_SLASH,
    )

    /** HID usage and whether Shift is needed to type [char] with a US host layout. */
    fun usageForCharacter(char: Char): Pair<Int, Boolean>? {
        when (char) {
            in 'a'..'z' -> return (KeyboardLayouts.KEY_A + (char - 'a')) to false
            in 'A'..'Z' -> return (KeyboardLayouts.KEY_A + (char - 'A')) to true
            ' ' -> return KeyboardLayouts.KEY_SPACE to false
            '\n', '\r' -> return KeyboardLayouts.KEY_ENTER to false
            '\t' -> return KeyboardLayouts.KEY_TAB to false
        }
        US_UNSHIFTED.indexOf(char).takeIf { it >= 0 }?.let { return US_SYMBOL_USAGES[it] to false }
        US_SHIFTED.indexOf(char).takeIf { it >= 0 }?.let { return US_SYMBOL_USAGES[it] to true }
        return null
    }

    /**
     * Decides what a physical key press sends.
     *
     * @param layerChar the character the key types with the phone's Alt layer (0 if none).
     * @param baseChar the character it types without that layer, used to tell whether the
     *   keyboard has a printed symbol layer at all.
     * @param typedChar the character it types right now, for keys with no fixed position.
     * @return null when the key is not something to send, so Android can handle it.
     */
    internal fun plan(
        androidKeyCode: Int,
        phoneLayerActive: Boolean,
        layerChar: Int,
        baseChar: Int,
        typedChar: Int,
        activeHostModifiers: Set<Int>,
    ): HardwareKeyPress? {
        modifierUsageFor(androidKeyCode)?.let { return HardwareKeyPress(it, throughKeyboard = true) }

        val shortcut = HOST_SHORTCUT_MODIFIERS.any { it in activeHostModifiers }
        if (phoneLayerActive && !shortcut && layerChar > 0 && layerChar != baseChar) {
            characterPress(layerChar, activeHostModifiers)?.let { return it }
        }

        usageFor(androidKeyCode)?.let { usage ->
            // With no printed symbol for this key, the phone's Alt works as the host's Alt.
            val addAlt = phoneLayerActive &&
                KeyboardLayouts.MOD_LALT !in activeHostModifiers &&
                KeyboardLayouts.MOD_RALT !in activeHostModifiers
            return HardwareKeyPress(
                usage,
                throughKeyboard = true,
                addedModifiers = if (addAlt) listOf(KeyboardLayouts.MOD_LALT) else emptyList(),
            )
        }

        // Keys such as @, + or * on phone keyboards have no US position; send what they type.
        if (typedChar > 0) return characterPress(typedChar, activeHostModifiers)
        return null
    }

    private fun characterPress(char: Int, activeHostModifiers: Set<Int>): HardwareKeyPress? {
        val (usage, needsShift) = usageForCharacter(char.toChar()) ?: return null
        val heldShifts = HOST_SHIFT_MODIFIERS.filter { it in activeHostModifiers }
        return HardwareKeyPress(
            usage = usage,
            throughKeyboard = false,
            addedModifiers = if (needsShift && heldShifts.isEmpty()) listOf(KeyboardLayouts.MOD_LSHIFT) else emptyList(),
            suppressedModifiers = if (!needsShift) heldShifts else emptyList(),
        )
    }
}
