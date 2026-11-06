package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.*

@Composable
fun SectionDesigner(
    sections: List<SectionDefinition>,
    signatures: List<SignatureDefinition>,
    palette: LegacyPalette,
    onSectionsChange: (List<SectionDefinition>) -> Unit,
    onSignaturesChange: (List<SignatureDefinition>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Secciones", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Combina líneas A, B, C y D. Cada barra vertical puede moverse con su slider.", color = palette.muted, fontSize = 10.sp)
            }
            LegacySecondaryButton("+ Sección", palette) {
                if (sections.size < 12) onSectionsChange(sections + SectionDefinition(title = "SECCIÓN ${sections.size + 1}"))
            }
        }

        if (sections.isEmpty()) {
            Text("No hay secciones configuradas.", color = palette.muted, fontSize = 11.sp)
        }

        sections.forEachIndexed { sectionIndex, section ->
            Card(
                colors = CardDefaults.cardColors(containerColor = palette.surface2),
                border = BorderStroke(1.dp, palette.border),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = section.title,
                            onValueChange = { value ->
                                onSectionsChange(sections.mapIndexed { i, s -> if (i == sectionIndex) s.copy(title = value.take(80)) else s })
                            },
                            label = { Text("Título de sección") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(7.dp))
                        TextButton(onClick = { onSectionsChange(sections.filterIndexed { i, _ -> i != sectionIndex }) }) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    section.lines.forEachIndexed { lineIndex, line ->
                        SectionLineDesigner(
                            line = line,
                            palette = palette,
                            onChange = { updated ->
                                val updatedSection = section.copy(lines = section.lines.mapIndexed { i, l -> if (i == lineIndex) updated else l })
                                onSectionsChange(sections.mapIndexed { i, s -> if (i == sectionIndex) updatedSection else s })
                            },
                            onRemove = {
                                if (section.lines.size > 1) {
                                    val updatedSection = section.copy(lines = section.lines.filterIndexed { i, _ -> i != lineIndex })
                                    onSectionsChange(sections.mapIndexed { i, s -> if (i == sectionIndex) updatedSection else s })
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLineType.values().forEach { type ->
                            AssistChip(
                                onClick = {
                                    if (section.lines.size < 12) {
                                        val updatedSection = section.copy(lines = section.lines + SectionLineDefinition(type = type))
                                        onSectionsChange(sections.mapIndexed { i, s -> if (i == sectionIndex) updatedSection else s })
                                    }
                                },
                                label = { Text("+ Línea ${type.name}", fontSize = 10.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionPreview(section.normalized(), emptyMap(), palette, editable = false)
                }
            }
        }

        Divider(color = palette.border.copy(alpha = .6f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Firmas al final", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Puedes usar 1, 2 o 3 firmas y alinear cada una de forma independiente.", color = palette.muted, fontSize = 10.sp)
            }
            LegacySecondaryButton("+ Firma", palette) {
                if (signatures.size < 3) onSignaturesChange(signatures + SignatureDefinition(name = "NOMBRE ${signatures.size + 1}"))
            }
        }
        signatures.forEachIndexed { index, signature ->
            Card(
                colors = CardDefaults.cardColors(containerColor = palette.surface2),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = signature.name,
                            onValueChange = { v -> onSignaturesChange(signatures.mapIndexed { i, s -> if (i == index) s.copy(name = v.take(70)) else s }) },
                            label = { Text("Nombre") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { onSignaturesChange(signatures.filterIndexed { i, _ -> i != index }) }) {
                            Text("Quitar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LegacySegmentedChoice(
                        listOf("Izquierda", "Centro", "Derecha"),
                        when (signature.alignment) { SignatureAlignment.LEFT -> "Izquierda"; SignatureAlignment.CENTER -> "Centro"; SignatureAlignment.RIGHT -> "Derecha" },
                        palette
                    ) { choice ->
                        val alignment = when (choice) { "Izquierda" -> SignatureAlignment.LEFT; "Derecha" -> SignatureAlignment.RIGHT; else -> SignatureAlignment.CENTER }
                        onSignaturesChange(signatures.mapIndexed { i, s -> if (i == index) s.copy(alignment = alignment) else s })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLineDesigner(
    line: SectionLineDefinition,
    palette: LegacyPalette,
    onChange: (SectionLineDefinition) -> Unit,
    onRemove: () -> Unit
) {
    val normalized = line.normalized()
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border.copy(alpha = .75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Línea", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Spacer(Modifier.width(7.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLineType.values().forEach { type ->
                        FilterChip(
                            selected = normalized.type == type,
                            onClick = {
                                onChange(
                                    normalized.copy(
                                        type = type,
                                        separators = SectionLineDefinition.defaultSeparators(type),
                                        cells = SectionLineDefinition.defaultCells(type)
                                    )
                                )
                            },
                            label = { Text(type.name, fontSize = 10.sp) }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("−", color = MaterialTheme.colorScheme.error) }
            }
            normalized.cells.forEachIndexed { cellIndex, cell ->
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = cell.text,
                        onValueChange = { v ->
                            onChange(normalized.copy(cells = normalized.cells.mapIndexed { i, c -> if (i == cellIndex) c.copy(text = v.take(60)) else c }))
                        },
                        label = { Text("Celda ${cellIndex + 1}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = cell.mode == SectionCellMode.LABEL,
                        onClick = {
                            val mode = if (cell.mode == SectionCellMode.LABEL) SectionCellMode.VALUE else SectionCellMode.LABEL
                            onChange(normalized.copy(cells = normalized.cells.mapIndexed { i, c -> if (i == cellIndex) c.copy(mode = mode) else c }))
                        },
                        label = { Text(if (cell.mode == SectionCellMode.LABEL) "Etiqueta" else "Dato", fontSize = 9.sp) }
                    )
                }
            }
            normalized.separators.forEachIndexed { separatorIndex, separator ->
                Spacer(Modifier.height(5.dp))
                Text("Barra ${separatorIndex + 1}: $separator%", color = palette.muted, fontSize = 9.sp)
                Slider(
                    value = separator.toFloat(),
                    onValueChange = { raw ->
                        val next = normalized.separators.toMutableList()
                        val min = if (separatorIndex == 0) 8 else next[separatorIndex - 1] + 8
                        val max = if (separatorIndex == next.lastIndex) 92 else next[separatorIndex + 1] - 8
                        next[separatorIndex] = raw.toInt().coerceIn(min, max)
                        onChange(normalized.copy(separators = next))
                    },
                    valueRange = 8f..92f
                )
            }
        }
    }
}

@Composable
fun SectionValuesEditor(
    sections: List<SectionDefinition>,
    values: Map<String, String>,
    palette: LegacyPalette,
    onValueChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEach { section ->
            SectionPreview(section.normalized(), values, palette, editable = true, onValueChange = onValueChange)
        }
    }
}

@Composable
fun SectionPreview(
    section: SectionDefinition,
    values: Map<String, String>,
    palette: LegacyPalette,
    editable: Boolean,
    onValueChange: (String, String) -> Unit = { _, _ -> }
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.Black),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 28.dp).background(Color(0xFFF7F7F7)).border(0.5.dp, Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(section.title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(5.dp))
            }
            section.lines.forEach { lineRaw ->
                val line = lineRaw.normalized()
                val bounds = listOf(0) + line.separators + listOf(100)
                Row(Modifier.fillMaxWidth().heightIn(min = if (editable) 48.dp else 30.dp)) {
                    line.cells.forEachIndexed { index, cell ->
                        val weight = (bounds[index + 1] - bounds[index]).coerceAtLeast(1).toFloat()
                        Box(
                            Modifier.weight(weight).fillMaxHeight().border(0.5.dp, Color.Black).padding(if (editable) 2.dp else 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cell.mode == SectionCellMode.LABEL || !editable) {
                                Text(
                                    if (cell.mode == SectionCellMode.LABEL) cell.text else values[cell.id].orEmpty().ifBlank { cell.text },
                                    color = Color.Black,
                                    fontWeight = if (cell.mode == SectionCellMode.LABEL) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            } else {
                                OutlinedTextField(
                                    value = values[cell.id].orEmpty(),
                                    onValueChange = { onValueChange(cell.id, it.take(500)) },
                                    placeholder = { Text(cell.text, fontSize = 7.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 9.sp, color = Color.Black),
                                    singleLine = false,
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedBorderColor = palette.blue,
                                        unfocusedBorderColor = Color.LightGray,
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactSectionsDocumentPreview(
    sections: List<SectionDefinition>,
    values: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    maxSections: Int = 3,
    maxLinesPerSection: Int = 4
) {
    if (sections.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sections.take(maxSections.coerceAtLeast(1)).forEach { rawSection ->
            val section = rawSection.normalized()
            Column(Modifier.fillMaxWidth().border(.6.dp, Color(0xFF6B7280))) {
                Box(
                    Modifier.fillMaxWidth().height(17.dp)
                        .background(Color(0xFFE9EDF2))
                        .border(.4.dp, Color(0xFF7C838D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        section.title,
                        color = Color(0xFF111827),
                        fontWeight = FontWeight.Bold,
                        fontSize = 5.8.sp,
                        lineHeight = 6.4.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                section.lines.take(maxLinesPerSection.coerceAtLeast(1)).forEach { rawLine ->
                    val line = rawLine.normalized()
                    val bounds = listOf(0) + line.separators + listOf(100)
                    Row(Modifier.fillMaxWidth().height(17.dp)) {
                        line.cells.forEachIndexed { index, cell ->
                            val weight = (bounds[index + 1] - bounds[index]).coerceAtLeast(1).toFloat()
                            val isLabel = cell.mode == SectionCellMode.LABEL
                            Box(
                                Modifier.weight(weight).fillMaxHeight()
                                    .background(if (isLabel) Color(0xFFF4F5F7) else Color.White)
                                    .border(.38.dp, Color(0xFF9CA3AF))
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val text = if (isLabel) {
                                    cell.text
                                } else {
                                    values[cell.id].orEmpty().ifBlank { cell.text }
                                }
                                Text(
                                    text,
                                    color = if (isLabel) Color(0xFF1F2937) else Color(0xFF6B7280),
                                    fontWeight = if (isLabel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 4.8.sp,
                                    lineHeight = 5.3.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                if (section.lines.size > maxLinesPerSection) {
                    Text(
                        "+ ${section.lines.size - maxLinesPerSection} línea(s)",
                        color = Color(0xFF6B7280),
                        fontSize = 4.4.sp,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (sections.size > maxSections) {
            Text(
                "+ ${sections.size - maxSections} sección(es) adicionales",
                color = Color(0xFF6B7280),
                fontSize = 4.8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SignaturePreview(signatures: List<SignatureDefinition>, palette: LegacyPalette, modifier: Modifier = Modifier) {
    if (signatures.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        signatures.forEach { signature ->
            val alignment = when (signature.alignment) {
                SignatureAlignment.LEFT -> Alignment.Start
                SignatureAlignment.CENTER -> Alignment.CenterHorizontally
                SignatureAlignment.RIGHT -> Alignment.End
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.width(180.dp).height(1.dp).background(palette.text.copy(alpha = .65f)))
                Text(signature.name, color = palette.text, fontSize = 10.sp, modifier = Modifier.width(180.dp), textAlign = TextAlign.Center)
            }
        }
    }
}
