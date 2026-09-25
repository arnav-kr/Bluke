package dev.arnv.bluke.ui

import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.data.LayoutRepository
import kotlinx.coroutines.launch

internal const val COMBINED_TOUCHPAD_FRACTION_KEY = "combined_touchpad_fraction"
internal const val COMBINED_TOUCHPAD_FIRST_KEY = "combined_touchpad_first"
internal const val DEFAULT_COMBINED_TOUCHPAD_FRACTION = 0.38f

internal fun normalizeCombinedTouchpadFraction(value: Float): Float = value.coerceIn(0.25f, 0.60f)

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
    val scope = rememberCoroutineScope()
    val layoutRepository = remember(context) { LayoutRepository(context) }
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
    var isEditingLayout by rememberSaveable { mutableStateOf(false) }
    var touchpadFraction by remember { mutableFloatStateOf(DEFAULT_COMBINED_TOUCHPAD_FRACTION) }
    var touchpadFirst by remember { mutableStateOf(true) }
    var contentWidthPx by remember { mutableIntStateOf(1) }

    fun persistLayout() {
        scope.launch {
            layoutRepository.save(
                mapOf(
                    COMBINED_TOUCHPAD_FRACTION_KEY to touchpadFraction,
                    COMBINED_TOUCHPAD_FIRST_KEY to if (touchpadFirst) 1f else 0f,
                )
            )
        }
    }

    LaunchedEffect(layoutRepository) {
        val saved = layoutRepository.load("combined")
        touchpadFraction = normalizeCombinedTouchpadFraction(
            saved[COMBINED_TOUCHPAD_FRACTION_KEY] ?: DEFAULT_COMBINED_TOUCHPAD_FRACTION
        )
        touchpadFirst = saved[COMBINED_TOUCHPAD_FIRST_KEY] != 0f
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
                if (isEditingLayout) {
                    CombinedToolbarIcon(
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = "Swap keyboard and touchpad",
                        testTag = "combined_layout_swap",
                        onClick = {
                            touchpadFirst = !touchpadFirst
                            persistLayout()
                        },
                    )
                    CombinedToolbarIcon(
                        icon = Icons.Default.RestartAlt,
                        contentDescription = "Reset combined layout",
                        testTag = "combined_layout_reset",
                        onClick = {
                            touchpadFraction = DEFAULT_COMBINED_TOUCHPAD_FRACTION
                            touchpadFirst = true
                            persistLayout()
                        },
                    )
                    CombinedToolbarIcon(
                        icon = Icons.Default.Done,
                        contentDescription = "Finish editing combined layout",
                        testTag = "combined_layout_done",
                        onClick = {
                            isEditingLayout = false
                            persistLayout()
                        },
                    )
                } else {
                    CombinedToolbarIcon(
                        icon = Icons.Default.Tune,
                        contentDescription = "Resize or rearrange keyboard and touchpad",
                        testTag = "combined_layout_edit",
                        onClick = { isEditingLayout = true },
                    )
                }
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
                .padding(8.dp)
                .onSizeChanged { contentWidthPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (touchpadFirst) {
                CombinedTouchpadPanel(
                    modifier = Modifier.weight(touchpadFraction),
                    btManager = btManager,
                    sensitivity = sensitivity,
                    scrollSensitivity = scrollSensitivity,
                    triggerVibration = triggerVibration,
                )
                CombinedResizeHandle(
                    isEditing = isEditingLayout,
                    onDrag = { dragAmount ->
                        touchpadFraction = normalizeCombinedTouchpadFraction(
                            touchpadFraction + dragAmount / contentWidthPx
                        )
                    },
                    onDragEnd = ::persistLayout,
                )
                CombinedKeyboardPanel(
                    modifier = Modifier.weight(1f - touchpadFraction),
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
                    isFnActive = isFnActive,
                )
            } else {
                CombinedKeyboardPanel(
                    modifier = Modifier.weight(1f - touchpadFraction),
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
                    isFnActive = isFnActive,
                )
                CombinedResizeHandle(
                    isEditing = isEditingLayout,
                    onDrag = { dragAmount ->
                        touchpadFraction = normalizeCombinedTouchpadFraction(
                            touchpadFraction - dragAmount / contentWidthPx
                        )
                    },
                    onDragEnd = ::persistLayout,
                )
                CombinedTouchpadPanel(
                    modifier = Modifier.weight(touchpadFraction),
                    btManager = btManager,
                    sensitivity = sensitivity,
                    scrollSensitivity = scrollSensitivity,
                    triggerVibration = triggerVibration,
                )
            }
        }
    }
}
