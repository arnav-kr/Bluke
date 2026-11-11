package dev.arnv.bluke.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-port-xhdpi", sdk = [36])
class TouchKeyboardScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun showLayer(
        layer: TouchLayer,
        latched: Map<Int, LatchState> = emptyMap(),
        style: KeyboardStyle = KeyboardStyle.MECHANICAL,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxWidth().height(380.dp)) {
                    KeyboardView(
                        geometry = KeyboardGeometry.COMPACT_75,
                        theme = KeyboardThemeCatalog.defaultTheme,
                        activePressedKeys = emptyList(),
                        isCapsLockActive = false,
                        isNumLockActive = false,
                        isScrollLockActive = false,
                        rowsOverride = TouchKeyboardLayouts.getLayout(layer),
                        fillHeight = true,
                        latchedKeys = latched,
                        legendScale = 1.5f,
                        style = style,
                        onKeyPressChange = { _, _ -> },
                    )
                }
            }
        }
    }

    @Test
    fun portraitBaseLayerWithLatchedModifiers() {
        showLayer(
            TouchLayer.BASE,
            latched = mapOf(
                KeyboardLayouts.MOD_LCTRL to LatchState.ONE_SHOT,
                KeyboardLayouts.MOD_LSHIFT to LatchState.LOCKED,
            ),
        )
        composeTestRule.onNodeWithTag("latch_one_shot_Ctrl", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("latch_locked_Shift", useUnmergedTree = true).assertExists()
        // Locked Shift shows uppercase letters.
        composeTestRule.onNodeWithTag("key_Q", useUnmergedTree = true).assertExists()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/touch-keyboard-base.png")
    }

    @Test
    fun portraitSymbolsLayer() {
        showLayer(TouchLayer.SYMBOLS)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/touch-keyboard-symbols.png")
    }

    @Test
    fun portraitFnLayer() {
        showLayer(TouchLayer.FN)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/touch-keyboard-fn.png")
    }

    @Test
    fun portraitMinimalStyle() {
        showLayer(TouchLayer.BASE, style = KeyboardStyle.MINIMAL)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/touch-keyboard-minimal.png")
    }
}
