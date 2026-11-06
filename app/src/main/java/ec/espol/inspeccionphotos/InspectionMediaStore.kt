package ec.espol.inspeccionphotos

import android.content.Context
import android.net.Uri
import java.io.File

class InspectionMediaStore(private val context: Context) {
    private val root = File(context.filesDir, "legacy_inspections").apply { mkdirs() }

    fun inspectionDir(inspectionId: String): File = File(root, sanitize(inspectionId)).apply { mkdirs() }

    fun photosDir(inspectionId: String): File = File(inspectionDir(inspectionId), "photos").apply { mkdirs() }

    fun createCaptureFile(inspectionId: String, desiredName: String): File {
        val file = File(photosDir(inspectionId), sanitizeFileName(desiredName))
        require(file.parentFile?.let { it.exists() || it.mkdirs() } != false) { "No se pudo preparar la carpeta de fotografías" }
        if (file.exists() && !file.delete()) error("No se pudo reemplazar el archivo temporal de cámara")
        require(file.createNewFile()) { "No se pudo crear el archivo de captura" }
        return file
    }

    fun copyPickedImage(inspectionId: String, desiredName: String, uri: Uri): File {
        val out = File(photosDir(inspectionId), sanitizeFileName(desiredName))
        if (out.exists() && !out.delete()) error("No se pudo preparar el archivo de destino")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "No se pudo abrir la imagen seleccionada" }
                out.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            require(out.exists() && out.length() > 0L) { "La imagen seleccionada está vacía o no pudo copiarse" }
            return out
        } catch (t: Throwable) {
            runCatching { out.delete() }
            throw t
        }
    }

    fun createReportMainCaptureFile(inspectionId: String): File {
        val dir = File(inspectionDir(inspectionId), "report").apply { mkdirs() }
        val out = File(dir, "main_image_camera.jpg")
        if (out.exists() && !out.delete()) error("No se pudo reemplazar la imagen principal temporal")
        require(out.createNewFile()) { "No se pudo preparar la captura de imagen principal" }
        return out
    }

    fun copyReportMainImageFromFile(inspectionId: String, source: File): File {
        require(source.exists() && source.length() > 0L) { "La imagen seleccionada no existe" }
        val dir = File(inspectionDir(inspectionId), "report").apply { mkdirs() }
        val ext = source.extension.ifBlank { "jpg" }
        val out = File(dir, "main_image.$ext")
        source.copyTo(out, overwrite = true)
        return out
    }

    fun copyReportMainImage(inspectionId: String, uri: Uri): File {
        val dir = File(inspectionDir(inspectionId), "report").apply { mkdirs() }
        val out = File(dir, "main_image.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir la imagen seleccionada" }
            out.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        require(out.exists() && out.length() > 0L) { "La imagen principal está vacía" }
        return out
    }

    fun renamePhoto(photo: PhotoItem, desiredName: String): PhotoItem {
        val source = File(photo.path)
        val ext = source.extension.ifBlank { "jpg" }
        val normalized = desiredName.trim().ifBlank { source.nameWithoutExtension }
        val fileName = if (normalized.contains('.')) normalized else "$normalized.$ext"
        val destination = File(source.parentFile ?: photosDir("misc"), sanitizeFileName(fileName))
        if (source.absolutePath != destination.absolutePath) {
            if (destination.exists()) destination.delete()
            if (!source.renameTo(destination)) source.copyTo(destination, overwrite = true).also { source.delete() }
        }
        return photo.copy(name = destination.name, path = destination.absolutePath)
    }

    fun deletePhoto(photo: PhotoItem) {
        runCatching { File(photo.path).delete() }
        if (photo.originalPath.isNotBlank()) runCatching { File(photo.originalPath).delete() }
    }

    fun deleteInspectionFiles(inspectionId: String) {
        runCatching { inspectionDir(inspectionId).deleteRecursively() }
    }

    fun nextPhotoName(codeName: String, photos: List<PhotoItem>): String {
        val prefix = sanitize(codeName).ifBlank { "FOTO" }
        val used = photos.mapNotNull { photo ->
            Regex("^${Regex.escape(prefix)}_(\\d+)", RegexOption.IGNORE_CASE)
                .find(photo.nameWithoutExtension())
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.toSet()
        var n = 1
        while (n in used) n++
        return "${prefix}_${n.toString().padStart(2, '0')}.jpg"
    }

    private fun PhotoItem.nameWithoutExtension(): String = File(name).nameWithoutExtension

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun sanitizeFileName(value: String): String = sanitize(value).ifBlank { "foto.jpg" }
}
