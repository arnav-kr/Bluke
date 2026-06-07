package dev.arnv.bluke.ui
import android.app.Activity
import android.util.Log
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import dev.arnv.bluke.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.arnv.bluke.R
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import dev.arnv.bluke.sound.SwitchType



enum class RgbMode(val displayName: String) {
    WAVE("RGB Wave"),
    REACTIVE("Reactive Spark"),
    STATIC("Ice Blue Glow"),
    BREATHING("Warm Ember"),
    OFF("Lights Off")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    btManager: BluetoothKeyboardManager,
    soundSynth: KeyboardSoundSynthesizer
) {
    val context = LocalContext.current
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    
    // UI state
    var selectedLayoutType by rememberSaveable { mutableStateOf(KeyboardLayoutType.OBLIVION_75) }
    var selectedCaseColor by rememberSaveable { mutableStateOf(CaseColor.BLACK) }
    var selectedRgbMode by rememberSaveable { mutableStateOf(RgbMode.OFF) }
    var isKeyboardActive by rememberSaveable { mutableStateOf(false) }
    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var hideUnknownDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unknown", false)) }
    var hideUnsupportedDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unsupported", true)) }
    var showMacAddress by remember { mutableStateOf(sharedPrefs.getBoolean("show_mac", false)) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(0) }
    var easterEggClicks by remember { mutableStateOf(0) }
    
    val view = LocalView.current
    var isHapticsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("haptics_enabled", true)) }
    var keySensitivity by remember { mutableStateOf(sharedPrefs.getFloat("key_sensitivity", 6f)) }
    var lockSyncMode by remember { mutableStateOf(sharedPrefs.getString("lock_sync_mode", "host") ?: "host") }
    var launchMode by rememberSaveable { mutableStateOf(sharedPrefs.getInt("launch_mode", 0)) }

    // Mute state
    var isMuted by rememberSaveable { mutableStateOf(!sharedPrefs.getBoolean("key_sound_enabled", true)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hideUnknownDevices = sharedPrefs.getBoolean("hide_unknown", false)
                hideUnsupportedDevices = sharedPrefs.getBoolean("hide_unsupported", true)
                showMacAddress = sharedPrefs.getBoolean("show_mac", false)
                val soundEnabled = sharedPrefs.getBoolean("key_sound_enabled", true)
                isMuted = !soundEnabled
                soundSynth.setMute(!soundEnabled)
                isHapticsEnabled = sharedPrefs.getBoolean("haptics_enabled", true)
                keySensitivity = sharedPrefs.getFloat("key_sensitivity", 6f)
                lockSyncMode = sharedPrefs.getString("lock_sync_mode", "host") ?: "host"
                val enabledModes = listOf(0, 1, 2).filter { mode ->
                    val modeStr = when (mode) {
                        0 -> "keyboard"
                        1 -> "touchpad"
                        2 -> "gamepad"
                        else -> "keyboard"
                    }
                    sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad"))?.contains(modeStr) == true
                }.ifEmpty { listOf(0) }
                val savedLaunchMode = sharedPrefs.getInt("launch_mode", 0)
                launchMode = if (enabledModes.contains(savedLaunchMode)) savedLaunchMode else enabledModes.first()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    
    // Bluetooth status flows
    val btState by btManager.serviceState.collectAsState()
    val btMessage by btManager.statusMessage.collectAsState()
    

    
    val bondedDevices by btManager.bondedDevices.collectAsState()
    val scannedDevices by btManager.scannedDevices.collectAsState()
    val isScanning by btManager.isScanning.collectAsState()
    
    // Active pressed keys set for visually pressing keycaps
    val activePressedKeys = remember { mutableStateListOf<Int>() }

    // Connection helper declared at outer scope
    val isConnected = btState is BluetoothState.Connected

    // Lock Indicator State variables - single source of truth, reactive to local presses and system LED reports
    var isCapsLockActive by rememberSaveable { mutableStateOf(false) }
    var isNumLockActive by rememberSaveable { mutableStateOf(true) }
    var isScrollLockActive by rememberSaveable { mutableStateOf(false) }

    val systemCapsLock by btManager.capsLockState.collectAsState()
    val systemNumLock by btManager.numLockState.collectAsState()
    val systemScrollLock by btManager.scrollLockState.collectAsState()

    LaunchedEffect(isConnected, systemCapsLock, systemNumLock, systemScrollLock, lockSyncMode) {
        if (isConnected && lockSyncMode == "host") {
            isCapsLockActive = systemCapsLock
            isNumLockActive = systemNumLock
            isScrollLockActive = systemScrollLock
        }
    }

    // Track last connected device for reconnection
    var lastConnectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    LaunchedEffect(btState) {
        if (btState is BluetoothState.Connected) {
            btManager.connectedDevice.value?.let {
                lastConnectedDevice = it
            }
        }
    }

    // Toggle orientation and full-screen layout helper automatically
    LaunchedEffect(isKeyboardActive) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window ?: return@LaunchedEffect
        if (isKeyboardActive) {
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error setting orientation to landscape: ${e.message}")
            }
            // Hide System UI for immersive mechanical keypad experience
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.hide(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                    window.insetsController?.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error hiding system bars: ${e.message}")
            }
        } else {
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error setting orientation to portrait: ${e.message}")
            }
            // Show system UI normally in config mode
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.show(
                        android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error showing system bars: ${e.message}")
            }
        }
    }

    // Process local screen-press inputs
    fun handleLocalKeyPress(keyCode: Int, isPress: Boolean) {
        if (isPress) {
            if (isHapticsEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_PRESS)
            }
            if (!activePressedKeys.contains(keyCode)) {
                activePressedKeys.add(keyCode)
                soundSynth.playPress(keyCode)
                btManager.sendKey(keyCode, true)

                // Only toggle status indicators locally if we are disconnected OR in device-controlled mode.
                // In Host-Controlled mode, we wait for the Host OS to send an LED Output Report (to ensure true sync).
                if (!isConnected || lockSyncMode == "device") {
                    when (keyCode) {
                        0x39 -> isCapsLockActive = !isCapsLockActive // KEY_CAPSLOCK
                        0x47 -> isScrollLockActive = !isScrollLockActive // KEY_SCROLLLOCK
                        0x53 -> isNumLockActive = !isNumLockActive // KEY_NUMLOCK
                    }
                }
            }
        } else {
            activePressedKeys.remove(keyCode)
            soundSynth.playRelease(keyCode)
            btManager.sendKey(keyCode, false)
        }
    }

    // Hoisted outside AnimatedContent so the infinite animation doesn't restart
    // when the user navigates away (e.g. Licenses) and returns to the keyboard screen.
    val infiniteTransition = rememberInfiniteTransition(label = "rgb_wave_anim")
    val rgbTimeProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    AnimatedContent(
        targetState = isKeyboardActive,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "screen_navigation"
    ) { keyboardActive ->
        if (keyboardActive) {
            val caseColor = selectedCaseColor
            val caseColorVal = caseColor.getActualColor(sharedPrefs)
            val caseMetallic = caseColor.getActualMetallic(sharedPrefs)
            val caseBrush = if (caseMetallic) {
                Brush.linearGradient(
                    colors = listOf(
                        caseColorVal,
                        caseColorVal.copy(alpha = 0.85f),
                        caseColorVal.copy(alpha = 0.7f),
                        caseColorVal,
                        caseColorVal.copy(alpha = 0.9f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(500f, 500f)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        caseColorVal,
                        caseColorVal.copy(alpha = 0.92f)
                    )
                )
            }

            when (launchMode) {
                1, 2 -> {
                    val darkScheme = androidx.compose.material3.darkColorScheme(
                        primary = MaterialTheme.colorScheme.primary,
                        background = Color(0xFF141218),
                        surface = Color(0xFF141218),
                        surfaceVariant = Color(0xFF2B2930),
                        onBackground = Color(0xFFE6E0E9),
                        onSurface = Color(0xFFE6E0E9),
                        onSurfaceVariant = Color(0xFFCBC4D0)
                    )
                    MaterialTheme(colorScheme = darkScheme) {
                        when (launchMode) {
                            1 -> {
                                TouchpadView(
                                    btManager = btManager,
                                    onClose = { isKeyboardActive = false },
                                    launchMode = launchMode,
                                    onModeChange = { newMode -> 
                                        launchMode = newMode
                                        sharedPrefs.edit().putInt("launch_mode", newMode).apply()
                                    },
                                    sharedPrefs = sharedPrefs,
                                    caseBrush = caseBrush,
                                    selectedCaseColor = selectedCaseColor,
                                    onCaseColorChange = { newColor ->
                                        selectedCaseColor = newColor
                                        soundSynth.playRelease()
                                    }
                                )
                            }
                            2 -> {
                                GamepadView(
                                    btManager = btManager,
                                    onClose = { isKeyboardActive = false },
                                    launchMode = launchMode,
                                    onModeChange = { newMode -> 
                                        launchMode = newMode
                                        sharedPrefs.edit().putInt("launch_mode", newMode).apply()
                                    },
                                    sharedPrefs = sharedPrefs,
                                    caseBrush = caseBrush
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Seamless full-screen canvas acting as the aluminum keyboard plate and chassis
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(caseBrush)
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Sleek Frosted Glass Top Settings Bar (KBSim Web Style Toggles Toolbar)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Section: Exit, Connection status and Reconnect Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Exit Button (Pill style to match other buttons)
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable { isKeyboardActive = false }
                                        .padding(horizontal = 8.dp)
                                        .testTag("exit_keyboard_btn"),
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

                                // Rotating Mode Switcher
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val enabledModes = listOf(0, 1, 2).filter { mode ->
                                                val modeStr = when (mode) {
                                                    0 -> "keyboard"
                                                    1 -> "touchpad"
                                                    2 -> "gamepad"
                                                    else -> "keyboard"
                                                }
                                                sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad"))?.contains(modeStr) == true
                                            }.ifEmpty { listOf(0) }
                                            val currentIndexInEnabled = enabledModes.indexOf(launchMode)
                                            val nextIndex = (currentIndexInEnabled + 1) % enabledModes.size
                                            val nextMode = enabledModes[nextIndex]
                                            launchMode = nextMode
                                            sharedPrefs.edit().putInt("launch_mode", nextMode).apply()
                                            if (isHapticsEnabled) {
                                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                            }
                                        }
                                        .padding(horizontal = 8.dp)
                                        .testTag("keyboard_mode_cycle_btn"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = "Switch Mode",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Keyboard Mode",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Status LED and connection details
                                val statusLedColor = if (isConnected) Color(0xFF39FF14) else Color(0xFFFF9800)
                                // Use collected state (not .value) so UI reacts to changes from background
                                val connectedDevNow by btManager.connectedDevice.collectAsState()
                                val activeDevice = connectedDevNow ?: lastConnectedDevice
                                val deviceName = activeDevice?.name ?: "No Host"
                                
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
                                    color = Color.White.copy(alpha = 0.5f), // grayscale font
                                    fontSize = 9.sp, // reduced size
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.SansSerif
                                )
                                
                                // Reconnect Button
                                if (!isConnected && lastConnectedDevice != null) {
                                    Row(
                                        modifier = Modifier
                                            .height(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.15f))
                                            .clickable {
                                                lastConnectedDevice?.let { dev ->
                                                    btManager.connectDevice(dev)
                                                }
                                            }
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reconnect",
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Reconnect",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Right Section: lock LEDs indicators, configuration pills, and mute button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. Keyboard Status Lock LEDs Panel
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // CAPS
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                if (isConnected) {
                                                    btManager.sendKey(0x39, true)
                                                    btManager.sendKey(0x39, false)
                                                    if (lockSyncMode == "device") {
                                                        isCapsLockActive = !isCapsLockActive
                                                    }
                                                } else {
                                                    isCapsLockActive = !isCapsLockActive
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isCapsLockActive) Color(0xFF39FF14) else Color.White.copy(alpha = 0.15f))
                                        )
                                        Text(
                                            text = "CAPS",
                                            color = if (isCapsLockActive) Color.White else Color.White.copy(alpha = 0.4f),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            style = androidx.compose.ui.text.TextStyle(
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                    
                                    // NUM
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                if (isConnected) {
                                                    btManager.sendKey(0x53, true)
                                                    btManager.sendKey(0x53, false)
                                                    if (lockSyncMode == "device") {
                                                        isNumLockActive = !isNumLockActive
                                                    }
                                                } else {
                                                    isNumLockActive = !isNumLockActive
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isNumLockActive) Color(0xFF39FF14) else Color.White.copy(alpha = 0.15f))
                                        )
                                        Text(
                                            text = "NUM",
                                            color = if (isNumLockActive) Color.White else Color.White.copy(alpha = 0.4f),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            style = androidx.compose.ui.text.TextStyle(
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                    
                                    // SCR
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                if (isConnected) {
                                                    btManager.sendKey(0x47, true)
                                                    btManager.sendKey(0x47, false)
                                                    if (lockSyncMode == "device") {
                                                        isScrollLockActive = !isScrollLockActive
                                                    }
                                                } else {
                                                    isScrollLockActive = !isScrollLockActive
                                                }
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (isScrollLockActive) Color(0xFF39FF14) else Color.White.copy(alpha = 0.15f))
                                        )
                                        Text(
                                            text = "SCR",
                                            color = if (isScrollLockActive) Color.White else Color.White.copy(alpha = 0.4f),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.SansSerif,
                                            style = androidx.compose.ui.text.TextStyle(
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                }

                                // 2. Layout Selector Pill
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val enabledLayouts = KeyboardLayoutType.values().filter { layout ->
                                                sharedPrefs.getStringSet("cycle_keyboard_layouts", KeyboardLayoutType.values().map { it.name }.toSet())?.contains(layout.name) == true
                                            }.ifEmpty { listOf(selectedLayoutType) }
                                            val currentIndexInEnabled = enabledLayouts.indexOf(selectedLayoutType)
                                            val nextIndex = (currentIndexInEnabled + 1) % enabledLayouts.size
                                            selectedLayoutType = enabledLayouts[nextIndex]
                                            soundSynth.playPress()
                                        }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = "Layout",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedLayoutType.displayName,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 3. Switch Selector Pill
                                val currentSwitch = soundSynth.getCurrentSwitch()
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val enabledSwitches = SwitchType.values().filter { switch ->
                                                sharedPrefs.getStringSet("cycle_key_sounds", SwitchType.values().map { it.name }.toSet())?.contains(switch.name) == true
                                            }.ifEmpty { listOf(currentSwitch) }
                                            val currentIndexInEnabled = enabledSwitches.indexOf(currentSwitch)
                                            val nextIndex = (currentIndexInEnabled + 1) % enabledSwitches.size
                                            val nextSwitch = enabledSwitches[nextIndex]
                                            soundSynth.changeSwitchType(nextSwitch)
                                            soundSynth.playPress()
                                        }
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Switch",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = currentSwitch.displayName,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 4. Case Color Selector Pill
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val enabledColors = CaseColor.values().filter { color ->
                                                sharedPrefs.getStringSet("cycle_case_colors", CaseColor.values().map { it.name }.toSet())?.contains(color.name) == true
                                            }.ifEmpty { listOf(selectedCaseColor) }
                                            val currentIndexInEnabled = enabledColors.indexOf(selectedCaseColor)
                                            val nextIndex = (currentIndexInEnabled + 1) % enabledColors.size
                                            selectedCaseColor = enabledColors[nextIndex]
                                            soundSynth.playRelease()
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

                                // 5. Mute Speaker Button (Squarish, height 28dp, radius 6dp)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val muted = !isMuted
                                            isMuted = muted
                                            soundSynth.setMute(muted)
                                            sharedPrefs.edit().putBoolean("key_sound_enabled", !muted).apply()
                                        }
                                        .testTag("mute_toggle"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // 2. Main Mechanical Keyboard Chassis (centered with custom margins)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            KeyboardView(
                                layoutType = selectedLayoutType,
                                caseColor = selectedCaseColor,
                                activePressedKeys = activePressedKeys,
                                isConnected = isConnected,
                                rgbMode = selectedRgbMode,
                                timeProgress = rgbTimeProgress,
                                isCapsLockActive = isCapsLockActive,
                                isNumLockActive = isNumLockActive,
                                isScrollLockActive = isScrollLockActive,
                                keySensitivity = keySensitivity,
                                onKeyPressChange = { code, press -> handleLocalKeyPress(code, press) }
                            )
                        }
                    }
                }
            }
        } else {
            // Configuration & Setup Portrait Screen
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.clickable {
                                        easterEggClicks++
                                        if (easterEggClicks < 5) {
                                            android.widget.Toast.makeText(context, "${5 - easterEggClicks} more clicks to activate", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            easterEggClicks = 0
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { 
                                context.startActivity(Intent(context, dev.arnv.bluke.SettingsActivity::class.java))
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                // Immersive Hub UI
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (btState is BluetoothState.BluetoothOff || btState is BluetoothState.Unsupported) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothDisabled,
                                contentDescription = "Bluetooth Off",
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = if (btState is BluetoothState.Unsupported) "Bluetooth Unsupported" else "Bluetooth is Off",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Please enable Bluetooth to connect devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(32.dp))
                            if (btState !is BluetoothState.Unsupported) {
                                Button(
                                    onClick = { 
                                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        btLauncher.launch(enableBtIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Turn On Bluetooth")
                                }
                            }
                        }
                    } else {
                        var isPairedExpanded by rememberSaveable { mutableStateOf(true) }
                        var isDiscoveredExpanded by rememberSaveable { mutableStateOf(true) }
                        val connectedDeviceState by btManager.connectedDevice.collectAsState()

                        val currentlyConnectedStateState by btManager.connectedDevice.collectAsState()
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Unified Top Scan & Status Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surface),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Bluetooth,
                                                        contentDescription = "Bluetooth",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (isScanning) "Scanning..." else "Ready to Scan",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Scan nearby devices",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        // Row for Pill status + Pill scan button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                            ) {
                                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    val statusColor = if (btState is BluetoothState.Connected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = when (btState) {
                                                            is BluetoothState.Connected -> "Connected"
                                                            is BluetoothState.PairingMode -> "Ready"
                                                            is BluetoothState.PermissionRequired -> "Permission Denied"
                                                            else -> "Offline"
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    if (isScanning) {
                                                        btManager.stopScanning()
                                                    } else {
                                                        btManager.startScanning()
                                                    }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isScanning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = if (isScanning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isScanning) Icons.Default.Close else Icons.Default.PlayArrow,
                                                    contentDescription = if (isScanning) "Stop" else "Scan",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isScanning) "Stop" else "Scan", 
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                        
                                        if (btState is BluetoothState.ProfileNotSupported || btMessage.contains("failed or stopped")) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = { btManager.restartHidService() },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry HID")
                                                Spacer(Modifier.width(8.dp))
                                                Text("Restart HID Service", fontWeight=FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Connected Device Card (Top Priority)
                            val currentlyConnectedState = connectedDeviceState
                            if (currentlyConnectedState != null) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "ACTIVE CONNECTION",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                                        )
                                        val deviceMessage = if ((currentlyConnectedState.name != null && btMessage.contains(currentlyConnectedState.name)) || btState is BluetoothState.Connected) btMessage else null
                                        DeviceRow(
                                            name = currentlyConnectedState.name ?: "Unknown Host",
                                            address = currentlyConnectedState.address,
                                            showAddress = showMacAddress,
                                            isConnected = true,
                                            bondState = currentlyConnectedState.bondState,
                                            statusText = deviceMessage,
                                            shape = RoundedCornerShape(28.dp),
                                            device = currentlyConnectedState,
                                            onActionClick = { btManager.disconnectDevice() }
                                        )
                                    }
                                }
                            } else {
                                // If not connected, check if there's an ongoing pairing/connection attempt and show it first
                                val activeDeviceAttempt = bondedDevices.firstOrNull { 
                                    (it.name != null && btMessage.contains(it.name)) || btMessage.contains(it.address) 
                                }
                                if (activeDeviceAttempt != null && btMessage.isNotEmpty() && btMessage != "Disconnected") {
                                      item {
                                          Column(modifier = Modifier.fillMaxWidth()) {
                                              Text(
                                                  text = "ACTIVE CONNECTION",
                                                  style = MaterialTheme.typography.bodyMedium,
                                                  fontWeight = FontWeight.Bold,
                                                  color = MaterialTheme.colorScheme.primary,
                                                  modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                                              )
                                              DeviceRow(
                                                  name = activeDeviceAttempt.name ?: "Unknown Host",
                                                  address = activeDeviceAttempt.address,
                                                  showAddress = showMacAddress,
                                                  isConnected = false,
                                                  bondState = activeDeviceAttempt.bondState,
                                                  statusText = btMessage,
                                                  shape = RoundedCornerShape(28.dp),
                                                  device = activeDeviceAttempt,
                                                  onActionClick = { btManager.connectDevice(activeDeviceAttempt) }
                                              )
                                          }
                                      }
                                }
                            }

                            // Paired Devices
                            val activeDeviceMac = if (currentlyConnectedState != null) currentlyConnectedState.address else bondedDevices.firstOrNull { btMessage.contains(it.name ?: "------") }?.address
                            val idleBonded = bondedDevices.filter { device -> device.address != activeDeviceMac && (!hideUnknownDevices || !device.name.isNullOrBlank()) && (!hideUnsupportedDevices || classifyDevice(device.name, device).isSupported) }

                            if (idleBonded.isNotEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isPairedExpanded = !isPairedExpanded }
                                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "PAIRED DEVICES",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${idleBonded.size} devices",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Icon(
                                                    imageVector = if (isPairedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start=4.dp).size(18.dp)
                                                )
                                            }
                                        }

                                        if (isPairedExpanded) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                                idleBonded.forEachIndexed { index, device ->
                                                    val deviceMsg = if ((device.name != null && btMessage.contains(device.name)) || btMessage.contains(device.address)) btMessage else null
                                                    val topRadius = if (index == 0) 28.dp else 4.dp
                                                    val bottomRadius = if (index == idleBonded.size - 1) 28.dp else 4.dp
                                                    DeviceRow(
                                                        name = device.name ?: "Unknown Host",
                                                        address = device.address,
                                                        showAddress = showMacAddress,
                                                        isConnected = false,
                                                        bondState = device.bondState,
                                                        statusText = deviceMsg,
                                                        shape = RoundedCornerShape(
                                                            topStart = topRadius,
                                                            topEnd = topRadius,
                                                            bottomStart = bottomRadius,
                                                            bottomEnd = bottomRadius
                                                        ),
                                                        device = device,
                                                        onActionClick = { btManager.connectDevice(device) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (bondedDevices.isEmpty()) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = "PAIRED DEVICES",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                                        )
                                        Text(
                                            text = "No paired devices yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Discovered devices (Collapsible)
                            val nonBondedDevices = scannedDevices.filter { device -> device.bondState != BluetoothDevice.BOND_BONDED && device.address != activeDeviceMac && (!hideUnknownDevices || !device.name.isNullOrBlank()) && (!hideUnsupportedDevices || classifyDevice(device.name, device).isSupported) }
                            if (nonBondedDevices.isNotEmpty() || isScanning) {
                                item {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isDiscoveredExpanded = !isDiscoveredExpanded }
                                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "DISCOVERED DEVICES",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${nonBondedDevices.size} found",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Icon(
                                                    imageVector = if (isDiscoveredExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start=4.dp).size(18.dp)
                                                )
                                            }
                                        }

                                        if (isDiscoveredExpanded) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                                                nonBondedDevices.forEachIndexed { index, device ->
                                                    val deviceMsg = if ((device.name != null && btMessage.contains(device.name)) || btMessage.contains(device.address)) btMessage else null
                                                    val topRadius = if (index == 0) 28.dp else 4.dp
                                                    val bottomRadius = if (index == nonBondedDevices.size - 1) 28.dp else 4.dp
                                                    DeviceRow(
                                                        name = device.name ?: "Unnamed Device",
                                                        address = device.address,
                                                        showAddress = showMacAddress,
                                                        isConnected = false,
                                                        bondState = device.bondState,
                                                        statusText = deviceMsg,
                                                        shape = RoundedCornerShape(
                                                            topStart = topRadius,
                                                            topEnd = topRadius,
                                                            bottomStart = bottomRadius,
                                                            bottomEnd = bottomRadius
                                                        ),
                                                        device = device,
                                                        onActionClick = { btManager.connectDevice(device) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Sticky Launch Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
                                )
                            )
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Mode Toggle Indicator Button
                            IconButton(
                                onClick = {
                                    val enabledModes = listOf(0, 1, 2).filter { mode ->
                                        val modeStr = when (mode) {
                                            0 -> "keyboard"
                                            1 -> "touchpad"
                                            2 -> "gamepad"
                                            else -> "keyboard"
                                        }
                                        sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad"))?.contains(modeStr) == true
                                    }.ifEmpty { listOf(0) }
                                    val currentIndex = enabledModes.indexOf(launchMode).coerceAtLeast(0)
                                    val nextMode = enabledModes[(currentIndex + 1) % enabledModes.size]
                                    launchMode = nextMode
                                    sharedPrefs.edit().putInt("launch_mode", nextMode).apply()
                                    if (isHapticsEnabled) {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                    }
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .testTag("mode_toggle_btn")
                            ) {
                                val modeIcon = when (launchMode) {
                                    1 -> Icons.Default.Mouse
                                    2 -> Icons.Default.SportsEsports
                                    else -> Icons.Default.Keyboard
                                }
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = "Cycle Input Mode",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            // Dynamic Launch Option button
                            Button(
                                onClick = { isKeyboardActive = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .testTag("start_keyboard_btn"),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                val launchText = when (launchMode) {
                                    1 -> "Launch Touchpad"
                                    2 -> "Launch Gamepad"
                                    else -> "Launch Keyboard"
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Launch Icon",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = launchText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

            }
        }
    }
}

}

