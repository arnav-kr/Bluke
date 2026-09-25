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
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.bluetooth.GAMEPAD_DPAD_MODE_PREFERENCE
import dev.arnv.bluke.bluetooth.GamepadDpadOutputMode
import dev.arnv.bluke.ui.HARDWARE_VOLUME_REMOTE_PREFERENCE
import dev.arnv.bluke.ui.KEYBOARD_CHARACTER_LAYOUT_PREFERENCE
import dev.arnv.bluke.ui.KeyboardCharacterLayout
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class BehaviorActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                var hideUnknownDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unknown", false)) }
                var hideUnsupportedDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unsupported", true)) }
                var showMacAddress by remember { mutableStateOf(sharedPrefs.getBoolean("show_mac", false)) }
                var autoConnectEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("auto_connect", true)) }
                var keepAudioOnPhone by remember { mutableStateOf(sharedPrefs.getBoolean("disconnect_audio_profiles", false)) }
                var hardwareVolumeRemote by remember {
                    mutableStateOf(sharedPrefs.getBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, false))
                }
                var keySensitivity by remember { mutableFloatStateOf(sharedPrefs.getFloat("key_sensitivity", 6f)) }
                var lockSyncMode by remember { mutableStateOf(sharedPrefs.getString("lock_sync_mode", "host") ?: "host") }
                var characterLayout by remember {
                    mutableStateOf(
                        KeyboardCharacterLayout.fromPreference(
                            sharedPrefs.getString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, null),
                        ),
                    )
                }
                var dpadMode by remember {
                    mutableStateOf(
                        GamepadDpadOutputMode.fromPreference(
                            sharedPrefs.getString(GAMEPAD_DPAD_MODE_PREFERENCE, null),
                        ),
                    )
                }
                var showTypingLayoutDialog by remember { mutableStateOf(false) }
                var showDpadDialog by remember { mutableStateOf(false) }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Controls & connection") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))

                        SettingsGroup(title = "Keyboard") {
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                                Text("Key touch area", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Broader values make keys easier to hit but can increase adjacent-key presses.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = keySensitivity,
                                    onValueChange = { value ->
                                        keySensitivity = value
                                        sharedPrefs.edit { putFloat("key_sensitivity", value) }
                                    },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                )
                                Text(
                                    text = if (keySensitivity <= 3f) "Precise" else if (keySensitivity >= 8f) "Broad" else "Balanced",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        SettingsCardGroup(
                            title = "Keyboard behavior",
                            items = listOf(
                                SettingsItemData(
                                    title = "Typing layout",
                                    subtitle = "${characterLayout.displayName} · set the host input source to ${characterLayout.hostLayoutName}",
                                    icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showTypingLayoutDialog = true },
                                ),
                                SettingsItemData(
                                    title = "Lock-key indicators",
                                    subtitle = if (lockSyncMode == "host") {
                                        "Follow the host (recommended for Windows and Linux)"
                                    } else {
                                        "Toggle on this phone (for hosts that do not return LED state)"
                                    },
                                    icon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(
                                            checked = lockSyncMode == "device",
                                            onCheckedChange = { local ->
                                                lockSyncMode = if (local) "device" else "host"
                                                sharedPrefs.edit { putString("lock_sync_mode", lockSyncMode) }
                                            },
                                        )
                                    },
                                ),
                            ),
                        )

                        SettingsCardGroup(
                            title = "Connection",
                            items = listOf(
                                SettingsItemData(
                                    title = "Hide unnamed devices",
                                    subtitle = "Do not show scan results without a device name",
                                    icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(hideUnknownDevices, { value ->
                                            hideUnknownDevices = value
                                            sharedPrefs.edit { putBoolean("hide_unknown", value) }
                                        })
                                    },
                                ),
                                SettingsItemData(
                                    title = "Hide unlikely hosts",
                                    subtitle = "Filter scan results that do not appear able to accept keyboard input",
                                    icon = { Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(hideUnsupportedDevices, { value ->
                                            hideUnsupportedDevices = value
                                            sharedPrefs.edit { putBoolean("hide_unsupported", value) }
                                        })
                                    },
                                ),
                                SettingsItemData(
                                    title = "Show device addresses",
                                    subtitle = "Useful when similarly named Bluetooth devices are nearby",
                                    icon = { Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(showMacAddress, { value ->
                                            showMacAddress = value
                                            sharedPrefs.edit { putBoolean("show_mac", value) }
                                        })
                                    },
                                ),
                                SettingsItemData(
                                    title = "Reconnect on launch",
                                    subtitle = "Try the most recently connected host when Bluetooth is ready",
                                    icon = { Icon(Icons.Default.BluetoothConnected, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(autoConnectEnabled, { value ->
                                            autoConnectEnabled = value
                                            sharedPrefs.edit { putBoolean("auto_connect", value) }
                                        })
                                    },
                                ),
                                SettingsItemData(
                                    title = "Keep audio on this phone",
                                    subtitle = "Best-effort Linux workaround when connecting Bluke moves phone audio to the computer",
                                    icon = { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(keepAudioOnPhone, { value ->
                                            keepAudioOnPhone = value
                                            sharedPrefs.edit { putBoolean("disconnect_audio_profiles", value) }
                                        })
                                    },
                                ),
                            ),
                        )

                        SettingsCardGroup(
                            title = "Remote controls",
                            items = listOf(
                                SettingsItemData(
                                    title = "Gamepad D-pad",
                                    subtitle = if (dpadMode == GamepadDpadOutputMode.NATIVE_HAT) {
                                        "Native games · standard controller direction control"
                                    } else {
                                        "Browser games · use when a web game ignores D-pad input"
                                    },
                                    icon = { Icon(Icons.Default.Gamepad, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showDpadDialog = true },
                                ),
                                SettingsItemData(
                                    title = "Phone volume buttons control host",
                                    subtitle = "Only while Media + Presentation is open; off by default",
                                    icon = { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(hardwareVolumeRemote, { value ->
                                            hardwareVolumeRemote = value
                                            sharedPrefs.edit { putBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, value) }
                                        })
                                    },
                                ),
                                SettingsItemData(
                                    title = "Quick-cycle choices",
                                    subtitle = "Choose what toolbar taps cycle through",
                                    icon = { Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        startActivity(Intent(this@BehaviorActivity, QuickCycleActivity::class.java))
                                    },
                                ),
                            ),
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }

                if (showTypingLayoutDialog) {
                    SingleChoiceDialog(
                        title = "Typing layout",
                        explanation = "Bluke changes the HID key positions it sends. Select the matching input source on the host for punctuation and symbols to agree.",
                        choices = KeyboardCharacterLayout.entries.map { it.displayName },
                        selectedIndex = KeyboardCharacterLayout.entries.indexOf(characterLayout),
                        onSelect = { index ->
                            characterLayout = KeyboardCharacterLayout.entries[index]
                            sharedPrefs.edit {
                                putString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, characterLayout.preferenceValue)
                            }
                            showTypingLayoutDialog = false
                        },
                        onDismiss = { showTypingLayoutDialog = false },
                    )
                }

                if (showDpadDialog) {
                    SingleChoiceDialog(
                        title = "Where will you use the D-pad?",
                        explanation = "Native games use the controller standard. Some browser games only understand four numbered buttons. You can switch any time without pairing again.",
                        choices = listOf("Native games", "Browser games"),
                        selectedIndex = if (dpadMode == GamepadDpadOutputMode.NATIVE_HAT) 0 else 1,
                        onSelect = { index ->
                            dpadMode = if (index == 0) {
                                GamepadDpadOutputMode.NATIVE_HAT
                            } else {
                                GamepadDpadOutputMode.WEB_BUTTONS
                            }
                            sharedPrefs.edit {
                                putString(GAMEPAD_DPAD_MODE_PREFERENCE, dpadMode.preferenceValue)
                            }
                            showDpadDialog = false
                        },
                        onDismiss = { showDpadDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceDialog(
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
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choices.forEachIndexed { index, choice ->
                    SettingsCardGroup(
                        items = listOf(
                            SettingsItemData(
                                title = choice,
                                action = {
                                    RadioButton(
                                        selected = index == selectedIndex,
                                        onClick = { onSelect(index) },
                                    )
                                },
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
