package ec.espol.inspeccionphotos.previews

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.PhotoRecord
import java.io.File
import java.io.FileOutputStream

object PreviewGenerator {
    data class Result(
        val referencePath: String?,
        val previewPath: String?,
        val thumbnailPath: String?
    )

    fun generate(repository: SurveyRepository, photo: PhotoRecord): Result {
        val source = repository.originalFile(photo)
        require(source.exists() && source.length() > 0L) { "Original local no disponible" }

        val rotation = exifRotation(source)
        val reference = generateVariant(repository, photo, source, rotation, "reference", 1920, 88)
        val preview = generateVariant(repository, photo, source, rotation, "previews", 960, 82)
        val thumb = generateVariant(repository, photo, source, rotation, "thumbnails", 320, 75)
        return Result(reference, preview, thumb)
    }

    private fun generateVariant(
        repository: SurveyRepository,
        photo: PhotoRecord,
        source: File,
        rotationDegrees: Float,
        folder: String,
        maxLongEdge: Int,
        quality: Int
    ): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxLongEdge * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: return@runCatching null
        val oriented = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        val scale = minOf(1f, maxLongEdge.toFloat() / maxOf(oriented.width, oriented.height).toFloat())
        val targetW = (oriented.width * scale).toInt().coerceAtLeast(1)
        val targetH = (oriented.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (targetW != oriented.width || targetH != oriented.height) {
            Bitmap.createScaledBitmap(oriented, targetW, targetH, true).also { if (it !== oriented) oriented.recycle() }
        } else oriented

        val relative = "$folder/${photo.id}.webp"
        val out = File(repository.surveyDir(photo.surveyId), relative).apply { parentFile?.mkdirs() }
        FileOutputStream(out).use { stream ->
            check(scaled.compress(webpFormat(), quality, stream)) { "No se pudo codificar $relative" }
        }
        scaled.recycle()
        relative
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun exifRotation(file: File): Float = runCatching {
        when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
}
