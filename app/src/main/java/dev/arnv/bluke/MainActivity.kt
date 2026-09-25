package dev.arnv.bluke

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import dev.arnv.bluke.sound.migrateSoundPreferences
import dev.arnv.bluke.data.migrateKeyboardCustomizationPreferences
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.HomeScreen

interface RemoteVolumeKeyHost {
    fun setRemoteVolumeKeyHandler(handler: ((keyCode: Int, isPressed: Boolean) -> Unit)?)
}

class MainActivity : ComponentActivity(), RemoteVolumeKeyHost {
    private lateinit var btManager: BluetoothKeyboardManager
    private lateinit var soundSynth: KeyboardSoundSynthesizer
    private var remoteVolumeKeyHandler: ((keyCode: Int, isPressed: Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Notify Bluetooth service to re-check status after user interaction
        btManager.checkBluetoothCapabilities()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        migrateSoundPreferences(sharedPrefs)
        migrateKeyboardCustomizationPreferences(sharedPrefs)
        if (!sharedPrefs.getBoolean("has_seen_onboarding", false)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        btManager = (application as BlukeApplication).bluetoothKeyboardManager

        soundSynth = KeyboardSoundSynthesizer(applicationContext)

        // Request Bluetooth and Location permissions dynamically
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HomeScreen(
                    btManager = btManager,
                    soundSynth = soundSynth
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btManager.isInitialized) {
            btManager.checkBluetoothCapabilities()
        }
        if (::soundSynth.isInitialized) {
            soundSynth.reloadSelectedSoundPack()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = remoteVolumeKeyHandler
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (handler != null && isVolumeKey) {
            if (event.repeatCount == 0) {
                handler(keyCode, true)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val handler = remoteVolumeKeyHandler
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (handler != null && isVolumeKey) {
            handler(keyCode, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun setRemoteVolumeKeyHandler(
        handler: ((keyCode: Int, isPressed: Boolean) -> Unit)?,
    ) {
        remoteVolumeKeyHandler = handler
    }

    override fun onDestroy() {
        remoteVolumeKeyHandler = null
        super.onDestroy()
        if (::soundSynth.isInitialized) {
            soundSynth.release()
        }
    }
}
