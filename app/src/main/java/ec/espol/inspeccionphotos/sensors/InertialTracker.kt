package ec.espol.inspeccionphotos.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import ec.espol.inspeccionphotos.survey.InertialSnapshot

class InertialTracker(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accel: FloatArray? = null
    private var gyro: FloatArray? = null
    private var magnetic: FloatArray? = null
    private var pressure: Float? = null
    private var stepCount: Long = 0L
    private var snapshotCallback: ((InertialSnapshot) -> Unit)? = null
    private var stepCallback: ((Long, InertialSnapshot) -> Unit)? = null
    private var lastSnapshotDispatchMs: Long = 0L

    fun start(
        onSnapshot: (InertialSnapshot) -> Unit,
        onStep: (Long, InertialSnapshot) -> Unit = { _, _ -> }
    ) {
        stop()
        snapshotCallback = onSnapshot
        stepCallback = onStep
        listOf(
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_STEP_DETECTOR
        ).forEach { type ->
            manager.getDefaultSensor(type)?.let { sensor ->
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        // Some devices do not expose linear acceleration but do expose the raw accelerometer.
        if (manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) == null) {
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stop() {
        manager.unregisterListener(this)
        snapshotCallback = null
        stepCallback = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> accel = event.values.copyOf(3)
            Sensor.TYPE_GYROSCOPE -> gyro = event.values.copyOf(3)
            Sensor.TYPE_MAGNETIC_FIELD -> magnetic = event.values.copyOf(3)
            Sensor.TYPE_PRESSURE -> pressure = event.values.firstOrNull()
            Sensor.TYPE_STEP_DETECTOR -> if (event.values.firstOrNull()?.let { it > 0f } == true) stepCount += 1
        }
        val snap = snapshot()
        val isStep = event.sensor.type == Sensor.TYPE_STEP_DETECTOR && event.values.firstOrNull()?.let { it > 0f } == true
        // Sensors may fire at tens of Hz. Keep raw sensing responsive but avoid forcing Compose
        // recompositions for every sample; 10 Hz is enough for UI/sidecar snapshots.
        if (isStep || snap.timestampEpochMs - lastSnapshotDispatchMs >= 100L) {
            lastSnapshotDispatchMs = snap.timestampEpochMs
            snapshotCallback?.invoke(snap)
        }
        if (isStep) stepCallback?.invoke(stepCount, snap)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun snapshot(): InertialSnapshot = InertialSnapshot(
        timestampEpochMs = System.currentTimeMillis(),
        accelX = accel?.getOrNull(0), accelY = accel?.getOrNull(1), accelZ = accel?.getOrNull(2),
        gyroX = gyro?.getOrNull(0), gyroY = gyro?.getOrNull(1), gyroZ = gyro?.getOrNull(2),
        magneticX = magnetic?.getOrNull(0), magneticY = magnetic?.getOrNull(1), magneticZ = magnetic?.getOrNull(2),
        pressureHpa = pressure,
        stepCount = stepCount
    )
}
