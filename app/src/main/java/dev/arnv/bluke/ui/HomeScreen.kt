package dev.arnv.bluke.ui
import android.annotation.SuppressLint
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
import androidx.compose.animation.*
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.arnv.bluke.R
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.CURRENT_HID_DESCRIPTOR_REVISION
import dev.arnv.bluke.bluetooth.HID_DESCRIPTOR_REVISION_PREFERENCE
import dev.arnv.bluke.bluetooth.requiresHidDescriptorRefresh
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import dev.arnv.bluke.sound.SwitchType

@SuppressLint("MissingPermission")
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    btManager: BluetoothKeyboardManager,
    soundSynth: KeyboardSoundSynthesizer
) {
    val context = LocalContext.current
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(btManager))
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    
    // UI state
    var selectedLayoutType by rememberSaveable { mutableStateOf(KeyboardLayoutType.OBLIVION_75) }
    var selectedCaseColor by rememberSaveable { mutableStateOf(CaseColor.BLACK) }
    var isKeyboardActive by rememberSaveable { mutableStateOf(false) }
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    var hideUnknownDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unknown", false)) }
    var hideUnsupportedDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unsupported", true)) }
    var showMacAddress by remember { mutableStateOf(sharedPrefs.getBoolean("show_mac", false)) }
    var easterEggClicks by remember { mutableIntStateOf(0) }
    
    val view = LocalView.current
    var isHapticsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("haptics_enabled", true)) }
    var keySensitivity by remember { mutableFloatStateOf(sharedPrefs.getFloat("key_sensitivity", 6f)) }
    var lockSyncMode by remember { mutableStateOf(sharedPrefs.getString("lock_sync_mode", "host") ?: "host") }
    var launchMode by rememberSaveable { mutableIntStateOf(sharedPrefs.getInt("launch_mode", 0)) }

    // Sound synth switch state
    var currentSwitch by remember { mutableStateOf(soundSynth.getCurrentSwitch()) }

    // Mute state
    var isMuted by rememberSaveable { mutableStateOf(!sharedPrefs.getBoolean("key_sound_enabled", true)) }

    var devModeRefreshTrigger by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sharedPrefs, soundSynth) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hideUnknownDevices = sharedPrefs.getBoolean("hide_unknown", false)
                hideUnsupportedDevices = sharedPrefs.getBoolean("hide_unsupported", true)
                showMacAddress = sharedPrefs.getBoolean("show_mac", false)
                val soundEnabled = sharedPrefs.getBoolean("key_sound_enabled", true)
                isMuted = !soundEnabled
                soundSynth.setMute(!soundEnabled)
                currentSwitch = soundSynth.getCurrentSwitch()
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
                    sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad", "gamepad"))?.contains(modeStr) == true
                }.ifEmpty { listOf(0) }
                val savedLaunchMode = sharedPrefs.getInt("launch_mode", 0)
                launchMode = if (enabledModes.contains(savedLaunchMode)) savedLaunchMode else enabledModes.first()
                devModeRefreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    
    // Bluetooth status flows
    val realBtState = homeUiState.bluetoothState
    
    val btState = remember(realBtState, devModeRefreshTrigger) {
        if (sharedPrefs.getBoolean("is_developer_mode", false)) {
            if (sharedPrefs.getBoolean("mock_bt_disabled", false)) return@remember dev.arnv.bluke.bluetooth.BluetoothState.BluetoothOff
            if (sharedPrefs.getBoolean("mock_device_unsupported", false)) return@remember dev.arnv.bluke.bluetooth.BluetoothState.Unsupported
            if (sharedPrefs.getBoolean("mock_hid_unsupported", false)) return@remember dev.arnv.bluke.bluetooth.BluetoothState.ProfileNotSupported
        }
        realBtState
    }
    val btMessage = homeUiState.statusMessage
    

    
    val bondedDevices = homeUiState.bondedDevices
    val scannedDevices = homeUiState.scannedDevices
    val isScanning = homeUiState.isScanning
    
    // Active pressed keys set for visually pressing keycaps
    val activePressedKeys = remember { mutableStateListOf<Int>() }

    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var descriptorRefreshRequired by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sharedPrefs) {
        val currentVersionCode = dev.arnv.bluke.BuildConfig.VERSION_CODE
        val savedVersionCode = sharedPrefs.getInt("last_run_version_code", 0)
        val hasSeenOnboarding = sharedPrefs.getBoolean("has_seen_onboarding", false)
        descriptorRefreshRequired = requiresHidDescriptorRefresh(
            savedRevision = sharedPrefs.getInt(HID_DESCRIPTOR_REVISION_PREFERENCE, 1),
            isExistingInstallation = savedVersionCode > 0 || hasSeenOnboarding,
        )
        
        // If they have seen onboarding, they are an existing user.
        // If savedVersionCode < currentVersionCode, it's an update.
        if ((savedVersionCode > 0 && savedVersionCode < currentVersionCode) || 
            (savedVersionCode == 0 && hasSeenOnboarding) || descriptorRefreshRequired) {
            showUpdateDialog = true
        }
        
        if (savedVersionCode != currentVersionCode) {
            sharedPrefs.edit { putInt("last_run_version_code", currentVersionCode) }
        }
    }
    
    if (sharedPrefs.getBoolean("is_developer_mode", false) && sharedPrefs.getBoolean("mock_update_popup", false)) {
        showUpdateDialog = true
    }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    if (showUpdateDialog) {
        val versionName = dev.arnv.bluke.BuildConfig.VERSION_NAME
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                showUpdateDialog = false
                sharedPrefs.edit { putBoolean("mock_update_popup", false) }
            },
            title = {
                Text(
                    if (descriptorRefreshRequired) "Bluetooth pairing refresh required"
                    else "Updated to v$versionName"
                )
            },
            text = {
                Text(
                    if (descriptorRefreshRequired) {
                        "Bluke's gamepad HID descriptor changed, but Bluetooth hosts cache the old layout. " +
                            "To restore the D-pad and center-button mappings, forget the host on this phone, " +
                            "remove Bluke on the host, then pair again. Reinstalling the app alone is not enough."
                    } else {
                        "We've added new features and made significant underlying changes to the controller!\n" +
                            "For detailed information, see the changelog."
                    }
                )
            },
            confirmButton = {
                Button(onClick = { 
                    showUpdateDialog = false 
                    sharedPrefs.edit {
                        putBoolean("mock_update_popup", false)
                        if (descriptorRefreshRequired) {
                            putInt(HID_DESCRIPTOR_REVISION_PREFERENCE, CURRENT_HID_DESCRIPTOR_REVISION)
                        }
                    }
                    descriptorRefreshRequired = false
                }) {
                    Text("Got it")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { 
                    uriHandler.openUri("https://github.com/arnav-kr/Bluke/releases")
                }) {
                    Text("Changelog")
                }
            }
        )
    }

    // Connection helper declared at outer scope
    val isConnected = btState is BluetoothState.Connected

    // Lock Indicator State variables - single source of truth, reactive to local presses and system LED reports
    var isCapsLockActive by rememberSaveable { mutableStateOf(false) }
    var isNumLockActive by rememberSaveable { mutableStateOf(true) }
    var isScrollLockActive by rememberSaveable { mutableStateOf(false) }

    val systemCapsLock = homeUiState.capsLock
    val systemNumLock = homeUiState.numLock
    val systemScrollLock = homeUiState.scrollLock

    LaunchedEffect(isConnected, systemCapsLock, systemNumLock, systemScrollLock, lockSyncMode) {
        if (isConnected && lockSyncMode == "host") {
            isCapsLockActive = systemCapsLock
            isNumLockActive = systemNumLock
            isScrollLockActive = systemScrollLock
        }
    }

    // Track last connected device for reconnection
    var lastConnectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

    LaunchedEffect(btState, homeUiState.connectedDevice) {
        if (btState is BluetoothState.Connected) {
            homeUiState.connectedDevice?.let {
                lastConnectedDevice = it
            }
        }
    }

    // Show Toast for connection errors, timeouts, rejections, or pairing failures
    LaunchedEffect(btMessage) {
        val lowerMessage = btMessage.lowercase()
        if (lowerMessage.contains("timed out") ||
            lowerMessage.contains("rejected") ||
            lowerMessage.contains("failed") ||
            lowerMessage.contains("refused") ||
            lowerMessage.contains("error")
        ) {
            android.widget.Toast.makeText(context, btMessage, android.widget.Toast.LENGTH_SHORT).show()
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
                @Suppress("SourceLockedOrientationActivity")
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
                        0x47 -> isScrollLockActive = !isScrollLockActive // KEY_SCROLL_LOCK
                        0x53 -> isNumLockActive = !isNumLockActive // KEY_NUM_LOCK
                    }
                }
            }
        } else {
            activePressedKeys.remove(keyCode)
            soundSynth.playRelease(keyCode)
            btManager.sendKey(keyCode, false)
        }
    }



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
                    val darkScheme = darkColorScheme(
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
                                        sharedPrefs.edit { putInt("launch_mode", newMode) }
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
                                        sharedPrefs.edit { putInt("launch_mode", newMode) }
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
                                                sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad", "gamepad"))?.contains(modeStr) == true
                                            }.ifEmpty { listOf(0) }
                                            val currentIndexInEnabled = enabledModes.indexOf(launchMode)
                                            val nextIndex = (currentIndexInEnabled + 1) % enabledModes.size
                                            val nextMode = enabledModes[nextIndex]
                                            launchMode = nextMode
                                            sharedPrefs.edit { putInt("launch_mode", nextMode) }
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
                                val activeDevice = homeUiState.connectedDevice ?: lastConnectedDevice
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
                                            val savedSet = sharedPrefs.getStringSet("cycle_keyboard_layouts", null)
                                            val enabledLayouts = if (savedSet == null) {
                                                KeyboardLayoutType.entries
                                            } else {
                                                KeyboardLayoutType.entries.filter { savedSet.contains(it.name) }
                                            }.ifEmpty { listOf(selectedLayoutType) }
                                            val currentIndexInEnabled = enabledLayouts.indexOf(selectedLayoutType)
                                            val nextIndex = if (currentIndexInEnabled < 0) 0 else (currentIndexInEnabled + 1) % enabledLayouts.size
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
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable {
                                            val enabledSwitches = SwitchType.entries.filter { switch ->
                                                sharedPrefs.getStringSet("cycle_key_sounds", SwitchType.entries.map { it.name }.toSet())?.contains(switch.name) == true
                                            }.ifEmpty { listOf(currentSwitch) }
                                            val currentIndexInEnabled = enabledSwitches.indexOf(currentSwitch)
                                            val nextIndex = (currentIndexInEnabled + 1) % enabledSwitches.size
                                            val nextSwitch = enabledSwitches[nextIndex]
                                            soundSynth.changeSwitchType(nextSwitch)
                                            currentSwitch = nextSwitch
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
                                            val enabledColors = CaseColor.entries.filter { color ->
                                                sharedPrefs.getStringSet("cycle_case_colors", CaseColor.entries.map { it.name }.toSet())?.contains(color.name) == true
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
                                            sharedPrefs.edit { putBoolean("key_sound_enabled", !muted) }
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
                                            val intent = Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=dQw4w9WgXcQ".toUri())
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
                    if (btState is BluetoothState.BluetoothOff || btState is BluetoothState.Unsupported || btState is BluetoothState.ProfileNotSupported) {
                        ProfileNotSupportedScreen(
                            bluetoothState = btState,
                            onEnableBluetooth = {
                                btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            },
                            onRetry = btManager::checkBluetoothCapabilities
                        )
                    } else {
                        var isPairedExpanded by rememberSaveable { mutableStateOf(true) }
                        var isDiscoveredExpanded by rememberSaveable { mutableStateOf(true) }
                        val connectedDeviceState = homeUiState.connectedDevice

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Unified Top Scan & Status Card
                            item {
                                StatusHeaderCard(
                                    bluetoothState = btState,
                                    hidLifecycleState = homeUiState.hidLifecycleState,
                                    isScanning = isScanning,
                                    onToggleScan = {
                                        if (isScanning) btManager.stopScanning() else btManager.startScanning()
                                    },
                                    onRestartHid = btManager::restartHidService
                                )
                            }

                            DeviceListSection(
                                bluetoothState = btState,
                                statusMessage = btMessage,
                                connectedDevice = connectedDeviceState,
                                bondedDevices = bondedDevices,
                                scannedDevices = scannedDevices,
                                isScanning = isScanning,
                                showMacAddress = showMacAddress,
                                hideUnknownDevices = hideUnknownDevices,
                                hideUnsupportedDevices = hideUnsupportedDevices,
                                isPairedExpanded = isPairedExpanded,
                                isDiscoveredExpanded = isDiscoveredExpanded,
                                onPairedExpandedChange = { isPairedExpanded = it },
                                onDiscoveredExpandedChange = { isDiscoveredExpanded = it },
                                onConnect = btManager::connectDevice,
                                onDisconnect = btManager::disconnectDevice
                            )
                        }
                    }

                    // Sticky Launch Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
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
                                        sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad", "gamepad"))?.contains(modeStr) == true
                                    }.ifEmpty { listOf(0) }
                                    val currentIndex = enabledModes.indexOf(launchMode).coerceAtLeast(0)
                                    val nextMode = enabledModes[(currentIndex + 1) % enabledModes.size]
                                    launchMode = nextMode
                                    sharedPrefs.edit { putInt("launch_mode", nextMode) }
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
