package dev.arnv.bluke.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.arnv.bluke.RemoteVolumeKeyHost
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.ConsumerControl

internal fun consumerControlForHardwareVolumeKey(keyCode: Int): ConsumerControl? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> ConsumerControl.VOLUME_UP
    KeyEvent.KEYCODE_VOLUME_DOWN -> ConsumerControl.VOLUME_DOWN
    else -> null
}

@Composable
fun MediaPresentationView(
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
    val volumeKeyHost = remember(context) { context.findRemoteVolumeKeyHost() }
    var useHardwareVolumeButtons by remember {
        mutableStateOf(sharedPrefs.getBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, false))
    }
    var isForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
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

    DisposableEffect(volumeKeyHost, useHardwareVolumeButtons, isForeground, btManager) {
        if (useHardwareVolumeButtons && isForeground) {
            volumeKeyHost?.setRemoteVolumeKeyHandler { keyCode, isPressed ->
                val control = consumerControlForHardwareVolumeKey(keyCode)
                if (control != null) {
                    btManager.sendConsumerControl(if (isPressed) control else null)
                }
            }
        }
        onDispose {
            volumeKeyHost?.setRemoteVolumeKeyHandler(null)
            btManager.sendConsumerControl(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(caseBrush)
            .navigationBarsPadding()
            .testTag("media_presentation_view_root"),
    ) {
        MediaTopBar(
            onClose = onClose,
            launchMode = launchMode,
            onModeChange = onModeChange,
            sharedPrefs = sharedPrefs,
            isConnected = isConnected,
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ControlSection(
                title = "Media",
                subtitle = "Volume and playback on the connected host",
                icon = Icons.Default.PlayArrow,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConsumerButton(
                        "Mute",
                        Icons.AutoMirrored.Filled.VolumeOff,
                        ConsumerControl.MUTE,
                        btManager,
                        Modifier.weight(1f),
                    )
                    ConsumerButton(
                        "Volume down",
                        Icons.AutoMirrored.Filled.VolumeDown,
                        ConsumerControl.VOLUME_DOWN,
                        btManager,
                        Modifier.weight(1f),
                    )
                    ConsumerButton(
                        "Volume up",
                        Icons.AutoMirrored.Filled.VolumeUp,
                        ConsumerControl.VOLUME_UP,
                        btManager,
                        Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConsumerButton(
                        "Previous",
                        Icons.Default.SkipPrevious,
                        ConsumerControl.PREVIOUS_TRACK,
                        btManager,
                        Modifier.weight(1f),
                    )
                    ConsumerButton(
                        "Play / pause",
                        Icons.Default.PlayArrow,
                        ConsumerControl.PLAY_PAUSE,
                        btManager,
                        Modifier.weight(1f),
                    )
                    ConsumerButton(
                        "Next",
                        Icons.Default.SkipNext,
                        ConsumerControl.NEXT_TRACK,
                        btManager,
                        Modifier.weight(1f),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Phone volume buttons control host",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Only while this mode is open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = useHardwareVolumeButtons,
                            onCheckedChange = { enabled ->
                                useHardwareVolumeButtons = enabled
                                sharedPrefs.edit {
                                    putBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, enabled)
                                }
                            },
                            modifier = Modifier.testTag("remote_hardware_volume_toggle"),
                        )
                    }
                }
            }

            ControlSection(
                title = "Presentation",
                subtitle = "Common slideshow shortcuts",
                icon = Icons.Default.Slideshow,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyboardButton("Previous slide", KeyboardLayouts.KEY_PAGEUP, btManager, Modifier.weight(1f))
                    KeyboardButton("Next slide", KeyboardLayouts.KEY_PAGEDOWN, btManager, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyboardButton("First slide", KeyboardLayouts.KEY_HOME, btManager, Modifier.weight(1f))
                    KeyboardButton("Last slide", KeyboardLayouts.KEY_END, btManager, Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KeyboardButton("Start (F5)", KeyboardLayouts.KEY_F5, btManager, Modifier.weight(1f))
                    KeyboardButton("End (Esc)", KeyboardLayouts.KEY_ESC, btManager, Modifier.weight(1f))
                    KeyboardButton("Black screen (B)", KeyboardLayouts.KEY_B, btManager, Modifier.weight(1f))
                }
                Text(
                    "Presentation shortcuts depend on the host application; Page Up/Down and Esc are the most portable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MediaTopBar(
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    isConnected: Boolean,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolbarPill(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Close", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            ToolbarPill(
                onClick = {
                    val enabled = sharedPrefs.enabledInputModes()
                    val index = enabled.indexOfFirst { it.id == launchMode }.coerceAtLeast(0)
                    onModeChange(enabled[(index + 1) % enabled.size].id)
                },
            ) {
                Icon(Icons.Default.Keyboard, "Switch mode", tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Media + Presentation", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
}

@Composable
private fun ControlSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

private tailrec fun Context.findRemoteVolumeKeyHost(): RemoteVolumeKeyHost? = when (this) {
    is RemoteVolumeKeyHost -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findRemoteVolumeKeyHost()
    else -> null
}
