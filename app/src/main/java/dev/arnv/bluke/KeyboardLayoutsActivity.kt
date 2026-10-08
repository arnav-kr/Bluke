package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.data.CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE
import dev.arnv.bluke.data.KEYBOARD_GEOMETRY_PREFERENCE
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.normalizedCycleSelection
import dev.arnv.bluke.ui.selectedOrFirstEnabled
import dev.arnv.bluke.ui.toggledCycleSelection
import dev.arnv.bluke.ui.theme.MyApplicationTheme

class KeyboardLayoutsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setContent {
            MyApplicationTheme {
                val ids = KeyboardGeometry.entries.map { it.name }
                val initialCycleSelection = remember {
                    normalizedCycleSelection(preferences.getStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, null), ids)
                }
                var cycleSelection by remember {
                    mutableStateOf(initialCycleSelection)
                }
                val storedLayout = remember {
                    KeyboardGeometry.fromPreference(preferences.getString(KEYBOARD_GEOMETRY_PREFERENCE, null))
                }
                var selectedLayout by remember {
                    val selectedName = selectedOrFirstEnabled(storedLayout.name, initialCycleSelection, ids)!!
                    mutableStateOf(KeyboardGeometry.valueOf(selectedName))
                }
                LaunchedEffect(Unit) {
                    if (selectedLayout != storedLayout) {
                        preferences.edit { putString(KEYBOARD_GEOMETRY_PREFERENCE, selectedLayout.name) }
                    }
                }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Keyboard layouts") },
                            navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item {
                            Text(
                                "Tap a layout to use it. Check the layouts included when tapping the keyboard toolbar button.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        items(KeyboardGeometry.entries.size) { index ->
                            val layout = KeyboardGeometry.entries[index]
                            val active = selectedLayout == layout
                            Surface(
                                onClick = {
                                    selectedLayout = layout
                                    val nextCycle = cycleSelection + layout.name
                                    cycleSelection = nextCycle
                                    preferences.edit {
                                        putString(KEYBOARD_GEOMETRY_PREFERENCE, layout.name)
                                        putStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, nextCycle)
                                    }
                                },
                                shape = RoundedCornerShape(
                                    topStart = if (index == 0) 28.dp else 4.dp,
                                    topEnd = if (index == 0) 28.dp else 4.dp,
                                    bottomStart = if (index == KeyboardGeometry.entries.lastIndex) 28.dp else 4.dp,
                                    bottomEnd = if (index == KeyboardGeometry.entries.lastIndex) 28.dp else 4.dp,
                                ),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                        Text(layout.displayName, style = MaterialTheme.typography.titleMedium)
                                        Text("Include in toolbar cycle", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (active) Icon(Icons.Default.Check, "Selected")
                                    Checkbox(
                                        checked = layout.name in cycleSelection,
                                        enabled = layout.name !in cycleSelection || cycleSelection.size > 1,
                                        onCheckedChange = {
                                            val nextCycle = toggledCycleSelection(cycleSelection, layout.name)
                                            val nextSelectedName = selectedOrFirstEnabled(selectedLayout.name, nextCycle, ids)!!
                                            cycleSelection = nextCycle
                                            selectedLayout = KeyboardGeometry.valueOf(nextSelectedName)
                                            preferences.edit {
                                                putStringSet(CYCLE_KEYBOARD_GEOMETRIES_PREFERENCE, nextCycle)
                                                putString(KEYBOARD_GEOMETRY_PREFERENCE, nextSelectedName)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
