package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.*

@Composable
fun ExportSelectionDialog(
    initial: ExportSelection,
    photoPresets: List<PhotoReportPreset> = emptyList(),
    hiddenLegacyPdfs: Set<Int> = emptySet(),
    spatialFunctionsEnabled: Boolean = false,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onExport: (ExportSelection) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial.normalized()) }
    var choosingStandardPdf by remember { mutableStateOf(false) }
    val visiblePdfOptions = (1..4).filterNot { it in hiddenLegacyPdfs }

    if (choosingStandardPdf) {
        AlertDialog(
            onDismissRequest = { choosingStandardPdf = false },
            containerColor = palette.surface,
            title = { Text("Reporte estándar", color = palette.text, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("¿Cuántas columnas de fotos deseas en el PDF?", color = palette.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    if (visiblePdfOptions.isEmpty()) {
                        Text("Los PDF 1, 2, 3 y 4 están ocultos en Configuración.", color = MaterialTheme.colorScheme.error)
                    } else {
                        visiblePdfOptions.forEach { columns ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = value.standardPdfColumns == columns,
                                    onClick = { value = value.copy(standardPdfColumns = columns) }
                                )
                                Spacer(Modifier.width(7.dp))
                                Column {
                                    Text("PDF a $columns columna${if (columns == 1) "" else "s"}", color = palette.text, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        when (columns) {
                                            1 -> "Fotografías grandes"
                                            2 -> "Formato intermedio"
                                            3 -> "Formato clásico"
                                            else -> "Formato compacto"
                                        },
                                        color = palette.muted, style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = visiblePdfOptions.isNotEmpty(),
                    onClick = {
                        val fixed = if (value.standardPdfColumns in visiblePdfOptions) value
                        else value.copy(standardPdfColumns = visiblePdfOptions.first())
                        onExport(fixed.copy(spatialReport = fixed.spatialReport && spatialFunctionsEnabled))
                    }
                ) { Text("Exportar", color = if (visiblePdfOptions.isNotEmpty()) palette.blue else palette.muted) }
            },
            dismissButton = { TextButton(onClick = { choosingStandardPdf = false }) { Text("Atrás", color = palette.muted) } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("¿Qué deseas exportar?", color = palette.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ExportCheck(
                    "Incluir archivo ZIP",
                    "Opcional: respaldo completo de la estructura y todas las fotografías.",
                    value.photosAndStructure, palette
                ) { value = value.copy(photosAndStructure = it) }
                ExportCheck(
                    "Reporte estándar",
                    "Después podrás escoger PDF a 1, 2, 3 o 4 columnas.",
                    value.standardReport, palette
                ) { value = value.copy(standardReport = it) }
                if (spatialFunctionsEnabled) {
                    ExportCheck(
                        "Reporte espacial",
                        "Incluye las fotografías con la información espacial almacenada como observación.",
                        value.spatialReport, palette
                    ) { value = value.copy(spatialReport = it) }
                }
                ExportCheck(
                    "Reporte personalizado",
                    "Permite elegir cualquiera de los formatos fotográficos creados.",
                    value.customReport, palette
                ) { value = value.copy(customReport = it) }
                if (value.customReport && photoPresets.isNotEmpty()) {
                    Text("Formato fotográfico", color = palette.text, fontWeight = FontWeight.SemiBold)
                    val selectedId = value.customReportPresetId.takeIf { id ->
                        photoPresets.any { it.id == id }
                    } ?: photoPresets.first().id
                    photoPresets.forEach { preset ->
                        Row(Modifier.fillMaxWidth().clickable {
                            value = value.copy(customReportPresetId = preset.id)
                        }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == preset.id,
                                onClick = { value = value.copy(customReportPresetId = preset.id) })
                            Text(preset.name, color = palette.text, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("La selección queda guardada para la próxima exportación.", color = palette.muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val effective = value.copy(spatialReport = value.spatialReport && spatialFunctionsEnabled)
            TextButton(
                enabled = effective.anySelected(),
                onClick = {
                    if (effective.standardReport) {
                        if (visiblePdfOptions.isEmpty()) {
                            value = effective.copy(standardReport = false)
                            if (value.anySelected()) onExport(value)
                        } else {
                            if (value.standardPdfColumns !in visiblePdfOptions) value = value.copy(standardPdfColumns = visiblePdfOptions.first())
                            choosingStandardPdf = true
                        }
                    } else onExport(effective)
                }
            ) { Text("Continuar", color = if (effective.anySelected()) palette.blue else palette.muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.muted) } }
    )
}

@Composable private fun ExportCheck(title:String,subtitle:String,checked:Boolean,palette:LegacyPalette,onChecked:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.Top){Checkbox(checked=checked,onCheckedChange=onChecked);Spacer(Modifier.width(6.dp));Column(Modifier.weight(1f)){Text(title,color=palette.text,fontWeight=FontWeight.SemiBold);Text(subtitle,color=palette.muted,style=MaterialTheme.typography.bodySmall)}}
}

@Composable
fun ReportTableEditorDialog(
    folder: Inspection,
    config: CustomReportConfig,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onSave: (List<List<String>>) -> Unit,
    onRemove: () -> Unit
) {
    val rows = config.tableRows
    val cols = config.tableColumns
    val compact = LocalConfiguration.current.screenWidthDp < 900
    var values by remember(folder.id, rows, cols, folder.reportTableValues) {
        mutableStateOf(
            List(rows) { r ->
                List(cols) { c -> folder.reportTableValues.getOrNull(r)?.getOrNull(c).orEmpty() }
            }
        )
    }

    fun updateCell(row: Int, col: Int, text: String) {
        val mutable = values.map { it.toMutableList() }.toMutableList()
        mutable[row][col] = text.take(80)
        values = mutable.map { it.toList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(if (compact) .96f else .88f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        title = {
            Column {
                Text("Tabla · ${folder.name}", color = palette.text, fontWeight = FontWeight.Bold)
                Text(
                    "${rows} fila(s) × ${cols} columna(s). Los nombres se configuran en Panel de Control.",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = if (compact) 560.dp else 620.dp).verticalScroll(rememberScrollState())) {
                if (compact) {
                    repeat(rows) { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            colors = CardDefaults.cardColors(containerColor = palette.surface2),
                            border = BorderStroke(1.dp, palette.border),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    config.rowHeaders.getOrElse(r) { "F${r + 1}" },
                                    color = palette.text,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                repeat(cols) { c ->
                                    OutlinedTextField(
                                        value = values[r][c],
                                        onValueChange = { updateCell(r, c, it) },
                                        label = { Text(config.columnHeaders.getOrElse(c) { "C${c + 1}" }) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        maxLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = palette.text,
                                            unfocusedTextColor = palette.text,
                                            focusedBorderColor = palette.blue,
                                            unfocusedBorderColor = palette.border,
                                            focusedContainerColor = palette.surface,
                                            unfocusedContainerColor = palette.surface,
                                            cursorColor = palette.blue
                                        )
                                    )
                                    if (c < cols - 1) Spacer(Modifier.height(7.dp))
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Row {
                            TableHeaderCell("ITEM", 150, palette)
                            config.columnHeaders.take(cols).forEach { TableHeaderCell(it, 180, palette) }
                        }
                        repeat(rows) { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TableHeaderCell(config.rowHeaders.getOrElse(r) { "F${r + 1}" }, 150, palette)
                                repeat(cols) { c ->
                                    OutlinedTextField(
                                        value = values[r][c],
                                        onValueChange = { updateCell(r, c, it) },
                                        modifier = Modifier.width(180.dp).padding(2.dp),
                                        singleLine = false,
                                        maxLines = 3,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = palette.text,
                                            unfocusedTextColor = palette.text,
                                            focusedBorderColor = palette.blue,
                                            unfocusedBorderColor = palette.border,
                                            focusedContainerColor = palette.surface2,
                                            unfocusedContainerColor = palette.surface2,
                                            cursorColor = palette.blue
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values) }) { Text("Guardar", color = palette.blue) }
        },
        dismissButton = {
            Row {
                if (folder.reportTableEnabled) {
                    TextButton(onClick = onRemove) { Text("Quitar tabla", color = palette.muted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.muted) }
            }
        }
    )
}

@Composable private fun TableHeaderCell(text:String,width:Int,palette:LegacyPalette){Box(Modifier.width(width.dp).heightIn(min=52.dp).padding(2.dp),contentAlignment=Alignment.Center){Text(text,color=palette.text,fontWeight=FontWeight.Bold)}}

/**
 * Editor modal de los valores de las secciones del reporte para UNA carpeta concreta.
 * La plantilla (títulos, líneas A-D y anchos) sigue siendo global y se define en Panel de Control;
 * aquí solo se capturan las descripciones/datos que pertenecen al nodo [folder].
 */
@Composable
fun ReportSectionsEditorDialog(
    folder: Inspection,
    sections: List<SectionDefinition>,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onClear: () -> Unit
) {
    val compact = LocalConfiguration.current.screenWidthDp < 900
    val normalizedSections = remember(sections) { sections.map { it.normalized() } }
    val valueCellIds = remember(normalizedSections) {
        normalizedSections
            .flatMap { it.lines }
            .flatMap { it.cells }
            .filter { it.mode == SectionCellMode.VALUE }
            .map { it.id }
            .toSet()
    }
    var values by remember(folder.id, folder.reportSectionValues, valueCellIds) {
        mutableStateOf(valueCellIds.associateWith { folder.reportSectionValues[it].orEmpty() })
    }

    fun updateValue(cellId: String, text: String) {
        if (cellId !in valueCellIds) return
        values = values.toMutableMap().apply { this[cellId] = text.take(500) }.toMap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(if (compact) .97f else .86f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        title = {
            Column {
                Text("Secciones · ${folder.name}", color = palette.text, fontWeight = FontWeight.Bold)
                Text(
                    "Estos datos se guardan únicamente en esta carpeta/subcarpeta. La estructura se configura en Panel de Control.",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (compact) 590.dp else 650.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (normalizedSections.isEmpty()) {
                    Text("No hay secciones configuradas.", color = palette.muted)
                } else {
                    SectionValuesEditor(
                        sections = normalizedSections,
                        values = values,
                        palette = palette,
                        onValueChange = ::updateValue,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedSections.isNotEmpty(),
                onClick = { onSave(values.filterKeys { it in valueCellIds }) }
            ) {
                Text("Guardar", color = if (normalizedSections.isNotEmpty()) palette.blue else palette.muted)
            }
        },
        dismissButton = {
            Row {
                if (folder.reportSectionValues.values.any { it.isNotBlank() } || values.values.any { it.isNotBlank() }) {
                    TextButton(onClick = onClear) { Text("Limpiar", color = palette.muted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.muted) }
            }
        }
    )
}

@Composable
fun MainImageSourceDialog(
    folder: Inspection,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onUsePhoto: (PhotoItem) -> Unit,
    onTakePhoto: () -> Unit,
    onChooseExternal: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("Imagen principal", color = palette.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                Text("Crear imagen principal", color = palette.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(5.dp))
                LegacyPrimaryButton("Tomar foto", palette, Modifier.fillMaxWidth()) { onTakePhoto() }
                Spacer(Modifier.height(7.dp))
                LegacySecondaryButton("Agregar desde galería", palette, Modifier.fillMaxWidth()) { onChooseExternal() }
                if (folder.photos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Divider(color = palette.border)
                    Spacer(Modifier.height(7.dp))
                    Text("O usar una evidencia existente", color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    folder.photos.forEach { p ->
                        TextButton(onClick = { onUsePhoto(p) }, modifier = Modifier.fillMaxWidth()) {
                            Text(p.name, color = palette.text, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (folder.reportMainImagePath.isNotBlank()) {
                    Divider(color = palette.border)
                    TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                        Text("Quitar imagen principal", color = palette.muted, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = palette.muted) } }
    )
}
