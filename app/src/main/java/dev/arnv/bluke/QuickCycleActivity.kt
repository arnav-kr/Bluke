package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.data.CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE
import dev.arnv.bluke.data.CYCLE_KEYBOARD_THEMES_PREFERENCE
import dev.arnv.bluke.data.KeyboardThemeRepository
import dev.arnv.bluke.sound.SwitchType
import dev.arnv.bluke.ui.CaseColor
import dev.arnv.bluke.ui.InputMode
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.SettingsCardGroup
import dev.arnv.bluke.ui.SettingsItem
import dev.arnv.bluke.ui.SettingsItemData
import dev.arnv.bluke.ui.VisualColorPicker
import dev.arnv.bluke.ui.normalizedCycleSelection
import dev.arnv.bluke.ui.toggledCycleSelection
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor
import kotlin.math.roundToInt

const val EXTRA_QUICK_CYCLE_SECTION = "quick_cycle_section"
const val QUICK_CYCLE_SECTION_KEY_SOUNDS = "key_sounds"

class QuickCycleActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                val themeRepository = remember { KeyboardThemeRepository(this) }
                val themes = remember { themeRepository.allThemes() }
                val geometryIds = KeyboardGeometry.entries.map { it.name }
                val soundIds = SwitchType.entries.map { it.name }
                val modeIds = InputMode.entries.map { it.preferenceKey }
                val themeIds = themes.map { it.id }
                val colorIds = CaseColor.entries.map { it.name }
                var selectedGeometries by remember {
                    mutableStateOf(normalizedCycleSelection(preferences.getStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, null), geometryIds))
                }
                var selectedSounds by remember {
                    mutableStateOf(normalizedCycleSelection(preferences.getStringSet("cycle_key_sounds", null), soundIds))
                }
                var selectedModes by remember {
                    mutableStateOf(normalizedCycleSelection(preferences.getStringSet("cycle_connection_modes", null), modeIds))
                }
                var selectedThemes by remember {
                    mutableStateOf(normalizedCycleSelection(preferences.getStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, null), themeIds))
                }
                var selectedColors by remember {
                    mutableStateOf(normalizedCycleSelection(preferences.getStringSet("cycle_case_colors", null), colorIds))
                }
                var showCustomCaseColorEditor by remember { mutableStateOf(false) }
                var customCaseColor by remember {
                    mutableIntStateOf(
                        android.graphics.Color.rgb(
                            preferences.getInt("custom_case_color_r", 63),
                            preferences.getInt("custom_case_color_g", 81),
                            preferences.getInt("custom_case_color_b", 181),
                        ),
                    )
                }
                var customCaseMetallic by remember {
                    mutableStateOf(preferences.getBoolean("custom_case_color_metallic", false))
                }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                val scrollState = rememberScrollState()
                var keySoundsOffset by remember { mutableIntStateOf(-1) }
                LaunchedEffect(keySoundsOffset) {
                    if (
                        keySoundsOffset >= 0 &&
                        intent.getStringExtra(EXTRA_QUICK_CYCLE_SECTION) == QUICK_CYCLE_SECTION_KEY_SOUNDS
                    ) {
                        scrollState.animateScrollTo(keySoundsOffset)
                    }
                }

                if (showCustomCaseColorEditor) {
                    AlertDialog(
                        onDismissRequest = { showCustomCaseColorEditor = false },
                        title = { Text("Custom case color") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                VisualColorPicker(
                                    label = "Case color",
                                    color = customCaseColor,
                                    onColorChange = { customCaseColor = it },
                                )
                                SettingsItem(
                                    title = "Metallic finish",
                                    subtitle = "Adds the existing reflective case treatment",
                                    action = {
                                        Checkbox(
                                            checked = customCaseMetallic,
                                            onCheckedChange = { customCaseMetallic = it },
                                        )
                                    },
                                    onClick = { customCaseMetallic = !customCaseMetallic },
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    preferences.edit {
                                        putInt("custom_case_color_r", android.graphics.Color.red(customCaseColor))
                                        putInt("custom_case_color_g", android.graphics.Color.green(customCaseColor))
                                        putInt("custom_case_color_b", android.graphics.Color.blue(customCaseColor))
                                        putBoolean("custom_case_color_metallic", customCaseMetallic)
                                    }
                                    showCustomCaseColorEditor = false
                                },
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomCaseColorEditor = false }) { Text("Cancel") }
                        },
                    )
                }

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Quick-cycle choices") },
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Toolbar taps only visit checked choices. At least one choice in every section stays enabled.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                        CycleSection(
                            title = "Keyboard layouts",
                            entries = KeyboardGeometry.entries.map { it.name to it.displayName },
                            selected = selectedGeometries,
                            icon = { Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary) },
                        ) { value ->
                            selectedGeometries = toggledCycleSelection(selectedGeometries, value)
                            preferences.edit { putStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, selectedGeometries) }
                        }
                        Box(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                if (keySoundsOffset < 0) {
                                    keySoundsOffset = coordinates.positionInParent().y.roundToInt()
                                }
                            },
                        ) {
                            CycleSection(
                                title = "Key sounds",
                                entries = SwitchType.entries.map { it.name to it.displayName },
                                selected = selectedSounds,
                                icon = { Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary) },
                            ) { value ->
                                selectedSounds = toggledCycleSelection(selectedSounds, value)
                                preferences.edit { putStringSet("cycle_key_sounds", selectedSounds) }
                            }
                        }
                        CycleSection(
                            title = "Input modes",
                            entries = InputMode.entries.map { it.preferenceKey to it.displayName },
                            selected = selectedModes,
                            icon = { Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.primary) },
                        ) { value ->
                            selectedModes = toggledCycleSelection(selectedModes, value)
                            preferences.edit { putStringSet("cycle_connection_modes", selectedModes) }
                        }
                        CycleSection(
                            title = "Keyboard themes",
                            entries = themes.map { theme -> theme.id to theme.name },
                            selected = selectedThemes,
                            icon = { Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                        ) { value ->
                            selectedThemes = toggledCycleSelection(selectedThemes, value)
                            preferences.edit { putStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, selectedThemes) }
                        }
                        CycleSection(
                            title = "Case colors",
                            entries = CaseColor.entries.map { it.name to it.displayName },
                            selected = selectedColors,
                            icon = { Icon(Icons.Default.ColorLens, null, tint = MaterialTheme.colorScheme.primary) },
                        ) { value ->
                            selectedColors = toggledCycleSelection(selectedColors, value)
                            preferences.edit { putStringSet("cycle_case_colors", selectedColors) }
                        }
                        SettingsCardGroup(
                            title = "Custom case color",
                            items = listOf(
                                SettingsItemData(
                                    title = "Edit custom color",
                                    subtitle = "${formatOpaqueHexColor(customCaseColor)}${if (customCaseMetallic) " · Metallic" else ""}",
                                    icon = { Icon(Icons.Default.ColorLens, null, tint = androidx.compose.ui.graphics.Color(customCaseColor)) },
                                    onClick = { showCustomCaseColorEditor = true },
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

@Composable
private fun CycleSection(
    title: String,
    entries: List<Pair<String, String>>,
    selected: Set<String>,
    icon: @Composable () -> Unit,
    onToggle: (String) -> Unit,
) {
    SettingsCardGroup(
        title = title,
        items = entries.map { (value, label) ->
            SettingsItemData(
                title = label,
                icon = icon,
                action = {
                    Checkbox(
                        checked = value in selected,
                        enabled = value !in selected || selected.size > 1,
                        onCheckedChange = { onToggle(value) },
                    )
                },
                onClick = {
                    if (value !in selected || selected.size > 1) onToggle(value)
                },
            )
        },
    )
}
