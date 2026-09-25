package dev.arnv.bluke.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor
import dev.arnv.bluke.ui.theme.parseOpaqueHexColor

@Composable
fun VisualColorPicker(
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
            center = Offset(saturation * size.width, (1f - brightness) * size.height),
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
                            hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
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
            center = Offset((hue / 360f) * size.width, size.height / 2f),
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
