package dev.arnv.bluke.ui

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import dev.arnv.bluke.R
import dev.arnv.bluke.ControllerSettingsActivity
import dev.arnv.bluke.QuickCycleActivity
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.GAMEPAD_DPAD_MODE_PREFERENCE
import dev.arnv.bluke.bluetooth.GAMEPAD_GUIDE_BUTTON_INDEX
import dev.arnv.bluke.bluetooth.GAMEPAD_SHARE_BUTTON_INDEX
import dev.arnv.bluke.bluetooth.GAMEPAD_TOUCHPAD_BUTTON_INDEX
import dev.arnv.bluke.bluetooth.GamepadDpadOutputMode
import dev.arnv.bluke.bluetooth.GamepadModeSwitchState
import dev.arnv.bluke.data.LayoutRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

// ── Console configuration ──

private data class ButtonDef(
    val label: String,
    val mappingId: Int,
    val color: Color = Color(0xFF3A3D42)
)

private data class ConsoleConfig(
    val id: String,
    val name: String,
    val faceTop: ButtonDef,
    val faceRight: ButtonDef,
    val faceBottom: ButtonDef,
    val faceLeft: ButtonDef,
    val leftBumper: ButtonDef,
    val rightBumper: ButtonDef,
    val leftTrigger: ButtonDef,
    val rightTrigger: ButtonDef,
    val selectButton: ButtonDef,
    val startButton: ButtonDef,
    val guideButton: ButtonDef,
    val shareButton: ButtonDef = ButtonDef("SHARE", GAMEPAD_SHARE_BUTTON_INDEX),
    val leftStickAboveDpad: Boolean = true,
    val hasTouchpad: Boolean = false,
    val touchpadMappingId: Int = -1
)

private const val GAMEPAD_REPORT_INTERVAL_MILLIS = 8L

private val CONSOLES = listOf(
    ConsoleConfig(
        id = "xbox_series",
        name = "Xbox Series",
        faceTop = ButtonDef("Y", 3, Color(0xFFFFCA28)),
        faceRight = ButtonDef("B", 1, Color(0xFFEF5350)),
        faceBottom = ButtonDef("A", 0, Color(0xFF66BB6A)),
        faceLeft = ButtonDef("X", 2, Color(0xFF42A5F5)),
        leftBumper = ButtonDef("LB", 4),
        rightBumper = ButtonDef("RB", 5),
        leftTrigger = ButtonDef("LT", 6),
        rightTrigger = ButtonDef("RT", 7),
        selectButton = ButtonDef("VIEW", 8),
        startButton = ButtonDef("MENU", 9),
        guideButton = ButtonDef("XBOX", GAMEPAD_GUIDE_BUTTON_INDEX, Color(0xFF2E7D32)),
        shareButton = ButtonDef("SHARE", GAMEPAD_SHARE_BUTTON_INDEX),
        leftStickAboveDpad = true
    ),
    ConsoleConfig(
        id = "playstation_5",
        name = "PlayStation 5",
        faceTop = ButtonDef("△", 3, Color(0xFF4DB6AC)),
        faceRight = ButtonDef("◯", 1, Color(0xFFEF5350)),
        faceBottom = ButtonDef("✕", 0, Color(0xFF5C6BC0)),
        faceLeft = ButtonDef("☐", 2, Color(0xFFEC407A)),
        leftBumper = ButtonDef("L1", 4),
        rightBumper = ButtonDef("R1", 5),
        leftTrigger = ButtonDef("L2", 6),
        rightTrigger = ButtonDef("R2", 7),
        selectButton = ButtonDef("CREATE", 8),
        startButton = ButtonDef("OPTIONS", 9),
        guideButton = ButtonDef("PS", GAMEPAD_GUIDE_BUTTON_INDEX, Color(0xFF1565C0)),
        shareButton = ButtonDef("SHARE", GAMEPAD_SHARE_BUTTON_INDEX),
        leftStickAboveDpad = false,
        hasTouchpad = true,
        touchpadMappingId = GAMEPAD_TOUCHPAD_BUTTON_INDEX
    )
)

// ── Main View ──

