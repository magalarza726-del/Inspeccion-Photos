package ec.espol.inspeccionphotos.ui

import android.content.Context
import android.net.Uri
import ec.espol.inspeccionphotos.FolderViewMode
import java.io.File

enum class AddPhotoAction { ASK, CAMERA_DIRECT }
enum class EditorPromptMode { ASK_AFTER_CAPTURE, NEVER_ASK }
enum class CameraSessionMode { ONE_BY_ONE, BATCH }

class LegacyUiPreferences(private val context: Context) {
    var developerModeEnabled: Boolean
        get() = prefs.getBoolean("developer_mode_enabled", false)
        set(value) { prefs.edit().putBoolean("developer_mode_enabled", value).apply() }

    private val prefs = context.getSharedPreferences("inspeccion_photos_prefs", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) { prefs.edit().putBoolean("dark_mode", value).apply() }




    var spatialFunctionsEnabled: Boolean
        get() = prefs.getBoolean("spatial_functions_enabled", false)
        set(value) { prefs.edit().putBoolean("spatial_functions_enabled", value).apply() }

    var showOt: Boolean
        get() = prefs.getBoolean("show_ot", true)
        set(value) { prefs.edit().putBoolean("show_ot", value).apply() }

    var rootViewMode: FolderViewMode
        get() = runCatching { FolderViewMode.valueOf(prefs.getString("root_view_mode", FolderViewMode.LIST.name).orEmpty()) }
            .getOrDefault(FolderViewMode.LIST)
        set(value) { prefs.edit().putString("root_view_mode", value.name).apply() }

    var addPhotoAction: AddPhotoAction
        get() = runCatching { AddPhotoAction.valueOf(prefs.getString("add_photo_action", AddPhotoAction.ASK.name).orEmpty()) }
            .getOrDefault(AddPhotoAction.ASK)
        set(value) { prefs.edit().putString("add_photo_action", value.name).apply() }

    var editorPromptMode: EditorPromptMode
        get() = runCatching { EditorPromptMode.valueOf(prefs.getString("editor_prompt_mode", EditorPromptMode.ASK_AFTER_CAPTURE.name).orEmpty()) }
            .getOrDefault(EditorPromptMode.ASK_AFTER_CAPTURE)
        set(value) { prefs.edit().putString("editor_prompt_mode", value.name).apply() }

    var cameraSessionMode: CameraSessionMode
        get() = runCatching { CameraSessionMode.valueOf(prefs.getString("camera_session_mode", CameraSessionMode.ONE_BY_ONE.name).orEmpty()) }
            .getOrDefault(CameraSessionMode.ONE_BY_ONE)
        set(value) { prefs.edit().putString("camera_session_mode", value.name).apply() }


    var askPhotoConfirmation: Boolean
        get() = prefs.getBoolean("ask_photo_confirmation", false)
        set(value) { prefs.edit().putBoolean("ask_photo_confirmation", value).apply() }

    var hiddenLegacyPdfs: Set<Int>
        get() = (1..4).filter { prefs.getBoolean("hide_pdf_$it", false) }.toSet()
        set(value) {
            prefs.edit().apply {
                (1..4).forEach { putBoolean("hide_pdf_$it", it in value) }
            }.apply()
        }

    var googleDriveConnected: Boolean
        get() = prefs.getBoolean("google_drive_connected", false)
        set(value) { prefs.edit().putBoolean("google_drive_connected", value).apply() }

    var googleDriveConnectedAt: Long
        get() = prefs.getLong("google_drive_connected_at", 0L)
        set(value) { prefs.edit().putLong("google_drive_connected_at", value).apply() }

    var companyName: String
        get() = prefs.getString("company_name", "").orEmpty()
        set(value) { prefs.edit().putString("company_name", value.trim()).apply() }

    var exportSelection: ec.espol.inspeccionphotos.ExportSelection
        get() = ec.espol.inspeccionphotos.ExportSelection(
            photosAndStructure = prefs.getBoolean("export_photos_structure", true),
            standardReport = prefs.getBoolean("export_standard_report", true),
            standardPdfColumns = prefs.getInt("export_standard_pdf_columns", 3).coerceIn(1, 4),
            spatialReport = prefs.getBoolean("export_spatial_report", false),
            customReport = prefs.getBoolean("export_custom_report", false)
        )
        set(value) {
            prefs.edit()
                .putBoolean("export_photos_structure", value.photosAndStructure)
                .putBoolean("export_standard_report", value.standardReport)
                .putInt("export_standard_pdf_columns", value.standardPdfColumns.coerceIn(1, 4))
                .putBoolean("export_spatial_report", value.spatialReport)
                .putBoolean("export_custom_report", value.customReport)
                .apply()
        }

    var pdfLogoPath: String
        get() = prefs.getString("pdf_logo_path", "").orEmpty()
        private set(value) { prefs.edit().putString("pdf_logo_path", value).apply() }

    fun importPdfLogo(uri: Uri): String {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty().lowercase()
        val extension = when {
            "png" in mime -> "png"
            "webp" in mime -> "webp"
            else -> "jpg"
        }
        val dir = File(context.filesDir, "branding").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith("pdf_logo.") }?.forEach { it.delete() }
        val out = File(dir, "pdf_logo.$extension")
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().buffered(256 * 1024).use { output -> input.copyTo(output, 256 * 1024) }
        } ?: error("No se pudo leer la imagen seleccionada")
        require(out.exists() && out.length() > 0L) { "El logo seleccionado está vacío" }
        pdfLogoPath = out.absolutePath
        return out.absolutePath
    }

    fun clearPdfLogo() {
        pdfLogoPath.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        pdfLogoPath = ""
    }
}
