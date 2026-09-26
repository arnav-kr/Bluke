package dev.arnv.bluke

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
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
import dev.arnv.bluke.sound.saveSoundCycleSelection
import dev.arnv.bluke.sound.soundCycleSelection
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.normalizedCycleSelection
import dev.arnv.bluke.ui.selectedOrFirstEnabled
import dev.arnv.bluke.ui.toggledCycleSelection
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
                val initialProfileIds = remember {
                    SwitchType.entries.map(::builtInSoundProfileId) + packs.map { customSoundProfileId(it.id) }
                }
                val initialCycleSelection = remember {
                    soundCycleSelection(preferences, packs.map { it.id })
                }
                var cycleSelection by remember {
                    mutableStateOf(initialCycleSelection)
                }
                val storedProfileId = remember {
                    repository.selectedPack()?.let { customSoundProfileId(it.id) }
                        ?: builtInSoundProfileId(selectedBuiltInSound(preferences))
                }
                var selectedProfileId by remember {
                    mutableStateOf(selectedOrFirstEnabled(storedProfileId, initialCycleSelection, initialProfileIds)!!)
                }
                var importing by remember { mutableStateOf(false) }
                var pendingDeletion by remember { mutableStateOf<CustomSoundPack?>(null) }
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                fun selectProfile(profileId: String) {
                    val builtIn = SwitchType.entries.firstOrNull { builtInSoundProfileId(it) == profileId }
                    if (builtIn != null) {
                        repository.select(null)
                        preferences.edit { putString(SELECTED_BUILT_IN_SOUND_PREFERENCE, builtIn.name) }
                        selectedProfileId = profileId
                        return
                    }
                    val custom = repository.listPacks().firstOrNull { customSoundProfileId(it.id) == profileId }
                    if (custom != null) {
                        repository.select(custom.id)
                        selectedProfileId = profileId
                    }
                }

                LaunchedEffect(Unit) {
                    if (selectedProfileId != storedProfileId) selectProfile(selectedProfileId)
                }
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
                                    cycleSelection = cycleSelection + customSoundProfileId(result.pack.id)
                                    saveSoundCycleSelection(preferences, cycleSelection)
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
                                            val updatedPacks = repository.listPacks()
                                            packs = updatedPacks
                                            val profileIds = SwitchType.entries.map(::builtInSoundProfileId) +
                                                updatedPacks.map { customSoundProfileId(it.id) }
                                            val nextCycle = normalizedCycleSelection(
                                                cycleSelection - customSoundProfileId(pack.id),
                                                profileIds,
                                            )
                                            val nextSelected = selectedOrFirstEnabled(selectedProfileId, nextCycle, profileIds)!!
                                            cycleSelection = nextCycle
                                            saveSoundCycleSelection(preferences, nextCycle)
                                            selectProfile(nextSelected)
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
                                    includedInCycle = choice.id in cycleSelection,
                                    canRemoveFromCycle = choice.id !in cycleSelection || cycleSelection.size > 1,
                                    first = index == 0,
                                    last = index == choices.lastIndex,
                                    onDelete = (choice as? SoundChoice.Imported)?.let {
                                        { pendingDeletion = it.pack }
                                    },
                                    onSelect = {
                                        val nextCycle = cycleSelection + choice.id
                                        cycleSelection = nextCycle
                                        saveSoundCycleSelection(preferences, nextCycle)
                                        selectProfile(choice.id)
                                    },
                                    onCycleToggle = {
                                        if (choice.id !in cycleSelection || cycleSelection.size > 1) {
                                            val profileIds = choices.map { it.id }
                                            val nextCycle = toggledCycleSelection(cycleSelection, choice.id)
                                            val nextSelected = selectedOrFirstEnabled(selectedProfileId, nextCycle, profileIds)!!
                                            cycleSelection = nextCycle
                                            saveSoundCycleSelection(preferences, nextCycle)
                                            selectProfile(nextSelected)
                                        }
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
    includedInCycle: Boolean,
    canRemoveFromCycle: Boolean,
    first: Boolean,
    last: Boolean,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
    onCycleToggle: () -> Unit,
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
            Checkbox(
                checked = includedInCycle,
                enabled = canRemoveFromCycle,
                onCheckedChange = { onCycleToggle() },
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete $title")
                }
            }
        }
    }
}
