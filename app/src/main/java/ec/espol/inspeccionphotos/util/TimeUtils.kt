package ec.espol.inspeccionphotos.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun date(epochMs: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))
    fun time(epochMs: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
    fun fileStamp(epochMs: Long): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(epochMs))
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d h %02d min".format(h, m) else "%d min %02d s".format(m, s)
    }
}
