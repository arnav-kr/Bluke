package dev.arnv.bluke.data

import dev.arnv.bluke.ui.CUSTOM_KEYBOARD_THEME_PREFIX
import dev.arnv.bluke.ui.KeyboardKeyStyle
import dev.arnv.bluke.ui.KeyboardThemeCatalog
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import dev.arnv.bluke.ui.ThemeQrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
class KeyboardThemeSharingTest {
    private val theme = KeyboardThemeDefinition(
        id = "${CUSTOM_KEYBOARD_THEME_PREFIX}original",
        name = "Sunset | *bold*",
        plateArgb = 0xFF101820.toInt(),
        caseArgb = 0xFF332211.toInt(),
        caseMetallic = true,
        alphaStyle = KeyboardKeyStyle(0xFFEEDDCC.toInt(), 0xFF112233.toInt()),
        modifierStyle = KeyboardKeyStyle(0xFF998877.toInt(), 0xFF000000.toInt(), legendScale = 1.2f),
        accentStyle = KeyboardKeyStyle(0xFFFF5500.toInt(), 0xFFFFFFFF.toInt()),
        keyOverrides = mapOf("44:0" to KeyboardKeyStyle(0xFF00FF00.toInt(), 0xFF000000.toInt(), 0.8f)),
        editable = true,
    )

    private fun assertSameLook(expected: KeyboardThemeDefinition, actual: KeyboardThemeDefinition) {
        assertEquals(expected.name, actual.name)
        assertEquals(expected.plateArgb, actual.plateArgb)
        assertEquals(expected.caseArgb, actual.caseArgb)
        assertEquals(expected.caseMetallic, actual.caseMetallic)
        assertEquals(expected.alphaStyle, actual.alphaStyle)
        assertEquals(expected.modifierStyle, actual.modifierStyle)
        assertEquals(expected.accentStyle, actual.accentStyle)
        assertEquals(expected.keyOverrides, actual.keyOverrides)
        assertTrue(actual.editable)
        assertTrue(actual.id.startsWith(CUSTOM_KEYBOARD_THEME_PREFIX))
        assertNotEquals(expected.id, actual.id)
    }

    @Test
    fun jsonRoundTripKeepsEveryColorAndGetsANewId() {
        assertSameLook(theme, KeyboardThemeSharing.parse(KeyboardThemeSharing.toJsonText(theme)))
    }

    @Test
    fun linkRoundTrip() {
        val link = KeyboardThemeSharing.toLink(theme)
        assertTrue(link.startsWith("simplybluke://theme?d="))
        assertSameLook(theme, KeyboardThemeSharing.parse(link))
    }

    @Test
    fun markdownRoundTripAndLinkInsideText() {
        val markdown = KeyboardThemeSharing.toMarkdown(theme)
        assertTrue(markdown.contains("```json"))
        assertTrue(markdown.contains("Sunset \\| \\*bold\\*"))
        assertTrue(!markdown.contains("simplybluke://"))
        assertSameLook(theme, KeyboardThemeSharing.parse(markdown))
        // A link pasted with surrounding chat text still works.
        val chat = "Try my theme: ${KeyboardThemeSharing.toLink(theme)} enjoy!"
        assertSameLook(theme, KeyboardThemeSharing.parse(chat))
    }

