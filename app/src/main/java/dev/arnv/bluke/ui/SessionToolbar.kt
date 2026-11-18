package dev.arnv.bluke.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sizes shared by the toolbars shown while an input mode is active. */
internal object SessionToolbarDefaults {
    val BarHeight = 52.dp
    val ButtonHeight = 44.dp
    val ButtonCorner = 12.dp
    val ButtonPadding = 12.dp
    val ItemSpacing = 8.dp
    val IconSize = 18.dp
    val LabelSize = 13.sp
    val BarColor = Color.Black.copy(alpha = 0.45f)
    val ButtonColor = Color.White.copy(alpha = 0.15f)
    val DangerColor = Color(0xFFEF5350).copy(alpha = 0.35f)
    val LedOn = Color(0xFF39FF14)
    val LedOff = Color(0xFFFF9800)
    val MutedContent = Color.White.copy(alpha = 0.45f)
}

/** Colors of a [SessionToolbar] and its buttons. */
@Immutable
internal data class SessionToolbarColors(
    val bar: Color,
    val button: Color,
    val content: Color,
    val mutedContent: Color,
    val hostChip: Color,
    /** A tonal button that stands out from the rest, such as the current input mode. */
    val accentButton: Color,
    val onAccentButton: Color,
)

/** Translucent look that works over any keyboard theme; screens with a palette provide their own. */
internal val DefaultSessionToolbarColors = SessionToolbarColors(
    bar = SessionToolbarDefaults.BarColor,
    button = SessionToolbarDefaults.ButtonColor,
    content = Color.White,
    mutedContent = SessionToolbarDefaults.MutedContent,
    hostChip = Color.White.copy(alpha = 0.06f),
    accentButton = SessionToolbarDefaults.ButtonColor,
    onAccentButton = Color.White,
)

internal val LocalSessionToolbarColors = staticCompositionLocalOf { DefaultSessionToolbarColors }

/**
 * Full-width toolbar holding a [leading] and a [trailing] group. The groups sit at the two
 * edges while they fit, and the whole bar scrolls sideways once they don't.
 */
@Composable
internal fun SessionToolbar(
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
    /** Settings page opened by a gear button at the far right, after [trailing]. */
    settingsActivity: Class<out Activity>? = null,
    /** Colors for the bar and every toolbar button inside it. */
    colors: SessionToolbarColors = LocalSessionToolbarColors.current,
) = CompositionLocalProvider(LocalSessionToolbarColors provides colors) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(SessionToolbarDefaults.BarHeight)
            .background(LocalSessionToolbarColors.current.bar),
    ) {
        val barWidth = maxWidth
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = barWidth)
                .height(SessionToolbarDefaults.BarHeight)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SessionToolbarDefaults.ItemSpacing),
                content = leading,
            )
            Row(
                modifier = Modifier.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SessionToolbarDefaults.ItemSpacing),
            ) {
                trailing()
                if (settingsActivity != null) ToolbarSettingsButton(settingsActivity)
            }
        }
    }
}

/** Gear button that opens [activity]. */
@Composable
internal fun ToolbarSettingsButton(activity: Class<out Activity>) {
    val context = LocalContext.current
    ToolbarIconButton(
        onClick = { context.startActivity(Intent(context, activity)) },
        onClickLabel = "Open settings",
        modifier = Modifier.testTag("toolbar_settings_btn"),
    ) {
        ToolbarIcon(Icons.Default.Settings, "Settings")
    }
}

/** A 44dp tall toolbar button with an optional long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    enabled: Boolean = true,
    /** Greyed out like a disabled button, but still clickable (e.g. to explain why). */
    dimmed: Boolean = false,
    containerColor: Color = LocalSessionToolbarColors.current.button,
    horizontalPadding: Dp = SessionToolbarDefaults.ButtonPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(SessionToolbarDefaults.ButtonHeight)
            .widthIn(min = SessionToolbarDefaults.ButtonHeight)
            .clip(RoundedCornerShape(SessionToolbarDefaults.ButtonCorner))
            .background(if (enabled && !dimmed) containerColor else containerColor.copy(alpha = containerColor.alpha * 0.5f))
            .combinedClickable(
                enabled = enabled,
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/** A square 44dp toolbar button for a single icon. */
@Composable
internal fun ToolbarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    ToolbarButton(
        onClick = onClick,
        modifier = modifier.size(SessionToolbarDefaults.ButtonHeight),
        onLongClick = onLongClick,
        onClickLabel = onClickLabel,
        enabled = enabled,
        horizontalPadding = 0.dp,
        content = content,
    )
}

@Composable
internal fun ToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = LocalSessionToolbarColors.current.content,
) {
    Icon(imageVector, contentDescription, tint = tint, modifier = Modifier.size(SessionToolbarDefaults.IconSize))
}

@Composable
internal fun ToolbarIcon(
    painter: Painter,
    contentDescription: String?,
    tint: Color = LocalSessionToolbarColors.current.content,
) {
    Icon(painter, contentDescription, tint = tint, modifier = Modifier.size(SessionToolbarDefaults.IconSize))
}

@Composable
internal fun ToolbarLabel(
    text: String,
    color: Color = LocalSessionToolbarColors.current.content,
    maxWidth: Dp = Dp.Unspecified,
) {
    Text(
        text = text,
        color = color,
        fontSize = SessionToolbarDefaults.LabelSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (maxWidth == Dp.Unspecified) Modifier else Modifier.widthIn(max = maxWidth),
    )
}

/** A small status light. */
@Composable
internal fun ToolbarLed(on: Boolean, size: Dp = 8.dp, onColor: Color = SessionToolbarDefaults.LedOn, offColor: Color = Color.White.copy(alpha = 0.2f)) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (on) onColor else offColor),
    )
}

/** Connection light and host name, grouped into one non-interactive chip. */
@Composable
internal fun ToolbarHostChip(isConnected: Boolean, hostName: String) {
    Row(
        modifier = Modifier
            .height(SessionToolbarDefaults.ButtonHeight)
            .clip(RoundedCornerShape(SessionToolbarDefaults.ButtonCorner))
            .background(LocalSessionToolbarColors.current.hostChip)
            .padding(horizontal = SessionToolbarDefaults.ButtonPadding)
            .semantics(mergeDescendants = true) {
                contentDescription = if (isConnected) "Connected to $hostName" else "$hostName, offline"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolbarLed(on = true, size = 10.dp, onColor = if (isConnected) SessionToolbarDefaults.LedOn else SessionToolbarDefaults.LedOff)
        ToolbarLabel(hostName, maxWidth = 160.dp)
    }
}
