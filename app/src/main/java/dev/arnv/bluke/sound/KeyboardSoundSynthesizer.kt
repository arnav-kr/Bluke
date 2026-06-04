package dev.arnv.bluke.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.exp
import kotlin.random.Random


enum class SwitchType(val displayName: String, val description: String) {
    CHERRY_MX_BROWN("Cherry MX Browns", "Tactile, classic scratchy clack"),
    HOLY_PANDA("Holy Pandas", "Highly tactile, loud pop clack"),
    ALPACAS("Alpacas", "Silky smooth linear, crisp high-end clack"),
    TURQUOISE_TEALIOS("Turquoise Tealios", "Linear, deep lubricated plastic clack"),
    GATERON_BLACK_INK("Gateron Black Inks", "Heavy linear, quiet deep bass thock"),
    CHERRY_MX_BLACK("Cherry MX Blacks", "Classic linear, medium dry thock"),
    CHERRY_MX_BLUE("Cherry MX Blues", "Sharp, snappy high-pitch click jacket"),
    KAILH_BOX_NAVY("Kailh Box Navies", "Very loud metal click-bar, heavy thud"),
    BUCKLING_SPRING("Buckling Spring", "Vintage clicky spring rattle and metallic ping"),
    SKCM_BLUE_ALPS("SKCM Blue Alps", "Vintage tactile click, beautiful hollow chamber"),
    TOPRE("Topre 45g", "Electro-capacitive plastic dome, round woody thock"),
    NOVELKEYS_CREAM("NovelKeys Creams", "Signature linear, self-lubricating dry thock")
}

class KeyboardSoundSynthesizer(private val context: Context) {
    private var soundPool: SoundPool? = null
    
    // Playback maps for compiled sounds (mapping variation index to SoundPool ID)
    private val pressSoundIds = mutableMapOf<Int, Int>()
    private val releaseSoundIds = mutableMapOf<Int, Int>()
    
    // Playback maps for loaded asset sound IDs (e.g. "SPACE", "ENTER", "BACKSPACE", "GENERIC_R0" to "GENERIC_R4")
    private val loadedPressIds = mutableMapOf<String, Int>()
    private val loadedReleaseIds = mutableMapOf<String, Int>()
    
    private var isMuted = false
    private var currentSwitchType = SwitchType.CHERRY_MX_BROWN
    private val sampleRate = 44100
    private val variationsCount = 3 // 3 different press variants to avoid repetitiveness
    
