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
    fun physicalOrientationRequiresAlignmentBeforeSelectingALayout() {
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
            multimediaPostureForDegrees(21, MultimediaPosture.LANDSCAPE),
        )
        assertEquals(
            MultimediaPosture.PORTRAIT_HELD,
            multimediaPostureForDegrees(69, MultimediaPosture.PORTRAIT_HELD),
        )
        assertEquals(
            MultimediaPosture.LANDSCAPE,
            multimediaPostureForDegrees(
                OrientationEventListener.ORIENTATION_UNKNOWN,
                MultimediaPosture.LANDSCAPE,
            ),
        )
    }

    @Test
    fun physicalOrientationMustRemainStableBeforeLayoutSwitches() {
        val stabilizer = MultimediaPostureStabilizer(MultimediaPosture.LANDSCAPE)

        assertEquals(MultimediaPosture.LANDSCAPE, stabilizer.update(0, 1_000L))
        assertEquals(
            MultimediaPosture.LANDSCAPE,
            stabilizer.update(0, 1_000L + POSTURE_STABILITY_MILLIS - 1L),
        )
        assertEquals(
            MultimediaPosture.PORTRAIT_HELD,
            stabilizer.update(0, 1_000L + POSTURE_STABILITY_MILLIS),
        )
    }

    @Test
    fun leavingTheAlignmentWindowCancelsAPendingLayoutSwitch() {
        val stabilizer = MultimediaPostureStabilizer(MultimediaPosture.LANDSCAPE)

        assertEquals(MultimediaPosture.LANDSCAPE, stabilizer.update(0, 1_000L))
        assertEquals(MultimediaPosture.LANDSCAPE, stabilizer.update(45, 1_400L))
        assertEquals(MultimediaPosture.LANDSCAPE, stabilizer.update(0, 1_500L))
        assertEquals(MultimediaPosture.LANDSCAPE, stabilizer.update(0, 1_999L))
        assertEquals(MultimediaPosture.PORTRAIT_HELD, stabilizer.update(0, 2_000L))
    }
}
