package ec.espol.inspeccionphotos.gis

import ec.espol.inspeccionphotos.survey.BuildingFootprint
import ec.espol.inspeccionphotos.survey.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID

object BuildingGeoJson {
    fun import(input: InputStream, destination: File): Int {
        val text = input.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val buildings = decode(root)
        require(buildings.isNotEmpty()) { "El GeoJSON no contiene polígonos de edificios utilizables" }
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".tmp")
        temp.writeText(root.toString(2))
        if (destination.exists()) destination.delete()
        if (!temp.renameTo(destination)) {
            destination.writeText(root.toString(2))
            temp.delete()
        }
        return buildings.size
    }

    fun load(file: File): List<BuildingFootprint> = runCatching {
        if (!file.exists()) emptyList() else decode(JSONObject(file.readText()))
    }.getOrDefault(emptyList())

    fun decode(root: JSONObject): List<BuildingFootprint> {
        val features = root.optJSONArray("features") ?: return emptyList()
        val result = mutableListOf<BuildingFootprint>()
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val baseId = feature.optString("id").takeIf { it.isNotBlank() }
                ?: properties.optString("id").takeIf { it.isNotBlank() }
                ?: properties.optString("building_id").takeIf { it.isNotBlank() }
                ?: UUID.nameUUIDFromBytes(feature.toString().toByteArray()).toString()
            val name = sequenceOf("name", "building", "ref", "code", "nombre")
                .map { properties.optString(it) }
                .firstOrNull { it.isNotBlank() }
                ?: "Edificio ${result.size + 1}"
            val faculty = sequenceOf("faculty", "facultad", "zone", "zona")
                .map { properties.optString(it) }
                .firstOrNull { it.isNotBlank() }
            when (geometry.optString("type")) {
                "Polygon" -> {
                    val polygon = firstRing(geometry.optJSONArray("coordinates"))
                    if (polygon.size >= 3) result += BuildingFootprint(baseId, name, faculty, polygon)
                }
                "MultiPolygon" -> {
                    val polys = geometry.optJSONArray("coordinates") ?: continue
                    for (p in 0 until polys.length()) {
                        val polygonArray = polys.optJSONArray(p) ?: continue
                        val polygon = firstRing(polygonArray)
                        if (polygon.size >= 3) {
                            result += BuildingFootprint("$baseId:$p", if (p == 0) name else "$name (${p + 1})", faculty, polygon)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun firstRing(coordinates: JSONArray?): List<GeoPoint> {
        val ring = coordinates?.optJSONArray(0) ?: return emptyList()
        val points = mutableListOf<GeoPoint>()
        for (i in 0 until ring.length()) {
            val c = ring.optJSONArray(i) ?: continue
            if (c.length() < 2) continue
            val lon = c.optDouble(0, Double.NaN)
            val lat = c.optDouble(1, Double.NaN)
            if (lat.isFinite() && lon.isFinite()) points += GeoPoint(lat, lon)
        }
        return if (points.size > 1 && points.first() == points.last()) points.dropLast(1) else points
    }
}
