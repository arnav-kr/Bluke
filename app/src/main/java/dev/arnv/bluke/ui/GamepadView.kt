package dev.arnv.bluke.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    val shareButton: ButtonDef = ButtonDef("SHARE", 17),
    val leftStickAboveDpad: Boolean = true,
    val hasTouchpad: Boolean = false,
    val touchpadMappingId: Int = -1
)

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
        guideButton = ButtonDef("XBOX", 16, Color(0xFF2E7D32)),
        shareButton = ButtonDef("SHARE", 17),
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
        guideButton = ButtonDef("PS", 16, Color(0xFF1565C0)),
        shareButton = ButtonDef("SHARE", 17),
        leftStickAboveDpad = false,
        hasTouchpad = true,
        touchpadMappingId = 18
    )
)

// ── Main View ──

@SuppressLint("MissingPermission")
@Composable
fun GamepadView(
    btManager: BluetoothKeyboardManager,
    onClose: () -> Unit,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    sharedPrefs: SharedPreferences,
    @Suppress("UNUSED_PARAMETER") caseBrush: Brush
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val config = CONSOLES[selectedIndex]

    var isEditMode by rememberSaveable { mutableStateOf(false) }

    var isVibrationEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("gamepad_vibration_enabled", true))
    }

    val triggerVibration = { milliseconds: Long ->
        if (isVibrationEnabled) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    val saveLayoutPref = { key: String, value: Float ->
        sharedPrefs.edit { putFloat(key, value) }
    }

    // Positions & Scales loaded dynamically per console layout (Xbox Series / PlayStation 5)
    var dpadOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_dpad_x", if (config.id == "playstation_5") -24f else 32f)) }
    var dpadOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_dpad_y", if (config.id == "playstation_5") 0f else 20f)) }
    var dpadScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_dpad_scale", 1f)) }

    var leftStickOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_stick_x", if (config.id == "playstation_5") 45f else 0f)) }
    var leftStickOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_stick_y", if (config.id == "playstation_5") 20f else 0f)) }
    var leftStickScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_stick_scale", 1f)) }

    var rightStickOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_stick_x", -45f)) }
    var rightStickOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_stick_y", 20f)) }
    var rightStickScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_stick_scale", 1f)) }

    var faceButtonsOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_face_buttons_x", 0f)) }
    var faceButtonsOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_face_buttons_y", 0f)) }
    var faceButtonsScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_face_buttons_scale", 1f)) }

    var leftTriggerOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_trigger_x", 0f)) }
    var leftTriggerOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_trigger_y", 0f)) }
    var leftTriggerScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_trigger_scale", 1f)) }

    var leftBumperOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_bumper_x", 0f)) }
    var leftBumperOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_bumper_y", 0f)) }
    var leftBumperScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_left_bumper_scale", 1f)) }

    var rightTriggerOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_trigger_x", 0f)) }
    var rightTriggerOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_trigger_y", 0f)) }
    var rightTriggerScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_trigger_scale", 1f)) }

    var rightBumperOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_bumper_x", 0f)) }
    var rightBumperOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_bumper_y", 0f)) }
    var rightBumperScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_right_bumper_scale", 1f)) }

    var guideOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_guide_x", 0f)) }
    var guideOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_guide_y", 0f)) }
    var guideScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_guide_scale", 1f)) }

    var selectOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_select_x", 0f)) }
    var selectOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_select_y", 0f)) }
    var selectScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_select_scale", 1f)) }

    var shareOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_share_x", 0f)) }
    var shareOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_share_y", 0f)) }
    var shareScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_share_scale", 1f)) }

    var startOffsetX by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_start_x", 0f)) }
    var startOffsetY by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_start_y", 0f)) }
    var startScale by remember(config.id) { mutableFloatStateOf(sharedPrefs.getFloat("${config.id}_start_scale", 1f)) }

    val isModified = remember(
        config.id,
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
        val defaultDpadX = if (config.id == "playstation_5") -24f else 32f
        val defaultDpadY = if (config.id == "playstation_5") 0f else 20f
        val defaultLeftX = if (config.id == "playstation_5") 45f else 0f
        val defaultLeftY = if (config.id == "playstation_5") 20f else 0f
        val defaultRightX = -45f
        val defaultRightY = 20f

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
        val defaultDpadX = if (config.id == "playstation_5") -24f else 32f
        val defaultDpadY = if (config.id == "playstation_5") 0f else 20f
        val defaultLeftX = if (config.id == "playstation_5") 45f else 0f
        val defaultLeftY = if (config.id == "playstation_5") 20f else 0f
        val defaultRightX = -45f
        val defaultRightY = 20f
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
            remove("${config.id}_dpad_x")
            remove("${config.id}_dpad_y")
            remove("${config.id}_dpad_scale")
            remove("${config.id}_left_stick_x")
            remove("${config.id}_left_stick_y")
            remove("${config.id}_left_stick_scale")
            remove("${config.id}_right_stick_x")
            remove("${config.id}_right_stick_y")
            remove("${config.id}_right_stick_scale")
            remove("${config.id}_face_buttons_x")
            remove("${config.id}_face_buttons_y")
            remove("${config.id}_face_buttons_scale")
            remove("${config.id}_left_trigger_x")
            remove("${config.id}_left_trigger_y")
            remove("${config.id}_left_trigger_scale")
            remove("${config.id}_left_bumper_x")
            remove("${config.id}_left_bumper_y")
            remove("${config.id}_left_bumper_scale")
            remove("${config.id}_right_trigger_x")
            remove("${config.id}_right_trigger_y")
            remove("${config.id}_right_trigger_scale")
            remove("${config.id}_right_bumper_x")
            remove("${config.id}_right_bumper_y")
            remove("${config.id}_right_bumper_scale")
            remove("${config.id}_guide_x")
            remove("${config.id}_guide_y")
            remove("${config.id}_guide_scale")
            remove("${config.id}_select_x")
            remove("${config.id}_select_y")
            remove("${config.id}_select_scale")
            remove("${config.id}_share_x")
            remove("${config.id}_share_y")
            remove("${config.id}_share_scale")
            remove("${config.id}_start_x")
            remove("${config.id}_start_y")
            remove("${config.id}_start_scale")
        }
            
        triggerVibration(50)
    }

    val connectedDevNow by btManager.connectedDevice.collectAsState()
    val isConnected = connectedDevNow != null
    val deviceName = connectedDevNow?.name ?: "No Host"

    var buttonMask by remember { mutableIntStateOf(0) }
    var lastGamepadReportTime by remember { mutableLongStateOf(0L) }
    var isGamepadDirty by remember { mutableStateOf(false) }
    
    var leftStickX by remember { mutableFloatStateOf(0f) }
    var leftStickY by remember { mutableFloatStateOf(0f) }
    var rightStickX by remember { mutableFloatStateOf(0f) }
    var rightStickY by remember { mutableFloatStateOf(0f) }

    val transmitGamepadState = { force: Boolean ->
        val now = System.currentTimeMillis()
        if (force || now - lastGamepadReportTime >= 8L) {
            btManager.sendGamepadReport(
                buttonMask,
                leftStickX,
                leftStickY,
                rightStickX,
                rightStickY
            )
            lastGamepadReportTime = now
            isGamepadDirty = false
        } else {
            isGamepadDirty = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10L.milliseconds)
            if (isGamepadDirty) {
                btManager.sendGamepadReport(
                    buttonMask,
                    leftStickX,
                    leftStickY,
                    rightStickX,
                    rightStickY
                )
                lastGamepadReportTime = System.currentTimeMillis()
                isGamepadDirty = false
            }
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
            .background(Color(0xFF28282A))
            .navigationBarsPadding()
            .testTag("gamepad_view_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Settings Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Close
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onClose() }
                            .padding(horizontal = 8.dp)
                            .testTag("exit_gamepad_btn"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, "Exit", tint = Color.White, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Close", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Mode cycle
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
                            .testTag("gamepad_mode_cycle_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.SportsEsports, "Switch Mode", tint = Color.White, modifier = Modifier.size(11.dp))
                        Text("Gamepad Mode", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Connection status
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (isConnected) Color(0xFF39FF14) else Color(0xFFFF9800)))
                    Text(deviceName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    Text(if (isConnected) "[connected]" else "[offline]", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontFamily = FontFamily.SansSerif)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset Defaults (only shown when edit mode is active and there are layout changes)
                    if (isEditMode && isModified) {
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.35f))
                                .clickable { resetDefaults() }
                                .padding(horizontal = 8.dp)
                                .testTag("reset_layout_defaults_btn"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Reset Defaults", tint = Color.White, modifier = Modifier.size(11.dp))
                            Text("Reset Defaults", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Edit Layout toggle
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                isEditMode = !isEditMode
                                triggerVibration(30)
                            }
                            .padding(horizontal = 8.dp)
                            .testTag("edit_layout_toggle_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Edit Layout",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isEditMode) "Done" else "Edit Layout",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Console layout selector
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                selectedIndex = (selectedIndex + 1) % CONSOLES.size
                                triggerVibration(15)
                            }
                            .padding(horizontal = 8.dp)
                            .testTag("console_selector_pill"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SportsEsports, "Consoles", tint = Color.White, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(config.name, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Vibration toggle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                val active = !isVibrationEnabled
                                isVibrationEnabled = active
                                sharedPrefs.edit { putBoolean("gamepad_vibration_enabled", active) }
                                if (active) triggerVibration(50)
                            }
                            .testTag("vibration_gamepad_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVibrationEnabled) {
                            Icon(Icons.Default.Vibration, "Haptics", tint = Color.White, modifier = Modifier.size(12.dp))
                        } else {
                            Icon(painterResource(R.drawable.ic_vibration_off), "Haptics", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // ── Main Controls Body ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                if (config.leftStickAboveDpad) {
                    // ── XBOX-STYLE OFFSET LAYOUT ──
                    // Left column: Left Stick (top), Dpad (bottom)
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(38.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = leftStickOffsetX,
                            offsetY = leftStickOffsetY,
                            scale = leftStickScale,
                            onOffsetChange = { x, y -> leftStickOffsetX = x; leftStickOffsetY = y; saveLayoutPref("${config.id}_left_stick_x", x); saveLayoutPref("${config.id}_left_stick_y", y) },
                            onScaleChange = { s -> leftStickScale = s; saveLayoutPref("${config.id}_left_stick_scale", s) }
                        ) {
                            GamepadAnalogStick(
                                label = "L",
                                isClicked = (buttonMask and (1 shl 10)) != 0,
                                isHeld = (buttonMask and (1 shl 10)) != 0,
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
                            onOffsetChange = { x, y -> dpadOffsetX = x; dpadOffsetY = y; saveLayoutPref("${config.id}_dpad_x", x); saveLayoutPref("${config.id}_dpad_y", y) },
                            onScaleChange = { s -> dpadScale = s; saveLayoutPref("${config.id}_dpad_scale", s) }
                        ) {
                            GamepadDpad(
                                isXboxStyle = true,
                                onDpadChange = { mask ->
                                    val cleared = buttonMask and (0xF shl 12).inv()
                                    buttonMask = cleared or (mask shl 12)
                                    transmitGamepadState(true)
                                    if (mask != 0) triggerVibration(15)
                                }
                            )
                        }
                    }

                    // Center column: Logo, Center Buttons, Triggers/Bumpers
                    Column(
                        modifier = Modifier.weight(0.4f).fillMaxHeight(),
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
                                    onOffsetChange = { x, y -> leftTriggerOffsetX = x; leftTriggerOffsetY = y; saveLayoutPref("${config.id}_left_trigger_x", x); saveLayoutPref("${config.id}_left_trigger_y", y) },
                                    onScaleChange = { s -> leftTriggerScale = s; saveLayoutPref("${config.id}_left_trigger_scale", s) }
                                ) {
                                    GamepadTriggerButton(config.leftTrigger, true, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = leftBumperOffsetX,
                                    offsetY = leftBumperOffsetY,
                                    scale = leftBumperScale,
                                    onOffsetChange = { x, y -> leftBumperOffsetX = x; leftBumperOffsetY = y; saveLayoutPref("${config.id}_left_bumper_x", x); saveLayoutPref("${config.id}_left_bumper_y", y) },
                                    onScaleChange = { s -> leftBumperScale = s; saveLayoutPref("${config.id}_left_bumper_scale", s) }
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
                                    onOffsetChange = { x, y -> rightTriggerOffsetX = x; rightTriggerOffsetY = y; saveLayoutPref("${config.id}_right_trigger_x", x); saveLayoutPref("${config.id}_right_trigger_y", y) },
                                    onScaleChange = { s -> rightTriggerScale = s; saveLayoutPref("${config.id}_right_trigger_scale", s) }
                                ) {
                                    GamepadTriggerButton(config.rightTrigger, false, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = rightBumperOffsetX,
                                    offsetY = rightBumperOffsetY,
                                    scale = rightBumperScale,
                                    onOffsetChange = { x, y -> rightBumperOffsetX = x; rightBumperOffsetY = y; saveLayoutPref("${config.id}_right_bumper_x", x); saveLayoutPref("${config.id}_right_bumper_y", y) },
                                    onScaleChange = { s -> rightBumperScale = s; saveLayoutPref("${config.id}_right_bumper_scale", s) }
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
                            onOffsetChange = { x, y -> guideOffsetX = x; guideOffsetY = y; saveLayoutPref("${config.id}_guide_x", x); saveLayoutPref("${config.id}_guide_y", y) },
                            onScaleChange = { s -> guideScale = s; saveLayoutPref("${config.id}_guide_scale", s) }
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
                                onOffsetChange = { x, y -> selectOffsetX = x; selectOffsetY = y; saveLayoutPref("${config.id}_select_x", x); saveLayoutPref("${config.id}_select_y", y) },
                                onScaleChange = { s -> selectScale = s; saveLayoutPref("${config.id}_select_scale", s) }
                            ) {
                                GamepadCenterButton(config.selectButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = shareOffsetX,
                                offsetY = shareOffsetY,
                                scale = shareScale,
                                onOffsetChange = { x, y -> shareOffsetX = x; shareOffsetY = y; saveLayoutPref("${config.id}_share_x", x); saveLayoutPref("${config.id}_share_y", y) },
                                onScaleChange = { s -> shareScale = s; saveLayoutPref("${config.id}_share_scale", s) }
                            ) {
                                GamepadCenterButton(config.shareButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = startOffsetX,
                                offsetY = startOffsetY,
                                scale = startScale,
                                onOffsetChange = { x, y -> startOffsetX = x; startOffsetY = y; saveLayoutPref("${config.id}_start_x", x); saveLayoutPref("${config.id}_start_y", y) },
                                onScaleChange = { s -> startScale = s; saveLayoutPref("${config.id}_start_scale", s) }
                            ) {
                                GamepadCenterButton(config.startButton, pressButton, releaseButton)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    // Right column: Face Buttons (top), Right Stick (bottom)
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(16.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = faceButtonsOffsetX,
                            offsetY = faceButtonsOffsetY,
                            scale = faceButtonsScale,
                            onOffsetChange = { x, y -> faceButtonsOffsetX = x; faceButtonsOffsetY = y; saveLayoutPref("${config.id}_face_buttons_x", x); saveLayoutPref("${config.id}_face_buttons_y", y) },
                            onScaleChange = { s -> faceButtonsScale = s; saveLayoutPref("${config.id}_face_buttons_scale", s) }
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
                            onOffsetChange = { x, y -> rightStickOffsetX = x; rightStickOffsetY = y; saveLayoutPref("${config.id}_right_stick_x", x); saveLayoutPref("${config.id}_right_stick_y", y) },
                            onScaleChange = { s -> rightStickScale = s; saveLayoutPref("${config.id}_right_stick_scale", s) }
                        ) {
                            GamepadAnalogStick(
                                label = "R",
                                isClicked = (buttonMask and (1 shl 11)) != 0,
                                isHeld = (buttonMask and (1 shl 11)) != 0,
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
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(26.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = dpadOffsetX,
                            offsetY = dpadOffsetY,
                            scale = dpadScale,
                            onOffsetChange = { x, y -> dpadOffsetX = x; dpadOffsetY = y; saveLayoutPref("${config.id}_dpad_x", x); saveLayoutPref("${config.id}_dpad_y", y) },
                            onScaleChange = { s -> dpadScale = s; saveLayoutPref("${config.id}_dpad_scale", s) }
                        ) {
                            GamepadDpad(
                                isXboxStyle = false,
                                onDpadChange = { mask ->
                                    val cleared = buttonMask and (0xF shl 12).inv()
                                    buttonMask = cleared or (mask shl 12)
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
                            onOffsetChange = { x, y -> leftStickOffsetX = x; leftStickOffsetY = y; saveLayoutPref("${config.id}_left_stick_x", x); saveLayoutPref("${config.id}_left_stick_y", y) },
                            onScaleChange = { s -> leftStickScale = s; saveLayoutPref("${config.id}_left_stick_scale", s) }
                        ) {
                            GamepadAnalogStick(
                                label = "L",
                                isClicked = (buttonMask and (1 shl 10)) != 0,
                                isHeld = (buttonMask and (1 shl 10)) != 0,
                                onMove = { x, y -> leftStickX = x; leftStickY = y; transmitGamepadState(false) },
                                onStickClick = { scope.launch { pressButton(10); delay(100L.milliseconds); releaseButton(10) } },
                                onToggleHold = { hold -> if (hold) pressButton(10) else releaseButton(10) }
                            )
                        }
                    }

                    // Center Column: PS Button + L1/L2 R1/R2, Right Stick/Left Stick spacing
                    Column(
                        modifier = Modifier.weight(0.4f).fillMaxHeight(),
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
                                    onOffsetChange = { x, y -> leftTriggerOffsetX = x; leftTriggerOffsetY = y; saveLayoutPref("${config.id}_left_trigger_x", x); saveLayoutPref("${config.id}_left_trigger_y", y) },
                                    onScaleChange = { s -> leftTriggerScale = s; saveLayoutPref("${config.id}_left_trigger_scale", s) }
                                ) {
                                    GamepadTriggerButton(config.leftTrigger, true, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = leftBumperOffsetX,
                                    offsetY = leftBumperOffsetY,
                                    scale = leftBumperScale,
                                    onOffsetChange = { x, y -> leftBumperOffsetX = x; leftBumperOffsetY = y; saveLayoutPref("${config.id}_left_bumper_x", x); saveLayoutPref("${config.id}_left_bumper_y", y) },
                                    onScaleChange = { s -> leftBumperScale = s; saveLayoutPref("${config.id}_left_bumper_scale", s) }
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
                                    onOffsetChange = { x, y -> rightTriggerOffsetX = x; rightTriggerOffsetY = y; saveLayoutPref("${config.id}_right_trigger_x", x); saveLayoutPref("${config.id}_right_trigger_y", y) },
                                    onScaleChange = { s -> rightTriggerScale = s; saveLayoutPref("${config.id}_right_trigger_scale", s) }
                                ) {
                                    GamepadTriggerButton(config.rightTrigger, false, pressButton, releaseButton)
                                }
                                Spacer(Modifier.height(4.dp))
                                EditableComponentWrapper(
                                    isEditMode = isEditMode,
                                    offsetX = rightBumperOffsetX,
                                    offsetY = rightBumperOffsetY,
                                    scale = rightBumperScale,
                                    onOffsetChange = { x, y -> rightBumperOffsetX = x; rightBumperOffsetY = y; saveLayoutPref("${config.id}_right_bumper_x", x); saveLayoutPref("${config.id}_right_bumper_y", y) },
                                    onScaleChange = { s -> rightBumperScale = s; saveLayoutPref("${config.id}_right_bumper_scale", s) }
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
                            onOffsetChange = { x, y -> guideOffsetX = x; guideOffsetY = y; saveLayoutPref("${config.id}_guide_x", x); saveLayoutPref("${config.id}_guide_y", y) },
                            onScaleChange = { s -> guideScale = s; saveLayoutPref("${config.id}_guide_scale", s) }
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
                                onOffsetChange = { x, y -> selectOffsetX = x; selectOffsetY = y; saveLayoutPref("${config.id}_select_x", x); saveLayoutPref("${config.id}_select_y", y) },
                                onScaleChange = { s -> selectScale = s; saveLayoutPref("${config.id}_select_scale", s) }
                            ) {
                                GamepadCenterButton(config.selectButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = shareOffsetX,
                                offsetY = shareOffsetY,
                                scale = shareScale,
                                onOffsetChange = { x, y -> shareOffsetX = x; shareOffsetY = y; saveLayoutPref("${config.id}_share_x", x); saveLayoutPref("${config.id}_share_y", y) },
                                onScaleChange = { s -> shareScale = s; saveLayoutPref("${config.id}_share_scale", s) }
                            ) {
                                GamepadCenterButton(config.shareButton, pressButton, releaseButton)
                            }
                            EditableComponentWrapper(
                                isEditMode = isEditMode,
                                offsetX = startOffsetX,
                                offsetY = startOffsetY,
                                scale = startScale,
                                onOffsetChange = { x, y -> startOffsetX = x; startOffsetY = y; saveLayoutPref("${config.id}_start_x", x); saveLayoutPref("${config.id}_start_y", y) },
                                onScaleChange = { s -> startScale = s; saveLayoutPref("${config.id}_start_scale", s) }
                            ) {
                                GamepadCenterButton(config.startButton, pressButton, releaseButton)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    // Right Column: Face Buttons (top), Right Stick (bottom)
                    Column(
                        modifier = Modifier.weight(0.3f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(Modifier.height(16.dp))
                        EditableComponentWrapper(
                            isEditMode = isEditMode,
                            offsetX = faceButtonsOffsetX,
                            offsetY = faceButtonsOffsetY,
                            scale = faceButtonsScale,
                            onOffsetChange = { x, y -> faceButtonsOffsetX = x; faceButtonsOffsetY = y; saveLayoutPref("${config.id}_face_buttons_x", x); saveLayoutPref("${config.id}_face_buttons_y", y) },
                            onScaleChange = { s -> faceButtonsScale = s; saveLayoutPref("${config.id}_face_buttons_scale", s) }
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
                            onOffsetChange = { x, y -> rightStickOffsetX = x; rightStickOffsetY = y; saveLayoutPref("${config.id}_right_stick_x", x); saveLayoutPref("${config.id}_right_stick_y", y) },
                            onScaleChange = { s -> rightStickScale = s; saveLayoutPref("${config.id}_right_stick_scale", s) }
                        ) {
                            GamepadAnalogStick(
                                label = "R",
                                isClicked = (buttonMask and (1 shl 11)) != 0,
                                isHeld = (buttonMask and (1 shl 11)) != 0,
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

fun Modifier.gamepadButtonTouch(
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onPressedStateChange: (Boolean) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val targetPointerId = down.id
        onPressedStateChange(true)
        onPress()
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == targetPointerId } ?: break
            if (!change.pressed) {
                onPressedStateChange(false)
                onRelease()
                break
            }
            change.consume()
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
            .background(Color(0xFF1B1B1C), outerShape)
            .border(1.2.dp, Color.Black.copy(alpha = 0.5f), outerShape),
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
                            listOf(Color(0xFF202022), Color(0xFF19191B))
                        } else {
                            listOf(Color(0xFF38383B), Color(0xFF2D2D30))
                        }
                    )
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
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
                color = Color.White.copy(alpha = 0.85f),
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
            .background(Color(0xFF1B1B1C), outerShape)
            .border(1.2.dp, Color.Black.copy(alpha = 0.5f), outerShape),
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
                            listOf(Color(0xFF202022), Color(0xFF19191B))
                        } else {
                            listOf(Color(0xFF38383B), Color(0xFF2D2D30))
                        }
                    )
                )
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
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
                color = Color.White.copy(alpha = 0.85f),
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
    val baseColor = Color(0xFF333336)
    
    // Label/Symbol color (A, B, X, Y or △, ◯, ✕, ☐)
    val labelColor = button.color

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
                .background(Color(0xFF1B1B1C))
                .border(0.8.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
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
                    color = Color(0xFF1F1F21),
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face of the disk (themed to the background, soft vertical gradient)
                val faceColor = if (isPressed) Color(0xFF232325) else baseColor
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(faceColor.gpLighter(0.08f), faceColor.gpDarker(0.12f))
                    ),
                    radius = faceR,
                    center = Offset(r, r - faceOffset)
                )

                // Flat face bevel edge highlight
                drawCircle(
                    color = Color.White.copy(alpha = if (isPressed) 0.05f else 0.15f),
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // Bezel shadow
                drawCircle(
                    color = Color.Black.copy(alpha = if (isPressed) 0.10f else 0.25f),
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
    var isPressed by remember { mutableStateOf(false) }
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
                .background(Color(0xFF1B1B1C))
                .border(0.8.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
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
            val baseColor = Color(0xFF333336)
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
                    color = Color(0xFF1F1F21),
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face
                val faceColor = if (isPressed) Color(0xFF232325) else baseColor
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
                            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                        } else {
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
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
                            color = Color.White.copy(alpha = 0.75f),
                            topLeft = Offset(w * 0.25f, 0f),
                            size = androidx.compose.ui.geometry.Size(w * 0.75f, h * 0.75f),
                            style = Stroke(strokeW)
                        )
                        // Front window
                        drawRect(
                            color = Color.White.copy(alpha = 0.75f),
                            topLeft = Offset(0f, h * 0.25f),
                            size = androidx.compose.ui.geometry.Size(w * 0.75f, h * 0.75f),
                            style = Stroke(strokeW)
                        )
                    }
                } else if (button.label == "SHARE") {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.75f),
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
                                    .background(Color.White.copy(alpha = 0.75f))
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
    var isPressed by remember { mutableStateOf(false) }
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
                .background(Color(0xFF1B1B1C), outerShape)
                .border(1.2.dp, Color.Black.copy(alpha = 0.5f), outerShape)
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
            val baseColor = Color(0xFF333336)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isPressed) 0.3f.dp.toPx() else 1.5f.dp.toPx()
                val faceOffset = if (isPressed) maxOffset else 0f

                // 3D side edge
                drawRoundRect(
                    color = Color(0xFF1F1F21),
                    topLeft = Offset(0f, edgeOffset),
                    size = androidx.compose.ui.geometry.Size(w, h - edgeOffset),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Top face
                val faceColor = if (isPressed) Color(0xFF232325) else baseColor
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
                    color = Color.White.copy(alpha = if (isPressed) 0.05f else 0.15f),
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
                    tint = Color.White.copy(alpha = 0.75f),
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
                .background(Color(0xFF1B1B1C))
                .border(0.8.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
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
            val baseColor = Color(0xFF333336)
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
                    color = Color(0xFF1F1F21),
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face
                val faceColor = if (isPressed) Color(0xFF232325) else baseColor
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
                            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                        } else {
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
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
                    tint = Color.White.copy(alpha = 0.85f),
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
                .background(Color(0xFF1B1B1C))
                .border(0.8.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
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
            val baseColor = Color(0xFF333336)
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
                    color = Color(0xFF1F1F21),
                    radius = edgeR,
                    center = Offset(r, r + edgeOffset / 2f)
                )

                // Top face
                val faceColor = if (isPressed) Color(0xFF232325) else baseColor
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
                            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                        } else {
                            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
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
                    tint = Color.White.copy(alpha = 0.85f),
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
                .background(Color(0xFF171718))
                .border(0.8.dp, Color.Black.copy(alpha = 0.5f), CircleShape)
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
            val baseColor = if (isHeld) Color(0xFF3A3A3E) else Color(0xFF28282B)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.width / 2f

                val maxOffset = 0.6.dp.toPx()
                val edgeOffset = if (isHeld) 0.2.dp.toPx() else 1.5.dp.toPx()
                val faceOffset = if (isHeld) 0f else maxOffset

                val edgeR = r - edgeOffset / 2f - 0.5.dp.toPx()
                val faceR = r - maxOffset - 0.5.dp.toPx()

                // 3D Side shadow edge
                drawCircle(
                    color = Color(0xFF161618),
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
                    color = if (isHeld) Color(0xFFE5E5EA) else Color.White.copy(alpha = 0.15f),
                    radius = faceR - 0.5.dp.toPx(),
                    center = Offset(r, r - faceOffset),
                    style = Stroke(width = if (isHeld) 1.dp.toPx() else 0.8.dp.toPx())
                )
            }

            // Centered Text "L3" / "R3"
            Text(
                text = label,
                color = if (isHeld) Color(0xFFFFFFFF) else Color.White.copy(alpha = 0.70f),
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
    isClicked: Boolean,
    modifier: Modifier = Modifier,
    isHeld: Boolean = false,
    onMove: (Float, Float) -> Unit,
    onStickClick: () -> Unit,
    onToggleHold: ((Boolean) -> Unit)? = null
) {
    var stickOffsetX by remember { mutableFloatStateOf(0f) }
    var stickOffsetY by remember { mutableFloatStateOf(0f) }
    var isTouchActive by remember { mutableStateOf(false) }

    val currentIsHeld by rememberUpdatedState(isHeld)
    val currentOnStickClick by rememberUpdatedState(onStickClick)
    val currentOnMove by rememberUpdatedState(onMove)

    val stickScale by animateFloatAsState(
        targetValue = if (isClicked || isHeld) 0.90f else 1.0f,
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
                        isTouchActive = true
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
                                isTouchActive = false
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
                    colors = listOf(Color(0xFF3B3B3E), Color(0xFF1B1B1C)),
                    start = Offset(0f, 0f),
                    end = Offset(w, w)
                ),
                radius = w / 2f + 3.dp.toPx()
            )
            drawCircle(
                color = Color(0xFF121213),
                radius = w / 2f + 3.dp.toPx(),
                style = Stroke(width = 0.8.dp.toPx())
            )
            
            // Recessed well background
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF141416), Color(0xFF222224))
                ),
                radius = w / 2f - 1.dp.toPx()
            )
            
            // Soft inner shadow ring inside the well for depth
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
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
                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.2f), Color.Transparent),
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
                    color = Color(0xFF161719),
                    radius = r
                )
                
                // Outer ring highlight/shadow
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = r - 0.5.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx())
                )
                
                // Shifting central elements for 3D parallax deflection and press look
                val pressShift = if (isClicked) 1.2.dp.toPx() else 0f
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
                        colors = listOf(Color(0xFF38383B), Color(0xFF232325))
                    )
                )
                
                // Flatter recessed cup gradient (no radial concentration in center)
                val cupRadius = r * 0.58f
                val concaveBrush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1B1C1E), Color(0xFF292A2E))
                )
                
                // 3D Inner cup recessed background
                drawCircle(
                    brush = concaveBrush,
                    radius = cupRadius,
                    center = Offset(cupCx, cupCy)
                )
                
                // Bevel ring border (simple dark separator)
                drawCircle(
                    color = Color(0xFF141517),
                    radius = cupRadius,
                    center = Offset(cupCx, cupCy),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                
                // Bevel highlight (simple soft outline ring)
                drawCircle(
                    color = Color.White.copy(alpha = 0.09f),
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
                isHeld = isHeld,
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
    var activeDirection by remember { mutableIntStateOf(0) }
    
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
                    var currentBit = determineDpadBit(down.position.x, down.position.y, size.width.toFloat())
                    activeDirection = currentBit
                    onDpadChange(currentBit)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            activeDirection = 0
                            onDpadChange(0)
                            break
                        }
                        change.consume()
                        val newBit = determineDpadBit(change.position.x, change.position.y, size.width.toFloat())
                        if (newBit != currentBit) {
                            currentBit = newBit
                            activeDirection = currentBit
                            onDpadChange(currentBit)
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
                color = Color(0xFF1B1B1C)
            )
            // Well border
            drawPath(
                path = wellPath,
                color = Color.Black.copy(alpha = 0.5f),
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Inner shadow for depth
            drawPath(
                path = wellPath,
                color = Color.Black.copy(alpha = 0.2f),
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
                    color = Color.Black.copy(alpha = 0.4f)
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
                    colors = listOf(Color(0xFF38383B), Color(0xFF2D2D30))
                )
                drawPath(
                    path = crossPath,
                    brush = bodyBrush
                )

                // Beveled highlight stroke remains static/unpressed on tap
                val highlightBrush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                )
                drawPath(
                    path = crossPath,
                    brush = highlightBrush,
                    style = Stroke(width = 0.8.dp.toPx())
                )
                // Outer dark border stroke remains static on tap
                drawPath(
                    path = crossPath,
                    color = Color.Black.copy(alpha = 0.35f),
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Central depressed dish (concave look)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1B1B1D), Color(0xFF2C2C30)),
                        center = Offset(cx, cy),
                        radius = barW / 1.5f
                    ),
                    radius = barW / 1.8f
                )
                // Soft outline for dish
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = barW / 1.8f,
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // Elegant, thin directional markers/arrows (static subtle white/grey)
                val arrowColor = { directionBit: Int ->
                    if (activeDirection and directionBit != 0) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.15f)
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

