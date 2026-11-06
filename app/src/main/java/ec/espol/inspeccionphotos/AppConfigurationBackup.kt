package ec.espol.inspeccionphotos

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Respaldo portátil de la configuración de la app.
 *
 * Incluye preferencias, branding, formato personalizado y plantillas de formularios.
 * No incluye proyectos/fotos, formularios rellenados ni sesiones/tokens de autenticación.
 */
class AppConfigurationBackup(private val context: Context) {
    companion object {
        private const val SCHEMA = "inspeccion-photos.app-configuration"
        private const val VERSION = 1
        private const val LEGACY_PREFS = "inspeccion_photos_prefs"
        private const val SPATIAL_PREFS = "inspeccion_photos_settings_v2"

        // Son estados locales/transitorios, no una autorización portable.
        private val EXCLUDED_LEGACY_KEYS = setOf(
            "pdf_logo_path",
            "google_drive_connected",
            "google_drive_connected_at"
        )
    }

    data class ImportResult(
        val formTemplateCount: Int,
        val pdfLogoRestored: Boolean
    )

    private val reportStore = CustomReportConfigStore(context)
    private val formStore = FormTemplateStore(context)

    fun exportToUri(uri: Uri) {
        val root = JSONObject().apply {
            put("schema", SCHEMA)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put(LEGACY_PREFS, preferencesToJson(LEGACY_PREFS, EXCLUDED_LEGACY_KEYS))
                put(SPATIAL_PREFS, preferencesToJson(SPATIAL_PREFS, emptySet()))
            })
            put("pdfLogo", pdfLogoToJson())
            put("customReport", reportStore.toPortableJson())
            put("formTemplates", formStore.toPortableTemplatesJson())
            put("notes", JSONObject().apply {
                put("projectsIncluded", false)
                put("filledFormsIncluded", false)
                put("authenticationIncluded", false)
            })
        }

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.writer(Charsets.UTF_8).use { writer -> writer.write(root.toString(2)) }
        } ?: error("No se pudo crear el archivo de configuración")
    }

    fun importFromUri(uri: Uri): ImportResult {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("No se pudo leer el archivo de configuración")
        val root = JSONObject(text)
        require(root.optString("schema") == SCHEMA) { "El archivo no es una configuración total de Inspeccion Photos" }
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) { "Versión de configuración no compatible: $version" }

        val preferences = root.optJSONObject("preferences")
            ?: error("El archivo no contiene las preferencias de la app")
        val legacyJson = preferences.optJSONObject(LEGACY_PREFS)
            ?: error("Falta la configuración principal")
        val spatialJson = preferences.optJSONObject(SPATIAL_PREFS) ?: JSONObject()
        val customReportJson = root.optJSONObject("customReport")
            ?: error("Falta el formato personalizado")
        val templatesJson = root.optJSONArray("formTemplates") ?: JSONArray()

        // Valida/decodifica el logo antes de modificar preferencias para reducir importaciones parciales.
        val logo = root.optJSONObject("pdfLogo") ?: JSONObject()
        val logoBytes = if (logo.optBoolean("present", false)) {
            val base64 = logo.optString("base64")
            require(base64.isNotBlank()) { "El respaldo indica un logo, pero no contiene sus datos" }
            Base64.decode(base64, Base64.DEFAULT)
        } else null

        restorePreferences(LEGACY_PREFS, legacyJson, EXCLUDED_LEGACY_KEYS)
        restorePreferences(SPATIAL_PREFS, spatialJson, emptySet())

        // Un estado "conectado" de Drive no equivale a una sesión OAuth válida en otro dispositivo.
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("google_drive_connected", false)
            .putLong("google_drive_connected_at", 0L)
            .apply()

        val logoRestored = restorePdfLogo(logo, logoBytes)
        reportStore.importPortableJson(customReportJson)
        val importedTemplates = formStore.importPortableTemplatesJson(templatesJson, preserveIds = true)
        formStore.saveTemplates(importedTemplates)

        return ImportResult(
            formTemplateCount = importedTemplates.size,
            pdfLogoRestored = logoRestored
        )
    }

    private fun preferencesToJson(name: String, excluded: Set<String>): JSONObject {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        return JSONObject().apply {
            prefs.all.toSortedMap().forEach { (key, value) ->
                if (key !in excluded) put(key, typedPreferenceToJson(value))
            }
        }
    }

    private fun typedPreferenceToJson(value: Any?): JSONObject = JSONObject().apply {
        when (value) {
            is String -> { put("type", "string"); put("value", value) }
            is Boolean -> { put("type", "boolean"); put("value", value) }
            is Int -> { put("type", "int"); put("value", value) }
            is Long -> { put("type", "long"); put("value", value) }
            is Float -> { put("type", "float"); put("value", value.toDouble()) }
            is Set<*> -> {
                put("type", "stringSet")
                put("value", JSONArray(value.filterIsInstance<String>().sorted()))
            }
            null -> { put("type", "null") }
            else -> error("Tipo de preferencia no soportado: ${value::class.java.simpleName}")
        }
    }

    private fun restorePreferences(name: String, json: JSONObject, excluded: Set<String>) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in excluded) continue
            val entry = json.optJSONObject(key) ?: continue
            when (entry.optString("type")) {
                "string" -> editor.putString(key, entry.optString("value"))
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "int" -> editor.putInt(key, entry.optInt("value"))
                "long" -> editor.putLong(key, entry.optLong("value"))
                "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                "stringSet" -> {
                    val array = entry.optJSONArray("value") ?: JSONArray()
                    editor.putStringSet(key, buildSet {
                        for (index in 0 until array.length()) add(array.optString(index))
                    })
                }
                "null" -> Unit
            }
        }
        editor.apply()
    }

    private fun pdfLogoToJson(): JSONObject {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val path = prefs.getString("pdf_logo_path", "").orEmpty()
        val file = path.takeIf { it.isNotBlank() }?.let(::File)
        val valid = file?.exists() == true && file.length() > 0L
        return JSONObject().apply {
            put("present", valid)
            if (valid && file != null) {
                put("extension", file.extension.ifBlank { "jpg" })
                put("base64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }
    }

    private fun restorePdfLogo(json: JSONObject, bytes: ByteArray?): Boolean {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val brandingDir = File(context.filesDir, "branding").apply { mkdirs() }
        brandingDir.listFiles()?.filter { it.name.startsWith("pdf_logo.") }?.forEach { it.delete() }

        if (bytes == null || !json.optBoolean("present", false)) {
            prefs.edit().putString("pdf_logo_path", "").apply()
            return false
        }

        val extension = json.optString("extension", "jpg")
            .replace(Regex("[^A-Za-z0-9]"), "")
            .ifBlank { "jpg" }
        val output = File(brandingDir, "pdf_logo.$extension")
        output.writeBytes(bytes)
        require(output.length() > 0L) { "El logo restaurado está vacío" }
        prefs.edit().putString("pdf_logo_path", output.absolutePath).apply()
        return true
    }
}
