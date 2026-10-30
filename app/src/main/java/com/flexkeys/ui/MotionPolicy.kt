package com.flexkeys.ui

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.flexkeys.theme.Prefs

/**
 * Whether motion-heavy effects should rest: the user's own "Reduce motion" switch, or Android's
 * "Remove animations" accessibility setting. Sensor-driven shifts, looping glass animations and
 * typing effects all check this. Cached briefly because it is asked often.
 */
object MotionPolicy {

    private var checkedAt = 0L
    private var systemOff = false

    fun reduced(context: Context, prefs: Prefs): Boolean {
        if (prefs.reduceMotion) return true
        val now = SystemClock.uptimeMillis()
        if (checkedAt == 0L || now - checkedAt > 5_000L) {
            checkedAt = now
            systemOff = try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            } catch (e: Exception) {
                false
            }
        }
        return prefs.followSystemMotion && systemOff
    }
}
