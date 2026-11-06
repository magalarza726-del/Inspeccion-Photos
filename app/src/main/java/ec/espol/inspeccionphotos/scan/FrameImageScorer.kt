package ec.espol.inspeccionphotos.scan

import android.graphics.Bitmap
import kotlin.math.abs

object FrameImageScorer {
    data class Score(val sharpness: Double, val perceptualHash: Long)

    fun score(bitmap: Bitmap): Score {
        val small = Bitmap.createScaledBitmap(bitmap, 96, 72, true)
        var sumGradient = 0.0
        var count = 0
        for (y in 1 until small.height - 1 step 2) {
            for (x in 1 until small.width - 1 step 2) {
                val c = gray(small.getPixel(x, y))
                val left = gray(small.getPixel(x - 1, y))
                val right = gray(small.getPixel(x + 1, y))
                val up = gray(small.getPixel(x, y - 1))
                val down = gray(small.getPixel(x, y + 1))
                sumGradient += abs(4 * c - left - right - up - down)
                count++
            }
        }
        val hashBmp = Bitmap.createScaledBitmap(small, 8, 8, true)
        val values = IntArray(64)
        var avg = 0.0
        var i = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            values[i] = gray(hashBmp.getPixel(x, y))
            avg += values[i]
            i++
        }
        avg /= 64.0
        var hash = 0L
        values.forEachIndexed { index, v -> if (v >= avg) hash = hash or (1L shl index) }
        if (small !== bitmap) small.recycle()
        if (hashBmp !== small && hashBmp !== bitmap) hashBmp.recycle()
        return Score(if (count == 0) 0.0 else sumGradient / count, hash)
    }

    private fun gray(pixel: Int): Int {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        return (r * 30 + g * 59 + b * 11) / 100
    }
}
