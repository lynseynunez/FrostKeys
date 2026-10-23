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
        if (prefs.haptics) buzz(long = false)
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

    /** A slightly stronger pulse for long presses (popups, caps lock). */
    fun longPress() {
        if (prefs.haptics) buzz(long = true)
    }

    /** Plays the haptic style chosen in settings: the phone's own tick/click/heavy effects, or a custom strength. */
    private fun buzz(long: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            val predefined = when (prefs.hapticStyle) {
                STYLE_TICK -> VibrationEffect.EFFECT_TICK
                STYLE_CLICK -> VibrationEffect.EFFECT_CLICK
                STYLE_HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> -1
            }
            if (predefined >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(if (long) VibrationEffect.EFFECT_HEAVY_CLICK else predefined))
                return
            }
            val strength = prefs.hapticStrength.coerceIn(0, 100)
            val ms = (6 + strength * 0.25f).toLong() + if (long) 10L else 0L
            val amplitude = if (v.hasAmplitudeControl()) (25 + strength * 2.3f).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } catch (_: RuntimeException) {
            // Some OEM builds throw when vibration is restricted; feedback is best-effort.
        }
    }

    companion object {
        const val STYLE_CUSTOM = 0
        const val STYLE_TICK = 1
        const val STYLE_CLICK = 2
        const val STYLE_HEAVY = 3
    }
}
