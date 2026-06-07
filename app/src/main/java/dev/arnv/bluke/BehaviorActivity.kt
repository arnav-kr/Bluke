package dev.arnv.bluke

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
