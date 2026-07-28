package com.ajimsjames.wearmaps.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2

class CompassHeadingListener(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var currentAzimuth: Float = 0f
        private set

    private var listener: ((Float) -> Unit)? = null

    fun start(onHeadingChanged: (Float) -> Unit) {
        this.listener = onHeadingChanged
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            azimuth = (azimuth + 360) % 360

            // Smooth interpolation
            currentAzimuth = currentAzimuth + 0.15f * (azimuth - currentAzimuth)
            listener?.invoke(currentAzimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
