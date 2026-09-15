package dev.arnv.bluke

import android.app.Application
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.utils.DeveloperLogManager

class BlukeApplication : Application() {
    private val bluetoothKeyboardManagerDelegate = lazy {
        BluetoothKeyboardManager(applicationContext)
    }
    val bluetoothKeyboardManager: BluetoothKeyboardManager by bluetoothKeyboardManagerDelegate

    override fun onCreate() {
        super.onCreate()
        DeveloperLogManager.init(applicationContext)
    }

    override fun onTerminate() {
        if (bluetoothKeyboardManagerDelegate.isInitialized()) {
            bluetoothKeyboardManager.close()
        }
        super.onTerminate()
    }
}
