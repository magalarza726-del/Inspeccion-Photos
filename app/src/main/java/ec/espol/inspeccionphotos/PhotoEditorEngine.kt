package ec.espol.inspeccionphotos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** Editor pequeño, offline y sin dependencias externas. */
data class PhotoEditParameters(
    val rotationQuarterTurns: Int = 0,
    val cropPercent: Int = 100,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val sharpness: Float = 0f
)

object PhotoEditorEngine {
    fun loadPreview(file: File, maxEdge: Int = 1200): Bitmap? = decodeSampled(file, maxEdge)

    fun renderPreview(source: Bitmap, params: PhotoEditParameters): Bitmap = apply(source, params, recycleInput = false)

    fun saveEdited(photo: PhotoItem, params: PhotoEditParameters): PhotoItem {
        val source = File(photo.path)
        require(source.exists() && source.length() > 0L) { "La fotografía original no existe" }
        val bitmap = decodeSampled(source, 4096) ?: error("No se pudo decodificar la fotografía")
        val edited = apply(bitmap, params, recycleInput = true)

        val originalsDir = File(source.parentFile, "originals").apply { mkdirs() }
        val originalPath = photo.originalPath.ifBlank {
            val backup = File(originalsDir, source.name)
            if (!backup.exists()) source.copyTo(backup, overwrite = false)
            backup.absolutePath
        }

        val temp = File(source.parentFile, ".edit_${source.name}")
        FileOutputStream(temp).use { out ->
            val format = if (source.extension.equals("png", true)) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (format == Bitmap.CompressFormat.JPEG) 96 else 100
            check(edited.compress(format, quality, out)) { "No se pudo guardar la edición" }
        }
        edited.recycle()
        require(temp.exists() && temp.length() > 0L) { "La edición resultó vacía" }
        if (source.exists() && !source.delete()) error("No se pudo reemplazar la fotografía")
        if (!temp.renameTo(source)) {
            temp.copyTo(source, overwrite = true)
            temp.delete()
        }
        return photo.copy(path = source.absolutePath, originalPath = originalPath)
    }

    fun restoreOriginal(photo: PhotoItem): PhotoItem {
        if (photo.originalPath.isBlank()) return photo
        val original = File(photo.originalPath)
        val current = File(photo.path)
        require(original.exists()) { "No se encontró la copia original" }
        original.copyTo(current, overwrite = true)
        runCatching { original.delete() }
        return photo.copy(originalPath = "")
    }

    private fun decodeSampled(file: File, maxEdge: Int): Bitmap? =
        ImageOrientationUtils.decodeOrientedBitmap(file, maxEdge)

    private fun apply(source: Bitmap, params: PhotoEditParameters, recycleInput: Boolean): Bitmap {
        var bitmap = source
        val turns = ((params.rotationQuarterTurns % 4) + 4) % 4
        if (turns != 0) {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(turns * 90f) }, true)
            if (recycleInput || bitmap !== source) bitmap.recycle()
            bitmap = rotated
        }

        val crop = params.cropPercent.coerceIn(50, 100) / 100f
        if (crop < .999f) {
            val w = max(1, (bitmap.width * crop).toInt())
            val h = max(1, (bitmap.height * crop).toInt())
            val left = max(0, (bitmap.width - w) / 2)
            val top = max(0, (bitmap.height - h) / 2)
            val cropped = Bitmap.createBitmap(bitmap, left, top, min(w, bitmap.width - left), min(h, bitmap.height - top))
            if (bitmap !== cropped && (recycleInput || bitmap !== source)) bitmap.recycle()
            bitmap = cropped
        }

        if (params.brightness != 0f || params.contrast != 1f) {
            val contrast = params.contrast.coerceIn(.5f, 1.8f)
            val brightness = params.brightness.coerceIn(-100f, 100f)
            val translate = 128f * (1f - contrast) + brightness
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            val adjusted = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Canvas(adjusted).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
            if (recycleInput || bitmap !== source) bitmap.recycle()
            bitmap = adjusted
        }

        if (params.sharpness > .01f) {
            val sharpened = sharpen(bitmap, params.sharpness.coerceIn(0f, 1.5f))
            if (recycleInput || bitmap !== source) bitmap.recycle()
            bitmap = sharpened
        }

        if (!recycleInput && bitmap === source) return source.copy(Bitmap.Config.ARGB_8888, false)
        return bitmap
    }

    private fun sharpen(source: Bitmap, amount: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3) return source.copy(Bitmap.Config.ARGB_8888, false)
        val input = IntArray(w * h)
        val output = IntArray(w * h)
        source.getPixels(input, 0, w, 0, 0, w, h)
        input.copyInto(output)
        val a = amount
        for (y in 1 until h - 1) {
            var idx = y * w + 1
            for (x in 1 until w - 1) {
                val c = input[idx]
                val l = input[idx - 1]
                val r = input[idx + 1]
                val u = input[idx - w]
                val d = input[idx + w]
                fun channel(shift: Int): Int {
                    val center = (c shr shift) and 0xff
                    val avg = (((l shr shift) and 0xff) + ((r shr shift) and 0xff) + ((u shr shift) and 0xff) + ((d shr shift) and 0xff)) / 4f
                    return (center + (center - avg) * a).toInt().coerceIn(0, 255)
                }
                output[idx] = (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                idx++
            }
        }
        return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }
}
