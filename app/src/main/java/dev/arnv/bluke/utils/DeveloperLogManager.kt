package dev.arnv.bluke.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val type: LogType = LogType.INFO
)

enum class LogType { INFO, ERROR, BLUETOOTH_PACKET }

object DeveloperLogManager {
    private const val MAX_LOG_COUNT = 1000
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val scope = CoroutineScope(Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private var autoSaveFile: File? = null

    val isEnabled: Boolean
        get() = prefs?.getBoolean("is_developer_mode", false) ?: false

    // Call this once on app startup or in AboutActivity when toggled
    fun init(context: Context) {
        prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        updateAutoSaveConfig(context)
    }

    fun updateAutoSaveConfig(context: Context) {
        val isAutoSaveEnabled = prefs?.getBoolean("dev_auto_save_logs", false) ?: false
        if (isAutoSaveEnabled) {
            val fileName = "bluke_dev_logs.txt"
            // Default to app-specific external files dir so it's easily visible to ADB without root
            val dir = context.getExternalFilesDir(null) 
            if (dir != null) {
                autoSaveFile = File(dir, fileName)
            }
        } else {
            autoSaveFile = null
        }
    }

    fun log(tag: String, message: String, type: LogType = LogType.INFO) {
        // Zero-cost abstraction for normal users
        if (!isEnabled) return

        val entry = LogEntry(tag = tag, message = message, type = type)
        
        // Update in-memory state for the LogViewer UI
        _logs.update { current -> (current + entry).takeLast(MAX_LOG_COUNT) }

        // Optionally autosave to file for live ADB tailing
        autoSaveFile?.let { file ->
            scope.launch {
                try {
                    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                    val time = sdf.format(Date(entry.timestamp))
                    val line = "[$time] [${entry.type}] $tag: $message\n"
                    FileWriter(file, true).use { it.write(line) }
                } catch (e: Exception) {
                    Log.e("DeveloperLogManager", "Failed to autosave log", e)
                }
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        autoSaveFile?.let { file ->
            scope.launch {
                try {
                    if (file.exists()) {
                        file.delete()
                        file.createNewFile()
                    }
                } catch (e: Exception) {
                    Log.e("DeveloperLogManager", "Failed to clear autosave file", e)
                }
            }
        }
    }
}
