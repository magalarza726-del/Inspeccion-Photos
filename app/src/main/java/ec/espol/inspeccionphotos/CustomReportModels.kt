package ec.espol.inspeccionphotos

enum class ReportSlotType { EMPTY, TEXT, IMAGE }
enum class ReportExportFormat { PDF, WORD, EXCEL }
enum class MainBlockLayout { IMAGE_LEFT_TABLE, IMAGE_RIGHT_TABLE, IMAGE_ABOVE_TABLE, IMAGE_BELOW_TABLE }
enum class SectionPlacement { ABOVE_MAIN_BLOCK, BELOW_MAIN_BLOCK }

data class ReportHeaderSlot(
    val type: ReportSlotType = ReportSlotType.EMPTY,
    val text: String = "",
    val imagePath: String = ""
)

data class CustomReportConfig(
    val title: String = "REPORTE DE INSPECCIÓN",
    val subtitle: String = "Evidencia fotográfica",
    val leftHeader: ReportHeaderSlot = ReportHeaderSlot(),
    val rightHeader: ReportHeaderSlot = ReportHeaderSlot(),
    val exportFormats: Set<ReportExportFormat> = setOf(ReportExportFormat.PDF),
    val mainBlockLayout: MainBlockLayout = MainBlockLayout.IMAGE_LEFT_TABLE,
    val mainImageWidthPercent: Int = 55,
    val tableRows: Int = 4,
    val tableColumns: Int = 3,
    val sectionPlacement: SectionPlacement = SectionPlacement.BELOW_MAIN_BLOCK,
    val sections: List<SectionDefinition> = emptyList(),
    val signatures: List<SignatureDefinition> = emptyList(),
    val rowHeaders: List<String> = listOf("F1", "F2", "F3", "F4"),
    val columnHeaders: List<String> = listOf("C1", "C2", "C3"),
    val evidenceColumns: Int = 3
) {
    fun normalized(): CustomReportConfig {
        val rows = tableRows.coerceIn(1, 12)
        val cols = tableColumns.coerceIn(1, 8)
        return copy(
            title = title.take(120),
            subtitle = subtitle.take(160),
            leftHeader = leftHeader.copy(text = leftHeader.text.take(70)),
            rightHeader = rightHeader.copy(text = rightHeader.text.take(70)),
            mainImageWidthPercent = mainImageWidthPercent.coerceIn(25, 80),
            tableRows = rows,
            tableColumns = cols,
            sections = sections.take(12).map { it.normalized() },
            signatures = signatures.take(3).map { it.normalized() },
            rowHeaders = List(rows) { rowHeaders.getOrNull(it).orEmpty().take(20) },
            columnHeaders = List(cols) { columnHeaders.getOrNull(it).orEmpty().take(20) },
            evidenceColumns = evidenceColumns.coerceIn(1, 4),
            exportFormats = exportFormats.ifEmpty { setOf(ReportExportFormat.PDF) }
        )
    }
}

data class ExportSelection(
    val photosAndStructure: Boolean = true,
    val standardReport: Boolean = true,
    val standardPdfColumns: Int = 3,
    val spatialReport: Boolean = false,
    val customReport: Boolean = false,
    val customReportPresetId: String = ""
) {
    fun normalized(): ExportSelection = copy(standardPdfColumns = standardPdfColumns.coerceIn(1, 4))
    fun anySelected(): Boolean = photosAndStructure || standardReport || spatialReport || customReport
}
