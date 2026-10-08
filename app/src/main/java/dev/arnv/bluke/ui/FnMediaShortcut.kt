package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.ConsumerControl

fun consumerControlForFnKey(keyCode: Int): ConsumerControl? = when (keyCode) {
    KeyboardLayouts.KEY_F1 -> ConsumerControl.MUTE
    KeyboardLayouts.KEY_F2 -> ConsumerControl.VOLUME_DOWN
    KeyboardLayouts.KEY_F3 -> ConsumerControl.VOLUME_UP
    KeyboardLayouts.KEY_F4 -> ConsumerControl.PREVIOUS_TRACK
    KeyboardLayouts.KEY_F5 -> ConsumerControl.PLAY_PAUSE
    KeyboardLayouts.KEY_F6 -> ConsumerControl.NEXT_TRACK
    KeyboardLayouts.KEY_F7 -> ConsumerControl.BRIGHTNESS_DOWN
    KeyboardLayouts.KEY_F8 -> ConsumerControl.BRIGHTNESS_UP
    KeyboardLayouts.KEY_F12 -> ConsumerControl.SLEEP
    else -> null
}

fun fnLegendForKey(keyCode: Int): String? = when (consumerControlForFnKey(keyCode)) {
    ConsumerControl.MUTE -> "Mute"
    ConsumerControl.VOLUME_DOWN -> "Vol −"
    ConsumerControl.VOLUME_UP -> "Vol +"
    ConsumerControl.PREVIOUS_TRACK -> "Prev"
    ConsumerControl.PLAY_PAUSE -> "Play"
    ConsumerControl.NEXT_TRACK -> "Next"
    ConsumerControl.BRIGHTNESS_DOWN -> "Dim"
    ConsumerControl.BRIGHTNESS_UP -> "Bright"
    ConsumerControl.SLEEP -> "Sleep"
    null -> null
}
