package dev.arnv.bluke.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.arnv.bluke.QuickCycleActivity
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
    val useHardwareVolumeButtons = sharedPrefs.getBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, false)
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
                consumerControlForHardwareVolumeKey(keyCode)?.let { control ->
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            val wide = maxWidth >= 840.dp
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(26.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                if (wide) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        MediaControls(btManager, Modifier.weight(0.9f).fillMaxHeight())
                        PresentationControls(btManager, Modifier.weight(1f).fillMaxHeight())
                        PresentationTouchpad(
                            btManager = btManager,
                            sharedPrefs = sharedPrefs,
                            modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        MediaControls(btManager, Modifier.fillMaxWidth())
                        PresentationControls(btManager, Modifier.fillMaxWidth())
                        PresentationTouchpad(
                            btManager = btManager,
                            sharedPrefs = sharedPrefs,
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaControls(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
) {
    ControlGroup(
        title = "Media",
        subtitle = "Sound and playback",
        icon = Icons.Default.PlayArrow,
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsumerButton("Mute", Icons.AutoMirrored.Filled.VolumeOff, ConsumerControl.MUTE, btManager, Modifier.weight(1f))
            ConsumerButton("Quieter", Icons.AutoMirrored.Filled.VolumeDown, ConsumerControl.VOLUME_DOWN, btManager, Modifier.weight(1f))
            ConsumerButton("Louder", Icons.AutoMirrored.Filled.VolumeUp, ConsumerControl.VOLUME_UP, btManager, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsumerButton("Previous", Icons.Default.SkipPrevious, ConsumerControl.PREVIOUS_TRACK, btManager, Modifier.weight(1f))
            ConsumerButton("Play / pause", Icons.Default.PlayArrow, ConsumerControl.PLAY_PAUSE, btManager, Modifier.weight(1f))
            ConsumerButton("Next", Icons.Default.SkipNext, ConsumerControl.NEXT_TRACK, btManager, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PresentationControls(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
) {
    ControlGroup(
        title = "Slides",
        subtitle = "Present and navigate",
        icon = Icons.Default.Slideshow,
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardButton("First", KeyboardLayouts.KEY_HOME, btManager, Modifier.weight(1f))
            KeyboardButton("Previous", KeyboardLayouts.KEY_PAGEUP, btManager, Modifier.weight(1f))
            KeyboardButton("Next", KeyboardLayouts.KEY_PAGEDOWN, btManager, Modifier.weight(1f))
            KeyboardButton("Last", KeyboardLayouts.KEY_END, btManager, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyboardButton("Start", KeyboardLayouts.KEY_F5, btManager, Modifier.weight(1f))
            KeyboardButton("Black", KeyboardLayouts.KEY_B, btManager, Modifier.weight(1f))
            KeyboardButton("End", KeyboardLayouts.KEY_ESC, btManager, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PresentationTouchpad(
    btManager: BluetoothKeyboardManager,
    sharedPrefs: SharedPreferences,
    modifier: Modifier,
) {
    val sensitivity = sharedPrefs.getFloat("touchpad_sensitivity", 1.5f)
    val scrollSensitivity = sharedPrefs.getFloat("touchpad_scroll_sensitivity", 1f)
    ControlGroup(
        title = "Pointer",
        subtitle = "Move, click, drag, or change slides",
        icon = Icons.Default.TouchApp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
            ) {
                TouchGestureLayer(
                    btManager = btManager,
                    sensitivity = sensitivity,
                    scrollSensitivity = scrollSensitivity,
                    buttonMode = TrackpadButtonMode.CLICKPAD,
                    triggerVibration = {},
                    showNumpadLed = false,
                )
                Text(
                    "Touchpad",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                )
            }
            Column(
                modifier = Modifier.width(86.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyboardButton("Previous", KeyboardLayouts.KEY_PAGEUP, btManager, Modifier.weight(1f))
                KeyboardButton("Next", KeyboardLayouts.KEY_PAGEDOWN, btManager, Modifier.weight(1f))
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
    val context = LocalContext.current
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
                onLongClick = {
                    context.startActivity(Intent(context, QuickCycleActivity::class.java))
                },
            ) {
                Icon(Icons.Default.Keyboard, "Switch mode", tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Media + Presentation", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (isConnected) Color(0xFF39FF14) else Color(0xFFFF9800))
                .semantics {
                    contentDescription = if (isConnected) "Host connected" else "Host offline"
                },
        )
    }
}

@Composable
private fun ControlGroup(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
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

private tailrec fun Context.findRemoteVolumeKeyHost(): RemoteVolumeKeyHost? = when (this) {
    is RemoteVolumeKeyHost -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findRemoteVolumeKeyHost()
    else -> null
}
