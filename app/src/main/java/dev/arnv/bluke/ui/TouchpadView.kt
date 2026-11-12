package dev.arnv.bluke.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import androidx.compose.ui.res.painterResource
import androidx.core.content.edit
import dev.arnv.bluke.R
import dev.arnv.bluke.QuickCycleActivity
import dev.arnv.bluke.data.KeyboardThemeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class TrackpadButtonMode(val displayName: String) {
    CLICKPAD("Clickpad"),
    TWO_BUTTONS("2-Button L/R"),
    THREE_BUTTONS("3-Button L/M/R")
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TouchpadView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    caseBrush: Brush,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Preferences & Config States
    var buttonMode by remember { mutableStateOf(TrackpadButtonMode.CLICKPAD) }
    var showNumpadLed by rememberSaveable { mutableStateOf(false) }
    var isVibrationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("touchpad_vibration_enabled", true))
    }
    var sensitivity by rememberTouchpadSpeed(
        sharedPrefs, TOUCHPAD_POINTER_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_POINTER_SPEED,
    )
    var scrollSensitivity by rememberTouchpadSpeed(
        sharedPrefs, TOUCHPAD_SCROLL_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_SCROLL_SPEED,
    )
    var modifierPosition by remember {
        mutableStateOf(
            TouchpadModifierPosition.fromPreference(
                sharedPrefs.getString(TOUCHPAD_MODIFIER_POSITION_PREFERENCE, null),
            ),
        )
    }
    val keyboardTheme = remember(context) { KeyboardThemeRepository(context).selectedTheme() }
    // Haptic buzz function using Android's Vibrator
    @Suppress("DEPRECATION")
    val triggerVibration = { milliseconds: Long ->
        if (isVibrationEnabled) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {
                // Fail-safe catch for security/hardware vibration errors
            }
        }
    }

    // Capture standard top-row numeric inputs for keyboard emulation
    val simulateKeyPress = { keyCode: Int, isPress: Boolean ->
        scope.launch {
            if (isPress) {
                triggerVibration(15)
                btManager.sendKey(keyCode, true)
            } else {
                btManager.sendKey(keyCode, false)
            }
        }
        Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(caseBrush)
            .testTag("touchpad_view_root")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // 1. Sleek Frosted Settings Bar for Touchpad (replaces standard Keyboard bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side actions: close and switch modes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Exit Button
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onClose() }
                            .padding(horizontal = 8.dp)
                            .testTag("exit_touchpad_btn"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Close",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Rotating Mode Switcher (cycles active connection modes)
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .combinedClickable(
                                onClickLabel = "Next input mode",
                                onLongClickLabel = "Configure input mode cycle",
                                onClick = {
                                    val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
                                    val currentIndexInEnabled = enabledModes.indexOf(launchMode)
                                    val nextIndex = (currentIndexInEnabled + 1) % enabledModes.size
                                    val nextMode = enabledModes[nextIndex]
                                    onModeChange(nextMode)
                                    triggerVibration(25)
                                },
                                onLongClick = {
                                    context.startActivity(Intent(context, QuickCycleActivity::class.java))
                                },
                            )
                            .padding(horizontal = 8.dp)
                            .testTag("touchpad_mode_cycle_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mouse,
                            contentDescription = "Switch Mode",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Touchpad Mode",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Connection status
                    val connectedDevNow by btManager.connectedDevice.collectAsState()
                    val isConnected = connectedDevNow != null
                    val deviceName = (connectedDevNow ?: btManager.getReconnectTarget())?.name ?: "No Host"
                    val statusLedColor = if (isConnected) Color(0xFF39FF14) else Color(0xFFFF9800)
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusLedColor)
                    )
                    Text(
                        text = deviceName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                ReconnectHostButton(btManager, iconOnly = true)

                // Right side configurations: Trackpad Button layout, Numpad LED toggle, Case Color, Sensitivity, Vibration haptics
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                modifierPosition = modifierPosition.next()
                                sharedPrefs.edit {
                                    putString(
                                        TOUCHPAD_MODIFIER_POSITION_PREFERENCE,
                                        modifierPosition.preferenceValue,
                                    )
                                }
                                triggerVibration(15)
                            }
                            .padding(horizontal = 8.dp)
                            .testTag("touchpad_modifier_position"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Keyboard, "Modifier key position", tint = Color.White, modifier = Modifier.size(12.dp))
                        Text(
                            modifierPosition.displayName,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                showNumpadLed = !showNumpadLed
                                triggerVibration(30)
                            }
                            .padding(horizontal = 8.dp)
                            .testTag("led_numpad_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showNumpadLed) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = "LED Pad",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dialpad_off),
                                contentDescription = "LED Pad",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "Numpad",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button Partition Mode selector
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val values = TrackpadButtonMode.entries
                                val nextMode = values[(buttonMode.ordinal + 1) % values.size]
                                buttonMode = nextMode
                                triggerVibration(15)
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mouse,
                            contentDescription = "Button Mode",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = buttonMode.displayName,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Mouse Sensitivity pill rotation (1.0x, 1.5x, 2.0x, 2.5x)
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val currentSens = nextTouchpadSpeed(sensitivity)
                                sensitivity = currentSens
                                sharedPrefs.edit { putFloat("touchpad_sensitivity", currentSens) }
                                triggerVibration(15)
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Sensitivity",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${sensitivity}x",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Scroll Speed pill rotation (1.0x, 1.5x, 2.0x, 2.5x)
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val currentScroll = nextTouchpadSpeed(scrollSensitivity)
                                scrollSensitivity = currentScroll
                                sharedPrefs.edit { putFloat("touchpad_scroll_sensitivity", currentScroll) }
                                triggerVibration(15)
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardDoubleArrowUp,
                            contentDescription = "Scroll Speed",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${scrollSensitivity}x",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Vibration Toggle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val active = !isVibrationEnabled
                                isVibrationEnabled = active
                                sharedPrefs.edit { putBoolean("touchpad_vibration_enabled", active) }
                                if (active) {
                                    triggerVibration(50)
                                }
                            }
                            .testTag("vibration_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVibrationEnabled) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = "Haptics Vibration",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_vibration_off),
                                contentDescription = "Haptics Vibration",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // 2. Large Glass-like Centered Touchpad Surface
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (modifierPosition == TouchpadModifierPosition.LEFT) {
                    TouchpadModifierRail(
                        modifier = Modifier.width(72.dp).fillMaxHeight(),
                        rightHandKeys = false,
                        theme = keyboardTheme,
                        btManager = btManager,
                        triggerVibration = triggerVibration,
                    )
                }

                // Glass panel plate backing container with high-end polished styling
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(10.dp),
                            spotColor = Color.Black.copy(alpha = 0.5f),
                            ambientColor = Color.Black
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .testTag("glass_touch_surface")
                ) {
                    
                    // Gesture and move touch capture layer
                    TouchGestureLayer(
                        btManager = btManager,
                        sensitivity = sensitivity,
                        scrollSensitivity = scrollSensitivity,
                        buttonMode = buttonMode,
                        triggerVibration = triggerVibration,
                        showNumpadLed = showNumpadLed,
                    )

                    // Asus-Style backlit LED number keyboard overlay (absolutely drawn over the trackpad background area)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showNumpadLed,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(if (buttonMode != TrackpadButtonMode.CLICKPAD) 0.82f else 1f)
                                    .padding(12.dp)
                            ) {
                                NumpadLedGrid(onKeyPress = simulateKeyPress)
                            }
                    }
                }

                if (modifierPosition == TouchpadModifierPosition.RIGHT) {
                    TouchpadModifierRail(
                        modifier = Modifier.width(72.dp).fillMaxHeight(),
                        rightHandKeys = true,
                        theme = keyboardTheme,
                        btManager = btManager,
                        triggerVibration = triggerVibration,
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchpadModifierRail(
    modifier: Modifier,
    rightHandKeys: Boolean,
    theme: KeyboardThemeDefinition,
    btManager: BluetoothKeyboardManager,
    triggerVibration: (Long) -> Unit,
) {
    val keys = if (rightHandKeys) {
        listOf("Ctrl" to 0xE4, "Shift" to 0xE5, "Alt" to 0xE6, "Meta" to 0xE7)
    } else {
        listOf("Ctrl" to 0xE0, "Shift" to 0xE1, "Alt" to 0xE2, "Meta" to 0xE3)
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        keys.forEach { (label, keyCode) ->
            MechanicalModifierKey(
                label = label,
                keyCode = keyCode,
                style = if (label == "Meta") theme.accentStyle else theme.modifierStyle,
                btManager = btManager,
                triggerVibration = triggerVibration,
            )
        }
    }
}

@Composable
private fun MechanicalModifierKey(
    label: String,
    keyCode: Int,
    style: KeyboardKeyStyle,
    btManager: BluetoothKeyboardManager,
    triggerVibration: (Long) -> Unit,
) {
    var isPressed by remember { mutableStateOf(false) }
    DisposableEffect(btManager, keyCode) {
        onDispose {
            if (isPressed) btManager.sendKey(keyCode, false)
        }
    }
    KeyCap(
        legend = label,
        shiftedLegend = "",
        width = 68.dp,
        height = 68.dp,
        isPressed = isPressed,
        keyBgColor = Color(style.backgroundArgb),
        legendColor = Color(style.legendArgb),
        legendScale = style.legendScale,
        baseUnitWidth = 68.dp,
        modifier = Modifier.pointerInput(keyCode) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    triggerVibration(12)
                    btManager.sendKey(keyCode, true)
                    try {
                        tryAwaitRelease()
                    } finally {
                        btManager.sendKey(keyCode, false)
                        isPressed = false
                    }
                },
            )
        },
    )
}

