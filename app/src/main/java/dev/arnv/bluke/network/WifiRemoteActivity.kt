package dev.arnv.bluke.network

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.theme.MyApplicationTheme

/**
 * Standalone screen for the optional WiFi (LAN) transport. Discovers Bluke
 * receivers advertised over mDNS and manages the connection to them. The rest
 * of the app is unaware of this screen: once connected, HID reports are
 * mirrored automatically (see WifiInputManager.mirror).
 */
class WifiRemoteActivity : ComponentActivity() {

    private lateinit var wifiManager: WifiInputManager

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = WifiInputManager.getInstance(applicationContext)

        setContent {
            MyApplicationTheme {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("WiFi Remote") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { innerPadding ->
                    WifiRemoteScreen(
                        wifiManager = wifiManager,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        wifiManager.startDiscovery()
    }

    override fun onStop() {
        super.onStop()
        wifiManager.stopDiscovery()
    }
}

@Composable
private fun WifiRemoteScreen(wifiManager: WifiInputManager, modifier: Modifier = Modifier) {
    val state by wifiManager.connectionState.collectAsState()
    val message by wifiManager.statusMessage.collectAsState()
    val hosts by wifiManager.discoveredHosts.collectAsState()
    val isDiscovering by wifiManager.isDiscovering.collectAsState()

    var pinDialogTarget by remember { mutableStateOf<WifiHost?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf(WifiInputManager.DEFAULT_PORT.toString()) }
    var manualPin by remember { mutableStateOf("") }

    fun connectTo(host: WifiHost) {
        val savedPin = wifiManager.storedPin(host.host, host.port)
        if (savedPin != null) {
            wifiManager.connect(host.host, host.port, savedPin)
        } else {
            pinDialogTarget = host
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (state) {
                        is WifiState.Connected -> Color(0xFF4CAF50)
                        is WifiState.Connecting -> MaterialTheme.colorScheme.primary
                        is WifiState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when (val s = state) {
                            is WifiState.Connected -> "Connected to ${s.receiverName}"
                            is WifiState.Connecting -> "Connecting..."
                            is WifiState.Error -> "Error"
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state is WifiState.Connected || state is WifiState.Connecting) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { wifiManager.disconnect() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Disconnect", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Discovered receivers
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RECEIVERS ON THIS NETWORK",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            if (hosts.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "No receivers found yet. Start the Bluke receiver on your PC (see the desktop folder of the project) and make sure both devices are on the same WiFi network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                hosts.forEachIndexed { index, host ->
                    val topRadius = if (index == 0) 24.dp else 4.dp
                    val bottomRadius = if (index == hosts.size - 1) 24.dp else 4.dp
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        shape = RoundedCornerShape(topStart = topRadius, topEnd = topRadius, bottomStart = bottomRadius, bottomEnd = bottomRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = host.serviceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${host.host}:${host.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { connectTo(host) },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Manual connection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "MANUAL CONNECTION",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it },
                            label = { Text("PC IP address") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it.filter { c -> c.isDigit() } },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = manualPin,
                        onValueChange = { manualPin = it.filter { c -> c.isDigit() } },
                        label = { Text("PIN (shown on the PC)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val port = manualPort.toIntOrNull() ?: WifiInputManager.DEFAULT_PORT
                            if (manualHost.isNotBlank() && manualPin.isNotBlank()) {
                                wifiManager.connect(manualHost.trim(), port, manualPin)
                            }
                        },
                        enabled = manualHost.isNotBlank() && manualPin.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = "The WiFi remote needs the Bluke receiver running on the PC — unlike Bluetooth, computers cannot accept keyboard input over the network natively. While connected over WiFi, keys, touchpad and gamepad input are sent to the receiver in addition to any Bluetooth host.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(24.dp))
    }

    // PIN entry dialog for discovered receivers without a stored PIN
    val target = pinDialogTarget
    if (target != null) {
        var pin by remember(target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinDialogTarget = null },
            title = { Text("Enter PIN") },
            text = {
                Column {
                    Text("Enter the PIN shown by the receiver on '${target.serviceName}'.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() } },
                        label = { Text("PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pin.isNotBlank(),
                    onClick = {
                        wifiManager.connect(target.host, target.port, pin)
                        pinDialogTarget = null
                    }
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { pinDialogTarget = null }) { Text("Cancel") }
            }
        )
    }
}
