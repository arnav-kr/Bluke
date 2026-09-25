package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.ConsumerControl

@Composable
internal fun ConsumerButton(
    label: String,
    icon: ImageVector,
    control: ConsumerControl,
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
) {
    RemoteHoldButton(
        label = label,
        icon = icon,
        modifier = modifier,
        onPressedChange = { pressed -> btManager.sendConsumerControl(if (pressed) control else null) },
    )
}

@Composable
internal fun KeyboardButton(
    label: String,
    keyCode: Int,
    btManager: BluetoothKeyboardManager,
    modifier: Modifier,
) {
    RemoteHoldButton(
        label = label,
        icon = null,
        modifier = modifier,
        onPressedChange = { pressed -> btManager.sendKey(keyCode, pressed) },
    )
}

@Composable
internal fun RemoteHoldButton(
    label: String,
    icon: ImageVector?,
    modifier: Modifier,
    onPressedChange: (Boolean) -> Unit,
) {
    val currentPressHandler by rememberUpdatedState(onPressedChange)
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
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
        if (icon != null) Icon(icon, null, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ToolbarPill(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
