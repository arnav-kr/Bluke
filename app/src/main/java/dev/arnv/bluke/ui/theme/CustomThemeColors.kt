package dev.arnv.bluke.ui.theme

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

/** WCAG relative luminance of an ARGB color (alpha ignored). */
internal fun relativeLuminance(argb: Int): Double {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear(redChannel(argb)) +
        0.7152 * linear(greenChannel(argb)) +
        0.0722 * linear(blueChannel(argb))
}

/** WCAG contrast ratio between two colors, from 1.0 (same) to 21.0 (black on white). */
internal fun contrastRatio(first: Int, second: Int): Double {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** HSV to opaque ARGB without android.graphics, so it runs in plain unit tests. */
internal fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val sector = h.toInt()
    val fraction = h - sector
    val p = v * (1 - s)
    val q = v * (1 - s * fraction)
    val t = v * (1 - s * (1 - fraction))
    val (r, g, b) = when (sector) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return opaqueRgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
}

/** ARGB to HSV (hue 0–360, saturation and value 0–1). */
internal fun argbToHsv(argb: Int): FloatArray {
    val r = redChannel(argb) / 255f
    val g = greenChannel(argb) / 255f
    val b = blueChannel(argb) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hue, saturation, max)
}
