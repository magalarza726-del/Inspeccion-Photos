package ec.espol.inspeccionphotos.storage

import ec.espol.inspeccionphotos.survey.*
import org.json.JSONObject

object SurveyJson {
    fun session(value: SurveySession): JSONObject = JSONObject().apply {
        put("schemaVersion", value.schemaVersion)
        put("id", value.id)
        put("place", value.place)
        put("createdAtEpochMs", value.createdAtEpochMs)
        put("startedAtEpochMs", value.startedAtEpochMs)
        putNullable("endedAtEpochMs", value.endedAtEpochMs)
        put("status", value.status.name)
        put("environmentMode", value.environmentMode.name)
        put("photoCount", value.photoCount)
        put("lastSequence", value.lastSequence)
        put("routePointCount", value.routePointCount)
        put("note", value.note)
    }

    fun decodeSession(o: JSONObject) = SurveySession(
        schemaVersion = o.optString("schemaVersion", "1.0"),
        id = o.getString("id"),
        place = o.optString("place", "Otro"),
        createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
        startedAtEpochMs = o.optLong("startedAtEpochMs", 0L),
        endedAtEpochMs = o.optNullableLong("endedAtEpochMs"),
        status = enumValueOrDefault(o.optString("status"), SurveyStatus.ACTIVE),
        environmentMode = enumValueOrDefault(o.optString("environmentMode"), EnvironmentMode.EXTERIOR),
        photoCount = o.optInt("photoCount", 0),
        lastSequence = o.optInt("lastSequence", 0),
        routePointCount = o.optInt("routePointCount", 0),
        note = o.optString("note", "")
    )

    fun location(v: LocationSnapshot?): Any = v?.let {
        JSONObject().apply {
            put("timestampEpochMs", it.timestampEpochMs)
            put("latitude", it.latitude)
            put("longitude", it.longitude)
            putNullable("altitudeMeters", it.altitudeMeters)
            putNullable("horizontalAccuracyMeters", it.horizontalAccuracyMeters)
            putNullable("verticalAccuracyMeters", it.verticalAccuracyMeters)
            putNullable("provider", it.provider)
            putNullable("speedMetersPerSecond", it.speedMetersPerSecond)
            put("positionReference", it.positionReference.name)
        }
    } ?: JSONObject.NULL

    fun decodeLocation(any: Any?): LocationSnapshot? {
        val o = any as? JSONObject ?: return null
        val provider = o.optNullableString("provider")
        val reference = if (o.has("positionReference")) {
            enumValueOrDefault(o.optString("positionReference"), PositionReference.UNKNOWN)
        } else {
            // Migration for Phase 1-4 data written before schema 1.3.1.
            when {
                provider?.contains("relative", ignoreCase = true) == true -> PositionReference.INTERIOR_RELATIVE
                provider?.contains("arcore", ignoreCase = true) == true -> PositionReference.INTERIOR_RELATIVE
                provider?.contains("dead_reckoning", ignoreCase = true) == true -> PositionReference.INTERIOR_RELATIVE
                provider != null -> PositionReference.GNSS_ABSOLUTE
                else -> PositionReference.UNKNOWN
            }
        }
        return LocationSnapshot(
            timestampEpochMs = o.optLong("timestampEpochMs"),
            latitude = o.optDouble("latitude"),
            longitude = o.optDouble("longitude"),
            altitudeMeters = o.optNullableDouble("altitudeMeters"),
            horizontalAccuracyMeters = o.optNullableDouble("horizontalAccuracyMeters")?.toFloat(),
            verticalAccuracyMeters = o.optNullableDouble("verticalAccuracyMeters")?.toFloat(),
            provider = provider,
            speedMetersPerSecond = o.optNullableDouble("speedMetersPerSecond")?.toFloat(),
            positionReference = reference
        )
    }

    fun orientation(v: OrientationSnapshot?): Any = v?.let {
        JSONObject().apply {
            put("timestampEpochMs", it.timestampEpochMs)
            put("azimuthDegrees", it.azimuthDegrees)
            put("pitchDegrees", it.pitchDegrees)
            put("rollDegrees", it.rollDegrees)
            put("accuracy", it.accuracy)
        }
    } ?: JSONObject.NULL

    fun decodeOrientation(any: Any?): OrientationSnapshot? {
        val o = any as? JSONObject ?: return null
        return OrientationSnapshot(
            timestampEpochMs = o.optLong("timestampEpochMs"),
            azimuthDegrees = o.optDouble("azimuthDegrees").toFloat(),
            pitchDegrees = o.optDouble("pitchDegrees").toFloat(),
            rollDegrees = o.optDouble("rollDegrees").toFloat(),
            accuracy = o.optInt("accuracy", 0)
        )
    }

