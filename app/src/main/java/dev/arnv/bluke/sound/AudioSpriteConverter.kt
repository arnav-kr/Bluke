package dev.arnv.bluke.sound

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class AudioSpriteSlice(
    val key: String,
    val startMillis: Long,
    val durationMillis: Long,
)

internal fun interface AudioSpriteConverter {
    fun convert(source: File, outputDirectory: File, slices: List<AudioSpriteSlice>): Map<String, File>
}

internal class AndroidAudioSpriteConverter : AudioSpriteConverter {
    private data class DecodedPcm(
        val bytes: ByteArray,
        val sampleRate: Int,
        val channelCount: Int,
    )

    override fun convert(
        source: File,
        outputDirectory: File,
        slices: List<AudioSpriteSlice>,
    ): Map<String, File> {
        require(slices.isNotEmpty()) { "The audio-sprite pack has no key definitions." }
        val decoded = decodeToPcm16(source)
        outputDirectory.mkdirs()
        val bytesPerFrame = decoded.channelCount * 2
        val totalFrames = decoded.bytes.size / bytesPerFrame
        val convertedByRange = mutableMapOf<Pair<Long, Long>, File>()

        return slices.associate { slice ->
            require(slice.startMillis >= 0 && slice.durationMillis > 0) {
                "Invalid audio-sprite range for key ${slice.key}."
            }
            val range = slice.startMillis to slice.durationMillis
            val file = convertedByRange.getOrPut(range) {
                val startFrame = (slice.startMillis * decoded.sampleRate / 1_000L)
                    .coerceIn(0, totalFrames.toLong())
                    .toInt()
                val requestedFrames = (slice.durationMillis * decoded.sampleRate / 1_000L)
                    .coerceAtLeast(1)
                    .toInt()
                val frameCount = requestedFrames.coerceAtMost(totalFrames - startFrame)
                require(frameCount > 0) { "Audio-sprite range for key ${slice.key} is outside the source audio." }
                val output = File(outputDirectory, "slice_${startFrame}_$frameCount.wav")
                output.writeBytes(pcm16WavBytes(
                    pcm = decoded.bytes,
                    offset = startFrame * bytesPerFrame,
                    length = frameCount * bytesPerFrame,
                    sampleRate = decoded.sampleRate,
                    channelCount = decoded.channelCount,
                ))
                output
            }
            slice.key to file
        }
    }

    private fun decodeToPcm16(source: File): DecodedPcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("The sprite file does not contain an audio track.")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("The sprite audio format has no MIME type.")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val output = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_MICROS)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: error("Audio decoder returned no input buffer.")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_MICROS)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                                ?: error("Audio decoder returned no output buffer.")
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            output.write(chunk)
                            require(output.size() <= MAX_DECODED_BYTES) {
                                "Decoded sound pack exceeds the 64 MiB safety limit."
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            val pcm16 = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_16BIT -> output.toByteArray()
                AudioFormat.ENCODING_PCM_FLOAT -> floatPcmTo16Bit(output.toByteArray())
                else -> error("Unsupported decoded PCM encoding: $pcmEncoding")
            }
            require(pcm16.isNotEmpty()) { "The sprite audio decoder produced no samples." }
            return DecodedPcm(pcm16, sampleRate, channelCount)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun floatPcmTo16Bit(bytes: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val output = ByteBuffer.allocate((bytes.size / 4) * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (input.remaining() >= 4) {
            val sample = input.float.coerceIn(-1f, 1f)
            output.putShort((sample * Short.MAX_VALUE).toInt().toShort())
        }
        return output.array()
    }

    private companion object {
        const val CODEC_TIMEOUT_MICROS = 10_000L
        const val MAX_DECODED_BYTES = 64 * 1024 * 1024
    }
}

internal fun pcm16WavBytes(
    pcm: ByteArray,
    offset: Int,
    length: Int,
    sampleRate: Int,
    channelCount: Int,
): ByteArray {
    require(offset >= 0 && length >= 0 && offset + length <= pcm.size)
    require(sampleRate > 0 && channelCount > 0)
    val byteRate = sampleRate * channelCount * 2
    val output = ByteArrayOutputStream(44 + length)
    fun writeAscii(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
    fun writeInt(value: Int) = output.write(
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    )
    fun writeShort(value: Int) = output.write(
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
    )

    writeAscii("RIFF")
    writeInt(36 + length)
    writeAscii("WAVE")
    writeAscii("fmt ")
    writeInt(16)
    writeShort(1)
    writeShort(channelCount)
    writeInt(sampleRate)
    writeInt(byteRate)
    writeShort(channelCount * 2)
    writeShort(16)
    writeAscii("data")
    writeInt(length)
    output.write(pcm, offset, length)
    return output.toByteArray()
}
