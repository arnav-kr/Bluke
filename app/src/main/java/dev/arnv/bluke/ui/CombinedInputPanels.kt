package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager

@Composable
internal fun CombinedToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun CombinedTouchpadPanel(
    modifier: Modifier,
    btManager: BluetoothKeyboardManager,
    sensitivity: Float,
    scrollSensitivity: Float,
    triggerVibration: (Long) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .testTag("combined_touch_surface"),
    ) {
        TouchGestureLayer(
            btManager = btManager,
            sensitivity = sensitivity,
            scrollSensitivity = scrollSensitivity,
            buttonMode = TrackpadButtonMode.CLICKPAD,
            triggerVibration = triggerVibration,
            showNumpadLed = false,
        )
        Text(
            text = "Touchpad",
            color = Color.White.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )
    }
}

@Composable
internal fun CombinedKeyboardPanel(
    modifier: Modifier,
    geometry: KeyboardGeometry,
    theme: KeyboardThemeDefinition,
    characterLayout: KeyboardCharacterLayout,
    activePressedKeys: List<Int>,
    isConnected: Boolean,
    isCapsLockActive: Boolean,
    isNumLockActive: Boolean,
    isScrollLockActive: Boolean,
    keySensitivity: Float,
    onKeyPressChange: (Int, Boolean) -> Unit,
    isFnActive: Boolean,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        KeyboardView(
            geometry = geometry,
            theme = theme,
            characterLayout = characterLayout,
            activePressedKeys = activePressedKeys,
            isConnected = isConnected,
            isCapsLockActive = isCapsLockActive,
            isNumLockActive = isNumLockActive,
            isScrollLockActive = isScrollLockActive,
            keySensitivity = keySensitivity,
            onKeyPressChange = onKeyPressChange,
        )
        if (isFnActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            ) {
                FnShortcutOverlay()
            }
        }
    }
}

@Composable
internal fun CombinedResizeHandle(
    isEditing: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val gestureModifier = if (isEditing) {
        Modifier.pointerInput(isEditing) {
            detectHorizontalDragGestures(
                onDragEnd = currentOnDragEnd,
                onDragCancel = currentOnDragEnd,
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    currentOnDrag(amount)
                },
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .width(if (isEditing) 28.dp else 8.dp)
            .fillMaxHeight()
            .then(gestureModifier)
            .testTag("combined_layout_resize_handle"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(if (isEditing) 20.dp else 2.dp)
                .fillMaxHeight(if (isEditing) 0.32f else 0.55f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isEditing) MaterialTheme.colorScheme.primaryContainer
                    else Color.White.copy(alpha = 0.18f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isEditing) {
                Icon(
                    Icons.Default.DragHandle,
                    "Drag to resize keyboard and touchpad",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
