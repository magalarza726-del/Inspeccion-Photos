package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.interior.InteriorDeadReckoner
import ec.espol.inspeccionphotos.survey.InteriorAnchor
import ec.espol.inspeccionphotos.survey.ArPoseSample
import org.junit.Assert.assertTrue
import org.junit.Test

class InteriorDeadReckonerTest {
    @Test fun eastStepMovesEast() {
        val tracker = InteriorDeadReckoner(stepLengthMeters = .75)
        tracker.setAnchor(InteriorAnchor("a1", "s1", 1, -2.147, -79.967, 8.0, 2f, 0f, 1013f))
        val pose = tracker.onStep("s1", 2, 90f, 1)!!
        assertTrue(pose.eastMeters > .70)
        assertTrue(kotlin.math.abs(pose.northMeters) < .1)
        assertTrue(tracker.toApproximateLocation(pose)!!.longitude > -79.967)
    }

    @Test fun arCoreForwardIsRotatedByExteriorHeading() {
        val tracker = InteriorDeadReckoner()
        // Heading 90° means the camera was looking East when the AR world was anchored.
        tracker.setAnchor(InteriorAnchor("a2", "s1", 1, -2.147, -79.967, 8.0, 2f, 90f, 1013f))
        tracker.onArCore("s1", ArPoseSample(2, 0f, 0f, 0f, 0f, 0f, 0f, 1f, true), 90f, 0)
        val pose = tracker.onArCore("s1", ArPoseSample(3, 0f, 0f, -2f, 0f, 0f, 0f, 1f, true), 90f, 0)!!
        assertTrue(pose.eastMeters > 1.8)
        assertTrue(kotlin.math.abs(pose.northMeters) < .2)
    }

    @Test fun reopeningArCoreDoesNotResetIndoorRoute() {
        val tracker = InteriorDeadReckoner(stepLengthMeters = 1.0)
        tracker.setAnchor(InteriorAnchor("a3", "s1", 1, -2.147, -79.967, 8.0, 2f, 0f, 1013f))
        val walked = tracker.onStep("s1", 2, 0f, 1)!!
        assertTrue(walked.northMeters > .9)
        tracker.beginArCoreSegment()
        tracker.onArCore("s1", ArPoseSample(3, 0f, 0f, 0f, 0f, 0f, 0f, 1f, true), 0f, 1)
        val continued = tracker.onArCore("s1", ArPoseSample(4, 0f, 0f, -1f, 0f, 0f, 0f, 1f, true), 0f, 1)!!
        assertTrue(continued.northMeters > 1.9)
    }
}
