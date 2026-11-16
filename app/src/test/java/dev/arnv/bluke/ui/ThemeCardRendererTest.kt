package dev.arnv.bluke.ui

import dev.arnv.bluke.data.KeyboardThemeSharing
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeCardRendererTest {
    @Test
    fun cardQrCodeScansBackToTheTheme() {
        KeyboardThemeCatalog.builtIns.forEach { theme ->
            val link = KeyboardThemeSharing.toLink(theme)
            val card = ThemeCardRenderer.render(theme, ThemeQrCode.encode(link)!!)
            assertEquals(ThemeCardRenderer.WIDTH, card.width)
            assertEquals(ThemeCardRenderer.HEIGHT, card.height)
            assertEquals(theme.name, link, ThemeQrCode.decode(card))
        }
    }

    @Test
    fun veryLongNamesStillRender() {
        val theme = KeyboardThemeCatalog.defaultTheme.copy(name = "W".repeat(40))
        val card = ThemeCardRenderer.render(theme, ThemeQrCode.encode(KeyboardThemeSharing.toLink(theme))!!)
        assertEquals(ThemeCardRenderer.WIDTH, card.width)
    }
}
