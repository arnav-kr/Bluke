package dev.arnv.bluke

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.KeyboardLayoutType
import dev.arnv.bluke.sound.SwitchType
import dev.arnv.bluke.ui.CaseColor
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape


class BehaviorActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        
        setContent {
            MyApplicationTheme {
                var hideUnknownDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unknown", false)) }
                var hideUnsupportedDevices by remember { mutableStateOf(sharedPrefs.getBoolean("hide_unsupported", true)) }
                var showMacAddress by remember { mutableStateOf(sharedPrefs.getBoolean("show_mac", false)) }
                var keySensitivity by remember { mutableStateOf(sharedPrefs.getFloat("key_sensitivity", 6f)) }
                var lockSyncMode by remember { mutableStateOf(sharedPrefs.getString("lock_sync_mode", "host") ?: "host") }
                
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Behavior") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SettingsGroup(title = "Touch Sensitivity") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                Text(
                                    text = "Key Touch Area",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Adjusts the touch radius around keys to prevent accidental adjacent key presses",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Precise",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Slider(
                                        value = keySensitivity,
                                        onValueChange = {
                                            keySensitivity = it
                                            sharedPrefs.edit().putFloat("key_sensitivity", it).apply()
                                        },
                                        valueRange = 0f..10f,
                                        steps = 9,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Broad",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }

                        SettingsCardGroup(
                            title = "Device Scanning",
                            items = listOf(
                                SettingsItemData(
                                    title = "Hide Unknown Devices",
                                    subtitle = "Filter out devices without a name",
                                    icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(
                                            checked = hideUnknownDevices,
                                            onCheckedChange = { 
                                                hideUnknownDevices = it
                                                sharedPrefs.edit().putBoolean("hide_unknown", it).apply()
                                            }
                                        )
                                    }
                                ),
                                SettingsItemData(
                                    title = "Hide Unsupported Devices",
                                    subtitle = "Filter out hosts lacking keyboard target capability",
                                    icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(
                                            checked = hideUnsupportedDevices,
                                            onCheckedChange = { 
                                                hideUnsupportedDevices = it
                                                sharedPrefs.edit().putBoolean("hide_unsupported", it).apply()
                                            }
                                        )
                                    }
                                ),
                                SettingsItemData(
                                    title = "Show MAC Address",
                                    subtitle = "Display device hardware address",
                                    icon = { Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Switch(
                                            checked = showMacAddress,
                                            onCheckedChange = { 
                                                showMacAddress = it
                                                sharedPrefs.edit().putBoolean("show_mac", it).apply()
                                            }
                                        )
                                    }
                                )
                            )
                        )
                        
                        // Quick Cycle Configurations
                        val allLayoutTypes = KeyboardLayoutType.values()
                        var activeLayoutsSet by remember {
                            mutableStateOf(sharedPrefs.getStringSet("cycle_keyboard_layouts", allLayoutTypes.map { it.name }.toSet()) ?: emptySet())
                        }
                        val layoutsDescription = if (activeLayoutsSet.size == allLayoutTypes.size) {
                            "All layouts active in cycle"
                        } else {
                            allLayoutTypes.filter { activeLayoutsSet.contains(it.name) }
                                .joinToString(", ") { it.displayName }
                        }

                        val allSwitches = SwitchType.values()
                        var activeSoundsSet by remember {
                            mutableStateOf(sharedPrefs.getStringSet("cycle_key_sounds", allSwitches.map { it.name }.toSet()) ?: emptySet())
                        }
                        val soundsDescription = if (activeSoundsSet.size == allSwitches.size) {
                            "All sound profiles active in cycle"
                        } else {
                            allSwitches.filter { activeSoundsSet.contains(it.name) }
                                .joinToString(", ") { it.displayName }
                        }

                        val connectionModesDisplayMap = mapOf(
                            "keyboard" to "Keyboard",
                            "touchpad" to "Touchpad",
                            "gamepad" to "Gamepad"
                        )
                        var activeModesSet by remember {
                            mutableStateOf(sharedPrefs.getStringSet("cycle_connection_modes", setOf("keyboard", "touchpad")) ?: emptySet())
                        }
                        val modesDescription = if (activeModesSet.size == 3) {
                            "All input modes active in cycle"
                        } else {
                            listOf("keyboard", "touchpad", "gamepad")
                                .filter { activeModesSet.contains(it) }
                                .map { connectionModesDisplayMap[it] ?: it }
                                .joinToString(", ")
                        }

                        val allCaseColors = CaseColor.values()
                        var activeColorsSet by remember {
                            mutableStateOf(sharedPrefs.getStringSet("cycle_case_colors", allCaseColors.map { it.name }.toSet()) ?: emptySet())
                        }
                        val colorsDescription = if (activeColorsSet.size == allCaseColors.size) {
                            "All colors active in cycle"
                        } else {
                            allCaseColors.filter { activeColorsSet.contains(it.name) }
                                .joinToString(", ") { it.displayName }
                        }

                        var showLayoutsDialog by remember { mutableStateOf(false) }
                        var showSoundsDialog by remember { mutableStateOf(false) }
                        var showModesDialog by remember { mutableStateOf(false) }
                        var showColorsDialog by remember { mutableStateOf(false) }

                        SettingsCardGroup(
                            title = "Quick Cycle Configurations",
                            items = listOf(
                                SettingsItemData(
                                    title = "Keyboard Layouts",
                                    subtitle = layoutsDescription,
                                    icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showLayoutsDialog = true }
                                ),
                                SettingsItemData(
                                    title = "Key Sounds",
                                    subtitle = soundsDescription,
                                    icon = { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showSoundsDialog = true }
                                ),
                                SettingsItemData(
                                    title = "Active Connection Modes",
                                    subtitle = modesDescription,
                                    icon = { Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showModesDialog = true }
                                ),
                                SettingsItemData(
                                    title = "Case Colors",
                                    subtitle = colorsDescription,
                                    icon = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showColorsDialog = true }
                                )
                            )
                        )
                        
                        SettingsCardGroup(
                            title = "Lock State Synchronization",
                            items = listOf(
                                SettingsItemData(
                                    title = "Host-Controlled (Windows / Linux)",
                                    subtitle = "Modifier states (Caps/Num Lock) sync with host LED reports. Best for Windows & Linux.",
                                    action = {
                                        RadioButton(
                                            selected = lockSyncMode == "host",
                                            onClick = {
                                                lockSyncMode = "host"
                                                sharedPrefs.edit().putString("lock_sync_mode", "host").apply()
                                            }
                                        )
                                    },
                                    onClick = {
                                        lockSyncMode = "host"
                                        sharedPrefs.edit().putString("lock_sync_mode", "host").apply()
                                    }
                                ),
                                SettingsItemData(
                                    title = "Device-Controlled (macOS / Android / TV)",
                                    subtitle = "Modifier states toggle locally. Necessary for macOS, mobile devices, and Smart TVs.",
                                    action = {
                                        RadioButton(
                                            selected = lockSyncMode == "device",
                                            onClick = {
                                                lockSyncMode = "device"
                                                sharedPrefs.edit().putString("lock_sync_mode", "device").apply()
                                            }
                                        )
                                    },
                                    onClick = {
                                        lockSyncMode = "device"
                                        sharedPrefs.edit().putString("lock_sync_mode", "device").apply()
                                    }
                                )
                            )
                        )

                        // Checkbox Dialogs
                        if (showLayoutsDialog) {
                            val selectedLayouts = remember {
                                mutableStateListOf<String>().apply {
                                    addAll(activeLayoutsSet)
                                }
                            }
                            AlertDialog(
                                onDismissRequest = { showLayoutsDialog = false },
                                title = { Text("Keyboard Layouts to Cycle") },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val toggleState = remember(selectedLayouts.size) {
                                            when (selectedLayouts.size) {
                                                allLayoutTypes.size -> ToggleableState.On
                                                0 -> ToggleableState.Off
                                                else -> ToggleableState.Indeterminate
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedLayouts.clear()
                                                        selectedLayouts.add(allLayoutTypes.first().name)
                                                    } else {
                                                        selectedLayouts.clear()
                                                        selectedLayouts.addAll(allLayoutTypes.map { it.name })
                                                    }
                                                }
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TriStateCheckbox(
                                                state = toggleState,
                                                onClick = {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedLayouts.clear()
                                                        selectedLayouts.add(allLayoutTypes.first().name)
                                                    } else {
                                                        selectedLayouts.clear()
                                                        selectedLayouts.addAll(allLayoutTypes.map { it.name })
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "Select All",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        
                                        val scrollState = rememberScrollState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(scrollState)
                                            ) {
                                                allLayoutTypes.forEach { layout ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (selectedLayouts.contains(layout.name)) {
                                                                    if (selectedLayouts.size > 1) {
                                                                        selectedLayouts.remove(layout.name)
                                                                    }
                                                                } else {
                                                                    selectedLayouts.add(layout.name)
                                                                }
                                                            }
                                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = selectedLayouts.contains(layout.name),
                                                            onCheckedChange = { checked ->
                                                                if (checked) {
                                                                    selectedLayouts.add(layout.name)
                                                                } else {
                                                                    if (selectedLayouts.size > 1) {
                                                                        selectedLayouts.remove(layout.name)
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.scale(0.85f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = layout.displayName,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }

                                            if (scrollState.value < scrollState.maxValue) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(24.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    AlertDialogDefaults.containerColor
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            sharedPrefs.edit().putStringSet("cycle_keyboard_layouts", selectedLayouts.toSet()).apply()
                                            activeLayoutsSet = selectedLayouts.toSet()
                                            showLayoutsDialog = false
                                        }
                                    ) {
                                        Text("Done")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLayoutsDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showSoundsDialog) {
                            val selectedSounds = remember {
                                mutableStateListOf<String>().apply {
                                    addAll(activeSoundsSet)
                                }
                            }
                            AlertDialog(
                                onDismissRequest = { showSoundsDialog = false },
                                title = { Text("Key Sounds to Cycle") },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val toggleState = remember(selectedSounds.size) {
                                            when (selectedSounds.size) {
                                                allSwitches.size -> ToggleableState.On
                                                0 -> ToggleableState.Off
                                                else -> ToggleableState.Indeterminate
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedSounds.clear()
                                                        selectedSounds.add(allSwitches.first().name)
                                                    } else {
                                                        selectedSounds.clear()
                                                        selectedSounds.addAll(allSwitches.map { it.name })
                                                    }
                                                }
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TriStateCheckbox(
                                                state = toggleState,
                                                onClick = {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedSounds.clear()
                                                        selectedSounds.add(allSwitches.first().name)
                                                    } else {
                                                        selectedSounds.clear()
                                                        selectedSounds.addAll(allSwitches.map { it.name })
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "Select All",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        
                                        val scrollState = rememberScrollState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(scrollState)
                                            ) {
                                                allSwitches.forEach { switch ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (selectedSounds.contains(switch.name)) {
                                                                    if (selectedSounds.size > 1) {
                                                                        selectedSounds.remove(switch.name)
                                                                    }
                                                                } else {
                                                                    selectedSounds.add(switch.name)
                                                                }
                                                            }
                                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = selectedSounds.contains(switch.name),
                                                            onCheckedChange = { checked ->
                                                                if (checked) {
                                                                    selectedSounds.add(switch.name)
                                                                } else {
                                                                    if (selectedSounds.size > 1) {
                                                                        selectedSounds.remove(switch.name)
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.scale(0.85f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = switch.displayName,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }

                                            if (scrollState.value < scrollState.maxValue) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(24.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    AlertDialogDefaults.containerColor
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            sharedPrefs.edit().putStringSet("cycle_key_sounds", selectedSounds.toSet()).apply()
                                            activeSoundsSet = selectedSounds.toSet()
                                            showSoundsDialog = false
                                        }
                                    ) {
                                        Text("Done")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSoundsDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showModesDialog) {
                            val selectedModes = remember {
                                mutableStateListOf<String>().apply {
                                    addAll(activeModesSet)
                                }
                            }
                            val allModes = listOf(
                                "keyboard" to "Keyboard Mode",
                                "touchpad" to "Touchpad Mode",
                                "gamepad" to "Gamepad Mode"
                            )
                            AlertDialog(
                                onDismissRequest = { showModesDialog = false },
                                title = { Text("Input Modes to Cycle") },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val toggleState = remember(selectedModes.size) {
                                            when (selectedModes.size) {
                                                allModes.size -> ToggleableState.On
                                                0 -> ToggleableState.Off
                                                else -> ToggleableState.Indeterminate
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedModes.clear()
                                                        selectedModes.add(allModes.first().first)
                                                    } else {
                                                        selectedModes.clear()
                                                        selectedModes.addAll(allModes.map { it.first })
                                                    }
                                                }
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TriStateCheckbox(
                                                state = toggleState,
                                                onClick = {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedModes.clear()
                                                        selectedModes.add(allModes.first().first)
                                                    } else {
                                                        selectedModes.clear()
                                                        selectedModes.addAll(allModes.map { it.first })
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "Select All",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        
                                        val scrollState = rememberScrollState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(scrollState)
                                            ) {
                                                allModes.forEach { (modeKey, modeName) ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (selectedModes.contains(modeKey)) {
                                                                    if (selectedModes.size > 1) {
                                                                        selectedModes.remove(modeKey)
                                                                    }
                                                                } else {
                                                                    selectedModes.add(modeKey)
                                                                }
                                                            }
                                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = selectedModes.contains(modeKey),
                                                            onCheckedChange = { checked ->
                                                                if (checked) {
                                                                    selectedModes.add(modeKey)
                                                                } else {
                                                                    if (selectedModes.size > 1) {
                                                                        selectedModes.remove(modeKey)
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.scale(0.85f)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = modeName,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }

                                            if (scrollState.value < scrollState.maxValue) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(24.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    AlertDialogDefaults.containerColor
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            sharedPrefs.edit().putStringSet("cycle_connection_modes", selectedModes.toSet()).apply()
                                            activeModesSet = selectedModes.toSet()
                                            showModesDialog = false
                                        }
                                    ) {
                                        Text("Done")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showModesDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showColorsDialog) {
                            val selectedColors = remember {
                                mutableStateListOf<String>().apply {
                                    addAll(activeColorsSet)
                                }
                            }
                            var customR by remember { mutableStateOf(sharedPrefs.getInt("custom_case_color_r", 63)) }
                            var customG by remember { mutableStateOf(sharedPrefs.getInt("custom_case_color_g", 81)) }
                            var customB by remember { mutableStateOf(sharedPrefs.getInt("custom_case_color_b", 181)) }
                            var customMetallic by remember { mutableStateOf(sharedPrefs.getBoolean("custom_case_color_metallic", false)) }

                            AlertDialog(
                                onDismissRequest = { showColorsDialog = false },
                                title = { Text("Case Colors to Cycle") },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val toggleState = remember(selectedColors.size) {
                                            when (selectedColors.size) {
                                                allCaseColors.size -> ToggleableState.On
                                                0 -> ToggleableState.Off
                                                else -> ToggleableState.Indeterminate
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedColors.clear()
                                                        selectedColors.add(allCaseColors.first().name)
                                                    } else {
                                                        selectedColors.clear()
                                                        selectedColors.addAll(allCaseColors.map { it.name })
                                                    }
                                                }
                                                .padding(vertical = 6.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TriStateCheckbox(
                                                state = toggleState,
                                                onClick = {
                                                    if (toggleState == ToggleableState.On) {
                                                        selectedColors.clear()
                                                        selectedColors.add(allCaseColors.first().name)
                                                    } else {
                                                        selectedColors.clear()
                                                        selectedColors.addAll(allCaseColors.map { it.name })
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = "Select All",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        
                                        val scrollState = rememberScrollState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(scrollState)
                                            ) {
                                                allCaseColors.forEach { colorOption ->
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    if (selectedColors.contains(colorOption.name)) {
                                                                        if (selectedColors.size > 1) {
                                                                            selectedColors.remove(colorOption.name)
                                                                        }
                                                                    } else {
                                                                        selectedColors.add(colorOption.name)
                                                                    }
                                                                }
                                                                .padding(vertical = 4.dp, horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Checkbox(
                                                                checked = selectedColors.contains(colorOption.name),
                                                                onCheckedChange = { checked ->
                                                                    if (checked) {
                                                                        selectedColors.add(colorOption.name)
                                                                    } else {
                                                                        if (selectedColors.size > 1) {
                                                                            selectedColors.remove(colorOption.name)
                                                                        }
                                                                    }
                                                                },
                                                                modifier = Modifier.scale(0.85f)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            
                                                            val resolvedColor = if (colorOption == CaseColor.CUSTOM) Color(customR, customG, customB) else colorOption.caseColor
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(12.dp)
                                                                    .clip(CircleShape)
                                                                    .background(resolvedColor)
                                                                    .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = colorOption.displayName,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        }

                                                        if (colorOption == CaseColor.CUSTOM && selectedColors.contains(CaseColor.CUSTOM.name)) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(start = 44.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                                    .padding(8.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Text("Customize Custom Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                                
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(30.dp)
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(customR, customG, customB))
                                                                )

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text("R", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodySmall)
                                                                    Slider(
                                                                        value = customR.toFloat(),
                                                                        onValueChange = { customR = it.toInt() },
                                                                        valueRange = 0f..255f,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    Text(customR.toString(), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                                                                }

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text("G", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodySmall)
                                                                    Slider(
                                                                        value = customG.toFloat(),
                                                                        onValueChange = { customG = it.toInt() },
                                                                        valueRange = 0f..255f,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    Text(customG.toString(), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                                                                }

                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Text("B", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodySmall)
                                                                    Slider(
                                                                        value = customB.toFloat(),
                                                                        onValueChange = { customB = it.toInt() },
                                                                        valueRange = 0f..255f,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    Text(customB.toString(), modifier = Modifier.width(28.dp), style = MaterialTheme.typography.bodySmall)
                                                                }

                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text("Metallic Finish", style = MaterialTheme.typography.bodySmall)
                                                                    Switch(
                                                                        checked = customMetallic,
                                                                        onCheckedChange = { customMetallic = it },
                                                                        modifier = Modifier.scale(0.8f)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                            }

                                            if (scrollState.value < scrollState.maxValue) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(24.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    AlertDialogDefaults.containerColor
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            sharedPrefs.edit()
                                                .putStringSet("cycle_case_colors", selectedColors.toSet())
                                                .putInt("custom_case_color_r", customR)
                                                .putInt("custom_case_color_g", customG)
                                                .putInt("custom_case_color_b", customB)
                                                .putBoolean("custom_case_color_metallic", customMetallic)
                                                .apply()
                                            activeColorsSet = selectedColors.toSet()
                                            showColorsDialog = false
                                        }
                                    ) {
                                        Text("Done")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showColorsDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
