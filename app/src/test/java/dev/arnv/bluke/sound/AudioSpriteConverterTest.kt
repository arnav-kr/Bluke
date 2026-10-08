package dev.arnv.bluke.sound

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioSpriteConverterTest {
    @Test
    fun wavSliceHasValidHeaderAndRequestedPcmOnly() {
        val pcm = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

        val wav = pcm16WavBytes(
            pcm = pcm,
            offset = 2,
            length = 4,
            sampleRate = 44_100,
            channelCount = 1,
        )

        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("data", wav.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(40, littleEndianInt(wav, 4))
        assertEquals(4, littleEndianInt(wav, 40))
        assertArrayEquals(byteArrayOf(2, 3, 4, 5), wav.copyOfRange(44, 48))
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
}
