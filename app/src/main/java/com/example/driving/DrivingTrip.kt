package com.example.driving

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/**
 * Runs while driving mode is on: feeds GPS speed and the accelerometer into [TripRecorder] and writes its entries to
 * the driving log. The accelerometer is read only while driving (about 50 times a second, on its own background
 * thread) and is released when driving ends, when monitoring stops and when the service is destroyed.
 * Nothing leaves the phone.
 */
object DrivingTrip : SensorEventListener {
    private var appContext: Context? = null
    private var sm: SensorManager? = null
    private var thread: HandlerThread? = null
    private val recorder = TripRecorder { kind, at, kmh, text ->
        appContext?.let { DrivingLog.add(it, DrivingLog.Entry(at, kind, kmh, text)) }
    }
    @Volatile private var running = false

    @Synchronized fun start(c: Context, startKmh: Float?, now: Long) {
        if (running) return // a second start must not wipe the trip in progress
        appContext = c.applicationContext
        recorder.start(startKmh, now)
        val m = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val acc = m?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var ok = false
        if (acc != null) {
            val t = HandlerThread("netra-trip").also { it.start() }
            ok = m.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, Handler(t.looper))
            if (ok) { thread = t; sm = m } else t.quitSafely()
        }
        running = true // speed and braking are recorded even when there is no accelerometer
        DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, startKmh,
            if (ok) "Trip log on: speed changes, hard braking and bumps (accelerometer) are recorded while driving mode is on"
            else "Accelerometer unavailable on this phone: bumps cannot be recorded (speed changes and braking still are)"))
    }

    @Synchronized fun stop() {
        if (!running) return
        running = false
        sm?.unregisterListener(this); sm = null
        thread?.quitSafely(); thread = null
        appContext = null
    }

    fun onSpeed(kmh: Float?, lat: Float, lon: Float, fixTimeMs: Long) { if (running) recorder.onSpeed(kmh, lat, lon, fixTimeMs) }

    override fun onSensorChanged(e: SensorEvent) {
        if (running && e.values.size >= 3) recorder.onAccel(e.values[0], e.values[1], e.values[2], System.currentTimeMillis())
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
