package ec.espol.inspeccionphotos.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ec.espol.inspeccionphotos.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Visor fiel: usa Android PdfRenderer sobre el mismo PDF del exportador. */
@Composable
fun ExactPdfPreview(
    palette: LegacyPalette,
    refreshKey: Any,
    createFile: () -> File,
    modifier: Modifier = Modifier,
    maxPages: Int = 4
) {
    var error by remember(refreshKey) { mutableStateOf<String?>(null) }
    val pages by produceState<List<Bitmap>>(emptyList(), refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pdf = createFile()
                require(pdf.exists() && pdf.length() > 0) { "El PDF generado está vacío" }
                val images = mutableListOf<Bitmap>()
                ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                            renderer.openPage(i).use { page ->
                                val width = 780
                                val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                images += bitmap
                            }
                        }
                    }
                }
                images
            }.onFailure { error = it.message ?: "No se pudo renderizar el PDF" }.getOrDefault(emptyList())
        }
    }
    Column(modifier) {
        if (pages.isEmpty()) {
            if (error == null) CircularProgressIndicator(color = palette.blue, modifier = Modifier.align(Alignment.CenterHorizontally))
            else Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } else {
            pages.forEachIndexed { i, page ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(5.dp),
                    border = BorderStroke(1.dp, palette.border),
                    color = androidx.compose.ui.graphics.Color.White
                ) {
                    Image(page.asImageBitmap(), "Página ${i + 1} del PDF", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}

@Composable
fun FolderSectionPreviewDialog(
    folder: Inspection,
    config: CustomReportConfig,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onEditTable: () -> Unit,
    onEditSections: () -> Unit,
    onEditSectionHeading: () -> Unit,
    onEditMainImage: () -> Unit,
    onEditObservation: () -> Unit,
    onToggleTable: () -> Unit,
    onToggleMainImage: () -> Unit,
    onToggleSections: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = palette.bg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("‹", fontSize = 28.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Vista real del PDF · Esta sección", color = palette.muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showMenu = true }) { Text("⋮", fontSize = 25.sp) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onEditTable) { Text("Tabla", fontSize = 11.sp) }
                    TextButton(onClick = onEditSections) { Text("Sección", fontSize = 11.sp) }
                    TextButton(onClick = onEditSectionHeading) { Text("Encabezado", fontSize = 11.sp) }
                    TextButton(onClick = onEditMainImage) { Text("Imagen", fontSize = 11.sp) }
                    TextButton(onClick = onEditObservation) { Text("Observación", fontSize = 11.sp) }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    item {
                        // Se excluyen los descendientes para renderizar únicamente la carpeta seleccionada.
                        ExactPdfPreview(
                            palette = palette,
                            refreshKey = folder to config,
                            createFile = { CustomReportExporter.createPdf(context, folder.copy(children = emptyList()), config) }
                        )
                    }
                }
            }
        }
    }
    if (showMenu) {
        LegacyMenuDialog(
            title = "Mostrar u ocultar en el reporte",
            options = listOf(
                (if (folder.reportTableVisible) "Ocultar tabla" else "Mostrar tabla") to onToggleTable,
                (if (folder.reportMainImageVisible) "Ocultar imagen principal" else "Mostrar imagen principal") to onToggleMainImage,
                (if (folder.reportSectionVisible) "Ocultar sección" else "Mostrar sección") to onToggleSections
            ),
            onDismiss = { showMenu = false },
            palette = palette
        )
    }
}