private fun determineDpadBit(x: Float, y: Float, totalSize: Float): Int {
    val center = totalSize / 2f
    val dx = x - center
    val dy = y - center
    val distance = sqrt(dx * dx + dy * dy)
    if (distance < totalSize * 0.08f) return 0

    var mask = 0
    // Threshold distance for diagonal combined directions
    val sectorThreshold = distance * 0.38f

    if (dy < -sectorThreshold) mask = mask or 1 // UP
    if (dy > sectorThreshold) mask = mask or 2  // DOWN
    if (dx < -sectorThreshold) mask = mask or 4 // LEFT
    if (dx > sectorThreshold) mask = mask or 8  // RIGHT

    if (mask == 0) {
        mask = if (abs(dx) > abs(dy)) {
            if (dx > 0) 8 else 4
        } else {
            if (dy > 0) 2 else 1
        }
    }
    return mask
}

@Composable
private fun EditableComponentWrapper(
    isEditMode: Boolean,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    onOffsetChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current.density
    
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentScale by rememberUpdatedState(scale)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    
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
                    offsetY.coerceAtLeast(minY)
                } else {
                    offsetY
                }
                IntOffset((offsetX * density).roundToInt(), (constrainedOffsetY * density).roundToInt())
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
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
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // 1. Update scale via pinch zoom
                            val newScale = (currentScale * zoom).coerceIn(0.6f, 1.8f)
                            currentOnScaleChange(newScale)
                            
                            // 2. Update offset with scale factor correction and topbar constraint
                            val minY = 38f + 4f - layoutTopInWindowPx / density
                            val newX = currentOffsetX + (pan.x * currentScale) / density
                            val newY = (currentOffsetY + (pan.y * currentScale) / density).coerceAtLeast(minY)
                            currentOnOffsetChange(newX, newY)
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
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaScale = (dragAmount.x + dragAmount.y) / 150f
                            currentOnScaleChange((currentScale + deltaScale).coerceIn(0.6f, 1.8f))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
