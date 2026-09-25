package dev.arnv.bluke.ui

import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager

@Composable
fun KeyboardTouchpadView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    caseBrush: Brush,
    geometry: KeyboardGeometry,
    theme: KeyboardThemeDefinition,
    characterLayout: KeyboardCharacterLayout,
    activePressedKeys: List<Int>,
    isConnected: Boolean,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    keySensitivity: Float,
    isFnActive: Boolean,
    onKeyPressChange: (Int, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val sensitivity = remember(sharedPrefs) { sharedPrefs.getFloat("touchpad_sensitivity", 1.5f) }
    val scrollSensitivity = remember(sharedPrefs) {
        sharedPrefs.getFloat("touchpad_scroll_sensitivity", 1f)
    }
    val triggerVibration: (Long) -> Unit = remember(context, sharedPrefs) {
        { duration ->
            if (sharedPrefs.getBoolean("touchpad_vibration_enabled", true)) {
                @Suppress("DEPRECATION")
                (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator)
                    ?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(caseBrush)
            .navigationBarsPadding()
            .testTag("keyboard_touchpad_view_root"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Close", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable {
                            val enabled = sharedPrefs.enabledInputModes()
                            val index = enabled.indexOfFirst { it.id == launchMode }.coerceAtLeast(0)
                            onModeChange(enabled[(index + 1) % enabled.size].id)
                            triggerVibration(25)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.Keyboard, "Switch mode", tint = Color.White, modifier = Modifier.size(11.dp))
                    Text("Keyboard + Touchpad", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF39FF14) else Color(0xFFFF9800)),
                )
                Text(
                    if (isConnected) "Host connected" else "Host offline",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight()
                    .shadow(4.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .testTag("combined_touch_surface"),
            ) {
                TouchGestureLayer(
                    btManager = btManager,
                    sensitivity = sensitivity,
                    scrollSensitivity = scrollSensitivity,
                    buttonMode = TrackpadButtonMode.CLICKPAD,
                    triggerVibration = triggerVibration,
                    showNumpadLed = false,
                )
                Text(
                    text = "Touchpad",
                    color = Color.White.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                KeyboardView(
                    geometry = geometry,
                    theme = theme,
                    characterLayout = characterLayout,
                    activePressedKeys = activePressedKeys,
                    isConnected = isConnected,
                    isCapsLockActive = isCapsLockActive,
                    isNumLockActive = isNumLockActive,
                    isScrollLockActive = isScrollLockActive,
                    keySensitivity = keySensitivity,
                    onKeyPressChange = onKeyPressChange,
                )
                if (isFnActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    ) {
                        FnShortcutOverlay()
                    }
                }
            }
        }
    }
}
