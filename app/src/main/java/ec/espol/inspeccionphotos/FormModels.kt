package ec.espol.inspeccionphotos

import java.util.UUID

enum class FormCellType { FREE_TEXT, DROPDOWN }
enum class FormExportFormat { PDF, WORD, EXCEL }

data class FormColumnDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Columna",
    val cellType: FormCellType = FormCellType.FREE_TEXT,
    val options: List<String> = emptyList(),
    val hiddenInApp: Boolean = false,
    val hiddenInReport: Boolean = false
) {
    fun normalized(index: Int): FormColumnDefinition = copy(
        name = name.take(30),
        options = options.map { it.trim().take(60) }.filter { it.isNotBlank() }.distinct().take(50)
    )
}

data class FormTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Nuevo formulario",
    val title: String = "FORMULARIO DE INSPECCIÓN",
    val subtitle: String = "Registro de campo",
    val leftHeader: ReportHeaderSlot = ReportHeaderSlot(),
    val rightHeader: ReportHeaderSlot = ReportHeaderSlot(),
    val initialRows: Int = 21,
    val exportFormats: Set<FormExportFormat> = setOf(FormExportFormat.PDF),
    val sections: List<SectionDefinition> = emptyList(),
    val signatures: List<SignatureDefinition> = emptyList(),
    val columns: List<FormColumnDefinition> = List(5) { index ->
        FormColumnDefinition(name = "C${index + 1}")
    },
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun normalized(): FormTemplate {
        val normalizedColumns = columns.take(12).ifEmpty { listOf(FormColumnDefinition(name = "C1")) }
            .mapIndexed { index, column -> column.normalized(index) }
        return copy(
            name = name.trim().take(70).ifBlank { "Formulario" },
            title = title.take(120),
            subtitle = subtitle.take(160),
            leftHeader = leftHeader.copy(text = leftHeader.text.take(70)),
            rightHeader = rightHeader.copy(text = rightHeader.text.take(70)),
            initialRows = initialRows.coerceIn(1, 100),
            exportFormats = exportFormats.ifEmpty { setOf(FormExportFormat.PDF) },
            sections = sections.take(12).map { it.normalized() },
            signatures = signatures.take(3).map { it.normalized() },
            columns = normalizedColumns
        )
    }
}

data class FormSubmission(
    val id: String = UUID.randomUUID().toString(),
    val template: FormTemplate,
    val cells: List<List<String>> = List(template.initialRows) { List(template.columns.size) { "" } },
    val sectionValues: Map<String, String> = emptyMap(),
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun normalized(): FormSubmission {
        val tpl = template.normalized()
        val columnCount = tpl.columns.size
        val normalizedRows = cells.take(100).ifEmpty { listOf(List(columnCount) { "" }) }.map { row ->
            List(columnCount) { index -> row.getOrNull(index).orEmpty().take(500) }
        }
        val validSectionCellIds = tpl.sections.flatMap { it.lines }.flatMap { it.cells }.map { it.id }.toSet()
        val cleanSectionValues = sectionValues.filterKeys { it in validSectionCellIds }.mapValues { it.value.take(500) }
        return copy(template = tpl, cells = normalizedRows, sectionValues = cleanSectionValues)
    }
}
