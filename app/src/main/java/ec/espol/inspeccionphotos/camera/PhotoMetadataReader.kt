package ec.espol.inspeccionphotos.camera

import android.media.ExifInterface
import ec.espol.inspeccionphotos.survey.CameraMetadata
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.atan

object PhotoMetadataReader {
    fun read(file: File): CameraMetadata? = runCatching {
        val exif = ExifInterface(file.absolutePath)
        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
        val focal = parseRational(exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH))
        val focal35 = exif.getAttribute("FocalLengthIn35mmFilm")?.toDoubleOrNull()?.takeIf { it > 0 }
        val hFov = focal35?.let { Math.toDegrees(2.0 * atan(36.0 / (2.0 * it))) }
        val vFov = focal35?.let { Math.toDegrees(2.0 * atan(24.0 / (2.0 * it))) }
        CameraMetadata(
            widthPx = width,
            heightPx = height,
            deviceMake = exif.getAttribute(ExifInterface.TAG_MAKE),
            deviceModel = exif.getAttribute(ExifInterface.TAG_MODEL),
            lensModel = exif.getAttribute("LensModel"),
            focalLengthMm = focal,
            focalLength35Mm = focal35,
            horizontalFovDegrees = hFov,
            verticalFovDegrees = vFov,
            exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
            exifTimestampEpochMs = parseExifTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)),
            zoom = null
        )
    }.getOrNull()

    private fun parseRational(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.toDoubleOrNull() ?: run {
            val pieces = raw.split('/')
            if (pieces.size != 2) null
            else {
                val n = pieces[0].toDoubleOrNull()
                val d = pieces[1].toDoubleOrNull()
                if (n != null && d != null && d != 0.0) n / d else null
            }
        }
    }

    private fun parseExifTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time
        }.getOrNull()
    }
}
