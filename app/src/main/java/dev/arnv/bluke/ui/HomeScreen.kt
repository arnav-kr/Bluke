package dev.arnv.bluke.ui
import dev.arnv.bluke.ui.theme.MyApplicationTheme
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import android.content.ContextWrapper
import android.view.InputDevice
import android.view.KeyEvent
import dev.arnv.bluke.HardwareKeyHost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.arnv.bluke.R
import dev.arnv.bluke.KeyboardThemesActivity
import dev.arnv.bluke.KeyboardLayoutsActivity
import dev.arnv.bluke.HelpActivity
import dev.arnv.bluke.QuickCycleActivity
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.core.net.toUri
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.ConsumerControl
import dev.arnv.bluke.bluetooth.CURRENT_HID_DESCRIPTOR_REVISION
import dev.arnv.bluke.bluetooth.HID_DESCRIPTOR_REVISION_PREFERENCE
import dev.arnv.bluke.bluetooth.requiresHidDescriptorRefresh
import dev.arnv.bluke.data.CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE
import dev.arnv.bluke.data.CYCLE_KEYBOARD_THEMES_PREFERENCE
import dev.arnv.bluke.data.KEYBOARD_GEOMETRY_PREFERENCE
import dev.arnv.bluke.data.KeyboardThemeRepository
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    btManager: BluetoothKeyboardManager,
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
    var isHapticsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(HAPTICS_PREFERENCE, true)) }
    val keyPressVibrator = remember(context) { KeyPressVibrator(context.applicationContext) }
    var isKeyVibrationEnabled by remember { mutableStateOf(sharedPrefs.isKeyVibrationEnabled()) }
    var keyVibrationStrength by remember { mutableStateOf(sharedPrefs.keyVibrationStrength()) }
    fun vibrateKeyPress() {
        if (isKeyVibrationEnabled) keyPressVibrator.vibrate(keyVibrationStrength)
    }
    var keySensitivity by remember { mutableFloatStateOf(sharedPrefs.getFloat("key_sensitivity", 6f)) }
    var lockSyncMode by remember { mutableStateOf(sharedPrefs.getString("lock_sync_mode", "host") ?: "host") }
    var keyboardOrientation by remember {
        mutableStateOf(KeyboardOrientation.fromPreference(sharedPrefs.getString(KEYBOARD_ORIENTATION_PREFERENCE, null)))
    }
    var portraitKeyboardLayout by remember {
        mutableStateOf(PortraitKeyboardLayout.fromPreference(sharedPrefs.getString(PORTRAIT_KEYBOARD_LAYOUT_PREFERENCE, null)))
    }
    var isKeyPreviewEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(KEY_PREVIEW_PREFERENCE, true)) }
    var keyboardHostOs by remember { mutableStateOf(sharedPrefs.keyboardHostOs()) }
    var keyboardStyle by remember {
        mutableStateOf(KeyboardStyle.fromPreference(sharedPrefs.getString(KEYBOARD_STYLE_PREFERENCE, null)))
    }
    var trackpadChoice by remember { mutableStateOf(sharedPrefs.keyboardTrackpadChoice()) }
    var trackpadSide by remember {
        mutableStateOf(TrackpadSide.fromPreference(sharedPrefs.getString(KEYBOARD_TRACKPAD_SIDE_PREFERENCE, null)))
    }
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // Set by dragging the handle above the keyboard; kept per orientation.
    var isLaptopLayoutEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(KEYBOARD_LAPTOP_LAYOUT_PREFERENCE, false)) }
    var laptopTrackpadPosition by remember {
        mutableStateOf(LaptopTrackpadPosition.fromPreference(sharedPrefs.getString(KEYBOARD_LAPTOP_TRACKPAD_PREFERENCE, null)))
    }
    val isWideScreen = isWideEnoughForLaptopLayout(LocalConfiguration.current.screenWidthDp)
    var keyboardHeightFraction by remember(isPortrait) { mutableStateOf(sharedPrefs.keyboardHeightFraction(isPortrait)) }
    var hardwareKeyboardMode by remember { mutableStateOf(sharedPrefs.hardwareKeyboardMode()) }
    val hasPhysicalKeyboard = LocalConfiguration.current.hasPhysicalKeyboard()
    // Letter keys are hidden and the phone's own keyboard types instead.
    val isHardwareKeyboardMode = hardwareKeyboardMode.isActive(hasPhysicalKeyboard)
    var launchMode by rememberSaveable {
        val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
        val savedMode = sharedPrefs.getInt("launch_mode", InputMode.KEYBOARD.id)
        mutableIntStateOf(savedMode.takeIf(enabledModes::contains) ?: enabledModes.first())
    }

    var devModeRefreshTrigger by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sharedPrefs) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hideUnknownDevices = sharedPrefs.getBoolean("hide_unknown", false)
                hideUnsupportedDevices = sharedPrefs.getBoolean("hide_unsupported", true)
                showMacAddress = sharedPrefs.getBoolean("show_mac", false)
                isHapticsEnabled = sharedPrefs.getBoolean(HAPTICS_PREFERENCE, true)
                isKeyVibrationEnabled = sharedPrefs.isKeyVibrationEnabled()
                keyVibrationStrength = sharedPrefs.keyVibrationStrength()
                keySensitivity = sharedPrefs.getFloat("key_sensitivity", 6f)
                lockSyncMode = sharedPrefs.getString("lock_sync_mode", "host") ?: "host"
                keyboardOrientation = KeyboardOrientation.fromPreference(
                    sharedPrefs.getString(KEYBOARD_ORIENTATION_PREFERENCE, null)
                )
                portraitKeyboardLayout = PortraitKeyboardLayout.fromPreference(
                    sharedPrefs.getString(PORTRAIT_KEYBOARD_LAYOUT_PREFERENCE, null)
                )
                isKeyPreviewEnabled = sharedPrefs.getBoolean(KEY_PREVIEW_PREFERENCE, true)
                keyboardHostOs = sharedPrefs.keyboardHostOs()
                keyboardStyle = KeyboardStyle.fromPreference(sharedPrefs.getString(KEYBOARD_STYLE_PREFERENCE, null))
                trackpadChoice = sharedPrefs.keyboardTrackpadChoice()
                isLaptopLayoutEnabled = sharedPrefs.getBoolean(KEYBOARD_LAPTOP_LAYOUT_PREFERENCE, false)
                laptopTrackpadPosition = LaptopTrackpadPosition.fromPreference(
                    sharedPrefs.getString(KEYBOARD_LAPTOP_TRACKPAD_PREFERENCE, null)
                )
                hardwareKeyboardMode = sharedPrefs.hardwareKeyboardMode()
                trackpadSide = TrackpadSide.fromPreference(sharedPrefs.getString(KEYBOARD_TRACKPAD_SIDE_PREFERENCE, null))
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
    val modifierLatch = remember {
        ModifierLatch(enabled = sharedPrefs.getBoolean(STICKY_MODIFIERS_PREFERENCE, true))
    }
    var latchedKeys by remember { mutableStateOf<Map<Int, LatchState>>(emptyMap()) }
    // The physical Alt/Sym key picks the phone keyboard's printed symbols. It latches
    // like the on-screen modifiers but is never sent to the host itself.
    val phoneLayerLatch = remember {
        ModifierLatch(enabled = sharedPrefs.getBoolean(STICKY_MODIFIERS_PREFERENCE, true))
    }
    var phoneLayerState by remember { mutableStateOf(LatchState.OFF) }
    // Physical keys currently down, keyed by Android key code, with what they sent.
    val hardwarePresses = remember { mutableMapOf<Int, HardwareKeyPress>() }
    // Physical keys that were down during a reset; their release is swallowed.
    val staleHardwareKeys = remember { mutableSetOf<Int>() }
    var touchLayer by remember { mutableStateOf(TouchLayer.BASE) }
    var layoutCycleSpaceActive by remember { mutableStateOf(false) }
    var pendingCharacterLayout by remember { mutableStateOf<KeyboardCharacterLayout?>(null) }
    var layoutSwitcherRevision by remember { mutableIntStateOf(0) }
    var showLayoutSwitcher by remember { mutableStateOf(false) }

    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var descriptorRefreshRequired by rememberSaveable { mutableStateOf(false) }
    var showGamepadGuide by rememberSaveable { mutableStateOf(false) }
    var pendingGamepadMode by rememberSaveable { mutableStateOf<Int?>(null) }

    fun switchInputMode(newMode: Int) {
        if (shouldShowGamepadGuide(newMode, sharedPrefs.getBoolean("has_seen_gamepad_guide", false))) {
            pendingGamepadMode = newMode
            showGamepadGuide = true
        } else {
            launchMode = newMode
            sharedPrefs.edit { putInt("launch_mode", newMode) }
        }
    }
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

    LaunchedEffect(layoutSwitcherRevision) {
        if (layoutSwitcherRevision > 0) {
            delay(1_400L)
            showLayoutSwitcher = false
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
        
        // A plain update shows "What's new" from MainActivity; this dialog is only for re-pairing.
        if (descriptorRefreshRequired) {
            showUpdateDialog = true
        }
        
        if (savedVersionCode != currentVersionCode) {
            sharedPrefs.edit { putInt("last_run_version_code", currentVersionCode) }
        }
    }
    
    if (showUpdateDialog) {
        val versionName = dev.arnv.bluke.BuildConfig.VERSION_NAME
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Bluetooth pairing refresh required") },
            text = {
                Text(
                    "Simply Bluke $versionName changes the input layout it advertises over Bluetooth, " +
                        "for example to add horizontal touchpad scrolling. Bluetooth hosts cache the old layout, " +
                        "so the touchpad and other controls may misbehave until you forget the host on this phone, " +
                        "remove Simply Bluke on the host, then pair again once. Reinstalling the app alone is not enough."
                )
            },
            confirmButton = {
                Button(onClick = { 
                    showUpdateDialog = false 
                    sharedPrefs.edit {
                        putInt(HID_DESCRIPTOR_REVISION_PREFERENCE, CURRENT_HID_DESCRIPTOR_REVISION)
                    }
                    descriptorRefreshRequired = false
                }) {
                    Text("Got it")
                }
            },
        )
    }

    // Connection helper declared at outer scope
    val isConnected = btState is BluetoothState.Connected
    val offerSettingsPairingHelp = shouldOfferSettingsPairingHelp(btState, homeUiState.hidLifecycleState)

    LaunchedEffect(offerSettingsPairingHelp) {
        if (offerSettingsPairingHelp) showTroubleshootingNudge = true
    }

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
            showTroubleshootingNudge = false
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
                pendingGamepadMode = null
                sharedPrefs.edit { putBoolean("mock_gamepad_guide", false) }
            },
            icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
            title = { Text("Before using the gamepad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose Native for Windows/Linux games, Android for games on Android, or Web when a browser game ignores directions. Tap the controller mode button to switch; hold it to open settings.")
                    Text("On Windows, some newer games accept only Xbox XInput controllers. Simply Bluke is a standard Bluetooth HID gamepad, so Steam Input or another compatibility layer may be needed.")
                    Text("If the host cached an older controller layout, forget Simply Bluke on both devices and pair again once.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sharedPrefs.edit { putBoolean("has_seen_gamepad_guide", true) }
                        sharedPrefs.edit { putBoolean("mock_gamepad_guide", false) }
                        showGamepadGuide = false
                        pendingGamepadMode?.let {
                            launchMode = it
                            sharedPrefs.edit { putInt("launch_mode", it) }
                        }
                        pendingGamepadMode = null
                        isKeyboardActive = true
                    },
                ) { Text("Open gamepad") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGamepadGuide = false
                    pendingGamepadMode = null
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

    // Input modes sit on a black backdrop that also fills the system bar areas,
    // so the bar icons are light there; elsewhere they follow the app theme.
    val appUsesLightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // Keyed on the theme too, so this runs after the theme sets its own bar appearance.
    LaunchedEffect(isKeyboardActive, appUsesLightBars) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val lightBars = appUsesLightBars && !isKeyboardActive
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    // Set the screen orientation for the current mode.
    LaunchedEffect(isKeyboardActive, keyboardOrientation) {
        val activity = context as? Activity ?: return@LaunchedEffect
        if (isKeyboardActive) {
            try {
                // Every input mode follows the rotation setting and has a portrait layout.
                @Suppress("SourceLockedOrientationActivity")
                activity.requestedOrientation = keyboardOrientation.activityOrientation
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error setting orientation: ${e.message}")
            }
        } else {
            try {
                @Suppress("SourceLockedOrientationActivity")
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error setting orientation to portrait: ${e.message}")
            }
        }
    }

    fun releaseModifierOnHost(keyCode: Int) {
        if (keyCode == KeyboardLayouts.KEY_FN) {
            isFnActive = false
            if (activeConsumerKey != null) {
                btManager.sendConsumerControl(null)
                activeConsumerKey = null
            }
        } else {
            btManager.sendKey(keyCode, false)
        }
    }

    fun releaseOneShotModifiers() {
        modifierLatch.onOtherKeyUp().forEach(::releaseModifierOnHost)
        latchedKeys = modifierLatch.latched
    }

    /** Lets go of every key, latch and layer, on screen and on the host. */
    fun resetKeyboardInput() {
        modifierLatch.clear()
        latchedKeys = emptyMap()
        phoneLayerLatch.clear()
        phoneLayerState = LatchState.OFF
        staleHardwareKeys += hardwarePresses.keys
        hardwarePresses.clear()
        activePressedKeys.clear()
        fnConsumedKeys.clear()
        isFnActive = false
        activeConsumerKey = null
        layoutCycleSpaceActive = false
        touchLayer = TouchLayer.BASE
        btManager.releaseAllInputs()
    }

    // Rotating, leaving the keyboard or leaving the app lets go of every key,
    // so nothing stays stuck down on the host.
    var lastKeyboardShape by remember { mutableStateOf(Triple(isKeyboardActive, isPortrait, isHardwareKeyboardMode)) }
    LaunchedEffect(isKeyboardActive, isPortrait, isHardwareKeyboardMode) {
        val shape = Triple(isKeyboardActive, isPortrait, isHardwareKeyboardMode)
        if (shape != lastKeyboardShape) {
            lastKeyboardShape = shape
            resetKeyboardInput()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> resetKeyboardInput()
                Lifecycle.Event.ON_RESUME -> {
                    modifierLatch.enabled = sharedPrefs.getBoolean(STICKY_MODIFIERS_PREFERENCE, true)
                    phoneLayerLatch.enabled = modifierLatch.enabled
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Process local screen-press inputs
    /** [fromHardware] presses come from a physical keyboard: no vibration or layout cycling. */
    fun handleLocalKeyPress(keyCode: Int, isPress: Boolean, fromHardware: Boolean = false) {
        // A physical keyboard has its own feel, so its presses don't vibrate.
        fun vibrateForKey() {
            if (!fromHardware) vibrateKeyPress()
        }
        fun applyPendingCharacterLayoutIfReleased() {
            val shiftStillPressed = activePressedKeys.any {
                it == KeyboardLayouts.MOD_LSHIFT || it == KeyboardLayouts.MOD_RSHIFT
            }
            if (!layoutCycleSpaceActive && !shiftStillPressed) {
                pendingCharacterLayout?.let { nextLayout ->
                    characterLayout = nextLayout
                    sharedPrefs.edit {
                        putString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, nextLayout.preferenceValue)
                    }
                    pendingCharacterLayout = null
                }
            }
        }

        // Layer keys only change the on-screen layout; nothing is sent to the host.
        if (keyCode in TouchKeyboardLayouts.LAYER_KEYS) {
            if (isPress && !activePressedKeys.contains(keyCode)) {
                activePressedKeys.add(keyCode)
                vibrateForKey()
                touchLayer = touchLayer.afterLayerKey(keyCode)
            } else if (!isPress) {
                activePressedKeys.remove(keyCode)
            }
            return
        }

        val isModifier = keyCode in KeyboardLayouts.MOD_LCTRL..KeyboardLayouts.MOD_RWIN ||
            keyCode == KeyboardLayouts.KEY_FN
        if (isPress && !isModifier) modifierLatch.onOtherKeyDown()

        // Media keys on the Touch layout's Fn layer.
        TouchKeyboardLayouts.consumerControlFor(keyCode)?.let { control ->
            if (isPress && !activePressedKeys.contains(keyCode)) {
                activePressedKeys.add(keyCode)
                vibrateForKey()
                activeConsumerKey = keyCode
                btManager.sendConsumerControl(control)
            } else if (!isPress && activePressedKeys.remove(keyCode)) {
                if (activeConsumerKey == keyCode) {
                    btManager.sendConsumerControl(null)
                    activeConsumerKey = null
                }
                releaseOneShotModifiers()
            }
            return
        }

        if (keyCode == KeyboardLayouts.KEY_SPACE && !fromHardware) {
            val isShiftPressed = activePressedKeys.any {
                it == KeyboardLayouts.MOD_LSHIFT || it == KeyboardLayouts.MOD_RSHIFT
            }
            if (isPress && isShiftPressed && !layoutCycleSpaceActive) {
                layoutCycleSpaceActive = true
                activePressedKeys.add(keyCode)
                vibrateForKey()
                pendingCharacterLayout = (pendingCharacterLayout ?: characterLayout).next()
                showLayoutSwitcher = true
                layoutSwitcherRevision++
                return
            }
            if (!isPress && layoutCycleSpaceActive) {
                layoutCycleSpaceActive = false
                activePressedKeys.remove(keyCode)
                applyPendingCharacterLayoutIfReleased()
                return
            }
        }

        if (isModifier) {
            if (isPress && !activePressedKeys.contains(keyCode)) {
                activePressedKeys.add(keyCode)
                vibrateForKey()
                if (modifierLatch.onModifierDown(keyCode)) {
                    if (keyCode == KeyboardLayouts.KEY_FN) isFnActive = true
                    else btManager.sendKey(keyCode, true)
                }
            } else if (!isPress && activePressedKeys.remove(keyCode)) {
                if (modifierLatch.onModifierUp(keyCode, android.os.SystemClock.uptimeMillis())) {
                    releaseModifierOnHost(keyCode)
                }
                applyPendingCharacterLayoutIfReleased()
            }
            latchedKeys = modifierLatch.latched
            return
        }

        val fnControl = if (isPress && isFnActive) consumerControlForFnKey(keyCode) else null
        if (fnControl != null) {
            if (fnConsumedKeys.add(keyCode)) {
                activePressedKeys.add(keyCode)
                vibrateForKey()
                activeConsumerKey = keyCode
                btManager.sendConsumerControl(fnControl)
            }
            return
        }

        if (!isPress && fnConsumedKeys.remove(keyCode)) {
            activePressedKeys.remove(keyCode)
            if (activeConsumerKey == keyCode) {
                btManager.sendConsumerControl(null)
                activeConsumerKey = null
            }
            releaseOneShotModifiers()
            return
        }

        if (isPress) {
            if (!activePressedKeys.contains(keyCode)) {
                activePressedKeys.add(keyCode)
                vibrateForKey()
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
            btManager.sendKey(keyCode, false)
            releaseOneShotModifiers()
            applyPendingCharacterLayoutIfReleased()
        }
    }




    fun activeHostModifiers(): Set<Int> =
        (KeyboardLayouts.MOD_LCTRL..KeyboardLayouts.MOD_RWIN).filterTo(mutableSetOf(), modifierLatch::isActive)

    fun updatePhoneLayerState() {
        val layerKey = HardwareKeyMap.PHONE_LAYER_KEY
        phoneLayerState = when {
            phoneLayerLatch.state(layerKey) == LatchState.LOCKED -> LatchState.LOCKED
            phoneLayerLatch.isActive(layerKey) -> LatchState.ONE_SHOT
            else -> LatchState.OFF
        }
    }

    fun pressHardwareKey(press: HardwareKeyPress) {
        if (press.throughKeyboard) {
            press.addedModifiers.forEach { btManager.sendKey(it, true) }
            handleLocalKeyPress(press.usage, true, fromHardware = true)
        } else {
            modifierLatch.onOtherKeyDown()
            press.suppressedModifiers.forEach { btManager.sendKey(it, false) }
            press.addedModifiers.forEach { btManager.sendKey(it, true) }
            btManager.sendKey(press.usage, true)
        }
        if (!press.isModifier) phoneLayerLatch.onOtherKeyDown()
    }

    fun releaseHardwareKey(press: HardwareKeyPress) {
        if (press.throughKeyboard) {
            handleLocalKeyPress(press.usage, false, fromHardware = true)
            press.addedModifiers.forEach { btManager.sendKey(it, false) }
        } else {
            btManager.sendKey(press.usage, false)
            press.addedModifiers.forEach { btManager.sendKey(it, false) }
            releaseOneShotModifiers()
            // Put back a Shift that is still held or latched.
            press.suppressedModifiers.filter(modifierLatch::isActive).forEach { btManager.sendKey(it, true) }
        }
        if (!press.isModifier) {
            phoneLayerLatch.onOtherKeyUp()
            updatePhoneLayerState()
        }
    }

    /** Sends a physical key press to the host. Returns false to leave the key to Android. */
    fun handleHardwareKey(event: KeyEvent): Boolean {
        // Injected keys may carry no source; gamepads, remotes and mice are left alone.
        if (event.source != InputDevice.SOURCE_UNKNOWN && !event.isFromSource(InputDevice.SOURCE_KEYBOARD)) {
            return false
        }
        val keyCode = event.keyCode
        val isDown = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }

        if (keyCode in staleHardwareKeys) {
            if (!isDown) staleHardwareKeys.remove(keyCode)
            return true
        }

        if (HardwareKeyMap.isPhoneLayerKey(keyCode)) {
            val layerKey = HardwareKeyMap.PHONE_LAYER_KEY
            if (isDown) {
                if (event.repeatCount == 0) phoneLayerLatch.onModifierDown(layerKey)
            } else {
                phoneLayerLatch.onModifierUp(layerKey, event.eventTime)
            }
            updatePhoneLayerState()
            return true
        }

        if (!isDown) {
            val press = hardwarePresses.remove(keyCode) ?: return false
            releaseHardwareKey(press)
            return true
        }
        // The host repeats held keys itself.
        if (event.repeatCount > 0 || keyCode in hardwarePresses) return keyCode in hardwarePresses

        val hostModifiers = activeHostModifiers()
        val shiftMeta = if (HOST_SHIFT_MODIFIERS.any { it in hostModifiers }) {
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        } else {
            0
        }
        val layerMeta = if (keyCode == KeyEvent.KEYCODE_SYM || event.isSymPressed) {
            KeyEvent.META_SYM_ON
        } else {
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        val charMap = event.keyCharacterMap
        val press = HardwareKeyMap.plan(
            androidKeyCode = keyCode,
            phoneLayerActive = phoneLayerLatch.isActive(HardwareKeyMap.PHONE_LAYER_KEY),
            layerChar = charMap.get(keyCode, layerMeta or shiftMeta),
            baseChar = charMap.get(keyCode, shiftMeta),
            typedChar = event.unicodeChar,
            activeHostModifiers = hostModifiers,
        ) ?: return false
        hardwarePresses[keyCode] = press
        pressHardwareKey(press)
        return true
    }

    val hardwareKeyHost = remember(context) { context.findHardwareKeyHost() }
    val takesHardwareKeys = isKeyboardActive && launchMode == InputMode.KEYBOARD.id && isHardwareKeyboardMode
    val currentHardwareKeyHandler by rememberUpdatedState(::handleHardwareKey)
    DisposableEffect(hardwareKeyHost, takesHardwareKeys) {
        if (takesHardwareKeys) hardwareKeyHost?.setHardwareKeyHandler { currentHardwareKeyHandler(it) }
        onDispose { hardwareKeyHost?.setHardwareKeyHandler(null) }
    }

    AnimatedContent(
        targetState = isKeyboardActive,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "screen_navigation"
    ) { keyboardActive ->
        if (keyboardActive) Box(
            // Keep every control clear of the status bar, navigation bar and display cutout.
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
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
                                        switchInputMode(newMode)
                                    },
                                    sharedPrefs = sharedPrefs,
                                    caseBrush = caseBrush,
                                )
                            }
                            // Gamepad and multimedia use the full dark palette of the app
                            // (wallpaper colors or accent), not just its primary color.
                            2 -> MyApplicationTheme(forceDark = true) {
                                GamepadView(
                                    btManager = btManager,
                                    onClose = { isKeyboardActive = false },
                                    launchMode = launchMode,
                                    onModeChange = { newMode -> 
                                        switchInputMode(newMode)
                                    },
                                    sharedPrefs = sharedPrefs,
                                    caseBrush = caseBrush
                                )
                            }
                            4 -> MyApplicationTheme(forceDark = true) {
                                MediaPresentationView(
                                    btManager = btManager,
                                    onClose = { isKeyboardActive = false },
                                    launchMode = launchMode,
                                    onModeChange = { newMode ->
                                        switchInputMode(newMode)
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
                            .padding(bottom = if (keyboardStyle == KeyboardStyle.MINIMAL) 0.dp else 6.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Hardware keyboard mode shows only a strip of keys the phone's keyboard lacks.
                        val useHardwareStrip = isHardwareKeyboardMode
                        val showTrackpad = trackpadChoice ?: useHardwareStrip
                        // Wide screens (tablets, unfolded foldables) can keep the full layout
                        // with the trackpad beside or below it.
                        val useLaptopLayout = isLaptopLayoutEnabled && isWideScreen && !useHardwareStrip
                        // A full-size layout is too cramped next to a trackpad in landscape,
                        // so the Touch layout is used there too.
                        val useTouchLayout = !useHardwareStrip && !useLaptopLayout && (
                            (isPortrait && portraitKeyboardLayout == PortraitKeyboardLayout.TOUCH) ||
                                (showTrackpad && !isPortrait)
                            )

                        // 1. Top toolbar. Touch-sized buttons; it scrolls sideways when it doesn't fit.
                        SessionToolbar(
                            leading = {
                                ToolbarButton(
                                    onClick = { isKeyboardActive = false },
                                    onClickLabel = "Close keyboard",
                                    modifier = Modifier.testTag("exit_keyboard_btn"),
                                ) {
                                    ToolbarIcon(Icons.Default.Close, "Exit")
                                    ToolbarLabel("Close")
                                }

                                // Rotating mode switcher
                                ToolbarButton(
                                    onClickLabel = "Next input mode",
                                    onLongClickLabel = "Configure input mode cycle",
                                    onClick = {
                                        val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
                                        val currentIndexInEnabled = enabledModes.indexOf(launchMode)
                                        val nextIndex = (currentIndexInEnabled + 1) % enabledModes.size
                                        val nextMode = enabledModes[nextIndex]
                                        switchInputMode(nextMode)
                                        if (isHapticsEnabled) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                        }
                                    },
                                    onLongClick = {
                                        context.startActivity(Intent(context, QuickCycleActivity::class.java))
                                    },
                                    modifier = Modifier.testTag("keyboard_mode_cycle_btn"),
                                ) {
                                    ToolbarIcon(Icons.Default.Keyboard, "Switch Mode")
                                    ToolbarLabel("Keyboard")
                                }

                                // Use collected state (not .value) so UI reacts to changes from background
                                val activeDevice = homeUiState.connectedDevice ?: lastConnectedDevice ?: btManager.getReconnectTarget()
                                ToolbarHostChip(isConnected = isConnected, hostName = activeDevice?.name ?: "No Host")

                                ReconnectHostButton(btManager)

                                // Show or hide the trackpad (one setting for both keyboard modes).
                                ToolbarButton(
                                    onClickLabel = if (showTrackpad) "Hide trackpad" else "Show trackpad",
                                    onClick = {
                                        trackpadChoice = !showTrackpad
                                        sharedPrefs.setKeyboardTrackpadEnabled(!showTrackpad)
                                        vibrateKeyPress()
                                    },
                                    modifier = Modifier.testTag("trackpad_toggle_btn"),
                                ) {
                                    ToolbarLed(on = showTrackpad)
                                    ToolbarIcon(Icons.Default.Mouse, null)
                                    ToolbarLabel("Trackpad", color = if (showTrackpad) Color.White else SessionToolbarDefaults.MutedContent)
                                }

                                // Move the trackpad: above/below in the laptop layout,
                                // otherwise to the other side in landscape.
                                if (showTrackpad && !isHardwareKeyboardMode && useLaptopLayout) {
                                    val next = laptopTrackpadPosition.toggled()
                                    ToolbarIconButton(
                                        onClickLabel = if (next == LaptopTrackpadPosition.BELOW) {
                                            "Move trackpad below the keyboard"
                                        } else {
                                            "Move trackpad above the keyboard"
                                        },
                                        onClick = {
                                            laptopTrackpadPosition = next
                                            sharedPrefs.edit { putString(KEYBOARD_LAPTOP_TRACKPAD_PREFERENCE, next.preferenceValue) }
                                        },
                                        modifier = Modifier.testTag("swap_trackpad_side_btn"),
                                    ) {
                                        ToolbarIcon(Icons.Default.SwapVert, "Move trackpad")
                                    }
                                } else if (showTrackpad && !isPortrait && !isHardwareKeyboardMode) {
                                    ToolbarIconButton(
                                        onClickLabel = "Move trackpad to the other side",
                                        onClick = {
                                            trackpadSide = trackpadSide.flipped()
                                            sharedPrefs.edit {
                                                putString(KEYBOARD_TRACKPAD_SIDE_PREFERENCE, trackpadSide.preferenceValue)
                                            }
                                        },
                                        modifier = Modifier.testTag("swap_trackpad_side_btn"),
                                    ) {
                                        ToolbarIcon(Icons.Default.SwapHoriz, "Move trackpad to the other side")
                                    }
                                }
                            },
                            settingsActivity = dev.arnv.bluke.KeyboardSettingsActivity::class.java,
                            trailing = {
                                // 1. Lock keys: each is its own touch target with an LED.
                                @Composable
                                fun LockKeyButton(label: String, active: Boolean, onToggle: () -> Unit) {
                                    ToolbarButton(
                                        onClick = onToggle,
                                        onClickLabel = "Toggle $label lock",
                                        horizontalPadding = 10.dp,
                                    ) {
                                        ToolbarLed(on = active)
                                        ToolbarLabel(label, color = if (active) Color.White else SessionToolbarDefaults.MutedContent)
                                    }
                                }
                                fun toggleLock(usage: Int, flip: () -> Unit) {
                                    if (isConnected) {
                                        btManager.sendKey(usage, true)
                                        btManager.sendKey(usage, false)
                                        if (lockSyncMode == "device") flip()
                                    } else {
                                        flip()
                                    }
                                }
                                LockKeyButton("CAPS", isCapsLockActive) { toggleLock(0x39) { isCapsLockActive = !isCapsLockActive } }
                                LockKeyButton("NUM", isNumLockActive) { toggleLock(0x53) { isNumLockActive = !isNumLockActive } }
                                LockKeyButton("SCR", isScrollLockActive) { toggleLock(0x47) { isScrollLockActive = !isScrollLockActive } }

                                // Phone keyboard Alt layer (hardware keyboard mode only)
                                if (isHardwareKeyboardMode) {
                                    Row(
                                        modifier = Modifier
                                            .height(SessionToolbarDefaults.ButtonHeight)
                                            .padding(horizontal = 6.dp)
                                            .testTag("phone_alt_indicator"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        ToolbarLed(
                                            on = phoneLayerState != LatchState.OFF,
                                            onColor = if (phoneLayerState == LatchState.LOCKED) {
                                                SessionToolbarDefaults.LedOn
                                            } else {
                                                SessionToolbarDefaults.LedOn.copy(alpha = 0.5f)
                                            },
                                        )
                                        ToolbarLabel(
                                            "ALT",
                                            color = if (phoneLayerState != LatchState.OFF) Color.White else SessionToolbarDefaults.MutedContent,
                                        )
                                    }
                                }

                                // 2. Layout selector (the hardware keyboard strip has a fixed layout)
                                if (!isHardwareKeyboardMode) {
                                    // The Touch layout replaces the chosen layout, so cycling would do nothing.
                                    val layoutInert = useTouchLayout
                                    ToolbarButton(
                                        onClickLabel = "Next keyboard layout",
                                        onLongClickLabel = "Configure keyboard layout cycle",
                                        dimmed = layoutInert,
                                        onClick = {
                                            if (layoutInert) {
                                                val reason = if (isPortrait) {
                                                    "Layouts don't apply to the portrait Touch keyboard. Rotate the phone to use them."
                                                } else {
                                                    "Layouts don't apply next to the trackpad. Turn the trackpad off to use them."
                                                }
                                                android.widget.Toast.makeText(context, reason, android.widget.Toast.LENGTH_LONG).show()
                                                return@ToolbarButton
                                            }
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
                                            vibrateKeyPress()
                                        },
                                        onLongClick = {
                                            context.startActivity(Intent(context, KeyboardLayoutsActivity::class.java))
                                        },
                                    ) {
                                        val layoutContent = if (layoutInert) SessionToolbarDefaults.MutedContent else Color.White
                                        ToolbarIcon(Icons.Default.Keyboard, "Layout", tint = layoutContent)
                                        ToolbarLabel(selectedGeometry.displayName, color = layoutContent, maxWidth = 120.dp)
                                    }
                                }

                                // 3. PC / Mac modifier keys
                                ToolbarButton(
                                    onClickLabel = "Switch to ${keyboardHostOs.toggled().displayName} keys",
                                    onLongClickLabel = "Keyboard settings",
                                    onClick = {
                                        // Held or latched modifiers change meaning, so let go of them first.
                                        resetKeyboardInput()
                                        keyboardHostOs = keyboardHostOs.toggled()
                                        sharedPrefs.edit { putString(KEYBOARD_HOST_OS_PREFERENCE, keyboardHostOs.preferenceValue) }
                                        vibrateKeyPress()
                                    },
                                    onLongClick = {
                                        context.startActivity(Intent(context, dev.arnv.bluke.KeyboardSettingsActivity::class.java))
                                    },
                                    modifier = Modifier.testTag("host_os_toggle"),
                                ) {
                                    ToolbarIcon(
                                        if (keyboardHostOs == KeyboardHostOs.MAC) Icons.Default.LaptopMac else Icons.Default.LaptopWindows,
                                        null,
                                    )
                                    ToolbarLabel(keyboardHostOs.shortName)
                                }

                                // 4. Keyboard theme selector
                                ToolbarButton(
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
                                        vibrateKeyPress()
                                    },
                                    onLongClick = {
                                        context.startActivity(
                                            Intent(context, KeyboardThemesActivity::class.java),
                                        )
                                    },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(selectedKeyboardTheme.accentStyle.backgroundArgb))
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    ToolbarLabel(selectedKeyboardTheme.name, maxWidth = 110.dp)
                                }

                                // 5. Key vibration toggle
                                if (keyPressVibrator.isAvailable) {
                                    ToolbarIconButton(
                                        onClickLabel = if (isKeyVibrationEnabled) "Turn off key vibration" else "Turn on key vibration",
                                        onClick = {
                                            isKeyVibrationEnabled = !isKeyVibrationEnabled
                                            sharedPrefs.edit { putBoolean(KEY_VIBRATION_PREFERENCE, isKeyVibrationEnabled) }
                                            vibrateKeyPress()
                                        },
                                        modifier = Modifier.testTag("key_vibration_toggle"),
                                    ) {
                                        if (isKeyVibrationEnabled) {
                                            ToolbarIcon(Icons.Default.Vibration, "Key vibration on")
                                        } else {
                                            ToolbarIcon(painterResource(R.drawable.ic_vibration_off), "Key vibration off")
                                        }
                                    }
                                }
                            },
                        )

                        // 2. Keyboard, optionally with a trackpad above it (portrait) or beside it (landscape)
                        val isMinimal = keyboardStyle == KeyboardStyle.MINIMAL
                        val keyboardRows = remember(useHardwareStrip, useTouchLayout, touchLayer, characterLayout, selectedGeometry, keyboardHostOs) {
                            val rows = when {
                                useHardwareStrip -> TouchKeyboardLayouts.hardwareStripLayout(touchLayer)
                                useTouchLayout -> TouchKeyboardLayouts.getLayout(touchLayer, characterLayout)
                                else -> KeyboardLayouts.getLayout(selectedGeometry, characterLayout)
                            }
                            rows.withHostOs(keyboardHostOs)
                        }
                        // Keys are a bit shorter with the trackpad above, leaving it more room.
                        // The strip's keys are short and wide in landscape so it stays a strip.
                        val keyAspect = when {
                            useHardwareStrip -> if (isPortrait) 1.1f else 0.55f
                            showTrackpad && isPortrait -> 1.25f
                            else -> 1.45f
                        }
                        val fillKeyboardHeight = useTouchLayout || useHardwareStrip

                        @Composable
                        fun KeyboardPanel(modifier: Modifier, fixedHeight: Boolean) {
                            Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                                    rowsOverride = keyboardRows,
                                    // A height set by hand is filled, whatever the key shape.
                                    fillHeight = fillKeyboardHeight || fixedHeight,
                                    maxKeyAspect = if (fixedHeight) Float.MAX_VALUE else keyAspect,
                                    latchedKeys = latchedKeys,
                                    legendScale = when {
                                        useTouchLayout -> 1.5f
                                        useHardwareStrip -> 1.2f
                                        else -> 1f
                                    },
                                    showKeyPreview = useTouchLayout && isKeyPreviewEnabled,
                                    style = keyboardStyle,
                                    onKeyPressChange = { code, press -> handleLocalKeyPress(code, press) }
                                )
                                KeyboardLayoutSwitcherOverlay(
                                    visible = showLayoutSwitcher,
                                    selectedLayout = pendingCharacterLayout ?: characterLayout,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }

                        KeyboardTrackpadSplit(
                            showTrackpad = showTrackpad,
                            // The strip always sits below the trackpad, even in landscape.
                            isPortrait = isPortrait || useHardwareStrip,
                            trackpadSide = trackpadSide,
                            style = keyboardStyle,
                            keyboardRows = keyboardRows,
                            keyAspect = if (fillKeyboardHeight) keyAspect else 1f,
                            laptopLayout = useLaptopLayout,
                            laptopTrackpadBelow = laptopTrackpadPosition == LaptopTrackpadPosition.BELOW,
                            heightFraction = keyboardHeightFraction,
                            onHeightFractionChange = { fraction ->
                                keyboardHeightFraction = fraction
                                sharedPrefs.setKeyboardHeightFraction(isPortrait, fraction)
                                vibrateKeyPress()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (isMinimal) 0.dp else 6.dp,
                                    vertical = if (isMinimal) 0.dp else 2.dp,
                                ),
                            trackpad = { trackpadModifier ->
                                KeyboardTrackpadPanel(
                                    btManager = btManager,
                                    sharedPrefs = sharedPrefs,
                                    style = keyboardStyle,
                                    modifier = trackpadModifier,
                                )
                            },
                            keyboard = { keyboardModifier, fixedHeight -> KeyboardPanel(keyboardModifier, fixedHeight) },
                        )
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
                            onRetry = btManager::retryBluetoothCapabilities
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
                                        offerSettingsPairingHelp = offerSettingsPairingHelp,
                                        onScan = { btManager.startScanning() },
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
                                connectionTargetAddress = homeUiState.connectionTargetAddress,
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
                                    4 -> Icons.Filled.SmartDisplay
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
                                    if (shouldShowGamepadGuide(launchMode, sharedPrefs.getBoolean("has_seen_gamepad_guide", false))) {
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
    offerSettingsPairingHelp: Boolean,
    onScan: () -> Unit,
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
                    if (offerSettingsPairingHelp) {
                        "If you paired through Bluetooth settings, forget the pairing on both devices, then use Scan in Simply Bluke to pair and connect again."
                    } else {
                        "Check both pairing prompts or follow the safe repair steps. A slow attempt does not automatically mean this phone is incompatible."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (offerSettingsPairingHelp) {
                        TextButton(onClick = onScan) { Text("Scan") }
                    }
                    TextButton(onClick = onOpenHelp) { Text("Help") }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss")
            }
        }
    }
}

private tailrec fun Context.findHardwareKeyHost(): HardwareKeyHost? = when (this) {
    is HardwareKeyHost -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findHardwareKeyHost()
    else -> null
}