@SuppressLint("MissingPermission")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamepadView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    @Suppress("UNUSED_PARAMETER") caseBrush: Brush
) {
    val gp = rememberControlSurfaceColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layoutRepository = remember(context) { LayoutRepository(context) }
    val pendingLayoutValues = remember { mutableMapOf<String, Float>() }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val config = CONSOLES[selectedIndex]
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // Portrait keeps its own edited positions. The prefix must not start with a console id,
    // or clearing the landscape layout would clear the portrait one too.
    val layoutKey = if (isPortrait) "portrait_${config.id}" else config.id
    val defaultDpadX = when { isPortrait -> 0f; config.id == "playstation_5" -> -24f; else -> 32f }
    val defaultDpadY = when { isPortrait -> 0f; config.id == "playstation_5" -> 0f; else -> 20f }
    val defaultLeftX = when { isPortrait -> 0f; config.id == "playstation_5" -> 45f; else -> 0f }
    val defaultLeftY = when { isPortrait -> 0f; config.id == "playstation_5" -> 20f; else -> 0f }
    val defaultRightX = if (isPortrait) 0f else -45f
    val defaultRightY = if (isPortrait) 0f else 20f

    var isEditMode by rememberSaveable { mutableStateOf(false) }

    var isVibrationEnabled by remember(sharedPrefs) {
        mutableStateOf(sharedPrefs.getBoolean("gamepad_vibration_enabled", true))
    }
    var dpadOutputMode by remember(sharedPrefs) {
        mutableStateOf(
            GamepadDpadOutputMode.fromPreference(
                sharedPrefs.getString(GAMEPAD_DPAD_MODE_PREFERENCE, null)
            )
        )
    }

    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == GAMEPAD_DPAD_MODE_PREFERENCE) {
                dpadOutputMode = GamepadDpadOutputMode.fromPreference(
                    preferences.getString(GAMEPAD_DPAD_MODE_PREFERENCE, null)
                )
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val triggerVibration = { milliseconds: Long ->
        if (isVibrationEnabled) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    val saveLayoutPref = { key: String, value: Float ->
        pendingLayoutValues[key] = value
    }
    val commitLayoutPrefs = {
        val values = pendingLayoutValues.toMap()
        pendingLayoutValues.clear()
        scope.launch { layoutRepository.save(values) }
        Unit
    }

    // Positions & Scales loaded dynamically per console layout (Xbox Series / PlayStation 5)
    var dpadOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_dpad_x", defaultDpadX)) }
    var dpadOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_dpad_y", defaultDpadY)) }
    var dpadScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_dpad_scale", 1f)) }

    var leftStickOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_stick_x", defaultLeftX)) }
    var leftStickOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_stick_y", defaultLeftY)) }
    var leftStickScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_stick_scale", 1f)) }

    var rightStickOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_stick_x", defaultRightX)) }
    var rightStickOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_stick_y", defaultRightY)) }
    var rightStickScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_stick_scale", 1f)) }

    var faceButtonsOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_face_buttons_x", 0f)) }
    var faceButtonsOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_face_buttons_y", 0f)) }
    var faceButtonsScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_face_buttons_scale", 1f)) }

    var leftTriggerOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_trigger_x", 0f)) }
    var leftTriggerOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_trigger_y", 0f)) }
    var leftTriggerScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_trigger_scale", 1f)) }

    var leftBumperOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_bumper_x", 0f)) }
    var leftBumperOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_bumper_y", 0f)) }
    var leftBumperScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_left_bumper_scale", 1f)) }

    var rightTriggerOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_trigger_x", 0f)) }
    var rightTriggerOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_trigger_y", 0f)) }
    var rightTriggerScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_trigger_scale", 1f)) }

    var rightBumperOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_bumper_x", 0f)) }
    var rightBumperOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_bumper_y", 0f)) }
    var rightBumperScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_right_bumper_scale", 1f)) }

    var guideOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_guide_x", 0f)) }
    var guideOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_guide_y", 0f)) }
    var guideScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_guide_scale", 1f)) }

    var selectOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_select_x", 0f)) }
    var selectOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_select_y", 0f)) }
    var selectScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_select_scale", 1f)) }

    var shareOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_share_x", 0f)) }
    var shareOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_share_y", 0f)) }
    var shareScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_share_scale", 1f)) }

    var startOffsetX by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_start_x", 0f)) }
    var startOffsetY by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_start_y", 0f)) }
    var startScale by remember(layoutKey) { mutableFloatStateOf(sharedPrefs.getFloat("${layoutKey}_start_scale", 1f)) }

    LaunchedEffect(layoutKey, layoutRepository) {
        val values = layoutRepository.load(layoutKey)
        fun stored(suffix: String, current: Float) = values["${layoutKey}_$suffix"] ?: current
        dpadOffsetX = stored("dpad_x", dpadOffsetX)
        dpadOffsetY = stored("dpad_y", dpadOffsetY)
        dpadScale = stored("dpad_scale", dpadScale)
        leftStickOffsetX = stored("left_stick_x", leftStickOffsetX)
        leftStickOffsetY = stored("left_stick_y", leftStickOffsetY)
        leftStickScale = stored("left_stick_scale", leftStickScale)
        rightStickOffsetX = stored("right_stick_x", rightStickOffsetX)
        rightStickOffsetY = stored("right_stick_y", rightStickOffsetY)
        rightStickScale = stored("right_stick_scale", rightStickScale)
        faceButtonsOffsetX = stored("face_buttons_x", faceButtonsOffsetX)
        faceButtonsOffsetY = stored("face_buttons_y", faceButtonsOffsetY)
        faceButtonsScale = stored("face_buttons_scale", faceButtonsScale)
        leftTriggerOffsetX = stored("left_trigger_x", leftTriggerOffsetX)
        leftTriggerOffsetY = stored("left_trigger_y", leftTriggerOffsetY)
        leftTriggerScale = stored("left_trigger_scale", leftTriggerScale)
        leftBumperOffsetX = stored("left_bumper_x", leftBumperOffsetX)
        leftBumperOffsetY = stored("left_bumper_y", leftBumperOffsetY)
        leftBumperScale = stored("left_bumper_scale", leftBumperScale)
        rightTriggerOffsetX = stored("right_trigger_x", rightTriggerOffsetX)
        rightTriggerOffsetY = stored("right_trigger_y", rightTriggerOffsetY)
        rightTriggerScale = stored("right_trigger_scale", rightTriggerScale)
        rightBumperOffsetX = stored("right_bumper_x", rightBumperOffsetX)
        rightBumperOffsetY = stored("right_bumper_y", rightBumperOffsetY)
        rightBumperScale = stored("right_bumper_scale", rightBumperScale)
        guideOffsetX = stored("guide_x", guideOffsetX)
        guideOffsetY = stored("guide_y", guideOffsetY)
        guideScale = stored("guide_scale", guideScale)
        selectOffsetX = stored("select_x", selectOffsetX)
        selectOffsetY = stored("select_y", selectOffsetY)
        selectScale = stored("select_scale", selectScale)
        shareOffsetX = stored("share_x", shareOffsetX)
        shareOffsetY = stored("share_y", shareOffsetY)
        shareScale = stored("share_scale", shareScale)
        startOffsetX = stored("start_x", startOffsetX)
        startOffsetY = stored("start_y", startOffsetY)
        startScale = stored("start_scale", startScale)
    }

    val isModified = remember(
        layoutKey,
        dpadOffsetX, dpadOffsetY, dpadScale,
        leftStickOffsetX, leftStickOffsetY, leftStickScale,
        rightStickOffsetX, rightStickOffsetY, rightStickScale,
        faceButtonsOffsetX, faceButtonsOffsetY, faceButtonsScale,
        leftTriggerOffsetX, leftTriggerOffsetY, leftTriggerScale,
        leftBumperOffsetX, leftBumperOffsetY, leftBumperScale,
        rightTriggerOffsetX, rightTriggerOffsetY, rightTriggerScale,
        rightBumperOffsetX, rightBumperOffsetY, rightBumperScale,
        guideOffsetX, guideOffsetY, guideScale,
        selectOffsetX, selectOffsetY, selectScale,
        shareOffsetX, shareOffsetY, shareScale,
        startOffsetX, startOffsetY, startScale
    ) {

        dpadOffsetX != defaultDpadX || dpadOffsetY != defaultDpadY || dpadScale != 1f ||
        leftStickOffsetX != defaultLeftX || leftStickOffsetY != defaultLeftY || leftStickScale != 1f ||
        rightStickOffsetX != defaultRightX || rightStickOffsetY != defaultRightY || rightStickScale != 1f ||
        faceButtonsOffsetX != 0f || faceButtonsOffsetY != 0f || faceButtonsScale != 1f ||
        leftTriggerOffsetX != 0f || leftTriggerOffsetY != 0f || leftTriggerScale != 1f ||
        leftBumperOffsetX != 0f || leftBumperOffsetY != 0f || leftBumperScale != 1f ||
        rightTriggerOffsetX != 0f || rightTriggerOffsetY != 0f || rightTriggerScale != 1f ||
        rightBumperOffsetX != 0f || rightBumperOffsetY != 0f || rightBumperScale != 1f ||
        guideOffsetX != 0f || guideOffsetY != 0f || guideScale != 1f ||
        selectOffsetX != 0f || selectOffsetY != 0f || selectScale != 1f ||
        shareOffsetX != 0f || shareOffsetY != 0f || shareScale != 1f ||
        startOffsetX != 0f || startOffsetY != 0f || startScale != 1f
    }

    val resetDefaults = {
        val defaultFaceX = 0f
        val defaultFaceY = 0f
        
        dpadOffsetX = defaultDpadX
        dpadOffsetY = defaultDpadY
        dpadScale = 1f
        
        leftStickOffsetX = defaultLeftX
        leftStickOffsetY = defaultLeftY
        leftStickScale = 1f
        
        rightStickOffsetX = defaultRightX
        rightStickOffsetY = defaultRightY
        rightStickScale = 1f
        
        faceButtonsOffsetX = defaultFaceX
        faceButtonsOffsetY = defaultFaceY
        faceButtonsScale = 1f

        leftTriggerOffsetX = 0f
        leftTriggerOffsetY = 0f
        leftTriggerScale = 1f

        leftBumperOffsetX = 0f
        leftBumperOffsetY = 0f
        leftBumperScale = 1f

        rightTriggerOffsetX = 0f
        rightTriggerOffsetY = 0f
        rightTriggerScale = 1f

        rightBumperOffsetX = 0f
        rightBumperOffsetY = 0f
        rightBumperScale = 1f

        guideOffsetX = 0f
        guideOffsetY = 0f
         selectOffsetX = 0f
        selectOffsetY = 0f
        selectScale = 1f
        shareOffsetX = 0f
        shareOffsetY = 0f
        shareScale = 1f
        startOffsetX = 0f
        startOffsetY = 0f
        startScale = 1f
        
        sharedPrefs.edit {
            remove("${layoutKey}_dpad_x")
            remove("${layoutKey}_dpad_y")
            remove("${layoutKey}_dpad_scale")
            remove("${layoutKey}_left_stick_x")
            remove("${layoutKey}_left_stick_y")
            remove("${layoutKey}_left_stick_scale")
            remove("${layoutKey}_right_stick_x")
            remove("${layoutKey}_right_stick_y")
            remove("${layoutKey}_right_stick_scale")
            remove("${layoutKey}_face_buttons_x")
            remove("${layoutKey}_face_buttons_y")
            remove("${layoutKey}_face_buttons_scale")
            remove("${layoutKey}_left_trigger_x")
            remove("${layoutKey}_left_trigger_y")
            remove("${layoutKey}_left_trigger_scale")
            remove("${layoutKey}_left_bumper_x")
            remove("${layoutKey}_left_bumper_y")
            remove("${layoutKey}_left_bumper_scale")
            remove("${layoutKey}_right_trigger_x")
            remove("${layoutKey}_right_trigger_y")
            remove("${layoutKey}_right_trigger_scale")
            remove("${layoutKey}_right_bumper_x")
            remove("${layoutKey}_right_bumper_y")
            remove("${layoutKey}_right_bumper_scale")
            remove("${layoutKey}_guide_x")
            remove("${layoutKey}_guide_y")
            remove("${layoutKey}_guide_scale")
            remove("${layoutKey}_select_x")
            remove("${layoutKey}_select_y")
            remove("${layoutKey}_select_scale")
            remove("${layoutKey}_share_x")
            remove("${layoutKey}_share_y")
            remove("${layoutKey}_share_scale")
            remove("${layoutKey}_start_x")
            remove("${layoutKey}_start_y")
            remove("${layoutKey}_start_scale")
        }
        pendingLayoutValues.clear()
        scope.launch { layoutRepository.clear(layoutKey) }
            
        triggerVibration(50)
    }

    val connectedDevNow by btManager.connectedDevice.collectAsState()
    val modeSwitchState by btManager.gamepadModeSwitchState.collectAsState()
    val modeSwitchError by btManager.gamepadModeSwitchError.collectAsState()
    val modeSwitchBusy = modeSwitchState != GamepadModeSwitchState.IDLE
    if (!modeSwitchBusy && modeSwitchError != null) {
        AlertDialog(
            onDismissRequest = { btManager.dismissGamepadModeSwitchError() },
            title = { Text("Controller mode change interrupted") },
            text = { Text(modeSwitchError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { btManager.retryGamepadModeSwitch() }) { Text("Retry") }
            },
            dismissButton = {
                TextButton(onClick = { btManager.dismissGamepadModeSwitchError() }) { Text("Close") }
            },
        )
    }
    if (modeSwitchBusy) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.padding(28.dp).testTag("gamepad_mode_switch_loading"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (modeSwitchState == GamepadModeSwitchState.REGISTERING)
                            "Changing controller mode…" else "Reconnecting to device…",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    val isConnected = connectedDevNow != null
    val deviceName = (connectedDevNow ?: btManager.getReconnectTarget())?.name ?: "No Host"

    var buttonMask by remember { mutableIntStateOf(0) }
    var dpadMask by remember { mutableIntStateOf(0) }
    var isGamepadDirty by remember { mutableStateOf(false) }
    
    var leftStickX by remember { mutableFloatStateOf(0f) }
    var leftStickY by remember { mutableFloatStateOf(0f) }
    var rightStickX by remember { mutableFloatStateOf(0f) }
    var rightStickY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(modeSwitchBusy) {
        if (modeSwitchBusy) {
            buttonMask = 0
            dpadMask = 0
            leftStickX = 0f
            leftStickY = 0f
            rightStickX = 0f
            rightStickY = 0f
            isGamepadDirty = false
        }
    }

    val transmitGamepadState = { force: Boolean ->
        if (force) {
            btManager.sendGamepadReport(
                buttonMask,
                dpadMask,
                leftStickX,
                leftStickY,
                rightStickX,
                rightStickY
            )
            isGamepadDirty = false
        } else {
            isGamepadDirty = true
        }
    }

    LaunchedEffect(btManager) {
        while (isActive) {
            delay(GAMEPAD_REPORT_INTERVAL_MILLIS.milliseconds)
            if (isGamepadDirty) {
                btManager.sendGamepadReport(
                    buttonMask,
                    dpadMask,
                    leftStickX,
                    leftStickY,
                    rightStickX,
                    rightStickY
                )
                isGamepadDirty = false
            }
        }
    }

    val inputLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(btManager, inputLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                buttonMask = 0
                dpadMask = 0
                leftStickX = 0f
                leftStickY = 0f
                rightStickX = 0f
                rightStickY = 0f
                isGamepadDirty = false
                btManager.sendGamepadReport(0, 0, 0f, 0f, 0f, 0f)
            }
        }
        inputLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            inputLifecycleOwner.lifecycle.removeObserver(observer)
            // Do not leave the host with a sampled stick/button state after this UI and its
            // 8 ms ticker are disposed. Release reports are safety-critical and bypass sampling.
            btManager.sendGamepadReport(0, 0, 0f, 0f, 0f, 0f)
        }
    }

    val pressButton = { bitIndex: Int ->
        buttonMask = buttonMask or (1 shl bitIndex)
        transmitGamepadState(true)
        triggerVibration(15)
    }
    val releaseButton = { bitIndex: Int ->
        buttonMask = buttonMask and (1 shl bitIndex).inv()
        transmitGamepadState(true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gp.casing)
            .testTag("gamepad_view_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Settings Bar ──
            SessionToolbar(
                leading = {
                    ToolbarButton(
                        onClick = onClose,
                        onClickLabel = "Close gamepad",
                        modifier = Modifier.testTag("exit_gamepad_btn"),
                    ) {
                        ToolbarIcon(Icons.Default.Close, "Exit")
                        ToolbarLabel("Close")
                    }

                    // Mode cycle
                    ToolbarButton(
                        onClickLabel = "Next input mode",
                        onLongClickLabel = "Configure input mode cycle",
                        onClick = {
                            val enabledModes = sharedPrefs.enabledInputModes().map(InputMode::id)
                            val idx = enabledModes.indexOf(launchMode)
                            onModeChange(enabledModes[(idx + 1) % enabledModes.size])
                            triggerVibration(25)
                        },
                        onLongClick = {
                            context.startActivity(Intent(context, QuickCycleActivity::class.java))
                        },
                        modifier = Modifier.testTag("gamepad_mode_cycle_btn"),
                        containerColor = gp.toolbar.accentButton,
                    ) {
                        ToolbarIcon(Icons.Default.SportsEsports, "Switch Mode", tint = gp.toolbar.onAccentButton)
                        ToolbarLabel("Gamepad", color = gp.toolbar.onAccentButton)
                    }

                    // Connection status
                    ToolbarHostChip(isConnected = isConnected, hostName = deviceName)
                    ReconnectHostButton(btManager, iconOnly = true)
                },
                settingsActivity = dev.arnv.bluke.ControllerSettingsActivity::class.java,
                colors = gp.toolbar,
                trailing = {
                    // Output profile selector. All profiles share one descriptor, so
                    // Native, Android and Web switch live without HID re-registration.
                    ToolbarButton(
                        enabled = !modeSwitchBusy,
                        onClickLabel = "Switch controller compatibility",
                        onLongClickLabel = "Configure controller compatibility",
                        onClick = {
                            val newMode = dpadOutputMode.next()
                            if (btManager.changeGamepadMode(newMode)) triggerVibration(15)
                        },
                        onLongClick = {
                            context.startActivity(Intent(context, ControllerSettingsActivity::class.java))
                        },
                        modifier = Modifier.testTag("dpad_output_mode_toggle"),
                    ) {
                        ToolbarIcon(
                            when (dpadOutputMode) {
                                GamepadDpadOutputMode.WEB_BUTTONS -> Icons.Default.Language
                                GamepadDpadOutputMode.ANDROID -> Icons.Default.Android
                                GamepadDpadOutputMode.NATIVE_HAT -> Icons.Default.Gamepad
                            },
                            null,
                        )
                        ToolbarLabel("Mode: ${dpadOutputMode.label}")
                    }

                    // Reset Defaults (only shown when edit mode is active and there are layout changes)
                    if (isEditMode && isModified) {
                        ToolbarButton(
                            onClick = { resetDefaults() },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.testTag("reset_layout_defaults_btn"),
                        ) {
                            ToolbarIcon(Icons.Default.Refresh, "Reset Defaults", tint = MaterialTheme.colorScheme.onErrorContainer)
                            ToolbarLabel("Reset Defaults", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }

                    // Edit Layout toggle
                    ToolbarButton(
                        onClick = {
                            isEditMode = !isEditMode
                            triggerVibration(30)
                        },
                        modifier = Modifier.testTag("edit_layout_toggle_btn"),
                        containerColor = if (isEditMode) gp.accent else gp.toolbar.button,
                    ) {
                        val editContent = if (isEditMode) gp.onAccent else gp.toolbar.content
                        ToolbarIcon(if (isEditMode) Icons.Default.Check else Icons.Default.Edit, "Edit Layout", tint = editContent)
                        ToolbarLabel(if (isEditMode) "Done" else "Edit Layout", color = editContent)
                    }

                    // Console layout selector
                    ToolbarButton(
                        onClickLabel = "Next console layout",
                        onClick = {
                            selectedIndex = (selectedIndex + 1) % CONSOLES.size
                            triggerVibration(15)
                        },
                        modifier = Modifier.testTag("console_selector_pill"),
                    ) {
                        ToolbarIcon(Icons.Default.SportsEsports, "Consoles")
                        ToolbarLabel(config.name)
                    }

                    // Vibration toggle
                    ToolbarIconButton(
                        onClickLabel = if (isVibrationEnabled) "Turn off vibration" else "Turn on vibration",
                        onClick = {
                            val active = !isVibrationEnabled
                            isVibrationEnabled = active
                            sharedPrefs.edit { putBoolean("gamepad_vibration_enabled", active) }
                            if (active) triggerVibration(50)
                        },
                        modifier = Modifier.testTag("vibration_gamepad_toggle"),
                    ) {
                        if (isVibrationEnabled) {
                            ToolbarIcon(Icons.Default.Vibration, "Haptics")
                        } else {
                            ToolbarIcon(painterResource(R.drawable.ic_vibration_off), "Haptics")
                        }
                    }
                },
            )

            // ── Main Controls Body ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                GamepadBodyLayout(
                    isPortrait = isPortrait,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                ) {
                if (config.leftStickAboveDpad) {
                    // ── XBOX-STYLE OFFSET LAYOUT ──
                    // Left column: Left Stick (top), Dpad (bottom)
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(38.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = leftStickOffsetX,
                            offsetY = leftStickOffsetY,
                            scale = leftStickScale,
                            onOffsetChange = { x, y -> leftStickOffsetX = x; leftStickOffsetY = y; saveLayoutPref("${layoutKey}_left_stick_x", x); saveLayoutPref("${layoutKey}_left_stick_y", y) },
                            onScaleChange = { s -> leftStickScale = s; saveLayoutPref("${layoutKey}_left_stick_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            GamepadAnalogStick(
                                label = "L",
                                isClicked = { (buttonMask and (1 shl 10)) != 0 },
                                isHeld = { (buttonMask and (1 shl 10)) != 0 },
                                onMove = { x, y -> leftStickX = x; leftStickY = y; transmitGamepadState(false) },
                                onStickClick = { scope.launch { pressButton(10); delay(100L.milliseconds); releaseButton(10) } },
                                onToggleHold = { hold -> if (hold) pressButton(10) else releaseButton(10) }
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = dpadOffsetX,
                            offsetY = dpadOffsetY,
                            scale = dpadScale,
                            onOffsetChange = { x, y -> dpadOffsetX = x; dpadOffsetY = y; saveLayoutPref("${layoutKey}_dpad_x", x); saveLayoutPref("${layoutKey}_dpad_y", y) },
                            onScaleChange = { s -> dpadScale = s; saveLayoutPref("${layoutKey}_dpad_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            GamepadDpad(
                                isXboxStyle = true,
                                onDpadChange = { mask ->
                                    dpadMask = mask
                                    transmitGamepadState(true)
                                    if (mask != 0) triggerVibration(15)
                                }
                            )
                        }
                    }

                    // Center column: Logo, Center Buttons, Triggers/Bumpers
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(14.dp))
                        // Triggers/Bumpers vertically stacked at the top
                        Row(
                            modifier = Modifier.fillMaxWidth(0.95f),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left stack: LB above LT
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = leftTriggerOffsetX,
                                    offsetY = leftTriggerOffsetY,
                                    scale = leftTriggerScale,
                                    onOffsetChange = { x, y -> leftTriggerOffsetX = x; leftTriggerOffsetY = y; saveLayoutPref("${layoutKey}_left_trigger_x", x); saveLayoutPref("${layoutKey}_left_trigger_y", y) },
                                    onScaleChange = { s -> leftTriggerScale = s; saveLayoutPref("${layoutKey}_left_trigger_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadTriggerButton(config.leftTrigger, true, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = leftBumperOffsetX,
                                    offsetY = leftBumperOffsetY,
                                    scale = leftBumperScale,
                                    onOffsetChange = { x, y -> leftBumperOffsetX = x; leftBumperOffsetY = y; saveLayoutPref("${layoutKey}_left_bumper_x", x); saveLayoutPref("${layoutKey}_left_bumper_y", y) },
                                    onScaleChange = { s -> leftBumperScale = s; saveLayoutPref("${layoutKey}_left_bumper_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadBumperButton(config.leftBumper, true, pressButton, releaseButton)
                                }
                            }
                            // Right stack: RB above RT
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = rightTriggerOffsetX,
                                    offsetY = rightTriggerOffsetY,
                                    scale = rightTriggerScale,
                                    onOffsetChange = { x, y -> rightTriggerOffsetX = x; rightTriggerOffsetY = y; saveLayoutPref("${layoutKey}_right_trigger_x", x); saveLayoutPref("${layoutKey}_right_trigger_y", y) },
                                    onScaleChange = { s -> rightTriggerScale = s; saveLayoutPref("${layoutKey}_right_trigger_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadTriggerButton(config.rightTrigger, false, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = rightBumperOffsetX,
                                    offsetY = rightBumperOffsetY,
                                    scale = rightBumperScale,
                                    onOffsetChange = { x, y -> rightBumperOffsetX = x; rightBumperOffsetY = y; saveLayoutPref("${layoutKey}_right_bumper_x", x); saveLayoutPref("${layoutKey}_right_bumper_y", y) },
                                    onScaleChange = { s -> rightBumperScale = s; saveLayoutPref("${layoutKey}_right_bumper_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadBumperButton(config.rightBumper, false, pressButton, releaseButton)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Large Xbox Guide button
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = guideOffsetX,
                            offsetY = guideOffsetY,
                            scale = guideScale,
                            onOffsetChange = { x, y -> guideOffsetX = x; guideOffsetY = y; saveLayoutPref("${layoutKey}_guide_x", x); saveLayoutPref("${layoutKey}_guide_y", y) },
                            onScaleChange = { s -> guideScale = s; saveLayoutPref("${layoutKey}_guide_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            XboxLogoGuideButton(config.guideButton, pressButton, releaseButton)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Center buttons (Select, Share, Start) flanking the area below Guide
                        Row(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = selectOffsetX,
                                offsetY = selectOffsetY,
                                scale = selectScale,
                                onOffsetChange = { x, y -> selectOffsetX = x; selectOffsetY = y; saveLayoutPref("${layoutKey}_select_x", x); saveLayoutPref("${layoutKey}_select_y", y) },
                                onScaleChange = { s -> selectScale = s; saveLayoutPref("${layoutKey}_select_scale", s) },
                                onTransformEnd = commitLayoutPrefs
                            ) {
                                GamepadCenterButton(config.selectButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = shareOffsetX,
                                offsetY = shareOffsetY,
                                scale = shareScale,
                                onOffsetChange = { x, y -> shareOffsetX = x; shareOffsetY = y; saveLayoutPref("${layoutKey}_share_x", x); saveLayoutPref("${layoutKey}_share_y", y) },
                                onScaleChange = { s -> shareScale = s; saveLayoutPref("${layoutKey}_share_scale", s) },
                                onTransformEnd = commitLayoutPrefs
                            ) {
                                GamepadCenterButton(config.shareButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = startOffsetX,
                                offsetY = startOffsetY,
                                scale = startScale,
                                onOffsetChange = { x, y -> startOffsetX = x; startOffsetY = y; saveLayoutPref("${layoutKey}_start_x", x); saveLayoutPref("${layoutKey}_start_y", y) },
                                onScaleChange = { s -> startScale = s; saveLayoutPref("${layoutKey}_start_scale", s) },
                                onTransformEnd = commitLayoutPrefs
                            ) {
                                GamepadCenterButton(config.startButton, pressButton, releaseButton)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    // Right column: Face Buttons (top), Right Stick (bottom)
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(16.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = faceButtonsOffsetX,
                            offsetY = faceButtonsOffsetY,
                            scale = faceButtonsScale,
                            onOffsetChange = { x, y -> faceButtonsOffsetX = x; faceButtonsOffsetY = y; saveLayoutPref("${layoutKey}_face_buttons_x", x); saveLayoutPref("${layoutKey}_face_buttons_y", y) },
                            onScaleChange = { s -> faceButtonsScale = s; saveLayoutPref("${layoutKey}_face_buttons_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            FaceButtonsDiamond(
                                config = config,
                                isXboxStyle = true,
                                onPress = pressButton,
                                onRelease = releaseButton
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = rightStickOffsetX,
                            offsetY = rightStickOffsetY,
                            scale = rightStickScale,
                            onOffsetChange = { x, y -> rightStickOffsetX = x; rightStickOffsetY = y; saveLayoutPref("${layoutKey}_right_stick_x", x); saveLayoutPref("${layoutKey}_right_stick_y", y) },
                            onScaleChange = { s -> rightStickScale = s; saveLayoutPref("${layoutKey}_right_stick_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            GamepadAnalogStick(
                                label = "R",
                                isClicked = { (buttonMask and (1 shl 11)) != 0 },
                                isHeld = { (buttonMask and (1 shl 11)) != 0 },
                                onMove = { x, y -> rightStickX = x; rightStickY = y; transmitGamepadState(false) },
                                onStickClick = { scope.launch { pressButton(11); delay(100L.milliseconds); releaseButton(11) } },
                                onToggleHold = { hold -> if (hold) pressButton(11) else releaseButton(11) }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    // ── PLAYSTATION-STYLE SYMMETRICAL LAYOUT ──
                    // Left Column: split Dpad (top), Left Stick (bottom)
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(26.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = dpadOffsetX,
                            offsetY = dpadOffsetY,
                            scale = dpadScale,
                            onOffsetChange = { x, y -> dpadOffsetX = x; dpadOffsetY = y; saveLayoutPref("${layoutKey}_dpad_x", x); saveLayoutPref("${layoutKey}_dpad_y", y) },
                            onScaleChange = { s -> dpadScale = s; saveLayoutPref("${layoutKey}_dpad_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            GamepadDpad(
                                isXboxStyle = false,
                                onDpadChange = { mask ->
                                    dpadMask = mask
                                    transmitGamepadState(true)
                                    if (mask != 0) triggerVibration(15)
                                }
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = leftStickOffsetX,
                            offsetY = leftStickOffsetY,
                            scale = leftStickScale,
                            onOffsetChange = { x, y -> leftStickOffsetX = x; leftStickOffsetY = y; saveLayoutPref("${layoutKey}_left_stick_x", x); saveLayoutPref("${layoutKey}_left_stick_y", y) },
                            onScaleChange = { s -> leftStickScale = s; saveLayoutPref("${layoutKey}_left_stick_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            GamepadAnalogStick(
                                label = "L",
                                isClicked = { (buttonMask and (1 shl 10)) != 0 },
                                isHeld = { (buttonMask and (1 shl 10)) != 0 },
                                onMove = { x, y -> leftStickX = x; leftStickY = y; transmitGamepadState(false) },
                                onStickClick = { scope.launch { pressButton(10); delay(100L.milliseconds); releaseButton(10) } },
                                onToggleHold = { hold -> if (hold) pressButton(10) else releaseButton(10) }
                            )
                        }
                    }

                    // Center Column: PS Button + L1/L2 R1/R2, Right Stick/Left Stick spacing
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(16.dp))
                        // Bumper/Trigger stacks
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = leftTriggerOffsetX,
                                    offsetY = leftTriggerOffsetY,
                                    scale = leftTriggerScale,
                                    onOffsetChange = { x, y -> leftTriggerOffsetX = x; leftTriggerOffsetY = y; saveLayoutPref("${layoutKey}_left_trigger_x", x); saveLayoutPref("${layoutKey}_left_trigger_y", y) },
                                    onScaleChange = { s -> leftTriggerScale = s; saveLayoutPref("${layoutKey}_left_trigger_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadTriggerButton(config.leftTrigger, true, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = leftBumperOffsetX,
                                    offsetY = leftBumperOffsetY,
                                    scale = leftBumperScale,
                                    onOffsetChange = { x, y -> leftBumperOffsetX = x; leftBumperOffsetY = y; saveLayoutPref("${layoutKey}_left_bumper_x", x); saveLayoutPref("${layoutKey}_left_bumper_y", y) },
                                    onScaleChange = { s -> leftBumperScale = s; saveLayoutPref("${layoutKey}_left_bumper_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadBumperButton(config.leftBumper, true, pressButton, releaseButton)
                                }
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = rightTriggerOffsetX,
                                    offsetY = rightTriggerOffsetY,
                                    scale = rightTriggerScale,
                                    onOffsetChange = { x, y -> rightTriggerOffsetX = x; rightTriggerOffsetY = y; saveLayoutPref("${layoutKey}_right_trigger_x", x); saveLayoutPref("${layoutKey}_right_trigger_y", y) },
                                    onScaleChange = { s -> rightTriggerScale = s; saveLayoutPref("${layoutKey}_right_trigger_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadTriggerButton(config.rightTrigger, false, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = rightBumperOffsetX,
                                    offsetY = rightBumperOffsetY,
                                    scale = rightBumperScale,
                                    onOffsetChange = { x, y -> rightBumperOffsetX = x; rightBumperOffsetY = y; saveLayoutPref("${layoutKey}_right_bumper_x", x); saveLayoutPref("${layoutKey}_right_bumper_y", y) },
                                    onScaleChange = { s -> rightBumperScale = s; saveLayoutPref("${layoutKey}_right_bumper_scale", s) },
                                    onTransformEnd = commitLayoutPrefs
                                ) {
                                    GamepadBumperButton(config.rightBumper, false, pressButton, releaseButton)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // PlayStation Guide button
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = guideOffsetX,
                            offsetY = guideOffsetY,
                            scale = guideScale,
                            onOffsetChange = { x, y -> guideOffsetX = x; guideOffsetY = y; saveLayoutPref("${layoutKey}_guide_x", x); saveLayoutPref("${layoutKey}_guide_y", y) },
                            onScaleChange = { s -> guideScale = s; saveLayoutPref("${layoutKey}_guide_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            PlayStationLogoButton(config.guideButton, pressButton, releaseButton)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Center buttons (Select, Share, Start) flanking the area below Guide
                        Row(
                            modifier = Modifier.fillMaxWidth(0.75f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = selectOffsetX,
                                offsetY = selectOffsetY,
                                scale = selectScale,
                                onOffsetChange = { x, y -> selectOffsetX = x; selectOffsetY = y; saveLayoutPref("${layoutKey}_select_x", x); saveLayoutPref("${layoutKey}_select_y", y) },
                                onScaleChange = { s -> selectScale = s; saveLayoutPref("${layoutKey}_select_scale", s) },
                                onTransformEnd = commitLayoutPrefs
                            ) {
                                GamepadCenterButton(config.selectButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = shareOffsetX,
                                offsetY = shareOffsetY,
                                scale = shareScale,
                                onOffsetChange = { x, y -> shareOffsetX = x; shareOffsetY = y; saveLayoutPref("${layoutKey}_share_x", x); saveLayoutPref("${layoutKey}_share_y", y) },
                                onScaleChange = { s -> shareScale = s; saveLayoutPref("${layoutKey}_share_scale", s) },
                                onTransformEnd = commitLayoutPrefs
                            ) {
                                GamepadCenterButton(config.shareButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = startOffsetX,
                                offsetY = startOffsetY,
                                scale = startScale,
                                onOffsetChange = { x, y -> startOffsetX = x; startOffsetY = y; saveLayoutPref("${layoutKey}_start_x", x); saveLayoutPref("${layoutKey}_start_y", y) },
                                onScaleChange = { s -> startScale = s; saveLayoutPref("${layoutKey}_start_scale", s) },
                                onTransformEnd = commitLayoutPrefs
                            ) {
                                GamepadCenterButton(config.startButton, pressButton, releaseButton)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    // Right Column: Face Buttons (top), Right Stick (bottom)
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(16.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = faceButtonsOffsetX,
                            offsetY = faceButtonsOffsetY,
                            scale = faceButtonsScale,
                            onOffsetChange = { x, y -> faceButtonsOffsetX = x; faceButtonsOffsetY = y; saveLayoutPref("${layoutKey}_face_buttons_x", x); saveLayoutPref("${layoutKey}_face_buttons_y", y) },
                            onScaleChange = { s -> faceButtonsScale = s; saveLayoutPref("${layoutKey}_face_buttons_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            FaceButtonsDiamond(
                                config = config,
                                isXboxStyle = false,
                                onPress = pressButton,
                                onRelease = releaseButton
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = rightStickOffsetX,
                            offsetY = rightStickOffsetY,
                            scale = rightStickScale,
                            onOffsetChange = { x, y -> rightStickOffsetX = x; rightStickOffsetY = y; saveLayoutPref("${layoutKey}_right_stick_x", x); saveLayoutPref("${layoutKey}_right_stick_y", y) },
                            onScaleChange = { s -> rightStickScale = s; saveLayoutPref("${layoutKey}_right_stick_scale", s) },
                            onTransformEnd = commitLayoutPrefs
                        ) {
                            GamepadAnalogStick(
                                label = "R",
                                isClicked = { (buttonMask and (1 shl 11)) != 0 },
                                isHeld = { (buttonMask and (1 shl 11)) != 0 },
                                onMove = { x, y -> rightStickX = x; rightStickY = y; transmitGamepadState(false) },
                                onStickClick = { scope.launch { pressButton(11); delay(100L.milliseconds); releaseButton(11) } },
                                onToggleHold = { hold -> if (hold) pressButton(11) else releaseButton(11) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

// ── Sub-Components ──

/**
 * Places the left, center and right control columns, emitted in that order.
 * Landscape: three columns side by side (30% / 40% / 30%).
 * Portrait: the center column (shoulders, guide, select/start) across the top,
 * with the left and right columns side by side below it.
 */
@Composable
private fun GamepadBodyLayout(
    isPortrait: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        check(measurables.size == 3) { "Expected left, center and right columns" }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (!isPortrait) {
            val side = (width * 0.3f).roundToInt()
            val widths = listOf(side, width - side * 2, side)
            val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], height)) }
            layout(width, height) {
                var x = 0
                placeables.forEach { it.place(x, 0); x += it.width }
            }
        } else {
            val centerHeight = minOf((height * 0.4f).roundToInt(), 240.dp.roundToPx())
            // Sticks and buttons sit at the bottom, where thumbs rest on an upright phone.
            val sideHeight = minOf(height - centerHeight, 400.dp.roundToPx())
            val sideTop = height - sideHeight
            val half = width / 2
            val left = measurables[0].measure(Constraints.fixed(half, sideHeight))
            val center = measurables[1].measure(Constraints.fixed(width, centerHeight))
            val right = measurables[2].measure(Constraints.fixed(width - half, sideHeight))
            layout(width, height) {
                center.place(0, 0)
                left.place(0, sideTop)
                right.place(half, sideTop)
            }
        }
    }
}

@Composable
fun Modifier.gamepadButtonTouch(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onPressedStateChange: (Boolean) -> Unit
): Modifier {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnPressedStateChange by rememberUpdatedState(onPressedStateChange)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val targetPointerId = down.id
            currentOnPressedStateChange(true)
            currentOnPress()
            down.consume()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == targetPointerId } ?: break
                if (!change.pressed) {
                    currentOnPressedStateChange(false)
                    currentOnRelease()
                    break
                }
                change.consume()
            }
        }
    }
}

@Composable
private fun GamepadBumperButton(
    button: ButtonDef,
    isLeft: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val gp = rememberControlSurfaceColors()
    var isPressed by remember { mutableStateOf(false) }
    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) (-0.6).dp else 0.dp,
        animationSpec = tween(durationMillis = 70, easing = LinearEasing),
        label = "bumperPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 70, easing = LinearEasing),
        label = "bumperScale"
    )

    val outerShape = if (isLeft) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 10.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 14.dp, bottomStart = 10.dp, bottomEnd = 6.dp)
    }
    
    val innerShape = if (isLeft) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomStart = 4.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomStart = 8.dp, bottomEnd = 4.dp)
    }

    // Outer well container (widened to 120.dp)
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(34.dp)
            .background(gp.well, outerShape)
            .border(1.2.dp, gp.shadow(0.5f), outerShape),
        contentAlignment = Alignment.Center
    ) {
        // Inner button cap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp) // Well padding
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleY = pressScale
                }
                .clip(innerShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(gp.capPressedTop, gp.capPressedBottom)
                        } else {
                            listOf(gp.capTop, gp.capBottom)
                        }
                    )
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        colors = listOf(gp.highlight, Color.Transparent)
                    ),
                    innerShape
                )
                .gamepadButtonTouch(
                    onPress = { onPress(button.mappingId) },
                    onRelease = { onRelease(button.mappingId) },
                    onPressedStateChange = { isPressed = it }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = button.label,
                color = if (isPressed) gp.labelPressed else gp.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
private fun GamepadTriggerButton(
    button: ButtonDef,
    isLeft: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val gp = rememberControlSurfaceColors()
    var isPressed by remember { mutableStateOf(false) }
    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "triggerPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        label = "triggerScale"
    )

    val outerShape = if (isLeft) {
        RoundedCornerShape(topStart = 10.dp, topEnd = 6.dp, bottomStart = 14.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 10.dp, bottomStart = 8.dp, bottomEnd = 14.dp)
    }

    val innerShape = if (isLeft) {
        RoundedCornerShape(topStart = 8.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 8.dp, bottomStart = 6.dp, bottomEnd = 12.dp)
    }

    // Outer well container (widened to 120.dp)
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(48.dp)
            .background(gp.well, outerShape)
            .border(1.2.dp, gp.shadow(0.5f), outerShape),
        contentAlignment = Alignment.Center
    ) {
        // Inner button cap
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp) // Well padding
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleY = pressScale
                }
                .clip(innerShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(gp.capPressedTop, gp.capPressedBottom)
                        } else {
                            listOf(gp.capTop, gp.capBottom)
                        }
                    )
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        colors = listOf(gp.highlight, Color.Transparent)
                    ),
                    innerShape
                )
                .gamepadButtonTouch(
                    onPress = { onPress(button.mappingId) },
                    onRelease = { onRelease(button.mappingId) },
                    onPressedStateChange = { isPressed = it }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = button.label,
                color = if (isPressed) gp.labelPressed else gp.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

private fun Color.gpDarker(factor: Float = 0.22f): Color {
    return Color(
        red = (this.red * (1f - factor)).coerceIn(0f, 1f),
        green = (this.green * (1f - factor)).coerceIn(0f, 1f),
        blue = (this.blue * (1f - factor)).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

private fun Color.gpLighter(factor: Float = 0.25f): Color {
    return Color(
        red = (this.red + (1f - this.red) * factor).coerceIn(0f, 1f),
        green = (this.green + (1f - this.green) * factor).coerceIn(0f, 1f),
        blue = (this.blue + (1f - this.blue) * factor).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}

@Composable
private fun GamepadFaceButton(
    button: ButtonDef,
    isXboxStyle: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
    externalIsPressed: Boolean = false
) {
    val gp = rememberControlSurfaceColors()
    var internalIsPressed by remember { mutableStateOf(false) }
    val isPressed = internalIsPressed || externalIsPressed

    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "pressOffset"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "pressScale"
    )

    // Base color of the disk itself (dark gray, themed to the background #28282A)
    val baseColor = gp.capTop
    
    // Label/Symbol color (A, B, X, Y or △, ◯, ✕, ☐)
    val labelColor = gp.harmonize(button.color)

    // Gap clearance reduced to 1.5.dp (well size 48.dp, cap size 45.dp)
    Box(
        modifier = modifier
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Button Well (physical hole in casing with shadow)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gp.well)
                .border(0.8.dp, gp.shadow(0.25f), CircleShape)
        )

        // 2. Button Cap (presses down into the well)
        Box(
            modifier = Modifier
                .size(45.dp)
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .gamepadButtonTouch(
                    onPress = { onPress(button.mappingId) },
                    onRelease = { onRelease(button.mappingId) },
                    onPressedStateChange = { internalIsPressed = it }
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f

                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isPressed) 0.3f.dp.toPx() else 1.8f.dp.toPx()
                val faceOffset = if (isPressed) 0f else maxOffset

                // Calculate radii to fit perfectly within the clip boundaries
                val edgeR = r - edgeOffset / 2f - 0.5.dp.toPx()
                val faceR = r - maxOffset - 0.5.dp.toPx()

                // Draw 3D side edge of the disk (darker than the face)
                drawCircle(
                    color = gp.capEdge,
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face of the disk: the theme's cap tone, faintly tinted by the button's
                // own color, lighting up in that color while pressed.
                val faceColor = if (isPressed) lerp(gp.capBottom, labelColor, 0.30f) else lerp(baseColor, labelColor, 0.06f)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceColor.gpLighter(0.08f), faceColor.gpDarker(0.12f))
                    ),
                    radius = faceR,
                    center = Offset(r, r - faceOffset)
                )

                // Flat face bevel edge highlight
                drawCircle(
                    color = gp.highlight(if (isPressed) 0.35f else 1f),
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // Bezel shadow
                drawCircle(
                    color = gp.shadow(if (isPressed) 0.10f else 0.25f),
                    radius = faceR,
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // Offset the content slightly to match the unpressed/pressed 3D displacement
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = if (isPressed) 0.dp else (-0.6).dp),
                contentAlignment = Alignment.Center
            ) {
                if (isXboxStyle) {
                    Text(
                        text = button.label,
                        color = labelColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val strokeW = 2.8.dp.toPx()
                        val radius = 6.5.dp.toPx()
                        
                        when (button.label) {
                            "△" -> {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(cx, cy - radius)
                                    lineTo(cx + radius, cy + radius * 0.5f)
                                    lineTo(cx - radius, cy + radius * 0.5f)
                                    close()
                                }
                                drawPath(path, labelColor, style = Stroke(width = strokeW))
                            }
                            "◯" -> {
                                drawCircle(labelColor, radius = radius, center = Offset(cx, cy), style = Stroke(width = strokeW))
                            }
                            "✕" -> {
                                drawLine(labelColor, start = Offset(cx - radius, cy - radius), end = Offset(cx + radius, cy + radius), strokeWidth = strokeW)
                                drawLine(labelColor, start = Offset(cx + radius, cy - radius), end = Offset(cx - radius, cy + radius), strokeWidth = strokeW)
                            }
                            "☐" -> {
                                val side = radius * 1.5f
                                drawRect(
                                    labelColor,
                                    topLeft = Offset(cx - side/2f, cy - side/2f),
                                    size = androidx.compose.ui.geometry.Size(side, side),
                                    style = Stroke(width = strokeW)
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
private fun FaceButtonsDiamond(
    config: ConsoleConfig,
    @Suppress("UNUSED_PARAMETER") isXboxStyle: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 40.dp
    val density = LocalDensity.current.density

    // Track active pressed button mapping IDs via proximity multi-touch
    val activePressedButtons = remember { mutableStateListOf<Int>() }

    val updateProximityPresses = { pointerPositions: List<Offset>, containerSizePx: Float ->
        val centerPx = containerSizePx / 2f
        val spacingPx = 40f * density
        val touchRadiusPx = 35f * density // Proximity radius covering multi-button thumb presses

        val topCenter = Offset(centerPx, centerPx - spacingPx)
        val rightCenter = Offset(centerPx + spacingPx, centerPx)
        val bottomCenter = Offset(centerPx, centerPx + spacingPx)
        val leftCenter = Offset(centerPx - spacingPx, centerPx)

        val buttonCenters = listOf(
            config.faceTop.mappingId to topCenter,
            config.faceRight.mappingId to rightCenter,
            config.faceBottom.mappingId to bottomCenter,
            config.faceLeft.mappingId to leftCenter
        )

        val newlyActive = mutableSetOf<Int>()
        for (pos in pointerPositions) {
            for ((mappingId, btnCenter) in buttonCenters) {
                val dx = pos.x - btnCenter.x
                val dy = pos.y - btnCenter.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= touchRadiusPx) {
                    newlyActive.add(mappingId)
                }
            }
        }

        // Press newly touched buttons
        for (mappingId in newlyActive) {
            if (!activePressedButtons.contains(mappingId)) {
                activePressedButtons.add(mappingId)
                onPress(mappingId)
            }
        }

        // Release buttons no longer under any active finger
        val toRemove = mutableListOf<Int>()
        for (mappingId in activePressedButtons) {
            if (!newlyActive.contains(mappingId)) {
                toRemove.add(mappingId)
                onRelease(mappingId)
            }
        }
        activePressedButtons.removeAll(toRemove.toSet())
    }

    Box(
        modifier = modifier
            .size(134.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val activePositions = mutableMapOf<PointerId, Offset>()
                    activePositions[down.id] = down.position
                    updateProximityPresses(activePositions.values.toList(), size.width.toFloat())

                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            if (change.pressed) {
                                activePositions[change.id] = change.position
                            } else {
                                activePositions.remove(change.id)
                            }
                        }
                        if (activePositions.isEmpty()) {
                            for (id in activePressedButtons.toList()) {
                                onRelease(id)
                            }
                            activePressedButtons.clear()
                            break
                        }
                        updateProximityPresses(activePositions.values.toList(), size.width.toFloat())
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        GamepadFaceButton(config.faceTop, isXboxStyle, onPress, onRelease, Modifier.offset(y = -spacing), externalIsPressed = activePressedButtons.contains(config.faceTop.mappingId))
        GamepadFaceButton(config.faceRight, isXboxStyle, onPress, onRelease, Modifier.offset(x = spacing), externalIsPressed = activePressedButtons.contains(config.faceRight.mappingId))
        GamepadFaceButton(config.faceBottom, isXboxStyle, onPress, onRelease, Modifier.offset(y = spacing), externalIsPressed = activePressedButtons.contains(config.faceBottom.mappingId))
        GamepadFaceButton(config.faceLeft, isXboxStyle, onPress, onRelease, Modifier.offset(x = -spacing), externalIsPressed = activePressedButtons.contains(config.faceLeft.mappingId))
    }
}

@Composable
private fun GamepadCenterButton(
    button: ButtonDef,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val gp = rememberControlSurfaceColors()
    var isPressed by remember { mutableStateOf(false) }
    val iconColor = if (isPressed) gp.labelPressed else gp.label
    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "centerBtnPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "centerBtnScale"
    )

    // Well size 32.dp, cap size 29.dp (1.5.dp gap)
    Box(
        modifier = Modifier
            .size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Button well
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gp.well)
                .border(0.8.dp, gp.shadow(0.25f), CircleShape)
        )

        // Button Cap (Flat Disk)
        Box(
            modifier = Modifier
                .size(29.dp)
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .gamepadButtonTouch(
                    onPress = { onPress(button.mappingId) },
                    onRelease = { onRelease(button.mappingId) },
                    onPressedStateChange = { isPressed = it }
                ),
            contentAlignment = Alignment.Center
        ) {
            val baseColor = gp.capTop
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f

                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isPressed) 0.3f.dp.toPx() else 1.5f.dp.toPx()
                val faceOffset = if (isPressed) 0f else maxOffset

                // Calculate radii to fit perfectly within the clip boundaries
                val edgeR = r - edgeOffset / 2f - 0.5.dp.toPx()
                val faceR = r - maxOffset - 0.5.dp.toPx()

                // 3D side edge
                drawCircle(
                    color = gp.capEdge,
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face
                val faceColor = if (isPressed) gp.capPressedTop else baseColor
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceColor.gpLighter(0.08f), faceColor.gpDarker(0.12f))
                    ),
                    radius = faceR,
                    center = Offset(r, r - faceOffset)
                )

                // Bevel highlight (vertical gradient brush for better shading)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(gp.highlight(0.35f), Color.Transparent)
                        } else {
                            listOf(gp.highlight, Color.Transparent)
                        }
                    ),
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // Offset the icon content to match the 3D displacement
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = if (isPressed) 0.dp else (-0.6).dp),
                contentAlignment = Alignment.Center
            ) {
                if (button.label == "VIEW" || button.label == "CREATE") {
                    Canvas(modifier = Modifier.size(11.dp)) {
                        val w = size.width
                        val h = size.height
                        val strokeW = 1.5.dp.toPx()
                        
                        // Back window
                        drawRect(
                            color = iconColor,
                            topLeft = Offset(w * 0.25f, 0f),
                            size = androidx.compose.ui.geometry.Size(w * 0.75f, h * 0.75f),
                            style = Stroke(strokeW)
                        )
                        // Front window
                        drawRect(
                            color = iconColor,
                            topLeft = Offset(0f, h * 0.25f),
                            size = androidx.compose.ui.geometry.Size(w * 0.75f, h * 0.75f),
                            style = Stroke(strokeW)
                        )
                    }
                } else if (button.label == "SHARE") {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = "Share",
                        tint = iconColor,
                        modifier = Modifier.size(11.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.size(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.6.dp)
                                    .background(iconColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamepadShareButton(
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val gp = rememberControlSurfaceColors()
    var isPressed by remember { mutableStateOf(false) }
    val iconColor = if (isPressed) gp.labelPressed else gp.label
    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "shareBtnPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "shareBtnScale"
    )

    Box(
        modifier = Modifier
            .width(38.dp)
            .height(28.dp),
        contentAlignment = Alignment.Center
    ) {
        val outerShape = RoundedCornerShape(8.dp)
        val innerShape = RoundedCornerShape(6.dp)
        
        // Button well
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gp.well, outerShape)
                .border(1.2.dp, gp.shadow(0.5f), outerShape)
        )

        // Button Cap (Flat Disk 3D) - Gap clearance reduced to 1.5.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp) // Gap clearance
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(innerShape)
                .gamepadButtonTouch(
                    onPress = { onPress(18) },
                    onRelease = { onRelease(18) },
                    onPressedStateChange = { isPressed = it }
                ),
            contentAlignment = Alignment.Center
        ) {
            val baseColor = gp.capTop
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isPressed) 0.3f.dp.toPx() else 1.5f.dp.toPx()
                val faceOffset = if (isPressed) maxOffset else 0f

                // 3D side edge
                drawRoundRect(
                    color = gp.capEdge,
                    topLeft = Offset(0f, edgeOffset),
                    size = androidx.compose.ui.geometry.Size(w, h - edgeOffset),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Top face
                val faceColor = if (isPressed) gp.capPressedTop else baseColor
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceColor.gpLighter(0.08f), faceColor.gpDarker(0.12f))
                    ),
                    topLeft = Offset(0f, faceOffset),
                    size = androidx.compose.ui.geometry.Size(w, h - maxOffset),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Bevel highlight
                drawRoundRect(
                    color = gp.highlight(if (isPressed) 0.35f else 1f),
                    topLeft = Offset(0.5.dp.toPx(), faceOffset + 0.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(w - 1.dp.toPx(), h - maxOffset - 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.5.dp.toPx(), 5.5.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = if (isPressed) 0.6.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.IosShare,
                    contentDescription = "Share",
                    tint = iconColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun XboxLogoGuideButton(
    button: ButtonDef,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val gp = rememberControlSurfaceColors()
    var isPressed by remember { mutableStateOf(false) }
    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "xboxLogoPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "xboxLogoScale"
    )

    // Well size 50.dp, cap size 47.dp (1.5.dp gap)
    Box(
        modifier = Modifier
            .size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Button well
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gp.well)
                .border(0.8.dp, gp.shadow(0.25f), CircleShape)
        )

        // 2. Button Cap (Flat Disk)
        Box(
            modifier = Modifier
                .size(47.dp)
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .gamepadButtonTouch(
                    onPress = { onPress(button.mappingId) },
                    onRelease = { onRelease(button.mappingId) },
                    onPressedStateChange = { isPressed = it }
                )
        ) {
            val baseColor = gp.capTop
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f

                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isPressed) 0.3f.dp.toPx() else 1.8f.dp.toPx()
                val faceOffset = if (isPressed) 0f else maxOffset

                // Calculate radii to fit perfectly within the clip boundaries
                val edgeR = r - edgeOffset / 2f - 0.5.dp.toPx()
                val faceR = r - maxOffset - 0.5.dp.toPx()

                // 3D side edge
                drawCircle(
                    color = gp.capEdge,
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face
                val faceColor = if (isPressed) gp.capPressedTop else baseColor
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceColor.gpLighter(0.08f), faceColor.gpDarker(0.12f))
                    ),
                    radius = faceR,
                    center = Offset(r, r - faceOffset)
                )

                // Bevel highlight border (vertical gradient brush for better shading)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(gp.highlight(0.35f), Color.Transparent)
                        } else {
                            listOf(gp.highlight, Color.Transparent)
                        }
                    ),
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
            
            // Developer logo overlay centered on top face
            val faceOffset = if (isPressed) 0.dp else 0.6.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = -faceOffset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    tint = if (isPressed) gp.labelPressed else gp.accent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayStationLogoButton(
    button: ButtonDef,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit
) {
    val gp = rememberControlSurfaceColors()
    var isPressed by remember { mutableStateOf(false) }
    val pressOffsetY by animateDpAsState(
        targetValue = if (isPressed) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "psLogoPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "psLogoScale"
    )

    // Well size 50.dp, cap size 47.dp (1.5.dp gap)
    Box(
        modifier = Modifier
            .size(50.dp),
        contentAlignment = Alignment.Center
    ) {
        // Button well
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gp.well)
                .border(0.8.dp, gp.shadow(0.25f), CircleShape)
        )

        // Button Cap (Flat Disk)
        Box(
            modifier = Modifier
                .size(47.dp)
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .gamepadButtonTouch(
                    onPress = { onPress(button.mappingId) },
                    onRelease = { onRelease(button.mappingId) },
                    onPressedStateChange = { isPressed = it }
                )
        ) {
            val baseColor = gp.capTop
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f

                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isPressed) 0.3f.dp.toPx() else 1.8f.dp.toPx()
                val faceOffset = if (isPressed) 0f else maxOffset

                // Calculate radii to fit perfectly within the clip boundaries
                val edgeR = r - edgeOffset / 2f - 0.5.dp.toPx()
                val faceR = r - maxOffset - 0.5.dp.toPx()

                // 3D side edge
                drawCircle(
                    color = gp.capEdge,
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face
                val faceColor = if (isPressed) gp.capPressedTop else baseColor
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceColor.gpLighter(0.08f), faceColor.gpDarker(0.12f))
                    ),
                    radius = faceR,
                    center = Offset(r, r - faceOffset)
                )
                
                // Bevel highlight (vertical gradient brush for better shading)
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(gp.highlight(0.35f), Color.Transparent)
                        } else {
                            listOf(gp.highlight, Color.Transparent)
                        }
                    ),
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
            
            // Developer logo overlay centered on top face
            val faceOffset = if (isPressed) 0.dp else 0.6.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = -faceOffset),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    tint = if (isPressed) gp.labelPressed else gp.accent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}



@Composable
private fun GamepadStickHoldButton(
    label: String,
    isHeld: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val gp = rememberControlSurfaceColors()
    val pressOffsetY by animateDpAsState(
        targetValue = if (isHeld) 0.6.dp else 0.dp,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "stickHoldPress"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isHeld) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = 65, easing = LinearEasing),
        label = "stickHoldScale"
    )

    // Well size 30.dp, Cap size 26.dp (Hole / Inset shadow effect)
    Box(
        modifier = modifier
            .size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Dark Button Well (Hole Inset)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(gp.well)
                .border(0.8.dp, gp.shadow(0.5f), CircleShape)
        )

        // 2. 3D Circular Button Cap
        Box(
            modifier = Modifier
                .size(26.dp)
                .offset(y = pressOffsetY)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(CircleShape)
                .clickable { onToggle(!isHeld) },
            contentAlignment = Alignment.Center
        ) {
            val baseColor = if (isHeld) gp.accentContainer else gp.capBottom
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f

                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isHeld) 0.2.dp.toPx() else 1.5.dp.toPx()
                val faceOffset = if (isHeld) 0f else maxOffset

                val edgeR = r - edgeOffset / 2f - 0.5.dp.toPx()
                val faceR = r - maxOffset - 0.5.dp.toPx()

                // 3D Side shadow edge
                drawCircle(
                    color = gp.capEdge,
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face gradient
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(baseColor.gpLighter(0.10f), baseColor.gpDarker(0.12f))
                    ),
                    radius = faceR,
                    center = Offset(r, r - faceOffset)
                )

                // Top bevel highlight / active border
                drawCircle(
                    color = if (isHeld) gp.accent else gp.highlight,
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = if (isHeld) 1.dp.toPx() else 0.8.dp.toPx())
                )
            }

            // Centered Text "L3" / "R3"
            Text(
                text = label,
                color = if (isHeld) gp.onAccentContainer else gp.label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GamepadAnalogStick(
    label: String,
    isClicked: () -> Boolean,
    modifier: Modifier = Modifier,
    isHeld: () -> Boolean = { false },
    onMove: (Float, Float) -> Unit,
    onStickClick: () -> Unit,
    onToggleHold: ((Boolean) -> Unit)? = null
) {
    val gp = rememberControlSurfaceColors()
    val clicked = isClicked()
    val held = isHeld()
    var stickOffsetX by remember { mutableFloatStateOf(0f) }
    var stickOffsetY by remember { mutableFloatStateOf(0f) }

    val currentIsHeld by rememberUpdatedState(held)
    val currentOnStickClick by rememberUpdatedState(onStickClick)
    val currentOnMove by rememberUpdatedState(onMove)

    val stickScale by animateFloatAsState(
        targetValue = if (clicked || held) 0.90f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "stickScale"
    )

    Box(
        modifier = modifier.size(108.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .pointerInput(Unit) {
                    val centerPx = size.width / 2f
                    val maxInputRadius = 32.dp.toPx()  // Wide linear touch response radius
                    val maxVisualRadius = 22.dp.toPx() // Clean visual cap travel boundary
                    val tapSlopPx = 8f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val downTime = System.currentTimeMillis()

                        val updateStickOffset = { pos: Offset ->
                            val dx = pos.x - centerPx
                            val dy = pos.y - centerPx
                            val dist = sqrt(dx * dx + dy * dy)

                            if (dist == 0f) {
                                stickOffsetX = 0f
                                stickOffsetY = 0f
                                currentOnMove(0f, 0f)
                            } else {
                                val normDist = (dist / maxInputRadius).coerceIn(0f, 1f)
                                val normalizedX = (dx / dist) * normDist
                                val normalizedY = (dy / dist) * normDist

                                val visualDist = dist.coerceAtMost(maxVisualRadius)
                                stickOffsetX = (dx / dist) * visualDist
                                stickOffsetY = (dy / dist) * visualDist

                                currentOnMove(normalizedX, normalizedY)
                            }
                        }

                        updateStickOffset(down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                            if (!change.pressed) {
                                val dragDistance = sqrt((change.position.x - down.position.x) * (change.position.x - down.position.x) + 
                                                        (change.position.y - down.position.y) * (change.position.y - down.position.y))

                                if (dragDistance < tapSlopPx && System.currentTimeMillis() - downTime < 250) {
                                    if (!currentIsHeld) {
                                        currentOnStickClick()
                                    }
                                }
                                stickOffsetX = 0f
                                stickOffsetY = 0f
                                currentOnMove(0f, 0f)
                                break
                            }

                            change.consume()
                            updateStickOffset(change.position)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
        // 1. Analog Stick Base Well (neumorphic molding & concentric rings)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width

            // Neumorphic outer lip molding (top-left highlight, bottom-right shadow)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(gp.capTop, gp.well),
                    start = Offset(0f, 0f),
                    end = Offset(w, w)
                ),
                radius = w / 2f + 3.dp.toPx()
            )
            drawCircle(
                color = gp.wellRim,
                radius = w / 2f + 3.dp.toPx(),
                style = Stroke(width = 0.8.dp.toPx())
            )
            
            // Recessed well background
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(gp.well, gp.capEdge)
                ),
                radius = w / 2f - 1.dp.toPx()
            )
            
            // Soft inner shadow ring inside the well for depth
            drawCircle(
                color = gp.shadow(0.45f),
                radius = w / 2f - 2.dp.toPx(),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // 2. Parallax 3D Shadow Layer (floats under the cap, shifts slightly less)
        Canvas(
            modifier = Modifier
                .offset { IntOffset((stickOffsetX * 0.6f).roundToInt(), (stickOffsetY * 0.6f).roundToInt()) }
                .size(82.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(gp.shadow(0.6f), gp.shadow(0.2f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width / 2f
                ),
                radius = size.width / 2f
            )
        }
            
        // 3. Analog Stick Thumb (rendered on top, NOT clipped, with 3D tilt & press)
        Box(
            modifier = Modifier
                .offset { IntOffset(stickOffsetX.roundToInt(), stickOffsetY.roundToInt()) }
                .size(76.dp) // Large thumb cap (narrow gap to well)
                .clip(CircleShape)
                .graphicsLayer {
                    // Quick press/sink scaling when clicked (no shape distortion when moving)
                    scaleX = stickScale
                    scaleY = stickScale
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val cx = w / 2f
                val cy = w / 2f
                val r = w / 2f
                
                // Outer base edge (very dark ring)
                drawCircle(
                    color = gp.wellRim,
                    radius = r
                )
                
                // Outer ring highlight/shadow
                drawCircle(
                    color = gp.highlight(0.55f),
                    radius = r - 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
                
                // Shifting central elements for 3D parallax deflection and press look
                val pressShift = if (clicked) 1.2.dp.toPx() else 0f
                val cupCx = cx + stickOffsetX * 0.12f
                val cupCy = cy + stickOffsetY * 0.12f + pressShift
                
                // Create paths for the knurled dome slope (between outer ring and inner cup)
                val pathOuter = androidx.compose.ui.graphics.Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(center = Offset(cx, cy), radius = r - 1.dp.toPx()))
                }
                val pathInner = androidx.compose.ui.graphics.Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(center = Offset(cupCx, cupCy), radius = r * 0.58f))
                }
                val ringPath = androidx.compose.ui.graphics.Path.combine(
                    androidx.compose.ui.graphics.PathOperation.Difference,
                    pathOuter,
                    pathInner
                )
                
                // Fill the dome slope with a soft vertical gradient for a flatter look
                drawPath(
                    path = ringPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(gp.capTop, gp.capBottom)
                    )
                )
                
                // Flatter recessed cup gradient (no radial concentration in center)
                val cupRadius = r * 0.58f
                val concaveBrush = Brush.verticalGradient(
                    colors = listOf(gp.capEdge, gp.capBottom)
                )
                
                // 3D Inner cup recessed background
                drawCircle(
                    brush = concaveBrush,
                    radius = cupRadius,
                    center = Offset(cupCx, cupCy)
                )
                
                // Bevel ring border (simple dark separator)
                drawCircle(
                    color = gp.wellRim,
                    radius = cupRadius,
                    center = Offset(cupCx, cupCy),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                
                // Bevel highlight (simple soft outline ring)
                drawCircle(
                    color = if (clicked) gp.accent else gp.highlight(0.6f),
                    radius = cupRadius - 0.6.dp.toPx(),
                    center = Offset(cupCx, cupCy),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }
        }
        }

        // 2. L3 / R3 Hold Toggle positioned at TOP-LEFT of joystick
        if (onToggleHold != null) {
            GamepadStickHoldButton(
                label = if (label == "L") "L3" else "R3",
                isHeld = held,
                onToggle = onToggleHold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-10).dp, y = (-10).dp)
            )
        }
    }
}

@Composable
private fun GamepadDpad(
    @Suppress("UNUSED_PARAMETER") isXboxStyle: Boolean,
    onDpadChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gp = rememberControlSurfaceColors()
    var activeDirection by remember { mutableIntStateOf(0) }
    val currentOnDpadChange by rememberUpdatedState(onDpadChange)
    
    // Physical tilt based on pressed direction (pure pivot rotation)
    var targetRotX = 0f
    var targetRotY = 0f
    
    if (activeDirection and 1 != 0) targetRotX = 12f  // UP: depresses top, raises bottom
    if (activeDirection and 2 != 0) targetRotX = -12f // DOWN: depresses bottom, raises top
    if (activeDirection and 4 != 0) targetRotY = -12f // LEFT: depresses left, raises right
    if (activeDirection and 8 != 0) targetRotY = 12f  // RIGHT: depresses right, raises left
    
    val rotX by animateFloatAsState(targetValue = targetRotX, animationSpec = spring(stiffness = Spring.StiffnessHigh))
    val rotY by animateFloatAsState(targetValue = targetRotY, animationSpec = spring(stiffness = Spring.StiffnessHigh))

    Box(
        modifier = modifier
            .size(114.dp) // Large D-pad size
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val targetPointerId = down.id
                    var currentBit = determineDpadBit(
                        down.position.x,
                        down.position.y,
                        size.width.toFloat(),
                        size.height.toFloat(),
                    )
                    activeDirection = currentBit
                    currentOnDpadChange(currentBit)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == targetPointerId } ?: break
                        if (!change.pressed) {
                            activeDirection = 0
                            currentOnDpadChange(0)
                            break
                        }
                        change.consume()
                        val newBit = determineDpadBit(
                            change.position.x,
                            change.position.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                        if (newBit != currentBit) {
                            currentBit = newBit
                            activeDirection = currentBit
                            currentOnDpadChange(currentBit)
                        }
                    }
                }
            }
    ) {
        // 1. Stationary Background Well (Casing hole remains static)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val barWWell = w * 0.36f
            val startOffWell = (w - barWWell) / 2f
            
            val path1 = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.02f,
                        top = startOffWell,
                        right = w * 0.98f,
                        bottom = startOffWell + barWWell,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                )
            }
            val path2 = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = startOffWell,
                        top = w * 0.02f,
                        right = startOffWell + barWWell,
                        bottom = w * 0.98f,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                )
            }
            val wellPath = androidx.compose.ui.graphics.Path.combine(
                androidx.compose.ui.graphics.PathOperation.Union,
                path1,
                path2
            )
            
            // Fill plus-shaped well
            drawPath(
                path = wellPath,
                color = gp.well
            )
            // Well border
            drawPath(
                path = wellPath,
                color = gp.shadow(0.5f),
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Inner shadow for depth
            drawPath(
                path = wellPath,
                color = gp.shadow(0.2f),
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // 2. Stationary Drop Shadow Layer (does NOT rotate in 3D, shifts dynamically opposite to tilt)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val barW = w * 0.28f
            val startOff = (w - barW) / 2f
            
            val cross1 = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = w * 0.06f,
                        top = startOff,
                        right = w * 0.94f,
                        bottom = startOff + barW,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                )
            }
            val cross2 = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = startOff,
                        top = w * 0.06f,
                        right = startOff + barW,
                        bottom = w * 0.94f,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                )
            }
            val crossPath = androidx.compose.ui.graphics.Path.combine(
                androidx.compose.ui.graphics.PathOperation.Union,
                cross1,
                cross2
            )
            
            // Dynamic D-pad drop shadow offset (shifts in opposite direction of tilt)
            var shadowOffsetX = 0f
            var shadowOffsetY = 1.5f.dp.toPx()
            
            if (activeDirection and 1 != 0) shadowOffsetY += 2f.dp.toPx()  // UP: shifts shadow down
            if (activeDirection and 2 != 0) shadowOffsetY -= 2f.dp.toPx()  // DOWN: shifts shadow up
            if (activeDirection and 4 != 0) shadowOffsetX += 2f.dp.toPx()  // LEFT: shifts shadow right
            if (activeDirection and 8 != 0) shadowOffsetX -= 2f.dp.toPx()  // RIGHT: shifts shadow left
            
            withTransform({
                translate(left = shadowOffsetX, top = shadowOffsetY)
            }) {
                drawPath(
                    path = crossPath,
                    color = gp.shadow(0.4f)
                )
            }
        }

        // 3. Animated D-pad Cross Body (Applying rotation to inner cross only)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = rotX
                    rotationY = rotY
                    cameraDistance = 8f * density
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val cx = w / 2f
                val cy = w / 2f
                
                val barW = w * 0.28f
                val startOff = (w - barW) / 2f
                
                val cross1 = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = w * 0.06f,
                            top = startOff,
                            right = w * 0.94f,
                            bottom = startOff + barW,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    )
                }
                val cross2 = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = startOff,
                            top = w * 0.06f,
                            right = startOff + barW,
                            bottom = w * 0.94f,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                        )
                    )
                }
                val crossPath = androidx.compose.ui.graphics.Path.combine(
                    androidx.compose.ui.graphics.PathOperation.Union,
                    cross1,
                    cross2
                )

                // Draw cross body (matte vertical gradient remains static/unpressed color on tap)
                val bodyBrush = Brush.verticalGradient(
                    colors = listOf(gp.capTop, gp.capBottom)
                )
                drawPath(
                    path = crossPath,
                    brush = bodyBrush
                )

                // Beveled highlight stroke remains static/unpressed on tap
                val highlightBrush = Brush.verticalGradient(
                    colors = listOf(gp.highlight, Color.Transparent)
                )
                drawPath(
                    path = crossPath,
                    brush = highlightBrush,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                // Outer dark border stroke remains static on tap
                drawPath(
                    path = crossPath,
                    color = gp.shadow(0.35f),
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Central depressed dish (concave look)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(gp.capEdge, gp.capBottom),
                        center = Offset(cx, cy),
                        radius = barW / 1.5f
                    ),
                    radius = barW / 1.8f
                )
                // Soft outline for dish
                drawCircle(
                    color = gp.highlight(0.3f),
                    radius = barW / 1.8f,
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // Elegant, thin directional markers/arrows (static subtle white/grey)
                val arrowColor = { directionBit: Int ->
                    if (activeDirection and directionBit != 0) gp.accent else gp.label.copy(alpha = 0.35f)
                }
                val arrowDist = w * 0.34f
                val arrowSize = w * 0.04f

                // Up Arrow
                val pathU = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy - arrowDist)
                    lineTo(cx - arrowSize, cy - arrowDist + arrowSize * 1.1f)
                    lineTo(cx + arrowSize, cy - arrowDist + arrowSize * 1.1f)
                    close()
                }
                drawPath(pathU, arrowColor(1))

                // Down Arrow
                val pathD = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cy + arrowDist)
                    lineTo(cx - arrowSize, cy + arrowDist - arrowSize * 1.1f)
                    lineTo(cx + arrowSize, cy + arrowDist - arrowSize * 1.1f)
                    close()
                }
                drawPath(pathD, arrowColor(2))

                // Left Arrow
                val pathL = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - arrowDist, cy)
                    lineTo(cx - arrowDist + arrowSize * 1.1f, cy - arrowSize)
                    lineTo(cx - arrowDist + arrowSize * 1.1f, cy + arrowSize)
                    close()
                }
                drawPath(pathL, arrowColor(4))

                // Right Arrow
                val pathR = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx + arrowDist, cy)
                    lineTo(cx + arrowDist - arrowSize * 1.1f, cy - arrowSize)
                    lineTo(cx + arrowDist - arrowSize * 1.1f, cy + arrowSize)
                    close()
                }
                drawPath(pathR, arrowColor(8))
            }
        }
    }
}

