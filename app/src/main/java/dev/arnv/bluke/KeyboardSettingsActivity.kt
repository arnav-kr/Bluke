package dev.arnv.bluke

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.ui.HARDWARE_KEYBOARD_MODE_PREFERENCE
import dev.arnv.bluke.ui.HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE
import dev.arnv.bluke.ui.HardwareKeyboardMode
import dev.arnv.bluke.ui.hardwareKeyboardMode
import dev.arnv.bluke.ui.hasPhysicalKeyboard
import dev.arnv.bluke.ui.isHardwareKeyboardTrackpadEnabled
import dev.arnv.bluke.ui.KEYBOARD_CHARACTER_LAYOUT_PREFERENCE
import dev.arnv.bluke.ui.KeyboardCharacterLayout
import dev.arnv.bluke.ui.KEYBOARD_HOST_OS_PREFERENCE
import dev.arnv.bluke.ui.KEYBOARD_ORIENTATION_PREFERENCE
import dev.arnv.bluke.ui.KeyboardHostOs
import dev.arnv.bluke.ui.keyboardHostOs
import dev.arnv.bluke.ui.KEY_PREVIEW_PREFERENCE
import dev.arnv.bluke.ui.KEY_VIBRATION_PREFERENCE
import dev.arnv.bluke.ui.KEY_VIBRATION_STRENGTH_PREFERENCE
import dev.arnv.bluke.ui.KeyPressVibrator
import dev.arnv.bluke.ui.KeyVibrationStrength
import dev.arnv.bluke.ui.isKeyVibrationEnabled
import dev.arnv.bluke.ui.keyVibrationStrength
import dev.arnv.bluke.ui.KeyboardOrientation
import dev.arnv.bluke.ui.KEYBOARD_STYLE_PREFERENCE
import dev.arnv.bluke.ui.KEYBOARD_TRACKPAD_PREFERENCE
import dev.arnv.bluke.ui.KEYBOARD_TRACKPAD_SIDE_PREFERENCE
import dev.arnv.bluke.ui.KeyboardStyle
import dev.arnv.bluke.ui.TrackpadSide
import dev.arnv.bluke.ui.PORTRAIT_KEYBOARD_LAYOUT_PREFERENCE
import dev.arnv.bluke.ui.PortraitKeyboardLayout
import dev.arnv.bluke.ui.STICKY_MODIFIERS_PREFERENCE
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class KeyboardSettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val keyPressVibrator = KeyPressVibrator(applicationContext)
        setContent {
            MyApplicationTheme {
                var keySensitivity by remember { mutableFloatStateOf(preferences.getFloat("key_sensitivity", 6f)) }
                var lockSyncMode by remember { mutableStateOf(preferences.getString("lock_sync_mode", "host") ?: "host") }
                var characterLayout by remember {
                    mutableStateOf(KeyboardCharacterLayout.fromPreference(preferences.getString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, null)))
                }
                var orientation by remember {
                    mutableStateOf(KeyboardOrientation.fromPreference(preferences.getString(KEYBOARD_ORIENTATION_PREFERENCE, null)))
                }
                var portraitLayout by remember {
                    mutableStateOf(PortraitKeyboardLayout.fromPreference(preferences.getString(PORTRAIT_KEYBOARD_LAYOUT_PREFERENCE, null)))
                }
                var hostOs by remember { mutableStateOf(preferences.keyboardHostOs()) }
                var stickyModifiers by remember { mutableStateOf(preferences.getBoolean(STICKY_MODIFIERS_PREFERENCE, true)) }
                var keyPreview by remember { mutableStateOf(preferences.getBoolean(KEY_PREVIEW_PREFERENCE, true)) }
                var keyVibration by remember { mutableStateOf(preferences.isKeyVibrationEnabled()) }
                var keyVibrationStrength by remember { mutableStateOf(preferences.keyVibrationStrength()) }
                var keyboardStyle by remember {
                    mutableStateOf(KeyboardStyle.fromPreference(preferences.getString(KEYBOARD_STYLE_PREFERENCE, null)))
                }
                var showTrackpad by remember { mutableStateOf(preferences.getBoolean(KEYBOARD_TRACKPAD_PREFERENCE, false)) }
                var trackpadSide by remember {
                    mutableStateOf(TrackpadSide.fromPreference(preferences.getString(KEYBOARD_TRACKPAD_SIDE_PREFERENCE, null)))
                }
                var hardwareMode by remember { mutableStateOf(preferences.hardwareKeyboardMode()) }
                var hardwareTrackpad by remember { mutableStateOf(preferences.isHardwareKeyboardTrackpadEnabled()) }
                val physicalKeyboardFound = LocalConfiguration.current.hasPhysicalKeyboard()
                var dialog by remember { mutableStateOf<KeyboardDialog?>(null) }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Keyboard") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        SettingsGroup(title = "Behaviour") {
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                                Text("Key touch area", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Broader values make keys easier to hit but can increase adjacent-key presses.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = keySensitivity,
                                    onValueChange = {
                                        keySensitivity = it
                                        preferences.edit { putFloat("key_sensitivity", it) }
                                    },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                )
                                Text(
                                    if (keySensitivity <= 3f) "Precise" else if (keySensitivity >= 8f) "Broad" else "Balanced",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        SettingsCardGroup(
                            items = listOf(
                                SettingsItemData(
                                    title = "Typing layout",
                                    subtitle = "${characterLayout.displayName} · host: ${characterLayout.hostLayoutName}",
                                    icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.TYPING_LAYOUT },
                                ),
                                SettingsItemData(
                                    title = "Modifier keys",
                                    subtitle = if (hostOs == KeyboardHostOs.MAC) {
                                        "Mac · Cmd next to the space bar, then Opt"
                                    } else {
                                        "Windows / Linux · Alt next to the space bar, then Win"
                                    },
                                    icon = {
                                        Icon(
                                            if (hostOs == KeyboardHostOs.MAC) Icons.Default.LaptopMac else Icons.Default.LaptopWindows,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = { dialog = KeyboardDialog.HOST_OS },
                                ),
                                SettingsItemData(
                                    title = "Caps Lock",
                                    subtitle = if (lockSyncMode == "host") "Follow host indicators" else "Track locally on this phone",
                                    icon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.CAPS_LOCK },
                                ),
                                SettingsItemData(
                                    title = "Sticky modifier keys",
                                    subtitle = "Tap Ctrl, Alt, Shift, Win or Fn to apply it to the next key. Tap twice to lock it.",
                                    icon = { Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(checked = stickyModifiers, onCheckedChange = {
                                            stickyModifiers = it
                                            preferences.edit { putBoolean(STICKY_MODIFIERS_PREFERENCE, it) }
                                        })
                                    },
                                    onClick = {
                                        stickyModifiers = !stickyModifiers
                                        preferences.edit { putBoolean(STICKY_MODIFIERS_PREFERENCE, stickyModifiers) }
                                    },
                                ),
                            ),
                        )
                        if (keyPressVibrator.isAvailable) {
                            SettingsCardGroup(
                                title = "Vibration",
                                items = listOf(
                                    SettingsItemData(
                                        title = "Vibrate on key press",
                                        subtitle = "Also switchable from the keyboard toolbar",
                                        icon = { Icon(Icons.Default.Vibration, null, tint = MaterialTheme.colorScheme.primary) },
                                        action = {
                                            Switch(checked = keyVibration, onCheckedChange = {
                                                keyVibration = it
                                                preferences.edit { putBoolean(KEY_VIBRATION_PREFERENCE, it) }
                                            })
                                        },
                                        onClick = {
                                            keyVibration = !keyVibration
                                            preferences.edit { putBoolean(KEY_VIBRATION_PREFERENCE, keyVibration) }
                                        },
                                    ),
                                ) + if (keyVibration) listOf(
                                    SettingsItemData(
                                        title = "Vibration strength",
                                        subtitle = keyVibrationStrength.displayName,
                                        icon = { Icon(Icons.Default.Vibration, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { dialog = KeyboardDialog.VIBRATION_STRENGTH },
                                    ),
                                ) else emptyList(),
                            )
                        }
                        SettingsCardGroup(
                            title = "Screen",
                            items = listOf(
                                SettingsItemData(
                                    title = "Keyboard style",
                                    subtitle = keyboardStyle.displayName,
                                    icon = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.STYLE },
                                ),
                                SettingsItemData(
                                    title = "Trackpad with keyboard",
                                    subtitle = "Show a trackpad above the keyboard in portrait, or beside it in landscape",
                                    icon = { Icon(Icons.Default.ViewQuilt, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(checked = showTrackpad, onCheckedChange = {
                                            showTrackpad = it
                                            preferences.edit { putBoolean(KEYBOARD_TRACKPAD_PREFERENCE, it) }
                                        })
                                    },
                                    onClick = {
                                        showTrackpad = !showTrackpad
                                        preferences.edit { putBoolean(KEYBOARD_TRACKPAD_PREFERENCE, showTrackpad) }
                                    },
                                ),
                            ) + if (showTrackpad) listOf(
                                SettingsItemData(
                                    title = "Trackpad side in landscape",
                                    subtitle = "${trackpadSide.displayName} · also switchable from the keyboard toolbar",
                                    icon = { Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.TRACKPAD_SIDE },
                                ),
                            ) else emptyList(),
                        )
                        SettingsCardGroup(
                            title = "Hardware keyboard",
                            items = listOf(
                                SettingsItemData(
                                    title = "Hardware keyboard mode",
                                    subtitle = hardwareMode.displayName + " · " + when {
                                        hardwareMode.isActive(physicalKeyboardFound) ->
                                            "type on the phone's keyboard; only special keys stay on screen"
                                        physicalKeyboardFound -> "phone keyboard found, mode is off"
                                        else -> "no phone keyboard found"
                                    },
                                    icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.HARDWARE_MODE },
                                ),
                            ) + if (hardwareMode != HardwareKeyboardMode.OFF) listOf(
                                SettingsItemData(
                                    title = "Trackpad in hardware keyboard mode",
                                    subtitle = "Fill the screen above the special keys with a trackpad",
                                    icon = { Icon(Icons.Default.ViewQuilt, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(checked = hardwareTrackpad, onCheckedChange = {
                                            hardwareTrackpad = it
                                            preferences.edit { putBoolean(HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE, it) }
                                        })
                                    },
                                    onClick = {
                                        hardwareTrackpad = !hardwareTrackpad
                                        preferences.edit { putBoolean(HARDWARE_KEYBOARD_TRACKPAD_PREFERENCE, hardwareTrackpad) }
                                    },
                                ),
                            ) else emptyList(),
                        )
                        SettingsCardGroup(
                            title = "Rotation",
                            items = listOf(
                                SettingsItemData(
                                    title = "Keyboard rotation",
                                    subtitle = orientation.displayName,
                                    icon = { Icon(Icons.Default.ScreenRotation, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.ORIENTATION },
                                ),
                                SettingsItemData(
                                    title = "Portrait layout",
                                    subtitle = portraitLayout.displayName,
                                    icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.PORTRAIT_LAYOUT },
                                ),
                                SettingsItemData(
                                    title = "Key preview",
                                    subtitle = "Show the pressed letter above your finger on the Touch layout",
                                    icon = { Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(checked = keyPreview, onCheckedChange = {
                                            keyPreview = it
                                            preferences.edit { putBoolean(KEY_PREVIEW_PREFERENCE, it) }
                                        })
                                    },
                                    onClick = {
                                        keyPreview = !keyPreview
                                        preferences.edit { putBoolean(KEY_PREVIEW_PREFERENCE, keyPreview) }
                                    },
                                ),
                            ),
                        )
                        SettingsCardGroup(
                            title = "Customization",
                            items = listOf(
                                SettingsItemData(
                                    title = "Layouts",
                                    subtitle = "Choose a layout and toolbar cycle choices",
                                    icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { startActivity(Intent(this@KeyboardSettingsActivity, KeyboardLayoutsActivity::class.java)) },
                                ),
                                SettingsItemData(
                                    title = "Themes",
                                    subtitle = "Keyboard colors, case, and toolbar cycle choices",
                                    icon = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { startActivity(Intent(this@KeyboardSettingsActivity, KeyboardThemesActivity::class.java)) },
                                ),
                            ),
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }

                when (dialog) {
                    KeyboardDialog.TYPING_LAYOUT -> ChoiceDialog(
                        title = "Typing layout",
                        explanation = "Select the matching input source on the host so punctuation and symbols agree.",
                        choices = KeyboardCharacterLayout.entries.map { it.displayName },
                        selectedIndex = KeyboardCharacterLayout.entries.indexOf(characterLayout),
                        onSelect = { index ->
                            characterLayout = KeyboardCharacterLayout.entries[index]
                            preferences.edit { putString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, characterLayout.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.CAPS_LOCK -> ChoiceDialog(
                        title = "Caps Lock",
                        explanation = "Choose where lock-key state is controlled.",
                        choices = listOf("Host controlled", "Local on this phone"),
                        selectedIndex = if (lockSyncMode == "host") 0 else 1,
                        onSelect = { index ->
                            lockSyncMode = if (index == 0) "host" else "device"
                            preferences.edit { putString("lock_sync_mode", lockSyncMode) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.ORIENTATION -> ChoiceDialog(
                        title = "Keyboard rotation",
                        explanation = "Applies to Keyboard mode. Touchpad, Gamepad and Multimedia stay landscape.",
                        choices = KeyboardOrientation.entries.map { it.displayName },
                        selectedIndex = KeyboardOrientation.entries.indexOf(orientation),
                        onSelect = { index ->
                            orientation = KeyboardOrientation.entries[index]
                            preferences.edit { putString(KEYBOARD_ORIENTATION_PREFERENCE, orientation.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.PORTRAIT_LAYOUT -> ChoiceDialog(
                        title = "Portrait layout",
                        explanation = "The Touch layout has bigger keys and extra pages for symbols, F-keys and media keys.",
                        choices = PortraitKeyboardLayout.entries.map { it.displayName },
                        selectedIndex = PortraitKeyboardLayout.entries.indexOf(portraitLayout),
                        onSelect = { index ->
                            portraitLayout = PortraitKeyboardLayout.entries[index]
                            preferences.edit { putString(PORTRAIT_KEYBOARD_LAYOUT_PREFERENCE, portraitLayout.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.STYLE -> ChoiceDialog(
                        title = "Keyboard style",
                        explanation = "Minimal draws flat keys with less padding, so keys are larger and easier to hit.",
                        choices = KeyboardStyle.entries.map { it.displayName },
                        selectedIndex = KeyboardStyle.entries.indexOf(keyboardStyle),
                        onSelect = { index ->
                            keyboardStyle = KeyboardStyle.entries[index]
                            preferences.edit { putString(KEYBOARD_STYLE_PREFERENCE, keyboardStyle.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.TRACKPAD_SIDE -> ChoiceDialog(
                        title = "Trackpad side in landscape",
                        explanation = "In portrait the trackpad is always above the keyboard.",
                        choices = TrackpadSide.entries.map { it.displayName },
                        selectedIndex = TrackpadSide.entries.indexOf(trackpadSide),
                        onSelect = { index ->
                            trackpadSide = TrackpadSide.entries[index]
                            preferences.edit { putString(KEYBOARD_TRACKPAD_SIDE_PREFERENCE, trackpadSide.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.HOST_OS -> ChoiceDialog(
                        title = "Modifier keys",
                        explanation = "Mac swaps Alt and Win so Cmd sits next to the space bar. Also switchable from the keyboard toolbar.",
                        choices = KeyboardHostOs.entries.map { it.displayName },
                        selectedIndex = KeyboardHostOs.entries.indexOf(hostOs),
                        onSelect = { index ->
                            hostOs = KeyboardHostOs.entries[index]
                            preferences.edit { putString(KEYBOARD_HOST_OS_PREFERENCE, hostOs.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.VIBRATION_STRENGTH -> ChoiceDialog(
                        title = "Vibration strength",
                        explanation = "How strongly the phone vibrates for each key press.",
                        choices = KeyVibrationStrength.entries.map { it.displayName },
                        selectedIndex = KeyVibrationStrength.entries.indexOf(keyVibrationStrength),
                        onSelect = { index ->
                            keyVibrationStrength = KeyVibrationStrength.entries[index]
                            preferences.edit { putString(KEY_VIBRATION_STRENGTH_PREFERENCE, keyVibrationStrength.preferenceValue) }
                            keyPressVibrator.vibrate(keyVibrationStrength)
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    KeyboardDialog.HARDWARE_MODE -> ChoiceDialog(
                        title = "Hardware keyboard mode",
                        explanation = "For phones with a keyboard, like the Clicks Communicator or Unihertz Titan, " +
                            "or an attached keyboard. Letter keys are hidden and the phone's keys are sent to the host. " +
                            "The phone's Alt key types the symbols printed on its keys; use the on-screen Alt for host shortcuts. " +
                            "Automatic turns on when Android reports a keyboard.",
                        choices = HardwareKeyboardMode.entries.map { it.displayName },
                        selectedIndex = HardwareKeyboardMode.entries.indexOf(hardwareMode),
                        onSelect = { index ->
                            hardwareMode = HardwareKeyboardMode.entries[index]
                            preferences.edit { putString(HARDWARE_KEYBOARD_MODE_PREFERENCE, hardwareMode.preferenceValue) }
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                    null -> Unit
                }
            }
        }
    }
}

private enum class KeyboardDialog { TYPING_LAYOUT, CAPS_LOCK, ORIENTATION, PORTRAIT_LAYOUT, STYLE, TRACKPAD_SIDE, VIBRATION_STRENGTH, HOST_OS, HARDWARE_MODE }

@Composable
internal fun ChoiceDialog(
    title: String,
    explanation: String,
    choices: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                choices.forEachIndexed { index, choice ->
                    SettingsCardGroup(
                        items = listOf(
                            SettingsItemData(
                                title = choice,
                                action = { RadioButton(selected = index == selectedIndex, onClick = { onSelect(index) }) },
                                onClick = { onSelect(index) },
                            ),
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
