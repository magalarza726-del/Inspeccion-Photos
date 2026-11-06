package ec.espol.inspeccionphotos.spatial

import ec.espol.inspeccionphotos.survey.*
import kotlin.math.max

object BuildingIdentifier {
    private const val MAX_BUILDING_DISTANCE_M = 150.0
    private const val MIN_CONFIDENCE = 0.55

    fun infer(
        survey: SurveySession,
        photos: List<PhotoRecord>,
        buildings: List<BuildingFootprint>
    ): List<BuildingInference> {
        if (buildings.isEmpty()) {
            return photos.map { unknown(it, "No hay huellas GIS de edificios cargadas") }
        }

        val provisional = photos.map { photo -> inferOne(survey, photo, buildings) }.toMutableList()
        val identifiedBySequence = provisional.filter { it.identified }.associateBy { it.sequence }

        // La secuencia se usa sólo como refuerzo de un candidato GIS ya plausible; nunca crea una identidad por sí sola.
        for (i in provisional.indices) {
            val current = provisional[i]
            if (!current.identified) continue
            val neighbors = (-2..2).filter { it != 0 }.mapNotNull { identifiedBySequence[current.sequence + it] }
            val same = neighbors.count { it.buildingId == current.buildingId }
            if (same > 0) {
                provisional[i] = current.copy(
                    confidence = (current.confidence + minOf(0.10, same * 0.035)).coerceAtMost(0.99),
                    reason = current.reason + "; reforzado por continuidad de secuencia"
                )
            }
        }
        return provisional
    }

    private fun inferOne(survey: SurveySession, photo: PhotoRecord, buildings: List<BuildingFootprint>): BuildingInference {
        val location = photo.location ?: return unknown(photo, "Fotografía sin posición")
        val heading = photo.orientation?.azimuthDegrees?.toDouble()
            ?: return unknown(photo, "Fotografía sin orientación de cámara")
        val cameraPoint = GeoPoint(location.latitude, location.longitude)
        val fov = photo.camera?.horizontalFovDegrees?.takeIf { it in 20.0..160.0 } ?: 60.0
        val maxAngularError = max(28.0, fov / 2.0 + 12.0)

        val candidates = buildings.mapNotNull { building ->
            val center = SpatialMath.centroid(building.polygon)
            val nearest = SpatialMath.nearestDistanceToPolygonMeters(cameraPoint, building.polygon)
            if (!nearest.isFinite() || nearest > MAX_BUILDING_DISTANCE_M) return@mapNotNull null
            val bearing = SpatialMath.bearingDegrees(cameraPoint, center)
            val angularError = SpatialMath.angularDeltaDegrees(heading, bearing)
            if (angularError > maxAngularError) return@mapNotNull null

            val angleScore = (1.0 - angularError / maxAngularError).coerceIn(0.0, 1.0)
            val distanceScore = (1.0 - nearest / MAX_BUILDING_DISTANCE_M).coerceIn(0.0, 1.0)
            val uncertainty = location.horizontalAccuracyMeters?.toDouble()
            val positionScore = when (location.positionReference) {
                PositionReference.GNSS_ABSOLUTE -> when {
                    uncertainty == null -> 0.25
                    uncertainty <= 3.0 -> 1.0
                    uncertainty <= 6.0 -> 0.85
                    uncertainty <= 12.0 -> 0.65
                    uncertainty <= 25.0 -> 0.40
                    else -> 0.18
                }
                // A relative indoor pose can be locally consistent without being absolutely precise.
                // It therefore contributes to GIS matching, but is deliberately capped.
                PositionReference.INTERIOR_RELATIVE -> when {
                    uncertainty == null -> 0.18
                    uncertainty <= 4.0 -> 0.62
                    uncertainty <= 8.0 -> 0.50
                    uncertainty <= 15.0 -> 0.34
                    else -> 0.16
                }
                PositionReference.UNKNOWN -> 0.16
            }
            val orientationScore = when (photo.orientation.accuracy) {
                3 -> 1.0
                2 -> 0.78
                1 -> 0.52
                else -> 0.28
            }
            val facultyScore = when {
                building.faculty.isNullOrBlank() -> 0.5
                building.faculty.equals(survey.place, ignoreCase = true) -> 1.0
                else -> 0.0
            }
            val score = 0.42 * angleScore + 0.23 * distanceScore + 0.18 * positionScore + 0.12 * orientationScore + 0.05 * facultyScore
            Candidate(building, nearest, bearing, angularError, score)
        }.sortedByDescending { it.score }

        val best = candidates.firstOrNull() ?: return unknown(photo, "Ningún edificio GIS coincide con posición + dirección")
        val second = candidates.getOrNull(1)
        val ambiguityPenalty = if (second != null && best.score - second.score < 0.08) 0.10 else 0.0
        val confidence = (best.score - ambiguityPenalty).coerceIn(0.0, 0.99)
        if (confidence < MIN_CONFIDENCE) {
            return BuildingInference(
                photoId = photo.id,
                sequence = photo.sequence,
                confidence = confidence,
                distanceMeters = best.distance,
                bearingToBuildingDegrees = best.bearing,
                angularErrorDegrees = best.angularError,
                reason = "Candidato ${best.building.name} insuficiente (${(confidence * 100).toInt()}%)"
            )
        }
        return BuildingInference(
            photoId = photo.id,
            sequence = photo.sequence,
            buildingId = best.building.id,
            buildingName = best.building.name,
            confidence = confidence,
            distanceMeters = best.distance,
            bearingToBuildingDegrees = best.bearing,
            angularErrorDegrees = best.angularError,
            reason = if (location.isInteriorRelative) "GIS + dirección de cámara + distancia + incertidumbre de pose interior relativa" else "GIS + dirección de cámara + distancia + precisión GNSS"
        )
    }

    private fun unknown(photo: PhotoRecord, reason: String) = BuildingInference(
        photoId = photo.id,
        sequence = photo.sequence,
        reason = reason
    )

    private data class Candidate(
        val building: BuildingFootprint,
        val distance: Double,
        val bearing: Double,
        val angularError: Double,
        val score: Double
    )
}
