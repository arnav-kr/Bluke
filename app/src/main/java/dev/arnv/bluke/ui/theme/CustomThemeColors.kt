package dev.arnv.bluke.ui.theme

internal const val CUSTOM_THEME_ENABLED = "custom_theme_enabled"
internal const val CUSTOM_THEME_BACKGROUND = "custom_theme_background"
internal const val CUSTOM_THEME_SURFACE = "custom_theme_surface"
internal const val CUSTOM_THEME_ACCENT = "custom_theme_accent"

internal const val DEFAULT_CUSTOM_BACKGROUND = 0xFF141218.toInt()
internal const val DEFAULT_CUSTOM_SURFACE = 0xFF2B2930.toInt()
internal const val DEFAULT_CUSTOM_ACCENT = 0xFFD4E3FF.toInt()

internal fun parseOpaqueHexColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return (0xFF000000L or hex.toLong(16)).toInt()
}

internal fun formatOpaqueHexColor(argb: Int): String =
    "#%06X".format(argb and 0x00FFFFFF)

internal fun opaqueRgb(red: Int, green: Int, blue: Int): Int =
    (0xFF000000L or
        (red.coerceIn(0, 255).toLong() shl 16) or
        (green.coerceIn(0, 255).toLong() shl 8) or
        blue.coerceIn(0, 255).toLong()).toInt()

internal fun redChannel(argb: Int): Int = argb shr 16 and 0xFF
internal fun greenChannel(argb: Int): Int = argb shr 8 and 0xFF
internal fun blueChannel(argb: Int): Int = argb and 0xFF

internal fun contrastingContentColor(argb: Int): Int {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    val luminance = 0.2126 * linear(argb shr 16 and 0xFF) +
        0.7152 * linear(argb shr 8 and 0xFF) +
        0.0722 * linear(argb and 0xFF)
    return if (luminance > 0.179) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
