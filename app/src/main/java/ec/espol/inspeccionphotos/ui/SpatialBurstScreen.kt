package ec.espol.inspeccionphotos.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ec.espol.inspeccionphotos.camera.CameraXController
import ec.espol.inspeccionphotos.camera.CameraXMode
import ec.espol.inspeccionphotos.camera.PhotoCaptureCommitter
import ec.espol.inspeccionphotos.scan.BurstPose
import ec.espol.inspeccionphotos.scan.BurstTriggerPolicy
import ec.espol.inspeccionphotos.storage.Phase4Repository
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.*
import ec.espol.inspeccionphotos.worker.Phase2Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpatialBurstScreen(
    survey: SurveySession,
    location: LocationSnapshot?,
    orientation: OrientationSnapshot?,
    interiorPose: InteriorPoseSample? = null,
    repository: SurveyRepository,
    phase4: Phase4Repository,
    onPhotoCommitted: (PhotoRecord) -> Unit,
    onBack: () -> Unit,
    cameraEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    val scope = rememberCoroutineScope()
    val latestLocation by rememberUpdatedState(location)
    val latestOrientation by rememberUpdatedState(orientation)
    val latestInteriorPose by rememberUpdatedState(interiorPose)
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var burst by remember(survey.id) { mutableStateOf<SpatialBurstRecord?>(null) }
    var lastAcceptedPose by remember { mutableStateOf<BurstPose?>(null) }
    var controller by remember { mutableStateOf<CameraXController?>(null) }

    LaunchedEffect(survey.id, cameraEnabled) {
        burst = if (cameraEnabled) withContext(Dispatchers.IO) { phase4.startBurst(survey.id) }
        else SpatialBurstRecord("preview-burst", survey.id, System.currentTimeMillis(), photoIds = listOf("p1", "p2", "p3", "p4", "p5"))
    }

    DisposableEffect(controller) {
        onDispose { controller?.release() }
    }

    LaunchedEffect(cameraReady, burst?.id) {
        if (!cameraReady || burst == null || !cameraEnabled) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val pose = BurstPose(now, latestLocation, latestOrientation, latestInteriorPose)
            val record = burst ?: break
            if (!capturing && BurstTriggerPolicy.shouldCapture(
                    lastAcceptedPose, pose, record.minDistanceMeters, record.minHeadingChangeDegrees,
                    maxIntervalMillis = record.maxIntervalMillis
                )) {
                val cam = controller ?: break
                capturing = true
                val prepared = withContext(Dispatchers.IO) {
                    val fresh = repository.loadSession(survey.id) ?: survey
                    repository.prepareCapture(fresh, latestLocation, latestOrientation)
                }
                cam.takePhoto(prepared.second) { saved ->
                    scope.launch {
                        val committed = if (saved.isSuccess) withContext(Dispatchers.IO) {
                            val fresh = repository.loadSession(survey.id) ?: survey
                            PhotoCaptureCommitter.commit(repository, fresh, prepared.first)
                        } else null
                        if (committed != null) {
                            lastAcceptedPose = pose
                            val updated = (burst ?: record).copy(photoIds = (burst ?: record).photoIds + committed.id)
                            burst = updated
                            withContext(Dispatchers.IO) { phase4.saveBurst(updated) }
                            Phase2Scheduler.enqueueLocalProcessing(context)
                            onPhotoCommitted(committed)
                        } else {
                            withContext(Dispatchers.IO) { repository.clearPendingCapture(survey.id) }
                            cameraError = saved.exceptionOrNull()?.message ?: "No se pudo confirmar la fotografía"
                        }
                        capturing = false
                    }
                }
            }
            delay(250)
        }
    }

    fun finish() {
        if (capturing) {
            cameraError = "Espera a que la fotografía actual quede confirmada localmente antes de salir."
            return
        }
        val current = burst
        if (current != null) {
            scope.launch(Dispatchers.IO) {
                phase4.saveBurst(current.copy(status = BurstStatus.COMPLETED, endedAtEpochMs = System.currentTimeMillis()))
                repository.writeManifest(survey.id)
            }
        }
        onBack()
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        ScreenTitle("Ráfaga espacial", "Camina lentamente alrededor del objeto. La aplicación decide cuándo una nueva perspectiva aporta información.", ::finish)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1.4f).fillMaxHeight().background(Color.Black, RoundedCornerShape(20.dp))) {
                if (cameraEnabled) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { view ->
                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                lifecycleOwner?.let { owner ->
                                    CameraXController(ctx, owner, view, CameraXMode.PHOTO).also { cam ->
                                        controller = cam
                                        cam.bind { ready, error -> cameraReady = ready; cameraError = error }
                                    }
                                }
                            }
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Vista de cámara", color = Color.White.copy(.5f)) }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color.Black.copy(alpha = .58f), shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (capturing) "CAPTURANDO…" else if (cameraReady || !cameraEnabled) "RÁFAGA ACTIVA" else "INICIANDO CÁMARA…", color = Color.White, modifier = Modifier.padding(10.dp))
                }
            }
            Column(Modifier.weight(.72f).fillMaxHeight()) {
                Card(colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(survey.place, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        MetricLine("Fotos de ráfaga", (burst?.photoIds?.size ?: 0).toString())
                        MetricLine("Distancia mínima", "${burst?.minDistanceMeters ?: 1.2} m")
                        MetricLine("Cambio angular", "${(burst?.minHeadingChangeDegrees ?: 18.0).toInt()}°")
                        MetricLine(
                            if (location?.isInteriorRelative == true) "Pose relativa" else "GNSS",
                            if (location?.isInteriorRelative == true)
                                location.relativePoseUncertaintyMeters?.let { "incertidumbre ± %.1f m".format(it) } ?: "—"
                            else location?.gnssAccuracyMeters?.let { "± %.1f m".format(it) } ?: "sin señal"
                        )
                        MetricLine("Orientación", orientation?.let { "%.0f°".format(it.azimuthDegrees) } ?: "—")
                    }
                }
                cameraError?.let { Text(it, color = SpatialBad, modifier = Modifier.padding(top = 10.dp), fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                Text("No dispares manualmente. Muévete con calma: una foto se crea al recorrer suficiente distancia, cambiar el ángulo o pasar demasiado tiempo sin una nueva toma.", color = SpatialMuted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = ::finish, enabled = !capturing, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (capturing) "GUARDANDO FOTO…" else "FINALIZAR RÁFAGA") }
            }
        }
    }
}

