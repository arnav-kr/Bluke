package dev.arnv.bluke.ui

import dev.arnv.bluke.ui.theme.argbToHsv
import dev.arnv.bluke.ui.theme.contrastRatio
import dev.arnv.bluke.ui.theme.hsvToArgb

/** Below this, key text is hard to read; the editor warns about it. */
const val MIN_LEGEND_CONTRAST = 3.0

/** Starting points for themes made from scratch instead of copied. */
object KeyboardThemeGenerator {
    /** A plain grey keyboard to edit by hand. */
    fun blank(id: String, name: String = "New theme"): KeyboardThemeDefinition = KeyboardThemeDefinition(
        id = id,
        name = name,
        caseArgb = 0xFF2A2A2D.toInt(),
        plateArgb = 0xFF1B1B1D.toInt(),
        alphaStyle = KeyboardKeyStyle(0xFFE4E4E7.toInt(), 0xFF1B1B1D.toInt()),
        modifierStyle = KeyboardKeyStyle(0xFF9B9BA1.toInt(), 0xFF111113.toInt()),
        accentStyle = KeyboardKeyStyle(0xFF56565C.toInt(), 0xFFF4F4F5.toInt()),
        editable = true,
    )

    /**
     * Builds a whole theme around [baseArgb]: the accent keys use it as is, the other
     * parts are tints and shades of the same hue. Every legend reaches at least 4.5:1.
     */
    fun fromBaseColor(id: String, baseArgb: Int, dark: Boolean, name: String = "New theme"): KeyboardThemeDefinition {
        val (hue, saturation, _) = argbToHsv(baseArgb).let { Triple(it[0], it[1], it[2]) }
        fun tone(sat: Float, value: Float) = hsvToArgb(hue, saturation * sat, value)
        val accent = baseArgb or 0xFF000000.toInt()
        return if (dark) {
            KeyboardThemeDefinition(
                id = id,
                name = name,
                caseArgb = tone(0.35f, 0.17f),
                plateArgb = tone(0.30f, 0.10f),
                alphaStyle = KeyboardKeyStyle(tone(0.22f, 0.24f), readableLegend(tone(0.22f, 0.24f), tone(0.12f, 0.95f))),
                modifierStyle = KeyboardKeyStyle(tone(0.45f, 0.36f), readableLegend(tone(0.45f, 0.36f), tone(0.10f, 0.97f))),
                accentStyle = KeyboardKeyStyle(accent, readableLegend(accent, tone(0.10f, 0.97f))),
                editable = true,
            )
        } else {
            KeyboardThemeDefinition(
                id = id,
                name = name,
                caseArgb = tone(0.25f, 0.86f),
                plateArgb = tone(0.22f, 0.72f),
                alphaStyle = KeyboardKeyStyle(tone(0.06f, 0.97f), readableLegend(tone(0.06f, 0.97f), tone(0.55f, 0.20f))),
                modifierStyle = KeyboardKeyStyle(tone(0.35f, 0.80f), readableLegend(tone(0.35f, 0.80f), tone(0.60f, 0.18f))),
                accentStyle = KeyboardKeyStyle(accent, readableLegend(accent, tone(0.60f, 0.15f))),
                editable = true,
            )
        }
    }

    /**
     * Keeps [preferred] when it reads well on [background]; otherwise returns a
     * near-white or near-black in the same hue, whichever contrasts more.
     */
    fun readableLegend(background: Int, preferred: Int, minimum: Double = 4.5): Int {
        if (contrastRatio(background, preferred) >= minimum) return preferred or 0xFF000000.toInt()
        val hue = argbToHsv(preferred)[0]
        val light = hsvToArgb(hue, 0.06f, 0.98f)
        val darkTone = hsvToArgb(hue, 0.30f, 0.10f)
        val candidates = listOf(light, darkTone, 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        return candidates.firstOrNull { contrastRatio(background, it) >= minimum }
            ?: candidates.maxBy { contrastRatio(background, it) }
    }

    /** Parts of [theme] whose key text falls below [MIN_LEGEND_CONTRAST]. */
    fun lowContrastParts(theme: KeyboardThemeDefinition): List<String> = buildList {
        if (contrastRatio(theme.alphaStyle.backgroundArgb, theme.alphaStyle.legendArgb) < MIN_LEGEND_CONTRAST) add("Alpha keys")
        if (contrastRatio(theme.modifierStyle.backgroundArgb, theme.modifierStyle.legendArgb) < MIN_LEGEND_CONTRAST) add("Control keys")
        if (contrastRatio(theme.accentStyle.backgroundArgb, theme.accentStyle.legendArgb) < MIN_LEGEND_CONTRAST) add("Accent keys")
        val badKeys = theme.keyOverrides.values.count { contrastRatio(it.backgroundArgb, it.legendArgb) < MIN_LEGEND_CONTRAST }
        if (badKeys > 0) add(if (badKeys == 1) "1 single key" else "$badKeys single keys")
    }
}
