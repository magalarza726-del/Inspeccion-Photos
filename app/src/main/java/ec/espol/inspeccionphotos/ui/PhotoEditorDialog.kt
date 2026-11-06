package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ec.espol.inspeccionphotos.ImageOrientationUtils
import ec.espol.inspeccionphotos.PhotoEditParameters
import ec.espol.inspeccionphotos.PhotoEditorEngine
import ec.espol.inspeccionphotos.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PhotoEditorDialog(
    photo: PhotoItem,
    palette: LegacyPalette,
    onDismiss: () -> Unit,
    onSave: (PhotoEditParameters) -> Unit,
    onRestoreOriginal: (() -> Unit)? = null
) {
    var rotation by remember(photo.path) { mutableIntStateOf(0) }
    var crop by remember(photo.path) { mutableFloatStateOf(100f) }
    var brightness by remember(photo.path) { mutableFloatStateOf(0f) }
    var contrast by remember(photo.path) { mutableFloatStateOf(1f) }
    var sharpness by remember(photo.path) { mutableFloatStateOf(0f) }

    val base by produceState<android.graphics.Bitmap?>(initialValue = null, photo.path) {
        value = withContext(Dispatchers.IO) { PhotoEditorEngine.loadPreview(File(photo.path), 1200) }
    }
    val params = PhotoEditParameters(rotation, crop.toInt(), brightness, contrast, sharpness)
    val preview by produceState<android.graphics.Bitmap?>(initialValue = base, base, params) {
        value = withContext(Dispatchers.Default) { base?.let { PhotoEditorEngine.renderPreview(it, params) } }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = palette.bg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).background(palette.header).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("×", color = Color.White, fontSize = 28.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Editor de foto", color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (photo.originalPath.isNotBlank()) Text("Original protegido", color = palette.green, fontSize = 10.sp)
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compact = maxWidth < 700.dp
                    if (compact) {
                        Column(Modifier.fillMaxSize()) {
                            EditorPreview(preview, Modifier.fillMaxWidth().weight(1f))
                            EditorControls(
                                palette = palette,
                                rotation = rotation,
                                crop = crop,
                                brightness = brightness,
                                contrast = contrast,
                                sharpness = sharpness,
                                onRotate = { rotation = (rotation + 1) % 4 },
                                onCrop = { crop = it },
                                onBrightness = { brightness = it },
                                onContrast = { contrast = it },
                                onSharpness = { sharpness = it },
                                onReset = { rotation = 0; crop = 100f; brightness = 0f; contrast = 1f; sharpness = 0f },
                                onRestoreOriginal = onRestoreOriginal,
                                onCancel = onDismiss,
                                onSave = { onSave(params) },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            EditorPreview(preview, Modifier.weight(1f).fillMaxHeight())
                            EditorControls(
                                palette = palette,
                                rotation = rotation,
                                crop = crop,
                                brightness = brightness,
                                contrast = contrast,
                                sharpness = sharpness,
                                onRotate = { rotation = (rotation + 1) % 4 },
                                onCrop = { crop = it },
                                onBrightness = { brightness = it },
                                onContrast = { contrast = it },
                                onSharpness = { sharpness = it },
                                onReset = { rotation = 0; crop = 100f; brightness = 0f; contrast = 1f; sharpness = 0f },
                                onRestoreOriginal = onRestoreOriginal,
                                onCancel = onDismiss,
                                onSave = { onSave(params) },
                                modifier = Modifier.width(380.dp).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCaptureEditPrompt(
    photoPath: String,
    palette: LegacyPalette,
    onEdit: () -> Unit,
    onKeep: () -> Unit
) {
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, photoPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(photoPath)
                if (!file.exists()) return@runCatching null
                ImageOrientationUtils.decodeOrientedBitmap(file, 1600)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onKeep, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = palette.bg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().height(54.dp).background(palette.header).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("‹", color = Color.White, fontSize = 30.sp, modifier = Modifier.clickable(onClick = onKeep).padding(4.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Revisión de foto", color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) Image(preview!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else CircularProgressIndicator(color = palette.cyan)
                }
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("¿Editar la foto?", color = palette.text, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text("Puedes rotar, cortar, ajustar brillo, contraste y nitidez.", color = palette.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegacySecondaryButton("Ahora no", palette, Modifier.weight(1f), onKeep)
                        LegacyPrimaryButton("Editar", palette, Modifier.weight(1f), onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPreview(bitmap: android.graphics.Bitmap?, modifier: Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        } else CircularProgressIndicator()
    }
}

@Composable
private fun EditorControls(
    palette: LegacyPalette,
    rotation: Int,
    crop: Float,
    brightness: Float,
    contrast: Float,
    sharpness: Float,
    onRotate: () -> Unit,
    onCrop: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onContrast: (Float) -> Unit,
    onSharpness: (Float) -> Unit,
    onReset: () -> Unit,
    onRestoreOriginal: (() -> Unit)?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.background(palette.surface).verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EditorToolButton("↻", "Rotar", palette, onRotate)
            EditorToolButton("✂", "Cortar", palette) { onCrop(if (crop > 80f) 75f else 100f) }
            EditorToolButton("☀", "Brillo", palette) { onBrightness(if (brightness == 0f) 18f else 0f) }
            EditorToolButton("◐", "Contraste", palette) { onContrast(if (contrast == 1f) 1.18f else 1f) }
            EditorToolButton("◇", "Nitidez", palette) { onSharpness(if (sharpness == 0f) .7f else 0f) }
        }
        Spacer(Modifier.height(12.dp))
        EditorSlider("Corte", "${crop.toInt()}%", crop, 50f..100f, onCrop, palette)
        EditorSlider("Brillo", brightness.toInt().toString(), brightness, -100f..100f, onBrightness, palette)
        EditorSlider("Contraste", String.format("%.2f", contrast), contrast, .5f..1.8f, onContrast, palette)
        EditorSlider("Nitidez", String.format("%.2f", sharpness), sharpness, 0f..1.5f, onSharpness, palette)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LegacySecondaryButton("Restablecer", palette, Modifier.weight(1f), onReset)
            if (onRestoreOriginal != null) LegacySecondaryButton("Original", palette, Modifier.weight(1f), onRestoreOriginal)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LegacySecondaryButton("Cancelar", palette, Modifier.weight(1f), onCancel)
            LegacyPrimaryButton("Guardar", palette, Modifier.weight(1f), onSave)
        }
    }
}

@Composable
private fun EditorToolButton(symbol: String, label: String, palette: LegacyPalette, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(2.dp)) {
        Box(Modifier.size(42.dp).background(palette.surface2, CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, color = palette.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = palette.muted, fontSize = 9.sp)
    }
}

@Composable
private fun EditorSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    palette: LegacyPalette
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = palette.text, fontSize = 12.sp)
        Text(valueText, color = palette.muted, fontSize = 11.sp)
    }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}
