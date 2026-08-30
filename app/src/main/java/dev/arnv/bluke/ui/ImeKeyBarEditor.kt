package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Editor for the System Keyboard extra key rows.
 *
 * Rows are assembled from [ImeKeyBar.CATALOG] rather than typed in, so a bar cannot be configured
 * into a key that does nothing. Reordering is via arrow buttons instead of drag-and-drop: the keys
 * are small targets in a scrolling dialog, where a long-press-to-drag gesture is easy to trigger by
 * accident and hard to undo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImeKeyBarEditorDialog(
    initialRows: List<List<String>>,
    onDismiss: () -> Unit,
    onSave: (List<List<String>>) -> Unit
) {
    // Edits are staged locally and only committed on Save, so Cancel genuinely discards.
    //
    // Both levels are snapshot lists: a plain MutableList inside would not be observable, so
    // reordering keys within a row would mutate state without triggering recomposition.
    val rows = remember {
        mutableStateListOf<SnapshotStateList<String>>().apply {
            initialRows.forEach { row -> add(mutableStateListOf<String>().apply { addAll(row) }) }
        }
    }
    // Which row a newly picked key should join; null closes the picker.
    var pickerForRow by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Phone Keyboard Extra Keys") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Arrange the extra key rows shown under the text field. " +
                        "Everything here is sent to the computer you are controlling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                rows.forEachIndexed { rowIndex, row ->
                    RowEditor(
                        rowIndex = rowIndex,
                        row = row,
                        canMoveRowUp = rowIndex > 0,
                        canMoveRowDown = rowIndex < rows.size - 1,
                        onMoveKeyLeft = { keyIndex ->
                            if (keyIndex > 0) {
                                val moved = row.removeAt(keyIndex)
                                row.add(keyIndex - 1, moved)
                            }
                        },
                        onMoveKeyRight = { keyIndex ->
                            if (keyIndex < row.size - 1) {
                                val moved = row.removeAt(keyIndex)
                                row.add(keyIndex + 1, moved)
                            }
                        },
                        onRemoveKey = { keyIndex ->
                            row.removeAt(keyIndex)
                            // A row with nothing in it would render as a gap, so drop it. The
                            // last row is kept so there is always somewhere to add a key.
                            if (row.isEmpty() && rows.size > 1) rows.removeAt(rowIndex)
                        },
                        onMoveRowUp = {
                            val moved = rows.removeAt(rowIndex)
                            rows.add(rowIndex - 1, moved)
                        },
                        onMoveRowDown = {
                            val moved = rows.removeAt(rowIndex)
                            rows.add(rowIndex + 1, moved)
                        },
                        onDeleteRow = { if (rows.size > 1) rows.removeAt(rowIndex) },
                        onAddKey = { pickerForRow = rowIndex }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (rows.size < ImeKeyBar.MAX_ROWS) {
                    TextButton(
                        onClick = { rows.add(mutableStateListOf()) },
                        modifier = Modifier.testTag("add_key_row_btn")
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add row")
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        rows.clear()
                        ImeKeyBar.DEFAULT_ROWS.forEach { row ->
                            rows.add(mutableStateListOf<String>().apply { addAll(row) })
                        }
                    },
                    modifier = Modifier.testTag("reset_key_rows_btn")
                ) {
                    Text("Reset to defaults")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(rows.map { it.toList() }.filter { it.isNotEmpty() }) },
                modifier = Modifier.testTag("save_key_rows_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    pickerForRow?.let { rowIndex ->
        KeyPickerDialog(
            // Keys already in this row are not offered again; the same key in two rows is allowed,
            // since a symbol may be wanted next to different neighbours.
            excluded = rows.getOrNull(rowIndex)?.toSet() ?: emptySet(),
            atRowLimit = (rows.getOrNull(rowIndex)?.size ?: 0) >= ImeKeyBar.MAX_KEYS_PER_ROW,
            onDismiss = { pickerForRow = null },
            onPick = { id ->
                rows.getOrNull(rowIndex)?.let { target ->
                    if (target.size < ImeKeyBar.MAX_KEYS_PER_ROW) target.add(id)
                }
                pickerForRow = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowEditor(
    rowIndex: Int,
    row: List<String>,
    canMoveRowUp: Boolean,
    canMoveRowDown: Boolean,
    onMoveKeyLeft: (Int) -> Unit,
    onMoveKeyRight: (Int) -> Unit,
    onRemoveKey: (Int) -> Unit,
    onMoveRowUp: () -> Unit,
    onMoveRowDown: () -> Unit,
    onDeleteRow: () -> Unit,
    onAddKey: () -> Unit
) {
    // Which key in this row has its reorder/remove controls expanded.
    var selectedKey by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(8.dp)
            .testTag("key_row_$rowIndex")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Row ${rowIndex + 1}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onMoveRowUp,
                enabled = canMoveRowUp,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "Move row up", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onMoveRowDown,
                enabled = canMoveRowDown,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    "Move row down",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDeleteRow, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Delete row", modifier = Modifier.size(16.dp))
            }
        }

        if (row.isEmpty()) {
            Text(
                text = "No keys yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            row.forEachIndexed { keyIndex, id ->
                val def = ImeKeyBar.byId(id)
                if (def != null) {
                    KeyChip(
                        label = def.label,
                        selected = selectedKey == keyIndex,
                        onClick = { selectedKey = if (selectedKey == keyIndex) null else keyIndex },
                        onMoveLeft = {
                            onMoveKeyLeft(keyIndex)
                            selectedKey = (keyIndex - 1).coerceAtLeast(0)
                        },
                        onMoveRight = {
                            onMoveKeyRight(keyIndex)
                            selectedKey = keyIndex + 1
                        },
                        onRemove = {
                            onRemoveKey(keyIndex)
                            selectedKey = null
                        }
                    )
                }
            }
            AssistChip(
                onClick = onAddKey,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("add_key_to_row_$rowIndex")
            )
        }
    }
}

@Composable
private fun KeyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        // Controls appear only for the tapped key, so a full row stays readable.
        if (selected) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onMoveLeft, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Move left", modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onMoveRight, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Move right", modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Catalog browser, grouped by category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyPickerDialog(
    excluded: Set<String>,
    atRowLimit: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (atRowLimit) "Row is full" else "Add a key") },
        text = {
            if (atRowLimit) {
                Text("A row holds at most ${ImeKeyBar.MAX_KEYS_PER_ROW} keys. Remove one first, or add another row.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    ImeKeyBar.CATEGORIES.forEach { category ->
                        val available = ImeKeyBar.CATALOG
                            .filter { it.category == category && it.id !in excluded }
                        if (available.isNotEmpty()) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                available.forEach { def ->
                                    SuggestionChip(
                                        onClick = { onPick(def.id) },
                                        label = { Text(def.label, maxLines = 1) },
                                        modifier = Modifier.testTag("pick_key_${def.id}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
