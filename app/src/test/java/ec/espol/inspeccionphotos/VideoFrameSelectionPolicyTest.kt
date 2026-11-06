package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.scan.VideoFrameSelectionPolicy
import ec.espol.inspeccionphotos.survey.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameSelectionPolicyTest {
    @Test fun selectsChangedViewAndRejectsRedundantHash() {
        val loc = LocationSnapshot(1, -2.147, -79.967, null, 2f, null, "gps", null)
        val samples = listOf(
            VideoPoseSample(0, loc, OrientationSnapshot(0,0f,0f,0f,3), null, null),
            VideoPoseSample(300, loc, OrientationSnapshot(300,5f,0f,0f,3), null, null),
            VideoPoseSample(1200, loc, OrientationSnapshot(1200,25f,0f,0f,3), null, null)
        )
        assertEquals(2, VideoFrameSelectionPolicy.selectPoseCandidates(samples).size)
        assertTrue(VideoFrameSelectionPolicy.isRedundant(0b1010, 0b1011, maxHammingDistance = 1))
        assertFalse(VideoFrameSelectionPolicy.isRedundant(0L, -1L, maxHammingDistance = 8))
    }
}
