package dev.arnv.bluke

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.time.Duration.Companion.milliseconds

class HelpActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isFirstRun = intent.getBooleanExtra("first_run", false)
        
        setContent {
            MyApplicationTheme {
                var timer by remember { mutableIntStateOf(if (isFirstRun) 5 else 0) }
                LaunchedEffect(timer) {
                    if (timer > 0) {
                        delay(1000L.milliseconds)
                        timer -= 1
                    }
                }
                androidx.activity.compose.BackHandler(enabled = isFirstRun && timer > 0) {
                    // Do nothing until timer completes
                }
                
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        LargeTopAppBar(
                            title = { Text("Help & Guide") },
                            navigationIcon = {
                                if (!isFirstRun || timer == 0) {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            scrollBehavior = scrollBehavior
                        )
                    },
                    bottomBar = {
                        if (isFirstRun) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { finish() },
                                    enabled = timer == 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(if (timer > 0) "I Understand ($timer)" else "I Understand")
                                }
                            }
                        }
                    }
                ) { innerPadding ->

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        
                        StepSection(
                            icon = Icons.Default.Bluetooth, 
                            title = "Connect a new host",
                            steps = listOf(
                                "Make the computer, TV, tablet, or phone visible in its Bluetooth settings.",
                                "Scan in Bluke and tap the host name.",
                                "Accept the matching pairing prompt on both devices.",
                                "Wait for Bluke's status dot to turn green before sending input."
                            )
                        )
                        
                        StepSection(
                            icon = Icons.Default.BluetoothConnected, 
                            title = "Repair a stale pairing",
                            steps = listOf(
                                "Forget the host in this phone's Bluetooth settings.",
                                "Remove Bluke or this phone from the host's Bluetooth settings.",
                                "Toggle Bluetooth on both sides, then pair again from Bluke.",
                                "Reinstalling Bluke alone does not clear the host's cached HID layout."
                            )
                        )
                        
                        StepSection(
                            icon = Icons.Default.Warning, 
                            title = "Pairing was refused",
                            isWarning = true,
                            steps = listOf(
                                "A refusal means the host or phone rejected the request; it does not prove HID is unsupported.",
                                "Retry once and accept every prompt on both devices.",
                                "If it repeats, use the full repair steps above."
                            )
                        )

                        StepSection(
                            icon = Icons.Default.ErrorOutline, 
                            title = "Controller and browser games",
                            isWarning = true,
                            steps = listOf(
                                "Start with D-pad: Native games. It is the standard controller representation.",
                                "Use D-pad: Browser games only when a web game ignores directions; testers may still work in both modes.",
                                "For Steam, enable the controller in Steam Input and verify it in Steam's controller test or the operating system's game-controller panel.",
                                "Bluke is a generic HID gamepad, not an Xbox XInput device. Some newer Windows games accept only XInput without a compatibility layer."
                            )
                        )

                        StepSection(
                            icon = Icons.Default.Warning,
                            title = "Platform limitations",
                            steps = listOf(
                                "Support depends on the phone firmware exposing Android's Bluetooth HID Device role.",
                                "Apple hosts may pair but can restrict composite keyboard, pointer, or gamepad functions; behavior varies by iOS/iPadOS release.",
                                "Host audio routing prevention is a best-effort workaround for Linux and may be undone by the host or phone firmware.",
                                "If Android repeatedly rejects HID registration, use Retry once. The incompatible-device screen appears only after the registration attempt ceiling."
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StepSection(icon: ImageVector, title: String, steps: List<String>, isWarning: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isWarning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isWarning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
