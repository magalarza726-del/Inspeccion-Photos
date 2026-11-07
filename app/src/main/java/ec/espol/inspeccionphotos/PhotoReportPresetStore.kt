package ec.espol.inspeccionphotos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PhotoReportPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Reporte fotográfico",
    val config: CustomReportConfig = CustomReportConfig(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

class PhotoReportPresetStore(context: Context, private val configStore: CustomReportConfigStore) {
    private val file = File(context.filesDir, "photo_report_presets.json")

    private fun toJson(value: PhotoReportPreset): JSONObject = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("createdAt", value.createdAt)
        put("updatedAt", value.updatedAt)
        put("config", configStore.encodeConfig(value.config))
    }

    private fun fromJson(value: JSONObject): PhotoReportPreset = PhotoReportPreset(
        id = value.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = value.optString("name").ifBlank { "Reporte fotográfico" },
        config = configStore.decodeConfig(value.optJSONObject("config") ?: JSONObject()),
        createdAt = value.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = value.optLong("updatedAt", System.currentTimeMillis())
    )

    fun newPreset(name: String, config: CustomReportConfig = CustomReportConfig()): PhotoReportPreset =
        PhotoReportPreset(name = name.trim().take(70).ifBlank { "Reporte fotográfico" }, config = config.normalized())

    fun load(): List<PhotoReportPreset> = runCatching {
        if (!file.exists()) {
            // Migrar la única plantilla antigua sin perderla.
            listOf(newPreset("Reporte fotográfico 1", configStore.load()))
        } else {
            val array = JSONArray(file.readText())
            List(array.length()) { index -> fromJson(array.getJSONObject(index)) }
        }
    }.getOrElse { listOf(newPreset("Reporte fotográfico 1", configStore.load())) }

    fun save(entries: List<PhotoReportPreset>) {
        val tmp = File(file.parentFile, "photo_report_presets.tmp")
        tmp.writeText(JSONArray(entries.map(::toJson)).toString(2))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun encodePreset(preset: PhotoReportPreset): String = toJson(preset).toString()
    fun decodePreset(payload: String): PhotoReportPreset = fromJson(JSONObject(payload))
}
