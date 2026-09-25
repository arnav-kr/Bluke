package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.ConsumerControl

@Composable
internal fun ConsumerButton(
    label: String,
    icon: ImageVector,
    control: ConsumerControl,
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    fillHeight: Boolean = false,
) {
    RemoteHoldButton(
        label = label,
        icon = icon,
        modifier = modifier,
        fillHeight = fillHeight,
        onPressedChange = { pressed -> btManager.sendConsumerControl(if (pressed) control else null) },
    )
}

@Composable
internal fun KeyboardButton(
    label: String,
    keyCode: Int,
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
    fillHeight: Boolean = false,
) {
    RemoteHoldButton(
        label = label,
        icon = null,
        modifier = modifier,
        fillHeight = fillHeight,
        onPressedChange = { pressed -> btManager.sendKey(keyCode, pressed) },
    )
}

@Composable
internal fun RemoteHoldButton(
    label: String,
    icon: ImageVector?,
    modifier: Modifier,
    fillHeight: Boolean = false,
    showLabel: Boolean = true,
    contentRotation: Float = 0f,
    cornerRadius: Dp = 16.dp,
    showBorder: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onPressedChange: (Boolean) -> Unit,
) {
    val currentPressHandler by rememberUpdatedState(onPressedChange)
    val resolvedContainerColor = containerColor ?: MaterialTheme.colorScheme.secondaryContainer
    val resolvedContentColor = contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(62.dp))
            .clip(RoundedCornerShape(cornerRadius))
            .background(resolvedContainerColor)
            .then(
                if (showBorder) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(cornerRadius),
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    currentPressHandler(true)
                    currentPressHandler(false)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentPressHandler(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            currentPressHandler(false)
                        }
                    },
                )
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = resolvedContentColor,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = contentRotation },
            )
        }
        if (showLabel) {
            Text(
                label,
                modifier = Modifier.graphicsLayer { rotationZ = contentRotation },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = resolvedContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RemoteIconHoldButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onPressedChange: (Boolean) -> Unit,
) {
    val currentPressHandler by rememberUpdatedState(onPressedChange)
    val resolvedContainerColor = containerColor ?: if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val resolvedContentColor = contentColor ?: if (emphasized) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(resolvedContainerColor)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    currentPressHandler(true)
                    currentPressHandler(false)
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentPressHandler(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            currentPressHandler(false)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = resolvedContentColor, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToolbarPill(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                },
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
