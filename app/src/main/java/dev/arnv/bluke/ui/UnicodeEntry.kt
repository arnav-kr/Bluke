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
 * - **Windows**: `Alt` held while typing keypad `+` and then the *hex* codepoint, which only works
 *   when the registry value `EnableHexNumpad` is set (and Num Lock is on), and only in applications
 *   that honour it. Notably unreliable outside Win32 text controls. Plain Alt+decimal is not used:
 *   that goes through the OEM code page, so most codepoints would come out as the wrong character.
 * - **macOS**: relies on the *Unicode Hex Input* keyboard source being selected; the user has to add
 *   it in System Settings. With a normal layout selected it will type raw hex digits instead.
 *
 * Because none of these is dependable everywhere, [UnicodeEntryMode.OFF] stays the default.
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
            "Alt, keypad + then hex. Requires the EnableHexNumpad registry value and Num Lock; unreliable outside Win32 text fields"
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
    private const val KEY_KP_PLUS = 0x57
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
     *
     * [hexKey] types one hex digit (`0-9`, `a-f`) on the host's layout, or returns null if it
     * cannot. On AZERTY, for one, the digits need Shift and `a` is where US has `q`; the default
     * assumes US positions.
     */
    fun sequenceFor(
        codePoint: Int,
        mode: UnicodeEntryMode,
        hexKey: (Char) -> List<KeyEvent>? = ::usHexKey,
    ): List<KeyEvent> = when (mode) {
        UnicodeEntryMode.OFF -> emptyList()
        UnicodeEntryMode.LINUX_IBUS -> linuxSequence(codePoint, hexKey)
        UnicodeEntryMode.WINDOWS_ALT -> windowsSequence(codePoint, hexKey)
        // The Unicode Hex Input source is itself the active layout, with US key positions.
        UnicodeEntryMode.MACOS_HEX -> macOsSequence(codePoint)
    }

    private fun usHexKey(c: Char): List<KeyEvent>? = HEX_KEYS[c]?.let(::tap)

    /** Ctrl+Shift+U, hex digits, then Enter to commit. U sits in the same place on every layout here. */
    private fun linuxSequence(codePoint: Int, hexKey: (Char) -> List<KeyEvent>?): List<KeyEvent> {
        val digits = codePoint.toString(16).map { hexKey(it) ?: return emptyList() }
        val out = mutableListOf<KeyEvent>()
        out.add(KeyEvent(K.MOD_LCTRL, true))
        out.add(KeyEvent(K.MOD_LSHIFT, true))
        out.addAll(tap(K.KEY_U))
        out.add(KeyEvent(K.MOD_LSHIFT, false))
        out.add(KeyEvent(K.MOD_LCTRL, false))
        digits.forEach(out::addAll)
        out.addAll(tap(K.KEY_ENTER))
        return out
    }

    /**
     * Alt held down while keypad `+` and the hex codepoint are typed, then released. Digits go on
     * the keypad; `a-f` have no keypad key, so they come from the letter keys.
     *
     * Windows only accepts four hex digits this way; anything above the BMP is out of reach, so
     * emoji are refused rather than typed as garbage.
     */
    private fun windowsSequence(codePoint: Int, hexKey: (Char) -> List<KeyEvent>?): List<KeyEvent> {
        if (codePoint > 0xFFFF) return emptyList()
        val digits = codePoint.toString(16).map { c ->
            KEYPAD_DIGITS[c]?.let(::tap) ?: hexKey(c) ?: return emptyList()
        }
        val out = mutableListOf<KeyEvent>()
        out.add(KeyEvent(K.MOD_LALT, true))
        out.addAll(tap(KEY_KP_PLUS))
        digits.forEach(out::addAll)
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
