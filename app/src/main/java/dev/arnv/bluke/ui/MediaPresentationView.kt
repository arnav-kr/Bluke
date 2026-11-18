package dev.arnv.bluke.ui

import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.SensorManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.OrientationEventListener
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.arnv.bluke.QuickCycleActivity
import dev.arnv.bluke.RemoteVolumeKeyHost
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.ConsumerControl
import kotlin.math.abs
import kotlin.math.min

/** Which Material color role a remote button is drawn in; groups related buttons by color. */
private enum class RemoteTone {
    /** Keyboard keys: Esc, Home, PgUp and the like. */
    NEUTRAL,
    /** Arrow keys and mouse buttons. */
    NAVIGATION,
    VOLUME,
    /** Previous and next track. */
    TRANSPORT,
    /** The primary action of a group: Play/Pause, OK. */
    EMPHASIS,
    /** Previous and next slide. */
    SLIDE,
}

@Composable
private fun RemoteTone.containerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberControlSurfaceColors()
    val dark = colors.isDark
    return when (this) {
        RemoteTone.NEUTRAL -> if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
        RemoteTone.NAVIGATION -> if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest
        RemoteTone.VOLUME -> scheme.secondaryContainer
        RemoteTone.TRANSPORT -> colors.accentContainer
        RemoteTone.EMPHASIS -> colors.accent
        RemoteTone.SLIDE -> scheme.tertiaryContainer
    }
}

@Composable
private fun RemoteTone.contentColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberControlSurfaceColors()
    return when (this) {
        RemoteTone.NEUTRAL -> scheme.onSurface
        RemoteTone.NAVIGATION -> scheme.onSurfaceVariant
        RemoteTone.VOLUME -> scheme.onSecondaryContainer
        RemoteTone.TRANSPORT -> colors.onAccentContainer
        RemoteTone.EMPHASIS -> colors.onAccent
        RemoteTone.SLIDE -> scheme.onTertiaryContainer
    }
}

internal enum class MultimediaPosture {
    LANDSCAPE,
    /** Phone held upright while the screen stays locked to landscape: content is drawn rotated. */
    PORTRAIT_HELD,
    /** The screen itself is portrait. */
    UPRIGHT,
}

internal fun multimediaPostureForDegrees(
    degrees: Int,
    current: MultimediaPosture,
): MultimediaPosture {
    if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return current
    val normalized = ((degrees % 360) + 360) % 360
    fun distanceTo(target: Int): Int {
        val direct = abs(normalized - target)
        return min(direct, 360 - direct)
    }
    val portraitDistance = min(distanceTo(0), distanceTo(180))
    val landscapeDistance = min(distanceTo(90), distanceTo(270))
    return when {
        portraitDistance <= POSTURE_ENTRY_DEGREES -> MultimediaPosture.PORTRAIT_HELD
        landscapeDistance <= POSTURE_ENTRY_DEGREES -> MultimediaPosture.LANDSCAPE
        else -> current
    }
}

internal class MultimediaPostureStabilizer(
    initial: MultimediaPosture,
    private val stabilityMillis: Long = POSTURE_STABILITY_MILLIS,
) {
    var current: MultimediaPosture = initial
        private set
    private var candidate: MultimediaPosture? = null
    private var candidateSinceMillis = 0L

    fun update(degrees: Int, nowMillis: Long): MultimediaPosture {
        val next = multimediaPostureForDegrees(degrees, current)
        if (next == current) {
            candidate = null
            return current
        }
        if (candidate != next) {
            candidate = next
            candidateSinceMillis = nowMillis
            return current
        }
        if (nowMillis - candidateSinceMillis >= stabilityMillis) {
            current = next
            candidate = null
        }
        return current
    }
}

internal fun consumerControlForHardwareVolumeKey(keyCode: Int): ConsumerControl? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP -> ConsumerControl.VOLUME_UP
    KeyEvent.KEYCODE_VOLUME_DOWN -> ConsumerControl.VOLUME_DOWN
    else -> null
}

