package dev.arnv.bluke.ui

import dev.arnv.bluke.bluetooth.ConsumerControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchKeyboardLayoutsTest {
    @Test
    fun everyLayerIsTenKeysWideWithNoUnknownKeys() {
        TouchLayer.entries.forEach { layer ->
            val rows = TouchKeyboardLayouts.getLayout(layer)
            assertEquals("$layer rows", 5, rows.size)
            rows.forEachIndexed { index, row ->
                val width = row.maxOf { it.x + it.widthRatio }
                assertTrue("$layer row $index is $width wide", width in 9.5f..10f)
            }
            val unknown = rows.flatten().filter { it.keyCode == 0 }
            assertTrue("$layer has unmapped keys: ${unknown.map { it.legend }}", unknown.isEmpty())
        }
    }

    @Test
    fun modifiersAndLayerKeysStayInPlaceAcrossLayers() {
        val pinned = setOf(
            KeyboardLayouts.MOD_LCTRL,
            KeyboardLayouts.MOD_LALT,
            KeyboardLayouts.MOD_LWIN,
            KeyboardLayouts.MOD_LSHIFT,
            TouchKeyboardLayouts.KEY_LAYER_SYMBOLS,
            TouchKeyboardLayouts.KEY_LAYER_FN,
        )
        fun positions(layer: TouchLayer) = TouchKeyboardLayouts.getLayout(layer).flatten()
            .filter { it.keyCode in pinned }
            .associate { it.keyCode to (it.x to it.y) }

        val base = positions(TouchLayer.BASE)
        assertEquals(pinned, base.keys)
        assertEquals(base, positions(TouchLayer.SYMBOLS))
        assertEquals(base, positions(TouchLayer.FN))
    }

    @Test
    fun layerKeysToggleTheirOwnLayer() {
        assertEquals(TouchLayer.SYMBOLS, TouchLayer.BASE.afterLayerKey(TouchKeyboardLayouts.KEY_LAYER_SYMBOLS))
        assertEquals(TouchLayer.BASE, TouchLayer.SYMBOLS.afterLayerKey(TouchKeyboardLayouts.KEY_LAYER_SYMBOLS))
        assertEquals(TouchLayer.FN, TouchLayer.SYMBOLS.afterLayerKey(TouchKeyboardLayouts.KEY_LAYER_FN))
        assertEquals(TouchLayer.BASE, TouchLayer.FN.afterLayerKey(TouchKeyboardLayouts.KEY_LAYER_FN))
    }

    @Test
    fun fnLayerHasEveryMediaKeyAndNoneLeakToTheHost() {
        val fnKeys = TouchKeyboardLayouts.getLayout(TouchLayer.FN).flatten()
        val controls = fnKeys.mapNotNull { TouchKeyboardLayouts.consumerControlFor(it.keyCode) }.toSet()
        assertEquals(ConsumerControl.entries.toSet(), controls)
        assertTrue(fnKeys.any { it.keyCode == KeyboardLayouts.KEY_F12 })
        // Real HID usages never map to a media key.
        assertNull(TouchKeyboardLayouts.consumerControlFor(KeyboardLayouts.KEY_A))
        assertNull(TouchKeyboardLayouts.consumerControlFor(KeyboardLayouts.MOD_RWIN))
    }

    @Test
    fun characterLayoutsApplyToTheTouchLayout() {
        val keys = TouchKeyboardLayouts.getLayout(TouchLayer.BASE, KeyboardCharacterLayout.FRENCH_AZERTY).flatten()
        val physicalQ = keys.single { it.physicalKeyCode == KeyboardLayouts.KEY_Q }
        assertEquals("A", physicalQ.legend)
    }
}
