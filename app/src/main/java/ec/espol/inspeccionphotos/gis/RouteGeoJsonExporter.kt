package ec.espol.inspeccionphotos.gis

import ec.espol.inspeccionphotos.survey.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object RouteGeoJsonExporter {
    fun write(
        output: File,
        survey: SurveySession,
        route: List<RoutePoint>,
        photos: List<PhotoRecord>,
        pois: List<PoiRecord>,
        inferences: List<BuildingInference>
    ): File {
        val inferenceByPhoto = inferences.associateBy { it.photoId }
        val features = JSONArray()

        if (route.isNotEmpty()) {
            features.put(feature(
                geometry = JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", JSONArray().apply {
                        route.forEach { p -> put(coord(p.location.longitude, p.location.latitude, p.location.altitudeMeters)) }
                    })
                },
                properties = JSONObject().apply {
                    put("kind", "route")
                    put("surveyId", survey.id)
                    put("place", survey.place)
                    put("pointCount", route.size)
                }
            ))
            environmentSegments(route).forEachIndexed { index, segment ->
                if (segment.size < 2) return@forEachIndexed
                features.put(feature(
                    geometry = JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", JSONArray().apply {
                            segment.forEach { p -> put(coord(p.location.longitude, p.location.latitude, p.location.altitudeMeters)) }
                        })
                    },
                    properties = JSONObject().apply {
                        put("kind", "route_segment")
                        put("segment", index)
                        put("environmentMode", segment.first().environmentMode.name)
                        put("pointCount", segment.size)
                    }
                ))
            }
            route.forEachIndexed { index, point ->
                val loc = point.location
                features.put(feature(
                    geometry = JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", coord(loc.longitude, loc.latitude, loc.altitudeMeters))
                    },
                    properties = JSONObject().apply {
                        put("kind", "route_point")
                        put("index", index)
                        put("timestampEpochMs", point.timestampEpochMs)
                        put("environmentMode", point.environmentMode.name)
                        put("positionReference", loc.positionReference.name)
                        putNullable("horizontalAccuracyMeters", loc.horizontalAccuracyMeters)
                        putNullable("gnssHorizontalAccuracyMeters", loc.gnssAccuracyMeters)
                        putNullable("relativePoseUncertaintyMeters", loc.relativePoseUncertaintyMeters)
                        put("horizontalAccuracyMeaning", if (loc.isInteriorRelative) "RELATIVE_POSE_UNCERTAINTY" else "GNSS_ACCURACY")
                        putNullable("verticalAccuracyMeters", loc.verticalAccuracyMeters)
                        putNullable("speedMetersPerSecond", loc.speedMetersPerSecond)
                        putNullable("headingDegrees", point.orientation?.azimuthDegrees)
                        putNullable("pitchDegrees", point.orientation?.pitchDegrees)
                        putNullable("rollDegrees", point.orientation?.rollDegrees)
                    }
                ))
            }
        }

        photos.forEach { photo ->
            val loc = photo.location ?: return@forEach
            val inference = inferenceByPhoto[photo.id]
            features.put(feature(
                geometry = JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", coord(loc.longitude, loc.latitude, loc.altitudeMeters))
                },
                properties = JSONObject().apply {
                    put("kind", "photo")
                    put("photoId", photo.id)
                    put("sequence", photo.sequence)
                    put("fileName", photo.fileName)
                    put("timestampEpochMs", photo.capturedAtEpochMs)
                    putNullable("headingDegrees", photo.orientation?.azimuthDegrees)
                    putNullable("pitchDegrees", photo.orientation?.pitchDegrees)
                    putNullable("rollDegrees", photo.orientation?.rollDegrees)
                    putNullable("horizontalFovDegrees", photo.camera?.horizontalFovDegrees)
                    put("positionReference", loc.positionReference.name)
                    putNullable("horizontalAccuracyMeters", loc.horizontalAccuracyMeters)
                    putNullable("gnssHorizontalAccuracyMeters", loc.gnssAccuracyMeters)
                    putNullable("relativePoseUncertaintyMeters", loc.relativePoseUncertaintyMeters)
                    put("horizontalAccuracyMeaning", if (loc.isInteriorRelative) "RELATIVE_POSE_UNCERTAINTY" else "GNSS_ACCURACY")
                    put("gpsQuality", photo.gpsQuality.name)
                    put("poseConfidence", photo.poseConfidence)
                    putNullable("buildingId", inference?.buildingId)
                    putNullable("buildingName", inference?.buildingName)
                    putNullable("buildingConfidence", inference?.confidence)
                }
            ))
        }

        pois.forEach { poi ->
            val loc = poi.location ?: return@forEach
            features.put(feature(
                geometry = JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", coord(loc.longitude, loc.latitude, loc.altitudeMeters))
                },
                properties = JSONObject().apply {
                    put("kind", "poi")
                    put("poiId", poi.id)
                    put("type", poi.type)
                    put("timestampEpochMs", poi.createdAtEpochMs)
                    putNullable("headingDegrees", poi.orientation?.azimuthDegrees)
                    put("note", poi.note)
                }
            ))
        }

        val root = JSONObject().apply {
            put("type", "FeatureCollection")
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("surveyId", survey.id)
            put("features", features)
        }
        output.parentFile?.mkdirs()
        val temp = File(output.parentFile, output.name + ".tmp")
        temp.writeText(root.toString(2))
        if (output.exists()) output.delete()
        if (!temp.renameTo(output)) {
            output.writeText(root.toString(2))
            temp.delete()
        }
        return output
    }

    private fun environmentSegments(route: List<RoutePoint>): List<List<RoutePoint>> {
        if (route.isEmpty()) return emptyList()
        val out = mutableListOf<MutableList<RoutePoint>>()
        route.forEach { point ->
            val current = out.lastOrNull()
            if (current == null || current.last().environmentMode != point.environmentMode) {
                out += mutableListOf(point)
            } else current += point
        }
        return out
    }

    private fun feature(geometry: JSONObject, properties: JSONObject) = JSONObject().apply {
        put("type", "Feature")
        put("geometry", geometry)
        put("properties", properties)
    }

    private fun coord(lon: Double, lat: Double, altitude: Double?): JSONArray = JSONArray().apply {
        put(lon); put(lat); if (altitude != null) put(altitude)
    }

    private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
}
