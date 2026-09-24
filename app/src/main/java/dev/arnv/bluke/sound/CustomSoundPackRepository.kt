package dev.arnv.bluke.sound

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

const val CUSTOM_SOUND_PACK_PREFERENCE = "custom_sound_pack_id"

data class CustomSoundPack(
    val id: String,
    val name: String,
    val directory: File,
    val defaultPressFiles: List<File>,
    val defaultReleaseFiles: List<File>,
    val pressFiles: Map<Int, List<File>>,
    val releaseFiles: Map<Int, List<File>>,
)

sealed interface SoundPackImportResult {
    data class Success(val pack: CustomSoundPack) : SoundPackImportResult
    data class Failure(val message: String) : SoundPackImportResult
}

internal class CustomSoundPackRepository(
    private val context: Context,
    private val audioSpriteConverter: AudioSpriteConverter = AndroidAudioSpriteConverter(),
) {
    private companion object {
        const val MAX_ARCHIVE_ENTRIES = 512
        const val MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L
        const val MAX_ENTRY_BYTES = 16L * 1024L * 1024L
        val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a", "aac", "flac")
    }

    private val packsDirectory = File(context.filesDir, "soundpacks")
    private val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun selectedPackId(): String? = preferences.getString(CUSTOM_SOUND_PACK_PREFERENCE, null)

    fun select(packId: String?) {
        preferences.edit {
            if (packId == null) remove(CUSTOM_SOUND_PACK_PREFERENCE)
            else putString(CUSTOM_SOUND_PACK_PREFERENCE, packId)
        }
    }

    fun selectedPack(): CustomSoundPack? {
        val selectedId = selectedPackId() ?: return null
        return findPack(selectedId) ?: run {
            select(null)
            null
        }
    }

    fun listPacks(): List<CustomSoundPack> = packsDirectory.listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { directory -> runCatching { parsePack(directory) }.getOrNull() }
        .sortedBy { it.name.lowercase() }

    fun findPack(id: String): CustomSoundPack? {
        val directory = File(packsDirectory, safePackId(id))
        if (!directory.isDirectory) return null
        return runCatching { parsePack(directory) }.getOrNull()
    }

    fun importZip(input: InputStream): SoundPackImportResult {
        packsDirectory.mkdirs()
        val staging = File(packsDirectory, ".import-${System.nanoTime()}")
        return try {
            staging.mkdirs()
            extractZip(input, staging)
            val configFile = staging.walkTopDown()
                .firstOrNull { it.isFile && it.name.equals("config.json", ignoreCase = true) }
                ?: return SoundPackImportResult.Failure("The archive does not contain a config.json file.")
                    .also { staging.deleteRecursively() }
            val root = configFile.parentFile ?: staging
            val parsed = preparePack(root)
            val destination = File(packsDirectory, safePackId(parsed.id))
            if (destination.exists()) destination.deleteRecursively()
            if (!root.renameTo(destination)) {
                root.copyRecursively(destination, overwrite = true)
            }
            staging.deleteRecursively()
            val installed = parsePack(destination)
            select(installed.id)
            SoundPackImportResult.Success(installed)
        } catch (error: UnsupportedSoundPackException) {
            staging.deleteRecursively()
            SoundPackImportResult.Failure(error.message ?: "Unsupported sound pack.")
        } catch (error: Exception) {
            staging.deleteRecursively()
            SoundPackImportResult.Failure(error.message ?: "Could not import this sound pack.")
        }
    }

    private fun preparePack(root: File): CustomSoundPack {
        val configFile = File(root, "config.json")
        require(configFile.isFile) { "The sound pack config.json must be at the pack root." }
        val config = JSONObject(configFile.readText())
        if (config.optString("key_define_type") == "single") {
            convertAudioSpritePack(root, configFile, config)
        }
        return parsePack(root)
    }

    private fun convertAudioSpritePack(root: File, configFile: File, config: JSONObject) {
        val sourceFiles = resolveAudioPattern(root, config.optString("sound"))
        require(sourceFiles.size == 1) { "The audio-sprite pack must reference one playable sound file." }
        val definitions = config.optJSONObject("defines")
            ?: error("The audio-sprite pack has no key definitions.")
        val slices = definitions.keys().asSequence().mapNotNull { key ->
            val range = definitions.optJSONArray(key) ?: return@mapNotNull null
            if (range.length() < 2) return@mapNotNull null
            AudioSpriteSlice(
                key = key,
                startMillis = range.optLong(0, -1),
                durationMillis = range.optLong(1, -1),
            )
        }.toList()
        require(slices.isNotEmpty()) { "The audio-sprite pack has no valid key ranges." }

        val generatedDirectory = File(root, ".bluke-samples")
        val converted = audioSpriteConverter.convert(sourceFiles.single(), generatedDirectory, slices)
        require(converted.isNotEmpty()) { "No audio-sprite samples could be converted." }
        val convertedDefinitions = JSONObject()
        converted.forEach { (key, file) ->
            convertedDefinitions.put(key, file.relativeTo(root).invariantSeparatorsPath)
        }
        val defaultFile = converted["30"] ?: converted.values.first()
        if (!File(root, "config.original.json").exists()) {
            File(root, "config.original.json").writeText(config.toString(2))
        }
        config.put("key_define_type", "multi")
        config.put("sound", defaultFile.relativeTo(root).invariantSeparatorsPath)
        config.put("soundup", "")
        config.put("defines", convertedDefinitions)
        config.put("version", 2)
        configFile.writeText(config.toString(2))
    }

    private fun extractZip(input: InputStream, destination: File) {
        var entryCount = 0
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ARCHIVE_ENTRIES) { "The archive contains too many files." }
                val relativePath = validatedArchivePath(entry.name)
                if (relativePath.isEmpty()) continue
                val output = File(destination, relativePath)
                val destinationPath = destination.canonicalPath + File.separator
                require(output.canonicalPath.startsWith(destinationPath)) { "Unsafe path in sound pack archive." }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= MAX_ENTRY_BYTES) { "A sound pack file is too large." }
                            require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "The sound pack is too large." }
                            target.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun parsePack(root: File): CustomSoundPack {
        val configFile = File(root, "config.json")
        require(configFile.isFile) { "The sound pack config.json must be at the pack root." }
        val config = JSONObject(configFile.readText())
        val type = config.optString("key_define_type")
        if (type == "single") {
            throw UnsupportedSoundPackException(
                "This audio-sprite pack has not been converted for low-latency playback."
            )
        }
        require(type == "multi") { "Only Mechvibes V2 multi-file packs are supported." }

        val name = config.optString("name").trim().ifEmpty { "Imported sound pack" }
        val configuredId = config.optString("id").trim()
        val id = safePackId(configuredId.ifEmpty { stableId(name, configFile.readText()) })
        val defaultPress = resolveAudioPattern(root, config.optString("sound"))
        val defaultRelease = resolveAudioPattern(root, config.optString("soundup"))
        val press = mutableMapOf<Int, List<File>>()
        val release = mutableMapOf<Int, List<File>>()
        val definitions = config.optJSONObject("defines") ?: JSONObject()
        definitions.keys().forEach { rawKey ->
            val isRelease = rawKey.endsWith("-up")
            val keyCode = rawKey.removeSuffix("-up").toIntOrNull() ?: return@forEach
            val files = resolveAudioPattern(root, definitions.optString(rawKey))
            if (files.isNotEmpty()) {
                if (isRelease) release[keyCode] = files else press[keyCode] = files
            }
        }
        require(defaultPress.isNotEmpty() || press.isNotEmpty()) {
            "The pack does not contain any playable key-down audio files."
        }
        return CustomSoundPack(
            id = id,
            name = name,
            directory = root,
            defaultPressFiles = defaultPress,
            defaultReleaseFiles = defaultRelease,
            pressFiles = press,
            releaseFiles = release,
        )
    }

    private fun resolveAudioPattern(root: File, pattern: String): List<File> {
        if (pattern.isBlank()) return emptyList()
        val candidates = expandNumberRange(pattern)
        return candidates.mapNotNull { relativePath ->
            val safePath = runCatching { validatedArchivePath(relativePath) }.getOrNull() ?: return@mapNotNull null
            val file = File(root, safePath)
            val rootPath = root.canonicalPath + File.separator
            val supported = file.extension.lowercase() in AUDIO_EXTENSIONS
            file.takeIf { supported && it.isFile && it.canonicalPath.startsWith(rootPath) }
        }
    }

    private fun stableId(name: String, config: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$name\n$config".toByteArray())
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
        return "pack-$digest"
    }

    private fun safePackId(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.')
        .take(80)
        .ifEmpty { "imported-pack" }
}

