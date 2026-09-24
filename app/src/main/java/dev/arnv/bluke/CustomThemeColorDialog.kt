package dev.arnv.bluke

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor
import dev.arnv.bluke.ui.theme.parseOpaqueHexColor

@Composable
internal fun CustomPalettePreview(background: Color, surface: Color, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        listOf(background, surface, accent).forEach { color ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
internal fun CustomColorDialog(
    initialBackground: Int,
    initialSurface: Int,
    initialAccent: Int,
    onDismiss: () -> Unit,
    onSave: (background: Int, surface: Int, accent: Int) -> Unit,
) {
    var backgroundText by remember(initialBackground) { mutableStateOf(formatOpaqueHexColor(initialBackground)) }
    var surfaceText by remember(initialSurface) { mutableStateOf(formatOpaqueHexColor(initialSurface)) }
    var accentText by remember(initialAccent) { mutableStateOf(formatOpaqueHexColor(initialAccent)) }
    val background = parseOpaqueHexColor(backgroundText)
    val surface = parseOpaqueHexColor(surfaceText)
    val accent = parseOpaqueHexColor(accentText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom theme colors") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter six-digit RGB hex colors. Preview updates as each value becomes valid.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CustomHexColorField("Background", backgroundText, background) { backgroundText = it }
                CustomHexColorField("Keys & surfaces", surfaceText, surface) { surfaceText = it }
                CustomHexColorField("Accent & text", accentText, accent) { accentText = it }
                if (background != null && surface != null && accent != null) {
                    Surface(
                        color = Color(background),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(color = Color(surface), shape = MaterialTheme.shapes.small) {
                                Text(
                                    "Aa",
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    color = Color(accent),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Text("Theme preview", color = Color(accent))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = background != null && surface != null && accent != null,
                onClick = { onSave(background!!, surface!!, accent!!) },
            ) { Text("Use colors") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomHexColorField(
    label: String,
    value: String,
    parsedColor: Int?,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            val normalized = candidate.uppercase().filterIndexed { index, character ->
                character.isDigit() || character in 'A'..'F' || (index == 0 && character == '#')
            }.let { filtered -> if (filtered.startsWith('#')) filtered.take(7) else filtered.take(6) }
            onValueChange(normalized)
        },
        label = { Text(label) },
        singleLine = true,
        isError = parsedColor == null,
        supportingText = if (parsedColor == null) ({ Text("Use #RRGGBB") }) else null,
        trailingIcon = {
            if (parsedColor != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(parsedColor)),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
