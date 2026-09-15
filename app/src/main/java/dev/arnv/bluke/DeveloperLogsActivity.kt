package dev.arnv.bluke

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.utils.DeveloperLogManager
import dev.arnv.bluke.utils.LogType
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeveloperLogsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val scope = rememberCoroutineScope()
                
                val logs by DeveloperLogManager.logs.collectAsState()
                var searchQuery by remember { mutableStateOf("") }
                var isAutoSaveEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("dev_auto_save_logs", false)) }
                
                val filteredLogs = remember(logs, searchQuery) {
                    if (searchQuery.isBlank()) logs
                    else logs.filter { it.message.contains(searchQuery, ignoreCase = true) || it.tag.contains(searchQuery, ignoreCase = true) }
                }

                val listState = rememberLazyListState()
                
                // Auto-scroll to bottom when new logs arrive if already near bottom
                LaunchedEffect(filteredLogs.size) {
                    if (filteredLogs.isNotEmpty()) {
                        listState.animateScrollToItem(filteredLogs.size - 1)
                    }
                }

                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text("Developer Logs") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { DeveloperLogManager.clearLogs() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear Logs")
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val logText = filteredLogs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                                    val clip = ClipData.newPlainText("Developer Logs", logText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                            val file = File(downloadsDir, "bluke_logs_$timeStamp.txt")
                                            val logText = filteredLogs.joinToString("\n") { "[${it.tag}] ${it.message}" }
                                            FileWriter(file).use { it.write(logText) }
                                            Toast.makeText(context, "Saved to Downloads: ${file.name}", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save to File")
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Settings bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Autosave for ADB (files/bluke_dev_logs.txt)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = isAutoSaveEnabled,
                                onCheckedChange = { 
                                    isAutoSaveEnabled = it
                                    sharedPrefs.edit { putBoolean("dev_auto_save_logs", it) }
                                    DeveloperLogManager.updateAutoSaveConfig(context)
                                }
                            )
                        }
                        
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            placeholder = { Text("Filter logs...") },
                            singleLine = true
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Log list
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(filteredLogs) { logEntry ->
                                val color = when (logEntry.type) {
                                    LogType.ERROR -> MaterialTheme.colorScheme.error
                                    LogType.BLUETOOTH_PACKET -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = "[${logEntry.tag}] ${logEntry.message}",
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
