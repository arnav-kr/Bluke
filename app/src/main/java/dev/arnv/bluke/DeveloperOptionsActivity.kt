package dev.arnv.bluke

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.edit
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class DeveloperOptionsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                
                var isDevMode by remember { mutableStateOf(sharedPrefs.getBoolean("is_developer_mode", false)) }
                var mockUpdatePopup by remember { mutableStateOf(sharedPrefs.getBoolean("mock_update_popup", false)) }
                var mockBtDisabled by remember { mutableStateOf(sharedPrefs.getBoolean("mock_bt_disabled", false)) }
                var mockUnsupported by remember { mutableStateOf(sharedPrefs.getBoolean("mock_device_unsupported", false)) }

                // Easter egg states
                val catEmojis = listOf("🐱", "😹", "😼")
                var catIndex by remember { mutableIntStateOf(0) }
                var catClicked by remember { mutableStateOf(false) }

                LaunchedEffect(catClicked) {
                    if (catClicked) {
                        catIndex = (catIndex + 1) % catEmojis.size
                        if (catIndex == 2) {
                            // Hit smirk
                            delay(500)
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=dQw4w9WgXcQ".toUri()))
                        }
                        catClicked = false
                    }
                }

                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Developer Options") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = "WARNING: These settings are intended for development use only. They may cause the app to malfunction, mock errors, or degrade your experience.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Master Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Developer Mode", style = MaterialTheme.typography.titleMedium)
                            Switch(
                                checked = isDevMode,
                                onCheckedChange = { 
                                    isDevMode = it
                                    sharedPrefs.edit { putBoolean("is_developer_mode", it) }
                                    if (!it) {
                                        // Auto turn off all mocks if dev mode disabled
                                        sharedPrefs.edit {
                                            putBoolean("mock_update_popup", false)
                                            putBoolean("mock_bt_disabled", false)
                                            putBoolean("mock_device_unsupported", false)
                                            putBoolean("mock_hid_unsupported", false)
                                        }
                                        finish() // Exit screen
                                    }
                                }
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        // Debug & Mocks
                        SettingsCardGroup(
                            items = listOf(
                                SettingsItemData(
                                    title = "Developer Logs",
                                    subtitle = "Live Bluetooth packets and OS logs",
                                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) },
                                    onClick = { 
                                        startActivity(Intent(this@DeveloperOptionsActivity, DeveloperLogsActivity::class.java))
                                    }
                                ),
                                SettingsItemData(
                                    title = "Force App Update Popup",
                                    subtitle = "Mocks an app update on HomeScreen",
                                    action = {
                                        Switch(
                                            checked = mockUpdatePopup,
                                            onCheckedChange = {
                                                mockUpdatePopup = it
                                                sharedPrefs.edit { putBoolean("mock_update_popup", it) }
                                            }
                                        )
                                    }
                                ),
                                SettingsItemData(
                                    title = "Home Screen Error State",
                                    subtitle = "Force UI into specific error state",
                                    action = {
                                        var expanded by remember { mutableStateOf(false) }
                                        val errorOptions = listOf("None", "Bluetooth Disabled", "Device Unsupported", "HID Unsupported")
                                        val mockHidUnsupported = sharedPrefs.getBoolean("mock_hid_unsupported", false)
                                        val currentSelection = when {
                                            mockBtDisabled -> "Bluetooth Disabled"
                                            mockUnsupported -> "Device Unsupported"
                                            mockHidUnsupported -> "HID Unsupported"
                                            else -> "None"
                                        }
                                        Box {
                                            TextButton(onClick = { expanded = true }) {
                                                Text(currentSelection)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                errorOptions.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = { Text(option) },
                                                        onClick = {
                                                            when (option) {
                                                                "None" -> {
                                                                    mockBtDisabled = false
                                                                    mockUnsupported = false
                                                                    sharedPrefs.edit { putBoolean("mock_hid_unsupported", false) }
                                                                }
                                                                "Bluetooth Disabled" -> {
                                                                    mockBtDisabled = true
                                                                    mockUnsupported = false
                                                                    sharedPrefs.edit { putBoolean("mock_hid_unsupported", false) }
                                                                }
                                                                "Device Unsupported" -> {
                                                                    mockBtDisabled = false
                                                                    mockUnsupported = true
                                                                    sharedPrefs.edit { putBoolean("mock_hid_unsupported", false) }
                                                                }
                                                                "HID Unsupported" -> {
                                                                    mockBtDisabled = false
                                                                    mockUnsupported = false
                                                                    sharedPrefs.edit { putBoolean("mock_hid_unsupported", true) }
                                                                }
                                                            }
                                                            sharedPrefs.edit {
                                                                putBoolean("mock_bt_disabled", mockBtDisabled)
                                                                putBoolean("mock_device_unsupported", mockUnsupported)
                                                            }
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                )
                            )
                        )

                        // Easter Egg
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = catEmojis[catIndex],
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    catClicked = true
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
