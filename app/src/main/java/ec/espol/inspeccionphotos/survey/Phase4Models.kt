package ec.espol.inspeccionphotos.survey

enum class InteriorTrackingSource { STEP_IMU, ARCORE_VIO, FUSED, GPS_ANCHOR }
enum class InteriorConfidence { HIGH, MEDIUM, LOW, VERY_LOW }
enum class BurstStatus { ACTIVE, COMPLETED, CANCELLED, INTERRUPTED }
enum class VideoScanStatus { RECORDING, PROCESSING, READY, ERROR }

data class InertialSnapshot(
    val timestampEpochMs: Long,
    val accelX: Float?,
    val accelY: Float?,
    val accelZ: Float?,
    val gyroX: Float?,
    val gyroY: Float?,
    val gyroZ: Float?,
    val magneticX: Float?,
    val magneticY: Float?,
    val magneticZ: Float?,
    val pressureHpa: Float?,
    val stepCount: Long
)

data class InteriorAnchor(
    val id: String,
    val surveyId: String,
    val createdAtEpochMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val horizontalAccuracyMeters: Float?,
    val headingDegrees: Float?,
    val pressureHpa: Float?
)

data class InteriorPoseSample(
    val surveyId: String,
    val anchorId: String,
    val timestampEpochMs: Long,
    val eastMeters: Double,
    val northMeters: Double,
    val upMeters: Double,
    val headingDegrees: Float?,
    val stepCount: Long,
    val uncertaintyMeters: Double,
    val confidence: InteriorConfidence,
    val source: InteriorTrackingSource,
    val arTracking: Boolean = false
)

data class ArPoseSample(
    val timestampEpochMs: Long,
    val xMeters: Float,
    val yMeters: Float,
    val zMeters: Float,
    val qx: Float,
    val qy: Float,
    val qz: Float,
    val qw: Float,
    val tracking: Boolean
)

data class SpatialBurstRecord(
    val id: String,
    val surveyId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val status: BurstStatus = BurstStatus.ACTIVE,
    val minDistanceMeters: Double = 1.2,
    val minHeadingChangeDegrees: Double = 18.0,
    val maxIntervalMillis: Long = 4_000,
    val photoIds: List<String> = emptyList()
)

data class VideoPoseSample(
    val timestampEpochMs: Long,
    val location: LocationSnapshot?,
    val orientation: OrientationSnapshot?,
    val interiorPose: InteriorPoseSample?,
    val inertial: InertialSnapshot?
)

data class VideoFrameCandidate(
    val timestampOffsetMs: Long,
    val relativePath: String,
    val sharpnessScore: Double,
    val perceptualHash: Long,
    val location: LocationSnapshot?,
    val orientation: OrientationSnapshot?,
    val interiorPose: InteriorPoseSample? = null,
    val selectedReason: String
)

data class VideoScanRecord(
    val id: String,
    val surveyId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val relativeVideoPath: String,
    val relativePosePath: String,
    val status: VideoScanStatus = VideoScanStatus.RECORDING,
    val durationMillis: Long = 0,
    val extractedFrames: Int = 0,
    val error: String? = null,
    val frames: List<VideoFrameCandidate> = emptyList()
)

data class Phase4Summary(
    val interiorPoints: Int = 0,
    val interiorArCorePoints: Int = 0,
    val bursts: Int = 0,
    val burstPhotos: Int = 0,
    val videoScans: Int = 0,
    val extractedVideoFrames: Int = 0
)
