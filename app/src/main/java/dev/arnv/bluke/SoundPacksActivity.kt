package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.sound.CustomSoundPack
import dev.arnv.bluke.sound.CustomSoundPackRepository
import dev.arnv.bluke.sound.SoundPackImportResult
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundPacksActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = CustomSoundPackRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                var packs by remember { mutableStateOf(repository.listPacks()) }
                var selectedId by remember { mutableStateOf(repository.selectedPackId()) }
                var message by remember { mutableStateOf<String?>(null) }
                var importing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val importer = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        importing = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use(repository::importZip)
                                    ?: SoundPackImportResult.Failure("Could not read the selected file.")
                            }
                            importing = false
                            when (result) {
                                is SoundPackImportResult.Success -> {
                                    packs = repository.listPacks()
                                    selectedId = result.pack.id
                                    message = "Imported and selected ${result.pack.name}."
                                }
                                is SoundPackImportResult.Failure -> message = result.message
                            }
                        }
                    }
                }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Custom key sounds") },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            scrollBehavior = scrollBehavior,
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
                            "Import Mechvibes V2 multi-file ZIP packs. The active pack is preloaded for low-latency playback.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                importer.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            enabled = !importing,
                        ) {
                            if (importing) CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            else Icon(Icons.Default.Add, contentDescription = null)
                            Text(if (importing) "  Importing…" else "  Import sound pack")
                        }
                        message?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                        SoundPackChoice(
                            title = "Built-in switch sounds",
                            subtitle = "Use Bluke's bundled and synthesized switch profiles",
                            selected = selectedId == null,
                            onSelect = {
                                repository.select(null)
                                selectedId = null
                            },
                        )
                        packs.forEach { pack ->
                            SoundPackChoice(
                                title = pack.name,
                                subtitle = "Imported Mechvibes pack",
                                selected = selectedId == pack.id,
                                onSelect = {
                                    repository.select(pack.id)
                                    selectedId = pack.id
                                },
                            )
                        }
                        if (packs.isEmpty()) {
                            Text(
                                "No custom packs imported yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundPackChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}
