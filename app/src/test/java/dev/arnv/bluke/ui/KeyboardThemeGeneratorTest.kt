package dev.arnv.bluke.ui

import dev.arnv.bluke.ui.theme.argbToHsv
import dev.arnv.bluke.ui.theme.contrastRatio
import dev.arnv.bluke.ui.theme.hsvToArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardThemeGeneratorTest {
    private val samples = listOf(
        0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFF7F00FF, 0xFF808080,
        0xFF000000, 0xFFFFFFFF, 0xFFE0B0FF, 0xFF0A3D2F, 0xFFFF8800,
    ).map { it.toInt() }

    @Test
    fun contrastRatioMatchesWcagEndpoints() {
        assertEquals(21.0, contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, contrastRatio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001)
    }

    @Test
    fun hsvRoundTripStaysClose() {
        samples.forEach { argb ->
            val hsv = argbToHsv(argb)
            val back = hsvToArgb(hsv[0], hsv[1], hsv[2])
            listOf(16, 8, 0).forEach { shift ->
                val delta = kotlin.math.abs((argb shr shift and 0xFF) - (back shr shift and 0xFF))
                assertTrue("channel off by $delta for ${Integer.toHexString(argb)}", delta <= 1)
            }
        }
    }

    @Test
    fun generatedThemesHaveReadableLegendsInDarkAndLight() {
        samples.forEach { base ->
            listOf(true, false).forEach { dark ->
                val theme = KeyboardThemeGenerator.fromBaseColor("custom:test", base, dark)
                listOf(theme.alphaStyle, theme.modifierStyle, theme.accentStyle).forEach { style ->
                    assertTrue(
                        "base ${Integer.toHexString(base)} dark=$dark",
                        contrastRatio(style.backgroundArgb, style.legendArgb) >= 4.5,
                    )
                }
                assertEquals(base, theme.accentStyle.backgroundArgb)
                assertTrue(KeyboardThemeGenerator.lowContrastParts(theme).isEmpty())
            }
        }
    }

    @Test
    fun blankThemeIsReadable() {
        assertTrue(KeyboardThemeGenerator.lowContrastParts(KeyboardThemeGenerator.blank("custom:x")).isEmpty())
    }

    @Test
    fun lowContrastPartsFlagsGroupsAndSingleKeys() {
        val grey = 0xFF808080.toInt()
        val theme = KeyboardThemeGenerator.blank("custom:x").copy(
            modifierStyle = KeyboardKeyStyle(grey, grey),
            keyOverrides = mapOf("4:0" to KeyboardKeyStyle(grey, 0xFF8A8A8A.toInt())),
        )
        assertEquals(listOf("Control keys", "1 single key"), KeyboardThemeGenerator.lowContrastParts(theme))
    }

    @Test
    fun readableLegendKeepsGoodColorsAndFixesBadOnes() {
        val navy = 0xFF001F3F.toInt()
        assertEquals(0xFFFFFFFF.toInt(), KeyboardThemeGenerator.readableLegend(navy, 0xFFFFFFFF.toInt()))
        val fixed = KeyboardThemeGenerator.readableLegend(navy, 0xFF002244.toInt())
        assertTrue(contrastRatio(navy, fixed) >= 4.5)
    }
}
