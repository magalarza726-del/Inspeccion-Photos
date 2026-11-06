package ec.espol.inspeccionphotos

import ec.espol.inspeccionphotos.integrity.SyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun retryBackoffGrowsAndCaps() {
        val now = 1_000_000L
        val first = SyncPolicy.nextAttemptEpochMs(now, 0) - now
        val second = SyncPolicy.nextAttemptEpochMs(now, 1) - now
        val huge = SyncPolicy.nextAttemptEpochMs(now, 99) - now
        assertEquals(60_000L, first)
        assertEquals(120_000L, second)
        assertTrue(huge <= 6 * 60 * 60_000L)
    }
}
