package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.survey.GpsQuality
import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.util.SurveyMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyMathTest {
    @Test
    fun gpsQualityUsesAccuracyBands() {
        fun location(a: Float) = LocationSnapshot(0, -2.0, -79.0, null, a, null, "gps", null)
        assertEquals(GpsQuality.EXCELLENT, location(2.3f).quality)
        assertEquals(GpsQuality.GOOD, location(4.0f).quality)
        assertEquals(GpsQuality.ACCEPTABLE, location(8.0f).quality)
        assertEquals(GpsQuality.LOW, location(20.0f).quality)
        assertEquals(GpsQuality.VERY_LOW, location(40.0f).quality)
    }

    @Test
    fun distanceIsZeroForSamePoint() {
        val a = LocationSnapshot(0, -2.147, -79.967, null, 2f, null, "gps", null)
        assertTrue(SurveyMath.distanceMeters(a, a) < 0.001)
    }
}
