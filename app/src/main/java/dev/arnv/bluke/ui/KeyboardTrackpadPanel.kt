package dev.arnv.bluke.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager

/**
 * The trackpad shown next to the keyboard. It reuses the Touchpad mode's gesture
 * surface and settings (pointer speed, scroll speed, vibration), as a clickpad.
 */
@Composable
fun KeyboardTrackpadPanel(
    btManager: BluetoothKeyboardManager,
    sharedPrefs: SharedPreferences,
    style: KeyboardStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sensitivity = remember { sharedPrefs.getFloat("touchpad_sensitivity", 1.5f) }
    val scrollSensitivity = remember { sharedPrefs.getFloat("touchpad_scroll_sensitivity", 1.0f) }
    val vibrationEnabled = remember { sharedPrefs.getBoolean("touchpad_vibration_enabled", true) }
    val vibrate = remember(context, vibrationEnabled) {
        { milliseconds: Long ->
            if (vibrationEnabled) {
                try {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) {
                    // Vibration is optional.
                }
            }
        }
    }

    val minimal = style == KeyboardStyle.MINIMAL
    val shape = RoundedCornerShape(if (minimal) 6.dp else 10.dp)
    Box(
        modifier = modifier
            .then(
                if (minimal) Modifier
                else Modifier.shadow(4.dp, shape, spotColor = Color.Black.copy(alpha = 0.5f), ambientColor = Color.Black)
            )
            .clip(shape)
            .background(Color(0xFF1E1E1E))
            .border(if (minimal) 1.dp else 1.5.dp, Color.White.copy(alpha = if (minimal) 0.08f else 0.15f), shape)
            .testTag("keyboard_trackpad"),
    ) {
        TouchGestureLayer(
            btManager = btManager,
            sensitivity = sensitivity,
            scrollSensitivity = scrollSensitivity,
            buttonMode = TrackpadButtonMode.CLICKPAD,
            triggerVibration = vibrate,
            showNumpadLed = false,
        )
    }
}

/** Space between the trackpad and the keyboard. */
internal fun trackpadGap(style: KeyboardStyle): Dp = if (style == KeyboardStyle.MINIMAL) 2.dp else 6.dp

/**
 * Places the keyboard alone, or with the trackpad above it (portrait) or
 * beside it on [trackpadSide] (landscape).
 */
@Composable
fun KeyboardTrackpadSplit(
    showTrackpad: Boolean,
    isPortrait: Boolean,
    trackpadSide: TrackpadSide,
    style: KeyboardStyle,
    keyboardRows: List<List<KeyLayoutInfo>>,
    keyAspect: Float,
    modifier: Modifier = Modifier,
    trackpad: @Composable (Modifier) -> Unit,
    keyboard: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val gap = trackpadGap(style)
        val areaWidth = maxWidth
        val areaHeight = maxHeight
        when {
            !showTrackpad -> keyboard(Modifier.fillMaxSize())
            isPortrait -> Column(Modifier.fillMaxSize()) {
                val keyboardHeight = keyboardHeightForWidth(areaWidth, keyboardRows, keyAspect, style)
                    .coerceAtMost(areaHeight * 0.7f)
                trackpad(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = gap, end = gap, top = gap),
                )
                keyboard(Modifier.fillMaxWidth().height(keyboardHeight))
            }
            else -> Row(Modifier.fillMaxSize()) {
                val trackpadModifier = Modifier.weight(0.36f).fillMaxHeight().padding(gap)
                if (trackpadSide == TrackpadSide.LEFT) trackpad(trackpadModifier)
                keyboard(Modifier.weight(0.64f).fillMaxHeight())
                if (trackpadSide == TrackpadSide.RIGHT) trackpad(trackpadModifier)
            }
        }
    }
}
