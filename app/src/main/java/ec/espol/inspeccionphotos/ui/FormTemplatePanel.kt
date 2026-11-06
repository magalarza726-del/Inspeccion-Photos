package ec.espol.inspeccionphotos.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.ImageOrientationUtils
import ec.espol.inspeccionphotos.*
import java.io.File
import java.util.UUID

@Composable
fun FormTemplateManager(
    palette: LegacyPalette,
    compact: Boolean,
    templates: List<FormTemplate>,
    store: FormTemplateStore,
    onTemplatesChange: (List<FormTemplate>) -> Unit
) {
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<FormTemplate?>(null) }
    var pendingJsonExport by remember { mutableStateOf<List<FormTemplate>>(emptyList()) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingJsonExport.isNotEmpty()) {
            runCatching { store.exportTemplatesToUri(pendingJsonExport, uri) }
                .onSuccess {
                    Toast.makeText(
                        context,
                        if (pendingJsonExport.size == 1) "Formato JSON exportado" else "${pendingJsonExport.size} formatos JSON exportados",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo exportar el JSON", Toast.LENGTH_LONG).show() }
        }
        pendingJsonExport = emptyList()
    }

    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { store.importTemplatesFromUri(uri) }
                .onSuccess { imported ->
                    val merged = uniquifyImportedTemplateNames(templates, imported)
                    onTemplatesChange(templates + merged)
                    Toast.makeText(
                        context,
                        if (merged.size == 1) "Formato importado" else "${merged.size} formatos importados",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo importar el JSON", Toast.LENGTH_LONG).show() }
        }
    }

    val selected = templates.firstOrNull { it.id == editingId }

    if (selected == null) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text("Formatos de Formularios", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Cada formato es independiente del Reporte de Fotos y conserva su propio encabezado, columnas y opciones.",
                    color = palette.muted, fontSize = 11.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LegacyPrimaryButton("+ Nuevo formato", palette) {
                        val template = FormTemplate(name = "Formulario ${templates.size + 1}")
                        onTemplatesChange(templates + template)
                        editingId = template.id
                    }
                    LegacySecondaryButton("Importar JSON", palette) {
                        importJson.launch("application/json")
                    }
                    if (templates.isNotEmpty()) {
                        LegacySecondaryButton("Exportar todos JSON", palette) {
                            pendingJsonExport = templates
                            exportJson.launch("formularios_inspeccion_photos.json")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "El JSON portátil conserva columnas, desplegables, secciones, firmas y las imágenes del encabezado.",
                    color = palette.muted,
                    fontSize = 9.sp
                )
            }
            Spacer(Modifier.height(14.dp))

            if (templates.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = palette.surface),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Todavía no hay formatos", color = palette.text, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Crea un formato aquí. Luego aparecerá en la nueva pestaña Formularios para llenarlo.", color = palette.muted, fontSize = 12.sp)
                    }
                }
            } else {
                templates.sortedBy { it.name.lowercase() }.forEach { template ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = palette.surface),
                        border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(template.name, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(3.dp))
                            val dropdownCount = template.columns.count { it.cellType == FormCellType.DROPDOWN }
                            Text(
                                "${template.columns.size} columna(s) · ${template.initialRows} filas iniciales · $dropdownCount desplegable(s)",
                                color = palette.muted, fontSize = 11.sp
                            )
                            Text(
                                "Salida: ${template.exportFormats.joinToString(" · ") { when (it) { FormExportFormat.PDF -> "PDF"; FormExportFormat.WORD -> "Word"; FormExportFormat.EXCEL -> "Excel" } }}",
                                color = palette.muted, fontSize = 10.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegacyPrimaryButton("Editar", palette, Modifier.weight(1f)) { editingId = template.id }
                                LegacySecondaryButton("Duplicar", palette, Modifier.weight(1f)) {
                                    val now = System.currentTimeMillis()
                                    val copy = template.copy(
                                        id = UUID.randomUUID().toString(),
                                        name = "${template.name} copia".take(70),
                                        createdAt = now,
                                        updatedAt = now
                                    )
                                    onTemplatesChange(templates + copy)
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegacySecondaryButton("Exportar JSON", palette, Modifier.weight(1f)) {
                                    pendingJsonExport = listOf(template)
                                    exportJson.launch("${safeJsonFileName(template.name)}.json")
                                }
                                LegacySecondaryButton("Eliminar", palette, Modifier.weight(1f)) { deleteCandidate = template }
                            }
                        }
                    }
                }
            }
        }
    } else {
        FormTemplateEditor(
            palette = palette,
            compact = compact,
            template = selected,
            store = store,
            onBack = { editingId = null },
            onSave = { updated ->
                val normalized = updated.normalized().copy(updatedAt = System.currentTimeMillis())
                onTemplatesChange(templates.map { if (it.id == normalized.id) normalized else it })
                editingId = normalized.id
                Toast.makeText(context, "Formato guardado", Toast.LENGTH_SHORT).show()
            }
        )
    }

    deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminar formato") },
            text = { Text("¿Eliminar '${template.name}'? Los formularios ya llenados conservan una copia del formato usado.") },
            confirmButton = {
                TextButton(onClick = {
                    onTemplatesChange(templates.filterNot { it.id == template.id })
                    deleteCandidate = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
    }
}

private fun safeJsonFileName(value: String): String = value
    .trim()
    .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ_-]+"), "_")
    .trim('_')
    .take(50)
    .ifBlank { "formulario" }