@Composable
fun NumpadLedGrid(onKeyPress: (Int, Boolean) -> Unit) {
    val getKeyCode: (String) -> Int = { char ->
        when (char) {
            "1" -> 0x1E
            "2" -> 0x1F
            "3" -> 0x20
            "4" -> 0x21
            "5" -> 0x22
            "6" -> 0x23
            "7" -> 0x24
            "8" -> 0x25
            "9" -> 0x26
            "0" -> 0x27
            "." -> 0x37
            "/" -> 0x38
            "*" -> 0x25
            "-" -> 0x2D
            "+" -> 0x2E
            "⌫" -> 0x2A
            "Enter" -> 0x28
            "%" -> 0x22
            "=" -> 0x2E
            else -> 0x2C
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 8.dp)
        ) {
            val cols = listOf(
                listOf("7", "4", "1", "0"),
                listOf("8", "5", "2", "."),
                listOf("9", "6", "3", "%"),
                listOf("/", "*", "-", "+")
            )
            
            cols.forEach { col ->
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    col.forEach { char ->
                        var isPressed by remember { mutableStateOf(false) }
                        val animatedAlpha by animateFloatAsState(
                            targetValue = if (isPressed) 0.2f else 0.0f,
                            animationSpec = tween(durationMillis = if (isPressed) 50 else 150),
                            label = "fill_anim"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = animatedAlpha))
                                .pointerInput(char) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            onKeyPress(getKeyCode(char), true)
                                            tryAwaitRelease()
                                            isPressed = false
                                            onKeyPress(getKeyCode(char), false)
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                }
            }
            
            // 5th Column (Backspace, Enter, =)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val keys5th = listOf(
                    "⌫" to 2f,
                    "Enter" to 1f,
                    "=" to 1f
                )
                keys5th.forEach { (char, weight) ->
                    var isPressed by remember { mutableStateOf(false) }
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isPressed) 0.2f else 0.0f,
                        animationSpec = tween(durationMillis = if (isPressed) 50 else 150),
                        label = "fill_anim"
                    )
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = animatedAlpha))
                            .pointerInput(char) {
                                detectTapGestures(
                                    onPress = {
                                        isPressed = true
                                        onKeyPress(getKeyCode(char), true)
                                        tryAwaitRelease()
                                        isPressed = false
                                        onKeyPress(getKeyCode(char), false)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { 
                        Text(
                            text = char,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = if (char == "Enter") 12.sp else 16.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }
        }

        val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        // Draw intersecting background lines (grid)
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 8.dp)
        ) {
            val strokeParams = 1.dp.toPx()
            
            // 4 vertical lines (1/5, 2/5, 3/5, 4/5)
            for (i in 1..4) {
                val x = size.width * (i / 5f)
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = strokeParams
                )
            }
            // 3 horizontal lines (1/4, 2/4, 3/4)
            for (i in 1..3) {
                val y = size.height * (i / 4f)
                val endX = if (i == 1) size.width * (4f / 5f) else size.width
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(endX, y),
                    strokeWidth = strokeParams
                )
            }
        }
    }
}

