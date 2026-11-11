package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun KeyboardView(
    geometry: KeyboardGeometry,
    theme: KeyboardThemeDefinition,
    characterLayout: KeyboardCharacterLayout = KeyboardCharacterLayout.US_QWERTY,
    activePressedKeys: List<Int>,
    isConnected: Boolean = false,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    isFnActive: Boolean = false,
    keySensitivity: Float = 6f,
    selectedStyleId: String? = null,
    /** Rows to draw instead of the [geometry] layout, e.g. a Touch layout layer. */
    rowsOverride: List<List<KeyLayoutInfo>>? = null,
    /** Stretch keys vertically to use the available height (up to [maxKeyAspect]). */
    fillHeight: Boolean = false,
    maxKeyAspect: Float = 1.45f,
    latchedKeys: Map<Int, LatchState> = emptyMap(),
    legendScale: Float = 1f,
    showKeyPreview: Boolean = false,
    onKeySelected: ((KeyLayoutInfo) -> Unit)? = null,
    onKeyPressChange: (Int, Boolean) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = if (fillHeight) Alignment.BottomCenter else Alignment.Center
        ) {
            val keyboardRows = rowsOverride ?: KeyboardLayouts.getLayout(geometry, characterLayout)
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
        val baseUnitWidth: Dp
        val baseUnitHeight: Dp
        if (fillHeight) {
            baseUnitWidth = widthBasedUnit
            baseUnitHeight = minOf(heightBasedUnit, widthBasedUnit * maxKeyAspect)
        } else {
            baseUnitWidth = minOf(widthBasedUnit, heightBasedUnit)
            baseUnitHeight = baseUnitWidth
        }
        // Keycap bevels and fonts follow the smaller side so tall keys don't look bloated.
        val keyStyleUnit = minOf(baseUnitWidth, baseUnitHeight)

        val plateWidth = baseUnitWidth * totalLayoutWidthInUnits
        val plateHeight = baseUnitHeight * totalLayoutHeightInUnits

        // The pointer loop below reads these through State so it keeps tracking fingers
        // when the layout changes (for example switching Touch layers mid-press).
        val currentKeys by rememberUpdatedState(allKeys)
        val currentUnitWidth by rememberUpdatedState(baseUnitWidth)
        val currentUnitHeight by rememberUpdatedState(baseUnitHeight)
        val currentSensitivity by rememberUpdatedState(keySensitivity)
        val currentOnKeySelected by rememberUpdatedState(onKeySelected)
        val currentOnKeyPressChange by rememberUpdatedState(onKeyPressChange)

        // Keyboard Case plate well holding absolutely-positioned keycaps (guarantees perfect grid alignments)
        Box(
            modifier = Modifier
                .padding(bottom = if (fillHeight) outerBoxPadding else 0.dp)
                .width(plateWidth + (platePadding * 2))
                .height(plateHeight + (platePadding * 2))
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                    ambientColor = Color.Black
                )
                .background(Color(theme.plateArgb), RoundedCornerShape(10.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(platePadding)
                .pointerInput(onKeySelected != null) {
                        awaitPointerEventScope {
                            fun keyAt(x: Float, y: Float, radiusPx: Float) = findKeyAt(
                                keys = currentKeys,
                                x = x,
                                y = y,
                                unitWidthPx = currentUnitWidth.toPx(),
                                unitHeightPx = currentUnitHeight.toPx(),
                                touchRadiusPx = radiusPx,
                            )

                            if (currentOnKeySelected != null) {
                                var pointerWasDown = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedChange = event.changes.firstOrNull { it.pressed }
                                    if (pressedChange != null && !pointerWasDown) {
                                        val pos = pressedChange.position
                                        keyAt(pos.x, pos.y, 0f)?.let { currentOnKeySelected?.invoke(it) }
                                    }
                                    pointerWasDown = pressedChange != null
                                    event.changes.forEach { it.consume() }
                                }
                            }

                            // Key code currently pressed by each finger.
                            val currentPointers = mutableMapOf<Long, Int>()

                            while (true) {
                                val event = awaitPointerEvent()
                                val newPointerStates = mutableMapOf<Long, Int>()
                                val touchRadius = currentSensitivity.dp.toPx()

                                event.changes.forEach { change ->
                                    if (change.pressed) {
                                        keyAt(change.position.x, change.position.y, touchRadius)?.let {
                                            newPointerStates[change.id.value] = it.keyCode
                                        }
                                        change.consume()
                                    }
                                }

                                // Synthesize differences to send onKeyPressChange
                                val oldAllPressed = currentPointers.values.toSet()
                                val newAllPressed = newPointerStates.values.toSet()

                                (oldAllPressed - newAllPressed).forEach { keyCode ->
                                    currentOnKeyPressChange(keyCode, false)
                                }
                                (newAllPressed - oldAllPressed).forEach { keyCode ->
                                    currentOnKeyPressChange(keyCode, true)
                                }

                                currentPointers.clear()
                                currentPointers.putAll(newPointerStates)
                            }
                        }
                    }
            ) {
                val isShiftActive = activePressedKeys.contains(KeyboardLayouts.MOD_LSHIFT) ||
                    activePressedKeys.contains(KeyboardLayouts.MOD_RSHIFT) ||
                    latchedKeys.containsKey(KeyboardLayouts.MOD_LSHIFT) ||
                    latchedKeys.containsKey(KeyboardLayouts.MOD_RSHIFT)
                val isUppercase = isCapsLockActive xor isShiftActive

                allKeys.forEach { key ->
                    val keyWidth = baseUnitWidth * key.widthRatio
                    val keyHeight = baseUnitHeight * key.heightRatio

                    val keyStyle = theme.styleFor(key)
                    val keyColor = Color(keyStyle.backgroundArgb)
                    val legendColor = Color(keyStyle.legendArgb)

                    val isPressed = activePressedKeys.contains(key.keyCode)

                    val isAlphabetic = key.legend.length == 1 && key.legend[0].isLetter()
                    val baseDisplayLegend = if (isAlphabetic) {
                        if (isUppercase) key.legend.uppercase() else key.legend.lowercase()
                    } else {
                        key.legend
                    }
                    val fnLegend = if (isFnActive) fnLegendForKey(key.keyCode) else null
                    val displayLegend = fnLegend ?: baseDisplayLegend
                    val displayShiftedLegend = if (fnLegend == null) key.shiftedLegend else ""

                    val isIndicatorActive = when (key.keyCode) {
                        0x39 -> isCapsLockActive
                        0x53 -> isNumLockActive
                        0x47 -> isScrollLockActive
                        else -> false
                    }
                    val latchState = latchedKeys[key.keyCode] ?: LatchState.OFF

                    val finalGlowColor = if (isIndicatorActive || latchState != LatchState.OFF) {
                        Color(theme.accentStyle.backgroundArgb).copy(
                            alpha = if (latchState == LatchState.LOCKED) 0.5f else 0.25f
                        )
                    } else {
                        null
                    }

                    KeyCap(
                        legend = displayLegend,
                        shiftedLegend = displayShiftedLegend,
                        width = keyWidth,
                        height = keyHeight,
                        isPressed = isPressed,
                        keyBgColor = keyColor,
                        legendColor = legendColor,
                        legendScale = keyStyle.legendScale * legendBoost(displayLegend, key.widthRatio, legendScale),
                        isSelected = selectedStyleId == key.styleId,
                        baseUnitWidth = keyStyleUnit,
                        latchState = latchState,
                        modifier = Modifier
                            .offset(
                                x = baseUnitWidth * key.x,
                                y = baseUnitHeight * key.y
                            ),
                        glowColor = finalGlowColor
                    )

                    if (showKeyPreview && isPressed && key.category == KeyColorCategory.ALPHA && displayLegend.isNotBlank()) {
                        KeyPreviewBubble(
                            legend = displayLegend,
                            background = keyColor,
                            legendColor = legendColor,
                            keyWidth = keyWidth,
                            keyHeight = keyHeight,
                            modifier = Modifier
                                .zIndex(1f)
                                .offset(
                                    x = baseUnitWidth * key.x,
                                    y = baseUnitHeight * key.y - keyHeight * 1.1f
                                ),
                        )
                    }
                }
            }
        }
        }
    }

/** Full [boost] for short labels, fading to none for labels that already fill the key. */
internal fun legendBoost(legend: String, widthRatio: Float, boost: Float): Float {
    if (boost <= 1f) return boost
    val charsPerUnit = legend.length / widthRatio.coerceAtLeast(0.5f)
    val smallest = 0.9f
    return when {
        charsPerUnit <= 2.5f -> boost
        charsPerUnit >= 4f -> smallest
        else -> smallest + (boost - smallest) * (4f - charsPerUnit) / 1.5f
    }
}

/** Enlarged copy of the pressed key, drawn above the finger so the letter stays visible. */
@Composable
private fun KeyPreviewBubble(
    legend: String,
    background: Color,
    legendColor: Color,
    keyWidth: Dp,
    keyHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(keyWidth)
            .height(keyHeight)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = legend,
            color = legendColor,
            fontSize = (keyHeight.value * 0.45f).coerceIn(14f, 28f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
