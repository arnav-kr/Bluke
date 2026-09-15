package dev.arnv.bluke.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.bluetooth.BluetoothState

@Composable
internal fun ProfileNotSupportedScreen(
    bluetoothState: BluetoothState,
    onEnableBluetooth: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = when (bluetoothState) {
                is BluetoothState.Unsupported -> "Bluetooth Not Supported"
                is BluetoothState.ProfileNotSupported -> "HID Profile Not Supported"
                else -> "Bluetooth is Off"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        if (bluetoothState is BluetoothState.ProfileNotSupported) {
            Text(
                text = "Your device's Bluetooth firmware does not support HID Device mode, which Bluke requires to act as a keyboard, mouse, or gamepad.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This is a manufacturer limitation and cannot be fixed by the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = when (bluetoothState) {
                    is BluetoothState.Unsupported -> "This device does not have Bluetooth hardware."
                    else -> "Please enable Bluetooth to connect devices."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(32.dp))
        if (bluetoothState is BluetoothState.BluetoothOff) {
            Button(
                onClick = onEnableBluetooth,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Turn On Bluetooth")
            }
        } else if (bluetoothState is BluetoothState.ProfileNotSupported) {
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}
