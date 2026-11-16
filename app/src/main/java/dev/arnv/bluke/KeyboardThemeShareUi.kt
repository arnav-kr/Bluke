package dev.arnv.bluke

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.arnv.bluke.data.KeyboardThemeSharing
import dev.arnv.bluke.ui.KeyLayoutInfo
import dev.arnv.bluke.ui.KeyboardCharacterLayout
import dev.arnv.bluke.ui.KeyboardGeometry
import dev.arnv.bluke.ui.KeyboardStyle
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import dev.arnv.bluke.ui.KeyboardThemeGenerator
import dev.arnv.bluke.ui.KeyboardView
import dev.arnv.bluke.ui.ThemeCardRenderer
import dev.arnv.bluke.ui.ThemeQrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Case color behind the preview keyboard, brushed when the theme asks for metal. */
internal fun caseBrush(theme: KeyboardThemeDefinition): Brush {
    val case = Color(theme.caseArgb)
    return if (theme.caseMetallic) {
        Brush.linearGradient(
            colors = listOf(case, case.copy(alpha = 0.85f), case.copy(alpha = 0.7f), case, case.copy(alpha = 0.9f)),
            start = Offset.Zero,
            end = Offset(500f, 500f),
        )
    } else {
        SolidColor(case)
    }
}

/** A keyboard drawn in [theme] on its own case, the way the keyboard screen shows it. */
@Composable
internal fun ThemePreviewKeyboard(
    theme: KeyboardThemeDefinition,
    style: KeyboardStyle,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    geometry: KeyboardGeometry = KeyboardGeometry.COMPACT_75,
    rowsOverride: List<List<KeyLayoutInfo>>? = null,
    touchLayout: Boolean = false,
    selectedStyleId: String? = null,
    onKeySelected: ((KeyLayoutInfo) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black)
            .background(caseBrush(theme))
            .padding(if (style == KeyboardStyle.MINIMAL) 4.dp else 8.dp),
    ) {
        KeyboardView(
            geometry = geometry,
            theme = theme,
            characterLayout = KeyboardCharacterLayout.US_QWERTY,
            activePressedKeys = emptyList(),
            isCapsLockActive = false,
            isNumLockActive = false,
            isScrollLockActive = false,
            selectedStyleId = selectedStyleId,
            rowsOverride = rowsOverride,
            fillHeight = touchLayout,
            legendScale = if (touchLayout) 1.5f else 1f,
            style = style,
            onKeySelected = onKeySelected,
            onKeyPressChange = { _, _ -> },
        )
    }
}

// ---------------- Share ----------------