/** Sideways travel that turns a straight two-finger scroll into a free one. */
private const val SCROLL_UNLOCK_DISTANCE_DP = 28f
/** Slowest and fastest two-finger lift-off that still glides. */
private const val MIN_FLING_DP_PER_SECOND = 300f
private const val MAX_FLING_DP_PER_SECOND = 4000f
/** Both fingers must be up this soon after the first one for the scroll to glide. */
private const val FLING_LIFT_WINDOW_MILLIS = 120L
private const val INERTIA_FRAME_MILLIS = 12L
/** Rate limit that keeps reports from flooding the Bluetooth link. */
private const val MIN_REPORT_INTERVAL_MILLIS = 10L
private const val KEY_LEFT_SHIFT = 0xE1

@Composable
fun TouchGestureLayer(
    btManager: BluetoothKeyboardManager,
    sensitivity: Float,
    scrollSensitivity: Float,
    buttonMode: TrackpadButtonMode,
    triggerVibration: (Long) -> Unit,
    showNumpadLed: Boolean,
) {
    // Read here so every trackpad (Touchpad mode, keyboard trackpad, Multimedia) follows it.
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    }
    val settings = rememberTouchpadSettings(preferences)

    val gestureScope = rememberCoroutineScope()
    val currentVibration by rememberUpdatedState(triggerVibration)
    val tapDrag = remember(btManager, sensitivity, scrollSensitivity, buttonMode, showNumpadLed) {
        TouchpadTapDragGesture(gestureScope,
            sendButton = { btManager.sendMouseReport(it, 0, 0, 0) },
            vibrate = { currentVibration(it) })
    }
    val scrollOutput = remember(btManager) {
        // Shift + wheel mode borrows Shift only when the user isn't already holding it.
        var ownsShift = false
        TouchpadScrollOutput(
            sendScroll = { wheel, pan -> btManager.sendMouseReport(0, 0, 0, wheel.toByte(), pan.toByte()) },
            setShift = { held ->
                if (held) {
                    ownsShift = !btManager.isModifierHeld(KEY_LEFT_SHIFT)
                    if (ownsShift) btManager.sendKey(KEY_LEFT_SHIFT, true)
                } else if (ownsShift) {
                    ownsShift = false
                    btManager.sendKey(KEY_LEFT_SHIFT, false)
                }
            },
        )
    }

    // Finger markers. Held as state objects so that only the markers redraw on each
    // touch event, not this whole layer.
    val isTouchActive = remember { mutableStateOf(false) }
    val activeTouchPoints = remember { mutableStateOf<List<Offset>>(emptyList()) }
    val touchCount = remember { mutableIntStateOf(0) }

    DisposableEffect(btManager, tapDrag, scrollOutput) {
        onDispose {
            tapDrag.cancel()
            scrollOutput.end()
            // Pointer cancellation (mode change, rotation, backgrounding) must never strand a host button down.
            btManager.sendMouseReport(0, 0, 0, 0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(sensitivity, scrollSensitivity, buttonMode, showNumpadLed) {
                val unlockDistancePx = SCROLL_UNLOCK_DISTANCE_DP.dp.toPx()
                val minFlingPx = MIN_FLING_DP_PER_SECOND.dp.toPx()
                val maxFlingPx = MAX_FLING_DP_PER_SECOND.dp.toPx()
                val axisAssist = TwoFingerScrollAxisAssist()
                val momentum = ScrollMomentum()
                val velocityTracker = VelocityTracker()
                val pointerDownInfo = mutableMapOf<PointerId, Pair<Long, Offset>>()

                // One-finger pointer movement
                var lastActivePointerId: PointerId? = null
                var accumulatedX = 0f
                var accumulatedY = 0f
                var lastPointX = 0f
                var lastPointY = 0f
                var lastReportTime = 0L
                var activeMouseButton: Byte = 0
                var maxPointersInTap = 0
                // After a multi-finger gesture, the finger left behind must move past the
                // tap slop before it steers the pointer again.
                var holdPointer = false
                var holdX = 0f
                var holdY = 0f

                // Two-finger scrolling
                var twoFingerActive = false
                var firstScrollPointer: PointerId? = null
                var secondScrollPointer: PointerId? = null
                var startCentroidX = 0f
                var startCentroidY = 0f
                var lastCentroidX = 0f
                var lastCentroidY = 0f
                var gestureScrolled = false
                var flingVx = 0f
                var flingVy = 0f
                var flingTime = 0L
                var inertiaJob: Job? = null

                fun stopScrolling() {
                    inertiaJob?.cancel()
                    inertiaJob = null
                    momentum.stop()
                    flingTime = 0L
                    scrollOutput.end()
                }

                fun startInertia() {
                    inertiaJob = gestureScope.launch {
                        var last = SystemClock.uptimeMillis()
                        while (momentum.isActive) {
                            delay(INERTIA_FRAME_MILLIS)
                            val now = SystemClock.uptimeMillis()
                            momentum.advance((now - last).toFloat())
                            last = now
                            val current = settings.value
                            scrollOutput.add(
                                wheelSteps = scrollWheelDelta(momentum.stepY, scrollSensitivity, current.naturalScroll),
                                panSteps = scrollPanDelta(momentum.stepX, scrollSensitivity, current.naturalScroll),
                            )
                            scrollOutput.flush(current.horizontalScroll)
                        }
                        // Only reached when the glide runs out; stopScrolling() cleans up a cancelled one.
                        scrollOutput.end()
                        inertiaJob = null
                    }
                }

                try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes

                        if (showNumpadLed) {
                            isTouchActive.value = false
                            touchCount.intValue = 0
                            continue
                        }

                        val current = settings.value
                        val gesture = current.gestureSensitivity
                        val tapSlopPx = gesture.tapSlopDp.dp.toPx()
                        val now = SystemClock.uptimeMillis()

                        // Determine active pointers
                        val pressedChanges = changes.filter { it.pressed }
                        val downCount = pressedChanges.size
                        touchCount.intValue = downCount
                        isTouchActive.value = downCount > 0
                        activeTouchPoints.value = pressedChanges.map { it.position }
                        if (downCount > maxPointersInTap) {
                            maxPointersInTap = downCount
                        }

                        // 1. Process Down Events for Taps
                        changes.forEach { change ->
                            if (change.pressed && !change.previousPressed) {
                                // A finger landing stops a gliding scroll, like on a laptop trackpad.
                                stopScrolling()
                                pointerDownInfo[change.id] = change.uptimeMillis to change.position

                                if (pointerDownInfo.size == 1) {
                                    tapDrag.down(change.uptimeMillis, change.position.x, change.position.y)
                                } else {
                                    tapDrag.cancel()
                                }
                            }
                        }

                        // 2. A two-finger scroll ends as soon as the finger count changes.
                        if (twoFingerActive && downCount != 2) {
                            twoFingerActive = false
                            scrollOutput.flush(current.horizontalScroll)
                            scrollOutput.end()
                            if (current.scrollInertia && downCount < 2 && axisAssist.isScrolling) {
                                val velocity = velocityTracker.calculateVelocity()
                                val (vx, vy) = axisAssist.project(velocity.x, velocity.y)
                                flingVx = vx
                                flingVy = vy
                                flingTime = now
                            }
                        }

                        if (downCount == 1) {
                            val pointer = pressedChanges[0]

                            if (lastActivePointerId != pointer.id || !pointer.previousPressed) {
                                lastActivePointerId = pointer.id
                                accumulatedX = 0f
                                accumulatedY = 0f
                                lastPointX = pointer.position.x
                                lastPointY = pointer.position.y
                                if (!pointer.previousPressed) holdPointer = false
                                holdX = pointer.position.x
                                holdY = pointer.position.y
                            } else if (holdPointer) {
                                val hx = pointer.position.x - holdX
                                val hy = pointer.position.y - holdY
                                if (hx * hx + hy * hy > tapSlopPx * tapSlopPx) holdPointer = false
                                lastPointX = pointer.position.x
                                lastPointY = pointer.position.y
                            } else {
                                accumulatedX += (pointer.position.x - lastPointX) * sensitivity
                                accumulatedY += (pointer.position.y - lastPointY) * sensitivity

                                if (now - lastReportTime >= MIN_REPORT_INTERVAL_MILLIS) {
                                    val sendX = accumulatedX.roundToInt().coerceIn(-127, 127)
                                    val sendY = accumulatedY.roundToInt().coerceIn(-127, 127)

                                    if (sendX != 0 || sendY != 0) {
                                        if (tapDrag.move()) {
                                            btManager.sendMouseReport(1, sendX.toByte(), sendY.toByte(), 0)
                                        } else {
                                            btManager.sendMouseReport(activeMouseButton, sendX.toByte(), sendY.toByte(), 0)
                                        }
                                        accumulatedX -= sendX
                                        accumulatedY -= sendY
                                        lastReportTime = now
                                    }
                                }

                                lastPointX = pointer.position.x
                                lastPointY = pointer.position.y
                            }

                            if (!pointer.previousPressed) {
                                val touchYVal = pointer.position.y
                                val touchXVal = pointer.position.x
                                val height = size.height
                                val width = size.width

                                if (touchYVal > height * 0.82f && !tapDrag.isArmed) {
                                    triggerVibration(25)
                                    val btnMask = when (buttonMode) {
                                        TrackpadButtonMode.TWO_BUTTONS -> {
                                            if (touchXVal < width / 2f) 1 else 2
                                        }
                                        TrackpadButtonMode.THREE_BUTTONS -> {
                                            when {
                                                touchXVal < width * 0.35f -> 1
                                                touchXVal < width * 0.65f -> 4
                                                else -> 2
                                            }
                                        }
                                        TrackpadButtonMode.CLICKPAD -> 1
                                    }
                                    activeMouseButton = btnMask.toByte()
                                    btManager.sendMouseReport(btnMask.toByte(), 0, 0, 0)
                                }
                            }

                        } else if (downCount == 2) {
                            val first = pressedChanges[0]
                            val second = pressedChanges[1]
                            val centroidX = (first.position.x + second.position.x) / 2f
                            val centroidY = (first.position.y + second.position.y) / 2f
                            lastActivePointerId = null
                            holdPointer = true

                            if (!twoFingerActive || first.id != firstScrollPointer || second.id != secondScrollPointer) {
                                twoFingerActive = true
                                firstScrollPointer = first.id
                                secondScrollPointer = second.id
                                accumulatedX = 0f
                                accumulatedY = 0f
                                scrollOutput.end()
                                axisAssist.reset(
                                    startSlopPx = gesture.scrollStartSlopDp.dp.toPx(),
                                    unlockDistancePx = unlockDistancePx,
                                )
                                velocityTracker.resetTracking()
                                startCentroidX = centroidX
                                startCentroidY = centroidY
                            } else {
                                val (scrollDx, scrollDy) = axisAssist.filter(
                                    dx = centroidX - lastCentroidX,
                                    dy = centroidY - lastCentroidY,
                                    // Shift + wheel can only scroll one way at a time.
                                    allowDiagonal = current.horizontalScroll == TouchpadHorizontalScroll.STANDARD,
                                )
                                if (!gestureScrolled) {
                                    val travelX = centroidX - startCentroidX
                                    val travelY = centroidY - startCentroidY
                                    // Past the tap slop this is a scroll, so lifting must not right-click.
                                    gestureScrolled = travelX * travelX + travelY * travelY > tapSlopPx * tapSlopPx
                                }
                                scrollOutput.add(
                                    wheelSteps = scrollWheelDelta(scrollDy, scrollSensitivity, current.naturalScroll),
                                    panSteps = scrollPanDelta(scrollDx, scrollSensitivity, current.naturalScroll),
                                )
                                if (now - lastReportTime >= MIN_REPORT_INTERVAL_MILLIS &&
                                    scrollOutput.flush(current.horizontalScroll)
                                ) {
                                    lastReportTime = now
                                }
                            }
                            velocityTracker.addPosition(first.uptimeMillis, Offset(centroidX, centroidY))
                            lastCentroidX = centroidX
                            lastCentroidY = centroidY
                        } else {
                            lastActivePointerId = null
                            if (downCount >= 3) holdPointer = true

                            val sendX = accumulatedX.roundToInt().coerceIn(-127, 127)
                            val sendY = accumulatedY.roundToInt().coerceIn(-127, 127)
                            if (sendX != 0 || sendY != 0) {
                                if (tapDrag.move()) {
                                    btManager.sendMouseReport(1, sendX.toByte(), sendY.toByte(), 0)
                                } else {
                                    btManager.sendMouseReport(activeMouseButton, sendX.toByte(), sendY.toByte(), 0)
                                }
                            }
                            accumulatedX = 0f
                            accumulatedY = 0f
                        }

                        // 3. Process Up Events for Taps (Clicks)
                        changes.forEach { change ->
                            if (change.changedToUp()) {
                                val downInfo = pointerDownInfo.remove(change.id)
                                if (tapDrag.release()) {
                                    activeMouseButton = 0
                                } else if (downInfo != null) {
                                    val height = size.height
                                    val touchYStart = downInfo.second.y
                                    if (touchYStart > height * 0.82f) {
                                        // Started in button partition, release button
                                        activeMouseButton = 0
                                        btManager.sendMouseReport(0, 0, 0, 0)
                                        triggerVibration(15)
                                    } else {
                                        val duration = change.uptimeMillis - downInfo.first
                                        val dx = change.position.x - downInfo.second.x
                                        val dy = change.position.y - downInfo.second.y
                                        val distanceSq = dx * dx + dy * dy

                                        if (!gestureScrolled && TouchpadGesturePolicy.isTap(
                                            durationMillis = duration,
                                            distanceSquaredPx = distanceSq,
                                            tapSlopPx = tapSlopPx,
                                            tapTimeoutMillis = gesture.tapTimeoutMillis,
                                        )) {
                                            if (downCount == 0) {
                                                triggerVibration(20)

                                                val clickButton = if (maxPointersInTap >= 3) 4 else if (maxPointersInTap == 2) 2 else 1

                                                if (clickButton == 1) {
                                                    // Match main: defer the click so tap-then-drag holds a
                                                    // single press instead of starting with a double-click.
                                                    tapDrag.queueClick(change.uptimeMillis, change.position.x, change.position.y)
                                                } else {
                                                    // Multi-finger click (Right/Middle click): send immediately
                                                    btManager.sendMouseReport(clickButton.toByte(), 0, 0, 0)
                                                    btManager.sendMouseReport(0, 0, 0, 0)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (downCount == 0) {
                            maxPointersInTap = 0
                            holdPointer = false
                            gestureScrolled = false
                            // Both fingers left together while still moving: let the scroll glide.
                            if (flingTime != 0L) {
                                val liftedTogether = now - flingTime <= FLING_LIFT_WINDOW_MILLIS
                                flingTime = 0L
                                if (liftedTogether && momentum.start(flingVx, flingVy, minFlingPx, maxFlingPx)) {
                                    startInertia()
                                }
                            }
                        }
                    }
                }
                } finally {
                    // A cancelled pointer handler may remain composed (for example after
                    // changing sensitivity). Never leave a delayed click, a held button,
                    // a held Shift or a gliding scroll behind.
                    stopScrolling()
                    tapDrag.cancel()
                    btManager.sendMouseReport(0, 0, 0, 0)
                    isTouchActive.value = false
                    touchCount.intValue = 0
                    activeTouchPoints.value = emptyList()
                }
            }
    ) {
        // Draw the bottom boundaries as subtle hints for clicking
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (buttonMode != TrackpadButtonMode.CLICKPAD) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.18f)
                        .align(Alignment.BottomCenter)
                ) {
                    when (buttonMode) {
                        TrackpadButtonMode.TWO_BUTTONS -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {}
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {}
                            }
                        }
                        TrackpadButtonMode.THREE_BUTTONS -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {}
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {}
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {}
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        TouchThumbs(
            points = activeTouchPoints,
            isTouchActive = isTouchActive,
            touchCount = touchCount,
            hidden = showNumpadLed,
        )
    }
}

/** Finger markers for every active touch (themed slate/gradient style). */
@Composable
private fun TouchThumbs(
    points: State<List<Offset>>,
    isTouchActive: State<Boolean>,
    touchCount: State<Int>,
    hidden: Boolean,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbActiveAlpha by animateFloatAsState(
        targetValue = if (isTouchActive.value && !hidden) 0.85f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "thumbAlpha"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (touchCount.value >= 2) 0.78f else (if (isTouchActive.value) 0.88f else 1.0f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "thumbScale"
    )
    val thumbSize = 28.dp
    val halfThumbPx = with(density) { (thumbSize / 2).toPx() }
    if (thumbActiveAlpha > 0.01f && !hidden) {
        points.value.forEach { point ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (point.x - halfThumbPx).roundToInt(),
                            y = (point.y - halfThumbPx).roundToInt()
                        )
                    }
                    .size(thumbSize)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                        alpha = thumbActiveAlpha
                    }
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF38383B), Color(0xFF232325))
                        ),
                        shape = CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Subtle inner core
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }
    }
}
