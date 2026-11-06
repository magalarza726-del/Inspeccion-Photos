package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.spatial.SpatialMath
import ec.espol.inspeccionphotos.survey.FacadeSide
import ec.espol.inspeccionphotos.survey.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialMathTest {
    @Test
    fun bearingNorthIsApproximatelyZero() {
        val a = GeoPoint(-2.1470, -79.9670)
        val b = GeoPoint(-2.1460, -79.9670)
        assertTrue(SpatialMath.angularDeltaDegrees(SpatialMath.bearingDegrees(a, b), 0.0) < 1.0)
    }

    @Test
    fun cameraNorthOfBuildingSeesNorthFacade() {
        val building = GeoPoint(-2.1470, -79.9670)
        val camera = GeoPoint(-2.1465, -79.9670)
        assertEquals(FacadeSide.NORTH, SpatialMath.facadeSeenFrom(camera, building))
    }
}