private fun uniquifyImportedTemplateNames(existing: List<FormTemplate>, imported: List<FormTemplate>): List<FormTemplate> {
    val used = existing.map { it.name.lowercase() }.toMutableSet()
    return imported.map { template ->
        val base = template.name.trim().ifBlank { "Formulario importado" }
        var index = 1
        var candidate = base.take(70)
        while (candidate.lowercase() in used) {
            index += 1
            val suffix = " ($index)"
            candidate = base.take((70 - suffix.length).coerceAtLeast(1)) + suffix
        }
        used += candidate.lowercase()
        template.copy(name = candidate)
    }
}

@Composable
private fun FormTemplateEditor(
    palette: LegacyPalette,
    compact: Boolean,
    template: FormTemplate,
    store: FormTemplateStore,
    onBack: () -> Unit,
    onSave: (FormTemplate) -> Unit
) {
    val context = LocalContext.current
    var draft by remember(template.id, template.updatedAt) { mutableStateOf(template) }
    var imageSide by remember { mutableStateOf("left") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val path = store.importHeaderImage(draft.id, imageSide, uri)
            draft = if (imageSide == "left") {
                draft.copy(leftHeader = draft.leftHeader.copy(type = ReportSlotType.IMAGE, imagePath = path))
            } else {
                draft.copy(rightHeader = draft.rightHeader.copy(type = ReportSlotType.IMAGE, imagePath = path))
            }
        }.onFailure { Toast.makeText(context, it.message ?: "No se pudo cargar la imagen", Toast.LENGTH_LONG).show() }
    }

    Column(
        Modifier.fillMaxWidth().padding(bottom = 30.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LegacySecondaryButton("← Volver", palette) { onBack() }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Editar formato", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${draft.columns.size} columna(s)", color = palette.muted, fontSize = 11.sp)
            }
            LegacyPrimaryButton("Guardar", palette) { onSave(draft) }
        }
        Spacer(Modifier.height(14.dp))

        FormPanelCard("Identidad del formulario", palette) {
            FormTextField("Nombre del formato", draft.name, palette, 70) { draft = draft.copy(name = it) }
            Spacer(Modifier.height(8.dp))
            FormTextField("Título (1)", draft.title, palette, 120) { draft = draft.copy(title = it) }
            Spacer(Modifier.height(8.dp))
            FormTextField("Subtítulo (2)", draft.subtitle, palette, 160) { draft = draft.copy(subtitle = it) }
            Spacer(Modifier.height(13.dp))
            Text("Espacio 3 - izquierda", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            FormHeaderSlotEditor(
                slot = draft.leftHeader, palette = palette,
                onChange = { draft = draft.copy(leftHeader = it) },
                onPickImage = { imageSide = "left"; imagePicker.launch("image/*") }
            )
            Spacer(Modifier.height(13.dp))
            Text("Espacio 4 - derecha", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            FormHeaderSlotEditor(
                slot = draft.rightHeader, palette = palette,
                onChange = { draft = draft.copy(rightHeader = it) },
                onPickImage = { imageSide = "right"; imagePicker.launch("image/*") }
            )
            Spacer(Modifier.height(8.dp))
            Text("Estos cuatro campos pertenecen únicamente a este formato y no modifican el Reporte de Fotos.", color = palette.muted, fontSize = 10.sp)
        }

        Spacer(Modifier.height(14.dp))
        FormPanelCard("Estructura de la tabla", palette) {
            SettingsChoiceRowPublic(
                label = "Cantidad de columnas",
                value = draft.columns.size.toString(),
                options = (1..12).map(Int::toString),
                palette = palette
            ) { value ->
                val count = value.toIntOrNull()?.coerceIn(1, 12) ?: draft.columns.size
                val next = List(count) { index ->
                    draft.columns.getOrNull(index) ?: FormColumnDefinition(name = "C${index + 1}")
                }
                draft = draft.copy(columns = next)
            }
            Divider(color = palette.border.copy(alpha = .55f))
            SettingsChoiceRowPublic(
                label = "Filas iniciales",
                value = draft.initialRows.toString(),
                options = listOf(5, 10, 15, 20, 21, 25, 30, 40, 50, 75, 100).map(Int::toString),
                palette = palette
            ) { value -> draft = draft.copy(initialRows = value.toIntOrNull()?.coerceIn(1, 100) ?: 21) }
            Spacer(Modifier.height(9.dp))
            Text("La columna ITEM se numera automáticamente. Durante el llenado se pueden agregar o quitar filas.", color = palette.muted, fontSize = 10.sp)
        }

        Spacer(Modifier.height(14.dp))
        FormPanelCard("Formatos de exportación", palette) {
            Text(
                "El formulario completado puede generarse como PDF, Word editable o Excel editable.",
                color = palette.muted,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(
                    "PDF" to FormExportFormat.PDF,
                    "Word" to FormExportFormat.WORD,
                    "Excel" to FormExportFormat.EXCEL
                ).forEach { (label, format) ->
                    val checked = format in draft.exportFormats
                    FilterChip(
                        selected = checked,
                        onClick = {
                            val next = draft.exportFormats.toMutableSet()
                            if (checked && next.size > 1) next.remove(format) else next.add(format)
                            draft = draft.copy(exportFormats = next)
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.blue,
                            selectedLabelColor = Color.White,
                            labelColor = palette.text
                        )
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text("Se conserva esta selección para cada formato de formulario.", color = palette.muted, fontSize = 9.sp)
        }

        Spacer(Modifier.height(14.dp))
        FormPanelCard("Secciones y firmas", palette) {
            SectionDesigner(
                sections = draft.sections,
                signatures = draft.signatures,
                palette = palette,
                onSectionsChange = { draft = draft.copy(sections = it) },
                onSignaturesChange = { draft = draft.copy(signatures = it) }
            )
        }

        Spacer(Modifier.height(14.dp))
        draft.columns.forEachIndexed { index, column ->
            FormPanelCard("Columna ${index + 1}", palette) {
                FormTextField("Nombre", column.name, palette, 30) { name ->
                    draft = draft.copy(columns = draft.columns.mapIndexed { i, c -> if (i == index) c.copy(name = name) else c })
                }
                Spacer(Modifier.height(9.dp))
                Text("Comportamiento de celdas", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LegacySegmentedChoice(
                    options = listOf("Celdas libres", "Celdas desplegables"),
                    selected = if (column.cellType == FormCellType.DROPDOWN) "Celdas desplegables" else "Celdas libres",
                    palette = palette
                ) { selected ->
                    val type = if (selected == "Celdas desplegables") FormCellType.DROPDOWN else FormCellType.FREE_TEXT
                    draft = draft.copy(columns = draft.columns.mapIndexed { i, c -> if (i == index) c.copy(cellType = type) else c })
                }
                if (column.cellType == FormCellType.DROPDOWN) {
                    Spacer(Modifier.height(10.dp))
                    DropdownOptionsEditor(
                        options = column.options,
                        palette = palette,
                        onOptionsChange = { options ->
                            draft = draft.copy(
                                columns = draft.columns.mapIndexed { i, c ->
                                    if (i == index) c.copy(options = options.take(50)) else c
                                }
                            )
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ocultar en la app", color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("No se muestra al llenar el formulario.", color = palette.muted, fontSize = 9.sp)
                    }
                    Switch(
                        checked = column.hiddenInApp,
                        onCheckedChange = { checked ->
                            draft = draft.copy(columns = draft.columns.mapIndexed { i, c -> if (i == index) c.copy(hiddenInApp = checked) else c })
                        }
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ocultar en el reporte", color = palette.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text("Se conserva el dato, pero no aparece en PDF/Word/Excel.", color = palette.muted, fontSize = 9.sp)
                    }
                    Switch(
                        checked = column.hiddenInReport,
                        onCheckedChange = { checked ->
                            draft = draft.copy(columns = draft.columns.mapIndexed { i, c -> if (i == index) c.copy(hiddenInReport = checked) else c })
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Text("Vista previa del formato", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(7.dp))
        FormTemplatePreview(draft.normalized(), palette)
        Spacer(Modifier.height(14.dp))
        LegacyPrimaryButton("Guardar formato", palette, Modifier.fillMaxWidth()) { onSave(draft) }
    }
}


@Composable
private fun DropdownOptionsEditor(
    options: List<String>,
    palette: LegacyPalette,
    onOptionsChange: (List<String>) -> Unit
) {
    // Mantener filas vacías durante la edición es intencional: así el usuario puede
    // pulsar +, escribir una opción y continuar agregando otras sin que Compose
    // elimine la fila mientras todavía está vacía.
    val editable = if (options.isEmpty()) listOf("") else options

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Opciones de la lista", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("Cada opción es independiente. Usa + para agregar otra.", color = palette.muted, fontSize = 10.sp)
        }
        FilledTonalButton(
            onClick = {
                if (editable.size < 50) onOptionsChange(editable + "")
            },
            enabled = editable.size < 50,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.blue.copy(alpha = .18f), contentColor = palette.blue),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
    Spacer(Modifier.height(8.dp))

    editable.forEachIndexed { optionIndex, option ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${optionIndex + 1}",
                color = palette.muted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.width(24.dp),
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = option,
                onValueChange = { value ->
                    val next = editable.toMutableList()
                    next[optionIndex] = value.take(60)
                    onOptionsChange(next)
                },
                placeholder = { Text("Opción ${optionIndex + 1}") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = formFieldColors(palette)
            )
            Spacer(Modifier.width(6.dp))
            TextButton(
                onClick = {
                    val next = editable.toMutableList().also { it.removeAt(optionIndex) }
                    onOptionsChange(if (next.isEmpty()) listOf("") else next)
                },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp)
            ) {
                Text("×", color = palette.muted, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (editable.all { it.isNotBlank() } && editable.size < 50) {
        LegacySecondaryButton("+ Agregar opción", palette, Modifier.fillMaxWidth()) {
            onOptionsChange(editable + "")
        }
    }
}

@Composable
private fun FormPanelCard(title: String, palette: LegacyPalette, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(7.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
private fun FormTextField(label: String, value: String, palette: LegacyPalette, maxChars: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(maxChars)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        colors = formFieldColors(palette)
    )
}

@Composable
private fun formFieldColors(palette: LegacyPalette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = palette.text,
    unfocusedTextColor = palette.text,
    focusedBorderColor = palette.blue,
    unfocusedBorderColor = palette.border,
    focusedContainerColor = palette.surface2,
    unfocusedContainerColor = palette.surface2,
    cursorColor = palette.blue
)

@Composable
private fun FormHeaderSlotEditor(
    slot: ReportHeaderSlot,
    palette: LegacyPalette,
    onChange: (ReportHeaderSlot) -> Unit,
    onPickImage: () -> Unit
) {
    Spacer(Modifier.height(6.dp))
    LegacySegmentedChoice(
        options = listOf("Vacío", "Texto", "Imagen"),
        selected = when (slot.type) {
            ReportSlotType.EMPTY -> "Vacío"
            ReportSlotType.TEXT -> "Texto"
            ReportSlotType.IMAGE -> "Imagen"
        },
        palette = palette
    ) { selected ->
        val type = when (selected) {
            "Texto" -> ReportSlotType.TEXT
            "Imagen" -> ReportSlotType.IMAGE
            else -> ReportSlotType.EMPTY
        }
        onChange(slot.copy(type = type))
    }
    Spacer(Modifier.height(8.dp))
    when (slot.type) {
        ReportSlotType.EMPTY -> Text("Este espacio quedará vacío.", color = palette.muted, fontSize = 11.sp)
        ReportSlotType.TEXT -> {
            OutlinedTextField(
                value = slot.text,
                onValueChange = { onChange(slot.copy(text = it.take(70))) },
                label = { Text("Texto · máximo 70 caracteres") },
                minLines = 3,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = formFieldColors(palette)
            )
            Text("${slot.text.length}/70 · admite Enter.", color = palette.muted, fontSize = 10.sp)
        }
        ReportSlotType.IMAGE -> {
            val file = File(slot.imagePath)
            val bitmap = remember(slot.imagePath) { ImageOrientationUtils.decodeOrientedBitmap(file, 1200) }
            Box(
                Modifier.fillMaxWidth().height(100.dp)
                    .background(palette.surface2, RoundedCornerShape(10.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                } else Text("Sin imagen", color = palette.muted)
            }
            Spacer(Modifier.height(7.dp))
            LegacySecondaryButton(if (file.exists()) "Cambiar imagen" else "Seleccionar imagen", palette, Modifier.fillMaxWidth(), onPickImage)
        }
    }
}

@Composable
fun FormTemplatePreview(template: FormTemplate, palette: LegacyPalette, modifier: Modifier = Modifier) {
    val normalized = template.normalized()
    val reportColumns = normalized.columns.filterNot { it.hiddenInReport }
    val shownColumns = reportColumns.take(5)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.surface2),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Previsualización del documento", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Representación aproximada de la primera página", color = palette.muted, fontSize = 9.sp)
                }
                Surface(
                    color = palette.blue.copy(alpha = .12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, palette.blue.copy(alpha = .22f))
                ) {
                    Text(
                        "${reportColumns.size} columnas · ${normalized.sections.size} secciones",
                        color = palette.blue,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .aspectRatio(210f / 297f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFB7BDC6)),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(9.dp)) {
                        Row(Modifier.fillMaxWidth().height(68.dp)) {
                            FormPreviewSlot(normalized.leftHeader, Modifier.weight(.19f).fillMaxHeight())
                            Column(
                                Modifier.weight(.62f).fillMaxHeight().border(.7.dp, Color(0xFF555B63)),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    normalized.title,
                                    color = Color(0xFF15171A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    lineHeight = 10.5.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                Divider(color = Color(0xFFD1D5DB), thickness = .6.dp, modifier = Modifier.padding(vertical = 3.dp))
                                Text(
                                    normalized.subtitle,
                                    color = Color(0xFF4B5563),
                                    fontSize = 6.8.sp,
                                    lineHeight = 7.5.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 5.dp)
                                )
                            }
                            FormPreviewSlot(normalized.rightHeader, Modifier.weight(.19f).fillMaxHeight())
                        }

                        Spacer(Modifier.height(5.dp))
                        Row(
                            Modifier.fillMaxWidth().height(18.dp)
                                .background(Color(0xFFF7F8FA))
                                .border(.5.dp, Color(0xFF9CA3AF)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                normalized.name,
                                color = Color(0xFF374151),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 5.7.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 5.dp).weight(1f)
                            )
                            Text("VISTA PREVIA", color = Color(0xFF6B7280), fontSize = 4.8.sp, modifier = Modifier.padding(end = 5.dp))
                        }

                        if (normalized.sections.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            CompactSectionsDocumentPreview(
                                sections = normalized.sections,
                                modifier = Modifier.fillMaxWidth(),
                                maxSections = 2,
                                maxLinesPerSection = 3
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("REGISTRO", color = Color(0xFF1F2937), fontWeight = FontWeight.Bold, fontSize = 6.2.sp)
                            Spacer(Modifier.weight(1f))
                            if (reportColumns.size > shownColumns.size) {
                                Text(
                                    "+${reportColumns.size - shownColumns.size} columna(s)",
                                    color = Color(0xFF6B7280),
                                    fontSize = 5.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))

                        if (shownColumns.isEmpty()) {
                            Box(
                                Modifier.fillMaxWidth().weight(1f).border(.7.dp, Color(0xFF9CA3AF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Todas las columnas están ocultas en el reporte",
                                    color = Color(0xFF6B7280),
                                    fontSize = 7.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().weight(1f)) {
                                Row(Modifier.fillMaxWidth().height(25.dp)) {
                                    PreviewTableCell("ITEM", Modifier.width(42.dp), header = true)
                                    shownColumns.forEach { column ->
                                        PreviewTableCell(column.name, Modifier.weight(1f), header = true)
                                    }
                                }
                                repeat(6) { row ->
                                    Row(Modifier.fillMaxWidth().weight(1f)) {
                                        PreviewTableCell("${row + 1}", Modifier.width(42.dp), header = false)
                                        shownColumns.forEach { column ->
                                            PreviewTableCell(
                                                if (column.cellType == FormCellType.DROPDOWN) "Seleccionar ▾" else "",
                                                Modifier.weight(1f),
                                                header = false,
                                                muted = true
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (normalized.signatures.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            PreviewSignatures(normalized.signatures)
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Inspeccion Photos", color = Color(0xFF9CA3AF), fontSize = 4.5.sp)
                            Spacer(Modifier.weight(1f))
                            Text("1 / 1", color = Color(0xFF9CA3AF), fontSize = 4.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTableCell(
    text: String,
    modifier: Modifier,
    header: Boolean,
    muted: Boolean = false
) {
    Box(
        modifier.fillMaxHeight()
            .background(if (header) Color(0xFFF0F2F5) else Color.White)
            .border(.45.dp, Color(0xFF9CA3AF))
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (muted) Color(0xFF9CA3AF) else Color(0xFF1F2937),
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (header) 5.4.sp else 5.sp,
            lineHeight = 5.8.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewSignatures(signatures: List<SignatureDefinition>) {
    Row(Modifier.fillMaxWidth().height(39.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        signatures.take(3).forEach { signature ->
            val horizontal = when (signature.alignment) {
                SignatureAlignment.LEFT -> Alignment.Start
                SignatureAlignment.CENTER -> Alignment.CenterHorizontally
                SignatureAlignment.RIGHT -> Alignment.End
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = horizontal,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(Modifier.fillMaxWidth(.8f).height(.6.dp).background(Color(0xFF6B7280)))
                Text(
                    signature.name,
                    color = Color(0xFF374151),
                    fontSize = 4.7.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.8f).padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun FormPreviewSlot(slot: ReportHeaderSlot, modifier: Modifier) {
    Box(
        modifier.border(.7.dp, Color(0xFF555B63)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        when (slot.type) {
            ReportSlotType.EMPTY -> Text("", color = Color.Transparent, fontSize = 5.sp)
            ReportSlotType.TEXT -> Text(
                slot.text.take(70),
                color = Color(0xFF1F2937),
                fontSize = 5.4.sp,
                lineHeight = 6.sp,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp)
            )
            ReportSlotType.IMAGE -> {
                val file = File(slot.imagePath)
                val bitmap = remember(slot.imagePath) { ImageOrientationUtils.decodeOrientedBitmap(file, 1200) }
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▧", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                        Text("IMAGEN", color = Color(0xFF9CA3AF), fontSize = 4.5.sp)
                    }
                }
            }
        }
    }
}
