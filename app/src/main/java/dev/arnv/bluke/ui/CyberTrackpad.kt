package dev.arnv.bluke.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** OLED black behind every trackpad surface. */
internal val TrackpadBackground = Color.Black

/**
 * The accent the trackpad outline is drawn in: the app's Material primary (dynamic colors or
 * the chosen accent). Light schemes have a dark primary that would vanish on black, so they
 * use their light counterpart instead.
 */
@Composable
internal fun trackpadAccent(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.primary.luminance() < 0.25f) scheme.inversePrimary else scheme.primary
}

/**
 * The trackpad look: OLED black, a faint frame, accent corner brackets, a tick in the middle
 * of each edge and a small reticle in the centre. The brackets brighten while a finger is down.
 * Touches are only watched, never consumed, so the gesture layer inside works as before.
 */
@Composable
internal fun Modifier.cyberTrackpad(
    cornerRadius: Dp = 10.dp,
    showReticle: Boolean = true,
): Modifier {
    val accent = trackpadAccent()
    var touching by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (touching) 1f else 0f, tween(if (touching) 90 else 380), label = "trackpadGlow")
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .clip(shape)
        .background(TrackpadBackground)
        .border(1.dp, accent.copy(alpha = 0.10f + 0.10f * glow), shape)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    touching = event.changes.any { it.pressed }
                }
            }
        }
        .drawWithContent {
            if (showReticle) drawReticle(accent.copy(alpha = 0.28f + 0.22f * glow))
            drawContent()
            drawFrameMarks(accent.copy(alpha = 0.7f + 0.3f * glow), cornerRadius.toPx(), glow)
        }
}

private fun DrawScope.drawFrameMarks(color: Color, cornerRadius: Float, glow: Float) {
    val strokeWidth = 1.5.dp.toPx() + 0.5.dp.toPx() * glow
    val inset = 7.dp.toPx()
    val arm = min(28.dp.toPx(), min(size.width, size.height) * 0.22f)
    val bend = min(cornerRadius * 0.6f, arm / 3f)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset

    // Corner brackets with a slightly rounded bend, like the corners of a viewfinder.
    fun bracket(cx: Float, cy: Float, dx: Float, dy: Float) {
        val path = Path().apply {
            moveTo(cx, cy + dy * arm)
            lineTo(cx, cy + dy * bend)
            quadraticTo(cx, cy, cx + dx * bend, cy)
            lineTo(cx + dx * arm, cy)
        }
        drawPath(path, color, style = stroke)
    }
    bracket(left, top, 1f, 1f)
    bracket(right, top, -1f, 1f)
    bracket(left, bottom, 1f, -1f)
    bracket(right, bottom, -1f, -1f)

    // A short tick centred on each edge.
    val tick = arm * 0.55f
    val midX = size.width / 2f
    val midY = size.height / 2f
    val tickColor = color.copy(alpha = color.alpha * 0.75f)
    drawLine(tickColor, Offset(midX - tick / 2f, top), Offset(midX + tick / 2f, top), strokeWidth, StrokeCap.Round)
    drawLine(tickColor, Offset(midX - tick / 2f, bottom), Offset(midX + tick / 2f, bottom), strokeWidth, StrokeCap.Round)
    drawLine(tickColor, Offset(left, midY - tick / 2f), Offset(left, midY + tick / 2f), strokeWidth, StrokeCap.Round)
    drawLine(tickColor, Offset(right, midY - tick / 2f), Offset(right, midY + tick / 2f), strokeWidth, StrokeCap.Round)
}

private fun DrawScope.drawReticle(color: Color) {
    val c = center
    val strokeWidth = 1.dp.toPx()
    val radius = 5.dp.toPx()
    val gap = 9.dp.toPx()
    val reach = 18.dp.toPx()
    if (min(size.width, size.height) < reach * 3f) return
    drawCircle(color, radius, c, style = Stroke(strokeWidth))
    drawLine(color, Offset(c.x - reach, c.y), Offset(c.x - gap, c.y), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + reach, c.y), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - reach), Offset(c.x, c.y - gap), strokeWidth, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + reach), strokeWidth, StrokeCap.Round)
}
