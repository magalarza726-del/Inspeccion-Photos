package ec.espol.inspeccionphotos.survey

const val CURRENT_SCHEMA_VERSION = "1.3.1"

enum class SurveyStatus { ACTIVE, COMPLETED, DISCARDED }
enum class EnvironmentMode { EXTERIOR, INTERIOR }
enum class GpsQuality { EXCELLENT, GOOD, ACCEPTABLE, LOW, VERY_LOW, UNAVAILABLE }

/**
 * Semántica de la posición. GNSS_ABSOLUTE representa una medición geográfica directa del
 * sistema de localización de Android. INTERIOR_RELATIVE es una estimación relativa anclada
 * a un último GNSS exterior (ARCore/IMU/pasos); nunca debe mostrarse como precisión GPS.
 */
enum class PositionReference { GNSS_ABSOLUTE, INTERIOR_RELATIVE, UNKNOWN }
enum class SyncState { LOCAL_ONLY, PROCESSING, QUEUED, UPLOADED, VERIFIED, ERROR }
enum class CaptureProfile { HIGH_ACCURACY, BALANCED }

data class SurveySession(
    val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    val id: String,
    val place: String,
    val createdAtEpochMs: Long,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val status: SurveyStatus = SurveyStatus.ACTIVE,
    val environmentMode: EnvironmentMode = EnvironmentMode.EXTERIOR,
    val photoCount: Int = 0,
    val lastSequence: Int = 0,
    val routePointCount: Int = 0,
    val note: String = ""
)

data class LocationSnapshot(
    val timestampEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val horizontalAccuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val provider: String?,
    val speedMetersPerSecond: Float?,
    // Default GNSS preserves source compatibility for Phase 1-3 constructors. JSON migration
    // explicitly infers legacy interior providers before creating this object.
    val positionReference: PositionReference = PositionReference.GNSS_ABSOLUTE
) {
    val isAbsoluteGnss: Boolean get() = positionReference == PositionReference.GNSS_ABSOLUTE
    val isInteriorRelative: Boolean get() = positionReference == PositionReference.INTERIOR_RELATIVE
    val gnssAccuracyMeters: Float? get() = horizontalAccuracyMeters.takeIf { isAbsoluteGnss }
    val relativePoseUncertaintyMeters: Float? get() = horizontalAccuracyMeters.takeIf { isInteriorRelative }

    /** GPS quality is intentionally unavailable for relative indoor poses. */
    val quality: GpsQuality
        get() {
            if (!isAbsoluteGnss) return GpsQuality.UNAVAILABLE
            return when (horizontalAccuracyMeters) {
                null -> GpsQuality.UNAVAILABLE
                in 0f..3f -> GpsQuality.EXCELLENT
                in 3f..6f -> GpsQuality.GOOD
                in 6f..12f -> GpsQuality.ACCEPTABLE
                in 12f..25f -> GpsQuality.LOW
                else -> GpsQuality.VERY_LOW
            }
        }
}

data class OrientationSnapshot(
    val timestampEpochMs: Long,
    val azimuthDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val accuracy: Int
)

data class RoutePoint(
    val timestampEpochMs: Long,
    val location: LocationSnapshot,
    val orientation: OrientationSnapshot?,
    val environmentMode: EnvironmentMode = EnvironmentMode.EXTERIOR
)

data class CameraMetadata(
    val widthPx: Int?,
    val heightPx: Int?,
    val deviceMake: String?,
    val deviceModel: String?,
    val lensModel: String?,
    val focalLengthMm: Double?,
    val focalLength35Mm: Double?,
    val horizontalFovDegrees: Double?,
    val verticalFovDegrees: Double?,
    val exifOrientation: Int?,
    val exifTimestampEpochMs: Long?,
    val zoom: Double? = null
)

data class PhotoRecord(
    val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    val id: String,
    val surveyId: String,
    val sequence: Int,
    val fileName: String,
    val relativeOriginalPath: String,
    val capturedAtEpochMs: Long,
    val place: String,
    val location: LocationSnapshot?,
    val orientation: OrientationSnapshot?,
    val camera: CameraMetadata?,
    val gpsQuality: GpsQuality,
    val poseConfidence: String,
    val sha256: String? = null,
    val referencePath: String? = null,
    val previewPath: String? = null,
    val thumbnailPath: String? = null,
    val processedAtEpochMs: Long? = null,
    val remoteOriginalPath: String? = null,
    val remoteReferencePath: String? = null,
    val remotePreviewPath: String? = null,
    val remoteThumbnailPath: String? = null,
    val remoteVerifiedSha256: String? = null,
    val remoteVerifiedAtEpochMs: Long? = null,
    val syncAttempts: Int = 0,
    val lastSyncError: String? = null,
    val syncState: SyncState = SyncState.LOCAL_ONLY
)

data class PoiRecord(
    val id: String,
    val surveyId: String,
    val type: String,
    val createdAtEpochMs: Long,
    val location: LocationSnapshot?,
    val orientation: OrientationSnapshot?,
    val note: String = ""
)

data class PendingCapture(
    val surveyId: String,
    val photoId: String,
    val sequence: Int,
    val fileName: String,
    val relativeOriginalPath: String,
    val startedAtEpochMs: Long,
    val place: String,
    val fallbackLocation: LocationSnapshot?,
    val fallbackOrientation: OrientationSnapshot?
)

data class SyncTask(
    val photoId: String,
    val surveyId: String,
    val createdAtEpochMs: Long,
    val attempts: Int = 0,
    val nextAttemptEpochMs: Long = 0L,
    val lastError: String? = null
)

data class SyncSummary(
    val totalPhotos: Int = 0,
    val localOnly: Int = 0,
    val processing: Int = 0,
    val queued: Int = 0,
    val uploaded: Int = 0,
    val verified: Int = 0,
    val errors: Int = 0,
    val queueItems: Int = 0
) {
    val pending: Int get() = totalPhotos - verified
}

data class AuditSummary(
    val photos: Int,
    val originalsPresent: Int,
    val routePoints: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val gpsExcellent: Int,
    val gpsGood: Int,
    val gpsAcceptable: Int,
    val gpsLow: Int,
    val gpsVeryLow: Int,
    val gpsUnavailable: Int,
    val orientationAvailable: Int,
    val warnings: List<String>,
    val hashesReady: Int = 0,
    val previewsReady: Int = 0,
    val syncVerified: Int = 0,
    val syncPending: Int = 0,
    val syncErrors: Int = 0,
    val spatialCoveragePercent: Int? = null,
    val gisBuildings: Int = 0,
    val identifiedPhotos: Int = 0,
    val interiorPoints: Int = 0,
    val interiorArCorePoints: Int = 0,
    val spatialBursts: Int = 0,
    val burstPhotos: Int = 0,
    val videoScans: Int = 0,
    val extractedVideoFrames: Int = 0,
    val interiorRelativePhotos: Int = 0
)
