package ec.espol.inspeccionphotos.ui

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ec.espol.inspeccionphotos.arcore.ArCorePoseTracker
import ec.espol.inspeccionphotos.arcore.ArCoreRuntimeStatus
import ec.espol.inspeccionphotos.survey.ArPoseSample
import ec.espol.inspeccionphotos.survey.InteriorPoseSample
import ec.espol.inspeccionphotos.survey.SurveySession

@Composable
fun InteriorTrackingScreen(
    survey: SurveySession,
    pose: InteriorPoseSample?,
    onArPose: (ArPoseSample) -> Unit,
    onBack: () -> Unit,
    arCoreEnabled: Boolean = true
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    var status by remember { mutableStateOf<ArCoreRuntimeStatus>(ArCoreRuntimeStatus.Starting) }
    val tracker = remember(activity, arCoreEnabled) {
        if (activity != null && arCoreEnabled) ArCorePoseTracker(context, activity, onArPose, { status = it }) else null
    }

    DisposableEffect(tracker, lifecycleOwner) {
        if (tracker == null || lifecycleOwner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tracker.resume()
                Lifecycle.Event.ON_PAUSE -> tracker.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) tracker.resume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); tracker.close() }
    }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        ScreenTitle("Seguimiento interior", "ARCore VIO cuando está disponible; IMU/pasos como respaldo. La posición sigue siendo relativa a la última ancla exterior fiable.", onBack)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1.35f).fillMaxHeight().background(Color.Black, RoundedCornerShape(20.dp))) {
                tracker?.let { arTracker ->
                    AndroidView<android.opengl.GLSurfaceView>(
                        factory = { arTracker.surfaceView },
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ARCore no disponible en esta vista previa/dispositivo", color = Color.White.copy(.55f))
                }
                Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color.Black.copy(.65f), shape = RoundedCornerShape(12.dp)) {
                    Text(statusLabel(status), color = Color.White, modifier = Modifier.padding(10.dp))
                }
                Text("La imagen de cámara no se guarda desde ARCore. Esta pantalla usa la cámara sólo para estimar movimiento relativo.", color = Color.White.copy(.6f), modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
            }
            Column(Modifier.weight(.75f).fillMaxHeight()) {
                Card(colors = CardDefaults.cardColors(containerColor = SpatialCard)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(survey.place, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        MetricLine("Este", pose?.let { "%.2f m".format(it.eastMeters) } ?: "—")
                        MetricLine("Norte", pose?.let { "%.2f m".format(it.northMeters) } ?: "—")
                        MetricLine("Vertical", pose?.let { "%.2f m".format(it.upMeters) } ?: "—")
                        MetricLine("Incertidumbre", pose?.let { "± %.1f m".format(it.uncertaintyMeters) } ?: "—")
                        MetricLine("Fuente", pose?.source?.name ?: "IMU en espera")
                        MetricLine("Confianza pose relativa", pose?.confidence?.name ?: "—")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("ARCore aporta odometría visual-inercial, no coordenadas GPS absolutas. Al volver al exterior la aplicación puede reanclar la ruta cuando recupere GNSS fiable.", color = SpatialMuted)
                Spacer(Modifier.height(8.dp))
                Text("Esta función usa Google Play Services for AR (ARCore), provisto por Google LLC y sujeto a la Política de Privacidad de Google.", color = SpatialMuted)
                Spacer(Modifier.weight(1f))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("VOLVER A CAPTURA") }
            }
        }
    }
}

private fun statusLabel(status: ArCoreRuntimeStatus): String = when (status) {
    ArCoreRuntimeStatus.Starting -> "ARCore · iniciando"
    ArCoreRuntimeStatus.Tracking -> "ARCore · TRACKING"
    ArCoreRuntimeStatus.Paused -> "ARCore · buscando características"
    ArCoreRuntimeStatus.Unsupported -> "ARCore · dispositivo no compatible"
    is ArCoreRuntimeStatus.Error -> "ARCore · ${status.message}"
}
