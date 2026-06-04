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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
