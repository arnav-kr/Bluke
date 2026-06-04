package dev.arnv.bluke.ui
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass
import dev.arnv.bluke.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp




/**
 * Categories of Bluetooth devices with custom dynamic icons, matching backgrounds, and keyboard HID capability flags
 */
enum class DeviceType(
    val id: String,
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bgColor: Color,
    val tintColor: Color,
    val isSupported: Boolean
) {
    COMPUTER("computer", "Computer", Icons.Default.Laptop, Color(0xFFE8DDFF), Color(0xFF65558F), true),
    PHONE("phone", "Phone", Icons.Default.PhoneAndroid, Color(0xFFD6F0FF), Color(0xFF006494), true),
    TV("tv", "Smart TV", Icons.Default.Tv, Color(0xFFE0F7FA), Color(0xFF006064), true),
    AUDIO("audio", "Audio Device", Icons.Default.Headphones, Color(0xFFFBE7E9), Color(0xFFC00021), false),
    MOUSE("mouse", "Input Accessory", Icons.Default.Mouse, Color(0xFFFFD8E4), Color(0xFFB11E4C), false),
    GAMEPAD("gamepad", "Game Controller", Icons.Default.SportsEsports, Color(0xFFFFE9A6), Color(0xFF7D5200), false),
    WATCH("watch", "Smartwatch", Icons.Default.Watch, Color(0xFFF3EDF7), Color(0xFF65558F), false),
    DEFAULT("default", "Bluetooth Device", Icons.Default.Bluetooth, Color(0xFFE2F1FF), Color(0xFF0F5A9E), true)
}

/**
 * Keyword and Bluetooth major class categorization helper
 */
fun classifyDevice(name: String?, device: BluetoothDevice?): DeviceType {
    val devName = name?.lowercase() ?: ""
    val majorClass = try {
        device?.bluetoothClass?.majorDeviceClass
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }

    // 1. Mouse / Pointer / Keyboard / Peripheral devices
    if (devName.contains("mouse") || devName.contains("atk") || devName.contains("g305") || 
        devName.contains("trackpad") || devName.contains("pointer") || devName.contains("mx master") ||
        devName.contains("keyboard") || devName.contains("keycap") || devName.contains("stylus") ||
        majorClass == 1280 || majorClass == BluetoothClass.Device.Major.PERIPHERAL) {
        
        // Exclude gamepads reporting as peripheral accessories
        if (devName.contains("controller") || devName.contains("gamepad") || devName.contains("xbox") || 
            devName.contains("playstation") || devName.contains("ps4") || devName.contains("ps5") || 
            devName.contains("switch") || devName.contains("joycon") || devName.contains("nintendo") || 
            devName.contains("wireless controller")) {
            return DeviceType.GAMEPAD
        }
        return DeviceType.MOUSE
    }

    // 2. Game controller / Gamepad
    if (devName.contains("controller") || devName.contains("gamepad") || devName.contains("xbox") || 
        devName.contains("playstation") || devName.contains("ps4") || devName.contains("ps5") || 
        devName.contains("switch") || devName.contains("joycon") || devName.contains("nintendo") || 
        devName.contains("wireless controller") || devName.contains("game") ||
        majorClass == BluetoothClass.Device.Major.TOY) {
        return DeviceType.GAMEPAD
    }

    // 3. Audio / Buds / Headphones (e.g. Earphones, audio receivers)
    if (devName.contains("buds") || devName.contains("ear") || devName.contains("headphone") || 
        devName.contains("headset") || devName.contains("audio") || devName.contains("speaker") || 
        devName.contains("sound") || devName.contains("music") || devName.contains("cmf") ||
        devName.contains("soundcore") || devName.contains("airpods") ||
        majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
        return DeviceType.AUDIO
    }

    // 4. Smartwatch / Wearable
    if (devName.contains("watch") || devName.contains("wear") || devName.contains("fitbit") || 
        devName.contains("band") || devName.contains("garmin") || devName.contains("wearable") || 
        majorClass == BluetoothClass.Device.Major.WEARABLE) {
        return DeviceType.WATCH
    }

    // 5. Computer / Laptop
    if (devName.contains("laptop") || devName.contains("pc") || devName.contains("computer") || 
        devName.contains("desktop") || devName.contains("mac") || devName.contains("linux") || 
        devName.contains("windows") || devName.contains("archy") || devName.contains("arch") || 
        devName.contains("host") || majorClass == BluetoothClass.Device.Major.COMPUTER) {
        return DeviceType.COMPUTER
    }

    // 6. Phone / Smartphone / Tablet
    if (devName.contains("phone") || devName.contains("galaxy") || devName.contains("iphone") || 
        devName.contains("pixel") || devName.contains("oneplus") || devName.contains("xiaomi") || 
        devName.contains("redmi") || devName.contains("mobile") || devName.contains("tablet") ||
        majorClass == BluetoothClass.Device.Major.PHONE) {
        return DeviceType.PHONE
    }

    // 7. TV / Chromecast / Smart Screen
    if (devName.contains("tv") || devName.contains("shield") || devName.contains("appletv") || 
        devName.contains("chromecast") || devName.contains("firestick") || devName.contains("television") || 
        devName.contains("smart tv")) {
        return DeviceType.TV
    }

    return DeviceType.DEFAULT
}

/**
 * Beautiful device row for bonded or discovered host entities with dynamic visual categorization
 */
@Composable
fun DeviceRow(
    name: String,
    address: String,
    showAddress: Boolean = true,
    isConnected: Boolean,
    bondState: Int,
    statusText: String? = null,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    device: BluetoothDevice? = null,
    onActionClick: () -> Unit
) {
    val isError = statusText?.let { it.contains("Failed", ignoreCase = true) || it.contains("error", ignoreCase = true) || it.contains("reject", ignoreCase = true) } == true
    val isWorking = (!isError && !isConnected && statusText?.let { it.contains("Connecting", ignoreCase=true) || it.contains("Pairing with", ignoreCase=true) } == true) || 
                    (isConnected && statusText?.contains("disconnect", ignoreCase=true) == true)
    
    // Categorize device to show specialized icons and capability badges
    val deviceType = classifyDevice(name, device)

    Surface(
        onClick = onActionClick,
        enabled = isConnected || deviceType.isSupported,
        shape = shape,
        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Beautiful dynamic icon container
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) MaterialTheme.colorScheme.surface else deviceType.bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), 
                            strokeWidth = 2.dp, 
                            color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = deviceType.icon,
                            contentDescription = deviceType.displayName,
                            tint = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else if (isError) MaterialTheme.colorScheme.error else deviceType.tintColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (showAddress) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    // Compact Row of Status and Capability Tags
                    if (!deviceType.isSupported || isError) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // Compatibility warning tag if keyboard emulation cannot work on this device type
                            if (!deviceType.isSupported) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "UNSUPPORTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // Bluetooth pairing/connection error tag
                            if (isError) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "FAILED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = onActionClick,
                enabled = isConnected || deviceType.isSupported,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = if (isConnected) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else if (isWorking) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (bondState == BluetoothDevice.BOND_BONDING) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConnected) "Disconnect" 
                           else "Connect",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}