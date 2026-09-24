package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun KeyboardView(
    layoutType: KeyboardLayoutType,
    characterLayout: KeyboardCharacterLayout = KeyboardCharacterLayout.US_QWERTY,
    caseColor: CaseColor,
    activePressedKeys: List<Int>,
    isConnected: Boolean = false,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    keySensitivity: Float = 6f,
    onKeyPressChange: (Int, Boolean) -> Unit
) {
    val palette = Colorways.PALETTES[layoutType] ?: Colorways.PALETTES[KeyboardLayoutType.OBLIVION_75]!!

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val keyboardRows = KeyboardLayouts.getLayout(layoutType, characterLayout)
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

        val context = androidx.compose.ui.platform.LocalContext.current
        val sharedPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

        // Keyboard Case plate well holding absolutely-positioned keycaps (guarantees perfect grid alignments)
        Box(
            modifier = Modifier
                .width(plateWidth + (platePadding * 2))
                .height(plateHeight + (platePadding * 2))
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black
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
                                        val touchRadius = keySensitivity.dp.toPx()
                                        
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

                    val finalGlowColor = if (isIndicatorActive) {
                        palette.accentBg.copy(alpha = 0.25f)
                    } else {
                        null
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
                        modifier = Modifier
                            .offset(
                                x = baseUnitWidth * keyX,
                                y = baseUnitWidth * keyY
                            ),
                        glowColor = finalGlowColor
                    )
                }
            }
        }
        }
    }
