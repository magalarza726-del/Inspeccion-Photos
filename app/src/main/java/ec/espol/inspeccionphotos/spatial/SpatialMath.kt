package ec.espol.inspeccionphotos.spatial

import ec.espol.inspeccionphotos.survey.FacadeSide
import ec.espol.inspeccionphotos.survey.GeoPoint
import kotlin.math.*

object SpatialMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun angularDeltaDegrees(a: Double, b: Double): Double {
        val d = abs(normalizeDegrees(a) - normalizeDegrees(b))
        return min(d, 360.0 - d)
    }

    fun normalizeDegrees(value: Double): Double {
        var v = value % 360.0
        if (v < 0) v += 360.0
        return v
    }

    fun centroid(points: List<GeoPoint>): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        // For campus-scale footprints an arithmetic centroid in lat/lon is sufficiently stable.
        return GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
    }

    fun nearestDistanceToPolygonMeters(point: GeoPoint, polygon: List<GeoPoint>): Double {
        if (polygon.isEmpty()) return Double.POSITIVE_INFINITY
        if (polygon.size == 1) return distanceMeters(point, polygon.first())
        val ref = point
        val p = toLocalMeters(point, ref)
        var best = Double.POSITIVE_INFINITY
        val closed = if (polygon.first() == polygon.last()) polygon else polygon + polygon.first()
        for (i in 0 until closed.lastIndex) {
            val a = toLocalMeters(closed[i], ref)
            val b = toLocalMeters(closed[i + 1], ref)
            best = min(best, distancePointToSegment(p.first, p.second, a.first, a.second, b.first, b.second))
        }
        return best
    }

    fun facadeSeenFrom(camera: GeoPoint, buildingCenter: GeoPoint): FacadeSide {
        // Bearing from building toward the camera tells which facade the camera is facing.
        val bearing = bearingDegrees(buildingCenter, camera)
        return when {
            bearing >= 315 || bearing < 45 -> FacadeSide.NORTH
            bearing < 135 -> FacadeSide.EAST
            bearing < 225 -> FacadeSide.SOUTH
            else -> FacadeSide.WEST
        }
    }

    fun projectLocalMeters(point: GeoPoint, reference: GeoPoint): Pair<Double, Double> = toLocalMeters(point, reference)

    private fun toLocalMeters(point: GeoPoint, reference: GeoPoint): Pair<Double, Double> {
        val latRad = Math.toRadians(reference.latitude)
        val x = Math.toRadians(point.longitude - reference.longitude) * EARTH_RADIUS_M * cos(latRad)
        val y = Math.toRadians(point.latitude - reference.latitude) * EARTH_RADIUS_M
        return x to y
    }

    private fun distancePointToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val qx = ax + t * dx
        val qy = ay + t * dy
        return hypot(px - qx, py - qy)
    }
}
