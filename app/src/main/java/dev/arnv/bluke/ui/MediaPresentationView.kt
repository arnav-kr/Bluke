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
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val RemoteBarColor = Color(0xFF24262B)
private val RemoteButtonColor = Color(0xFF34373E)
private val RemoteContentColor = Color(0xFFF4F4F6)

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
    var showPresentationTools by rememberSaveable { mutableStateOf(false) }

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
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val compact = maxHeight < 330.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                PresentationPad(
                    btManager = btManager,
                    sharedPrefs = sharedPrefs,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    compact = compact,
                )
                MediaTransportDock(
                    btManager = btManager,
                    toolsVisible = showPresentationTools,
                    onToggleTools = { showPresentationTools = !showPresentationTools },
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun PresentationPad(
    btManager: BluetoothKeyboardManager,
    sharedPrefs: SharedPreferences,
    modifier: Modifier,
    compact: Boolean,
) {
    val sensitivity = sharedPrefs.getFloat("touchpad_sensitivity", 1.5f)
    val scrollSensitivity = sharedPrefs.getFloat("touchpad_scroll_sensitivity", 1f)
    Surface(
        modifier = modifier,
        color = RemoteBarColor,
        shape = RoundedCornerShape(if (compact) 20.dp else 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(if (compact) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(if (compact) 14.dp else 20.dp))
                    .background(Color(0xFF17181B))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        RoundedCornerShape(if (compact) 14.dp else 20.dp),
                    )
                    .testTag("presentation_touchpad"),
            ) {
                TouchGestureLayer(
                    btManager = btManager,
                    sensitivity = sensitivity,
                    scrollSensitivity = scrollSensitivity,
                    buttonMode = TrackpadButtonMode.CLICKPAD,
                    triggerVibration = {},
                    showNumpadLed = false,
                )
            }
            Column(
                modifier = Modifier
                    .width(if (compact) 64.dp else 78.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(if (compact) 14.dp else 20.dp))
                    .background(RemoteBarColor)
                    .padding(if (compact) 6.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                RemoteHoldButton(
                    label = "Previous slide",
                    icon = Icons.Default.KeyboardArrowUp,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fillHeight = true,
                    showLabel = false,
                    containerColor = RemoteButtonColor,
                    contentColor = RemoteContentColor,
                    onPressedChange = { btManager.sendKey(KeyboardLayouts.KEY_PAGEUP, it) },
                )
                RemoteHoldButton(
                    label = "Next slide",
                    icon = Icons.Default.KeyboardArrowDown,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fillHeight = true,
                    showLabel = false,
                    containerColor = RemoteButtonColor,
                    contentColor = RemoteContentColor,
                    onPressedChange = { btManager.sendKey(KeyboardLayouts.KEY_PAGEDOWN, it) },
                )
            }
        }
    }
}

@Composable
private fun MediaTransportDock(
    btManager: BluetoothKeyboardManager,
    toolsVisible: Boolean,
    onToggleTools: () -> Unit,
    compact: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RemoteBarColor,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 10.dp),
            horizontalArrangement = if (toolsVisible) Arrangement.spacedBy(8.dp) else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (toolsVisible) {
                PresentationTools(btManager, Modifier.weight(1f))
            } else {
                ConsumerIconButton("Mute", Icons.AutoMirrored.Filled.VolumeOff, ConsumerControl.MUTE, btManager, compact)
                ConsumerIconButton("Volume down", Icons.AutoMirrored.Filled.VolumeDown, ConsumerControl.VOLUME_DOWN, btManager, compact)
                ConsumerIconButton("Previous track", Icons.Default.SkipPrevious, ConsumerControl.PREVIOUS_TRACK, btManager, compact)
                ConsumerIconButton("Play or pause", Icons.Default.PlayArrow, ConsumerControl.PLAY_PAUSE, btManager, compact, emphasized = true)
                ConsumerIconButton("Next track", Icons.Default.SkipNext, ConsumerControl.NEXT_TRACK, btManager, compact)
                ConsumerIconButton("Volume up", Icons.AutoMirrored.Filled.VolumeUp, ConsumerControl.VOLUME_UP, btManager, compact)
            }
            Surface(
                onClick = onToggleTools,
                modifier = Modifier.size(if (compact) 42.dp else 50.dp),
                shape = CircleShape,
                color = RemoteButtonColor,
                contentColor = RemoteContentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = if (toolsVisible) "Hide presentation tools" else "Show presentation tools",
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsumerIconButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    control: ConsumerControl,
    btManager: BluetoothKeyboardManager,
    compact: Boolean,
    emphasized: Boolean = false,
) {
    RemoteIconHoldButton(
        label = label,
        icon = icon,
        modifier = Modifier.size(if (compact) 42.dp else if (emphasized) 56.dp else 50.dp),
        emphasized = emphasized,
        containerColor = if (emphasized) MaterialTheme.colorScheme.primary else RemoteButtonColor,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else RemoteContentColor,
        onPressedChange = { pressed ->
            btManager.sendConsumerControl(if (pressed) control else null)
        },
    )
}

@Composable
private fun PresentationTools(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RemoteHoldButton(
            "First",
            Icons.Default.FirstPage,
            Modifier.weight(1f),
            showLabel = false,
            containerColor = RemoteButtonColor,
            contentColor = RemoteContentColor,
            onPressedChange = { btManager.sendKey(KeyboardLayouts.KEY_HOME, it) },
        )
        RemoteHoldButton(
            "Start",
            Icons.Default.Slideshow,
            Modifier.weight(1f),
            showLabel = false,
            containerColor = RemoteButtonColor,
            contentColor = RemoteContentColor,
            onPressedChange = { btManager.sendKey(KeyboardLayouts.KEY_F5, it) },
        )
        RemoteHoldButton(
            "End",
            Icons.Default.Close,
            Modifier.weight(1f),
            showLabel = false,
            containerColor = RemoteButtonColor,
            contentColor = RemoteContentColor,
            onPressedChange = { btManager.sendKey(KeyboardLayouts.KEY_ESC, it) },
        )
        RemoteHoldButton(
            "Last",
            Icons.AutoMirrored.Filled.LastPage,
            Modifier.weight(1f),
            showLabel = false,
            containerColor = RemoteButtonColor,
            contentColor = RemoteContentColor,
            onPressedChange = { btManager.sendKey(KeyboardLayouts.KEY_END, it) },
        )
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
                Text("Media remote", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

private tailrec fun Context.findRemoteVolumeKeyHost(): RemoteVolumeKeyHost? = when (this) {
    is RemoteVolumeKeyHost -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findRemoteVolumeKeyHost()
    else -> null
}
