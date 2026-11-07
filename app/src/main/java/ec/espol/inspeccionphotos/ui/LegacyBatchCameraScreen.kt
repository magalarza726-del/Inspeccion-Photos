package ec.espol.inspeccionphotos.ui

import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleOwner
import ec.espol.inspeccionphotos.Inspection
import ec.espol.inspeccionphotos.ImageOrientationUtils
import ec.espol.inspeccionphotos.InspectionMediaStore
import ec.espol.inspeccionphotos.PhotoItem
import ec.espol.inspeccionphotos.camera.CameraXController
import ec.espol.inspeccionphotos.camera.CameraXMode
import java.io.File

/** Cámara integrada para mantener la sesión abierta y confirmar todas las fotos al finalizar. */
data class LegacyCapturedPhoto(val file: File, val observation: String)

@Composable
fun LegacyBatchCameraScreen(
    folder: Inspection,
    mediaStore: InspectionMediaStore,
    palette: LegacyPalette,
    singleShot: Boolean = false,
    askBeforeSaving: Boolean = false,
    observationProvider: () -> String = { "" },
    onFinish: (List<LegacyCapturedPhoto>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner? = remember(context) {
        val owner: LifecycleOwner? = context.findLifecycleOwner()
        owner
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var controller by remember { mutableStateOf<CameraXController?>(null) }
    var ready by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var sessionFilePaths by rememberSaveable(folder.id) { mutableStateOf(arrayListOf<String>()) }
    var sessionObservations by rememberSaveable(folder.id) { mutableStateOf(arrayListOf<String>()) }
    var pendingCapturedPath by rememberSaveable(folder.id) { mutableStateOf<String?>(null) }
    var pendingCapturedObservation by rememberSaveable(folder.id) { mutableStateOf("") }
    val sessionFiles = sessionFilePaths.map(::File)
    fun completedCaptures(): List<LegacyCapturedPhoto> = sessionFiles.mapIndexed { index, file ->
        LegacyCapturedPhoto(file, sessionObservations.getOrNull(index).orEmpty())
    }

    val takePhotoNow: () -> Unit = capture@{
        if (!ready || capturing) return@capture
        val activeController = controller ?: return@capture
        capturing = true
        val virtualPhotos = folder.photos + sessionFiles.map { PhotoItem(it.name, it.absolutePath) }
        val name = mediaStore.nextPhotoName(folder.name, virtualPhotos)
        val file = runCatching { mediaStore.createCaptureFile(folder.id, name) }.getOrElse {
            capturing = false
            Toast.makeText(context, it.message ?: "No se pudo preparar la foto", Toast.LENGTH_LONG).show()
            return@capture
        }
        activeController.takePhoto(file) { result ->
            capturing = false
            result.onSuccess { saved ->
                val capturedObservation = observationProvider()
                if (askBeforeSaving) {
                    pendingCapturedPath = saved.absolutePath
                    pendingCapturedObservation = capturedObservation
                } else if (singleShot) {
                    onFinish(listOf(LegacyCapturedPhoto(saved, capturedObservation)))
                } else {
                    sessionFilePaths = ArrayList(sessionFilePaths + saved.absolutePath)
                    sessionObservations = ArrayList(sessionObservations + capturedObservation)
                }
            }.onFailure { err ->
                runCatching { file.delete() }
                Toast.makeText(context, err.message ?: "No se pudo tomar la foto", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            if (lifecycleOwner == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se pudo iniciar la cámara integrada", color = Color.White)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    // Se adapta al tamaño disponible sin recrear la sesión CameraX al girar.
                    val isLandscape = maxWidth > maxHeight
                    AndroidView<PreviewView>(
                        factory = { ctx -> PreviewView(ctx).also { previewView = it } },
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(previewView, folder.id) {
                        val view = previewView
                        if (view != null) {
                            val c = CameraXController(context, lifecycleOwner, view, CameraXMode.PHOTO)
                            controller = c
                            c.bind { ok, error ->
                                ready = ok
                                if (!ok) Toast.makeText(context, error ?: "No se pudo iniciar la cámara", Toast.LENGTH_LONG).show()
                            }
                        }
                        onDispose {
                            controller?.release()
                            controller = null
                            ready = false
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = .55f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "×",
                            color = Color.White,
                            fontSize = 30.sp,
                            modifier = Modifier.clickable {
                                sessionFiles.forEach { runCatching { it.delete() } }
                                pendingCapturedPath?.let { runCatching { File(it).delete() } }
                                pendingCapturedObservation = ""
                                onCancel()
                            }.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Cámara", color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(if (singleShot) "Una foto · ${folder.name}" else "Sesión continua · ${folder.name}", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, maxLines = 1)
                        }
                        Text("⚡", color = Color.White, fontSize = 20.sp)
                    }


                    if (isLandscape) {
                        // Como en la cámara del teléfono: obturador centrado en el lateral derecho.
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(132.dp)
                                .align(Alignment.CenterEnd)
                                .background(Color.Black.copy(alpha = .72f))
                                .padding(horizontal = 9.dp, vertical = 10.dp)
                        ) {
                            Column(
                                Modifier.align(Alignment.TopCenter),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("FOTO", color = palette.cyan, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(
                                    if (singleShot) "UNA POR UNA" else "SESIÓN ${sessionFiles.size}",
                                    color = Color.White.copy(alpha = .75f), fontSize = 10.sp
                                )
                            }
                            CameraShutter(
                                ready = ready,
                                capturing = capturing,
                                onCapture = takePhotoNow,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            if (singleShot) {
                                Text(
                                    "1 foto",
                                    color = Color.White.copy(alpha = .75f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            } else {
                                Button(
                                    onClick = { if (sessionFiles.isNotEmpty()) onFinish(completedCaptures()) },
                                    enabled = sessionFiles.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.blue, contentColor = Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    Text("Finalizar", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = .72f))
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("FOTO", color = palette.cyan, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(if (singleShot) "UNA POR UNA" else "SESIÓN ${sessionFiles.size}", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                            }
                            if (!singleShot && sessionFiles.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    sessionFiles.forEachIndexed { index, file ->
                                        val bitmap = remember(file.absolutePath, file.lastModified()) {
                                            ImageOrientationUtils.decodeOrientedBitmap(file, 320)
                                        }
                                        Box(
                                            Modifier.size(58.dp).background(Color.White.copy(alpha = .10f), RoundedCornerShape(9.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bitmap != null) {
                                                Image(bitmap.asImageBitmap(), "Foto ${index + 1}", Modifier.fillMaxSize().padding(2.dp), contentScale = ContentScale.Crop)
                                            } else Text("${index + 1}", color = Color.White)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    Modifier
                                        .size(54.dp)
                                        .background(Color.White.copy(alpha = .12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(sessionFiles.size.toString().padStart(2, '0'), color = Color.White, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                                CameraShutter(ready, capturing, takePhotoNow)
                                if (singleShot) {
                                    Box(Modifier.width(78.dp), contentAlignment = Alignment.Center) {
                                        Text("1 foto", color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { if (sessionFiles.isNotEmpty()) onFinish(completedCaptures()) },
                                        enabled = sessionFiles.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.blue, contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Finalizar", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    pendingCapturedPath?.let { pendingPath ->
                        val pendingFile = File(pendingPath)
                        val pendingBitmap = remember(pendingPath, pendingFile.lastModified()) {
                            ImageOrientationUtils.decodeOrientedBitmap(pendingFile, 1600)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(18.dp),
                            color = Color.Black.copy(alpha = .88f),
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = 8.dp
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("¿Está bien la foto?", color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                                if (pendingBitmap != null) {
                                    Image(
                                        pendingBitmap.asImageBitmap(),
                                        contentDescription = "Vista previa de la captura",
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 430.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching { pendingFile.delete() }
                                            pendingCapturedPath = null
                                            pendingCapturedObservation = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Repetir") }
                                    Button(
                                        onClick = {
                                            val observation = pendingCapturedObservation
                                            pendingCapturedPath = null
                                            pendingCapturedObservation = ""
                                            if (singleShot) onFinish(listOf(LegacyCapturedPhoto(pendingFile, observation)))
                                            else {
                                                sessionFilePaths = ArrayList(sessionFilePaths + pendingFile.absolutePath)
                                                sessionObservations = ArrayList(sessionObservations + observation)
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = palette.blue, contentColor = Color.White)
                                    ) { Text("Usar foto") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CameraShutter(
    ready: Boolean,
    capturing: Boolean,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onCapture,
        enabled = ready && !capturing,
        modifier = modifier.size(86.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            Modifier.size(70.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (capturing) Text("…", color = Color.Black, fontSize = 24.sp)
        }
    }
}
