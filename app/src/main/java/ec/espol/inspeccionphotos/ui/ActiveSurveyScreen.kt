package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.EnvironmentMode
import ec.espol.inspeccionphotos.survey.InteriorPoseSample
import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.OrientationSnapshot
import ec.espol.inspeccionphotos.survey.SurveySession
import ec.espol.inspeccionphotos.util.SurveyMath
import ec.espol.inspeccionphotos.util.TimeUtils

private val PoiTypes = listOf(
    "Entrada", "Puerta", "Escalera", "Ascensor", "Baño", "Aula", "Laboratorio", "Oficina",
    "Pasillo", "Parqueadero", "Monumento", "Vegetación", "Otro"
)

@Composable
fun ActiveSurveyScreen(
    survey: SurveySession,
    location: LocationSnapshot?,
    orientation: OrientationSnapshot?,
    photoCount: Int,
    routeDistanceMeters: Double,
    elapsedMillis: Long,
    interiorPose: InteriorPoseSample? = null,
    onCapture: () -> Unit,
    onAddPoi: (String) -> Unit,
    onToggleEnvironment: () -> Unit,
    onInteriorTracking: () -> Unit = {},
    onSpatialBurst: () -> Unit = {},
    onVideoScan: () -> Unit = {},
    onMap: () -> Unit = {},
    onFinishRequest: () -> Unit,
    onHome: () -> Unit
) {
    var showPoi by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        if (compact) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                SurveyHeader(survey, location, orientation, compact = true)
                Spacer(Modifier.height(12.dp))
                CameraPanel(onCapture, Modifier.fillMaxWidth().height(360.dp))
                Spacer(Modifier.height(12.dp))
                SessionCard(survey, photoCount, routeDistanceMeters, elapsedMillis, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SurveyActionButtons(
                    survey, interiorPose,
                    onPoi = { showPoi = true }, onMap = onMap,
                    onToggleEnvironment = onToggleEnvironment,
                    onInteriorTracking = onInteriorTracking,
                    onSpatialBurst = onSpatialBurst,
                    onVideoScan = onVideoScan,
                    onHome = onHome,
                    onFinishRequest = onFinishRequest
                )
                Spacer(Modifier.height(20.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                SurveyHeader(survey, location, orientation, compact = false)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CameraPanel(onCapture, Modifier.weight(1.45f).fillMaxHeight())
                    Column(Modifier.weight(.8f).fillMaxHeight()) {
                        SessionCard(survey, photoCount, routeDistanceMeters, elapsedMillis, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        SurveyActionButtons(
                            survey, interiorPose,
                            onPoi = { showPoi = true }, onMap = onMap,
                            onToggleEnvironment = onToggleEnvironment,
                            onInteriorTracking = onInteriorTracking,
                            onSpatialBurst = onSpatialBurst,
                            onVideoScan = onVideoScan,
                            onHome = onHome,
                            onFinishRequest = onFinishRequest,
                            fillRemaining = true
                        )
                    }
                }
            }
        }
    }

    if (showPoi) {
        AlertDialog(
            onDismissRequest = { showPoi = false },
            title = { Text("+ Punto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PoiTypes.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { type ->
                                OutlinedButton(
                                    onClick = { onAddPoi(type); showPoi = false },
                                    modifier = Modifier.weight(1f)
                                ) { Text(type, fontSize = 13.sp) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPoi = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun SurveyHeader(
    survey: SurveySession,
    location: LocationSnapshot?,
    orientation: OrientationSnapshot?,
    compact: Boolean
) {
    if (compact) {
        Column {
            Text(survey.place, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Levantamiento activo", color = SpatialGood)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GpsBadge(location)
                MetricChip("Orient.", orientation?.let { "%.0f°".format(it.azimuthDegrees) } ?: "—")
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(survey.place, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Levantamiento activo", color = SpatialGood)
            }
            Spacer(Modifier.weight(1f))
            GpsBadge(location)
            Spacer(Modifier.width(8.dp))
            MetricChip("Orientación", orientation?.let { "%.0f°".format(it.azimuthDegrees) } ?: "—")
        }
    }
}

@Composable
private fun CameraPanel(onCapture: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .background(Color.Black, RoundedCornerShape(22.dp))
            .border(1.dp, SpatialBorder, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(18.dp)) {
            Text("CÁMARA", color = Color.White.copy(alpha = .45f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Captura fiable con la cámara del dispositivo",
                color = Color.White.copy(alpha = .55f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCapture,
                modifier = Modifier.size(126.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("FOTO", fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SessionCard(
    survey: SurveySession,
    photoCount: Int,
    routeDistanceMeters: Double,
    elapsedMillis: Long,
    modifier: Modifier
) {
    Card(colors = CardDefaults.cardColors(containerColor = SpatialCard), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("Sesión", color = SpatialMuted)
            Spacer(Modifier.height(8.dp))
            SessionMetricRow("Foto", photoCount.toString().padStart(3, '0'))
            SessionMetricRow("Recorrido", SurveyMath.formatDistance(routeDistanceMeters))
            SessionMetricRow("Duración", TimeUtils.duration(elapsedMillis))
            SessionMetricRow("Modo", if (survey.environmentMode == EnvironmentMode.EXTERIOR) "Exterior" else "Interior")
        }
    }
}

@Composable
private fun SurveyActionButtons(
    survey: SurveySession,
    interiorPose: InteriorPoseSample?,
    onPoi: () -> Unit,
    onMap: () -> Unit,
    onToggleEnvironment: () -> Unit,
    onInteriorTracking: () -> Unit,
    onSpatialBurst: () -> Unit,
    onVideoScan: () -> Unit,
    onHome: () -> Unit,
    onFinishRequest: () -> Unit,
    fillRemaining: Boolean = false
) {
    Column(Modifier.fillMaxWidth().then(if (fillRemaining) Modifier.fillMaxHeight() else Modifier)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("+ Punto", Modifier.weight(1f), onPoi)
            SecondaryButton("Mapa", Modifier.weight(1f), onMap)
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            if (survey.environmentMode == EnvironmentMode.EXTERIOR) "Cambiar a Interior" else "Cambiar a Exterior",
            Modifier.fillMaxWidth(),
            onToggleEnvironment
        )
        if (survey.environmentMode == EnvironmentMode.INTERIOR) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                interiorPose?.let { "Seguimiento interior · ± %.1f m".format(it.uncertaintyMeters) } ?: "Seguimiento interior / ARCore",
                Modifier.fillMaxWidth(),
                onInteriorTracking
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Ráfaga espacial", Modifier.weight(1f), onSpatialBurst)
            SecondaryButton("Escaneo vídeo", Modifier.weight(1f), onVideoScan)
        }
        if (fillRemaining) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Volver a capa") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onFinishRequest,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpatialWarn, contentColor = Color.Black)
        ) { Text("FINALIZAR", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SessionMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = SpatialMuted)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}
