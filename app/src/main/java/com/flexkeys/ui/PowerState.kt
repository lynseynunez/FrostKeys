package com.flexkeys.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock

/** Tells whether the phone is short on battery, so animations can pause. Cached for a while. */
object PowerState {

    private const val LOW_PERCENT = 20
    private const val CACHE_MS = 30_000L

    private var checkedAt = 0L
    private var low = false

    fun isLow(context: Context): Boolean {
        val now = SystemClock.uptimeMillis()
        if (checkedAt == 0L || now - checkedAt > CACHE_MS) {
            checkedAt = now
            low = compute(context.applicationContext)
        }
        return low
    }

    private fun compute(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isPowerSaveMode == true) return true
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return !charging && level >= 0 && level * 100 / scale <= LOW_PERCENT
    }
}
