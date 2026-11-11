package dev.arnv.bluke.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

const val KEY_VIBRATION_PREFERENCE = "key_vibration_enabled"
const val KEY_VIBRATION_STRENGTH_PREFERENCE = "key_vibration_strength"

/** General app haptics; also the default for key vibration before it had its own setting. */
const val HAPTICS_PREFERENCE = "haptics_enabled"

enum class KeyVibrationStrength(
    val preferenceValue: String,
    val displayName: String,
    val durationMs: Long,
    val amplitude: Int,
) {
    LIGHT("light", "Light", 10, 70),
    MEDIUM("medium", "Medium", 18, 150),
    STRONG("strong", "Strong", 28, 255);

    companion object {
        fun fromPreference(value: String?): KeyVibrationStrength =
            entries.firstOrNull { it.preferenceValue == value } ?: MEDIUM
    }
}

fun SharedPreferences.isKeyVibrationEnabled(): Boolean =
    getBoolean(KEY_VIBRATION_PREFERENCE, getBoolean(HAPTICS_PREFERENCE, true))

fun SharedPreferences.keyVibrationStrength(): KeyVibrationStrength =
    KeyVibrationStrength.fromPreference(getString(KEY_VIBRATION_STRENGTH_PREFERENCE, null))

/**
 * Vibrates the phone on key presses. Uses the vibrator directly rather than view haptic
 * feedback, so it still works when the system "touch feedback" setting is turned off.
 */
class KeyPressVibrator(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }?.takeIf { it.hasVibrator() }

    val isAvailable: Boolean get() = vibrator != null

    fun vibrate(strength: KeyVibrationStrength) {
        val vibrator = vibrator ?: return
        val amplitude = if (vibrator.hasAmplitudeControl()) {
            strength.amplitude
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(strength.durationMs, amplitude))
        } catch (e: RuntimeException) {
            Log.w("KeyPressVibrator", "Key vibration failed", e)
        }
    }
}
