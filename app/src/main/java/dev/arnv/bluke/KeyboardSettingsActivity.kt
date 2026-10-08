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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.ui.KEYBOARD_CHARACTER_LAYOUT_PREFERENCE
import dev.arnv.bluke.ui.KeyboardCharacterLayout
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsGroup
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class KeyboardSettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setContent {
            MyApplicationTheme {
                var keySensitivity by remember { mutableFloatStateOf(preferences.getFloat("key_sensitivity", 6f)) }
                var lockSyncMode by remember { mutableStateOf(preferences.getString("lock_sync_mode", "host") ?: "host") }
                var characterLayout by remember {
                    mutableStateOf(KeyboardCharacterLayout.fromPreference(preferences.getString(KEYBOARD_CHARACTER_LAYOUT_PREFERENCE, null)))
                }
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
                                    title = "Caps Lock",
                                    subtitle = if (lockSyncMode == "host") "Follow host indicators" else "Track locally on this phone",
                                    icon = { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { dialog = KeyboardDialog.CAPS_LOCK },
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
                                SettingsItemData(
                                    title = "Key sound packs",
                                    subtitle = "Choose, import, and configure toolbar cycle choices",
                                    icon = { Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { startActivity(Intent(this@KeyboardSettingsActivity, SoundPacksActivity::class.java)) },
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
                    null -> Unit
                }
            }
        }
    }
}

private enum class KeyboardDialog { TYPING_LAYOUT, CAPS_LOCK }

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
