package dev.arnv.bluke

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import dev.arnv.bluke.bluetooth.BluetoothKeyboardManager
import dev.arnv.bluke.bluetooth.BluetoothState
import dev.arnv.bluke.data.migrateKeyboardCustomizationPreferences
import dev.arnv.bluke.ui.theme.MyApplicationTheme
import dev.arnv.bluke.ui.HomeScreen
import dev.arnv.bluke.ui.UpdateAvailableDialog
import dev.arnv.bluke.ui.UpdateOptInDialog
import dev.arnv.bluke.ui.WhatsNewDialog
import dev.arnv.bluke.update.isFreshInstall
import dev.arnv.bluke.update.loadBundledChangelog
import dev.arnv.bluke.update.markChangelogSeen
import dev.arnv.bluke.update.pendingWhatsNew
import dev.arnv.bluke.update.releasesToShow
import dev.arnv.bluke.update.GitHubRelease
import dev.arnv.bluke.update.UpdateCheckResult
import dev.arnv.bluke.update.UpdateChecker
import dev.arnv.bluke.update.answerUpdateOptIn
import dev.arnv.bluke.update.shouldAskUpdateOptIn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow

interface RemoteVolumeKeyHost {
    fun setRemoteVolumeKeyHandler(handler: ((keyCode: Int, isPressed: Boolean) -> Unit)?)
}

/** Lets the keyboard screen take presses from a physical keyboard before Android does. */
interface HardwareKeyHost {
    /** [handler] returns true when it used the event. */
    fun setHardwareKeyHandler(handler: ((KeyEvent) -> Boolean)?)
}

class MainActivity : ComponentActivity(), RemoteVolumeKeyHost, HardwareKeyHost {
    private lateinit var btManager: BluetoothKeyboardManager
    private var remoteVolumeKeyHandler: ((keyCode: Int, isPressed: Boolean) -> Unit)? = null
    private var hardwareKeyHandler: ((KeyEvent) -> Boolean)? = null
    private val pendingUpdate = MutableStateFlow<GitHubRelease?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (!::btManager.isInitialized || isDestroyed) return@registerForActivityResult
        // Notify Bluetooth service to re-check status after user interaction
        btManager.checkBluetoothCapabilities()
        ensureBluetoothSessionService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        migrateKeyboardCustomizationPreferences(sharedPrefs)
        if (!sharedPrefs.getBoolean("has_seen_onboarding", false)) {
            startActivity(android.content.Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        btManager = (application as BlukeApplication).bluetoothKeyboardManager
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                btManager.serviceState.collect { ensureBluetoothSessionService() }
            }
        }
        val contentView = findViewById<View>(android.R.id.content)
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                private var terminalStateObserved = false

                override fun onPreDraw(): Boolean {
                    if (btManager.serviceState.value is BluetoothState.InitializingCapabilities) {
                        return false
                    }
                    if (!terminalStateObserved) {
                        terminalStateObserved = true
                        contentView.postInvalidateOnAnimation()
                        return false
                    }
                    if (contentView.viewTreeObserver.isAlive) {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    return true
                }
            }
        )

        // Request Bluetooth and Location permissions dynamically
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)

        // Update check: only while the app is open, at most once per chosen interval, never in the background.
        val updateChecker = UpdateChecker(sharedPrefs, BuildConfig.VERSION_NAME)
        suspend fun checkForUpdate() {
            val result = updateChecker.checkIfDue()
            if (result is UpdateCheckResult.Available) pendingUpdate.value = result.release
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { checkForUpdate() }
        }

        // Changes since the last version the user saw, shown once after an update (never on a fresh install).
        // Developer options → "mock update popup" previews it with this version's entry.
        val changelog = loadBundledChangelog()
        val mockWhatsNew = sharedPrefs.getBoolean("is_developer_mode", false) &&
            sharedPrefs.getBoolean("mock_update_popup", false)
        val whatsNew = pendingWhatsNew(sharedPrefs, changelog, BuildConfig.VERSION_NAME, isFreshInstall())
            .ifEmpty { if (mockWhatsNew) releasesToShow(changelog, null, BuildConfig.VERSION_NAME) else emptyList() }

        setContent {
            MyApplicationTheme {
                HomeScreen(btManager = btManager)
                var showWhatsNew by remember { mutableStateOf(whatsNew.isNotEmpty()) }
                var askOptIn by remember { mutableStateOf(sharedPrefs.shouldAskUpdateOptIn) }
                val update by pendingUpdate.collectAsState()
                val connectedHost by btManager.connectedDevice.collectAsState()
                // These dialogs are held while a host is connected so they can't land under a finger that is typing.
                if (showWhatsNew && connectedHost == null) {
                    WhatsNewDialog(releases = whatsNew, onDismiss = {
                        sharedPrefs.markChangelogSeen(BuildConfig.VERSION_NAME)
                        sharedPrefs.edit().putBoolean("mock_update_popup", false).apply()
                        showWhatsNew = false
                    })
                }
                if (askOptIn && connectedHost == null && !showWhatsNew) {
                    UpdateOptInDialog { enable ->
                        sharedPrefs.answerUpdateOptIn(enable)
                        askOptIn = false
                        if (enable) lifecycleScope.launch { checkForUpdate() }
                    }
                }
                update?.takeIf { connectedHost == null && !askOptIn && !showWhatsNew }?.let { release ->
                    UpdateAvailableDialog(
                        release = release,
                        currentVersion = BuildConfig.VERSION_NAME,
                        onDismiss = { pendingUpdate.value = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::btManager.isInitialized) {
            ensureBluetoothSessionService()
        }
    }

    override fun onStop() {
        if (::btManager.isInitialized) {
            btManager.releaseAllInputs()
            if (!btManager.isAppVisible() && btManager.connectedDevice.value == null && !btManager.hasPendingConnection.value) {
                stopService(android.content.Intent(this, BluetoothSessionService::class.java))
            }
        }
        super.onStop()
    }

    private fun ensureBluetoothSessionService() {
        if (!::btManager.isInitialized || !btManager.isAppVisible()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        when (btManager.serviceState.value) {
            BluetoothState.BluetoothOff, BluetoothState.PermissionRequired,
            BluetoothState.Unsupported, BluetoothState.ProfileNotSupported -> return
            else -> Unit
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                this, android.content.Intent(this, BluetoothSessionService::class.java),
            )
        } catch (e: RuntimeException) {
            android.util.Log.w("BluetoothSession", "Could not start connection notification", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btManager.isInitialized) {
            btManager.checkBluetoothCapabilities()
            ensureBluetoothSessionService()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hardwareKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun setHardwareKeyHandler(handler: ((KeyEvent) -> Boolean)?) {
        hardwareKeyHandler = handler
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
        hardwareKeyHandler = null
        super.onDestroy()
    }
}
