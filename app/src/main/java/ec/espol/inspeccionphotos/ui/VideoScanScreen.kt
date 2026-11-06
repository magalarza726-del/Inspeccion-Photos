package ec.espol.inspeccionphotos.ui

import androidx.camera.video.VideoRecordEvent
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
import androidx.compose.ui.viewinterop.AndroidView
import ec.espol.inspeccionphotos.camera.CameraXController
import ec.espol.inspeccionphotos.camera.CameraXMode
import ec.espol.inspeccionphotos.storage.Phase4Repository
import ec.espol.inspeccionphotos.storage.SurveyRepository
import ec.espol.inspeccionphotos.survey.*
import ec.espol.inspeccionphotos.worker.Phase4Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoScanScreen(
    survey: SurveySession,
    location: LocationSnapshot?,
    orientation: OrientationSnapshot?,
    inertial: InertialSnapshot?,
    interiorPose: InteriorPoseSample?,
    repository: SurveyRepository,
    phase4: Phase4Repository,
    onBack: () -> Unit,
    cameraEnabled: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    val scope = rememberCoroutineScope()
    val latestLocation by rememberUpdatedState(location)
    val latestOrientation by rememberUpdatedState(orientation)
    val latestInertial by rememberUpdatedState(inertial)
    val latestInterior by rememberUpdatedState(interiorPose)
    var controller by remember { mutableStateOf<CameraXController?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableStateOf<VideoScanRecord?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    DisposableEffect(controller) { onDispose { controller?.release() } }

    LaunchedEffect(recording, scan?.id) {
        val current = scan ?: return@LaunchedEffect
        while (recording) {
            val now = System.currentTimeMillis()
            elapsed = now - current.startedAtEpochMs
            val pose = VideoPoseSample(now, latestLocation, latestOrientation, latestInterior, latestInertial)
            withContext(Dispatchers.IO) { phase4.appendVideoPose(current, pose) }
            delay(250)
        }
    }

    fun startRecording() {
        if (!cameraReady || recording) return
        scope.launch {
            val prepared = withContext(Dispatchers.IO) { phase4.createVideoScan(survey.id) }
            scan = prepared.first
            val result = controller?.startVideo(prepared.second) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    scope.launch {
                        val current = scan ?: prepared.first
                        val end = System.currentTimeMillis()
                        val updated = current.copy(
                            endedAtEpochMs = end,
                            durationMillis = end - current.startedAtEpochMs,
                            status = if (event.hasError()) VideoScanStatus.ERROR else VideoScanStatus.PROCESSING,
                            error = if (event.hasError()) "CameraX video error ${event.error}" else null
                        )
                        scan = updated
                        recording = false
                        withContext(Dispatchers.IO) { phase4.saveVideoScan(updated); repository.writeManifest(survey.id) }
                        if (!event.hasError()) Phase4Scheduler.extractRepresentativeFrames(context, survey.id, updated.id)
                    }
                }
            }
            if (result?.isSuccess == true) recording = true
            else cameraError = result?.exceptionOrNull()?.message ?: "No se pudo iniciar la grabación"
        }
    }

    fun stopRecording() { if (recording) controller?.stopVideo() }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        ScreenTitle("Escaneo caminando", "Vídeo + trayectoria + pose. Después se extraen sólo frames representativos.", {
            if (recording) stopRecording() else onBack()
        })
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1.4f).fillMaxHeight().background(Color.Black, RoundedCornerShape(20.dp))) {
                if (cameraEnabled) {
                    AndroidView<PreviewView>(
                        factory = { ctx ->
                            PreviewView(ctx).also { view ->
                                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                                lifecycleOwner?.let { owner ->
                                    CameraXController(ctx, owner, view, CameraXMode.VIDEO).also { cam ->
                                        controller = cam
                                        cam.bind { ready, error ->
                                            cameraReady = ready
                                            cameraError = error
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Vista de vídeo", color = Color.White.copy(.5f)) }
                if (recording) {
                    Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = SpatialBad.copy(alpha = .85f), shape = RoundedCornerShape(12.dp)) {
                        Text("● REC  ${ec.espol.inspeccionphotos.util.TimeUtils.duration(elapsed)}", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
            Column(Modifier.weight(.72f).fillMaxHeight()) {
                Card(colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(survey.place, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        MetricLine("Estado", when { recording -> "Grabando"; scan?.status == VideoScanStatus.PROCESSING -> "Extrayendo frames"; scan?.status == VideoScanStatus.READY -> "Listo"; else -> "Preparado" })
                        MetricLine(
                            if (location?.isInteriorRelative == true) "Pose relativa" else "GNSS",
                            if (location?.isInteriorRelative == true)
                                location.relativePoseUncertaintyMeters?.let { "incertidumbre ± %.1f m".format(it) } ?: "—"
                            else location?.gnssAccuracyMeters?.let { "± %.1f m".format(it) } ?: "sin señal"
                        )
                        MetricLine("Orientación", orientation?.let { "%.0f°".format(it.azimuthDegrees) } ?: "—")
                        MetricLine("Pasos IMU", inertial?.stepCount?.toString() ?: "0")
                        MetricLine("Frames útiles", scan?.extractedFrames?.toString() ?: "—")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("El vídeo original se conserva. Un worker analiza desplazamiento, cambio angular, nitidez y redundancia para no convertir cada frame en una foto duplicada.", color = SpatialMuted)
                cameraError?.let { Text(it, color = SpatialBad, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.weight(1f))
                if (!recording) Button(onClick = ::startRecording, enabled = cameraReady || !cameraEnabled, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("INICIAR ESCANEO") }
                else Button(onClick = ::stopRecording, colors = ButtonDefaults.buttonColors(containerColor = SpatialBad), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("DETENER Y PROCESAR") }
            }
        }
    }
}
