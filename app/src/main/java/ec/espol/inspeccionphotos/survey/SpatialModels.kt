package ec.espol.inspeccionphotos.survey

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class BuildingFootprint(
    val id: String,
    val name: String,
    val faculty: String? = null,
    val polygon: List<GeoPoint>,
    val source: String = "geojson"
)

data class BuildingInference(
    val photoId: String,
    val sequence: Int,
    val buildingId: String? = null,
    val buildingName: String? = null,
    val confidence: Double = 0.0,
    val distanceMeters: Double? = null,
    val bearingToBuildingDegrees: Double? = null,
    val angularErrorDegrees: Double? = null,
    val reason: String = "Sin candidato GIS con confianza suficiente"
) {
    val identified: Boolean get() = buildingId != null && confidence >= 0.55
}

enum class FacadeSide { NORTH, EAST, SOUTH, WEST }

data class FacadeCoverage(
    val side: FacadeSide,
    val percent: Int,
    val supportingPhotos: Int,
    val closePhotos: Int,
    val lowAccuracyPhotos: Int,
    val angularSpreadDegrees: Double
)

data class BuildingCoverage(
    val buildingId: String,
    val buildingName: String,
    val overallPercent: Int,
    val facades: List<FacadeCoverage>,
    val warnings: List<String>
)

data class SpatialSummary(
    val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    val surveyId: String,
    val analyzedAtEpochMs: Long,
    val routeGeoJsonPath: String = "route.geojson",
    val gisBuildingsAvailable: Int = 0,
    val identifiedPhotos: Int = 0,
    val unknownPhotos: Int = 0,
    val buildingsWithCoverage: Int = 0,
    val averageCoveragePercent: Int? = null,
    val warnings: List<String> = emptyList()
)

data class SpatialAnalysisBundle(
    val buildings: List<BuildingFootprint> = emptyList(),
    val inferences: List<BuildingInference> = emptyList(),
    val coverage: List<BuildingCoverage> = emptyList(),
    val summary: SpatialSummary? = null
)
