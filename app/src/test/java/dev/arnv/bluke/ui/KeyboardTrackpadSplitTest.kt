package dev.arnv.bluke.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.doubleClick
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

    private fun show(
        isPortrait: Boolean,
        side: TrackpadSide,
        style: KeyboardStyle,
        heightFraction: Float? = null,
        onHeightFractionChange: (Float?) -> Unit = {},
        laptopLayout: Boolean = false,
        laptopTrackpadBelow: Boolean = true,
    ) {
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
                        heightFraction = heightFraction,
                        onHeightFractionChange = onHeightFractionChange,
                        laptopLayout = laptopLayout,
                        laptopTrackpadBelow = laptopTrackpadBelow,
                        modifier = Modifier.fillMaxSize(),
                        trackpad = { modifier ->
                            KeyboardTrackpadPanel(manager, preferences, style, modifier)
                        },
                        keyboard = { modifier, _ ->
                            Box(modifier.testTag("keyboard_area")) {
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
    @Config(qualifiers = "w360dp-h740dp-port-xhdpi", sdk = [36])
    fun savedHeightIsAShareOfTheScreenAndTheTrackpadTakesTheRest() {
        show(isPortrait = true, side = TrackpadSide.RIGHT, style = KeyboardStyle.MINIMAL, heightFraction = 0.4f)
        val keyboard = composeTestRule.onNodeWithTag("keyboard_area").getBoundsInRoot()
        val screenHeight = context.resources.configuration.screenHeightDp.toFloat()
        assertEquals(screenHeight * 0.4f, (keyboard.bottom - keyboard.top).value, 1f)
        assertTrue(trackpadBounds().bottom <= keyboard.top)
        composeTestRule.onNodeWithTag("keyboard_height_handle").assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp-port-xhdpi", sdk = [36])
    fun handleDragSavesAShareAndDoubleTapGoesBackToAutomatic() {
        val saved = mutableListOf<Float?>()
        show(
            isPortrait = true,
            side = TrackpadSide.RIGHT,
            style = KeyboardStyle.MINIMAL,
            heightFraction = 0.4f,
            onHeightFractionChange = { saved += it },
        )
        val handle = composeTestRule.onNodeWithTag("keyboard_height_handle")
        handle.performTouchInput { swipeUp(startY = centerY, endY = centerY - 150f, durationMillis = 300) }
        composeTestRule.waitForIdle()
        val dragged = saved.single()!!
        assertTrue("drag up should make the keyboard taller, got $dragged", dragged > 0.4f)
        handle.performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()
        assertEquals(null, saved.last())
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-port-xhdpi", sdk = [36])
    fun laptopLayoutPutsANarrowerTrackpadBelowTheKeyboard() {
        show(isPortrait = true, side = TrackpadSide.RIGHT, style = KeyboardStyle.MINIMAL, laptopLayout = true)
        val keyboard = composeTestRule.onNodeWithTag("keyboard_area").getBoundsInRoot()
        val trackpad = trackpadBounds()
        assertTrue(trackpad.top >= keyboard.bottom)
        assertTrue(trackpad.right - trackpad.left < keyboard.right - keyboard.left)
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-land-xhdpi", sdk = [36])
    fun laptopLayoutCanPutTheTrackpadAboveEvenInLandscape() {
        show(
            isPortrait = false,
            side = TrackpadSide.RIGHT,
            style = KeyboardStyle.MINIMAL,
            laptopLayout = true,
            laptopTrackpadBelow = false,
        )
        assertTrue(trackpadBounds().bottom <= composeTestRule.onNodeWithTag("keyboard_area").getBoundsInRoot().top)
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-land-xhdpi", sdk = [36])
    fun laptopLayoutNeverPutsTheTrackpadBeside() {
        show(isPortrait = false, side = TrackpadSide.LEFT, style = KeyboardStyle.MINIMAL, laptopLayout = true)
        assertTrue(trackpadBounds().top >= composeTestRule.onNodeWithTag("keyboard_area").getBoundsInRoot().bottom)
    }

    @Test
    fun laptopTrackpadPositions() {
        assertEquals(LaptopTrackpadPosition.BELOW, LaptopTrackpadPosition.fromPreference(null))
        assertEquals(LaptopTrackpadPosition.ABOVE, LaptopTrackpadPosition.BELOW.toggled())
        assertTrue(isWideEnoughForLaptopLayout(600))
        assertTrue(!isWideEnoughForLaptopLayout(599))
    }

    @Test
    @Config(sdk = [36])
    fun keyboardHeightLeavesExactlyTheRequestedKeyShape() {
        val rows = TouchKeyboardLayouts.getLayout(TouchLayer.BASE)
        // Minimal style: 2dp plate padding each side, so 4dp of chrome.
        val height = keyboardHeightForWidth(364.dp, rows, keyAspect = 1.25f, style = KeyboardStyle.MINIMAL)
        assertEquals(36f * 1.25f * 5f + 4f, height.value, 0.01f)
        assertEquals(TrackpadSide.LEFT, TrackpadSide.RIGHT.flipped())
        assertEquals(KeyboardStyle.MINIMAL, KeyboardStyle.fromPreference(null))
    }
}
