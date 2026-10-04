package dev.arnv.bluke

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
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.bluetooth.GAMEPAD_DPAD_MODE_PREFERENCE
import dev.arnv.bluke.bluetooth.GamepadDpadOutputMode
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class ControllerSettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setContent {
            MyApplicationTheme {
                var dpadMode by remember {
                    mutableStateOf(GamepadDpadOutputMode.fromPreference(preferences.getString(GAMEPAD_DPAD_MODE_PREFERENCE, null)))
                }
                var showDialog by remember { mutableStateOf(false) }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Controller") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                        SettingsCardGroup(
                            title = "Behaviour",
                            items = listOf(
                                SettingsItemData(
                                    title = "Controller compatibility",
                                    subtitle = dpadMode.description,
                                    icon = { Icon(Icons.Default.Gamepad, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { showDialog = true },
                                ),
                            ),
                        )
                    }
                }
                if (showDialog) {
                    ChoiceDialog(
                        title = "Controller compatibility",
                        explanation = "Use Native for Windows/Linux games, Android for games on Android, or Web when a browser game ignores directions. Switching does not require pairing again. Games may not support Share or touchpad-click.",
                        choices = GamepadDpadOutputMode.entries.map { it.label },
                        selectedIndex = dpadMode.ordinal,
                        onSelect = { index ->
                            dpadMode = GamepadDpadOutputMode.entries[index]
                            preferences.edit { putString(GAMEPAD_DPAD_MODE_PREFERENCE, dpadMode.preferenceValue) }
                            showDialog = false
                        },
                        onDismiss = { showDialog = false },
                    )
                }
            }
        }
    }
}
