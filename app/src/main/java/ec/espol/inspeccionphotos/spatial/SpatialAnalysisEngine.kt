package ec.espol.inspeccionphotos.spatial

import ec.espol.inspeccionphotos.coverage.CoverageAnalyzer
import ec.espol.inspeccionphotos.gis.RouteGeoJsonExporter
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.SpatialAnalysisBundle
import ec.espol.inspeccionphotos.survey.SpatialSummary
import kotlin.math.roundToInt

class SpatialAnalysisEngine(private val repository: SurveyRepository) {
    fun analyzeSurvey(surveyId: String): SpatialAnalysisBundle {
        val survey = repository.loadSession(surveyId)
            ?: return SpatialAnalysisBundle()
        val photos = repository.loadPhotos(surveyId)
        val route = repository.loadRoute(surveyId)
        val pois = repository.loadPois(surveyId)
        val buildings = repository.loadBuildings(surveyId)

        val inferences = BuildingIdentifier.infer(survey, photos, buildings)
        val routeLocations = (
            route.map { ec.espol.inspeccionphotos.survey.GeoPoint(it.location.latitude, it.location.longitude) } +
                photos.mapNotNull { it.location?.let { loc -> ec.espol.inspeccionphotos.survey.GeoPoint(loc.latitude, loc.longitude) } }
        )
        val relevantBuildings = buildings.filter { building ->
            building.faculty?.equals(survey.place, ignoreCase = true) == true ||
                (building.faculty.isNullOrBlank() && routeLocations.any { point ->
                    SpatialMath.distanceMeters(point, SpatialMath.centroid(building.polygon)) <= 120.0
                }) || inferences.any { it.identified && it.buildingId == building.id }
        }
        val coverage = CoverageAnalyzer.analyze(photos, relevantBuildings, inferences)
        RouteGeoJsonExporter.write(
            output = repository.routeGeoJsonFile(surveyId),
            survey = survey,
            route = route,
            photos = photos,
            pois = pois,
            inferences = inferences
        )

        val identified = inferences.count { it.identified }
        val warnings = buildList {
            if (route.size < 2) add("Trayectoria insuficiente para un mapa completo.")
            if (photos.any { it.location == null }) add("${photos.count { it.location == null }} foto(s) no pueden ubicarse en el mapa por falta de posición.")
            if (buildings.isEmpty()) add("No hay GIS de edificios cargado: la identificación y cobertura de fachadas quedan pendientes.")
            coverage.flatMap { it.warnings }.distinct().take(12).forEach(::add)
        }
        val summary = SpatialSummary(
            surveyId = surveyId,
            analyzedAtEpochMs = System.currentTimeMillis(),
            gisBuildingsAvailable = buildings.size,
            identifiedPhotos = identified,
            unknownPhotos = photos.size - identified,
            buildingsWithCoverage = coverage.count { it.overallPercent > 0 },
            averageCoveragePercent = coverage.takeIf { it.isNotEmpty() }?.map { it.overallPercent }?.average()?.roundToInt(),
            warnings = warnings.distinct()
        )
        val bundle = SpatialAnalysisBundle(buildings, inferences, coverage, summary)
        repository.saveSpatialAnalysis(bundle)
        return bundle
    }
}
