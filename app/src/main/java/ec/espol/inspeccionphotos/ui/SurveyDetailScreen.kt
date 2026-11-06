package ec.espol.inspeccionphotos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.espol.inspeccionphotos.survey.*
import ec.espol.inspeccionphotos.util.SurveyMath
import ec.espol.inspeccionphotos.util.TimeUtils

@Composable
fun SurveyDetailScreen(
    survey: SurveySession,
    photos: List<PhotoRecord>,
    distanceMeters: Double,
    spatial: SpatialAnalysisBundle = SpatialAnalysisBundle(),
    phase4: Phase4Summary = Phase4Summary(),
    onBack: () -> Unit,
    onContinue: (() -> Unit)?,
    onManualZipBackup: () -> Unit,
    onOpenMap: () -> Unit = {},
    onImportGis: () -> Unit = {},
    onAnalyzeSpatial: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenTitle(survey.place, "${TimeUtils.date(survey.startedAtEpochMs)} · ${photos.size} fotos", onBack)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip("Recorrido", SurveyMath.formatDistance(distanceMeters))
            MetricChip("Estado", survey.status.name)
            MetricChip("Formato", survey.schemaVersion)
            MetricChip("SHA-256", "${photos.count { !it.sha256.isNullOrBlank() }}/${photos.size}")
            MetricChip("Verificadas", "${photos.count { it.syncState == SyncState.VERIFIED }}/${photos.size}", SpatialGood)
            MetricChip("GIS", "${spatial.buildings.size}")
            MetricChip("Cobertura", spatial.summary?.averageCoveragePercent?.let { "$it%" } ?: "—")
            MetricChip("Interior", "${phase4.interiorPoints} pts")
            MetricChip("Ráfagas", "${phase4.bursts}")
            MetricChip("Vídeos", "${phase4.videoScans}")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            onContinue?.let { Button(onClick = it) { Text("Continuar") } }
            Button(onClick = onOpenMap) { Text("Mapa espacial") }
            OutlinedButton(onClick = onImportGis) { Text("Importar GIS") }
            OutlinedButton(onClick = onAnalyzeSpatial) { Text("Reanalizar") }
            OutlinedButton(onClick = onManualZipBackup) { Text("Backup ZIP manual") }
        }
        spatial.summary?.let { summary ->
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = SpatialPanel),
                border = BorderStroke(1.dp, SpatialBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Fase 3", color = SpatialBlue, fontWeight = FontWeight.Bold)
                    Text("${summary.identifiedPhotos} fotos con edificio probable", color = SpatialMuted, fontSize = 12.sp)
                    Text("${summary.buildingsWithCoverage} edificios con cobertura", color = SpatialMuted, fontSize = 12.sp)
                    Text("route.geojson ✓", color = SpatialGood, fontSize = 12.sp)
                }
            }
        }
        if (phase4.interiorPoints > 0 || phase4.bursts > 0 || phase4.videoScans > 0) {
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SpatialPanel), border = BorderStroke(1.dp, SpatialBorder), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Fase 4", color = SpatialBlue, fontWeight = FontWeight.Bold)
                    Text("${phase4.interiorPoints} puntos interiores · ${phase4.interiorArCorePoints} ARCore VIO", color = SpatialMuted, fontSize = 12.sp)
                    Text("${phase4.burstPhotos} fotos de ráfaga", color = SpatialMuted, fontSize = 12.sp)
                    Text("${phase4.extractedVideoFrames} frames de vídeo útiles", color = SpatialMuted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Fotografías", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val inferenceByPhoto = spatial.inferences.associateBy { it.photoId }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.id }) { photo ->
                val inference = inferenceByPhoto[photo.id]
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpatialCard),
                    border = BorderStroke(1.dp, SpatialBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(photo.fileName, fontWeight = FontWeight.Bold)
                            Text("Foto ${photo.sequence.toString().padStart(3, '0')} · ${TimeUtils.time(photo.capturedAtEpochMs)}", color = SpatialMuted, fontSize = 12.sp)
                            Text(
                                photo.sha256?.let { "SHA-256 ${it.take(10)}…${it.takeLast(6)}" } ?: "SHA-256 pendiente",
                                color = if (photo.sha256 != null) SpatialGood else SpatialMuted,
                                fontSize = 12.sp
                            )
                            if (inference != null) {
                                Text(
                                    if (inference.identified) "Edificio probable: ${inference.buildingName} · ${(inference.confidence * 100).toInt()}%" else "Edificio probable: Desconocido",
                                    color = if (inference.identified) SpatialBlue else SpatialMuted,
                                    fontSize = 12.sp
                                )
                            }
                            photo.lastSyncError?.let { Text(it, color = SpatialWarn, fontSize = 11.sp, maxLines = 2) }
                        }
                        Column {
                            if (photo.location?.isInteriorRelative == true) {
                                Text("POSE RELATIVA", fontSize = 12.sp, color = SpatialBlue)
                                Text(photo.location.relativePoseUncertaintyMeters?.let { "incertidumbre ± %.1f m".format(it) } ?: "incertidumbre —", color = SpatialMuted, fontSize = 12.sp)
                            } else {
                                Text(photo.gpsQuality.name, fontSize = 12.sp)
                                Text(photo.location?.gnssAccuracyMeters?.let { "GNSS ± %.1f m".format(it) } ?: "sin GNSS", color = SpatialMuted, fontSize = 12.sp)
                            }
                            Text(
                                when (photo.syncState) {
                                    SyncState.VERIFIED -> "✓ VERIFICADA"
                                    SyncState.UPLOADED -> "↑ verificando"
                                    SyncState.QUEUED -> "↻ en cola"
                                    SyncState.PROCESSING -> "procesando"
                                    SyncState.ERROR -> "⚠ error"
                                    SyncState.LOCAL_ONLY -> "local"
                                },
                                color = when (photo.syncState) {
                                    SyncState.VERIFIED -> SpatialGood
                                    SyncState.ERROR -> SpatialBad
                                    SyncState.QUEUED, SyncState.UPLOADED -> SpatialBlue
                                    else -> SpatialMuted
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
