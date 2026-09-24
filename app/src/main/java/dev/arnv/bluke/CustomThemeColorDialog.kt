package dev.arnv.bluke

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor
import dev.arnv.bluke.ui.theme.blueChannel
import dev.arnv.bluke.ui.theme.greenChannel
import dev.arnv.bluke.ui.theme.opaqueRgb
import dev.arnv.bluke.ui.theme.parseOpaqueHexColor
import dev.arnv.bluke.ui.theme.redChannel
import kotlin.math.roundToInt

private enum class CustomColorRole(val label: String) {
    BACKGROUND("Background"),
    SURFACE("Keys"),
    ACCENT("Accent"),
}

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
    var background by remember(initialBackground) { mutableIntStateOf(initialBackground) }
    var surface by remember(initialSurface) { mutableIntStateOf(initialSurface) }
    var accent by remember(initialAccent) { mutableIntStateOf(initialAccent) }
    var selectedRole by remember { mutableStateOf(CustomColorRole.BACKGROUND) }
    val selectedColor = when (selectedRole) {
        CustomColorRole.BACKGROUND -> background
        CustomColorRole.SURFACE -> surface
        CustomColorRole.ACCENT -> accent
    }
    fun updateSelectedColor(color: Int) {
        when (selectedRole) {
            CustomColorRole.BACKGROUND -> background = color
            CustomColorRole.SURFACE -> surface = color
            CustomColorRole.ACCENT -> accent = color
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom theme colors") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select a theme part, then use the RGB sliders or enter an exact hex color.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomColorRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) },
                            leadingIcon = {
                                val roleColor = when (role) {
                                    CustomColorRole.BACKGROUND -> background
                                    CustomColorRole.SURFACE -> surface
                                    CustomColorRole.ACCENT -> accent
                                }
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(roleColor))
                                )
                            },
                        )
                    }
                }
                SelectedColorEditor(
                    role = selectedRole,
                    color = selectedColor,
                    onColorChange = ::updateSelectedColor,
                )
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
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(background, surface, accent) },
            ) { Text("Use colors") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SelectedColorEditor(
    role: CustomColorRole,
    color: Int,
    onColorChange: (Int) -> Unit,
) {
    var hexText by remember(role) { mutableStateOf(formatOpaqueHexColor(color)) }
    val parsedHex = parseOpaqueHexColor(hexText)
    val red = redChannel(color)
    val green = greenChannel(color)
    val blue = blueChannel(color)

    OutlinedTextField(
        value = hexText,
        onValueChange = { candidate ->
            val normalized = candidate.uppercase().filterIndexed { index, character ->
                character.isDigit() || character in 'A'..'F' || (index == 0 && character == '#')
            }.let { filtered -> if (filtered.startsWith('#')) filtered.take(7) else filtered.take(6) }
            hexText = normalized
            parseOpaqueHexColor(normalized)?.let(onColorChange)
        },
        label = { Text("${role.label} hex") },
        singleLine = true,
        isError = parsedHex == null,
        supportingText = if (parsedHex == null) ({ Text("Use #RRGGBB") }) else null,
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(color)),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
    RgbChannelSlider("R", red, Color.Red) { value ->
        onColorChange(opaqueRgb(value, green, blue))
        hexText = formatOpaqueHexColor(opaqueRgb(value, green, blue))
    }
    RgbChannelSlider("G", green, Color.Green) { value ->
        onColorChange(opaqueRgb(red, value, blue))
        hexText = formatOpaqueHexColor(opaqueRgb(red, value, blue))
    }
    RgbChannelSlider("B", blue, Color.Blue) { value ->
        onColorChange(opaqueRgb(red, green, value))
        hexText = formatOpaqueHexColor(opaqueRgb(red, green, value))
    }
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
