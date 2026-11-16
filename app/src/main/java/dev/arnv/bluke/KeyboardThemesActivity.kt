package dev.arnv.bluke

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.data.CYCLE_KEYBOARD_THEMES_PREFERENCE
import dev.arnv.bluke.data.KEYBOARD_GEOMETRY_PREFERENCE
import dev.arnv.bluke.data.KeyboardThemeRepository
import dev.arnv.bluke.data.KeyboardThemeSharing
import dev.arnv.bluke.ui.CUSTOM_KEYBOARD_THEME_PREFIX
import dev.arnv.bluke.ui.KeyColorCategory
import dev.arnv.bluke.ui.KeyLayoutInfo
import dev.arnv.bluke.ui.KeyboardCharacterLayout
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.KEYBOARD_STYLE_PREFERENCE
import dev.arnv.bluke.ui.KeyboardKeyStyle
import dev.arnv.bluke.ui.KeyboardStyle
import dev.arnv.bluke.ui.KeyboardThemeGenerator
import dev.arnv.bluke.ui.MIN_LEGEND_CONTRAST
import dev.arnv.bluke.ui.KeyboardThemeCatalog
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import dev.arnv.bluke.ui.TouchKeyboardLayouts
import dev.arnv.bluke.ui.TouchLayer
import dev.arnv.bluke.ui.VisualColorPicker
import dev.arnv.bluke.ui.normalizedCycleSelection
import dev.arnv.bluke.ui.selectedOrFirstEnabled
import dev.arnv.bluke.ui.toggledCycleSelection
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.theme.contrastRatio
import java.util.UUID
import kotlin.math.roundToInt