private fun fileSlug(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { "keyboard-theme" }

/** Writes [write] to a fresh file in the cache and opens the share sheet for it. */
private fun shareFile(context: Context, fileName: String, mimeType: String, write: (File) -> Unit) {
    val directory = File(context.cacheDir, "shared_themes").apply {
        mkdirs()
        listFiles()?.forEach { it.delete() }
    }
    val file = File(directory, fileName)
    write(file)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData carries the read grant to the share sheet itself, so it can show a preview.
    send.clipData = android.content.ClipData.newRawUri(fileName, uri)
    val chooser = Intent.createChooser(send, "Share keyboard theme")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(chooser)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareThemeDialog(
    theme: KeyboardThemeDefinition,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // The QR code holds the whole theme as a simplybluke://theme link; no server involved.
    val link = remember(theme) { KeyboardThemeSharing.toLink(theme) }
    val qrMatrix = remember(link) { ThemeQrCode.encode(link) }
    val qrBitmap: Bitmap? = remember(qrMatrix) { qrMatrix?.let { ThemeQrCode.toBitmap(it) } }
    val slug = remember(theme.name) { fileSlug(theme.name) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // The card (name, QR, base colors) is drawn only when it's first needed.
    val card = remember(theme, qrMatrix) { lazy { qrMatrix?.let { ThemeCardRenderer.render(theme, it) } } }

    // Android 9 can't add to the gallery without a storage permission, so it saves through the file picker.
    val saveCardFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = card.value
        if (uri == null || bitmap == null) return@rememberLauncherForActivityResult
        status = try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?.let { "Image saved." } ?: "Couldn't save the image."
        } catch (_: Exception) {
            "Couldn't save the image."
        }
    }

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        status = writeToUri(context.contentResolver, uri, KeyboardThemeSharing.toJsonText(theme))
    }
    val saveMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        status = writeToUri(context.contentResolver, uri, KeyboardThemeSharing.toMarkdown(theme))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Share, contentDescription = null) },
        title = { Text("Share ${theme.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR code for ${theme.name}",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 280.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.CenterHorizontally),
                    )
                    Text(
                        "Scan it with Simply Bluke on another phone (Themes → Scan), or import a screenshot of it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "This theme changes too many single keys to fit in a QR code. Share the file instead.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (qrBitmap != null) {
                        AssistChip(
                            onClick = {
                                runCatching {
                                    shareFile(context, "$slug-qr.png", "image/png") { file ->
                                        val image = card.value ?: qrBitmap
                                        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                    }
                                }.onFailure { status = "Couldn't share the QR image." }
                            },
                            label = { Text("QR image") },
                            leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                        )
                        AssistChip(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                                    saveCardFile.launch("$slug-qr.png")
                                } else {
                                    scope.launch {
                                        status = withContext(Dispatchers.IO) {
                                            val bitmap = card.value
                                            if (bitmap != null && saveImageToGallery(context, bitmap, "$slug-qr")) {
                                                "Saved to Pictures/Simply Bluke."
                                            } else {
                                                "Couldn't save the image."
                                            }
                                        }
                                    }
                                }
                            },
                            label = { Text("Save to gallery") },
                            leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                        )
                    }
                    AssistChip(
                        onClick = {
                            runCatching {
                                shareFile(context, "$slug.json", "application/json") {
                                    it.writeText(KeyboardThemeSharing.toJsonText(theme))
                                }
                            }.onFailure { status = "Couldn't share the file." }
                        },
                        label = { Text("JSON file") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = {
                            runCatching {
                                shareFile(context, "$slug.md", "text/markdown") {
                                    it.writeText(KeyboardThemeSharing.toMarkdown(theme))
                                }
                            }.onFailure { status = "Couldn't share the file." }
                        },
                        label = { Text("Markdown file") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { saveJson.launch("$slug.json") },
                        label = { Text("Save .json") },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { saveMarkdown.launch("$slug.md") },
                        label = { Text("Save .md") },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                    )
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * Adds [bitmap] to the gallery under Pictures/Simply Bluke (Android 10 and newer;
 * no permission needed). Returns false if it couldn't be written.
 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
private fun saveImageToGallery(context: Context, bitmap: Bitmap, baseName: String): Boolean {
    val resolver = context.contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$baseName.png")
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_PICTURES}/Simply Bluke")
        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
    }
    val collection = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: return false
    return try {
        val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        if (!written) error("not written")
        values.clear()
        values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}

private fun writeToUri(resolver: ContentResolver, uri: Uri, text: String): String = try {
    val stream = resolver.openOutputStream(uri, "wt")
    stream?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    if (stream == null) "Couldn't save the file." else "Saved."
} catch (_: IOException) {
    "Couldn't save the file."
} catch (_: SecurityException) {
    "Couldn't save the file."
}

// ---------------- Import ----------------

/** Reads a theme from a file the user picked or opened: JSON, Markdown, or a QR image. */
internal suspend fun readThemeFromUri(resolver: ContentResolver, uri: Uri): KeyboardThemeDefinition =
    withContext(Dispatchers.IO) {
        val type = resolver.getType(uri).orEmpty()
        if (type.startsWith("image/")) return@withContext themeFromQrImage(resolver, uri)
        val text = try {
            resolver.openInputStream(uri)?.use(::readCapped)
                ?: throw KeyboardThemeSharing.ImportException("Couldn't open this file.")
        } catch (_: IOException) {
            throw KeyboardThemeSharing.ImportException("Couldn't open this file.")
        } catch (_: SecurityException) {
            throw KeyboardThemeSharing.ImportException("Simply Bluke isn't allowed to open this file.")
        }
        try {
            KeyboardThemeSharing.parse(text)
        } catch (error: KeyboardThemeSharing.ImportException) {
            // Some file pickers report images with no type; give the QR reader a go.
            if (type.isEmpty() || type == "application/octet-stream") {
                runCatching { themeFromQrImage(resolver, uri) }.getOrNull()?.let { return@withContext it }
            }
            throw error
        }
    }

private fun themeFromQrImage(resolver: ContentResolver, uri: Uri): KeyboardThemeDefinition {
    val text = try {
        ThemeQrCode.decode(resolver, uri)
    } catch (_: IOException) {
        throw KeyboardThemeSharing.ImportException("Couldn't read this image.")
    } catch (_: SecurityException) {
        throw KeyboardThemeSharing.ImportException("Simply Bluke isn't allowed to open this image.")
    } catch (_: OutOfMemoryError) {
        throw KeyboardThemeSharing.ImportException("This image is too large to read.")
    } ?: throw KeyboardThemeSharing.ImportException("No QR code found in this image. Try a sharper, uncropped picture.")
    return KeyboardThemeSharing.parse(text)
}

private fun readCapped(stream: InputStream): String {
    val limit = KeyboardThemeSharing.MAX_INPUT_CHARS
    // InputStream.readNBytes needs API 33; read in chunks up to the limit instead.
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        if (output.size() > limit) throw KeyboardThemeSharing.ImportException("This file is too large to be a keyboard theme.")
    }
    return output.toString(Charsets.UTF_8.name())
}

