package dev.arnv.bluke

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.arnv.bluke.data.KeyboardThemeSharing
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import dev.arnv.bluke.ui.ThemeQrCode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** True when the device has any camera the scanner could use. */
internal fun Context.canScanQrCodes(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Full-screen camera viewfinder that reads theme QR codes. Frames are decoded on the
 * device as they arrive and dropped right away; nothing is saved or sent.
 * Camera permission is asked the first time it opens.
 */
@Composable
internal fun ThemeQrScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (KeyboardThemeDefinition) -> Unit,
) {
    val context = LocalContext.current
    var permission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        askedOnce = true
        permanentlyDenied = !granted && context.findActivity()?.let {
            !it.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        } == true
    }
    LaunchedEffect(Unit) { if (!permission) request.launch(Manifest.permission.CAMERA) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            if (permission) {
                CameraScanner(onDismiss = onDismiss, onScanned = onScanned)
            } else if (askedOnce) {
                PermissionNeeded(
                    permanentlyDenied = permanentlyDenied,
                    onRetry = { request.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PermissionNeeded(
    permanentlyDenied: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access needed", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Simply Bluke uses the camera only to read theme QR codes. Pictures are read on your phone and never saved or sent. " +
                "You can also import a QR code from a picture instead.",
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text("Open app settings") }
        } else {
            Button(onClick = onRetry) { Text("Allow camera") }
        }
        TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
    }
}

@Composable
private fun CameraScanner(
    onDismiss: () -> Unit,
    onScanned: (KeyboardThemeDefinition) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Point the camera at a Simply Bluke theme QR code.") }
    var cameraError by remember { mutableStateOf(false) }
    val currentOnScanned by rememberUpdatedState(onScanned)

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val done = AtomicBoolean(false)
        var lastRejected: String? = null
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .build()
        analysis.setAnalyzer(executor) { image: ImageProxy ->
            image.use {
                if (done.get()) return@use
                val plane = it.planes[0]
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining()).also(buffer::get)
                // The last row can be shorter than the stride; pad so the source stays in bounds.
                val luminance = if (bytes.size >= plane.rowStride * it.height) bytes
                else bytes.copyOf(plane.rowStride * it.height)
                val text = ThemeQrCode.decodeLuminance(luminance, plane.rowStride, it.width, it.height)
                    ?: return@use
                if (text == lastRejected) return@use
                try {
                    val theme = KeyboardThemeSharing.parse(text)
                    if (done.compareAndSet(false, true)) mainExecutor.execute { currentOnScanned(theme) }
                } catch (failure: KeyboardThemeSharing.ImportException) {
                    lastRejected = text
                    val message = if (KeyboardThemeSharing.LINK_SCHEME in text) failure.message
                    else "That QR code isn't a Simply Bluke theme."
                    mainExecutor.execute { hint = message ?: "That QR code can't be read." }
                }
            }
        }

        providerFuture.addListener({
            try {
                val cameraProvider = providerFuture.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (_: Exception) {
                cameraError = true
            }
        }, mainExecutor)

        onDispose {
            done.set(true)
            analysis.clearAnalyzer()
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .widthIn(max = 360.dp)
                .aspectRatio(1f)
                .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 8.dp, end = 8.dp, top = 32.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close scanner") }
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }) {
                    Icon(
                        if (torchOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                        contentDescription = if (torchOn) "Turn off flashlight" else "Turn on flashlight",
                    )
                }
            }
        }
        Text(
            text = if (cameraError) "The camera couldn't be started. Close other camera apps and try again, or import a picture instead."
            else hint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // Dialog windows don't always report the navigation bar; keep clear of it anyway.
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 88.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
