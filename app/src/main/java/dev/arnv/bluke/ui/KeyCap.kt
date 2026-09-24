package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KeyCap(
    legend: String,
    shiftedLegend: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    isPressed: Boolean,
    keyBgColor: Color,
    legendColor: Color,
    legendScale: Float = 1f,
    isSelected: Boolean = false,
    baseUnitWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    glowColor: Color? = null
) {
    val keyBorderColor = keyBgColor.darker()
    
    // Proportional radii mapped directly from KBSim's 5px/3px on a 54px keysize
    val outerRadius = baseUnitWidth * 0.092f
    val innerRadius = baseUnitWidth * 0.055f
    
    // Inset padding mapped directly from KBSim's keysize/9 and keysize/18
    val insetX = baseUnitWidth / 9f
    val insetTop = baseUnitWidth / 18f
    val insetBottom = baseUnitWidth / 6f // keysize * 3 / 18

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .testTag("key_$legend")
            .padding(1.dp)
            .then(
                if (isSelected) Modifier.border(2.dp, Color(0xFF00BCD4), RoundedCornerShape(outerRadius))
                else Modifier
            )
    ) {
        // Underglow if active
        if (glowColor != null && glowColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .background(glowColor.copy(alpha = glowColor.alpha * 0.45f), RoundedCornerShape(outerRadius))
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(outerRadius), spotColor = glowColor)
            )
        }

        // 1. Key Border Base Bezel (KBSim .keyborder)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(keyBorderColor, RoundedCornerShape(outerRadius))
                .border(1.dp, Color.Black.copy(alpha = 0.85f), RoundedCornerShape(outerRadius))
        )

        // 2. Key Top Bevel (KBSim .keytop, inset and styled)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = insetX,
                    end = insetX,
                    top = insetTop,
                    bottom = insetBottom
                )
                .background(
                    color = if (isPressed) keyBorderColor else keyBgColor,
                    shape = RoundedCornerShape(innerRadius)
                )
                .border(1.dp, Color.Black.copy(alpha = 0.15f), RoundedCornerShape(innerRadius)),
            contentAlignment = Alignment.Center
        ) {
            val legendLengthScale = when {
                legend.length >= 9 -> 0.5f
                legend.length >= 6 -> 0.62f
                legend.length >= 4 -> 0.78f
                else -> 1f
            }
            val shiftedLengthScale = when {
                shiftedLegend.length >= 4 -> 0.72f
                else -> 1f
            }
            val mainFontSize = (
                (baseUnitWidth.value * 0.24f).coerceIn(3f, 13f) *
                    legendScale * legendLengthScale
                ).sp
            val shiftFontSize = (
                (baseUnitWidth.value * 0.17f).coerceIn(2.5f, 9f) *
                    legendScale * shiftedLengthScale
                ).sp

            if (shiftedLegend.isNotEmpty()) {
                // Stack legends inside a column so the secondary text sits exactly slightly above the primary with perfect alignment
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = (baseUnitWidth.value * 0.055f).coerceAtMost(3f).dp)
                        .padding(
                            start = (baseUnitWidth.value * 0.08f).dp,
                            end = (baseUnitWidth.value * 0.08f).dp,
                            top = 0.dp,
                            bottom = 0.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(
                        (baseUnitWidth.value * 0.03f).coerceAtMost(2f).dp,
                    ),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = shiftedLegend,
                        fontSize = shiftFontSize,
                        color = legendColor.copy(alpha = 0.82f),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Text(
                        text = legend,
                        fontSize = mainFontSize,
                        color = legendColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            } else {
                // Alphabetical or single legend, centered
                Text(
                    text = legend,
                    fontSize = mainFontSize,
                    color = legendColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(
                        (baseUnitWidth.value * 0.04f).coerceIn(0.5f, 2f).dp,
                    ),
                )
            }
        }
    }
}



// Extension function to darken mechanical key highlights on pressing

fun Color.darker(): Color {
    return Color(
        red = (this.red * 0.82f).coerceIn(0f, 1f),
        green = (this.green * 0.82f).coerceIn(0f, 1f),
        blue = (this.blue * 0.82f).coerceIn(0f, 1f),
        alpha = this.alpha
    )
}