    init {
        createSoundPool()
        recompileSounds(SwitchType.CHERRY_MX_BROWN)
    }

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
            Log.d("KeyboardSoundSynth", "Sample loaded: id=$sampleId, status=$status")
        }

        soundPool = pool
    }

    fun setMute(mute: Boolean) {
        this.isMuted = mute
    }

    fun getMuted(): Boolean = isMuted

    fun changeSwitchType(switchType: SwitchType) {
        if (currentSwitchType == switchType) return
        currentSwitchType = switchType
        recompileSounds(switchType)
    }

    fun getCurrentSwitch(): SwitchType = currentSwitchType

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

    private fun loadAssetsForSwitch(switchType: SwitchType) {
        val folder = switchType.toFolderName()
        
        // Load press files
        val pressKeys = listOf("SPACE", "ENTER", "BACKSPACE")
        pressKeys.forEach { key ->
            try {
                val path = "audio/$folder/press/$key.mp3"
                context.assets.openFd(path).use { fd ->
                    soundPool?.let { pool ->
                        val id = pool.load(fd, 1)
                        loadedPressIds[key] = id
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
                        loadedPressIds["GENERIC_R$i"] = id
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
                        loadedReleaseIds[key] = id
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
                    loadedReleaseIds["GENERIC"] = id
                }
            }
        } catch (e: Exception) {
            Log.w("KeyboardSoundSynth", "Missing generic release in folder $folder: ${e.message}")
        }
    }

    /**
     * Synthesizes and loads keyboard sounds into SoundPool in a background thread
     */
    private fun recompileSounds(switchType: SwitchType) {
        Thread {
            try {
                // Clear existing loaded sounds
                pressSoundIds.clear()
                releaseSoundIds.clear()
                
                loadedPressIds.clear()
                loadedReleaseIds.clear()
                
                // Load assets first
                loadAssetsForSwitch(switchType)
                
                // Create temp files in cache for synthesized sounds or as stand-bys
                val cacheDir = context.cacheDir
                
                // Compile multiple press variations
                for (varIndex in 0 until variationsCount) {
                    val pressFile = File(cacheDir, "pb_press_${switchType.name}_$varIndex.wav")
                    val pressSamples = synthesizeKeystroke(switchType, isPress = true, variation = varIndex)
                    writeWavFile(pressFile, sampleRate, pressSamples)
                    
                    soundPool?.let { pool ->
                        val id = pool.load(pressFile.absolutePath, 1)
                        pressSoundIds[varIndex] = id
                    }
                }
                
                // Compile single release sound
                val releaseFile = File(cacheDir, "pb_release_${switchType.name}.wav")
                val releaseSamples = synthesizeKeystroke(switchType, isPress = false)
                writeWavFile(releaseFile, sampleRate, releaseSamples)
                
                soundPool?.let { pool ->
                    val id = pool.load(releaseFile.absolutePath, 1)
                    releaseSoundIds[0] = id
                }
                
                Log.d("KeyboardSoundSynth", "Successfully recompiled switch sounds for: ${switchType.displayName}")
            } catch (e: Exception) {
                Log.e("KeyboardSoundSynth", "Failed to compile switch wav files", e)
            }
        }.start()
    }

    fun playPress(keyCode: Int = 0) {
        if (isMuted) return
        
        val key = getSoundKey(keyCode)
        val soundId = if (key == "GENERIC") {
            val varIdx = Random.nextInt(5)
            loadedPressIds["GENERIC_R$varIdx"] ?: loadedPressIds["GENERIC_R0"]
        } else {
            loadedPressIds[key] ?: loadedPressIds["GENERIC_R0"]
        }
        
        val volume = if (key == "SPACE") 1.0f else 0.8f // Slightly boost space bar volume as it's bigger
        val pitch = 1.0f
        
        soundId?.let { id ->
            if (id > 0) {
                val streamId = soundPool?.play(id, volume, volume, 1, 0, pitch) ?: 0
                if (streamId == 0) {
                    val fallbackId = loadedPressIds["GENERIC_R0"] ?: pressSoundIds[0]
                    fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
                }
            } else {
                val fallbackId = pressSoundIds[0]
                fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
            }
        } ?: run {
            // Fallback to compiled synthesizer sound
            val randomVarIdx = Random.nextInt(variationsCount)
            val fallbackId = pressSoundIds[randomVarIdx] ?: pressSoundIds[0]
            fallbackId?.let { id -> soundPool?.play(id, volume, volume, 1, 0, pitch) }
        }
    }

    fun playRelease(keyCode: Int = 0) {
        if (isMuted) return
        
        val key = getSoundKey(keyCode)
        val soundId = loadedReleaseIds[key] ?: loadedReleaseIds["GENERIC"]
        
        val volume = if (key == "SPACE") 0.9f else 0.8f
        val pitch = 1.0f
        
        soundId?.let { id ->
            if (id > 0) {
                val streamId = soundPool?.play(id, volume, volume, 1, 0, pitch) ?: 0
                if (streamId == 0) {
                    val fallbackId = loadedReleaseIds["GENERIC"] ?: releaseSoundIds[0]
                    fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
                }
            } else {
                val fallbackId = releaseSoundIds[0]
                fallbackId?.let { fid -> soundPool?.play(fid, volume, volume, 1, 0, pitch) }
            }
        } ?: run {
            // Fallback to compiled synthesizer sound
            val fallbackId = releaseSoundIds[0]
            fallbackId?.let { id -> soundPool?.play(id, volume, volume, 1, 0, pitch) }
        }
    }

    fun release() {
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
            var amplitude = 0.0
            
            if (isPress) {
                // Key down Bottom-out synthesis
                when (switch) {
                    SwitchType.CHERRY_MX_BROWN -> {
                        // Soft transient scratch + medium pitch clack
                        val clickNoise = noise(i) * exp(-1500.0 * t) * 0.25
                        val tactileBump = sin(2.0 * Math.PI * 340.0 * pitchFactor * t) * exp(-120.0 * t) * 0.25
                        val housingMode = sin(2.0 * Math.PI * 190.0 * t) * exp(-70.0 * t) * 0.2
                        amplitude = clickNoise + tactileBump + housingMode
                    }
                    SwitchType.HOLY_PANDA -> {
                        // Sharp tactile click plus deep, hollow bubbly pop
                        val sharpTick = noise(i) * exp(-3000.0 * t) * 0.35
                        val snapF1 = sin(2.0 * Math.PI * 520.0 * pitchFactor * t) * exp(-110.0 * t) * 0.4
                        val popBody = sin(2.0 * Math.PI * 260.0 * t) * exp(-55.0 * t) * 0.35
                        amplitude = sharpTick + snapF1 + popBody
                    }
                    SwitchType.ALPACAS -> {
                        // Crisp clean lubricated nylon clack
                        val slideTape = noise(i) * exp(-2000.0 * t) * 0.15
                        val crispF1 = sin(2.0 * Math.PI * 450.0 * pitchFactor * t) * exp(-140.0 * t) * 0.45
                        val plasticF2 = sin(2.0 * Math.PI * 890.0 * pitchFactor * t) * exp(-180.0 * t) * 0.2
                        amplitude = slideTape + crispF1 + plasticF2
                    }
                    SwitchType.TURQUOISE_TEALIOS -> {
                        // Slightly damp, deep lubricated plastic drop thock
                        val lubeTape = noise(i) * exp(-1800.0 * t) * 0.08
                        val warmThock = sin(2.0 * Math.PI * 220.0 * pitchFactor * t) * exp(-80.0 * t) * 0.55
                        val casing = sin(2.0 * Math.PI * 130.0 * t) * exp(-45.0 * t) * 0.2
                        amplitude = lubeTape + warmThock + casing
                    }
                    SwitchType.GATERON_BLACK_INK -> {
                        // Low pitch, incredibly solid heavy thock
                        val slideLube = noise(i) * exp(-900.0 * t) * 0.05
                        val bassBody = sin(2.0 * Math.PI * 165.0 * pitchFactor * t) * exp(-60.0 * t) * 0.65
                        val secondary = sin(2.0 * Math.PI * 330.0 * t) * exp(-90.0 * t) * 0.25
                        amplitude = slideLube + bassBody + secondary
                    }
                    SwitchType.CHERRY_MX_BLACK -> {
                        // Classic solid dry linear medium clack
                        val slide = noise(i) * exp(-1200.0 * t) * 0.15
                        val dryThock = sin(2.0 * Math.PI * 260.0 * pitchFactor * t) * exp(-100.0 * t) * 0.45
                        amplitude = slide + dryThock
                    }
                    SwitchType.CHERRY_MX_BLUE -> {
                        // Double click jacket snap
                        var click = 0.0
                        // First small leaf contact snap
                        if (t > 0.002f && t < 0.005f) {
                            click += sin(2.0 * Math.PI * 2800.0 * pitchFactor * (t - 0.002)) * 0.4
                        }
                        // Main click jacket drop snap
                        if (t > 0.006f) {
                            click += sin(2.0 * Math.PI * 2200.0 * pitchFactor * (t - 0.006)) * exp(-1200.0 * (t - 0.006)) * 0.45
                        }
                        val bottomThump = sin(2.0 * Math.PI * 230.0 * t) * exp(-90.0 * t) * 0.25
                        amplitude = click + bottomThump
                    }
                    SwitchType.KAILH_BOX_NAVY -> {
                        // Heavy, massive metal click-bar pop
                        var clickBar = 0.0
                        if (t > 0.003f) {
                            clickBar += sin(2.0 * Math.PI * 1400.0 * pitchFactor * (t - 0.003)) * exp(-800.0 * (t - 0.003)) * 0.6
                        }
                        val heavyBody = sin(2.0 * Math.PI * 190.0 * t) * exp(-50.0 * t) * 0.45
                        amplitude = clickBar + heavyBody
                    }
                    SwitchType.BUCKLING_SPRING -> {
                        // Complex buckling spring click plus metallic coil ring
                        var clickValue = 0.0
                        if (t > 0.001f) {
                            clickValue += sin(2.0 * Math.PI * 1750.0 * pitchFactor * (t - 0.001)) * exp(-1000.0 * (t - 0.001)) * 0.45
                        }
                        // Copper spring ring modulation (950Hz vibrating at 10Hz)
                        val springRing = sin(2.0 * Math.PI * 920.0 * t) * 
                                         sin(2.0 * Math.PI * 12.0 * t) * 
                                         exp(-32.0 * t) * 0.25
                        val housingFrame = sin(2.0 * Math.PI * 290.0 * t) * exp(-70.0 * t) * 0.2
                        amplitude = clickValue + springRing + housingFrame
                    }
                    SwitchType.SKCM_BLUE_ALPS -> {
                        // Dynamic vintage tactility, hollow chamber
                        val metalPlate = sin(2.0 * Math.PI * 1250.0 * pitchFactor * t) * exp(-600.0 * t) * 0.35
                        val hollowChamber = sin(2.0 * Math.PI * 410.0 * t) * exp(-45.0 * t) * 0.45
                        amplitude = metalPlate + hollowChamber
                    }
                    SwitchType.TOPRE -> {
                        // Pristine "rubber cup pop" - silent wood-like sound
                        val rubberDomePop = sin(2.0 * Math.PI * 115.0 * pitchFactor * t) * exp(-45.0 * t) * 0.75
                        val cleanSlider = sin(2.0 * Math.PI * 250.0 * pitchFactor * t) * exp(-85.0 * t) * 0.25
                        amplitude = rubberDomePop + cleanSlider
                    }
                    SwitchType.NOVELKEYS_CREAM -> {
                        // Linear, dry scratch clack
                        val scratch = noise(i) * exp(-1400.0 * t) * 0.22
                        val dryPlastic = sin(2.0 * Math.PI * 310.0 * pitchFactor * t) * exp(-110.0 * t) * 0.5
                        amplitude = scratch + dryPlastic
                    }
                }
            } else {
                // Key Up Release synthesis (shorter, higher pitch, lighter damping)
                amplitude = when (switch) {
                    SwitchType.CHERRY_MX_BROWN -> {
                        sin(2.0 * Math.PI * 390.0 * t) * exp(-200.0 * t) * 0.2
                    }
                    SwitchType.HOLY_PANDA -> {
                        sin(2.0 * Math.PI * 460.0 * t) * exp(-170.0 * t) * 0.22
                    }
                    SwitchType.ALPACAS -> {
                        sin(2.0 * Math.PI * 550.0 * t) * exp(-240.0 * t) * 0.25
                    }
                    SwitchType.TURQUOISE_TEALIOS -> {
                        sin(2.0 * Math.PI * 280.0 * t) * exp(-140.0 * t) * 0.22
                    }
                    SwitchType.GATERON_BLACK_INK -> {
                        sin(2.0 * Math.PI * 210.0 * t) * exp(-120.0 * t) * 0.25
                    }
                    SwitchType.CHERRY_MX_BLACK -> {
                        sin(2.0 * Math.PI * 330.0 * t) * exp(-180.0 * t) * 0.22
                    }
                    SwitchType.CHERRY_MX_BLUE -> {
                        // Light return tactile reset snap
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
                    SwitchType.SKCM_BLUE_ALPS -> {
                        sin(2.0 * Math.PI * 520.0 * t) * exp(-160.0 * t) * 0.22
                    }
                    SwitchType.TOPRE -> {
                        // Extremely light Topre whispy return damp
                        val airHiss = noise(i) * exp(-1500.0 * t) * 0.04
                        val sliderThump = sin(2.0 * Math.PI * 190.0 * t) * exp(-130.0 * t) * 0.18
                        airHiss + sliderThump
                    }
                    SwitchType.NOVELKEYS_CREAM -> {
                        sin(2.0 * Math.PI * 410.0 * t) * exp(-170.0 * t) * 0.2
                    }
                }
            }
            
            // Normalize & Clip
            if (amplitude > 1.0) amplitude = 1.0
            if (amplitude < -1.0) amplitude = -1.0
            
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
    private fun writeWavFile(file: File, sampleRate: Int, shortSamples: ShortArray) {
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
