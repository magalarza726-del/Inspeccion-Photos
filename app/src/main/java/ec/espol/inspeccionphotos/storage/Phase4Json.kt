package ec.espol.inspeccionphotos.storage

import ec.espol.inspeccionphotos.survey.*
import org.json.JSONArray
import org.json.JSONObject

object Phase4Json {
    fun inertial(v: InertialSnapshot?): Any = v?.let {
        JSONObject().apply {
            put("timestampEpochMs", it.timestampEpochMs)
            putNullable("accelX", it.accelX); putNullable("accelY", it.accelY); putNullable("accelZ", it.accelZ)
            putNullable("gyroX", it.gyroX); putNullable("gyroY", it.gyroY); putNullable("gyroZ", it.gyroZ)
            putNullable("magneticX", it.magneticX); putNullable("magneticY", it.magneticY); putNullable("magneticZ", it.magneticZ)
            putNullable("pressureHpa", it.pressureHpa)
            put("stepCount", it.stepCount)
        }
    } ?: JSONObject.NULL

    fun decodeInertial(any: Any?): InertialSnapshot? {
        val o = any as? JSONObject ?: return null
        return InertialSnapshot(
            timestampEpochMs = o.optLong("timestampEpochMs"),
            accelX = o.floatOrNull("accelX"), accelY = o.floatOrNull("accelY"), accelZ = o.floatOrNull("accelZ"),
            gyroX = o.floatOrNull("gyroX"), gyroY = o.floatOrNull("gyroY"), gyroZ = o.floatOrNull("gyroZ"),
            magneticX = o.floatOrNull("magneticX"), magneticY = o.floatOrNull("magneticY"), magneticZ = o.floatOrNull("magneticZ"),
            pressureHpa = o.floatOrNull("pressureHpa"),
            stepCount = o.optLong("stepCount")
        )
    }

    fun anchor(v: InteriorAnchor): JSONObject = JSONObject().apply {
        put("id", v.id); put("surveyId", v.surveyId); put("createdAtEpochMs", v.createdAtEpochMs)
        put("latitude", v.latitude); put("longitude", v.longitude)
        putNullable("altitudeMeters", v.altitudeMeters); putNullable("horizontalAccuracyMeters", v.horizontalAccuracyMeters)
        putNullable("headingDegrees", v.headingDegrees); putNullable("pressureHpa", v.pressureHpa)
    }

    fun decodeAnchor(o: JSONObject) = InteriorAnchor(
        id = o.optString("id", "legacy-anchor"), surveyId = o.getString("surveyId"), createdAtEpochMs = o.optLong("createdAtEpochMs"),
        latitude = o.optDouble("latitude"), longitude = o.optDouble("longitude"),
        altitudeMeters = o.doubleOrNull("altitudeMeters"), horizontalAccuracyMeters = o.floatOrNull("horizontalAccuracyMeters"),
        headingDegrees = o.floatOrNull("headingDegrees"), pressureHpa = o.floatOrNull("pressureHpa")
    )

    fun interiorPose(v: InteriorPoseSample): JSONObject = JSONObject().apply {
        put("surveyId", v.surveyId); put("anchorId", v.anchorId); put("timestampEpochMs", v.timestampEpochMs)
        put("eastMeters", v.eastMeters); put("northMeters", v.northMeters); put("upMeters", v.upMeters)
        putNullable("headingDegrees", v.headingDegrees); put("stepCount", v.stepCount)
        put("uncertaintyMeters", v.uncertaintyMeters); put("confidence", v.confidence.name)
        put("source", v.source.name); put("arTracking", v.arTracking)
    }

    fun decodeInteriorPose(o: JSONObject) = InteriorPoseSample(
        surveyId = o.getString("surveyId"), anchorId = o.optString("anchorId", "legacy-anchor"), timestampEpochMs = o.optLong("timestampEpochMs"),
        eastMeters = o.optDouble("eastMeters"), northMeters = o.optDouble("northMeters"), upMeters = o.optDouble("upMeters"),
        headingDegrees = o.floatOrNull("headingDegrees"), stepCount = o.optLong("stepCount"),
        uncertaintyMeters = o.optDouble("uncertaintyMeters"),
        confidence = enumOr(o.optString("confidence"), InteriorConfidence.VERY_LOW),
        source = enumOr(o.optString("source"), InteriorTrackingSource.STEP_IMU),
        arTracking = (o.opt("arTracking") as? Boolean) ?: false
    )

