package dev.arnv.bluke

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.sound.CustomSoundPack
import dev.arnv.bluke.sound.CustomSoundPackRepository
import dev.arnv.bluke.sound.SELECTED_BUILT_IN_SOUND_PREFERENCE
import dev.arnv.bluke.sound.SoundPackImportResult
import dev.arnv.bluke.sound.SwitchType
import dev.arnv.bluke.sound.builtInSoundProfileId
import dev.arnv.bluke.sound.customSoundProfileId
import dev.arnv.bluke.sound.selectedBuiltInSound
import dev.arnv.bluke.ui.SettingsItem
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundPacksActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = CustomSoundPackRepository(applicationContext)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                var packs by remember { mutableStateOf(repository.listPacks()) }
                var selectedProfileId by remember {
                    mutableStateOf(
                        repository.selectedPack()?.let { customSoundProfileId(it.id) }
                            ?: builtInSoundProfileId(selectedBuiltInSound(preferences)),
                    )
                }
                var importing by remember { mutableStateOf(false) }
                var pendingDeletion by remember { mutableStateOf<CustomSoundPack?>(null) }
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }
                val importer = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        importing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use(repository::importZip)
                                    ?: SoundPackImportResult.Failure("Could not read the selected file.")
                            }
                            importing = false
                            when (result) {
                                is SoundPackImportResult.Success -> {
                                    packs = repository.listPacks()
                                    selectedProfileId = customSoundProfileId(result.pack.id)
                                    snackbarHostState.showSnackbar("Imported and selected ${result.pack.name}.")
                                }
                                is SoundPackImportResult.Duplicate -> {
                                    snackbarHostState.showSnackbar("${result.pack.name} is already imported.")
                                }
                                is SoundPackImportResult.Failure -> {
                                    snackbarHostState.showSnackbar(result.message)
                                }
                            }
                        }
                    }
                }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

                pendingDeletion?.let { pack ->
                    AlertDialog(
                        onDismissRequest = { pendingDeletion = null },
                        title = { Text("Delete ${pack.name}?") },
                        text = { Text("This removes the imported pack from Bluke. Built-in sounds are not affected.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    pendingDeletion = null
                                    scope.launch {
                                        val deleted = withContext(Dispatchers.IO) {
                                            repository.deletePack(pack.id)
                                        }
                                        if (deleted) {
                                            packs = repository.listPacks()
                                            if (selectedProfileId == customSoundProfileId(pack.id)) {
                                                selectedProfileId = builtInSoundProfileId(selectedBuiltInSound(preferences))
                                            }
                                            snackbarHostState.showSnackbar("Deleted ${pack.name}.")
                                        } else {
                                            snackbarHostState.showSnackbar("Could not delete ${pack.name}.")
                                        }
                                    }
                                },
                            ) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") }
                        },
                    )
                }

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Key sounds") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (!importing) {
                                    importer.launch(arrayOf("application/zip", "application/octet-stream"))
                                }
                            },
                            icon = {
                                if (importing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            },
                            text = { Text(if (importing) "Importing…" else "Import pack") },
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Choose one sound profile for every key press. Imported Mechvibes packs live beside Bluke's built-in switch sounds and can be reached by the keyboard toolbar cycle.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            SettingsItem(
                                title = "Quick-cycle choices",
                                subtitle = "Choose which built-in sounds appear when cycling from the keyboard toolbar",
                                icon = {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                action = {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                },
                                onClick = {
                                    startActivity(
                                        Intent(this@SoundPacksActivity, QuickCycleActivity::class.java)
                                            .putExtra(
                                                EXTRA_QUICK_CYCLE_SECTION,
                                                QUICK_CYCLE_SECTION_KEY_SOUNDS,
                                            ),
                                    )
                                },
                            )
                        }
                        Text(
                            "Sound profiles",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val choices = SwitchType.entries.map { SoundChoice.BuiltIn(it) } +
                            packs.map { SoundChoice.Imported(it) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            choices.forEachIndexed { index, choice ->
                                SoundProfileChoice(
                                    title = choice.title,
                                    subtitle = choice.subtitle,
                                    selected = selectedProfileId == choice.id,
                                    first = index == 0,
                                    last = index == choices.lastIndex,
                                    onDelete = (choice as? SoundChoice.Imported)?.let {
                                        { pendingDeletion = it.pack }
                                    },
                                    onSelect = {
                                        when (choice) {
                                            is SoundChoice.BuiltIn -> {
                                                repository.select(null)
                                                preferences.edit {
                                                    putString(
                                                        SELECTED_BUILT_IN_SOUND_PREFERENCE,
                                                        choice.switchType.name,
                                                    )
                                                }
                                            }
                                            is SoundChoice.Imported -> repository.select(choice.pack.id)
                                        }
                                        selectedProfileId = choice.id
                                    },
                                )
                            }
                        }
                        if (packs.isEmpty()) {
                            Text(
                                "Imported packs will appear in this same list.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(96.dp))
                    }
                }
            }
        }
    }
}

private sealed interface SoundChoice {
    val id: String
    val title: String
    val subtitle: String

    data class BuiltIn(val switchType: SwitchType) : SoundChoice {
        override val id = builtInSoundProfileId(switchType)
        override val title = switchType.displayName
        override val subtitle = "Built-in"
    }

    data class Imported(val pack: CustomSoundPack) : SoundChoice {
        override val id = customSoundProfileId(pack.id)
        override val title = pack.name
        override val subtitle = "Imported Mechvibes pack"
    }
}

@Composable
private fun SoundProfileChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = if (first) 28.dp else 4.dp,
            topEnd = if (first) 28.dp else 4.dp,
            bottomStart = if (last) 28.dp else 4.dp,
            bottomEnd = if (last) 28.dp else 4.dp,
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected")
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete $title")
                }
            }
        }
    }
}
