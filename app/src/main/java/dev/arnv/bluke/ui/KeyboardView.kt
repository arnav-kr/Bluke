package dev.arnv.bluke.ui
import dev.arnv.bluke.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp




@Composable
fun KeyboardView(
    layoutType: KeyboardLayoutType,
    caseColor: CaseColor,
    activePressedKeys: List<Int>,
    isConnected: Boolean,
    rgbMode: RgbMode,
    timeProgress: Float,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    onKeyPressChange: (Int, Boolean) -> Unit
) {
    val palette = Colorways.PALETTES[layoutType] ?: Colorways.PALETTES[KeyboardLayoutType.OBLIVION_75]!!

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val keyboardRows = KeyboardLayouts.getLayout(layoutType)
        val allKeys = keyboardRows.flatten()

        val totalLayoutWidthInUnits = allKeys.maxOfOrNull { it.x + it.widthRatio } ?: 15.0f
        val totalLayoutHeightInUnits = allKeys.maxOfOrNull { it.y + it.heightRatio } ?: 5.0f

        val platePadding = 6.dp
        val outerBoxPadding = 6.dp

        // Adjust constraints perfectly to take up absolutely maximum available space without dead zones
        val usableWidthDp = maxWidth - (outerBoxPadding * 2) - (platePadding * 2)
        val usableHeightDp = maxHeight - (outerBoxPadding * 2) - (platePadding * 2)

        val widthBasedUnit = usableWidthDp / totalLayoutWidthInUnits
        val heightBasedUnit = usableHeightDp / totalLayoutHeightInUnits
        val baseUnitWidth = minOf(widthBasedUnit, heightBasedUnit)

        val plateWidth = baseUnitWidth * totalLayoutWidthInUnits
        val plateHeight = baseUnitWidth * totalLayoutHeightInUnits

        // Keyboard Case plate well holding absolutely-positioned keycaps (guarantees perfect grid alignments)
        Box(
                modifier = Modifier
                    .width(plateWidth + (platePadding * 2))
                    .height(plateHeight + (platePadding * 2))
                    .shadow(
                        elevation = if (rgbMode != RgbMode.OFF) 12.dp else 4.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = if (rgbMode == RgbMode.STATIC) Color(0xFF00E5FF) else if (rgbMode == RgbMode.BREATHING) Color(0xFFFF5722) else Color.Black.copy(alpha = 0.5f),
                        ambientColor = if (rgbMode != RgbMode.OFF) Color.Cyan.copy(alpha = 0.4f) else Color.Black
                    )
                    .background(palette.bgCode, RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .padding(platePadding)
                    .pointerInput(baseUnitWidth, allKeys) {
                        awaitPointerEventScope {
                            // Map of PointerId to List of KeyCodes currently being pressed by that pointer
                            val currentPointers = mutableMapOf<Long, List<Int>>()
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val eventChanges = event.changes
                                
                                val newPointerStates = mutableMapOf<Long, List<Int>>()
                                
                                eventChanges.forEach { change ->
                                    if (change.pressed) {
                                        val pos = change.position
                                        val touchRadius = 6.dp.toPx()
                                        
                                        // Find all keys that intersect the touch radius
                                        val intersectedKeys = allKeys.filter { key ->
                                            val keyLeft = baseUnitWidth.toPx() * key.x
                                            val keyRight = keyLeft + baseUnitWidth.toPx() * key.widthRatio
                                            val keyTop = baseUnitWidth.toPx() * key.y
                                            val keyBottom = keyTop + baseUnitWidth.toPx() * key.heightRatio
                                            
                                            // Closest point on the rectangle to the touch center
                                            val closestX = pos.x.coerceIn(keyLeft, keyRight)
                                            val closestY = pos.y.coerceIn(keyTop, keyBottom)
                                            
                                            val dx = pos.x - closestX
                                            val dy = pos.y - closestY
                                            
                                            (dx * dx + dy * dy) <= (touchRadius * touchRadius)
                                        }.map { it.keyCode }
                                        
                                        newPointerStates[change.id.value] = intersectedKeys
                                        change.consume()
                                    }
                                }
                                
                                // Synthesize differences to send onKeyPressChange
                                val oldAllPressed = currentPointers.values.flatten().toSet()
                                val newAllPressed = newPointerStates.values.flatten().toSet()
                                
                                val newlyPressed = newAllPressed - oldAllPressed
                                val newlyReleased = oldAllPressed - newAllPressed
                                
                                newlyReleased.forEach { keyCode ->
                                    onKeyPressChange(keyCode, false)
                                }
                                newlyPressed.forEach { keyCode ->
                                    onKeyPressChange(keyCode, true)
                                }
                                
                                currentPointers.clear()
                                currentPointers.putAll(newPointerStates)
                            }
                        }
                    }
            ) {
                allKeys.forEach { key ->
                    val keyX = key.x
                    val keyY = key.y

                    val keyWidth = baseUnitWidth * key.widthRatio
                    val keyHeight = baseUnitWidth * key.heightRatio

                    val (keyColor, legendColor) = when (key.category) {
                        KeyColorCategory.ALPHA -> palette.alphaBg to palette.alphaLegend
                        KeyColorCategory.MOD -> palette.modBg to palette.modLegend
                        KeyColorCategory.ACCENT -> palette.accentBg to palette.accentLegend
                    }

                    val isPressed = activePressedKeys.contains(key.keyCode)

                    val glowColor = when (rgbMode) {
                        RgbMode.OFF -> null
                        RgbMode.STATIC -> Color(0xFF00E5FF).copy(alpha = 0.4f)
                        RgbMode.BREATHING -> {
                            val breathAlpha = 0.12f + 0.35f * (0.5f + 0.5f * kotlin.math.sin(timeProgress * 2 * Math.PI.toFloat())).toFloat()
                            Color(0xFFFF5722).copy(alpha = breathAlpha)
                        }
                        RgbMode.WAVE -> {
                            val waveFraction = keyX / totalLayoutWidthInUnits
                            val hue = (timeProgress * 360f + waveFraction * 240f) % 360f
                            Color.hsv(hue, 0.85f, 0.95f).copy(alpha = 0.5f)
                        }
                        RgbMode.REACTIVE -> null
                    }

                    val isReactive = (rgbMode == RgbMode.REACTIVE)

                    val isShiftActive = activePressedKeys.contains(0xE1) || activePressedKeys.contains(0xE5)
                    val isUppercase = isCapsLockActive xor isShiftActive
                    val isAlphabetic = key.legend.length == 1 && key.legend[0].isLetter()
                    val displayLegend = if (isAlphabetic) {
                        if (isUppercase) key.legend.uppercase() else key.legend.lowercase()
                    } else {
                        key.legend
                    }

                    val isIndicatorActive = when (key.keyCode) {
                        0x39 -> isCapsLockActive
                        0x53 -> isNumLockActive
                        0x47 -> isScrollLockActive
                        else -> false
                    }

                    val finalGlowColor = if (isIndicatorActive && glowColor == null && rgbMode == RgbMode.OFF) {
                        palette.accentBg.copy(alpha = 0.25f)
                    } else {
                        glowColor
                    }

                    KeyCap(
                        legend = displayLegend,
                        shiftedLegend = key.shiftedLegend,
                        width = keyWidth,
                        height = keyHeight,
                        isPressed = isPressed,
                        keyBgColor = keyColor,
                        legendColor = legendColor,
                        baseUnitWidth = baseUnitWidth,
                        glowColor = finalGlowColor,
                        isReactive = isReactive,
                        modifier = Modifier
                            .offset(
                                x = baseUnitWidth * keyX,
                                y = baseUnitWidth * keyY
                            )
                    )
                }
            }
        }
    }