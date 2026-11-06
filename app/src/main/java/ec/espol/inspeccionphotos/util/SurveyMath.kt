package ec.espol.inspeccionphotos.util

import ec.espol.inspeccionphotos.survey.LocationSnapshot
import kotlin.math.*

object SurveyMath {
    fun distanceMeters(a: LocationSnapshot, b: LocationSnapshot): Double {
        val r = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    fun routeDistanceMeters(points: List<ec.espol.inspeccionphotos.survey.RoutePoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.location, b.location) }

    fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format("%.2f km", meters / 1000.0) else String.format("%.0f m", meters)
}
