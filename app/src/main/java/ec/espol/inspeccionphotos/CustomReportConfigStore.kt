package ec.espol.inspeccionphotos

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CustomReportConfigStore(private val context: Context) {
    private val configFile = File(context.filesDir, "custom_report_config.json")
    private val imageDir = File(context.filesDir, "custom_report_branding").apply { mkdirs() }

    fun load(): CustomReportConfig = runCatching {
        if (!configFile.exists()) return CustomReportConfig()
        fromJson(JSONObject(configFile.readText()))
    }.getOrDefault(CustomReportConfig())

    /** Serialización reutilizable de varias plantillas dentro del mismo dispositivo. */
    fun encodeConfig(config: CustomReportConfig): JSONObject = toJson(config.normalized(), portable = false)
    fun decodeConfig(json: JSONObject): CustomReportConfig = fromJson(json).normalized()

    fun save(config: CustomReportConfig) {
        configFile.writeText(toJson(config.normalized(), portable = false).toString(2))
    }

    fun importHeaderImage(uri: Uri, side: String): String {
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val ext = when { "png" in mime -> "png"; "webp" in mime -> "webp"; else -> "jpg" }
        // Cada formato necesita sus imágenes independientes. No sobrescribir el logo
        // que utiliza otra plantilla fotográfica.
        val out = File(imageDir, "header_${side}_${java.util.UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it, 256 * 1024) } }
            ?: error("No se pudo leer la imagen")
        require(out.length() > 0) { "La imagen está vacía" }
        return out.absolutePath
    }

    /** JSON portátil del formato personalizado, incluidas las imágenes del encabezado en Base64. */
    fun toPortableJson(config: CustomReportConfig = load()): JSONObject =
        toJson(config.normalized(), portable = true)

    /** Restaura un formato personalizado desde su JSON portátil y lo deja guardado. */
    fun fromPortableJson(json: JSONObject): CustomReportConfig {
        val imported = fromJson(json)
        return imported.copy(
            leftHeader = restorePortableImage(json.optJSONObject("leftHeader"), imported.leftHeader, "left"),
            rightHeader = restorePortableImage(json.optJSONObject("rightHeader"), imported.rightHeader, "right")
        ).normalized()
    }

    fun importPortableJson(json: JSONObject): CustomReportConfig =
        fromPortableJson(json).also(::save)

    fun exportToUri(config: CustomReportConfig, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.writer(Charsets.UTF_8).use { it.write(toPortableJson(config).toString(2)) }
        } ?: error("No se pudo crear el JSON")
    }

    fun importFromUri(uri: Uri): CustomReportConfig {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo leer el JSON")
        return importPortableJson(JSONObject(text))
    }

    private fun restorePortableImage(json: JSONObject?, slot: ReportHeaderSlot, side: String): ReportHeaderSlot {
        if (json == null || slot.type != ReportSlotType.IMAGE) return slot
        val base64 = json.optString("imageBase64")
        if (base64.isBlank()) return slot
        return runCatching {
            val ext = json.optString("imageExtension", "jpg").replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "jpg" }
            val file = File(imageDir, "header_${side}_${java.util.UUID.randomUUID()}.$ext")
            file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
            slot.copy(imagePath = file.absolutePath)
        }.getOrDefault(slot)
    }


    private fun sectionCellToJson(cell: SectionCellDefinition): JSONObject = JSONObject().apply {
        put("id", cell.id); put("text", cell.text); put("mode", cell.mode.name)
    }
    private fun sectionLineToJson(line: SectionLineDefinition): JSONObject = JSONObject().apply {
        put("id", line.id); put("type", line.type.name); put("separators", JSONArray(line.separators)); put("cells", JSONArray(line.cells.map(::sectionCellToJson)))
    }
    private fun sectionToJson(section: SectionDefinition): JSONObject = JSONObject().apply {
        put("id", section.id); put("title", section.title); put("lines", JSONArray(section.lines.map(::sectionLineToJson)))
    }
    private fun signatureToJson(signature: SignatureDefinition): JSONObject = JSONObject().apply {
        put("id", signature.id); put("name", signature.name); put("alignment", signature.alignment.name)
    }
    private fun sectionCellFromJson(json: JSONObject): SectionCellDefinition = SectionCellDefinition(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        text = json.optString("text", "Campo"),
        mode = runCatching { SectionCellMode.valueOf(json.optString("mode", SectionCellMode.VALUE.name)) }.getOrDefault(SectionCellMode.VALUE)
    )
    private fun sectionLineFromJson(json: JSONObject): SectionLineDefinition {
        val type = runCatching { SectionLineType.valueOf(json.optString("type", SectionLineType.A.name)) }.getOrDefault(SectionLineType.A)
        val separators = json.optJSONArray("separators")?.let { arr -> List(arr.length()) { arr.optInt(it) } }.orEmpty()
        val cells = json.optJSONArray("cells")?.let { arr -> List(arr.length()) { sectionCellFromJson(arr.getJSONObject(it)) } }.orEmpty()
        return SectionLineDefinition(
            id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
            type = type,
            separators = separators.ifEmpty { SectionLineDefinition.defaultSeparators(type) },
            cells = cells.ifEmpty { SectionLineDefinition.defaultCells(type) }
        ).normalized()
    }
    private fun sectionFromJson(json: JSONObject): SectionDefinition = SectionDefinition(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        title = json.optString("title", "SECCIÓN"),
        lines = json.optJSONArray("lines")?.let { arr -> List(arr.length()) { sectionLineFromJson(arr.getJSONObject(it)) } }.orEmpty()
    ).normalized()
    private fun signatureFromJson(json: JSONObject): SignatureDefinition = SignatureDefinition(
        id = json.optString("id").ifBlank { java.util.UUID.randomUUID().toString() },
        name = json.optString("name", "NOMBRE"),
        alignment = runCatching { SignatureAlignment.valueOf(json.optString("alignment", SignatureAlignment.CENTER.name)) }.getOrDefault(SignatureAlignment.CENTER)
    ).normalized()

    private fun toJson(config: CustomReportConfig, portable: Boolean): JSONObject = JSONObject().apply {
        put("version", 3)
        put("title", config.title)
        put("subtitle", config.subtitle)
        put("leftHeader", slotToJson(config.leftHeader, portable))
        put("rightHeader", slotToJson(config.rightHeader, portable))
        put("exportFormats", JSONArray(config.exportFormats.map { it.name }))
        put("mainBlockLayout", config.mainBlockLayout.name)
        put("mainImageWidthPercent", config.mainImageWidthPercent)
        put("tableRows", config.tableRows)
        put("tableColumns", config.tableColumns)
        put("sectionPlacement", config.sectionPlacement.name)
        put("sections", JSONArray(config.sections.map(::sectionToJson)))
        put("signatures", JSONArray(config.signatures.map(::signatureToJson)))
        put("rowHeaders", JSONArray(config.rowHeaders))
        put("columnHeaders", JSONArray(config.columnHeaders))
        put("evidenceColumns", config.evidenceColumns)
    }

    private fun slotToJson(slot: ReportHeaderSlot, portable: Boolean): JSONObject = JSONObject().apply {
        put("type", slot.type.name)
        put("text", slot.text)
        put("imagePath", if (portable) "" else slot.imagePath)
        if (portable && slot.type == ReportSlotType.IMAGE && slot.imagePath.isNotBlank()) {
            val file = File(slot.imagePath)
            if (file.exists() && file.length() > 0) {
                put("imageBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                put("imageExtension", file.extension.ifBlank { "jpg" })
            }
        }
    }

    private fun fromJson(json: JSONObject): CustomReportConfig {
        fun slot(name: String): ReportHeaderSlot {
            val s = json.optJSONObject(name) ?: JSONObject()
            return ReportHeaderSlot(
                type = runCatching { ReportSlotType.valueOf(s.optString("type", "EMPTY")) }.getOrDefault(ReportSlotType.EMPTY),
                text = s.optString("text"),
                imagePath = s.optString("imagePath")
            )
        }
        fun stringList(name: String): List<String> {
            val arr = json.optJSONArray(name) ?: return emptyList()
            return List(arr.length()) { arr.optString(it) }
        }
        val formats = buildSet {
            val arr = json.optJSONArray("exportFormats") ?: JSONArray().put("PDF")
            for (i in 0 until arr.length()) runCatching { add(ReportExportFormat.valueOf(arr.optString(i))) }
        }
        return CustomReportConfig(
            title = json.optString("title", "REPORTE DE INSPECCIÓN"),
            subtitle = json.optString("subtitle", "Evidencia fotográfica"),
            leftHeader = slot("leftHeader"),
            rightHeader = slot("rightHeader"),
            exportFormats = formats,
            mainBlockLayout = runCatching { MainBlockLayout.valueOf(json.optString("mainBlockLayout", MainBlockLayout.IMAGE_LEFT_TABLE.name)) }.getOrDefault(MainBlockLayout.IMAGE_LEFT_TABLE),
            mainImageWidthPercent = json.optInt("mainImageWidthPercent", 55),
            tableRows = json.optInt("tableRows", 4),
            tableColumns = json.optInt("tableColumns", 3),
            sectionPlacement = runCatching { SectionPlacement.valueOf(json.optString("sectionPlacement", SectionPlacement.BELOW_MAIN_BLOCK.name)) }.getOrDefault(SectionPlacement.BELOW_MAIN_BLOCK),
            sections = json.optJSONArray("sections")?.let { arr -> List(arr.length()) { sectionFromJson(arr.getJSONObject(it)) } }.orEmpty(),
            signatures = json.optJSONArray("signatures")?.let { arr -> List(arr.length()) { signatureFromJson(arr.getJSONObject(it)) } }.orEmpty(),
            rowHeaders = stringList("rowHeaders"),
            columnHeaders = stringList("columnHeaders"),
            evidenceColumns = json.optInt("evidenceColumns", 3)
        ).normalized()
    }
}