    fun route(v: RoutePoint): JSONObject = JSONObject().apply {
        put("timestampEpochMs", v.timestampEpochMs)
        put("location", location(v.location))
        put("orientation", orientation(v.orientation))
        put("environmentMode", v.environmentMode.name)
    }

    fun decodeRoute(o: JSONObject): RoutePoint? {
        val loc = decodeLocation(o.opt("location")) ?: return null
        return RoutePoint(
            o.optLong("timestampEpochMs", loc.timestampEpochMs),
            loc,
            decodeOrientation(o.opt("orientation")),
            enumValueOrDefault(o.optString("environmentMode"), EnvironmentMode.EXTERIOR)
        )
    }

    fun camera(v: CameraMetadata?): Any = v?.let {
        JSONObject().apply {
            putNullable("widthPx", it.widthPx)
            putNullable("heightPx", it.heightPx)
            putNullable("deviceMake", it.deviceMake)
            putNullable("deviceModel", it.deviceModel)
            putNullable("lensModel", it.lensModel)
            putNullable("focalLengthMm", it.focalLengthMm)
            putNullable("focalLength35Mm", it.focalLength35Mm)
            putNullable("horizontalFovDegrees", it.horizontalFovDegrees)
            putNullable("verticalFovDegrees", it.verticalFovDegrees)
            putNullable("exifOrientation", it.exifOrientation)
            putNullable("exifTimestampEpochMs", it.exifTimestampEpochMs)
            putNullable("zoom", it.zoom)
        }
    } ?: JSONObject.NULL

    fun decodeCamera(any: Any?): CameraMetadata? {
        val o = any as? JSONObject ?: return null
        return CameraMetadata(
            widthPx = o.optNullableInt("widthPx"),
            heightPx = o.optNullableInt("heightPx"),
            deviceMake = o.optNullableString("deviceMake"),
            deviceModel = o.optNullableString("deviceModel"),
            lensModel = o.optNullableString("lensModel"),
            focalLengthMm = o.optNullableDouble("focalLengthMm"),
            focalLength35Mm = o.optNullableDouble("focalLength35Mm"),
            horizontalFovDegrees = o.optNullableDouble("horizontalFovDegrees"),
            verticalFovDegrees = o.optNullableDouble("verticalFovDegrees"),
            exifOrientation = o.optNullableInt("exifOrientation"),
            exifTimestampEpochMs = o.optNullableLong("exifTimestampEpochMs"),
            zoom = o.optNullableDouble("zoom")
        )
    }

    fun photo(v: PhotoRecord): JSONObject = JSONObject().apply {
        put("schemaVersion", v.schemaVersion)
        put("id", v.id)
        put("surveyId", v.surveyId)
        put("sequence", v.sequence)
        put("fileName", v.fileName)
        put("relativeOriginalPath", v.relativeOriginalPath)
        put("capturedAtEpochMs", v.capturedAtEpochMs)
        put("place", v.place)
        put("location", location(v.location))
        put("orientation", orientation(v.orientation))
        put("camera", camera(v.camera))
        put("gpsQuality", v.gpsQuality.name)
        put("poseConfidence", v.poseConfidence)
        putNullable("sha256", v.sha256)
        putNullable("referencePath", v.referencePath)
        putNullable("previewPath", v.previewPath)
        putNullable("thumbnailPath", v.thumbnailPath)
        putNullable("processedAtEpochMs", v.processedAtEpochMs)
        putNullable("remoteOriginalPath", v.remoteOriginalPath)
        putNullable("remoteReferencePath", v.remoteReferencePath)
        putNullable("remotePreviewPath", v.remotePreviewPath)
        putNullable("remoteThumbnailPath", v.remoteThumbnailPath)
        putNullable("remoteVerifiedSha256", v.remoteVerifiedSha256)
        putNullable("remoteVerifiedAtEpochMs", v.remoteVerifiedAtEpochMs)
        put("syncAttempts", v.syncAttempts)
        putNullable("lastSyncError", v.lastSyncError)
        put("syncState", v.syncState.name)
    }

