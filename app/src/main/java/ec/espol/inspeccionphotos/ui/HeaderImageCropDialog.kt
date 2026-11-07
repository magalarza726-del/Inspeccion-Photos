package ec.espol.inspeccionphotos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ec.espol.inspeccionphotos.ImageOrientationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/** Recorta una copia; el original del encabezado nunca se sobrescribe. */
@Composable
fun HeaderImageCropDialog(
    imagePath: String,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember(imagePath) {
        runCatching { ImageOrientationUtils.decodeOrientedBitmap(File(imagePath), 2200) }.getOrNull()
    }
    var ratioName by remember(imagePath) { mutableStateOf("Original") }
    var fraction by remember(imagePath) { mutableFloatStateOf(1f) }
    var horizontal by remember(imagePath) { mutableFloatStateOf(0.5f) }
    var vertical by remember(imagePath) { mutableFloatStateOf(0.5f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val crop = remember(original, ratioName, fraction, horizontal, vertical) {
        original?.let { source ->
            val ratio = when (ratioName) {
                "1:1" -> 1f
                "4:3" -> 4f / 3f
                "16:9" -> 16f / 9f
                "3:4" -> 3f / 4f
                else -> source.width.toFloat() / source.height
            }
            var maxW = source.width.toFloat()
            var maxH = maxW / ratio
            if (maxH > source.height) {
                maxH = source.height.toFloat()
                maxW = maxH * ratio
            }
            val w = (maxW * fraction).roundToInt().coerceIn(1, source.width)
            val h = (maxH * fraction).roundToInt().coerceIn(1, source.height)
            val x = ((source.width - w) * horizontal).roundToInt().coerceIn(0, source.width - w)
            val y = ((source.height - h) * vertical).roundToInt().coerceIn(0, source.height - h)
            runCatching { Bitmap.createBitmap(source, x, y, w, h) }.getOrNull()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            color = palette.surface,
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Recortar imagen del encabezado", color = palette.text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (crop != null) {
                    Surface(color = palette.surface2, shape = RoundedCornerShape(12.dp)) {
                        Image(
                            bitmap = crop.asImageBitmap(),
                            contentDescription = "Vista previa del recorte",
                            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 245.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Proporción del recorte", color = palette.text)
                    Spacer(Modifier.height(5.dp))
                    LegacySegmentedChoice(
                        listOf("Original", "1:1", "4:3", "16:9", "3:4"),
                        ratioName,
                        palette
                    ) { ratioName = it }
                    Spacer(Modifier.height(12.dp))
                    Text("Tamaño del área seleccionada", color = palette.text)
                    Slider(value = fraction, onValueChange = { fraction = it }, valueRange = 0.2f..1f)
                    Text("Posición horizontal", color = palette.text)
                    Slider(value = horizontal, onValueChange = { horizontal = it })
                    Text("Posición vertical", color = palette.text)
                    Slider(value = vertical, onValueChange = { vertical = it })
                } else {
                    Text("No se pudo abrir la imagen original.", color = MaterialTheme.colorScheme.error)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = crop != null && !saving,
                        onClick = {
                            val current = crop ?: return@Button
                            saving = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        val folder = File(context.filesDir, "report_header_crops").apply { mkdirs() }
                                        val output = File(folder, "header_${UUID.randomUUID()}.png")
                                        output.outputStream().use { stream ->
                                            require(current.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                                "No se pudo guardar el recorte"
                                            }
                                        }
                                        output.absolutePath
                                    }
                                }.onSuccess(onSave).onFailure { error = it.message ?: "No se pudo guardar" }
                                saving = false
                            }
                        }
                    ) { Text(if (saving) "Guardando..." else "Aplicar recorte") }
                }
            }
        }
    }
}
