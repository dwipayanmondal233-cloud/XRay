package com.example.xray

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.SystemClock

class ShakeDetector(
    level: Int,
    private val onShake: () -> Unit
) : SensorEventListener {

    // level 0 = LOW (hard shake), 1 = MEDIUM, 2 = HIGH (light shake)
    private val threshold: Float = when (level) {
        0 -> 3.0f
        2 -> 2.0f
        else -> 2.5f
    }

    private var lastShake = 0L
    private var lastTrigger = 0L
    private var count = 0

    override fun onSensorChanged(event: SensorEvent) {
        val gx = event.values[0] / 9.80665f
        val gy = event.values[1] / 9.80665f
        val gz = event.values[2] / 9.80665f
        val g = Math.sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()

        if (g > threshold) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastShake < 150) return
            if (now - lastShake > 900) count = 0
            lastShake = now
            count++
            if (count >= 3 && now - lastTrigger > 2500) {
                count = 0
                lastTrigger = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}