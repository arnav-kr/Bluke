package dev.arnv.bluke.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h360dp-land-xhdpi", sdk = [36])
class MediaPresentationScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BluetoothKeyboardManager(context)

    @After
    fun closeManager() {
        manager.close()
        // Theme overrides set by a test must not leak into the next one.
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun multimediaMatchesLandscapeControlLayout() {
        val preferences = context.getSharedPreferences("multimedia-landscape-screenshot", Context.MODE_PRIVATE)
        composeTestRule.setContent {
            MyApplicationTheme(forceDark = true) {
                MediaPresentationView(
                    btManager = manager,
                    onClose = {},
                    launchMode = InputMode.MEDIA_PRESENTATION.id,
                    onModeChange = {},
                    sharedPrefs = preferences,
                    isConnected = false,
                    postureOverride = MultimediaPosture.LANDSCAPE,
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/multimedia-landscape.png",
        )
    }

    @Test
    fun multimediaMatchesUprightControlLayout() {
        val preferences = context.getSharedPreferences("multimedia-upright-screenshot", Context.MODE_PRIVATE)
        composeTestRule.setContent {
            MyApplicationTheme(forceDark = true) {
                MediaPresentationView(
                    btManager = manager,
                    onClose = {},
                    launchMode = InputMode.MEDIA_PRESENTATION.id,
                    onModeChange = {},
                    sharedPrefs = preferences,
                    isConnected = false,
                    postureOverride = MultimediaPosture.PORTRAIT_HELD,
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/multimedia-upright.png",
        )
    }

    @Test
    fun multimediaFollowsDarkTheme() {
        // Fixed accent instead of wallpaper colors, so the image is deterministic.
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("dynamic_color", false)
            .putInt("theme_mode", 2)
            .commit()
        val preferences = context.getSharedPreferences("multimedia-dark-screenshot", Context.MODE_PRIVATE)
        composeTestRule.setContent {
            MyApplicationTheme(forceDark = true) {
                MediaPresentationView(
                    btManager = manager,
                    onClose = {},
                    launchMode = InputMode.MEDIA_PRESENTATION.id,
                    onModeChange = {},
                    sharedPrefs = preferences,
                    isConnected = true,
                    postureOverride = MultimediaPosture.LANDSCAPE,
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/multimedia-dark.png",
        )
    }

    @Test
    fun touchpadModifierKeysOccupyDedicatedSideRail() {
        val preferences = context.getSharedPreferences("touchpad-screenshot", Context.MODE_PRIVATE)
        preferences.edit()
            .putString(TOUCHPAD_MODIFIER_POSITION_PREFERENCE, TouchpadModifierPosition.LEFT.preferenceValue)
            .commit()
        composeTestRule.setContent {
            MyApplicationTheme {
                TouchpadView(
                    btManager = manager,
                    onClose = {},
                    launchMode = InputMode.TOUCHPAD.id,
                    onModeChange = {},
                    sharedPrefs = preferences,
                    caseBrush = SolidColor(Color(0xFF1E1E20)),
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/touchpad-modifier-rails.png",
        )
    }
}