internal fun validatedArchivePath(path: String): String {
    val normalized = path.replace('\\', '/').trimStart('/')
    require(normalized.isNotBlank()) { "Empty path in sound pack archive." }
    require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "Unsafe path in sound pack archive." }
    require(normalized.split('/').none { it == ".." }) { "Unsafe path in sound pack archive." }
    return normalized
}

internal fun expandNumberRange(pattern: String): List<String> {
    val match = Regex("\\{(-?\\d+)-(-?\\d+)\\}").find(pattern) ?: return listOf(pattern)
    val start = match.groupValues[1].toInt()
    val end = match.groupValues[2].toInt()
    require(end >= start && end - start <= 64) { "Invalid or excessive audio-file range." }
    return (start..end).map { value -> pattern.replaceRange(match.range, value.toString()) }
}

private class UnsupportedSoundPackException(message: String) : IllegalArgumentException(message)

internal fun mechvibesKeyCodeForHid(hidCode: Int): Int? = when (hidCode) {
    in 0x04..0x1d -> intArrayOf(
        30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50,
        49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44,
    )[hidCode - 0x04]
    in 0x1e..0x26 -> hidCode - 0x1c
    0x27 -> 11
    0x28 -> 28
    0x29 -> 1
    0x2a -> 14
    0x2b -> 15
    0x2c -> 57
    0x2d -> 12
    0x2e -> 13
    0x2f -> 26
    0x30 -> 27
    0x31 -> 43
    0x33 -> 39
    0x34 -> 40
    0x35 -> 41
    0x36 -> 51
    0x37 -> 52
    0x38 -> 53
    0x39 -> 58
    in 0x3a..0x43 -> 59 + (hidCode - 0x3a)
    0x44 -> 87
    0x45 -> 88
    0x46 -> 3639
    0x47 -> 70
    0x48 -> 3653
    0x49 -> 3666
    0x4a -> 3655
    0x4b -> 3657
    0x4c -> 3667
    0x4d -> 3663
    0x4e -> 3665
    0x4f -> 57421
    0x50 -> 57419
    0x51 -> 57424
    0x52 -> 57416
    0x53 -> 69
    0xe0 -> 29
    0xe1 -> 42
    0xe2 -> 56
    0xe3 -> 3675
    0xe4 -> 3613
    0xe5 -> 54
    0xe6 -> 3640
    0xe7 -> 3676
    else -> null
}
