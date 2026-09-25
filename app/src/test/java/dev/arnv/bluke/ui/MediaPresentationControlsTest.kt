package dev.arnv.bluke.ui

import android.view.KeyEvent
import dev.arnv.bluke.bluetooth.ConsumerControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPresentationControlsTest {
    @Test
    fun hardwareVolumeKeysMapOnlyToRemoteVolumeControls() {
        assertEquals(
            ConsumerControl.VOLUME_UP,
            consumerControlForHardwareVolumeKey(KeyEvent.KEYCODE_VOLUME_UP),
        )
        assertEquals(
            ConsumerControl.VOLUME_DOWN,
            consumerControlForHardwareVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN),
        )
        assertNull(consumerControlForHardwareVolumeKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
