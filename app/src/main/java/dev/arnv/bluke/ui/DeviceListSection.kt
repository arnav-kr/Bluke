package dev.arnv.bluke.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.bluetooth.BluetoothState

@SuppressLint("MissingPermission")
internal fun LazyListScope.DeviceListSection(
    bluetoothState: BluetoothState,
    statusMessage: String,
    connectedDevice: BluetoothDevice?,
    bondedDevices: List<BluetoothDevice>,
    scannedDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    showMacAddress: Boolean,
    hideUnknownDevices: Boolean,
    hideUnsupportedDevices: Boolean,
    isPairedExpanded: Boolean,
    isDiscoveredExpanded: Boolean,
    onPairedExpandedChange: (Boolean) -> Unit,
    onDiscoveredExpandedChange: (Boolean) -> Unit,
    onConnect: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit
) {
    val currentlyConnectedState = connectedDevice
    if (currentlyConnectedState != null) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("ACTIVE CONNECTION")
                val deviceMessage = if ((currentlyConnectedState.name != null && statusMessage.contains(currentlyConnectedState.name)) || bluetoothState is BluetoothState.Connected) statusMessage else null
                DeviceRow(
                    name = currentlyConnectedState.name ?: "Unknown Host",
                    address = currentlyConnectedState.address,
                    showAddress = showMacAddress,
                    isConnected = true,
                    bondState = currentlyConnectedState.bondState,
                    statusText = deviceMessage,
                    shape = RoundedCornerShape(28.dp),
                    device = currentlyConnectedState,
                    onActionClick = onDisconnect
                )
            }
        }
    } else {
        val activeDeviceAttempt = bondedDevices.firstOrNull {
            (it.name != null && statusMessage.contains(it.name)) || statusMessage.contains(it.address)
        }
        if (activeDeviceAttempt != null && statusMessage.isNotEmpty() && statusMessage != "Disconnected") {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle("ACTIVE CONNECTION")
                    DeviceRow(
                        name = activeDeviceAttempt.name ?: "Unknown Host",
                        address = activeDeviceAttempt.address,
                        showAddress = showMacAddress,
                        isConnected = false,
                        bondState = activeDeviceAttempt.bondState,
                        statusText = statusMessage,
                        shape = RoundedCornerShape(28.dp),
                        device = activeDeviceAttempt,
                        onActionClick = { onConnect(activeDeviceAttempt) }
                    )
                }
            }
        }
    }

    val activeDeviceMac = currentlyConnectedState?.address
        ?: bondedDevices.firstOrNull { statusMessage.contains(it.name ?: "------") }?.address
    val idleBonded = bondedDevices.filter { device ->
        device.address != activeDeviceMac &&
            (!hideUnknownDevices || !device.name.isNullOrBlank()) &&
            (!hideUnsupportedDevices || classifyDevice(device.name, device).isSupported)
    }
    if (idleBonded.isNotEmpty()) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                CollapsibleSectionHeader("PAIRED DEVICES", "${idleBonded.size} devices", isPairedExpanded) {
                    onPairedExpandedChange(!isPairedExpanded)
                }
                if (isPairedExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        idleBonded.forEachIndexed { index, device ->
                            val deviceMsg = if ((device.name != null && statusMessage.contains(device.name)) || statusMessage.contains(device.address)) statusMessage else null
                            DeviceRow(
                                name = device.name ?: "Unknown Host",
                                address = device.address,
                                showAddress = showMacAddress,
                                isConnected = false,
                                bondState = device.bondState,
                                statusText = deviceMsg,
                                shape = listShape(index, idleBonded.lastIndex),
                                device = device,
                                onActionClick = { onConnect(device) }
                            )
                        }
                    }
                }
            }
        }
    } else if (bondedDevices.isEmpty()) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("PAIRED DEVICES")
                Text(
                    text = "No paired devices yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
            }
        }
    }

    val nonBondedDevices = scannedDevices.filter { device ->
        device.bondState != BluetoothDevice.BOND_BONDED &&
            device.address != activeDeviceMac &&
            (!hideUnknownDevices || !device.name.isNullOrBlank()) &&
            (!hideUnsupportedDevices || classifyDevice(device.name, device).isSupported)
    }
    if (nonBondedDevices.isNotEmpty() || isScanning) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                CollapsibleSectionHeader("DISCOVERED DEVICES", "${nonBondedDevices.size} found", isDiscoveredExpanded) {
                    onDiscoveredExpandedChange(!isDiscoveredExpanded)
                }
                if (isDiscoveredExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                        nonBondedDevices.forEachIndexed { index, device ->
                            val deviceMsg = if ((device.name != null && statusMessage.contains(device.name)) || statusMessage.contains(device.address)) statusMessage else null
                            DeviceRow(
                                name = device.name ?: "Unnamed Device",
                                address = device.address,
                                showAddress = showMacAddress,
                                isConnected = false,
                                bondState = device.bondState,
                                statusText = deviceMsg,
                                shape = listShape(index, nonBondedDevices.lastIndex),
                                device = device,
                                onActionClick = { onConnect(device) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

@androidx.compose.runtime.Composable
private fun CollapsibleSectionHeader(title: String, count: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(18.dp)
            )
        }
    }
}

private fun listShape(index: Int, lastIndex: Int): RoundedCornerShape {
    val topRadius = if (index == 0) 28.dp else 4.dp
    val bottomRadius = if (index == lastIndex) 28.dp else 4.dp
    return RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomStart = bottomRadius, bottomEnd = bottomRadius)
}
