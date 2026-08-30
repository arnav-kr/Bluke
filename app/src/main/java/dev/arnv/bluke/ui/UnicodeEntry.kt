package dev.arnv.bluke.ui

/**
 * Types characters the host's keyboard layout cannot reach, by driving the host OS's own
 * Unicode-entry escape hatch.
 *
 * Every mechanism here is an OS feature, not a HID one, so the target OS has to be declared by the
 * user - and each comes with real caveats:
 *
 * - **Linux (IBus/GTK)**: `Ctrl+Shift+U`, hex digits, then Enter or Space. Works in GTK and Qt apps
 *   under IBus, but not in every toolkit, and not in a bare TTY.
 * - **Windows**: `Alt` held while typing the *decimal* codepoint on the **numeric keypad**, which
 *   only works when the registry value `EnableHexNumpad` is set, and only in applications that
 *   honour it. Notably unreliable outside Win32 text controls.
 * - **macOS**: relies on the *Unicode Hex Input* keyboard source being selected; the user has to add
 *   it in System Settings. With a normal layout selected it will type raw hex digits instead.
 *
 * Because none of these is dependable everywhere, [UnicodeEntryMode.OFF] stays the default and the
 * clipboard route in [ImeInputView] remains the sturdier option for anything important.
 */
object UnicodeEntry {

    enum class UnicodeEntryMode(val id: String, val displayName: String, val description: String) {
        OFF(
            "off",
            "Off",
            "Characters the host layout cannot type are skipped and reported"
        ),
        LINUX_IBUS(
            "linux",
            "Linux (IBus / GTK)",
            "Ctrl+Shift+U then hex. Works in most GTK and Qt apps, not in a TTY"
        ),
        WINDOWS_ALT(
            "windows",
            "Windows (Alt+Numpad)",
            "Requires the EnableHexNumpad registry value; unreliable outside Win32 text fields"
        ),
        MACOS_HEX(
            "macos",
            "macOS (Unicode Hex Input)",
            "Requires the Unicode Hex Input keyboard source to be active"
        );

        companion object {
            fun byId(id: String?): UnicodeEntryMode =
                entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    /** One key event in a generated sequence: a usage code and whether it is a press. */
    data class KeyEvent(val keyCode: Int, val isPress: Boolean)

    private val K = KeyboardLayouts

    private val HEX_KEYS = mapOf(
        '0' to K.KEY_0, '1' to K.KEY_1, '2' to K.KEY_2, '3' to K.KEY_3, '4' to K.KEY_4,
        '5' to K.KEY_5, '6' to K.KEY_6, '7' to K.KEY_7, '8' to K.KEY_8, '9' to K.KEY_9,
        'a' to K.KEY_A, 'b' to K.KEY_B, 'c' to K.KEY_C, 'd' to K.KEY_D, 'e' to K.KEY_E,
        'f' to K.KEY_F
    )

    /** Windows Alt-entry must use the keypad digits; the top-row ones are ignored. */
    private val KEYPAD_DIGITS = mapOf(
        '0' to 0x62, '1' to 0x59, '2' to 0x5A, '3' to 0x5B, '4' to 0x5C,
        '5' to 0x5D, '6' to 0x5E, '7' to 0x5F, '8' to 0x60, '9' to 0x61
    )

    private fun tap(code: Int) = listOf(KeyEvent(code, true), KeyEvent(code, false))

    /**
     * The key events that type [codePoint] on the host, or an empty list when [mode] is
     * [UnicodeEntryMode.OFF] or the codepoint cannot be expressed.
     *
     * Takes a codepoint rather than a Char so characters outside the BMP - emoji, most importantly -
     * are handled as one unit instead of as broken surrogate halves.
     */
    fun sequenceFor(codePoint: Int, mode: UnicodeEntryMode): List<KeyEvent> = when (mode) {
        UnicodeEntryMode.OFF -> emptyList()
        UnicodeEntryMode.LINUX_IBUS -> linuxSequence(codePoint)
        UnicodeEntryMode.WINDOWS_ALT -> windowsSequence(codePoint)
        UnicodeEntryMode.MACOS_HEX -> macOsSequence(codePoint)
    }

    /** Ctrl+Shift+U, hex digits, then Enter to commit. */
    private fun linuxSequence(codePoint: Int): List<KeyEvent> {
        val hex = codePoint.toString(16).lowercase()
        if (hex.any { it !in HEX_KEYS }) return emptyList()
        val out = mutableListOf<KeyEvent>()
        out.add(KeyEvent(K.MOD_LCTRL, true))
        out.add(KeyEvent(K.MOD_LSHIFT, true))
        out.addAll(tap(K.KEY_U))
        out.add(KeyEvent(K.MOD_LSHIFT, false))
        out.add(KeyEvent(K.MOD_LCTRL, false))
        hex.forEach { out.addAll(tap(HEX_KEYS.getValue(it))) }
        out.addAll(tap(K.KEY_ENTER))
        return out
    }

    /**
     * Alt held down while the decimal codepoint is typed on the keypad, then released.
     *
     * Windows only accepts this for values it can represent this way; anything above the BMP is
     * out of reach, so emoji are refused rather than typed as garbage.
     */
    private fun windowsSequence(codePoint: Int): List<KeyEvent> {
        if (codePoint > 0xFFFF) return emptyList()
        val decimal = codePoint.toString()
        if (decimal.any { it !in KEYPAD_DIGITS }) return emptyList()
        val out = mutableListOf<KeyEvent>()
        out.add(KeyEvent(K.MOD_LALT, true))
        decimal.forEach { out.addAll(tap(KEYPAD_DIGITS.getValue(it))) }
        out.add(KeyEvent(K.MOD_LALT, false))
        return out
    }

    /**
     * Option held down while the hex codepoint is typed, with the Unicode Hex Input source active.
     *
     * That source only accepts four hex digits, so anything above the BMP has to be sent as its
     * UTF-16 surrogate pair - two Option+hex bursts that macOS recombines.
     */
    private fun macOsSequence(codePoint: Int): List<KeyEvent> {
        val units: List<Int> = if (codePoint > 0xFFFF) {
            val v = codePoint - 0x10000
            listOf(0xD800 + (v shr 10), 0xDC00 + (v and 0x3FF))
        } else {
            listOf(codePoint)
        }
        val out = mutableListOf<KeyEvent>()
        out.add(KeyEvent(K.MOD_LALT, true))
        for (unit in units) {
            val hex = unit.toString(16).lowercase().padStart(4, '0')
            if (hex.any { it !in HEX_KEYS }) return emptyList()
            hex.forEach { out.addAll(tap(HEX_KEYS.getValue(it))) }
        }
        out.add(KeyEvent(K.MOD_LALT, false))
        return out
    }
}
