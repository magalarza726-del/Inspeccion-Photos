package ec.espol.inspeccionphotos.camera

import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import ec.espol.inspeccionphotos.survey.PendingCapture
import ec.espol.inspeccionphotos.survey.RoutePoint
import kotlin.math.abs

object PoseResolver {
    data class ResolvedPose(
        val location: LocationSnapshot?,
        val orientation: OrientationSnapshot?,
        val confidence: String
    )

    fun resolve(targetEpochMs: Long?, route: List<RoutePoint>, pending: PendingCapture): ResolvedPose {
        val target = targetEpochMs ?: pending.startedAtEpochMs
        val nearest = route.minByOrNull { abs(it.timestampEpochMs - target) }
        val delta = nearest?.let { abs(it.timestampEpochMs - target) } ?: Long.MAX_VALUE
        return when {
            nearest != null && delta <= 2_500L -> ResolvedPose(
                nearest.location, nearest.orientation ?: pending.fallbackOrientation, confidenceLabel(nearest, delta)
            )
            nearest != null && delta <= 8_000L -> ResolvedPose(
                nearest.location, nearest.orientation ?: pending.fallbackOrientation, confidenceLabel(nearest, delta)
            )
            pending.fallbackLocation != null || pending.fallbackOrientation != null -> {
                val loc = pending.fallbackLocation
                val label = if (loc?.positionReference == ec.espol.inspeccionphotos.survey.PositionReference.INTERIOR_RELATIVE) {
                    relativeLabel(loc.horizontalAccuracyMeters, stale = true)
                } else "BAJA"
                ResolvedPose(loc, pending.fallbackOrientation, label)
            }
            else -> ResolvedPose(null, null, "MUY BAJA")
        }
    }

    private fun confidenceLabel(point: RoutePoint, deltaMs: Long): String {
        val loc = point.location
        return if (point.environmentMode == ec.espol.inspeccionphotos.survey.EnvironmentMode.INTERIOR ||
            loc.positionReference == ec.espol.inspeccionphotos.survey.PositionReference.INTERIOR_RELATIVE) {
            relativeLabel(loc.horizontalAccuracyMeters, stale = deltaMs > 2_500L)
        } else if (deltaMs <= 2_500L) "ALTA" else "MEDIA"
    }

    private fun relativeLabel(uncertaintyMeters: Float?, stale: Boolean): String {
        val base = when {
            uncertaintyMeters == null -> "MUY_BAJA"
            uncertaintyMeters <= 4f -> "ALTA"
            uncertaintyMeters <= 8f -> "MEDIA"
            uncertaintyMeters <= 15f -> "BAJA"
            else -> "MUY_BAJA"
        }
        val degraded = if (!stale) base else when (base) {
            "ALTA" -> "MEDIA"
            "MEDIA" -> "BAJA"
            else -> "MUY_BAJA"
        }
        return "INTERIOR_RELATIVA_$degraded"
    }
}
