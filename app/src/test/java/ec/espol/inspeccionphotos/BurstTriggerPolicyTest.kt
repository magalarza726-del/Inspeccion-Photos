package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.scan.BurstPose
import ec.espol.inspeccionphotos.scan.BurstTriggerPolicy
import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstTriggerPolicyTest {
    private val loc = LocationSnapshot(1, -2.147, -79.967, null, 2f, null, "gps", null)
    @Test fun rejectsTooFastDuplicate() {
        val a = BurstPose(1000, loc, OrientationSnapshot(1000, 0f,0f,0f,3))
        val b = BurstPose(1200, loc, OrientationSnapshot(1200, 40f,0f,0f,3))
        assertFalse(BurstTriggerPolicy.shouldCapture(a,b))
    }
    @Test fun acceptsAngularChangeAfterMinimumInterval() {
        val a = BurstPose(1000, loc, OrientationSnapshot(1000, 0f,0f,0f,3))
        val b = BurstPose(2200, loc, OrientationSnapshot(2200, 30f,0f,0f,3))
        assertTrue(BurstTriggerPolicy.shouldCapture(a,b))
    }
}
