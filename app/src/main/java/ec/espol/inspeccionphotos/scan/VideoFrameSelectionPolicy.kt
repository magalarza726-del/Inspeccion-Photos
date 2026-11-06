package ec.espol.inspeccionphotos.scan

import ec.espol.inspeccionphotos.survey.VideoPoseSample
import kotlin.math.abs
import kotlin.math.min

object VideoFrameSelectionPolicy {
    fun selectPoseCandidates(
        samples: List<VideoPoseSample>,
        minDistanceMeters: Double = 1.0,
        minHeadingDegrees: Double = 16.0,
        maxGapMillis: Long = 3_000,
        minGapMillis: Long = 600
    ): List<VideoPoseSample> {
        if (samples.isEmpty()) return emptyList()
        val sorted = samples.sortedBy { it.timestampEpochMs }
        val out = mutableListOf(sorted.first())
        for (current in sorted.drop(1)) {
            val previous = out.last()
            val elapsed = current.timestampEpochMs - previous.timestampEpochMs
            if (elapsed < minGapMillis) continue
            val distance = when {
                previous.interiorPose != null && current.interiorPose != null &&
                    previous.interiorPose.anchorId == current.interiorPose.anchorId -> {
                    val dx = current.interiorPose.eastMeters - previous.interiorPose.eastMeters
                    val dy = current.interiorPose.northMeters - previous.interiorPose.northMeters
                    kotlin.math.hypot(dx, dy)
                }
                else -> BurstTriggerPolicy.distanceMeters(previous.location, current.location)
            }
            val angle = angleDelta(previous.orientation?.azimuthDegrees, current.orientation?.azimuthDegrees)
            if (distance >= minDistanceMeters || angle >= minHeadingDegrees || elapsed >= maxGapMillis) out += current
        }
        return out
    }

    fun isRedundant(previousHash: Long?, currentHash: Long, maxHammingDistance: Int = 8): Boolean {
        if (previousHash == null) return false
        return java.lang.Long.bitCount(previousHash xor currentHash) <= maxHammingDistance
    }

    private fun angleDelta(a: Float?, b: Float?): Double {
        if (a == null || b == null) return 0.0
        val raw = abs(a.toDouble() - b.toDouble()) % 360.0
        return min(raw, 360.0 - raw)
    }
}
