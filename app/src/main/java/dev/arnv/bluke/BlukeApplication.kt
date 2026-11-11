package dev.arnv.bluke

import android.app.Application
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.data.removeLegacySoundData
import dev.arnv.bluke.utils.DeveloperLogManager

class BlukeApplication : Application() {
    private val bluetoothKeyboardManagerDelegate = lazy {
        BluetoothKeyboardManager(applicationContext)
    }
    val bluetoothKeyboardManager: BluetoothKeyboardManager by bluetoothKeyboardManagerDelegate

    override fun onCreate() {
        super.onCreate()
        DeveloperLogManager.init(applicationContext)
        Thread({ removeLegacySoundData(applicationContext) }, "LegacySoundCleanup").start()
        // Visibility belongs to the whole app, not just MainActivity (settings are activities too).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (bluetoothKeyboardManagerDelegate.isInitialized()) bluetoothKeyboardManager.setAppInForeground(true)
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0 && bluetoothKeyboardManagerDelegate.isInitialized()) {
                    bluetoothKeyboardManager.setAppInForeground(false)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Every screen draws behind the system bars and pads its content to stay clear of
                // them, on every Android version (Android 15+ enforces this anyway).
                (activity as? ComponentActivity)?.enableEdgeToEdge()
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun onTerminate() {
        if (bluetoothKeyboardManagerDelegate.isInitialized()) {
            bluetoothKeyboardManager.close()
        }
        super.onTerminate()
    }
}