class KeyboardThemesActivity : ComponentActivity() {
    /** A theme link or file this screen was opened with, waiting to be read. */
    private val incomingImport = mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingImport.value = importUriFrom(intent)
    }

    private fun importUriFrom(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return when (uri.scheme) {
            KeyboardThemeSharing.LINK_SCHEME -> uri.takeIf { it.host == KeyboardThemeSharing.LINK_HOST }
            "content" -> uri
            else -> null // file:// and others are not accepted from outside the app.
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) incomingImport.value = importUriFrom(intent)
        setContent {
            MyApplicationTheme {
                val repository = remember { KeyboardThemeRepository(this) }
                var customThemes by remember { mutableStateOf(repository.listCustomThemes()) }
                var editingTheme by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                var pendingDelete by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                val preferences = remember { getSharedPreferences("app_prefs", MODE_PRIVATE) }
                val initialThemeIds = remember { repository.allThemes().map { it.id } }
                val initialCycleThemes = remember {
                    normalizedCycleSelection(preferences.getStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, null), initialThemeIds)
                }
                var cycleThemes by remember {
                    mutableStateOf(initialCycleThemes)
                }
                val storedThemeId = remember { repository.selectedThemeId() }
                var selectedThemeId by remember {
                    mutableStateOf(selectedOrFirstEnabled(storedThemeId, initialCycleThemes, initialThemeIds)!!)
                }
                LaunchedEffect(Unit) {
                    if (selectedThemeId != storedThemeId) repository.selectTheme(selectedThemeId)
                }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                val keyboardStyle = remember {
                    KeyboardStyle.fromPreference(preferences.getString(KEYBOARD_STYLE_PREFERENCE, null))
                }
                var showNewTheme by remember { mutableStateOf(false) }
                var showImport by remember { mutableStateOf(false) }
                var sharingTheme by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                var importCandidate by remember { mutableStateOf<KeyboardThemeDefinition?>(null) }
                var importError by remember { mutableStateOf<String?>(null) }
                // The editor reports whether it holds edits that are not saved yet.
                var editorDirty by remember { mutableStateOf(false) }
                var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }

                /** Runs [then] right away, or after confirming when unsaved edits would be lost. */
                fun leaveEditor(then: () -> Unit) {
                    if (editingTheme != null && editorDirty) pendingLeave = then else then()
                }

                fun closeEditor() {
                    editingTheme = null
                    editorDirty = false
                }

                // Opened from a simplybluke://theme link or a shared file.
                val incoming = incomingImport.value
                LaunchedEffect(incoming) {
                    if (incoming == null) return@LaunchedEffect
                    incomingImport.value = null
                    try {
                        importCandidate = if (incoming.scheme == KeyboardThemeSharing.LINK_SCHEME) {
                            KeyboardThemeSharing.parse(incoming.toString())
                        } else {
                            readThemeFromUri(contentResolver, incoming)
                        }
                    } catch (failure: KeyboardThemeSharing.ImportException) {
                        importError = failure.message
                    }
                }

                fun newThemeId() = "$CUSTOM_KEYBOARD_THEME_PREFIX${UUID.randomUUID()}"

                fun beginCopy(source: KeyboardThemeDefinition) {
                    editingTheme = source.copy(
                        id = newThemeId(),
                        name = "${source.name} copy".take(40),
                        editable = true,
                    )
                }

                fun useSavedTheme(saved: KeyboardThemeDefinition) {
                    repository.selectTheme(saved.id)
                    selectedThemeId = saved.id
                    customThemes = repository.listCustomThemes()
                    cycleThemes = cycleThemes + saved.id
                    preferences.edit { putStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, cycleThemes) }
                }

                BackHandler(enabled = editingTheme != null) { leaveEditor(::closeEditor) }
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    when {
                                        editingTheme == null -> "Keyboard themes"
                                        customThemes.any { it.id == editingTheme?.id } -> "Edit keyboard theme"
                                        else -> "New keyboard theme"
                                    },
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (editingTheme == null) finish() else leaveEditor(::closeEditor)
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
                            cycleThemes = cycleThemes,
                            onCreate = { showNewTheme = true },
                            onImport = { showImport = true },
                            onShare = { sharingTheme = it },
                            onSelect = { selected ->
                                val nextCycle = cycleThemes + selected.id
                                cycleThemes = nextCycle
                                preferences.edit { putStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, nextCycle) }
                                repository.selectTheme(selected.id)
                                selectedThemeId = selected.id
                            },
                            onCopy = ::beginCopy,
                            onEdit = { editingTheme = it },
                            onDelete = { pendingDelete = it },
                            onCycleToggle = { id ->
                                val nextCycle = toggledCycleSelection(cycleThemes, id)
                                val themeIds = repository.allThemes().map { it.id }
                                val nextSelected = selectedOrFirstEnabled(selectedThemeId, nextCycle, themeIds)!!
                                cycleThemes = nextCycle
                                selectedThemeId = nextSelected
                                repository.selectTheme(nextSelected)
                                preferences.edit { putStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, nextCycle) }
                            },
                        )
                    } else {
                        KeyboardThemeEditor(
                            modifier = Modifier.padding(padding),
                            initialTheme = theme,
                            keyboardStyle = keyboardStyle,
                            initialGeometry = KeyboardGeometry.fromPreference(
                                getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .getString(KEYBOARD_GEOMETRY_PREFERENCE, null),
                            ),
                            onDirtyChange = { editorDirty = it },
                            onCancel = { leaveEditor(::closeEditor) },
                            onSave = { updated ->
                                useSavedTheme(repository.save(updated))
                                closeEditor()
                            },
                        )
                    }
                }

                if (showNewTheme) {
                    NewThemeDialog(
                        currentTheme = remember(selectedThemeId) { repository.resolve(selectedThemeId) ?: repository.selectedTheme() },
                        onDismiss = { showNewTheme = false },
                        onCopy = { source ->
                            showNewTheme = false
                            beginCopy(source)
                        },
                        onCreate = { created ->
                            showNewTheme = false
                            editingTheme = created.copy(id = newThemeId())
                        },
                    )
                }
                if (showImport) {
                    ImportThemeDialog(
                        onDismiss = { showImport = false },
                        onParsed = {
                            showImport = false
                            importCandidate = it
                        },
                    )
                }
                importCandidate?.let { candidate ->
                    ImportPreviewDialog(
                        theme = candidate,
                        style = keyboardStyle,
                        onDismiss = { importCandidate = null },
                        onImport = { edit ->
                            importCandidate = null
                            val saved = repository.importTheme(candidate)
                            useSavedTheme(saved)
                            if (edit) {
                                leaveEditor {
                                    closeEditor()
                                    editingTheme = saved
                                }
                            }
                        },
                    )
                }
                importError?.let { message ->
                    AlertDialog(
                        onDismissRequest = { importError = null },
                        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        title = { Text("Couldn't import the theme") },
                        text = { Text(message) },
                        confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } },
                    )
                }
                sharingTheme?.let { ShareThemeDialog(theme = it, onDismiss = { sharingTheme = null }) }

                pendingLeave?.let { leave ->
                    AlertDialog(
                        onDismissRequest = { pendingLeave = null },
                        title = { Text("Discard changes?") },
                        text = { Text("Your changes to this theme haven't been saved.") },
                        confirmButton = {
                            TextButton(onClick = {
                                pendingLeave = null
                                leave()
                            }) { Text("Discard") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingLeave = null }) { Text("Keep editing") }
                        },
                    )
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
                                val themeIds = repository.allThemes().map { it.id }
                                val nextCycle = normalizedCycleSelection(cycleThemes - themeToDelete.id, themeIds)
                                val nextSelected = selectedOrFirstEnabled(repository.selectedThemeId(), nextCycle, themeIds)!!
                                cycleThemes = nextCycle
                                selectedThemeId = nextSelected
                                repository.selectTheme(nextSelected)
                                preferences.edit { putStringSet(CYCLE_KEYBOARD_THEMES_PREFERENCE, nextCycle) }
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
    cycleThemes: Set<String>,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onShare: (KeyboardThemeDefinition) -> Unit,
    onSelect: (KeyboardThemeDefinition) -> Unit,
    onCopy: (KeyboardThemeDefinition) -> Unit,
    onEdit: (KeyboardThemeDefinition) -> Unit,
    onDelete: (KeyboardThemeDefinition) -> Unit,
    onCycleToggle: (String) -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onCreate,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("New theme", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Import", style = MaterialTheme.typography.titleMedium)
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
                            includedInCycle = theme.id in cycleThemes,
                            canRemoveFromCycle = theme.id !in cycleThemes || cycleThemes.size > 1,
                            first = index == 0,
                            last = index == customThemes.lastIndex,
                            onSelect = { onSelect(theme) },
                            onCopy = { onCopy(theme) },
                            onShare = { onShare(theme) },
                            onEdit = { onEdit(theme) },
                            onDelete = { onDelete(theme) },
                            onCycleToggle = { onCycleToggle(theme.id) },
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
                        includedInCycle = theme.id in cycleThemes,
                        canRemoveFromCycle = theme.id !in cycleThemes || cycleThemes.size > 1,
                        first = index == 0,
                        last = index == KeyboardThemeCatalog.builtIns.lastIndex,
                        onSelect = { onSelect(theme) },
                        onCopy = { onCopy(theme) },
                        onShare = { onShare(theme) },
                        onCycleToggle = { onCycleToggle(theme.id) },
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
    includedInCycle: Boolean,
    canRemoveFromCycle: Boolean,
    first: Boolean,
    last: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onCycleToggle: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuExpanded by remember(theme.id) { mutableStateOf(false) }
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = includedInCycle,
                enabled = canRemoveFromCycle,
                onCheckedChange = { onCycleToggle() },
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${theme.name}")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                    )
                    onEdit?.let { edit ->
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                edit()
                            },
                        )
                    }
                    onDelete?.let { delete ->
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                delete()
                            },
                        )
                    }
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
    CASE("Case"),
    PLATE("Background"),
    ALPHA("Alpha keys"),
    MODIFIER("Control keys"),
    ACCENT("Accent keys"),
    KEY("One key"),
}

