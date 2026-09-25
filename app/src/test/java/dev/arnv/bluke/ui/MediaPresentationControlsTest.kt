package dev.arnv.bluke.ui

import android.view.KeyEvent
import android.view.OrientationEventListener
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

    @Test
    fun physicalOrientationUsesHysteresisBetweenUprightAndLandscapeLayouts() {
        assertEquals(
            MultimediaPosture.PORTRAIT_HELD,
            multimediaPostureForDegrees(0, MultimediaPosture.LANDSCAPE),
        )
        assertEquals(
            MultimediaPosture.LANDSCAPE,
            multimediaPostureForDegrees(90, MultimediaPosture.PORTRAIT_HELD),
        )
        assertEquals(
            MultimediaPosture.PORTRAIT_HELD,
            multimediaPostureForDegrees(45, MultimediaPosture.PORTRAIT_HELD),
        )
        assertEquals(
            MultimediaPosture.LANDSCAPE,
            multimediaPostureForDegrees(45, MultimediaPosture.LANDSCAPE),
        )
        assertEquals(
            MultimediaPosture.LANDSCAPE,
            multimediaPostureForDegrees(
                OrientationEventListener.ORIENTATION_UNKNOWN,
                MultimediaPosture.LANDSCAPE,
            ),
        )
    }
}
