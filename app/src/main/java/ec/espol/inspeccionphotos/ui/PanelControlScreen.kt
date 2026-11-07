package ec.espol.inspeccionphotos.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@Composable
fun PanelControlScreen(
    palette: LegacyPalette,
    compact: Boolean,
    config: CustomReportConfig,
    store: CustomReportConfigStore,
    formTemplates: List<FormTemplate>,
    formStore: FormTemplateStore,
    onConfigChange: (CustomReportConfig) -> Unit,
    onFormTemplatesChange: (List<FormTemplate>) -> Unit,
    onTemplateTrash: (FormTemplate) -> Unit = {},
    defaultFormTemplateId: String = "",
    onDefaultFormTemplateChange: (String) -> Unit = {},
    photoPresets: List<PhotoReportPreset> = emptyList(),
    activePhotoPresetId: String = "",
    defaultPhotoReport: String = "PDF 3",
    onPhotoPresetSelect: (String) -> Unit = {},
    onPhotoPresetNew: () -> Unit = {},
    onPhotoPresetDuplicate: () -> Unit = {},
    onPhotoPresetRename: () -> Unit = {},
    onPhotoPresetTrash: () -> Unit = {},
    onDefaultPhotoReportChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var panelTab by rememberSaveable { mutableStateOf("Fotos") }
    var photoTab by rememberSaveable { mutableStateOf("Encabezado") }
    var imageSide by remember { mutableStateOf("left") }
    var cropSide by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val path = store.importHeaderImage(uri, imageSide)
            val next = if (imageSide == "left") {
                config.copy(leftHeader = config.leftHeader.copy(type = ReportSlotType.IMAGE, imagePath = path))
            } else {
                config.copy(rightHeader = config.rightHeader.copy(type = ReportSlotType.IMAGE, imagePath = path))
            }
            onConfigChange(next.normalized())
        }.onFailure {
            Toast.makeText(context, it.message ?: "No se pudo cargar la imagen", Toast.LENGTH_LONG).show()
        }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { store.exportToUri(config, uri) }
            .onSuccess { Toast.makeText(context, "Configuración exportada", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, it.message ?: "No se pudo exportar", Toast.LENGTH_LONG).show() }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { store.importFromUri(uri) }
            .onSuccess { onConfigChange(it); Toast.makeText(context, "Configuración importada", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, it.message ?: "JSON no válido", Toast.LENGTH_LONG).show() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 10.dp)
    ) {
        if (!compact) {
            LegacyTitle("Formatos", palette, 25)
            Spacer(Modifier.height(3.dp))
            LegacySubtitle("Configura por separado el reporte fotográfico y los formularios.", palette)
            Spacer(Modifier.height(12.dp))
        }

        // Pestañas principales siempre visibles. En teléfono no desaparecen al hacer scroll.
        LegacySegmentedChoice(
            listOf("Fotos", "Formularios"),
            panelTab,
            palette
        ) { panelTab = it }
        Spacer(Modifier.height(8.dp))

        if (panelTab == "Fotos") {
            PhotoControlTabs(photoTab, palette) { photoTab = it }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
            if (panelTab == "Fotos") {
                PhotoPresetControls(
                    palette, photoPresets, activePhotoPresetId, defaultPhotoReport,
                    onSelect = onPhotoPresetSelect,
                    onNew = onPhotoPresetNew,
                    onDuplicate = onPhotoPresetDuplicate,
                    onRename = onPhotoPresetRename,
                    onTrash = onPhotoPresetTrash,
                    onDefault = onDefaultPhotoReportChange
                )
                Spacer(Modifier.height(12.dp))
                when (photoTab) {
                    "Encabezado" -> {
                        PanelSection("Encabezado del reporte", palette) {
                            Text(
                                "Título y subtítulo se ajustan automáticamente al ancho disponible en el PDF.",
                                color = palette.muted,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            ReportTextField("Título", config.title, palette, 120) { onConfigChange(config.copy(title = it)) }
                            Spacer(Modifier.height(8.dp))
                            ReportTextField("Subtítulo", config.subtitle, palette, 160) { onConfigChange(config.copy(subtitle = it)) }
                            Spacer(Modifier.height(14.dp))
                            Text("Espacio 3 · izquierda", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            HeaderSlotEditor(
                                config.leftHeader,
                                palette,
                                onChange = { onConfigChange(config.copy(leftHeader = it)) },
                                onPickImage = { imageSide = "left"; imagePicker.launch("image/*") },
                                onCropImage = { cropSide = "left" }
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("Espacio 4 · derecha", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            HeaderSlotEditor(
                                config.rightHeader,
                                palette,
                                onChange = { onConfigChange(config.copy(rightHeader = it)) },
                                onPickImage = { imageSide = "right"; imagePicker.launch("image/*") },
                                onCropImage = { cropSide = "right" }
                            )
                        }
                    }
    
                    "Diseño" -> {
                        PanelSection("Imagen principal y tabla", palette) {
                            SettingsChoiceRowPublic(
                                "Alineación",
                                layoutLabel(config.mainBlockLayout),
                                listOf("Imagen izquierda", "Imagen derecha", "Imagen arriba", "Imagen abajo"),
                                palette
                            ) { label -> onConfigChange(config.copy(mainBlockLayout = layoutFromLabel(label))) }
                            Divider(color = palette.border.copy(alpha = .55f))
                            SettingsChoiceRowPublic(
                                "Ancho de imagen",
                                "${config.mainImageWidthPercent}%",
                                (25..80 step 5).map { "$it%" },
                                palette
                            ) { label ->
                                onConfigChange(config.copy(mainImageWidthPercent = label.removeSuffix("%").toIntOrNull() ?: 55))
                            }
                            Divider(color = palette.border.copy(alpha = .55f))
                            NumberChoice("Filas de tabla", config.tableRows, 1..12, palette) { rows ->
                                onConfigChange(
                                    config.copy(
                                        tableRows = rows,
                                        rowHeaders = List(rows) { config.rowHeaders.getOrNull(it) ?: "F${it + 1}" }
                                    ).normalized()
                                )
                            }
                            NumberChoice("Columnas de tabla", config.tableColumns, 1..8, palette) { cols ->
                                onConfigChange(
                                    config.copy(
                                        tableColumns = cols,
                                        columnHeaders = List(cols) { config.columnHeaders.getOrNull(it) ?: "C${it + 1}" }
                                    ).normalized()
                                )
                            }
                        }
    
                        Spacer(Modifier.height(12.dp))
                        PanelSection("Nombres de la tabla", palette) {
                            Text("Filas", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            config.rowHeaders.take(config.tableRows).forEachIndexed { i, value ->
                                ReportTextField("Fila ${i + 1}", value, palette, 20) { nv ->
                                    val list = config.rowHeaders.toMutableList().apply {
                                        while (size < config.tableRows) add("")
                                        this[i] = nv
                                    }
                                    onConfigChange(config.copy(rowHeaders = list))
                                }
                                if (i < config.tableRows - 1) Spacer(Modifier.height(5.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Columnas", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            config.columnHeaders.take(config.tableColumns).forEachIndexed { i, value ->
                                ReportTextField("Columna ${i + 1}", value, palette, 20) { nv ->
                                    val list = config.columnHeaders.toMutableList().apply {
                                        while (size < config.tableColumns) add("")
                                        this[i] = nv
                                    }
                                    onConfigChange(config.copy(columnHeaders = list))
                                }
                                if (i < config.tableColumns - 1) Spacer(Modifier.height(5.dp))
                            }
                        }
    
                        Spacer(Modifier.height(12.dp))
                        PanelSection("Evidencias fotográficas", palette) {
                            Text("Imágenes por fila", color = palette.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            LegacySegmentedChoice(listOf("1", "2", "3", "4"), config.evidenceColumns.toString(), palette) {
                                onConfigChange(config.copy(evidenceColumns = it.toInt()))
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "Las filas incompletas se centran automáticamente para mantener una distribución simétrica.",
                                color = palette.muted,
                                fontSize = 10.sp
                            )
                        }
                    }
    

                    "Secciones" -> {
                        PanelSection("Secciones y firmas", palette) {
                            Text(
                                "Posición respecto a la imagen principal + tabla",
                                color = palette.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(7.dp))
                            LegacySegmentedChoice(
                                listOf("Encima", "Debajo"),
                                if (config.sectionPlacement == SectionPlacement.ABOVE_MAIN_BLOCK) "Encima" else "Debajo",
                                palette
                            ) { selected ->
                                onConfigChange(
                                    config.copy(
                                        sectionPlacement = if (selected == "Encima") {
                                            SectionPlacement.ABOVE_MAIN_BLOCK
                                        } else {
                                            SectionPlacement.BELOW_MAIN_BLOCK
                                        }
                                    ).normalized()
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "La vista previa y el reporte exportado respetan esta posición en cada carpeta o subcarpeta.",
                                color = palette.muted,
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            SectionDesigner(
                                sections = config.sections,
                                signatures = config.signatures,
                                palette = palette,
                                onSectionsChange = { onConfigChange(config.copy(sections = it).normalized()) },
                                onSignaturesChange = { onConfigChange(config.copy(signatures = it).normalized()) }
                            )
                        }
                    }

                    "Salida" -> {
                        PanelSection("Formatos de salida", palette) {
                            Text("El Reporte Personalizado puede generarse en uno o varios formatos.", color = palette.muted, fontSize = 11.sp)
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReportFormatToggle("PDF", ReportExportFormat.PDF, config, palette, onConfigChange)
                                ReportFormatToggle("Word", ReportExportFormat.WORD, config, palette, onConfigChange)
                                ReportFormatToggle("Excel", ReportExportFormat.EXCEL, config, palette, onConfigChange)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        PanelSection("Plantilla JSON", palette) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LegacySecondaryButton("Exportar JSON", palette, Modifier.weight(1f)) {
                                    exportJson.launch("reporte_personalizado.json")
                                }
                                LegacySecondaryButton("Importar JSON", palette, Modifier.weight(1f)) {
                                    importJson.launch("application/json")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "El JSON portátil incluye las imágenes de los espacios 3 y 4 para poder mover la plantilla a otro teléfono.",
                                color = palette.muted,
                                fontSize = 10.sp
                            )
                        }
                    }
    
                    else -> Unit
                }
                Spacer(Modifier.height(20.dp))
                Text("Vista previa exacta del PDF", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("Se renderiza el mismo archivo que se exporta. El contenido es un ejemplo.",
                    color = palette.muted, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                ExactPdfPreview(
                    palette = palette,
                    refreshKey = config,
                    createFile = {
                        val previewValues = config.sections.flatMap { it.lines }.flatMap { it.cells }
                            .filter { it.mode == SectionCellMode.VALUE }
                            .associate { it.id to "Dato de ejemplo" }
                        val sample = Inspection(
                            id = "sample-format",
                            name = "EJEMPLO DE INSPECCIÓN",
                            created = "2026-11-06",
                            reportTableEnabled = true,
                            reportTableValues = List(config.tableRows) { r ->
                                List(config.tableColumns) { c -> "Dato ${r + 1}.${c + 1}" }
                            },
                            reportSectionValues = previewValues
                        )
                        CustomReportExporter.createPdf(context, sample, config)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxPages = 6
                )
            } else {
                FormTemplateManager(
                    palette = palette,
                    compact = compact,
                    templates = formTemplates,
                    store = formStore,
                    onTemplatesChange = onFormTemplatesChange,
                    onMoveToTrash = onTemplateTrash,
                    defaultTemplateId = defaultFormTemplateId,
                    onDefaultTemplateChange = onDefaultFormTemplateChange
                )
            }
        }
    }
            }
        }
    cropSide?.let { side ->
        val slot = if (side == "left") config.leftHeader else config.rightHeader
        if (slot.imagePath.isNotBlank()) {
            HeaderImageCropDialog(
                imagePath = slot.imagePath,
                palette = palette,
                onDismiss = { cropSide = null },
                onSave = { path ->
                    val cropped = slot.copy(type = ReportSlotType.IMAGE, imagePath = path)
                    onConfigChange(if (side == "left") config.copy(leftHeader = cropped) else config.copy(rightHeader = cropped))
                    cropSide = null
                }
            )
        } else cropSide = null
    }
}

@Composable
private fun PhotoControlTabs(selected: String, palette: LegacyPalette, onSelected: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf("Encabezado", "Diseño", "Secciones", "Salida").forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(item, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = palette.surface,
                    labelColor = palette.text,
                    selectedContainerColor = palette.blue,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun PanelSection(title: String, palette: LegacyPalette, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(7.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun ReportTextField(label: String, value: String, palette: LegacyPalette, maxChars: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(maxChars)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        maxLines = 4,
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

@Composable
private fun HeaderSlotEditor(
    slot: ReportHeaderSlot,
    palette: LegacyPalette,
    onChange: (ReportHeaderSlot) -> Unit,
    onPickImage: () -> Unit,
    onCropImage: () -> Unit
) {
    Spacer(Modifier.height(6.dp))
    LegacySegmentedChoice(
        listOf("Vacío", "Texto", "Imagen"),
        when (slot.type) {
            ReportSlotType.EMPTY -> "Vacío"
            ReportSlotType.TEXT -> "Texto"
            ReportSlotType.IMAGE -> "Imagen"
        },
        palette
    ) { value ->
        onChange(
            slot.copy(
                type = when (value) {
                    "Texto" -> ReportSlotType.TEXT
                    "Imagen" -> ReportSlotType.IMAGE
                    else -> ReportSlotType.EMPTY
                }
            )
        )
    }
    Spacer(Modifier.height(8.dp))
    when (slot.type) {
        ReportSlotType.EMPTY -> Text("Este espacio quedará vacío.", color = palette.muted, fontSize = 11.sp)
        ReportSlotType.TEXT -> {
            OutlinedTextField(
                value = slot.text,
                onValueChange = { onChange(slot.copy(text = it.take(70))) },
                label = { Text("Texto (máx. 70 caracteres)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 4,
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
            Text("${slot.text.length}/70 · admite Enter y queda centrado.", color = palette.muted, fontSize = 10.sp)
        }
        ReportSlotType.IMAGE -> {
            val file = File(slot.imagePath)
            val bitmap = remember(slot.imagePath) { ImageOrientationUtils.decodeOrientedBitmap(file, 1200) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(palette.surface2, RoundedCornerShape(10.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                } else {
                    Text("Sin imagen", color = palette.muted)
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LegacySecondaryButton(if (file.exists()) "Cambiar" else "Subir imagen",
                    palette, Modifier.weight(1f), onPickImage)
                if (file.exists()) {
                    LegacySecondaryButton("Recortar", palette, Modifier.weight(1f), onCropImage)
                    LegacySecondaryButton("Eliminar", palette, Modifier.weight(1f)) {
                        onChange(slot.copy(type = ReportSlotType.EMPTY, imagePath = ""))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportFormatToggle(
    label: String,
    format: ReportExportFormat,
    config: CustomReportConfig,
    palette: LegacyPalette,
    onChange: (CustomReportConfig) -> Unit
) {
    val checked = format in config.exportFormats
    FilterChip(
        selected = checked,
        onClick = {
            val next = config.exportFormats.toMutableSet()
            if (checked && next.size > 1) next.remove(format) else next.add(format)
            onChange(config.copy(exportFormats = next))
        },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.blue.copy(alpha = .25f),
            selectedLabelColor = palette.text,
            labelColor = palette.text
        )
    )
}

@Composable
private fun NumberChoice(label: String, value: Int, range: IntRange, palette: LegacyPalette, onChange: (Int) -> Unit) {
    SettingsChoiceRowPublic(label, value.toString(), range.map(Int::toString), palette) { onChange(it.toInt()) }
}

@Composable
fun SettingsChoiceRowPublic(
    label: String,
    value: String,
    options: List<String>,
    palette: LegacyPalette,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                border = BorderStroke(1.dp, palette.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.text)
            ) {
                Text(value, fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text("⌄", color = palette.muted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = palette.surface2) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = palette.text) },
                        onClick = { expanded = false; onSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomReportPreview(config: CustomReportConfig, palette: LegacyPalette, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreviewBadge("A4 · página 1", palette)
            PreviewBadge("${config.evidenceColumns} evidencia(s) por fila", palette)
            PreviewBadge("Tabla ${config.tableRows}×${config.tableColumns}", palette)
            if (config.sections.isNotEmpty()) {
                PreviewBadge("${config.sections.size} sección(es)", palette)
                PreviewBadge(if (config.sectionPlacement == SectionPlacement.ABOVE_MAIN_BLOCK) "Secciones arriba" else "Secciones abajo", palette)
            }
            PreviewBadge(layoutLabel(config.mainBlockLayout), palette)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(palette.surface2, RoundedCornerShape(12.dp))
                .padding(10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .aspectRatio(210f / 297f),
                border = BorderStroke(1.dp, Color(0xFFB8BEC7)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(3.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(7.dp)) {
                    Row(Modifier.fillMaxWidth().weight(.145f)) {
                        PreviewSlot(config.leftHeader, Modifier.weight(.18f).fillMaxHeight())
                        Column(
                            Modifier.weight(.64f).fillMaxHeight().border(.7.dp, Color.Gray),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.weight(.66f).fillMaxWidth().padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    config.title.ifBlank { "TÍTULO DEL REPORTE" },
                                    color = Color.Black,
                                    fontSize = 8.5.sp,
                                    lineHeight = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Divider(color = Color.LightGray, thickness = .6.dp)
                            Box(Modifier.weight(.34f).fillMaxWidth().padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    config.subtitle,
                                    color = Color.DarkGray,
                                    fontSize = 6.sp,
                                    lineHeight = 6.8.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        PreviewSlot(config.rightHeader, Modifier.weight(.18f).fillMaxHeight())
                    }

                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().weight(.035f).border(.6.dp, Color.Gray).background(Color(0xFFF8F9FB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SEPTIEMBRE / CLIENTE A / PLANTA 1 / EQUIPO 001",
                            color = Color(0xFF333333), fontSize = 4.9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(5.dp))

                    if (config.sections.isNotEmpty() && config.sectionPlacement == SectionPlacement.ABOVE_MAIN_BLOCK) {
                        CompactSectionsDocumentPreview(
                            sections = config.sections,
                            modifier = Modifier.fillMaxWidth().weight(.18f),
                            maxSections = 2,
                            maxLinesPerSection = 3
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    PreviewMainBlockV2(config, Modifier.fillMaxWidth().weight(if (config.sections.isEmpty()) .275f else .22f))

                    if (config.sections.isNotEmpty() && config.sectionPlacement == SectionPlacement.BELOW_MAIN_BLOCK) {
                        Spacer(Modifier.height(4.dp))
                        CompactSectionsDocumentPreview(
                            sections = config.sections,
                            modifier = Modifier.fillMaxWidth().weight(.18f),
                            maxSections = 2,
                            maxLinesPerSection = 3
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().height(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("EVIDENCIAS FOTOGRÁFICAS", color = Color(0xFF202020), fontWeight = FontWeight.Bold, fontSize = 5.8.sp)
                        Spacer(Modifier.weight(1f))
                        Text("Ejemplo", color = Color.Gray, fontSize = 5.sp)
                    }

                    PreviewEvidenceGrid(config.evidenceColumns, Modifier.fillMaxWidth().weight(if (config.sections.isEmpty()) .47f else .34f))
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth().weight(.025f), verticalAlignment = Alignment.CenterVertically) {
                        Text("Inspeccion Photos", color = Color.Gray, fontSize = 4.6.sp)
                        Spacer(Modifier.weight(1f))
                        Text("1 / 1", color = Color.Gray, fontSize = 4.6.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Vista previa a escala con datos simulados. El PDF final conserva esta distribución y reemplaza los ejemplos con el contenido de cada carpeta.",
            color = palette.muted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PreviewBadge(text: String, palette: LegacyPalette) {
    Surface(
        color = palette.surface,
        border = BorderStroke(1.dp, palette.border),
        shape = RoundedCornerShape(50)
    ) {
        Text(text, color = palette.text, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun PreviewSlot(slot: ReportHeaderSlot, modifier: Modifier) {
    Box(modifier.border(.7.dp, Color.Gray), contentAlignment = Alignment.Center) {
        when (slot.type) {
            ReportSlotType.EMPTY -> Text("VACÍO", color = Color.LightGray, fontSize = 4.5.sp)
            ReportSlotType.TEXT -> Text(
                slot.text.take(70),
                color = Color.Black,
                fontSize = 5.3.sp,
                lineHeight = 6.sp,
                textAlign = TextAlign.Center,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(3.dp)
            )
            ReportSlotType.IMAGE -> {
                val file = File(slot.imagePath)
                val bitmap = remember(slot.imagePath) { ImageOrientationUtils.decodeOrientedBitmap(file, 1200) }
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Fit)
                } else {
                    Text("IMAGEN", fontSize = 5.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun PreviewMainBlockV2(config: CustomReportConfig, modifier: Modifier = Modifier) {
    val image: @Composable (Modifier) -> Unit = { m ->
        Box(m.border(.7.dp, Color.Gray).background(Color(0xFFF1F3F6)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▧", color = Color(0xFF7F8A98), fontSize = 15.sp)
                Text("Imagen principal", fontSize = 5.4.sp, color = Color.Gray)
                Text("tomada o agregada en la carpeta", fontSize = 4.2.sp, color = Color.LightGray)
            }
        }
    }
    val table: @Composable (Modifier) -> Unit = { m ->
        val cols = config.tableColumns.coerceIn(1, 8)
        val rows = config.tableRows.coerceIn(1, 12).coerceAtMost(5)
        Column(m.border(.7.dp, Color.Gray)) {
            Row(Modifier.weight(1f)) {
                repeat(cols + 1) { index ->
                    Box(Modifier.weight(if (index == 0) .72f else 1f).fillMaxHeight().border(.35.dp, Color.LightGray), contentAlignment = Alignment.Center) {
                        Text(
                            if (index == 0) "ITEM" else config.columnHeaders.getOrNull(index - 1).orEmpty(),
                            fontSize = if (cols > 5) 3.6.sp else 4.4.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            repeat(rows) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(cols + 1) { column ->
                        Box(Modifier.weight(if (column == 0) .72f else 1f).fillMaxHeight().border(.35.dp, Color.LightGray), contentAlignment = Alignment.Center) {
                            Text(
                                if (column == 0) config.rowHeaders.getOrNull(row).orEmpty()
                                else listOf("OK", "12.4", "A-01", "—").getOrElse((row + column) % 4) { "" },
                                fontSize = if (cols > 5) 3.4.sp else 4.2.sp,
                                color = if (column == 0) Color.Black else Color.DarkGray,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    when (config.mainBlockLayout) {
        MainBlockLayout.IMAGE_LEFT_TABLE -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            image(Modifier.weight(config.mainImageWidthPercent.toFloat()).fillMaxHeight())
            table(Modifier.weight((100 - config.mainImageWidthPercent).toFloat()).fillMaxHeight())
        }
        MainBlockLayout.IMAGE_RIGHT_TABLE -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            table(Modifier.weight((100 - config.mainImageWidthPercent).toFloat()).fillMaxHeight())
            image(Modifier.weight(config.mainImageWidthPercent.toFloat()).fillMaxHeight())
        }
        MainBlockLayout.IMAGE_ABOVE_TABLE -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            image(Modifier.fillMaxWidth(config.mainImageWidthPercent / 100f).weight(.52f))
            table(Modifier.fillMaxWidth().weight(.48f))
        }
        MainBlockLayout.IMAGE_BELOW_TABLE -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            table(Modifier.fillMaxWidth().weight(.48f))
            image(Modifier.fillMaxWidth(config.mainImageWidthPercent / 100f).weight(.52f))
        }
    }
}

@Composable
private fun PreviewEvidenceGrid(columns: Int, modifier: Modifier = Modifier) {
    val cols = columns.coerceIn(1, 4)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(cols) { col ->
                    val number = row * cols + col + 1
                    Column(
                        Modifier.weight(1f).fillMaxHeight().border(.6.dp, Color(0xFFC4C9D0)).background(Color.White)
                    ) {
                        Box(Modifier.fillMaxWidth().weight(.72f).background(Color(0xFFE7EAEE)), contentAlignment = Alignment.Center) {
                            Text("FOTO", color = Color(0xFF8D96A1), fontSize = if (cols >= 4) 4.6.sp else 5.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.fillMaxWidth().weight(.28f).padding(horizontal = 2.dp, vertical = 1.dp)) {
                            Text("001_${number.toString().padStart(2, '0')}.jpg", color = Color.Black, fontSize = if (cols >= 4) 3.7.sp else 4.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Sin observación", color = Color.Gray, fontSize = if (cols >= 4) 3.2.sp else 4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun layoutLabel(value: MainBlockLayout) = when (value) {
    MainBlockLayout.IMAGE_LEFT_TABLE -> "Imagen izquierda"
    MainBlockLayout.IMAGE_RIGHT_TABLE -> "Imagen derecha"
    MainBlockLayout.IMAGE_ABOVE_TABLE -> "Imagen arriba"
    MainBlockLayout.IMAGE_BELOW_TABLE -> "Imagen abajo"
}

private fun layoutFromLabel(value: String) = when (value) {
    "Imagen derecha" -> MainBlockLayout.IMAGE_RIGHT_TABLE
    "Imagen arriba" -> MainBlockLayout.IMAGE_ABOVE_TABLE
    "Imagen abajo" -> MainBlockLayout.IMAGE_BELOW_TABLE
    else -> MainBlockLayout.IMAGE_LEFT_TABLE
}