private enum class PreviewKind(val label: String) {
    PHYSICAL("Physical layout"),
    TOUCH("Touch layout"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyboardThemeEditor(
    modifier: Modifier,
    initialTheme: KeyboardThemeDefinition,
    keyboardStyle: KeyboardStyle,
    initialGeometry: KeyboardGeometry,
    onDirtyChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: (KeyboardThemeDefinition) -> Unit,
) {
    var name by remember(initialTheme.id) { mutableStateOf(initialTheme.name) }
    var plateArgb by remember(initialTheme.id) { mutableIntStateOf(initialTheme.plateArgb) }
    var caseArgb by remember(initialTheme.id) { mutableIntStateOf(initialTheme.caseArgb) }
    var caseMetallic by remember(initialTheme.id) { mutableStateOf(initialTheme.caseMetallic) }
    var alphaStyle by remember(initialTheme.id) { mutableStateOf(initialTheme.alphaStyle) }
    var modifierStyle by remember(initialTheme.id) { mutableStateOf(initialTheme.modifierStyle) }
    var accentStyle by remember(initialTheme.id) { mutableStateOf(initialTheme.accentStyle) }
    val overrides = remember(initialTheme.id) { mutableStateMapOf<String, KeyboardKeyStyle>().apply { putAll(initialTheme.keyOverrides) } }
    var previewKind by remember(initialTheme.id) { mutableStateOf(PreviewKind.PHYSICAL) }
    var previewGeometry by remember(initialTheme.id) { mutableStateOf(initialGeometry) }
    var previewLayer by remember(initialTheme.id) { mutableStateOf(TouchLayer.BASE) }
    var previewStyle by remember(initialTheme.id) { mutableStateOf(keyboardStyle) }
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
            ThemeEditTarget.CASE -> Unit
        }
    }

