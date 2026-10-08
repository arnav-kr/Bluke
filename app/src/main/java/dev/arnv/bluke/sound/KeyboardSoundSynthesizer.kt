package dev.arnv.bluke.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.math.exp
import kotlin.random.Random


enum class SwitchType(val displayName: String) {
    CHERRY_MX_BROWN("Cherry MX Browns"),
    HOLY_PANDA("Holy Pandas"),
    ALPACAS("Alpacas"),
    TURQUOISE_TEALIOS("Turquoise Tealios"),
    GATERON_BLACK_INK("Gateron Black Inks"),
    CHERRY_MX_BLACK("Cherry MX Blacks"),
    CHERRY_MX_BLUE("Cherry MX Blues"),
    KAILH_BOX_NAVY("Kailh Box Navies"),
    BUCKLING_SPRING("Buckling Spring"),
    SKCM_BLUE_ALPS("SKCM Blue Alps"),
    TOPRE("Topre 45g"),
    NOVELKEYS_CREAM("NovelKeys Creams")
}

const val SELECTED_BUILT_IN_SOUND_PREFERENCE = "selected_builtin_key_sound"
private const val BUILT_IN_PROFILE_PREFIX = "built_in:"
private const val CUSTOM_PROFILE_PREFIX = "custom:"

fun builtInSoundProfileId(switchType: SwitchType): String =
    "$BUILT_IN_PROFILE_PREFIX${switchType.name}"

fun customSoundProfileId(packId: String): String = "$CUSTOM_PROFILE_PREFIX$packId"

fun selectedBuiltInSound(preferences: android.content.SharedPreferences): SwitchType =
    preferences.getString(SELECTED_BUILT_IN_SOUND_PREFERENCE, null)
        ?.let { stored -> SwitchType.entries.firstOrNull { it.name == stored } }
        ?: SwitchType.CHERRY_MX_BROWN

class KeyboardSoundSynthesizer(private val context: Context) {
    private data class CustomSoundBank(
        val packId: String,
        val packName: String,
        val pressIds: Map<Int, List<Int>>,
        val releaseIds: Map<Int, List<Int>>,
        val defaultPressIds: List<Int>,
        val defaultReleaseIds: List<Int>,
    )

    private data class BuiltInSoundBank(
        val generatedPressIds: Map<Int, Int> = emptyMap(),
        val generatedReleaseIds: Map<Int, Int> = emptyMap(),
        val loadedPressIds: Map<String, Int> = emptyMap(),
        val loadedReleaseIds: Map<String, Int> = emptyMap(),
    ) {
        fun allSampleIds(): List<Int> =
            (generatedPressIds.values + generatedReleaseIds.values +
                loadedPressIds.values + loadedReleaseIds.values).distinct()
    }

