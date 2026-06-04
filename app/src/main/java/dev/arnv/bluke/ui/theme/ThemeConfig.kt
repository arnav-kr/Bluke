package dev.arnv.bluke.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable

val AccentColors = listOf(
    Color(0xFF0A3D91), // Bluke Blue
    Color(0xFFD32F2F), // Red
    Color(0xFFC2185B), // Pink
    Color(0xFF7B1FA2), // Purple
    Color(0xFF512DA8), // Deep Purple
    Color(0xFF303F9F), // Indigo
    Color(0xFF1976D2), // Blue
    Color(0xFF0288D1), // Light Blue
    Color(0xFF0097A7), // Cyan
    Color(0xFF00796B), // Teal
    Color(0xFF388E3C), // Green
    Color(0xFF689F38), // Light Green
    Color(0xFFAFB42B), // Lime
    Color(0xFFFBC02D), // Yellow
    Color(0xFFFFA000), // Amber
    Color(0xFFF57C00), // Orange
    Color(0xFFE64A19), // Deep Orange
    Color(0xFF5D4037), // Brown
    Color(0xFF616161), // Grey
    Color(0xFF455A64)  // Blue Grey
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun getCookieShape(sides: Int): androidx.compose.ui.graphics.Shape {
    return if (sides <= 5) MaterialShapes.Pentagon.toShape() else MaterialShapes.Cookie7Sided.toShape()
}

