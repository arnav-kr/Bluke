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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
    val sensitivity by rememberTouchpadSpeed(
        sharedPrefs, TOUCHPAD_POINTER_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_POINTER_SPEED,
    )
    val scrollSensitivity by rememberTouchpadSpeed(
        sharedPrefs, TOUCHPAD_SCROLL_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_SCROLL_SPEED,
    )
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
    Box(
        modifier = modifier
            .cyberTrackpad(cornerRadius = if (minimal) 6.dp else 10.dp)
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
 *
 * When the keyboard sits at the bottom (portrait, or no trackpad) a handle above it sets its
 * height as a share of the screen height: drag to resize, double-tap to go back to automatic.
 * [heightFraction] is the saved height (null for automatic) and [onHeightFractionChange]
 * saves a new one. [keyboard] is told whether its height was set by hand, so it can fill it.
 *
 * With [laptopLayout] (wide screens) the trackpad is always above or below the keyboard, in
 * both orientations: below when [laptopTrackpadBelow], like a laptop.
 */
@Composable
fun KeyboardTrackpadSplit(
    showTrackpad: Boolean,
    isPortrait: Boolean,
    trackpadSide: TrackpadSide,
    style: KeyboardStyle,
    keyboardRows: List<List<KeyLayoutInfo>>,
    keyAspect: Float,
    heightFraction: Float?,
    onHeightFractionChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    laptopLayout: Boolean = false,
    laptopTrackpadBelow: Boolean = true,
    trackpad: @Composable (Modifier) -> Unit,
    keyboard: @Composable (Modifier, fixedHeight: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(modifier = modifier) {
        val gap = trackpadGap(style)
        val areaWidth = maxWidth
        val areaHeight = maxHeight
        // The laptop layout keeps the trackpad above or below the keyboard, never beside it.
        val trackpadBelow = showTrackpad && laptopLayout && laptopTrackpadBelow
        if (showTrackpad && !isPortrait && !laptopLayout) {
            Row(Modifier.fillMaxSize()) {
                val trackpadModifier = Modifier.weight(0.36f).fillMaxHeight().padding(gap)
                if (trackpadSide == TrackpadSide.LEFT) trackpad(trackpadModifier)
                keyboard(Modifier.weight(0.64f).fillMaxHeight(), false)
                if (trackpadSide == TrackpadSide.RIGHT) trackpad(trackpadModifier)
            }
            return@BoxWithConstraints
        }

        val handleHeight = KeyboardHeightHandleHeight
        // Leave the trackpad enough room to stay usable.
        val maxKeyboard = (areaHeight - handleHeight - if (showTrackpad) MinTrackpadHeight + gap else 0.dp)
            .coerceAtMost(screenHeight * MAX_KEYBOARD_HEIGHT_FRACTION)
            .coerceAtLeast(0.dp)
        val minKeyboard = (screenHeight * MIN_KEYBOARD_HEIGHT_FRACTION).coerceAtMost(maxKeyboard)
        val autoHeight = keyboardHeightForWidth(areaWidth, keyboardRows, keyAspect, style)
            .coerceAtMost(if (showTrackpad) areaHeight * 0.7f else areaHeight - handleHeight)
        var dragHeight by remember { mutableStateOf<Dp?>(null) }
        val keyboardHeight = (dragHeight ?: heightFraction?.let { screenHeight * it } ?: autoHeight)
            .coerceIn(minKeyboard, maxKeyboard)

        val currentHeight by rememberUpdatedState(keyboardHeight)
        val currentMin by rememberUpdatedState(minKeyboard)
        val currentMax by rememberUpdatedState(maxKeyboard)
        val currentScreenHeight by rememberUpdatedState(screenHeight)
        val currentOnChange by rememberUpdatedState(onHeightFractionChange)
        val currentTrackpadBelow by rememberUpdatedState(trackpadBelow)

        val handle: @Composable () -> Unit = {
            KeyboardHeightHandle(
                percent = if (dragHeight != null) (keyboardHeight / screenHeight * 100).roundToInt() else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragHeight = currentHeight },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                // The handle sits on the keyboard's free edge: its top, or its bottom
                                // when the trackpad is below.
                                val delta = with(density) { dragAmount.toDp() }
                                val dragged = (dragHeight ?: currentHeight) + if (currentTrackpadBelow) delta else -delta
                                dragHeight = dragged.coerceIn(currentMin, currentMax)
                            },
                            onDragEnd = {
                                dragHeight?.let { currentOnChange(it / currentScreenHeight) }
                                dragHeight = null
                            },
                            onDragCancel = { dragHeight = null },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { currentOnChange(null) })
                    }
                    .semantics {
                        contentDescription = "Keyboard height, ${(keyboardHeight / screenHeight * 100).roundToInt()} percent of the screen. " +
                            "Drag to resize, double-tap for automatic height"
                    }
                    .testTag("keyboard_height_handle"),
            )
        }
        val fixedHeight = dragHeight != null || heightFraction != null
        Column(Modifier.fillMaxSize()) {
            if (trackpadBelow) {
                keyboard(Modifier.fillMaxWidth().height(keyboardHeight), fixedHeight)
                handle()
                // Centred and narrower than the keys, like a laptop's.
                trackpad(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(LaptopTrackpadWidthFraction)
                        .align(Alignment.CenterHorizontally)
                        .padding(start = gap, end = gap, bottom = gap),
                )
            } else {
                if (showTrackpad) {
                    trackpad(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = gap, end = gap, top = gap),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                handle()
                keyboard(Modifier.fillMaxWidth().height(keyboardHeight), fixedHeight)
            }
        }
    }
}

private val KeyboardHeightHandleHeight = 22.dp
private val MinTrackpadHeight = 96.dp
private const val LaptopTrackpadWidthFraction = 0.6f

/** The grip above the keyboard; shows the height while it's being dragged. */
@Composable
private fun KeyboardHeightHandle(percent: Int?, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 44.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = if (percent != null) 0.8f else 0.35f)),
        )
        if (percent != null) {
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            )
        }
    }
}
