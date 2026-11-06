package ec.espol.inspeccionphotos.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import kotlin.math.roundToInt

class OrientationTracker(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var callback: ((OrientationSnapshot) -> Unit)? = null
    private var accuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    fun start(onOrientation: (OrientationSnapshot) -> Unit) {
        stop()
        callback = onOrientation
        if (rotation != null) {
            manager.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stop() {
        manager.unregisterListener(this)
        callback = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val r = FloatArray(9)
        val ok = when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(r, event.values)
                true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravity = event.values.clone()
                matrixFromFallback(r)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagnetic = event.values.clone()
                matrixFromFallback(r)
            }
            else -> false
        }
        if (!ok) return
        val values = FloatArray(3)
        SensorManager.getOrientation(r, values)
        val azimuth = normalize(Math.toDegrees(values[0].toDouble()).toFloat())
        val pitch = Math.toDegrees(values[1].toDouble()).toFloat()
        val roll = Math.toDegrees(values[2].toDouble()).toFloat()
        callback?.invoke(
            OrientationSnapshot(
                timestampEpochMs = System.currentTimeMillis(),
                azimuthDegrees = azimuth,
                pitchDegrees = pitch,
                rollDegrees = roll,
                accuracy = accuracy
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, value: Int) { accuracy = value }

    private fun matrixFromFallback(out: FloatArray): Boolean {
        val g = gravity ?: return false
        val m = geomagnetic ?: return false
        return SensorManager.getRotationMatrix(out, null, g, m)
    }

    private fun normalize(value: Float): Float {
        val rounded = (value * 10).roundToInt() / 10f
        return ((rounded % 360f) + 360f) % 360f
    }
}
