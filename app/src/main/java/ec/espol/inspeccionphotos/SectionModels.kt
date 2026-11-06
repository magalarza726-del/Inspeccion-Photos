package ec.espol.inspeccionphotos

import java.util.UUID

enum class SectionLineType(val cellCount: Int) { A(2), B(4), C(3), D(4) }
enum class SectionCellMode { LABEL, VALUE }
enum class SignatureAlignment { LEFT, CENTER, RIGHT }

data class SectionCellDefinition(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Campo",
    val mode: SectionCellMode = SectionCellMode.VALUE
) {
    fun normalized(index: Int): SectionCellDefinition = copy(
        // El texto editable se conserva exactamente (incluidos vacío y espacios).
        // Los valores por defecto solo existen al crear la celda, no al normalizarla.
        text = text.take(60)
    )
}

data class SectionLineDefinition(
    val id: String = UUID.randomUUID().toString(),
    val type: SectionLineType = SectionLineType.A,
    /** Posiciones acumuladas de las barras verticales, expresadas en porcentaje (10..90). */
    val separators: List<Int> = defaultSeparators(type),
    val cells: List<SectionCellDefinition> = defaultCells(type)
) {
    fun normalized(): SectionLineDefinition {
        val count = type.cellCount
        val normalizedCells = List(count) { i ->
            cells.getOrNull(i)?.normalized(i) ?: defaultCells(type)[i]
        }
        val required = count - 1
        val raw = List(required) { i -> separators.getOrNull(i) ?: defaultSeparators(type)[i] }
        val sorted = raw.map { it.coerceIn(8, 92) }.sorted().toMutableList()
        for (i in sorted.indices) {
            val min = if (i == 0) 8 else sorted[i - 1] + 8
            val max = 92 - (sorted.lastIndex - i) * 8
            sorted[i] = sorted[i].coerceIn(min, max)
        }
        return copy(separators = sorted, cells = normalizedCells)
    }

    companion object {
        fun defaultSeparators(type: SectionLineType): List<Int> = when (type) {
            SectionLineType.A -> listOf(25)
            SectionLineType.B -> listOf(25, 50, 75)
            SectionLineType.C -> listOf(25, 50)
            SectionLineType.D -> listOf(25, 50, 75)
        }

        fun defaultCells(type: SectionLineType): List<SectionCellDefinition> = when (type) {
            SectionLineType.A -> listOf(
                SectionCellDefinition(text = "CAMPO 1", mode = SectionCellMode.LABEL),
                SectionCellDefinition(text = "DESCRIPCIÓN 1", mode = SectionCellMode.VALUE)
            )
            SectionLineType.B -> listOf(
                SectionCellDefinition(text = "CAMPO 2", mode = SectionCellMode.LABEL),
                SectionCellDefinition(text = "DESCRIPCIÓN 2", mode = SectionCellMode.VALUE),
                SectionCellDefinition(text = "CAMPO 3", mode = SectionCellMode.LABEL),
                SectionCellDefinition(text = "DESCRIPCIÓN 3", mode = SectionCellMode.VALUE)
            )
            SectionLineType.C -> listOf(
                SectionCellDefinition(text = "CAMPO 4", mode = SectionCellMode.LABEL),
                SectionCellDefinition(text = "DESCRIPCIÓN 4a", mode = SectionCellMode.VALUE),
                SectionCellDefinition(text = "DESCRIPCIÓN 4b", mode = SectionCellMode.VALUE)
            )
            SectionLineType.D -> listOf(
                SectionCellDefinition(text = "CAMPO 5", mode = SectionCellMode.LABEL),
                SectionCellDefinition(text = "DESCRIPCIÓN 5a", mode = SectionCellMode.VALUE),
                SectionCellDefinition(text = "DESCRIPCIÓN 5b", mode = SectionCellMode.VALUE),
                SectionCellDefinition(text = "DESCRIPCIÓN 5c", mode = SectionCellMode.VALUE)
            )
        }
    }
}

data class SectionDefinition(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "TÍTULO DE SECCIÓN",
    val lines: List<SectionLineDefinition> = listOf(SectionLineDefinition( type = SectionLineType.A))
) {
    fun normalized(): SectionDefinition = copy(
        title = title.take(80),
        lines = lines.take(12).ifEmpty { listOf(SectionLineDefinition(type = SectionLineType.A)) }.map { it.normalized() }
    )
}

data class SignatureDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "NOMBRE",
    val alignment: SignatureAlignment = SignatureAlignment.CENTER
) {
    fun normalized(): SignatureDefinition = copy(name = name.take(70))
}
