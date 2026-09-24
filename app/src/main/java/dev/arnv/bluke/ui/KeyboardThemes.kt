package dev.arnv.bluke.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

const val BUILTIN_KEYBOARD_THEME_PREFIX = "builtin:"
const val CUSTOM_KEYBOARD_THEME_PREFIX = "custom:"

enum class BuiltinKeyboardTheme(
    val displayName: String,
    internal val legacySource: KeyboardLayoutType,
) {
    OLIVIA("Olivia", KeyboardLayoutType.OLIVIA_75),
    DRACULA("Dracula", KeyboardLayoutType.DRACULA_75),
    CAFE("Cafe", KeyboardLayoutType.CAFE_65),
    HHKB("HHKB", KeyboardLayoutType.HHKB_60),
    MODEL_M("Model M", KeyboardLayoutType.MODEL_M_VINTAGE),
    MIZU("Mizu", KeyboardLayoutType.MIZU_65),
    LASER("Laser", KeyboardLayoutType.LASER_75),
    OBLIVION("Oblivion", KeyboardLayoutType.OBLIVION_75),
    NINE_ZERO_ZERO_NINE("9009", KeyboardLayoutType.NINE_ZERO_ZERO_NINE_TKL),
    EIGHT_ZERO_ZERO_EIGHT("8008", KeyboardLayoutType.EIGHT_ZERO_ZERO_EIGHT_65);

    val id: String get() = "$BUILTIN_KEYBOARD_THEME_PREFIX$name"

    companion object {
        fun fromLegacy(type: KeyboardLayoutType): BuiltinKeyboardTheme =
            entries.first { it.legacySource == type }
    }
}

data class KeyboardKeyStyle(
    val backgroundArgb: Int,
    val legendArgb: Int,
    val legendScale: Float = 1f,
)

data class KeyboardThemeDefinition(
    val id: String,
    val name: String,
    val plateArgb: Int,
    val alphaStyle: KeyboardKeyStyle,
    val modifierStyle: KeyboardKeyStyle,
    val accentStyle: KeyboardKeyStyle,
    val keyOverrides: Map<String, KeyboardKeyStyle> = emptyMap(),
    val editable: Boolean = false,
) {
    fun styleFor(key: KeyLayoutInfo): KeyboardKeyStyle = keyOverrides[key.styleId] ?: when (key.category) {
        KeyColorCategory.ALPHA -> alphaStyle
        KeyColorCategory.MOD -> modifierStyle
        KeyColorCategory.ACCENT -> accentStyle
    }
}

object KeyboardThemeCatalog {
    val builtIns: List<KeyboardThemeDefinition> = BuiltinKeyboardTheme.entries.map { preset ->
        val palette = Colorways.PALETTES.getValue(preset.legacySource)
        KeyboardThemeDefinition(
            id = preset.id,
            name = preset.displayName,
            plateArgb = palette.bgCode.toArgb(),
            alphaStyle = KeyboardKeyStyle(palette.alphaBg.toArgb(), palette.alphaLegend.toArgb()),
            modifierStyle = KeyboardKeyStyle(palette.modBg.toArgb(), palette.modLegend.toArgb()),
            accentStyle = KeyboardKeyStyle(palette.accentBg.toArgb(), palette.accentLegend.toArgb()),
        )
    }

    val defaultTheme: KeyboardThemeDefinition = builtIns.first { it.id == BuiltinKeyboardTheme.OBLIVION.id }

    fun builtIn(id: String?): KeyboardThemeDefinition? = builtIns.firstOrNull { it.id == id }

    fun previewColors(theme: KeyboardThemeDefinition): List<Color> = listOf(
        Color(theme.plateArgb),
        Color(theme.alphaStyle.backgroundArgb),
        Color(theme.accentStyle.backgroundArgb),
    )
}
