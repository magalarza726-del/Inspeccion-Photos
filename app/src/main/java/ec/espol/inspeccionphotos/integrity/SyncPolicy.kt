package ec.espol.inspeccionphotos.integrity

import kotlin.math.min
import kotlin.math.pow

object SyncPolicy {
    private const val BASE_DELAY_MS = 60_000L
    private const val MAX_DELAY_MS = 6 * 60 * 60_000L

    fun nextAttemptEpochMs(nowEpochMs: Long, attempts: Int): Long {
        val exponent = attempts.coerceIn(0, 12)
        val delay = min(MAX_DELAY_MS.toDouble(), BASE_DELAY_MS * 2.0.pow(exponent.toDouble())).toLong()
        return nowEpochMs + delay
    }
}