    val previewTheme = KeyboardThemeDefinition(
        id = initialTheme.id,
        name = name,
        plateArgb = plateArgb,
        caseArgb = caseArgb,
        caseMetallic = caseMetallic,
        alphaStyle = alphaStyle,
        modifierStyle = modifierStyle,
        accentStyle = accentStyle,
        keyOverrides = overrides.toMap(),
        editable = true,
    )
    // Only the theme itself counts; preview layout, style and the selected key are not saved.
    val dirty = previewTheme != initialTheme.copy(editable = true)
    LaunchedEffect(dirty) { onDirtyChange(dirty) }
    val selectedStyle = when (target) {
        ThemeEditTarget.ALPHA -> alphaStyle
        ThemeEditTarget.MODIFIER -> modifierStyle
        ThemeEditTarget.ACCENT -> accentStyle
        ThemeEditTarget.KEY -> selectedKey?.let { overrides[it.styleId] ?: groupStyleFor(it) }
        ThemeEditTarget.PLATE -> null
        ThemeEditTarget.CASE -> null
    }
    val lowContrastParts = KeyboardThemeGenerator.lowContrastParts(previewTheme)
    val touchRows = remember(previewLayer) { TouchKeyboardLayouts.getLayout(previewLayer) }

    fun fixLowContrast() {
        fun KeyboardKeyStyle.fixed() =
            if (contrastRatio(backgroundArgb, legendArgb) >= MIN_LEGEND_CONTRAST) this
            else copy(legendArgb = KeyboardThemeGenerator.readableLegend(backgroundArgb, legendArgb))
        alphaStyle = alphaStyle.fixed()
        modifierStyle = modifierStyle.fixed()
        accentStyle = accentStyle.fixed()
        overrides.keys.toList().forEach { key -> overrides[key] = overrides.getValue(key).fixed() }
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
            supportingText = "Preview on a physical layout or the Touch layout. This does not change the layout used on the keyboard screen.",
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PreviewKind.entries.forEach { kind ->
                    FilterChip(
                        selected = previewKind == kind,
                        onClick = {
                            previewKind = kind
                            selectedKey = null
                            if (target == ThemeEditTarget.KEY) target = ThemeEditTarget.ALPHA
                        },
                        label = { Text(kind.label) },
                    )
                }
                KeyboardStyle.entries.forEach { style ->
                    FilterChip(
                        selected = previewStyle == style,
                        onClick = { previewStyle = style },
                        label = { Text(if (style == KeyboardStyle.MINIMAL) "Flat keys" else "Keycaps") },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (previewKind == PreviewKind.PHYSICAL) {
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
                } else {
                    listOf(TouchLayer.BASE to "ABC", TouchLayer.SYMBOLS to "?123", TouchLayer.FN to "Fn").forEach { (layer, label) ->
                        FilterChip(
                            selected = previewLayer == layer,
                            onClick = {
                                previewLayer = layer
                                selectedKey = null
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
            ThemePreviewKeyboard(
                theme = previewTheme,
                style = previewStyle,
                height = if (previewKind == PreviewKind.TOUCH) 240.dp else 180.dp,
                geometry = previewGeometry,
                rowsOverride = if (previewKind == PreviewKind.TOUCH) touchRows else null,
                touchLayout = previewKind == PreviewKind.TOUCH,
                selectedStyleId = selectedKey?.styleId,
                onKeySelected = { key ->
                    selectedKey = key
                    target = ThemeEditTarget.KEY
                },
            )
            Text(
                "Tap a key for a one-key override, or edit a key group below. A one-key override " +
                    "colors that key in every layout, physical and Touch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ThemeEditorSection(
            title = "Customize keys",
            supportingText = "Change the keyboard case, background, a whole key group, or the selected key.",
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

            if (target == ThemeEditTarget.CASE) {
                VisualColorPicker("Keyboard case", caseArgb) { caseArgb = it }
                FilterChip(
                    selected = caseMetallic,
                    onClick = { caseMetallic = !caseMetallic },
                    label = { Text("Metallic finish") },
                )
            } else if (target == ThemeEditTarget.PLATE) {
                VisualColorPicker("Keyboard background", plateArgb) { plateArgb = it }
            } else if (selectedStyle != null) {
                VisualColorPicker("Key color", selectedStyle.backgroundArgb) { color ->
                    updateTargetStyle { it.copy(backgroundArgb = color) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                VisualColorPicker("Key text color", selectedStyle.legendArgb) { color ->
                    updateTargetStyle { it.copy(legendArgb = color) }
                }
                val ratio = contrastRatio(selectedStyle.backgroundArgb, selectedStyle.legendArgb)
                Text(
                    "Text contrast: ${"%.1f".format(ratio)}:1" +
                        if (ratio < MIN_LEGEND_CONTRAST) " (hard to read)" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ratio < MIN_LEGEND_CONTRAST) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        if (lowContrastParts.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Key text may be hard to read", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "${lowContrastParts.joinToString()}: text and key colors are too close " +
                            "(below ${MIN_LEGEND_CONTRAST.toInt()}:1).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = ::fixLowContrast) { Text("Fix text colors") }
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

private enum class NewThemeStart { COPY, COLOR, BLANK }

@Composable
private fun NewThemeDialog(
    currentTheme: KeyboardThemeDefinition,
    onDismiss: () -> Unit,
    onCopy: (KeyboardThemeDefinition) -> Unit,
    onCreate: (KeyboardThemeDefinition) -> Unit,
) {
    var start by remember { mutableStateOf(NewThemeStart.COLOR) }
    var baseColor by remember { mutableIntStateOf(currentTheme.accentStyle.backgroundArgb) }
    var dark by remember { mutableStateOf(true) }
    val generated = KeyboardThemeGenerator.fromBaseColor("preview", baseColor, dark)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New theme") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NewThemeOption(
                    selected = start == NewThemeStart.COLOR,
                    title = "Start from a color",
                    subtitle = "Pick one color; the rest of the keyboard is made to match.",
                    onClick = { start = NewThemeStart.COLOR },
                )
                if (start == NewThemeStart.COLOR) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VisualColorPicker("Base color", baseColor) { baseColor = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = dark, onClick = { dark = true }, label = { Text("Dark") })
                            FilterChip(selected = !dark, onClick = { dark = false }, label = { Text("Light") })
                        }
                        PaletteDots(
                            listOf(
                                generated.caseArgb,
                                generated.plateArgb,
                                generated.alphaStyle.backgroundArgb,
                                generated.modifierStyle.backgroundArgb,
                                generated.accentStyle.backgroundArgb,
                            ),
                        )
                    }
                }
                NewThemeOption(
                    selected = start == NewThemeStart.BLANK,
                    title = "Start blank",
                    subtitle = "A plain grey keyboard to color by hand.",
                    onClick = { start = NewThemeStart.BLANK },
                )
                NewThemeOption(
                    selected = start == NewThemeStart.COPY,
                    title = "Copy ${currentTheme.name}",
                    subtitle = "An editable copy of the theme you use now; the original stays unchanged.",
                    onClick = { start = NewThemeStart.COPY },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (start) {
                    NewThemeStart.COPY -> onCopy(currentTheme)
                    NewThemeStart.COLOR -> onCreate(generated)
                    NewThemeStart.BLANK -> onCreate(KeyboardThemeGenerator.blank("blank"))
                }
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NewThemeOption(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
