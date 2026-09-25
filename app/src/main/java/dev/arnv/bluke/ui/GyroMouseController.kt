package dev.arnv.bluke.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.WindowManager

class GyroMouseController(
    context: Context,
    private val onMouseDelta: (MouseDelta) -> Unit,
) : SensorEventListener, AutoCloseable {
    companion object {
        private const val SENSOR_PERIOD_MICROSECONDS = 8_000
    }

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val motion = GyroMouseMotion()
    private var sensorThread: HandlerThread? = null
    @Volatile
    private var running = false

    @Volatile
    var sensitivity: Float = 1f

    val isAvailable: Boolean
        get() = gyroscope != null

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val sensor = gyroscope ?: return false
        val thread = HandlerThread("gyro-mouse-sensor").apply { start() }
        motion.reset()
        val registered = sensorManager.registerListener(
            this,
            sensor,
            SENSOR_PERIOD_MICROSECONDS,
            0,
            Handler(thread.looper),
        )
        if (!registered) {
            thread.quitSafely()
            return false
        }
        sensorThread = thread
        running = true
        return true
    }

    @Synchronized
    fun stop() {
        if (running) sensorManager.unregisterListener(this)
        running = false
        motion.reset()
        sensorThread?.quitSafely()
        sensorThread = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        motion.addSample(
            angularVelocityX = event.values[0],
            angularVelocityY = event.values[1],
            timestampNanos = event.timestamp,
            displayRotation = currentDisplayRotation(),
            sensitivity = sensitivity,
        )?.let(onMouseDelta)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int =
        (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay
            ?.rotation
            ?: Surface.ROTATION_0

    override fun close() = stop()
}
