package com.flexkeys.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/**
 * How far the phone has been tilted from the way it is usually held, as two numbers from -1 to 1.
 * The reading adapts slowly to the resting angle, so only real movement shows. Uses the accelerometer,
 * which needs no permission, and only runs while started.
 */
class TiltSensor(context: Context, private val onChange: () -> Unit) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var x = 0f
        private set
    var y = 0f
        private set
    var running = false
        private set

    private var fastX = 0f
    private var fastY = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var primed = false
    private var lastNotify = 0L

    fun start() {
        if (running || sensor == null) return
        running = manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI) == true
    }

    fun stop() {
        if (!running) return
        manager?.unregisterListener(this)
        running = false
        primed = false
        x = 0f
        y = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val ax = event.values[0]
        val ay = event.values[1]
        if (!primed) {
            fastX = ax; fastY = ay; baseX = ax; baseY = ay
            primed = true
        }
        fastX += (ax - fastX) * 0.2f
        fastY += (ay - fastY) * 0.2f
        baseX += (fastX - baseX) * 0.01f
        baseY += (fastY - baseY) * 0.01f
        // About 4 m/s^2 of change (a tilt of roughly 25 degrees) is the full range.
        x = ((fastX - baseX) / 4f).coerceIn(-1f, 1f)
        y = ((fastY - baseY) / 4f).coerceIn(-1f, 1f)
        val now = SystemClock.uptimeMillis()
        if (now - lastNotify >= 33L) {
            lastNotify = now
            onChange()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
