package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.camera.PoseResolver
import ec.espol.inspeccionphotos.survey.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSemanticsTest {
    @Test fun interiorRelativePoseIsNeverClassifiedAsExcellentGps() {
        val loc = LocationSnapshot(
            timestampEpochMs = 1L, latitude = -2.147, longitude = -79.967, altitudeMeters = 8.0,
            horizontalAccuracyMeters = 1.8f, verticalAccuracyMeters = 2.0f,
            provider = "arcore_vio_relative", speedMetersPerSecond = null,
            positionReference = PositionReference.INTERIOR_RELATIVE
        )
        assertEquals(GpsQuality.UNAVAILABLE, loc.quality)
        assertEquals(null, loc.gnssAccuracyMeters)
        assertEquals(1.8f, loc.relativePoseUncertaintyMeters)
    }

    @Test fun absoluteGnssKeepsNormalGpsQuality() {
        val loc = LocationSnapshot(1L, -2.147, -79.967, 8.0, 1.8f, 2.0f, "gps", null, PositionReference.GNSS_ABSOLUTE)
        assertEquals(GpsQuality.EXCELLENT, loc.quality)
        assertEquals(1.8f, loc.gnssAccuracyMeters)
    }

    @Test fun poseResolverLabelsInteriorAsRelativeConfidence() {
        val loc = LocationSnapshot(1_000L, -2.147, -79.967, 8.0, 3.0f, null, "arcore_vio_relative", null, PositionReference.INTERIOR_RELATIVE)
        val route = listOf(RoutePoint(1_000L, loc, null, EnvironmentMode.INTERIOR))
        val pending = PendingCapture("s", "p", 1, "x.jpg", "originals/x.jpg", 1_000L, "FIMCP", null, null)
        val resolved = PoseResolver.resolve(1_000L, route, pending)
        assertEquals(GpsQuality.UNAVAILABLE, resolved.location?.quality)
        assertTrue(resolved.confidence.startsWith("INTERIOR_RELATIVA_"))
    }
}
