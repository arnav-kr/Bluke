package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                val repository = remember { KeyboardThemeRepository(this) }
                var customThemes by remember { mutableStateOf(repository.listCustomThemes()) }
                var selectedThemeId by remember { mutableStateOf(repository.selectedThemeId()) }
                var editingTheme by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                var pendingDelete by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

                fun beginCopy(source: KeyboardThemeDefinition) {
                    editingTheme = source.copy(
                        id = "$CUSTOM_KEYBOARD_THEME_PREFIX${UUID.randomUUID()}",
                        name = "${source.name} copy",
                        editable = true,
                    )
                }

                BackHandler(enabled = editingTheme != null) { editingTheme = null }
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(if (editingTheme == null) "Keyboard themes" else "Edit keyboard theme") },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (editingTheme == null) finish() else editingTheme = null
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Choose a keyboard look. Layout and letter arrangement stay independent.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        item {
            val selectedThemeName = (customThemes + KeyboardThemeCatalog.builtIns)
                .firstOrNull { it.id == selectedThemeId }
                ?.name
                ?: "selected theme"
            FilledTonalButton(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text("Create from $selectedThemeName", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Makes an editable copy; the original theme stays unchanged",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (customThemes.isNotEmpty()) {
            item { ThemeSectionTitle("Your themes") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    customThemes.forEachIndexed { index, theme ->
                        KeyboardThemeCard(
                            theme = theme,
                            selected = selectedThemeId == theme.id,
                            first = index == 0,
                            last = index == customThemes.lastIndex,
                            onSelect = { onSelect(theme) },
                            onCopy = { onCopy(theme) },
                            onEdit = { onEdit(theme) },
                            onDelete = { onDelete(theme) },
                        )
                    }
                }
            }
        }
        item { ThemeSectionTitle("Built-in themes") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KeyboardThemeCatalog.builtIns.forEachIndexed { index, theme ->
                    KeyboardThemeCard(
                        theme = theme,
                        selected = selectedThemeId == theme.id,
                        first = index == 0,
                        last = index == KeyboardThemeCatalog.builtIns.lastIndex,
                        onSelect = { onSelect(theme) },
                        onCopy = { onCopy(theme) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
    first: Boolean,
    last: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (first) 28.dp else 4.dp,
            topEnd = if (first) 28.dp else 4.dp,
            bottomStart = if (last) 28.dp else 4.dp,
            bottomEnd = if (last) 28.dp else 4.dp,
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyboardThemeSwatches(theme)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    theme.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
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

@OptIn(ExperimentalLayoutApi::class)
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ThemeEditorSection(
            title = "Theme details",
            supportingText = "Give this keyboard-only theme a name you will recognize.",
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Theme name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ThemeEditorSection(
            title = "Live preview",
            supportingText = "Pick a geometry to preview. This does not change the layout saved on the keyboard screen.",
        ) {
            Text(
                "Preview layout",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                "Tap a key for a one-key override, or edit a key group below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ThemeEditorSection(
            title = "Customize keys",
            supportingText = "Change the keyboard background, a whole key group, or the selected key.",
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (target == ThemeEditTarget.PLATE) {
                VisualColorEditor("Keyboard background", plateArgb) { plateArgb = it }
            } else if (selectedStyle != null) {
                VisualColorEditor("Key color", selectedStyle.backgroundArgb) { color ->
                    updateTargetStyle { it.copy(backgroundArgb = color) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                VisualColorEditor("Key text color", selectedStyle.legendArgb) { color ->
                    updateTargetStyle { it.copy(legendArgb = color) }
                }
                Text(
                    "Text size: ${(selectedStyle.legendScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = selectedStyle.legendScale,
                    onValueChange = { scale -> updateTargetStyle { it.copy(legendScale = scale) } },
                    valueRange = 0.7f..1.5f,
                )
                if (target == ThemeEditTarget.KEY) {
                    OutlinedButton(
                        onClick = { selectedKey?.let { overrides.remove(it.styleId) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset this key to its group")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(previewTheme.copy(name = name.trim())) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Palette, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeEditorSection(
    title: String,
    supportingText: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun VisualColorEditor(
    label: String,
    color: Int,
    onColorChange: (Int) -> Unit,
) {
    val initialHsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
    }
    var hue by remember(color) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(color) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(color) { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember(color) { mutableStateOf(formatOpaqueHexColor(color)) }
    var showPreciseInput by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(color)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                formatOpaqueHexColor(color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
            .pointerInput(hue) {
                awaitPointerEventScope {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                            brightness = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                            onColorChange(
                                android.graphics.Color.HSVToColor(
                                    floatArrayOf(hue, saturation, brightness),
                                ),
                            )
                            change.consume()
                        }
                    }
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                x = saturation * size.width,
                y = (1f - brightness) * size.height,
            ),
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .pointerInput(saturation, brightness) {
                awaitPointerEventScope {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            hue = ((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                            onColorChange(
                                android.graphics.Color.HSVToColor(
                                    floatArrayOf(hue, saturation, brightness),
                                ),
                            )
                            change.consume()
                        }
                    }
                }
            },
    ) {
        drawRect(
            Brush.horizontalGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                ),
            ),
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(
                x = (hue / 360f) * size.width,
                y = size.height / 2f,
            ),
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    TextButton(onClick = { showPreciseInput = !showPreciseInput }) {
        Text(if (showPreciseInput) "Hide precise value" else "Enter a precise hex value")
    }
    if (showPreciseInput) {
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
    }
}
