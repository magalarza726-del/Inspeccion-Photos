package ec.espol.inspeccionphotos

import android.hardware.SensorManager
import ec.espol.inspeccionphotos.survey.InertialSnapshot
import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Convierte el estado espacial capturado junto a una foto en una observación humana y portable.
 * El texto se guarda con la foto, por lo que también viaja en respaldos/ZIP y no depende de
 * que los sensores sigan disponibles al momento de generar el reporte.
 */
object SpatialObservationFormatter {
    fun build(
        location: LocationSnapshot?,
        orientation: OrientationSnapshot?,
        inertial: InertialSnapshot? = null
    ): String {
        val lines = mutableListOf<String>()
        val now = System.currentTimeMillis()
        lines += "[ESPACIAL] ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))}"

        if (location != null) {
            lines += "GPS: %.6f, %.6f".format(Locale.US, location.latitude, location.longitude)
            val gnss = mutableListOf<String>()
            location.horizontalAccuracyMeters?.let { gnss += "H ±%.1f m".format(Locale.US, it) }
            location.verticalAccuracyMeters?.let { gnss += "V ±%.1f m".format(Locale.US, it) }
            location.altitudeMeters?.let { gnss += "Alt %.1f m".format(Locale.US, it) }
            location.speedMetersPerSecond?.let { gnss += "Vel %.2f m/s".format(Locale.US, it) }
            location.provider?.takeIf { it.isNotBlank() }?.let { gnss += "Fuente $it" }
            if (gnss.isNotEmpty()) lines += "GNSS: ${gnss.joinToString(" · ")}"
        } else {
            lines += "GPS: no disponible"
        }

        if (orientation != null) {
            lines += "Orientación: azimut %.1f° · pitch %.1f° · roll %.1f°".format(
                Locale.US,
                orientation.azimuthDegrees,
                orientation.pitchDegrees,
                orientation.rollDegrees
            )
            lines += "Precisión orientación: ${orientationAccuracyLabel(orientation.accuracy)}"
        } else {
            lines += "Orientación: no disponible"
        }

        inertial?.let { sample ->
            vectorLine("Giroscopio", sample.gyroX, sample.gyroY, sample.gyroZ, "rad/s")?.let(lines::add)
            vectorLine("Aceleración", sample.accelX, sample.accelY, sample.accelZ, "m/s²")?.let(lines::add)
            vectorLine("Magnetómetro", sample.magneticX, sample.magneticY, sample.magneticZ, "µT")?.let(lines::add)
            val extras = mutableListOf<String>()
            sample.pressureHpa?.let { extras += "Presión %.1f hPa".format(Locale.US, it) }
            if (sample.stepCount > 0) extras += "Pasos ${sample.stepCount}"
            if (extras.isNotEmpty()) lines += "Sensores: ${extras.joinToString(" · ")}"
        }

        return lines.joinToString("\n")
    }

    fun merge(existing: String, spatial: String): String {
        if (spatial.isBlank()) return existing
        if (existing.isBlank()) return spatial
        return existing.trimEnd() + "\n\n" + spatial
    }

    private fun vectorLine(label: String, x: Float?, y: Float?, z: Float?, unit: String): String? {
        if (x == null && y == null && z == null) return null
        fun f(value: Float?): String = value?.let { "%.3f".format(Locale.US, it) } ?: "—"
        return "$label: X ${f(x)} · Y ${f(y)} · Z ${f(z)} $unit"
    }

    private fun orientationAccuracyLabel(value: Int): String = when (value) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "alta"
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "media"
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "baja"
        SensorManager.SENSOR_STATUS_UNRELIABLE -> "no fiable"
        else -> "desconocida"
    }
}