    /** Draws [link] as a QR code at [scale], placed at ([left], [top]) on a [canvas]-sized grey picture. */
    private fun qrPixels(link: String, scale: Int, canvas: Int? = null, left: Int = 0, top: Int = 0): Triple<IntArray, Int, Int> {
        val matrix = ThemeQrCode.encode(link)
        assertNotNull(matrix)
        val qrSize = matrix!!.width * scale
        val size = canvas ?: qrSize
        val pixels = IntArray(size * size) { 0xFF6E6E6E.toInt() }
        for (y in 0 until qrSize) for (x in 0 until qrSize) {
            pixels[(y + top) * size + x + left] =
                if (matrix.get(x / scale, y / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Triple(pixels, size, size)
    }

    @Test
    fun everyBuiltInThemeFitsInAQrCodeAndReadsBack() {
        KeyboardThemeCatalog.builtIns.forEach { builtIn ->
            val link = KeyboardThemeSharing.toLink(builtIn)
            listOf(3, 4, 7, 12).forEach { scale ->
                val (pixels, width, height) = qrPixels(link, scale)
                assertEquals("${builtIn.name} at scale $scale", link, ThemeQrCode.decode(pixels, width, height))
            }
            assertSameLook(builtIn.copy(editable = true), KeyboardThemeSharing.parse(link))
        }
    }

    @Test
    fun qrCodeInsideALargerScreenshotIsFound() {
        val link = KeyboardThemeSharing.toLink(theme)
        val (pixels, width, height) = qrPixels(link, scale = 6, canvas = 900, left = 170, top = 260)
        assertEquals(link, ThemeQrCode.decode(pixels, width, height))
    }

    @Test
    fun cameraFrameWithRowPaddingIsRead() {
        val link = KeyboardThemeSharing.toLink(theme)
        // A 640x480 grey frame with the code off-centre, like a camera Y plane whose
        // rows are padded to a 704-byte stride.
        val (pixels, size, _) = qrPixels(link, scale = 5, canvas = 480, left = 40, top = 30)
        val width = 640
        val stride = 704
        val luminance = ByteArray(stride * size) { 0x80.toByte() }
        for (y in 0 until size) for (x in 0 until size) {
            luminance[y * stride + x + 60] = (pixels[y * size + x] and 0xFF).toByte()
        }
        assertEquals(link, ThemeQrCode.decodeLuminance(luminance, stride, width, size))
        assertEquals(null, ThemeQrCode.decodeLuminance(ByteArray(stride * size) { 0x80.toByte() }, stride, width, size))
    }

    @Test
    fun themeWithManyKeyOverridesStillFitsInAQrCode() {
        val busy = theme.copy(
            keyOverrides = (0 until 110).associate { code ->
                "$code:0" to KeyboardKeyStyle(0xFF000000.toInt() or (code * 2311), 0xFFFFFFFF.toInt(), 1f)
            },
        )
        assertNotNull(ThemeQrCode.encode(KeyboardThemeSharing.toLink(busy)))
    }

    @Test
    fun acceptsAppStorageIntegerColorsAndForcesOpaque() {
        val json = """
            {"format":"simplybluke-keyboard-theme","version":1,"name":"Ints",
             "case":${0x00123456},"plate":"#000000",
             "alpha":{"background":"#FFFFFF","legend":"#000000"},
             "modifier":{"background":"#777777","legend":"#000000","legendScale":9},
             "accent":{"background":"#FF0000","legend":"#FFFFFF"}}
        """.trimIndent()
        val parsed = KeyboardThemeSharing.parse(json)
        assertEquals(0xFF123456.toInt(), parsed.caseArgb)
        assertEquals(1.5f, parsed.modifierStyle.legendScale)
    }

    @Test
    fun rejectsWrongFormatNewerVersionsAndBadColors() {
        val valid = KeyboardThemeSharing.toJson(theme)
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse(valid.put("format", "something-else").toString())
        }
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse(KeyboardThemeSharing.toJson(theme).put("version", 99).toString())
        }
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse(KeyboardThemeSharing.toJson(theme).put("plate", "red").toString())
        }
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse(KeyboardThemeSharing.toJson(theme).apply { remove("alpha") }.toString())
        }
        assertThrows(KeyboardThemeSharing.ImportException::class.java) { KeyboardThemeSharing.parse("hello") }
        assertThrows(KeyboardThemeSharing.ImportException::class.java) { KeyboardThemeSharing.parse("{not json") }
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse("simplybluke://theme?d=%%%")
        }
    }

    @Test
    fun truncatedLinkIsRejected() {
        val link = KeyboardThemeSharing.toLink(theme)
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse(link.dropLast(20))
        }
    }

    @Test
    fun compressionBombIsRejected() {
        val huge = ByteArray(5 * 1024 * 1024) { 'a'.code.toByte() }
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(huge); finish() }
        val buffer = ByteArray(64 * 1024)
        val size = deflater.deflate(buffer)
        deflater.end()
        val link = "simplybluke://theme?d=" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.copyOf(size))
        assertThrows(KeyboardThemeSharing.ImportException::class.java) { KeyboardThemeSharing.parse(link) }
    }

    @Test
    fun ignoresMalformedOverrideKeysAndCleansName() {
        val json = KeyboardThemeSharing.toJson(theme)
            .put("name", "  Evil\u0000\nname that is far too long to be shown anywhere at all  ")
        json.getJSONObject("overrides").put("../../etc", json.getJSONObject("alpha"))
        val parsed = KeyboardThemeSharing.parse(json.toString())
        assertEquals(setOf("44:0"), parsed.keyOverrides.keys)
        assertEquals(40, parsed.name.length)
        assertTrue(parsed.name.none { it.isISOControl() })
    }

    @Test
    fun oversizedInputIsRejectedBeforeParsing() {
        assertThrows(KeyboardThemeSharing.ImportException::class.java) {
            KeyboardThemeSharing.parse("x".repeat(KeyboardThemeSharing.MAX_INPUT_CHARS + 1))
        }
    }
}
