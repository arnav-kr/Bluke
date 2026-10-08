package dev.arnv.bluke.ui

const val KEYBOARD_CHARACTER_LAYOUT_PREFERENCE = "keyboard_character_layout"

enum class KeyboardCharacterLayout(
    val preferenceValue: String,
    val displayName: String,
    val hostLayoutName: String,
) {
    US_QWERTY("us_qwerty", "QWERTY", "English (US)"),
    FRENCH_AZERTY("fr_azerty", "AZERTY", "French"),
    GERMAN_QWERTZ("de_qwertz", "QWERTZ", "German"),
    DVORAK("dvorak", "Dvorak", "Dvorak"),
    COLEMAK("colemak", "Colemak", "Colemak"),
    RUSSIAN_JCUKEN("ru_jcuken", "ЙЦУКЕН", "Russian");

    companion object {
        fun fromPreference(value: String?): KeyboardCharacterLayout =
            entries.firstOrNull { it.preferenceValue == value } ?: US_QWERTY
    }

    fun next(): KeyboardCharacterLayout = entries[(ordinal + 1) % entries.size]
}

private data class KeyLegend(
    val normal: String,
    val shifted: String = "",
    val outputKeyCode: Int? = outputKeyCodeForLegend(normal),
)

internal fun List<List<KeyLayoutInfo>>.withCharacterLayout(
    characterLayout: KeyboardCharacterLayout,
): List<List<KeyLayoutInfo>> {
    val overrides = characterLayout.legendOverrides()
    if (overrides.isEmpty()) return this

    return map { row ->
        row.map { key ->
            overrides[key.keyCode]?.let { legend ->
                key.copy(
                    legend = legend.normal,
                    shiftedLegend = legend.shifted,
                    keyCode = legend.outputKeyCode ?: key.keyCode,
                )
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
        KeyboardLayouts.KEY_M to KeyLegend(",", "<"),
        KeyboardLayouts.KEY_COMMA to KeyLegend(";", "."),
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
    KeyboardCharacterLayout.RUSSIAN_JCUKEN -> mapOf(
        physicalLegend(KeyboardLayouts.KEY_GRAVE, "Ё"),
        physicalLegend(KeyboardLayouts.KEY_1, "1", "!"),
        physicalLegend(KeyboardLayouts.KEY_2, "2", "\""),
        physicalLegend(KeyboardLayouts.KEY_3, "3", "№"),
        physicalLegend(KeyboardLayouts.KEY_4, "4", ";"),
        physicalLegend(KeyboardLayouts.KEY_5, "5", "%"),
        physicalLegend(KeyboardLayouts.KEY_6, "6", ":"),
        physicalLegend(KeyboardLayouts.KEY_7, "7", "?"),
        physicalLegend(KeyboardLayouts.KEY_8, "8", "*"),
        physicalLegend(KeyboardLayouts.KEY_9, "9", "("),
        physicalLegend(KeyboardLayouts.KEY_0, "0", ")"),
        physicalLegend(KeyboardLayouts.KEY_MINUS, "-", "_"),
        physicalLegend(KeyboardLayouts.KEY_EQUAL, "=", "+"),
        physicalLegend(KeyboardLayouts.KEY_Q, "Й"),
        physicalLegend(KeyboardLayouts.KEY_W, "Ц"),
        physicalLegend(KeyboardLayouts.KEY_E, "У"),
        physicalLegend(KeyboardLayouts.KEY_R, "К"),
        physicalLegend(KeyboardLayouts.KEY_T, "Е"),
        physicalLegend(KeyboardLayouts.KEY_Y, "Н"),
        physicalLegend(KeyboardLayouts.KEY_U, "Г"),
        physicalLegend(KeyboardLayouts.KEY_I, "Ш"),
        physicalLegend(KeyboardLayouts.KEY_O, "Щ"),
        physicalLegend(KeyboardLayouts.KEY_P, "З"),
        physicalLegend(KeyboardLayouts.KEY_LBRACKET, "Х"),
        physicalLegend(KeyboardLayouts.KEY_RBRACKET, "Ъ"),
        physicalLegend(KeyboardLayouts.KEY_A, "Ф"),
        physicalLegend(KeyboardLayouts.KEY_S, "Ы"),
        physicalLegend(KeyboardLayouts.KEY_D, "В"),
        physicalLegend(KeyboardLayouts.KEY_F, "А"),
        physicalLegend(KeyboardLayouts.KEY_G, "П"),
        physicalLegend(KeyboardLayouts.KEY_H, "Р"),
        physicalLegend(KeyboardLayouts.KEY_J, "О"),
        physicalLegend(KeyboardLayouts.KEY_K, "Л"),
        physicalLegend(KeyboardLayouts.KEY_L, "Д"),
        physicalLegend(KeyboardLayouts.KEY_SEMICOLON, "Ж"),
        physicalLegend(KeyboardLayouts.KEY_APOSTROPHE, "Э"),
        physicalLegend(KeyboardLayouts.KEY_Z, "Я"),
        physicalLegend(KeyboardLayouts.KEY_X, "Ч"),
        physicalLegend(KeyboardLayouts.KEY_C, "С"),
        physicalLegend(KeyboardLayouts.KEY_V, "М"),
        physicalLegend(KeyboardLayouts.KEY_B, "И"),
        physicalLegend(KeyboardLayouts.KEY_N, "Т"),
        physicalLegend(KeyboardLayouts.KEY_M, "Ь"),
        physicalLegend(KeyboardLayouts.KEY_COMMA, "Б"),
        physicalLegend(KeyboardLayouts.KEY_PERIOD, "Ю"),
        physicalLegend(KeyboardLayouts.KEY_SLASH, ".", ","),
    )
}

private fun physicalLegend(
    keyCode: Int,
    normal: String,
    shifted: String = "",
): Pair<Int, KeyLegend> = keyCode to KeyLegend(normal, shifted, keyCode)

private fun legendMap(vararg entries: Pair<Int, String>): Map<Int, KeyLegend> =
    entries.associate { (keyCode, legend) -> keyCode to KeyLegend(legend) }

private fun outputKeyCodeForLegend(legend: String): Int? {
    if (legend.length == 1 && legend[0].isLetter() && legend[0].code < 128) {
        return KeyboardLayouts.KEY_A + (legend[0].uppercaseChar() - 'A')
    }
    return when (legend) {
        "-" -> KeyboardLayouts.KEY_MINUS
        "=" -> KeyboardLayouts.KEY_EQUAL
        "[" -> KeyboardLayouts.KEY_LBRACKET
        "]" -> KeyboardLayouts.KEY_RBRACKET
        "\\" -> KeyboardLayouts.KEY_BACKSLASH
        ";" -> KeyboardLayouts.KEY_SEMICOLON
        "'" -> KeyboardLayouts.KEY_APOSTROPHE
        "`" -> KeyboardLayouts.KEY_GRAVE
        "," -> KeyboardLayouts.KEY_COMMA
        "." -> KeyboardLayouts.KEY_PERIOD
        "/" -> KeyboardLayouts.KEY_SLASH
        else -> null
    }
}