@Composable
internal fun ImportThemeDialog(
    onDismiss: () -> Unit,
    onParsed: (KeyboardThemeDefinition) -> Unit,
    /** Opens the camera scanner; null on devices without a camera. */
    onScan: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun load(block: suspend () -> KeyboardThemeDefinition) {
        busy = true
        error = null
        scope.launch {
            try {
                onParsed(block())
            } catch (failure: KeyboardThemeSharing.ImportException) {
                error = failure.message
            } finally {
                busy = false
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { load { readThemeFromUri(context.contentResolver, it) } }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { load { withContext(Dispatchers.IO) { themeFromQrImage(context.contentResolver, it) } } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a theme") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Bring in a theme someone shared: a .json or .md file, or a theme QR code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onScan != null) {
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = onScan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan a QR code")
                    }
                }
                FilledTonalButton(
                    enabled = !busy,
                    onClick = {
                        pickFile.launch(
                            arrayOf("application/json", "text/markdown", "text/x-markdown", "text/plain", "application/octet-stream", "image/*"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose a file")
                }
                FilledTonalButton(
                    enabled = !busy,
                    onClick = {
                        pickImage.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Read a QR code from a picture")
                }
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it.take(KeyboardThemeSharing.MAX_INPUT_CHARS) },
                    label = { Text("Or paste theme text") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 4,
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = {
                            clipboard.getText()?.text?.let { pasted = it.take(KeyboardThemeSharing.MAX_INPUT_CHARS) }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    enabled = !busy && pasted.isNotBlank(),
                    onClick = { load { KeyboardThemeSharing.parse(pasted) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Read pasted text")
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ImportPreviewDialog(
    theme: KeyboardThemeDefinition,
    style: KeyboardStyle,
    onDismiss: () -> Unit,
    onImport: (edit: Boolean) -> Unit,
) {
    val lowContrast = remember(theme) { KeyboardThemeGenerator.lowContrastParts(theme) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${theme.name}?", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePreviewKeyboard(theme = theme, style = style, height = 120.dp)
                val details = buildList {
                    if (theme.keyOverrides.isNotEmpty()) add("${theme.keyOverrides.size} single-key changes")
                    if (theme.caseMetallic) add("metallic case")
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lowContrast.isNotEmpty()) {
                    Text(
                        "Hard-to-read key text: ${lowContrast.joinToString()}. You can fix it after importing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "It's added to your themes and used right away. Nothing else changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onImport(true) }) { Text("Import & edit") }
                TextButton(onClick = { onImport(false) }) { Text("Import") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small color dots, used by the new-theme dialog to show a generated palette. */
@Composable
internal fun PaletteDots(colors: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { argb ->
            Surface(
                modifier = Modifier.size(22.dp),
                shape = RoundedCornerShape(50),
                color = Color(argb),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {}
        }
    }
}
