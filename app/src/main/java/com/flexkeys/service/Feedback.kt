package com.flexkeys.service

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.flexkeys.layout.KeyType
import com.flexkeys.theme.Prefs

/** Key-press haptics and sounds, each controlled by its own setting. */
class Feedback(context: Context, private val prefs: Prefs) {

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun keyPress(type: KeyType) {
        if (prefs.haptics) vibrate(KEY_MS)
        if (prefs.sound) {
            val effect = when (type) {
                KeyType.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                KeyType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(effect, prefs.soundVolume.coerceIn(0, 100) / 100f)
        }
    }

    /** A slightly longer pulse for long presses (popups, caps lock). */
    fun longPress() {
        if (prefs.haptics) vibrate(LONG_MS)
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            val amplitude = if (v.hasAmplitudeControl()) 90 else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } catch (_: RuntimeException) {
            // Some OEM builds throw when vibration is restricted; feedback is best-effort.
        }
    }

    private companion object {
        const val KEY_MS = 12L
        const val LONG_MS = 25L
    }
}
