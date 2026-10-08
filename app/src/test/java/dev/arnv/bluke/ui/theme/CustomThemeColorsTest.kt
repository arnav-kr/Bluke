package dev.arnv.bluke.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomThemeColorsTest {
    @Test
    fun parsesAndFormatsOpaqueRgbHex() {
        val color = parseOpaqueHexColor("#1aB2c3")

        assertEquals(0xFF1AB2C3.toInt(), color)
        assertEquals("#1AB2C3", formatOpaqueHexColor(color!!))
    }

    @Test
    fun rejectsIncompleteOrNonHexInput() {
        assertNull(parseOpaqueHexColor("#12345"))
        assertNull(parseOpaqueHexColor("#12GG34"))
        assertNull(parseOpaqueHexColor("#00112233"))
    }

    @Test
    fun choosesReadableBlackOrWhiteForAccentButtons() {
        assertEquals(0xFFFFFFFF.toInt(), contrastingContentColor(0xFF101010.toInt()))
        assertEquals(0xFF000000.toInt(), contrastingContentColor(0xFFF0E060.toInt()))
    }

    @Test
    fun composesAndExtractsRgbChannels() {
        val color = opaqueRgb(26, 178, 195)

        assertEquals(0xFF1AB2C3.toInt(), color)
        assertEquals(26, redChannel(color))
        assertEquals(178, greenChannel(color))
        assertEquals(195, blueChannel(color))
    }
}
