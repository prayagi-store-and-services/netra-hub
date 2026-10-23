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
    // Liveness numbers, shown on the driving card: proof the motion sensor is really delivering.
    @Volatile private var accelCount = 0L
    @Volatile private var accelFirstMs = 0L
    @Volatile private var accelLastMs = 0L
    @Volatile private var lastFixMs = 0L
    @Volatile private var bootOffsetMs = 0L
    @Volatile private var wasListening = false

    data class Liveness(val running: Boolean, val samples: Long, val perSecond: Float, val lastSampleAt: Long, val lastFixAt: Long)
    fun liveness(): Liveness {
        val span = accelLastMs - accelFirstMs
        return Liveness(running, accelCount, if (span > 1000L) accelCount * 1000f / span else 0f, accelLastMs, lastFixMs)
    }

    fun isRecording(): Boolean = running

    @Synchronized fun start(c: Context, startKmh: Float?, now: Long) {
        if (running) return // a second start must not wipe the trip in progress
        appContext = c.applicationContext
        recorder.start(startKmh, now)
        accelCount = 0L; accelFirstMs = 0L; accelLastMs = 0L; lastFixMs = 0L
        bootOffsetMs = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val m = c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val acc = m?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var ok = false
        if (acc != null) {
            val t = HandlerThread("netra-trip").also { it.start() }
            ok = m.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME, Handler(t.looper))
            if (ok) { thread = t; sm = m; wasListening = true } else t.quitSafely()
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
        val c = appContext; val l = liveness()
        if (c != null) {
            val now = System.currentTimeMillis()
            if (accelFirstMs != 0L && l.perSecond in 0.1f..20f) DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, null, "Motion sensor delivered only about %.0f samples a second on this trip (the phone may slow it down with the screen off), so short bumps may have been missed".format(l.perSecond)))
            else if (accelFirstMs == 0L && wasListening) DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, null, "Motion sensor delivered no samples on this trip, so no bumps could be recorded"))
        }
        wasListening = false
        appContext = null
    }

    fun onSpeed(kmh: Float?, lat: Float, lon: Float, fixTimeMs: Long) { if (running) { lastFixMs = fixTimeMs; recorder.onSpeed(kmh, lat, lon, fixTimeMs) } }

    override fun onSensorChanged(e: SensorEvent) {
        if (!running || e.values.size < 3) return
        // The sensor's own clock (nanoseconds since boot), so a busy or frozen app thread cannot distort the timing.
        val t = bootOffsetMs + e.timestamp / 1_000_000L
        if (accelFirstMs == 0L) accelFirstMs = t
        accelLastMs = t; accelCount++
        recorder.onAccel(e.values[0], e.values[1], e.values[2], t)
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
