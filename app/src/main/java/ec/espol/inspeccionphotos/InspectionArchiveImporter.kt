package ec.espol.inspeccionphotos

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Importa los ZIP creados por Fotos + Carpetas y reconstruye una copia independiente
 * del árbol con sus fotografías y datos asociados.
 */
class InspectionArchiveImporter(private val context: Context) {
    private val mediaStore = InspectionMediaStore(context)

    fun importArchive(uri: Uri): Inspection {
        val tempRoot = File(context.cacheDir, "inspection_zip_import_${System.currentTimeMillis()}")
        tempRoot.mkdirs()
        try {
            extractSafely(uri, tempRoot)
            val manifestFile = File(tempRoot, "estructura/estructura_carpetas.json")
            require(manifestFile.exists()) {
                "El ZIP no contiene estructura/estructura_carpetas.json. Exporta Fotos + Carpetas desde Inspeccion Photos."
            }
            val manifest = JSONObject(manifestFile.readText())
            require(manifest.optString("format") == "inspeccion-photos-folder-structure") {
                "El ZIP no corresponde a un proyecto compatible de Inspeccion Photos."
            }
            val rootJson = manifest.optJSONObject("root") ?: error("El manifiesto no contiene una carpeta raíz")
            val rootZipPath = "fotos/${sanitizePathSegment(rootJson.optString("name").ifBlank { "Proyecto" })}"
            return decodeNode(rootJson, File(tempRoot, rootZipPath))
        } finally {
            runCatching { tempRoot.deleteRecursively() }
        }
    }

    private fun extractSafely(uri: Uri, destination: File) {
        val rootCanonical = destination.canonicalFile
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered(256 * 1024)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val out = File(destination, entry.name).canonicalFile
                    require(out.path == rootCanonical.path || out.path.startsWith(rootCanonical.path + File.separator)) {
                        "El ZIP contiene una ruta no válida"
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered(256 * 1024).use { output -> zip.copyTo(output, 256 * 1024) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("No se pudo abrir el ZIP seleccionado")
    }

    private fun decodeNode(json: JSONObject, photoDirectory: File): Inspection {
        val newId = UUID.randomUUID().toString()
        val photosJson = json.optJSONArray("photos") ?: JSONArray()
        val photos = buildList {
            val objects = (0 until photosJson.length())
                .mapNotNull { photosJson.optJSONObject(it) }
                .sortedBy { it.optInt("order", 0) }
            objects.forEachIndexed { fallbackIndex, photoJson ->
                val order = photoJson.optInt("order", fallbackIndex)
                val prefix = "%04d_".format(order + 1)
                val source = photoDirectory.listFiles()?.firstOrNull { it.isFile && it.name.startsWith(prefix) }
                    ?: return@forEachIndexed
                val displayName = photoJson.optString("name").ifBlank { source.name.removePrefix(prefix) }
                val destination = uniqueDestination(mediaStore.photosDir(newId), displayName, source.extension.ifBlank { "jpg" })
                source.copyTo(destination, overwrite = true)
                add(
                    PhotoItem(
                        name = displayName,
                        path = destination.absolutePath,
                        observation = photoJson.optString("observation")
                    )
                )
            }
        }

        val mainImageSource = File(photoDirectory, "_reporte")
            .listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith("imagen_principal") }
        val mainImagePath = mainImageSource?.let {
            runCatching { mediaStore.copyReportMainImageFromFile(newId, it).absolutePath }.getOrDefault("")
        }.orEmpty()

        val childArray = json.optJSONArray("children") ?: JSONArray()
        val children = (0 until childArray.length())
            .mapNotNull { childArray.optJSONObject(it) }
            .sortedBy { it.optInt("order", 0) }
            .mapIndexed { fallbackIndex, childJson ->
                val order = childJson.optInt("order", fallbackIndex)
                val childDirName = "%04d_%s".format(order + 1, sanitizePathSegment(childJson.optString("name")))
                decodeNode(childJson, File(photoDirectory, childDirName))
            }

        return Inspection(
            id = newId,
            name = json.optString("name").ifBlank { "Carpeta importada" },
            created = json.optString("created").ifBlank { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) },
            ot = json.optString("ot"),
            generalObs = json.optString("generalObs"),
            photos = photos,
            children = children,
            allowPhotos = json.optBoolean("allowPhotos", true),
            allowSubfolders = json.optBoolean("allowSubfolders", true),
            viewMode = runCatching { FolderViewMode.valueOf(json.optString("viewMode", FolderViewMode.GALLERY.name)) }
                .getOrDefault(FolderViewMode.GALLERY),
            collapsed = json.optBoolean("collapsed", false),
            reportMainImagePath = mainImagePath,
            reportTableEnabled = json.optBoolean("reportTableEnabled", false),
            reportTableValues = decodeTable(json.optJSONArray("reportTableValues")),
            reportSectionValues = json.optJSONObject("reportSectionValues")?.let { values ->
                values.keys().asSequence().associateWith { values.optString(it) }
            }.orEmpty()
        )
    }

    private fun decodeTable(array: JSONArray?): List<List<String>> {
        if (array == null) return emptyList()
        return List(array.length()) { rowIndex ->
            val row = array.optJSONArray(rowIndex) ?: JSONArray()
            List(row.length()) { columnIndex -> row.optString(columnIndex) }
        }
    }

    private fun uniqueDestination(directory: File, requestedName: String, fallbackExtension: String): File {
        directory.mkdirs()
        val safe = sanitizeFileName(requestedName).ifBlank { "foto.$fallbackExtension" }
        var candidate = File(directory, safe)
        if (!candidate.exists()) return candidate
        val base = candidate.nameWithoutExtension
        val ext = candidate.extension
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, if (ext.isBlank()) "${base}_$index" else "${base}_$index.$ext")
            index++
        }
        return candidate
    }

    private fun sanitizePathSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private fun sanitizeFileName(value: String): String = sanitizePathSegment(value)
}
