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
        if (prefs.haptics) {
            if (prefs.keyFeel != KeyFeel.CLASSIC) feel(type, long = false) else buzz(long = false)
        }
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
        if (prefs.haptics) {
            if (prefs.keyFeel != KeyFeel.CLASSIC) feel(KeyType.CHAR, long = true) else buzz(long = true)
        }
    }

    /** Plays the chosen keyboard feel for [type]: a short pattern of pulses, scaled by the strength slider. */
    private fun feel(type: KeyType, long: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val steps = KeyFeel.steps(prefs.keyFeel, type, long)
        if (steps.isEmpty()) return
        // The strength slider (50 = as designed) makes the whole feel stronger or fainter.
        val gain = (0.5f + prefs.hapticStrength.coerceIn(0, 100) / 100f).coerceIn(0.3f, 1.5f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && composeSupported(v, steps)) {
                val composition = VibrationEffect.startComposition()
                for (s in steps) composition.addPrimitive(primitive(s.prim), (s.scale * gain).coerceIn(0.05f, 1f), s.delayMs)
                v.vibrate(composition.compose())
                return
            }
            // Fallback: a waveform of short pulses with the gaps between them.
            val timings = ArrayList<Long>()
            val amplitudes = ArrayList<Int>()
            for (s in steps) {
                val (ms, amp) = KeyFeel.fallbackPulse(s.prim)
                if (s.delayMs > 0) { timings.add(s.delayMs.toLong()); amplitudes.add(0) }
                timings.add(ms)
                amplitudes.add(if (v.hasAmplitudeControl()) (amp * s.scale * gain).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE)
            }
            // A waveform starts with a pause; a zero-length one is fine.
            timings.add(0, 0L); amplitudes.add(0, 0)
            v.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1))
        } catch (_: RuntimeException) {
            // Vibration can be restricted on some phones; feedback is best-effort.
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private fun primitive(p: KeyFeel.Prim): Int = when (p) {
        KeyFeel.Prim.CLICK -> VibrationEffect.Composition.PRIMITIVE_CLICK
        KeyFeel.Prim.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
        KeyFeel.Prim.LOW_TICK -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
        KeyFeel.Prim.THUD -> VibrationEffect.Composition.PRIMITIVE_THUD
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private fun composeSupported(v: Vibrator, steps: List<KeyFeel.Step>): Boolean =
        v.areAllPrimitivesSupported(*steps.map { primitive(it.prim) }.distinct().toIntArray())

    /** Plays the haptic style chosen in settings: the phone's own tick/click/heavy effects, or a custom strength. */
    @android.annotation.SuppressLint("InlinedApi") // the effect numbers are plain ints, only used on Android 10+
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
