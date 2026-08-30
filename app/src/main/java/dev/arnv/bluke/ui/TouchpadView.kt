package dev.arnv.bluke.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

enum class TrackpadButtonMode(val displayName: String) {
    CLICKPAD("Clickpad"),
    TWO_BUTTONS("2-Button L/R"),
    THREE_BUTTONS("3-Button L/M/R")
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchpadView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    caseBrush: Brush,
    selectedCaseColor: CaseColor,
    onCaseColorChange: (CaseColor) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Preferences & Config States
    var buttonMode by remember { mutableStateOf(TrackpadButtonMode.CLICKPAD) }
    var showNumpadLed by rememberSaveable { mutableStateOf(false) }
    var isVibrationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("touchpad_vibration_enabled", true))
    }
    var sensitivity by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("touchpad_sensitivity", 1.5f))
    }
    var scrollSensitivity by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("touchpad_scroll_sensitivity", 1.0f))
    }

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
            .navigationBarsPadding()
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
                            .clickable {
                                onModeChange(InputModes.next(sharedPrefs, launchMode))
                                triggerVibration(25)
                            }
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
                    val deviceName = connectedDevNow?.name ?: "No Host"
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
                    Text(
                        text = if (isConnected) "[connected]" else "[offline]",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif
                    )
                }

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
                                var currentSens = sensitivity
                                currentSens = when {
                                    currentSens <= 1.0f -> 1.5f
                                    currentSens <= 1.5f -> 2.0f
                                    currentSens <= 2.0f -> 2.5f
                                    else -> 1.0f
                                }
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
                                var currentScroll = scrollSensitivity
                                currentScroll = when {
                                    currentScroll <= 1.0f -> 1.5f
                                    currentScroll <= 1.5f -> 2.0f
                                    currentScroll <= 2.0f -> 2.5f
                                    else -> 1.0f
                                }
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

                    // Case Color Selector Pill
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val enabledColors = CaseColor.entries.filter { color ->
                                    sharedPrefs.getStringSet("cycle_case_colors", CaseColor.entries.map { it.name }.toSet())?.contains(color.name) == true
                                }.ifEmpty { listOf(selectedCaseColor) }
                                val currentIndexInEnabled = enabledColors.indexOf(selectedCaseColor)
                                val nextIndex = (currentIndexInEnabled + 1) % enabledColors.size
                                onCaseColorChange(enabledColors[nextIndex])
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(selectedCaseColor.getActualColor(sharedPrefs))
                                .border(0.5.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedCaseColor.displayName,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Glass panel plate backing container with high-end polished styling
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                        showNumpadLed = showNumpadLed
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
            }
        }
    }
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

