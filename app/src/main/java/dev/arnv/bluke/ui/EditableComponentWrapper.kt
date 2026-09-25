package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun EditableComponentWrapper(
    modifier: Modifier = Modifier,
    isEditMode: Boolean,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    onOffsetChange: (Float, Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onTransformEnd: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentScale by rememberUpdatedState(scale)
    val currentOnOffsetChange by rememberUpdatedState(onOffsetChange)
    val currentOnScaleChange by rememberUpdatedState(onScaleChange)
    val currentOnTransformEnd by rememberUpdatedState(onTransformEnd)
    var layoutTopInWindowPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                layoutTopInWindowPx = coordinates.positionInWindow().y
            }
            .offset {
                val layoutTopInWindow = layoutTopInWindowPx / density
                val constrainedOffsetY = if (layoutTopInWindow > 0) {
                    val minY = 42f - layoutTopInWindow
                    currentOffsetY.coerceAtLeast(minY)
                } else {
                    currentOffsetY
                }
                IntOffset(
                    (currentOffsetX * density).roundToInt(),
                    (constrainedOffsetY * density).roundToInt(),
                )
            }
            .graphicsLayer {
                scaleX = currentScale
                scaleY = currentScale
            },
        contentAlignment = Alignment.Center,
    ) {
        content()

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .border(
                        width = 1.2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .pointerInput(isEditMode) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var event: PointerEvent
                            do {
                                event = awaitPointerEvent()
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                currentOnScaleChange((currentScale * zoom).coerceIn(0.6f, 1.8f))

                                val minY = 42f - layoutTopInWindowPx / density
                                currentOnOffsetChange(
                                    currentOffsetX + (pan.x * currentScale) / density,
                                    (currentOffsetY + (pan.y * currentScale) / density).coerceAtLeast(minY),
                                )
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            currentOnTransformEnd()
                        }
                    },
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = currentOnTransformEnd,
                            onDragCancel = currentOnTransformEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaScale = (dragAmount.x + dragAmount.y) / 150f
                                currentOnScaleChange((currentScale + deltaScale).coerceIn(0.6f, 1.8f))
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Resize",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}
