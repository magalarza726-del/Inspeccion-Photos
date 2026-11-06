package ec.espol.inspeccionphotos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * Decodificación común para fotos JPEG respetando la orientación EXIF.
 *
 * CameraX puede guardar los píxeles en la orientación nativa del sensor y escribir en EXIF
 * cómo deben mostrarse. BitmapFactory no aplica ese dato por sí solo; por eso cualquier
 * consumidor que dibuje el bitmap directamente (PDF, miniaturas, editor, etc.) debe hacerlo.
 */
object ImageOrientationUtils {
    fun decodeOrientedBitmap(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val rotation = exifRotationDegrees(file)
        // Cuando la imagen debe girarse 90/270°, ancho y alto visuales quedan intercambiados.
        val wantedW = if (rotation == 90f || rotation == 270f) targetHeight else targetWidth
        val wantedH = if (rotation == 90f || rotation == 270f) targetWidth else targetHeight

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= wantedW.coerceAtLeast(1) &&
            bounds.outHeight / (sample * 2) >= wantedH.coerceAtLeast(1)
        ) {
            sample *= 2
        }

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        return orient(decoded, file)
    }

    fun decodeOrientedBitmap(file: File, maxLongEdge: Int = 1600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        val scale = maxLongEdge.coerceAtLeast(1).toFloat() / longEdge.toFloat()
        val w = if (scale < 1f) (bounds.outWidth * scale).toInt() else bounds.outWidth
        val h = if (scale < 1f) (bounds.outHeight * scale).toInt() else bounds.outHeight
        return decodeOrientedBitmap(file, w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    private fun orient(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }.getOrElse { bitmap }
    }

    fun exifRotationDegrees(file: File): Float = runCatching {
        when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
}