    fun decodePhoto(o: JSONObject): PhotoRecord {
        val decodedLocation = decodeLocation(o.opt("location"))
        val correctedGpsQuality = if (decodedLocation?.positionReference == PositionReference.GNSS_ABSOLUTE) {
            // Recalculate from the measurement so legacy metadata cannot keep an impossible label.
            decodedLocation.quality
        } else GpsQuality.UNAVAILABLE
        val storedPoseConfidence = o.optString("poseConfidence", "BAJA")
        val correctedPoseConfidence = if (decodedLocation?.isInteriorRelative == true && !storedPoseConfidence.startsWith("INTERIOR_RELATIVA_")) {
            val u = decodedLocation.relativePoseUncertaintyMeters
            val level = when {
                u == null -> "MUY_BAJA"
                u <= 4f -> "ALTA"
                u <= 8f -> "MEDIA"
                u <= 15f -> "BAJA"
                else -> "MUY_BAJA"
            }
            "INTERIOR_RELATIVA_$level"
        } else storedPoseConfidence

        return PhotoRecord(
            schemaVersion = o.optString("schemaVersion", "1.0"),
            id = o.getString("id"),
            surveyId = o.getString("surveyId"),
            sequence = o.optInt("sequence"),
            fileName = o.getString("fileName"),
            relativeOriginalPath = o.optString("relativeOriginalPath", "originals/${o.getString("fileName")}"),
            capturedAtEpochMs = o.optLong("capturedAtEpochMs"),
            place = o.optString("place"),
            location = decodedLocation,
            orientation = decodeOrientation(o.opt("orientation")),
            camera = decodeCamera(o.opt("camera")),
            gpsQuality = correctedGpsQuality,
            poseConfidence = correctedPoseConfidence,
            sha256 = o.optNullableString("sha256"),
            referencePath = o.optNullableString("referencePath"),
            previewPath = o.optNullableString("previewPath"),
            thumbnailPath = o.optNullableString("thumbnailPath"),
            processedAtEpochMs = o.optNullableLong("processedAtEpochMs"),
            remoteOriginalPath = o.optNullableString("remoteOriginalPath"),
            remoteReferencePath = o.optNullableString("remoteReferencePath"),
            remotePreviewPath = o.optNullableString("remotePreviewPath"),
            remoteThumbnailPath = o.optNullableString("remoteThumbnailPath"),
            remoteVerifiedSha256 = o.optNullableString("remoteVerifiedSha256"),
            remoteVerifiedAtEpochMs = o.optNullableLong("remoteVerifiedAtEpochMs"),
            syncAttempts = o.optInt("syncAttempts", 0),
            lastSyncError = o.optNullableString("lastSyncError"),
            syncState = enumValueOrDefault(o.optString("syncState"), SyncState.LOCAL_ONLY)
        )
    }


    fun poi(v: PoiRecord): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("surveyId", v.surveyId)
        put("type", v.type)
        put("createdAtEpochMs", v.createdAtEpochMs)
        put("location", location(v.location))
        put("orientation", orientation(v.orientation))
        put("note", v.note)
    }

    fun decodePoi(o: JSONObject) = PoiRecord(
        id = o.getString("id"),
        surveyId = o.getString("surveyId"),
        type = o.optString("type"),
        createdAtEpochMs = o.optLong("createdAtEpochMs"),
        location = decodeLocation(o.opt("location")),
        orientation = decodeOrientation(o.opt("orientation")),
        note = o.optString("note", "")
    )

    fun pending(v: PendingCapture): JSONObject = JSONObject().apply {
        put("surveyId", v.surveyId)
        put("photoId", v.photoId)
        put("sequence", v.sequence)
        put("fileName", v.fileName)
        put("relativeOriginalPath", v.relativeOriginalPath)
        put("startedAtEpochMs", v.startedAtEpochMs)
        put("place", v.place)
        put("fallbackLocation", location(v.fallbackLocation))
        put("fallbackOrientation", orientation(v.fallbackOrientation))
    }

    fun decodePending(o: JSONObject) = PendingCapture(
        surveyId = o.getString("surveyId"),
        photoId = o.getString("photoId"),
        sequence = o.optInt("sequence"),
        fileName = o.getString("fileName"),
        relativeOriginalPath = o.getString("relativeOriginalPath"),
        startedAtEpochMs = o.optLong("startedAtEpochMs"),
        place = o.optString("place"),
        fallbackLocation = decodeLocation(o.opt("fallbackLocation")),
        fallbackOrientation = decodeOrientation(o.opt("fallbackOrientation"))
    )

    fun syncTask(v: SyncTask): JSONObject = JSONObject().apply {
        put("photoId", v.photoId)
        put("surveyId", v.surveyId)
        put("createdAtEpochMs", v.createdAtEpochMs)
        put("attempts", v.attempts)
        put("nextAttemptEpochMs", v.nextAttemptEpochMs)
        putNullable("lastError", v.lastError)
    }

    fun decodeSyncTask(o: JSONObject) = SyncTask(
        photoId = o.getString("photoId"),
        surveyId = o.getString("surveyId"),
        createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis()),
        attempts = o.optInt("attempts", 0),
        nextAttemptEpochMs = o.optLong("nextAttemptEpochMs", 0L),
        lastError = o.optNullableString("lastError")
    )

    private fun JSONObject.putNullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
    private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: default
}
