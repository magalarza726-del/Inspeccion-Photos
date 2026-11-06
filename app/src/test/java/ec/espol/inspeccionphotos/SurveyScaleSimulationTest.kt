package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.survey.GpsQuality
import ec.espol.inspeccionphotos.survey.PhotoRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SurveyScaleSimulationTest {
    @Test
    fun oneThousandPhotosKeepIndependentIdentityAndSequence() {
        val photos = (1..1000).map { sequence ->
            PhotoRecord(
                id = UUID.randomUUID().toString(),
                surveyId = "simulation",
                sequence = sequence,
                fileName = "FIMCP_${sequence.toString().padStart(4, '0')}.jpg",
                relativeOriginalPath = "originals/FIMCP_${sequence.toString().padStart(4, '0')}.jpg",
                capturedAtEpochMs = 1_700_000_000_000L + sequence * 1_000,
                place = "FIMCP",
                location = null,
                orientation = null,
                camera = null,
                gpsQuality = GpsQuality.UNAVAILABLE,
                poseConfidence = "BAJA"
            )
        }
        assertEquals(1000, photos.size)
        assertEquals(1000, photos.map { it.id }.toSet().size)
        assertEquals((1..1000).toList(), photos.map { it.sequence })
        assertTrue(photos.all { it.relativeOriginalPath.startsWith("originals/") })
    }
}
