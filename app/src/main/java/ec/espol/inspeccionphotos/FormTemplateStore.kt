package ec.espol.inspeccionphotos

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class FormTemplateStore(private val context: Context) {
    private val templatesFile = File(context.filesDir, "form_templates.json")
    private val submissionsFile = File(context.filesDir, "form_submissions.json")
    private val imageDir = File(context.filesDir, "form_template_images").apply { mkdirs() }

    fun loadTemplates(): List<FormTemplate> = runCatching {
        if (!templatesFile.exists()) return emptyList()
        val array = JSONArray(templatesFile.readText())
        List(array.length()) { templateFromJson(array.getJSONObject(it)) }.map { it.normalized() }
    }.getOrDefault(emptyList())

    fun saveTemplates(templates: List<FormTemplate>) {
        val normalizedTemplates = templates.map { it.normalized() }
        templatesFile.writeText(JSONArray(normalizedTemplates.map { templateToJson(it) }).toString(2))
        // Los formularios ya llenados conservan sus datos, pero adoptan automáticamente
        // los cambios compatibles del formato (exportación, desplegables, encabezados,
        // visibilidad, secciones, firmas, reordenamiento/agregado de columnas, etc.).
        reconcileSavedSubmissions(normalizedTemplates)
    }

    fun reconcileSavedSubmissions(templates: List<FormTemplate> = loadTemplates()): List<FormSubmission> {
        val byId = templates.associateBy { it.id }
        val current = loadSubmissions()
        if (current.isEmpty()) return current
        var changed = false
        val reconciled = current.map { submission ->
            val latest = byId[submission.template.id] ?: return@map submission
            val migrated = reconcileSubmission(submission, latest)
            if (migrated != submission) changed = true
            migrated
        }
        if (changed) saveSubmissions(reconciled)
        return reconciled
    }

    private fun reconcileSubmission(submission: FormSubmission, latestTemplate: FormTemplate): FormSubmission {
        val oldTemplate = submission.template.normalized()
        val latest = latestTemplate.normalized()
        if (oldTemplate == latest) return submission.normalized()

        val oldIndexByColumnId = oldTemplate.columns.mapIndexed { index, column -> column.id to index }.toMap()
        val migratedRows = submission.cells.map { row ->
            latest.columns.map { newColumn ->
                val oldIndex = oldIndexByColumnId[newColumn.id]
                if (oldIndex == null) "" else row.getOrNull(oldIndex).orEmpty()
            }
        }.ifEmpty { List(latest.initialRows.coerceAtLeast(1)) { List(latest.columns.size) { "" } } }

        val validSectionCellIds = latest.sections
            .flatMap { it.lines }
            .flatMap { it.cells }
            .map { it.id }
            .toSet()
        val migratedSectionValues = submission.sectionValues.filterKeys { it in validSectionCellIds }

        return submission.copy(
            template = latest,
            cells = migratedRows,
            sectionValues = migratedSectionValues
        ).normalized()
    }

    fun importHeaderImage(templateId: String, side: String, uri: Uri): String {
        val safeId = templateId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeSide = if (side == "right") "right" else "left"
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val ext = when {
            "png" in mime -> "png"
            "webp" in mime -> "webp"
            else -> "jpg"
        }
        imageDir.listFiles()?.filter { it.name.startsWith("${safeId}_${safeSide}.") }?.forEach { it.delete() }
        val output = File(imageDir, "${safeId}_${safeSide}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { out -> input.copyTo(out, 256 * 1024) }
        } ?: error("No se pudo leer la imagen")
        require(output.length() > 0L) { "La imagen está vacía" }
        return output.absolutePath
    }

    /**
     * Exporta uno o varios formatos de formulario como JSON portátil.
     * Las imágenes del encabezado se incrustan en Base64 para que el archivo se pueda mover a otro equipo.
     */
    /** Devuelve los formatos como un array JSON portátil, con imágenes incrustadas. */
    fun toPortableTemplatesJson(templates: List<FormTemplate> = loadTemplates()): JSONArray =
        JSONArray(templates.map { templateToPortableJson(it.normalized()) })

    /**
     * Restaura formatos desde un array portátil. Cuando preserveIds=true se conservan los IDs
     * para que los formularios ya llenados puedan adoptar los cambios compatibles del formato.
     */
    fun importPortableTemplatesJson(array: JSONArray, preserveIds: Boolean): List<FormTemplate> =
        List(array.length()) { index -> portableTemplateFromJson(array.getJSONObject(index), preserveIds) }

    fun exportTemplatesToUri(templates: List<FormTemplate>, uri: Uri) {
        require(templates.isNotEmpty()) { "No hay formatos para exportar" }
        val root = JSONObject().apply {
            put("schema", "inspeccion-photos.form-templates")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("templates", toPortableTemplatesJson(templates))
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.writer(Charsets.UTF_8).use { it.write(root.toString(2)) }
        } ?: error("No se pudo crear el JSON")
    }

    /**
     * Importa formatos desde JSON. Acepta el formato portátil de la app, un array antiguo de formatos
     * o incluso un único objeto de formato. Se asigna un id nuevo para no sobrescribir formatos existentes.
     */
    fun importTemplatesFromUri(uri: Uri): List<FormTemplate> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo leer el JSON")
        val objects = parseTemplateObjects(text)
        require(objects.isNotEmpty()) { "El JSON no contiene formatos de formulario" }
        return objects.map { portableTemplateFromJson(it, preserveId = false) }
    }

    fun loadSubmissions(): List<FormSubmission> = runCatching {
        if (!submissionsFile.exists()) return emptyList()
        val array = JSONArray(submissionsFile.readText())
        List(array.length()) { submissionFromJson(array.getJSONObject(it)) }.map { it.normalized() }
    }.getOrDefault(emptyList())

    fun saveSubmissions(submissions: List<FormSubmission>) {
        submissionsFile.writeText(JSONArray(submissions.map { submissionToJson(it.normalized()) }).toString(2))
    }

    /** Exporta formularios ya llenados como respaldo JSON portátil. */
    fun exportSubmissionsToUri(submissions: List<FormSubmission>, uri: Uri) {
        require(submissions.isNotEmpty()) { "No hay formularios guardados para exportar" }
        val root = JSONObject().apply {
            put("schema", "inspeccion-photos.form-submissions")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("submissions", JSONArray(submissions.map { submissionToPortableJson(it.normalized()) }))
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.writer(Charsets.UTF_8).use { it.write(root.toString(2)) }
        } ?: error("No se pudo crear el JSON")
    }

    /** Importa formularios ya llenados. Cada formulario recibe un id nuevo para evitar sobrescrituras. */
    fun importSubmissionsFromUri(uri: Uri): List<FormSubmission> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo leer el JSON")
        val trimmed = text.trim()
        require(trimmed.isNotBlank()) { "El JSON está vacío" }

        val objects: List<JSONObject> = when {
            trimmed.startsWith("[") -> {
                val array = JSONArray(trimmed)
                List(array.length()) { array.getJSONObject(it) }
            }
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                when {
                    root.has("submissions") -> {
                        val array = root.optJSONArray("submissions") ?: JSONArray()
                        List(array.length()) { array.getJSONObject(it) }
                    }
                    root.has("template") && root.has("cells") -> listOf(root)
                    else -> error("El JSON no contiene formularios guardados")
                }
            }
            else -> error("El archivo no contiene JSON válido")
        }

        require(objects.isNotEmpty()) { "El JSON no contiene formularios guardados" }
        return objects.map { portableSubmissionFromJson(it) }
    }

    fun newSubmission(template: FormTemplate): FormSubmission {
        val normalized = template.normalized()
        val submission = FormSubmission(
            template = normalized,
            cells = List(normalized.initialRows) { List(normalized.columns.size) { "" } }
        )
        upsertSubmission(submission)
        return submission
    }

    fun upsertSubmission(submission: FormSubmission) {
        val current = loadSubmissions().toMutableList()
        val normalized = submission.normalized()
        val index = current.indexOfFirst { it.id == normalized.id }
        if (index >= 0) current[index] = normalized else current.add(normalized)
        saveSubmissions(current)
    }

    fun deleteSubmission(id: String) {
        saveSubmissions(loadSubmissions().filterNot { it.id == id })
    }

    private fun headerSlotToJson(slot: ReportHeaderSlot, portable: Boolean = false): JSONObject = JSONObject().apply {
        put("type", slot.type.name)
        put("text", slot.text)
        put("imagePath", if (portable) "" else slot.imagePath)
        if (portable && slot.type == ReportSlotType.IMAGE && slot.imagePath.isNotBlank()) {
            val file = File(slot.imagePath)
            if (file.exists() && file.length() > 0L) {
                put("imageBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                put("imageExtension", file.extension.ifBlank { "jpg" })
            }
        }
    }

    private fun headerSlotFromJson(json: JSONObject?): ReportHeaderSlot {
        val source = json ?: JSONObject()
        return ReportHeaderSlot(
            type = runCatching { ReportSlotType.valueOf(source.optString("type", ReportSlotType.EMPTY.name)) }
                .getOrDefault(ReportSlotType.EMPTY),
            text = source.optString("text"),
            imagePath = source.optString("imagePath")
        )
    }

    private fun restorePortableHeaderSlot(json: JSONObject?, templateId: String, side: String): ReportHeaderSlot {
        val source = json ?: JSONObject()
        val type = runCatching { ReportSlotType.valueOf(source.optString("type", ReportSlotType.EMPTY.name)) }
            .getOrDefault(ReportSlotType.EMPTY)
        val text = source.optString("text")
        if (type != ReportSlotType.IMAGE) return ReportHeaderSlot(type = type, text = text)

        val restoredPath = runCatching {
            val base64 = source.optString("imageBase64")
            if (base64.isNotBlank()) {
                val ext = source.optString("imageExtension", "jpg")
                    .replace(Regex("[^A-Za-z0-9]"), "")
                    .ifBlank { "jpg" }
                val safeId = templateId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val safeSide = if (side == "right") "right" else "left"
                val output = File(imageDir, "${safeId}_${safeSide}.$ext")
                output.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                require(output.length() > 0L) { "La imagen incrustada está vacía" }
                output.absolutePath
            } else {
                val oldPath = source.optString("imagePath")
                oldPath.takeIf { it.isNotBlank() && File(it).exists() }.orEmpty()
            }
        }.getOrDefault("")

        return ReportHeaderSlot(type = type, text = text, imagePath = restoredPath)
    }

    private fun columnToJson(column: FormColumnDefinition): JSONObject = JSONObject().apply {
        put("id", column.id)
        put("name", column.name)
        put("cellType", column.cellType.name)
        put("options", JSONArray(column.options))
        put("hiddenInApp", column.hiddenInApp)
        put("hiddenInReport", column.hiddenInReport)
    }

    private fun columnFromJson(json: JSONObject): FormColumnDefinition = FormColumnDefinition(
        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = json.optString("name", "Columna"),
        cellType = runCatching { FormCellType.valueOf(json.optString("cellType", FormCellType.FREE_TEXT.name)) }
            .getOrDefault(FormCellType.FREE_TEXT),
        options = json.optJSONArray("options")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty(),
        hiddenInApp = json.optBoolean("hiddenInApp", false),
        hiddenInReport = json.optBoolean("hiddenInReport", false)
    )

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
        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
        text = json.optString("text", "Campo"),
        mode = runCatching { SectionCellMode.valueOf(json.optString("mode", SectionCellMode.VALUE.name)) }.getOrDefault(SectionCellMode.VALUE)
    )

    private fun sectionLineFromJson(json: JSONObject): SectionLineDefinition {
        val type = runCatching { SectionLineType.valueOf(json.optString("type", SectionLineType.A.name)) }.getOrDefault(SectionLineType.A)
        val separators = json.optJSONArray("separators")?.let { arr -> List(arr.length()) { arr.optInt(it) } }.orEmpty()
        val cells = json.optJSONArray("cells")?.let { arr -> List(arr.length()) { sectionCellFromJson(arr.getJSONObject(it)) } }.orEmpty()
        return SectionLineDefinition(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            type = type,
            separators = separators.ifEmpty { SectionLineDefinition.defaultSeparators(type) },
            cells = cells.ifEmpty { SectionLineDefinition.defaultCells(type) }
        ).normalized()
    }

    private fun sectionFromJson(json: JSONObject): SectionDefinition = SectionDefinition(
        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
        title = json.optString("title", "SECCIÓN"),
        lines = json.optJSONArray("lines")?.let { arr -> List(arr.length()) { sectionLineFromJson(arr.getJSONObject(it)) } }.orEmpty()
    ).normalized()

    private fun signatureFromJson(json: JSONObject): SignatureDefinition = SignatureDefinition(
        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = json.optString("name", "NOMBRE"),
        alignment = runCatching { SignatureAlignment.valueOf(json.optString("alignment", SignatureAlignment.CENTER.name)) }.getOrDefault(SignatureAlignment.CENTER)
    ).normalized()

    private fun templateToJson(template: FormTemplate): JSONObject = JSONObject().apply {
        put("id", template.id)
        put("name", template.name)
        put("title", template.title)
        put("subtitle", template.subtitle)
        put("leftHeader", headerSlotToJson(template.leftHeader))
        put("rightHeader", headerSlotToJson(template.rightHeader))
        put("initialRows", template.initialRows)
        put("exportFormats", JSONArray(template.exportFormats.map { it.name }))
        put("sections", JSONArray(template.sections.map(::sectionToJson)))
        put("signatures", JSONArray(template.signatures.map(::signatureToJson)))
        put("columns", JSONArray(template.columns.map(::columnToJson)))
        put("createdAt", template.createdAt)
        put("updatedAt", template.updatedAt)
    }

    private fun templateToPortableJson(template: FormTemplate): JSONObject = templateToJson(template).apply {
        put("leftHeader", headerSlotToJson(template.leftHeader, portable = true))
        put("rightHeader", headerSlotToJson(template.rightHeader, portable = true))
    }

    private fun templateFromJson(json: JSONObject): FormTemplate = FormTemplate(
        id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = json.optString("name", "Formulario"),
        title = json.optString("title", "FORMULARIO DE INSPECCIÓN"),
        subtitle = json.optString("subtitle", "Registro de campo"),
        leftHeader = headerSlotFromJson(json.optJSONObject("leftHeader")),
        rightHeader = headerSlotFromJson(json.optJSONObject("rightHeader")),
        initialRows = json.optInt("initialRows", 21),
        exportFormats = json.optJSONArray("exportFormats")?.let { array ->
            buildSet { for (i in 0 until array.length()) runCatching { add(FormExportFormat.valueOf(array.optString(i))) } }
        }.orEmpty().ifEmpty { setOf(FormExportFormat.PDF) },
        sections = json.optJSONArray("sections")?.let { array -> List(array.length()) { sectionFromJson(array.getJSONObject(it)) } }.orEmpty(),
        signatures = json.optJSONArray("signatures")?.let { array -> List(array.length()) { signatureFromJson(array.getJSONObject(it)) } }.orEmpty(),
        columns = json.optJSONArray("columns")?.let { array ->
            List(array.length()) { columnFromJson(array.getJSONObject(it)) }
        }.orEmpty(),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
    ).normalized()

    private fun portableTemplateFromJson(json: JSONObject, preserveId: Boolean = false): FormTemplate {
        val now = System.currentTimeMillis()
        val base = templateFromJson(json)
        val targetId = if (preserveId) base.id else UUID.randomUUID().toString()
        return base.copy(
            id = targetId,
            leftHeader = restorePortableHeaderSlot(json.optJSONObject("leftHeader"), targetId, "left"),
            rightHeader = restorePortableHeaderSlot(json.optJSONObject("rightHeader"), targetId, "right"),
            createdAt = if (preserveId) base.createdAt else now,
            updatedAt = if (preserveId) base.updatedAt else now
        ).normalized()
    }

    private fun parseTemplateObjects(text: String): List<JSONObject> {
        val trimmed = text.trim()
        require(trimmed.isNotBlank()) { "El JSON está vacío" }
        return when {
            trimmed.startsWith("[") -> {
                val array = JSONArray(trimmed)
                List(array.length()) { array.getJSONObject(it) }
            }
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                when {
                    root.has("templates") -> {
                        val array = root.optJSONArray("templates") ?: JSONArray()
                        List(array.length()) { array.getJSONObject(it) }
                    }
                    root.has("template") && root.optJSONObject("template") != null -> listOf(root.getJSONObject("template"))
                    root.has("columns") -> listOf(root)
                    else -> error("El JSON no contiene formatos de formulario")
                }
            }
            else -> error("El archivo no contiene JSON válido")
        }
    }

    private fun submissionToJson(submission: FormSubmission): JSONObject = JSONObject().apply {
        put("id", submission.id)
        put("template", templateToJson(submission.template))
        put("cells", JSONArray(submission.cells.map { JSONArray(it) }))
        put("sectionValues", JSONObject(submission.sectionValues))
        put("completed", submission.completed)
        put("createdAt", submission.createdAt)
        put("updatedAt", submission.updatedAt)
    }

    private fun submissionToPortableJson(submission: FormSubmission): JSONObject = submissionToJson(submission).apply {
        put("template", templateToPortableJson(submission.template))
    }

    private fun submissionFromJson(json: JSONObject): FormSubmission {
        val template = templateFromJson(json.optJSONObject("template") ?: JSONObject())
        val rows = json.optJSONArray("cells")?.let { array ->
            List(array.length()) { rowIndex ->
                val row = array.optJSONArray(rowIndex) ?: JSONArray()
                List(row.length()) { columnIndex -> row.optString(columnIndex) }
            }
        }.orEmpty()
        return FormSubmission(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            template = template,
            cells = rows,
            sectionValues = json.optJSONObject("sectionValues")?.let { obj -> obj.keys().asSequence().associateWith { obj.optString(it) } }.orEmpty(),
            completed = json.optBoolean("completed", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        ).normalized()
    }

    private fun portableSubmissionFromJson(json: JSONObject): FormSubmission {
        val templateJson = json.optJSONObject("template") ?: error("El formulario no contiene su formato")
        val template = portableTemplateFromJson(templateJson, preserveId = false)
        val rows = json.optJSONArray("cells")?.let { array ->
            List(array.length()) { rowIndex ->
                val row = array.optJSONArray(rowIndex) ?: JSONArray()
                List(row.length()) { columnIndex -> row.optString(columnIndex) }
            }
        }.orEmpty()
        val sectionValues = json.optJSONObject("sectionValues")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.optString(it) }
        }.orEmpty()
        val now = System.currentTimeMillis()
        return FormSubmission(
            id = UUID.randomUUID().toString(),
            template = template,
            cells = rows,
            sectionValues = sectionValues,
            completed = json.optBoolean("completed", false),
            createdAt = now,
            updatedAt = now
        ).normalized()
    }
}