@Composable
fun TouchGestureLayer(
    btManager: BluetoothKeyboardManager,
    sensitivity: Float,
    scrollSensitivity: Float,
    buttonMode: TrackpadButtonMode,
    triggerVibration: (Long) -> Unit,
    showNumpadLed: Boolean
) {
    // Tracking points and states for reliable swipe gesture translation
    var lastActivePointerId by remember { mutableStateOf<PointerId?>(null) }
    var accumulatedX by remember { mutableFloatStateOf(0f) }
    var accumulatedY by remember { mutableFloatStateOf(0f) }
    var lastPointX by remember { mutableFloatStateOf(0f) }
    var lastPointY by remember { mutableFloatStateOf(0f) }

    // Multi-touch tracking
    var isTwoFingerActive by remember { mutableStateOf(false) }
    var lastTwoFingerY by remember { mutableFloatStateOf(0f) }
    var accumulatedScrollY by remember { mutableFloatStateOf(0f) }

    // Tap tracking
    val pointerDownInfo = remember { mutableMapOf<PointerId, Pair<Long, Offset>>() }
    var maxPointersInTap by remember { mutableIntStateOf(0) }
    var lastTapReleaseTime by remember { mutableLongStateOf(0L) }
    var lastTapReleasePosition by remember { mutableStateOf<Offset?>(null) }
    var isDoubleTapDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    var hasMovedDuringDrag by remember { mutableStateOf(false) }

    // Rate limiting to prevent Bluetooth L2CAP packet flooding
    var lastReportTime by remember { mutableLongStateOf(0L) }

    // Visual pointer (touchpad thumb) tracking states
    var isTouchActive by remember { mutableStateOf(false) }
    var activeTouchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var touchCount by remember { mutableIntStateOf(0) }
    var activeMouseButton by remember { mutableStateOf<Byte>(0) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbActiveAlpha by animateFloatAsState(
        targetValue = if (isTouchActive && !showNumpadLed) 0.85f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "thumbAlpha"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (touchCount >= 2) 0.78f else (if (isTouchActive) 0.88f else 1.0f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "thumbScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(sensitivity, scrollSensitivity, buttonMode, showNumpadLed) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes

                        if (showNumpadLed) {
                            isTouchActive = false
                            touchCount = 0
                            continue
                        }

                        // Determine active pointers
                        val pressedChanges = changes.filter { it.pressed }
                        val downCount = pressedChanges.size
                        touchCount = downCount
                        isTouchActive = downCount > 0
                        activeTouchPoints = pressedChanges.map { it.position }

                        if (downCount > maxPointersInTap) {
                            maxPointersInTap = downCount
                        }

                        // 1. Process Down Events for Taps
                        changes.forEach { change ->
                            if (change.pressed && !change.previousPressed) {
                                pointerDownInfo[change.id] = change.uptimeMillis to change.position
                                
                                // Cancel any pending single-finger click since a new touch down occurred
                                pendingTapJob?.cancel()
                                pendingTapJob = null
                                
                                // Check for double tap drag gesture start
                                val nowUptime = change.uptimeMillis
                                val lastRelease = lastTapReleaseTime
                                val lastPos = lastTapReleasePosition
                                if (nowUptime - lastRelease < 300L && lastPos != null) {
                                    val dx = change.position.x - lastPos.x
                                    val dy = change.position.y - lastPos.y
                                    val distSq = dx * dx + dy * dy
                                    if (distSq < 10000f) { // ~100px radius tolerance
                                        isDoubleTapDragging = true
                                        hasMovedDuringDrag = false
                                        triggerVibration(15)
                                    }
                                }
                            }
                        }

                        if (downCount == 1) {
                            isTwoFingerActive = false
                            accumulatedScrollY = 0f
                            val pointer = changes.first { it.pressed }
                            
                            if (lastActivePointerId != pointer.id || !pointer.previousPressed) {
                                lastActivePointerId = pointer.id
                                accumulatedX = 0f
                                accumulatedY = 0f
                                lastPointX = pointer.position.x
                                lastPointY = pointer.position.y
                            } else {
                                accumulatedX += (pointer.position.x - lastPointX) * sensitivity
                                accumulatedY += (pointer.position.y - lastPointY) * sensitivity

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= 10L) {
                                    val sendX = accumulatedX.roundToInt().coerceIn(-127, 127)
                                    val sendY = accumulatedY.roundToInt().coerceIn(-127, 127)

                                    if (sendX != 0 || sendY != 0) {
                                        if (isDoubleTapDragging) {
                                            if (!hasMovedDuringDrag) {
                                                // Drag gesture officially started! Send left button down
                                                btManager.sendMouseReport(1, 0, 0, 0)
                                                hasMovedDuringDrag = true
                                            }
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

                                if (touchYVal > height * 0.82f) {
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
                            val pressedList = changes.filter { it.pressed }
                            if (pressedList.size == 2) {
                                val currentYAverage = (pressedList[0].position.y + pressedList[1].position.y) / 2f
                                if (!isTwoFingerActive) {
                                    isTwoFingerActive = true
                                    accumulatedScrollY = 0f
                                    lastTwoFingerY = currentYAverage
                                } else {
                                    accumulatedScrollY += (currentYAverage - lastTwoFingerY) * 0.06f * scrollSensitivity
                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime >= 10L) {
                                        val sendScroll = accumulatedScrollY.roundToInt().coerceIn(-127, 127)
                                        if (sendScroll != 0) {
                                            btManager.sendMouseReport(0, 0, 0, sendScroll.toByte())
                                            accumulatedScrollY -= sendScroll
                                            lastReportTime = now
                                        }
                                    }
                                    lastTwoFingerY = currentYAverage
                                }
                            }
                        } else {
                            isTwoFingerActive = false
                            lastActivePointerId = null
                            
                            val sendX = accumulatedX.roundToInt().coerceIn(-127, 127)
                            val sendY = accumulatedY.roundToInt().coerceIn(-127, 127)
                            if (sendX != 0 || sendY != 0) {
                                if (isDoubleTapDragging) {
                                    if (!hasMovedDuringDrag) {
                                        btManager.sendMouseReport(1, 0, 0, 0)
                                        hasMovedDuringDrag = true
                                    }
                                    btManager.sendMouseReport(1, sendX.toByte(), sendY.toByte(), 0)
                                } else {
                                    btManager.sendMouseReport(activeMouseButton, sendX.toByte(), sendY.toByte(), 0)
                                }
                            }
                            val sendScroll = accumulatedScrollY.roundToInt().coerceIn(-127, 127)
                            if (sendScroll != 0) {
                                btManager.sendMouseReport(0, 0, 0, sendScroll.toByte())
                            }
                            accumulatedX = 0f
                            accumulatedY = 0f
                            accumulatedScrollY = 0f
                        }

                        // 3. Process Up Events for Taps (Clicks)
                        changes.forEach { change ->
                            if (change.changedToUp()) {
                                val downInfo = pointerDownInfo.remove(change.id)
                                if (isDoubleTapDragging) {
                                    isDoubleTapDragging = false
                                    if (hasMovedDuringDrag) {
                                        // End of drag gesture: release left mouse button
                                        activeMouseButton = 0
                                        btManager.sendMouseReport(0, 0, 0, 0)
                                        triggerVibration(15)
                                    } else {
                                        // No movement occurred: this is a double click!
                                        // Click 1
                                        btManager.sendMouseReport(1, 0, 0, 0)
                                        btManager.sendMouseReport(0, 0, 0, 0)
                                        // Click 2
                                        btManager.sendMouseReport(1, 0, 0, 0)
                                        btManager.sendMouseReport(0, 0, 0, 0)
                                        triggerVibration(20)
                                    }
                                    hasMovedDuringDrag = false
                                    lastTapReleaseTime = 0L
                                    lastTapReleasePosition = null
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
                                        
                                        if (duration < 250L && distanceSq < 40f) {
                                            if (downCount == 0) {
                                                triggerVibration(20)
                                                
                                                val clickButton = if (maxPointersInTap >= 3) 4 else if (maxPointersInTap == 2) 2 else 1
                                                
                                                if (clickButton == 1) {
                                                    // Single-finger click: delay to check for double tap
                                                    lastTapReleaseTime = change.uptimeMillis
                                                    lastTapReleasePosition = change.position
                                                    pendingTapJob = scope.launch {
                                                        delay(180L.milliseconds)
                                                        btManager.sendMouseReport(1, 0, 0, 0)
                                                        btManager.sendMouseReport(0, 0, 0, 0)
                                                        pendingTapJob = null
                                                        lastTapReleaseTime = 0L
                                                        lastTapReleasePosition = null
                                                    }
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
                        }
                    }
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

        // Draw touchpad thumbs for all active finger coordinates (Themed slate/gradient style)
        val thumbSize = 28.dp
        if (thumbActiveAlpha > 0.01f && !showNumpadLed) {
            activeTouchPoints.forEach { point ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (point.x - with(density) { (thumbSize / 2).toPx() }).roundToInt(),
                                y = (point.y - with(density) { (thumbSize / 2).toPx() }).roundToInt()
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
}
