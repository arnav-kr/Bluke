package dev.arnv.bluke.ui
import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import android.view.View
import androidx.compose.animation.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.arnv.bluke.R
import dev.arnv.bluke.KeyboardThemesActivity
import dev.arnv.bluke.KeyboardLayoutsActivity
import dev.arnv.bluke.HelpActivity
import dev.arnv.bluke.QuickCycleActivity
import dev.arnv.bluke.SoundPacksActivity
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.ConsumerControl
import dev.arnv.bluke.bluetooth.CURRENT_HID_DESCRIPTOR_REVISION
import dev.arnv.bluke.bluetooth.HID_DESCRIPTOR_REVISION_PREFERENCE
import dev.arnv.bluke.bluetooth.requiresHidDescriptorRefresh
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import dev.arnv.bluke.sound.SwitchType
import dev.arnv.bluke.sound.soundCycleSelection
import dev.arnv.bluke.data.CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE
import dev.arnv.bluke.data.CYCLE_KEYBOARD_THEMES_PREFERENCE
import dev.arnv.bluke.data.KEYBOARD_GEOMETRY_PREFERENCE
import dev.arnv.bluke.data.KeyboardThemeRepository

@SuppressLint("MissingPermission")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }
    val keyboardThemeRepository = remember(context) { KeyboardThemeRepository(context) }
    var selectedGeometry by rememberSaveable {
        mutableStateOf(
            KeyboardGeometry.fromPreference(sharedPrefs.getString(KEYBOARD_GEOMETRY_PREFERENCE, null))
        )
    }
    var selectedKeyboardTheme by remember { mutableStateOf(keyboardThemeRepository.selectedTheme()) }
    var characterLayout by rememberSaveable {
        mutableStateOf(
            KeyboardCharacterLayout.fromPreference(
                sharedPrefs.getString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, null)
            )
        )
    }
    var isKeyboardActive by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isKeyboardActive) {
        isKeyboardActive = false
    }
    var hideUnknownDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unknown", false)) }
    var hideUnsupportedDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unsupported", true)) }
    var showMacAddress by remember { mutableStateOf(sharedPrefs.getBoolean("show_mac", false)) }
    var easterEggClicks by remember { mutableIntStateOf(0) }
    
    val view = LocalView.current
    var isHapticsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("haptics_enabled", true)) }
    var keySensitivity by remember { mutableFloatStateOf(sharedPrefs.getFloat("key_sensitivity", 6f)) }
    var lockSyncMode by remember { mutableStateOf(sharedPrefs.getString("lock_sync_mode", "host") ?: "host") }
    var launchMode by rememberSaveable {
        val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
        val savedMode = sharedPrefs.getInt("launch_mode", InputMode.KEYBOARD.id)
        mutableIntStateOf(savedMode.takeIf(enabledModes::contains) ?: enabledModes.first())
    }

    // Sound synth switch state
    var currentSwitch by remember { mutableStateOf(soundSynth.getCurrentSwitch()) }
    var currentSoundProfileName by remember { mutableStateOf(soundSynth.getSelectedSoundProfileName()) }

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
                currentSoundProfileName = soundSynth.getSelectedSoundProfileName()
                isHapticsEnabled = sharedPrefs.getBoolean("haptics_enabled", true)
                keySensitivity = sharedPrefs.getFloat("key_sensitivity", 6f)
                lockSyncMode = sharedPrefs.getString("lock_sync_mode", "host") ?: "host"
                characterLayout = KeyboardCharacterLayout.fromPreference(
                    sharedPrefs.getString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, null)
                )
                selectedGeometry = KeyboardGeometry.fromPreference(
                    sharedPrefs.getString(KEYBOARD_GEOMETRY_PREFERENCE, null)
                )
                selectedKeyboardTheme = keyboardThemeRepository.selectedTheme()
                val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
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
    var isFnActive by remember { mutableStateOf(false) }
    var activeConsumerKey by remember { mutableStateOf<Int?>(null) }
    val fnConsumedKeys = remember { mutableSetOf<Int>() }

    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var descriptorRefreshRequired by rememberSaveable { mutableStateOf(false) }
    var showGamepadGuide by rememberSaveable { mutableStateOf(false) }
    var connectionAttempts by rememberSaveable { mutableIntStateOf(0) }
    var showTroubleshootingNudge by rememberSaveable { mutableStateOf(false) }
    var previousConnectionState by remember { mutableStateOf<Boolean?>(null) }
    var connectionTransitions by remember { mutableStateOf(emptyList<Long>()) }

    LaunchedEffect(devModeRefreshTrigger) {
        if (sharedPrefs.getBoolean("is_developer_mode", false)) {
            if (sharedPrefs.getBoolean("mock_gamepad_guide", false)) showGamepadGuide = true
            if (sharedPrefs.getBoolean("mock_troubleshooting_nudge", false)) {
                showTroubleshootingNudge = true
            }
        }
    }

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
                        "Bluke's HID descriptor changed to add corrected gamepad mappings and media controls, " +
                            "but Bluetooth hosts cache the old layout. To use the new controls, forget the host on this phone, " +
                            "remove Bluke on the host, then pair again once. Reinstalling the app alone is not enough. " +
                            "After this refresh, switching controller behavior or using Fn shortcuts does not require pairing again."
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

    LaunchedEffect(isConnected) {
        val previousState = previousConnectionState
        if (previousState != null && previousState != isConnected) {
            val now = SystemClock.elapsedRealtime()
            connectionTransitions = (connectionTransitions + now).filter {
                now - it <= CONNECTION_HELP_CHURN_WINDOW_MILLIS
            }
            if (
                shouldOfferConnectionHelp(
                    transitionTimestamps = connectionTransitions,
                    nowMillis = now,
                )
            ) {
                showTroubleshootingNudge = true
            }
        }
        previousConnectionState = isConnected
        if (isConnected) {
            connectionAttempts = 0
        }
    }

    LaunchedEffect(btMessage, isConnected) {
        val attemptInProgress = btMessage.contains("connecting", ignoreCase = true) ||
            btMessage.contains("pairing", ignoreCase = true) ||
            btMessage.contains("switching", ignoreCase = true)
        if (!isConnected && attemptInProgress) {
            kotlinx.coroutines.delay(CONNECTION_HELP_STALL_MILLIS)
            if (shouldOfferConnectionHelp(stalledForMillis = CONNECTION_HELP_STALL_MILLIS)) {
                showTroubleshootingNudge = true
            }
        }
    }

    if (showGamepadGuide) {
        AlertDialog(
            onDismissRequest = {
                showGamepadGuide = false
                sharedPrefs.edit { putBoolean("mock_gamepad_guide", false) }
            },
            icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
            title = { Text("Before using the gamepad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Start with D-pad: Native games. Switch to Browser games only when a web game ignores directions.")
                    Text("On Windows, some newer games accept only Xbox XInput controllers. Bluke is a standard Bluetooth HID gamepad, so Steam Input or another compatibility layer may be needed.")
                    Text("If the host cached an older Bluke controller layout, forget Bluke on both devices and pair again once.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit { putBoolean("has_seen_gamepad_guide", true) }
                        sharedPrefs.edit { putBoolean("mock_gamepad_guide", false) }
                        showGamepadGuide = false
                        isKeyboardActive = true
                    },
                ) { Text("Open gamepad") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGamepadGuide = false
                    sharedPrefs.edit { putBoolean("mock_gamepad_guide", false) }
                }) { Text("Not now") }
            },
        )
    }

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
    LaunchedEffect(btMessage, btState) {
        if (shouldShowBluetoothErrorToast(btState, btMessage)) {
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
        if (keyCode == KeyboardLayouts.KEY_FN) {
            if (isPress && !isFnActive) {
                if (isHapticsEnabled) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_PRESS)
                }
                isFnActive = true
                activePressedKeys.add(keyCode)
                soundSynth.playPress(keyCode)
            } else if (!isPress && isFnActive) {
                isFnActive = false
                activePressedKeys.remove(keyCode)
                soundSynth.playRelease(keyCode)
                if (activeConsumerKey != null) {
                    btManager.sendConsumerControl(null)
                    activeConsumerKey = null
                }
            }
            return
        }

        val fnControl = if (isPress && isFnActive) consumerControlForFnKey(keyCode) else null
        if (fnControl != null) {
            if (isHapticsEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_PRESS)
            }
            if (fnConsumedKeys.add(keyCode)) {
                activePressedKeys.add(keyCode)
                soundSynth.playPress(keyCode)
                activeConsumerKey = keyCode
                btManager.sendConsumerControl(fnControl)
            }
            return
        }

        if (!isPress && fnConsumedKeys.remove(keyCode)) {
            activePressedKeys.remove(keyCode)
            soundSynth.playRelease(keyCode)
            if (activeConsumerKey == keyCode) {
                btManager.sendConsumerControl(null)
                activeConsumerKey = null
            }
            return
        }

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
            val caseColorVal = Color(selectedKeyboardTheme.caseArgb)
            val caseMetallic = selectedKeyboardTheme.caseMetallic
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
                SolidColor(caseColorVal)
            }

            when (launchMode) {
                1, 2, 4 -> {
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
                            4 -> {
                                MediaPresentationView(
                                    btManager = btManager,
                                    onClose = { isKeyboardActive = false },
                                    launchMode = launchMode,
                                    onModeChange = { newMode ->
                                        launchMode = newMode
                                        sharedPrefs.edit { putInt("launch_mode", newMode) }
                                    },
                                    sharedPrefs = sharedPrefs,
                                    isConnected = isConnected,
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
                                        .combinedClickable(
                                            onClickLabel = "Next input mode",
                                            onLongClickLabel = "Configure input mode cycle",
                                            onClick = {
                                                val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
                                                val currentIndexInEnabled = enabledModes.indexOf(launchMode)
                                                val nextIndex = (currentIndexInEnabled + 1) % enabledModes.size
                                                val nextMode = enabledModes[nextIndex]
                                                launchMode = nextMode
                                                sharedPrefs.edit { putInt("launch_mode", nextMode) }
                                                if (isHapticsEnabled) {
                                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                                }
                                            },
                                            onLongClick = {
                                                context.startActivity(Intent(context, QuickCycleActivity::class.java))
                                            },
                                        )
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
                                        .combinedClickable(
                                            onClickLabel = "Next keyboard layout",
                                            onLongClickLabel = "Configure keyboard layout cycle",
                                            onClick = {
                                                val savedSet = sharedPrefs.getStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, null)
                                                val enabledLayouts = if (savedSet == null) {
                                                    KeyboardGeometry.entries
                                                } else {
                                                    KeyboardGeometry.entries.filter { savedSet.contains(it.name) }
                                                }.ifEmpty { listOf(selectedGeometry) }
                                                val currentIndexInEnabled = enabledLayouts.indexOf(selectedGeometry)
                                                val nextIndex = if (currentIndexInEnabled < 0) 0 else (currentIndexInEnabled + 1) % enabledLayouts.size
                                                selectedGeometry = enabledLayouts[nextIndex]
                                                sharedPrefs.edit { putString(KEYBOARD_GEOMETRY_PREFERENCE, selectedGeometry.name) }
                                                soundSynth.playPress()
                                            },
                                            onLongClick = {
                                                context.startActivity(Intent(context, KeyboardLayoutsActivity::class.java))
                                            },
                                        )
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
                                        text = selectedGeometry.displayName,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 90.dp),
                                    )
                                }

                                // 3. Switch Selector Pill
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .combinedClickable(
                                            onClickLabel = "Next key sound",
                                            onLongClickLabel = "Manage key sounds",
                                            onClick = {
                                                val enabledProfiles = soundCycleSelection(
                                                    sharedPrefs,
                                                    dev.arnv.bluke.sound.CustomSoundPackRepository(context).listPacks().map { it.id },
                                                )
                                                soundSynth.cycleSoundProfile(enabledProfiles)
                                                currentSwitch = soundSynth.getCurrentSwitch()
                                                currentSoundProfileName = soundSynth.getSelectedSoundProfileName()
                                                soundSynth.playPress()
                                            },
                                            onLongClick = {
                                                context.startActivity(Intent(context, SoundPacksActivity::class.java))
                                            },
                                        )
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
                                        text = currentSoundProfileName,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // 4. Keyboard Theme Selector Pill
                                Row(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .combinedClickable(
                                            onClickLabel = "Next keyboard theme",
                                            onLongClickLabel = "Customize keyboard themes",
                                            onClick = {
                                                val availableThemes = keyboardThemeRepository.allThemes()
                                                val savedThemeIds = sharedPrefs.getStringSet(
                                                    CYCLE_KEYBOARD_THEMES_PREFERENCE,
                                                    null,
                                                )
                                                val enabledThemes = if (savedThemeIds == null) {
                                                    availableThemes
                                                } else {
                                                    availableThemes.filter { theme -> theme.id in savedThemeIds }
                                                }.ifEmpty { listOf(selectedKeyboardTheme) }
                                                val currentIndex = enabledThemes.indexOfFirst {
                                                    it.id == selectedKeyboardTheme.id
                                                }
                                                val nextIndex = if (currentIndex < 0) {
                                                    0
                                                } else {
                                                    (currentIndex + 1) % enabledThemes.size
                                                }
                                                selectedKeyboardTheme = enabledThemes[nextIndex]
                                                keyboardThemeRepository.selectTheme(selectedKeyboardTheme.id)
                                                soundSynth.playPress()
                                            },
                                            onLongClick = {
                                                context.startActivity(
                                                    Intent(context, KeyboardThemesActivity::class.java),
                                                )
                                            },
                                        )
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(selectedKeyboardTheme.accentStyle.backgroundArgb))
                                            .border(0.5.dp, Color.White, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedKeyboardTheme.name,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 80.dp),
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
                                geometry = selectedGeometry,
                                theme = selectedKeyboardTheme,
                                characterLayout = characterLayout,
                                activePressedKeys = activePressedKeys,
                                isConnected = isConnected,
                                isCapsLockActive = isCapsLockActive,
                                isNumLockActive = isNumLockActive,
                                isScrollLockActive = isScrollLockActive,
                                isFnActive = isFnActive,
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
                    if (btState.blocksInputLaunch()) {
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

                            if (showTroubleshootingNudge) {
                                item {
                                    TroubleshootingNudgeCard(
                                        onOpenHelp = {
                                            context.startActivity(Intent(context, HelpActivity::class.java))
                                        },
                                        onDismiss = {
                                            showTroubleshootingNudge = false
                                            sharedPrefs.edit { putBoolean("mock_troubleshooting_nudge", false) }
                                        },
                                    )
                                }
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
                                onConnect = { device ->
                                    connectionAttempts += 1
                                    if (shouldOfferConnectionHelp(connectionAttempts = connectionAttempts)) {
                                        showTroubleshootingNudge = true
                                    }
                                    btManager.connectDevice(device)
                                },
                                onDisconnect = btManager::disconnectDevice
                            )
                        }
                    }

                    // Sticky Launch Button
                    if (!btState.blocksInputLaunch()) Box(
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
                                    val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
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
                                    4 -> Icons.Default.Slideshow
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
                                onClick = {
                                    if (
                                        launchMode == InputMode.GAMEPAD.id &&
                                        !sharedPrefs.getBoolean("has_seen_gamepad_guide", false)
                                    ) {
                                        showGamepadGuide = true
                                    } else {
                                        isKeyboardActive = true
                                    }
                                },
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
                                    4 -> "Launch Multimedia"
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

@Composable
private fun TroubleshootingNudgeCard(
    onOpenHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Having trouble connecting?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Check both pairing prompts or follow the safe repair steps. A slow attempt does not automatically mean this phone is incompatible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onOpenHelp) { Text("Help") }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}
