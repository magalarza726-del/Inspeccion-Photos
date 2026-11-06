package ec.espol.inspeccionphotos.coverage

import ec.espol.inspeccionphotos.spatial.SpatialMath
import ec.espol.inspeccionphotos.survey.*
import kotlin.math.roundToInt

object CoverageAnalyzer {
    fun analyze(
        photos: List<PhotoRecord>,
        buildings: List<BuildingFootprint>,
        inferences: List<BuildingInference>
    ): List<BuildingCoverage> {
        val photoById = photos.associateBy { it.id }
        val identified = inferences.filter { it.identified }.groupBy { it.buildingId }
        return buildings.map { building ->
            val matches = identified[building.id].orEmpty()
            val center = SpatialMath.centroid(building.polygon)
            val perSide = FacadeSide.entries.map { side ->
                val observations = matches.mapNotNull observation@{ inf ->
                    val photo = photoById[inf.photoId] ?: return@observation null
                    val loc = photo.location ?: return@observation null
                    val camera = GeoPoint(loc.latitude, loc.longitude)
                    val observedSide = SpatialMath.facadeSeenFrom(camera, center)
                    if (observedSide != side) return@observation null
                    Observation(
                        bearingFromBuilding = SpatialMath.bearingDegrees(center, camera),
                        distance = inf.distanceMeters ?: SpatialMath.distanceMeters(center, camera),
                        lowAccuracy = when (loc.positionReference) {
                            PositionReference.GNSS_ABSOLUTE -> (loc.horizontalAccuracyMeters ?: Float.MAX_VALUE) > 12f
                            PositionReference.INTERIOR_RELATIVE -> (loc.horizontalAccuracyMeters ?: Float.MAX_VALUE) > 8f
                            PositionReference.UNKNOWN -> true
                        },
                        relativePosition = loc.isInteriorRelative
                    )
                }
                coverageFor(side, observations)
            }
            val warnings = buildList {
                if (matches.isEmpty()) add("No se identificaron fotografías de ${building.name} durante este levantamiento.")
                perSide.forEach { facade ->
                    val label = facade.side.spanish()
                    when {
                        facade.percent < 25 -> add("Falta documentar fachada $label de ${building.name}.")
                        facade.percent < 55 -> add("Cobertura parcial en fachada $label de ${building.name} (${facade.percent}%).")
                    }
                    if (facade.supportingPhotos > 0 && facade.lowAccuracyPhotos == facade.supportingPhotos) {
                        add("La fachada $label de ${building.name} sólo tiene fotografías con baja confianza posicional (GNSS o pose interior relativa).")
                    }
                }
                if (matches.none { (it.distanceMeters ?: Double.MAX_VALUE) <= 20.0 }) {
                    add("${building.name} no tiene fotografías identificadas a menos de 20 m.")
                }
            }.distinct()
            BuildingCoverage(
                buildingId = building.id,
                buildingName = building.name,
                overallPercent = perSide.map { it.percent }.average().roundToInt(),
                facades = perSide,
                warnings = warnings
            )
        }.sortedByDescending { it.overallPercent }
    }

    private fun coverageFor(side: FacadeSide, observations: List<Observation>): FacadeCoverage {
        if (observations.isEmpty()) return FacadeCoverage(side, 0, 0, 0, 0, 0.0)
        val center = when (side) {
            FacadeSide.NORTH -> 0.0
            FacadeSide.EAST -> 90.0
            FacadeSide.SOUTH -> 180.0
            FacadeSide.WEST -> 270.0
        }
        val relatives = observations.map { signedAngularDelta(it.bearingFromBuilding, center) }
        val spread = (relatives.maxOrNull()!! - relatives.minOrNull()!!).coerceIn(0.0, 90.0)
        val close = observations.count { it.distance <= 25.0 }
        val low = observations.count { it.lowAccuracy }
        val countContribution = (observations.size * 9).coerceAtMost(36)
        val closeContribution = (close * 7).coerceAtMost(28)
        val spreadContribution = (spread / 90.0 * 30.0).roundToInt()
        val qualityPenalty = (low * 4).coerceAtMost(20)
        val percent = (12 + countContribution + closeContribution + spreadContribution - qualityPenalty).coerceIn(5, 100)
        return FacadeCoverage(side, percent, observations.size, close, low, spread)
    }

    private fun signedAngularDelta(value: Double, center: Double): Double {
        var d = (value - center + 540.0) % 360.0 - 180.0
        if (d < -180) d += 360.0
        return d
    }

    private fun FacadeSide.spanish(): String = when (this) {
        FacadeSide.NORTH -> "norte"
        FacadeSide.EAST -> "este"
        FacadeSide.SOUTH -> "sur"
        FacadeSide.WEST -> "oeste"
    }

    private data class Observation(val bearingFromBuilding: Double, val distance: Double, val lowAccuracy: Boolean, val relativePosition: Boolean)
}
