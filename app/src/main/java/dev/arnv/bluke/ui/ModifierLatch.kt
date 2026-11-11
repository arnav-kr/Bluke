package dev.arnv.bluke.ui

const val STICKY_MODIFIERS_PREFERENCE = "sticky_modifiers"

enum class LatchState { OFF, ONE_SHOT, LOCKED }

/**
 * Sticky modifier keys for one-handed shortcuts.
 *
 * - Tap a modifier: it applies to the next key only.
 * - Tap it twice quickly: it stays on until tapped again.
 * - Hold it while pressing another key: works like a normal keyboard.
 *
 * This class only decides what should happen; the caller sends the HID events.
 * A latched modifier stays pressed on the host until it is released here.
 */
internal class ModifierLatch(
    var enabled: Boolean = true,
    private val doubleTapWindowMillis: Long = 400L,
) {
    private val states = mutableMapOf<Int, LatchState>()
    private val lastTapAt = mutableMapOf<Int, Long>()
    private val held = mutableSetOf<Int>()
    private val chorded = mutableSetOf<Int>()

    /** Modifiers that are latched or locked (not including ones only held down). */
    val latched: Map<Int, LatchState> get() = states.filterValues { it != LatchState.OFF }

    fun state(keyCode: Int): LatchState = states[keyCode] ?: LatchState.OFF

    fun isActive(keyCode: Int): Boolean = keyCode in held || state(keyCode) != LatchState.OFF

    /** Finger went down on a modifier. Returns true when the host should get a press. */
    fun onModifierDown(keyCode: Int): Boolean {
        held += keyCode
        chorded -= keyCode
        return state(keyCode) == LatchState.OFF
    }

    /** Finger lifted from a modifier. Returns true when the host should get a release. */
    fun onModifierUp(keyCode: Int, nowMillis: Long): Boolean {
        // Ignore lifts we never saw go down (for example after clear()).
        if (!held.remove(keyCode)) return false
        val wasChord = chorded.remove(keyCode)
        val previous = state(keyCode)
        if (!enabled) {
            states.remove(keyCode)
            return true
        }
        if (wasChord) {
            // Used as a normal held modifier. A locked modifier stays locked.
            if (previous == LatchState.LOCKED) return false
            states.remove(keyCode)
            return true
        }
        return when (previous) {
            LatchState.OFF -> {
                states[keyCode] = LatchState.ONE_SHOT
                lastTapAt[keyCode] = nowMillis
                false
            }
            LatchState.ONE_SHOT -> {
                val last = lastTapAt[keyCode] ?: Long.MIN_VALUE
                if (nowMillis - last <= doubleTapWindowMillis) {
                    states[keyCode] = LatchState.LOCKED
                    false
                } else {
                    // A slow second tap cancels the latch.
                    states.remove(keyCode)
                    true
                }
            }
            LatchState.LOCKED -> {
                states.remove(keyCode)
                true
            }
        }
    }

    /** A regular key went down. Any modifier held right now counts as a normal chord. */
    fun onOtherKeyDown() {
        chorded += held
    }

    /** A regular key went up. Returns one-shot modifiers that should be released now. */
    fun onOtherKeyUp(): List<Int> {
        val release = states.filter { (code, state) -> state == LatchState.ONE_SHOT && code !in held }.keys.toList()
        release.forEach(states::remove)
        return release
    }

    /** Drops every latch. Returns the modifiers that should be released on the host. */
    fun clear(): List<Int> {
        val release = (states.filterValues { it != LatchState.OFF }.keys + held).distinct()
        states.clear()
        held.clear()
        chorded.clear()
        lastTapAt.clear()
        return release
    }
}