@Composable
internal fun MediaPresentationView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    isConnected: Boolean,
    postureOverride: MultimediaPosture? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val volumeKeyHost = remember(context) { context.findRemoteVolumeKeyHost() }
    val useHardwareVolumeButtons = sharedPrefs.getBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, false)
    var isForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var posture by rememberSaveable { mutableStateOf(MultimediaPosture.LANDSCAPE) }
    var sensitivity by rememberTouchpadSpeed(
        sharedPrefs, TOUCHPAD_POINTER_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_POINTER_SPEED,
    )
    var scrollSensitivity by rememberTouchpadSpeed(
        sharedPrefs, TOUCHPAD_SCROLL_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_SCROLL_SPEED,
    )
    var isVibrationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("touchpad_vibration_enabled", true))
    }

    @Suppress("DEPRECATION")
    fun triggerVibration(milliseconds: Long) {
        if (!isVibrationEnabled) return
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(
                VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }

    val isWindowPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // When the screen follows the phone, rotating it gives a real portrait window. The rotated
    // "held upright" layout is only needed while the screen is locked to landscape.
    val isLockedLandscape = KeyboardOrientation.fromPreference(
        sharedPrefs.getString(KEYBOARD_ORIENTATION_PREFERENCE, null),
    ) == KeyboardOrientation.LANDSCAPE

    DisposableEffect(context, postureOverride, isLockedLandscape) {
        if (postureOverride != null) {
            posture = postureOverride
            onDispose {}
        } else if (!isLockedLandscape) {
            posture = MultimediaPosture.LANDSCAPE
            onDispose {}
        } else {
            val stabilizer = MultimediaPostureStabilizer(posture)
            val listener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_UI) {
                override fun onOrientationChanged(orientation: Int) {
                    posture = stabilizer.update(orientation, SystemClock.elapsedRealtime())
                }
            }
            if (listener.canDetectOrientation()) listener.enable()
            onDispose { listener.disable() }
        }
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

    val resolvedPosture = postureOverride ?: if (isWindowPortrait) MultimediaPosture.UPRIGHT else posture
    val cycleSensitivity = {
        sensitivity = nextTouchpadSpeed(sensitivity)
        sharedPrefs.edit { putFloat("touchpad_sensitivity", sensitivity) }
        triggerVibration(15)
    }
    val cycleScrollSensitivity = {
        scrollSensitivity = nextTouchpadSpeed(scrollSensitivity)
        sharedPrefs.edit { putFloat("touchpad_scroll_sensitivity", scrollSensitivity) }
        triggerVibration(15)
    }
    val cycleMode = {
        val enabled = sharedPrefs.enabledInputModes()
        val index = enabled.indexOfFirst { it.id == launchMode }.coerceAtLeast(0)
        onModeChange(enabled[(index + 1) % enabled.size].id)
    }
    val toggleVibration = {
        isVibrationEnabled = !isVibrationEnabled
        sharedPrefs.edit { putBoolean("touchpad_vibration_enabled", isVibrationEnabled) }
        triggerVibration(35)
    }
    val rootModifier = Modifier
        .fillMaxSize()
        .background(rememberControlSurfaceColors().casing)
        .testTag("media_presentation_view_root")

    if (resolvedPosture == MultimediaPosture.UPRIGHT) {
        Column(modifier = rootModifier) {
            MultimediaTopBar(
                btManager = btManager,
                onClose = onClose,
                launchMode = launchMode,
                onModeChange = onModeChange,
                sharedPrefs = sharedPrefs,
                isConnected = isConnected,
                sensitivity = sensitivity,
                onSensitivityChange = cycleSensitivity,
                scrollSensitivity = scrollSensitivity,
                onScrollSensitivityChange = cycleScrollSensitivity,
                isVibrationEnabled = isVibrationEnabled,
                onToggleVibration = toggleVibration,
            )
            MultimediaUprightCanvas(
                btManager = btManager,
                sensitivity = sensitivity,
                scrollSensitivity = scrollSensitivity,
                triggerVibration = ::triggerVibration,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    } else if (resolvedPosture == MultimediaPosture.LANDSCAPE) {
        Column(modifier = rootModifier) {
            MultimediaTopBar(
                btManager = btManager,
                onClose = onClose,
                launchMode = launchMode,
                onModeChange = onModeChange,
                sharedPrefs = sharedPrefs,
                isConnected = isConnected,
                sensitivity = sensitivity,
                onSensitivityChange = cycleSensitivity,
                scrollSensitivity = scrollSensitivity,
                onScrollSensitivityChange = cycleScrollSensitivity,
                isVibrationEnabled = isVibrationEnabled,
                onToggleVibration = toggleVibration,
            )
            MultimediaControlCanvas(
                btManager = btManager,
                posture = resolvedPosture,
                sensitivity = sensitivity,
                scrollSensitivity = scrollSensitivity,
                triggerVibration = ::triggerVibration,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Row(modifier = rootModifier) {
            MultimediaUprightBar(
                btManager = btManager,
                onClose = onClose,
                onModeChange = cycleMode,
                isConnected = isConnected,
                sensitivity = sensitivity,
                onSensitivityChange = cycleSensitivity,
                scrollSensitivity = scrollSensitivity,
                onScrollSensitivityChange = cycleScrollSensitivity,
                isVibrationEnabled = isVibrationEnabled,
                onToggleVibration = toggleVibration,
            )
            MultimediaControlCanvas(
                btManager = btManager,
                posture = resolvedPosture,
                sensitivity = sensitivity,
                scrollSensitivity = scrollSensitivity,
                triggerVibration = ::triggerVibration,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** Portrait screen: buttons on top, then slide controls, touchpad and mouse buttons. */
@Composable
private fun MultimediaUprightCanvas(
    btManager: BluetoothKeyboardManager,
    sensitivity: Float,
    scrollSensitivity: Float,
    triggerVibration: (Long) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(12.dp)) {
        val gap = 8.dp
        // Two blocks of three columns, with a wider gap between the blocks.
        val buttonSize = ((maxWidth - gap * 7) / 6).coerceIn(44.dp, 64.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MultimediaButtonDeck(
                btManager = btManager,
                posture = MultimediaPosture.UPRIGHT,
                buttonWidth = buttonSize,
                buttonHeight = buttonSize,
                horizontalGap = gap,
                verticalGap = gap,
                triggerVibration = triggerVibration,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            SlideNavigationRow(btManager, Modifier.fillMaxWidth().height(48.dp), gap, triggerVibration)
            MultimediaTouchpad(
                btManager,
                sensitivity,
                scrollSensitivity,
                triggerVibration,
                Modifier.weight(1f).fillMaxWidth(),
            )
            MouseButtonRow(btManager, Modifier.fillMaxWidth().height(52.dp), gap, triggerVibration)
        }
    }
}

@Composable
private fun MultimediaControlCanvas(
    btManager: BluetoothKeyboardManager,
    posture: MultimediaPosture,
    sensitivity: Float,
    scrollSensitivity: Float,
    triggerVibration: (Long) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.padding(
            start = if (posture == MultimediaPosture.LANDSCAPE) 32.dp else 24.dp,
            top = if (posture == MultimediaPosture.LANDSCAPE) 4.dp else 28.dp,
            end = if (posture == MultimediaPosture.LANDSCAPE) 13.dp else 23.dp,
            bottom = if (posture == MultimediaPosture.LANDSCAPE) 22.dp else 28.dp,
        ),
    ) {
        val compact = maxHeight < 280.dp
        val controlWidth = when {
            posture == MultimediaPosture.PORTRAIT_HELD -> 48.5f.dp
            compact -> 46.dp
            else -> 52.dp
        }
        val controlHeight = when {
            posture == MultimediaPosture.PORTRAIT_HELD -> 70.dp
            compact -> 46.dp
            else -> 52.dp
        }
        val horizontalGap = when {
            posture == MultimediaPosture.PORTRAIT_HELD -> 7.5f.dp
            compact -> 7.dp
            else -> 8.dp
        }
        val verticalGap = if (compact) 7.dp else 8.dp
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 24.dp),
        ) {
            MultimediaButtonDeck(
                btManager = btManager,
                posture = posture,
                buttonWidth = controlWidth,
                buttonHeight = controlHeight,
                horizontalGap = horizontalGap,
                verticalGap = verticalGap,
                triggerVibration = triggerVibration,
                modifier = Modifier
                    .weight(if (posture == MultimediaPosture.LANDSCAPE) 1.025f else 1f)
                    .fillMaxHeight(),
            )
            MultimediaPointerDeck(
                btManager = btManager,
                posture = posture,
                sensitivity = sensitivity,
                scrollSensitivity = scrollSensitivity,
                triggerVibration = triggerVibration,
                compact = compact,
                modifier = Modifier
                    .weight(if (posture == MultimediaPosture.LANDSCAPE) 0.975f else 1.08f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MultimediaButtonDeck(
    btManager: BluetoothKeyboardManager,
    posture: MultimediaPosture,
    buttonWidth: Dp,
    buttonHeight: Dp,
    horizontalGap: Dp,
    verticalGap: Dp,
    triggerVibration: (Long) -> Unit,
    modifier: Modifier,
) {
    val contentRotation = if (posture == MultimediaPosture.PORTRAIT_HELD) -90f else 0f
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalGap * 3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mediaRows = if (posture == MultimediaPosture.PORTRAIT_HELD) {
            portraitHeldMediaRows
        } else {
            landscapeMediaRows
        }
        Column(verticalArrangement = Arrangement.spacedBy(verticalGap)) {
            mediaRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                    row.forEach { action ->
                        MultimediaActionButton(
                            action = action,
                            btManager = btManager,
                            width = buttonWidth,
                            height = buttonHeight,
                            contentRotation = contentRotation,
                            triggerVibration = triggerVibration,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(verticalGap)) {
            Row(
                modifier = Modifier.width(buttonWidth * 3 + horizontalGap * 2),
                horizontalArrangement = Arrangement.Center,
            ) {
                MultimediaActionButton(
                    RemoteAction.Key("Up", Icons.Default.KeyboardArrowUp, KeyboardLayouts.KEY_UP, RemoteTone.NAVIGATION),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    triggerVibration = triggerVibration,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                MultimediaActionButton(
                    RemoteAction.Key("Left", Icons.AutoMirrored.Filled.KeyboardArrowLeft, KeyboardLayouts.KEY_LEFT, RemoteTone.NAVIGATION),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    triggerVibration = triggerVibration,
                )
                MultimediaActionButton(
                    RemoteAction.Key("OK", null, KeyboardLayouts.KEY_ENTER, RemoteTone.EMPHASIS),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    contentRotation,
                    triggerVibration,
                )
                MultimediaActionButton(
                    RemoteAction.Key("Right", Icons.AutoMirrored.Filled.KeyboardArrowRight, KeyboardLayouts.KEY_RIGHT, RemoteTone.NAVIGATION),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    triggerVibration = triggerVibration,
                )
            }
            Row(
                modifier = Modifier.width(buttonWidth * 3 + horizontalGap * 2),
                horizontalArrangement = Arrangement.Center,
            ) {
                MultimediaActionButton(
                    RemoteAction.Key("Down", Icons.Default.KeyboardArrowDown, KeyboardLayouts.KEY_DOWN, RemoteTone.NAVIGATION),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    triggerVibration = triggerVibration,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                MultimediaActionButton(
                    RemoteAction.Key("Back", null, KeyboardLayouts.KEY_BACKSPACE),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    contentRotation,
                    triggerVibration,
                )
                MultimediaActionButton(
                    RemoteAction.Key("Start", null, KeyboardLayouts.KEY_F5),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    contentRotation,
                    triggerVibration,
                )
                MultimediaActionButton(
                    RemoteAction.Key("Menu", null, KEY_APPLICATION),
                    btManager,
                    buttonWidth,
                    buttonHeight,
                    contentRotation,
                    triggerVibration,
                )
            }
        }
    }
}

@Composable
private fun MultimediaPointerDeck(
    btManager: BluetoothKeyboardManager,
    posture: MultimediaPosture,
    sensitivity: Float,
    scrollSensitivity: Float,
    triggerVibration: (Long) -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    val gap = if (compact) 7.dp else 8.dp
    val navigationWidth = if (compact) 52.dp else 60.dp
    val mouseThickness = if (compact) 38.dp else 42.dp
    if (posture == MultimediaPosture.LANDSCAPE) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                MultimediaTouchpad(
                    btManager,
                    sensitivity,
                    scrollSensitivity,
                    triggerVibration,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                MouseButtonRow(btManager, Modifier.fillMaxWidth().height(mouseThickness), gap, triggerVibration)
            }
            SlideNavigationColumn(btManager, Modifier.width(navigationWidth).fillMaxHeight(), gap, triggerVibration)
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                SlideNavigationRow(btManager, Modifier.fillMaxWidth().height(mouseThickness), gap, triggerVibration)
                MultimediaTouchpad(
                    btManager,
                    sensitivity,
                    scrollSensitivity,
                    triggerVibration,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
            MouseButtonColumn(btManager, Modifier.width(mouseThickness).fillMaxHeight(), gap, triggerVibration)
        }
    }
}

@Composable
private fun MultimediaTouchpad(
    btManager: BluetoothKeyboardManager,
    sensitivity: Float,
    scrollSensitivity: Float,
    triggerVibration: (Long) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .cyberTrackpad(cornerRadius = 8.dp)
            .testTag("multimedia_touchpad"),
    ) {
        TouchGestureLayer(
            btManager = btManager,
            sensitivity = sensitivity,
            scrollSensitivity = scrollSensitivity,
            buttonMode = TrackpadButtonMode.CLICKPAD,
            triggerVibration = triggerVibration,
            showNumpadLed = false,
        )
    }
}

@Composable
private fun SlideNavigationColumn(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    gap: Dp,
    triggerVibration: (Long) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        MultimediaActionButton(
            RemoteAction.Key("Previous slide", Icons.Default.KeyboardArrowUp, KeyboardLayouts.KEY_PAGEUP, RemoteTone.SLIDE),
            btManager,
            Modifier.weight(1f).fillMaxWidth(),
            triggerVibration = triggerVibration,
        )
        MultimediaActionButton(
            RemoteAction.Key("Next slide", Icons.Default.KeyboardArrowDown, KeyboardLayouts.KEY_PAGEDOWN, RemoteTone.SLIDE),
            btManager,
            Modifier.weight(1f).fillMaxWidth(),
            triggerVibration = triggerVibration,
        )
    }
}

@Composable
private fun SlideNavigationRow(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    gap: Dp,
    triggerVibration: (Long) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        MultimediaActionButton(
            RemoteAction.Key("Previous slide", Icons.AutoMirrored.Filled.KeyboardArrowLeft, KeyboardLayouts.KEY_PAGEUP, RemoteTone.SLIDE),
            btManager,
            Modifier.weight(1f).fillMaxHeight(),
            triggerVibration = triggerVibration,
        )
        MultimediaActionButton(
            RemoteAction.Key("Next slide", Icons.AutoMirrored.Filled.KeyboardArrowRight, KeyboardLayouts.KEY_PAGEDOWN, RemoteTone.SLIDE),
            btManager,
            Modifier.weight(1f).fillMaxHeight(),
            triggerVibration = triggerVibration,
        )
    }
}

@Composable
private fun MouseButtonRow(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    gap: Dp,
    triggerVibration: (Long) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        MouseHoldButton("Left mouse button", 0x01, btManager, Modifier.weight(1f).fillMaxHeight(), triggerVibration)
        MouseHoldButton("Middle mouse button", 0x04, btManager, Modifier.weight(1f).fillMaxHeight(), triggerVibration)
        MouseHoldButton("Right mouse button", 0x02, btManager, Modifier.weight(1f).fillMaxHeight(), triggerVibration)
    }
}

@Composable
private fun MouseButtonColumn(
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    gap: Dp,
    triggerVibration: (Long) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        MouseHoldButton("Left mouse button", 0x01, btManager, Modifier.weight(1f).fillMaxWidth(), triggerVibration)
        MouseHoldButton("Middle mouse button", 0x04, btManager, Modifier.weight(1f).fillMaxWidth(), triggerVibration)
        MouseHoldButton("Right mouse button", 0x02, btManager, Modifier.weight(1f).fillMaxWidth(), triggerVibration)
    }
}

@Composable
private fun MouseHoldButton(
    label: String,
    mask: Int,
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    triggerVibration: (Long) -> Unit,
) {
    RemoteHoldButton(
        label = label,
        icon = null,
        modifier = modifier,
        fillHeight = true,
        showLabel = false,
        cornerRadius = 8.dp,
        showBorder = false,
        containerColor = RemoteTone.NAVIGATION.containerColor(),
        contentColor = RemoteTone.NAVIGATION.contentColor(),
        onPressFeedback = { triggerVibration(15) },
        onPressedChange = { pressed ->
            btManager.sendMouseReport(if (pressed) mask.toByte() else 0, 0, 0, 0)
        },
    )
}

@Composable
private fun MultimediaActionButton(
    action: RemoteAction,
    btManager: BluetoothKeyboardManager,
    width: Dp,
    height: Dp,
    contentRotation: Float = 0f,
    triggerVibration: (Long) -> Unit,
) {
    Box(Modifier.width(width).height(height)) {
        MultimediaActionButton(action, btManager, Modifier.fillMaxSize(), contentRotation, triggerVibration)
    }
}

@Composable
private fun MultimediaActionButton(
    action: RemoteAction,
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    contentRotation: Float = 0f,
    triggerVibration: (Long) -> Unit,
) {
    RemoteHoldButton(
        label = action.label,
        icon = action.icon,
        modifier = modifier,
        fillHeight = true,
        showLabel = action.icon == null,
        contentRotation = contentRotation,
        cornerRadius = 8.dp,
        showBorder = false,
        containerColor = action.tone.containerColor(),
        contentColor = action.tone.contentColor(),
        onPressFeedback = { triggerVibration(15) },
        onPressedChange = { pressed ->
            when (action) {
                is RemoteAction.Consumer -> {
                    btManager.sendConsumerControl(if (pressed) action.control else null)
                }
                is RemoteAction.Key -> btManager.sendKey(action.keyCode, pressed)
            }
        },
    )
}

private sealed interface RemoteAction {
    val label: String
    val icon: ImageVector?
    val tone: RemoteTone

    data class Consumer(
        override val label: String,
        override val icon: ImageVector,
        val control: ConsumerControl,
        override val tone: RemoteTone = RemoteTone.NEUTRAL,
    ) : RemoteAction

    data class Key(
        override val label: String,
        override val icon: ImageVector?,
        val keyCode: Int,
        override val tone: RemoteTone = RemoteTone.NEUTRAL,
    ) : RemoteAction
}

private val muteAction = RemoteAction.Consumer("Mute", Icons.AutoMirrored.Filled.VolumeOff, ConsumerControl.MUTE, RemoteTone.VOLUME)
private val volumeUpAction = RemoteAction.Consumer("Volume up", Icons.AutoMirrored.Filled.VolumeUp, ConsumerControl.VOLUME_UP, RemoteTone.VOLUME)
private val volumeDownAction = RemoteAction.Consumer("Volume down", Icons.AutoMirrored.Filled.VolumeDown, ConsumerControl.VOLUME_DOWN, RemoteTone.VOLUME)
private val previousAction = RemoteAction.Consumer("Previous track", Icons.Default.SkipPrevious, ConsumerControl.PREVIOUS_TRACK, RemoteTone.TRANSPORT)
private val playAction = RemoteAction.Consumer("Play or pause", Icons.Default.PlayArrow, ConsumerControl.PLAY_PAUSE, RemoteTone.EMPHASIS)
private val nextAction = RemoteAction.Consumer("Next track", Icons.Default.SkipNext, ConsumerControl.NEXT_TRACK, RemoteTone.TRANSPORT)
// Upright content is rotated -90 degrees. Use the opposite source glyphs so the rendered
// Previous/Next symbols retain their intended visual direction without swapping HID commands.
private val uprightPreviousAction = RemoteAction.Consumer("Previous track", Icons.Default.SkipNext, ConsumerControl.PREVIOUS_TRACK, RemoteTone.TRANSPORT)
private val uprightNextAction = RemoteAction.Consumer("Next track", Icons.Default.SkipPrevious, ConsumerControl.NEXT_TRACK, RemoteTone.TRANSPORT)
private val escapeAction = RemoteAction.Key("Esc", null, KeyboardLayouts.KEY_ESC)
private val homeAction = RemoteAction.Key("Home", null, KeyboardLayouts.KEY_HOME)
private val endAction = RemoteAction.Key("End", null, KeyboardLayouts.KEY_END)
private val pageUpAction = RemoteAction.Key("PgUp", null, KeyboardLayouts.KEY_PAGEUP)
private val pageDownAction = RemoteAction.Key("PgDn", null, KeyboardLayouts.KEY_PAGEDOWN)
private val blackAction = RemoteAction.Key("Black", null, KeyboardLayouts.KEY_B)

private val landscapeMediaRows = listOf(
    listOf(muteAction, volumeUpAction, volumeDownAction),
    listOf(previousAction, playAction, nextAction),
    listOf(escapeAction, homeAction, endAction),
    listOf(pageUpAction, pageDownAction, blackAction),
)

private val portraitHeldMediaRows = listOf(
    listOf(muteAction, volumeUpAction, volumeDownAction),
    listOf(uprightPreviousAction, escapeAction, blackAction),
    listOf(playAction, pageDownAction, endAction),
    listOf(uprightNextAction, pageUpAction, homeAction),
)

@Composable
private fun MultimediaUprightBar(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    onModeChange: () -> Unit,
    isConnected: Boolean,
    sensitivity: Float,
    onSensitivityChange: () -> Unit,
    scrollSensitivity: Float,
    onScrollSensitivityChange: () -> Unit,
    isVibrationEnabled: Boolean,
    onToggleVibration: () -> Unit,
) {
    val toolbarColors = rememberControlSurfaceColors().toolbar
    val content = toolbarColors.content
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(toolbarColors.bar)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UprightToolbarButton("Close Multimedia", onClose) {
            Icon(Icons.Default.Close, null, tint = content, modifier = Modifier.size(20.dp))
        }
        UprightToolbarButton("Switch input mode", onModeChange, containerColor = toolbarColors.accentButton) {
            Icon(Icons.Default.Mouse, null, tint = toolbarColors.onAccentButton, modifier = Modifier.size(20.dp))
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isConnected) SessionToolbarDefaults.LedOn else SessionToolbarDefaults.LedOff)
                .semantics {
                    contentDescription = if (isConnected) "Host connected" else "Host offline"
                },
        )
        ReconnectHostButton(btManager, iconOnly = true)
        Spacer(Modifier.weight(1f))
        UprightToolbarButton("Pointer sensitivity ${sensitivity}x", onSensitivityChange) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Speed, null, tint = content, modifier = Modifier.size(14.dp))
                Text("${sensitivity}x", color = content, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        UprightToolbarButton("Scroll sensitivity ${scrollSensitivity}x", onScrollSensitivityChange) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = content, modifier = Modifier.size(14.dp))
                Text("${scrollSensitivity}x", color = content, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        UprightToolbarButton(
            if (isVibrationEnabled) "Disable vibration" else "Enable vibration",
            onToggleVibration,
        ) {
            Icon(
                Icons.Default.Vibration,
                null,
                tint = if (isVibrationEnabled) content else toolbarColors.mutedContent,
                modifier = Modifier.size(20.dp),
            )
        }
        val context = LocalContext.current
        UprightToolbarButton("Open settings", {
            context.startActivity(android.content.Intent(context, dev.arnv.bluke.SettingsActivity::class.java))
        }) {
            Icon(Icons.Default.Settings, null, tint = content, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun UprightToolbarButton(
    description: String,
    onClick: () -> Unit,
    containerColor: Color = rememberControlSurfaceColors().toolbar.button,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier.graphicsLayer { rotationZ = -90f },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultimediaTopBar(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    isConnected: Boolean,
    sensitivity: Float,
    onSensitivityChange: () -> Unit,
    scrollSensitivity: Float,
    onScrollSensitivityChange: () -> Unit,
    isVibrationEnabled: Boolean,
    onToggleVibration: () -> Unit,
) {
    val context = LocalContext.current
    val toolbarColors = rememberControlSurfaceColors().toolbar
    val connectedDevice by btManager.connectedDevice.collectAsState()
    val hostLabel = try {
        (connectedDevice ?: btManager.getReconnectTarget())?.name ?: "No Host"
    } catch (_: SecurityException) {
        if (isConnected) "Host" else "No Host"
    }
    SessionToolbar(
        leading = {
            ToolbarButton(onClick = onClose, onClickLabel = "Close multimedia") {
                ToolbarIcon(Icons.Default.Close, "Close")
                ToolbarLabel("Close")
            }
            ToolbarButton(
                onClickLabel = "Next input mode",
                onLongClickLabel = "Configure input mode cycle",
                onClick = {
                    val enabled = sharedPrefs.enabledInputModes()
                    val index = enabled.indexOfFirst { it.id == launchMode }.coerceAtLeast(0)
                    onModeChange(enabled[(index + 1) % enabled.size].id)
                },
                onLongClick = {
                    context.startActivity(Intent(context, QuickCycleActivity::class.java))
                },
                containerColor = toolbarColors.accentButton,
            ) {
                ToolbarIcon(Icons.Default.Mouse, "Switch mode", tint = toolbarColors.onAccentButton)
                ToolbarLabel("Multimedia", color = toolbarColors.onAccentButton)
            }
            ToolbarHostChip(isConnected = isConnected, hostName = hostLabel)
            ReconnectHostButton(btManager, iconOnly = true)
        },
        settingsActivity = dev.arnv.bluke.SettingsActivity::class.java,
        colors = toolbarColors,
        trailing = {
            ToolbarButton(onClick = onSensitivityChange, onClickLabel = "Next pointer speed") {
                ToolbarIcon(Icons.Default.Speed, "Sensitivity")
                ToolbarLabel("${sensitivity}x")
            }
            ToolbarButton(onClick = onScrollSensitivityChange, onClickLabel = "Next scroll speed") {
                ToolbarIcon(Icons.Default.KeyboardArrowUp, "Scroll speed")
                ToolbarLabel("${scrollSensitivity}x")
            }
            ToolbarIconButton(onClick = onToggleVibration) {
                ToolbarIcon(
                    Icons.Default.Vibration,
                    if (isVibrationEnabled) "Disable vibration" else "Enable vibration",
                    tint = if (isVibrationEnabled) toolbarColors.content else toolbarColors.mutedContent,
                )
            }
        },
    )
}

private const val KEY_APPLICATION = 0x65
private const val POSTURE_ENTRY_DEGREES = 20
internal const val POSTURE_STABILITY_MILLIS = 500L

private tailrec fun Context.findRemoteVolumeKeyHost(): RemoteVolumeKeyHost? = when (this) {
    is RemoteVolumeKeyHost -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findRemoteVolumeKeyHost()
    else -> null
}
