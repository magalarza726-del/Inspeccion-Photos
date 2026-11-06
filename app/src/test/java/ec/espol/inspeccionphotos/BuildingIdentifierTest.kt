package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.spatial.BuildingIdentifier
import ec.espol.inspeccionphotos.survey.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildingIdentifierTest {
    @Test
    fun identifiesOnlyBuildingInFrontOfCamera() {
        val survey = SurveySession(id = "s", place = "FIMCP", createdAtEpochMs = 0, startedAtEpochMs = 0)
        val building = BuildingFootprint(
            id = "24E", name = "24E", faculty = "FIMCP",
            polygon = listOf(
                GeoPoint(-2.14705, -79.96655), GeoPoint(-2.14705, -79.96635),
                GeoPoint(-2.14685, -79.96635), GeoPoint(-2.14685, -79.96655)
            )
        )
        val photo = PhotoRecord(
            id = "p", surveyId = "s", sequence = 1, fileName = "p.jpg", relativeOriginalPath = "originals/p.jpg",
            capturedAtEpochMs = 0, place = "FIMCP",
            location = LocationSnapshot(0, -2.1470, -79.9670, null, 2f, null, "gps", null),
            orientation = OrientationSnapshot(0, 90f, 0f, 0f, 3), camera = null,
            gpsQuality = GpsQuality.EXCELLENT, poseConfidence = "ALTA"
        )
        val result = BuildingIdentifier.infer(survey, listOf(photo), listOf(building)).single()
        assertTrue(result.identified)
        assertEquals("24E", result.buildingName)
    }

    @Test
    fun doesNotInventWithoutHeading() {
        val survey = SurveySession(id = "s", place = "FIMCP", createdAtEpochMs = 0, startedAtEpochMs = 0)
        val building = BuildingFootprint("b", "B", null, listOf(GeoPoint(-2.147, -79.967), GeoPoint(-2.147, -79.966), GeoPoint(-2.146, -79.966)))
        val photo = PhotoRecord(
            id = "p", surveyId = "s", sequence = 1, fileName = "p.jpg", relativeOriginalPath = "originals/p.jpg",
            capturedAtEpochMs = 0, place = "FIMCP",
            location = LocationSnapshot(0, -2.147, -79.968, null, 2f, null, "gps", null),
            orientation = null, camera = null, gpsQuality = GpsQuality.EXCELLENT, poseConfidence = "ALTA"
        )
        assertFalse(BuildingIdentifier.infer(survey, listOf(photo), listOf(building)).single().identified)
    }
}
