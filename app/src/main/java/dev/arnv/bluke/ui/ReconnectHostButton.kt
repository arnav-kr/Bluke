package dev.arnv.bluke.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.bluetooth.HidLifecycleState

@Composable
internal fun ReconnectHostButton(btManager: BluetoothKeyboardManager, iconOnly: Boolean = false) {
    val state by btManager.serviceState.collectAsState()
    val lifecycle by btManager.lifecycleState.collectAsState()
    val devices by btManager.bondedDevices.collectAsState()
    val target = btManager.getReconnectTarget()?.takeIf { it in devices } ?: return
    if (state is BluetoothState.Connected || state.blocksInputLaunch()) return
    val enabled = lifecycle !is HidLifecycleState.Connecting &&
        lifecycle !is HidLifecycleState.Registering && lifecycle !is HidLifecycleState.BindingProxy
    Row(
        modifier = Modifier.height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.15f else 0.08f))
            .clickable(enabled = enabled) { btManager.connectDevice(target) }
            .padding(horizontal = 8.dp)
            .testTag("reconnect_host_btn"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Refresh, "Reconnect to last host", tint = Color.White, modifier = Modifier.size(12.dp))
        if (!iconOnly) Text("Reconnect", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
