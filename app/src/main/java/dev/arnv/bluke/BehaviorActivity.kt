package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.ui.HARDWARE_VOLUME_REMOTE_PREFERENCE
import dev.arnv.bluke.ui.DEFAULT_TOUCHPAD_POINTER_SPEED
import dev.arnv.bluke.ui.DEFAULT_TOUCHPAD_SCROLL_SPEED
import dev.arnv.bluke.ui.MAX_TOUCHPAD_SPEED
import dev.arnv.bluke.ui.MIN_TOUCHPAD_SPEED
import dev.arnv.bluke.ui.NATURAL_SCROLL_PREFERENCE
import dev.arnv.bluke.ui.TOUCHPAD_GESTURE_SENSITIVITY_PREFERENCE
import dev.arnv.bluke.ui.TOUCHPAD_HORIZONTAL_SCROLL_PREFERENCE
import dev.arnv.bluke.ui.TOUCHPAD_POINTER_SPEED_PREFERENCE
import dev.arnv.bluke.ui.TOUCHPAD_SCROLL_INERTIA_PREFERENCE
import dev.arnv.bluke.ui.TOUCHPAD_SCROLL_SPEED_PREFERENCE
import dev.arnv.bluke.ui.TOUCHPAD_SPEED_STEP
import dev.arnv.bluke.ui.TouchpadHorizontalScroll
import dev.arnv.bluke.ui.snapTouchpadSpeed
import dev.arnv.bluke.ui.touchpadSettings
import dev.arnv.bluke.ui.isNaturalScrollEnabled
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class BehaviorActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setContent {
            MyApplicationTheme {
                var hideUnknown by remember { mutableStateOf(preferences.getBoolean("hide_unknown", false)) }
                var hideUnlikely by remember { mutableStateOf(preferences.getBoolean("hide_unsupported", true)) }
                var showMac by remember { mutableStateOf(preferences.getBoolean("show_mac", false)) }
                var reconnect by remember { mutableStateOf(preferences.getBoolean("auto_connect", true)) }
                var keepAudio by remember { mutableStateOf(preferences.getBoolean("disconnect_audio_profiles", false)) }
                var naturalScroll by remember { mutableStateOf(preferences.isNaturalScrollEnabled()) }
                val touchpad = remember { preferences.touchpadSettings() }
                var pointerSpeed by remember {
                    mutableFloatStateOf(
                        snapTouchpadSpeed(preferences.getFloat(TOUCHPAD_POINTER_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_POINTER_SPEED)),
                    )
                }
                var scrollSpeed by remember {
                    mutableFloatStateOf(
                        snapTouchpadSpeed(preferences.getFloat(TOUCHPAD_SCROLL_SPEED_PREFERENCE, DEFAULT_TOUCHPAD_SCROLL_SPEED)),
                    )
                }
                var gestureSensitivity by remember { mutableStateOf(touchpad.gestureSensitivity) }
                var scrollInertia by remember { mutableStateOf(touchpad.scrollInertia) }
                var horizontalScroll by remember { mutableStateOf(touchpad.horizontalScroll) }
                var volumeControlsHost by remember { mutableStateOf(preferences.getBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, false)) }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Controls & connection") },
                            navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        SettingsCardGroup(
                            title = "Behaviour",
                            items = listOf(
                                SettingsItemData("Reconnect on launch", "Try the most recently connected host when Bluetooth is ready", { Icon(Icons.Default.BluetoothConnected, null, tint = MaterialTheme.colorScheme.primary) }, { Switch(reconnect, { reconnect = it; preferences.edit { putBoolean("auto_connect", it) } }) }),
                                SettingsItemData("Hide unnamed devices", "Do not show scan results without a device name", { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) }, { Switch(hideUnknown, { hideUnknown = it; preferences.edit { putBoolean("hide_unknown", it) } }) }),
                                SettingsItemData("Hide unlikely hosts", "Filter devices that do not appear able to accept input", { Icon(Icons.Default.FilterAltOff, null, tint = MaterialTheme.colorScheme.primary) }, { Switch(hideUnlikely, { hideUnlikely = it; preferences.edit { putBoolean("hide_unsupported", it) } }) }),
                                SettingsItemData("Show MAC addresses", "Useful when similarly named Bluetooth devices are nearby", { Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary) }, { Switch(showMac, { showMac = it; preferences.edit { putBoolean("show_mac", it) } }) }),
                                SettingsItemData("Keep audio on this phone", "Best-effort workaround when a host takes over phone audio", { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary) }, { Switch(keepAudio, { keepAudio = it; preferences.edit { putBoolean("disconnect_audio_profiles", it) } }) }),
                                SettingsItemData("Volume buttons control host volume", "Only while Multimedia is open", { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.primary) }, { Switch(volumeControlsHost, { volumeControlsHost = it; preferences.edit { putBoolean(HARDWARE_VOLUME_REMOTE_PREFERENCE, it) } }) }),
                            ),
                        )
                        SettingsCardGroup(
                            title = "Touchpad",
                            items = listOf(
                                SettingsItemData(
                                    title = "Pointer speed",
                                    subtitle = "${pointerSpeed}x. How far the pointer moves for the same finger movement",
                                    icon = { Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        TouchpadSpeedSlider(pointerSpeed, "Pointer speed") {
                                            pointerSpeed = it
                                            preferences.edit { putFloat(TOUCHPAD_POINTER_SPEED_PREFERENCE, it) }
                                        }
                                    },
                                ),
                                SettingsItemData(
                                    title = "Scroll speed",
                                    subtitle = "${scrollSpeed}x. How far a two-finger swipe scrolls",
                                    icon = { Icon(Icons.Default.KeyboardDoubleArrowUp, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        TouchpadSpeedSlider(scrollSpeed, "Scroll speed") {
                                            scrollSpeed = it
                                            preferences.edit { putFloat(TOUCHPAD_SCROLL_SPEED_PREFERENCE, it) }
                                        }
                                    },
                                ),
                                SettingsItemData(
                                    title = "Gesture sensitivity",
                                    subtitle = gestureSensitivity.description,
                                    icon = { Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = { SettingValueLabel(gestureSensitivity.displayName) },
                                    onClick = {
                                        gestureSensitivity = gestureSensitivity.next()
                                        preferences.edit {
                                            putString(TOUCHPAD_GESTURE_SENSITIVITY_PREFERENCE, gestureSensitivity.preferenceValue)
                                        }
                                    },
                                ),
                                SettingsItemData(
                                    title = "Scroll glide",
                                    subtitle = "Scrolling coasts to a stop after a two-finger flick. Touch the pad to stop it.",
                                    icon = { Icon(Icons.Default.Swipe, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(scrollInertia, {
                                            scrollInertia = it
                                            preferences.edit { putBoolean(TOUCHPAD_SCROLL_INERTIA_PREFERENCE, it) }
                                        })
                                    },
                                    onClick = {
                                        scrollInertia = !scrollInertia
                                        preferences.edit { putBoolean(TOUCHPAD_SCROLL_INERTIA_PREFERENCE, scrollInertia) }
                                    },
                                ),
                                SettingsItemData(
                                    title = "Sideways scrolling",
                                    subtitle = if (horizontalScroll == TouchpadHorizontalScroll.STANDARD) {
                                        "Scrolls in any direction, including diagonally. If sideways scrolling does nothing, pair the computer again or switch to Shift + wheel."
                                    } else {
                                        "Holds Shift while scrolling sideways. Works without pairing again, but one direction at a time and not in every app."
                                    },
                                    icon = { Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = { SettingValueLabel(horizontalScroll.displayName) },
                                    onClick = {
                                        horizontalScroll = horizontalScroll.next()
                                        preferences.edit {
                                            putString(TOUCHPAD_HORIZONTAL_SCROLL_PREFERENCE, horizontalScroll.preferenceValue)
                                        }
                                    },
                                ),
                                SettingsItemData(
                                    title = "Natural scrolling",
                                    subtitle = if (naturalScroll) {
                                        "Content follows your fingers, like on a phone. If scrolling feels reversed on a Mac, turn this off."
                                    } else {
                                        "Moving two fingers down scrolls down, like a mouse wheel"
                                    },
                                    icon = { Icon(Icons.Default.SwipeVertical, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(naturalScroll, {
                                            naturalScroll = it
                                            preferences.edit { putBoolean(NATURAL_SCROLL_PREFERENCE, it) }
                                        })
                                    },
                                    onClick = {
                                        naturalScroll = !naturalScroll
                                        preferences.edit { putBoolean(NATURAL_SCROLL_PREFERENCE, naturalScroll) }
                                    },
                                ),
                            ),
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

/** A slider from 0.25x to 3x in quarter steps, sized to sit at the end of a settings row. */
@Composable
private fun TouchpadSpeedSlider(value: Float, label: String, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = { onChange(snapTouchpadSpeed(it)) },
        valueRange = MIN_TOUCHPAD_SPEED..MAX_TOUCHPAD_SPEED,
        steps = ((MAX_TOUCHPAD_SPEED - MIN_TOUCHPAD_SPEED) / TOUCHPAD_SPEED_STEP).toInt() - 1,
        modifier = Modifier.width(150.dp).semantics { contentDescription = label },
    )
}

@Composable
private fun SettingValueLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
