package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.data.KEYBOARD_GEOMETRY_PREFERENCE
import dev.arnv.bluke.data.KeyboardThemeRepository
import dev.arnv.bluke.ui.CUSTOM_KEYBOARD_THEME_PREFIX
import dev.arnv.bluke.ui.KeyColorCategory
import dev.arnv.bluke.ui.KeyLayoutInfo
import dev.arnv.bluke.ui.KeyboardCharacterLayout
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.KeyboardKeyStyle
import dev.arnv.bluke.ui.KeyboardThemeCatalog
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import dev.arnv.bluke.ui.KeyboardView
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.blueChannel
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor
import dev.arnv.bluke.ui.theme.greenChannel
import dev.arnv.bluke.ui.theme.opaqueRgb
import dev.arnv.bluke.ui.theme.parseOpaqueHexColor
import dev.arnv.bluke.ui.theme.redChannel
import java.util.UUID
import kotlin.math.roundToInt

class KeyboardThemesActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                val repository = remember { KeyboardThemeRepository(this) }
                var customThemes by remember { mutableStateOf(repository.listCustomThemes()) }
                var selectedThemeId by remember { mutableStateOf(repository.selectedThemeId()) }
                var editingTheme by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                var pendingDelete by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }

                fun beginCopy(source: KeyboardThemeDefinition) {
                    editingTheme = source.copy(
                        id = "$CUSTOM_KEYBOARD_THEME_PREFIX${UUID.randomUUID()}",
                        name = "${source.name} copy",
                        editable = true,
                    )
                }

                BackHandler(enabled = editingTheme != null) { editingTheme = null }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (editingTheme == null) "Keyboard themes" else "Edit keyboard theme") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (editingTheme == null) finish() else editingTheme = null
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                        )
                    },
                    floatingActionButton = {
                        if (editingTheme == null) {
                            FloatingActionButton(onClick = {
                                beginCopy(repository.selectedTheme())
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Create custom keyboard theme")
                            }
                        }
                    },
                ) { padding ->
                    val theme = editingTheme
                    if (theme == null) {
                        KeyboardThemeLibrary(
                            modifier = Modifier.padding(padding),
                            selectedThemeId = selectedThemeId,
                            customThemes = customThemes,
                            onCreate = { beginCopy(repository.selectedTheme()) },
                            onSelect = { selected ->
                                repository.selectTheme(selected.id)
                                selectedThemeId = selected.id
                            },
                            onCopy = ::beginCopy,
                            onEdit = { editingTheme = it },
                            onDelete = { pendingDelete = it },
                        )
                    } else {
                        KeyboardThemeEditor(
                            modifier = Modifier.padding(padding),
                            initialTheme = theme,
                            initialGeometry = KeyboardGeometry.fromPreference(
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .getString(KEYBOARD_GEOMETRY_PREFERENCE, null),
                            ),
                            onCancel = { editingTheme = null },
                            onSave = { updated ->
                                val saved = repository.save(updated)
                                repository.selectTheme(saved.id)
                                selectedThemeId = saved.id
                                customThemes = repository.listCustomThemes()
                                editingTheme = null
                            },
                        )
                    }
                }

                pendingDelete?.let { themeToDelete ->
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Delete ${themeToDelete.name}?") },
                        text = { Text("This removes the custom keyboard theme. Built-in themes are not affected.") },
                        confirmButton = {
                            TextButton(onClick = {
                                repository.delete(themeToDelete.id)
                                customThemes = repository.listCustomThemes()
                                selectedThemeId = repository.selectedThemeId()
                                pendingDelete = null
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeLibrary(
    modifier: Modifier,
    selectedThemeId: String,
    customThemes: List<KeyboardThemeDefinition>,
    onCreate: () -> Unit,
    onSelect: (KeyboardThemeDefinition) -> Unit,
    onCopy: (KeyboardThemeDefinition) -> Unit,
    onEdit: (KeyboardThemeDefinition) -> Unit,
    onDelete: (KeyboardThemeDefinition) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreate),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Create custom theme", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Copy the selected theme, then edit groups or individual keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (customThemes.isNotEmpty()) {
            item { ThemeSectionTitle("Your themes") }
            items(customThemes, key = { it.id }) { theme ->
                KeyboardThemeCard(
                    theme = theme,
                    selected = selectedThemeId == theme.id,
                    onSelect = { onSelect(theme) },
                    onCopy = { onCopy(theme) },
                    onEdit = { onEdit(theme) },
                    onDelete = { onDelete(theme) },
                )
            }
        }
        item { ThemeSectionTitle("Built-in themes") }
        items(KeyboardThemeCatalog.builtIns, key = { it.id }) { theme ->
            KeyboardThemeCard(
                theme = theme,
                selected = selectedThemeId == theme.id,
                onSelect = { onSelect(theme) },
                onCopy = { onCopy(theme) },
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ThemeSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun KeyboardThemeCard(
    theme: KeyboardThemeDefinition,
    selected: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyboardThemeSwatches(theme)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(theme.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (theme.editable) "Custom" else "Built-in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy ${theme.name}")
            }
            onEdit?.let { edit ->
                IconButton(onClick = edit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit ${theme.name}")
                }
            }
            onDelete?.let { delete ->
                IconButton(onClick = delete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${theme.name}")
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeSwatches(theme: KeyboardThemeDefinition) {
    Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
        KeyboardThemeCatalog.previewColors(theme).forEach { color ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private enum class ThemeEditTarget(val label: String) {
    PLATE("Background"),
    ALPHA("Alpha keys"),
    MODIFIER("Control keys"),
    ACCENT("Accent keys"),
    KEY("One key"),
}

@Composable
private fun KeyboardThemeEditor(
    modifier: Modifier,
    initialTheme: KeyboardThemeDefinition,
    initialGeometry: KeyboardGeometry,
    onCancel: () -> Unit,
    onSave: (KeyboardThemeDefinition) -> Unit,
) {
    var name by remember(initialTheme.id) { mutableStateOf(initialTheme.name) }
    var plateArgb by remember(initialTheme.id) { mutableIntStateOf(initialTheme.plateArgb) }
    var alphaStyle by remember(initialTheme.id) { mutableStateOf(initialTheme.alphaStyle) }
    var modifierStyle by remember(initialTheme.id) { mutableStateOf(initialTheme.modifierStyle) }
    var accentStyle by remember(initialTheme.id) { mutableStateOf(initialTheme.accentStyle) }
    val overrides = remember(initialTheme.id) { mutableStateMapOf<String, KeyboardKeyStyle>().apply { putAll(initialTheme.keyOverrides) } }
    var previewGeometry by remember(initialTheme.id) { mutableStateOf(initialGeometry) }
    var target by remember(initialTheme.id) { mutableStateOf(ThemeEditTarget.ALPHA) }
    var selectedKey by remember(initialTheme.id) { mutableStateOf<KeyLayoutInfo?>(null) }

    fun groupStyleFor(key: KeyLayoutInfo): KeyboardKeyStyle = when (key.category) {
        KeyColorCategory.ALPHA -> alphaStyle
        KeyColorCategory.MOD -> modifierStyle
        KeyColorCategory.ACCENT -> accentStyle
    }

    fun updateTargetStyle(transform: (KeyboardKeyStyle) -> KeyboardKeyStyle) {
        when (target) {
            ThemeEditTarget.ALPHA -> alphaStyle = transform(alphaStyle)
            ThemeEditTarget.MODIFIER -> modifierStyle = transform(modifierStyle)
            ThemeEditTarget.ACCENT -> accentStyle = transform(accentStyle)
            ThemeEditTarget.KEY -> selectedKey?.let { key ->
                overrides[key.styleId] = transform(overrides[key.styleId] ?: groupStyleFor(key))
            }
            ThemeEditTarget.PLATE -> Unit
        }
    }

    val previewTheme = KeyboardThemeDefinition(
        id = initialTheme.id,
        name = name,
        plateArgb = plateArgb,
        alphaStyle = alphaStyle,
        modifierStyle = modifierStyle,
        accentStyle = accentStyle,
        keyOverrides = overrides.toMap(),
        editable = true,
    )
    val selectedStyle = when (target) {
        ThemeEditTarget.ALPHA -> alphaStyle
        ThemeEditTarget.MODIFIER -> modifierStyle
        ThemeEditTarget.ACCENT -> accentStyle
        ThemeEditTarget.KEY -> selectedKey?.let { overrides[it.styleId] ?: groupStyleFor(it) }
        ThemeEditTarget.PLATE -> null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Theme name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Preview layout", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyboardGeometry.entries.forEach { geometry ->
                FilterChip(
                    selected = previewGeometry == geometry,
                    onClick = {
                        previewGeometry = geometry
                        selectedKey = null
                    },
                    label = { Text(geometry.displayName) },
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .padding(8.dp),
            ) {
                KeyboardView(
                    geometry = previewGeometry,
                    theme = previewTheme,
                    characterLayout = KeyboardCharacterLayout.US_QWERTY,
                    activePressedKeys = emptyList(),
                    isCapsLockActive = false,
                    isNumLockActive = false,
                    isScrollLockActive = false,
                    selectedStyleId = selectedKey?.styleId,
                    onKeySelected = { key ->
                        selectedKey = key
                        target = ThemeEditTarget.KEY
                    },
                    onKeyPressChange = { _, _ -> },
                )
            }
        }
        Text(
            "Tap a key in the preview for a one-key override, or edit a group below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeEditTarget.entries.forEach { editTarget ->
                FilterChip(
                    selected = target == editTarget,
                    enabled = editTarget != ThemeEditTarget.KEY || selectedKey != null,
                    onClick = { target = editTarget },
                    label = {
                        Text(
                            if (editTarget == ThemeEditTarget.KEY && selectedKey != null) {
                                "Key: ${selectedKey?.legend?.ifEmpty { "Space" }}"
                            } else {
                                editTarget.label
                            },
                        )
                    },
                )
            }
        }

        if (target == ThemeEditTarget.PLATE) {
            RgbColorEditor("Keyboard background", plateArgb) { plateArgb = it }
        } else if (selectedStyle != null) {
            RgbColorEditor("Key color", selectedStyle.backgroundArgb) { color ->
                updateTargetStyle { it.copy(backgroundArgb = color) }
            }
            RgbColorEditor("Key text color", selectedStyle.legendArgb) { color ->
                updateTargetStyle { it.copy(legendArgb = color) }
            }
            Text("Text size: ${(selectedStyle.legendScale * 100).roundToInt()}%")
            Slider(
                value = selectedStyle.legendScale,
                onValueChange = { scale -> updateTargetStyle { it.copy(legendScale = scale) } },
                valueRange = 0.7f..1.5f,
            )
            if (target == ThemeEditTarget.KEY) {
                OutlinedButton(onClick = {
                    selectedKey?.let { overrides.remove(it.styleId) }
                }) { Text("Reset this key to its group") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(previewTheme.copy(name = name.trim())) },
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save theme")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RgbColorEditor(
    label: String,
    color: Int,
    onColorChange: (Int) -> Unit,
) {
    var hexText by remember(color) { mutableStateOf(formatOpaqueHexColor(color)) }
    val red = redChannel(color)
    val green = greenChannel(color)
    val blue = blueChannel(color)

    Text(label, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = hexText,
        onValueChange = { candidate ->
            val normalized = candidate.uppercase().filterIndexed { index, character ->
                character.isDigit() || character in 'A'..'F' || (index == 0 && character == '#')
            }.let { filtered -> if (filtered.startsWith('#')) filtered.take(7) else filtered.take(6) }
            hexText = normalized
            parseOpaqueHexColor(normalized)?.let(onColorChange)
        },
        label = { Text("Hex color") },
        singleLine = true,
        isError = parseOpaqueHexColor(hexText) == null,
        supportingText = if (parseOpaqueHexColor(hexText) == null) ({ Text("Use #RRGGBB") }) else null,
        trailingIcon = {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color)),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    RgbChannelSlider("R", red, Color.Red) { value -> onColorChange(opaqueRgb(value, green, blue)) }
    RgbChannelSlider("G", green, Color.Green) { value -> onColorChange(opaqueRgb(red, value, blue)) }
    RgbChannelSlider("B", blue, Color.Blue) { value -> onColorChange(opaqueRgb(red, green, value)) }
}

@Composable
private fun RgbChannelSlider(
    label: String,
    value: Int,
    channelColor: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.width(16.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = channelColor,
                activeTrackColor = channelColor,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(value.toString(), modifier = Modifier.width(32.dp))
    }
}
