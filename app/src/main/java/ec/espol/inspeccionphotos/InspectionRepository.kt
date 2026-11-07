package ec.espol.inspeccionphotos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Persistencia offline-first del árbol clásico de carpetas/fotos. */
class InspectionRepository(private val context: Context) {
    private val dataFile = File(context.filesDir, "respaldo_datos.json")

    fun load(): List<Inspection> = runCatching {
        if (!dataFile.exists()) return emptyList()
        decode(dataFile.readText())
    }.getOrElse {
        runCatching {
            dataFile.copyTo(File(context.filesDir, "respaldo_datos_corrupto_${System.currentTimeMillis()}.json"), overwrite = false)
        }
        emptyList()
    }

    fun save(inspections: List<Inspection>) {
        val temp = File(context.filesDir, "respaldo_datos.tmp")
        temp.writeText(encode(inspections))
        if (dataFile.exists()) runCatching { dataFile.copyTo(File(context.filesDir, "respaldo_datos.bak"), overwrite = true) }
        if (!temp.renameTo(dataFile)) {
            temp.copyTo(dataFile, overwrite = true)
            temp.delete()
        }
    }

    fun encodeTrashFolder(folder: Inspection): String = encode(listOf(folder))
    fun decodeTrashFolder(payload: String): Inspection? = decode(payload).firstOrNull()

    fun backupFile(inspections: List<Inspection>): File {
        val out = File(context.cacheDir, "respaldo_datos.json")
        out.writeText(encode(inspections))
        return out
    }

    fun encode(inspections: List<Inspection>): String = JSONObject().apply {
        put("format", "inspeccion-photos")
        put("version", 5)
        put("maxSubfolderLevels", InspectionTree.MAX_SUBFOLDER_LEVELS)
        put("inspections", JSONArray().apply { inspections.forEach { put(folderToJson(it)) } })
    }.toString(2)

    private fun folderToJson(folder: Inspection): JSONObject = JSONObject().apply {
        put("id", folder.id)
        put("name", folder.name)
        put("created", folder.created)
        put("ot", folder.ot)
        put("generalObs", folder.generalObs)
        put("allowPhotos", folder.allowPhotos)
        put("allowSubfolders", folder.allowSubfolders)
        put("viewMode", folder.viewMode.name)
        put("collapsed", folder.collapsed)
        put("reportMainImagePath", folder.reportMainImagePath)
        put("reportTableEnabled", folder.reportTableEnabled)
        put("reportTableValues", JSONArray().apply { folder.reportTableValues.forEach { row -> put(JSONArray(row)) } })
        put("reportSectionValues", JSONObject(folder.reportSectionValues))
        put("photos", JSONArray().apply { folder.photos.forEach { put(photoToJson(it)) } })
        put("children", JSONArray().apply { folder.children.forEach { put(folderToJson(it)) } })
    }

    private fun photoToJson(photo: PhotoItem): JSONObject = JSONObject()
        .put("name", photo.name)
        .put("path", photo.path)
        .put("observation", photo.observation)
        .put("originalPath", photo.originalPath)

    private fun decode(text: String): List<Inspection> {
        val trimmed = text.trim()
        val arr = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("inspections") ?: JSONArray()
        }
        return buildList {
            for (index in 0 until arr.length()) {
                val json = arr.optJSONObject(index) ?: continue
                add(decodeFolder(json, parentCreated = json.optString("created"), legacyIndex = index, root = true))
            }
        }
    }

    private fun decodeFolder(json: JSONObject, parentCreated: String, legacyIndex: Int, root: Boolean): Inspection {
        val name = json.optString("name")
        val created = json.optString("created").ifBlank { parentCreated }
        val id = json.optString("id").ifBlank { stableLegacyId(name, created, legacyIndex) }
        val ownPhotos = decodePhotos(json.optJSONArray("photos"))
        val childrenJson = json.optJSONArray("children")
        val legacyCodes = json.optJSONArray("codes")

        val children = when {
            childrenJson != null -> buildList {
                for (i in 0 until childrenJson.length()) {
                    val child = childrenJson.optJSONObject(i) ?: continue
                    add(decodeFolder(child, created, i, root = false))
                }
            }
            legacyCodes != null -> buildList {
                for (i in 0 until legacyCodes.length()) {
                    val code = legacyCodes.optJSONObject(i) ?: continue
                    val codeName = code.optString("name")
                    add(
                        Inspection(
                            id = stableLegacyId("$id/$codeName", created, i),
                            name = codeName,
                            created = created,
                            generalObs = code.optString("generalObs"),
                            photos = decodePhotos(code.optJSONArray("photos")),
                            allowPhotos = true,
                            allowSubfolders = true,
                            viewMode = FolderViewMode.GALLERY
                        )
                    )
                }
            }
            else -> emptyList()
        }

        val migratedFromCodes = legacyCodes != null && childrenJson == null
        return Inspection(
            id = id,
            name = name,
            created = created,
            ot = json.optString("ot"),
            codes = emptyList(),
            generalObs = json.optString("generalObs"),
            photos = ownPhotos,
            children = children,
            allowPhotos = if (json.has("allowPhotos")) json.optBoolean("allowPhotos", true) else !migratedFromCodes,
            allowSubfolders = json.optBoolean("allowSubfolders", true),
            viewMode = runCatching { FolderViewMode.valueOf(json.optString("viewMode", "GALLERY")) }.getOrDefault(FolderViewMode.GALLERY),
            collapsed = json.optBoolean("collapsed", false),
            reportMainImagePath = json.optString("reportMainImagePath"),
            reportTableEnabled = if (json.has("reportTableEnabled")) json.optBoolean("reportTableEnabled", false) else {
                val old = decodeTable(json.optJSONArray("reportTableValues")); old.isNotEmpty()
            },
            reportTableValues = decodeTable(json.optJSONArray("reportTableValues")),
            reportSectionValues = json.optJSONObject("reportSectionValues")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.optString(it) }
            }.orEmpty()
        )
    }

    private fun decodePhotos(arr: JSONArray?): List<PhotoItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                add(
                    PhotoItem(
                        name = p.optString("name"),
                        path = p.optString("path"),
                        observation = p.optString("observation"),
                        originalPath = p.optString("originalPath")
                    )
                )
            }
        }
    }

    private fun decodeTable(arr: JSONArray?): List<List<String>> {
        if (arr == null) return emptyList()
        return List(arr.length()) { r ->
            val row = arr.optJSONArray(r) ?: JSONArray()
            List(row.length()) { c -> row.optString(c) }
        }
    }

    private fun stableLegacyId(name: String, created: String, index: Int): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest("$name|$created|$index".toByteArray())
        return bytes.take(10).joinToString("") { "%02x".format(it) }
    }
}
