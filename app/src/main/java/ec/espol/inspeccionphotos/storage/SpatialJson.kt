package ec.espol.inspeccionphotos.storage

import ec.espol.inspeccionphotos.survey.*
import org.json.JSONArray
import org.json.JSONObject

object SpatialJson {
    fun inference(v: BuildingInference): JSONObject = JSONObject().apply {
        put("photoId", v.photoId)
        put("sequence", v.sequence)
        putNullable("buildingId", v.buildingId)
        putNullable("buildingName", v.buildingName)
        put("confidence", v.confidence)
        putNullable("distanceMeters", v.distanceMeters)
        putNullable("bearingToBuildingDegrees", v.bearingToBuildingDegrees)
        putNullable("angularErrorDegrees", v.angularErrorDegrees)
        put("reason", v.reason)
    }

    fun decodeInference(o: JSONObject) = BuildingInference(
        photoId = o.optString("photoId"),
        sequence = o.optInt("sequence"),
        buildingId = o.optNullableString("buildingId"),
        buildingName = o.optNullableString("buildingName"),
        confidence = o.optDouble("confidence", 0.0),
        distanceMeters = o.optNullableDouble("distanceMeters"),
        bearingToBuildingDegrees = o.optNullableDouble("bearingToBuildingDegrees"),
        angularErrorDegrees = o.optNullableDouble("angularErrorDegrees"),
        reason = o.optString("reason", "")
    )

    fun facade(v: FacadeCoverage): JSONObject = JSONObject().apply {
        put("side", v.side.name)
        put("percent", v.percent)
        put("supportingPhotos", v.supportingPhotos)
        put("closePhotos", v.closePhotos)
        put("lowAccuracyPhotos", v.lowAccuracyPhotos)
        put("angularSpreadDegrees", v.angularSpreadDegrees)
    }

    fun decodeFacade(o: JSONObject) = FacadeCoverage(
        side = runCatching { FacadeSide.valueOf(o.optString("side")) }.getOrDefault(FacadeSide.NORTH),
        percent = o.optInt("percent", 0),
        supportingPhotos = o.optInt("supportingPhotos", 0),
        closePhotos = o.optInt("closePhotos", 0),
        lowAccuracyPhotos = o.optInt("lowAccuracyPhotos", 0),
        angularSpreadDegrees = o.optDouble("angularSpreadDegrees", 0.0)
    )

    fun buildingCoverage(v: BuildingCoverage): JSONObject = JSONObject().apply {
        put("buildingId", v.buildingId)
        put("buildingName", v.buildingName)
        put("overallPercent", v.overallPercent)
        put("facades", JSONArray().apply { v.facades.forEach { put(facade(it)) } })
        put("warnings", JSONArray().apply { v.warnings.forEach { put(it) } })
    }

    fun decodeBuildingCoverage(o: JSONObject) = BuildingCoverage(
        buildingId = o.optString("buildingId"),
        buildingName = o.optString("buildingName"),
        overallPercent = o.optInt("overallPercent", 0),
        facades = o.optJSONArray("facades").toList { decodeFacade(it) },
        warnings = o.optJSONArray("warnings").stringList()
    )

    fun summary(v: SpatialSummary): JSONObject = JSONObject().apply {
        put("schemaVersion", v.schemaVersion)
        put("surveyId", v.surveyId)
        put("analyzedAtEpochMs", v.analyzedAtEpochMs)
        put("routeGeoJsonPath", v.routeGeoJsonPath)
        put("gisBuildingsAvailable", v.gisBuildingsAvailable)
        put("identifiedPhotos", v.identifiedPhotos)
        put("unknownPhotos", v.unknownPhotos)
        put("buildingsWithCoverage", v.buildingsWithCoverage)
        putNullable("averageCoveragePercent", v.averageCoveragePercent)
        put("warnings", JSONArray().apply { v.warnings.forEach { put(it) } })
    }

    fun decodeSummary(o: JSONObject) = SpatialSummary(
        schemaVersion = o.optString("schemaVersion", "1.2"),
        surveyId = o.optString("surveyId"),
        analyzedAtEpochMs = o.optLong("analyzedAtEpochMs", 0L),
        routeGeoJsonPath = o.optString("routeGeoJsonPath", "route.geojson"),
        gisBuildingsAvailable = o.optInt("gisBuildingsAvailable", 0),
        identifiedPhotos = o.optInt("identifiedPhotos", 0),
        unknownPhotos = o.optInt("unknownPhotos", 0),
        buildingsWithCoverage = o.optInt("buildingsWithCoverage", 0),
        averageCoveragePercent = if (o.isNull("averageCoveragePercent") || !o.has("averageCoveragePercent")) null else o.optInt("averageCoveragePercent"),
        warnings = o.optJSONArray("warnings").stringList()
    )

    private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun <T> JSONArray?.toList(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = mutableListOf<T>()
        for (i in 0 until length()) optJSONObject(i)?.let { out += transform(it) }
        return out
    }

    private fun JSONArray?.stringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    }
}