    fun burst(v: SpatialBurstRecord): JSONObject = JSONObject().apply {
        put("id", v.id); put("surveyId", v.surveyId); put("startedAtEpochMs", v.startedAtEpochMs)
        putNullable("endedAtEpochMs", v.endedAtEpochMs); put("status", v.status.name)
        put("minDistanceMeters", v.minDistanceMeters); put("minHeadingChangeDegrees", v.minHeadingChangeDegrees)
        put("maxIntervalMillis", v.maxIntervalMillis)
        put("photoIds", JSONArray().apply { v.photoIds.forEach { put(it) } })
    }

    fun decodeBurst(o: JSONObject) = SpatialBurstRecord(
        id = o.getString("id"), surveyId = o.getString("surveyId"), startedAtEpochMs = o.optLong("startedAtEpochMs"),
        endedAtEpochMs = o.longOrNull("endedAtEpochMs"), status = enumOr(o.optString("status"), BurstStatus.ACTIVE),
        minDistanceMeters = o.optDouble("minDistanceMeters", 1.2), minHeadingChangeDegrees = o.optDouble("minHeadingChangeDegrees", 18.0),
        maxIntervalMillis = o.optLong("maxIntervalMillis", 4_000),
        photoIds = o.optJSONArray("photoIds")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) } } ?: emptyList()
    )

    fun videoPose(v: VideoPoseSample): JSONObject = JSONObject().apply {
        put("timestampEpochMs", v.timestampEpochMs)
        put("location", SurveyJson.location(v.location))
        put("orientation", SurveyJson.orientation(v.orientation))
        put("interiorPose", v.interiorPose?.let(::interiorPose) ?: JSONObject.NULL)
        put("inertial", inertial(v.inertial))
    }

    fun decodeVideoPose(o: JSONObject) = VideoPoseSample(
        timestampEpochMs = o.optLong("timestampEpochMs"),
        location = SurveyJson.decodeLocation(o.opt("location")),
        orientation = SurveyJson.decodeOrientation(o.opt("orientation")),
        interiorPose = (o.opt("interiorPose") as? JSONObject)?.let(::decodeInteriorPose),
        inertial = decodeInertial(o.opt("inertial"))
    )

    fun frame(v: VideoFrameCandidate): JSONObject = JSONObject().apply {
        put("timestampOffsetMs", v.timestampOffsetMs); put("relativePath", v.relativePath)
        put("sharpnessScore", v.sharpnessScore); put("perceptualHash", v.perceptualHash)
        put("location", SurveyJson.location(v.location)); put("orientation", SurveyJson.orientation(v.orientation))
        put("interiorPose", v.interiorPose?.let(::interiorPose) ?: JSONObject.NULL)
        put("selectedReason", v.selectedReason)
    }

    fun decodeFrame(o: JSONObject) = VideoFrameCandidate(
        timestampOffsetMs = o.optLong("timestampOffsetMs"), relativePath = o.optString("relativePath"),
        sharpnessScore = o.optDouble("sharpnessScore"), perceptualHash = o.optLong("perceptualHash"),
        location = SurveyJson.decodeLocation(o.opt("location")), orientation = SurveyJson.decodeOrientation(o.opt("orientation")),
        interiorPose = (o.opt("interiorPose") as? JSONObject)?.let(::decodeInteriorPose),
        selectedReason = o.optString("selectedReason")
    )

    fun videoScan(v: VideoScanRecord): JSONObject = JSONObject().apply {
        put("id", v.id); put("surveyId", v.surveyId); put("startedAtEpochMs", v.startedAtEpochMs)
        putNullable("endedAtEpochMs", v.endedAtEpochMs); put("relativeVideoPath", v.relativeVideoPath)
        put("relativePosePath", v.relativePosePath); put("status", v.status.name); put("durationMillis", v.durationMillis)
        put("extractedFrames", v.extractedFrames); putNullable("error", v.error)
        put("frames", JSONArray().apply { v.frames.forEach { put(frame(it)) } })
    }

    fun decodeVideoScan(o: JSONObject) = VideoScanRecord(
        id = o.getString("id"), surveyId = o.getString("surveyId"), startedAtEpochMs = o.optLong("startedAtEpochMs"),
        endedAtEpochMs = o.longOrNull("endedAtEpochMs"), relativeVideoPath = o.optString("relativeVideoPath"),
        relativePosePath = o.optString("relativePosePath"), status = enumOr(o.optString("status"), VideoScanStatus.RECORDING),
        durationMillis = o.optLong("durationMillis"), extractedFrames = o.optInt("extractedFrames"), error = o.stringOrNull("error"),
        frames = o.optJSONArray("frames")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::decodeFrame) } } ?: emptyList()
    )

    private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.floatOrNull(key: String): Float? = if (!has(key) || isNull(key)) null else optDouble(key).toFloat()
    private fun JSONObject.doubleOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private inline fun <reified T : Enum<T>> enumOr(raw: String, default: T): T = enumValues<T>().firstOrNull { it.name == raw } ?: default
}