    private var soundPool: SoundPool? = null
    private val soundLoader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BlukeSoundLoader")
    }
    private val customSoundPacks = CustomSoundPackRepository(context)
    private val preferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    @Volatile private var customSoundBank: CustomSoundBank? = null
    @Volatile private var builtInSoundBank = BuiltInSoundBank()
    private val completedLoadStatuses = ConcurrentHashMap<Int, Int>()
    private val loadWaiters = ConcurrentHashMap<Int, CompletableFuture<Boolean>>()
    @Volatile private var trackLoadStatuses = false
    
    private var isMuted = false
    private var currentSwitchType = selectedBuiltInSound(preferences)
    private val sampleRate = 44100
    private val variationsCount = 3 // 3 different press variants to avoid repetitiveness
    
    init {
        createSoundPool()
        val selectedPack = customSoundPacks.selectedPack()
        if (selectedPack == null) recompileSounds(SwitchType.CHERRY_MX_BROWN)
        else loadCustomSoundPack(selectedPack)
    }

    @Suppress("DEPRECATION")
    private fun createSoundPool() {
        soundPool?.release()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
            .build()
            
        val pool = SoundPool.Builder()
            .setMaxStreams(24) // Increase max streams to support fast typing roll-overs
            .setAudioAttributes(attrs)
            .build()

        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (trackLoadStatuses) {
                completedLoadStatuses[sampleId] = status
                loadWaiters.remove(sampleId)?.complete(status == 0)
            }
            Log.d("KeyboardSoundSynth", "Sample loaded: id=$sampleId, status=$status")
        }

        soundPool = pool
    }

    fun setMute(mute: Boolean) {
        this.isMuted = mute
    }

    fun changeSwitchType(switchType: SwitchType) {
        if (currentSwitchType == switchType && customSoundBank == null) return
        customSoundPacks.select(null)
        currentSwitchType = switchType
        preferences.edit().putString(SELECTED_BUILT_IN_SOUND_PREFERENCE, switchType.name).apply()
        recompileSounds(switchType)
    }

    fun getCurrentSwitch(): SwitchType = currentSwitchType

    fun getCurrentSoundProfileName(): String =
        customSoundBank?.packName ?: currentSwitchType.displayName

    fun getSelectedSoundProfileName(): String =
        customSoundPacks.selectedPack()?.name ?: currentSwitchType.displayName

    fun getSelectedSoundProfileId(): String =
        customSoundPacks.selectedPack()?.let { customSoundProfileId(it.id) }
            ?: builtInSoundProfileId(currentSwitchType)

    fun cycleSoundProfile(enabledProfileIds: Set<String>) {
        val profileIds = (SwitchType.entries.map(::builtInSoundProfileId) +
            customSoundPacks.listPacks().map { customSoundProfileId(it.id) })
            .filter(enabledProfileIds::contains)
        if (profileIds.isEmpty()) return
        val currentIndex = profileIds.indexOf(getSelectedSoundProfileId())
        selectSoundProfile(profileIds[(currentIndex + 1) % profileIds.size])
    }

    private fun selectSoundProfile(profileId: String) {
        when {
            profileId.startsWith(BUILT_IN_PROFILE_PREFIX) -> {
                val switchName = profileId.removePrefix(BUILT_IN_PROFILE_PREFIX)
                SwitchType.entries.firstOrNull { it.name == switchName }?.let(::changeSwitchType)
            }
            profileId.startsWith(CUSTOM_PROFILE_PREFIX) -> {
                val packId = profileId.removePrefix(CUSTOM_PROFILE_PREFIX)
                val pack = customSoundPacks.listPacks().firstOrNull { it.id == packId } ?: return
                customSoundPacks.select(pack.id)
                loadCustomSoundPack(pack)
            }
        }
    }

    fun reloadSelectedSoundPack() {
        val preferredBuiltIn = selectedBuiltInSound(preferences)
        val selectedPack = customSoundPacks.selectedPack()
        if (selectedPack == null) {
            if (customSoundBank != null || currentSwitchType != preferredBuiltIn) {
                currentSwitchType = preferredBuiltIn
                recompileSounds(currentSwitchType)
            }
        } else if (customSoundBank?.packId != selectedPack.id) {
            loadCustomSoundPack(selectedPack)
        }
    }

    private fun SwitchType.toFolderName(): String {
        return when (this) {
            SwitchType.CHERRY_MX_BROWN -> "mxbrown"
            SwitchType.HOLY_PANDA -> "holypanda"
            SwitchType.ALPACAS -> "alpaca"
            SwitchType.TURQUOISE_TEALIOS -> "turquoise"
            SwitchType.GATERON_BLACK_INK -> "blackink"
            SwitchType.CHERRY_MX_BLACK -> "mxblack"
            SwitchType.CHERRY_MX_BLUE -> "mxblue"
            SwitchType.KAILH_BOX_NAVY -> "boxnavy"
            SwitchType.BUCKLING_SPRING -> "buckling"
            SwitchType.SKCM_BLUE_ALPS -> "bluealps"
            SwitchType.TOPRE -> "topre"
            SwitchType.NOVELKEYS_CREAM -> "cream"
        }
    }

    private fun getSoundKey(keyCode: Int): String {
        return when (keyCode) {
            0x2C -> "SPACE"     // KEY_SPACE
            0x28 -> "ENTER"     // KEY_ENTER
            0x2A -> "BACKSPACE" // KEY_BACKSPACE
            else -> "GENERIC"
        }
    }

    private fun loadAssetsForSwitch(switchType: SwitchType): Pair<Map<String, Int>, Map<String, Int>> {
        val folder = switchType.toFolderName()
        val pressIds = mutableMapOf<String, Int>()
        val releaseIds = mutableMapOf<String, Int>()
        
        // Load press files
        val pressKeys = listOf("SPACE", "ENTER", "BACKSPACE")
        pressKeys.forEach { key ->
            try {
                val path = "audio/$folder/press/$key.mp3"
                context.assets.openFd(path).use { fd ->
                    soundPool?.let { pool ->
                        val id = pool.load(fd, 1)
                        pressIds[key] = id
                    }
                }
            } catch (e: Exception) {
                Log.w("KeyboardSoundSynth", "Missing press asset for key $key in folder $folder: ${e.message}")
            }
        }
        
        // Load press generics
        for (i in 0 until 5) {
            try {
                val path = "audio/$folder/press/GENERIC_R$i.mp3"
                context.assets.openFd(path).use { fd ->
                    soundPool?.let { pool ->
                        val id = pool.load(fd, 1)
                    pressIds["GENERIC_R$i"] = id
                    }
                }
            } catch (e: Exception) {
                Log.w("KeyboardSoundSynth", "Missing generic press variation $i in folder $folder: ${e.message}")
            }
        }
        
        // Load release files
        val releaseKeys = listOf("SPACE", "ENTER", "BACKSPACE")
        releaseKeys.forEach { key ->
            try {
                val path = "audio/$folder/release/$key.mp3"
                context.assets.openFd(path).use { fd ->
                    soundPool?.let { pool ->
                        val id = pool.load(fd, 1)
                        releaseIds[key] = id
                    }
                }
            } catch (e: Exception) {
                Log.w("KeyboardSoundSynth", "Missing release asset for key $key in folder $folder: ${e.message}")
            }
        }
        
        // Load release generic
        try {
            val path = "audio/$folder/release/GENERIC.mp3"
            context.assets.openFd(path).use { fd ->
                soundPool?.let { pool ->
                    val id = pool.load(fd, 1)
                    releaseIds["GENERIC"] = id
                }
            }
        } catch (e: Exception) {
            Log.w("KeyboardSoundSynth", "Missing generic release in folder $folder: ${e.message}")
        }
        return pressIds to releaseIds
    }

    /**
     * Synthesizes and loads keyboard sounds into SoundPool in a background thread
     */
    private fun recompileSounds(switchType: SwitchType) {
        soundLoader.execute {
            try {
                val previousBank = builtInSoundBank
                soundPool?.let { pool ->
                    previousBank.allSampleIds().forEach(pool::unload)
                    customSoundBank?.let { bank ->
                        (bank.pressIds.values.flatten() + bank.releaseIds.values.flatten() +
                            bank.defaultPressIds + bank.defaultReleaseIds)
                            .distinct()
                            .forEach(pool::unload)
                    }
                }
                customSoundBank = null
                builtInSoundBank = BuiltInSoundBank()
                
                // Load assets first
                val (loadedPressIds, loadedReleaseIds) = loadAssetsForSwitch(switchType)
                val generatedPressIds = mutableMapOf<Int, Int>()
                val generatedReleaseIds = mutableMapOf<Int, Int>()
                
                // Create temp files in cache for synthesized sounds or as stand-bys
                val cacheDir = context.cacheDir
                
                // Compile multiple press variations
                for (varIndex in 0 until variationsCount) {
                    val pressFile = File(cacheDir, "pb_press_${switchType.name}_$varIndex.wav")
                    val pressSamples = synthesizeKeystroke(switchType, isPress = true, variation = varIndex)
                    writeWavFile(pressFile, pressSamples)
                    
                    soundPool?.let { pool ->
                        val id = pool.load(pressFile.absolutePath, 1)
                        generatedPressIds[varIndex] = id
                    }
                }
                
                // Compile single release sound
                val releaseFile = File(cacheDir, "pb_release_${switchType.name}.wav")
                val releaseSamples = synthesizeKeystroke(switchType, isPress = false)
                writeWavFile(releaseFile, releaseSamples)
                
                soundPool?.let { pool ->
                    val id = pool.load(releaseFile.absolutePath, 1)
                    generatedReleaseIds[0] = id
                }

                builtInSoundBank = BuiltInSoundBank(
                    generatedPressIds = generatedPressIds,
                    generatedReleaseIds = generatedReleaseIds,
                    loadedPressIds = loadedPressIds,
                    loadedReleaseIds = loadedReleaseIds,
                )
                
                Log.d("KeyboardSoundSynth", "Successfully recompiled switch sounds for: ${switchType.displayName}")
            } catch (e: Exception) {
                Log.e("KeyboardSoundSynth", "Failed to compile switch wav files", e)
            }
        }
    }

    private fun loadCustomSoundPack(pack: CustomSoundPack) {
        soundLoader.execute {
            try {
                val pool = soundPool ?: return@execute
                builtInSoundBank.allSampleIds().forEach(pool::unload)
                customSoundBank?.let { bank ->
                    (bank.pressIds.values.flatten() + bank.releaseIds.values.flatten() +
                        bank.defaultPressIds + bank.defaultReleaseIds)
                        .distinct()
                        .forEach(pool::unload)
                }
                builtInSoundBank = BuiltInSoundBank()

                val loadedFiles = mutableMapOf<String, Int>()
                trackLoadStatuses = true
                fun load(files: List<File>): List<Int> = files.mapNotNull { file ->
                    val id = loadedFiles.getOrPut(file.absolutePath) {
                        pool.load(file.absolutePath, 1)
                    }
                    id.takeIf { it > 0 }
                }
                val rawPressIds = pack.pressFiles.mapValues { (_, files) -> load(files) }
                val rawReleaseIds = pack.releaseFiles.mapValues { (_, files) -> load(files) }
                val rawDefaultPressIds = load(pack.defaultPressFiles)
                val rawDefaultReleaseIds = load(pack.defaultReleaseFiles)
                val allIds = loadedFiles.values.toSet()
                val successfulIds = awaitSuccessfulSamples(allIds)
                trackLoadStatuses = false
                check(successfulIds.isNotEmpty()) { "Android could not decode any audio in ${pack.name}." }
                fun successful(ids: List<Int>): List<Int> = ids.filter(successfulIds::contains)
                val bank = CustomSoundBank(
                    packId = pack.id,
                    packName = pack.name,
                    pressIds = rawPressIds.mapValues { (_, ids) -> successful(ids) },
                    releaseIds = rawReleaseIds.mapValues { (_, ids) -> successful(ids) },
                    defaultPressIds = successful(rawDefaultPressIds),
                    defaultReleaseIds = successful(rawDefaultReleaseIds),
                )
                check(bank.defaultPressIds.isNotEmpty() || bank.pressIds.values.any { it.isNotEmpty() }) {
                    "The custom pack has no decodable key-down sounds."
                }
                customSoundBank = bank
                Log.d("KeyboardSoundSynth", "Loaded custom sound pack: ${pack.name}")
            } catch (error: Exception) {
                trackLoadStatuses = false
                completedLoadStatuses.clear()
                loadWaiters.clear()
                customSoundBank = null
                customSoundPacks.select(null)
                Log.e("KeyboardSoundSynth", "Failed to load custom sound pack", error)
                recompileSounds(currentSwitchType)
            }
        }
    }

    private fun awaitSuccessfulSamples(sampleIds: Set<Int>): Set<Int> {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        return sampleIds.filterTo(mutableSetOf()) { id ->
            completedLoadStatuses[id]?.let { status ->
                completedLoadStatuses.remove(id)
                return@filterTo status == 0
            }

            val candidate = CompletableFuture<Boolean>()
            val waiter = loadWaiters.putIfAbsent(id, candidate) ?: candidate
            completedLoadStatuses[id]?.let { status ->
                if (loadWaiters.remove(id, waiter)) waiter.complete(status == 0)
            }
            try {
                val remainingNanos = deadlineNanos - System.nanoTime()
                remainingNanos > 0 && waiter.get(remainingNanos, TimeUnit.NANOSECONDS)
            } catch (_: Exception) {
                false
            } finally {
                loadWaiters.remove(id, waiter)
                completedLoadStatuses.remove(id)
            }
        }
    }

    fun playPress(keyCode: Int = 0) {
        if (isMuted) return

        customSoundBank?.let { bank ->
            val ids = mechvibesKeyCodeForHid(keyCode)?.let(bank.pressIds::get)
                .orEmpty()
                .ifEmpty { bank.defaultPressIds }
            ids.randomOrNull()?.let { id -> soundPool?.play(id, 0.8f, 0.8f, 1, 0, 1.0f) }
            return
        }
        val builtInBank = builtInSoundBank
        
        val key = getSoundKey(keyCode)
        val soundId = if (key == "GENERIC") {
            val varIdx = Random.nextInt(5)
            builtInBank.loadedPressIds["GENERIC_R$varIdx"] ?: builtInBank.loadedPressIds["GENERIC_R0"]
        } else {
            builtInBank.loadedPressIds[key] ?: builtInBank.loadedPressIds["GENERIC_R0"]
        }
        
        val volume = if (key == "SPACE") 1.0f else 0.8f // Slightly boost space bar volume as it's bigger
        val pitch = 1.0f
        
        soundId?.let { id ->
            if (id > 0) {
                val streamId = soundPool?.play(id, volume, volume, 1, 0, pitch) ?: 0
                if (streamId == 0) {
                    val fallbackId = builtInBank.loadedPressIds["GENERIC_R0"] ?: builtInBank.generatedPressIds[0]
                    fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
                }
            } else {
                val fallbackId = builtInBank.generatedPressIds[0]
                fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
            }
        } ?: run {
            // Fallback to compiled synthesizer sound
            val randomVarIdx = Random.nextInt(variationsCount)
            val fallbackId = builtInBank.generatedPressIds[randomVarIdx] ?: builtInBank.generatedPressIds[0]
            fallbackId?.let { id -> soundPool?.play(id, volume, volume, 1, 0, pitch) }
        }
    }

    fun playRelease(keyCode: Int = 0) {
        if (isMuted) return

        customSoundBank?.let { bank ->
            val ids = mechvibesKeyCodeForHid(keyCode)?.let(bank.releaseIds::get)
                .orEmpty()
                .ifEmpty { bank.defaultReleaseIds }
            ids.randomOrNull()?.let { id -> soundPool?.play(id, 0.8f, 0.8f, 1, 0, 1.0f) }
            return
        }
        val builtInBank = builtInSoundBank
        
        val key = getSoundKey(keyCode)
        val soundId = builtInBank.loadedReleaseIds[key] ?: builtInBank.loadedReleaseIds["GENERIC"]
        
        val volume = if (key == "SPACE") 0.9f else 0.8f
        val pitch = 1.0f
        
        soundId?.let { id ->
            if (id > 0) {
                val streamId = soundPool?.play(id, volume, volume, 1, 0, pitch) ?: 0
                if (streamId == 0) {
                    val fallbackId = builtInBank.loadedReleaseIds["GENERIC"] ?: builtInBank.generatedReleaseIds[0]
                    fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
                }
            } else {
                val fallbackId = builtInBank.generatedReleaseIds[0]
                fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
            }
        } ?: run {
            // Fallback to compiled synthesizer sound
            val fallbackId = builtInBank.generatedReleaseIds[0]
            fallbackId?.let { id -> soundPool?.play(id, volume, volume, 1, 0, pitch) }
        }
    }

    fun release() {
        soundLoader.shutdownNow()
        soundPool?.release()
        soundPool = null
    }

    /**
     * Highly complex synthesis equations generating true-to-life switch acoustics
     */
    private fun synthesizeKeystroke(switch: SwitchType, isPress: Boolean, variation: Int = 0): ShortArray {
        val durationSec = if (isPress) 0.10f else 0.05f
        val totalSamples = (sampleRate * durationSec).toInt()
        val data = ShortArray(totalSamples)
        
        // Randomization based on variation to give custom organic keystrokes
        val pitchFactor = 1.0f + ((variation - 1) * 0.03f) // -3%, 0%, +3%
        
        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate
            
            val amplitudeRaw = if (isPress) {
                when (switch) {
                    SwitchType.CHERRY_MX_BROWN -> {
                        val clickNoise = noise(i) * exp(-1500.0 * t) * 0.25
                        val tactileBump = sin(2.0 * Math.PI * 340.0 * pitchFactor * t) * exp(-120.0 * t) * 0.25
                        val housingMode = sin(2.0 * Math.PI * 190.0 * t) * exp(-70.0 * t) * 0.2
                        clickNoise + tactileBump + housingMode
                    }
                    SwitchType.HOLY_PANDA -> {
                        val sharpTick = noise(i) * exp(-3000.0 * t) * 0.35
                        val snapF1 = sin(2.0 * Math.PI * 520.0 * pitchFactor * t) * exp(-110.0 * t) * 0.4
                        val popBody = sin(2.0 * Math.PI * 260.0 * t) * exp(-55.0 * t) * 0.35
                        sharpTick + snapF1 + popBody
                    }
                    SwitchType.ALPACAS -> {
                        val slideTape = noise(i) * exp(-2000.0 * t) * 0.15
                        val crispF1 = sin(2.0 * Math.PI * 450.0 * pitchFactor * t) * exp(-140.0 * t) * 0.45
                        val plasticF2 = sin(2.0 * Math.PI * 890.0 * pitchFactor * t) * exp(-180.0 * t) * 0.2
                        slideTape + crispF1 + plasticF2
                    }
                    SwitchType.TURQUOISE_TEALIOS -> {
                        val lubeTape = noise(i) * exp(-1800.0 * t) * 0.08
                        val warmThock = sin(2.0 * Math.PI * 220.0 * pitchFactor * t) * exp(-80.0 * t) * 0.55
                        val casing = sin(2.0 * Math.PI * 130.0 * t) * exp(-45.0 * t) * 0.2
                        lubeTape + warmThock + casing
                    }
                    SwitchType.GATERON_BLACK_INK -> {
                        val slideLube = noise(i) * exp(-900.0 * t) * 0.05
                        val bassBody = sin(2.0 * Math.PI * 165.0 * pitchFactor * t) * exp(-60.0 * t) * 0.65
                        val secondary = sin(2.0 * Math.PI * 330.0 * t) * exp(-90.0 * t) * 0.25
                        slideLube + bassBody + secondary
                    }
                    SwitchType.CHERRY_MX_BLACK -> {
                        val slide = noise(i) * exp(-1200.0 * t) * 0.15
                        val dryThock = sin(2.0 * Math.PI * 260.0 * t) * exp(-100.0 * t) * 0.45
                        slide + dryThock
                    }
                    SwitchType.CHERRY_MX_BLUE -> {
                        var click = 0.0
                        if (t > 0.002f && t < 0.005f) {
                            click += sin(2.0 * Math.PI * 2800.0 * pitchFactor * (t - 0.002)) * 0.4
                        }
                        if (t > 0.006f) {
                            click += sin(2.0 * Math.PI * 2200.0 * pitchFactor * (t - 0.006)) * exp(-1200.0 * (t - 0.006)) * 0.45
                        }
                        val bottomThump = sin(2.0 * Math.PI * 230.0 * t) * exp(-90.0 * t) * 0.25
                        click + bottomThump
                    }
                    SwitchType.KAILH_BOX_NAVY -> {
                        var clickBar = 0.0
                        if (t > 0.003f) {
                            clickBar += sin(2.0 * Math.PI * 1400.0 * pitchFactor * (t - 0.003)) * exp(-800.0 * (t - 0.003)) * 0.6
                        }
                        val heavyBody = sin(2.0 * Math.PI * 190.0 * t) * exp(-50.0 * t) * 0.45
                        clickBar + heavyBody
                    }
                    SwitchType.BUCKLING_SPRING -> {
                        var clickValue = 0.0
                        if (t > 0.001f) {
                            clickValue += sin(2.0 * Math.PI * 1750.0 * pitchFactor * (t - 0.001)) * exp(-1000.0 * (t - 0.001)) * 0.45
                        }
                        val springRing = sin(2.0 * Math.PI * 920.0 * t) * 
                                         sin(2.0 * Math.PI * 12.0 * t) * 
                                         exp(-32.0 * t) * 0.25
                        val housingFrame = sin(2.0 * Math.PI * 290.0 * t) * exp(-70.0 * t) * 0.2
                        clickValue + springRing + housingFrame
                    }
                    SwitchType.SKCM_BLUE_ALPS -> {
                        val metalPlate = sin(2.0 * Math.PI * 1250.0 * pitchFactor * t) * exp(-600.0 * t) * 0.35
                        val hollowChamber = sin(2.0 * Math.PI * 410.0 * t) * exp(-45.0 * t) * 0.45
                        metalPlate + hollowChamber
                    }
                    SwitchType.TOPRE -> {
                        val rubberDomePop = sin(2.0 * Math.PI * 115.0 * pitchFactor * t) * exp(-45.0 * t) * 0.75
                        val cleanSlider = sin(2.0 * Math.PI * 250.0 * pitchFactor * t) * exp(-85.0 * t) * 0.25
                        rubberDomePop + cleanSlider
                    }
                    SwitchType.NOVELKEYS_CREAM -> {
                        val scratch = noise(i) * exp(-1400.0 * t) * 0.22
                        val dryPlastic = sin(2.0 * Math.PI * 310.0 * pitchFactor * t) * exp(-110.0 * t) * 0.5
                        scratch + dryPlastic
                    }
                }
            } else {
                when (switch) {
                    SwitchType.CHERRY_MX_BROWN -> sin(2.0 * Math.PI * 390.0 * t) * exp(-200.0 * t) * 0.2
                    SwitchType.HOLY_PANDA -> sin(2.0 * Math.PI * 460.0 * t) * exp(-170.0 * t) * 0.22
                    SwitchType.ALPACAS -> sin(2.0 * Math.PI * 550.0 * t) * exp(-240.0 * t) * 0.25
                    SwitchType.TURQUOISE_TEALIOS -> sin(2.0 * Math.PI * 280.0 * t) * exp(-140.0 * t) * 0.22
                    SwitchType.GATERON_BLACK_INK -> sin(2.0 * Math.PI * 210.0 * t) * exp(-120.0 * t) * 0.25
                    SwitchType.CHERRY_MX_BLACK -> sin(2.0 * Math.PI * 330.0 * t) * exp(-180.0 * t) * 0.22
                    SwitchType.CHERRY_MX_BLUE -> {
                        val snapReset = sin(2.0 * Math.PI * 1500.0 * t) * exp(-500.0 * t) * 0.18
                        val casing = sin(2.0 * Math.PI * 340.0 * t) * exp(-130.0 * t) * 0.1
                        snapReset + casing
                    }
                    SwitchType.KAILH_BOX_NAVY -> {
                        val barReset = sin(2.0 * Math.PI * 1100.0 * t) * exp(-400.0 * t) * 0.25
                        val heavyRes = sin(2.0 * Math.PI * 240.0 * t) * exp(-110.0 * t) * 0.15
                        barReset + heavyRes
                    }
                    SwitchType.BUCKLING_SPRING -> {
                        val rattle = noise(i) * exp(-600.0 * t) * 0.08
                        val ring = sin(2.0 * Math.PI * 800.0 * t) * exp(-80.0 * t) * 0.15
                        rattle + ring
                    }
                    SwitchType.SKCM_BLUE_ALPS -> sin(2.0 * Math.PI * 520.0 * t) * exp(-160.0 * t) * 0.22
                    SwitchType.TOPRE -> {
                        val airHiss = noise(i) * exp(-1500.0 * t) * 0.04
                        val sliderThump = sin(2.0 * Math.PI * 190.0 * t) * exp(-130.0 * t) * 0.18
                        airHiss + sliderThump
                    }
                    SwitchType.NOVELKEYS_CREAM -> sin(2.0 * Math.PI * 410.0 * t) * exp(-170.0 * t) * 0.2
                }
            }
            
            val amplitude = amplitudeRaw.coerceIn(-1.0, 1.0)
            data[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        
        return data
    }
    
    private fun noise(step: Int): Double {
        // High quality fast pseudo-random values
        val x = (step * 12345).toLong()
        val r = (x xor (x shr 15) xor (x shl 21)) % 1000
        return r.toDouble() / 500.0 - 1.0
    }

    /**
     * Standard RIFF WAV format exporter
     */
    private fun writeWavFile(file: File, shortSamples: ShortArray) {
        val totalAudioLen = shortSamples.size * 2
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2 // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        FileOutputStream(file).use { out ->
            out.write(header)
            val byteBuffer = ByteBuffer.allocate(shortSamples.size * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            for (sample in shortSamples) {
                byteBuffer.putShort(sample)
            }
            out.write(byteBuffer.array())
        }
    }
}
