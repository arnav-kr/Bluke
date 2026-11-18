package dev.arnv.bluke.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Colors for the physical-looking controls (gamepad, multimedia remote), taken from the active
 * Material color scheme so they follow light/dark mode, dynamic colors and the chosen accent.
 * Surfaces use the tonal surface-container roles; pressed and latched states use the accent.
 */
@Immutable
internal data class ControlSurfaceColors(
    val isDark: Boolean,
    /** Screen background behind the controls. */
    val casing: Color,
    /** Recess a button sits in. */
    val well: Color,
    /** Darkest rim around wells and cups. */
    val wellRim: Color,
    /** Top and bottom of a button cap's gradient. */
    val capTop: Color,
    val capBottom: Color,
    /** The visible side of a raised disk. */
    val capEdge: Color,
    /** Cap gradient while pressed: the cap picks up the accent. */
    val capPressedTop: Color,
    val capPressedBottom: Color,
    val label: Color,
    val labelPressed: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    /** Content drawn on a solid [accent] fill. */
    val onAccent: Color,
    /** Bevel light along the top edge of a cap. */
    val highlight: Color,
    /** Base for drop and inner shadows; callers pick the alpha. */
    val shadow: Color,
    /** Strength of [shadow] relative to the dark look the controls were drawn for. */
    val shadowStrength: Float,
    val toolbar: SessionToolbarColors,
)

@Composable
internal fun rememberControlSurfaceColors(): ControlSurfaceColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { controlSurfaceColors(scheme) }
}

internal fun controlSurfaceColors(scheme: ColorScheme): ControlSurfaceColors {
    val isDark = scheme.surface.luminance() < 0.5f
    // In light schemes a dark primary reads well on light caps; in dark schemes the accent must
    // stay light enough to glow on the dark caps (a custom accent can be very dark), so it is
    // lightened while keeping its hue.
    val accent = if (isDark && scheme.primary.luminance() < 0.25f) lerp(scheme.primary, Color.White, 0.55f) else scheme.primary
    // Custom accents build primaryContainer as a translucent primary, which turns muddy over
    // the caps; mix a solid container from the accent instead.
    val solidContainer = scheme.primaryContainer.alpha >= 1f
    val accentContainer = when {
        solidContainer -> scheme.primaryContainer
        isDark -> lerp(scheme.surfaceContainerHighest, accent, 0.35f)
        else -> lerp(scheme.surfaceContainerLowest, accent, 0.22f)
    }
    val onAccentContainer = when {
        solidContainer -> scheme.onPrimaryContainer
        isDark -> lerp(accent, Color.White, 0.55f)
        else -> lerp(scheme.primary, Color.Black, 0.45f)
    }
    return if (isDark) {
        ControlSurfaceColors(
            isDark = true,
            casing = scheme.surfaceContainer,
            well = scheme.surfaceContainerLowest,
            wellRim = lerp(scheme.surfaceContainerLowest, Color.Black, 0.5f),
            capTop = scheme.surfaceContainerHighest,
            capBottom = scheme.surfaceContainerHigh,
            capEdge = scheme.surfaceContainerLow,
            capPressedTop = lerp(scheme.surfaceContainerHigh, accent, 0.22f),
            capPressedBottom = lerp(scheme.surfaceContainerLow, accent, 0.16f),
            label = scheme.onSurfaceVariant,
            labelPressed = accent,
            accent = accent,
            accentContainer = accentContainer,
            onAccentContainer = onAccentContainer,
            onAccent = if (accent.luminance() > 0.2f) scheme.surfaceContainerLowest else Color.White,
            highlight = Color.White.copy(alpha = 0.12f),
            shadow = Color.Black,
            shadowStrength = 1f,
            toolbar = SessionToolbarColors(
                bar = scheme.surfaceContainerLowest,
                button = scheme.surfaceContainerHigh,
                content = scheme.onSurface,
                mutedContent = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                hostChip = scheme.surfaceContainerLow,
                accentButton = scheme.secondaryContainer,
                onAccentButton = scheme.onSecondaryContainer,
            ),
        )
    } else {
        ControlSurfaceColors(
            isDark = false,
            casing = scheme.surfaceContainer,
            well = scheme.surfaceDim,
            wellRim = scheme.outlineVariant,
            capTop = scheme.surfaceContainerLowest,
            capBottom = scheme.surfaceContainerLow,
            capEdge = scheme.surfaceContainerHighest,
            capPressedTop = lerp(scheme.surfaceContainerLow, accent, 0.16f),
            capPressedBottom = lerp(scheme.surfaceContainerHigh, accent, 0.20f),
            label = scheme.onSurfaceVariant,
            labelPressed = accent,
            accent = accent,
            accentContainer = accentContainer,
            onAccentContainer = onAccentContainer,
            onAccent = if (accent.luminance() > 0.2f) scheme.surfaceContainerLowest else Color.White,
            highlight = Color.White.copy(alpha = 0.85f),
            shadow = Color.Black,
            shadowStrength = 0.35f,
            toolbar = SessionToolbarColors(
                bar = scheme.surfaceContainerHigh,
                button = scheme.surfaceContainerLowest,
                content = scheme.onSurface,
                mutedContent = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                hostChip = scheme.surfaceContainerLow,
                accentButton = scheme.secondaryContainer,
                onAccentButton = scheme.onSecondaryContainer,
            ),
        )
    }
}

/** [shadow] at [alpha], scaled down for light schemes where full-strength shadows look dirty. */
internal fun ControlSurfaceColors.shadow(alpha: Float): Color = shadow.copy(alpha = alpha * shadowStrength)

/** [highlight] scaled by [fraction] of its alpha. */
internal fun ControlSurfaceColors.highlight(fraction: Float): Color =
    highlight.copy(alpha = (highlight.alpha * fraction).coerceIn(0f, 1f))

/**
 * Pulls a fixed brand color (Xbox face letters, PlayStation symbols) a little toward the
 * scheme's accent hue, the way Material's color harmonization does, and adjusts its lightness
 * so it keeps contrast on light or dark caps.
 */
internal fun ControlSurfaceColors.harmonize(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    val accentHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), accentHsv)
    if (accentHsv[1] > 0.08f) {
        val delta = ((accentHsv[0] - hsv[0] + 540f) % 360f) - 180f
        val rotation = (delta * 0.5f).coerceIn(-8f, 8f)
        hsv[0] = (hsv[0] + rotation + 360f) % 360f
    }
    val harmonized = Color(android.graphics.Color.HSVToColor(hsv))
    return if (isDark) lerp(harmonized, Color.White, 0.08f) else lerp(harmonized, Color.Black, 0.22f)
}
