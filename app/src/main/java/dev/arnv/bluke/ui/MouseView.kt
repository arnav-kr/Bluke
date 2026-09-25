package dev.arnv.bluke.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import java.util.concurrent.atomic.AtomicInteger

private const val GYRO_MOUSE_ENABLED_PREFERENCE = "gyro_mouse_enabled"
private const val GYRO_MOUSE_SENSITIVITY_PREFERENCE = "gyro_mouse_sensitivity"
private val gyroSensitivitySteps = floatArrayOf(0.5f, 1f, 1.5f, 2f, 2.5f)

@Composable
fun MouseView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    caseBrush: Brush,
    isConnected: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isGyroEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(GYRO_MOUSE_ENABLED_PREFERENCE, true))
    }
    var sensitivity by remember {
        mutableFloatStateOf(sharedPrefs.getFloat(GYRO_MOUSE_SENSITIVITY_PREFERENCE, 1f))
    }
    var isForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val activeButtons = remember { AtomicInteger(0) }
    val gyroController = remember(context, btManager) {
        GyroMouseController(context) { delta ->
            btManager.sendMouseReport(
                activeButtons.get().toByte(),
                delta.x.toByte(),
                delta.y.toByte(),
                0,
            )
        }
    }

    SideEffect {
        gyroController.sensitivity = sensitivity
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isForeground = true
                Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(gyroController, isGyroEnabled, isForeground) {
        if (isGyroEnabled && isForeground) {
            if (!gyroController.start()) {
                isGyroEnabled = false
                sharedPrefs.edit { putBoolean(GYRO_MOUSE_ENABLED_PREFERENCE, false) }
                Toast.makeText(
                    context,
                    "Gyroscope is not available on this device",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            gyroController.stop()
        }
    }

    DisposableEffect(gyroController, btManager) {
        onDispose {
            gyroController.close()
            activeButtons.set(0)
            btManager.sendMouseReport(0, 0, 0, 0)
        }
    }

    fun updateButton(mask: Int, pressed: Boolean) {
        val buttons = activeButtons.updateAndGet { current ->
            if (pressed) current or mask else current and mask.inv()
        }
        btManager.sendMouseReport(buttons.toByte(), 0, 0, 0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(caseBrush)
            .navigationBarsPadding()
            .testTag("mouse_view_root"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MouseToolbarPill(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Close", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                MouseToolbarPill(
                    onClick = {
                        val enabled = sharedPrefs.enabledInputModes()
                        val index = enabled.indexOfFirst { it.id == launchMode }.coerceAtLeast(0)
                        onModeChange(enabled[(index + 1) % enabled.size].id)
                    },
                ) {
                    Icon(Icons.Default.Mouse, "Switch mode", tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mouse", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
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
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E1E),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.15f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isGyroEnabled) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.12f),
                                )
                                .pointerInput(isGyroEnabled) {
                                    detectTapGestures {
                                        if (!gyroController.isAvailable) {
                                            Toast.makeText(
                                                context,
                                                "Gyroscope is not available on this device",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            isGyroEnabled = !isGyroEnabled
                                            sharedPrefs.edit {
                                                putBoolean(GYRO_MOUSE_ENABLED_PREFERENCE, isGyroEnabled)
                                            }
                                        }
                                    }
                                }
                                .testTag("gyro_mouse_toggle"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                if (isGyroEnabled) "Pause gyroscope mouse" else "Start gyroscope mouse",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (isGyroEnabled) "Move the phone to move the pointer" else "Gyroscope paused",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Hold a mouse button while moving to drag",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MouseToolbarPill(
                            onClick = {
                                val currentIndex = gyroSensitivitySteps.indexOfFirst { it == sensitivity }
                                    .coerceAtLeast(0)
                                sensitivity = gyroSensitivitySteps[(currentIndex + 1) % gyroSensitivitySteps.size]
                                sharedPrefs.edit {
                                    putFloat(GYRO_MOUSE_SENSITIVITY_PREFERENCE, sensitivity)
                                }
                            },
                        ) {
                            Text(
                                "Sensitivity ${sensitivity}x",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MouseHoldButton(
                            label = "Left",
                            modifier = Modifier.weight(1f),
                            testTag = "mouse_left_button",
                            onPressedChange = { updateButton(1, it) },
                        )
                        MouseHoldButton(
                            label = "Middle",
                            modifier = Modifier.weight(1f),
                            testTag = "mouse_middle_button",
                            onPressedChange = { updateButton(4, it) },
                        )
                        MouseHoldButton(
                            label = "Right",
                            modifier = Modifier.weight(1f),
                            testTag = "mouse_right_button",
                            onPressedChange = { updateButton(2, it) },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MouseScrollButton(
                    wheel = 1,
                    activeButtons = activeButtons,
                    btManager = btManager,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Scroll up", tint = Color.White)
                    Text("Scroll", color = Color.White, fontSize = 10.sp)
                }
                MouseScrollButton(
                    wheel = -1,
                    activeButtons = activeButtons,
                    btManager = btManager,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Text("Scroll", color = Color.White, fontSize = 10.sp)
                    Icon(Icons.Default.KeyboardArrowDown, "Scroll down", tint = Color.White)
                }
            }
        }
    }
}
