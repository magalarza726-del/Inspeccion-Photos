package ec.espol.inspeccionphotos.scan

import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import ec.espol.inspeccionphotos.survey.InteriorPoseSample
import kotlin.math.*

data class BurstPose(
    val timestampEpochMs: Long,
    val location: LocationSnapshot?,
    val orientation: OrientationSnapshot?,
    val interiorPose: InteriorPoseSample? = null
)

object BurstTriggerPolicy {
    fun shouldCapture(
        previous: BurstPose?,
        current: BurstPose,
        minDistanceMeters: Double = 1.2,
        minHeadingChangeDegrees: Double = 18.0,
        minIntervalMillis: Long = 850,
        maxIntervalMillis: Long = 4_000
    ): Boolean {
        if (previous == null) return true
        val elapsed = current.timestampEpochMs - previous.timestampEpochMs
        if (elapsed < minIntervalMillis) return false
        if (elapsed >= maxIntervalMillis) return true
        val movedDistance = if (previous.interiorPose != null && current.interiorPose != null &&
            previous.interiorPose.anchorId == current.interiorPose.anchorId) {
            hypot(
                current.interiorPose.eastMeters - previous.interiorPose.eastMeters,
                current.interiorPose.northMeters - previous.interiorPose.northMeters
            )
        } else distanceMeters(previous.location, current.location)
        val moved = movedDistance >= minDistanceMeters
        val turned = headingDelta(previous.orientation?.azimuthDegrees, current.orientation?.azimuthDegrees) >= minHeadingChangeDegrees
        return moved || turned
    }

    fun headingDelta(a: Float?, b: Float?): Double {
        if (a == null || b == null) return 0.0
        val raw = abs(a.toDouble() - b.toDouble()) % 360.0
        return min(raw, 360.0 - raw)
    }

    fun distanceMeters(a: LocationSnapshot?, b: LocationSnapshot?): Double {
        if (a == null || b == null) return 0.0
        val r = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
