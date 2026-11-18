package dev.arnv.bluke.ui

import android.content.Context
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.content.edit
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
class GamepadScreenshotTest {
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

    private fun capture(themeMode: Int, filePath: String) {
        // Fixed accent instead of wallpaper colors, so the images are deterministic.
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("dynamic_color", false)
            putInt("theme_mode", themeMode)
        }
        val preferences = context.getSharedPreferences("gamepad-screenshot", Context.MODE_PRIVATE)
        composeTestRule.setContent {
            // Same wrapper as HomeScreen: the gamepad always uses the dark palette.
            MyApplicationTheme(forceDark = true) {
                GamepadView(
                    btManager = manager,
                    onClose = {},
                    launchMode = InputMode.GAMEPAD.id,
                    onModeChange = {},
                    sharedPrefs = preferences,
                    caseBrush = SolidColor(Color.Black),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(filePath = filePath)
    }

    @Test
    fun gamepadUsesDarkPalette() = capture(themeMode = 2, filePath = "src/test/screenshots/gamepad-dark.png")

    @Test
    fun gamepadStaysDarkWithLightAppTheme() =
        capture(themeMode = 1, filePath = "src/test/screenshots/gamepad-light-app-theme.png")
}
