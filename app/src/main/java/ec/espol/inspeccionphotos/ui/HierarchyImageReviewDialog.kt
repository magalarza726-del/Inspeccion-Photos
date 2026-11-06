package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ec.espol.inspeccionphotos.PhotoItem
import ec.espol.inspeccionphotos.ImageOrientationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HierarchyImageReviewDialog(photo: PhotoItem, palette: LegacyPalette, onDismiss: () -> Unit) {
    var scale by remember(photo.path) { mutableFloatStateOf(1f) }
    var offsetX by remember(photo.path) { mutableFloatStateOf(0f) }
    var offsetY by remember(photo.path) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, photo.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(photo.path)
                if (!file.exists()) return@runCatching null
                ImageOrientationUtils.decodeOrientedBitmap(file, 2200)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = palette.bg) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).background(palette.header).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("‹", color = Color.White, fontSize = 31.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(photo.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (photo.originalPath.isNotBlank()) Text("Editada · original protegido", color = palette.green, fontSize = 10.sp)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
                    if (image != null) {
                        Image(
                            image!!,
                            photo.name,
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                                .transformable(transformState),
                            contentScale = ContentScale.Fit
                        )
                    } else Text("No se pudo cargar la imagen", color = Color.White.copy(alpha = .7f))
                }
                if (photo.observation.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().background(palette.surface).padding(14.dp)) {
                        Text(photo.observation, color = palette.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
