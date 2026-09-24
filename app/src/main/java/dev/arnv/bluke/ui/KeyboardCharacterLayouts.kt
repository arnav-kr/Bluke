package dev.arnv.bluke.ui

const val KEYBOARD_CHARACTER_LAYOUT_PREFERENCE = "keyboard_character_layout"

enum class KeyboardCharacterLayout(
    val preferenceValue: String,
    val displayName: String,
    val hostLayoutName: String,
) {
    US_QWERTY("us_qwerty", "US QWERTY", "English (US)"),
    FRENCH_AZERTY("fr_azerty", "French AZERTY", "French"),
    GERMAN_QWERTZ("de_qwertz", "German QWERTZ", "German"),
    DVORAK("dvorak", "Dvorak", "Dvorak"),
    COLEMAK("colemak", "Colemak", "Colemak");

    companion object {
        fun fromPreference(value: String?): KeyboardCharacterLayout =
            entries.firstOrNull { it.preferenceValue == value } ?: US_QWERTY
    }
}

private data class KeyLegend(val normal: String, val shifted: String = "")

internal fun List<List<KeyLayoutInfo>>.withCharacterLayout(
    characterLayout: KeyboardCharacterLayout,
): List<List<KeyLayoutInfo>> {
    val overrides = characterLayout.legendOverrides()
    if (overrides.isEmpty()) return this

    return map { row ->
        row.map { key ->
            overrides[key.keyCode]?.let { legend ->
                key.copy(legend = legend.normal, shiftedLegend = legend.shifted)
            } ?: key
        }
    }
}

private fun KeyboardCharacterLayout.legendOverrides(): Map<Int, KeyLegend> = when (this) {
    KeyboardCharacterLayout.US_QWERTY -> emptyMap()
    KeyboardCharacterLayout.GERMAN_QWERTZ -> mapOf(
        KeyboardLayouts.KEY_Y to KeyLegend("Z"),
        KeyboardLayouts.KEY_Z to KeyLegend("Y"),
    )
    KeyboardCharacterLayout.FRENCH_AZERTY -> mapOf(
        KeyboardLayouts.KEY_Q to KeyLegend("A"),
        KeyboardLayouts.KEY_W to KeyLegend("Z"),
        KeyboardLayouts.KEY_A to KeyLegend("Q"),
        KeyboardLayouts.KEY_Z to KeyLegend("W"),
        KeyboardLayouts.KEY_SEMICOLON to KeyLegend("M"),
        KeyboardLayouts.KEY_M to KeyLegend(",", "?"),
        KeyboardLayouts.KEY_COMMA to KeyLegend(";", "."),
        KeyboardLayouts.KEY_PERIOD to KeyLegend(":", "/"),
        KeyboardLayouts.KEY_SLASH to KeyLegend("!", "§"),
        KeyboardLayouts.KEY_1 to KeyLegend("&", "1"),
        KeyboardLayouts.KEY_2 to KeyLegend("é", "2"),
        KeyboardLayouts.KEY_3 to KeyLegend("\"", "3"),
        KeyboardLayouts.KEY_4 to KeyLegend("'", "4"),
        KeyboardLayouts.KEY_5 to KeyLegend("(", "5"),
        KeyboardLayouts.KEY_6 to KeyLegend("-", "6"),
        KeyboardLayouts.KEY_7 to KeyLegend("è", "7"),
        KeyboardLayouts.KEY_8 to KeyLegend("_", "8"),
        KeyboardLayouts.KEY_9 to KeyLegend("ç", "9"),
        KeyboardLayouts.KEY_0 to KeyLegend("à", "0"),
        KeyboardLayouts.KEY_MINUS to KeyLegend(")", "°"),
        KeyboardLayouts.KEY_EQUAL to KeyLegend("^", "¨"),
    )
    KeyboardCharacterLayout.DVORAK -> legendMap(
        KeyboardLayouts.KEY_Q to "'", KeyboardLayouts.KEY_W to ",",
        KeyboardLayouts.KEY_E to ".", KeyboardLayouts.KEY_R to "P",
        KeyboardLayouts.KEY_T to "Y", KeyboardLayouts.KEY_Y to "F",
        KeyboardLayouts.KEY_U to "G", KeyboardLayouts.KEY_I to "C",
        KeyboardLayouts.KEY_O to "R", KeyboardLayouts.KEY_P to "L",
        KeyboardLayouts.KEY_LBRACKET to "/", KeyboardLayouts.KEY_RBRACKET to "=",
        KeyboardLayouts.KEY_A to "A", KeyboardLayouts.KEY_S to "O",
        KeyboardLayouts.KEY_D to "E", KeyboardLayouts.KEY_F to "U",
        KeyboardLayouts.KEY_G to "I", KeyboardLayouts.KEY_H to "D",
        KeyboardLayouts.KEY_J to "H", KeyboardLayouts.KEY_K to "T",
        KeyboardLayouts.KEY_L to "N", KeyboardLayouts.KEY_SEMICOLON to "S",
        KeyboardLayouts.KEY_APOSTROPHE to "-", KeyboardLayouts.KEY_Z to ";",
        KeyboardLayouts.KEY_X to "Q", KeyboardLayouts.KEY_C to "J",
        KeyboardLayouts.KEY_V to "K", KeyboardLayouts.KEY_B to "X",
        KeyboardLayouts.KEY_N to "B", KeyboardLayouts.KEY_M to "M",
        KeyboardLayouts.KEY_COMMA to "W", KeyboardLayouts.KEY_PERIOD to "V",
        KeyboardLayouts.KEY_SLASH to "Z",
    )
    KeyboardCharacterLayout.COLEMAK -> legendMap(
        KeyboardLayouts.KEY_E to "F", KeyboardLayouts.KEY_R to "P",
        KeyboardLayouts.KEY_T to "G", KeyboardLayouts.KEY_Y to "J",
        KeyboardLayouts.KEY_U to "L", KeyboardLayouts.KEY_I to "U",
        KeyboardLayouts.KEY_O to "Y", KeyboardLayouts.KEY_P to ";",
        KeyboardLayouts.KEY_S to "R", KeyboardLayouts.KEY_D to "S",
        KeyboardLayouts.KEY_F to "T", KeyboardLayouts.KEY_G to "D",
        KeyboardLayouts.KEY_J to "N", KeyboardLayouts.KEY_K to "E",
        KeyboardLayouts.KEY_L to "I", KeyboardLayouts.KEY_SEMICOLON to "O",
        KeyboardLayouts.KEY_N to "K",
    )
}

private fun legendMap(vararg entries: Pair<Int, String>): Map<Int, KeyLegend> =
    entries.associate { (keyCode, legend) -> keyCode to KeyLegend(legend) }
