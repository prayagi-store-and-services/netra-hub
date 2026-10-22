package com.example.driving

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Runs while driving mode is on: feeds GPS speed and the accelerometer into [TripRecorder] and writes its entries to
 * the driving log. The accelerometer is read only while driving (about 50 times a second) and nothing leaves the phone.
 */
object DrivingTrip : SensorEventListener {
    private var appContext: Context? = null
    private var sm: SensorManager? = null
    private val recorder = TripRecorder { kind, at, kmh, text ->
        appContext?.let { DrivingLog.add(it, DrivingLog.Entry(at, kind, kmh, text)) }
    }
    @Volatile private var running = false

    @Synchronized fun start(c: Context, startKmh: Float?, now: Long) {
        appContext = c.applicationContext
        recorder.start(startKmh, now)
        if (running) return
        val m = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val acc = m?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val ok = acc != null && m.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
        if (ok) { sm = m; running = true }
        DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, startKmh,
            if (ok) "Trip log on: speed changes, hard braking and bumps (accelerometer) are recorded while driving mode is on"
            else "Accelerometer unavailable on this phone: bumps cannot be recorded (speed changes and braking still are)"))
    }

    @Synchronized fun stop() {
        if (running) { sm?.unregisterListener(this); running = false }
    }

    fun onSpeed(kmh: Float?, lat: Float, lon: Float, now: Long) { if (running || appContext != null) recorder.onSpeed(kmh, lat, lon, now) }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.values.size >= 3) recorder.onAccel(e.values[0], e.values[1], e.values[2], System.currentTimeMillis())
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
