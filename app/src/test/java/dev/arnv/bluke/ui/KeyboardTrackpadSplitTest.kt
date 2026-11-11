package dev.arnv.bluke.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardTrackpadSplitTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BluetoothKeyboardManager(context)
    private val preferences = context.getSharedPreferences("keyboard-trackpad-split-test", Context.MODE_PRIVATE)

    @After
    fun closeManager() {
        manager.close()
    }

    private fun show(isPortrait: Boolean, side: TrackpadSide, style: KeyboardStyle) {
        val rows = TouchKeyboardLayouts.getLayout(TouchLayer.BASE)
        val aspect = if (isPortrait) 1.25f else 1.45f
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    KeyboardTrackpadSplit(
                        showTrackpad = true,
                        isPortrait = isPortrait,
                        trackpadSide = side,
                        style = style,
                        keyboardRows = rows,
                        keyAspect = aspect,
                        modifier = Modifier.fillMaxSize(),
                        trackpad = { modifier ->
                            KeyboardTrackpadPanel(manager, preferences, style, modifier)
                        },
                        keyboard = { modifier ->
                            Box(modifier) {
                                KeyboardView(
                                    geometry = KeyboardGeometry.COMPACT_75,
                                    theme = KeyboardThemeCatalog.defaultTheme,
                                    activePressedKeys = emptyList(),
                                    isCapsLockActive = false,
                                    isNumLockActive = false,
                                    isScrollLockActive = false,
                                    rowsOverride = rows,
                                    fillHeight = true,
                                    maxKeyAspect = aspect,
                                    legendScale = 1.5f,
                                    style = style,
                                    onKeyPressChange = { _, _ -> },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    private fun trackpadBounds() = composeTestRule.onNodeWithTag("keyboard_trackpad").getBoundsInRoot()
    private fun keyBounds() = composeTestRule.onNodeWithTag("key_q", useUnmergedTree = true).getBoundsInRoot()

    @Test
    @Config(qualifiers = "w360dp-h740dp-port-xhdpi", sdk = [36])
    fun portraitPutsTheTrackpadAboveTheKeyboard() {
        show(isPortrait = true, side = TrackpadSide.RIGHT, style = KeyboardStyle.MINIMAL)
        assertTrue(trackpadBounds().bottom <= keyBounds().top)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/keyboard-trackpad-portrait-minimal.png")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi", sdk = [36])
    fun landscapeTrackpadCanSitOnTheLeft() {
        show(isPortrait = false, side = TrackpadSide.LEFT, style = KeyboardStyle.MECHANICAL)
        assertTrue(trackpadBounds().right <= keyBounds().left)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/keyboard-trackpad-landscape-left.png")
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xhdpi", sdk = [36])
    fun landscapeTrackpadCanSitOnTheRight() {
        show(isPortrait = false, side = TrackpadSide.RIGHT, style = KeyboardStyle.MINIMAL)
        assertTrue(trackpadBounds().left >= keyBounds().right)
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/keyboard-trackpad-landscape-right.png")
    }

    @Test
    @Config(sdk = [36])
    fun keyboardHeightLeavesExactlyTheRequestedKeyShape() {
        val rows = TouchKeyboardLayouts.getLayout(TouchLayer.BASE)
        // Minimal style: 2dp plate padding each side, so 4dp of chrome.
        val height = keyboardHeightForWidth(364.dp, rows, keyAspect = 1.25f, style = KeyboardStyle.MINIMAL)
        assertEquals(36f * 1.25f * 5f + 4f, height.value, 0.01f)
        assertEquals(TrackpadSide.LEFT, TrackpadSide.RIGHT.flipped())
        assertEquals(KeyboardStyle.MECHANICAL, KeyboardStyle.fromPreference(null))
    }
}
