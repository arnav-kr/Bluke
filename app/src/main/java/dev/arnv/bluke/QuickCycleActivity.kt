package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.ui.InputMode
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.normalizedCycleSelection
import dev.arnv.bluke.ui.toggledCycleSelection
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class QuickCycleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setContent {
            MyApplicationTheme {
                val modeIds = InputMode.entries.map { it.preferenceKey }
                var selectedModes by remember {
                    mutableStateOf(normalizedCycleSelection(preferences.getStringSet("cycle_connection_modes", null), modeIds))
                }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Input mode cycle") },
                            navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Toolbar taps only visit checked input modes. Configure layout, theme, and sound cycles in their own Keyboard settings lists.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        SettingsCardGroup(
                            title = "Input modes",
                            items = InputMode.entries.map { mode ->
                                SettingsItemData(
                                    title = mode.displayName,
                                    icon = { Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.primary) },
                                    action = {
                                        Checkbox(
                                            checked = mode.preferenceKey in selectedModes,
                                            enabled = mode.preferenceKey !in selectedModes || selectedModes.size > 1,
                                            onCheckedChange = {
                                                selectedModes = toggledCycleSelection(selectedModes, mode.preferenceKey)
                                                preferences.edit { putStringSet("cycle_connection_modes", selectedModes) }
                                            },
                                        )
                                    },
                                    onClick = {
                                        if (mode.preferenceKey !in selectedModes || selectedModes.size > 1) {
                                            selectedModes = toggledCycleSelection(selectedModes, mode.preferenceKey)
                                            preferences.edit { putStringSet("cycle_connection_modes", selectedModes) }
                                        }
                                    },
                                )
                            },
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
