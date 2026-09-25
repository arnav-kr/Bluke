package dev.arnv.bluke.ui

import android.content.Context
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
    }

    @Test
    fun mediaPresentationUsesOnePrimaryControlSurface() {
        val preferences = context.getSharedPreferences("media-presentation-screenshot", Context.MODE_PRIVATE)
        composeTestRule.setContent {
            MyApplicationTheme {
                MediaPresentationView(
                    btManager = manager,
                    onClose = {},
                    launchMode = InputMode.MEDIA_PRESENTATION.id,
                    onModeChange = {},
                    sharedPrefs = preferences,
                    caseBrush = SolidColor(CaseColor.BLACK.caseColor),
                    isConnected = true,
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/media-presentation.png",
        )

        composeTestRule.onNodeWithContentDescription("Show presentation tools").performClick()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/media-presentation-tools.png",
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
                    caseBrush = SolidColor(CaseColor.BLACK.caseColor),
                    selectedCaseColor = CaseColor.BLACK,
                    onCaseColorChange = {},
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/touchpad-modifier-rails.png",
        )
    }
}
