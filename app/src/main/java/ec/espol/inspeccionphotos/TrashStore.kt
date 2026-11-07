package ec.espol.inspeccionphotos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class TrashType { PHOTO, FOLDER, FORM, TEMPLATE, PHOTO_TEMPLATE }

data class TrashEntry(
    val id: String,
    val type: TrashType,
    val name: String,
    val payload: String,
    val parentId: String? = null,
    val deletedAt: Long = System.currentTimeMillis()
)

/** Papelera offline: los archivos originales se conservan hasta el borrado definitivo. */
class TrashStore(context: Context) {
    private val dataFile = File(context.filesDir, "inspeccion_trash.json")

    fun load(): List<TrashEntry> = runCatching {
        if (!dataFile.exists()) return emptyList()
        val array = JSONArray(dataFile.readText())
        List(array.length()) { i ->
            val e = array.getJSONObject(i)
            TrashEntry(
                id = e.getString("id"),
                type = TrashType.valueOf(e.getString("type")),
                name = e.optString("name"),
                payload = e.getString("payload"),
                parentId = e.optString("parentId").takeIf { it.isNotBlank() },
                deletedAt = e.optLong("deletedAt")
            )
        }
    }.getOrDefault(emptyList())

    private fun save(entries: List<TrashEntry>) {
        val array = JSONArray(entries.map { e ->
            JSONObject().apply {
                put("id", e.id)
                put("type", e.type.name)
                put("name", e.name)
                put("payload", e.payload)
                put("parentId", e.parentId ?: "")
                put("deletedAt", e.deletedAt)
            }
        })
        val temp = File(dataFile.parentFile, "inspeccion_trash.tmp")
        temp.writeText(array.toString(2))
        if (!temp.renameTo(dataFile)) {
            temp.copyTo(dataFile, overwrite = true)
            temp.delete()
        }
    }

    fun put(type: TrashType, name: String, payload: String, parentId: String? = null): TrashEntry {
        val entry = TrashEntry(UUID.randomUUID().toString(), type, name, payload, parentId)
        save(load() + entry)
        return entry
    }

    fun remove(ids: Set<String>) { save(load().filterNot { it.id in ids }) }
}
