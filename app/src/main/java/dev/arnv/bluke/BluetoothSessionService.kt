package dev.arnv.bluke

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dev.arnv.bluke.bluetooth.BluetoothState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Keeps Android's UID-importance policy from unregistering an active HID session. */
class BluetoothSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.bluetooth_session_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.bluetooth_session_notification))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: RuntimeException) {
            Log.w("BluetoothSession", "Unable to keep the HID session in foreground", e)
            stopSelf()
            return
        }

        val manager = (application as BlukeApplication).bluetoothKeyboardManager
        scope.launch {
            combine(manager.connectedDevice, manager.serviceState, manager.appVisible, manager.hasPendingConnection) { device, state, visible, pending ->
                !shouldKeepBluetoothSession(visible, device != null, state, pending)
            }.collect { stop -> if (stop) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "bluetooth_session"
        const val NOTIFICATION_ID = 1
    }
}

internal fun shouldKeepBluetoothSession(
    appVisible: Boolean,
    hasConnectedHost: Boolean,
    state: BluetoothState,
    hasPendingConnection: Boolean = false,
): Boolean = when (state) {
    BluetoothState.BluetoothOff, BluetoothState.PermissionRequired,
    BluetoothState.Unsupported, BluetoothState.ProfileNotSupported -> false
    else -> appVisible || hasConnectedHost || hasPendingConnection
}
