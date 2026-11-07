package ec.espol.inspeccionphotos.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import ec.espol.inspeccionphotos.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FormSortMode { ORIGINAL, ASC, DESC }

@Composable
fun FormsScreen(
    palette: LegacyPalette,
    compact: Boolean,
    templates: List<FormTemplate>,
    store: FormTemplateStore,
    initialOpenSubmissionId: String? = null,
    onMoveToTrash: (FormSubmission) -> Unit = { store.deleteSubmission(it.id) }
) {
    val context = LocalContext.current
    var submissions by remember(templates) { mutableStateOf(store.reconcileSavedSubmissions(templates)) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialOpenSubmissionId) { if (initialOpenSubmissionId != null) activeId = initialOpenSubmissionId }

    val exportSavedJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { store.exportSubmissionsToUri(submissions, uri) }
                .onSuccess { Toast.makeText(context, "Formularios guardados exportados a JSON", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo exportar el JSON", Toast.LENGTH_LONG).show() }
        }
    }
    val importSavedJson = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching { store.importSubmissionsFromUri(uri) }
                .onSuccess { imported ->
                    store.saveSubmissions(submissions + imported)
                    submissions = store.reconcileSavedSubmissions(templates)
                    Toast.makeText(
                        context,
                        if (imported.size == 1) "Formulario importado" else "${imported.size} formularios importados",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo importar el JSON", Toast.LENGTH_LONG).show() }
        }
    }

    val active = submissions.firstOrNull { it.id == activeId }

    if (active != null) {
        FormFillScreen(
            palette = palette,
            compact = compact,
            submission = active,
            onBack = {
                submissions = store.reconcileSavedSubmissions(templates)
                activeId = null
            },
            onChange = { updated ->
                store.upsertSubmission(updated)
                submissions = submissions.map { if (it.id == updated.id) updated else it }
            },
            onComplete = { updated ->
                val completed = updated.copy(completed = true, updatedAt = System.currentTimeMillis()).normalized()
                store.upsertSubmission(completed)
                submissions = store.reconcileSavedSubmissions(templates)
                activeId = null
            },
            onExport = { current ->
                runCatching { FormSubmissionExporter.export(context, current) }
                    .onSuccess { files -> FormSubmissionExporter.share(context, files) }
                    .onFailure { Toast.makeText(context, it.message ?: "No se pudo exportar el formulario", Toast.LENGTH_LONG).show() }
            }
        )
        return
    }

    var deleteCandidate by remember { mutableStateOf<FormSubmission?>(null) }
    var showTemplatePicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Documentos", color = palette.text, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { showTemplatePicker = true }) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_plus_original),
                    contentDescription = "Nuevo formulario", tint = palette.blue)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Toca un formulario para abrirlo.", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        if (submissions.isEmpty()) {
            Text("Todavía no hay formularios. Pulsa + para crear uno.", color = palette.muted)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(submissions.sortedByDescending { it.updatedAt }, key = { it.id }) { submission ->
                    FormSubmissionCard(
                        submission = submission, palette = palette,
                        onOpen = { activeId = submission.id },
                        onDelete = { deleteCandidate = submission }
                    )
                }
            }
        }
    }

    if (showTemplatePicker) {
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text("Elegir formato") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    if (templates.isEmpty()) Text("No hay formatos. Créalos desde Formatos.")
                    templates.sortedBy { it.name.lowercase() }.forEach { template ->
                        Text(
                            template.name, modifier = Modifier.fillMaxWidth().clickable {
                                val submission = store.newSubmission(template)
                                submissions = store.reconcileSavedSubmissions(templates)
                                activeId = submission.id
                                showTemplatePicker = false
                            }.padding(vertical = 14.dp), color = palette.text
                        )
                        Divider(color = palette.border)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTemplatePicker = false }) { Text("Cancelar") } }
        )
    }

    deleteCandidate?.let { submission ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Mover a papelera") },
            text = { Text("¿Mover '${submission.template.name}' a la Papelera? Podrás restaurarlo.") },
            confirmButton = {
                TextButton(onClick = {
                    onMoveToTrash(submission)
                    submissions = store.reconcileSavedSubmissions(templates)
                    deleteCandidate = null
                }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun FormSubmissionCard(
    submission: FormSubmission,
    palette: LegacyPalette,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(submission.template.name, color = palette.text, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatter.format(Date(submission.updatedAt)), color = palette.muted, fontSize = 10.sp)
                    Text(if (submission.completed) "Completado" else "Borrador",
                        color = if (submission.completed) palette.green else palette.cyan, fontSize = 10.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_trash),
                    contentDescription = "Mover formulario a papelera", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FormFillScreen(
    palette: LegacyPalette,
    compact: Boolean,
    submission: FormSubmission,
    onBack: () -> Unit,
    onChange: (FormSubmission) -> Unit,
    onComplete: (FormSubmission) -> Unit,
    onExport: (FormSubmission) -> Unit
) {
    var current by remember(submission.id, submission.updatedAt) { mutableStateOf(submission.normalized()) }
    val template = current.template
    val horizontal = rememberScrollState()
    val visibleColumns = template.columns.withIndex().filter { !it.value.hiddenInApp }
    val tableWidth = (56 + visibleColumns.size.coerceAtLeast(1) * 180).dp
    var sortColumnIndex by rememberSaveable(submission.id) { mutableStateOf(-1) }
    var sortModeName by rememberSaveable(submission.id) { mutableStateOf(FormSortMode.ORIGINAL.name) }
    var showSectionEditor by rememberSaveable(submission.id) { mutableStateOf(false) }
    val sortMode = runCatching { FormSortMode.valueOf(sortModeName) }.getOrDefault(FormSortMode.ORIGINAL)

    fun updateCells(nextCells: List<List<String>>) {
        val updated = current.copy(cells = nextCells, updatedAt = System.currentTimeMillis()).normalized()
        current = updated
        onChange(updated)
    }

    fun updateSectionValues(nextValues: Map<String, String>) {
        val updated = current.copy(
            sectionValues = nextValues.mapValues { it.value.take(500) },
            updatedAt = System.currentTimeMillis()
        ).normalized()
        current = updated
        onChange(updated)
    }

    fun compareValues(a: String, b: String): Int {
        val an = a.trim().replace(',', '.').toDoubleOrNull()
        val bn = b.trim().replace(',', '.').toDoubleOrNull()
        return if (an != null && bn != null) an.compareTo(bn) else a.trim().lowercase().compareTo(b.trim().lowercase())
    }

    val displayRows: List<Pair<Int, List<String>>> = current.cells.mapIndexed { index, row -> index to row }.let { rows ->
        if (sortMode == FormSortMode.ORIGINAL || sortColumnIndex !in template.columns.indices) rows
        else rows.sortedWith { a, b ->
            val cmp = compareValues(a.second.getOrNull(sortColumnIndex).orEmpty(), b.second.getOrNull(sortColumnIndex).orEmpty())
            if (sortMode == FormSortMode.ASC) cmp else -cmp
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 8.dp else 18.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LegacySecondaryButton("← Formularios", palette) { onBack() }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(template.name, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (current.completed) "Completado" else "Borrador guardado automáticamente", color = palette.muted, fontSize = 10.sp)
            }
            LegacyPrimaryButton("Finalizar", palette) { onComplete(current) }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (template.sections.isNotEmpty()) {
                Text("Secciones", color = palette.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(7.dp))
                ReportQuickAction(
                    iconRes = R.drawable.ic_sections_report,
                    label = "Abrir secciones",
                    active = current.sectionValues.values.any { it.isNotBlank() },
                    palette = palette,
                    onClick = { showSectionEditor = true }
                )
            }
            Spacer(Modifier.weight(1f))
            LegacySecondaryButton(
                "Exportar ${template.exportFormats.joinToString("/") { when (it) { FormExportFormat.PDF -> "PDF"; FormExportFormat.WORD -> "Word"; FormExportFormat.EXCEL -> "Excel" } }}",
                palette
            ) { onExport(current) }
        }
        Spacer(Modifier.height(9.dp))

        FormFillHeader(template, palette)
        Spacer(Modifier.height(8.dp))

        if (visibleColumns.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(9.dp)) {
                    Text("Ordenar filas", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = sortMode == FormSortMode.ORIGINAL,
                            onClick = { sortModeName = FormSortMode.ORIGINAL.name; sortColumnIndex = -1 },
                            label = { Text("Original", fontSize = 10.sp) }
                        )
                        visibleColumns.forEach { indexed ->
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                AssistChip(onClick = { expanded = true }, label = {
                                    val suffix = if (sortColumnIndex == indexed.index && sortMode != FormSortMode.ORIGINAL) {
                                        if (sortMode == FormSortMode.ASC) " ↑" else " ↓"
                                    } else ""
                                    Text(indexed.value.name + suffix, fontSize = 10.sp)
                                })
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(text = { Text("A → Z / menor → mayor") }, onClick = { expanded = false; sortColumnIndex = indexed.index; sortModeName = FormSortMode.ASC.name })
                                    DropdownMenuItem(text = { Text("Z → A / mayor → menor") }, onClick = { expanded = false; sortColumnIndex = indexed.index; sortModeName = FormSortMode.DESC.name })
                                    DropdownMenuItem(text = { Text("Orden original") }, onClick = { expanded = false; sortColumnIndex = -1; sortModeName = FormSortMode.ORIGINAL.name })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LegacySecondaryButton("− Fila", palette) {
                if (current.cells.size > 1) updateCells(current.cells.dropLast(1))
            }
            Spacer(Modifier.width(7.dp))
            LegacySecondaryButton("+ Fila", palette) {
                if (current.cells.size < 100) updateCells(current.cells + listOf(List(template.columns.size) { "" }))
            }
        }
        Spacer(Modifier.height(7.dp))

        Box(Modifier.fillMaxWidth().weight(1f).horizontalScroll(horizontal)) {
            Column(Modifier.width(tableWidth).fillMaxHeight()) {
                FormTableHeader(visibleColumns, palette)
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(displayRows, key = { it.first }) { indexedRow ->
                        val sourceRowIndex = indexedRow.first
                        val row = indexedRow.second
                        FormTableRow(
                            rowIndex = sourceRowIndex,
                            row = row,
                            columns = visibleColumns,
                            palette = palette,
                            onCellChange = { columnIndex, value ->
                                val next = current.cells.mapIndexed { rIndex, existingRow ->
                                    if (rIndex != sourceRowIndex) existingRow else List(template.columns.size) { cIndex ->
                                        if (cIndex == columnIndex) value.take(500) else existingRow.getOrNull(cIndex).orEmpty()
                                    }
                                }
                                updateCells(next)
                            }
                        )
                    }
                }
            }
        }
        if (template.signatures.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SignaturePreview(template.signatures, palette, Modifier.fillMaxWidth())
        }
    }

    if (showSectionEditor && template.sections.isNotEmpty()) {
        FormSectionValuesDialog(
            template = template,
            values = current.sectionValues,
            palette = palette,
            compact = compact,
            onDismiss = { showSectionEditor = false },
            onSave = { values ->
                updateSectionValues(values)
                showSectionEditor = false
            },
            onClear = {
                updateSectionValues(emptyMap())
                showSectionEditor = false
            }
        )
    }
}

@Composable
private fun FormSectionValuesDialog(
    template: FormTemplate,
    values: Map<String, String>,
    palette: LegacyPalette,
    compact: Boolean,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onClear: () -> Unit
) {
    val normalizedSections = remember(template.id, template.sections) { template.sections.map { it.normalized() } }
    val valueCellIds = remember(normalizedSections) {
        normalizedSections.flatMap { section ->
            section.lines.flatMap { line -> line.cells.filter { it.mode == SectionCellMode.VALUE }.map { it.id } }
        }.toSet()
    }
    var draft by remember(template.id, values, valueCellIds) {
        mutableStateOf(valueCellIds.associateWith { values[it].orEmpty() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(if (compact) .97f else .86f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        containerColor = palette.surface,
        title = {
            Column {
                Text("Secciones · ${template.name}", color = palette.text, fontWeight = FontWeight.Bold)
                Text(
                    "Rellena aquí las descripciones del formulario. La tabla permanece visible y utilizable en la pantalla principal.",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = if (compact) 590.dp else 650.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionValuesEditor(
                    sections = normalizedSections,
                    values = draft,
                    palette = palette,
                    onValueChange = { cellId, text ->
                        if (cellId in valueCellIds) draft = draft.toMutableMap().apply { this[cellId] = text.take(500) }.toMap()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.filterKeys { it in valueCellIds }) }) {
                Text("Guardar", color = palette.blue)
            }
        },
        dismissButton = {
            Row {
                if (values.values.any { it.isNotBlank() } || draft.values.any { it.isNotBlank() }) {
                    TextButton(onClick = onClear) { Text("Limpiar", color = palette.muted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.muted) }
            }
        }
    )
}

@Composable
private fun FormFillHeader(template: FormTemplate, palette: LegacyPalette) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            FillHeaderSlot(template.leftHeader, Modifier.weight(.18f).fillMaxHeight())
            Column(
                Modifier.weight(.64f).fillMaxHeight().border(1.dp, Color.Gray),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(template.title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                Divider(color = Color.LightGray)
                Text(template.subtitle, color = Color.DarkGray, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
            FillHeaderSlot(template.rightHeader, Modifier.weight(.18f).fillMaxHeight())
        }
    }
}

@Composable
private fun FillHeaderSlot(slot: ReportHeaderSlot, modifier: Modifier) {
    Box(modifier.border(1.dp, Color.Gray), contentAlignment = Alignment.Center) {
        when (slot.type) {
            ReportSlotType.EMPTY -> Unit
            ReportSlotType.TEXT -> Text(slot.text, color = Color.Black, fontSize = 7.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(3.dp), maxLines = 4)
            ReportSlotType.IMAGE -> {
                val file = File(slot.imagePath)
                val bitmap = remember(slot.imagePath) { ImageOrientationUtils.decodeOrientedBitmap(file, 1200) }
                if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
                else Text("IMAGEN", color = Color.Gray, fontSize = 6.sp)
            }
        }
    }
}

@Composable
private fun FormTableHeader(columns: List<IndexedValue<FormColumnDefinition>>, palette: LegacyPalette) {
    Row(Modifier.height(48.dp)) {
        Box(
            Modifier.width(56.dp).fillMaxHeight().background(palette.surface2).border(1.dp, palette.border),
            contentAlignment = Alignment.Center
        ) { Text("ITEM", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        columns.forEach { indexed ->
            val column = indexed.value
            Box(
                Modifier.width(180.dp).fillMaxHeight().background(palette.surface2).border(1.dp, palette.border).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(column.name, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
                    if (column.cellType == FormCellType.DROPDOWN) Text("Desplegable", color = palette.muted, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun FormTableRow(
    rowIndex: Int,
    row: List<String>,
    columns: List<IndexedValue<FormColumnDefinition>>,
    palette: LegacyPalette,
    onCellChange: (Int, String) -> Unit
) {
    Row(Modifier.heightIn(min = 58.dp)) {
        Box(
            Modifier.width(56.dp).fillMaxHeight().border(1.dp, palette.border),
            contentAlignment = Alignment.Center
        ) { Text("${rowIndex + 1}", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp) }
        columns.forEach { indexed ->
            val columnIndex = indexed.index
            val column = indexed.value
            val value = row.getOrNull(columnIndex).orEmpty()
            when (column.cellType) {
                FormCellType.FREE_TEXT -> OutlinedTextField(
                    value = value,
                    onValueChange = { onCellChange(columnIndex, it) },
                    modifier = Modifier.width(180.dp).padding(horizontal = 3.dp, vertical = 2.dp),
                    singleLine = true,
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
                FormCellType.DROPDOWN -> FormDropdownCell(
                    value = value,
                    options = column.options,
                    palette = palette,
                    onSelected = { onCellChange(columnIndex, it) }
                )
            }
        }
    }
}

@Composable
private fun FormDropdownCell(value: String, options: List<String>, palette: LegacyPalette, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.width(180.dp).padding(horizontal = 3.dp, vertical = 6.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            border = BorderStroke(1.dp, palette.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(value.ifBlank { if (options.isEmpty()) "Sin opciones" else "Seleccionar" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("⌄", color = palette.muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = palette.surface2) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text("No hay opciones configuradas", color = palette.muted) }, onClick = { expanded = false })
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = palette.text) },
                        onClick = { expanded = false; onSelected(option) }
                    )
                }
                if (value.isNotBlank()) {
                    Divider(color = palette.border)
                    DropdownMenuItem(text = { Text("Limpiar", color = palette.muted) }, onClick = { expanded = false; onSelected("") })
                }
            }
        }
    }
}
