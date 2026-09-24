package dev.arnv.bluke.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomSoundPackRepositoryTest {
    @Test
    fun importsAndSelectsV2MultiFilePack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = CustomSoundPackRepository(context)
        val archive = zip(
            "pack/config.json" to """
                {
                  "id": "test-multi-pack",
                  "name": "Test Multi",
                  "version": 2,
                  "key_define_type": "multi",
                  "sound": "press_{0-1}.ogg",
                  "soundup": "release.ogg",
                  "defines": { "30": "a.ogg" }
                }
            """.trimIndent().toByteArray(),
            "pack/press_0.ogg" to byteArrayOf(1),
            "pack/press_1.ogg" to byteArrayOf(2),
            "pack/release.ogg" to byteArrayOf(3),
            "pack/a.ogg" to byteArrayOf(4),
        )

        val result = repository.importZip(ByteArrayInputStream(archive))

        assertTrue(result is SoundPackImportResult.Success)
        val pack = (result as SoundPackImportResult.Success).pack
        assertEquals("test-multi-pack", repository.selectedPackId())
        assertEquals(2, pack.defaultPressFiles.size)
        assertEquals(1, pack.pressFiles.getValue(30).size)
    }

    @Test
    fun rejectsTraversalAndSpritePacks() {
        try {
            validatedArchivePath("../outside.ogg")
            fail("Traversal path should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = CustomSoundPackRepository(context)
        val archive = zip(
            "config.json" to """
                {
                  "name": "Sprite Pack",
                  "key_define_type": "single",
                  "sound": "sound.ogg",
                  "defines": { "30": [0, 100] }
                }
            """.trimIndent().toByteArray(),
            "sound.ogg" to byteArrayOf(1),
        )

        val result = repository.importZip(ByteArrayInputStream(archive))

        assertTrue(result is SoundPackImportResult.Failure)
        assertTrue((result as SoundPackImportResult.Failure).message.contains("Audio-sprite"))
    }

    @Test
    fun mapsHidUsagesToMechvibesStandardKeycodes() {
        assertEquals(30, mechvibesKeyCodeForHid(0x04))
        assertEquals(16, mechvibesKeyCodeForHid(0x14))
        assertEquals(57, mechvibesKeyCodeForHid(0x2c))
        assertEquals(57416, mechvibesKeyCodeForHid(0x52))
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
