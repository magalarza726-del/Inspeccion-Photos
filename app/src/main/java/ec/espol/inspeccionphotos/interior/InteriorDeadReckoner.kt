package ec.espol.inspeccionphotos.interior

import ec.espol.inspeccionphotos.survey.*
import kotlin.math.*

class InteriorDeadReckoner(
    private val stepLengthMeters: Double = 0.72
) {
    private var anchor: InteriorAnchor? = null
    private var east = 0.0
    private var north = 0.0
    private var up = 0.0
    private var steps = 0L
    private var uncertainty = 0.0
    private var arOrigin: ArPoseSample? = null
    private var arBaseEast = 0.0
    private var arBaseNorth = 0.0
    private var arBaseUp = 0.0

    fun setAnchor(value: InteriorAnchor) {
        anchor = value
        east = 0.0
        north = 0.0
        up = 0.0
        steps = 0L
        uncertainty = (value.horizontalAccuracyMeters ?: 6f).toDouble().coerceAtLeast(1.5)
        arOrigin = null
        arBaseEast = 0.0
        arBaseNorth = 0.0
        arBaseUp = 0.0
    }

    fun currentAnchor(): InteriorAnchor? = anchor

    /** Starts a new ARCore camera/session segment without resetting the accumulated indoor route. */
    fun beginArCoreSegment() {
        arOrigin = null
        arBaseEast = east
        arBaseNorth = north
        arBaseUp = up
    }

    fun clearAnchor() {
        anchor = null; arOrigin = null
        east = 0.0; north = 0.0; up = 0.0; uncertainty = 0.0
        arBaseEast = 0.0; arBaseNorth = 0.0; arBaseUp = 0.0
    }

    fun onStep(surveyId: String, timestamp: Long, headingDegrees: Float?, totalStepCount: Long): InteriorPoseSample? {
        if (anchor == null) return null
        val heading = headingDegrees?.toDouble() ?: 0.0
        val radians = Math.toRadians(heading)
        east += stepLengthMeters * sin(radians)
        north += stepLengthMeters * cos(radians)
        steps = totalStepCount
        uncertainty += if (headingDegrees == null) 0.45 else 0.22
        return sample(surveyId, timestamp, headingDegrees, InteriorTrackingSource.STEP_IMU, false)
    }

    fun onBarometer(surveyId: String, timestamp: Long, pressureHpa: Float?, headingDegrees: Float?, totalStepCount: Long): InteriorPoseSample? {
        val a = anchor ?: return null
        val base = a.pressureHpa ?: return null
        val current = pressureHpa ?: return null
        // Hypsometric approximation. Used only as a relative vertical estimate.
        val baseAlt = 44330.0 * (1.0 - (base / 1013.25).toDouble().pow(0.1903))
        val currentAlt = 44330.0 * (1.0 - (current / 1013.25).toDouble().pow(0.1903))
        up = currentAlt - baseAlt
        steps = totalStepCount
        return sample(surveyId, timestamp, headingDegrees, InteriorTrackingSource.STEP_IMU, false)
    }

    fun onArCore(surveyId: String, pose: ArPoseSample, headingDegrees: Float?, totalStepCount: Long): InteriorPoseSample? {
        if (anchor == null || !pose.tracking) return null
        val origin = arOrigin ?: pose.also {
            arOrigin = it
            // A new ARCore Session has its own origin. Preserve the relative route accumulated
            // before opening/reopening this screen instead of snapping back to (0,0,0).
            arBaseEast = east
            arBaseNorth = north
            arBaseUp = up
        }
        // ARCore world: X = camera-right, Y = up and camera-forward points toward -Z.
        // That world is NOT geographic east/north. Rotate its horizontal displacement using the
        // exterior anchor heading so downstream GIS never mistakes ARCore's arbitrary axes for ENU.
        val right = (pose.xMeters - origin.xMeters).toDouble()
        val forward = -(pose.zMeters - origin.zMeters).toDouble()
        val referenceHeading = anchor?.headingDegrees ?: headingDegrees
        val h = Math.toRadians((referenceHeading ?: 0f).toDouble())
        east = arBaseEast + right * cos(h) + forward * sin(h)
        north = arBaseNorth - right * sin(h) + forward * cos(h)
        up = arBaseUp + (pose.yMeters - origin.yMeters).toDouble()
        steps = totalStepCount
        // ARCore VIO is relative. We still accumulate uncertainty from the GNSS anchor and drift.
        // If no heading was available at anchoring time, add a penalty because ENU orientation is weak.
        uncertainty = maxOf((anchor?.horizontalAccuracyMeters ?: 4f).toDouble(), 1.5) +
            hypot(east, north) * 0.018 + if (referenceHeading == null) 3.0 else 0.0
        return sample(surveyId, pose.timestampEpochMs, headingDegrees, InteriorTrackingSource.ARCORE_VIO, true)
    }

    fun toApproximateLocation(sample: InteriorPoseSample): LocationSnapshot? {
        val a = anchor ?: return null
        val latRad = Math.toRadians(a.latitude)
        val dLat = sample.northMeters / 6_378_137.0
        val dLon = sample.eastMeters / (6_378_137.0 * cos(latRad).coerceAtLeast(1e-6))
        return LocationSnapshot(
            timestampEpochMs = sample.timestampEpochMs,
            latitude = a.latitude + Math.toDegrees(dLat),
            longitude = a.longitude + Math.toDegrees(dLon),
            altitudeMeters = a.altitudeMeters?.plus(sample.upMeters),
            horizontalAccuracyMeters = sample.uncertaintyMeters.toFloat(),
            verticalAccuracyMeters = maxOf(2.0, sample.uncertaintyMeters * 0.65).toFloat(),
            provider = if (sample.source == InteriorTrackingSource.ARCORE_VIO) "arcore_vio_relative" else "imu_dead_reckoning_relative",
            speedMetersPerSecond = null,
            positionReference = PositionReference.INTERIOR_RELATIVE
        )
    }

    private fun sample(
        surveyId: String,
        timestamp: Long,
        heading: Float?,
        source: InteriorTrackingSource,
        arTracking: Boolean
    ): InteriorPoseSample {
        val confidence = when {
            uncertainty <= 4.0 -> InteriorConfidence.HIGH
            uncertainty <= 8.0 -> InteriorConfidence.MEDIUM
            uncertainty <= 15.0 -> InteriorConfidence.LOW
            else -> InteriorConfidence.VERY_LOW
        }
        return InteriorPoseSample(
            surveyId = surveyId,
            anchorId = requireNotNull(anchor).id,
            timestampEpochMs = timestamp,
            eastMeters = east,
            northMeters = north,
            upMeters = up,
            headingDegrees = heading,
            stepCount = steps,
            uncertaintyMeters = uncertainty,
            confidence = confidence,
            source = source,
            arTracking = arTracking
        )
    }
}