@Composable
private fun EditableComponentWrapper(
    isEditMode: Boolean,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    onOffsetChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onTransformEnd: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current.density
    
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentScale by rememberUpdatedState(scale)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnTransformEnd by rememberUpdatedState(onTransformEnd)
    
    var layoutTopInWindowPx by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                layoutTopInWindowPx = coordinates.positionInWindow().y
            }
            .offset {
                val layoutTopInWindow = layoutTopInWindowPx / density
                val constrainedOffsetY = if (layoutTopInWindow > 0) {
                    val minY = 38f + 4f - layoutTopInWindow
                    currentOffsetY.coerceAtLeast(minY)
                } else {
                    currentOffsetY
                }
                IntOffset((currentOffsetX * density).roundToInt(), (constrainedOffsetY * density).roundToInt())
            }
            .graphicsLayer {
                scaleX = currentScale
                scaleY = currentScale
            },
        contentAlignment = Alignment.Center
    ) {
        content()
        
        if (isEditMode) {
            // 1. Overlay container with border that intercepts gestures for moving and pinch zoom
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .border(
                        width = 1.2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                    .pointerInput(isEditMode) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var event: PointerEvent
                            do {
                                event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                            // 1. Update scale via pinch zoom
                            val newScale = (currentScale * zoom).coerceIn(0.6f, 1.8f)
                            currentOnScaleChange(newScale)
                            
                            // 2. Update offset with scale factor correction and topbar constraint
                            val minY = 38f + 4f - layoutTopInWindowPx / density
                            val newX = currentOffsetX + (pan.x * currentScale) / density
                            val newY = (currentOffsetY + (pan.y * currentScale) / density).coerceAtLeast(minY)
                            currentOnOffsetChange(newX, newY)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            currentOnTransformEnd()
                        }
                    }
            )
            
            // 2. Drag resize handle in bottom-right corner (Alternative scaling option)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = currentOnTransformEnd,
                            onDragCancel = currentOnTransformEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaScale = (dragAmount.x + dragAmount.y) / 150f
                                currentOnScaleChange((currentScale + deltaScale).coerceIn(0.6f, 1.8f))
                            },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
